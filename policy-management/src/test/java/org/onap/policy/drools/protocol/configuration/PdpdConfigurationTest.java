/*-
 * ============LICENSE_START=======================================================
 * Configuration Test
 * ================================================================================
 * Copyright (C) 2017-2020 AT&T Intellectual Property. All rights reserved.
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
import static org.junit.Assert.assertNotEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import org.onap.policy.common.utils.gson.GsonTestUtils;
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

        assertEquals(drools, (Object) drools);
        assertNotEquals(drools, new Object());

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

        assertEquals(drools, drools2);

        //
        // with methods
        //
        drools2.withArtifactId(ARTIFACT2).withGroupId(GROUPID2).withVersion(VERSION2)
            .withAdditionalProperty(PROPERTY2, VALUE2);

        assertNotEquals(drools, drools2);

        //
        // Test get additional properties
        //
        assertEquals(1, drools.getAdditionalProperties().size());

        //
        // Test Not found
        //
        assertEquals(DroolsConfiguration.NOT_FOUND_VALUE,
                drools.declaredPropertyOrNotFound(PROPERTY2, DroolsConfiguration.NOT_FOUND_VALUE));

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

        assertEquals(controller, (Object) controller);
        assertNotEquals(controller, new Object());

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

        assertEquals(controller, controller2);

        //
        // test with methods
        //

        controller2.withDrools(drools2).withName(NAME2)
            .withOperation(OPERATION2).withAdditionalProperty(PROPERTY2, VALUE2);

        assertNotEquals(controller, controller2);

        //
        // Test additional properties
        //
        assertEquals(1, controller.getAdditionalProperties().size());

        //
        // Not found
        //
        assertEquals(ControllerConfiguration.NOT_FOUND_VALUE,
                controller.declaredPropertyOrNotFound(PROPERTY2, ControllerConfiguration.NOT_FOUND_VALUE));

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

        assertEquals(config, (Object) config);
        assertNotEquals(config, new Object());

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

        assertEquals(config, config2);

        //
        // Test with methods
        //
        List<ControllerConfiguration> controllers2 = new ArrayList<>();
        controllers2.add(controller2);
        config2.withRequestId(REQUEST_ID2).withEntity(ENTITY2).withController(controllers2);

        assertNotEquals(config, config2);

        //
        // Test additional properties
        //

        assertEquals(1, config.getAdditionalProperties().size());

        //
        // Test NOT FOUND
        //
        assertEquals(ControllerConfiguration.NOT_FOUND_VALUE,
                config.declaredPropertyOrNotFound(PROPERTY2, ControllerConfiguration.NOT_FOUND_VALUE));

        //
        // toString
        //
        logger.info("Config {}", config);
        logger.info("Config2 {}", config2);

    }

    @Test
    public void testConstructor() {
        PdpdConfiguration config = new PdpdConfiguration(REQUEST_ID, ENTITY, null);
        assertEquals(REQUEST_ID, config.getRequestId());
        assertEquals(ENTITY, config.getEntity());
    }

    @Test
    public void testSerialize() throws IOException {
        List<ControllerConfiguration> controllers = Arrays.asList(new ControllerConfiguration(NAME, OPERATION, null),
                        new ControllerConfiguration(NAME2, OPERATION2, null));
        PdpdConfiguration pdpConfig = new PdpdConfiguration(REQUEST_ID, ENTITY, controllers);

        GsonTestUtils gson = new GsonTestUtils();

        // ensure jackson and gson give same result
        gson.compareGson(pdpConfig, PdpdConfigurationTest.class);

        // ensure we get the same value when decoding
        PdpdConfiguration config2 = gson.gsonRoundTrip(pdpConfig, PdpdConfiguration.class);
        assertEquals(stripIdent(pdpConfig.toString()), stripIdent(config2.toString()));
        assertEquals(pdpConfig, config2);
        assertEquals(gson.gsonEncode(pdpConfig), gson.gsonEncode(config2));
    }

    /**
     * Object identifiers may change with each execution, so this method is used to strip
     * the identifier from the text string so that the strings will still match across
     * different runs.
     *
     * @param text text from which to strip the identifier
     * @return the text, without the identifier
     */
    private String stripIdent(String text) {
        return text.replaceAll("@\\w+", "@");
    }
}
