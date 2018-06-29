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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.apache.commons.dbcp2.BasicDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TargetLock {
	
	private static final Logger logger = LoggerFactory.getLogger(TargetLock.class);
	
	/**
	 * The Target resource we want to lock
	 */
	private String resourceId;
    
    /**
     * Data source used to connect to the DB containing locks.
     */
    private BasicDataSource dataSource;

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
	 * @param dataSource used to connect to the DB containing locks
	 */
	public TargetLock (String resourceId, UUID uuid, String owner, BasicDataSource dataSource) {
		this.resourceId = resourceId;
		this.uuid = uuid;
		this.owner = owner;
		this.dataSource = dataSource;
	}
	
	/**
	 * obtain a lock
     * @param holdSec the amount of time, in seconds, that the lock should be held
	 */
	public boolean lock(int holdSec) {
		
		return grabLock(holdSec);
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
     * @param holdSec the amount of time, in seconds, that the lock should be held
	 */
	private boolean grabLock(int holdSec) {

		// try to insert a record into the table(thereby grabbing the lock)
		try (Connection conn = dataSource.getConnection();

				PreparedStatement statement = conn.prepareStatement(
						"INSERT INTO pooling.locks (resourceId, host, owner, expirationTime) values (?, ?, ?, timestampadd(second, ?, now()))")) {
			
		    int i = 1;
			statement.setString(i++, this.resourceId);
			statement.setString(i++, this.uuid.toString());
			statement.setString(i++, this.owner);
			statement.setInt(i++, holdSec);
			statement.executeUpdate();
		}

		catch (SQLException e) {
			logger.error("error in TargetLock.grabLock()", e);
			return secondGrab(holdSec);
		}

		return true;
	}

	/**
	 * A second attempt at grabbing a lock. It first attempts to update the lock in case it is expired.
	 * If that fails, it attempts to insert a new record again
     * @param holdSec the amount of time, in seconds, that the lock should be held
	 */
	private boolean secondGrab(int holdSec) {

		try (Connection conn = dataSource.getConnection();

				PreparedStatement updateStatement = conn.prepareStatement(
						"UPDATE pooling.locks SET host = ?, owner = ?, expirationTime = timestampadd(second, ?, now()) WHERE resourceId = ? AND (owner = ? OR expirationTime < now())");

				PreparedStatement insertStatement = conn.prepareStatement(
						"INSERT INTO pooling.locks (resourceId, host, owner, expirationTime) values (?, ?, ?, timestampadd(second, ?, now()))");) {

		    int i = 1;
			updateStatement.setString(i++, this.uuid.toString());
			updateStatement.setString(i++, this.owner);
			updateStatement.setInt(i++, holdSec);
            updateStatement.setString(i++, this.resourceId);
            updateStatement.setString(i++, this.owner);

			// The lock was expired and we grabbed it.
			// return true
			if (updateStatement.executeUpdate() == 1) {
				return true;
			}

			// If our update does not return 1 row, the lock either has not expired
			// or it was removed. Try one last grab
			else {
			    i = 1;
				insertStatement.setString(i++, this.resourceId);
				insertStatement.setString(i++, this.uuid.toString());
				insertStatement.setString(i++, this.owner);
				insertStatement.setInt(i++, holdSec);

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

		try (Connection conn = dataSource.getConnection();

				PreparedStatement deleteStatement = conn.prepareStatement(
						"DELETE FROM pooling.locks WHERE resourceId = ? AND owner = ? AND host = ?")) {

			deleteStatement.setString(1, this.resourceId);
			deleteStatement.setString(2, this.owner);
			deleteStatement.setString(3, this.uuid.toString());

			return (deleteStatement.executeUpdate() == 1);

		} catch (SQLException e) {
			logger.error("error in TargetLock.deleteLock()", e);
			return false;
		}

	}

	/**
	 * Is the lock active
	 */
	public boolean isActive() {
		try (Connection conn = dataSource.getConnection();

				PreparedStatement selectStatement = conn.prepareStatement(
						"SELECT * FROM pooling.locks WHERE resourceId = ? AND host = ? AND owner= ? AND expirationTime >= now()")) {

			selectStatement.setString(1, this.resourceId);
			selectStatement.setString(2, this.uuid.toString());
			selectStatement.setString(3, this.owner);
			try (ResultSet result = selectStatement.executeQuery()) {

				// This will return true if the
				// query returned at least one row
				return result.first();
			}

		}

		catch (SQLException e) {
			logger.error("error in TargetLock.isActive()", e);
			return false;
		}

	}

	/**
	 * Is the resource locked
	 */
	public boolean isLocked() {

		try (Connection conn = dataSource.getConnection();
			
				PreparedStatement selectStatement = conn
						.prepareStatement("SELECT * FROM pooling.locks WHERE resourceId = ? AND expirationTime >= now()")) {

			selectStatement.setString(1, this.resourceId);
			try (ResultSet result = selectStatement.executeQuery()) {
				// This will return true if the
				// query returned at least one row
				return result.first();
			}
		}

		catch (SQLException e) {
			logger.error("error in TargetLock.isActive()", e);
			return false;
		}
	}

}
