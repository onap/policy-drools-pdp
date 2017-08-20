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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

import org.onap.policy.drools.utils.PropertyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface SystemPersistence {
	
	/**
	 * configuration directory
	 */
	public static String CONFIG_DIR_NAME = "config";
	
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
	 * policy engine properties file name
	 */
	public final static String PROPERTIES_FILE_ENGINE = "policy-engine.properties";
	
	
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
	 * @throws IllegalArgumentException if the configuration cannot be handled by the persistence manager
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
	 * @throws IllegalArgumentException if the controller name does not lead to a properties configuration
	 */
	public Properties getControllerProperties(String controllerName);
	
	/**
	 * get properties by name
	 * 
	 * @param name 
	 * @return properties 
	 * 
	 * @throws IllegalArgumentException if the name does not lead to a properties configuration
	 */
	public Properties getProperties(String name);
	
	/**
	 * Persistence Manager.   For now it is a file-based properties management,
	 * In the future, it will probably be DB based, so manager implementation
	 * will change.
	 */
	public static final SystemPersistence manager = new SystemPropertiesPersistence();
}

/** 
 * Properties based Persistence
 */
class SystemPropertiesPersistence implements SystemPersistence {
	
	/**
	 * logger 
	 */
	private static Logger logger = LoggerFactory.getLogger(SystemPropertiesPersistence.class);
	
	/**
	 * backs up the properties-based controller configuration
	 * @param controllerName the controller name
	 * @return true if the configuration is backed up in disk or back up does not apply, false otherwise.
	 */
	@Override
	public boolean backupController(String controllerName) {
	   	Path controllerPropertiesPath = 
	   			Paths.get(CONFIG_DIR_NAME, controllerName + PROPERTIES_FILE_CONTROLLER_SUFFIX);
	   	
		if (Files.exists(controllerPropertiesPath)) {
			try {
				logger.warn("There is an existing configuration file at " + 
			                controllerPropertiesPath.toString() +
						    " with contents: " + Files.readAllBytes(controllerPropertiesPath));
				Path controllerPropertiesBakPath = 
						Paths.get(CONFIG_DIR_NAME, controllerName + 
                                  PROPERTIES_FILE_CONTROLLER_SUFFIX + ".bak");
				Files.copy(controllerPropertiesPath, 
						   controllerPropertiesBakPath, StandardCopyOption.REPLACE_EXISTING);
			} catch (Exception e) {
				logger.warn("{}: cannot be backed up", controllerName, e);
	    		return false;
			}
		} 
		
		return true;
	}
	
	/**
	 * persists properties-based controller configuration and makes a backup if necessary
	 * @param controllerName the controller name
	 * @return true if the properties has been stored in disk, false otherwise
	 */
	@Override
	public boolean storeController(String controllerName, Object configuration) {
	   	if (!(configuration instanceof Properties)) {
	   		throw new IllegalArgumentException("configuration must be of type properties to be handled by this manager");
	   	}
	   	
	   	Properties properties = (Properties) configuration;
	   	
		Path controllerPropertiesPath = 
	   			Paths.get(CONFIG_DIR_NAME, controllerName + PROPERTIES_FILE_CONTROLLER_SUFFIX);
    	if (Files.exists(controllerPropertiesPath)) {
    		try {
				Properties oldProperties = PropertyUtil.getProperties(controllerPropertiesPath.toFile());
				if (oldProperties.equals(properties)) {
					logger.info("A properties file with the same contents already exists for controller " + 
				                controllerName + 
				                ". No action is taken");
					return true;
				} else {
					this.backupController(controllerName);
				}
			} catch (Exception e) {
				logger.info("{}: no existing Properties", controllerName);
				// continue
			}
    	}
	  	
		try {
	    	File controllerPropertiesFile = controllerPropertiesPath.toFile();
	    	FileWriter writer = new FileWriter(controllerPropertiesFile);
	    	properties.store(writer, "Machine created Policy Controller Configuration");
		} catch (Exception e) {
			logger.warn("{}: cannot be STORED", controllerName, e);
			return false;
		}
		
		return true;
	}
	
	/**
	 * deletes properties-based controller configuration
	 * @param controllerName the controller name
	 * 
	 * @return true if the properties has been deleted from disk, false otherwise
	 */
	@Override
	public boolean deleteController(String controllerName) {
		
	   	Path controllerPropertiesPath = 
	   			Paths.get(CONFIG_DIR_NAME, 
	   					  controllerName + PROPERTIES_FILE_CONTROLLER_SUFFIX);

	   	if (Files.exists(controllerPropertiesPath)) {
			try {
				Path controllerPropertiesBakPath = 
						Paths.get(CONFIG_DIR_NAME, controllerName + 
                                  PROPERTIES_FILE_CONTROLLER_SUFFIX + ".bak");
				Files.move(controllerPropertiesPath, 
						   controllerPropertiesBakPath, 
						   StandardCopyOption.REPLACE_EXISTING);
			} catch (Exception e) {
				logger.warn("{}: cannot be DELETED", controllerName, e);
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
	public Properties getProperties(String name) {
	   	Path propertiesPath = 
	   			Paths.get(CONFIG_DIR_NAME, name + ".properties");
	   	
	   	if (!Files.exists(propertiesPath)) {
			throw new IllegalArgumentException("properties for " + name + " are not persisted.");
	   	}

		try {
			return PropertyUtil.getProperties(propertiesPath.toFile());
		} catch (Exception e) {
			logger.warn("{}: can't read properties @ {}", name, propertiesPath);
			throw new IllegalArgumentException("can't read properties for " + 
			                                   name + " @ " + 
					                           propertiesPath, e);
		}
	}
}
