/*-
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2017 AT&T Intellectual Property. All rights reserved.
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
package org.onap.policy.drools.persistence.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.onap.policy.drools.persistence.FileSystemPersistence;
import org.onap.policy.drools.persistence.SystemPersistence;
import org.onap.policy.drools.properties.PolicyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * (File) System Persistence Tests
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SystemPersistenceTest {

  /**
   * logger
   */
  private static final Logger logger = LoggerFactory.getLogger(SystemPersistenceTest.class);

  /**
   * sample configuration dir
   */
  public static final String OTHER_CONFIG_DIR = "tmp";

  /**
   * Test JUnit Controller Name
   */
  public static final String TEST_CONTROLLER_NAME = "foo";

  /**
   * Test JUnit Controller Name
   */
  public static final String TEST_CONTROLLER_FILE = TEST_CONTROLLER_NAME + "-controller.properties";

  /**
   * Test JUnit Controller Name Backup
   */
  public static final String TEST_CONTROLLER_FILE_BAK = TEST_CONTROLLER_FILE + ".bak";

  /**
   * Test JUnit Environment/Engine properties
   */
  private static final String ENV_PROPS = "envProps";
  private static final String ENV_PROPS_FILE = ENV_PROPS + ".environment";
  private static final String POLICY_ENGINE_PROPERTIES_FILE = "policy-engine.properties";

  @BeforeClass
  public static void setUp() throws IOException {
    cleanUpWorkingDirs();
  }

  @AfterClass
  public static void tearDown() throws IOException {
    cleanUpWorkingDirs();
  }

  @Test
  public void test1NonDefaultConfigDir() throws IOException {
    logger.info("enter");

    SystemPersistence.manager.setConfigurationDir(OTHER_CONFIG_DIR);
    assertTrue(
        SystemPersistence.manager.getConfigurationPath().toString().equals(OTHER_CONFIG_DIR));

    SystemPersistence.manager.setConfigurationDir(null);
    assertTrue(SystemPersistence.manager.getConfigurationPath().toString()
        .equals(SystemPersistence.DEFAULT_CONFIGURATION_DIR));
  }

  @Test
  public void test2Engine() throws IOException {
    logger.info("enter");

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

    assertEquals(SystemPersistence.manager.getEngineProperties(), engineProps);

    final Path environmentPropertiesPath =
        Paths.get(SystemPersistence.manager.getConfigurationPath().toString(), ENV_PROPS_FILE);
    if (Files.notExists(environmentPropertiesPath)) {
      Files.createFile(environmentPropertiesPath);
    }
    assertTrue(SystemPersistence.manager.getEnvironmentProperties(ENV_PROPS).isEmpty());
    assertTrue(SystemPersistence.manager.getEnvironmentProperties().size() == 1);
  }

  @Test
  public void test3PersistConfiguration() {
    logger.info("enter");

    SystemPersistence.manager.setConfigurationDir(null);

    final Path controllerPath = Paths
        .get(SystemPersistence.manager.getConfigurationPath().toString(), TEST_CONTROLLER_FILE);

    final Path controllerBakPath = Paths
        .get(SystemPersistence.manager.getConfigurationPath().toString(), TEST_CONTROLLER_FILE_BAK);

    assertTrue(Files.notExists(controllerPath));
    assertTrue(Files.notExists(controllerBakPath));

    Properties properties = new Properties();
    properties.put(PolicyProperties.PROPERTY_CONTROLLER_NAME, TEST_CONTROLLER_NAME);
    SystemPersistence.manager.storeController(TEST_CONTROLLER_NAME, properties);

    assertTrue(Files.exists(controllerPath));

    properties = SystemPersistence.manager.getControllerProperties(TEST_CONTROLLER_NAME);
    assertTrue(properties != null);

    SystemPersistence.manager.backupController(TEST_CONTROLLER_NAME);
    assertTrue(Files.exists(controllerBakPath));

    assertFalse(SystemPersistence.manager.getControllerProperties().isEmpty());

    SystemPersistence.manager.deleteController(TEST_CONTROLLER_NAME);
    assertTrue(Files.notExists(controllerPath));
  }

  public static void cleanUpWorkingDirs() throws IOException {

    SystemPersistence.manager.setConfigurationDir(null);

    final Path testControllerPath = Paths
        .get(SystemPersistence.manager.getConfigurationPath().toString(), TEST_CONTROLLER_FILE);
    final Path testControllerBakPath = Paths
        .get(SystemPersistence.manager.getConfigurationPath().toString(), TEST_CONTROLLER_FILE_BAK);

    final Path policyEnginePath = Paths.get(OTHER_CONFIG_DIR, POLICY_ENGINE_PROPERTIES_FILE);
    final Path environmentPath = Paths.get(OTHER_CONFIG_DIR, ENV_PROPS_FILE);

    Files.deleteIfExists(testControllerPath);
    Files.deleteIfExists(testControllerBakPath);
    Files.deleteIfExists(policyEnginePath);
    Files.deleteIfExists(environmentPath);
  }

}
