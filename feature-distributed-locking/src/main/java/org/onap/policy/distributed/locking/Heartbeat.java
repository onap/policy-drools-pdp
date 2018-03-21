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

import org.onap.policy.drools.utils.NetworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * This runnable class scans the locks table for all locks owned by this host.
 * It refreshes the expiration time of each lock using the locking.distributed.aging
 * property
 *
 */
public class Heartbeat implements Runnable{

	private static final Logger logger = LoggerFactory.getLogger(Heartbeat.class);

	/**
	 * Properties object containing properties needed by class
	 */
	private Properties lockProps;

	/**
	 * A connection object through which we interact with db
	 */
	private Connection conn;
	
	/**
	 * PreparedStatement used against the db
	 */
	private PreparedStatement statement;
	
	public Heartbeat(Properties lockProps) {
		this.lockProps = lockProps;
		getConnection();
	}
	
	@Override
	public void run() {
		
		long expirationAge = Long.parseLong(lockProps.getProperty(DistributedLockingProperties.AGING_PROPERTY));
		
		if (conn != null) {
			try {
				statement = conn.prepareStatement("UPDATE drools.locks SET expirationTime = ? WHERE host = ?");
				statement.setLong(1, System.currentTimeMillis() + expirationAge);
				statement.setString(2, NetworkUtil.getHostname());
				statement.executeUpdate();
			} catch (SQLException e) {

				logger.error("error in Heartbeat.run()", e);
			}
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
