/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2021 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2024-2025 OpenInfra Foundation Europe. All rights reserved.
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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AlwaysFailLockTest extends AlwaysLockBaseTest<AlwaysFailLock> {

    @BeforeEach
    void setUp() {
        callback = mock(LockCallback.class);
        lock = new AlwaysFailLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback);
    }

    @Test
    void testAlwaysFailLockNoArgs() {
        assertThatCode(AlwaysFailLock::new).doesNotThrowAnyException();
    }

    @Test
    void testUnavailableLock() {
        assertTrue(lock.isUnavailable());
    }

    @Test
    void testFree() {
        assertFalse(lock.free());
        assertTrue(lock.isUnavailable());
    }

    @Test
    void testExtend() {
        assertDoesNotThrow(() -> lock.extend(10, callback));
    }
}
