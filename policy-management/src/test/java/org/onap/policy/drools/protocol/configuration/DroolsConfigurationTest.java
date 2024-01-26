/*-
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2017-2021 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2024 Nordix Foundation.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.onap.policy.common.utils.gson.GsonTestUtils;

class DroolsConfigurationTest {
    private static final String ARTIFACT_ID_STRING = "artifactId";
    private static final String GROUP_ID_STRING = "groupId";
    private static final String VERSION_STRING = "version";


    private static final String NAME = "name";
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

    @Test
    void test() {
        final Properties additionalProperties = new Properties();
        additionalProperties.put(ADDITIONAL_PROPERTY_KEY, ADDITIONAL_PROPERTY_VALUE);

        final DroolsConfiguration droolsConfig = new DroolsConfiguration(ARTIFACT, GROUPID, VERSION);
        assertEquals(droolsConfig, (Object) droolsConfig);

        droolsConfig.set(ARTIFACT_ID_STRING, "foobar");
        assertEquals("foobar", droolsConfig.get(ARTIFACT_ID_STRING));

        assertEquals(droolsConfig.with(ARTIFACT_ID_STRING, "foobar2"), droolsConfig);

        final DroolsConfiguration droolsConfig2 = new DroolsConfiguration();
        droolsConfig2.setArtifactId(ARTIFACT2);
        droolsConfig2.setGroupId(GROUPID2);
        droolsConfig2.setVersion(VERSION2);

        assertEquals(ARTIFACT2, droolsConfig2.getArtifactId());
        assertEquals(GROUPID2, droolsConfig2.getGroupId());
        assertEquals(VERSION2, droolsConfig2.getVersion());

        assertEquals(droolsConfig2.withArtifactId(ARTIFACT2), droolsConfig2);
        assertEquals(droolsConfig2.withGroupId(GROUPID2), droolsConfig2);
        assertEquals(droolsConfig2.withVersion(VERSION2), droolsConfig2);

        droolsConfig2.setAdditionalProperty(ADDITIONAL_PROPERTY_KEY, ADDITIONAL_PROPERTY_VALUE);
        assertEquals(droolsConfig2.getAdditionalProperties(), additionalProperties);

        assertEquals(droolsConfig2,
                droolsConfig2.withAdditionalProperty(ADDITIONAL_PROPERTY_KEY, ADDITIONAL_PROPERTY_VALUE));

        assertTrue(droolsConfig2.declaredProperty(ARTIFACT_ID_STRING, ARTIFACT2));
        assertTrue(droolsConfig2.declaredProperty(GROUP_ID_STRING, GROUPID2));
        assertTrue(droolsConfig2.declaredProperty(VERSION_STRING, VERSION2));
        assertFalse(droolsConfig2.declaredProperty("dummy", NAME));

        assertEquals(ARTIFACT2, droolsConfig2.declaredPropertyOrNotFound(ARTIFACT_ID_STRING, ARTIFACT2));
        assertEquals(GROUPID2, droolsConfig2.declaredPropertyOrNotFound(GROUP_ID_STRING, GROUPID2));
        assertEquals(VERSION2, droolsConfig2.declaredPropertyOrNotFound(VERSION_STRING, VERSION2));
        assertEquals(ARTIFACT2, droolsConfig2.declaredPropertyOrNotFound("dummy", ARTIFACT2));

        assertThat(droolsConfig2.hashCode()).isNotZero();
    }

    @Test
    void testSerialize() {
        final DroolsConfiguration droolsConfig = new DroolsConfiguration(ARTIFACT, GROUPID, VERSION);
        droolsConfig.setAdditionalProperty(ADDITIONAL_PROPERTY_KEY, ADDITIONAL_PROPERTY_VALUE);
        droolsConfig.setAdditionalProperty(ADDITIONAL_PROPERTY_KEY2, ADDITIONAL_PROPERTY_VALUE2);

        GsonTestUtils gson = new GsonTestUtils();

        // ensure jackson and gson give same result
        gson.compareGson(droolsConfig, DroolsConfigurationTest.class);

        // ensure we get the same value when decoding
        DroolsConfiguration config2 = gson.gsonRoundTrip(droolsConfig, DroolsConfiguration.class);
        assertEquals(droolsConfig, config2);
        assertEquals(gson.gsonEncode(droolsConfig), gson.gsonEncode(config2));
    }
}
