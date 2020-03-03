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

package org.onap.policy.drools.system;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.common.endpoints.event.comm.Topic.CommInfrastructure;
import org.onap.policy.common.endpoints.event.comm.TopicEndpoint;
import org.onap.policy.common.endpoints.event.comm.TopicSink;
import org.onap.policy.common.endpoints.event.comm.TopicSource;
import org.onap.policy.common.endpoints.http.client.HttpClient;
import org.onap.policy.common.endpoints.http.client.HttpClientFactory;
import org.onap.policy.common.endpoints.http.server.HttpServletServer;
import org.onap.policy.common.endpoints.http.server.HttpServletServerFactory;
import org.onap.policy.common.utils.gson.GsonTestUtils;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.core.lock.Lock;
import org.onap.policy.drools.core.lock.LockCallback;
import org.onap.policy.drools.core.lock.PolicyResourceLockManager;
import org.onap.policy.drools.features.PolicyControllerFeatureApi;
import org.onap.policy.drools.features.PolicyEngineFeatureApi;
import org.onap.policy.drools.persistence.SystemPersistence;
import org.onap.policy.drools.properties.DroolsPropertyConstants;
import org.onap.policy.drools.protocol.coders.EventProtocolCoder;
import org.onap.policy.drools.protocol.configuration.ControllerConfiguration;
import org.onap.policy.drools.protocol.configuration.DroolsConfiguration;
import org.onap.policy.drools.protocol.configuration.PdpdConfiguration;
import org.onap.policy.drools.system.internal.SimpleLockManager;
import org.onap.policy.drools.system.internal.SimpleLockProperties;

public class PolicyEngineManagerTest {
    private static final String EXPECTED = "expected exception";

    private static final String NOOP_STR = CommInfrastructure.NOOP.name();
    private static final String MY_NAME = "my-name";
    private static final String CONTROLLER1 = "controller-a";
    private static final String CONTROLLER2 = "controller-b";
    private static final String CONTROLLER3 = "controller-c";
    private static final String CONTROLLER4 = "controller-d";
    private static final String FEATURE1 = "feature-a";
    private static final String FEATURE2 = "feature-b";
    private static final String MY_TOPIC = "my-topic";
    private static final String MESSAGE = "my-message";
    private static final String MY_OWNER = "my-owner";
    private static final String MY_RESOURCE = "my-resource";

    private static final Object MY_EVENT = new Object();

    private static final GsonTestUtils gson = new GsonMgmtTestBuilder().addTopicSourceMock().addTopicSinkMock()
                    .addHttpServletServerMock().build();

    private Properties properties;
    private PolicyEngineFeatureApi prov1;
    private PolicyEngineFeatureApi prov2;
    private List<PolicyEngineFeatureApi> providers;
    private PolicyControllerFeatureApi contProv1;
    private PolicyControllerFeatureApi contProv2;
    private List<PolicyControllerFeatureApi> contProviders;
    private String[] globalInitArgs;
    private TopicSource source1;
    private TopicSource source2;
    private List<TopicSource> sources;
    private TopicSink sink1;
    private TopicSink sink2;
    private List<TopicSink> sinks;
    private HttpServletServer server1;
    private HttpServletServer server2;
    private List<HttpServletServer> servers;
    private HttpServletServerFactory serverFactory;
    private HttpClientFactory clientFactory;
    private HttpClient client1;
    private HttpClient client2;
    private List<HttpClient> clients;
    private TopicEndpoint endpoint;
    private PolicyController controller;
    private PolicyController controller2;
    private PolicyController controller3;
    private PolicyController controller4;
    private List<PolicyController> controllers;
    private PolicyControllerFactory controllerFactory;
    private boolean jmxStarted;
    private boolean jmxStopped;
    private long threadSleepMs;
    private int threadExitCode;
    private boolean threadStarted;
    private boolean threadInterrupted;
    private Thread shutdownThread;
    private boolean shouldInterrupt;
    private EventProtocolCoder coder;
    private SystemPersistence persist;
    private PolicyEngine engine;
    private DroolsConfiguration drools3;
    private DroolsConfiguration drools4;
    private ControllerConfiguration config3;
    private ControllerConfiguration config4;
    private PdpdConfiguration pdpConfig;
    private String pdpConfigJson;
    private PolicyEngineManager mgr;
    private ScheduledExecutorService exsvc;
    private PolicyResourceLockManager lockmgr;

    /**
     * Initializes the object to be tested.
     *
     * @throws Exception if an error occurs
     */
    @Before
    public void setUp() throws Exception {

        properties = new Properties();
        prov1 = mock(PolicyEngineFeatureApi.class);
        prov2 = mock(PolicyEngineFeatureApi.class);
        providers = Arrays.asList(prov1, prov2);
        contProv1 = mock(PolicyControllerFeatureApi.class);
        contProv2 = mock(PolicyControllerFeatureApi.class);
        contProviders = Arrays.asList(contProv1, contProv2);
        globalInitArgs = null;
        source1 = mock(TopicSource.class);
        source2 = mock(TopicSource.class);
        sources = Arrays.asList(source1, source2);
        sink1 = mock(TopicSink.class);
        sink2 = mock(TopicSink.class);
        sinks = Arrays.asList(sink1, sink2);
        server1 = mock(HttpServletServer.class);
        server2 = mock(HttpServletServer.class);
        servers = Arrays.asList(server1, server2);
        serverFactory = mock(HttpServletServerFactory.class);
        client1 = mock(HttpClient.class);
        client2 = mock(HttpClient.class);
        clients = Arrays.asList(client1, client2);
        clientFactory = mock(HttpClientFactory.class);
        endpoint = mock(TopicEndpoint.class);
        controller = mock(PolicyController.class);
        controller2 = mock(PolicyController.class);
        controller3 = mock(PolicyController.class);
        controller4 = mock(PolicyController.class);
        // do NOT include controller3 or controller4 in the list
        controllers = Arrays.asList(controller, controller2);
        controllerFactory = mock(PolicyControllerFactory.class);
        jmxStarted = false;
        jmxStopped = false;
        threadSleepMs = -1;
        threadExitCode = -1;
        threadStarted = false;
        threadInterrupted = false;
        shutdownThread = null;
        shouldInterrupt = false;
        coder = mock(EventProtocolCoder.class);
        persist = mock(SystemPersistence.class);
        engine = mock(PolicyEngine.class);
        drools3 = new DroolsConfiguration();
        drools4 = new DroolsConfiguration();
        config3 = new ControllerConfiguration();
        config4 = new ControllerConfiguration();
        pdpConfig = new PdpdConfiguration();
        exsvc = mock(ScheduledExecutorService.class);
        lockmgr = mock(PolicyResourceLockManager.class);

        when(lockmgr.start()).thenReturn(true);
        when(lockmgr.stop()).thenReturn(true);
        when(lockmgr.lock()).thenReturn(true);
        when(lockmgr.unlock()).thenReturn(true);

        when(prov2.beforeCreateLockManager(any(), any())).thenReturn(lockmgr);

        when(prov1.getName()).thenReturn(FEATURE1);
        when(prov2.getName()).thenReturn(FEATURE2);

        when(controllerFactory.build(any(), any())).thenReturn(controller);
        when(controllerFactory.inventory()).thenReturn(controllers);
        when(controllerFactory.get(CONTROLLER1)).thenReturn(controller);
        when(controllerFactory.get(CONTROLLER2)).thenReturn(controller2);
        // do NOT return controller3 or controller4

        when(server1.getPort()).thenReturn(1001);
        when(server1.waitedStart(anyLong())).thenReturn(true);
        when(server1.stop()).thenReturn(true);

        when(server2.getPort()).thenReturn(1002);
        when(server2.waitedStart(anyLong())).thenReturn(true);
        when(server2.stop()).thenReturn(true);

        when(serverFactory.build(any())).thenReturn(servers);

        when(client1.getPort()).thenReturn(2001);
        when(client1.start()).thenReturn(true);
        when(client1.stop()).thenReturn(true);

        when(client2.getPort()).thenReturn(2002);
        when(client2.start()).thenReturn(true);
        when(client2.stop()).thenReturn(true);

        when(clientFactory.inventory()).thenReturn(clients);

        when(source1.getTopic()).thenReturn("source1-topic");
        when(source1.start()).thenReturn(true);
        when(source1.stop()).thenReturn(true);

        when(source2.getTopic()).thenReturn("source2-topic");
        when(source2.start()).thenReturn(true);
        when(source2.stop()).thenReturn(true);

        when(sink1.getTopic()).thenReturn("sink1-topic");
        when(sink1.start()).thenReturn(true);
        when(sink1.stop()).thenReturn(true);
        when(sink1.send(any())).thenReturn(true);
        when(sink1.getTopicCommInfrastructure()).thenReturn(CommInfrastructure.NOOP);

        when(sink2.getTopic()).thenReturn("sink2-topic");
        when(sink2.start()).thenReturn(true);
        when(sink2.stop()).thenReturn(true);

        when(controller.getName()).thenReturn(CONTROLLER1);
        when(controller.start()).thenReturn(true);
        when(controller.stop()).thenReturn(true);
        when(controller.lock()).thenReturn(true);
        when(controller.unlock()).thenReturn(true);

        when(controller2.getName()).thenReturn(CONTROLLER2);
        when(controller2.start()).thenReturn(true);
        when(controller2.stop()).thenReturn(true);
        when(controller2.lock()).thenReturn(true);
        when(controller2.unlock()).thenReturn(true);

        when(controller3.getName()).thenReturn(CONTROLLER3);
        when(controller3.start()).thenReturn(true);
        when(controller3.stop()).thenReturn(true);
        when(controller3.lock()).thenReturn(true);
        when(controller3.unlock()).thenReturn(true);

        when(controller4.getName()).thenReturn(CONTROLLER4);
        when(controller4.start()).thenReturn(true);
        when(controller4.stop()).thenReturn(true);
        when(controller4.lock()).thenReturn(true);
        when(controller4.unlock()).thenReturn(true);

        when(endpoint.addTopicSources(any(Properties.class))).thenReturn(sources);
        when(endpoint.addTopicSinks(any(Properties.class))).thenReturn(sinks);
        when(endpoint.start()).thenReturn(true);
        when(endpoint.stop()).thenReturn(true);
        when(endpoint.lock()).thenReturn(true);
        when(endpoint.unlock()).thenReturn(true);
        when(endpoint.getTopicSink(CommInfrastructure.NOOP, MY_TOPIC)).thenReturn(sink1);
        when(endpoint.getTopicSinks(MY_TOPIC)).thenReturn(Arrays.asList(sink1));

        when(coder.encode(any(), any())).thenReturn(MESSAGE);

        when(persist.getControllerProperties(CONTROLLER3)).thenReturn(properties);
        when(persist.getControllerProperties(CONTROLLER4)).thenReturn(properties);

        when(engine.createPolicyController(CONTROLLER3, properties)).thenReturn(controller3);
        when(engine.createPolicyController(CONTROLLER4, properties)).thenReturn(controller4);

        config3.setName(CONTROLLER3);
        config3.setOperation(ControllerConfiguration.CONFIG_CONTROLLER_OPERATION_CREATE);
        config3.setDrools(drools3);

        config4.setName(CONTROLLER4);
        config4.setOperation(ControllerConfiguration.CONFIG_CONTROLLER_OPERATION_UPDATE);
        config4.setDrools(drools4);

        pdpConfig.getControllers().add(config3);
        pdpConfig.getControllers().add(config4);
        pdpConfig.setEntity(PdpdConfiguration.CONFIG_ENTITY_CONTROLLER);

        pdpConfigJson = gson.gsonEncode(pdpConfig);

        mgr = new PolicyEngineManagerImpl();
    }

