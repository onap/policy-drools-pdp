/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018-2020 AT&T Intellectual Property. All rights reserved.
 * ================================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ============LICENSE_END=========================================================
 */

package org.onap.policy.drools.pooling;

import com.google.gson.JsonParseException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.onap.policy.common.endpoints.event.comm.Topic.CommInfrastructure;
import org.onap.policy.common.endpoints.event.comm.TopicListener;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.pooling.message.BucketAssignments;
import org.onap.policy.drools.pooling.message.Leader;
import org.onap.policy.drools.pooling.message.Message;
import org.onap.policy.drools.pooling.message.Offline;
import org.onap.policy.drools.pooling.state.ActiveState;
import org.onap.policy.drools.pooling.state.IdleState;
import org.onap.policy.drools.pooling.state.InactiveState;
import org.onap.policy.drools.pooling.state.QueryState;
import org.onap.policy.drools.pooling.state.StartState;
import org.onap.policy.drools.pooling.state.State;
import org.onap.policy.drools.pooling.state.StateTimerTask;
import org.onap.policy.drools.protocol.coders.EventProtocolCoderConstants;
import org.onap.policy.drools.system.PolicyController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of a {@link PoolingManager}. Until bucket assignments have been made,
 * events coming from external topics are saved in a queue for later processing. Once
 * assignments are made, the saved events are processed. In addition, while the controller
 * is locked, events are still forwarded to other hosts and bucket assignments are still
 * updated, based on any {@link Leader} messages that it receives.
 */
public class PoolingManagerImpl implements PoolingManager, TopicListener {

    private static final Logger logger = LoggerFactory.getLogger(PoolingManagerImpl.class);

    /**
     * Maximum number of times a message can be forwarded.
     */
    public static final int MAX_HOPS = 5;

    /**
     * ID of this host.
     */
    private final String host;

    /**
     * Properties with which this was configured.
     */
    private final PoolingProperties props;

    /**
     * Associated controller.
     */
    private final PolicyController controller;

    /**
     * Decremented each time the manager enters the Active state. Used by junit tests.
     */
    private final CountDownLatch activeLatch;

    /**
     * Used to encode & decode request objects received from & sent to a rule engine.
     */
    private final Serializer serializer;

    /**
     * Internal DMaaP topic used by this controller.
     */
    private final String topic;

    /**
     * Manager for the internal DMaaP topic.
     */
    private final DmaapManager dmaapMgr;

    /**
     * Lock used while updating {@link #current}. In general, public methods must use
     * this, while private methods assume the lock is already held.
     */
    private final Object curLocker = new Object();

    /**
     * Current state.
     *
     * <p>This uses a finite state machine, wherein the state object contains all of the data
     * relevant to that state. Each state object has a process() method, specific to each
     * type of {@link Message} subclass. The method returns the next state object, or
     * {@code null} if the state is to remain the same.
     */
    private State current;

    /**
     * Current bucket assignments or {@code null}.
     */
    private BucketAssignments assignments = null;

    /**
     * Pool used to execute timers.
     */
    private ScheduledThreadPoolExecutor scheduler = null;

    /**
     * Constructs the manager, initializing all of the data structures.
     *
     * @param host name/uuid of this host
     * @param controller controller with which this is associated
     * @param props feature properties specific to the controller
     * @param activeLatch latch to be decremented each time the manager enters the Active
     *        state
     */
    public PoolingManagerImpl(String host, PolicyController controller, PoolingProperties props,
                    CountDownLatch activeLatch) {
        this.host = host;
        this.controller = controller;
        this.props = props;
        this.activeLatch = activeLatch;

        try {
            this.serializer = new Serializer();
            this.topic = props.getPoolingTopic();
            this.dmaapMgr = makeDmaapManager(props.getPoolingTopic());
            this.current = new IdleState(this);

            logger.info("allocating host {} to controller {} for topic {}", host, controller.getName(), topic);

        } catch (ClassCastException e) {
            logger.error("not a topic listener, controller {}", controller.getName());
            throw new PoolingFeatureRtException(e);

        } catch (PoolingFeatureException e) {
            logger.error("failed to attach internal DMaaP topic to controller {}", controller.getName());
            throw new PoolingFeatureRtException(e);
        }
    }

    /**
     * Should only be used by junit tests.
     *
     * @return the current state
     */
    protected State getCurrent() {
        synchronized (curLocker) {
            return current;
        }
    }

