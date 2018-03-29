/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018 AT&T Intellectual Property. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.onap.policy.drools.core.lock.TestUtils.expectException;
import java.util.LinkedList;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.drools.core.lock.Lock.RemoveResult;
import org.onap.policy.drools.core.lock.PolicyResourceLockFeatureAPI.Callback;

public class PlainLockManagerTest {

    private static final String NULL_RESOURCE_ID = "null resourceId";
    private static final String NULL_OWNER = "null owner";

    private static final String RESOURCE_A = "resource.a";
    private static final String RESOURCE_B = "resource.b";
    private static final String RESOURCE_C = "resource.c";

    private static final String OWNER1 = "owner.one";
    private static final String OWNER2 = "owner.two";
    private static final String OWNER3 = "owner.three";

    private Callback callback1;
    private Callback callback2;
    private Callback callback3;

    private LockRequestFuture fut;
    private PlainLockManager mgr;

    @Before
    public void setUp() {
        callback1 = mock(Callback.class);
        callback2 = mock(Callback.class);
        callback3 = mock(Callback.class);

        mgr = new PlainLockManager();
    }

    @Test
    public void testLock() throws Exception {
        fut = mgr.lock(RESOURCE_A, OWNER1, callback1);

        assertTrue(fut.isDone());
        assertTrue(fut.get());

        assertTrue(mgr.isLocked(RESOURCE_A));
        assertTrue(mgr.isLockedBy(RESOURCE_A, OWNER1));
        assertFalse(mgr.isLocked(RESOURCE_B));
        assertFalse(mgr.isLockedBy(RESOURCE_A, OWNER2));

        // null callback - not locked yet
        fut = mgr.lock(RESOURCE_C, OWNER3, null);
        assertTrue(fut.isDone());
        assertTrue(fut.get());

        // null callback - already locked
        fut = mgr.lock(RESOURCE_A, OWNER3, null);
        assertTrue(fut.isDone());
        assertFalse(fut.get());
    }

    @Test(expected = IllegalStateException.class)
    public void testLock_LockedSameOwner() {
        mgr.lock(RESOURCE_A, OWNER1, callback1);

        mgr.lock(RESOURCE_A, OWNER1, callback2);
    }

    @Test
    public void testLock_LockedNoCallback() throws Exception {
        mgr.lock(RESOURCE_A, OWNER1, callback1);

        fut = mgr.lock(RESOURCE_A, OWNER2, null);
        assertTrue(fut.isDone());
        assertFalse(fut.get());
    }

    @Test
    public void testLock_LockedNeitherOwnerNorInQueue() {
        mgr.lock(RESOURCE_A, OWNER1, callback1);

        fut = mgr.lock(RESOURCE_A, OWNER2, callback2);
        assertFalse(fut.isDone());

        verify(callback1, never()).set(anyBoolean());
        verify(callback2, never()).set(anyBoolean());
    }

    @Test(expected = IllegalStateException.class)
    public void testLock_InQueue() {
        mgr.lock(RESOURCE_A, OWNER1, callback1);

        mgr.lock(RESOURCE_A, OWNER2, callback2);

        // already in the queue - this should throw an exception
        mgr.lock(RESOURCE_A, OWNER2, callback2);
    }

    @Test
    public void testLock_ArgEx() {
        IllegalArgumentException ex =
                        expectException(IllegalArgumentException.class, xxx -> mgr.lock(null, OWNER1, callback1));
        assertEquals(NULL_RESOURCE_ID, ex.getMessage());

        ex = expectException(IllegalArgumentException.class, xxx -> mgr.lock(RESOURCE_A, null, callback1));
        assertEquals(NULL_OWNER, ex.getMessage());

        // this should not throw an exception
        mgr.lock(RESOURCE_A, OWNER1, null);
    }

    @Test
    public void testUnlock() throws Exception {
        mgr.lock(RESOURCE_A, OWNER1, null);
        fut = mgr.lock(RESOURCE_A, OWNER2, callback2);

        assertFalse(fut.isDone());

        // unlock it
        assertTrue(mgr.unlock(RESOURCE_A, OWNER1));

        verify(callback2).set(true);
        assertTrue(fut.isDone());
        assertTrue(fut.get());
    }

