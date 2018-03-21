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
import java.util.UUID;

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
	 * Properties object containing properties needed by class
	 */
	private DistributedLockingProperties lockProps;

	/**
	 * UUID 
	 */
	private UUID uuid;
	
	/**
	 * Owner
	 */
	private String owner;
	
	/**
	 * Constructs a TargetLock object.
	 * 
	 * @param resourceId ID of the entity we want to lock
	 * @param lockProps Properties object containing properties needed by class
	 */
	public TargetLock (String resourceId, UUID uuid, String owner, DistributedLockingProperties lockProps) {
		this.resourceId = resourceId;
		this.uuid = uuid;
		this.owner = owner;
		this.lockProps = lockProps;
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

		try (Connection conn = DriverManager.getConnection(lockProps.getDbUrl(), lockProps.getDbUser(),
				lockProps.getDbPwd());

				// try to insert a record into the table(thereby grabbing the lock)
				PreparedStatement statement = conn
						.prepareStatement("INSERT INTO pooling.locks (resourceId, host, owner, expirationTime) values (?, ?, ?, ?)");) {
			statement.setString(1, this.resourceId);
			statement.setString(2, this.uuid.toString());
			statement.setString(3, this.owner);
			statement.setLong(4, System.currentTimeMillis() + lockProps.getAgingProperty());

			statement.executeUpdate();
		}  catch (SQLException e) {
			logger.error("error in TargetLock.grabLock()", e);
			return secondGrab();
		}

		return true;
	}

	private boolean secondGrab() {

		try (Connection conn = DriverManager.getConnection(lockProps.getDbUrl(), lockProps.getDbUser(),
				lockProps.getDbPwd());

				PreparedStatement updateStatement = conn.prepareStatement("UPDATE pooling.locks SET host = ?, owner = ?, expirationTime = ? WHERE expirationTime < ? AND resourceId = ?");
				
				PreparedStatement insertStatement = conn.prepareStatement("INSERT INTO pooling.locks (resourceId, host, owner, expirationTime) values (?, ?, ?, ?)");) {

			updateStatement.setString(1, this.uuid.toString());
			updateStatement.setString(2, this.owner);
			updateStatement.setLong(3, System.currentTimeMillis() + lockProps.getAgingProperty());
			updateStatement.setLong(4, System.currentTimeMillis());
			updateStatement.setString(5, this.resourceId);

			// The lock was expired and we grabbed it.
			// return true
			if (updateStatement.executeUpdate() == 1) {
				return true;
			}
			// If our update does not return 1 row, the lock either has not expired
			// or it was removed. Try one last grab
			else {
				insertStatement.setString(1, this.resourceId);
				insertStatement.setString(2, this.uuid.toString());
				insertStatement.setString(3, this.owner);
				insertStatement.setLong(4, System.currentTimeMillis() + lockProps.getAgingProperty());

					// If our insert returns 1 we successfully grabbed the lock
				return (insertStatement.executeUpdate() == 1);
			}

		} catch (SQLException e) {
			logger.error("error in TargetLock.secondGrab()", e);
			return false;
		}

	}
	
	/**
	 *To remove a lock we simply delete the record from the db 
	 */
	private boolean deleteLock() {

		try (Connection conn = DriverManager.getConnection(lockProps.getDbUrl(), lockProps.getDbUser(),
				lockProps.getDbPwd());

				PreparedStatement deleteStatement = conn
						.prepareStatement("DELETE FROM pooling.locks WHERE resourceId = ? AND owner = ?");) {

			deleteStatement.setString(1, this.resourceId);
			deleteStatement.setString(2, this.owner);

			deleteStatement.execute();

		} catch (SQLException e) {
			logger.error("error in TargetLock.deleteLock()", e);
			return false;
		}
		;

		// Removing a lock should always return true if a db exception is not thrown.
		// There are 2 scenarios:
		// 1.) We owned the lock and successfully deleted the record from the DB
		// 2.) The delete failed because we didn't own the lock, therefore, there was no
		// reason to remove a row
		return true;
	}
	
	/**
	 * Is the lock active
	 */
	public boolean isActive() {

		try (Connection conn = DriverManager.getConnection(lockProps.getDbUrl(), lockProps.getDbUser(),
				lockProps.getDbPwd());

				PreparedStatement selectStatement = conn
						.prepareStatement("SELECT * FROM pooling.locks WHERE resourceId = ? AND host = ? AND owner= ? AND expirationTime > ?");) {
			{
				selectStatement.setString(1, this.resourceId);
				selectStatement.setString(2, this.uuid.toString());
				selectStatement.setString(3, this.owner);
				selectStatement.setLong(4, System.currentTimeMillis());

				ResultSet result = selectStatement.executeQuery();

				// This will return true if the
				// query returned at least one row
				return result.first();

			}
		} catch (SQLException e) {
			logger.error("error in TargetLock.isActive()", e);
			return false;
		}
	}

}
