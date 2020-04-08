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

package org.onap.policy.drools.system.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.onap.policy.drools.core.DroolsRunnable;
import org.onap.policy.drools.core.PolicySession;
import org.onap.policy.drools.core.lock.LockCallback;
import org.onap.policy.drools.core.lock.LockState;
import org.onap.policy.drools.system.PolicyEngineConstants;
import org.powermock.reflect.Whitebox;

public class FeatureLockImplTest {
    private static final String POLICY_ENGINE_EXECUTOR_FIELD = "executorService";
    private static final String OWNER_KEY = "my key";
    private static final String RESOURCE = "my resource";
    private static final int HOLD_SEC = 100;
    private static final int HOLD_SEC2 = 120;

    private static ScheduledExecutorService saveExec;

    @Mock
    private ScheduledExecutorService exsvc;

    @Mock
    private LockCallback callback;


    /**
     * Saves static fields and configures the location of the property files.
     */
    @BeforeClass
    public static void setUpBeforeClass() {
        saveExec = Whitebox.getInternalState(PolicyEngineConstants.getManager(), POLICY_ENGINE_EXECUTOR_FIELD);
    }

    /**
     * Restores static fields.
     */
    @AfterClass
    public static void tearDownAfterClass() {
        Whitebox.setInternalState(PolicyEngineConstants.getManager(), POLICY_ENGINE_EXECUTOR_FIELD, saveExec);
    }