    @Test
    public void testUnlock_ArgEx() {
        IllegalArgumentException ex = expectException(IllegalArgumentException.class, xxx -> mgr.unlock(null, OWNER1));
        assertEquals(NULL_RESOURCE_ID, ex.getMessage());

        ex = expectException(IllegalArgumentException.class, xxx -> mgr.unlock(RESOURCE_A, null));
        assertEquals(NULL_OWNER, ex.getMessage());
    }

    @Test
    public void testUnlock_NotLocked() {
        assertFalse(mgr.unlock(RESOURCE_A, OWNER1));
    }

    @Test
    public void testUnlock_Owner_EmptyQueue() {
        mgr.lock(RESOURCE_A, OWNER1, callback1);

        assertTrue(mgr.unlock(RESOURCE_A, OWNER1));

        // repeated call should now be false
        assertFalse(mgr.unlock(RESOURCE_A, OWNER1));

        // should not own the lock
        assertFalse(mgr.isLocked(RESOURCE_A));
        assertFalse(mgr.isLockedBy(RESOURCE_A, OWNER1));
    }

    @Test
    public void testUnlock_Owner_NotEmptyQueue() throws Exception {
        mgr.lock(RESOURCE_A, OWNER1, callback1);
        fut = mgr.lock(RESOURCE_A, OWNER2, callback2);

        assertTrue(mgr.unlock(RESOURCE_A, OWNER1));

        verify(callback2).set(true);
        assertTrue(fut.get(0, TimeUnit.SECONDS));

        // repeated call should now be false
        assertFalse(mgr.unlock(RESOURCE_A, OWNER1));

        // owner1 should not own the lock
        assertFalse(mgr.isLockedBy(RESOURCE_A, OWNER1));

        // owner2 should own the lock
        assertTrue(mgr.isLocked(RESOURCE_A));
        assertTrue(mgr.isLockedBy(RESOURCE_A, OWNER2));

        // owner2 should be able to unlock it
        assertTrue(mgr.unlock(RESOURCE_A, OWNER2));

        // nowbody owns the lock now
        assertFalse(mgr.isLocked(RESOURCE_A));
        assertFalse(mgr.isLockedBy(RESOURCE_A, OWNER1));
        assertFalse(mgr.isLockedBy(RESOURCE_A, OWNER2));
    }

    @Test
    public void testUnlock_InQueueNothingElse() {
        mgr.lock(RESOURCE_A, OWNER1, callback1);
        mgr.lock(RESOURCE_A, OWNER2, callback2);

        // doesn't own the lock so unlock should return false
        assertFalse(mgr.unlock(RESOURCE_A, OWNER2));
        assertFalse(mgr.isLockedBy(RESOURCE_A, OWNER2));

        verify(callback1, never()).set(anyBoolean());
        verify(callback2, never()).set(anyBoolean());

        // after owner1 unlocks, it should be completely unlocked
        assertTrue(mgr.unlock(RESOURCE_A, OWNER1));
        assertFalse(mgr.isLocked(RESOURCE_A));
    }

    @Test
    public void testUnlock_InQueueMore() {
        mgr.lock(RESOURCE_A, OWNER1, callback1);
        mgr.lock(RESOURCE_A, OWNER2, callback2);
        mgr.lock(RESOURCE_A, OWNER3, callback3);

        // doesn't own the lock so unlock should return false
        assertFalse(mgr.unlock(RESOURCE_A, OWNER2));
        assertFalse(mgr.isLockedBy(RESOURCE_A, OWNER2));

        verify(callback1, never()).set(anyBoolean());
        verify(callback2, never()).set(anyBoolean());
        verify(callback3, never()).set(anyBoolean());

        // after owner1 unlocks, it should be owned by owner3
        assertTrue(mgr.unlock(RESOURCE_A, OWNER1));
        assertTrue(mgr.isLocked(RESOURCE_A));
        assertTrue(mgr.isLockedBy(RESOURCE_A, OWNER3));

        // after owner3 unlocks, it should be completely unlocked
        assertTrue(mgr.unlock(RESOURCE_A, OWNER3));
        assertFalse(mgr.isLocked(RESOURCE_A));

        verify(callback1, never()).set(anyBoolean());
        verify(callback2, never()).set(anyBoolean());
        verify(callback3).set(true);
    }