    @Test
    public void testSerialize() {
        mgr.configure(properties);
        gson.compareGson(mgr, PolicyEngineManagerTest.class);
    }

    @Test
    public void testFactory() {
        mgr = new PolicyEngineManager();

        assertNotNull(mgr.getEngineProviders());
        assertNotNull(mgr.getControllerProviders());
        assertNotNull(mgr.getTopicEndpointManager());
        assertNotNull(mgr.getServletFactory());
        assertNotNull(mgr.getControllerFactory());
        assertNotNull(mgr.makeShutdownThread());
        assertNotNull(mgr.getProtocolCoder());
        assertNotNull(mgr.getPersistenceManager());
        assertNotNull(mgr.getPolicyEngine());
    }

    @Test
    public void testBoot() throws Exception {
        String[] args = {"boot-a", "boot-b"};

        // arrange for first provider to throw exceptions
        when(prov1.beforeBoot(mgr, args)).thenThrow(new RuntimeException(EXPECTED));
        when(prov1.afterBoot(mgr)).thenThrow(new RuntimeException(EXPECTED));

        mgr.boot(args);

        verify(prov1).beforeBoot(mgr, args);
        verify(prov2).beforeBoot(mgr, args);

        assertTrue(globalInitArgs == args);

        verify(prov1).afterBoot(mgr);
        verify(prov2).afterBoot(mgr);

        // global init throws exception - still calls afterBoot
        setUp();
        mgr = new PolicyEngineManagerImpl() {
            @Override
            protected void globalInitContainer(String[] cliArgs) {
                throw new RuntimeException(EXPECTED);
            }
        };
        mgr.boot(args);
        verify(prov2).afterBoot(mgr);

        // other tests
        checkBeforeAfter(
            (prov, flag) -> when(prov.beforeBoot(mgr, args)).thenReturn(flag),
            (prov, flag) -> when(prov.afterBoot(mgr)).thenReturn(flag),
            () -> mgr.boot(args),
            prov -> verify(prov).beforeBoot(mgr, args),
            () -> assertTrue(globalInitArgs == args),
            prov -> verify(prov).afterBoot(mgr));
    }

    @Test
    public void testSetEnvironment_testGetEnvironment_testGetEnvironmentProperty_setEnvironmentProperty() {
        Properties props1 = new Properties();
        props1.put("prop1-a", "value1-a");
        props1.put("prop1-b", "value1-b");

        mgr.setEnvironment(props1);

        Properties env = mgr.getEnvironment();
        assertEquals(props1, env);

        // add more properties
        Properties props2 = new Properties();
        String propKey = "prop2-a";
        props2.put(propKey, "value2-a");
        props2.put("prop2-b", "value2-b");

        mgr.setEnvironment(props2);

        assertTrue(mgr.getEnvironment() == env);

        // new env should have a union of the properties
        props1.putAll(props2);
        assertEquals(props1, env);

        assertEquals("value2-a", mgr.getEnvironmentProperty(propKey));

        String newValue = "new-value";
        mgr.setEnvironmentProperty(propKey, newValue);
        assertEquals(newValue, mgr.getEnvironmentProperty(propKey));

        props1.setProperty(propKey, newValue);
        assertEquals(props1, env);

        assertNotNull(mgr.getEnvironmentProperty("PATH"));
        assertNull(mgr.getEnvironmentProperty("unknown-env-property"));

        System.setProperty("propS-a", "valueS-a");
        assertEquals("valueS-a", mgr.getEnvironmentProperty("propS-a"));

        Properties props3 = new Properties();
        props3.put("prop3-a", "${env:HOME}");
        mgr.setEnvironment(props3);
        assertEquals(System.getenv("HOME"), mgr.getEnvironmentProperty("prop3-a"));
        assertEquals("valueS-a", mgr.getEnvironmentProperty("propS-a"));
        assertEquals(newValue, mgr.getEnvironmentProperty(propKey));
    }

    @Test
    public void testDefaultTelemetryConfig() {
        Properties config = mgr.defaultTelemetryConfig();
        assertNotNull(config);
        assertFalse(config.isEmpty());
    }

    /**
     * Tests that makeExecutorService() uses the value from the thread
     * property.
     */
    @Test
    public void testMakeExecutorServicePropertyProvided() {
        PolicyEngineManager mgrspy = spy(mgr);

        properties.setProperty(PolicyEngineManager.EXECUTOR_THREAD_PROP, "3");
        mgrspy.configure(properties);
        assertSame(exsvc, mgrspy.getExecutorService());
        verify(mgrspy).makeScheduledExecutor(3);
    }

    /**
     * Tests that makeExecutorService() uses the default thread count when no thread
     * property is provided.
     */
    @Test
    public void testMakeExecutorServiceNoProperty() {
        PolicyEngineManager mgrspy = spy(mgr);

        mgrspy.configure(properties);
        assertSame(exsvc, mgrspy.getExecutorService());
        verify(mgrspy).makeScheduledExecutor(PolicyEngineManager.DEFAULT_EXECUTOR_THREADS);
    }

    /**
     * Tests that makeExecutorService() uses the default thread count when the thread
     * property is invalid.
     */
    @Test
    public void testMakeExecutorServiceInvalidProperty() {
        PolicyEngineManager mgrspy = spy(mgr);

        properties.setProperty(PolicyEngineManager.EXECUTOR_THREAD_PROP, "abc");
        mgrspy.configure(properties);
        assertSame(exsvc, mgrspy.getExecutorService());
        verify(mgrspy).makeScheduledExecutor(PolicyEngineManager.DEFAULT_EXECUTOR_THREADS);
    }

    /**
     * Tests createLockManager() when beforeCreateLock throws an exception and returns a
     * manager.
     */
    @Test
    public void testCreateLockManagerHaveProvider() {
        // first provider throws an exception
        when(prov1.beforeCreateLockManager(any(), any())).thenThrow(new RuntimeException(EXPECTED));

        mgr.configure(properties);
        assertSame(lockmgr, mgr.getLockManager());
    }

    /**
     * Tests createLockManager() when SimpleLockManager throws an exception.
     */
    @Test
    public void testCreateLockManagerSimpleEx() {
        when(prov2.beforeCreateLockManager(any(), any())).thenReturn(null);

        // invalid property for SimpleLockManager
        properties.setProperty(SimpleLockProperties.EXPIRE_CHECK_SEC, "abc");
        mgr.configure(properties);

        // should create a manager using default properties
        assertTrue(mgr.getLockManager() instanceof SimpleLockManager);
    }

    /**
     * Tests createLockManager() when SimpleLockManager is returned.
     */
    @Test
    public void testCreateLockManagerSimple() {
        when(prov2.beforeCreateLockManager(any(), any())).thenReturn(null);

        mgr.configure(properties);
        assertTrue(mgr.getLockManager() instanceof SimpleLockManager);
    }

