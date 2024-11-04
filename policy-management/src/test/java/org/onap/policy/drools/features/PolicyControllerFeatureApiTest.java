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
import static org.onap.policy.common.message.bus.event.Topic.CommInfrastructure.NOOP;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.onap.policy.drools.protocol.configuration.DroolsConfiguration;
import org.onap.policy.drools.system.PolicyController;

/**
 * PolicyControllerFeatureApi is implemented in other modules, therefore, coverage is not coming up in this module.
 * This class has no intention of unit testing features, sole goal is raise coverage.
 */
class PolicyControllerFeatureApiTest {

    PolicyControllerFeatureApi testClass = new TestPolicyControllerFeatureApi();
    Properties props = new Properties();
    PolicyController controller;
    DroolsConfiguration configuration;


    @Test
    void beforeCreate() {
        assertNull(testClass.beforeCreate("name", props));
    }

    @Test
    void afterCreate() {
        assertFalse(testClass.afterCreate(controller));
    }

    @Test
    void beforeInstance() {
        assertNull(testClass.beforeInstance("name", props));
    }

    @Test
    void afterInstance() {
        assertFalse(testClass.afterInstance(controller, props));
    }

    @Test
    void beforeStart() {
        assertFalse(testClass.beforeStart(controller));
    }

    @Test
    void afterStart() {
        assertFalse(testClass.afterStart(controller));
    }

    @Test
    void beforeStop() {
        assertFalse(testClass.beforeStop(controller));
    }

    @Test
    void afterStop() {
        assertFalse(testClass.afterStop(controller));
    }

    @Test
    void beforePatch() {
        assertFalse(testClass.beforePatch(controller, configuration, configuration));
    }

    @Test
    void afterPatch() {
        assertFalse(testClass.afterPatch(controller, configuration, configuration, true));
    }

    @Test
    void beforeLock() {
        assertFalse(testClass.beforeLock(controller));
    }

    @Test
    void afterLock() {
        assertFalse(testClass.afterLock(controller));
    }

    @Test
    void beforeUnlock() {
        assertFalse(testClass.beforeUnlock(controller));
    }

    @Test
    void afterUnlock() {
        assertFalse(testClass.afterUnlock(controller));
    }

    @Test
    void beforeShutdown() {
        assertFalse(testClass.beforeShutdown(controller));
    }

    @Test
    void afterShutdown() {
        assertFalse(testClass.afterShutdown(controller));
    }

    @Test
    void beforeHalt() {
        assertFalse(testClass.beforeHalt(controller));
    }

    @Test
    void afterHalt() {
        assertFalse(testClass.afterHalt(controller));
    }

    @Test
    void beforeOffer() {
        assertFalse(testClass.beforeOffer(controller, new Object()));
    }

    @Test
    void testBeforeOffer() {
        assertFalse(testClass.beforeOffer(controller, NOOP, "topic", "event"));
    }

    @Test
    void afterOffer() {
        assertFalse(testClass.afterOffer(controller, new Object(), true));
    }

    @Test
    void testAfterOffer() {
        assertFalse(testClass.afterOffer(controller, NOOP, "topic", "event", true));
    }

    @Test
    void beforeDeliver() {
        assertFalse(testClass.beforeDeliver(controller, NOOP, "topic", "event"));
    }

    @Test
    void afterDeliver() {
        assertFalse(testClass.afterDeliver(controller, NOOP, "topic", "event", true));
    }

    static class TestPolicyControllerFeatureApi implements PolicyControllerFeatureApi {

        @Override
        public int getSequenceNumber() {
            return 20;
        }
    }
}