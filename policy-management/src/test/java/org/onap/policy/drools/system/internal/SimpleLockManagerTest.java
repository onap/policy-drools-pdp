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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.kie.api.runtime.KieSession;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.onap.policy.common.utils.time.CurrentTime;
import org.onap.policy.common.utils.time.TestTime;
import org.onap.policy.drools.core.PolicySession;
import org.onap.policy.drools.core.lock.Lock;
import org.onap.policy.drools.core.lock.LockCallback;
import org.onap.policy.drools.core.lock.LockState;
import org.onap.policy.drools.system.PolicyEngineConstants;
import org.onap.policy.drools.system.internal.SimpleLockManager.SimpleLock;
import org.powermock.reflect.Whitebox;

public class SimpleLockManagerTest {
    private static final String POLICY_ENGINE_EXECUTOR_FIELD = "executorService";
    private static final String TIME_FIELD = "currentTime";
    private static final String OWNER_KEY = "my key";
    private static final String RESOURCE = "my resource";
    private static final String RESOURCE2 = "my resource #2";
    private static final String RESOURCE3 = "my resource #3";
    private static final int HOLD_SEC = 100;
    private static final int HOLD_SEC2 = 120;
    private static final int HOLD_MS = HOLD_SEC * 1000;
    private static final int HOLD_MS2 = HOLD_SEC2 * 1000;
    private static final int MAX_THREADS = 10;
    private static final int MAX_LOOPS = 50;

    private static CurrentTime saveTime;
    private static ScheduledExecutorService saveExec;
    private static ScheduledExecutorService realExec;

    private PolicySession session;
    private TestTime testTime;
    private AtomicInteger nactive;
    private AtomicInteger nsuccesses;
    private SimpleLockManager feature;

    @Mock
    private KieSession kieSess;

    @Mock
    private ScheduledExecutorService exsvc;

    @Mock
    private ScheduledFuture<?> future;

    @Mock
    private LockCallback callback;


    /**
     * Saves static fields and configures the location of the property files.
     */
    @BeforeClass
    public static void setUpBeforeClass() {
        saveTime = Whitebox.getInternalState(SimpleLockManager.class, TIME_FIELD);
        saveExec = Whitebox.getInternalState(PolicyEngineConstants.getManager(), POLICY_ENGINE_EXECUTOR_FIELD);

        realExec = Executors.newScheduledThreadPool(3);
    }

    /**
     * Restores static fields.
     */
    @AfterClass
    public static void tearDownAfterClass() {
        Whitebox.setInternalState(SimpleLockManager.class, TIME_FIELD, saveTime);
        Whitebox.setInternalState(PolicyEngineConstants.getManager(), POLICY_ENGINE_EXECUTOR_FIELD, saveExec);

        realExec.shutdown();
    }

    /**
     * Initializes the mocks and creates a feature that uses {@link #exsvc} to execute
     * tasks.
     */
    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        // grant() and deny() calls will come through here and be immediately executed
        session = new PolicySession(null, null, kieSess) {
            @Override
            public void insertDrools(Object object) {
                ((Runnable) object).run();
            }
        };

        session.setPolicySession();

        testTime = new TestTime();
        nactive = new AtomicInteger(0);
        nsuccesses = new AtomicInteger(0);

        Whitebox.setInternalState(SimpleLockManager.class, TIME_FIELD, testTime);

        Whitebox.setInternalState(PolicyEngineConstants.getManager(), POLICY_ENGINE_EXECUTOR_FIELD, exsvc);

