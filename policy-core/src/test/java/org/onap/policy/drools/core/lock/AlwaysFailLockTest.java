/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2019 AT&T Intellectual Property. All rights reserved.
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.Before;
import org.junit.Test;

public class AlwaysFailLockTest {
    private static final String RESOURCE = "hello";
    private static final String OWNER_KEY = "world";
    private static final int HOLD_SEC = 10;
    private static final int HOLD_SEC2 = 10;

    private LockCallback callback;
    private AlwaysFailLock lock;

    /**
     * Populates {@link #lock}.
     */
    @Before
    public void setUp() {
        callback = mock(LockCallback.class);

        lock = new AlwaysFailLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback);
    }

    @Test
    public void testSerializable() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(lock);
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        try (ObjectInputStream ois = new ObjectInputStream(bais)) {
            lock = (AlwaysFailLock) ois.readObject();
        }

        assertEquals(LockState.UNAVAILABLE, lock.getState());
        assertEquals(RESOURCE, lock.getResourceId());
        assertEquals(OWNER_KEY, lock.getOwnerKey());
        assertEquals(HOLD_SEC, lock.getHoldSec());

        // these fields are transient
        assertNull(lock.getCallback());
    }

    @Test
    public void testAlwaysFailLockNoArgs() {
        // verify that no-arg constructor doesn't throw an exception
        assertThatCode(() -> new AlwaysFailLock()).doesNotThrowAnyException();
    }

    @Test
    public void testAlwaysFailLock() {
        assertTrue(lock.isUnavailable());
        assertEquals(RESOURCE, lock.getResourceId());
        assertEquals(OWNER_KEY, lock.getOwnerKey());
        assertEquals(HOLD_SEC, lock.getHoldSec());
        assertSame(callback, lock.getCallback());
    }

    @Test
    public void testFree() {
        assertFalse(lock.free());
        assertTrue(lock.isUnavailable());
    }

    @Test
    public void testExtend() {
        LockCallback callback2 = mock(LockCallback.class);
        lock.extend(HOLD_SEC2, callback2);

        assertEquals(HOLD_SEC2, lock.getHoldSec());
        assertSame(callback2, lock.getCallback());
    }
}
