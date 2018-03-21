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
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class TargetLock {

	private String resourceId;
	private String owner;
	private long expirationTimeMs;
	
	public TargetLock (String resourceId, String owner, long expirationTimeMs) {
		this.resourceId = resourceId;
		this.owner = owner;
		this.expirationTimeMs = expirationTimeMs;
	}
	
	public boolean lock() throws SQLException {
			
			if (grabLock()) {
					return true;
			}
			else {
					while (!grabLock()) {
						if (checkLock() == false) {
							try {
								TimeUnit.SECONDS.sleep(5);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						}
						else {
							return true;
						}
					}
			}
	
		return false;
	}
	
	public boolean unlock() {
		return true;
	}
	
	private boolean grabLock() {
		try {
			Connection conn = getConnection();
			PreparedStatement lockInsert = conn.prepareStatement("INSERT INTO drools.locks " + "values (?, ?, ?)");
			lockInsert.setString(1, this.resourceId);
			lockInsert.setString(2, this.owner);
			lockInsert.setLong(3, this.expirationTimeMs);
			lockInsert.executeUpdate();
			conn.close();
		
		} catch (SQLIntegrityConstraintViolationException e) {
			
			return false;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return true;
	}
	
	private boolean checkLock() {
		
		//Record for the key (resourceId) already exists

		try {
			Connection conn = getConnection();
			PreparedStatement updateStatement = conn.prepareStatement("UPDATE drools.locks SET owner = ?, expirationTime = ? WHERE expirationTime < ? AND resourceId = ?");
			updateStatement.setString(1, this.owner);
			updateStatement.setLong(2, System.currentTimeMillis() + 20000); //we'll need to increase the expiration time from the original
			updateStatement.setLong(3, System.currentTimeMillis());
			updateStatement.setString(4, this.resourceId);
			conn.close();
			if (updateStatement.executeUpdate() == 0 ) {
				return false;
			}
			else {
				return true;
			}
			
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}

	}
	
	
	
	private Connection getConnection() throws SQLException {

	    Connection conn = null;
	    Properties connectionProps = new Properties();
	    connectionProps.put("user", "policy_user");
	    connectionProps.put("password", "policy_user");
	    

	    conn = DriverManager.getConnection(
	                   "jdbc:mariadb://hyperion-4.pedc.sbc.com:3306/drools","policy_user","policy_user");
	    return conn;
	}

}