    @Test
    public void testConfigureProperties() throws Exception {
        // arrange for first provider to throw exceptions
        when(prov1.beforeConfigure(mgr, properties)).thenThrow(new RuntimeException(EXPECTED));
        when(prov1.afterConfigure(mgr)).thenThrow(new RuntimeException(EXPECTED));

        mgr.configure(properties);

        verify(prov1).beforeConfigure(mgr, properties);
        verify(prov2).beforeConfigure(mgr, properties);

        assertTrue(mgr.getProperties() == properties);

        assertEquals(sources, mgr.getSources());
        assertEquals(sinks, mgr.getSinks());
        assertEquals(servers, mgr.getHttpServers());

        verify(source1).register(mgr);
        verify(source2).register(mgr);

        verify(prov1).afterConfigure(mgr);
        verify(prov2).afterConfigure(mgr);

        // middle stuff throws exception - still calls afterXxx
        setUp();
        when(endpoint.addTopicSources(properties)).thenThrow(new IllegalArgumentException(EXPECTED));
        when(endpoint.addTopicSinks(properties)).thenThrow(new IllegalArgumentException(EXPECTED));
        when(serverFactory.build(properties)).thenThrow(new IllegalArgumentException(EXPECTED));
        when(clientFactory.build(properties)).thenThrow(new IllegalArgumentException(EXPECTED));
        mgr.configure(properties);
        verify(prov2).afterConfigure(mgr);

        // null properties - nothing should be invoked
        setUp();
        Properties nullProps = null;
        assertThatIllegalArgumentException().isThrownBy(() -> mgr.configure(nullProps));
        verify(prov1, never()).beforeConfigure(mgr, properties);
        verify(prov1, never()).afterConfigure(mgr);

        // other tests
        checkBeforeAfter(
            (prov, flag) -> when(prov.beforeConfigure(mgr, properties)).thenReturn(flag),
            (prov, flag) -> when(prov.afterConfigure(mgr)).thenReturn(flag),
            () -> mgr.configure(properties),
            prov -> verify(prov).beforeConfigure(mgr, properties),
            () -> assertTrue(mgr.getProperties() == properties),
            prov -> verify(prov).afterConfigure(mgr));
    }

    @Test
    public void testConfigurePdpdConfiguration() throws Exception {
        mgr.configure(properties);
        assertTrue(mgr.configure(pdpConfig));

        verify(controllerFactory).patch(controller3, drools3);
        verify(controllerFactory).patch(controller4, drools4);

        // invalid params
        PdpdConfiguration nullConfig = null;
        assertThatIllegalArgumentException().isThrownBy(() -> mgr.configure(nullConfig));

        pdpConfig.setEntity("unknown-entity");
        assertThatIllegalArgumentException().isThrownBy(() -> mgr.configure(pdpConfig));

        // source list of size 1
        setUp();
        when(endpoint.addTopicSources(any(Properties.class))).thenReturn(Arrays.asList(source1));
        mgr.configure(properties);
        assertTrue(mgr.configure(pdpConfig));

        verify(controllerFactory).patch(controller3, drools3);
        verify(controllerFactory).patch(controller4, drools4);
    }

    @Test
    public void testCreatePolicyController() throws Exception {
        assertEquals(controller, mgr.createPolicyController(MY_NAME, properties));

        verify(contProv1).beforeCreate(MY_NAME, properties);
        verify(contProv2).beforeCreate(MY_NAME, properties);
        verify(controller, never()).lock();
        verify(contProv1).afterCreate(controller);
        verify(contProv2).afterCreate(controller);

        // first provider throws exceptions - same result
        setUp();
        when(contProv1.beforeCreate(MY_NAME, properties)).thenThrow(new RuntimeException(EXPECTED));
        when(contProv1.afterCreate(controller)).thenThrow(new RuntimeException(EXPECTED));
        assertEquals(controller, mgr.createPolicyController(MY_NAME, properties));

        verify(contProv1).beforeCreate(MY_NAME, properties);
        verify(contProv2).beforeCreate(MY_NAME, properties);
        verify(controller, never()).lock();
        verify(contProv1).afterCreate(controller);
        verify(contProv2).afterCreate(controller);

        // locked - same result, but engine locked
        setUp();
        mgr.lock();
        assertEquals(controller, mgr.createPolicyController(MY_NAME, properties));
        verify(contProv1).beforeCreate(MY_NAME, properties);
        verify(controller, times(2)).lock();
        verify(contProv2).afterCreate(controller);

        // empty name in properties - same result
        setUp();
        properties.setProperty(DroolsPropertyConstants.PROPERTY_CONTROLLER_NAME, "");
        assertEquals(controller, mgr.createPolicyController(MY_NAME, properties));
        verify(contProv1).beforeCreate(MY_NAME, properties);

        // matching name in properties - same result
        setUp();
        properties.setProperty(DroolsPropertyConstants.PROPERTY_CONTROLLER_NAME, MY_NAME);
        assertEquals(controller, mgr.createPolicyController(MY_NAME, properties));
        verify(contProv1).beforeCreate(MY_NAME, properties);

        // mismatching name in properties - nothing should happen besides exception
        setUp();
        properties.setProperty(DroolsPropertyConstants.PROPERTY_CONTROLLER_NAME, "mistmatched-name");
        assertThatIllegalStateException().isThrownBy(() -> mgr.createPolicyController(MY_NAME, properties));
        verify(contProv1, never()).beforeCreate(MY_NAME, properties);

        // first provider generates controller - stops after first provider
        setUp();
        when(contProv1.beforeCreate(MY_NAME, properties)).thenReturn(controller2);
        assertEquals(controller2, mgr.createPolicyController(MY_NAME, properties));
        verify(contProv1).beforeCreate(MY_NAME, properties);
        verify(contProv2, never()).beforeCreate(MY_NAME, properties);
        verify(controller, never()).lock();
        verify(contProv1, never()).afterCreate(controller);
        verify(contProv2, never()).afterCreate(controller);

        // first provider returns true - stops after first provider afterXxx
        setUp();
        when(contProv1.afterCreate(controller)).thenReturn(true);
        assertEquals(controller, mgr.createPolicyController(MY_NAME, properties));
        verify(contProv1).beforeCreate(MY_NAME, properties);
        verify(contProv2).beforeCreate(MY_NAME, properties);
        verify(contProv1).afterCreate(controller);
        verify(contProv2, never()).afterCreate(controller);
    }

    @Test
    public void testUpdatePolicyControllers() throws Exception {
        assertEquals(Arrays.asList(controller3, controller4), mgr.updatePolicyControllers(pdpConfig.getControllers()));

        // controller3 was CREATE
        verify(controllerFactory).patch(controller3, drools3);
        verify(controller3, never()).lock();
        verify(controller3, never()).unlock();

        // controller4 was UPDATE
        verify(controllerFactory).patch(controller4, drools4);
        verify(controller4, never()).lock();
        verify(controller4).unlock();

        // invalid args
        assertTrue(mgr.updatePolicyControllers(null).isEmpty());
        assertTrue(mgr.updatePolicyControllers(Collections.emptyList()).isEmpty());

        // force exception in the first controller with invalid operation
        setUp();
        config3.setOperation("unknown-operation");
        assertEquals(Arrays.asList(controller4), mgr.updatePolicyControllers(pdpConfig.getControllers()));

        // controller3 should NOT have been done
        verify(controllerFactory, never()).patch(controller3, drools3);

        // controller4 should still be done
        verify(controllerFactory).patch(controller4, drools4);
        verify(controller4, never()).lock();
        verify(controller4).unlock();
    }

