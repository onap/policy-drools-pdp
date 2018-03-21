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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.onap.policy.drools.features.PolicyEngineFeatureAPI;
import org.onap.policy.drools.persistence.SystemPersistence;
import org.onap.policy.drools.system.PolicyEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DistributedLockingFeature implements PolicyEngineFeatureAPI, LockFeatureAPI {
	
	/**
	 * Logger instance
	 */
	private static final Logger logger = LoggerFactory.getLogger(DistributedLockingFeature.class);
	
	/**
	 * Properties Configuration Name
	 */
	public static final String CONFIGURATION_PROPERTIES_NAME = "feature-distributed-locking";
	
	/**
	 * Properties for locking feature
	 */
	private Properties lockProps;
	
	/**
	 *ScheduledExecutorService for LockHeartbeat 
	 */
	ScheduledExecutorService scheduledExecutorService;
	
	
	@Override
	public int getSequenceNumber() {
        return 1000;
	}
	
	@Override
	public boolean beforeLock(String resourceId) {
		TargetLock tLock = new TargetLock(resourceId, lockProps);
		
		return tLock.lock();
		
	}

	@Override
	public boolean beforeUnlock(String resourceId) {
		TargetLock tLock = new TargetLock(resourceId, lockProps);
		
		return tLock.unlock();
	}

	@Override
	public Boolean beforeIsActive(String resourceId) {
		TargetLock tLock = new TargetLock(resourceId, lockProps);
		
		return tLock.isActive();
	}
	
	@Override
	public boolean afterStart(PolicyEngine engine) {
		
		this.lockProps =  SystemPersistence.manager.getProperties(DistributedLockingFeature.CONFIGURATION_PROPERTIES_NAME);
		long heartbeatInterval = Long.parseLong(lockProps.getProperty(DistributedLockingProperties.HEARTBEAT_INTERVAL_PROPERTY));
		
		Heartbeat heartbeat = new Heartbeat(lockProps);
		
		this.scheduledExecutorService = Executors.newScheduledThreadPool(1);
		this.scheduledExecutorService.scheduleAtFixedRate(heartbeat, 10000, heartbeatInterval, TimeUnit.MILLISECONDS);
		
		return false;
	}
	
	@Override
	public boolean beforeShutdown(PolicyEngine engine) {
		scheduledExecutorService.shutdown();
		refreshLockTable();
		return false;
	}

	private void refreshLockTable() {
		
	    try (Connection conn = DriverManager.getConnection(lockProps.getProperty(DistributedLockingProperties.DB_URL), 
				lockProps.getProperty(DistributedLockingProperties.DB_USER), 
				lockProps.getProperty(DistributedLockingProperties.DB_PWD))){
			
			PreparedStatement statement;
			
				// delete all records this host owns or that are expired.
			statement = conn.prepareStatement("DELETE FROM drools.locks WHERE host = ? OR expirationTime < ?");
			statement.setString(1, System.getenv("FQDN"));
			statement.setLong(2, System.currentTimeMillis());
			statement.executeUpdate();
			
		} catch (SQLException e) {
			logger.error("error in refreshLockTable()", e);
		}
		
	}

}
