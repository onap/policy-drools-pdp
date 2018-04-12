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

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.onap.policy.distributed.locking.DistributedLockingFeature;
import org.onap.policy.drools.core.lock.PolicyResourceLockFeatureAPI.OperResult;
import org.onap.policy.drools.persistence.SystemPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class TargetLockTest {
	private static final Logger logger = LoggerFactory.getLogger(TargetLockTest.class);
	private static final String DB_CONNECTION = "jdbc:h2:mem:pooling;INIT=CREATE SCHEMA IF NOT EXISTS pooling\\;SET SCHEMA pooling";
	private static final String DB_USER = "user";
	private static final String DB_PASSWORD = "password";
	private static Connection conn = null;
	private static DistributedLockingFeature distLockFeat;

	@BeforeClass
	public static void setup() {
		getDBConnection();
		createTable();
		SystemPersistence.manager.setConfigurationDir("src/test/resources");
		distLockFeat = new DistributedLockingFeature();
		distLockFeat.afterStart(null);
	
	}
	
	@AfterClass
	public static void cleanUp() {
		distLockFeat.beforeShutdown(null);
		try {
			conn.close();
		} catch (SQLException e) {
			logger.error("Error in TargetLockTest.cleanUp()", e);
		}
	}
	
	@Before
	public void wipeDb() {
		try (PreparedStatement lockDelete = conn.prepareStatement("DELETE FROM pooling.locks");){
			lockDelete.executeUpdate();
		} catch (SQLException e) {
			logger.error("Error in TargetLockTest.wipeDb()", e);
			throw new RuntimeException(e);
		}

	}
	
	@Test
	public void testGrabLockSuccess() throws InterruptedException, ExecutionException {
		assertTrue(distLockFeat.beforeLock("resource1", "owner1", null).get());
	
			//attempt to grab expiredLock
		try (PreparedStatement updateStatement = conn.prepareStatement("UPDATE pooling.locks SET expirationTime = ? WHERE resourceId = ?");)
		{
			updateStatement.setLong(1, System.currentTimeMillis() - 1000);
			updateStatement.setString(2, "resource1");
			updateStatement.executeUpdate();
				
		} catch (SQLException e) {
			logger.error("Error in TargetLockTest.testGrabLockSuccess()", e);
			throw new RuntimeException(e);
		}
		
		assertTrue(distLockFeat.beforeLock("resource1", "owner1", null).get());
	}

	@Test
	public void testExpiredLocks() throws InterruptedException, ExecutionException {

		CountDownLatch rowExpireLatch = new CountDownLatch(1);
		CountDownLatch heartbeatLatch = new CountDownLatch(1);
			
			//grab lock
		distLockFeat.beforeLock("resource1", "owner1", null);
		
			//Wait for lock to expire
		try {
			rowExpireLatch.await(150, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			logger.error("Error in testExpiredLocks", e);
		}
		
		// Grab reference to heartbeat object
		Heartbeat heartbeat = DistributedLockingFeature.getHeartbeat();

		// Pass heartbeat object countdown latch
		try {
			heartbeat.giveLatch(heartbeatLatch);
			heartbeatLatch.await(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			logger.error("Error in testExpiredLocks", e);
		}
	
			//Heartbeat should keep it active
		assertFalse(distLockFeat.beforeLock("resource1", "owner1", null).get());
	}
	
	@Test
	public void testGrabLockFail() throws InterruptedException, ExecutionException {
		CountDownLatch latch = new CountDownLatch(1);
		
		distLockFeat.beforeLock("resource1", "owner1", null);
		
		try {
			latch.await(10, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			logger.error("Error in testExpiredLocks", e);
		}
		assertFalse(distLockFeat.beforeLock("resource1", "owner1", null).get());

	}
	
	
	@Test
	public void testUnlock() throws InterruptedException, ExecutionException {
		distLockFeat.beforeLock("resource1", "owner1", null);

		assertEquals(OperResult.OPER_ACCEPTED, distLockFeat.beforeUnlock("resource1", "owner1"));
		assertTrue(distLockFeat.beforeLock("resource1", "owner1", null).get());
	}
	
	@Test
	public void testIsActive() {
		assertEquals(OperResult.OPER_DENIED, distLockFeat.beforeIsLockedBy("resource1", "owner1"));
		distLockFeat.beforeLock("resource1", "owner1", null);
		assertEquals(OperResult.OPER_ACCEPTED, distLockFeat.beforeIsLockedBy("resource1", "owner1"));
		assertEquals(OperResult.OPER_DENIED, distLockFeat.beforeIsLockedBy("resource1", "owner2"));

		// isActive on expiredLock
		try (PreparedStatement updateStatement = conn
				.prepareStatement("UPDATE pooling.locks SET expirationTime = ? WHERE resourceId = ?");) {
			updateStatement.setLong(1, System.currentTimeMillis() - 5000);
			updateStatement.setString(2, "resource1");
			updateStatement.executeUpdate();

		} catch (SQLException e) {
			logger.error("Error in TargetLockTest.testIsActive()", e);
			throw new RuntimeException(e);
		}

		assertEquals(OperResult.OPER_DENIED, distLockFeat.beforeIsLockedBy("resource1", "owner1"));

		distLockFeat.beforeLock("resource1", "owner1", null);
			//Unlock record, next isActive attempt should fail
		distLockFeat.beforeUnlock("resource1", "owner1");
		assertEquals(OperResult.OPER_DENIED, distLockFeat.beforeIsLockedBy("resource1", "owner1"));
		
	}
	
	@Test
	public void testHeartbeat() {
		
		CountDownLatch rowExpireLatch = new CountDownLatch(1);
		CountDownLatch heartbeatLatch = new CountDownLatch(1);
		
			//grab lock
		distLockFeat.beforeLock("resource1", "owner1", null);
		
			//Wait for lock to expire
		try {
			rowExpireLatch.await(150, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			logger.error("Error in testExpiredLocks", e);
		}
		
			//Grab reference to heartbeat object
		Heartbeat heartbeat = DistributedLockingFeature.getHeartbeat();
		
			//Pass heartbeat object countdown latch
		try {
			heartbeat.giveLatch(heartbeatLatch);
			heartbeatLatch.await(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			logger.error("Error in testExpiredLocks", e);
		}
			//At this point the heartbeat object should hve
			//refreshed the lock. assert that resource1 is
			//locked
		assertEquals(OperResult.OPER_ACCEPTED, distLockFeat.beforeIsLocked("resource1"));
	}
	
	@Test
	public void unlockBeforeLock() {
		assertEquals(OperResult.OPER_DENIED, distLockFeat.beforeUnlock("resource1", "owner1"));
		distLockFeat.beforeLock("resource1", "owner1", null);
		assertEquals(OperResult.OPER_ACCEPTED, distLockFeat.beforeUnlock("resource1", "owner1"));
		assertEquals(OperResult.OPER_DENIED, distLockFeat.beforeUnlock("resource1", "owner1"));
	}
	
	@Test
	public void testIsLocked() {
		assertEquals(OperResult.OPER_DENIED, distLockFeat.beforeIsLocked("resource1"));
		distLockFeat.beforeLock("resource1", "owner1", null);
		assertEquals(OperResult.OPER_ACCEPTED, distLockFeat.beforeIsLocked("resource1"));
	
	}
	
	private static void getDBConnection() {
		try {
			conn = DriverManager.getConnection(DB_CONNECTION, DB_USER, DB_PASSWORD);
		} catch (SQLException e) {
			logger.error("Error in TargetLockTest.getDBConnection()", e);
		}
	}

	private static void createTable() {
		String createString = "create table if not exists pooling.locks (resourceId VARCHAR(128), host VARCHAR(128), owner VARCHAR(128), expirationTime BIGINT, PRIMARY KEY (resourceId))";
		try (PreparedStatement createStmt = conn.prepareStatement(createString);) {
			createStmt.executeUpdate();
			
		} catch (SQLException e) {
			logger.error("Error in TargetLockTest.createTable()", e);
		}
	}	
	 

}
