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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.onap.policy.common.utils.properties.exception.PropertyException;
import org.onap.policy.drools.event.comm.Topic.CommInfrastructure;
import org.onap.policy.drools.event.comm.TopicListener;
import org.onap.policy.drools.pooling.message.BucketAssignments;
import org.onap.policy.drools.pooling.message.Forward;
import org.onap.policy.drools.pooling.message.Message;
import org.onap.policy.drools.pooling.state.StartState;
import org.onap.policy.drools.pooling.state.State;
import org.onap.policy.drools.pooling.state.StateTimerTask;
import org.onap.policy.drools.system.PolicyController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.core.JsonProcessingException;

// TODO re-comment
// TODO comment: messages read by the controller are held in a queue
// until a bucket assignment has been made

/*
 * TODO while locked, should still forward messages. However, stuff destined
 * for this rule engine should be discarded (same behavior as
 * PolicyController.offer()).
 */

/**
 * Implementation of a {@link PoolingManager}. Until bucket assignments have
 * been made, events coming from external topics are saved in a queue for later
 * processing. Once assignments are made, the saved events are processed.
 */
public class PoolingManagerImpl implements PoolingManager, TopicListener {

    private static final Logger logger = LoggerFactory.getLogger(PoolingManagerImpl.class);

    // TODO remove constants

    // TODO metrics, audit logging

    /**
     * Maximum number of times a message can be forwarded.
     */
    private static final int MAX_HOPS = 0;

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
     * Where to offer events that have been forwarded to this host (i.e, the
     * controller).
     */
    private final TopicListener listener;

    /**
     * Used to encode & decode request objects received from & sent to a rule
     * engine.
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
     * Lock used while updating {@link #current}. In general, public methods
     * must use this, while private methods assume the lock is already held.
     */
    private final Object curLocker = new Object();

    /**
     * Current state, or {@code null} if not currently running.
     * <p>
     * This uses a finite state machine, wherein the state object contains all
     * of the data relevant to that state. Each state object has a process()
     * method, specific to each type of {@link Message} subclass. The method
     * returns the next state object, or {@code null} if the state is to remain
     * the same.
     */
    private State current = null;

