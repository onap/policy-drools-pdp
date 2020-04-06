/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2019-2020 AT&T Intellectual Property. All rights reserved.
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.Before;
import org.junit.Test;

public class LockImplTest {
    private static final LockState STATE = LockState.WAITING;
    private static final String RESOURCE = "hello";
    private static final String OWNER_KEY = "world";
    private static final int HOLD_SEC = 10;
    private static final int HOLD_SEC2 = 20;
    private static final String EXPECTED_EXCEPTION = "expected exception";

    private LockCallback callback;
    private LockImpl lock;

    /**
     * Populates {@link #lock}.
     */
    @Before
    public void setUp() {
        callback = mock(LockCallback.class);

        lock = new LockImpl(STATE, RESOURCE, OWNER_KEY, HOLD_SEC, callback);
    }

    @Test
    public void testSerializable() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(lock);
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        try (ObjectInputStream ois = new ObjectInputStream(bais)) {
            lock = (LockImpl) ois.readObject();
        }

        assertEquals(STATE, lock.getState());
        assertEquals(RESOURCE, lock.getResourceId());
        assertEquals(OWNER_KEY, lock.getOwnerKey());
        assertEquals(HOLD_SEC, lock.getHoldSec());

        // these fields are transient
        assertNull(lock.getCallback());
    }

    @Test
    public void testLockImplNoArgs() {
        // use no-arg constructor
        lock = new LockImpl();
        assertEquals(LockState.UNAVAILABLE, lock.getState());
        assertNull(lock.getResourceId());
        assertNull(lock.getOwnerKey());
        assertNull(lock.getCallback());
        assertEquals(0, lock.getHoldSec());
    }

    @Test
    public void testLockImpl_testGetters() {
        assertEquals(STATE, lock.getState());
        assertEquals(RESOURCE, lock.getResourceId());
        assertEquals(OWNER_KEY, lock.getOwnerKey());
        assertSame(callback, lock.getCallback());
        assertEquals(HOLD_SEC, lock.getHoldSec());

        // test illegal args
        assertThatThrownBy(() -> new LockImpl(null, RESOURCE, OWNER_KEY, HOLD_SEC, callback))
                        .hasMessageContaining("state");
        assertThatThrownBy(() -> new LockImpl(STATE, null, OWNER_KEY, HOLD_SEC, callback))
                        .hasMessageContaining("resourceId");
        assertThatThrownBy(() -> new LockImpl(STATE, RESOURCE, null, HOLD_SEC, callback))
                        .hasMessageContaining("ownerKey");
        assertThatIllegalArgumentException().isThrownBy(() -> new LockImpl(STATE, RESOURCE, OWNER_KEY, -1, callback))
                        .withMessageContaining("holdSec");
        assertThatThrownBy(() -> new LockImpl(STATE, RESOURCE, OWNER_KEY, HOLD_SEC, null))
                        .hasMessageContaining("callback");
    }

    @Test
    public void testFree() {
        assertTrue(lock.free());
        assertTrue(lock.isUnavailable());

        // should fail this time
        assertFalse(lock.free());
        assertTrue(lock.isUnavailable());

        // no call-backs should have been invoked
        verify(callback, never()).lockAvailable(any());
        verify(callback, never()).lockUnavailable(any());
    }

    @Test
    public void testExtend() {
        lock.setState(LockState.WAITING);

        LockCallback callback2 = mock(LockCallback.class);
        lock.extend(HOLD_SEC2, callback2);
        assertTrue(lock.isActive());
        assertEquals(HOLD_SEC2, lock.getHoldSec());
        assertSame(callback2, lock.getCallback());
        verify(callback2).lockAvailable(lock);
        verify(callback2, never()).lockUnavailable(any());

        // first call-back should never have been invoked
        verify(callback, never()).lockAvailable(any());
        verify(callback, never()).lockUnavailable(any());

        // extend again
        LockCallback callback3 = mock(LockCallback.class);
        lock.extend(HOLD_SEC, callback3);
        assertEquals(HOLD_SEC, lock.getHoldSec());
        assertSame(callback3, lock.getCallback());
        assertTrue(lock.isActive());
        verify(callback3).lockAvailable(lock);
        verify(callback3, never()).lockUnavailable(any());

        // other call-backs should not have been invoked again
        verify(callback, never()).lockAvailable(any());
        verify(callback, never()).lockUnavailable(any());

        verify(callback2).lockAvailable(any());
        verify(callback2, never()).lockUnavailable(any());

        assertTrue(lock.free());

        // extend after free - should fail
        lock.extend(HOLD_SEC2, callback);
        assertTrue(lock.isUnavailable());

        // call-backs should not have been invoked again
        verify(callback, never()).lockAvailable(any());
        verify(callback, never()).lockUnavailable(any());

        verify(callback2).lockAvailable(any());
        verify(callback2, never()).lockUnavailable(any());

        verify(callback3).lockAvailable(lock);
        verify(callback3, never()).lockUnavailable(any());
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
        assertThatCode(() -> lock.notifyAvailable()).doesNotThrowAnyException();
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
        assertThatCode(() -> lock.notifyUnavailable()).doesNotThrowAnyException();
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
        assertEquals(HOLD_SEC, lock.getHoldSec());

        lock.setHoldSec(HOLD_SEC2);
        assertEquals(HOLD_SEC2, lock.getHoldSec());
    }

    @Test
    public void testSetCallback() {
        assertSame(callback, lock.getCallback());

        LockCallback callback2 = mock(LockCallback.class);
        lock.setCallback(callback2);
        assertSame(callback2, lock.getCallback());
    }

    @Test
    public void testToString() {
        String text = lock.toString();

        assertNotNull(text);
        assertThat(text).doesNotContain("ownerInfo").doesNotContain("callback").doesNotContain("succeed");
    }
}
