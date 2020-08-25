/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018-2019 AT&T Intellectual Property. All rights reserved.
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
import org.onap.policy.common.endpoints.event.comm.Topic.CommInfrastructure;
import org.onap.policy.common.endpoints.event.comm.TopicEndpointManager;
import org.onap.policy.common.endpoints.event.comm.TopicListener;
import org.onap.policy.common.endpoints.event.comm.TopicSink;
import org.onap.policy.common.endpoints.event.comm.TopicSource;
import org.onap.policy.common.endpoints.properties.PolicyEndPointProperties;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.drools.system.PolicyEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * End-to-end tests of the pooling feature. Launches one or more "hosts", each one having its own
 * feature object. Uses real feature objects, as well as real DMaaP sources and sinks. However, the
 * following are not: <dl> <dt>PolicyEngine, PolicyController, DroolsController</dt> <dd>mocked</dd>
 * </dl>
 *
 * <p>The following fields must be set before executing this: <ul> <li>UEB_SERVERS</li>
 * <li>INTERNAL_TOPIC</li> <li>EXTERNAL_TOPIC</li> </ul>
 */
public class EndToEndFeatureTest {

    private static final Logger logger = LoggerFactory.getLogger(EndToEndFeatureTest.class);

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
    private static final String EXTERNAL_GROUP = EndToEndFeatureTest.class.getName();

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

    /**
     * Used to decode events from the external topic.
     */
    private static final Gson mapper = new Gson();

    /**
     * Used to identify the current host.
     */
    private static final ThreadLocal<Host> currentHost = new ThreadLocal<Host>();

    /**
     * Sink for external DMaaP topic.
     */
    private static TopicSink externalSink;

    /**
     * Sink for internal DMaaP topic.
     */
    private static TopicSink internalSink;

    /**
     * Context for the current test case.
     */
    private Context ctx;

    /**
     * Setup before class.
     *
     */
    @BeforeClass
    public static void setUpBeforeClass() {
        externalSink = TopicEndpointManager.getManager().addTopicSinks(makeSinkProperties(EXTERNAL_TOPIC)).get(0);
        externalSink.start();

        internalSink = TopicEndpointManager.getManager().addTopicSinks(makeSinkProperties(INTERNAL_TOPIC)).get(0);
        internalSink.start();
    }

    /**
     * Tear down after class.
     *
     */
    @AfterClass
    public static void tearDownAfterClass() {
        externalSink.stop();
        internalSink.stop();
    }

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

    /*
     * This test should only be run manually, after configuring all of the fields,
     * thus it is ignored.
     */
    @Ignore
    @Test
    public void test_SingleHost() throws Exception {    // NOSONAR
        run(70, 1);
    }

    /*
     * This test should only be run manually, after configuring all of the fields,
     * thus it is ignored.
     */
    @Ignore
    @Test
    public void test_TwoHosts() throws Exception {      // NOSONAR
        run(200, 2);
    }

    /*
     * This test should only be run manually, after configuring all of the fields,
     * thus it is ignored.
     */
    @Ignore
    @Test
    public void test_ThreeHosts() throws Exception {    // NOSONAR
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

        props.setProperty(PolicyEndPointProperties.PROPERTY_UEB_SINK_TOPICS, topic);

        props.setProperty(PolicyEndPointProperties.PROPERTY_UEB_SINK_TOPICS + "." + topic
                + PolicyEndPointProperties.PROPERTY_TOPIC_SERVERS_SUFFIX, UEB_SERVERS);
        props.setProperty(PolicyEndPointProperties.PROPERTY_UEB_SINK_TOPICS + "." + topic
                + PolicyEndPointProperties.PROPERTY_TOPIC_SINK_PARTITION_KEY_SUFFIX, "0");
        props.setProperty(PolicyEndPointProperties.PROPERTY_UEB_SINK_TOPICS + "." + topic
                + PolicyEndPointProperties.PROPERTY_MANAGED_SUFFIX, "false");

        return props;
    }

