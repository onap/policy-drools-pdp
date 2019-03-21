/*-
 * ============LICENSE_START=======================================================
 * ONAP
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
import org.onap.policy.drools.properties.DroolsProperties;

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
     * Test JUnit Controller File.
     */
    private static final String TEST_CONTROLLER_FILE = TEST_CONTROLLER_NAME + "-controller.properties";

    /**
     * Test JUnit Controller Backup File
     */
    private static final String TEST_CONTROLLER_FILE_BAK = TEST_CONTROLLER_FILE + ".bak";

    /**
     * Test JUnit Topic File
     */
    private static final String TEST_TOPIC_FILE = TEST_CONTROLLER_NAME + "-topic.properties";

    /**
     * Test JUnit Controller Name Backup.
     */
    private static final String TEST_TOPIC_FILE_BAK = TEST_TOPIC_FILE + ".bak";

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
        SystemPersistence.manager.setConfigurationDir(OTHER_CONFIG_DIR);
        assertEquals(OTHER_CONFIG_DIR, SystemPersistence.manager.getConfigurationPath().toString());

        SystemPersistence.manager.setConfigurationDir(null);
        assertEquals(SystemPersistence.DEFAULT_CONFIGURATION_DIR,
                SystemPersistence.manager.getConfigurationPath().toString());

        SystemPersistence.manager.setConfigurationDir();
        assertEquals(SystemPersistence.DEFAULT_CONFIGURATION_DIR,
                SystemPersistence.manager.getConfigurationPath().toString());
    }

    @Test
    public void test2Engine_Environment_System() throws IOException {
        SystemPersistence.manager.setConfigurationDir(OTHER_CONFIG_DIR);

        final Path policyEnginePropsPath =
                Paths.get(SystemPersistence.manager.getConfigurationPath().toString(),
                        FileSystemPersistence.PROPERTIES_FILE_ENGINE);

        final Properties engineProps = new Properties();
        engineProps.setProperty("foo", "bar");
        engineProps.setProperty("fiz", "buz");
        if (Files.notExists(policyEnginePropsPath)) {
            try (final OutputStream fout = new FileOutputStream(policyEnginePropsPath.toFile())) {
                engineProps.store(fout, "");
            }
        }

        assertEquals(engineProps, SystemPersistence.manager.getEngineProperties());

        final Path environmentPropertiesPath =
                Paths.get(SystemPersistence.manager.getConfigurationPath().toString(), ENV_PROPS_FILE);
        if (Files.notExists(environmentPropertiesPath)) {
            Files.createFile(environmentPropertiesPath);
        }
        assertNotNull(SystemPersistence.manager.getEnvironmentProperties(ENV_PROPS));
        assertTrue(SystemPersistence.manager.getEnvironmentProperties(ENV_PROPS).isEmpty());
        assertEquals(1, SystemPersistence.manager.getEnvironmentProperties().size());
        assertEquals(SystemPersistence.manager.getEnvironmentProperties(ENV_PROPS),
                     SystemPersistence.manager.getEnvironmentProperties().get(0));

        Path systemPropertiesPath =
            Paths.get(SystemPersistence.manager.getConfigurationPath().toString(), SYSTEM_PROPS_FILE);
        if (Files.notExists(systemPropertiesPath)) {
            Files.createFile(systemPropertiesPath);
        }
        assertNotNull(SystemPersistence.manager.getSystemProperties(SYSTEM_PROPS));
        assertTrue(SystemPersistence.manager.getSystemProperties(SYSTEM_PROPS).isEmpty());
        assertEquals(1, SystemPersistence.manager.getSystemProperties().size());
        assertEquals(SystemPersistence.manager.getSystemProperties(SYSTEM_PROPS),
                     SystemPersistence.manager.getSystemProperties().get(0));
    }

    @Test
    public void test3Topic() {
        SystemPersistence.manager.setConfigurationDir(null);

        Path topicPath = Paths
            .get(SystemPersistence.manager.getConfigurationPath().toString(), TEST_TOPIC_FILE);

        Path topicBakPath = Paths
            .get(SystemPersistence.manager.getConfigurationPath().toString(), TEST_TOPIC_FILE_BAK);

        assertTrue(Files.notExists(topicPath));
        assertTrue(Files.notExists(topicBakPath));

        SystemPersistence.manager.storeTopic(TEST_TOPIC_NAME, new Properties());

        assertTrue(Files.exists(topicPath));

        Properties properties = SystemPersistence.manager.getTopicProperties(TEST_TOPIC_NAME);
        assertNotNull(properties);

        List<Properties> topicPropsList = SystemPersistence.manager.getTopicProperties();
        assertEquals(1,  topicPropsList.size());

        SystemPersistence.manager.backupTopic(TEST_TOPIC_NAME);
        assertTrue(Files.exists(topicBakPath));

        SystemPersistence.manager.deleteTopic(TEST_TOPIC_NAME);
        assertTrue(Files.notExists(topicPath));
    }

    @Test
    public void test4Controller() {
        SystemPersistence.manager.setConfigurationDir(null);

        Path controllerPath = Paths
                .get(SystemPersistence.manager.getConfigurationPath().toString(), TEST_CONTROLLER_FILE);

        Path controllerBakPath = Paths
                .get(SystemPersistence.manager.getConfigurationPath().toString(), TEST_CONTROLLER_FILE_BAK);

        assertTrue(Files.notExists(controllerPath));
        assertTrue(Files.notExists(controllerBakPath));

        SystemPersistence.manager.storeController(TEST_CONTROLLER_NAME, new Properties());

        assertTrue(Files.exists(controllerPath));

        Properties properties = SystemPersistence.manager.getControllerProperties(TEST_CONTROLLER_NAME);
        assertNotNull(properties);

        List<Properties> controllerPropsList = SystemPersistence.manager.getControllerProperties();
        assertEquals(1,  controllerPropsList.size());
        assertEquals(TEST_CONTROLLER_NAME, controllerPropsList
                     .get(0).getProperty(DroolsProperties.PROPERTY_CONTROLLER_NAME));

        SystemPersistence.manager.backupController(TEST_CONTROLLER_NAME);
        assertTrue(Files.exists(controllerBakPath));

        SystemPersistence.manager.deleteController(TEST_CONTROLLER_NAME);
        assertTrue(Files.notExists(controllerPath));
    }

    /**
     * Clean up the working directories.
     * 
     * @throws IOException throws IO exception
     */
    private static void cleanUpWorkingDirs() throws IOException {
        SystemPersistence.manager.setConfigurationDir(null);

        for (Properties properties : SystemPersistence.manager.getControllerProperties()) {
            SystemPersistence.manager.deleteController(properties
                        .getProperty(DroolsProperties.PROPERTY_CONTROLLER_NAME));
        }

        SystemPersistence.manager.deleteTopic(TEST_TOPIC_NAME);

        final Path testControllerBakPath = Paths
                .get(SystemPersistence.manager.getConfigurationPath().toString(), TEST_CONTROLLER_FILE_BAK);

        final Path testTopicBakPath = Paths
            .get(SystemPersistence.manager.getConfigurationPath().toString(), TEST_TOPIC_FILE_BAK);

        final Path policyEnginePath = Paths.get(OTHER_CONFIG_DIR, FileSystemPersistence.PROPERTIES_FILE_ENGINE);
        final Path environmentPath = Paths.get(OTHER_CONFIG_DIR, ENV_PROPS_FILE);
        final Path systemPath = Paths.get(OTHER_CONFIG_DIR, SYSTEM_PROPS_FILE);

        Files.deleteIfExists(testControllerBakPath);
        Files.deleteIfExists(testTopicBakPath);
        Files.deleteIfExists(policyEnginePath);
        Files.deleteIfExists(environmentPath);
        Files.deleteIfExists(systemPath);
    }

}
