/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018, 2020 AT&T Intellectual Property. All rights reserved.
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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.Assert.assertEquals;
import static org.onap.policy.drools.pooling.PoolingProperties.ACTIVE_HEARTBEAT_MS;
import static org.onap.policy.drools.pooling.PoolingProperties.FEATURE_ENABLED;
import static org.onap.policy.drools.pooling.PoolingProperties.IDENTIFICATION_MS;
import static org.onap.policy.drools.pooling.PoolingProperties.INTER_HEARTBEAT_MS;
import static org.onap.policy.drools.pooling.PoolingProperties.OFFLINE_AGE_MS;
import static org.onap.policy.drools.pooling.PoolingProperties.OFFLINE_LIMIT;
import static org.onap.policy.drools.pooling.PoolingProperties.OFFLINE_PUB_WAIT_MS;
import static org.onap.policy.drools.pooling.PoolingProperties.POOLING_TOPIC;
import static org.onap.policy.drools.pooling.PoolingProperties.PREFIX;
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
    public static final long STD_OFFLINE_PUB_WAIT_MS = 2000L;
    public static final long STD_START_HEARTBEAT_MS = 3000L;
    public static final long STD_REACTIVATE_MS = 4000L;
    public static final long STD_IDENTIFICATION_MS = 5000L;
    public static final long STD_LEADER_MS = 6000L;
    public static final long STD_ACTIVE_HEARTBEAT_MS = 7000L;
    public static final long STD_INTER_HEARTBEAT_MS = 8000L;

    private Properties plain;
    private PoolingProperties pooling;

    /**
     * Setup.
     *
     * @throws Exception throws an exception
     */
    @Before
    public void setUp() throws Exception {
        plain = makeProperties();

        pooling = new PoolingProperties(CONTROLLER, plain);
    }

    @Test
    public void testPoolingProperties() throws PropertyException {
        // ensure no exceptions
        assertThatCode(() -> new PoolingProperties(CONTROLLER, plain)).doesNotThrowAnyException();
    }

    @Test
    public void testGetSource() {
        assertEquals(plain, pooling.getSource());
    }

    @Test
    public void testGetPoolingTopic() {
        assertEquals(STD_POOLING_TOPIC, pooling.getPoolingTopic());
    }

    @Test
    public void testGetOfflineLimit() throws PropertyException {
        doTest(OFFLINE_LIMIT, STD_OFFLINE_LIMIT, 1000, xxx -> pooling.getOfflineLimit());
    }

    @Test
    public void testGetOfflineAgeMs() throws PropertyException {
        doTest(OFFLINE_AGE_MS, STD_OFFLINE_AGE_MS, 60000L, xxx -> pooling.getOfflineAgeMs());
    }

    @Test
    public void testGetOfflinePubWaitMs() throws PropertyException {
        doTest(OFFLINE_PUB_WAIT_MS, STD_OFFLINE_PUB_WAIT_MS, 3000L, xxx -> pooling.getOfflinePubWaitMs());
    }

    @Test
    public void testGetStartHeartbeatMs() throws PropertyException {
        doTest(START_HEARTBEAT_MS, STD_START_HEARTBEAT_MS, 100000L, xxx -> pooling.getStartHeartbeatMs());
    }

    @Test
    public void testGetReactivateMs() throws PropertyException {
        doTest(REACTIVATE_MS, STD_REACTIVATE_MS, 50000L, xxx -> pooling.getReactivateMs());
    }

    @Test
    public void testGetIdentificationMs() throws PropertyException {
        doTest(IDENTIFICATION_MS, STD_IDENTIFICATION_MS, 50000L, xxx -> pooling.getIdentificationMs());
    }

    @Test
    public void testGetActiveHeartbeatMs() throws PropertyException {
        doTest(ACTIVE_HEARTBEAT_MS, STD_ACTIVE_HEARTBEAT_MS, 50000L, xxx -> pooling.getActiveHeartbeatMs());
    }

    @Test
    public void testGetInterHeartbeatMs() throws PropertyException {
        doTest(INTER_HEARTBEAT_MS, STD_INTER_HEARTBEAT_MS, 15000L, xxx -> pooling.getInterHeartbeatMs());
    }

    /**
     * Tests a particular property. Verifies that the correct value is returned if the
     * specialized property has a value or the property has no value. Also verifies that
     * the property name can be generalized.
     *
     * @param propnm name of the property of interest
     * @param specValue expected specialized value
     * @param dfltValue expected default value
     * @param func function to get the field
     * @throws PropertyException if an error occurs
     */
    private <T> void doTest(String propnm, T specValue, T dfltValue, Function<Void, T> func) throws PropertyException {
        /*
         * With specialized property
         */
        pooling = new PoolingProperties(CONTROLLER, plain);
        assertEquals("special " + propnm, specValue, func.apply(null));

        /*
         * Without the property - should use the default value.
         */
        plain.remove(specialize(propnm, CONTROLLER));
        plain.remove(propnm);
        pooling = new PoolingProperties(CONTROLLER, plain);
        assertEquals("default " + propnm, dfltValue, func.apply(null));
    }

    /**
     * Makes a set of properties, where all of the properties are specialized for the
     * controller.
     *
     * @return a new property set
     */
    private Properties makeProperties() {
        Properties props = new Properties();

        props.setProperty(specialize(POOLING_TOPIC, CONTROLLER), STD_POOLING_TOPIC);
        props.setProperty(specialize(FEATURE_ENABLED, CONTROLLER), "" + STD_FEATURE_ENABLED);
        props.setProperty(specialize(OFFLINE_LIMIT, CONTROLLER), "" + STD_OFFLINE_LIMIT);
        props.setProperty(specialize(OFFLINE_AGE_MS, CONTROLLER), "" + STD_OFFLINE_AGE_MS);
        props.setProperty(specialize(OFFLINE_PUB_WAIT_MS, CONTROLLER), "" + STD_OFFLINE_PUB_WAIT_MS);
        props.setProperty(specialize(START_HEARTBEAT_MS, CONTROLLER), "" + STD_START_HEARTBEAT_MS);
        props.setProperty(specialize(REACTIVATE_MS, CONTROLLER), "" + STD_REACTIVATE_MS);
        props.setProperty(specialize(IDENTIFICATION_MS, CONTROLLER), "" + STD_IDENTIFICATION_MS);
        props.setProperty(specialize(ACTIVE_HEARTBEAT_MS, CONTROLLER), "" + STD_ACTIVE_HEARTBEAT_MS);
        props.setProperty(specialize(INTER_HEARTBEAT_MS, CONTROLLER), "" + STD_INTER_HEARTBEAT_MS);

        return props;
    }

    /**
     * Embeds a specializer within a property name, after the prefix.
     *
     * @param propnm property name into which it should be embedded
     * @param spec specializer to be embedded
     * @return the property name, with the specializer embedded within it
     */
    private String specialize(String propnm, String spec) {
        String suffix = propnm.substring(PREFIX.length());
        return PREFIX + spec + "." + suffix;
    }
}
