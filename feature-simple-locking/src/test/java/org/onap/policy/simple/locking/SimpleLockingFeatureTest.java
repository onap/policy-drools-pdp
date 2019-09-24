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

package org.onap.policy.simple.locking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.onap.policy.common.utils.time.CurrentTime;
import org.onap.policy.common.utils.time.TestTime;
import org.onap.policy.drools.core.lock.Lock;
import org.onap.policy.drools.core.lock.LockCallback;
import org.onap.policy.drools.core.lock.LockState;
import org.onap.policy.drools.persistence.SystemPersistenceConstants;
import org.onap.policy.drools.system.PolicyEngine;
import org.onap.policy.simple.locking.SimpleLockingFeature.SimpleLock;
import org.powermock.reflect.Whitebox;

public class SimpleLockingFeatureTest {
    private static final String TIME_FIELD = "currentTime";
    private static final String EXPECTED_EXCEPTION = "expected exception";
    private static final String OWNER_KEY = "my key";
    private static final String RESOURCE = "my resource";
    private static final String RESOURCE2 = "my resource #2";
    private static final String RESOURCE3 = "my resource #3";
    private static final int HOLD_SEC = 100;
    private static final int HOLD_SEC2 = 120;
    private static final int MAX_THREADS = 10;
    private static final int MAX_LOOPS = 50;

    private static CurrentTime saveTime;

    private TestTime testTime;
    private Object owner;
    private AtomicInteger nactive;
    private AtomicInteger nsuccesses;
    private SimpleLockingFeature feature;

    @Mock
    private ScheduledExecutorService exsvc;

    @Mock
    private LockCallback callback;


    /**
     * Saves static fields and configures the location of the property files.
     */
    @BeforeClass
    public static void setUpBeforeClass() {
        saveTime = Whitebox.getInternalState(SimpleLockingFeature.class, TIME_FIELD);
    }

    @AfterClass
    public static void tearDownAfterClass() {
        Whitebox.setInternalState(SimpleLockingFeature.class, TIME_FIELD, saveTime);
    }

    /**
     * Initializes the mocks and creates a feature that uses {@link #exsvc} to execute
     * tasks.
     */
    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        testTime = new TestTime();
        owner = new MyOwner();
        nactive = new AtomicInteger(0);
        nsuccesses = new AtomicInteger(0);

        Whitebox.setInternalState(SimpleLockingFeature.class, TIME_FIELD, testTime);

        SystemPersistenceConstants.getManager().setConfigurationDir("src/test/resources");

        feature = new MyLockingFeature();
        feature.afterStart((PolicyEngine) null);

