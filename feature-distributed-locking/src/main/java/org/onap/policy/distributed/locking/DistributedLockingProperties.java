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

import java.util.Properties;

import org.onap.policy.common.utils.properties.PropertyConfiguration;
import org.onap.policy.common.utils.properties.exception.PropertyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DistributedLockingProperties extends PropertyConfiguration{
	
	private static final Logger  logger = LoggerFactory.getLogger(DistributedLockingProperties.class);
	
	/**
     * Feature properties all begin with this prefix.
     */
    public static final String PREFIX = "distributed.locking.";
    
	public static final String DB_DRIVER = "javax.persistence.jdbc.driver";
	public static final String DB_URL = "javax.persistence.jdbc.url";
	public static final String DB_USER = "javax.persistence.jdbc.user";
	public static final String DB_PWD = "javax.persistence.jdbc.password";
	public static final String AGING_PROPERTY = PREFIX + "lock.aging";
	public static final String HEARTBEAT_INTERVAL_PROPERTY = PREFIX + "heartbeat.interval";
	
	/**
     * Properties from which this was constructed.
     */
    private Properties source;

    /**
     * Database driver
     */
    @Property(name = DB_DRIVER)
    private String dbDriver;
    
    /**
     * Database url
     */
    @Property(name = DB_URL)
    private String dbUrl;
    
    /**
     * Database user
     */
    @Property(name = DB_USER)
    private String dbUser;
    
    /**
     * Database password
     */
    @Property(name = DB_PWD)
    private String dbPwd;
    
    /**
     * Used to set expiration time for lock.
     */
    @Property(name = AGING_PROPERTY, defaultValue = "300000")
    private long agingProperty;
    
    /**
     * Indicates intervals at which we refresh locks.
     */
    @Property(name = HEARTBEAT_INTERVAL_PROPERTY, defaultValue = "60000")
    private long heartBeatIntervalProperty;

    public DistributedLockingProperties(Properties props) throws PropertyException {
    	super(props);
    	source = props;
    }


	public Properties getSource() {
		return source;
	}


	public String getDbDriver() {
		return dbDriver;
	}


	public String getDbUrl() {
		return dbUrl;
	}


	public String getDbUser() {
		return dbUser;
	}


	public String getDbPwd() {
		return dbPwd;
	}


	public long getAgingProperty() {
		return agingProperty;
	}


	public long getHeartBeatIntervalProperty() {
		return heartBeatIntervalProperty;
	}

}