    @Test
    public void testUpdatePolicyController() throws Exception {
        assertEquals(controller3, mgr.updatePolicyController(config3));
        verify(engine).createPolicyController(CONTROLLER3, properties);

        // invalid parameters
        assertThatIllegalArgumentException().isThrownBy(() -> mgr.updatePolicyController(null));

        // invalid name
        setUp();
        config3.setName(null);
        assertThatIllegalArgumentException().isThrownBy(() -> mgr.updatePolicyController(config3));

        config3.setName("");
        assertThatIllegalArgumentException().isThrownBy(() -> mgr.updatePolicyController(config3));

        // invalid operation
        setUp();
        config3.setOperation(null);
        assertThatIllegalArgumentException().isThrownBy(() -> mgr.updatePolicyController(config3));

        config3.setOperation("");
        assertThatIllegalArgumentException().isThrownBy(() -> mgr.updatePolicyController(config3));

        config3.setOperation(ControllerConfiguration.CONFIG_CONTROLLER_OPERATION_LOCK);
        assertThatIllegalArgumentException().isThrownBy(() -> mgr.updatePolicyController(config3));

        config3.setOperation(ControllerConfiguration.CONFIG_CONTROLLER_OPERATION_UNLOCK);
        assertThatIllegalArgumentException().isThrownBy(() -> mgr.updatePolicyController(config3));

        // exception from get() - should create controller
        setUp();
        when(controllerFactory.get(CONTROLLER3)).thenThrow(new IllegalArgumentException(EXPECTED));
        assertEquals(controller3, mgr.updatePolicyController(config3));
        verify(engine).createPolicyController(CONTROLLER3, properties);

        // null properties
        setUp();
        when(persist.getControllerProperties(CONTROLLER3)).thenReturn(null);
        assertThatIllegalArgumentException().isThrownBy(() -> mgr.updatePolicyController(config3));

        // throw linkage error
        setUp();
        when(persist.getControllerProperties(CONTROLLER3)).thenThrow(new LinkageError(EXPECTED));
        assertThatIllegalStateException().isThrownBy(() -> mgr.updatePolicyController(config3));

        /*
         * For remaining tests, the factory will return the controller instead of creating
         * one.
         */
        setUp();
        when(controllerFactory.get(CONTROLLER3)).thenReturn(controller3);

        assertEquals(controller3, mgr.updatePolicyController(config3));

        // should NOT have created a new controller
        verify(engine, never()).createPolicyController(any(), any());

        int countPatch = 0;
        int countLock = 0;
        int countUnlock = 0;

        // check different operations

        // CREATE only invokes patch() (note: mgr.update() has already been called)
        verify(controllerFactory, times(++countPatch)).patch(controller3, drools3);
        verify(controller3, times(countLock)).lock();
        verify(controller3, times(countUnlock)).unlock();

        // UPDATE invokes unlock() and patch()
        config3.setOperation(ControllerConfiguration.CONFIG_CONTROLLER_OPERATION_UPDATE);
        assertEquals(controller3, mgr.updatePolicyController(config3));
        verify(controllerFactory, times(++countPatch)).patch(controller3, drools3);
        verify(controller3, times(countLock)).lock();
        verify(controller3, times(++countUnlock)).unlock();

        // LOCK invokes lock()
        config3.setOperation(ControllerConfiguration.CONFIG_CONTROLLER_OPERATION_LOCK);
        assertEquals(controller3, mgr.updatePolicyController(config3));
        verify(controllerFactory, times(countPatch)).patch(controller3, drools3);
        verify(controller3, times(++countLock)).lock();
        verify(controller3, times(countUnlock)).unlock();

        // UNLOCK invokes unlock()
        config3.setOperation(ControllerConfiguration.CONFIG_CONTROLLER_OPERATION_UNLOCK);
        assertEquals(controller3, mgr.updatePolicyController(config3));
        verify(controllerFactory, times(countPatch)).patch(controller3, drools3);
        verify(controller3, times(countLock)).lock();
        verify(controller3, times(++countUnlock)).unlock();

        // invalid operation
        config3.setOperation("invalid-operation");
        assertThatIllegalArgumentException().isThrownBy(() -> mgr.updatePolicyController(config3));
    }

    @Test
    public void testStart() throws Throwable {
        // normal success case
        testStart(true, () -> {
            // arrange for first provider, server, source, and sink to throw exceptions
            when(prov1.beforeStart(mgr)).thenThrow(new RuntimeException(EXPECTED));
            when(prov1.afterStart(mgr)).thenThrow(new RuntimeException(EXPECTED));
            when(server1.waitedStart(anyLong())).thenThrow(new RuntimeException(EXPECTED));
            when(client1.start()).thenThrow(new RuntimeException(EXPECTED));
            when(source1.start()).thenThrow(new RuntimeException(EXPECTED));
            when(sink1.start()).thenThrow(new RuntimeException(EXPECTED));
        });

        // lock manager fails to start - still does everything
        testStart(false, () -> when(lockmgr.start()).thenReturn(false));

        //  lock manager throws an exception - still does everything
        testStart(false, () -> when(lockmgr.start()).thenThrow(new RuntimeException(EXPECTED)));

        // servlet wait fails - still does everything
        testStart(false, () -> when(server1.waitedStart(anyLong())).thenReturn(false));

        // client fails - still does everything
        testStart(false, () -> when(client1.start()).thenReturn(false));

        // topic source is not started with start
        testStart(true, () -> when(source1.start()).thenReturn(false));

        // topic sink is not started with start
        testStart(true, () -> when(sink1.start()).thenReturn(false));

        // controller fails to start - still does everything
        testStart(false, () -> when(controller.start()).thenReturn(false));

        // controller throws an exception - still does everything
        testStart(false, () -> when(controller.start()).thenThrow(new RuntimeException(EXPECTED)));

        // endpoint manager fails to start - still does everything
        testStart(false, () -> when(endpoint.start()).thenReturn(false));

        // endpoint manager throws an exception - still does everything AND succeeds
        testStart(true, () -> when(endpoint.start()).thenThrow(new IllegalStateException(EXPECTED)));

        // locked - nothing other than beforeXxx methods should be invoked
        setUp();
        mgr.configure(properties);
        mgr.lock();
        assertThatIllegalStateException().isThrownBy(() -> mgr.start());
        verify(prov2).beforeStart(mgr);
        verify(server2, never()).waitedStart(anyLong());
        verify(source2, never()).start();
        verify(sink1, never()).start();
        verify(controller, never()).start();
        verify(endpoint, never()).start();
        assertFalse(jmxStarted);
        verify(prov1, never()).afterStart(mgr);

        // other tests
        checkBeforeAfter(
            (prov, flag) -> when(prov.beforeStart(mgr)).thenReturn(flag),
            (prov, flag) -> when(prov.afterStart(mgr)).thenReturn(flag),
            () -> {
                mgr.configure(properties);
                assertTrue(mgr.start());
            },
            prov -> verify(prov).beforeStart(mgr),
            () -> assertTrue(jmxStarted),
            prov -> verify(prov).afterStart(mgr));
    }

    /**
     * Tests the start() method, after setting some option.
     *
     * @param expectedResult what start() is expected to return
     * @param setOption function that sets an option
     * @throws Throwable if an error occurs during setup
     */
    private void testStart(boolean expectedResult, RunnableWithEx setOption) throws Throwable {
        setUp();
        setOption.run();

        mgr.configure(properties);
        assertEquals(expectedResult, mgr.start());

        verify(prov1).beforeStart(mgr);
        verify(prov2).beforeStart(mgr);

        verify(server1).waitedStart(anyLong());
        verify(server2).waitedStart(anyLong());

        verify(client1).start();
        verify(client2).start();

        verify(source1, never()).start();
        verify(source2, never()).start();

        verify(sink1, never()).start();
        verify(sink2, never()).start();

        verify(controller).start();
        verify(controller2).start();

        verify(endpoint).start();

        assertTrue(jmxStarted);

        verify(prov1).afterStart(mgr);
        verify(prov2).afterStart(mgr);
    }

    @Test
    public void testStop() throws Throwable {
        // normal success case
        testStop(true, () -> {
            // arrange for first provider, server, source, and sink to throw exceptions
            when(prov1.beforeStop(mgr)).thenThrow(new RuntimeException(EXPECTED));
            when(prov1.afterStop(mgr)).thenThrow(new RuntimeException(EXPECTED));
            when(server1.stop()).thenThrow(new RuntimeException(EXPECTED));
            when(client1.stop()).thenThrow(new RuntimeException(EXPECTED));
            when(source1.stop()).thenThrow(new RuntimeException(EXPECTED));
            when(sink1.stop()).thenThrow(new RuntimeException(EXPECTED));
        });

        // not alive - shouldn't run anything besides beforeStop()
        setUp();
        mgr.configure(properties);
        assertTrue(mgr.stop());
        verify(prov1).beforeStop(mgr);
        verify(prov2).beforeStop(mgr);
        verify(controller, never()).stop();
        verify(source1, never()).stop();
        verify(sink1, never()).stop();
        verify(endpoint, never()).stop();
        verify(server1, never()).stop();
        verify(client1, never()).stop();
        verify(prov1, never()).afterStop(mgr);
        verify(prov2, never()).afterStop(mgr);

        // controller fails to stop - still does everything
        testStop(false, () -> when(controller.stop()).thenReturn(false));

        // controller throws an exception - still does everything
        testStop(false, () -> when(controller.stop()).thenThrow(new RuntimeException(EXPECTED)));

        // topic source fails to stop - still does everything
        testStop(false, () -> when(source1.stop()).thenReturn(false));

        // topic sink fails to stop - still does everything
        testStop(false, () -> when(sink1.stop()).thenReturn(false));

        // endpoint manager fails to stop - still does everything
        testStop(false, () -> when(endpoint.stop()).thenReturn(false));

        // servlet fails to stop - still does everything
        testStop(false, () -> when(server1.stop()).thenReturn(false));

        // client fails to stop - still does everything
        testStop(false, () -> when(client1.stop()).thenReturn(false));

        // lock manager fails to stop - still does everything
        testStop(false, () -> when(lockmgr.stop()).thenReturn(false));

        // lock manager throws an exception - still does everything
        testStop(false, () -> when(lockmgr.stop()).thenThrow(new RuntimeException(EXPECTED)));

        // other tests
        checkBeforeAfter(
            (prov, flag) -> when(prov.beforeStop(mgr)).thenReturn(flag),
            (prov, flag) -> when(prov.afterStop(mgr)).thenReturn(flag),
            () -> {
                mgr.configure(properties);
                mgr.start();
                assertTrue(mgr.stop());
            },
            prov -> verify(prov).beforeStop(mgr),
            () -> verify(endpoint).stop(),
            prov -> verify(prov).afterStop(mgr));
    }

