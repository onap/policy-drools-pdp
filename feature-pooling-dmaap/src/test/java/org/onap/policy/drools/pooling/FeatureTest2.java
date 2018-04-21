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
import static org.onap.policy.common.utils.properties.SpecPropertyConfiguration.specialize;
import java.io.IOException;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.event.comm.Topic.CommInfrastructure;
import org.onap.policy.drools.event.comm.TopicEndpoint;
import org.onap.policy.drools.event.comm.TopicListener;
import org.onap.policy.drools.event.comm.TopicSink;
import org.onap.policy.drools.event.comm.TopicSource;
import org.onap.policy.drools.properties.PolicyProperties;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.drools.system.PolicyEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * End-to-end tests of the pooling feature. Launches one or more "hosts", each one having
 * its own feature object. Uses real feature objects, as well as real DMaaP sources and
 * sinks. However, the following are not:
 * <dl>
 * <dt>PolicyEngine, PolicyController, DroolsController</dt>
 * <dd>mocked</dd>
 * </dl>
 * 
 * <p>
 * The following fields must be set before executing this:
 * <ul>
 * <li>UEB_SERVERS</li>
 * <li>INTERNAL_TOPIC</li>
 * <li>EXTERNAL_TOPIC</li>
 * </ul>
 */
public class FeatureTest2 {

    private static final Logger logger = LoggerFactory.getLogger(FeatureTest2.class);

    /**
     * UEB servers for both internal & external topics.
     */
    private static final String UEB_SERVERS = "";

    /**
     * Name of the topic used for inter-host communication.
     */
    private static final String INTERNAL_TOPIC = "";

    /**
     * Name of the topic from which "external" events "arrive".
     */
    private static final String EXTERNAL_TOPIC = "";

    /**
     * Consumer group to use when polling the external topic.
     */
    private static final String EXTERNAL_GROUP = FeatureTest2.class.getName();

    /**
     * Name of the controller.
     */
    private static final String CONTROLLER1 = "controller.one";

    /**
     * Maximum number of items to fetch from DMaaP in a single poll.
     */
    private static final String FETCH_LIMIT = "5";

    private static final long STD_REACTIVATE_WAIT_MS = 10000;
    private static final long STD_IDENTIFICATION_MS = 10000;
    private static final long STD_START_HEARTBEAT_MS = 15000;
    private static final long STD_ACTIVE_HEARTBEAT_MS = 12000;
    private static final long STD_INTER_HEARTBEAT_MS = 5000;
    private static final long STD_OFFLINE_PUB_WAIT_MS = 2;
    private static final long EVENT_WAIT_SEC = 15;

    // these are saved and restored on exit from this test class
    private static PoolingFeature.Factory saveFeatureFactory;
    private static PoolingManagerImpl.Factory saveManagerFactory;

    /**
     * Sink for external DMaaP topic.
     */
    private static TopicSink externalSink;

    /**
     * Context for the current test case.
     */
    private Context ctx;


    @BeforeClass
    public static void setUpBeforeClass() {
        saveFeatureFactory = PoolingFeature.getFactory();
        saveManagerFactory = PoolingManagerImpl.getFactory();

        Properties props = makeSinkProperties(EXTERNAL_TOPIC);
        externalSink = TopicEndpoint.manager.addTopicSinks(props).get(0);
        externalSink.start();
    }

    @AfterClass
    public static void tearDownAfterClass() {
        PoolingFeature.setFactory(saveFeatureFactory);
        PoolingManagerImpl.setFactory(saveManagerFactory);

        externalSink.stop();
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
        run(70, 1);
    }

    @Ignore
    @Test
    public void test_TwoHosts() throws Exception {
        run(200, 2);
    }

    @Ignore
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
        ctx.awaitAllActive(STD_IDENTIFICATION_MS * 2);

        for (int x = 0; x < nmessages; ++x) {
            ctx.offerExternal(makeMessage(x));
        }

        ctx.awaitEvents(EVENT_WAIT_SEC, TimeUnit.SECONDS);

