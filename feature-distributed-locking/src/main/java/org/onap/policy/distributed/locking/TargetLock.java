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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Properties;

import org.onap.policy.drools.utils.NetworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TargetLock {
	
	private static final Logger logger = LoggerFactory.getLogger(TargetLock.class);
	
	/**
	 * The Target resource we want to lock
	 */
	private String resourceId;
	
	/**
	 * A connection object through which we interact with db
	 */
	private Connection conn;
	
	/**
	 * Properties object containing properties needed by class
	 */
	private Properties lockProps;

	/**
	 * PreparedStatement used against the db
	 */
	private PreparedStatement statement;
	
	/**
	 * Constructs a TargetLock object.
	 * 
	 * @param resourceId ID of the entity we want to lock
	 * @param lockProps Properties object containing properties needed by class
	 */
	public TargetLock (String resourceId, Properties lockProps) {
		this.resourceId = resourceId;
		this.lockProps = lockProps;
		getConnection();
	}
	
	/**
	 * obtain a lock
	 */
	public boolean lock() {
		
		return grabLock();
	}
	
	/**
	 * Unlock a resource by deleting it's associated record in the db
	 */
	public boolean unlock() {
		return deleteLock();
	}
	
	/**
	 * "Grabs" lock by attempting to insert a new record in the db.
	 *  If the insert fails due to duplicate key error resource is already locked
	 *  so we call secondGrab. 
	 */
	private boolean grabLock() {
		
		if (conn == null) {
			logger.error("error in TargetLock.grabLock(). DB connection null");
			return false;
		}
		
		try {	
					// try to insert a record into the table(thereby grabbing the lock)
				statement = conn.prepareStatement("INSERT INTO drools.locks " + "values (?, ?, ?)");
				statement.setString(1, this.resourceId);
				statement.setString(2, NetworkUtil.getHostname());
				statement.setLong(3, System.currentTimeMillis() + Long.valueOf(lockProps.getProperty(DistributedLockingProperties.AGING_PROPERTY)));
				statement.executeUpdate();
			} 
		catch (SQLException e) {
				// any other SQL error should return false
			logger.error("error in TargetLock.grabLock()", e);
			return secondGrab();
		}			
		
		cleanUp();
		return true;
	}

	private boolean secondGrab() {
		
		try {
			statement = conn.prepareStatement("UPDATE drools.locks SET host = ?, expirationTime = ? WHERE expirationTime < ? AND resourceId = ?");
			statement.setString(1, NetworkUtil.getHostname());
			statement.setLong(2, System.currentTimeMillis() + Long.valueOf(lockProps.getProperty(DistributedLockingProperties.AGING_PROPERTY))); 
			statement.setLong(3, System.currentTimeMillis());
			statement.setString(4, this.resourceId);
			
				// The lock was expired and we grabbed it.
				// return true
			if (statement.executeUpdate() == 1) {
				cleanUp();
				return true;	
			}
				// If our update does not return 1 row, the lock either has not expired
				// or it was removed. Try one last grab
			else {				
				statement = conn.prepareStatement("INSERT INTO drools.locks " + "values (?, ?, ?)");
				statement.setString(1, this.resourceId);
				statement.setString(2, NetworkUtil.getHostname());
				statement.setLong(3, System.currentTimeMillis() + Long.valueOf(lockProps.getProperty(DistributedLockingProperties.AGING_PROPERTY)));
				statement.executeUpdate();
				
					// If our insert returns 1 we successfully grabbed the lock
				if (statement.executeUpdate() == 1) {
					cleanUp();
					return true;	
				}
				
				else {
					cleanUp();
					return false;
				}
			}
			
		} catch (SQLException e) {
			logger.error("error in TargetLock.secondGrab()", e);
			cleanUp();
			return false;
		}
		
	}
	
	/**
	 *To remove a lock we simply delete the record from the db 
	 */
	private boolean deleteLock() {
		
		if (conn == null) {
			logger.error("error in TargetLock.deleteLock(). DB connection null");
			return false;
		}
		try {
			statement = conn.prepareStatement("DELETE FROM drools.locks WHERE resourceId = ? AND host = ?");
			statement.setString(1, this.resourceId);
			statement.setString(2, NetworkUtil.getHostname());
			
			statement.execute();

		} catch (SQLException e) {
			logger.error("error in TargetLock.deleteLock()", e);
			cleanUp();
			return false;
		};
		
			// Removing a lock should always return true if a db exception is not thrown.
			// There are 2 scenarios:
			// 1.) We owned the lock and successfully deleted the record from the DB
			// 2.) The delete failed because we didn't own the lock, therefore, there was no reason to remove a row
		cleanUp();
		return true;
	}
	
	/**
	 * Is the lock active
	 */
	public boolean isActive() {
		if (conn == null) {
			logger.error("error in TargetLock.isActive(). DB connection null");
			return false;
		}
		
		try {
			statement = conn.prepareStatement("SELECT * FROM drools.locks WHERE resourceId = ? AND host = ?");
			statement.setString(1, this.resourceId);
			statement.setString(2, NetworkUtil.getHostname());
			

			ResultSet result = statement.executeQuery();

				// 	This will return true if the 
				//  query returned at least one row 
			if (result.first()) {
				cleanUp();
				return true;	
			}
			
			else {
				cleanUp();
				return false;
			}

		} catch (SQLException e) {
			logger.error("error in TargetLock.isActive()", e);
			cleanUp();
			return false;
		}
	}
	
	/**
	 * Close connection to db
	 */
	private void cleanUp() {} {
		try {
			if(conn != null) {
				conn.close();
			}
		} catch (SQLException e) {
			logger.error("error in TargetLock.cleanUp()", e);
		}
	}
	
	/**
	 * Establish connection to db
	 */
	private void getConnection() {

	    try {
			this.conn = DriverManager.getConnection(lockProps.getProperty(DistributedLockingProperties.DB_URL), 
													lockProps.getProperty(DistributedLockingProperties.DB_USER), 
													lockProps.getProperty(DistributedLockingProperties.DB_PWD));
		} catch (SQLException e) {
			logger.error("error in TargetLock.getConnection()", e);
		}

	}
}