    /**
     * Tests the stop() method, after setting some option.
     *
     * @param expectedResult what stop() is expected to return
     * @param setOption function that sets an option
     * @throws Throwable if an error occurs during setup
     */
    private void testStop(boolean expectedResult, RunnableWithEx setOption) throws Throwable {
        setUp();
        setOption.run();

        mgr.configure(properties);
        mgr.start();
        assertEquals(expectedResult, mgr.stop());

        verify(prov1).beforeStop(mgr);
        verify(prov2).beforeStop(mgr);

        verify(controller).stop();
        verify(controller2).stop();

        verify(source1).stop();
        verify(source2).stop();

        verify(sink1).stop();
        verify(sink2).stop();

        verify(endpoint).stop();

        verify(server1).stop();
        verify(server2).stop();

        verify(client1).stop();
        verify(client2).stop();

        verify(prov1).afterStop(mgr);
        verify(prov2).afterStop(mgr);
    }

    @Test
    public void testShutdown() throws Throwable {
        // normal success case
        testShutdown(() -> {
            // arrange for first provider, source, and sink to throw exceptions
            when(prov1.beforeShutdown(mgr)).thenThrow(new RuntimeException(EXPECTED));
            when(prov1.afterShutdown(mgr)).thenThrow(new RuntimeException(EXPECTED));
            doThrow(new RuntimeException(EXPECTED)).when(source1).shutdown();
            doThrow(new RuntimeException(EXPECTED)).when(sink1).shutdown();
        });

        assertNotNull(shutdownThread);
        assertTrue(threadStarted);
        assertTrue(threadInterrupted);


        // lock manager throws an exception - still does everything
        testShutdown(() -> doThrow(new RuntimeException(EXPECTED)).when(lockmgr).shutdown());

        // other tests
        checkBeforeAfter(
            (prov, flag) -> when(prov.beforeShutdown(mgr)).thenReturn(flag),
            (prov, flag) -> when(prov.afterShutdown(mgr)).thenReturn(flag),
            () -> {
                mgr.configure(properties);
                mgr.start();
                mgr.shutdown();
            },
            prov -> verify(prov).beforeShutdown(mgr),
            () -> assertTrue(jmxStopped),
            prov -> verify(prov).afterShutdown(mgr));
    }

    /**
     * Tests the shutdown() method, after setting some option.
     *
     * @param setOption function that sets an option
     * @throws Throwable if an error occurs during setup
     */
    private void testShutdown(RunnableWithEx setOption) throws Throwable {
        setUp();
        setOption.run();

        mgr.configure(properties);
        mgr.start();
        mgr.shutdown();

        verify(prov1).beforeShutdown(mgr);
        verify(prov2).beforeShutdown(mgr);

        verify(source1).shutdown();
        verify(source2).shutdown();

        verify(sink1).shutdown();
        verify(sink2).shutdown();

        verify(controllerFactory).shutdown();
        verify(endpoint).shutdown();
        verify(serverFactory).destroy();
        verify(clientFactory).destroy();

        assertTrue(jmxStopped);

        verify(prov1).afterShutdown(mgr);
        verify(prov2).afterShutdown(mgr);

        verify(exsvc).shutdownNow();
    }

    @Test
    public void testShutdownThreadRun() throws Throwable {
        // arrange for first server to throw exceptions
        testShutdownThreadRun(() -> doThrow(new RuntimeException(EXPECTED)).when(server1).shutdown());

        // sleep throws an exception
        testShutdownThreadRun(() -> shouldInterrupt = true);
    }

    /**
     * Tests the ShutdownThread.run() method, after setting some option.
     *
     * @param setOption function that sets an option
     * @throws Throwable if an error occurs during setup
     */
    private void testShutdownThreadRun(RunnableWithEx setOption) throws Throwable {
        setUp();
        setOption.run();

        mgr.configure(properties);
        mgr.start();
        mgr.shutdown();

        assertNotNull(shutdownThread);

        shutdownThread.run();

        assertTrue(threadSleepMs >= 0);
        assertEquals(0, threadExitCode);

        verify(server1).shutdown();
        verify(server2).shutdown();

        verify(clientFactory).destroy();
    }

    @Test
    public void testIsAlive() {
        mgr.configure(properties);
        assertFalse(mgr.isAlive());

        mgr.start();
        assertTrue(mgr.isAlive());

        mgr.stop();
        assertFalse(mgr.isAlive());
    }

    @Test
    public void testLock() throws Throwable {
        // normal success case
        testLock(true, () -> {
            // arrange for first provider to throw exceptions
            when(prov1.beforeLock(mgr)).thenThrow(new RuntimeException(EXPECTED));
            when(prov1.afterLock(mgr)).thenThrow(new RuntimeException(EXPECTED));
        });

        // already locked - shouldn't run anything besides beforeLock()
        setUp();
        mgr.configure(properties);
        mgr.lock();
        assertTrue(mgr.lock());
        verify(prov1, times(2)).beforeLock(mgr);
        verify(prov2, times(2)).beforeLock(mgr);
        verify(controller).lock();
        verify(controller2).lock();
        verify(endpoint).lock();
        verify(prov1).afterLock(mgr);
        verify(prov2).afterLock(mgr);

        // controller fails to lock - still does everything
        testLock(false, () -> when(controller.lock()).thenReturn(false));

        // controller throws an exception - still does everything
        testLock(false, () -> when(controller.lock()).thenThrow(new RuntimeException(EXPECTED)));

        // endpoint manager fails to lock - still does everything
        testLock(false, () -> when(endpoint.lock()).thenReturn(false));

        // lock manager fails to lock - still does everything
        testLock(false, () -> when(lockmgr.lock()).thenReturn(false));

        // lock manager throws an exception - still does everything
        testLock(false, () -> when(lockmgr.lock()).thenThrow(new RuntimeException(EXPECTED)));

        // other tests
        checkBeforeAfter(
            (prov, flag) -> when(prov.beforeLock(mgr)).thenReturn(flag),
            (prov, flag) -> when(prov.afterLock(mgr)).thenReturn(flag),
            () -> {
                mgr.configure(properties);
                mgr.start();
                assertTrue(mgr.lock());
            },
            prov -> verify(prov).beforeLock(mgr),
            () -> verify(endpoint).lock(),
            prov -> verify(prov).afterLock(mgr));
    }

    /**
     * Tests the lock() method, after setting some option.
     *
     * @param expectedResult what lock() is expected to return
     * @param setOption function that sets an option
     * @throws Throwable if an error occurs during setup
     */
    private void testLock(boolean expectedResult, RunnableWithEx setOption) throws Throwable {
        setUp();
        setOption.run();

        mgr.configure(properties);
        assertEquals(expectedResult, mgr.lock());

        verify(prov1).beforeLock(mgr);
        verify(prov2).beforeLock(mgr);

        verify(controller).lock();
        verify(controller2).lock();

        verify(endpoint).lock();

        verify(prov1).afterLock(mgr);
        verify(prov2).afterLock(mgr);
    }

    @Test
    public void testUnlock() throws Throwable {
        // normal success case
        testUnlock(true, () -> {
            // arrange for first provider to throw exceptions
            when(prov1.beforeUnlock(mgr)).thenThrow(new RuntimeException(EXPECTED));
            when(prov1.afterUnlock(mgr)).thenThrow(new RuntimeException(EXPECTED));
        });

        // not locked - shouldn't run anything besides beforeUnlock()
        setUp();
        mgr.configure(properties);
        assertTrue(mgr.unlock());
        verify(prov1).beforeUnlock(mgr);
        verify(prov2).beforeUnlock(mgr);
        verify(controller, never()).unlock();
        verify(controller2, never()).unlock();
        verify(endpoint, never()).unlock();
        verify(prov1, never()).afterUnlock(mgr);
        verify(prov2, never()).afterUnlock(mgr);

        // controller fails to unlock - still does everything
        testUnlock(false, () -> when(controller.unlock()).thenReturn(false));

        // controller throws an exception - still does everything
        testUnlock(false, () -> when(controller.unlock()).thenThrow(new RuntimeException(EXPECTED)));

        // endpoint manager fails to unlock - still does everything
        testUnlock(false, () -> when(endpoint.unlock()).thenReturn(false));

        // lock manager fails to lock - still does everything
        testUnlock(false, () -> when(lockmgr.unlock()).thenReturn(false));

        // lock manager throws an exception - still does everything
        testUnlock(false, () -> when(lockmgr.unlock()).thenThrow(new RuntimeException(EXPECTED)));

        // other tests
        checkBeforeAfter(
            (prov, flag) -> when(prov.beforeUnlock(mgr)).thenReturn(flag),
            (prov, flag) -> when(prov.afterUnlock(mgr)).thenReturn(flag),
            () -> {
                mgr.configure(properties);
                mgr.lock();
                assertTrue(mgr.unlock());
            },
            prov -> verify(prov).beforeUnlock(mgr),
            () -> verify(endpoint).unlock(),
            prov -> verify(prov).afterUnlock(mgr));
    }

