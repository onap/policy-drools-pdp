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

package org.openecomp.policy.drools.system;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.openecomp.policy.common.logging.flexlogger.FlexLogger;
import org.openecomp.policy.common.logging.flexlogger.Logger;
import org.openecomp.policy.drools.core.PolicyContainer;
import org.openecomp.policy.drools.persistence.SystemPersistence;
import org.openecomp.policy.drools.utils.PropertyUtil;

/**
 * Programmatic entry point to the management layer
 */
public class Main {
	
	/**
	 * logger
	 */
	private static Logger  logger = FlexLogger.getLogger(Main.class, true);  
	
	public static void main(String args[]) {
		
		File configDir = new File(SystemPersistence.CONFIG_DIR_NAME);
		
		if (!configDir.isDirectory()) {
			throw new IllegalArgumentException
						("config directory: " + configDir.getAbsolutePath() + 
						 " not found");
		}
		
		
		/* 0. Start the CORE layer first */

		try {
			PolicyContainer.globalInit(args);
		} catch (Exception e) {
			System.out.println("policy-core startup failed");
			logger.warn("policy-core startup failed");
			e.printStackTrace();
		}
		
		/* 1. Configure the Engine */

		try {
			Path policyEnginePath = Paths.get(configDir.toPath().toString(), SystemPersistence.PROPERTIES_FILE_ENGINE);
			Properties properties = PropertyUtil.getProperties(policyEnginePath.toFile());
			PolicyEngine.manager.configure(properties);
		} catch (Exception e) {
			String msg = "Policy Engine cannot be configured with properties: " + e.getMessage() + " : " + PolicyEngine.manager;
			System.out.println(msg);
			logger.warn(msg);
		}
		
		/* 2. Start the Engine with the basic services only (no Policy Controllers) */
		
		try {
			boolean success = PolicyEngine.manager.start();
			if (!success) {
				System.out.println("Policy Engine found some problems starting some components: " + PolicyEngine.manager);
				logger.warn("Policy Engine is in an invalid state: " + PolicyEngine.manager);				
			}
		} catch (IllegalStateException e) {
			String msg = "Policy Engine is starting in an unexpected state: " + e.getMessage() + " : " + PolicyEngine.manager;
			System.out.println(msg);
			logger.warn(msg);
		} catch (Exception e) {
			String msg = "Unexpected Situation.  Policy Engine cannot be started: " + e.getMessage() + " : " + PolicyEngine.manager;
			System.out.println(msg);
			e.printStackTrace();
			System.exit(1);
		}
		
		/* 3. Create and start the controllers */
		
		File[] controllerFiles = configDir.listFiles();
		for (File config : controllerFiles) {

			if (config.getName().endsWith(SystemPersistence.PROPERTIES_FILE_CONTROLLER_SUFFIX)) {
				int idxSuffix = 
						config.getName().indexOf(SystemPersistence.PROPERTIES_FILE_CONTROLLER_SUFFIX);
				int lastIdxSuffix = 
						config.getName().lastIndexOf(SystemPersistence.PROPERTIES_FILE_CONTROLLER_SUFFIX);
				if (idxSuffix != lastIdxSuffix) {
					throw new IllegalArgumentException
								("Improper naming of controller properties file: " +
				                 "Expected <controller-name>" + 
				                 SystemPersistence.PROPERTIES_FILE_CONTROLLER_SUFFIX);
				}

				String name = 
						config.getName().substring(0, lastIdxSuffix);
				try {
					Properties properties = PropertyUtil.getProperties(config);
					PolicyController controller = PolicyEngine.manager.createPolicyController(name, properties);
					controller.start();
				} catch (Exception e) {
					System.out.println("can't instantiate Policy Controller based on properties file: " + 
				                       config + " with message " + e.getMessage());
					e.printStackTrace();
				} catch (LinkageError le) {
					System.out.println("can't instantiate Policy Controller based on properties file: " + 
		                       config + ". A Linkage Error has been encountered: " + le.getMessage());
					le.printStackTrace();					
				}
			}
		}
	}
}