    @Override
    public String getHost() {
        return host;
    }

    @Override
    public String getTopic() {
        return topic;
    }

    @Override
    public PoolingProperties getProperties() {
        return props;
    }

    /**
     * Indicates that the controller is about to start. Starts the publisher for the
     * internal topic, and creates a thread pool for the timers.
     */
    public void beforeStart() {
        synchronized (curLocker) {
            if (scheduler == null) {
                dmaapMgr.startPublisher();

                logger.debug("make scheduler thread for topic {}", getTopic());
                scheduler = makeScheduler();

                /*
                 * Only a handful of timers at any moment, thus we can afford to take the
                 * time to remove them when they're cancelled.
                 */
                scheduler.setRemoveOnCancelPolicy(true);
                scheduler.setMaximumPoolSize(1);
                scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
                scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
            }
        }
    }

    /**
     * Indicates that the controller has successfully started. Starts the consumer for the
     * internal topic, enters the {@link StartState}, and sets the filter for the initial
     * state.
     */
    public void afterStart() {
        synchronized (curLocker) {
            if (current instanceof IdleState) {
                dmaapMgr.startConsumer(this);
                changeState(new StartState(this));
            }
        }
    }

    /**
     * Indicates that the controller is about to stop. Stops the consumer, the scheduler,
     * and the current state.
     */
    public void beforeStop() {
        ScheduledThreadPoolExecutor sched;

        synchronized (curLocker) {
            sched = scheduler;
            scheduler = null;

            if (!(current instanceof IdleState)) {
                changeState(new IdleState(this));
                dmaapMgr.stopConsumer(this);
                publishAdmin(new Offline(getHost()));
            }

            assignments = null;
        }

        if (sched != null) {
            logger.debug("stop scheduler for topic {}", getTopic());
            sched.shutdownNow();
        }
    }

    /**
     * Indicates that the controller has stopped. Stops the publisher and logs a warning
     * if any events are still in the queue.
     */
    public void afterStop() {
        synchronized (curLocker) {
            /*
             * stop the publisher, but allow time for any Offline message to be
             * transmitted
             */
            dmaapMgr.stopPublisher(props.getOfflinePubWaitMs());
        }
    }

    /**
     * Indicates that the controller is about to be locked. Enters the idle state, as all
     * it will be doing is forwarding messages.
     */
    public void beforeLock() {
        logger.info("locking manager for topic {}", getTopic());

        synchronized (curLocker) {
            changeState(new IdleState(this));
        }
    }

    /**
     * Indicates that the controller has been unlocked. Enters the start state, if the
     * controller is running.
     */
    public void afterUnlock() {
        logger.info("unlocking manager for topic {}", getTopic());

        synchronized (curLocker) {
            if (controller.isAlive() && current instanceof IdleState && scheduler != null) {
                changeState(new StartState(this));
            }
        }
    }

    /**
     * Changes the finite state machine to a new state, provided the new state is not
     * {@code null}.
     *
     * @param newState new state, or {@code null} if to remain unchanged
     */
    private void changeState(State newState) {
        if (newState != null) {
            current.cancelTimers();
            current = newState;

            newState.start();
        }
    }

    @Override
    public CancellableScheduledTask schedule(long delayMs, StateTimerTask task) {
        // wrap the task in a TimerAction and schedule it
        ScheduledFuture<?> fut = scheduler.schedule(new TimerAction(task), delayMs, TimeUnit.MILLISECONDS);

        // wrap the future in a "CancellableScheduledTask"
        return () -> fut.cancel(false);
    }

    @Override
    public CancellableScheduledTask scheduleWithFixedDelay(long initialDelayMs, long delayMs, StateTimerTask task) {
        // wrap the task in a TimerAction and schedule it
        ScheduledFuture<?> fut = scheduler.scheduleWithFixedDelay(new TimerAction(task), initialDelayMs, delayMs,
                        TimeUnit.MILLISECONDS);

        // wrap the future in a "CancellableScheduledTask"
        return () -> fut.cancel(false);
    }

    @Override
    public void publishAdmin(Message msg) {
        publish(Message.ADMIN, msg);
    }

