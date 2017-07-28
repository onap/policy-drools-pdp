/*-
 * ============LICENSE_START=======================================================
 * policy-persistence
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

package org.openecomp.policy.drools.activestandby;

import java.util.Properties;

import org.openecomp.policy.common.logging.flexlogger.FlexLogger;
import org.openecomp.policy.common.logging.flexlogger.Logger;

public class DroolsPersistenceProperties {
	// get an instance of logger 
	private static Logger  logger = FlexLogger.getLogger(DroolsPersistenceProperties.class);		
	/*
	 * droolsPersistence.properties parameter key values
	 */
	public static final String DB_DRIVER = "javax.persistence.jdbc.driver";
	public static final String DB_DATA_SOURCE  = "hibernate.dataSource";
	public static final String DB_URL = "javax.persistence.jdbc.url";
	public static final String DB_USER = "javax.persistence.jdbc.user";
	public static final String DB_PWD = "javax.persistence.jdbc.password";
	
	private static Properties properties = null;
	/*
	 * Initialize the parameter values from the droolsPersitence.properties file values
	 * 
	 * This is designed so that the Properties object is obtained from the droolsPersistence.properties
	 * file and then is passed to this method to initialize the value of the parameters.
	 * This allows the flexibility of JUnit tests using getProperties(filename) to get the
	 * properties while runtime methods can use getPropertiesFromClassPath(filename).
	 * 
	 */
	public static void initProperties (Properties prop){
		logger.info("DroolsPersistenceProperties.initProperties(Properties): entry");
		logger.info("\n\nDroolsPersistenceProperties.initProperties: Properties = \n" + prop + "\n\n");
		
		properties = prop;
	}

	public static String getProperty(String key){
		return properties.getProperty(key);
	}
	
	public static Properties getProperties() {
		return properties;
	}
}
