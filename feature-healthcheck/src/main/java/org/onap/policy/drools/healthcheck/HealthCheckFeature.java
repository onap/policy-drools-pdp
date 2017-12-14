/*-
 * ============LICENSE_START=======================================================
 * feature-healthcheck
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

package org.onap.policy.drools.healthcheck;

import org.onap.policy.drools.features.PolicyEngineFeatureAPI;
import org.onap.policy.drools.system.PolicyEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This feature provides healthcheck verification of remotely associated RESTful components
 */
public class HealthCheckFeature implements PolicyEngineFeatureAPI {	
	
	/**
	 * Logger
	 */
	private static Logger logger = LoggerFactory.getLogger(HealthCheckFeature.class); 
	
	/**
	 * Properties Configuration Name
	 */
	public static final String CONFIGURATION_PROPERTIES_NAME = "feature-healthcheck";

	@Override
	public int getSequenceNumber() {
		return 1000;
	}

	@Override
	public boolean afterStart(PolicyEngine engine) {
		try {
			HealthCheck.monitor.start();
		} catch (IllegalStateException e) {
			logger.error("Healthcheck Monitor cannot be started", e);
		}
		
		return false;
	}

	@Override
	public boolean afterShutdown() {
		try {
			HealthCheck.monitor.stop();
		} catch (IllegalStateException e) {
			logger.error("Healthcheck Monitor cannot be stopped", e);
		}
		
		return false;
	}
	
	/**
	 * gets the monitor
	 * @return the healthcheck monitor
	 */
	public HealthCheck getMonitor() {
		return HealthCheck.monitor;
	}

}
