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

package org.onap.policy.distributed.locking.test;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.onap.policy.distributed.locking.DistributedLockingFeature;
import org.onap.policy.drools.persistence.SystemPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class TargetLockTest {
	private static final Logger logger = LoggerFactory.getLogger(TargetLockTest.class);
	private static final String DB_CONNECTION = "jdbc:h2:mem:drools;INIT=CREATE SCHEMA IF NOT EXISTS drools\\;SET SCHEMA drools";
	private static final String DB_USER = "";
	private static final String DB_PASSWORD = "";
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
		try {
			conn.close();
		} catch (SQLException e) {
			logger.error("Error in TargetLockTest.cleanUp()", e);
		}
	}
	
	@Before
	public void wipeDb() {
		PreparedStatement lockDelete;
		try {
			lockDelete = conn.prepareStatement("DELETE FROM drools.locks WHERE 1=1");
			lockDelete.executeUpdate();
		} catch (SQLException e) {
			logger.error("Error in TargetLockTest.wipeDb()", e);
		}

	}


	@Test
	public void testGrabLockSuccess() {
		assertTrue(distLockFeat.lockResource("resource1", "owner1", System.currentTimeMillis() + 3000));
	}
	

	@Test
	public void testGrabLockFail() {
		distLockFeat.lockResource("resource1", "owner1", System.currentTimeMillis() + 3000);
		assertFalse(distLockFeat.lockResource("resource1", "owner2", System.currentTimeMillis() + 3000));
	}
	
	@Test
	public void testUnlock() {
		distLockFeat.lockResource("resource1", "owner1", System.currentTimeMillis() + 3000);
		assertTrue(distLockFeat.unlockResource("resource1", "owner1"));
	}

	private static void getDBConnection() {
		try {
			conn = DriverManager.getConnection(DB_CONNECTION, DB_USER, DB_PASSWORD);
		} catch (SQLException e) {
			logger.error("Error in TargetLockTest.getDBConnection()", e);
		}
	}

	private static void createTable() {
		String createString = "create table drools.locks (resourceId VARCHAR(128), owner VARCHAR(128), expirationTime BIGINT, PRIMARY KEY (resourceId))";
		try {
			PreparedStatement createStmt = conn.prepareStatement(createString);
			createStmt.executeUpdate();

			
		} catch (SQLException e) {
			logger.error("Error in TargetLockTest.createTable()", e);
		}
	}	
	 

}