    /**
     * Tests the unlock() method, after setting some option.
     *
     * @param expectedResult what unlock() is expected to return
     * @param setOption function that sets an option
     * @throws Throwable if an error occurs during setup
     */
    private void testUnlock(boolean expectedResult, RunnableWithEx setOption) throws Throwable {
        setUp();
        setOption.run();

        mgr.configure(properties);
        mgr.lock();
        assertEquals(expectedResult, mgr.unlock());

        verify(prov1).beforeUnlock(mgr);
        verify(prov2).beforeUnlock(mgr);

        verify(controller).unlock();
        verify(controller2).unlock();

        verify(endpoint).unlock();

        verify(prov1).afterUnlock(mgr);
        verify(prov2).afterUnlock(mgr);
    }

    @Test
    public void testIsLocked() {
        mgr.configure(properties);
        assertFalse(mgr.isLocked());

        mgr.lock();
        assertTrue(mgr.isLocked());

        mgr.unlock();
        assertFalse(mgr.isLocked());
    }

    @Test
    public void testRemovePolicyControllerString() {
        mgr.removePolicyController(MY_NAME);

        verify(controllerFactory).destroy(MY_NAME);
    }

    @Test
    public void testRemovePolicyControllerPolicyController() {
        mgr.removePolicyController(controller);

        verify(controllerFactory).destroy(controller);
    }

    @Test
    public void testGetPolicyControllers() {
        assertEquals(controllers, mgr.getPolicyControllers());
    }

    @Test
    public void testGetPolicyControllerIds() {
        assertEquals(Arrays.asList(CONTROLLER1, CONTROLLER2), mgr.getPolicyControllerIds());
    }

    @Test
    public void testGetProperties() {
        properties.setProperty("prop-x", "value-x");
        properties.setProperty("prop-y", "value-y");

        mgr.configure(properties);
        assertEquals(properties, mgr.getProperties());
    }

    @Test
    public void testGetSources() {
        mgr.configure(properties);
        assertEquals(sources, mgr.getSources());
    }

    @Test
    public void testGetSinks() {
        mgr.configure(properties);
        assertEquals(sinks, mgr.getSinks());
    }

    @Test
    public void testGetHttpServers() {
        mgr.configure(properties);
        assertEquals(servers, mgr.getHttpServers());
    }

    @Test
    public void testGetFeatures() {
        assertEquals(Arrays.asList(FEATURE1, FEATURE2), mgr.getFeatures());
    }

    @Test
    public void testGetFeatureProviders() {
        assertEquals(providers, mgr.getFeatureProviders());
    }

    @Test
    public void testGetFeatureProvider() {
        assertEquals(prov1, mgr.getFeatureProvider(FEATURE1));
        assertEquals(prov2, mgr.getFeatureProvider(FEATURE2));

        // null feature
        assertThatIllegalArgumentException().isThrownBy(() -> mgr.getFeatureProvider(null));

        // empty feature
        assertThatIllegalArgumentException().isThrownBy(() -> mgr.getFeatureProvider(""));

        // unknown feature
        assertThatIllegalArgumentException().isThrownBy(() -> mgr.getFeatureProvider("unknown-feature"));
    }

    @Test
    public void testOnTopicEvent() {
        mgr.onTopicEvent(CommInfrastructure.NOOP, MY_TOPIC, pdpConfigJson);

        verify(controllerFactory).patch(controller3, drools3);
        verify(controllerFactory).patch(controller4, drools4);

        // null json - no additional patches
        mgr.onTopicEvent(CommInfrastructure.NOOP, MY_TOPIC, null);

        verify(controllerFactory).patch(controller3, drools3);
        verify(controllerFactory).patch(controller4, drools4);
    }

    @Test
    public void testDeliverStringObject() throws Exception {
        mgr.configure(properties);
        mgr.start();

        assertTrue(mgr.deliver(MY_TOPIC, MY_EVENT));

        verify(sink1).send(MESSAGE);

        // invalid parameters
        String nullStr = null;
        assertThatIllegalArgumentException().isThrownBy(() -> mgr.deliver(nullStr, MY_EVENT));
        assertThatIllegalArgumentException().isThrownBy(() -> mgr.deliver("", MY_EVENT));

        Object nullObj = null;
        assertThatIllegalArgumentException().isThrownBy(() -> mgr.deliver(MY_TOPIC, nullObj));

        // locked
        mgr.lock();
        assertThatIllegalStateException().isThrownBy(() -> mgr.deliver(MY_TOPIC, MY_EVENT));
        mgr.unlock();

        // not running
        mgr.stop();
        assertThatIllegalStateException().isThrownBy(() -> mgr.deliver(MY_TOPIC, MY_EVENT));

        // issues with topic
        setUp();
        mgr.configure(properties);
        mgr.start();

        // null sinks
        when(endpoint.getTopicSinks(MY_TOPIC)).thenReturn(null);
        assertThatIllegalStateException().isThrownBy(() -> mgr.deliver(MY_TOPIC, MY_EVENT));

        // empty sinks
        when(endpoint.getTopicSinks(MY_TOPIC)).thenReturn(Collections.emptyList());
        assertThatIllegalStateException().isThrownBy(() -> mgr.deliver(MY_TOPIC, MY_EVENT));

        // too many sinks
        when(endpoint.getTopicSinks(MY_TOPIC)).thenReturn(sinks);
        assertThatIllegalStateException().isThrownBy(() -> mgr.deliver(MY_TOPIC, MY_EVENT));
    }

    @Test
    public void testDeliverStringStringObject() {
        mgr.configure(properties);
        mgr.start();

        assertTrue(mgr.deliver(NOOP_STR, MY_TOPIC, MY_EVENT));

        verify(sink1).send(MESSAGE);

        // invalid parameters
        String nullStr = null;
        assertThatIllegalArgumentException().isThrownBy(() -> mgr.deliver(nullStr, MY_TOPIC, MY_EVENT));
        assertThatIllegalArgumentException().isThrownBy(() -> mgr.deliver("", MY_TOPIC, MY_EVENT));
        assertThatIllegalArgumentException().isThrownBy(() -> mgr.deliver("unknown-bus-type", MY_TOPIC, MY_EVENT));

        assertThatIllegalArgumentException().isThrownBy(() -> mgr.deliver(NOOP_STR, nullStr, MY_EVENT));
        assertThatIllegalArgumentException().isThrownBy(() -> mgr.deliver(NOOP_STR, "", MY_EVENT));

        Object nullObj = null;
        assertThatIllegalArgumentException().isThrownBy(() -> mgr.deliver(NOOP_STR, MY_TOPIC, nullObj));

        // locked
        mgr.lock();
        assertThatIllegalStateException().isThrownBy(() -> mgr.deliver(NOOP_STR, MY_TOPIC, MY_EVENT));
        mgr.unlock();

        // not running
        mgr.stop();
        assertThatIllegalStateException().isThrownBy(() -> mgr.deliver(NOOP_STR, MY_TOPIC, MY_EVENT));
    }

    @Test
    public void testDeliverCommInfrastructureStringObject() throws Exception {
        mgr.configure(properties);
        mgr.start();

        assertTrue(mgr.deliver(CommInfrastructure.NOOP, MY_TOPIC, MY_EVENT));

        verify(controller, never()).deliver(CommInfrastructure.NOOP, MY_TOPIC, MY_EVENT);

        verify(coder).encode(MY_TOPIC, MY_EVENT);
        verify(sink1).send(MESSAGE);

        // invalid parameters
        assertThatIllegalArgumentException().isThrownBy(() -> mgr.deliver(CommInfrastructure.NOOP, null, MY_EVENT));
        assertThatIllegalArgumentException().isThrownBy(() -> mgr.deliver(CommInfrastructure.NOOP, "", MY_EVENT));

        Object nullObj = null;
        assertThatIllegalArgumentException().isThrownBy(() -> mgr.deliver(CommInfrastructure.NOOP, MY_TOPIC, nullObj));

        // locked
        mgr.lock();
        assertThatIllegalStateException().isThrownBy(() -> mgr.deliver(CommInfrastructure.NOOP, MY_TOPIC, MY_EVENT));
        mgr.unlock();

        // not started
        mgr.stop();
        assertThatIllegalStateException().isThrownBy(() -> mgr.deliver(CommInfrastructure.NOOP, MY_TOPIC, MY_EVENT));

        // send() throws an exception
        setUp();
        mgr.configure(properties);
        mgr.start();
        when(sink1.send(any())).thenThrow(new ArithmeticException(EXPECTED));
        assertThatThrownBy(() -> mgr.deliver(CommInfrastructure.NOOP, MY_TOPIC, MY_EVENT))
                        .isInstanceOf(ArithmeticException.class);

        /*
         * For remaining tests, have the controller handle delivery.
         */
        setUp();
        mgr.configure(properties);
        mgr.start();
        DroolsController drools = mock(DroolsController.class);
        when(coder.getDroolsController(MY_TOPIC, MY_EVENT)).thenReturn(drools);
        when(controllerFactory.get(drools)).thenReturn(controller);
        when(controller.deliver(CommInfrastructure.NOOP, MY_TOPIC, MY_EVENT)).thenReturn(true);

        assertTrue(mgr.deliver(CommInfrastructure.NOOP, MY_TOPIC, MY_EVENT));

        verify(controller).deliver(CommInfrastructure.NOOP, MY_TOPIC, MY_EVENT);

        verify(coder, never()).encode(MY_TOPIC, MY_EVENT);
        verify(sink1, never()).send(MESSAGE);

        // controller throws exception, so should drop into regular handling
        when(controller.deliver(CommInfrastructure.NOOP, MY_TOPIC, MY_EVENT)).thenThrow(new RuntimeException(EXPECTED));

        assertTrue(mgr.deliver(CommInfrastructure.NOOP, MY_TOPIC, MY_EVENT));

        // should have attempted this again
        verify(controller, times(2)).deliver(CommInfrastructure.NOOP, MY_TOPIC, MY_EVENT);

        // now these should have been called
        verify(coder).encode(MY_TOPIC, MY_EVENT);
        verify(sink1).send(MESSAGE);
    }

