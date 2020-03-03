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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

import java.util.function.BiPredicate;
import org.onap.policy.drools.properties.DroolsPropertyConstants;
import org.onap.policy.drools.utils.PropertyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Properties based Persistence.
 */
public class FileSystemPersistence implements SystemPersistence {

    /**
     * Properties file extension.
     */
    public static final String PROPERTIES_FILE_EXTENSION = ".properties";

    /**
     * Policy controllers suffix.
     */
    public static final String CONTROLLER_SUFFIX_IDENTIFIER = "-controller";

    /**
     * File Backup Suffix.
     */
    public static final String FILE_BACKUP_SUFFIX = ".bak";

    /**
     * Policy controller properties file suffix.
     */
    public static final String PROPERTIES_FILE_CONTROLLER_SUFFIX =
            CONTROLLER_SUFFIX_IDENTIFIER + PROPERTIES_FILE_EXTENSION;

    /**
     * Topic configuration suffix.
     */
    public static final String TOPIC_SUFFIX_IDENTIFIER = "-topic";

    /**
     * Topic properties file suffix.
     */
    public static final String PROPERTIES_FILE_TOPIC_SUFFIX = TOPIC_SUFFIX_IDENTIFIER + PROPERTIES_FILE_EXTENSION;

    /**
     * Policy engine properties file name.
     */
    public static final String PROPERTIES_FILE_ENGINE = "engine" + PROPERTIES_FILE_EXTENSION;

    public static final String HTTP_SERVER_SUFFIX_IDENTIFIER = "-http-server";
    public static final String PROPERTIES_FILE_HTTP_SERVER_SUFFIX =
            HTTP_SERVER_SUFFIX_IDENTIFIER + PROPERTIES_FILE_EXTENSION;

    public static final String HTTP_CLIENT_SUFFIX_IDENTIFIER = "-http-client";
    public static final String PROPERTIES_FILE_HTTP_CLIENT_SUFFIX =
            HTTP_CLIENT_SUFFIX_IDENTIFIER + PROPERTIES_FILE_EXTENSION;

    /**
     * Installation environment suffix for files.
     */
    public static final String ENV_FILE_SUFFIX = ".environment";

    /**
     * Environment properties extension.
     */
    public static final String ENV_FILE_EXTENSION = ENV_FILE_SUFFIX;

    /**
     * Installation environment suffix for files.
     */
    public static final String SYSTEM_PROPERTIES_SUFFIX = "-system";
    public static final String SYSTEM_PROPERTIES_FILE_SUFFIX = SYSTEM_PROPERTIES_SUFFIX + PROPERTIES_FILE_EXTENSION;

    /**
     * Configuration directory.
     */
    protected Path configurationDirectory = Paths.get(SystemPersistenceConstants.DEFAULT_CONFIGURATION_DIR);