    @Test
    public void testWasOwner() {
        assertTrue(mgr.wasOwner(RemoveResult.RELOCKED));
        assertTrue(mgr.wasOwner(RemoveResult.UNLOCKED));

        assertFalse(mgr.wasOwner(RemoveResult.REMOVED));
        assertFalse(mgr.wasOwner(RemoveResult.NOT_FOUND));
    }

    @Test
    public void testIsLocked() {
        assertFalse(mgr.isLocked(RESOURCE_A));

        mgr.lock(RESOURCE_A, OWNER1, callback1);
        mgr.lock(RESOURCE_B, OWNER1, callback2);

        mgr.lock(RESOURCE_A, OWNER2, callback3);

        assertTrue(mgr.isLocked(RESOURCE_A));
        assertTrue(mgr.isLocked(RESOURCE_B));
        assertFalse(mgr.isLocked(RESOURCE_C));

        // unlock from first owner
        mgr.unlock(RESOURCE_A, OWNER1);
        assertTrue(mgr.isLocked(RESOURCE_A));
        assertTrue(mgr.isLocked(RESOURCE_B));
        assertFalse(mgr.isLocked(RESOURCE_C));

        // unlock from second owner
        mgr.unlock(RESOURCE_A, OWNER2);
        assertFalse(mgr.isLocked(RESOURCE_A));
        assertTrue(mgr.isLocked(RESOURCE_B));
        assertFalse(mgr.isLocked(RESOURCE_C));
    }

    @Test
    public void testIsLockedBy() {
        assertFalse(mgr.isLockedBy(RESOURCE_A, OWNER1));

        mgr.lock(RESOURCE_A, OWNER1, callback1);
        mgr.lock(RESOURCE_A, OWNER2, callback2);
        mgr.lock(RESOURCE_A, OWNER3, callback3);

        assertFalse(mgr.isLockedBy(RESOURCE_B, OWNER1));

        assertTrue(mgr.isLockedBy(RESOURCE_A, OWNER1));
        assertFalse(mgr.isLockedBy(RESOURCE_A, OWNER2));
        assertFalse(mgr.isLockedBy(RESOURCE_A, OWNER3));

        // unlock from the first owner
        mgr.unlock(RESOURCE_A, OWNER1);
        assertFalse(mgr.isLockedBy(RESOURCE_A, OWNER1));
        assertTrue(mgr.isLockedBy(RESOURCE_A, OWNER2));
        assertFalse(mgr.isLockedBy(RESOURCE_A, OWNER3));

        // unlock from the second owner
        mgr.unlock(RESOURCE_A, OWNER2);
        assertFalse(mgr.isLockedBy(RESOURCE_A, OWNER1));
        assertFalse(mgr.isLockedBy(RESOURCE_A, OWNER2));
        assertTrue(mgr.isLockedBy(RESOURCE_A, OWNER3));

        // unlock from last owner
        mgr.unlock(RESOURCE_A, OWNER3);
        assertFalse(mgr.isLockedBy(RESOURCE_A, OWNER1));
        assertFalse(mgr.isLockedBy(RESOURCE_A, OWNER2));
        assertFalse(mgr.isLockedBy(RESOURCE_A, OWNER3));
    }

    @Test
    public void testIsLockedBy_ArgEx() {
        IllegalArgumentException ex =
                        expectException(IllegalArgumentException.class, xxx -> mgr.isLockedBy(null, OWNER1));
        assertEquals(NULL_RESOURCE_ID, ex.getMessage());

        ex = expectException(IllegalArgumentException.class, xxx -> mgr.isLockedBy(RESOURCE_A, null));
        assertEquals(NULL_OWNER, ex.getMessage());
    }

    @Test
    public void testIsLockedBy_NotLocked() {
        mgr.lock(RESOURCE_A, OWNER1, null);

        // different resource, thus no lock
        assertFalse(mgr.isLockedBy(RESOURCE_B, OWNER1));
    }

    @Test
    public void testIsLockedBy_LockedButNotOwner() {
        mgr.lock(RESOURCE_A, OWNER1, null);

        // different owner
        assertFalse(mgr.isLockedBy(RESOURCE_A, OWNER2));
    }

