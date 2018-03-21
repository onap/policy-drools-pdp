/*
 * ============LICENSE_START=======================================================
 * feature-distributed-locking
 * ================================================================================
 * Copyright (C) 2018 AT&T Intellectual Property. All rights reserved.
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
package org.onap.policy.distributed.locking;

import java.util.Properties;

import org.onap.policy.drools.features.PolicyEngineFeatureAPI;
import org.onap.policy.drools.persistence.SystemPersistence;
import org.onap.policy.drools.system.PolicyEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DistributedLockingFeature implements PolicyEngineFeatureAPI {
	
	// get an instance of logger 
	private static final Logger logger = LoggerFactory.getLogger(DistributedLockingFeature.class);
	
	/**
	 * Properties Configuration Name
	 */
	public final static String CONFIGURATION_PROPERTIES_NAME = "feature-distributed-locking";
	
	private Properties lockProps;
	
	@Override
	public int getSequenceNumber() {
        return 1000;
	}

	public boolean lockResource(String resourceId, String owner, long expirationTime) {
		TargetLock tLock = new TargetLock(resourceId, owner, lockProps);
		
		return tLock.lock(expirationTime);
			
	}
	
	public boolean unlockResource(String resourceId, String owner) {
		TargetLock tLock = new TargetLock(resourceId, owner, lockProps);
		
		return tLock.unlock();
		
	}
	
	@Override
	public boolean afterStart(PolicyEngine engine) {
		
		lockProps =  SystemPersistence.manager.getProperties(DistributedLockingFeature.CONFIGURATION_PROPERTIES_NAME);
		
		if (lockProps != null) {
			return true;
		}
		
		return false;
		
	}
}
