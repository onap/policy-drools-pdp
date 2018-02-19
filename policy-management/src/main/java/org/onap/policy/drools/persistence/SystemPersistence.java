/*
 * ============LICENSE_START=======================================================
 * policy-management
 * ================================================================================
 * Copyright (C) 2017-2018 AT&T Intellectual Property. All rights reserved.
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

import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/**
 * System Configuration
 */
public interface SystemPersistence {
  /**
   * configuration directory
   */
  public static final String DEFAULT_CONFIGURATION_DIR = "config";

  /**
   * Persistence Manager. For now it is a file-based properties management, In the future, it will
   * probably be DB based, so manager implementation will change.
   */
  public static final SystemPersistence manager = new FileSystemPersistence();

  /**
   * sets a configuration directory and ensures it exists
   *
   * @param configDir configuration directory or null to use the default one
   */
  public void setConfigurationDir(String configDir);

  /**
   * get configuration directory path
   *
   * @return configuration directory path
   */
  public Path getConfigurationPath();

  /**
   * backs up a controller configuration.
   *
   * @param controllerName the controller name
   * @return true if the configuration is backed up
   */
  public boolean backupController(String controllerName);

  /**
   * persists controller configuration
   *
   * @param controllerName the controller name
   * @param configuration object containing the configuration
   *
   * @return true if storage is succesful, false otherwise
   * @throws IllegalArgumentException if the configuration cannot be handled by the persistence
   *         manager
   */
  public boolean storeController(String controllerName, Object configuration);

  /**
   * delete controller configuration
   *
   * @param controllerName the controller name
   * @return true if storage is succesful, false otherwise
   */
  public boolean deleteController(String controllerName);

  /**
   * get controller properties
   *
   * @param controllerName controller name
   * @return properties for this controller
   *
   * @throws IllegalArgumentException if the controller name does not lead to a properties
   *         configuration
   */
  public Properties getControllerProperties(String controllerName);

  /**
   * get controllers configuration
   *
   * @return list of controllers properties
   */
  public List<Properties> getControllerProperties();

  /**
   * get environments
   *
   * @param environment name
   */
  public List<Properties> getEnvironmentProperties();

  /**
   * get environment properties
   *
   * @param environment name
   */
  public Properties getEnvironmentProperties(String environmentName);

  /**
   * get the engine properties
   *
   * @return the engine properties
   */
  public Properties getEngineProperties();

  /**
   * get properties by name
   *
   * @param name
   * @return properties
   *
   * @throws IllegalArgumentException if the name does not lead to a properties configuration
   */
  public Properties getProperties(String name);
}
