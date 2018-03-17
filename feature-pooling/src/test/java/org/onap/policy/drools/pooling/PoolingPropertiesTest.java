/*
 * ============LICENSE_START=======================================================
 * ONAP
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

package org.onap.policy.drools.pooling;

import static org.junit.Assert.assertEquals;
import static org.onap.policy.common.utils.properties.SpecPropertyConfiguration.generalize;
import static org.onap.policy.common.utils.properties.SpecPropertyConfiguration.specialize;
import static org.onap.policy.drools.pooling.PoolingProperties.ACTIVE_HEARTBEAT_MS;
import static org.onap.policy.drools.pooling.PoolingProperties.FEATURE_ENABLED;
import static org.onap.policy.drools.pooling.PoolingProperties.IDENTIFICATION_MS;
import static org.onap.policy.drools.pooling.PoolingProperties.INTER_HEARTBEAT_MS;
import static org.onap.policy.drools.pooling.PoolingProperties.LEADER_MS;
import static org.onap.policy.drools.pooling.PoolingProperties.OFFLINE_AGE_MS;
import static org.onap.policy.drools.pooling.PoolingProperties.OFFLINE_LIMIT;
import static org.onap.policy.drools.pooling.PoolingProperties.POOLING_TOPIC;
import static org.onap.policy.drools.pooling.PoolingProperties.REACTIVATE_MS;
import static org.onap.policy.drools.pooling.PoolingProperties.START_HEARTBEAT_MS;
import java.util.Properties;
import java.util.function.Function;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.common.utils.properties.exception.PropertyException;

public class PoolingPropertiesTest {

    private static final String CONTROLLER = "a.controller";

    private static final String STD_POOLING_TOPIC = "my.topic";
    public static final boolean STD_FEATURE_ENABLED = true;
    public static final int STD_OFFLINE_LIMIT = 10;
    public static final long STD_OFFLINE_AGE_MS = 1000L;
    public static final long STD_START_HEARTBEAT_MS = 2000L;
    public static final long STD_REACTIVATE_MS = 3000L;
    public static final long STD_IDENTIFICATION_MS = 4000L;
    public static final long STD_LEADER_MS = 5000L;
    public static final long STD_ACTIVE_HEARTBEAT_MS = 6000L;
    public static final long STD_INTER_HEARTBEAT_MS = 7000L;

    private Properties plain;
    private PoolingProperties pooling;

    @Before
    public void setUp() throws Exception {
        plain = makeProperties();

        pooling = new PoolingProperties(CONTROLLER, plain);
    }

    @Test
    public void testPoolingProperties() throws PropertyException {
        // ensure no exceptions
        new PoolingProperties(CONTROLLER, plain);
    }

    @Test
    public void testIsEnabled() throws PropertyException {
        // with specialized property
        assertEquals(true, pooling.isEnabled());

        // no "enabled" property - disabled
        plain = new Properties();
        assertEquals(false, new PoolingProperties(CONTROLLER, plain).isEnabled());

        // replace specialized property with general property
        plain = makeProperties();
        plain.remove(specialize(FEATURE_ENABLED, CONTROLLER));
        plain.setProperty(generalize(FEATURE_ENABLED), "true");

        assertEquals(true, new PoolingProperties(CONTROLLER, plain).isEnabled());

        // no property at all
        plain = new Properties();
        assertEquals(false, new PoolingProperties(CONTROLLER, plain).isEnabled());
    }

    @Test
    public void testGetSource() {
        assertEquals(plain, pooling.getSource());
    }

    @Test
    public void testGetPoolingTopic() {
        assertEquals(STD_POOLING_TOPIC, pooling.getPoolingTopic());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetPoolingTopic_Generalize() {
        // shouldn't be able to generalize the topic
        generalize(POOLING_TOPIC);
    }

    @Test
    public void testGetOfflineLimit() throws PropertyException {
        doTest(OFFLINE_LIMIT, STD_OFFLINE_LIMIT, STD_OFFLINE_LIMIT + 1, 1000, xxx -> pooling.getOfflineLimit());
    }

    @Test
    public void testGetOfflineAgeMs() throws PropertyException {
        doTest(OFFLINE_AGE_MS, STD_OFFLINE_AGE_MS, STD_OFFLINE_AGE_MS + 1, 60000L, xxx -> pooling.getOfflineAgeMs());
    }

    @Test
    public void testGetStartHeartbeatMs() throws PropertyException {
        doTest(START_HEARTBEAT_MS, STD_START_HEARTBEAT_MS, STD_START_HEARTBEAT_MS + 1, 50000L,
                        xxx -> pooling.getStartHeartbeatMs());
    }

    @Test
    public void testGetReactivateMs() throws PropertyException {
        doTest(REACTIVATE_MS, STD_REACTIVATE_MS, STD_REACTIVATE_MS + 1, 50000L, xxx -> pooling.getReactivateMs());
    }

    @Test
    public void testGetIdentificationMs() throws PropertyException {
        doTest(IDENTIFICATION_MS, STD_IDENTIFICATION_MS, STD_IDENTIFICATION_MS + 1, 50000L,
                        xxx -> pooling.getIdentificationMs());
    }

    @Test
    public void testGetLeaderMs() throws PropertyException {
        doTest(LEADER_MS, STD_LEADER_MS, STD_LEADER_MS + 1, 70000L, xxx -> pooling.getLeaderMs());
    }

    @Test
    public void testGetActiveHeartbeatMs() throws PropertyException {
        doTest(ACTIVE_HEARTBEAT_MS, STD_ACTIVE_HEARTBEAT_MS, STD_ACTIVE_HEARTBEAT_MS + 1, 50000L,
                        xxx -> pooling.getActiveHeartbeatMs());
    }

    @Test
    public void testGetInterHeartbeatMs() throws PropertyException {
        doTest(INTER_HEARTBEAT_MS, STD_INTER_HEARTBEAT_MS, STD_INTER_HEARTBEAT_MS + 1, 15000L,
                        xxx -> pooling.getInterHeartbeatMs());
    }

    /**
     * Tests a particular property. Verifies that the correct value is returned
     * if the specialized property has a value, the generalized property has a
     * value, or neither has a value.
     * 
     * @param propnm name of the property of interest
     * @param specValue expected specialized value
     * @param genValue expected generalized value
     * @param dfltValue expected default value
     * @param func function to get the field
     * @throws PropertyException if an error occurs
     */
    private <T> void doTest(String propnm, T specValue, T genValue, T dfltValue, Function<Void, T> func)
                    throws PropertyException {
        /*
         * With specialized property
         */
        pooling = new PoolingProperties(CONTROLLER, plain);
        assertEquals("special " + propnm, specValue, func.apply(null));

        /*
         * With generalized, but no specialized property - should use the
         * generalized value.
         */
        plain.remove(specialize(propnm, CONTROLLER));
        plain.setProperty(generalize(propnm), "" + genValue);

        pooling = new PoolingProperties(CONTROLLER, plain);
        assertEquals("general " + propnm, genValue, func.apply(null));

        /*
         * Without the property - should use the default value.
         */
        plain.remove(specialize(propnm, CONTROLLER));
        plain.remove(generalize(propnm));
        pooling = new PoolingProperties(CONTROLLER, plain);
        assertEquals("default " + propnm, dfltValue, func.apply(null));
    }

    /**
     * Makes a set of properties, where all of the properties are specialized
     * for the controller.
     * 
     * @return a new property set
     */
    private Properties makeProperties() {
        Properties props = new Properties();

        props.setProperty(specialize(POOLING_TOPIC, CONTROLLER), STD_POOLING_TOPIC);
        props.setProperty(specialize(FEATURE_ENABLED, CONTROLLER), "" + STD_FEATURE_ENABLED);
        props.setProperty(specialize(OFFLINE_LIMIT, CONTROLLER), "" + STD_OFFLINE_LIMIT);
        props.setProperty(specialize(OFFLINE_AGE_MS, CONTROLLER), "" + STD_OFFLINE_AGE_MS);
        props.setProperty(specialize(START_HEARTBEAT_MS, CONTROLLER), "" + STD_START_HEARTBEAT_MS);
        props.setProperty(specialize(REACTIVATE_MS, CONTROLLER), "" + STD_REACTIVATE_MS);
        props.setProperty(specialize(IDENTIFICATION_MS, CONTROLLER), "" + STD_IDENTIFICATION_MS);
        props.setProperty(specialize(LEADER_MS, CONTROLLER), "" + STD_LEADER_MS);
        props.setProperty(specialize(ACTIVE_HEARTBEAT_MS, CONTROLLER), "" + STD_ACTIVE_HEARTBEAT_MS);
        props.setProperty(specialize(INTER_HEARTBEAT_MS, CONTROLLER), "" + STD_INTER_HEARTBEAT_MS);

        return props;
    }
}
