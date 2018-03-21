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
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.onap.policy.common.utils.properties.exception.PropertyException;
import org.onap.policy.drools.core.PolicyResourceLockFeatureAPI;
import org.onap.policy.drools.features.PolicyEngineFeatureAPI;
import org.onap.policy.drools.persistence.SystemPersistence;
import org.onap.policy.drools.system.PolicyEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DistributedLockingFeature implements PolicyEngineFeatureAPI, PolicyResourceLockFeatureAPI {
	
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
	private DistributedLockingProperties lockProps;
	
	/**
	 *ScheduledExecutorService for LockHeartbeat 
	 */
	private ScheduledExecutorService scheduledExecutorService;
	
	/**
	 * UUID 
	 */
	private static final UUID uuid = UUID.randomUUID();
	
	/**
	 * Config directory
	 */
	@Override
	public int getSequenceNumber() {
        return 1000;
	}
	
	@Override
	public BeforeLockResult beforeLock(String resourceId, String owner, Callback callback) {
		
		if (callback != null) {
			logger.warn("BeforeLock not handling request. Callback not null");
			return BeforeLockResult.NOT_HANDLED;
		}
		
		TargetLock tLock = new TargetLock(resourceId, this.uuid, owner, lockProps);
		
		return (tLock.lock() ? BeforeLockResult.ACQUIRED : BeforeLockResult.DENIED);
		
	}

	@Override
	public Boolean beforeUnlock(String resourceId, String owner) {
		TargetLock tLock = new TargetLock(resourceId, this.uuid, owner, lockProps);
		
		return tLock.unlock();
	}

	public Boolean beforeIsActive(String resourceId, String owner) {
		TargetLock tLock = new TargetLock(resourceId, this.uuid, owner, lockProps);
		
		return tLock.isActive();
	}
	
	@Override
	public boolean afterStart(PolicyEngine engine) {

		try {
			this.lockProps = new DistributedLockingProperties(SystemPersistence.manager.getProperties(DistributedLockingFeature.CONFIGURATION_PROPERTIES_NAME));
		} catch (PropertyException e) {
			logger.error("DistributedLockingFeature feature properies have not been loaded", e);
			throw new DistributedLockingFeatureException(e);
		}
		
		long heartbeatInterval = this.lockProps.getHeartBeatIntervalProperty();
		
		cleanLockTable();
		Heartbeat heartbeat = new Heartbeat(this.uuid, lockProps);
		
		this.scheduledExecutorService = Executors.newScheduledThreadPool(1);
		this.scheduledExecutorService.scheduleAtFixedRate(heartbeat, heartbeatInterval, heartbeatInterval, TimeUnit.MILLISECONDS);
		return false;
	}
	
	/**
	 * This method kills the heartbeat thread and calls refreshLockTable which removes
	 * any records from the db where the current host is the owner.
	 */
	@Override
	public boolean beforeShutdown(PolicyEngine engine) {
		scheduledExecutorService.shutdown();
		cleanLockTable();
		return false;
	}

	/**
	 * This method removes all records owned by the current host from the db.
	 */
	private void cleanLockTable() {
		
	    try (Connection conn = DriverManager.getConnection(lockProps.getDbUrl(), 
			lockProps.getDbUser(),
			lockProps.getDbPwd());
	    	PreparedStatement statement = conn.prepareStatement("DELETE FROM pooling.locks WHERE host = ? OR expirationTime < ?");
	    	){
			
				statement.setString(1, this.uuid.toString());
				statement.setLong(2, System.currentTimeMillis());
				statement.executeUpdate();
			
		} catch (SQLException e) {
			logger.error("error in refreshLockTable()", e);
		}
		
	}
	
}
