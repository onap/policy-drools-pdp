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

package org.onap.policy.drools.core.jmx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class PdpJmxTest {

    private PdpJmx jmx;

    @Before
    public void setUp() {
        jmx = new PdpJmx();
    }

    @Test
    public void testGetInstance() {
        jmx = PdpJmx.getInstance();
        assertNotNull(jmx);
        assertSame(jmx, PdpJmx.getInstance());
    }

    @Test
    public void testGetUpdates_testUpdateOccured() {
        assertEquals(0, jmx.getUpdates());
        assertEquals(0, jmx.getRulesFired());

        jmx.updateOccured();
        assertEquals(1, jmx.getUpdates());
        assertEquals(0, jmx.getRulesFired());

        jmx.updateOccured();
        assertEquals(2, jmx.getUpdates());
        assertEquals(0, jmx.getRulesFired());
    }

    @Test
    public void testGetRulesFired_testRuleFired() {
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
