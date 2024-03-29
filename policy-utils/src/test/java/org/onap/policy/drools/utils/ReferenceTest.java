/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReferenceTest {

    @Test
    void testReference() {
        Reference<Integer> val = new Reference<>(null);
        assertNull(val.get());

        val = new Reference<>(10);
        assertEquals(10, val.get().intValue());
    }

    @Test
    void testGet_testSet() {
        Reference<Integer> val = new Reference<>(null);
        assertNull(val.get());

        val.set(20);
        assertEquals(20, val.get().intValue());

        val.set(30);
        assertEquals(30, val.get().intValue());
    }

    @Test
    void testCompareAndSet() {
        Reference<Integer> val = new Reference<>(null);

        Integer valCompare = 100;

        // try an incorrect value - should fail and leave value unchanged
        assertFalse(val.compareAndSet(500, valCompare));
        assertNull(val.get());

        assertTrue(val.compareAndSet(null, valCompare));
        assertEquals(valCompare, val.get());

        // try an incorrect value - should fail and leave value unchanged
        Integer v2 = 200;
        assertFalse(val.compareAndSet(600, v2));
        assertEquals(valCompare, val.get());

        // now try again, this time with the correct value
        assertTrue(val.compareAndSet(valCompare, v2));
        assertEquals(v2, val.get());

        Integer v3 = 300;
        assertTrue(val.compareAndSet(v2, v3));
        assertEquals(v3, val.get());

        // try setting it back to null
        assertTrue(val.compareAndSet(v3, null));
        assertNull(val.get());
    }

}
