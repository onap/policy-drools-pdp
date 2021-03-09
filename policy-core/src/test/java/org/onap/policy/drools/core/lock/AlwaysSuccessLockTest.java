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

package org.onap.policy.drools.core.lock;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.Before;
import org.junit.Test;

public class AlwaysSuccessLockTest extends AlwaysLockBaseTest<AlwaysSuccessLock> {

    @Before
    public void setUp() {
        callback = mock(LockCallback.class);
        lock = new AlwaysSuccessLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback);
    }

    @Test
    public void testAlwaysSuccessLockNoArgs() {
        assertThatCode(AlwaysSuccessLock::new).doesNotThrowAnyException();
    }

    @Test
    public void testActiveLock() {
        assertTrue(lock.isActive());
    }

    @Test
    public void testFree() {
        assertTrue(lock.free());
        assertTrue(lock.isActive());
    }

    @Test
    public void testExtend() {
        LockCallback callback2 = mock(LockCallback.class);
        lock.extend(HOLD_SEC2, callback2);

        assertEquals(HOLD_SEC2, lock.getHoldSec());
        assertSame(callback2, lock.getCallback());
    }
}
