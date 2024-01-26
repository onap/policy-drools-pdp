/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018, 2020 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.core.jmx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PdpJmxTest {

    private PdpJmx jmx;

    @BeforeEach
    public void setUp() {
        jmx = new PdpJmx();
    }

    @Test
    void testGetInstance() {
        jmx = PdpJmx.getInstance();
        assertNotNull(jmx);
        assertSame(jmx, PdpJmx.getInstance());
    }

    @Test
    void testGetUpdates_testUpdateOccurred() {
        assertEquals(0, jmx.getUpdates());
        assertEquals(0, jmx.getRulesFired());

        jmx.updateOccurred();
        assertEquals(1, jmx.getUpdates());
        assertEquals(0, jmx.getRulesFired());

        jmx.updateOccurred();
        assertEquals(2, jmx.getUpdates());
        assertEquals(0, jmx.getRulesFired());
    }

    @Test
    void testGetRulesFired_testRuleFired() {
        assertEquals(0, jmx.getUpdates());
        assertEquals(0, jmx.getRulesFired());

        jmx.ruleFired();
        assertEquals(0, jmx.getUpdates());
        assertEquals(1, jmx.getRulesFired());

        jmx.ruleFired();
        assertEquals(0, jmx.getUpdates());
        assertEquals(2, jmx.getRulesFired());
    }
}
