/*-
 * ============LICENSE_START=======================================================
 * policy-healthcheck
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

package org.openecomp.policy.drools.healthcheck;

import java.util.Properties;

import org.openecomp.policy.drools.features.PolicyEngineFeatureAPI;
import org.openecomp.policy.drools.system.PolicyEngine;

public class HealthCheckFeature implements PolicyEngineFeatureAPI {
	
	public static final String CONFIGURATION_PROPERTIES_NAME = "policy-healthcheck";

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getSequenceNumber() {
		return 1000;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean beforeStart(PolicyEngine engine) throws IllegalStateException {
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean afterStart(PolicyEngine engine) {
		try {
			HealthCheck.monitor.start();
		} catch (IllegalStateException e) {
			e.printStackTrace();
		}
		
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean beforeShutdown(PolicyEngine engine) {
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean afterShutdown(PolicyEngine engine) {
		try {
			HealthCheck.monitor.stop();
		} catch (IllegalStateException e) {
			e.printStackTrace();
		}
		
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean beforeConfigure(PolicyEngine engine, Properties properties) {
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean afterConfigure(PolicyEngine engine) {
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean beforeActivate(PolicyEngine engine) {
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean afterActivate(PolicyEngine engine) {
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean beforeDeactivate(PolicyEngine engine) {
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean afterDeactivate(PolicyEngine engine) {
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean beforeStop(PolicyEngine engine) {
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean afterStop(PolicyEngine engine) {
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean beforeLock(PolicyEngine engine) {
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean afterLock(PolicyEngine engine) {
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean beforeUnlock(PolicyEngine engine) {
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean afterUnlock(PolicyEngine engine) {
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
