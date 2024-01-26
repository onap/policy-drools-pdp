/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2021 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.legacy.config;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.onap.policy.common.endpoints.event.comm.Topic;
import org.onap.policy.common.endpoints.event.comm.bus.NoopTopicFactories;
import org.onap.policy.drools.persistence.SystemPersistenceConstants;

class LegacyConfigTest {

    private static final String PDPD_CONFIGURATION_TOPIC = "pdpd-configuration";

    /**
     * Set up.
     */
    @BeforeEach
    public void setUp() {
        SystemPersistenceConstants.getManager().setConfigurationDir("target/test-classes");
    }

    /**
     * Tear down.
     */
    @AfterEach
    public void tearDown() {
        NoopTopicFactories.getSourceFactory().destroy();
        NoopTopicFactories.getSinkFactory().destroy();
        SystemPersistenceConstants.getManager().setConfigurationDir(null);
    }

    @Test
    void testStartStop() {
        LegacyConfig config = new LegacyConfig();
        assertFalse(config.isAlive());

        assertTrue(config.start());
        assertTrue(config.isAlive());

        config.onTopicEvent(Topic.CommInfrastructure.NOOP, PDPD_CONFIGURATION_TOPIC, "{}");
        assertTrue(config.isAlive());

        assertTrue(config.stop());
        assertFalse(config.isAlive());

        config.shutdown();
        assertFalse(config.isAlive());
    }

    @Test
    void testConstructors() {
        LegacyConfig config = new LegacyConfig();
        assertNotNull(config.getProperties());
        assertEquals(PDPD_CONFIGURATION_TOPIC, config.getSource().getTopic());

        SystemPersistenceConstants.getManager().setConfigurationDir("target/test-classes/bad-properties-1");
        assertThatIllegalStateException().isThrownBy(LegacyConfig::new);

        /* two sources are ok - no exception */
        SystemPersistenceConstants.getManager().setConfigurationDir("target/test-classes/properties-2");
        new LegacyConfig();
    }
}