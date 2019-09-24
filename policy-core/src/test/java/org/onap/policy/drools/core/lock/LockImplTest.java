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
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;

public class LockImplTest {
    private static final LockState STATE = LockState.WAITING;
    private static final String RESOURCE = "hello";
    private static final Object OWNER = new Object();
    private static final String OWNER_KEY = "world";
    private static final int HOLD_SEC = 10;
    private static final int HOLD_SEC2 = 20;
    private static final String EXPECTED_EXCEPTION = "expected exception";

    private LockCallback callback;
    private LockCallback callback2;
    private LockImpl lock;

    /**
     * Populates {@link #lock}.
     */
    @Before
    public void setUp() {
        callback = mock(LockCallback.class);
        callback2 = mock(LockCallback.class);

        lock = new LockImpl(STATE, RESOURCE, OWNER, OWNER_KEY, HOLD_SEC, callback, false);
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
    public void testFree_Succeed() {
        lock = new LockImpl(LockState.ACTIVE, RESOURCE, OWNER, OWNER_KEY, HOLD_SEC, callback, true);

        assertTrue(lock.free());
        assertFalse(lock.isActive());

        // should now return false
        assertFalse(lock.free());
        assertFalse(lock.isActive());

        verify(callback, never()).lockAvailable(any());
        verify(callback, never()).lockUnavailable(any());
    }

    @Test
    public void testExtend() {
        // succeed is set to "false", thus extend should fail
        lock.extend(HOLD_SEC, callback2);

        verify(callback, never()).lockAvailable(any());
        verify(callback, never()).lockUnavailable(any());

        verify(callback2, never()).lockAvailable(any());
        verify(callback2).lockUnavailable(any());
    }

    @Test
    public void testExtend_Succeed() {
        lock = new LockImpl(LockState.ACTIVE, RESOURCE, OWNER, OWNER_KEY, HOLD_SEC, callback, true);

        lock.extend(HOLD_SEC, callback2);
        assertTrue(lock.isActive());

        verify(callback, never()).lockAvailable(any());
        verify(callback, never()).lockUnavailable(any());

        verify(callback2).lockAvailable(any());
        verify(callback2, never()).lockUnavailable(any());


        // free it and then try to extend again
        lock.free();

        lock.extend(HOLD_SEC, callback2);
        assertFalse(lock.isActive());

        // should not have had any more calls
        verify(callback, never()).lockAvailable(any());
        verify(callback, never()).lockUnavailable(any());

        verify(callback2, times(1)).lockAvailable(any());

        // should have indicated that it's unavailable
        verify(callback2).lockUnavailable(any());
    }

    @Test
    public void testNotifyAvailable() {
        lock.notifyAvailable();

        verify(callback).lockAvailable(any());
        verify(callback, never()).lockUnavailable(any());
    }

    @Test
    public void testNotifyAvailable_Ex() {
        doThrow(new IllegalArgumentException(EXPECTED_EXCEPTION)).when(callback).lockAvailable(any());
        doThrow(new IllegalArgumentException(EXPECTED_EXCEPTION)).when(callback).lockUnavailable(any());

        // should not throw an exception
        lock.notifyAvailable();
    }

    @Test
    public void testNotifyUnavailable() {
        lock.notifyUnavailable();

        verify(callback, never()).lockAvailable(any());
        verify(callback).lockUnavailable(any());
    }

    @Test
    public void testNotifyUnavailable_Ex() {
        doThrow(new IllegalArgumentException(EXPECTED_EXCEPTION)).when(callback).lockAvailable(any());
        doThrow(new IllegalArgumentException(EXPECTED_EXCEPTION)).when(callback).lockUnavailable(any());

        // should not throw an exception
        lock.notifyUnavailable();
    }

    @Test
    public void testSetState_testIsActive_testIsWaiting_testIsUnavailable() {
        lock.setState(LockState.WAITING);
        assertEquals(LockState.WAITING, lock.getState());
        assertFalse(lock.isActive());
        assertFalse(lock.isUnavailable());
        assertTrue(lock.isWaiting());

        lock.setState(LockState.ACTIVE);
        assertEquals(LockState.ACTIVE, lock.getState());
        assertTrue(lock.isActive());
        assertFalse(lock.isUnavailable());
        assertFalse(lock.isWaiting());

        lock.setState(LockState.UNAVAILABLE);
        assertEquals(LockState.UNAVAILABLE, lock.getState());
        assertFalse(lock.isActive());
        assertTrue(lock.isUnavailable());
        assertFalse(lock.isWaiting());
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
        assertThat(text).doesNotContain("ownerInfo").doesNotContain("callback").doesNotContain("succeed");
    }

}
