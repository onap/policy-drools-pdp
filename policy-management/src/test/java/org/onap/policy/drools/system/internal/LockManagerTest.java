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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.ScheduledExecutorService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.onap.policy.drools.core.lock.AlwaysFailLock;
import org.onap.policy.drools.core.lock.Lock;
import org.onap.policy.drools.core.lock.LockCallback;
import org.onap.policy.drools.core.lock.LockState;

public class LockManagerTest {
    private static final String OWNER_KEY = "my key";
    private static final String RESOURCE = "my resource";
    private static final String RESOURCE2 = "my resource #2";
    private static final int HOLD_SEC = 100;

    @Mock
    private LockCallback callback;

    @Mock
    private ScheduledExecutorService exsvc;

    private MyManager mgr;

    /**
     * Resets fields and creates {@link #mgr}.
     */
    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        doAnswer(args -> {
            args.getArgument(0, Runnable.class).run();
            return null;
        }).when(exsvc).execute(any());

        mgr = new MyManager();
    }

    @After
    public void tearDown() {

    }

    @Test
    public void testIsAlive() {
        assertFalse(mgr.isAlive());
        assertFalse(mgr.isLocked());

        mgr.start();
        assertTrue(mgr.isAlive());
        assertFalse(mgr.isLocked());

        mgr.stop();
        assertFalse(mgr.isAlive());
    }

    @Test
    public void testStart() {
        assertTrue(mgr.start());
        assertTrue(mgr.isAlive());

        assertFalse(mgr.start());
        assertTrue(mgr.isAlive());

        mgr.stop();
        assertTrue(mgr.start());
        assertTrue(mgr.isAlive());
    }

    @Test
    public void testStop() {
        assertFalse(mgr.stop());

        mgr.start();
        assertTrue(mgr.stop());
        assertFalse(mgr.isAlive());
    }

    @Test
    public void testShutdown() {
        mgr.start();
        mgr.shutdown();
        assertFalse(mgr.isAlive());

        mgr.shutdown();
        assertFalse(mgr.isAlive());
    }

    @Test
    public void testIsLocked() {
        assertFalse(mgr.isLocked());
        assertFalse(mgr.isAlive());

        mgr.lock();
        assertTrue(mgr.isLocked());
        assertFalse(mgr.isAlive());

        mgr.unlock();
        assertFalse(mgr.isLocked());
    }

    @Test
    public void testLock() {
        assertTrue(mgr.lock());
        assertTrue(mgr.isLocked());

        assertFalse(mgr.lock());
        assertTrue(mgr.isLocked());

        mgr.unlock();
        assertTrue(mgr.lock());
        assertTrue(mgr.isLocked());
    }

    @Test
    public void testUnlock() {
        assertFalse(mgr.unlock());

        mgr.lock();
        assertTrue(mgr.unlock());
        assertFalse(mgr.isLocked());
    }

    @Test
    public void testCreateLock() {
        Lock lock = mgr.createLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);
        assertTrue(lock.isActive());
        verify(callback).lockAvailable(lock);
        verify(callback, never()).lockUnavailable(lock);

        // should not be able to lock it again
        LockCallback callback2 = mock(LockCallback.class);
        Lock lock2 = mgr.createLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback2, false);
        assertTrue(lock2.isUnavailable());
        verify(callback2, never()).lockAvailable(lock2);
        verify(callback2).lockUnavailable(lock2);

        // should be able to lock another resource
        LockCallback callback3 = mock(LockCallback.class);
        Lock lock3 = mgr.createLock(RESOURCE2, OWNER_KEY, HOLD_SEC, callback3, false);
        assertTrue(lock3.isActive());
        verify(callback3).lockAvailable(lock3);
        verify(callback3, never()).lockUnavailable(lock3);
    }

    /**
     * Tests createLock() when the feature instance has changed.
     */
    @Test
    public void testCreateLockInstanceChanged() {
        mgr = spy(mgr);
        when(mgr.hasInstanceChanged()).thenReturn(true);

        Lock lock = mgr.createLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);
        assertTrue(lock instanceof AlwaysFailLock);
        assertTrue(lock.isUnavailable());

        verify(callback, never()).lockAvailable(lock);
        verify(callback).lockUnavailable(lock);
    }

    @Test
    public void testGetResource2lock() {
        assertNotNull(mgr.getResource2lock());
    }

    private class MyManager extends LockManager<MyLock> {

        @Override
        protected boolean hasInstanceChanged() {
            return false;
        }

        @Override
        protected void finishLock(MyLock lock) {
            lock.grant();
        }

        @Override
        protected MyLock makeLock(LockState waiting, String resourceId, String ownerKey, int holdSec,
                        LockCallback callback) {
            return new MyLock(waiting, resourceId, ownerKey, holdSec, callback);
        }

    }

    private class MyLock extends FeatureLockImpl {
        private static final long serialVersionUID = 1L;

        public MyLock(LockState waiting, String resourceId, String ownerKey, int holdSec, LockCallback callback) {
            super(waiting, resourceId, ownerKey, holdSec, callback);
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
            return false;
        }

        @Override
        public void notifyAvailable() {
            getCallback().lockAvailable(this);
        }

        @Override
        public void notifyUnavailable() {
            getCallback().lockUnavailable(this);
        }

        @Override
        protected ScheduledExecutorService getThreadPool() {
            return exsvc;
        }
    }
}