        Properties props = new Properties();
        props.setProperty(SimpleLockingFeature.EXTRACTOR_PREFIX + MyOwner.class.getName(), "v.key");
        assertNull(feature.beforeCreate(null, props));
    }

    @Test
    public void testGetSequenceNumber() {
        assertEquals(0, feature.getSequenceNumber());
    }

    @Test
    public void testAfterStart_Ex() {
        feature = new MyLockingFeature() {
            @Override
            protected Properties getFeatureProperties() {
                throw new IllegalArgumentException(EXPECTED_EXCEPTION);
            }
        };

        assertThatThrownBy(() -> feature.afterStart((PolicyEngine) null))
                        .isInstanceOf(SimpleLockingFeatureException.class);
    }

    @Test
    public void testGetFeatureProperties() {
        // use a non-existent config directory
        SystemPersistenceConstants.getManager().setConfigurationDir("src/test/resources/xxx");

        feature = new SimpleLockingFeature();

        // should use default properties without throwing an exception
        feature.afterStart((PolicyEngine) null);

        // should be enabled now
        assertTrue(feature.enabled());
    }

    @Test
    public void testEnabled() {
        assertTrue(feature.enabled());
    }

    @Test
    public void testBeforeShutdown() {
        // with no thread pool
        assertFalse(feature.beforeShutdown((PolicyEngine) null));

        // with thread pool
        feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);
        assertFalse(feature.beforeShutdown((PolicyEngine) null));
        verify(exsvc).shutdown();
    }

    @Test
    public void testLock() {
        // this lock should be granted immediately
        SimpleLock lock = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);
        assertTrue(lock.isActive());
        assertEquals(testTime.getMillis() + HOLD_SEC, lock.getHoldUntilMs());

        invokeCallback(1);

        verify(callback).lockAvailable(lock);
        verify(callback, never()).lockUnavailable(lock);


        // this time it should be busy
        Lock lock2 = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);
        assertFalse(lock2.isActive());
        assertEquals(LockState.UNAVAILABLE, lock2.getState());

        invokeCallback(2);

        verify(callback, never()).lockAvailable(lock2);
        verify(callback).lockUnavailable(lock2);

        // should have been no change to the original lock
        assertTrue(lock.isActive());
        verify(callback).lockAvailable(lock);
        verify(callback, never()).lockUnavailable(lock);

        // should work with "true" value also
        Lock lock3 = feature.lock(RESOURCE2, owner, HOLD_SEC, callback, true);
        assertTrue(lock3.isActive());
        invokeCallback(3);
        verify(callback).lockAvailable(lock3);
        verify(callback, never()).lockUnavailable(lock3);
    }

    @Test
    public void testInitThreadPool() {
        // force init() to be invoked
        feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);
        verify(exsvc).scheduleWithFixedDelay(any(), eq(900L), eq(900L), eq(TimeUnit.SECONDS));

        // invoke it again - nothing should change
        feature.lock(RESOURCE2, owner, HOLD_SEC, callback, false);
        verify(exsvc, times(1)).scheduleWithFixedDelay(any(), anyLong(), anyLong(), any());
    }

    @Test
    public void testCheckExpired() throws InterruptedException {
        SimpleLock lock = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);
        SimpleLock lock2 = feature.lock(RESOURCE2, owner, HOLD_SEC, callback, false);
        SimpleLock lock3 = feature.lock(RESOURCE3, owner, HOLD_SEC2, callback, false);

        testCheckExpired2(lock, lock2, lock3);
    }

    /*
     * Had to put this in a separate method to avoid checkstyle issue about variables
     * being defined too soon before their actual usage.
     */
    private void testCheckExpired2(SimpleLock lock, SimpleLock lock2, SimpleLock lock3) throws InterruptedException {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(exsvc).scheduleWithFixedDelay(captor.capture(), anyLong(), anyLong(), any());

        Runnable checker = captor.getValue();

        // time unchanged - checker should have no impact
        checker.run();
        assertTrue(lock.isActive());
        assertTrue(lock2.isActive());
        assertTrue(lock3.isActive());

        // expire the first two locks
        testTime.sleep(HOLD_SEC);
        checker.run();
        assertFalse(lock.isActive());
        assertFalse(lock2.isActive());
        assertTrue(lock3.isActive());

        // run the callbacks
        captor = ArgumentCaptor.forClass(Runnable.class);
        verify(exsvc, times(5)).execute(captor.capture());
        captor.getAllValues().forEach(Runnable::run);
        verify(callback).lockUnavailable(lock);
        verify(callback).lockUnavailable(lock2);
        verify(callback, never()).lockUnavailable(lock3);

        // should be able to get a lock on the first two resources
        assertTrue(feature.lock(RESOURCE, owner, HOLD_SEC + HOLD_SEC2, callback, false).isActive());
        assertTrue(feature.lock(RESOURCE2, owner, HOLD_SEC + HOLD_SEC2, callback, false).isActive());

        // lock is still busy on the last resource
        assertFalse(feature.lock(RESOURCE3, owner, HOLD_SEC + HOLD_SEC2, callback, false).isActive());

        // expire the last lock
        testTime.sleep(HOLD_SEC2);
        checker.run();
        assertFalse(lock3.isActive());

        // run the callback
        captor = ArgumentCaptor.forClass(Runnable.class);
        verify(exsvc, times(9)).execute(captor.capture());
        captor.getValue().run();
        verify(callback).lockUnavailable(lock3);
    }

    /**
     * Tests checkExpired(), where the lock is removed from the map between invoking
     * expired() and compute(). Should cause "null" to be returned by compute().
     *
     * @throws InterruptedException if the test is interrupted
     */
    @Test
    public void testCheckExpired_LockDeleted() throws InterruptedException {
        feature = new MyLockingFeature() {
            @Override
            protected SimpleLock makeLock(LockState waiting, String resourceId, Object ownerInfo, int holdSec,
                            LockCallback callback) {
                return new SimpleLock(waiting, resourceId, ownerInfo, holdSec, callback) {
                    @Override
                    public boolean expired(long currentMs) {
                        // remove the lock from the map
                        free();
                        return true;
                    }
                };
            }
        };

        feature.afterStart((PolicyEngine) null);

        feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);
        invokeCallback(1);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(exsvc).scheduleWithFixedDelay(captor.capture(), anyLong(), anyLong(), any());

        Runnable checker = captor.getValue();

        checker.run();

        // lock should now be gone and we should be able to get another
        feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);
        invokeCallback(2);

        // should have succeeded twice
        verify(callback, times(2)).lockAvailable(any());

        // lock should not be available now
        feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);
        invokeCallback(3);
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
    public void testCheckExpired_LockReplaced() throws InterruptedException {
        feature = new MyLockingFeature() {
            private boolean madeLock = false;

            @Override
            protected SimpleLock makeLock(LockState waiting, String resourceId, Object ownerInfo, int holdSec,
                            LockCallback callback) {
                if (madeLock) {
                    return new SimpleLock(waiting, resourceId, ownerInfo, holdSec, callback);
                }

                madeLock = true;

                return new SimpleLock(waiting, resourceId, ownerInfo, holdSec, callback) {
                    @Override
                    public boolean expired(long currentMs) {
                        // remove the lock from the map and add a new lock
                        free();
                        feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);

                        return true;
                    }
                };
            }
        };

        feature.afterStart((PolicyEngine) null);

        feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);
        invokeCallback(1);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(exsvc).scheduleWithFixedDelay(captor.capture(), anyLong(), anyLong(), any());

        Runnable checker = captor.getValue();

        checker.run();

        // lock should not be available now
        feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);
        invokeCallback(3);
        verify(callback).lockUnavailable(any());
    }

    @Test
    public void testMakeThreadPool() {
        // use a real feature
        feature = new SimpleLockingFeature();

        // load properties
        feature.afterStart((PolicyEngine) null);

        // should create thread pool
        feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);

        // should shut down thread pool
        feature.beforeShutdown((PolicyEngine) null);
    }

    @Test
    public void testSimpleLockSimpleLock() {
        SimpleLock lock = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);
        assertEquals(RESOURCE, lock.getResourceId());
        assertSame(owner, lock.getOwnerInfo());
        assertEquals(OWNER_KEY, lock.getOwnerKey());
        assertSame(callback, lock.getCallback());
        assertEquals(HOLD_SEC, lock.getHoldSec());

        assertNull(feature.lock(RESOURCE, null, HOLD_SEC, callback, false).getOwnerKey());

        assertThatIllegalArgumentException().isThrownBy(() -> feature.lock(RESOURCE, owner, -1, callback, false))
                        .withMessageContaining("holdSec");
    }

    @Test
    public void testSimpleLockExpired() {
        SimpleLock lock = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);
        lock.grant();

        assertFalse(lock.expired(testTime.getMillis()));
        assertFalse(lock.expired(testTime.getMillis() + HOLD_SEC - 1));
        assertTrue(lock.expired(testTime.getMillis() + HOLD_SEC));
    }

    @Test
    public void testSimpleLockFree() {
        SimpleLock lock = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);

        // lock2 should be denied
        SimpleLock lock2 = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);
        invokeCallback(2);
        verify(callback, never()).lockAvailable(lock2);
        verify(callback).lockUnavailable(lock2);

        // lock2 was denied, so nothing new should happen when freed
        assertFalse(lock2.free());
        invokeCallback(2);

        // force lock2 to be active - still nothing should happen
        Whitebox.setInternalState(lock2, "state", LockState.ACTIVE);
        assertFalse(lock2.free());
        invokeCallback(2);

        // now free the first lock
        assertTrue(lock.free());
        assertEquals(LockState.UNAVAILABLE, lock.getState());

        // should be able to get the lock now
        SimpleLock lock3 = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);
        assertTrue(lock3.isActive());
    }

    @Test
    public void testSimpleLockExtend() {
        SimpleLock lock = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);

        // lock2 should be denied
        SimpleLock lock2 = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);
        invokeCallback(2);
        verify(callback, never()).lockAvailable(lock2);
        verify(callback).lockUnavailable(lock2);

        // lock2 will still be denied
        lock2.extend(HOLD_SEC, callback);
        invokeCallback(3);
        verify(callback, times(2)).lockUnavailable(lock2);

        // force lock2 to be active - should still be denied
        Whitebox.setInternalState(lock2, "state", LockState.ACTIVE);
        lock2.extend(HOLD_SEC, callback);
        invokeCallback(4);
        verify(callback, times(3)).lockUnavailable(lock2);

        assertThatIllegalArgumentException().isThrownBy(() -> lock.extend(-1, callback))
                        .withMessageContaining("holdSec");

        // now extend the first lock
        lock.extend(HOLD_SEC2, callback);
        assertEquals(HOLD_SEC2, lock.getHoldSec());
        assertEquals(testTime.getMillis() + HOLD_SEC2, lock.getHoldUntilMs());
        invokeCallback(5);
        verify(callback).lockAvailable(lock);
        verify(callback, never()).lockUnavailable(lock);
    }

    @Test
    public void testSimpleLockExtendToString() {
        String text = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false).toString();
        assertNotNull(text);
        assertThat(text).contains("holdUntil").doesNotContain("ownerInfo").doesNotContain("callback");
    }

    @Test
    public void testMultiThreaded() throws InterruptedException {
        Whitebox.setInternalState(SimpleLockingFeature.class, TIME_FIELD, testTime);
        feature = new SimpleLockingFeature();
        feature.afterStart((PolicyEngine) null);

        Properties props = new Properties();
        props.setProperty(SimpleLockingFeature.EXTRACTOR_PREFIX + MyOwner.class.getName(), "v.key");
        assertNull(feature.beforeCreate(null, props));

        List<MyThread> threads = new ArrayList<>(MAX_THREADS);
        for (int x = 0; x < MAX_THREADS; ++x) {
            threads.add(new MyThread());
        }

        threads.forEach(Thread::start);

        for (MyThread thread : threads) {
            thread.join(5000);
            assertFalse(thread.isAlive());
        }

        for (MyThread thread : threads) {
            if (thread.err != null) {
                throw thread.err;
            }
        }

        assertTrue(nsuccesses.get() > 0);
    }

    private void invokeCallback(int nexpected) {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(exsvc, times(nexpected)).execute(captor.capture());

        if (nexpected > 0) {
            captor.getAllValues().get(nexpected - 1).run();
        }
    }

    private class MyLockingFeature extends SimpleLockingFeature {

        @Override
        protected ScheduledExecutorService makeThreadPool(int nthreads) {
            return exsvc;
        }
    }

    @Getter
    public static class MyOwner {
        private String key = OWNER_KEY;
    }

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

                Lock lock = feature.lock(RESOURCE, owner, HOLD_SEC, cb, false);

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

                lock.free();
                assertFalse(lock.isActive());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted", e);
            }
        }
    }
}