    @Override
    public void publish(String channel, Message msg) {
        logger.info("publish {} to {} on topic {}", msg.getClass().getSimpleName(), channel, getTopic());

        msg.setChannel(channel);

        try {
            // ensure it's valid before we send it
            msg.checkValidity();

            String txt = serializer.encodeMsg(msg);
            dmaapMgr.publish(txt);

        } catch (JsonParseException e) {
            logger.error("failed to serialize message for topic {} channel {}", topic, channel, e);

        } catch (PoolingFeatureException e) {
            logger.error("failed to publish message for topic {} channel {}", topic, channel, e);
        }
    }

    /**
     * Handles an event from the internal topic.
     *
     * @param commType comm infrastructure
     * @param topic2 topic
     * @param event event
     */
    @Override
    public void onTopicEvent(CommInfrastructure commType, String topic2, String event) {

        if (event == null) {
            logger.error("null event on topic {}", topic);
            return;
        }

        synchronized (curLocker) {
            // it's on the internal topic
            handleInternal(event);
        }
    }

    /**
     * Called by the PolicyController before it offers the event to the DroolsController.
     * If the controller is locked, then it isn't processing events. However, they still
     * need to be forwarded, thus in that case, they are decoded and forwarded.
     *
     * <p>On the other hand, if the controller is not locked, then we just return immediately
     * and let {@link #beforeInsert(Object, String, String, Object) beforeInsert()} handle
     * it instead, as it already has the decoded message.
     *
     * @param topic2 topic
     * @param event event
     * @return {@code true} if the event was handled by the manager, {@code false} if it
     *         must still be handled by the invoker
     */
    public boolean beforeOffer(String topic2, String event) {

        if (!controller.isLocked()) {
            // we should NOT intercept this message - let the invoker handle it
            return false;
        }

        return handleExternal(topic2, decodeEvent(topic2, event));
    }

    /**
     * Called by the DroolsController before it inserts the event into the rule engine.
     *
     * @param topic2 topic
     * @param event event, as an object
     * @return {@code true} if the event was handled by the manager, {@code false} if it
     *         must still be handled by the invoker
     */
    public boolean beforeInsert(String topic2, Object event) {
        return handleExternal(topic2, event);
    }

    /**
     * Handles an event from an external topic.
     *
     * @param topic2 topic
     * @param event event, as an object, or {@code null} if it cannot be decoded
     * @return {@code true} if the event was handled by the manager, {@code false} if it
     *         must still be handled by the invoker
     */
    private boolean handleExternal(String topic2, Object event) {
        if (event == null) {
            // no event - let the invoker handle it
            return false;
        }

        synchronized (curLocker) {
            return handleExternal(topic2, event, event.hashCode());
        }
    }

    /**
     * Handles an event from an external topic.
     *
     * @param topic2 topic
     * @param event event, as an object
     * @param eventHashCode event's hash code
     * @return {@code true} if the event was handled, {@code false} if the invoker should
     *         handle it
     */
    private boolean handleExternal(String topic2, Object event, int eventHashCode) {
        if (assignments == null) {
            // no bucket assignments yet - handle locally
            logger.info("handle event locally for request {}", event);

            // we did NOT consume the event
            return false;

        } else {
            return handleEvent(topic2, event, eventHashCode);
        }
    }

    /**
     * Handles a {@link Forward} event, possibly forwarding it again.
     *
     * @param topic2 topic
     * @param event event, as an object
     * @param eventHashCode event's hash code
     * @return {@code true} if the event was handled, {@code false} if the invoker should
     *         handle it
     */
    private boolean handleEvent(String topic2, Object event, int eventHashCode) {
        String target = assignments.getAssignedHost(eventHashCode);

        if (target == null) {
            /*
             * This bucket has no assignment - just discard the event
             */
            logger.warn("discarded event for unassigned bucket from topic {}", topic2);
            return true;
        }

        if (target.equals(host)) {
            /*
             * Message belongs to this host - allow the controller to handle it.
             */
            logger.info("handle local event for request {} from topic {}", event, topic2);
            return false;
        }

        // not our message, consume the event
        logger.warn("discarded event for host {} from topic {}", target, topic2);
        return true;
    }

