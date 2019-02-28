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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.onap.policy.drools.properties.DroolsProperties;
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
     * Policy controller properties file suffix.
     */
    public static final String PROPERTIES_FILE_CONTROLLER_SUFFIX =
            CONTROLLER_SUFFIX_IDENTIFIER + PROPERTIES_FILE_EXTENSION;

    /**
     * Policy controller properties file suffix.
     */
    public static final String PROPERTIES_FILE_CONTROLLER_BACKUP_SUFFIX =
            CONTROLLER_SUFFIX_IDENTIFIER + PROPERTIES_FILE_EXTENSION + ".bak";

    /**
     * Policy engine properties file name.
     */
    public static final String PROPERTIES_FILE_ENGINE = "engine" + PROPERTIES_FILE_EXTENSION;

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
    protected Path configurationDirectory = Paths.get(DEFAULT_CONFIGURATION_DIR);

    /**
     * Logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(FileSystemPersistence.class);

    @Override
    public void setConfigurationDir(String configDir) {
        String tempConfigDir = configDir;

        if (tempConfigDir == null) {
            tempConfigDir = DEFAULT_CONFIGURATION_DIR;
            this.configurationDirectory = Paths.get(DEFAULT_CONFIGURATION_DIR);
        }

        if (!tempConfigDir.equals(DEFAULT_CONFIGURATION_DIR)) {
            this.configurationDirectory = Paths.get(tempConfigDir);
        }

        setConfigurationDirectory();
    }

    @Override
    public void setConfigurationDirectory() {
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
        List<Properties> properties = new ArrayList<>();
        File[] files = this.sortedListFiles();
        for (File file : files) {
            if (file.getName().endsWith(suffix)) {
                try {
                    properties.add(getProperties(file.getName()));
                } catch (final Exception e) {
                    logger.error("{}: cannot get properties {} because of {}", this, file.getName(),
                        e.getMessage(), e);
                }
            }
        }
        return properties;
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
        final List<Properties> controllers = new ArrayList<>();
        final File[] controllerFiles = this.sortedListFiles();
        for (final File controllerFile : controllerFiles) {
            if (controllerFile.getName().endsWith(PROPERTIES_FILE_CONTROLLER_SUFFIX)) {
                final int idxSuffix = controllerFile.getName().indexOf(PROPERTIES_FILE_CONTROLLER_SUFFIX);
                final int lastIdxSuffix =
                    controllerFile.getName().lastIndexOf(PROPERTIES_FILE_CONTROLLER_SUFFIX);
                if (idxSuffix != lastIdxSuffix) {
                    throw new IllegalArgumentException(
                        "Improper naming of controller properties file: " + "Expected <controller-name>"
                            + FileSystemPersistence.PROPERTIES_FILE_CONTROLLER_SUFFIX);
                }

                final String name = controllerFile.getName().substring(0, lastIdxSuffix);
                try {
                    final Properties controllerProperties = this.getControllerProperties(name);
                    final String controllerName =
                        controllerProperties.getProperty(DroolsProperties.PROPERTY_CONTROLLER_NAME);
                    if (controllerName == null) {
                        controllerProperties.setProperty(DroolsProperties.PROPERTY_CONTROLLER_NAME, name);
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
        if (!(configuration instanceof Properties)) {
            throw new IllegalArgumentException(
                    "configuration must be of type properties to be handled by this manager");
        }

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
                logger.info("{}: no existing {} properties {}", this, controllerName, e);
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

    /**
     * provides a list of files sorted by name in ascending order in the configuration directory.
     */
    protected File[] sortedListFiles() {
        final File[] dirFiles = this.configurationDirectory.toFile().listFiles();
        Arrays.sort(dirFiles, (e1, e2) -> e1.getName().compareTo(e2.getName()));
        return dirFiles;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("FileSystemPersistence [configurationDirectory=")
        .append(this.configurationDirectory).append("]");
        return builder.toString();
    }
}
