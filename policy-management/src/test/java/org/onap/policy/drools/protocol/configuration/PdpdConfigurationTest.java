/*-
 * ============LICENSE_START=======================================================
 * Configuration Test
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
package org.onap.policy.drools.protocol.configuration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PdpdConfigurationTest {

	private static final Logger logger = LoggerFactory.getLogger(PdpdConfigurationTest.class);

	private static final String REQUEST_ID = UUID.randomUUID().toString();
	private static final String REQUEST_ID2 = UUID.randomUUID().toString();

	private static final String ENTITY = "entity1";
	private static final String ENTITY2 = "entity2";

	private static final String PROPERTY1 = "property1";
	private static final String PROPERTY2 = "property2";

	private static final String VALUE1 = "value1";
	private static final String VALUE2 = "value2";

	private static final String ARTIFACT = "org.onap.artifact";
	private static final String GROUPID = "group";
	private static final String VERSION = "1.0.0";

	private static final String ARTIFACT2 = "org.onap.artifact2";
	private static final String GROUPID2 = "group2";
	private static final String VERSION2 = "1.0.1";

	private static final String NAME = "name";
	private static final String OPERATION = "operation";

	private static final String NAME2 = "name2";
	private static final String OPERATION2 = "operation2";

	@Test
	public void test() {
		//
		// Empty constructor test
		//
		DroolsConfiguration drools = new DroolsConfiguration();
		drools.set("artifactId", ARTIFACT);
		drools.set("groupId", GROUPID);
		drools.set("version", VERSION);
		drools.set(PROPERTY1, VALUE1);

		assertTrue(drools.equals(drools));
		assertFalse(drools.equals(new Object()));

		logger.info("Drools HashCode {}", drools.hashCode());

		//
		// Constructor with values test get calls
		//
		DroolsConfiguration drools2 = new DroolsConfiguration(
				drools.get("artifactId"),
				drools.get("groupId"),
				drools.get("version"));

		//
		// get Property
		//

		drools2.set(PROPERTY1, drools.get(PROPERTY1));

		assertTrue(drools.equals(drools2));

		//
		// with methods
		//
		drools2.withArtifactId(ARTIFACT2).withGroupId(GROUPID2).withVersion(VERSION2).withAdditionalProperty(PROPERTY2, VALUE2);

		assertFalse(drools.equals(drools2));

		//
		// Test get additional properties
		//
		assertEquals(drools.getAdditionalProperties().size(), 1);

		//
		// Test Not found
		//
		assertEquals(drools.declaredPropertyOrNotFound(PROPERTY2, DroolsConfiguration.NOT_FOUND_VALUE), DroolsConfiguration.NOT_FOUND_VALUE);

		logger.info("drools {}", drools);
		logger.info("drools2 {}", drools2);

		//
		// Test Controller Default Constructor
		//
		ControllerConfiguration controller = new ControllerConfiguration();

		//
		// Test set
		//

		controller.set("name", NAME);
		controller.set("operation", OPERATION);
		controller.set("drools", drools);
		controller.set(PROPERTY1, VALUE1);

		assertTrue(controller.equals(controller));
		assertFalse(controller.equals(new Object()));

		logger.info("Controller HashCode {}", controller.hashCode());

		//
		// Controller Constructor gets
		//
		ControllerConfiguration controller2 = new ControllerConfiguration(
				controller.get("name"),
				controller.get("operation"),
				controller.get("drools"));

		//
		// Test get property
		//

		controller2.set(PROPERTY1, controller.get(PROPERTY1));

		assertTrue(controller.equals(controller2));

		//
		// test with methods
		//

		controller2.withDrools(drools2).withName(NAME2).withOperation(OPERATION2).withAdditionalProperty(PROPERTY2, VALUE2);

		assertFalse(controller.equals(controller2));

		//
		// Test additional properties
		//
		assertEquals(controller.getAdditionalProperties().size(), 1);

		//
		// Not found
		//
		assertEquals(controller.declaredPropertyOrNotFound(PROPERTY2, ControllerConfiguration.NOT_FOUND_VALUE), ControllerConfiguration.NOT_FOUND_VALUE);

		//
		// toString
		//
		logger.info("Controller {}", controller);
		logger.info("Controller2 {}", controller2);

		//
		// PDP Configuration empty constructor
		//
		PdpdConfiguration config = new PdpdConfiguration();

		//
		// Test set
		//

		config.set("requestID", REQUEST_ID);
		config.set("entity", ENTITY);
		List<ControllerConfiguration> controllers = new ArrayList<>();
		controllers.add(controller);
		config.set("controllers", controllers);
		config.set(PROPERTY1, VALUE1);

		assertTrue(config.equals(config));
		assertFalse(config.equals(new Object()));

		logger.info("Config HashCode {}", config.hashCode());

		//
		// Test constructor with values
		//

		PdpdConfiguration config2 = new PdpdConfiguration(
				config.get("requestID"),
				config.get("entity"),
				config.get("controllers"));

		//
		// Test set
		//

		config2.set(PROPERTY1, config.get(PROPERTY1));

		assertTrue(config.equals(config2));

		//
		// Test with methods
		//
		List<ControllerConfiguration> controllers2 = new ArrayList<>();
		controllers2.add(controller2);
		config2.withRequestID(REQUEST_ID2).withEntity(ENTITY2).withController(controllers2);

		assertFalse(config.equals(config2));

		//
		// Test additional properties
		//

		assertEquals(config.getAdditionalProperties().size(), 1);

		//
		// Test NOT FOUND
		//
		assertEquals(config.declaredPropertyOrNotFound(PROPERTY2, ControllerConfiguration.NOT_FOUND_VALUE), ControllerConfiguration.NOT_FOUND_VALUE);

		//
		// toString
		//
		logger.info("Config {}", config);
		logger.info("Config2 {}", config2);

	}

	@Test
	public void testConstructor() {

		PdpdConfiguration config = new PdpdConfiguration(REQUEST_ID, ENTITY, null);
		assertEquals(config.getRequestID(), REQUEST_ID);
		assertEquals(config.getEntity(), ENTITY);

	}

}