    /**
     * Default handler used when {@link #current} is {@code null}.
     */
    private final State defaultHandler = new State(this) {
        @Override
        public Map<String, Object> getFilter() {
            throw new UnsupportedOperationException("default handler has no filter");
        }
    };

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
     * @param properties feature properties specific to the controller
     */
    public PoolingManagerImpl(PolicyController controller, Properties properties) {
        this.host = UUID.randomUUID().toString();
        this.controller = controller;

        try {
            this.props = new PoolingProperties(controller.getName(), properties);
            this.listener = (TopicListener) controller;
            this.serializer = new Serializer();
            this.topic = props.getPoolingTopic();
            this.eventq = new EventQueue(props.getOfflineLimit(), props.getOfflineAgeMs());
            this.dmaapMgr = new DmaapManager(topic, properties);

            logger.info("allocating host {} to controller {} for topic {}", host, controller.getName(), topic);

        } catch (ClassCastException e) {
            logger.error("not a topic listener, controller {}", controller.getName());
            throw new PoolingFeatureRtException(e);

        } catch (PoolingFeatureException e) {
            logger.error("failed to attach internal DMaaP topic to controller {}", controller.getName());
            throw e.toRuntimeException();

        } catch (PropertyException e) {
            logger.error("failed to load properties for controller {}", controller.getName());
            throw new PoolingFeatureRtException(e);
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

    @Override
    public void internalTopicFailed() {
        logger.error("communication failed for topic {}", topic);

        /*
         * We don't want to build up items in our queue if we can't forward them
         * to other hosts, so we just stop the controller.
         * 
         * Use a background thread to prevent deadlocks.
         */
        new Thread() {
            @Override
            public void run() {
                controller.stop();
            }
        }.start();
    }

    @Override
    public ScheduledFuture<?> schedule(long delayMs, StateTimerTask task) {
        return scheduler.schedule(new TimerAction(task), delayMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(long initialDelayMs, long delayMs, StateTimerTask task) {
        return scheduler.scheduleWithFixedDelay(new TimerAction(task), initialDelayMs, delayMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void publishAdmin(Message msg) {
        publish(Message.ADMIN, msg);
    }

    @Override
    public void publish(String channel, Message msg) {
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
     * Changes the finite state machine to a new state, provided both the old
     * and new states are non-null.
     * 
     * @param newState new state, or {@code null} if to remain unchanged
     */
    private void changeState(State newState) {
        /*
         * Note: have to check "current != null", too, because we may be using
         * the defaultHandler, in which case, we really don't want to change
         * states.
         */
        if (newState != null && current != null) {
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

    /**
     * Handles an event from the internal topic.
     * 
     * @param topic2
     * @param event
     * @return {@code true} if the event was handled, {@code false} if the
     *         controller should handle it
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
     * Called by the PolicyController before it offers the event to the rule
     * engine.
     * 
     * @param protocol
     * @param topic2
     * @param event
     * @return {@code true} if the event was handled by the manager,
     *         {@code false} if it must still be handled by the invoker
     */
    public boolean beforeOffer(CommInfrastructure protocol, String topic2, String event) {

        if (!intercept) {
            // we should NOT intercept this message - let the invoker handle it
            return false;
        }

        String reqid = controller.getDrools().extractRequestId(topic2, event);
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
            return true;
        }

        synchronized (curLocker) {
            return handleExternal(ev);
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
        intercept = false;
        listener.onTopicEvent(event.getProtocol(), event.getTopic(), event.getPayload());

        intercept = true;
    }

    /**
     * Handles an event from an external topic.
     * 
     * @param event
     * @return {@code true} if the event was handled, {@code false} if the
     *         invoker should handle it
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
     * Handles an event from the internal topic. This uses reflection to
     * identify the appropriate process() method to invoke, based on the type of
     * Message that was decoded.
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

            State hdlr = (current != null ? current : defaultHandler);

            Method meth = hdlr.getClass().getMethod("process", msg.getClass());
            changeState((State) meth.invoke(hdlr, msg));

        } catch (IOException e) {
            logger.warn("failed to decode message for topic {}", topic, e);

        } catch (NoSuchMethodException | SecurityException e) {
            logger.error("no processor for message {} for topic {}", clazz, topic, e);

        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            logger.error("failed to process message {} for topic {}", clazz, topic, e);

        } catch (PoolingFeatureException e) {
            logger.error("failed to process message {} for topic {}", clazz, topic, e);
        }
    }

    @Override
    public void startDistributing(BucketAssignments assignments) {
        if (assignments == null) {
            return;
        }

        synchronized (curLocker) {
            this.assignments = assignments;

            // now that we have assignments, we can process the queue
            Forward ev;
            while ((ev = eventq.poll()) != null) {
                handleEvent(ev);
            }
        }
    }

    /**
     * Handles a {@link Forward} event, possibly forwarding it again.
     * 
     * @param event
     * @return {@code true} if the event was handled, {@code false} if the
     *         invoker should handle it
     */
    private boolean handleEvent(Forward event) {
        int bucket = Math.abs(event.getRequestId().hashCode()) % assignments.size();
        String target = assignments.getAssignedHost(bucket);

        if (target == null) {
            /*
             * This bucket has no assignment - just discard the event
             */
            return true;
        }

        if (target.equals(host)) {
            /*
             * Message belongs to this host - allow the controller to handle it.
             */
            return false;
        }

        // forward to a different host
        if (event.getNumHops() > MAX_HOPS) {
            logger.warn("message discarded - hop count {} exceeded {} for topic {}", event.getNumHops(), MAX_HOPS,
                            topic);

        } else {
            event.bumpNumHops();
            publish(target, event);
        }

        return true;
    }

    /**
     * Indicates that the controller is about to start. Starts the publisher for
     * the internal topic, and creates a thread pool for the timers.
     * 
     * @throws PoolingFeatureException if the internal topic publisher cannot be
     *         started
     */
    public void beforeStart() throws PoolingFeatureException {
        synchronized (curLocker) {
            if (scheduler == null) {
                dmaapMgr.startPublisher();

                scheduler = new ScheduledThreadPoolExecutor(1);

                /*
                 * Only a handful of timers at any moment, thus we can afford to
                 * take the time to remove them when they're cancelled.
                 */
                scheduler.setRemoveOnCancelPolicy(true);
                scheduler.setMaximumPoolSize(1);
                scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
                scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
            }
        }
    }

    /**
     * Indicates that the controller has successfully started. Starts the
     * consumer for the internal topic, enters the {@link StartState}, and sets
     * the filter for the initial state.
     * 
     * @throws PoolingFeatureException if the internal topic consumer cannot be
     *         started
     */
    public void afterStart() throws PoolingFeatureException {
        synchronized (curLocker) {
            if (current == null) {
                dmaapMgr.startConsumer(this);

                current = new StartState(this);

                setFilter(current.getFilter());
                current.start();
            }
        }
    }

    /**
     * Indicates that the controller is about to stop. Stops the consumer, the
     * scheduler, and the current state.
     */
    public void beforeStop() {
        ScheduledThreadPoolExecutor sched;

        synchronized (curLocker) {
            sched = scheduler;
            scheduler = null;

            dmaapMgr.stopConsumer(this);

            if (current != null) {
                State st = current;
                current = null;

                st.stop();

                /*
                 * Need a brief delay here to allow "offline" message to be
                 * transmitted?
                 */
            }
        }

        if (sched != null) {
            sched.shutdownNow();
        }
    }

    /**
     * Indicates that the controller has stopped. Stops the publisher and logs a
     * warning if any events are still in the queue.
     */
    public void afterStop() {
        synchronized (curLocker) {
            if (!eventq.isEmpty()) {
                logger.warn("discarded {} messages after stopping topic {}", eventq.size(), topic);
            }

            dmaapMgr.stopPublisher();
        }
    }

    /**
     * Action to run a timer task. Only runs the task if the machine is still in
     * the state that it was in when the timer was created.
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
         * @param task
         */
        public TimerAction(StateTimerTask task) {
            this.origState = current;
            this.task = task;
        }

        @Override
        public void run() {
            synchronized (curLocker) {
                if (current == origState) {
                    changeState(task.fire(null));
                }
            }
        }
    }
}