    @Test
    public void testMapFutureCancel() {
        mgr.lock(RESOURCE_A, OWNER1, callback1);
        fut = mgr.lock(RESOURCE_A, OWNER2, callback2);

        fut.cancel(false);
        assertTrue(fut.isDone());

        // after owner1 unlocks, it should be completely unlocked
        assertTrue(mgr.unlock(RESOURCE_A, OWNER1));
        assertFalse(mgr.isLocked(RESOURCE_A));
    }

    @Test
    public void testMapFutureCancel_InQueueNothingElse() {
        mgr.lock(RESOURCE_A, OWNER1, callback1);
        fut = mgr.lock(RESOURCE_A, OWNER2, callback2);

        fut.cancel(false);
        assertTrue(fut.isDone());

        // after owner1 unlocks, it should be completely unlocked
        assertTrue(mgr.unlock(RESOURCE_A, OWNER1));
        assertFalse(mgr.isLocked(RESOURCE_A));
    }

    @Test
    public void testMapFutureCancel_InQueueMore() {
        mgr.lock(RESOURCE_A, OWNER1, callback1);
        fut = mgr.lock(RESOURCE_A, OWNER2, callback2);
        mgr.lock(RESOURCE_A, OWNER3, callback3);

        fut.cancel(false);
        assertTrue(fut.isDone());

        // after owner1 unlocks, it should be locked by owner3
        assertTrue(mgr.unlock(RESOURCE_A, OWNER1));
        assertTrue(mgr.unlock(RESOURCE_A, OWNER3));
    }

    @Test
    public void testMapFutureCancel_NotInQueue() {
        // this should be immediately acquired
        fut = mgr.lock(RESOURCE_A, OWNER1, callback1);
        assertTrue(fut.isDone());
        assertFalse(fut.cancel(true));

        // cancel should have no bearing on existing lock
        assertTrue(mgr.isLocked(RESOURCE_A));

        // no callback - this should be immediately denied
        fut = mgr.lock(RESOURCE_A, OWNER2, null);
        assertTrue(fut.isDone());
        assertFalse(fut.cancel(false));

        // after owner1 unlocks, it should be completely unlocked
        assertTrue(mgr.unlock(RESOURCE_A, OWNER1));
        assertFalse(mgr.isLocked(RESOURCE_A));
    }

    @Test
    public void testMultiThreaded() throws InterruptedException {
        int nthreads = 10;
        int nlocks = 100;

        LinkedList<Thread> threads = new LinkedList<>();

        String[] resources = {RESOURCE_A, RESOURCE_B};

        AtomicInteger nsucc = new AtomicInteger(0);
        AtomicInteger nfail = new AtomicInteger(0);

        CountDownLatch stopper = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(nthreads);

        for (int x = 0; x < nthreads; ++x) {
            String owner = "owner." + x;

            Thread t = new Thread() {
                @Override
                public void run() {

                    for (int y = 0; y < nlocks; ++y) {
                        String res = resources[y % resources.length];

                        // counts successes and failures
                        Callback callback = new Callback() {
                            private AtomicBoolean invoked = new AtomicBoolean(false);

                            @Override
                            public void set(boolean locked) {
                                if (invoked.compareAndSet(false, true)) {
                                    if (locked) {
                                        nsucc.incrementAndGet();

                                    } else {
                                        nfail.incrementAndGet();
                                    }
                                }
                            }
                        };

                        try {
                            LockRequestFuture fut = mgr.lock(res, owner, callback);

                            // set the callback, in case it hasn't been set yet
                            callback.set(fut.get());

                            // do some "work"
                            stopper.await(1L, TimeUnit.MILLISECONDS);

                            mgr.unlock(res, owner);

                        } catch (CancellationException e) {
                            nfail.incrementAndGet();

                        } catch (InterruptedException expected) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }

                    completed.countDown();
                }
            };

            t.setDaemon(true);
            threads.add(t);
        }

        // start the threads
        for (Thread t : threads) {
            t.start();
        }

        // wait for them to complete
        completed.await(5000L, TimeUnit.SECONDS);

        // stop the threads from sleeping
        stopper.countDown();

        completed.await(1L, TimeUnit.SECONDS);

        // interrupt those that are still alive
        for (Thread t : threads) {
            if (t.isAlive()) {
                t.interrupt();
            }
        }

        assertEquals(nthreads * nlocks, nsucc.get());
        assertEquals(0, nfail.get());
    }

}
