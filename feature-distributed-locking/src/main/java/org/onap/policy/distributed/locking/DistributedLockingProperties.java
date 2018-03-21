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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DistributedLockingProperties {
	
	private static final Logger  logger = LoggerFactory.getLogger(DistributedLockingProperties.class);
	
	public static final String DB_DRIVER = "javax.persistence.jdbc.driver";
	public static final String DB_URL = "javax.persistence.jdbc.url";
	public static final String DB_USER = "javax.persistence.jdbc.user";
	public static final String DB_PWD = "javax.persistence.jdbc.password";
	public static final String LOCKING_PROPERTY = "distributed.locking.enabled";
	public static final String AGING_PROPERTY = "distributed.locking.lock.aging";
	public static final String HEARTBEAT_INTERVAL_PROPERTY = "distributed.locking.heartbeat.interval";
	
	private static Properties properties = null;

	private DistributedLockingProperties(){
		super();
	}

	public static void initProperties (Properties prop){
		logger.info("DistributedLockingProperties.initProperties(Properties): entry");
		logger.info("\n\nDistributedLockingProperties.initProperties: Properties = \n{}\n\n", prop);
		
		properties = prop;
	}

	public static String getProperty(String key){
		return properties.getProperty(key);
	}
	
	public static Properties getProperties() {
		return properties;
	}
}
