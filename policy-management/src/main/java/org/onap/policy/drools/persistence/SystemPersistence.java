/*
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

import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/**
 * System Configuration.
 */
public interface SystemPersistence {

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
     * backs up a topic configuration.
     *
     * @param topicName the controller name
     * @return true if the configuration is backed up
     */
    boolean backupTopic(String topicName);

    /**
     * backs up an http server configuration.
     *
     * @param serverName the HTTP server name
     * @return true if the configuration is backed up
     */
    boolean backupHttpServer(String serverName);

    /**
     * backs up an http client configuration.
     *
     * @param clientName the HTTP client name
     * @return true if the configuration is backed up
     */
    boolean backupHttpClient(String clientName);

    /**
     * persists controller configuration.
     *
     * @param controllerName the controller name
     * @param configuration object containing the configuration
     *
     * @return true if storage is successful, false otherwise
     * @throws IllegalArgumentException if the configuration cannot be handled by the persistence
     *         manager
     */
    boolean storeController(String controllerName, Object configuration);

    /**
     * persists topic configuration.
     *
     * @param topicName the controller name
     * @param configuration object containing the configuration
     *
     * @return true if storage is successful, false otherwise
     * @throws IllegalArgumentException if the configuration cannot be handled by the persistence
     *         manager
     */
    boolean storeTopic(String topicName, Object configuration);

    /**
     * persists http server configuration.
     *
     * @param serverName the server name
     * @param configuration object containing the configuration
     *
     * @return true if storage is successful, false otherwise
     * @throws IllegalArgumentException if the configuration cannot be handled by the persistence
     *         manager
     */
    boolean storeHttpServer(String serverName, Object configuration);

    /**
     * persists http client configuration.
     *
     * @param clientName the client name
     * @param configuration object containing the configuration
     *
     * @return true if storage is successful, false otherwise
     * @throws IllegalArgumentException if the configuration cannot be handled by the persistence
     *         manager
     */
    boolean storeHttpClient(String clientName, Object configuration);

    /**
     * delete controller configuration.
     *
     * @param controllerName the controller name
     * @return true if storage is successful, false otherwise
     */
    boolean deleteController(String controllerName);

    /**
     * delete topic configuration.
     *
     * @param topicName the topic name
     * @return true if storage is successful, false otherwise
     */
    boolean deleteTopic(String topicName);

    /**
     * deletes an http server configuration.
     *
     * @param serverName the HTTP server name
     * @return true if storage is successful, false otherwise
     */
    boolean deleteHttpServer(String serverName);

    /**
     * deletes an http client configuration.
     *
     * @param clientName the HTTP client name
     * @return true if storage is successful, false otherwise
     */
    boolean deleteHttpClient(String clientName);

    /**
     * get controllers configuration.
     *
     * @return list of controllers properties
     */
    List<Properties> getControllerProperties();

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
     * get topic configuration.
     *
     * @return list of topic properties
     */
    List<Properties> getTopicProperties();

    /**
     * get topic properties.
     *
     * @param topicName topic name
     * @return properties for this topic
     *
     * @throws IllegalArgumentException if topicName is invalid
     */
    Properties getTopicProperties(String topicName);

    /**
     * get HTTP Servers configuration.
     *
     * @return list of HTTP server properties
     */
    List<Properties> getHttpServerProperties();

    /**
     * get HTTP Server properties.
     *
     * @param serverName name
     * @return properties for this server
     *
     * @throws IllegalArgumentException if topicName is invalid
     */
    Properties getHttpServerProperties(String serverName);

    /**
     * get HTTP Client configuration.
     *
     * @return list of HTTP client properties
     */
    List<Properties> getHttpClientProperties();

    /**
     * get HTTP Client properties.
     *
     * @param clientName name
     * @return properties for this client
     *
     * @throws IllegalArgumentException if topicName is invalid
     */
    Properties getHttpClientProperties(String clientName);

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
