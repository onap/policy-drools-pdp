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

public class ActiveStandbyProperties {
	// get an instance of logger 
	private static Logger  logger = FlexLogger.getLogger(ActiveStandbyProperties.class);
		
	public static final String PDP_CHECK_INVERVAL = "pdp.checkInterval";
	public static final String PDP_UPDATE_INTERVAL = "pdp.updateInterval";
	public static final String PDP_TIMEOUT = "pdp.timeout";
	public static final String PDP_INITIAL_WAIT_PERIOD = "pdp.initialWait";

	public static final String NODE_NAME = "resource.name";
	public static final String SITE_NAME = "site_name";
		
	private static Properties properties = null;
	/*
	 * Initialize the parameter values from the droolsPersitence.properties file values
	 * 
	 * This is designed so that the Properties object is obtained from properties
	 * file and then is passed to this method to initialize the value of the parameters.
	 * This allows the flexibility of JUnit tests using getProperties(filename) to get the
	 * properties while runtime methods can use getPropertiesFromClassPath(filename).
	 * 
	 */
	public static void initProperties (Properties prop){
		logger.info("ActiveStandbyProperties.initProperties(Properties): entry");
		logger.info("\n\nActiveStandbyProperties.initProperties: Properties = \n" + prop + "\n\n");
		
		properties = prop;
	}

	public static String getProperty(String key){
		return properties.getProperty(key);
	}
	
	public static Properties getProperties() {
		return properties;
	}
}
