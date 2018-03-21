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

import java.io.IOException;
import java.util.Properties;

import org.onap.policy.drools.core.PolicySessionFeatureAPI;
import org.onap.policy.drools.utils.PropertyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DistributedLockingFeature implements PolicySessionFeatureAPI{
	
	// get an instance of logger 
	private static final Logger  logger = LoggerFactory.getLogger(DistributedLockingFeature.class);
	private Properties lockProps;
	
	@Override
	public int getSequenceNumber() {
        return 1000;
	}

	public boolean lockResource(String resourceId, String owner) {
		TargetLock tLock = new TargetLock(resourceId, owner, System.currentTimeMillis() + 5000, lockProps);
		
		return tLock.lock();
			
	}
	
	public boolean unlockResource(String resourceId, String owner) {
		TargetLock tLock = new TargetLock(resourceId, owner, System.currentTimeMillis() + 5000, lockProps);
		
		return tLock.unlock();
		
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void globalInit(String[] args, String configDir) {

		try {
			lockProps = PropertyUtil.getProperties(configDir + "/feature-distributed-locking.properties");

		} catch (IOException e1) {
			logger.error("initializing DistributedLockingFeature: ", e1);
		}

	}
}