    /**
     * Initializes the mocks and creates a feature that uses {@link #exsvc} to execute
     * tasks.
     */
    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        Whitebox.setInternalState(PolicyEngineConstants.getManager(), POLICY_ENGINE_EXECUTOR_FIELD, exsvc);
    }

    @Test
    public void testNoArgs() {
        MyLock lock = new MyLock();
        assertNull(lock.getResourceId());
        assertNull(lock.getOwnerKey());
        assertNull(lock.getCallback());
        assertEquals(0, lock.getHoldSec());
    }

    @Test
    public void testFeatureLockImpl() {
        MyLock lock = new MyLock(LockState.WAITING, RESOURCE, OWNER_KEY, HOLD_SEC, callback);
        assertTrue(lock.isWaiting());
        assertEquals(RESOURCE, lock.getResourceId());
        assertEquals(OWNER_KEY, lock.getOwnerKey());
        assertSame(callback, lock.getCallback());
        assertEquals(HOLD_SEC, lock.getHoldSec());
    }

    @Test
    public void testSerializable() throws Exception {
        MyLock lock = new MyLock(LockState.WAITING, RESOURCE, OWNER_KEY, HOLD_SEC, callback);
        lock = roundTrip(lock);

        assertTrue(lock.isWaiting());

        assertEquals(RESOURCE, lock.getResourceId());
        assertEquals(OWNER_KEY, lock.getOwnerKey());
        assertNull(lock.getCallback());
        assertEquals(HOLD_SEC, lock.getHoldSec());
    }

    @Test
    public void testGrant() {
        MyLock lock = new MyLock(LockState.WAITING, RESOURCE, OWNER_KEY, HOLD_SEC, callback);
        lock.grant();

        assertTrue(lock.isActive());
        assertEquals(1, lock.nupdates);

        invokeCallback(1);
        verify(callback).lockAvailable(any());
        verify(callback, never()).lockUnavailable(any());
    }

    /**
     * Tests grant() when the lock is already unavailable.
     */
    @Test
    public void testGrantUnavailable() {
        MyLock lock = new MyLock(LockState.UNAVAILABLE, RESOURCE, OWNER_KEY, HOLD_SEC, callback);
        lock.setState(LockState.UNAVAILABLE);
        lock.grant();

        assertTrue(lock.isUnavailable());
        assertEquals(0, lock.nupdates);

        verify(exsvc, never()).execute(any());
    }

    @Test
    public void testDeny() {
        MyLock lock = new MyLock(LockState.WAITING, RESOURCE, OWNER_KEY, HOLD_SEC, callback);
        lock.deny("my reason");

        assertTrue(lock.isUnavailable());

        invokeCallback(1);
        verify(callback, never()).lockAvailable(any());
        verify(callback).lockUnavailable(any());
    }

    /**
     * Tests doNotify() when a session exists.
     */
    @Test
    public void testDoNotifySession() {
        PolicySession session = mock(PolicySession.class);

        MyLock lock = new MyLock(LockState.WAITING, RESOURCE, OWNER_KEY, HOLD_SEC, callback) {
            private static final long serialVersionUID = 1L;

            @Override
            protected PolicySession getSession() {
                return session;
            }
        };

        lock.grant();

        assertTrue(lock.isActive());
        assertEquals(1, lock.nupdates);

        verify(exsvc, never()).execute(any());

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(session).insertDrools(captor.capture());

        DroolsRunnable runner = (DroolsRunnable) captor.getValue();
        runner.run();

        verify(callback).lockAvailable(any());
        verify(callback, never()).lockUnavailable(any());
    }

    /**
     * Tests doNotify() when there is no session.
     */
    @Test
    public void testDoNotifyNoSession() {
        MyLock lock = new MyLock(LockState.WAITING, RESOURCE, OWNER_KEY, HOLD_SEC, callback);
        lock.grant();

        assertTrue(lock.isActive());
        assertEquals(1, lock.nupdates);

        invokeCallback(1);
        verify(callback).lockAvailable(any());
        verify(callback, never()).lockUnavailable(any());
    }

    @Test
    public void testFreeAllowed() {
        MyLock lock = new MyLock(LockState.WAITING, RESOURCE, OWNER_KEY, HOLD_SEC, callback);
        assertTrue(lock.freeAllowed());
    }

    /**
     * Tests freeAllowed() when the lock is unavailable.
     */
    @Test
    public void testFreeAllowedUnavailable() {
        MyLock lock = new MyLock(LockState.UNAVAILABLE, RESOURCE, OWNER_KEY, HOLD_SEC, callback);
        assertFalse(lock.freeAllowed());
        assertTrue(lock.isUnavailable());
    }

    /**
     * Tests that free() works on a serialized lock with a new feature.
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void testFreeAllowedSerialized() throws Exception {
        MyLock lock = new MyLock(LockState.WAITING, RESOURCE, OWNER_KEY, HOLD_SEC, callback);

        lock = roundTrip(lock);
        assertTrue(lock.freeAllowed());
    }

    /**
     * Tests free() on a serialized lock without a feature.
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void testFreeAllowedNoFeature() throws Exception {
        MyLock lock = new MyLockNoFeature(LockState.WAITING, RESOURCE, OWNER_KEY, HOLD_SEC, callback);

        lock = roundTrip(lock);
        assertFalse(lock.freeAllowed());
        assertTrue(lock.isUnavailable());
    }

    @Test
    public void testExtendAllowed() {
        MyLock lock = new MyLock(LockState.WAITING, RESOURCE, OWNER_KEY, HOLD_SEC, callback);

        LockCallback scallback = mock(LockCallback.class);
        assertTrue(lock.extendAllowed(HOLD_SEC2, scallback));
        assertTrue(lock.isWaiting());
        assertEquals(HOLD_SEC2, lock.getHoldSec());
        assertSame(scallback, lock.getCallback());

        verify(exsvc, never()).execute(any());

        // invalid arguments

        // @formatter:off
        assertThatIllegalArgumentException().isThrownBy(
            () -> new MyLock(LockState.WAITING, RESOURCE, OWNER_KEY, HOLD_SEC, callback)
                            .extendAllowed(-1, callback))
            .withMessageContaining("holdSec");
        // @formatter:on
    }

    /**
     * Tests extendAllowed() when the lock is unavailable.
     */
    @Test
    public void testExtendAllowedUnavailable() {
        MyLock lock = new MyLock(LockState.UNAVAILABLE, RESOURCE, OWNER_KEY, HOLD_SEC, callback);

        LockCallback scallback = mock(LockCallback.class);
        assertFalse(lock.extendAllowed(HOLD_SEC2, scallback));
        assertTrue(lock.isUnavailable());
        assertEquals(HOLD_SEC2, lock.getHoldSec());
        assertSame(scallback, lock.getCallback());

        invokeCallback(1);
        verify(scallback, never()).lockAvailable(lock);
        verify(scallback).lockUnavailable(lock);
    }

    /**
     * Tests that extendAllowed() works on a serialized lock with a new feature.
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void testExtendAllowedSerialized() throws Exception {
        MyLock lock = new MyLock(LockState.WAITING, RESOURCE, OWNER_KEY, HOLD_SEC, callback);

        lock = roundTrip(lock);

        LockCallback scallback = mock(LockCallback.class);
        assertTrue(lock.extendAllowed(HOLD_SEC2, scallback));
        assertTrue(lock.isWaiting());
        assertEquals(HOLD_SEC2, lock.getHoldSec());
        assertSame(scallback, lock.getCallback());

        verify(exsvc, never()).execute(any());
    }

    /**
     * Tests extendAllowed() on a serialized lock without a feature.
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void testExtendAllowedNoFeature() throws Exception {
        MyLock lock = new MyLockNoFeature(LockState.WAITING, RESOURCE, OWNER_KEY, HOLD_SEC, callback);

        lock = roundTrip(lock);

        LockCallback scallback = mock(LockCallback.class);
        assertFalse(lock.extendAllowed(HOLD_SEC2, scallback));
        assertTrue(lock.isUnavailable());
        assertEquals(HOLD_SEC2, lock.getHoldSec());
        assertSame(scallback, lock.getCallback());

        invokeCallback(1);
        verify(scallback, never()).lockAvailable(lock);
        verify(scallback).lockUnavailable(lock);
    }

    @Test
    public void testGetSession() {
        MyLockStdSession lock = new MyLockStdSession(LockState.WAITING, RESOURCE, OWNER_KEY, HOLD_SEC, callback);

        // this should invoke the real policy session without throwing an exception
        assertThatCode(() -> lock.grant()).doesNotThrowAnyException();
    }

    @Test
    public void testToString() {
        String text = new MyLock(LockState.WAITING, RESOURCE, OWNER_KEY, HOLD_SEC, callback).toString();
        assertNotNull(text);
        assertThat(text).contains("LockImpl");
    }

    private MyLock roundTrip(MyLock lock) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(lock);
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        try (ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (MyLock) ois.readObject();
        }
    }

    /**
     * Invokes the last call-back in the work queue.
     *
     * @param nexpected number of call-backs expected in the work queue
     */
    private void invokeCallback(int nexpected) {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(exsvc, times(nexpected)).execute(captor.capture());

        if (nexpected > 0) {
            captor.getAllValues().get(nexpected - 1).run();
        }
    }

    /**
     * Lock that inherits the normal getSession() method.
     */
    public static class MyLockStdSession extends FeatureLockImpl {
        private static final long serialVersionUID = 1L;
        protected int nupdates = 0;

        public MyLockStdSession() {
            super();
        }

        public MyLockStdSession(LockState state, String resourceId, String ownerKey, int holdSec,
                        LockCallback callback) {
            super(state, resourceId, ownerKey, holdSec, callback);
        }

        @Override
        protected void updateGrant() {
            super.updateGrant();
            ++nupdates;
        }

        @Override
        public boolean free() {
            return false;
        }

        @Override
        public void extend(int holdSec, LockCallback callback) {
            // do nothing
        }

        @Override
        protected boolean addToFeature() {
            return true;
        }
    }

    public static class MyLock extends MyLockStdSession {
        private static final long serialVersionUID = 1L;

        public MyLock() {
            super();
        }

        public MyLock(LockState state, String resourceId, String ownerKey, int holdSec, LockCallback callback) {
            super(state, resourceId, ownerKey, holdSec, callback);
        }

        @Override
        protected PolicySession getSession() {
            return null;
        }
    }

    public static class MyLockNoFeature extends MyLock {
        private static final long serialVersionUID = 1L;

        public MyLockNoFeature() {
            super();
        }

        public MyLockNoFeature(LockState state, String resourceId, String ownerKey, int holdSec,
                        LockCallback callback) {
            super(state, resourceId, ownerKey, holdSec, callback);
        }

        @Override
        protected boolean addToFeature() {
            return false;
        }
    }
}
