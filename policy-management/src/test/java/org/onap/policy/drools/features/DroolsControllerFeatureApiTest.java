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

import java.util.Collections;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.onap.policy.common.endpoints.event.comm.TopicSink;
import org.onap.policy.drools.controller.DroolsController;

/**
 * DroolsControllerFeatureApi is implemented in other modules, therefore, coverage is not coming up in this module.
 * This class has no intention of unit testing features, sole goal is raise coverage.
 */
class DroolsControllerFeatureApiTest {

    DroolsControllerFeatureApi testClass = new TestDroolsControllerFeatureApi();
    Properties props = new Properties();
    DroolsController controller;
    TopicSink sink;
    Object fact;

    @Test
    void beforeInstance() {
        assertNull(testClass.beforeInstance(props, "group", "artifact", "version",
            Collections.emptyList(), Collections.emptyList()));
    }

    @Test
    void afterInstance() {
        assertFalse(testClass.afterInstance(controller, props));
    }

    @Test
    void beforeInsert() {
        assertFalse(testClass.beforeInsert(controller, props));
    }

    @Test
    void afterInsert() {
        assertFalse(testClass.afterInsert(controller, fact, false));
    }

    @Test
    void beforeDeliver() {
        assertFalse(testClass.beforeDeliver(controller, sink, fact));
    }

    @Test
    void afterDeliver() {
        assertFalse(testClass.afterDeliver(controller, sink, fact, "json", false));
    }

    static class TestDroolsControllerFeatureApi implements DroolsControllerFeatureApi {

        @Override
        public int getSequenceNumber() {
            return 30;
        }
    }
}