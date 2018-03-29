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

package org.onap.policy.drools.core.lock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ReferenceTest {

    @Test
    public void testReference() {
        Reference<Integer> val = new Reference<>(null);
        assertNull(val.get());

        val = new Reference<>(10);
        assertEquals(10, val.get().intValue());
    }

    @Test
    public void testGet_testSet() {
        Reference<Integer> val = new Reference<>(null);
        assertNull(val.get());

        val.set(20);
        assertEquals(20, val.get().intValue());

        val.set(30);
        assertEquals(30, val.get().intValue());
    }

    @Test
    public void testCompareAndSet() {
        Reference<Integer> val = new Reference<>(null);

        Integer v = 100;

        // try an incorrect value - should fail and leave value unchanged
        assertFalse(val.compareAndSet(500, v));
        assertNull(val.get());

        assertTrue(val.compareAndSet(null, v));
        assertEquals(v, val.get());

        // try an incorrect value - should fail and leave value unchanged
        Integer v2 = 200;
        assertFalse(val.compareAndSet(600, v2));
        assertEquals(v, val.get());

        // now try again, this time with the correct value
        assertTrue(val.compareAndSet(v, v2));
        assertEquals(v2, val.get());

        Integer v3 = 300;
        assertTrue(val.compareAndSet(v2, v3));
        assertEquals(v3, val.get());

        // try setting it back to null
        assertTrue(val.compareAndSet(v3, null));
        assertNull(val.get());
    }

}
