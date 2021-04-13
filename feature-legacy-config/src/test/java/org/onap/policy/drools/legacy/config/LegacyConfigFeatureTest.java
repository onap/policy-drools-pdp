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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.common.endpoints.event.comm.bus.NoopTopicFactories;
import org.onap.policy.drools.persistence.SystemPersistenceConstants;
import org.onap.policy.drools.system.PolicyEngineConstants;

public class LegacyConfigFeatureTest {

    private LegacyConfigFeature configF;

    /**
     * Set up.
     */
    @Before
    public void setUp() {
        SystemPersistenceConstants.getManager().setConfigurationDir("target/test-classes");
        configF = new LegacyConfigFeature();
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
    public void getSequenceNumber() {
        assertEquals(LegacyConfigFeature.SEQNO, new LegacyConfigFeature().getSequenceNumber());
    }

    @Test
    public void afterOpenBeforeShutdown() {
        assertFalse(LegacyConfigFeature.getLegacyConfig().isAlive());
        configF.afterOpen(PolicyEngineConstants.getManager());
        assertTrue(LegacyConfigFeature.getLegacyConfig().isAlive());
        configF.beforeShutdown(PolicyEngineConstants.getManager());
        assertFalse(LegacyConfigFeature.getLegacyConfig().isAlive());
    }
}