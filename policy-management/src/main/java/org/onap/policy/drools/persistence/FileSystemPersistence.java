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
package org.onap.policy.drools.persistence;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.onap.policy.drools.properties.PolicyProperties;
import org.onap.policy.drools.utils.PropertyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Properties based Persistence
 */
public class FileSystemPersistence implements SystemPersistence {
  /**
   * policy controllers suffix
   */
  public final static String CONTROLLER_SUFFIX_IDENTIFIER = "-controller";

  /**
   * policy controller properties file suffix
   */
  public final static String PROPERTIES_FILE_CONTROLLER_SUFFIX =
      CONTROLLER_SUFFIX_IDENTIFIER + ".properties";

  /**
   * policy controller properties file suffix
   */
  public final static String PROPERTIES_FILE_CONTROLLER_BACKUP_SUFFIX =
      CONTROLLER_SUFFIX_IDENTIFIER + ".properties.bak";

  /**
   * policy engine properties file name
   */
  public final static String PROPERTIES_FILE_ENGINE = "policy-engine.properties";

  /**
   * Installation environment suffix for files
   */
  public final static String ENV_SUFFIX = ".environment";

  /**
   * configuration directory
   */
  protected Path configurationDirectory = Paths.get(DEFAULT_CONFIGURATION_DIR);

  /**
   * logger
   */
  private static final Logger logger = LoggerFactory.getLogger(FileSystemPersistence.class);


  @Override
  public void setConfigurationDir(String configDir) {
    if (configDir == null) {
      configDir = DEFAULT_CONFIGURATION_DIR;
      this.configurationDirectory = Paths.get(DEFAULT_CONFIGURATION_DIR);
    }

    if (!configDir.equals(DEFAULT_CONFIGURATION_DIR))
      this.configurationDirectory = Paths.get(configDir);

    if (Files.notExists(this.configurationDirectory)) {
      try {
        Files.createDirectories(this.configurationDirectory);
      } catch (final IOException e) {
        throw new IllegalStateException("cannot create " + this.configurationDirectory, e);
      }
    }

    if (!Files.isDirectory(this.configurationDirectory))
      throw new IllegalStateException(
          "config directory: " + this.configurationDirectory + " is not a directory");
  }

  @Override
  public Path getConfigurationPath() {
    return this.configurationDirectory;
  }

  @Override
  public Properties getEngineProperties() {
    final Path policyEnginePath =
        Paths.get(this.configurationDirectory.toString(), PROPERTIES_FILE_ENGINE);
    try {
      if (Files.exists(policyEnginePath))
        return PropertyUtil.getProperties(policyEnginePath.toFile());
    } catch (final Exception e) {
      logger.warn("{}: could not find {}", this, policyEnginePath, e);
    }

    return null;
  }

  @Override
  public boolean backupController(String controllerName) {
    final Path controllerPropertiesPath = Paths.get(this.configurationDirectory.toString(),
        controllerName + PROPERTIES_FILE_CONTROLLER_SUFFIX);

    if (Files.exists(controllerPropertiesPath)) {
      try {
        logger.info("{}: there is an existing configuration file @ {} ", this,
            controllerPropertiesPath);
        final Path controllerPropertiesBakPath = Paths.get(this.configurationDirectory.toString(),
            controllerName + PROPERTIES_FILE_CONTROLLER_BACKUP_SUFFIX);
        Files.copy(controllerPropertiesPath, controllerPropertiesBakPath,
            StandardCopyOption.REPLACE_EXISTING);
      } catch (final Exception e) {
        logger.warn("{}: {} cannot be backed up", this, controllerName, e);
        return false;
      }
    }

    return true;
  }

  @Override
  public boolean storeController(String controllerName, Object configuration) {
    if (!(configuration instanceof Properties))
      throw new IllegalArgumentException(
          "configuration must be of type properties to be handled by this manager");

    final Properties properties = (Properties) configuration;

    final Path controllerPropertiesPath = Paths.get(this.configurationDirectory.toString(),
        controllerName + PROPERTIES_FILE_CONTROLLER_SUFFIX);
    if (Files.exists(controllerPropertiesPath)) {
      try {
        final Properties oldProperties =
            PropertyUtil.getProperties(controllerPropertiesPath.toFile());
        if (oldProperties.equals(properties)) {
          logger.info(
              "{}: noop: a properties file with the same contents exists for controller {}.", this,
              controllerName);
          return true;
        } else {
          this.backupController(controllerName);
        }
      } catch (final Exception e) {
        logger.warn("{}: no existing {} properties", this, controllerName, e);
        // continue
      }
    }

    final File controllerPropertiesFile = controllerPropertiesPath.toFile();
    try (FileWriter writer = new FileWriter(controllerPropertiesFile)) {
      properties.store(writer, "Machine created Policy Controller Configuration");
    } catch (final Exception e) {
      logger.warn("{}: {} cannot be saved", this, controllerName, e);
      return false;
    }

    return true;
  }

