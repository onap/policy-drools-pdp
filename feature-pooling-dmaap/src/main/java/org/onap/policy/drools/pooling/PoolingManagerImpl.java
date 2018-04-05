/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018 AT&T Intellectual Property. All rights reserved.
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

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.event.comm.Topic.CommInfrastructure;
import org.onap.policy.drools.event.comm.TopicListener;
import org.onap.policy.drools.pooling.extractor.ClassExtractors;
import org.onap.policy.drools.pooling.message.BucketAssignments;
import org.onap.policy.drools.pooling.message.Forward;
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
import org.onap.policy.drools.protocol.coders.EventProtocolCoder;
import org.onap.policy.drools.system.PolicyController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.core.JsonProcessingException;

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
     * Type of item that the extractors will be extracting.
     */
    private static final String EXTRACTOR_TYPE = "requestId";

    /**
     * Prefix for extractor properties.
     */
    private static final String PROP_EXTRACTOR_PREFIX = "extractor." + EXTRACTOR_TYPE;

    /**
     * Factory used to create various objects. Can be overridden during junit testing.
     */
    private static Factory factory = new Factory();

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
     * Where to offer events that have been forwarded to this host (i.e, the controller).
     */
    private final TopicListener listener;

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
     * Used to extract the request id from the decoded message.
     */
    private final ClassExtractors extractors;

    /**
     * Lock used while updating {@link #current}. In general, public methods must use
     * this, while private methods assume the lock is already held.
     */
    private final Object curLocker = new Object();

    /**
     * Current state.
     * <p>
     * This uses a finite state machine, wherein the state object contains all of the data
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
     * Queue used when no bucket assignments are available.
     */
    private EventQueue eventq;

    /**
     * {@code True} if events offered by the controller should be intercepted,
     * {@code false} otherwise.
     */
    private boolean intercept = true;

    /**
     * Constructs the manager, initializing all of the data structures.
     * 
     * @param controller controller with which this is associated
     * @param props feature properties specific to the controller
     */
    public PoolingManagerImpl(PolicyController controller, PoolingProperties props) {
        this.host = UUID.randomUUID().toString();
        this.controller = controller;
        this.props = props;

        try {
            this.listener = (TopicListener) controller;
            this.serializer = new Serializer();
            this.topic = props.getPoolingTopic();
            this.eventq = factory.makeEventQueue(props);

            SpecProperties spec = new SpecProperties(PROP_EXTRACTOR_PREFIX, controller.getName());
            this.extractors = factory.makeClassExtractors(spec);

            this.dmaapMgr = factory.makeDmaapManager(props);
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

    protected static Factory getFactory() {
        return factory;
    }

    protected static void setFactory(Factory factory) {
        PoolingManagerImpl.factory = factory;
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

    public String getHost() {
        return host;
    }

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
     * 
     * @throws PoolingFeatureException if the internal topic publisher cannot be started
     */
    public void beforeStart() throws PoolingFeatureException {
        synchronized (curLocker) {
            if (scheduler == null) {
                dmaapMgr.startPublisher();

                logger.debug("make scheduler thread for topic {}", getTopic());
                scheduler = factory.makeScheduler();

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
                dmaapMgr.stopConsumer(this);
                changeState(new IdleState(this));
                publishAdmin(new Offline(getHost()));
            }
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
            if (!eventq.isEmpty()) {
                logger.warn("discarded {} messages after stopping topic {}", eventq.size(), topic);
                eventq.clear();
            }

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

            // set the filter before starting the state
            setFilter(newState.getFilter());
            newState.start();
        }
    }

    /**
     * Sets the server-side filter for the internal topic.
     * 
     * @param filter new filter to be used
     */
    private void setFilter(Map<String, Object> filter) {
        try {
            dmaapMgr.setFilter(serializer.encodeFilter(filter));

        } catch (JsonProcessingException e) {
            logger.error("failed to encode server-side filter for topic {}, {}", topic, filter, e);

        } catch (PoolingFeatureException e) {
            logger.error("failed to set server-side filter for topic {}, {}", topic, filter, e);
        }
    }

    @Override
    public CountDownLatch internalTopicFailed() {
        logger.error("communication failed for topic {}", topic);

        CountDownLatch latch = new CountDownLatch(1);

        /*
         * We don't want to build up items in our queue if we can't forward them to other
         * hosts, so we just stop the controller.
         * 
         * Use a background thread to prevent deadlocks.
         */
        new Thread(() -> {
            controller.stop();
            latch.countDown();
        }).start();

        return latch;
    }

    @Override
    public CancellableScheduledTask schedule(long delayMs, StateTimerTask task) {
        // wrap the task in a TimerAction and schedule it
        ScheduledFuture<?> fut = scheduler.schedule(new TimerAction(task), delayMs, TimeUnit.MILLISECONDS);

        // wrap the future in a "CancellableScheduledTask"
        return new CancellableScheduledTask() {
            @Override
            public void cancel() {
                fut.cancel(false);
            }
        };
    }

    @Override
    public CancellableScheduledTask scheduleWithFixedDelay(long initialDelayMs, long delayMs, StateTimerTask task) {
        // wrap the task in a TimerAction and schedule it
        ScheduledFuture<?> fut = scheduler.scheduleWithFixedDelay(new TimerAction(task), initialDelayMs, delayMs,
                        TimeUnit.MILLISECONDS);

        // wrap the future in a "CancellableScheduledTask"
        return new CancellableScheduledTask() {
            @Override
            public void cancel() {
                fut.cancel(false);
            }
        };
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

        } catch (JsonProcessingException e) {
            logger.error("failed to serialize message for topic {} channel {}", topic, channel, e);

        } catch (PoolingFeatureException e) {
            logger.error("failed to publish message for topic {} channel {}", topic, channel, e);
        }
    }

    /**
     * Handles an event from the internal topic.
     * 
     * @param topic2
     * @param event
     * @return {@code true} if the event was handled, {@code false} if the controller
     *         should handle it
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
     * <p>
     * On the other hand, if the controller is not locked, then we just return immediately
     * and let {@link #beforeInsert(Object, String, String, Object) beforeInsert()} handle
     * it instead, as it already has the decoded message.
     * 
     * @param protocol
     * @param topic2
     * @param event
     * @return {@code true} if the event was handled by the manager, {@code false} if it
     *         must still be handled by the invoker
     */
    public boolean beforeOffer(CommInfrastructure protocol, String topic2, String event) {

        if (!controller.isLocked() || !intercept) {
            // we should NOT intercept this message - let the invoker handle it
            return false;
        }

        return handleExternal(protocol, topic2, event, extractRequestId(decodeEvent(topic2, event)));
    }

    /**
     * Called by the DroolsController before it inserts the event into the rule engine.
     * 
     * @param protocol
     * @param topic2
     * @param event original event text, as received from the Bus
     * @param event2 event, as an object
     * @return {@code true} if the event was handled by the manager, {@code false} if it
     *         must still be handled by the invoker
     */
    public boolean beforeInsert(CommInfrastructure protocol, String topic2, String event, Object event2) {

        if (!intercept) {
            // we should NOT intercept this message - let the invoker handle it
            return false;
        }

        return handleExternal(protocol, topic2, event, extractRequestId(event2));
    }

    /**
     * Handles an event from an external topic.
     * 
     * @param protocol
     * @param topic2
     * @param event
     * @param reqid request id extracted from the event, or {@code null} if it couldn't be
     *        extracted
     * @return {@code true} if the event was handled by the manager, {@code false} if it
     *         must still be handled by the invoker
     */
    private boolean handleExternal(CommInfrastructure protocol, String topic2, String event, String reqid) {
        if (reqid == null) {
            // no request id - let the invoker handle it
            return false;
        }

        if (reqid.isEmpty()) {
            logger.warn("handle locally due to empty request id for topic {}", topic2);
            // no request id - let the invoker handle it
            return false;
        }

        Forward ev = makeForward(protocol, topic2, event, reqid);
        if (ev == null) {
            // invalid args - consume the message
            logger.warn("constructed an invalid Forward message on topic {}", getTopic());
            return true;
        }

        synchronized (curLocker) {
            return handleExternal(ev);
        }
    }

    /**
     * Handles an event from an external topic.
     * 
     * @param event
     * @return {@code true} if the event was handled, {@code false} if the invoker should
     *         handle it
     */
    private boolean handleExternal(Forward event) {
        if (assignments == null) {
            // no bucket assignments yet - add it to the queue
            eventq.add(event);

            // we've consumed the event
            return true;

        } else {
            return handleEvent(event);
        }
    }

    /**
     * Handles a {@link Forward} event, possibly forwarding it again.
     * 
     * @param event
     * @return {@code true} if the event was handled, {@code false} if the invoker should
     *         handle it
     */
    private boolean handleEvent(Forward event) {
        String target = assignments.getAssignedHost(event.getRequestId().hashCode());

        if (target == null) {
            /*
             * This bucket has no assignment - just discard the event
             */
            logger.warn("discarded event for unassigned bucket from topic {}", event.getTopic());
            return true;
        }

        if (target.equals(host)) {
            /*
             * Message belongs to this host - allow the controller to handle it.
             */
            return false;
        }

        // forward to a different host, if hop count has been exhausted
        if (event.getNumHops() > MAX_HOPS) {
            logger.warn("message discarded - hop count {} exceeded {} for topic {}", event.getNumHops(), MAX_HOPS,
                            topic);

        } else {
            logger.warn("reforward event hop-count={} from topic {}", event.getNumHops(), event.getTopic());
            event.bumpNumHops();
            publish(target, event);
        }

        // either way, consume the event
        return true;
    }

    /**
     * Extract the request id from an event object.
     * 
     * @param event the event object, or {@code null}
     * @return the event's request id, or {@code null} if it can't be extracted
     */
    private String extractRequestId(Object event) {
        if (event == null) {
            return null;
        }

        Object reqid = extractors.extract(event);
        return (reqid != null ? reqid.toString() : null);
    }

    /**
     * Decodes an event from a String into an event Object.
     * 
     * @param topic2
     * @param event
     * @return the decoded event object, or {@code null} if it can't be decoded
     */
    private Object decodeEvent(String topic2, String event) {
        DroolsController drools = controller.getDrools();

        // check if this topic has a decoder

        if (!factory.canDecodeEvent(drools, topic2)) {

            logger.warn("{}: DECODING-UNSUPPORTED {}:{}:{}", drools, topic2, drools.getGroupId(),
                            drools.getArtifactId());
            return null;
        }

        // decode

        try {
            return factory.decodeEvent(drools, topic2, event);

        } catch (UnsupportedOperationException | IllegalStateException | IllegalArgumentException e) {
            logger.debug("{}: DECODE FAILED: {} <- {} because of {}", drools, topic2, event, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Makes a {@link Forward}, and validates its contents.
     * 
     * @param protocol
     * @param topic2
     * @param event
     * @param reqid
     * @return a new message, or {@code null} if the message was invalid
     */
    private Forward makeForward(CommInfrastructure protocol, String topic2, String event, String reqid) {
        try {
            Forward ev = new Forward(host, protocol, topic2, event, reqid);

            // required for the validity check
            ev.setChannel(host);

            ev.checkValidity();

            return ev;

        } catch (PoolingFeatureException e) {
            logger.error("invalid message for topic {}", topic2, e);
            return null;
        }
    }

    @Override
    public void handle(Forward event) {
        synchronized (curLocker) {
            if (!handleExternal(event)) {
                // this host should handle it - inject it
                inject(event);
            }
        }
    }

    /**
     * Injects an event into the controller.
     * 
     * @param event
     */
    private void inject(Forward event) {
        try {
            intercept = false;
            listener.onTopicEvent(event.getProtocol(), event.getTopic(), event.getPayload());

        } finally {
            intercept = true;
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

        } catch (IOException e) {
            logger.warn("failed to decode message for topic {}", topic, e);

        } catch (NoSuchMethodException | SecurityException e) {
            logger.error("no processor for message {} for topic {}", clazz, topic, e);

        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
                        | PoolingFeatureException e) {
            logger.error("failed to process message {} for topic {}", clazz, topic, e);
        }
    }

    @Override
    public void startDistributing(BucketAssignments assignments) {
        if (assignments == null) {
            return;
        }

        logger.info("new assignments for topic {}", getTopic());

        synchronized (curLocker) {
            this.assignments = assignments;

            // now that we have assignments, we can process the queue
            Forward ev;
            while ((ev = eventq.poll()) != null) {
                handle(ev);
            }
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
     * Factory used to create objects.
     */
    public static class Factory {

        /**
         * Creates an event queue.
         * 
         * @param props properties used to configure the event queue
         * @return a new event queue
         */
        public EventQueue makeEventQueue(PoolingProperties props) {
            return new EventQueue(props.getOfflineLimit(), props.getOfflineAgeMs());
        }

        /**
         * Creates object extractors.
         * 
         * @param props properties used to configure the extractors
         * @return a new set of extractors
         */
        public ClassExtractors makeClassExtractors(Properties props) {
            return new ClassExtractors(props, PROP_EXTRACTOR_PREFIX, EXTRACTOR_TYPE);
        }

        /**
         * Creates a DMaaP manager.
         * 
         * @param props properties used to configure the manager
         * @return a new DMaaP manager
         * @throws PoolingFeatureException if an error occurs
         */
        public DmaapManager makeDmaapManager(PoolingProperties props) throws PoolingFeatureException {
            return new DmaapManager(props.getPoolingTopic(), props.getSource());
        }

        /**
         * Creates a scheduled thread pool.
         * 
         * @return a new scheduled thread pool
         */
        public ScheduledThreadPoolExecutor makeScheduler() {
            return new ScheduledThreadPoolExecutor(1);
        }

        /**
         * Determines if the event can be decoded.
         * 
         * @param drools drools controller
         * @param topic topic on which the event was received
         * @return {@code true} if the event can be decoded, {@code false} otherwise
         */
        public boolean canDecodeEvent(DroolsController drools, String topic) {
            return EventProtocolCoder.manager.isDecodingSupported(drools.getGroupId(), drools.getArtifactId(), topic);
        }

        /**
         * Decodes the event.
         * 
         * @param drools drools controller
         * @param topic topic on which the event was received
         * @param event event text to be decoded
         * @return the decoded event
         * @throws IllegalArgumentException
         * @throw UnsupportedOperationException
         * @throws IllegalStateException
         */
        public Object decodeEvent(DroolsController drools, String topic, String event) {
            return EventProtocolCoder.manager.decode(drools.getGroupId(), drools.getArtifactId(), topic, event);
        }
    }
}
