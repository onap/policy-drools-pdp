/*-
 * ============LICENSE_START=======================================================
 * feature-test-transaction
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

package org.onap.policy.drools.testtransaction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.Set;

import org.junit.BeforeClass;
import org.junit.Test;
import org.onap.policy.drools.persistence.SystemPersistence;
import org.onap.policy.drools.properties.PolicyProperties;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.drools.system.PolicyEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestTransactionTest {
  /**
   * Test JUnit Controller Name
   */
  public static final String TEST_CONTROLLER_NAME = "unnamed";
  /**
   * Controller Configuration File
   */
  public static final String TEST_CONTROLLER_FILE = TEST_CONTROLLER_NAME + "-controller.properties";

  /**
   * Controller Configuration Backup File
   */
  public static final String TEST_CONTROLLER_FILE_BAK =
      TEST_CONTROLLER_NAME + "-controller.properties.bak";


  /**
   * logger
   */
  private static Logger logger = LoggerFactory.getLogger(TestTransactionTest.class);



  @BeforeClass
  public static void startUp() throws IOException {
    logger.info("enter");

    cleanUpWorkingDir();

    /* ensure presence of config directory */
    SystemPersistence.manager.setConfigurationDir(null);
  }

  @Test
  public void registerUnregisterTest() {
    final Properties controllerProperties = new Properties();
    controllerProperties.put(PolicyProperties.PROPERTY_CONTROLLER_NAME, TEST_CONTROLLER_NAME);
    final PolicyController controller =
        PolicyEngine.manager.createPolicyController(TEST_CONTROLLER_NAME, controllerProperties);
    Thread ttThread = null;

    TestTransaction.manager.register(controller);
    assertNotNull(TestTransaction.manager);

    /*
     * If the controller was successfully registered it will have a thread created.
     */
    ttThread = this.getThread("tt-controller-task-" + TEST_CONTROLLER_NAME);
    assertNotNull(ttThread);

    /*
     * Unregistering the controller should terminate its TestTransaction thread if it hasn't already
     * been terminated
     */
    TestTransaction.manager.unregister(controller);

    /*
     * Put this thread to sleep so the TestTransaction thread has enough time to terminate before we
     * check.
     */
    try {
      Thread.sleep(2000);
    } catch (final InterruptedException e) {

    }
    ttThread = this.getThread("tt-controller-task-" + TEST_CONTROLLER_NAME);
    assertEquals(null, ttThread);


  }

  /*
   * Returns thread object based on String name
   */
  public Thread getThread(String threadName) {

    final Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
    for (final Thread thread : threadSet) {
      if (thread.getName().equals(threadName)) {
        return thread;
      }

    }
    return null;
  }

  /**
   * clean up working directory
   */
  protected static void cleanUpWorkingDir() {
    final Path testControllerPath = Paths
        .get(SystemPersistence.manager.getConfigurationPath().toString(), TEST_CONTROLLER_FILE);
    try {
      Files.deleteIfExists(testControllerPath);
    } catch (final Exception e) {
      logger.info("Problem cleaning {}", testControllerPath, e);
    }

    final Path testControllerBakPath = Paths
        .get(SystemPersistence.manager.getConfigurationPath().toString(), TEST_CONTROLLER_FILE_BAK);
    try {
      Files.deleteIfExists(testControllerBakPath);
    } catch (final Exception e) {
      logger.info("Problem cleaning {}", testControllerBakPath, e);
    }
  }

}