  @Override
  public boolean deleteController(String controllerName) {
    final Path controllerPropertiesPath = Paths.get(this.configurationDirectory.toString(),
        controllerName + PROPERTIES_FILE_CONTROLLER_SUFFIX);

    if (Files.exists(controllerPropertiesPath)) {
      try {
        final Path controllerPropertiesBakPath = Paths.get(this.configurationDirectory.toString(),
            controllerName + PROPERTIES_FILE_CONTROLLER_BACKUP_SUFFIX);
        Files.move(controllerPropertiesPath, controllerPropertiesBakPath,
            StandardCopyOption.REPLACE_EXISTING);
      } catch (final Exception e) {
        logger.warn("{}: {} cannot be deleted", this, controllerName, e);
        return false;
      }
    }

    return true;
  }

  @Override
  public Properties getControllerProperties(String controllerName) {
    return this.getProperties(controllerName + CONTROLLER_SUFFIX_IDENTIFIER);
  }

  @Override
  public List<Properties> getControllerProperties() {
    final List<Properties> controllers = new ArrayList<>();
    final File[] controllerFiles = this.configurationDirectory.toFile().listFiles();
    for (final File controllerFile : controllerFiles) {
      if (controllerFile.getName().endsWith(PROPERTIES_FILE_CONTROLLER_SUFFIX)) {
        final int idxSuffix = controllerFile.getName().indexOf(PROPERTIES_FILE_CONTROLLER_SUFFIX);
        final int lastIdxSuffix =
            controllerFile.getName().lastIndexOf(PROPERTIES_FILE_CONTROLLER_SUFFIX);
        if (idxSuffix != lastIdxSuffix)
          throw new IllegalArgumentException(
              "Improper naming of controller properties file: " + "Expected <controller-name>"
                  + FileSystemPersistence.PROPERTIES_FILE_CONTROLLER_SUFFIX);

        final String name = controllerFile.getName().substring(0, lastIdxSuffix);
        try {
          final Properties controllerProperties = this.getControllerProperties(name);
          final String controllerName =
              controllerProperties.getProperty(PolicyProperties.PROPERTY_CONTROLLER_NAME);
          if (controllerName == null) {
            controllerProperties.setProperty(PolicyProperties.PROPERTY_CONTROLLER_NAME, name);
          } else if (!controllerName.equals(name)) {
            logger.error("{}: mismatch controller named {} with file name {}", this, controllerName,
                controllerFile.getName());
            continue;
          }
          controllers.add(this.getControllerProperties(name));
        } catch (final Exception e) {
          logger.error("{}: cannot obtain properties for controller {} because of {}", name,
              e.getMessage(), e);
        }
      }
    }
    return controllers;
  }

  @Override
  public Properties getProperties(String name) {
    final Path propertiesPath =
        Paths.get(this.configurationDirectory.toString(), name + ".properties");

    if (!Files.exists(propertiesPath)) {
      throw new IllegalArgumentException("properties for " + name + " are not persisted.");
    }

    try {
      return PropertyUtil.getProperties(propertiesPath.toFile());
    } catch (final Exception e) {
      logger.warn("{}: can't read properties @ {}", name, propertiesPath);
      throw new IllegalArgumentException(
          "can't read properties for " + name + " @ " + propertiesPath, e);
    }
  }

  @Override
  public List<Properties> getEnvironmentProperties() {
    final List<Properties> envs = new ArrayList<>();
    final File[] envFiles = this.configurationDirectory.toFile().listFiles();
    for (final File envFile : envFiles) {
      if (envFile.getName().endsWith(ENV_SUFFIX)) {
        final String name = envFile.getName().substring(0, envFile.getName().indexOf(ENV_SUFFIX));
        try {
          envs.add(this.getEnvironmentProperties(name));
        } catch (final Exception e) {
          logger.error("{}: cannot get environment {} because of {}", name, e.getMessage(), e);
        }
      }
    }
    return envs;
  }

  @Override
  public Properties getEnvironmentProperties(String name) {
    final Path envPath = Paths.get(this.configurationDirectory.toString(), name + ENV_SUFFIX);
    if (!Files.exists(envPath)) {
      throw new IllegalArgumentException("{} environment" + name + " cannot be retrieved");
    }

    try {
      return PropertyUtil.getProperties(envPath.toFile());
    } catch (final Exception e) {
      throw new IllegalArgumentException("cannot read environment " + name, e);
    }
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("FileSystemPersistence [configurationDirectory=")
        .append(this.configurationDirectory).append("]");
    return builder.toString();
  }
}
