/*-
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2017-2020 AT&T Intellectual Property. All rights reserved.
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

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.onap.policy.common.endpoints.event.comm.TopicEndpointManager;
import org.onap.policy.common.endpoints.event.comm.TopicSink;
import org.onap.policy.common.endpoints.event.comm.bus.NoopTopicFactories;
import org.onap.policy.common.endpoints.properties.PolicyEndPointProperties;
import org.onap.policy.common.utils.gson.GsonTestUtils;
import org.onap.policy.drools.persistence.SystemPersistenceConstants;
import org.onap.policy.drools.properties.DroolsPropertyConstants;
import org.onap.policy.drools.protocol.coders.EventProtocolCoderConstants;
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
                        Paths.get(SystemPersistenceConstants.getManager().getConfigurationPath().toString(),
                                        TEST_CONTROLLER_FILE);
        try {
            Files.deleteIfExists(testControllerPath);
        } catch (final Exception e) {
            logger.info("Problem cleaning {}", testControllerPath, e);
        }

        final Path testControllerBakPath =
                        Paths.get(SystemPersistenceConstants.getManager().getConfigurationPath().toString(),
                                        TEST_CONTROLLER_FILE_BAK);
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
        final Path configDir = Paths.get(SystemPersistenceConstants.DEFAULT_CONFIGURATION_DIR);
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

        final Properties engineProps = PolicyEngineConstants.getManager().defaultTelemetryConfig();

        /* override default port */
        engineProps.put(PolicyEndPointProperties.PROPERTY_HTTP_SERVER_SERVICES + "."
                        + PolicyEngineConstants.TELEMETRY_SERVER_DEFAULT_NAME
                        + PolicyEndPointProperties.PROPERTY_HTTP_PORT_SUFFIX, "" + DEFAULT_TELEMETRY_PORT);

        assertFalse(PolicyEngineConstants.getManager().isAlive());
        PolicyEngineConstants.getManager().configure(engineProps);
        assertFalse(PolicyEngineConstants.getManager().isAlive());

        logger.info("engine {} has configuration {}", PolicyEngineConstants.getManager(), engineProps);

        gson.compareGson(PolicyEngineConstants.getManager(),
                        new File(PolicyEngineTest.class.getSimpleName() + "Config.json"));
    }

    @Test
    public void test200Start() {
        logger.info("enter");

        PolicyEngineConstants.getManager().start();

        assertTrue(PolicyEngineConstants.getManager().isAlive());
        assertFalse(PolicyEngineConstants.getManager().isLocked());
        assertFalse(PolicyEngineConstants.getManager().getHttpServers().isEmpty());
        assertTrue(PolicyEngineConstants.getManager().getHttpServers().get(0).isAlive());
    }

    @Test
    public void test300Lock() {
        logger.info("enter");

        PolicyEngineConstants.getManager().lock();

        assertTrue(PolicyEngineConstants.getManager().isAlive());
        assertTrue(PolicyEngineConstants.getManager().isLocked());
        assertFalse(PolicyEngineConstants.getManager().getHttpServers().isEmpty());
        assertTrue(PolicyEngineConstants.getManager().getHttpServers().get(0).isAlive());
    }

    @Test
    public void test301Unlock() {
        logger.info("enter");

        PolicyEngineConstants.getManager().unlock();

        assertTrue(PolicyEngineConstants.getManager().isAlive());
        assertFalse(PolicyEngineConstants.getManager().isLocked());
        assertFalse(PolicyEngineConstants.getManager().getHttpServers().isEmpty());
        assertTrue(PolicyEngineConstants.getManager().getHttpServers().get(0).isAlive());
    }

    @Test
    public void test350TopicDeliver() {
        final Properties noopSinkProperties = new Properties();
        noopSinkProperties.put(PolicyEndPointProperties.PROPERTY_NOOP_SINK_TOPICS, NOOP_TOPIC);

        TopicEndpointManager.getManager().addTopicSinks(noopSinkProperties).get(0).start();

        EventProtocolCoderConstants.getManager().addEncoder(
                EventProtocolParams.builder().groupId(ENCODER_GROUP).artifactId(ENCODER_ARTIFACT)
                        .topic(NOOP_TOPIC).eventClass(DroolsConfiguration.class.getName())
                        .protocolFilter(new JsonProtocolFilter()).customGsonCoder(null)
                        .modelClassLoaderHash(DroolsConfiguration.class.getName().hashCode()));

        assertTrue(PolicyEngineConstants.getManager().deliver(NOOP_TOPIC,
                new DroolsConfiguration(ENCODER_ARTIFACT, ENCODER_GROUP, ENCODER_VERSION)));

        final TopicSink sink = NoopTopicFactories.getSinkFactory().get(NOOP_TOPIC);
        assertTrue(sink.getRecentEvents()[0].contains(ENCODER_GROUP));
        assertTrue(sink.getRecentEvents()[0].contains(ENCODER_ARTIFACT));

        EventProtocolCoderConstants.getManager().removeEncoders(ENCODER_GROUP, ENCODER_ARTIFACT, NOOP_TOPIC);
    }

    @Test
    public void test400ControllerAdd() throws Exception {
        logger.info("enter");

        final Properties controllerProperties = new Properties();
        controllerProperties.put(DroolsPropertyConstants.PROPERTY_CONTROLLER_NAME, TEST_CONTROLLER_NAME);
        PolicyEngineConstants.getManager().createPolicyController(TEST_CONTROLLER_NAME, controllerProperties);

        assertEquals(1, PolicyControllerConstants.getFactory().inventory().size());

        gson.compareGson(PolicyEngineConstants.getManager(),
                        new File(PolicyEngineTest.class.getSimpleName() + "Add.json"));
    }

    @Test
    public void test401ControllerVerify() {
        logger.info("enter");

        final PolicyController testController = PolicyControllerConstants.getFactory().get(TEST_CONTROLLER_NAME);

        assertFalse(testController.isAlive());
        assertFalse(testController.isLocked());

        testController.start();

        assertTrue(testController.isAlive());
        assertFalse(testController.isLocked());
    }

    @Test
    public void test500Deactivate() throws Exception {
        logger.info("enter");

        PolicyEngineConstants.getManager().deactivate();

        final PolicyController testController = PolicyControllerConstants.getFactory().get(TEST_CONTROLLER_NAME);
        assertFalse(testController.isAlive());
        assertTrue(testController.isLocked());
        assertTrue(PolicyEngineConstants.getManager().isLocked());
        assertTrue(PolicyEngineConstants.getManager().isAlive());
    }

    @Test
    public void test501Activate() throws Exception {
        logger.info("enter");

        PolicyEngineConstants.getManager().activate();

        final PolicyController testController = PolicyControllerConstants.getFactory().get(TEST_CONTROLLER_NAME);
        assertTrue(testController.isAlive());
        assertFalse(testController.isLocked());
        assertFalse(PolicyEngineConstants.getManager().isLocked());
        assertTrue(PolicyEngineConstants.getManager().isAlive());
    }

    @Test
    public void test900ControllerRemove() throws Exception {
        logger.info("enter");

        PolicyEngineConstants.getManager().removePolicyController(TEST_CONTROLLER_NAME);
        assertTrue(PolicyControllerConstants.getFactory().inventory().isEmpty());
    }

    @Test
    public void test901Stop() throws InterruptedException {
        logger.info("enter");

        /* Shutdown managed resources */
        PolicyControllerConstants.getFactory().shutdown();
        TopicEndpointManager.getManager().shutdown();
        PolicyEngineConstants.getManager().stop();

        await().atMost(10, TimeUnit.SECONDS).until(() -> !PolicyEngineConstants.getManager().isAlive());
        assertFalse(PolicyEngineConstants.getManager().isAlive());
    }
}
