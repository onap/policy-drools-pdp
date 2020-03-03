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

package org.onap.policy.drools.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.onap.policy.drools.properties.DroolsPropertyConstants;

/**
 * (File) System Persistence Tests.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SystemPersistenceTest {
    /**
     * sample configuration dir.
     */
    private static final String OTHER_CONFIG_DIR = "tmp";

    /**
     * Test JUnit Controller Name.
     */
    private static final String TEST_CONTROLLER_NAME = "foo";


    /**
     * Test JUnit Topic Name.
     */
    private static final String TEST_TOPIC_NAME = TEST_CONTROLLER_NAME;

    /**
     * Test JUnit HTTP Server Name.
     */
    private static final String TEST_HTTP_SERVER_NAME = TEST_CONTROLLER_NAME;

    /**
     * Test JUnit HTTP Client Name.
     */
    private static final String TEST_HTTP_CLIENT_NAME = TEST_CONTROLLER_NAME;

    /**
     * Test JUnit Controller File.
     */
    private static final String TEST_CONTROLLER_FILE = TEST_CONTROLLER_NAME + "-controller.properties";

    /**
     * Test JUnit Controller Backup File.
     */
    private static final String TEST_CONTROLLER_FILE_BAK = TEST_CONTROLLER_FILE + ".bak";

    /**
     * Test JUnit Topic File.
     */
    private static final String TEST_TOPIC_FILE = TEST_CONTROLLER_NAME + "-topic.properties";

    /**
     * Test JUnit Controller Name Backup.
     */
    private static final String TEST_TOPIC_FILE_BAK = TEST_TOPIC_FILE + ".bak";

    /**
     * Test JUnit Http Server File.
     */
    private static final String TEST_HTTP_SERVER_FILE = TEST_CONTROLLER_NAME
                            + FileSystemPersistence.PROPERTIES_FILE_HTTP_SERVER_SUFFIX;

    /**
     * Test JUnit Backup Http Server File.
     */
    private static final String TEST_HTTP_SERVER_FILE_BAK = TEST_HTTP_SERVER_FILE + ".bak";

    /**
     * Test JUnit Http Client File.
     */
    private static final String TEST_HTTP_CLIENT_FILE = TEST_CONTROLLER_NAME
                            + FileSystemPersistence.PROPERTIES_FILE_HTTP_CLIENT_SUFFIX;

    /**
     * Test JUnit Backup Http Server File.
     */
    private static final String TEST_HTTP_CLIENT_FILE_BAK = TEST_HTTP_CLIENT_FILE + ".bak";

    /**
     * Test JUnit Environment/Engine properties.
     */
    private static final String ENV_PROPS = TEST_CONTROLLER_NAME;
    private static final String ENV_PROPS_FILE = ENV_PROPS + ".environment";

    /**
     * Test JUnit system properties.
     */
    private static final String SYSTEM_PROPS = TEST_CONTROLLER_NAME;
    private static final String SYSTEM_PROPS_FILE =  SYSTEM_PROPS + "-system.properties";

    @BeforeClass
    public static void setUp() throws IOException {
        cleanUpWorkingDirs();
    }

    @AfterClass
    public static void tearDown() throws IOException {
        cleanUpWorkingDirs();
    }

    @Test
    public void test1NonDefaultConfigDir() {
        SystemPersistenceConstants.getManager().setConfigurationDir(OTHER_CONFIG_DIR);
        assertEquals(OTHER_CONFIG_DIR, SystemPersistenceConstants.getManager().getConfigurationPath().toString());

        SystemPersistenceConstants.getManager().setConfigurationDir(null);
        assertEquals(SystemPersistenceConstants.DEFAULT_CONFIGURATION_DIR,
                SystemPersistenceConstants.getManager().getConfigurationPath().toString());

        SystemPersistenceConstants.getManager().setConfigurationDir();
        assertEquals(SystemPersistenceConstants.DEFAULT_CONFIGURATION_DIR,
                SystemPersistenceConstants.getManager().getConfigurationPath().toString());
    }

    @Test
    public void test2Engine_Environment_System() throws IOException {
        SystemPersistenceConstants.getManager().setConfigurationDir(OTHER_CONFIG_DIR);

        final Path policyEnginePropsPath =
                Paths.get(SystemPersistenceConstants.getManager().getConfigurationPath().toString(),
                        FileSystemPersistence.PROPERTIES_FILE_ENGINE);

        final Properties engineProps = new Properties();
        engineProps.setProperty("foo", "bar");
        engineProps.setProperty("fiz", "buz");
        if (Files.notExists(policyEnginePropsPath)) {
            try (final OutputStream fout = new FileOutputStream(policyEnginePropsPath.toFile())) {
                engineProps.store(fout, "");
            }
        }

        assertEquals(engineProps, SystemPersistenceConstants.getManager().getEngineProperties());

        final Path environmentPropertiesPath =
                Paths.get(SystemPersistenceConstants.getManager().getConfigurationPath().toString(), ENV_PROPS_FILE);
        if (Files.notExists(environmentPropertiesPath)) {
            Files.createFile(environmentPropertiesPath);
        }
        assertNotNull(SystemPersistenceConstants.getManager().getEnvironmentProperties(ENV_PROPS));
        assertTrue(SystemPersistenceConstants.getManager().getEnvironmentProperties(ENV_PROPS).isEmpty());
        assertEquals(1, SystemPersistenceConstants.getManager().getEnvironmentProperties().size());
        assertEquals(SystemPersistenceConstants.getManager().getEnvironmentProperties(ENV_PROPS),
                     SystemPersistenceConstants.getManager().getEnvironmentProperties().get(0));

        Path systemPropertiesPath =
            Paths.get(SystemPersistenceConstants.getManager().getConfigurationPath().toString(), SYSTEM_PROPS_FILE);
        if (Files.notExists(systemPropertiesPath)) {
            Files.createFile(systemPropertiesPath);
        }
        assertNotNull(SystemPersistenceConstants.getManager().getSystemProperties(SYSTEM_PROPS));
        assertTrue(SystemPersistenceConstants.getManager().getSystemProperties(SYSTEM_PROPS).isEmpty());
        assertEquals(1, SystemPersistenceConstants.getManager().getSystemProperties().size());
        assertEquals(SystemPersistenceConstants.getManager().getSystemProperties(SYSTEM_PROPS),
                     SystemPersistenceConstants.getManager().getSystemProperties().get(0));
    }

    @Test
    public void test3Topic() {
        SystemPersistenceConstants.getManager().setConfigurationDir(null);

        Path topicPath = Paths
            .get(SystemPersistenceConstants.getManager().getConfigurationPath().toString(), TEST_TOPIC_FILE);

        Path topicBakPath = Paths
            .get(SystemPersistenceConstants.getManager().getConfigurationPath().toString(), TEST_TOPIC_FILE_BAK);

        assertTrue(Files.notExists(topicPath));
        assertTrue(Files.notExists(topicBakPath));

        SystemPersistenceConstants.getManager().storeTopic(TEST_TOPIC_NAME, new Properties());

        assertTrue(Files.exists(topicPath));

        Properties properties = SystemPersistenceConstants.getManager().getTopicProperties(TEST_TOPIC_NAME);
        assertNotNull(properties);

        List<Properties> topicPropsList = SystemPersistenceConstants.getManager().getTopicProperties();
        assertEquals(1,  topicPropsList.size());

        SystemPersistenceConstants.getManager().backupTopic(TEST_TOPIC_NAME);
        assertTrue(Files.exists(topicBakPath));

        SystemPersistenceConstants.getManager().deleteTopic(TEST_TOPIC_NAME);
        assertTrue(Files.notExists(topicPath));
    }

    @Test
    public void test4HttpServer() {
        SystemPersistenceConstants.getManager().setConfigurationDir(null);

        Path httpServerPath = Paths
            .get(SystemPersistenceConstants.getManager().getConfigurationPath().toString(), TEST_HTTP_SERVER_FILE);

        Path httpServerBakPath = Paths
            .get(SystemPersistenceConstants.getManager().getConfigurationPath().toString(), TEST_HTTP_SERVER_FILE_BAK);

        assertTrue(Files.notExists(httpServerPath));
        assertTrue(Files.notExists(httpServerBakPath));

        SystemPersistenceConstants.getManager().storeHttpServer(TEST_HTTP_SERVER_NAME, new Properties());

        assertTrue(Files.exists(httpServerPath));

        Properties properties = SystemPersistenceConstants.getManager().getHttpServerProperties(TEST_HTTP_SERVER_NAME);
        assertNotNull(properties);

        List<Properties> httpServerPropsList = SystemPersistenceConstants.getManager().getHttpServerProperties();
        assertEquals(1,  httpServerPropsList.size());

        SystemPersistenceConstants.getManager().backupHttpServer(TEST_HTTP_SERVER_NAME);
        assertTrue(Files.exists(httpServerBakPath));

        SystemPersistenceConstants.getManager().deleteHttpServer(TEST_HTTP_SERVER_NAME);
        assertTrue(Files.notExists(httpServerPath));
    }

    @Test
    public void test5HttpClient() {
        SystemPersistenceConstants.getManager().setConfigurationDir(null);

        Path httpClientPath = Paths
            .get(SystemPersistenceConstants.getManager().getConfigurationPath().toString(), TEST_HTTP_CLIENT_FILE);

        Path httpClientBakPath = Paths
            .get(SystemPersistenceConstants.getManager().getConfigurationPath().toString(), TEST_HTTP_CLIENT_FILE_BAK);

        assertTrue(Files.notExists(httpClientPath));
        assertTrue(Files.notExists(httpClientBakPath));

        SystemPersistenceConstants.getManager().storeHttpClient(TEST_HTTP_CLIENT_NAME, new Properties());

        assertTrue(Files.exists(httpClientPath));

        Properties properties = SystemPersistenceConstants.getManager().getHttpClientProperties(TEST_HTTP_CLIENT_NAME);
        assertNotNull(properties);

        List<Properties> httpClientPropsList = SystemPersistenceConstants.getManager().getHttpClientProperties();
        assertEquals(1,  httpClientPropsList.size());

        SystemPersistenceConstants.getManager().backupHttpClient(TEST_HTTP_CLIENT_NAME);
        assertTrue(Files.exists(httpClientBakPath));

        SystemPersistenceConstants.getManager().deleteHttpClient(TEST_HTTP_CLIENT_NAME);
        assertTrue(Files.notExists(httpClientPath));
    }

    @Test
    public void test6Controller() {
        SystemPersistenceConstants.getManager().setConfigurationDir(null);

        Path controllerPath = Paths
                .get(SystemPersistenceConstants.getManager().getConfigurationPath().toString(), TEST_CONTROLLER_FILE);

        Path controllerBakPath = Paths.get(SystemPersistenceConstants.getManager().getConfigurationPath().toString(),
                        TEST_CONTROLLER_FILE_BAK);

        assertTrue(Files.notExists(controllerPath));
        assertTrue(Files.notExists(controllerBakPath));

        SystemPersistenceConstants.getManager().storeController(TEST_CONTROLLER_NAME, new Properties());

        assertTrue(Files.exists(controllerPath));

        Properties properties = SystemPersistenceConstants.getManager().getControllerProperties(TEST_CONTROLLER_NAME);
        assertNotNull(properties);

        List<Properties> controllerPropsList = SystemPersistenceConstants.getManager().getControllerProperties();
        assertEquals(1,  controllerPropsList.size());
        assertEquals(TEST_CONTROLLER_NAME, controllerPropsList
                     .get(0).getProperty(DroolsPropertyConstants.PROPERTY_CONTROLLER_NAME));

        SystemPersistenceConstants.getManager().backupController(TEST_CONTROLLER_NAME);
        assertTrue(Files.exists(controllerBakPath));

        SystemPersistenceConstants.getManager().deleteController(TEST_CONTROLLER_NAME);
        assertTrue(Files.notExists(controllerPath));
    }

    /**
     * Clean up the working directories.
     *
     * @throws IOException throws IO exception
     */
    private static void cleanUpWorkingDirs() throws IOException {
        SystemPersistenceConstants.getManager().setConfigurationDir(null);

        for (Properties properties : SystemPersistenceConstants.getManager().getControllerProperties()) {
            SystemPersistenceConstants.getManager().deleteController(properties
                        .getProperty(DroolsPropertyConstants.PROPERTY_CONTROLLER_NAME));
        }

        SystemPersistenceConstants.getManager().deleteTopic(TEST_TOPIC_NAME);
        SystemPersistenceConstants.getManager().deleteHttpServer(TEST_HTTP_SERVER_NAME);
        SystemPersistenceConstants.getManager().deleteHttpClient(TEST_HTTP_CLIENT_NAME);

        final Path testControllerBakPath =
                        Paths.get(SystemPersistenceConstants.getManager().getConfigurationPath().toString(),
                                        TEST_CONTROLLER_FILE_BAK);

        final Path testTopicBakPath = Paths
            .get(SystemPersistenceConstants.getManager().getConfigurationPath().toString(), TEST_TOPIC_FILE_BAK);
        final Path testHttpServerBakPath = Paths
            .get(SystemPersistenceConstants.getManager().getConfigurationPath().toString(), TEST_HTTP_SERVER_FILE_BAK);
        final Path testHttpClientBakPath = Paths
            .get(SystemPersistenceConstants.getManager().getConfigurationPath().toString(), TEST_HTTP_CLIENT_FILE_BAK);

        final Path policyEnginePath = Paths.get(OTHER_CONFIG_DIR, FileSystemPersistence.PROPERTIES_FILE_ENGINE);
        final Path environmentPath = Paths.get(OTHER_CONFIG_DIR, ENV_PROPS_FILE);
        final Path systemPath = Paths.get(OTHER_CONFIG_DIR, SYSTEM_PROPS_FILE);

        Files.deleteIfExists(testControllerBakPath);
        Files.deleteIfExists(testTopicBakPath);
        Files.deleteIfExists(testHttpServerBakPath);
        Files.deleteIfExists(testHttpClientBakPath);
        Files.deleteIfExists(policyEnginePath);
        Files.deleteIfExists(environmentPath);
        Files.deleteIfExists(systemPath);
    }

}