    /**
     * Logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(FileSystemPersistence.class);

    @Override
    public void setConfigurationDir(String configDir) {
        String tempConfigDir = configDir;

        if (tempConfigDir == null) {
            tempConfigDir = SystemPersistenceConstants.DEFAULT_CONFIGURATION_DIR;
            this.configurationDirectory = Paths.get(SystemPersistenceConstants.DEFAULT_CONFIGURATION_DIR);
        }

        if (!tempConfigDir.equals(SystemPersistenceConstants.DEFAULT_CONFIGURATION_DIR)) {
            this.configurationDirectory = Paths.get(tempConfigDir);
        }

        setConfigurationDir();
    }

    @Override
    public void setConfigurationDir() {
        if (Files.notExists(this.configurationDirectory)) {
            try {
                Files.createDirectories(this.configurationDirectory);
            } catch (final IOException e) {
                throw new IllegalStateException("cannot create " + this.configurationDirectory, e);
            }
        }

        if (!Files.isDirectory(this.configurationDirectory)) {
            throw new IllegalStateException(
                "config directory: " + this.configurationDirectory + " is not a directory");
        }
    }

    @Override
    public Path getConfigurationPath() {
        return this.configurationDirectory;
    }

    protected Properties getProperties(Path propertiesPath) {
        if (!Files.exists(propertiesPath)) {
            throw new IllegalArgumentException("properties for " + propertiesPath.toString() + " are not persisted.");
        }

        try {
            return PropertyUtil.getProperties(propertiesPath.toFile());
        } catch (final Exception e) {
            throw new IllegalArgumentException("can't read properties for " + propertiesPath.toString(), e);
        }
    }

    @Override
    public Properties getProperties(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("properties name must be provided");
        }

        Path propertiesPath = Paths.get(this.configurationDirectory.toString());
        if (name.endsWith(PROPERTIES_FILE_EXTENSION) || name.endsWith(ENV_FILE_EXTENSION)) {
            propertiesPath = propertiesPath.resolve(name);
        } else {
            propertiesPath = propertiesPath.resolve(name + PROPERTIES_FILE_EXTENSION);
        }

        return getProperties(propertiesPath);
    }

    protected List<Properties> getPropertiesList(String suffix) {
        return getPropertiesList(suffix, (name, props) ->  true);
    }

    protected List<Properties> getPropertiesList(String suffix, BiPredicate<String, Properties> preCondition) {
        List<Properties> properties = new ArrayList<>();
        File[] files = this.sortedListFiles();
        for (File file : files) {
            if (file.getName().endsWith(suffix)) {
                addToPropertiesList(file, properties, preCondition);
            }
        }
        return properties;
    }

    private void addToPropertiesList(File file, List<Properties> properties,
                                     BiPredicate<String, Properties> preCondition) {
        try {
            Properties proposedProps = getProperties(file.getName());
            if (preCondition.test(file.getName(), proposedProps)) {
                properties.add(proposedProps);
            }
        } catch (final Exception e) {
            logger.error("{}: cannot get properties {} because of {}", this, file.getName(),
                e.getMessage(), e);
        }
    }

    @Override
    public Properties getEnvironmentProperties(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("environment name must be provided");
        }

        return this.getProperties(Paths.get(this.configurationDirectory.toString(), name + ENV_FILE_SUFFIX));
    }

    @Override
    public List<Properties> getEnvironmentProperties() {
        return getPropertiesList(ENV_FILE_SUFFIX);
    }

    @Override
    public Properties getSystemProperties(String name) {
        return this.getProperties(name + SYSTEM_PROPERTIES_SUFFIX);
    }

    @Override
    public List<Properties> getSystemProperties() {
        return getPropertiesList(SYSTEM_PROPERTIES_FILE_SUFFIX);
    }

    @Override
    public Properties getEngineProperties() {
        return this.getProperties(PROPERTIES_FILE_ENGINE);
    }

    @Override
    public Properties getControllerProperties(String controllerName) {
        return this.getProperties(controllerName + CONTROLLER_SUFFIX_IDENTIFIER);
    }

    @Override
    public List<Properties> getControllerProperties() {
        return getPropertiesList(PROPERTIES_FILE_CONTROLLER_SUFFIX, this::testControllerName);
    }

    @Override
    public Properties getTopicProperties(String topicName) {
        return this.getProperties(topicName + TOPIC_SUFFIX_IDENTIFIER);
    }

    @Override
    public List<Properties> getTopicProperties() {
        return getPropertiesList(PROPERTIES_FILE_TOPIC_SUFFIX);
    }

    @Override
    public Properties getHttpServerProperties(String serverName) {
        return this.getProperties(serverName + HTTP_SERVER_SUFFIX_IDENTIFIER);
    }

    @Override
    public List<Properties> getHttpServerProperties() {
        return getPropertiesList(PROPERTIES_FILE_HTTP_SERVER_SUFFIX);
    }

    @Override
    public Properties getHttpClientProperties(String clientName) {
        return this.getProperties(clientName + HTTP_CLIENT_SUFFIX_IDENTIFIER);
    }

    @Override
    public List<Properties> getHttpClientProperties() {
        return getPropertiesList(PROPERTIES_FILE_HTTP_CLIENT_SUFFIX);
    }

    private boolean testControllerName(String controllerFilename, Properties controllerProperties) {
        String controllerName = controllerFilename
                .substring(0, controllerFilename.length() - PROPERTIES_FILE_CONTROLLER_SUFFIX.length());
        String controllerPropName = controllerProperties.getProperty(DroolsPropertyConstants.PROPERTY_CONTROLLER_NAME);
        if (controllerPropName == null) {
            controllerProperties.setProperty(DroolsPropertyConstants.PROPERTY_CONTROLLER_NAME, controllerName);
        } else if (!controllerPropName.equals(controllerName)) {
            logger.error("{}: mismatch controller named {} against {} in file {}",
                         this, controllerPropName, controllerName, controllerFilename);
            return false;
        }
        return true;
    }


    @Override
    public boolean backupController(String controllerName) {
        return backup(controllerName, PROPERTIES_FILE_CONTROLLER_SUFFIX);
    }

    @Override
    public boolean backupTopic(String topicName) {
        return backup(topicName, PROPERTIES_FILE_TOPIC_SUFFIX);
    }

    @Override
    public boolean backupHttpServer(String serverName) {
        return backup(serverName, PROPERTIES_FILE_HTTP_SERVER_SUFFIX);
    }

    @Override
    public boolean backupHttpClient(String clientName) {
        return backup(clientName, PROPERTIES_FILE_HTTP_CLIENT_SUFFIX);
    }

    protected boolean backup(String name, String fileSuffix) {
        Path path = Paths.get(this.configurationDirectory.toString(), name + fileSuffix);
        if (Files.exists(path)) {
            try {
                logger.info("{}: there is an existing configuration file @ {} ", this, path);
                Path bakPath = Paths.get(this.configurationDirectory.toString(),
                                            name + fileSuffix + FILE_BACKUP_SUFFIX);
                Files.copy(path, bakPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                logger.warn("{}: {} cannot be backed up", this, name, e);
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean storeController(String controllerName, Object configuration) {
        checkPropertiesParam(configuration);
        return store(controllerName, (Properties) configuration, PROPERTIES_FILE_CONTROLLER_SUFFIX);
    }

    @Override
    public boolean storeTopic(String topicName, Object configuration) {
        checkPropertiesParam(configuration);
        return store(topicName, (Properties) configuration, PROPERTIES_FILE_TOPIC_SUFFIX);
    }

    @Override
    public boolean storeHttpServer(String serverName, Object configuration) {
        checkPropertiesParam(configuration);
        return store(serverName, (Properties) configuration, PROPERTIES_FILE_HTTP_SERVER_SUFFIX);
    }

    @Override
    public boolean storeHttpClient(String clientName, Object configuration) {
        checkPropertiesParam(configuration);
        return store(clientName, (Properties) configuration, PROPERTIES_FILE_HTTP_CLIENT_SUFFIX);
    }

    private boolean store(String name, Properties properties, String fileSuffix) {
        Path path = Paths.get(this.configurationDirectory.toString(), name + fileSuffix);
        if (Files.exists(path)) {
            try {
                Properties oldProperties = PropertyUtil.getProperties(path.toFile());
                if (oldProperties.equals(properties)) {
                    logger.info("{}: noop: a properties file with the same contents exists for controller {}.", this,
                                name);
                    return true;
                } else {
                    this.backupController(name);
                }
            } catch (Exception e) {
                logger.info("{}: no existing {} properties {}", this, name, e);
                // continue
            }
        }

        File file = path.toFile();
        try (FileWriter writer = new FileWriter(file)) {
            properties.store(writer, "Machine created Policy Controller Configuration");
        } catch (Exception e) {
            logger.warn("{}: {} cannot be saved", this, name, e);
            return false;
        }

        return true;
    }

    private void checkPropertiesParam(Object configuration) {
        if (!(configuration instanceof Properties)) {
            throw new IllegalArgumentException(
                "configuration must be of type properties to be handled by this manager");
        }
    }


    @Override
    public boolean deleteController(String controllerName) {
        return delete(controllerName, PROPERTIES_FILE_CONTROLLER_SUFFIX);
    }

    @Override
    public boolean deleteTopic(String topicName) {
        return delete(topicName, PROPERTIES_FILE_TOPIC_SUFFIX);
    }

    @Override
    public boolean deleteHttpServer(String serverName) {
        return delete(serverName, PROPERTIES_FILE_HTTP_SERVER_SUFFIX);
    }

    @Override
    public boolean deleteHttpClient(String clientName) {
        return delete(clientName, PROPERTIES_FILE_HTTP_CLIENT_SUFFIX);
    }

    protected boolean delete(String name, String fileSuffix) {
        Path path = Paths.get(this.configurationDirectory.toString(), name + fileSuffix);

        if (Files.exists(path)) {
            try {
                Path bakPath = Paths.get(this.configurationDirectory.toString(),
                                            name + fileSuffix + FILE_BACKUP_SUFFIX);
                Files.move(path, bakPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (final Exception e) {
                logger.warn("{}: {} cannot be deleted", this, name, e);
                return false;
            }
        }

        return true;
    }

    /**
     * provides a list of files sorted by name in ascending order in the configuration directory.
     */
    private File[] sortedListFiles() {
        File[] dirFiles = this.configurationDirectory.toFile().listFiles();
        if (dirFiles != null) {
            Arrays.sort(dirFiles, Comparator.comparing(File::getName));
        } else {
            dirFiles = new File[]{};
        }
        return dirFiles;
    }

    @Override
    public String toString() {
        return "FileSystemPersistence [configurationDirectory=" + this.configurationDirectory + "]";
    }
}
