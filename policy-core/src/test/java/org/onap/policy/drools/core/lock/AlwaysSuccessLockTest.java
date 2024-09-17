/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2021 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.core.lock;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AlwaysSuccessLockTest extends AlwaysLockBaseTest<AlwaysSuccessLock> {

    @BeforeEach
    public void setUp() {
        callback = mock(LockCallback.class);
        lock = new AlwaysSuccessLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback);
    }

    @Test
    void testAlwaysSuccessLockConstructors() {
        assertThatCode(AlwaysSuccessLock::new).doesNotThrowAnyException();
        assertThatCode(() -> new AlwaysSuccessLock(LockState.ACTIVE, RESOURCE, OWNER_KEY, HOLD_SEC, callback))
            .doesNotThrowAnyException();
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new AlwaysSuccessLock(LockState.UNAVAILABLE, RESOURCE, OWNER_KEY, HOLD_SEC, callback));
    }

    @Test
    void testActiveLock() {
        assertTrue(lock.isActive());
    }

    @Test
    void testFree() {
        assertTrue(lock.free());
        assertTrue(lock.isActive());
    }

    @Test
    void testExtend() {
        LockCallback callback2 = mock(LockCallback.class);
        lock.extend(HOLD_SEC2, callback2);

        assertEquals(HOLD_SEC2, lock.getHoldSec());
        assertSame(callback2, lock.getCallback());
    }

    @Test
    void testNullArgs() {
        assertThrows(NullPointerException.class,
            () -> new AlwaysSuccessLock(null, RESOURCE, OWNER_KEY, HOLD_SEC, callback));

        assertThrows(NullPointerException.class,
            () -> new AlwaysSuccessLock(LockState.WAITING, null, OWNER_KEY, HOLD_SEC, callback));

        assertThrows(NullPointerException.class,
            () -> new AlwaysSuccessLock(LockState.WAITING, RESOURCE, null, HOLD_SEC, callback));

        assertThrows(NullPointerException.class,
            () -> new AlwaysSuccessLock(LockState.WAITING, RESOURCE, OWNER_KEY, HOLD_SEC, null));

        assertThrows(NullPointerException.class,
            () -> new AlwaysSuccessLock(null, OWNER_KEY, HOLD_SEC, callback));

        assertThrows(NullPointerException.class,
            () -> new AlwaysSuccessLock(RESOURCE, null, HOLD_SEC, callback));

        assertThrows(NullPointerException.class,
            () -> new AlwaysSuccessLock(RESOURCE, OWNER_KEY, HOLD_SEC, null));
    }
}
