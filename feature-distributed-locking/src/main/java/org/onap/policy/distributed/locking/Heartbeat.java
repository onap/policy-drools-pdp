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
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import org.apache.commons.dbcp2.BasicDataSource;
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
	private DistributedLockingProperties lockProps;
    
    /**
     * Data source used to connect to the DB containing locks.
     */
    private BasicDataSource dataSource;

	/**
	 * UUID 
	 */
	private UUID uuid;
	
	/**
	 * Countdown latch for testing 
	 */
	private volatile CountDownLatch latch;
	
	/**
	 * 
	 * @param uuid
	 * @param lockProps
	 * @param dataSource 
	 */
	public Heartbeat(UUID uuid, DistributedLockingProperties lockProps, BasicDataSource dataSource) {
		this.lockProps = lockProps;
		this.dataSource = dataSource;
		this.uuid = uuid;
		this.latch = new CountDownLatch(1);
	}
	/**
	 * 
	 * @param latch
	 * Used for testing purposes
	 */
	protected void giveLatch(CountDownLatch latch) {
		this.latch = latch;
	}
	
	@Override
	public void run() {

		
		long expirationAge = lockProps.getAgingProperty();

		try (Connection conn = dataSource.getConnection();
			PreparedStatement statement = conn
						.prepareStatement("UPDATE pooling.locks SET expirationTime = ? WHERE host = ?");) {

			statement.setLong(1, System.currentTimeMillis() + expirationAge);
			statement.setString(2, this.uuid.toString());
			statement.executeUpdate();
			
			latch.countDown();
			
		} catch (SQLException e) {
			logger.error("error in Heartbeat.run()", e);
		}

	}
	
	
}
