/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2021 AT&T Intellectual Property. All rights reserved.
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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.common.endpoints.event.comm.Topic;
import org.onap.policy.common.endpoints.event.comm.bus.NoopTopicFactories;
import org.onap.policy.drools.persistence.SystemPersistenceConstants;
import org.onap.policy.drools.utils.logging.LoggerUtil;

/**
 * Legacy Configurator Tests.
 */
public class LegacyConfigTest {

    /**
     * Set up.
     */
    @Before
    public void setUp() {
        SystemPersistenceConstants.getManager().setConfigurationDir("target/test-classes");

        LoggerUtil.setLevel(LoggerUtil.ROOT_LOGGER, "INFO");
        LoggerUtil.setLevel("org.onap.policy.drools.legacy", "DEBUG");
    }

    /**
     * Tear down.
     */
    @After
    public void tearDown() {
        NoopTopicFactories.getSourceFactory().destroy();
        NoopTopicFactories.getSinkFactory().destroy();
        SystemPersistenceConstants.getManager().setConfigurationDir(null);
    }

    @Test
    public void testStartStop() {
        LegacyConfig config = new LegacyConfig();
        assertFalse(config.isAlive());

        assertTrue(config.start());
        assertTrue(config.isAlive());

        config.onTopicEvent(Topic.CommInfrastructure.NOOP, "PDPD-CONFIGURATION", "{}");
        assertTrue(config.isAlive());

        assertTrue(config.stop());
        assertFalse(config.isAlive());

        config.shutdown();
        assertFalse(config.isAlive());
    }

    @Test
    public void testConstructors() {
        LegacyConfig config = new LegacyConfig();
        assertNotNull(config.getProperties());
        assertEquals("PDPD-CONFIGURATION", config.getSource().getTopic());

        SystemPersistenceConstants.getManager().setConfigurationDir("target/test-classes/bad-properties-1");
        assertThatIllegalStateException().isThrownBy(LegacyConfig::new);

        /* two sources are ok - no exception */
        SystemPersistenceConstants.getManager().setConfigurationDir("target/test-classes/properties-2");
        new LegacyConfig();
    }
}