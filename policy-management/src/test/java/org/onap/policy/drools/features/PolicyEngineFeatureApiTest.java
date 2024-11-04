/*-
 * ============LICENSE_START===============================================
 * ONAP
 * ========================================================================
 * Copyright (C) 2024 Nordix Foundation.
 * ========================================================================
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
 * ============LICENSE_END=================================================
 */

package org.onap.policy.drools.features;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.onap.policy.common.message.bus.event.Topic;
import org.onap.policy.drools.core.lock.PolicyResourceLockManager;
import org.onap.policy.drools.protocol.configuration.PdpdConfiguration;
import org.onap.policy.drools.system.PolicyEngine;

/**
 * PolicyEngineFeatureApi is implemented in other modules, therefore, coverage is not coming up in this module.
 * This class has no intention of unit testing features, sole goal is raise coverage.
 */
class PolicyEngineFeatureApiTest {

    PolicyEngineFeatureApi testClass = new TestPolicyEngineFeatureApi();

    PolicyEngine policyEngine = mock(PolicyEngine.class);
    Properties props = new Properties();

    @Test
    void beforeBoot() {
        assertFalse(testClass.beforeBoot(policyEngine, new String[] {"a", "b"}));
    }

    @Test
    void afterBoot() {
        assertFalse(testClass.afterBoot(policyEngine));
    }

    @Test
    void beforeConfigure() {
        assertFalse(testClass.beforeConfigure(policyEngine, props));
    }

    @Test
    void afterConfigure() {
        assertFalse(testClass.afterConfigure(policyEngine));
    }

    @Test
    void beforeActivate() {
        assertFalse(testClass.beforeActivate(policyEngine));
    }

    @Test
    void afterActivate() {
        assertFalse(testClass.afterActivate(policyEngine));
    }

    @Test
    void beforeDeactivate() {
        assertFalse(testClass.beforeDeactivate(policyEngine));
    }

    @Test
    void afterDeactivate() {
        assertFalse(testClass.afterDeactivate(policyEngine));
    }

    @Test
    void beforeStart() {
        assertFalse(testClass.beforeStart(policyEngine));
    }

    @Test
    void afterStart() {
        assertFalse(testClass.afterStart(policyEngine));
    }

    @Test
    void beforeStop() {
        assertFalse(testClass.beforeStop(policyEngine));
    }

    @Test
    void afterStop() {
        assertFalse(testClass.afterStop(policyEngine));
    }

    @Test
    void beforeLock() {
        assertFalse(testClass.beforeLock(policyEngine));
    }

    @Test
    void afterLock() {
        assertFalse(testClass.afterLock(policyEngine));
    }

    @Test
    void beforeUnlock() {
        assertFalse(testClass.beforeUnlock(policyEngine));
    }

    @Test
    void afterUnlock() {
        assertFalse(testClass.afterUnlock(policyEngine));
    }

    @Test
    void beforeShutdown() {
        assertFalse(testClass.beforeShutdown(policyEngine));
    }

    @Test
    void afterShutdown() {
        assertFalse(testClass.afterShutdown(policyEngine));
    }

    @Test
    void beforeOnTopicEvent() {
        assertFalse(testClass.beforeOnTopicEvent(policyEngine, Topic.CommInfrastructure.NOOP, "topic", "event"));
    }

    @Test
    void afterOnTopicEvent() {
        assertFalse(testClass.afterOnTopicEvent(policyEngine, mock(PdpdConfiguration.class),
            Topic.CommInfrastructure.NOOP, "topic", "event"));
    }

    @Test
    void beforeOpen() {
        assertFalse(testClass.beforeOpen(policyEngine));
    }

    @Test
    void afterOpen() {
        assertFalse(testClass.afterOpen(policyEngine));
    }

    @Test
    void beforeCreateLockManager() {
        assertNull(testClass.beforeCreateLockManager());
    }

    @Test
    void afterCreateLockManager() {
        assertFalse(testClass.afterCreateLockManager(policyEngine, props, mock(PolicyResourceLockManager.class)));
    }

    static class TestPolicyEngineFeatureApi implements PolicyEngineFeatureApi {

        @Override
        public int getSequenceNumber() {
            return 10;
        }

    }
}