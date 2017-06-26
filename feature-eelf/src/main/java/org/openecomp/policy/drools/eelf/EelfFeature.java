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

package org.openecomp.policy.drools.eelf;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.openecomp.policy.common.logging.flexlogger.FlexLogger;
import org.openecomp.policy.common.logging.flexlogger.Logger;
import org.openecomp.policy.drools.features.PolicyEngineFeatureAPI;
import org.openecomp.policy.drools.system.Main;
import org.openecomp.policy.drools.system.PolicyEngine;

public class EelfFeature implements PolicyEngineFeatureAPI {
	
	/**
	 * EELF logback configuration path system property
	 */
	public static final String EELF_LOGBACK_PATH_SYSTEM_PROPERTY = "com.att.eelf.logging.file";
	
	/**
	 * EELF logback configuration path value
	 */
	public static final String EELF_LOGBACK_PATH_DEFAULT = "config";
	
	/**
	 * EELF logback configuration file system property
	 */
	public static final String EELF_LOGBACK_FILE_SYSTEM_PROPERTY = "com.att.eelf.logging.path";
	
	/**
	 * EELF logback configuration file default value
	 */
	public static final String EELF_LOGBACK_FILE_DEFAULT = "logback.xml";

	@Override
	public boolean beforeBoot(PolicyEngine engine, String cliArgs[]) {
		
		String logback = System.getProperty(Main.LOGBACK_CONFIGURATION_FILE_SYSTEM_PROPERTY, 
				                            Main.LOGBACK_CONFIGURATION_FILE_DEFAULT);
		
		Path logbackPath = Paths.get(logback);
		
		if (System.getProperty(EELF_LOGBACK_PATH_SYSTEM_PROPERTY) == null)
			System.setProperty(EELF_LOGBACK_PATH_SYSTEM_PROPERTY, logbackPath.getFileName().toString());
		
		if (System.getProperty(EELF_LOGBACK_FILE_SYSTEM_PROPERTY) == null)
			System.setProperty(EELF_LOGBACK_FILE_SYSTEM_PROPERTY, 
					           logbackPath.toAbsolutePath().getParent().toString());
		
		Logger logger = FlexLogger.getLogger(this.getClass(), true);
		
		logger.warn("EELF/Common Frameworks Logging Enabled");
		if (logger.isInfoEnabled()) {
			logger.info("EELFFeature: Property " + Main.LOGBACK_CONFIGURATION_FILE_SYSTEM_PROPERTY + "=" + 
			            System.getProperty(Main.LOGBACK_CONFIGURATION_FILE_SYSTEM_PROPERTY));
			logger.info("EELFFeature: Property " + EELF_LOGBACK_PATH_SYSTEM_PROPERTY + "=" + 
		                System.getProperty(EELF_LOGBACK_PATH_SYSTEM_PROPERTY));
			logger.info("EELFFeature: Property " + EELF_LOGBACK_FILE_SYSTEM_PROPERTY + "=" + 
	                    System.getProperty(EELF_LOGBACK_FILE_SYSTEM_PROPERTY));
		}
		
		return false;
	};
	
	@Override
	public int getSequenceNumber() {
		return 0;
	}

}
