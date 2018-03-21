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
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TargetLock {
	
	private static final Logger logger = LoggerFactory.getLogger(TargetLock.class);
	
	/**
	 * The Target resource we want to lock
	 */
	private String resourceId;
	
	/**
	 * The owner of the lock
	 */
	private String owner;
	
	/**
	 * The time the lock will expire in milliseconds
	 */
	private long expirationTimeMs;
	
	/**
	 * A jdbc connection object through which we interact with db
	 */
	private Connection conn;
	
	/**
	 * Properties object containing properties needed by class
	 */
	private Properties lockProps;
	
	/**
	 * Constructs a TargetLock object.
	 * 
	 * @param resourceId ID of the entity we want to lock
	 * @param owner Owner of the lock
	 * @param expirationTimeMx Time in milliseconds the lock expires
	 * @param lockProps Properties object containing properties needed by class
	 */
	public TargetLock (String resourceId, String owner, long expirationTimeMs, Properties lockProps) {
		this.resourceId = resourceId;
		this.owner = owner;
		this.expirationTimeMs = expirationTimeMs;
		this.lockProps = lockProps;
		getConnection();
	}
	
	/**
	 * obtain a lock
	 */
	public boolean lock() {
			
		while (!grabLock() && !checkLock()) {
			
				// If currentTime has passed our expirationTime return false
			if (this.expirationTimeMs <= System.currentTimeMillis()) {
				cleanUp();
				return false;
			}
			
			try {
				TimeUnit.SECONDS.sleep(5);	
			} catch (InterruptedException e) {
				logger.error("error in TargetLock.lock()", e);
				cleanUp();
				Thread.currentThread().interrupt();
				return false;
			}
		}

		cleanUp();
		return true;

	}
	
	/**
	 * Unlock a resource by deleting it's associated record in the db
	 */
	public boolean unlock() {

		return deleteLock();
	}
	
	/**
	 * "Grabs" lock by attempting to insert a new record in the db.
	 *  If the insert fails due to duplicate key error it makes a call to update lock 
	 */
	private boolean grabLock() {
		
		try {	
				// try to insert a record into the table(thereby grabbing the lock)
				PreparedStatement lockInsert = conn.prepareStatement("INSERT INTO drools.locks " + "values (?, ?, ?)");
				lockInsert.setString(1, this.resourceId);
				lockInsert.setString(2, this.owner);
				lockInsert.setLong(3, this.expirationTimeMs);
				lockInsert.executeUpdate();
			} catch (SQLIntegrityConstraintViolationException e) {

				//If a duplicate key error is thrown, a record for the resourceId already exists
				//try and update the row in case we already own the lock or the expiration time has expired
			return updateLock();
			
		} catch (SQLException e) {
			logger.error("error in TargetLock.grabLock()", e);
			cleanUp();
			return false;
		}
		return true;
	}
	
	/** 
	 * Checks a db record to see if the expirationTime has passed.
	 * If it has passed, it updates the row with the current owner and sets the new expiration
	 * time to the current time plus 20000 ms (20 secs) 
	 */
	private boolean checkLock() {

		try {
			PreparedStatement updateStatement = conn.prepareStatement("UPDATE drools.locks SET owner = ?, expirationTime = ? WHERE expirationTime < ? AND resourceId = ?");
			updateStatement.setString(1, this.owner);
			updateStatement.setLong(2, System.currentTimeMillis() + 20000); //we'll need to increase the expiration time from the original
			updateStatement.setLong(3, System.currentTimeMillis());
			updateStatement.setString(4, this.resourceId);
			int updateReturn = updateStatement.executeUpdate();

				//no row was returned, so the expirationTime has not hit yet
			if (updateReturn == 0 ) {
				return false;
			}
				//a row was returned indicating that we succesfully updated the row
				//setting the new owner of the lock.
			else {
				return true;
			}
			
		} catch (SQLException e) {
			logger.error("error in TargetLock.cleanLock()", e);
			cleanUp();
			return false;
		}

	}
	
	/**
	 * 	UpdateLock attempts to update a db record for the target resource where we are the owner.
	 */
	private boolean updateLock() {

		try {
			PreparedStatement lockUpdate;
			lockUpdate = conn.prepareStatement("UPDATE drools.locks SET expirationTime = ? WHERE resourceId = ? AND owner = ?");
			lockUpdate.setLong(1, this.expirationTimeMs);
			lockUpdate.setString(2, this.resourceId);
			lockUpdate.setString(3, this.owner);

				// if the update doesn't return a count then we didn't update a row, meaning we don't own the lock
				// return false
			if (lockUpdate.executeUpdate() == 0) {
				return false;
			}
				// else, we already own the lock, update the expirationTime to the new time
			else {
				return true;
			}
		
		} catch (SQLException e) {
			logger.error("error in TargetLock.updateLock()", e);
			cleanUp();
			return false;
		}
	}
	
	/**
	 *To remove a lock we simply delete the record from the db 
	 */
	private boolean deleteLock() {
		try {
				

			PreparedStatement lockDelete = conn.prepareStatement("DELETE FROM drools.locks WHERE resourceId = ? AND owner = ?");
			lockDelete.setString(1, this.resourceId);
			lockDelete.setString(2, this.owner);
			
			lockDelete.execute();

			
		} catch (SQLException e) {
			logger.error("error in TargetLock.deleteLock()", e);
			cleanUp();
		};
		
			// Removing a lock should always return true if a db exception is not thrown.
			// There are 2 scenarios:
			// 1.) We owned the lock and successfully deleted the record from the DB
			// 2.) The delete failed because we didn't own the lock, therefore, there was no reason to remove a row
		return true;
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
			logger.error("error in TargetLock.cleanUp()", e);
		}

	}

}