    private static Properties makeSourceProperties(String topic) {
        Properties props = new Properties();

        props.setProperty(PolicyEndPointProperties.PROPERTY_UEB_SOURCE_TOPICS, topic);

        props.setProperty(PolicyEndPointProperties.PROPERTY_UEB_SOURCE_TOPICS + "." + topic
                + PolicyEndPointProperties.PROPERTY_TOPIC_SERVERS_SUFFIX, UEB_SERVERS);
        props.setProperty(PolicyEndPointProperties.PROPERTY_UEB_SOURCE_TOPICS + "." + topic
                + PolicyEndPointProperties.PROPERTY_TOPIC_SOURCE_FETCH_LIMIT_SUFFIX, FETCH_LIMIT);
        props.setProperty(PolicyEndPointProperties.PROPERTY_UEB_SOURCE_TOPICS + "." + topic
                + PolicyEndPointProperties.PROPERTY_MANAGED_SUFFIX, "false");

        if (EXTERNAL_TOPIC.equals(topic)) {
            // consumer group is a constant
            props.setProperty(PolicyEndPointProperties.PROPERTY_UEB_SOURCE_TOPICS + "." + topic
                    + PolicyEndPointProperties.PROPERTY_TOPIC_SOURCE_CONSUMER_GROUP_SUFFIX, EXTERNAL_GROUP);

            // consumer instance is generated by the BusConsumer code
        }

        // else internal topic: feature populates info for internal topic

        return props;
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
         * Counts the number of decode errors.
         */
        private final AtomicInteger decodeErrors = new AtomicInteger(0);

        /**
         * Number of events we're still waiting to receive.
         */
        private final CountDownLatch eventCounter;

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
         * Offers an event to the external topic.
         *
         * @param event event
         */
        public void offerExternal(String event) {
            externalSink.send(event);
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
         * @return the controller associated with a drools controller, or {@code null} if it has no
         *         associated controller
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
            return decodeErrors.get();
        }

        /**
         * Increments the count of decode errors.
         */
        public void bumpDecodeErrors() {
            decodeErrors.incrementAndGet();
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
         * @throws InterruptedException throws interrupted exception
         */
        public boolean awaitEvents(long time, TimeUnit units) throws InterruptedException {
            return eventCounter.await(time, units);
        }

        /**
         * Waits, for a period of time, for all hosts to enter the Active state.
         *
         * @param timeMs maximum time to wait, in milliseconds
         * @throws InterruptedException throws interrupted exception
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

        private final PoolingFeature feature;

        /**
         * {@code True} if this host has processed a message, {@code false} otherwise.
         */
        private final AtomicBoolean sawMsg = new AtomicBoolean(false);

        private final TopicSource externalSource;
        private final TopicSource internalSource;

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

            when(controller.getName()).thenReturn(CONTROLLER1);
            when(controller.getDrools()).thenReturn(drools);

            externalSource = TopicEndpointManager.getManager().addTopicSources(makeSourceProperties(EXTERNAL_TOPIC))
                            .get(0);
            internalSource = TopicEndpointManager.getManager().addTopicSources(makeSourceProperties(INTERNAL_TOPIC))
                            .get(0);

            // stop consuming events if the controller stops
            when(controller.stop()).thenAnswer(args -> {
                externalSource.unregister(controller);
                return true;
            });

            doAnswer(new MyExternalTopicListener(context, this)).when(controller).onTopicEvent(any(), any(), any());

            context.addController(controller, drools);

            feature = new PoolingFeatureImpl(context, this);
        }

        /**
         * Waits, for a period of time, for the host to enter the Active state.
         *
         * @param timeMs time to wait, in milliseconds
         * @return {@code true} if the host entered the Active state within the given amount of
         *         time, {@code false} otherwise
         * @throws InterruptedException throws interrupted exception
         */
        public boolean awaitActive(long timeMs) throws InterruptedException {
            return feature.getActiveLatch().await(timeMs, TimeUnit.MILLISECONDS);
        }

        /**
         * Starts threads for the host so that it begins consuming from both the external "DMaaP"
         * topic and its own internal "DMaaP" topic.
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
         * @return {@code true} if a message was seen for this host, {@code false} otherwise
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
     * Feature with overrides.
     */
    private static class PoolingFeatureImpl extends PoolingFeature {

        private final Context context;
        private final Host host;

        /**
         * Constructor.
         *
         * @param context context
         */
        public PoolingFeatureImpl(Context context, Host host) {
            this.context = context;
            this.host = host;

            /*
             * Note: do NOT extract anything from "context" at this point, because it hasn't been
             * fully initialized yet
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
        protected PoolingManagerImpl makeManager(String hostName, PolicyController controller, PoolingProperties props,
                        CountDownLatch activeLatch) {

            /*
             * Set this before creating the test, because the test's superclass
             * constructor uses it before the test object has a chance to store it.
             */
            currentHost.set(host);

            return new PoolingManagerTest(hostName, controller, props, activeLatch);
        }
    }

    /**
     * Pooling Manager with overrides.
     */
    private static class PoolingManagerTest extends PoolingManagerImpl {

        /**
         * Constructor.
         *
         * @param hostName the host
         * @param controller the controller
         * @param props the properties
         * @param activeLatch the latch
         */
        public PoolingManagerTest(String hostName, PolicyController controller,
                        PoolingProperties props, CountDownLatch activeLatch) {

            super(hostName, controller, props, activeLatch);
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
         * @param topic the topic
         * @throws PoolingFeatureException if an error occurs
         */
        public DmaapManagerImpl(String topic) throws PoolingFeatureException {
            super(topic);
        }

        @Override
        protected List<TopicSource> getTopicSources() {
            Host host = currentHost.get();
            return Arrays.asList(host.internalSource, host.externalSource);
        }

        @Override
        protected List<TopicSink> getTopicSinks() {
            return Arrays.asList(internalSink, externalSink);
        }
    }

    /**
     * Controller that also implements the {@link TopicListener} interface.
     */
    private static interface ListenerController extends PolicyController, TopicListener {

    }
}
