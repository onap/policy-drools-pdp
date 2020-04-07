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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Properties;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.junit.Test;
import org.onap.policy.common.utils.gson.GsonTestUtils;

public class ControllerConfigurationTest {

    private static final String NAME = "name";
    private static final String OPERATION = "operation";
    private static final String NAME2 = "name2";
    private static final String OPERATION2 = "operation2";

    private static final String ARTIFACT = "org.onap.artifact";
    private static final String GROUPID = "group";
    private static final String VERSION = "1.0.0";

    private static final String ARTIFACT2 = "org.onap.artifact2";
    private static final String GROUPID2 = "group2";
    private static final String VERSION2 = "1.0.1";

    private static final String ADDITIONAL_PROPERTY_KEY = "foo";
    private static final String ADDITIONAL_PROPERTY_VALUE = "bar";

    private static final String ADDITIONAL_PROPERTY_KEY2 = "hello";
    private static final String ADDITIONAL_PROPERTY_VALUE2 = "world";

    private static final DroolsConfiguration DROOLS_CONFIG = new DroolsConfiguration(ARTIFACT, GROUPID, VERSION);
    private static final DroolsConfiguration DROOLS_CONFIG2 = new DroolsConfiguration(ARTIFACT2, GROUPID2, VERSION2);

    private static final String DROOLS_STRING = "drools";

    /**
     * Test.
     */
    @Test
    public void test() {

        Properties additionalProperties = new Properties();
        additionalProperties.put(ADDITIONAL_PROPERTY_KEY, ADDITIONAL_PROPERTY_VALUE);

        ControllerConfiguration controllerConfig = new ControllerConfiguration(NAME, OPERATION, DROOLS_CONFIG);

        assertTrue(controllerConfig.equals(controllerConfig));
        assertFalse(controllerConfig.equals(new Object()));

        ControllerConfiguration controllerConfig2 = new ControllerConfiguration();
        controllerConfig2.setName(NAME2);
        controllerConfig2.setOperation(OPERATION2);
        controllerConfig2.setDrools(DROOLS_CONFIG2);

        assertEquals(NAME2, controllerConfig2.getName());
        assertEquals(OPERATION2, controllerConfig2.getOperation());
        assertEquals(DROOLS_CONFIG2, controllerConfig2.getDrools());

        assertEquals(controllerConfig2, controllerConfig2.withName(NAME2));
        assertEquals(controllerConfig2, controllerConfig2.withOperation(OPERATION2));
        assertEquals(controllerConfig2, controllerConfig2.withDrools(DROOLS_CONFIG2));

        controllerConfig2.setAdditionalProperty(ADDITIONAL_PROPERTY_KEY, ADDITIONAL_PROPERTY_VALUE);
        assertEquals(controllerConfig2.getAdditionalProperties(), additionalProperties);

        assertEquals(controllerConfig2, controllerConfig2.withAdditionalProperty(ADDITIONAL_PROPERTY_KEY,
                ADDITIONAL_PROPERTY_VALUE));

        assertTrue(controllerConfig2.declaredProperty(NAME, NAME2));
        assertTrue(controllerConfig2.declaredProperty(OPERATION, OPERATION2));
        assertTrue(controllerConfig2.declaredProperty(DROOLS_STRING, DROOLS_CONFIG2));
        assertFalse(controllerConfig2.declaredProperty("dummy", NAME));


        assertEquals(NAME2, controllerConfig2.declaredPropertyOrNotFound(NAME, NAME2));
        assertEquals(OPERATION2, controllerConfig2.declaredPropertyOrNotFound(OPERATION, OPERATION2));
        assertEquals(DROOLS_CONFIG2, controllerConfig2.declaredPropertyOrNotFound(DROOLS_STRING, DROOLS_CONFIG2));
        assertEquals(NAME, controllerConfig2.declaredPropertyOrNotFound("dummy", NAME));

        int hashCode = new HashCodeBuilder().append(NAME2).append(OPERATION2).append(DROOLS_CONFIG2)
                .append(additionalProperties).toHashCode();
        assertEquals(controllerConfig2.hashCode(), hashCode);
    }

    @Test
    public void testSerialize() {
        ControllerConfiguration controllerConfig = new ControllerConfiguration(NAME, OPERATION, DROOLS_CONFIG);
        controllerConfig.setAdditionalProperty(ADDITIONAL_PROPERTY_KEY, ADDITIONAL_PROPERTY_VALUE);
        controllerConfig.setAdditionalProperty(ADDITIONAL_PROPERTY_KEY2, ADDITIONAL_PROPERTY_VALUE2);

        GsonTestUtils gson = new GsonTestUtils();

        // ensure jackson and gson give same result
        gson.compareGson(controllerConfig, ControllerConfigurationTest.class);

        // ensure we get the same value when decoding
        ControllerConfiguration config2 = gson.gsonRoundTrip(controllerConfig, ControllerConfiguration.class);
        assertEquals(controllerConfig, config2);
        assertEquals(gson.gsonEncode(controllerConfig), gson.gsonEncode(config2));
    }
}
