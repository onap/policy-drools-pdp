/*-
 * ============LICENSE_START=======================================================
 * policy-management
 * ================================================================================
 * Copyright (C) 2017-2019 AT&T Intellectual Property. All rights reserved.
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.onap.policy.common.endpoints.event.comm.TopicEndpoint;
import org.onap.policy.common.endpoints.event.comm.TopicSink;
import org.onap.policy.common.endpoints.event.comm.bus.NoopTopicSink;
import org.onap.policy.common.endpoints.properties.PolicyEndPointProperties;
import org.onap.policy.common.utils.gson.GsonTestUtils;
import org.onap.policy.drools.persistence.SystemPersistence;
import org.onap.policy.drools.properties.DroolsProperties;
import org.onap.policy.drools.protocol.coders.EventProtocolCoder;
import org.onap.policy.drools.protocol.coders.EventProtocolParams;
import org.onap.policy.drools.protocol.coders.JsonProtocolFilter;
import org.onap.policy.drools.protocol.configuration.DroolsConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PolicyEngine unit tests.
 */

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PolicyEngineTest {
    /**
     * Default Telemetry port for JUnits.
     */
    public static final int DEFAULT_TELEMETRY_PORT = 9698;

    /**
     * Test JUnit Controller Name.
     */
    public static final String TEST_CONTROLLER_NAME = "foo";

    /**
     * Controller Configuration File.
     */
    public static final String TEST_CONTROLLER_FILE = TEST_CONTROLLER_NAME + "-controller.properties";

    /**
     * Controller Configuration Backup File.
     */
    public static final String TEST_CONTROLLER_FILE_BAK = TEST_CONTROLLER_FILE + ".bak";

    /**
     * Coder Group.
     */
    private static final String ENCODER_GROUP = "foo";

    /**
     * Coder Artifact.
     */
    private static final String ENCODER_ARTIFACT = "bar";

    /**
     * Coder Version.
     */
    private static final String ENCODER_VERSION = null;

    /**
     * noop topic.
     */
    private static final String NOOP_TOPIC = "JUNIT";

    /**
     * logger.
     */
    private static Logger logger = LoggerFactory.getLogger(PolicyEngineTest.class);

    private static GsonTestUtils gson;

    /**
     * clean up working directory.
     */
    protected static void cleanUpWorkingDir() {
        final Path testControllerPath =
                Paths.get(SystemPersistence.manager.getConfigurationPath().toString(), TEST_CONTROLLER_FILE);
        try {
            Files.deleteIfExists(testControllerPath);
        } catch (final Exception e) {
            logger.info("Problem cleaning {}", testControllerPath, e);
        }

        final Path testControllerBakPath =
                Paths.get(SystemPersistence.manager.getConfigurationPath().toString(), TEST_CONTROLLER_FILE_BAK);
        try {
            Files.deleteIfExists(testControllerBakPath);
        } catch (final Exception e) {
            logger.info("Problem cleaning {}", testControllerBakPath, e);
        }
    }

    /**
     * Start up.
     *
     * @throws IOException throws IO exception
     */
    @BeforeClass
    public static void startUp() throws IOException {
        logger.info("enter");

        gson = new GsonTestUtils();

        cleanUpWorkingDir();

        /* ensure presence of config directory */
        final Path configDir = Paths.get(SystemPersistence.DEFAULT_CONFIGURATION_DIR);
        if (Files.notExists(configDir)) {
            Files.createDirectories(configDir);
        }
    }

    @AfterClass
    public static void tearDown() throws IOException {
        logger.info("enter");
        cleanUpWorkingDir();
    }

    @Test
    public void test100Configure() {
        logger.info("enter");

        final Properties engineProps = PolicyEngine.manager.defaultTelemetryConfig();

        /* override default port */
        engineProps.put(PolicyEndPointProperties.PROPERTY_HTTP_SERVER_SERVICES + "."
                        + PolicyEngine.TELEMETRY_SERVER_DEFAULT_NAME
                        + PolicyEndPointProperties.PROPERTY_HTTP_PORT_SUFFIX, "" + DEFAULT_TELEMETRY_PORT);

        assertFalse(PolicyEngine.manager.isAlive());
        PolicyEngine.manager.configure(engineProps);
        assertFalse(PolicyEngine.manager.isAlive());

        logger.info("policy-engine {} has configuration {}", PolicyEngine.manager, engineProps);

        gson.compareGson(PolicyEngine.manager, new File(PolicyEngineTest.class.getSimpleName() + "Config.json"));
    }

    @Test
    public void test200Start() {
        logger.info("enter");

        PolicyEngine.manager.start();

        assertTrue(PolicyEngine.manager.isAlive());
        assertFalse(PolicyEngine.manager.isLocked());
        assertFalse(PolicyEngine.manager.getHttpServers().isEmpty());
        assertTrue(PolicyEngine.manager.getHttpServers().get(0).isAlive());
    }

    @Test
    public void test300Lock() {
        logger.info("enter");

        PolicyEngine.manager.lock();

        assertTrue(PolicyEngine.manager.isAlive());
        assertTrue(PolicyEngine.manager.isLocked());
        assertFalse(PolicyEngine.manager.getHttpServers().isEmpty());
        assertTrue(PolicyEngine.manager.getHttpServers().get(0).isAlive());
    }

    @Test
    public void test301Unlock() {
        logger.info("enter");

        PolicyEngine.manager.unlock();

        assertTrue(PolicyEngine.manager.isAlive());
        assertFalse(PolicyEngine.manager.isLocked());
        assertFalse(PolicyEngine.manager.getHttpServers().isEmpty());
        assertTrue(PolicyEngine.manager.getHttpServers().get(0).isAlive());
    }

    @Test
    public void test350TopicDeliver() {
        final Properties noopSinkProperties = new Properties();
        noopSinkProperties.put(PolicyEndPointProperties.PROPERTY_NOOP_SINK_TOPICS, NOOP_TOPIC);

        TopicEndpoint.manager.addTopicSinks(noopSinkProperties).get(0).start();

        EventProtocolCoder.manager.addEncoder(
                EventProtocolParams.builder().groupId(ENCODER_GROUP).artifactId(ENCODER_ARTIFACT)
                        .topic(NOOP_TOPIC).eventClass(DroolsConfiguration.class.getCanonicalName())
                        .protocolFilter(new JsonProtocolFilter()).customGsonCoder(null)
                        .modelClassLoaderHash(DroolsConfiguration.class.getName().hashCode()));

        assertTrue(PolicyEngine.manager.deliver(NOOP_TOPIC,
                new DroolsConfiguration(ENCODER_GROUP, ENCODER_ARTIFACT, ENCODER_VERSION)));

        final TopicSink sink = NoopTopicSink.factory.get(NOOP_TOPIC);
        assertTrue(sink.getRecentEvents()[0].contains(ENCODER_GROUP));
        assertTrue(sink.getRecentEvents()[0].contains(ENCODER_ARTIFACT));

        EventProtocolCoder.manager.removeEncoders(ENCODER_GROUP, ENCODER_ARTIFACT, NOOP_TOPIC);
    }

    @Test
    public void test400ControllerAdd() throws Exception {
        logger.info("enter");

        final Properties controllerProperties = new Properties();
        controllerProperties.put(DroolsProperties.PROPERTY_CONTROLLER_NAME, TEST_CONTROLLER_NAME);
        PolicyEngine.manager.createPolicyController(TEST_CONTROLLER_NAME, controllerProperties);

        assertTrue(PolicyController.factory.inventory().size() == 1);

        gson.compareGson(PolicyEngine.manager, new File(PolicyEngineTest.class.getSimpleName() + "Add.json"));
    }

    @Test
    public void test401ControllerVerify() {
        logger.info("enter");

        final PolicyController testController = PolicyController.factory.get(TEST_CONTROLLER_NAME);

        assertFalse(testController.isAlive());
        assertFalse(testController.isLocked());

        testController.start();

        assertTrue(testController.isAlive());
        assertFalse(testController.isLocked());
    }

    @Test
    public void test500Deactivate() throws Exception {
        logger.info("enter");

        PolicyEngine.manager.deactivate();

        final PolicyController testController = PolicyController.factory.get(TEST_CONTROLLER_NAME);
        assertFalse(testController.isAlive());
        assertTrue(testController.isLocked());
        assertTrue(PolicyEngine.manager.isLocked());
        assertTrue(PolicyEngine.manager.isAlive());
    }

    @Test
    public void test501Activate() throws Exception {
        logger.info("enter");

        PolicyEngine.manager.activate();

        final PolicyController testController = PolicyController.factory.get(TEST_CONTROLLER_NAME);
        assertTrue(testController.isAlive());
        assertFalse(testController.isLocked());
        assertFalse(PolicyEngine.manager.isLocked());
        assertTrue(PolicyEngine.manager.isAlive());
    }

    @Test
    public void test900ControllerRemove() throws Exception {
        logger.info("enter");

        PolicyEngine.manager.removePolicyController(TEST_CONTROLLER_NAME);
        assertTrue(PolicyController.factory.inventory().isEmpty());
    }

    @Test
    public void test901Stop() throws InterruptedException {
        logger.info("enter");

        /* Shutdown managed resources */
        PolicyController.factory.shutdown();
        TopicEndpoint.manager.shutdown();
        PolicyEngine.manager.stop();

        Thread.sleep(10000L);
        assertFalse(PolicyEngine.manager.isAlive());
    }
}