    @Test
    public void testDeliverCommInfrastructureStringString() {
        mgr.configure(properties);

        // not started yet
        assertThatIllegalStateException().isThrownBy(() -> mgr.deliver(CommInfrastructure.NOOP, MY_TOPIC, MESSAGE));

        // start it
        mgr.start();

        assertTrue(mgr.deliver(CommInfrastructure.NOOP, MY_TOPIC, MESSAGE));
        verify(sink1).send(MESSAGE);
        verify(sink2, never()).send(any());

        // invalid parameters
        assertThatIllegalArgumentException().isThrownBy(() -> mgr.deliver(CommInfrastructure.NOOP, null, MESSAGE));
        assertThatIllegalArgumentException().isThrownBy(() -> mgr.deliver(CommInfrastructure.NOOP, "", MESSAGE));

        String nullStr = null;
        assertThatIllegalArgumentException().isThrownBy(() -> mgr.deliver(CommInfrastructure.NOOP, MY_TOPIC, nullStr));
        assertThatIllegalArgumentException().isThrownBy(() -> mgr.deliver(CommInfrastructure.NOOP, MY_TOPIC, ""));

        // unknown topic
        assertThatIllegalStateException()
                        .isThrownBy(() -> mgr.deliver(CommInfrastructure.NOOP, "unknown-topic", MESSAGE));

        // locked
        mgr.lock();
        assertThatIllegalStateException().isThrownBy(() -> mgr.deliver(CommInfrastructure.NOOP, MY_TOPIC, MESSAGE));
        mgr.unlock();
    }

    @Test
    public void testActivate() throws Throwable {
        // normal success case
        testActivate(() -> {
            // arrange for first provider and controller to throw exceptions
            when(prov1.beforeActivate(mgr)).thenThrow(new RuntimeException(EXPECTED));
            when(prov1.afterActivate(mgr)).thenThrow(new RuntimeException(EXPECTED));
            when(controller.start()).thenThrow(new RuntimeException(EXPECTED));
        });

        // controller generates linkage error
        testActivate(() -> when(controller.start()).thenThrow(new LinkageError(EXPECTED)));

        // other tests
        checkBeforeAfter(
            (prov, flag) -> when(prov.beforeActivate(mgr)).thenReturn(flag),
            (prov, flag) -> when(prov.afterActivate(mgr)).thenReturn(flag),
            () -> {
                mgr.configure(properties);
                mgr.lock();
                mgr.activate();
            },
            prov -> verify(prov).beforeActivate(mgr),
            () -> assertFalse(mgr.isLocked()),
            prov -> verify(prov).afterActivate(mgr));
    }

    /**
     * Tests the activate() method, after setting some option.
     *
     * @param setOption function that sets an option
     * @throws Throwable if an error occurs during setup
     */
    private void testActivate(RunnableWithEx setOption) throws Throwable {
        setUp();
        setOption.run();

        mgr.configure(properties);
        mgr.lock();
        mgr.activate();

        verify(prov1).beforeActivate(mgr);
        verify(prov2).beforeActivate(mgr);

        // unlocked by activate() AND by unlock() (which is invoked by activate())
        verify(controller, times(2)).unlock();
        verify(controller2, times(2)).unlock();

        verify(controller).start();
        verify(controller2).start();

        assertFalse(mgr.isLocked());

        verify(prov1).afterActivate(mgr);
        verify(prov2).afterActivate(mgr);
    }

    @Test
    public void testDeactivate() throws Throwable {
        // normal success case
        testDeactivate(() -> {
            // arrange for first provider and controller to throw exceptions
            when(prov1.beforeDeactivate(mgr)).thenThrow(new RuntimeException(EXPECTED));
            when(prov1.afterDeactivate(mgr)).thenThrow(new RuntimeException(EXPECTED));
            when(controller.stop()).thenThrow(new RuntimeException(EXPECTED));
        });

        // controller generates linkage error
        testDeactivate(() -> when(controller.stop()).thenThrow(new LinkageError(EXPECTED)));

        // other tests
        checkBeforeAfter(
            (prov, flag) -> when(prov.beforeDeactivate(mgr)).thenReturn(flag),
            (prov, flag) -> when(prov.afterDeactivate(mgr)).thenReturn(flag),
            () -> {
                mgr.configure(properties);
                mgr.deactivate();
            },
            prov -> verify(prov).beforeDeactivate(mgr),
            () -> assertTrue(mgr.isLocked()),
            prov -> verify(prov).afterDeactivate(mgr));
    }

    /**
     * Tests the deactivate() method, after setting some option.
     *
     * @param setOption function that sets an option
     * @throws Throwable if an error occurs during setup
     */
    private void testDeactivate(RunnableWithEx setOption) throws Throwable {
        setUp();
        setOption.run();

        mgr.configure(properties);
        mgr.deactivate();

        verify(prov1).beforeDeactivate(mgr);
        verify(prov2).beforeDeactivate(mgr);

        verify(controller).lock();
        verify(controller2).lock();

        verify(controller).stop();
        verify(controller2).stop();

        assertTrue(mgr.isLocked());

        verify(prov1).afterDeactivate(mgr);
        verify(prov2).afterDeactivate(mgr);
    }

    @Test
    public void testCreateLock() {
        Lock lock = mock(Lock.class);
        LockCallback callback = mock(LockCallback.class);
        when(lockmgr.createLock(MY_RESOURCE, MY_OWNER, 10, callback, false)).thenReturn(lock);

        // not configured yet, thus no lock manager
        assertThatIllegalStateException()
                        .isThrownBy(() -> mgr.createLock(MY_RESOURCE, MY_OWNER, 10, callback, false));

        // now configure it and try again
        mgr.configure(properties);
        assertSame(lock, mgr.createLock(MY_RESOURCE, MY_OWNER, 10, callback, false));

        // test illegal args
        assertThatThrownBy(() -> mgr.createLock(null, MY_OWNER, 10, callback, false))
                        .hasMessageContaining("resourceId");
        assertThatThrownBy(() -> mgr.createLock(MY_RESOURCE, null, 10, callback, false))
                        .hasMessageContaining("ownerKey");
        assertThatIllegalArgumentException()
                        .isThrownBy(() -> mgr.createLock(MY_RESOURCE, MY_OWNER, -1, callback, false))
                        .withMessageContaining("holdSec");
        assertThatThrownBy(() -> mgr.createLock(MY_RESOURCE, MY_OWNER, 10, null, false))
                        .hasMessageContaining("callback");
    }

    @Test
    public void testOpen() throws Throwable {
        when(prov1.beforeOpen(mgr)).thenThrow(new RuntimeException(EXPECTED));
        when(prov1.afterOpen(mgr)).thenThrow(new RuntimeException(EXPECTED));

        assertTrue(mgr.lock());
        assertThatIllegalStateException().isThrownBy(() -> mgr.open());
        unsuccessfulOpen();

        assertTrue(mgr.unlock());
        unsuccessfulOpen();

        setUp();
        mgr.configure(properties);
        assertTrue(mgr.start());

        verify(source1, never()).start();
        verify(source2, never()).start();

        assertTrue(mgr.open());

        verify(prov1).beforeOpen(mgr);
        verify(prov2).beforeOpen(mgr);

        verify(source1).start();
        verify(source2).start();

        verify(prov1).afterOpen(mgr);
        verify(prov2).afterOpen(mgr);

        when(source1.start()).thenReturn(false);
        assertFalse(mgr.open());
        when(source1.start()).thenReturn(true);

        when(sink1.start()).thenReturn(false);
        assertFalse(mgr.open());
        when(sink1.start()).thenReturn(true);

        assertTrue(mgr.open());
    }

    private void unsuccessfulOpen() {
        verify(prov1).beforeOpen(mgr);
        verify(prov2).beforeOpen(mgr);

        verify(prov1, never()).afterOpen(mgr);
        verify(prov2, never()).afterOpen(mgr);

        verify(source1, never()).start();
        verify(source2, never()).start();

        verify(sink1, never()).start();
        verify(sink2, never()).start();
    }

    @Test
    public void testControllerConfig() throws Exception {
        mgr.configure(properties);
        assertTrue(mgr.configure(pdpConfig));

        verify(controllerFactory).patch(controller3, drools3);
        verify(controllerFactory).patch(controller4, drools4);

        // empty controllers
        pdpConfig.getControllers().clear();
        assertFalse(mgr.configure(pdpConfig));

        // null controllers
        pdpConfig.setControllers(null);
        assertFalse(mgr.configure(pdpConfig));

        // arrange for controller3 to fail
        setUp();
        config3.setOperation("fail-3");
        assertFalse(mgr.configure(pdpConfig));

        verify(controllerFactory, never()).patch(controller3, drools3);
        verify(controllerFactory).patch(controller4, drools4);

        // arrange for both controllers to fail
        setUp();
        config3.setOperation("fail-3");
        config4.setOperation("fail-4");
        assertFalse(mgr.configure(pdpConfig));
    }

