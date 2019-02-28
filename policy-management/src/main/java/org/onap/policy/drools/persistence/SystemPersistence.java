/*
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

package org.onap.policy.drools.persistence;

import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/**
 * System Configuration.
 */
public interface SystemPersistence {
    /**
     * configuration directory.
     */
    String DEFAULT_CONFIGURATION_DIR = "config";

    /**
     * Persistence Manager. For now it is a file-based properties management, In the future, it will
     * probably be DB based, so manager implementation will change.
     */
    SystemPersistence manager = new FileSystemPersistence();

    /**
     * sets a configuration directory and ensures it exists.
     *
     * @param configDir configuration directory or null to use the default one
     */
    void setConfigurationDir(String configDir);

    /**
     * sets the default configuration directory and ensures it exists.
     */
    void setConfigurationDir();

    /**
     * get configuration directory path.
     *
     * @return configuration directory path
     */
    Path getConfigurationPath();

    /**
     * backs up a controller configuration.
     *
     * @param controllerName the controller name
     * @return true if the configuration is backed up
     */
    boolean backupController(String controllerName);

    /**
     * persists controller configuration.
     *
     * @param controllerName the controller name
     * @param configuration object containing the configuration
     *
     * @return true if storage is succesful, false otherwise
     * @throws IllegalArgumentException if the configuration cannot be handled by the persistence
     *         manager
     */
    boolean storeController(String controllerName, Object configuration);

    /**
     * delete controller configuration.
     *
     * @param controllerName the controller name
     * @return true if storage is succesful, false otherwise
     */
    boolean deleteController(String controllerName);

    /**
     * get controller properties.
     *
     * @param controllerName controller name
     * @return properties for this controller
     *
     * @throws IllegalArgumentException if the controller name does not lead to a properties
     *         configuration
     */
    Properties getControllerProperties(String controllerName);

    /**
     * get controllers configuration.
     *
     * @return list of controllers properties
     */
    List<Properties> getControllerProperties();

    /**
     * get environments.
     *
     */
    List<Properties> getEnvironmentProperties();

    /**
     * get environment properties.
     *
     * @param environmentName name
     */
    Properties getEnvironmentProperties(String environmentName);

    /**
     * get the engine properties.
     *
     * @return the engine properties
     */
    Properties getEngineProperties();

    /**
     * get properties by name.
     *
     * @param name name
     * @return properties
     *
     * @throws IllegalArgumentException if the name does not lead to a properties configuration
     */
    Properties getProperties(String name);

    /**
     * get system properties configuration.
     *
     * @param name name
     * @return properties
     */
    Properties getSystemProperties(String name);

    /**
     * get system properties configuration list.
     *
     * @return list of system properties
     */
    List<Properties> getSystemProperties();
}
