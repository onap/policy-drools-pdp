/*-
 * ============LICENSE_START=======================================================
 * policy-management
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

package org.onap.policy.drools.system;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

import org.onap.policy.drools.persistence.SystemPersistence;
import org.onap.policy.drools.properties.PolicyProperties;
import org.onap.policy.drools.utils.LoggerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Programmatic entry point to the management layer
 */
public class Main {

  /**
   * logback configuration file system property
   */
  public static final String LOGBACK_CONFIGURATION_FILE_SYSTEM_PROPERTY =
      "logback.configurationFile";

  /**
   * logback configuration file system property
   */
  public static final String LOGBACK_CONFIGURATION_FILE_DEFAULT = "config/logback.xml";

  /**
   * constructor (hides public default one)
   */
  private Main() {}

  /**
   * main
   *
   * @param args program arguments
   * @throws IOException
   */
  public static void main(String[] args) {

    /* logging defaults */

    if (System.getProperty(LOGBACK_CONFIGURATION_FILE_SYSTEM_PROPERTY) == null) {
      if (Files.exists(Paths.get(LOGBACK_CONFIGURATION_FILE_DEFAULT))) {
        System.setProperty(LOGBACK_CONFIGURATION_FILE_SYSTEM_PROPERTY,
            LOGBACK_CONFIGURATION_FILE_DEFAULT);
      } else {
        LoggerUtil.setLevel(LoggerUtil.ROOT_LOGGER, ch.qos.logback.classic.Level.INFO.toString());
      }
    }

    /* make sure the default configuration directory is properly set up */

    SystemPersistence.manager.setConfigurationDir(null);

    /* logging defaults */

    if (System.getProperty(LOGBACK_CONFIGURATION_FILE_SYSTEM_PROPERTY) == null) {
      if (Files.exists(Paths.get(LOGBACK_CONFIGURATION_FILE_DEFAULT))) {
        System.setProperty(LOGBACK_CONFIGURATION_FILE_SYSTEM_PROPERTY,
            LOGBACK_CONFIGURATION_FILE_DEFAULT);
      } else {
        LoggerUtil.setLevel(LoggerUtil.ROOT_LOGGER, ch.qos.logback.classic.Level.INFO.toString());
      }
    }

    /* 0. boot */

    PolicyEngine.manager.boot(args);

    /* start logger */

    final Logger logger = LoggerFactory.getLogger(Main.class);

    /* 1.a. Configure the Engine */

    Properties engineProperties = SystemPersistence.manager.getEngineProperties();
    if (engineProperties == null)
      engineProperties = PolicyEngine.manager.defaultTelemetryConfig();

    PolicyEngine.manager.configure(engineProperties);

    /* 1.b. Load Installation Environment(s) */

    for (final Properties env : SystemPersistence.manager.getEnvironmentProperties()) {
      PolicyEngine.manager.setEnvironment(env);
    }

    /* 2. Start the Engine with the basic services only (no Policy Controllers) */

    try {
      final boolean success = PolicyEngine.manager.start();
      if (!success) {
        logger.warn("Main: {} has been partially started", PolicyEngine.manager);
      }
    } catch (final IllegalStateException e) {
      logger.warn("Main: cannot start {} (bad state) because of {}", PolicyEngine.manager,
          e.getMessage(), e);
    } catch (final Exception e) {
      logger.warn("Main: cannot start {} because of {}", PolicyEngine.manager, e.getMessage(), e);
      System.exit(1);
    }

    /* 3. Create and start the controllers */

    for (final Properties controllerProperties : SystemPersistence.manager
        .getControllerProperties()) {
      final String controllerName =
          controllerProperties.getProperty(PolicyProperties.PROPERTY_CONTROLLER_NAME);
      try {
        final PolicyController controller =
            PolicyEngine.manager.createPolicyController(controllerName, controllerProperties);
        controller.start();
      } catch (final Exception e) {
        logger.error("Main: cannot instantiate policy-controller {} because of {}", controllerName,
            e.getMessage(), e);
      } catch (final LinkageError e) {
        logger.warn("Main: cannot instantiate policy-controller {} (linkage) because of {}",
            controllerName, e.getMessage(), e);
      }
    }
  }
}
