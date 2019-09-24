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

package org.onap.policy.drools.features.lock;

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
import org.onap.policy.drools.core.lock.PolicyResourceLockFeatureApi;
import org.onap.policy.drools.core.lock.PolicyResourceLockManager;
import org.onap.policy.drools.features.lock.SimpleLockingFeature.SimpleLock;
import org.onap.policy.drools.persistence.SystemPersistenceConstants;
import org.onap.policy.drools.system.PolicyEngine;
import org.onap.policy.drools.system.PolicyEngineConstants;
import org.powermock.reflect.Whitebox;

public class SimpleLockingFeatureTest {
    private static final String POLICY_ENGINE_EXECUTOR_FIELD = "exsvc";
    private static final String TIME_FIELD = "currentTime";
    private static final String OWNER_KEY = "my key";
    private static final String RESOURCE = "my resource";
    private static final String RESOURCE2 = "my resource #2";
    private static final String RESOURCE3 = "my resource #3";
    private static final int HOLD_SEC = 100;
    private static final int HOLD_SEC2 = 120;
    private static final int MAX_THREADS = 10;
    private static final int MAX_LOOPS = 50;

    private static CurrentTime saveTime;
    private static ScheduledExecutorService saveExec;
    private static ScheduledExecutorService realExec;

    private TestTime testTime;
    private Object owner;
    private AtomicInteger nactive;
    private AtomicInteger nsuccesses;
    private SimpleLockingFeature feature;

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
        saveTime = Whitebox.getInternalState(SimpleLockingFeature.class, TIME_FIELD);
        saveExec = Whitebox.getInternalState(PolicyEngineConstants.getManager(), POLICY_ENGINE_EXECUTOR_FIELD);

