/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018-2019 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2020 Nordix Foundation
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.onap.policy.drools.pooling.PoolingProperties.PREFIX;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.Arrays;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.onap.policy.common.endpoints.event.comm.Topic;
import org.onap.policy.common.endpoints.event.comm.Topic.CommInfrastructure;
import org.onap.policy.common.endpoints.event.comm.TopicListener;
import org.onap.policy.common.endpoints.event.comm.TopicSink;
import org.onap.policy.common.endpoints.event.comm.TopicSource;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.drools.system.PolicyEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * End-to-end tests of the pooling feature. Launches one or more "hosts", each one having
 * its own feature object. Uses real feature objects. However, the following are not:
 * <dl>
 * <dt>DMaaP sources and sinks</dt>
 * <dd>simulated using queues. There is one queue for the external topic, and one queue
 * for each host's internal topic. Messages published to the "admin" channel are simply
 * sent to all of the hosts' internal topic queues</dd>
 * <dt>PolicyEngine, PolicyController, DroolsController</dt>
 * <dd>mocked</dd>
 * </dl>
 *
 * <p>Invoke {@link #runSlow()}, before the test, to slow things down.
 */

public class FeatureTest {
    private static final Logger logger = LoggerFactory.getLogger(FeatureTest.class);
    /**
     * Name of the topic used for inter-host communication.
     */
    private static final String INTERNAL_TOPIC = "my.internal.topic";
    /**
     * Name of the topic from which "external" events "arrive".
     */
    private static final String EXTERNAL_TOPIC = "my.external.topic";
    /**
     * Name of the controller.
     */
    private static final String CONTROLLER1 = "controller.one";
    private static long stdReactivateWaitMs = 200;
    private static long stdIdentificationMs = 60;
    private static long stdStartHeartbeatMs = 60;
    private static long stdActiveHeartbeatMs = 50;
    private static long stdInterHeartbeatMs = 5;
    private static long stdOfflinePubWaitMs = 2;
    private static long stdPollMs = 2;
    private static long stdInterPollMs = 2;
    private static long stdEventWaitSec = 10;
    /**
     * Used to decode events from the external topic.
     */
    private static final Gson mapper = new Gson();
    /**
     * Used to identify the current context.
     */
    private static final ThreadLocal<Context> currentContext = new ThreadLocal<Context>();
    /**
     * Context for the current test case.
     */
    private Context ctx;
    /**
     * Setup.
     */

    @Before
    public void setUp() {
        ctx = null;
    }
    /**
     * Tear down.
     */

    @After
    public void tearDown() {
        if (ctx != null) {
            ctx.destroy();
        }
    }

    @Test
    public void test_SingleHost() throws Exception {
        run(70, 1);
    }

    @Test
    public void test_TwoHosts() throws Exception {
        run(200, 2);
    }

    @Test
    public void test_ThreeHosts() throws Exception {
        run(200, 3);
    }

    private void run(int nmessages, int nhosts) throws Exception {
        ctx = new Context(nmessages);
        for (int x = 0; x < nhosts; ++x) {
            ctx.addHost();
        }
        ctx.startHosts();
        for (int x = 0; x < nmessages; ++x) {
            ctx.offerExternal(makeMessage(x));
        }
        ctx.awaitEvents(stdEventWaitSec, TimeUnit.SECONDS);
        assertEquals(0, ctx.getDecodeErrors());
        assertEquals(0, ctx.getRemainingEvents());
        ctx.checkAllSawAMsg();
    }

    private String makeMessage(int reqnum) {
        return "{\"reqid\":\"req" + reqnum + "\", \"data\":\"hello " + reqnum + "\"}";
    }
    /**
     * Invoke this to slow the timers down.
     */

    protected static void runSlow() {
        stdReactivateWaitMs = 10000;
        stdIdentificationMs = 10000;
        stdStartHeartbeatMs = 15000;
        stdActiveHeartbeatMs = 12000;
        stdInterHeartbeatMs = 5000;
        stdOfflinePubWaitMs = 2;
        stdPollMs = 2;
        stdInterPollMs = 2000;
        stdEventWaitSec = 1000;
    }
    /**
     * Decodes an event.
     *
     * @param event event
     * @return the decoded event, or {@code null} if it cannot be decoded
     */

    private static Object decodeEvent(String event) {
        try {
            return mapper.fromJson(event, TreeMap.class);
        } catch (JsonParseException e) {
            logger.warn("cannot decode external event", e);
            return null;
        }
    }
    /**
     * Context used for a single test case.
     */

    private static class Context {
        /**
         * Hosts that have been added to this context.
         */
        private final Deque<Host> hosts = new LinkedList<>();
        /**
         * Maps a drools controller to its policy controller.
         */
        private final IdentityHashMap<DroolsController, PolicyController> drools2policy = new IdentityHashMap<>();
        /**
         * Maps a channel to its queue. Does <i>not</i> include the "admin" channel.
         */
        private final ConcurrentMap<String, BlockingQueue<String>> channel2queue = new ConcurrentHashMap<>(7);
        /**
         * Counts the number of decode errors.
         */
        private final AtomicInteger numDecodeErrors = new AtomicInteger(0);
        /**
         * Number of events we're still waiting to receive.
         */
        private final CountDownLatch eventCounter;
        /**
         * The current host. Set by {@link #withHost(Host, VoidFunction)} and used by
         * {@link #getCurrentHost()}.
         */
        private Host currentHost = null;

        /**
         * Constructor.
         *
         * @param nEvents number of events to be processed
         */

        public Context(int events) {
            eventCounter = new CountDownLatch(events);
        }
        /**
         * Destroys the context, stopping any hosts that remain.
         */

        public void destroy() {
            stopHosts();
            hosts.clear();
        }

        /**
         * Creates and adds a new host to the context.
         *
         * @return the new Host
         */

        public Host addHost() {
            Host host = new Host(this);
            hosts.add(host);
            return host;
        }

        /**
         * Starts the hosts.
         */

        public void startHosts() {
            hosts.forEach(host -> host.start());
        }

        /**
         * Stops the hosts.
         */

        public void stopHosts() {
            hosts.forEach(host -> host.stop());
        }

        /**
         * Verifies that all hosts processed at least one message.
         */

        public void checkAllSawAMsg() {
            int msgs = 0;
            for (Host host : hosts) {
                assertTrue("msgs=" + msgs, host.messageSeen());
                ++msgs;
            }
        }

        /**
         * Sets {@link #currentHost} to the specified host, and then invokes the given
         * function. Resets {@link #currentHost} to {@code null} before returning.
         *
         * @param host host
         * @param func function to invoke
         */

        public void withHost(Host host, VoidFunction func) {
            currentHost = host;
            func.apply();
            currentHost = null;
        }

        /**
         * Offers an event to the external topic. As each host needs a copy, it is posted
         * to each Host's queue.
         *
         * @param event event
         */

        public void offerExternal(String event) {
            for (Host host : hosts) {
                host.getExternalTopic().offer(event);
            }
        }

        /**
         * Adds an internal channel to the set of channels.
         *
         * @param channel channel
         * @param queue the channel's queue
         */

        public void addInternal(String channel, BlockingQueue<String> queue) {
            channel2queue.put(channel, queue);
        }

        /**
         * Offers a message to all internal channels.
         *
         * @param message message
         */

        public void offerInternal(String message) {
            channel2queue.values().forEach(queue -> queue.offer(message));
        }

        /**
         * Associates a controller with its drools controller.
         *
         * @param controller controller
         * @param droolsController drools controller
         */

        public void addController(PolicyController controller, DroolsController droolsController) {
            drools2policy.put(droolsController, controller);
        }

        /**
         * Get controller.
         *
         * @param droolsController drools controller
         * @return the controller associated with a drools controller, or {@code null} if
         *         it has no associated controller
         */

        public PolicyController getController(DroolsController droolsController) {
            return drools2policy.get(droolsController);
        }

        /**
         * Get decode errors.
         *
         * @return the number of decode errors so far
         */

        public int getDecodeErrors() {
            return numDecodeErrors.get();
        }

        /**
         * Increments the count of decode errors.
         */

        public void bumpDecodeErrors() {
            numDecodeErrors.incrementAndGet();
        }

        /**
         * Get remaining events.
         *
         * @return the number of events that haven't been processed
         */

        public long getRemainingEvents() {
            return eventCounter.getCount();
        }

        /**
         * Adds an event to the counter.
         */

        public void addEvent() {
            eventCounter.countDown();
        }

        /**
         * Waits, for a period of time, for all events to be processed.
         *
         * @param time time
         * @param units units
         * @return {@code true} if all events have been processed, {@code false} otherwise
         * @throws InterruptedException throws interrupted
         */

        public boolean awaitEvents(long time, TimeUnit units) throws InterruptedException {
            return eventCounter.await(time, units);
        }

        /**
         * Gets the current host, provided this is used from within a call to
         * {@link #withHost(Host, VoidFunction)}.
         *
         * @return the current host, or {@code null} if there is no current host
         */

        public Host getCurrentHost() {
            return currentHost;
        }
    }

    /**
     * Simulates a single "host".
     */

    private static class Host {
        private final Context context;
        private final PoolingFeature feature;

        /**
         * {@code True} if this host has processed a message, {@code false} otherwise.
         */

        private final AtomicBoolean sawMsg = new AtomicBoolean(false);

        /**
         * This host's internal "DMaaP" topic.
         */

        private final BlockingQueue<String> msgQueue = new LinkedBlockingQueue<>();

        /**
         * Queue for the external "DMaaP" topic.
         */
        @Getter
        private final BlockingQueue<String> externalTopic = new LinkedBlockingQueue<String>();

        /**
         * Source that reads from the external topic and posts to the listener.
         */

        private TopicSource externalSource;

        // mock objects
        private final PolicyEngine engine = mock(PolicyEngine.class);
        private final ListenerController controller = mock(ListenerController.class);
        private final DroolsController drools = mock(DroolsController.class);

        /**
         * Constructor.
         *
         * @param context context
         */

        public Host(Context context) {
            this.context = context;
            when(controller.getName()).thenReturn(CONTROLLER1);
            when(controller.getDrools()).thenReturn(drools);
            // stop consuming events if the controller stops
            when(controller.stop()).thenAnswer(args -> {
                externalSource.unregister(controller);
                return true;
            });
            doAnswer(new MyExternalTopicListener(context, this)).when(controller).onTopicEvent(any(), any(), any());
            context.addController(controller, drools);
            // arrange to read from the external topic
            externalSource = new TopicSourceImpl(context, externalTopic);
            feature = new PoolingFeatureImpl(context);
        }

        /**
         * Get name.
         *
         * @return the host name
         */

        public String getName() {
            return feature.getHost();
        }

        /**
         * Starts threads for the host so that it begins consuming from both the external
         * "DMaaP" topic and its own internal "DMaaP" topic.
         */

        public void start() {
            context.withHost(this, () -> {
                feature.beforeStart(engine);
                feature.afterCreate(controller);
                // assign the queue for this host's internal topic
                context.addInternal(getName(), msgQueue);
                feature.beforeStart(controller);
                // start consuming events from the external topic
                externalSource.register(controller);
                feature.afterStart(controller);
            });
        }

        /**
         * Stops the host's threads.
         */

        public void stop() {
            feature.beforeStop(controller);
            externalSource.unregister(controller);
            feature.afterStop(controller);
        }

        /**
         * Offers an event to the feature, before the policy controller handles it.
         *
         * @param protocol protocol
         * @param topic2 topic
         * @param event event
         * @return {@code true} if the event was handled, {@code false} otherwise
         */

        public boolean beforeOffer(CommInfrastructure protocol, String topic2, String event) {
            return feature.beforeOffer(controller, protocol, topic2, event);
        }

        /**
         * Offers an event to the feature, after the policy controller handles it.
         *
         * @param protocol protocol
         * @param topic topic
         * @param event event
         * @param success success
         * @return {@code true} if the event was handled, {@code false} otherwise
         */

        public boolean afterOffer(CommInfrastructure protocol, String topic, String event, boolean success) {
            return feature.afterOffer(controller, protocol, topic, event, success);
        }

        /**
         * Offers an event to the feature, before the drools controller handles it.
         *
         * @param fact fact
         * @return {@code true} if the event was handled, {@code false} otherwise
         */

        public boolean beforeInsert(Object fact) {
            return feature.beforeInsert(drools, fact);
        }

        /**
         * Offers an event to the feature, after the drools controller handles it.
         *
         * @param fact fact
         * @param successInsert {@code true} if it was successfully inserted by the drools
         *        controller, {@code false} otherwise
         * @return {@code true} if the event was handled, {@code false} otherwise
         */

        public boolean afterInsert(Object fact, boolean successInsert) {
            return feature.afterInsert(drools, fact, successInsert);
        }

        /**
         * Indicates that a message was seen for this host.
         */

        public void sawMessage() {
            sawMsg.set(true);
        }

        /**
         * Message seen.
         *
         * @return {@code true} if a message was seen for this host, {@code false}
         *         otherwise
         */

        public boolean messageSeen() {
            return sawMsg.get();
        }

        /**
         * Get internal queue.
         *
         * @return the queue associated with this host's internal topic
         */

        public BlockingQueue<String> getInternalQueue() {
            return msgQueue;
        }
    }

    /**
     * Listener for the external topic. Simulates the actions taken by
     * <i>AggregatedPolicyController.onTopicEvent</i>.
     */

    private static class MyExternalTopicListener implements Answer<Void> {
        private final Context context;
        private final Host host;

        public MyExternalTopicListener(Context context, Host host) {
            this.context = context;
            this.host = host;
        }

        @Override
        public Void answer(InvocationOnMock args) throws Throwable {
            int index = 0;
            CommInfrastructure commType = args.getArgument(index++);
            String topic = args.getArgument(index++);
            String event = args.getArgument(index++);
            if (host.beforeOffer(commType, topic, event)) {
                return null;
            }
            boolean result;
            Object fact = decodeEvent(event);
            if (fact == null) {
                result = false;
                context.bumpDecodeErrors();
            } else {
                result = true;
                if (!host.beforeInsert(fact)) {
                    // feature did not handle it so we handle it here
                    host.afterInsert(fact, result);
                    host.sawMessage();
                    context.addEvent();
                }
            }
            host.afterOffer(commType, topic, event, result);
            return null;
        }
    }

    /**
     * Sink implementation that puts a message on the queue specified by the
     * <i>channel</i> embedded within the message. If it's the "admin" channel, then the
     * message is placed on all queues.
     */

    private static class TopicSinkImpl extends TopicImpl implements TopicSink {
        private final Context context;

        /**
         * Constructor.
         *
         * @param context context
         */

        public TopicSinkImpl(Context context) {
            this.context = context;
        }

        @Override
        public synchronized boolean send(String message) {
            if (!isAlive()) {
                return false;
            }
            try {
                context.offerInternal(message);
                return true;
            } catch (JsonParseException e) {
                logger.warn("could not decode message: {}", message);
                context.bumpDecodeErrors();
                return false;
            }
        }
    }

    /**
     * Source implementation that reads from a queue associated with a topic.
     */

    private static class TopicSourceImpl extends TopicImpl implements TopicSource {

        private final String topic;
        /**
         * Queue from which to retrieve messages.
         */
        private final BlockingQueue<String> queue;
        /**
         * Manages the current consumer thread. The "first" item is used as a trigger to
         * tell the thread to stop processing, while the "second" item is triggered <i>by
         * the thread</i> when it completes.
         */
        private AtomicReference<Pair<CountDownLatch, CountDownLatch>> pair = new AtomicReference<>(null);

        /**
         * Constructor.
         *
         * @param context context
         * @param externalTopic {@code true} if to read from the internal topic, {@code false}
         *        to read from the external topic
         */

        public TopicSourceImpl(Context context, BlockingQueue<String> externalTopic) {
            if (externalTopic == null) {
                this.topic = INTERNAL_TOPIC;
                this.queue = context.getCurrentHost().getInternalQueue();
            } else {
                this.topic = EXTERNAL_TOPIC;
                this.queue = externalTopic;
            }
        }

        @Override
        public String getTopic() {
            return topic;
        }

        @Override
        public boolean offer(String event) {
            throw new UnsupportedOperationException("offer topic source");
        }

        /**
         * Starts a thread that takes messages from the queue and gives them to the
         * listener. Stops the thread of any previously registered listener.
         */

        @Override
        public void register(TopicListener listener) {
            Pair<CountDownLatch, CountDownLatch> newPair = Pair.of(new CountDownLatch(1), new CountDownLatch(1));
            reregister(newPair);
            Thread thread = new Thread(() -> {
                try {
                    do {
                        processMessages(newPair.getLeft(), listener);
                    } while (!newPair.getLeft().await(stdInterPollMs, TimeUnit.MILLISECONDS));
                    logger.info("topic source thread completed");
                } catch (InterruptedException e) {
                    logger.warn("topic source thread aborted", e);
                    Thread.currentThread().interrupt();
                } catch (RuntimeException e) {
                    logger.warn("topic source thread aborted", e);
                }
                newPair.getRight().countDown();
            });
            thread.setDaemon(true);
            thread.start();
        }

        /**
         * Stops the thread of <i>any</i> currently registered listener.
         */

        @Override
        public void unregister(TopicListener listener) {
            reregister(null);
        }

        /**
         * Registers a new "pair" with this source, stopping the consumer associated with
         * any previous registration.
         *
         * @param newPair the new "pair", or {@code null} to unregister
         */

        private void reregister(Pair<CountDownLatch, CountDownLatch> newPair) {
            try {
                Pair<CountDownLatch, CountDownLatch> oldPair = pair.getAndSet(newPair);
                if (oldPair == null) {
                    if (newPair == null) {
                        // unregister was invoked twice in a row
                        logger.warn("re-unregister for topic source");
                    }
                    // no previous thread to stop
                    return;
                }
                // need to stop the previous thread
                // tell it to stop
                oldPair.getLeft().countDown();
                // wait for it to stop
                if (!oldPair.getRight().await(2, TimeUnit.SECONDS)) {
                    logger.warn("old topic registration is still running");
                }
            } catch (InterruptedException e) {
                logger.warn("old topic registration may still be running", e);
                Thread.currentThread().interrupt();
            }
            if (newPair != null) {
                // register was invoked twice in a row
                logger.warn("re-register for topic source");
            }
        }

        /**
         * Polls for messages from the topic and offers them to the listener.
         *
         * @param stopped triggered if processing should stop
         * @param listener listener
         * @throws InterruptedException throws interrupted exception
         */

        private void processMessages(CountDownLatch stopped, TopicListener listener) throws InterruptedException {
            for (int x = 0; x < 5 && stopped.getCount() > 0; ++x) {
                String msg = queue.poll(stdPollMs, TimeUnit.MILLISECONDS);
                if (msg == null) {
                    return;
                }
                listener.onTopicEvent(CommInfrastructure.UEB, topic, msg);
            }
        }
    }

    /**
     * Topic implementation. Most methods just throw
     * {@link UnsupportedOperationException}.
     */

    private static class TopicImpl implements Topic {

        /**
         * Constructor.
         */

        public TopicImpl() {
            super();
        }

        @Override
        public String getTopic() {
            return INTERNAL_TOPIC;
        }

        @Override
        public String getEffectiveTopic() {
            return INTERNAL_TOPIC;
        }

        @Override
        public CommInfrastructure getTopicCommInfrastructure() {
            throw new UnsupportedOperationException("topic protocol");
        }

        @Override
        public List<String> getServers() {
            throw new UnsupportedOperationException("topic servers");
        }

        @Override
        public String[] getRecentEvents() {
            throw new UnsupportedOperationException("topic events");
        }

        @Override
        public void register(TopicListener topicListener) {
            throw new UnsupportedOperationException("register topic");
        }

        @Override
        public void unregister(TopicListener topicListener) {
            throw new UnsupportedOperationException("unregister topic");
        }

        @Override
        public synchronized boolean start() {
            return true;
        }

        @Override
        public synchronized boolean stop() {
            return true;
        }

        @Override
        public synchronized void shutdown() {
            // do nothing
        }

        @Override
        public synchronized boolean isAlive() {
            return true;
        }

        @Override
        public boolean lock() {
            throw new UnsupportedOperationException("lock topicink");
        }

        @Override
        public boolean unlock() {
            throw new UnsupportedOperationException("unlock topic");
        }

        @Override
        public boolean isLocked() {
            throw new UnsupportedOperationException("topic isLocked");
        }
    }

    /**
     * Feature with overrides.
     */

    private static class PoolingFeatureImpl extends PoolingFeature {
        private final Context context;

        /**
         * Constructor.
         *
         * @param context context
         */

        public PoolingFeatureImpl(Context context) {
            this.context = context;
            /*
             * Note: do NOT extract anything from "context" at this point, because it
             * hasn't been fully initialized yet
             */
        }

        @Override
        public Properties getProperties(String featName) {
            Properties props = new Properties();
            props.setProperty(PoolingProperties.PROP_EXTRACTOR_PREFIX + ".java.util.Map", "${reqid}");
            props.setProperty(specialize(PoolingProperties.FEATURE_ENABLED, CONTROLLER1), "true");
            props.setProperty(specialize(PoolingProperties.POOLING_TOPIC, CONTROLLER1), INTERNAL_TOPIC);
            props.setProperty(specialize(PoolingProperties.OFFLINE_LIMIT, CONTROLLER1), "10000");
            props.setProperty(specialize(PoolingProperties.OFFLINE_AGE_MS, CONTROLLER1), "1000000");
            props.setProperty(specialize(PoolingProperties.OFFLINE_PUB_WAIT_MS, CONTROLLER1),
                            "" + stdOfflinePubWaitMs);
            props.setProperty(specialize(PoolingProperties.START_HEARTBEAT_MS, CONTROLLER1),
                            "" + stdStartHeartbeatMs);
            props.setProperty(specialize(PoolingProperties.REACTIVATE_MS, CONTROLLER1), "" + stdReactivateWaitMs);
            props.setProperty(specialize(PoolingProperties.IDENTIFICATION_MS, CONTROLLER1), "" + stdIdentificationMs);
            props.setProperty(specialize(PoolingProperties.ACTIVE_HEARTBEAT_MS, CONTROLLER1),
                            "" + stdActiveHeartbeatMs);
            props.setProperty(specialize(PoolingProperties.INTER_HEARTBEAT_MS, CONTROLLER1),
                            "" + stdInterHeartbeatMs);
            return props;
        }

        @Override
        public PolicyController getController(DroolsController droolsController) {
            return context.getController(droolsController);
        }

        /**
         * Embeds a specializer within a property name, after the prefix.
         *
         * @param propnm property name into which it should be embedded
         * @param spec specializer to be embedded
         * @return the property name, with the specializer embedded within it
         */

        private String specialize(String propnm, String spec) {
            String suffix = propnm.substring(PREFIX.length());
            return PREFIX + spec + "." + suffix;
        }

        @Override
        protected PoolingManagerImpl makeManager(String host, PolicyController controller, PoolingProperties props,
                        CountDownLatch activeLatch) {
            currentContext.set(context);
            return new PoolingManagerTest(host, controller, props, activeLatch);
        }
    }

    /**
     * Pooling Manager with overrides.
     */

    private static class PoolingManagerTest extends PoolingManagerImpl {

        /**
         * Constructor.
         *
         * @param host the host
         * @param controller the controller
         * @param props the properties
         * @param activeLatch the latch
         */

        public PoolingManagerTest(String host, PolicyController controller, PoolingProperties props,
                        CountDownLatch activeLatch) {
            super(host, controller, props, activeLatch);
        }

        @Override
        protected DmaapManager makeDmaapManager(String topic) throws PoolingFeatureException {
            return new DmaapManagerImpl(topic);
        }

        @Override
        protected boolean canDecodeEvent(DroolsController drools, String topic) {
            return true;
        }

        @Override
        protected Object decodeEventWrapper(DroolsController drools, String topic, String event) {
            return decodeEvent(event);
        }
    }

    /**
     * DMaaP Manager with overrides.
     */

    private static class DmaapManagerImpl extends DmaapManager {

        /**
         * Constructor.
         *
         * @param context this manager's context
         * @param topic the topic
         * @throws PoolingFeatureException if an error occurs
         */

        public DmaapManagerImpl(String topic) throws PoolingFeatureException {
            super(topic);
        }

        @Override
        protected List<TopicSource> getTopicSources() {
            return Arrays.asList(new TopicSourceImpl(currentContext.get(), null));
        }

        @Override
        protected List<TopicSink> getTopicSinks() {
            return Arrays.asList(new TopicSinkImpl(currentContext.get()));
        }
    }

    /**
     * Controller that also implements the {@link TopicListener} interface.
     */

    private static interface ListenerController extends PolicyController, TopicListener {
    }

    /**
     * Simple function that takes no arguments and returns nothing.
     */

    @FunctionalInterface
    private static interface VoidFunction {
        void apply();
    }
}
