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

import static org.junit.Assert.assertTrue;

import java.util.Properties;

import org.junit.BeforeClass;
import org.junit.Test;

public class TargetLockTest {
	private static Properties connectionProps;
	
	@BeforeClass
	public static void setup() {
		connectionProps = new Properties();
	    connectionProps.put("javax.persistence.jdbc.user", "policy_user");
	    connectionProps.put("javax.persistence.jdbc.password", "policy_user");
	    connectionProps.put("javax.persistence.jdbc.url", "jdbc:mariadb://hyperion-4.pedc.sbc.com:3306/drools");
	    connectionProps.put("javax.persistence.jdbc.driver", "org.mariadb.jdbc.Driver");
	    
	}
	@Test
	public void test() {
		
		TargetLock tl = new TargetLock("r1", "o1", System.currentTimeMillis() + 10000, connectionProps);
		assertTrue(tl.lock());
		assertTrue(tl.unlock());
	}
	

}