        assertEquals(0, ctx.getDecodeErrors());
        assertEquals(0, ctx.getRemainingEvents());
        ctx.checkAllSawAMsg();
    }

    private String makeMessage(int reqnum) {
        return "{\"reqid\":\"req" + reqnum + "\", \"data\":\"hello " + reqnum + "\"}";
    }

    private static Properties makeSinkProperties(String topic) {
        Properties props = new Properties();

        props.setProperty(PolicyProperties.PROPERTY_UEB_SINK_TOPICS, topic);

        props.setProperty(PolicyProperties.PROPERTY_UEB_SINK_TOPICS + "." + topic
                        + PolicyProperties.PROPERTY_TOPIC_SERVERS_SUFFIX, UEB_SERVERS);
        props.setProperty(PolicyProperties.PROPERTY_UEB_SINK_TOPICS + "." + topic
                        + PolicyProperties.PROPERTY_TOPIC_SINK_PARTITION_KEY_SUFFIX, "0");
        props.setProperty(PolicyProperties.PROPERTY_UEB_SINK_TOPICS + "." + topic
                        + PolicyProperties.PROPERTY_MANAGED_SUFFIX, "false");

        return props;
    }

    private static Properties makeSourceProperties(String topic) {
        Properties props = new Properties();

        props.setProperty(PolicyProperties.PROPERTY_UEB_SOURCE_TOPICS, topic);

        props.setProperty(PolicyProperties.PROPERTY_UEB_SOURCE_TOPICS + "." + topic
                        + PolicyProperties.PROPERTY_TOPIC_SERVERS_SUFFIX, UEB_SERVERS);
        props.setProperty(PolicyProperties.PROPERTY_UEB_SOURCE_TOPICS + "." + topic
                        + PolicyProperties.PROPERTY_TOPIC_SOURCE_FETCH_LIMIT_SUFFIX, FETCH_LIMIT);
        props.setProperty(PolicyProperties.PROPERTY_UEB_SOURCE_TOPICS + "." + topic
                        + PolicyProperties.PROPERTY_MANAGED_SUFFIX, "false");

        if (EXTERNAL_TOPIC.equals(topic)) {
            // consumer group is a constant
            props.setProperty(
                            PolicyProperties.PROPERTY_UEB_SOURCE_TOPICS + "." + topic
                                            + PolicyProperties.PROPERTY_TOPIC_SOURCE_CONSUMER_GROUP_SUFFIX,
                            EXTERNAL_GROUP);

            // consumer instance is generated by the BusConsumer code
        }

        // else internal topic: feature populates info for internal topic

        return props;
    }

    /**
     * Context used for a single test case.
     */
    private static class Context {

        private final FeatureFactory featureFactory;
        private final ManagerFactory managerFactory;

        /**
         * Hosts that have been added to this context.
         */
        private final Deque<Host> hosts = new LinkedList<>();

        /**
         * Maps a drools controller to its policy controller.
         */
        private final IdentityHashMap<DroolsController, PolicyController> drools2policy = new IdentityHashMap<>();

        /**
         * Counts the number of decode errors.
         */
        private final AtomicInteger nDecodeErrors = new AtomicInteger(0);

        /**
         * Number of events we're still waiting to receive.
         */
        private final CountDownLatch eventCounter;

        /**
         * 
         * @param nEvents number of events to be processed
         */
        public Context(int nEvents) {
            featureFactory = new FeatureFactory(this);
            managerFactory = new ManagerFactory(this);
            eventCounter = new CountDownLatch(nEvents);

            PoolingFeature.setFactory(featureFactory);
            PoolingManagerImpl.setFactory(managerFactory);
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
            int x = 0;
            for (Host host : hosts) {
                assertTrue("x=" + x, host.messageSeen());
                ++x;
            }
        }

        /**
         * Offers an event to the external topic.
         * 
         * @param event
         */
        public void offerExternal(String event) {
            externalSink.send(event);
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
         * Waits, for a period of time, for all hosts to enter the Active state.
         * 
         * @param timeMs maximum time to wait, in milliseconds
         * @throws InterruptedException
         */
        public void awaitAllActive(long timeMs) throws InterruptedException {
            long tend = timeMs + System.currentTimeMillis();

            for (Host host : hosts) {
                long tremain = Math.max(0, tend - System.currentTimeMillis());
                assertTrue(host.awaitActive(tremain));
            }
        }
    }

    /**
     * Simulates a single "host".
     */
    private static class Host {

        private final PoolingFeature feature = new PoolingFeature();

        /**
         * {@code True} if this host has processed a message, {@code false} otherwise.
         */
        private final AtomicBoolean sawMsg = new AtomicBoolean(false);

        private final TopicSource externalSource;

        // mock objects
        private final PolicyEngine engine = mock(PolicyEngine.class);
        private final ListenerController controller = mock(ListenerController.class);
        private final DroolsController drools = mock(DroolsController.class);

        /**
         * 
         * @param context
         */
        public Host(Context context) {

            when(controller.getName()).thenReturn(CONTROLLER1);
            when(controller.getDrools()).thenReturn(drools);

            Properties props = makeSourceProperties(EXTERNAL_TOPIC);
            externalSource = TopicEndpoint.manager.addTopicSources(props).get(0);

            // stop consuming events if the controller stops
            when(controller.stop()).thenAnswer(args -> {
                externalSource.unregister(controller);
                return true;
            });

            doAnswer(new MyExternalTopicListener(context, this)).when(controller).onTopicEvent(any(), any(), any());

            context.addController(controller, drools);
        }

        /**
         * Waits, for a period of time, for the host to enter the Active state.
         * 
         * @param timeMs time to wait, in milliseconds
         * @return {@code true} if the host entered the Active state within the given
         *         amount of time, {@code false} otherwise
         * @throws InterruptedException
         */
        public boolean awaitActive(long timeMs) throws InterruptedException {
            return feature.getActiveLatch().await(timeMs, TimeUnit.MILLISECONDS);
        }

        /**
         * Starts threads for the host so that it begins consuming from both the external
         * "DMaaP" topic and its own internal "DMaaP" topic.
         */
        public void start() {
            feature.beforeStart(engine);
            feature.afterCreate(controller);

            feature.beforeStart(controller);

            // start consuming events from the external topic
            externalSource.register(controller);

            feature.afterStart(controller);
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

            props.setProperty(specialize(PoolingProperties.FEATURE_ENABLED, CONTROLLER1), "true");
            props.setProperty(specialize(PoolingProperties.POOLING_TOPIC, CONTROLLER1), INTERNAL_TOPIC);
            props.setProperty(specialize(PoolingProperties.OFFLINE_LIMIT, CONTROLLER1), "10000");
            props.setProperty(specialize(PoolingProperties.OFFLINE_AGE_MS, CONTROLLER1), "1000000");
            props.setProperty(specialize(PoolingProperties.OFFLINE_PUB_WAIT_MS, CONTROLLER1),
                            "" + STD_OFFLINE_PUB_WAIT_MS);
            props.setProperty(specialize(PoolingProperties.START_HEARTBEAT_MS, CONTROLLER1),
                            "" + STD_START_HEARTBEAT_MS);
            props.setProperty(specialize(PoolingProperties.REACTIVATE_MS, CONTROLLER1), "" + STD_REACTIVATE_WAIT_MS);
            props.setProperty(specialize(PoolingProperties.IDENTIFICATION_MS, CONTROLLER1), "" + STD_IDENTIFICATION_MS);
            props.setProperty(specialize(PoolingProperties.ACTIVE_HEARTBEAT_MS, CONTROLLER1),
                            "" + STD_ACTIVE_HEARTBEAT_MS);
            props.setProperty(specialize(PoolingProperties.INTER_HEARTBEAT_MS, CONTROLLER1),
                            "" + STD_INTER_HEARTBEAT_MS);

            props.putAll(makeSinkProperties(INTERNAL_TOPIC));
            props.putAll(makeSourceProperties(INTERNAL_TOPIC));

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
     * Controller that also implements the {@link TopicListener} interface.
     */
    private static interface ListenerController extends PolicyController, TopicListener {

    }
}
