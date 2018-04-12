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
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.onap.policy.common.utils.properties.exception.PropertyException;
import org.onap.policy.drools.core.lock.LockRequestFuture;
import org.onap.policy.drools.core.lock.PolicyResourceLockFeatureAPI;
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
	 * Reference to Heartbeat
	 */
	private static Heartbeat heartbeat = null;
	
	@Override
	public int getSequenceNumber() {
        return 1000;
	}
	
	@Override
	public Future<Boolean> beforeLock(String resourceId, String owner, Callback callback) {
		
		TargetLock tLock = new TargetLock(resourceId, uuid, owner, lockProps);
		
		return new LockRequestFuture(resourceId, owner, tLock.lock());
				
	}

	@Override
	public OperResult beforeUnlock(String resourceId, String owner) {
		TargetLock tLock = new TargetLock(resourceId, uuid, owner, lockProps);
		
		return(tLock.unlock() ? OperResult.OPER_ACCEPTED : OperResult.OPER_DENIED);
	}
	
	@Override
	public OperResult beforeIsLockedBy(String resourceId, String owner) {
		TargetLock tLock = new TargetLock(resourceId, uuid, owner, lockProps);

        return(tLock.isActive() ? OperResult.OPER_ACCEPTED : OperResult.OPER_DENIED);
	}
	
	@Override
	public OperResult beforeIsLocked(String resourceId) {
		TargetLock tLock = new TargetLock(resourceId, uuid, "dummyOwner", lockProps);

        return(tLock.isLocked() ? OperResult.OPER_ACCEPTED : OperResult.OPER_DENIED);
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
		heartbeat = new Heartbeat(uuid, lockProps);
		
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
			
				statement.setString(1, uuid.toString());
				statement.setLong(2, System.currentTimeMillis());
				statement.executeUpdate();
			
		} catch (SQLException e) {
			logger.error("error in refreshLockTable()", e);
		}
		
	}

	public static Heartbeat getHeartbeat() {
		return heartbeat;
	}
	
}