    /**
     * Decodes an event from a String into an event Object.
     *
     * @param topic2 topic
     * @param event event
     * @return the decoded event object, or {@code null} if it can't be decoded
     */
    private Object decodeEvent(String topic2, String event) {
        DroolsController drools = controller.getDrools();

        // check if this topic has a decoder

        if (!canDecodeEvent(drools, topic2)) {

            logger.warn("{}: DECODING-UNSUPPORTED {}:{}:{}", drools, topic2, drools.getGroupId(),
                            drools.getArtifactId());
            return null;
        }

        // decode

        try {
            return decodeEventWrapper(drools, topic2, event);

        } catch (UnsupportedOperationException | IllegalStateException | IllegalArgumentException e) {
            logger.debug("{}: DECODE FAILED: {} <- {} because of {}", drools, topic2, event, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Handles an event from the internal topic. This uses reflection to identify the
     * appropriate process() method to invoke, based on the type of Message that was
     * decoded.
     *
     * @param event the serialized {@link Message} read from the internal topic
     */
    private void handleInternal(String event) {
        Class<?> clazz = null;

        try {
            Message msg = serializer.decodeMsg(event);

            // get the class BEFORE checking the validity
            clazz = msg.getClass();

            msg.checkValidity();

            Method meth = current.getClass().getMethod("process", msg.getClass());
            changeState((State) meth.invoke(current, msg));

        } catch (JsonParseException e) {
            logger.warn("failed to decode message for topic {}", topic, e);

        } catch (NoSuchMethodException | SecurityException e) {
            logger.error("no processor for message {} for topic {}", clazz, topic, e);

        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
                        | PoolingFeatureException e) {
            logger.error("failed to process message {} for topic {}", clazz, topic, e);
        }
    }

    @Override
    public void startDistributing(BucketAssignments asgn) {
        synchronized (curLocker) {
            int sz = (asgn == null ? 0 : asgn.getAllHosts().size());
            logger.info("new assignments for {} hosts on topic {}", sz, getTopic());
            assignments = asgn;
        }
    }

    @Override
    public BucketAssignments getAssignments() {
        return assignments;
    }

    @Override
    public State goStart() {
        return new StartState(this);
    }

    @Override
    public State goQuery() {
        return new QueryState(this);
    }

    @Override
    public State goActive() {
        activeLatch.countDown();
        return new ActiveState(this);
    }

    @Override
    public State goInactive() {
        return new InactiveState(this);
    }

    /**
     * Action to run a timer task. Only runs the task if the machine is still in the state
     * that it was in when the timer was created.
     */
    private class TimerAction implements Runnable {

        /**
         * State of the machine when the timer was created.
         */
        private State origState;

        /**
         * Task to be executed.
         */
        private StateTimerTask task;

        /**
         * Constructor.
         *
         * @param task task to execute when this timer runs
         */
        public TimerAction(StateTimerTask task) {
            this.origState = current;
            this.task = task;
        }

        @Override
        public void run() {
            synchronized (curLocker) {
                if (current == origState) {
                    changeState(task.fire());
                }
            }
        }
    }

    /**
     * Creates a DMaaP manager.
     *
     * @param topic name of the internal DMaaP topic
     * @return a new DMaaP manager
     * @throws PoolingFeatureException if an error occurs
     */
    protected DmaapManager makeDmaapManager(String topic) throws PoolingFeatureException {
        return new DmaapManager(topic);
    }

    /**
     * Creates a scheduled thread pool.
     *
     * @return a new scheduled thread pool
     */
    protected ScheduledThreadPoolExecutor makeScheduler() {
        return new ScheduledThreadPoolExecutor(1);
    }

    /**
     * Determines if the event can be decoded.
     *
     * @param drools drools controller
     * @param topic topic on which the event was received
     * @return {@code true} if the event can be decoded, {@code false} otherwise
     */
    protected boolean canDecodeEvent(DroolsController drools, String topic) {
        return EventProtocolCoderConstants.getManager().isDecodingSupported(drools.getGroupId(), drools.getArtifactId(),
                        topic);
    }

    /**
     * Decodes the event.
     *
     * @param drools drools controller
     * @param topic topic on which the event was received
     * @param event event text to be decoded
     * @return the decoded event
     * @throws IllegalArgumentException illegal argument
     * @throw UnsupportedOperationException unsupported operation
     * @throws IllegalStateException illegal state
     */
    protected Object decodeEventWrapper(DroolsController drools, String topic, String event) {
        return EventProtocolCoderConstants.getManager().decode(drools.getGroupId(), drools.getArtifactId(), topic,
                        event);
    }
}