    @Test
    public void testToString() {
        assertTrue(mgr.toString().startsWith("PolicyEngineManager ["));
    }

    /**
     * Performs an operation that has a beforeXxx method and an afterXxx method. Tries
     * combinations where beforeXxx and afterXxx return {@code true} and {@code false}.
     *
     * @param setBefore function to set the return value of a provider's beforeXxx method
     * @param setAfter function to set the return value of a provider's afterXxx method
     * @param action invokes the operation
     * @param verifyBefore verifies that a provider's beforeXxx method was invoked
     * @param verifyMiddle verifies that the action occurring between the beforeXxx loop
     *        and the afterXxx loop was invoked
     * @param verifyAfter verifies that a provider's afterXxx method was invoked
     * @throws Exception if an error occurs while calling {@link #setUp()}
     */
    private void checkBeforeAfter(BiConsumer<PolicyEngineFeatureApi, Boolean> setBefore,
                    BiConsumer<PolicyEngineFeatureApi, Boolean> setAfter, Runnable action,
                    Consumer<PolicyEngineFeatureApi> verifyBefore, Runnable verifyMiddle,
                    Consumer<PolicyEngineFeatureApi> verifyAfter) throws Exception {

        checkBeforeAfter_FalseFalse(setBefore, setAfter, action, verifyBefore, verifyMiddle, verifyAfter);
        checkBeforeAfter_FalseTrue(setBefore, setAfter, action, verifyBefore, verifyMiddle, verifyAfter);
        checkBeforeAfter_TrueFalse(setBefore, setAfter, action, verifyBefore, verifyMiddle, verifyAfter);

        // don't need to test true-true, as it's behavior is a subset of true-false
    }

    /**
     * Performs an operation that has a beforeXxx method and an afterXxx method. Tries the
     * case where both the beforeXxx and afterXxx methods return {@code false}.
     *
     * @param setBefore function to set the return value of a provider's beforeXxx method
     * @param setAfter function to set the return value of a provider's afterXxx method
     * @param action invokes the operation
     * @param verifyBefore verifies that a provider's beforeXxx method was invoked
     * @param verifyMiddle verifies that the action occurring between the beforeXxx loop
     *        and the afterXxx loop was invoked
     * @param verifyAfter verifies that a provider's afterXxx method was invoked
     * @throws Exception if an error occurs while calling {@link #setUp()}
     */
    private void checkBeforeAfter_FalseFalse(BiConsumer<PolicyEngineFeatureApi, Boolean> setBefore,
                    BiConsumer<PolicyEngineFeatureApi, Boolean> setAfter, Runnable action,
                    Consumer<PolicyEngineFeatureApi> verifyBefore, Runnable verifyMiddle,
                    Consumer<PolicyEngineFeatureApi> verifyAfter) throws Exception {

        setUp();

        // configure for the test
        setBefore.accept(prov1, false);
        setBefore.accept(prov2, false);

        setAfter.accept(prov1, false);
        setAfter.accept(prov2, false);

        // run the action
        action.run();

        // verify that various methods were invoked
        verifyBefore.accept(prov1);
        verifyBefore.accept(prov2);

        verifyMiddle.run();

        verifyAfter.accept(prov1);
        verifyAfter.accept(prov2);
    }

    /**
     * Performs an operation that has a beforeXxx method and an afterXxx method. Tries the
     * case where the first provider's afterXxx returns {@code true}, while the others
     * return {@code false}.
     *
     * @param setBefore function to set the return value of a provider's beforeXxx method
     * @param setAfter function to set the return value of a provider's afterXxx method
     * @param action invokes the operation
     * @param verifyBefore verifies that a provider's beforeXxx method was invoked
     * @param verifyMiddle verifies that the action occurring between the beforeXxx loop
     *        and the afterXxx loop was invoked
     * @param verifyAfter verifies that a provider's afterXxx method was invoked
     * @throws Exception if an error occurs while calling {@link #setUp()}
     */
    private void checkBeforeAfter_FalseTrue(BiConsumer<PolicyEngineFeatureApi, Boolean> setBefore,
                    BiConsumer<PolicyEngineFeatureApi, Boolean> setAfter, Runnable action,
                    Consumer<PolicyEngineFeatureApi> verifyBefore, Runnable verifyMiddle,
                    Consumer<PolicyEngineFeatureApi> verifyAfter) throws Exception {

        setUp();

        // configure for the test
        setBefore.accept(prov1, false);
        setBefore.accept(prov2, false);

        setAfter.accept(prov1, true);
        setAfter.accept(prov2, false);

        // run the action
        action.run();

        // verify that various methods were invoked
        verifyBefore.accept(prov1);
        verifyBefore.accept(prov2);

        verifyMiddle.run();

        verifyAfter.accept(prov1);
        assertThatThrownBy(() -> verifyAfter.accept(prov2)).isInstanceOf(AssertionError.class);
    }

    /**
     * Performs an operation that has a beforeXxx method and an afterXxx method. Tries the
     * case where the first provider's beforeXxx returns {@code true}, while the others
     * return {@code false}.
     *
     * @param setBefore function to set the return value of a provider's beforeXxx method
     * @param setAfter function to set the return value of a provider's afterXxx method
     * @param action invokes the operation
     * @param verifyBefore verifies that a provider's beforeXxx method was invoked
     * @param verifyMiddle verifies that the action occurring between the beforeXxx loop
     *        and the afterXxx loop was invoked
     * @param verifyAfter verifies that a provider's afterXxx method was invoked
     * @throws Exception if an error occurs while calling {@link #setUp()}
     */
    private void checkBeforeAfter_TrueFalse(BiConsumer<PolicyEngineFeatureApi, Boolean> setBefore,
                    BiConsumer<PolicyEngineFeatureApi, Boolean> setAfter, Runnable action,
                    Consumer<PolicyEngineFeatureApi> verifyBefore, Runnable verifyMiddle,
                    Consumer<PolicyEngineFeatureApi> verifyAfter) throws Exception {

        setUp();

        // configure for the test
        setBefore.accept(prov1, true);
        setBefore.accept(prov2, false);

        setAfter.accept(prov1, false);
        setAfter.accept(prov2, false);

        // run the action
        action.run();

        // verify that various methods were invoked
        verifyBefore.accept(prov1);

        // remaining methods should not have been invoked
        assertThatThrownBy(() -> verifyBefore.accept(prov2)).isInstanceOf(AssertionError.class);

        assertThatThrownBy(() -> verifyMiddle.run()).isInstanceOf(AssertionError.class);

        assertThatThrownBy(() -> verifyAfter.accept(prov1)).isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> verifyAfter.accept(prov2)).isInstanceOf(AssertionError.class);
    }

    /**
     * Manager with overrides.
     */
    private class PolicyEngineManagerImpl extends PolicyEngineManager {

        @Override
        protected List<PolicyEngineFeatureApi> getEngineProviders() {
            return providers;
        }

        @Override
        protected List<PolicyControllerFeatureApi> getControllerProviders() {
            return contProviders;
        }

        @Override
        protected void globalInitContainer(String[] cliArgs) {
            globalInitArgs = cliArgs;
        }

        @Override
        protected TopicEndpoint getTopicEndpointManager() {
            return endpoint;
        }

        @Override
        protected HttpServletServerFactory getServletFactory() {
            return serverFactory;
        }

        @Override
        protected HttpClientFactory getHttpClientFactory() {
            return clientFactory;
        }

        @Override
        protected PolicyControllerFactory getControllerFactory() {
            return controllerFactory;
        }

        @Override
        protected void startPdpJmxListener() {
            jmxStarted = true;
        }

        @Override
        protected void stopPdpJmxListener() {
            jmxStopped = true;
        }

        @Override
        protected Thread makeShutdownThread() {
            shutdownThread = new MyShutdown();
            return shutdownThread;
        }

        @Override
        protected EventProtocolCoder getProtocolCoder() {
            return coder;
        }

        @Override
        protected SystemPersistence getPersistenceManager() {
            return persist;
        }

        @Override
        protected PolicyEngine getPolicyEngine() {
            return engine;
        }

        @Override
        protected ScheduledExecutorService makeScheduledExecutor(int nthreads) {
            return exsvc;
        }

        /**
         * Shutdown thread with overrides.
         */
        private class MyShutdown extends ShutdownThread {

            @Override
            protected void doSleep(long sleepMs) throws InterruptedException {
                threadSleepMs = sleepMs;

                if (shouldInterrupt) {
                    throw new InterruptedException(EXPECTED);
                }
            }

            @Override
            protected void doExit(int code) {
                threadExitCode = code;
            }

            @Override
            public synchronized void start() {
                threadStarted = true;
            }

            @Override
            public void interrupt() {
                threadInterrupted = true;
            }
        }
    }

    @FunctionalInterface
    private static interface RunnableWithEx {
        void run() throws Exception;
    }
}
