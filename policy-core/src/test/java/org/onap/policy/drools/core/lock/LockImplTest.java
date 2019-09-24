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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;

public class LockImplTest {
    private static final Lock.State STATE = Lock.State.WAITING;
    private static final Lock.State STATE2 = Lock.State.ACTIVE;
    private static final String RESOURCE = "hello";
    private static final Object OWNER = new Object();
    private static final String OWNER_KEY = "world";
    private static final int HOLD_SEC = 10;
    private static final int HOLD_SEC2 = 20;

    private LockCallback callback;
    private LockImpl lock;

    /**
     * Populates {@link #lock}.
     */
    @Before
    public void setUp() {
        callback = mock(LockCallback.class);

        lock = new LockImpl(STATE, RESOURCE, OWNER, OWNER_KEY, callback, HOLD_SEC);
    }

    @Test
    public void testLockImpl_testGetters() {
        assertEquals(STATE, lock.getState());
        assertEquals(RESOURCE, lock.getResourceId());
        assertEquals(OWNER, lock.getOwnerInfo());
        assertEquals(OWNER_KEY, lock.getOwnerKey());
        assertSame(callback, lock.getCallback());
        assertEquals(HOLD_SEC, lock.getHoldSec());
    }

    @Test
    public void testFree() {
        assertFalse(lock.free());

        verify(callback, never()).lockAvailable(any());
        verify(callback, never()).lockUnavailable(any());
    }

    @Test
    public void testExtend() {
        assertFalse(lock.extend(HOLD_SEC));

        verify(callback, never()).lockAvailable(any());
        verify(callback, never()).lockUnavailable(any());
    }

    @Test
    public void testNotifyAvailable() {
        lock.notifyAvailable();

        verify(callback).lockAvailable(any());
        verify(callback, never()).lockUnavailable(any());
    }

    @Test
    public void testNotifyUnavailable() {
        lock.notifyUnavailable();

        verify(callback, never()).lockAvailable(any());
        verify(callback).lockUnavailable(any());
    }

    @Test
    public void testSetState() {
        lock.setState(STATE2);

        assertEquals(STATE2, lock.getState());
    }

    @Test
    public void testSetHoldSec() {
        lock.setHoldSec(HOLD_SEC2);

        assertEquals(HOLD_SEC2, lock.getHoldSec());
    }

    @Test
    public void testToString() {
        String text = lock.toString();

        assertNotNull(text);
        assertThat(text).doesNotContain("ownerInfo").doesNotContain("callback");
    }

}
