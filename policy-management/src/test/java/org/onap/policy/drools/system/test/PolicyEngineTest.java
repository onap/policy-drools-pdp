/*-
 * ============LICENSE_START=======================================================
 * policy-management
 * ================================================================================
 * Copyright (C) 2017 AT&T Intellectual Property. All rights reserved.
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
package org.onap.policy.drools.system.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Properties;

import javax.ws.rs.core.Response;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.onap.policy.drools.http.client.HttpClient;
import org.onap.policy.drools.http.server.HttpServletServer;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.drools.system.PolicyEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PolicyEngine unit tests
 */
public class PolicyEngineTest {
	private static Logger logger = LoggerFactory.getLogger(PolicyEngineTest.class);
	
	@BeforeClass
	public static void startUp() {
		logger.info("----- TEST: startUp() ---------");
		
		Properties engineProperties = new Properties();
		engineProperties.put("http.server.services", "CONFIG");
		engineProperties.put("http.server.services.CONFIG.host", "0.0.0.0");
		engineProperties.put("http.server.services.CONFIG.port", "9696");
		engineProperties.put("http.server.services.CONFIG.restPackages", "org.onap.policy.drools.server.restful");
		
		assertFalse(PolicyEngine.manager.isAlive());
		
		PolicyEngine.manager.configure(engineProperties);		
		assertFalse(PolicyEngine.manager.isAlive());
		
		PolicyEngine.manager.start();
		assertTrue(PolicyEngine.manager.isAlive());
		assertFalse(PolicyEngine.manager.isLocked());
		assertTrue(HttpServletServer.factory.get(9696).isAlive());
	}
	
	@AfterClass
	public static void tearDown() {	
		logger.info("----- TEST: tearDown() ---------");
		
		PolicyEngine.manager.stop();
		assertFalse(PolicyEngine.manager.isAlive());
	}
	
	@Test
	public void addController() throws Exception {
		logger.info("----- TEST: addController() ---------");
		
		Properties controllerProperties = new Properties();
		controllerProperties.put("controller.name", "unnamed");
		
		PolicyEngine.manager.createPolicyController("unnamed", controllerProperties);
		assertTrue(PolicyController.factory.inventory().size() == 1);
		
		HttpClient client = HttpClient.factory.build("telemetry", false, false, 
                                                     "localhost", 9696, "policy/pdp", 
                                                      null, null, false);
		Response response = client.get("engine");
		Object body = HttpClient.getBody(response, Object.class);
		logger.info("policy-engine: {}", body);
		
		assertTrue(response.getStatus() == 200);
		
		PolicyController testController = PolicyController.factory.get("unnamed");
		assertFalse(testController.getDrools().isAlive());
		assertFalse(testController.getDrools().isLocked());
	}

}