        feature = new MyLockingFeature();
        feature.start();
    }

    /**
     * Tests constructor() when properties are invalid.
     */
    @Test
    public void testSimpleLockManagerInvalidProperties() {
        // use properties containing an invalid value
        Properties props = new Properties();
        props.setProperty(SimpleLockProperties.EXPIRE_CHECK_SEC, "abc");

        assertThatThrownBy(() -> new MyLockingFeature(props)).isInstanceOf(SimpleLockManagerException.class);
    }

    @Test
    public void testStart() {
        assertTrue(feature.isAlive());
        verify(exsvc).scheduleWithFixedDelay(any(), anyLong(), anyLong(), any());

        assertFalse(feature.start());

        feature.stop();
        assertTrue(feature.start());
    }

    @Test
    public void testStop() {
        assertTrue(feature.stop());
        assertFalse(feature.isAlive());
        verify(future).cancel(true);

        assertFalse(feature.stop());

        // no more invocations
        verify(future).cancel(anyBoolean());
    }

    @Test
    public void testShutdown() {
        feature.shutdown();

        verify(future).cancel(true);
    }

    @Test
    public void testCreateLock() {
        // this lock should be granted immediately
        SimpleLock lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);
        assertTrue(lock.isActive());
        assertEquals(testTime.getMillis() + HOLD_MS, lock.getHoldUntilMs());

        verify(callback).lockAvailable(lock);
        verify(callback, never()).lockUnavailable(lock);


        // this time it should be busy
        Lock lock2 = feature.createLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);
        assertFalse(lock2.isActive());
        assertTrue(lock2.isUnavailable());

        verify(callback, never()).lockAvailable(lock2);
        verify(callback).lockUnavailable(lock2);

        // should have been no change to the original lock
        assertTrue(lock.isActive());
        verify(callback).lockAvailable(lock);
        verify(callback, never()).lockUnavailable(lock);

        // should work with "true" value also
        Lock lock3 = feature.createLock(RESOURCE2, OWNER_KEY, HOLD_SEC, callback, true);
        assertTrue(lock3.isActive());
        verify(callback).lockAvailable(lock3);
        verify(callback, never()).lockUnavailable(lock3);
    }

    /**
     * Tests createLock() when the feature is not the latest instance.
     */
    @Test
    public void testCreateLockNotLatestInstance() {
        SimpleLockManager.setLatestInstance(null);

        Lock lock = feature.createLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);
        assertTrue(lock.isUnavailable());
        verify(callback, never()).lockAvailable(any());
        verify(callback).lockUnavailable(lock);
    }

    @Test
    public void testCheckExpired() throws InterruptedException {
        final SimpleLock lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);
        final SimpleLock lock2 = getLock(RESOURCE2, OWNER_KEY, HOLD_SEC, callback, false);
        final SimpleLock lock3 = getLock(RESOURCE3, OWNER_KEY, HOLD_SEC2, callback, false);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(exsvc).scheduleWithFixedDelay(captor.capture(), anyLong(), anyLong(), any());

        Runnable checker = captor.getValue();

        // time unchanged - checker should have no impact
        checker.run();
        assertTrue(lock.isActive());
        assertTrue(lock2.isActive());
        assertTrue(lock3.isActive());

        // expire the first two locks
        testTime.sleep(HOLD_MS);
        checker.run();
        assertFalse(lock.isActive());
        assertFalse(lock2.isActive());
        assertTrue(lock3.isActive());

        verify(callback).lockUnavailable(lock);
        verify(callback).lockUnavailable(lock2);
        verify(callback, never()).lockUnavailable(lock3);

        // should be able to get a lock on the first two resources
        assertTrue(feature.createLock(RESOURCE, OWNER_KEY, HOLD_SEC + HOLD_SEC2, callback, false).isActive());
        assertTrue(feature.createLock(RESOURCE2, OWNER_KEY, HOLD_SEC + HOLD_SEC2, callback, false).isActive());

        // lock is still busy on the last resource
        assertFalse(feature.createLock(RESOURCE3, OWNER_KEY, HOLD_SEC + HOLD_SEC2, callback, false).isActive());

        // expire the last lock
        testTime.sleep(HOLD_MS2);
        checker.run();
        assertFalse(lock3.isActive());

        verify(callback).lockUnavailable(lock3);
    }

    /**
     * Tests checkExpired(), where the lock is removed from the map between invoking
     * expired() and compute(). Should cause "null" to be returned by compute().
     *
     * @throws InterruptedException if the test is interrupted
     */
    @Test
    public void testCheckExpiredLockDeleted() throws InterruptedException {
        feature = new MyLockingFeature() {
            @Override
            protected SimpleLock makeLock(LockState waiting, String resourceId, String ownerKey, int holdSec,
                            LockCallback callback) {
                return new SimpleLock(waiting, resourceId, ownerKey, holdSec, callback, feature) {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public boolean expired(long currentMs) {
                        // remove the lock from the map
                        free();
                        return true;
                    }
                };
            }
        };

        feature.start();

        feature.createLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(exsvc).scheduleWithFixedDelay(captor.capture(), anyLong(), anyLong(), any());

        Runnable checker = captor.getValue();

        checker.run();

        // lock should now be gone and we should be able to get another
        feature.createLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);

        // should have succeeded twice
        verify(callback, times(2)).lockAvailable(any());

        // lock should not be available now
        feature.createLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);
        verify(callback).lockUnavailable(any());
    }

    /**
     * Tests checkExpired(), where the lock is removed from the map and replaced with a
     * new lock, between invoking expired() and compute(). Should cause the new lock to be
     * returned.
     *
     * @throws InterruptedException if the test is interrupted
     */
    @Test
    public void testCheckExpiredLockReplaced() throws InterruptedException {
        feature = new MyLockingFeature() {
            private boolean madeLock = false;

            @Override
            protected SimpleLock makeLock(LockState waiting, String resourceId, String ownerKey, int holdSec,
                            LockCallback callback) {
                if (madeLock) {
                    return new SimpleLock(waiting, resourceId, ownerKey, holdSec, callback, feature);
                }

                madeLock = true;

                return new SimpleLock(waiting, resourceId, ownerKey, holdSec, callback, feature) {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public boolean expired(long currentMs) {
                        // remove the lock from the map and add a new lock
                        free();
                        feature.createLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);

                        return true;
                    }
                };
            }
        };

        feature.start();

        feature.createLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(exsvc).scheduleWithFixedDelay(captor.capture(), anyLong(), anyLong(), any());

        Runnable checker = captor.getValue();

        checker.run();

        // lock should not be available now
        feature.createLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);
        verify(callback).lockUnavailable(any());
    }

    @Test
    public void testGetThreadPool() {
        // use a real feature
        feature = new SimpleLockManager(null, new Properties());

        // load properties
        feature.start();

        // should create thread pool
        feature.createLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);

        // should shut down thread pool
        assertThatCode(() -> feature.stop()).doesNotThrowAnyException();
    }

    @Test
    public void testSimpleLockNoArgs() {
        SimpleLock lock = new SimpleLock();
        assertNull(lock.getResourceId());
        assertNull(lock.getOwnerKey());
        assertNull(lock.getCallback());
        assertEquals(0, lock.getHoldSec());

        assertEquals(0, lock.getHoldUntilMs());
    }

    @Test
    public void testSimpleLockSimpleLock() {
        SimpleLock lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);
        assertEquals(RESOURCE, lock.getResourceId());
        assertEquals(OWNER_KEY, lock.getOwnerKey());
        assertSame(callback, lock.getCallback());
        assertEquals(HOLD_SEC, lock.getHoldSec());

        assertThatIllegalArgumentException()
                        .isThrownBy(() -> feature.createLock(RESOURCE, OWNER_KEY, -1, callback, false))
                        .withMessageContaining("holdSec");
    }

    @Test
    public void testSimpleLockSerializable() throws Exception {
        SimpleLock lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);
        lock = roundTrip(lock);

        assertTrue(lock.isActive());

        assertEquals(RESOURCE, lock.getResourceId());
        assertEquals(OWNER_KEY, lock.getOwnerKey());
        assertNull(lock.getCallback());
        assertEquals(HOLD_SEC, lock.getHoldSec());
    }

    @Test
    public void testSimpleLockExpired() {
        SimpleLock lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);
        lock.grant();

        assertFalse(lock.expired(testTime.getMillis()));
        assertFalse(lock.expired(testTime.getMillis() + HOLD_MS - 1));
        assertTrue(lock.expired(testTime.getMillis() + HOLD_MS));
    }

    @Test
    public void testSimpleLockFree() {
        final SimpleLock lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);

        // lock2 should be denied
        SimpleLock lock2 = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);
        verify(callback, never()).lockAvailable(lock2);
        verify(callback).lockUnavailable(lock2);

        // lock2 was denied, so nothing new should happen when freed
        assertFalse(lock2.free());

        // force lock2 to be active - still nothing should happen
        Whitebox.setInternalState(lock2, "state", LockState.ACTIVE);
        assertFalse(lock2.free());

        // now free the first lock
        assertTrue(lock.free());
        assertEquals(LockState.UNAVAILABLE, lock.getState());

        // should be able to get the lock now
        SimpleLock lock3 = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);
        assertTrue(lock3.isActive());

        verify(callback).lockAvailable(lock3);
        verify(callback, never()).lockUnavailable(lock3);
    }

    /**
     * Tests that free() works on a serialized lock with a new feature.
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void testSimpleLockFreeSerialized() throws Exception {
        SimpleLock lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);

        feature = new MyLockingFeature();
        feature.start();

        lock = roundTrip(lock);
        assertTrue(lock.free());
        assertTrue(lock.isUnavailable());
    }

    @Test
    public void testSimpleLockExtend() {
        final SimpleLock lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);

        // lock2 should be denied
        SimpleLock lock2 = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);
        verify(callback, never()).lockAvailable(lock2);
        verify(callback).lockUnavailable(lock2);

        // lock2 will still be denied
        lock2.extend(HOLD_SEC, callback);
        verify(callback, times(2)).lockUnavailable(lock2);

        // force lock2 to be active - should still be denied
        Whitebox.setInternalState(lock2, "state", LockState.ACTIVE);
        lock2.extend(HOLD_SEC, callback);
        verify(callback, times(3)).lockUnavailable(lock2);

        assertThatIllegalArgumentException().isThrownBy(() -> lock.extend(-1, callback))
                        .withMessageContaining("holdSec");

        // now extend the first lock
        lock.extend(HOLD_SEC2, callback);
        assertEquals(HOLD_SEC2, lock.getHoldSec());
        assertEquals(testTime.getMillis() + HOLD_MS2, lock.getHoldUntilMs());
        verify(callback, times(2)).lockAvailable(lock);
        verify(callback, never()).lockUnavailable(lock);
    }

    /**
     * Tests that extend() works on a serialized lock with a new feature.
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void testSimpleLockExtendSerialized() throws Exception {
        SimpleLock lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);

        feature = new MyLockingFeature();
        feature.start();

        lock = roundTrip(lock);
        LockCallback scallback = mock(LockCallback.class);

        lock.extend(HOLD_SEC, scallback);
        assertTrue(lock.isActive());

        verify(scallback).lockAvailable(lock);
        verify(scallback, never()).lockUnavailable(lock);
    }

    /**
     * Tests that extend() fails when there is no feature.
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void testSimpleLockExtendNoFeature() throws Exception {
        SimpleLock lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);

        SimpleLockManager.setLatestInstance(null);

        lock = roundTrip(lock);
        LockCallback scallback = mock(LockCallback.class);

        lock.extend(HOLD_SEC, scallback);
        assertTrue(lock.isUnavailable());

        verify(scallback, never()).lockAvailable(lock);
        verify(scallback).lockUnavailable(lock);
    }

    @Test
    public void testSimpleLockToString() {
        String text = feature.createLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false).toString();
        assertNotNull(text);
        assertThat(text).contains("holdUntil").doesNotContain("ownerInfo").doesNotContain("callback");
    }

    /**
     * Performs a multi-threaded test of the locking facility.
     *
     * @throws InterruptedException if the current thread is interrupted while waiting for
     *         the background threads to complete
     */
    @Test
    public void testMultiThreaded() throws InterruptedException {
        Whitebox.setInternalState(SimpleLockManager.class, TIME_FIELD, testTime);
        Whitebox.setInternalState(PolicyEngineConstants.getManager(), POLICY_ENGINE_EXECUTOR_FIELD, realExec);
        feature = new SimpleLockManager(null, new Properties());
        feature.start();

        List<MyThread> threads = new ArrayList<>(MAX_THREADS);
        for (int x = 0; x < MAX_THREADS; ++x) {
            threads.add(new MyThread());
        }

        threads.forEach(Thread::start);

        for (MyThread thread : threads) {
            thread.join(6000);
            assertFalse(thread.isAlive());
        }

        for (MyThread thread : threads) {
            if (thread.err != null) {
                throw thread.err;
            }
        }

        assertTrue(nsuccesses.get() > 0);
    }

    private SimpleLock getLock(String resource, String ownerKey, int holdSec, LockCallback callback,
                    boolean waitForLock) {
        return (SimpleLock) feature.createLock(resource, ownerKey, holdSec, callback, waitForLock);
    }

    private SimpleLock roundTrip(SimpleLock lock) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(lock);
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        try (ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (SimpleLock) ois.readObject();
        }
    }

    /**
     * Feature that uses <i>exsvc</i> to execute requests.
     */
    private class MyLockingFeature extends SimpleLockManager {

        public MyLockingFeature() {
            this(new Properties());
        }

        public MyLockingFeature(Properties props) {
            super(null, props);

            exsvc = mock(ScheduledExecutorService.class);
            Whitebox.setInternalState(PolicyEngineConstants.getManager(), POLICY_ENGINE_EXECUTOR_FIELD, exsvc);

            when(exsvc.scheduleWithFixedDelay(any(), anyLong(), anyLong(), any())).thenAnswer(answer -> {
                return future;
            });
        }
    }

    /**
     * Thread used with the multi-threaded test. It repeatedly attempts to get a lock,
     * extend it, and then unlock it.
     */
    private class MyThread extends Thread {
        AssertionError err = null;

        public MyThread() {
            setDaemon(true);
        }

        @Override
        public void run() {
            try {
                for (int x = 0; x < MAX_LOOPS; ++x) {
                    makeAttempt();
                }

            } catch (AssertionError e) {
                err = e;
            }
        }

        private void makeAttempt() {
            try {
                Semaphore sem = new Semaphore(0);

                LockCallback cb = new LockCallback() {
                    @Override
                    public void lockAvailable(Lock lock) {
                        sem.release();
                    }

                    @Override
                    public void lockUnavailable(Lock lock) {
                        sem.release();
                    }
                };

                Lock lock = feature.createLock(RESOURCE, getName(), HOLD_SEC, cb, false);

                // wait for callback, whether available or unavailable
                assertTrue(sem.tryAcquire(5, TimeUnit.SECONDS));
                if (!lock.isActive()) {
                    return;
                }

                nsuccesses.incrementAndGet();

                assertEquals(1, nactive.incrementAndGet());

                lock.extend(HOLD_SEC2, cb);
                assertTrue(sem.tryAcquire(5, TimeUnit.SECONDS));
                assertTrue(lock.isActive());

                // decrement BEFORE free()
                nactive.decrementAndGet();

                assertTrue(lock.free());
                assertTrue(lock.isUnavailable());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted", e);
            }
        }
    }
}
