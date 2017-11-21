/*-
 * ============LICENSE_START=======================================================
 * feature-eelf
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

package org.onap.policy.drools.eelf;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.onap.policy.common.logging.eelf.Configuration;
import org.onap.policy.common.logging.flexlogger.FlexLogger;
import org.onap.policy.common.logging.flexlogger.Logger;
import org.onap.policy.drools.features.PolicyEngineFeatureAPI;
import org.onap.policy.drools.system.Main;
import org.onap.policy.drools.system.PolicyEngine;

/**
 * Feature EELF : Enables EELF Logging Libraries 
 */
public class EelfFeature implements PolicyEngineFeatureAPI {

	@Override
	final public boolean beforeBoot(PolicyEngine engine, String cliArgs[]) {
		
		String logback = System.getProperty(Main.LOGBACK_CONFIGURATION_FILE_SYSTEM_PROPERTY, 
				                            Main.LOGBACK_CONFIGURATION_FILE_DEFAULT);		
		Path logbackPath = Paths.get(logback);
		
		if (System.getProperty(Configuration.PROPERTY_LOGGING_FILE_PATH) == null)
			System.setProperty(Configuration.PROPERTY_LOGGING_FILE_PATH, 
					           logbackPath.toAbsolutePath().getParent().toString());
		
		if (System.getProperty(Configuration.PROPERTY_LOGGING_FILE_NAME) == null)
			System.setProperty(Configuration.PROPERTY_LOGGING_FILE_NAME, 
					           logbackPath.getFileName().toString());
		
		Logger logger = FlexLogger.getLogger(this.getClass(), true);
		
		if (logger.isInfoEnabled()) {
			logger.info("eelf-feature: Property " + Main.LOGBACK_CONFIGURATION_FILE_SYSTEM_PROPERTY + "=" + 
			            System.getProperty(Main.LOGBACK_CONFIGURATION_FILE_SYSTEM_PROPERTY));
			logger.info("eelf-feature: Property " + Configuration.PROPERTY_LOGGING_FILE_PATH + "=" + 
		                System.getProperty(Configuration.PROPERTY_LOGGING_FILE_PATH));
			logger.info("eelf-feature: Property " + Configuration.PROPERTY_LOGGING_FILE_NAME + "=" + 
	                    System.getProperty(Configuration.PROPERTY_LOGGING_FILE_NAME));
		}
		
		return false;
	}
	
	@Override
	public int getSequenceNumber() {
		return 0;
	}

}