        realExec = Executors.newScheduledThreadPool(3);
        Whitebox.setInternalState(PolicyEngineConstants.getManager(), POLICY_ENGINE_EXECUTOR_FIELD, realExec);
    }

    /**
     * Restores static fields.
     */
    @AfterClass
    public static void tearDownAfterClass() {
        Whitebox.setInternalState(SimpleLockingFeature.class, TIME_FIELD, saveTime);
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

    /**
     * Verifies that the SimpleLockingFeature is included in the list of providers.
     */
    @Test
    public void testLoaded() {
        assertTrue(new MyManager().theProviders.stream().anyMatch(feature -> feature instanceof SimpleLockingFeature));
    }

    @Test
    public void testGetSequenceNumber() {
        assertEquals(990, feature.getSequenceNumber());
    }

    /**
     * Tests afterStart(), when getFeatureProperties() throws a runtime exception.
     */
    @Test
    public void testAfterStartEx() {
        // return properties containing an invalid value
        feature = new MyLockingFeature() {
            @Override
            protected Properties getFeatureProperties() {
                Properties props = new Properties();
                props.setProperty(SimpleLockingProperties.EXPIRE_CHECK_SEC, "abc");

                return props;
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
        assertNotNull(getLock(RESOURCE, owner, HOLD_SEC, callback, false));
    }

    @Test
    public void testAfterStop() {
        // with thread pool
        assertFalse(feature.afterStop((PolicyEngine) null));
        verify(future).cancel(false);

        // with no thread pool
        assertFalse(feature.afterStop((PolicyEngine) null));

        // no more invocations
        verify(future).cancel(false);
    }

    @Test
    public void testBeforeLock() {
        // this lock should be granted immediately
        SimpleLock lock = getLock(RESOURCE, owner, HOLD_SEC, callback, false);
        assertTrue(lock.isActive());
        assertEquals(testTime.getMillis() + HOLD_SEC, lock.getHoldUntilMs());

        invokeCallback(1);

        verify(callback).lockAvailable(lock);
        verify(callback, never()).lockUnavailable(lock);


        // this time it should be busy
        Lock lock2 = feature.beforeCreateLock(RESOURCE, owner, HOLD_SEC, callback, false);
        assertFalse(lock2.isActive());
        assertTrue(lock2.isUnavailable());

        invokeCallback(2);

        verify(callback, never()).lockAvailable(lock2);
        verify(callback).lockUnavailable(lock2);

        // should have been no change to the original lock
        assertTrue(lock.isActive());
        verify(callback).lockAvailable(lock);
        verify(callback, never()).lockUnavailable(lock);

        // should work with "true" value also
        Lock lock3 = feature.beforeCreateLock(RESOURCE2, owner, HOLD_SEC, callback, true);
        assertTrue(lock3.isActive());
        invokeCallback(3);
        verify(callback).lockAvailable(lock3);
        verify(callback, never()).lockUnavailable(lock3);
    }

    /**
     * Tests lock() when the feature is not the latest instance.
     */
    @Test
    public void testBeforeLockNotLatestInstance() {
        SimpleLockingFeature.setLatestInstance(null);

        Lock lock = feature.beforeCreateLock(RESOURCE, owner, HOLD_SEC, callback, false);
        assertTrue(lock.isUnavailable());
        verify(callback, never()).lockAvailable(any());
        verify(callback).lockUnavailable(lock);
    }

    @Test
    public void testAfterLock() {
        Lock lock = feature.beforeCreateLock(RESOURCE, owner, HOLD_SEC, callback, false);

        assertFalse(feature.afterCreateLock(lock, HOLD_SEC, callback, false));
    }

    @Test
    public void testCheckExpired() throws InterruptedException {
        final SimpleLock lock = getLock(RESOURCE, owner, HOLD_SEC, callback, false);
        final SimpleLock lock2 = getLock(RESOURCE2, owner, HOLD_SEC, callback, false);
        final SimpleLock lock3 = getLock(RESOURCE3, owner, HOLD_SEC2, callback, false);

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
        assertTrue(feature.beforeCreateLock(RESOURCE, owner, HOLD_SEC + HOLD_SEC2, callback, false).isActive());
        assertTrue(feature.beforeCreateLock(RESOURCE2, owner, HOLD_SEC + HOLD_SEC2, callback, false).isActive());

        // lock is still busy on the last resource
        assertFalse(feature.beforeCreateLock(RESOURCE3, owner, HOLD_SEC + HOLD_SEC2, callback, false).isActive());

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
    public void testCheckExpiredLockDeleted() throws InterruptedException {
        feature = new MyLockingFeature() {
            @Override
            protected SimpleLock makeLock(LockState waiting, String resourceId, Object ownerInfo, int holdSec,
                            LockCallback callback) {
                return new SimpleLock(waiting, resourceId, ownerInfo, holdSec, callback, feature) {
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

        feature.afterStart((PolicyEngine) null);

        feature.beforeCreateLock(RESOURCE, owner, HOLD_SEC, callback, false);
        invokeCallback(1);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(exsvc).scheduleWithFixedDelay(captor.capture(), anyLong(), anyLong(), any());

        Runnable checker = captor.getValue();

        checker.run();

        // lock should now be gone and we should be able to get another
        feature.beforeCreateLock(RESOURCE, owner, HOLD_SEC, callback, false);
        invokeCallback(2);

        // should have succeeded twice
        verify(callback, times(2)).lockAvailable(any());

        // lock should not be available now
        feature.beforeCreateLock(RESOURCE, owner, HOLD_SEC, callback, false);
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
    public void testCheckExpiredLockReplaced() throws InterruptedException {
        feature = new MyLockingFeature() {
            private boolean madeLock = false;

            @Override
            protected SimpleLock makeLock(LockState waiting, String resourceId, Object ownerInfo, int holdSec,
                            LockCallback callback) {
                if (madeLock) {
                    return new SimpleLock(waiting, resourceId, ownerInfo, holdSec, callback, feature);
                }

                madeLock = true;

                return new SimpleLock(waiting, resourceId, ownerInfo, holdSec, callback, feature) {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public boolean expired(long currentMs) {
                        // remove the lock from the map and add a new lock
                        free();
                        feature.beforeCreateLock(RESOURCE, owner, HOLD_SEC, callback, false);

                        return true;
                    }
                };
            }
        };

        feature.afterStart((PolicyEngine) null);

        feature.beforeCreateLock(RESOURCE, owner, HOLD_SEC, callback, false);
        invokeCallback(1);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(exsvc).scheduleWithFixedDelay(captor.capture(), anyLong(), anyLong(), any());

        Runnable checker = captor.getValue();

        checker.run();

        // lock should not be available now
        feature.beforeCreateLock(RESOURCE, owner, HOLD_SEC, callback, false);
        invokeCallback(3);
        verify(callback).lockUnavailable(any());
    }

    @Test
    public void testGetThreadPool() {
        // use a real feature
        feature = new SimpleLockingFeature();

        // load properties
        feature.afterStart((PolicyEngine) null);

        // should create thread pool
        feature.beforeCreateLock(RESOURCE, owner, HOLD_SEC, callback, false);

        // should shut down thread pool
        feature.afterStop((PolicyEngine) null);
    }

    @Test
    public void testSimpleLockNoArgs() {
        SimpleLock lock = new SimpleLock();
        assertNull(lock.getResourceId());
        assertNull(lock.getOwnerInfo());
        assertNull(lock.getOwnerKey());
        assertNull(lock.getCallback());
        assertEquals(0, lock.getHoldSec());

        assertEquals(0, lock.getHoldUntilMs());
    }

    @Test
    public void testSimpleLockSimpleLock() {
        SimpleLock lock = getLock(RESOURCE, owner, HOLD_SEC, callback, false);
        assertEquals(RESOURCE, lock.getResourceId());
        assertSame(owner, lock.getOwnerInfo());
        assertEquals(OWNER_KEY, lock.getOwnerKey());
        assertSame(callback, lock.getCallback());
        assertEquals(HOLD_SEC, lock.getHoldSec());

        assertNull(feature.beforeCreateLock(RESOURCE, null, HOLD_SEC, callback, false).getOwnerKey());

        assertThatIllegalArgumentException()
                        .isThrownBy(() -> feature.beforeCreateLock(RESOURCE, owner, -1, callback, false))
                        .withMessageContaining("holdSec");
    }

    @Test
    public void testSimpleLockSerializable() throws Exception {
        SimpleLock lock = getLock(RESOURCE, owner, HOLD_SEC, callback, false);
        lock = roundTrip(lock);

        assertTrue(lock.isActive());

        assertEquals(RESOURCE, lock.getResourceId());
        assertNull(lock.getOwnerInfo());
        assertEquals(OWNER_KEY, lock.getOwnerKey());
        assertNull(lock.getCallback());
        assertEquals(HOLD_SEC, lock.getHoldSec());
    }

    @Test
    public void testSimpleLockExpired() {
        SimpleLock lock = getLock(RESOURCE, owner, HOLD_SEC, callback, false);
        lock.grant();

        assertFalse(lock.expired(testTime.getMillis()));
        assertFalse(lock.expired(testTime.getMillis() + HOLD_SEC - 1));
        assertTrue(lock.expired(testTime.getMillis() + HOLD_SEC));
    }

    /**
     * Tests grant() when the lock is already unavailable.
     */
    @Test
    public void testSimpleLockGrantUnavailable() {
        SimpleLock lock = getLock(RESOURCE, owner, HOLD_SEC, callback, false);
        lock.setState(LockState.UNAVAILABLE);
        lock.grant();

        assertTrue(lock.isUnavailable());
        verify(callback, never()).lockAvailable(any());
        verify(callback, never()).lockUnavailable(any());
    }

    @Test
    public void testSimpleLockFree() {
        final SimpleLock lock = getLock(RESOURCE, owner, HOLD_SEC, callback, false);

        // lock2 should be denied
        SimpleLock lock2 = getLock(RESOURCE, owner, HOLD_SEC, callback, false);
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
        SimpleLock lock3 = getLock(RESOURCE, owner, HOLD_SEC, callback, false);
        assertTrue(lock3.isActive());
    }

    /**
     * Tests that free() works on a serialized lock with a new feature.
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void testSimpleLockFreeSerialized() throws Exception {
        SimpleLock lock = getLock(RESOURCE, owner, HOLD_SEC, callback, false);

        feature = new MyLockingFeature();
        feature.afterStart((PolicyEngine) null);

        lock = roundTrip(lock);
        assertTrue(lock.free());
        assertTrue(lock.isUnavailable());
    }

    /**
     * Tests free() on a serialized lock without a feature.
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void testSimpleLockFreeNoFeature() throws Exception {
        SimpleLock lock = getLock(RESOURCE, owner, HOLD_SEC, callback, false);

        SimpleLockingFeature.setLatestInstance(null);

        lock = roundTrip(lock);
        assertFalse(lock.free());
        assertTrue(lock.isUnavailable());
    }

    @Test
    public void testSimpleLockExtend() {
        final SimpleLock lock = getLock(RESOURCE, owner, HOLD_SEC, callback, false);

        // lock2 should be denied
        SimpleLock lock2 = getLock(RESOURCE, owner, HOLD_SEC, callback, false);
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

    /**
     * Tests that extend() works on a serialized lock with a new feature.
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void testSimpleLockExtendSerialized() throws Exception {
        SimpleLock lock = getLock(RESOURCE, owner, HOLD_SEC, callback, false);

        feature = new MyLockingFeature();
        feature.afterStart((PolicyEngine) null);

        lock = roundTrip(lock);
        LockCallback scallback = mock(LockCallback.class);

        lock.extend(HOLD_SEC, scallback);
        assertTrue(lock.isActive());

        invokeCallback(1);
        verify(scallback).lockAvailable(lock);
        verify(scallback, never()).lockUnavailable(lock);
    }

    /**
     * Tests extend() on a serialized lock without a feature.
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void testSimpleLockExtendNoFeature() throws Exception {
        SimpleLock lock = getLock(RESOURCE, owner, HOLD_SEC, callback, false);

        SimpleLockingFeature.setLatestInstance(null);

        lock = roundTrip(lock);
        LockCallback scallback = mock(LockCallback.class);

        lock.extend(HOLD_SEC, scallback);
        assertTrue(lock.isUnavailable());

        invokeCallback(1);
        verify(scallback, never()).lockAvailable(lock);
        verify(scallback).lockUnavailable(lock);
    }

    @Test
    public void testSimpleLockToString() {
        String text = feature.beforeCreateLock(RESOURCE, owner, HOLD_SEC, callback, false).toString();
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

    private SimpleLock getLock(String resource, Object owner, int holdSec, LockCallback callback, boolean waitForLock) {
        return (SimpleLock) feature.beforeCreateLock(resource, owner, holdSec, callback, waitForLock);
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
     * Feature that uses <i>exsvc</i> to execute requests.
     */
    private class MyLockingFeature extends SimpleLockingFeature {
        public MyLockingFeature() {
            exsvc = mock(ScheduledExecutorService.class);

            when(exsvc.scheduleWithFixedDelay(any(), anyLong(), anyLong(), any())).thenAnswer(answer -> {
                return future;
            });
        }

        @Override
        protected ScheduledExecutorService getThreadPool() {
            return exsvc;
        }
    }

    @Getter
    public static class MyOwner {
        private String key = OWNER_KEY;
    }

    private static class MyManager extends PolicyResourceLockManager {
        private List<PolicyResourceLockFeatureApi> theProviders;

        MyManager() {
            theProviders = getProviders();
        }
    }

    /**
     * Thread used with the multi-threaded test. It repeatedly attempts to get a lock,
     * extend it, and then unlock it.
     */
    private class MyThread extends Thread {
        AssertionError err = null;
        MyOwner myOwner;

        public MyThread() {
            setDaemon(true);

            myOwner = new MyOwner();
            myOwner.key = this.getName();
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

                Lock lock = feature.beforeCreateLock(RESOURCE, myOwner, HOLD_SEC, cb, false);

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
