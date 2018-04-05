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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.io.IOException;
import java.util.Arrays;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.event.comm.FilterableTopicSource;
import org.onap.policy.drools.event.comm.Topic;
import org.onap.policy.drools.event.comm.Topic.CommInfrastructure;
import org.onap.policy.drools.event.comm.TopicListener;
import org.onap.policy.drools.event.comm.TopicSink;
import org.onap.policy.drools.event.comm.TopicSource;
import org.onap.policy.drools.pooling.message.Message;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.drools.system.PolicyEngine;
import org.onap.policy.drools.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * End-to-end tests of the pooling feature. Launches one or more "hosts", each one having
 * its own feature object.
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

    // private static final long STD_HEARTBEAT_WAIT_MS = 100;
    // private static final long STD_REACTIVATE_WAIT_MS = 200;
    // private static final long STD_IDENTIFICATION_MS = 60;
    // private static final long STD_ACTIVE_HEARTBEAT_MS = 5;
    // private static final long STD_INTER_HEARTBEAT_MS = 50;
    // private static final long STD_OFFLINE_PUB_WAIT_MS = 2;
    // private static final long POLL_MS = 2;
    // private static final long INTER_POLL_MS = 2;
    // private static final long EVENT_WAIT_SEC = 5;

    // use these to slow things down
    private static final long STD_HEARTBEAT_WAIT_MS = 5000;
    private static final long STD_REACTIVATE_WAIT_MS = 10000;
    private static final long STD_IDENTIFICATION_MS = 10000;
    private static final long STD_ACTIVE_HEARTBEAT_MS = 5000;
    private static final long STD_INTER_HEARTBEAT_MS = 12000;
    private static final long STD_OFFLINE_PUB_WAIT_MS = 2;
    private static final long POLL_MS = 2;
    private static final long INTER_POLL_MS = 2000;
    private static final long EVENT_WAIT_SEC = 1000;

    // these are saved and restored on exit from this test class
    private static PoolingFeature.Factory saveFeatureFactory;
    private static PoolingManagerImpl.Factory saveManagerFactory;
    private static DmaapManager.Factory saveDmaapFactory;

    /**
     * Context for the current test case.
     */
    private Context ctx;

    @BeforeClass
    public static void setUpBeforeClass() {
        saveFeatureFactory = PoolingFeature.getFactory();
        saveManagerFactory = PoolingManagerImpl.getFactory();
        saveDmaapFactory = DmaapManager.getFactory();
    }

    @AfterClass
    public static void tearDownAfterClass() {
        PoolingFeature.setFactory(saveFeatureFactory);
        PoolingManagerImpl.setFactory(saveManagerFactory);
        DmaapManager.setFactory(saveDmaapFactory);
    }

    @Before
    public void setUp() {
        ctx = null;
    }

    @After
    public void tearDown() {
        if (ctx != null) {
            ctx.destroy();
        }
    }

    @Ignore
    @Test
    public void test_SingleHost() throws Exception {
        int nmessages = 70;

        ctx = new Context(nmessages);

        ctx.addHost();
        ctx.startHosts();

        for (int x = 0; x < nmessages; ++x) {
            ctx.offerExternal(makeMessage(x));
        }

        ctx.awaitEvents(EVENT_WAIT_SEC, TimeUnit.SECONDS);

        assertEquals(0, ctx.getDecodeErrors());
        assertEquals(0, ctx.getRemainingEvents());
        ctx.checkAllSawAMsg();
    }

    @Ignore
    @Test
    public void test_TwoHosts() throws Exception {
        int nmessages = 200;

        ctx = new Context(nmessages);

        ctx.addHost();
        ctx.addHost();
        ctx.startHosts();

        for (int x = 0; x < nmessages; ++x) {
            ctx.offerExternal(makeMessage(x));
        }

        // wait for all hosts to have time to process a few messages
        Thread.sleep(STD_ACTIVE_HEARTBEAT_MS + INTER_POLL_MS * 3);

        // pause a topic for a bit
//        ctx.pauseTopic();

        // now we'll see if it recovers

        ctx.awaitEvents(EVENT_WAIT_SEC, TimeUnit.SECONDS);

        assertEquals(0, ctx.getDecodeErrors());
        assertEquals(0, ctx.getRemainingEvents());
        ctx.checkAllSawAMsg();
    }

    private String makeMessage(int reqnum) {
        return "{\"reqid\":\"req" + reqnum + "\", \"data\":\"hello " + reqnum + "\"}";
    }

    /**
     * Context used for a single test case.
     */
    private static class Context {

        private final FeatureFactory featureFactory;
        private final ManagerFactory managerFactory;
        private final DmaapFactory dmaapFactory;

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
         * Queue for the external "DMaaP" topic.
         */
        private final BlockingQueue<String> externalTopic = new LinkedBlockingQueue<String>();

        /**
         * Counts the number of decode errors.
         */
        private final AtomicInteger nDecodeErrors = new AtomicInteger(0);

        /**
         * Number of events we're still waiting to receive.
         */
        private final CountDownLatch eventCounter;

        /**
         * Maps host name to its topic source. This must be in sorted order so we can
         * identify the source for the host with the higher name.
         */
        private TreeMap<String, TopicSourceImpl> host2topic = new TreeMap<>();

        /**
         * The current host. Set by {@link #withHost(Host, VoidFunction)} and used by
         * {@link #getCurrentHost()}.
         */
        private Host currentHost = null;

        /**
         * 
         * @param nEvents number of events to be processed
         */
        public Context(int nEvents) {
            featureFactory = new FeatureFactory(this);
            managerFactory = new ManagerFactory(this);
            dmaapFactory = new DmaapFactory(this);
            eventCounter = new CountDownLatch(nEvents);

            PoolingFeature.setFactory(featureFactory);
            PoolingManagerImpl.setFactory(managerFactory);
            DmaapManager.setFactory(dmaapFactory);
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
         */
        public void addHost() {
            hosts.add(new Host(this));
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
            int x = 0;
            for (Host host : hosts) {
                assertTrue("x=" + x, host.messageSeen());
                ++x;
            }
        }

        /**
         * Sets {@link #currentHost} to the specified host, and then invokes the given
         * function. Resets {@link #currentHost} to {@code null} before returning.
         * 
         * @param host
         * @param func function to invoke
         */
        public void withHost(Host host, VoidFunction func) {
            currentHost = host;
            func.apply();
            currentHost = null;
        }

        /**
         * Offers an event to the external topic.
         * 
         * @param event
         */
        public void offerExternal(String event) {
            externalTopic.offer(event);
        }

        /**
         * Adds an internal channel to the set of channels.
         * 
         * @param channel
         * @param queue the channel's queue
         */
        public void addInternal(String channel, BlockingQueue<String> queue) {
            channel2queue.put(channel, queue);
        }

        /**
         * Offers a message to all internal channels.
         * 
         * @param message
         */
        public void offerInternal(String message) {
            channel2queue.values().forEach(queue -> queue.offer(message));
        }

        /**
         * Offers amessage to an internal channel.
         * 
         * @param channel
         * @param message
         */
        public void offerInternal(String channel, String message) {
            BlockingQueue<String> queue = channel2queue.get(channel);
            if (queue != null) {
                queue.offer(message);
            }
        }

        /**
         * Decodes an event.
         * 
         * @param event
         * @return the decoded event, or {@code null} if it cannot be decoded
         */
        public Object decodeEvent(String event) {
            return managerFactory.decodeEvent(null, null, event);
        }

        /**
         * Associates a controller with its drools controller.
         * 
         * @param controller
         * @param droolsController
         */
        public void addController(PolicyController controller, DroolsController droolsController) {
            drools2policy.put(droolsController, controller);
        }

        /**
         * @param droolsController
         * @return the controller associated with a drools controller, or {@code null} if
         *         it has no associated controller
         */
        public PolicyController getController(DroolsController droolsController) {
            return drools2policy.get(droolsController);
        }

        /**
         * @return queue for the external topic
         */
        public BlockingQueue<String> getExternalTopic() {
            return externalTopic;
        }

        /**
         * 
         * @return the number of decode errors so far
         */
        public int getDecodeErrors() {
            return nDecodeErrors.get();
        }

        /**
         * Increments the count of decode errors.
         */
        public void bumpDecodeErrors() {
            nDecodeErrors.incrementAndGet();
        }

        /**
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
         * @param time
         * @param units
         * @return {@code true} if all events have been processed, {@code false} otherwise
         * @throws InterruptedException
         */
        public boolean awaitEvents(long time, TimeUnit units) throws InterruptedException {
            return eventCounter.await(time, units);
        }

        /**
         * Associates a host with a topic.
         * 
         * @param host
         * @param topic
         */
        public void addTopicSource(String host, TopicSourceImpl topic) {
            host2topic.put(host, topic);
        }

        /**
         * Pauses the last topic source long enough to miss a heart beat.
         */
        public void pauseTopic() {
            Entry<String, TopicSourceImpl> ent = host2topic.lastEntry();
            if (ent != null) {
                ent.getValue().pause(STD_ACTIVE_HEARTBEAT_MS);
            }
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

        private final PoolingFeature feature = new PoolingFeature();

        /**
         * {@code True} if this host has processed a message, {@code false} otherwise.
         */
        private final AtomicBoolean sawMsg = new AtomicBoolean(false);

        /**
         * This host's internal "DMaaP" topic.
         */
        private final BlockingQueue<String> msgQueue = new LinkedBlockingQueue<>();

        /**
         * Source that reads from the external topic and posts to the listener.
         */
        private TopicSource externalSource;

        // mock objects
        private final PolicyEngine engine = mock(PolicyEngine.class);
        private final ListenerController controller = mock(ListenerController.class);
        private final DroolsController drools = mock(DroolsController.class);

        /**
         * 
         * @param context
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
            externalSource = new TopicSourceImpl(context, false);
        }

        /**
         * Gets the host name. This should only be invoked within {@link #start()}.
         * 
         * @return the host name
         */
        public String getName() {
            return PoolingManagerImpl.getLastHost();
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
         * @param protocol
         * @param topic2
         * @param event
         * @return {@code true} if the event was handled, {@code false} otherwise
         */
        public boolean beforeOffer(CommInfrastructure protocol, String topic2, String event) {
            return feature.beforeOffer(controller, protocol, topic2, event);
        }

        /**
         * Offers an event to the feature, after the policy controller handles it.
         * 
         * @param protocol
         * @param topic
         * @param event
         * @param success
         * @return {@code true} if the event was handled, {@code false} otherwise
         */
        public boolean afterOffer(CommInfrastructure protocol, String topic, String event, boolean success) {

            return feature.afterOffer(controller, protocol, topic, event, success);
        }

        /**
         * Offers an event to the feature, before the drools controller handles it.
         * 
         * @param fact
         * @return {@code true} if the event was handled, {@code false} otherwise
         */
        public boolean beforeInsert(Object fact) {
            return feature.beforeInsert(drools, fact);
        }

        /**
         * Offers an event to the feature, after the drools controller handles it.
         * 
         * @param fact
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
         * 
         * @return {@code true} if a message was seen for this host, {@code false}
         *         otherwise
         */
        public boolean messageSeen() {
            return sawMsg.get();
        }

        /**
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
            int i = 0;
            CommInfrastructure commType = args.getArgument(i++);
            String topic = args.getArgument(i++);
            String event = args.getArgument(i++);

            if (host.beforeOffer(commType, topic, event)) {
                return null;
            }

            boolean result;
            Object fact = context.decodeEvent(event);

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
         * Used to decode the messages so that the channel can be extracted.
         */
        private final Serializer serializer = new Serializer();

        /**
         * 
         * @param context
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
                Message msg = serializer.decodeMsg(message);
                String channel = msg.getChannel();

                if (Message.ADMIN.equals(channel)) {
                    // add to every queue
                    context.offerInternal(message);

                } else {
                    // add to a specific queue
                    context.offerInternal(channel, message);
                }

                return true;

            } catch (IOException e) {
                logger.warn("could not decode message: {}", message);
                context.bumpDecodeErrors();
                return false;
            }
        }
    }

    /**
     * Source implementation that reads from a queue associated with a topic.
     */
    private static class TopicSourceImpl extends TopicImpl implements FilterableTopicSource {

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
         * Time, in milliseconds, to pause before polling for more messages.
         */
        private AtomicLong pauseTimeMs = new AtomicLong(0);

        /**
         * 
         * @param context
         * @param internal {@code true} if to read from the internal topic, {@code false}
         *        to read from the external topic
         */
        public TopicSourceImpl(Context context, boolean internal) {
            if (internal) {
                Host host = context.getCurrentHost();

                this.topic = INTERNAL_TOPIC;
                this.queue = host.getInternalQueue();

                context.addTopicSource(host.getName(), this);

            } else {
                this.topic = EXTERNAL_TOPIC;
                this.queue = context.getExternalTopic();
            }
        }

        @Override
        public void setFilter(String filter) {
            logger.info("topic filter set to: {}", filter);
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
            Pair<CountDownLatch, CountDownLatch> newPair = new Pair<>(new CountDownLatch(1), new CountDownLatch(1));

            reregister(newPair);

            new Thread(() -> {
                try {
                    do {
                        processMessages(newPair.first(), listener);
                    } while (!newPair.first().await(INTER_POLL_MS, TimeUnit.MILLISECONDS));

                    logger.info("topic source thread completed");

                } catch (InterruptedException e) {
                    logger.warn("topic source thread aborted", e);
                    Thread.currentThread().interrupt();

                } catch (RuntimeException e) {
                    logger.warn("topic source thread aborted", e);
                }

                newPair.second().countDown();

            }).start();
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
                oldPair.first().countDown();

                // wait for it to stop
                if (!oldPair.second().await(2, TimeUnit.SECONDS)) {
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
         * Indicates that {@link #processMessages(CountDownLatch, TopicListener)} should
         * pause a bit.
         * 
         * @param timeMs time, in milliseconds, to pause
         */
        public void pause(long timeMs) {
            pauseTimeMs.set(timeMs);
        }

        /**
         * Polls for messages from the topic and offers them to the listener. If
         * {@link #pauseTimeMs} is non-zero, then it pauses for the specified time and
         * then immediately returns.
         * 
         * @param stopped triggered if processing should stop
         * @param listener
         * @throws InterruptedException
         */
        private void processMessages(CountDownLatch stopped, TopicListener listener) throws InterruptedException {

            for (int x = 0; x < 5 && stopped.getCount() > 0; ++x) {

                long ptm = pauseTimeMs.getAndSet(0);
                if (ptm != 0) {
                    logger.warn("pause processing");
                    stopped.await(ptm, TimeUnit.MILLISECONDS);
                    return;
                }

                String msg = queue.poll(POLL_MS, TimeUnit.MILLISECONDS);
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
         * {@code True} if this topic is alive/running, {@code false} otherwise.
         */
        private boolean alive = false;

        /**
         * 
         */
        public TopicImpl() {
            super();
        }

        @Override
        public String getTopic() {
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
            if (alive) {
                throw new IllegalStateException("topic already started");
            }

            alive = true;
            return true;
        }

        @Override
        public synchronized boolean stop() {
            if (!alive) {
                throw new IllegalStateException("topic is not running");
            }

            alive = false;
            return true;
        }

        @Override
        public synchronized void shutdown() {
            alive = false;
        }

        @Override
        public synchronized boolean isAlive() {
            return alive;
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
     * Simulator for the feature-level factory.
     */
    private static class FeatureFactory extends PoolingFeature.Factory {

        private final Context context;

        /**
         * 
         * @param context
         */
        public FeatureFactory(Context context) {
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

            props.setProperty("pooling." + CONTROLLER1 + ".topic", INTERNAL_TOPIC);
            props.setProperty("pooling." + CONTROLLER1 + ".enabled", "true");
            props.setProperty("pooling." + CONTROLLER1 + ".offline.queue.limit", "10000");
            props.setProperty("pooling." + CONTROLLER1 + ".offline.queue.age.milliseconds", "1000000");
            props.setProperty("pooling." + CONTROLLER1 + ".start.heartbeat.milliseconds", "" + STD_HEARTBEAT_WAIT_MS);
            props.setProperty("pooling." + CONTROLLER1 + ".reactivate.milliseconds", "" + STD_REACTIVATE_WAIT_MS);
            props.setProperty("pooling." + CONTROLLER1 + ".identification.milliseconds", "" + STD_IDENTIFICATION_MS);
            props.setProperty("pooling." + CONTROLLER1 + ".active.heartbeat.milliseconds",
                            "" + STD_ACTIVE_HEARTBEAT_MS);
            props.setProperty("pooling." + CONTROLLER1 + ".inter.heartbeat.milliseconds", "" + STD_INTER_HEARTBEAT_MS);
            props.setProperty("pooling." + CONTROLLER1 + ".offline.publish.wait.milliseconds",
                            "" + STD_OFFLINE_PUB_WAIT_MS);

            return props;
        }

        @Override
        public PolicyController getController(DroolsController droolsController) {
            return context.getController(droolsController);
        }
    }

    /**
     * Simulator for the pooling manager factory.
     */
    private static class ManagerFactory extends PoolingManagerImpl.Factory {

        /**
         * Used to decode events from the external topic.
         */
        private final ThreadLocal<ObjectMapper> mapper = new ThreadLocal<ObjectMapper>() {
            @Override
            protected ObjectMapper initialValue() {
                return new ObjectMapper();
            }
        };

        /**
         * Used to decode events into a Map.
         */
        private final TypeReference<TreeMap<String, String>> typeRef = new TypeReference<TreeMap<String, String>>() {};

        /**
         * 
         * @param context
         */
        public ManagerFactory(Context context) {

            /*
             * Note: do NOT extract anything from "context" at this point, because it
             * hasn't been fully initialized yet
             */
        }

        @Override
        public boolean canDecodeEvent(DroolsController drools, String topic) {
            return true;
        }

        @Override
        public Object decodeEvent(DroolsController drools, String topic, String event) {
            try {
                return mapper.get().readValue(event, typeRef);

            } catch (IOException e) {
                logger.warn("cannot decode external event", e);
                return null;
            }
        }
    }

    /**
     * Simulator for the dmaap manager factory.
     */
    private static class DmaapFactory extends DmaapManager.Factory {

        private final Context context;

        /**
         * 
         * @param context
         */
        public DmaapFactory(Context context) {
            this.context = context;

            /*
             * Note: do NOT extract anything from "context" at this point, because it
             * hasn't been fully initialized yet
             */
        }

        @Override
        public List<TopicSource> initTopicSources(Properties props) {
            return Arrays.asList(new TopicSourceImpl(context, true));
        }

        @Override
        public List<TopicSink> initTopicSinks(Properties props) {
            return Arrays.asList(new TopicSinkImpl(context));
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

        public void apply();
    }
}
