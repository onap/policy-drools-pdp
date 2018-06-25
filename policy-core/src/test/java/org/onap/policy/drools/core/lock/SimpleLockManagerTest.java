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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.onap.policy.drools.core.lock.TestUtils.expectException;
import java.util.LinkedList;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.onap.policy.common.utils.time.CurrentTime;
import org.onap.policy.common.utils.time.TestTime;
import org.onap.policy.drools.core.lock.SimpleLockManager.Data;
import org.powermock.reflect.Whitebox;

public class SimpleLockManagerTest {

    private static final String NULL_RESOURCE_ID = "null resourceId";
    private static final String NULL_OWNER = "null owner";

    private static final String RESOURCE_A = "resource.a";
    private static final String RESOURCE_B = "resource.b";
    private static final String RESOURCE_C = "resource.c";
    private static final String RESOURCE_D = "resource.d";

    private static final String OWNER1 = "owner.one";
    private static final String OWNER2 = "owner.two";
    private static final String OWNER3 = "owner.three";
    
    /**
     * Name of the "current time" field within the {@link SimpleLockManager} class.
     */
    private static final String TIME_FIELD = "currentTime";
    
    private static CurrentTime savedTime;

    private TestTime testTime;
    private Future<Boolean> fut;
    private SimpleLockManager mgr;
    
    @BeforeClass
    public static void setUpBeforeClass() {
        savedTime = Whitebox.getInternalState(SimpleLockManager.class, TIME_FIELD);
    }
    
    @AfterClass
    public static void tearDownAfterClass() {
        Whitebox.setInternalState(SimpleLockManager.class, TIME_FIELD, savedTime);
    }

    @Before
    public void setUp() {
        testTime = new TestTime();
        Whitebox.setInternalState(SimpleLockManager.class, TIME_FIELD, testTime);
        
        mgr = new SimpleLockManager();
    }
    
    @Test
    public void testCurrentTime() {
        assertNotNull(savedTime);
    }

    @Test
    public void testLock() throws Exception {
        fut = mgr.lock(RESOURCE_A, OWNER1, null);

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

    @Test
    public void testLock_AlreadyLocked() throws Exception {
        mgr.lock(RESOURCE_A, OWNER1, null);

        fut = mgr.lock(RESOURCE_A, OWNER2, null);
        assertTrue(fut.isDone());
        assertFalse(fut.get());
    }

    @Test(expected = IllegalStateException.class)
    public void testLock_SameOwner() throws Exception {
        mgr.lock(RESOURCE_A, OWNER1, null);

        // should throw an exception
        mgr.lock(RESOURCE_A, OWNER1, null);
    }

    @Test
    public void testLock_ArgEx() {
        IllegalArgumentException ex =
                        expectException(IllegalArgumentException.class, () -> mgr.lock(null, OWNER1, null));
        assertEquals(NULL_RESOURCE_ID, ex.getMessage());

        ex = expectException(IllegalArgumentException.class, () -> mgr.lock(RESOURCE_A, null, null));
        assertEquals(NULL_OWNER, ex.getMessage());

        // this should not throw an exception
        mgr.lock(RESOURCE_A, OWNER1, null);
    }

    @Test
    public void testUnlock() throws Exception {
        mgr.lock(RESOURCE_A, OWNER1, null);

        // unlock it
        assertTrue(mgr.unlock(RESOURCE_A, OWNER1));
    }

    @Test
    public void testUnlock_ArgEx() {
        IllegalArgumentException ex = expectException(IllegalArgumentException.class, () -> mgr.unlock(null, OWNER1));
        assertEquals(NULL_RESOURCE_ID, ex.getMessage());

        ex = expectException(IllegalArgumentException.class, () -> mgr.unlock(RESOURCE_A, null));
        assertEquals(NULL_OWNER, ex.getMessage());
    }

    @Test
    public void testUnlock_NotLocked() {
        assertFalse(mgr.unlock(RESOURCE_A, OWNER1));
    }

    @Test
    public void testUnlock_DiffOwner() {
        mgr.lock(RESOURCE_A, OWNER1, null);
        assertFalse(mgr.unlock(RESOURCE_A, OWNER2));
    }

    @Test
    public void testIsLocked() {
        assertFalse(mgr.isLocked(RESOURCE_A));

        mgr.lock(RESOURCE_A, OWNER1, null);
        mgr.lock(RESOURCE_B, OWNER1, null);

        assertTrue(mgr.isLocked(RESOURCE_A));
        assertTrue(mgr.isLocked(RESOURCE_B));
        assertFalse(mgr.isLocked(RESOURCE_C));

        // unlock from first resource
        mgr.unlock(RESOURCE_A, OWNER1);
        assertFalse(mgr.isLocked(RESOURCE_A));
        assertTrue(mgr.isLocked(RESOURCE_B));
        assertFalse(mgr.isLocked(RESOURCE_C));

        // unlock from second resource
        mgr.unlock(RESOURCE_B, OWNER1);
        assertFalse(mgr.isLocked(RESOURCE_A));
        assertFalse(mgr.isLocked(RESOURCE_B));
        assertFalse(mgr.isLocked(RESOURCE_C));
    }

    @Test
    public void testIsLocked_ArgEx() {
        IllegalArgumentException ex = expectException(IllegalArgumentException.class, () -> mgr.isLocked(null));
        assertEquals(NULL_RESOURCE_ID, ex.getMessage());
    }

    @Test
    public void testIsLockedBy() {
        assertFalse(mgr.isLockedBy(RESOURCE_A, OWNER1));

        mgr.lock(RESOURCE_A, OWNER1, null);

        assertFalse(mgr.isLockedBy(RESOURCE_B, OWNER1));

        assertTrue(mgr.isLockedBy(RESOURCE_A, OWNER1));
        assertFalse(mgr.isLockedBy(RESOURCE_A, OWNER2));

        // unlock from the resource
        mgr.unlock(RESOURCE_A, OWNER1);
        assertFalse(mgr.isLockedBy(RESOURCE_A, OWNER1));
        assertFalse(mgr.isLockedBy(RESOURCE_A, OWNER2));
        assertFalse(mgr.isLockedBy(RESOURCE_B, OWNER1));
    }

    @Test
    public void testIsLockedBy_ArgEx() {
        IllegalArgumentException ex =
                        expectException(IllegalArgumentException.class, () -> mgr.isLockedBy(null, OWNER1));
        assertEquals(NULL_RESOURCE_ID, ex.getMessage());

        ex = expectException(IllegalArgumentException.class, () -> mgr.isLockedBy(RESOURCE_A, null));
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
    public void testCleanUpLocks() throws Exception {
        // note: this assumes that MAX_AGE_MS is divisible by 4
        mgr.lock(RESOURCE_A, OWNER1, null);
        assertTrue(mgr.isLocked(RESOURCE_A));
        
        testTime.sleep(10);
        mgr.lock(RESOURCE_B, OWNER1, null);
        assertTrue(mgr.isLocked(RESOURCE_A));
        assertTrue(mgr.isLocked(RESOURCE_B));
        
        testTime.sleep(SimpleLockManager.MAX_AGE_MS/4);
        mgr.lock(RESOURCE_C, OWNER1, null);
        assertTrue(mgr.isLocked(RESOURCE_A));
        assertTrue(mgr.isLocked(RESOURCE_B));
        assertTrue(mgr.isLocked(RESOURCE_C));
        
        testTime.sleep(SimpleLockManager.MAX_AGE_MS/4);
        mgr.lock(RESOURCE_D, OWNER1, null);
        assertTrue(mgr.isLocked(RESOURCE_A));
        assertTrue(mgr.isLocked(RESOURCE_B));
        assertTrue(mgr.isLocked(RESOURCE_C));
        assertTrue(mgr.isLocked(RESOURCE_D));
        
        // sleep remainder of max age - first two should expire
        testTime.sleep(SimpleLockManager.MAX_AGE_MS/2);
        assertFalse(mgr.isLocked(RESOURCE_A));
        assertFalse(mgr.isLocked(RESOURCE_B));
        assertTrue(mgr.isLocked(RESOURCE_C));
        assertTrue(mgr.isLocked(RESOURCE_D));
        
        // another quarter - next one should expire
        testTime.sleep(SimpleLockManager.MAX_AGE_MS/4);
        assertFalse(mgr.isLocked(RESOURCE_C));
        assertTrue(mgr.isLocked(RESOURCE_D));
        
        // another quarter - last one should expire
        testTime.sleep(SimpleLockManager.MAX_AGE_MS/4);
        assertFalse(mgr.isLocked(RESOURCE_D));
    }
    
    @Test
    public void testDataGetXxx() {
        long ttime = System.currentTimeMillis() + 5;
        Data data = new Data(OWNER1, RESOURCE_A, ttime);
        
        assertEquals(OWNER1, data.getOwner());
        assertEquals(RESOURCE_A, data.getResource());
        assertEquals(ttime, data.getExpirationMs());
    }
    
    @Test
    public void testDataCompareTo() {
        long ttime = System.currentTimeMillis() + 50;
        Data data = new Data(OWNER1, RESOURCE_A, ttime);
        Data dataSame = new Data(OWNER1, RESOURCE_A, ttime);
        Data dataDiffExpire = new Data(OWNER1, RESOURCE_A, ttime+1);
        Data dataDiffOwner = new Data(OWNER2, RESOURCE_A, ttime);
        Data dataDiffResource = new Data(OWNER1, RESOURCE_B, ttime);
        
        assertEquals(0, data.compareTo(data));
        assertEquals(0, data.compareTo(dataSame));
        
        assertTrue(data.compareTo(dataDiffExpire) < 0);
        assertTrue(dataDiffExpire.compareTo(data) > 0);
        
        assertTrue(data.compareTo(dataDiffOwner) < 0);
        assertTrue(dataDiffOwner.compareTo(data) > 0);
        
        assertTrue(data.compareTo(dataDiffResource) < 0);
        assertTrue(dataDiffResource.compareTo(data) > 0);
    }
    
    @Test
    public void testDataHashCode() {
        long ttime = System.currentTimeMillis() + 1;
        Data data = new Data(OWNER1, RESOURCE_A, ttime);
        Data dataSame = new Data(OWNER1, RESOURCE_A, ttime);
        Data dataDiffExpire = new Data(OWNER1, RESOURCE_A, ttime+1);
        Data dataDiffOwner = new Data(OWNER2, RESOURCE_A, ttime);
        Data dataDiffResource = new Data(OWNER1, RESOURCE_B, ttime);
        Data dataNullOwner = new Data(null, RESOURCE_A, ttime);
        Data dataNullResource = new Data(OWNER1, null, ttime);
        
        int hc1 = data.hashCode();
        assertEquals(hc1, dataSame.hashCode());

        assertTrue(hc1 != dataDiffExpire.hashCode());
        assertTrue(hc1 != dataDiffOwner.hashCode());
        assertTrue(hc1 != dataDiffResource.hashCode());
        assertTrue(hc1 != dataNullOwner.hashCode());
        assertTrue(hc1 != dataNullResource.hashCode());
    }
    
    @Test
    public void testDataEquals() {
        long ttime = System.currentTimeMillis() + 50;
        Data data = new Data(OWNER1, RESOURCE_A, ttime);
        Data dataSame = new Data(OWNER1, RESOURCE_A, ttime);
        Data dataDiffExpire = new Data(OWNER1, RESOURCE_A, ttime+1);
        Data dataDiffOwner = new Data(OWNER2, RESOURCE_A, ttime);
        Data dataDiffResource = new Data(OWNER1, RESOURCE_B, ttime);
        Data dataNullOwner = new Data(null, RESOURCE_A, ttime);
        Data dataNullResource = new Data(OWNER1, null, ttime);
        
        assertTrue(data.equals(data));
        assertTrue(data.equals(dataSame));

        assertFalse(data.equals(dataDiffExpire));
        assertFalse(data.equals(dataDiffOwner));
        assertFalse(data.equals(dataDiffResource));

        assertFalse(data.equals(null));
        assertFalse(data.equals("string"));

        assertFalse(dataNullOwner.equals(data));
        assertFalse(dataNullResource.equals(data));

        assertTrue(dataNullOwner.equals(new Data(null, RESOURCE_A, ttime)));
        assertTrue(dataNullResource.equals(new Data(OWNER1, null, ttime)));
    }

    @Test
    public void testMultiThreaded() throws InterruptedException {
        int nthreads = 10;
        int nlocks = 100;

        LinkedList<Thread> threads = new LinkedList<>();

        String[] resources = {RESOURCE_A, RESOURCE_B};

        AtomicInteger nfail = new AtomicInteger(0);

        CountDownLatch stopper = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(nthreads);

        for (int x = 0; x < nthreads; ++x) {
            String owner = "owner." + x;

            Thread t = new Thread(() -> {

                for (int y = 0; y < nlocks; ++y) {
                    String res = resources[y % resources.length];

                    try {
                        // some locks will be acquired, some denied
                        mgr.lock(res, owner, null).get();

                        // do some "work"
                        stopper.await(1L, TimeUnit.MILLISECONDS);

                        mgr.unlock(res, owner);

                    } catch (CancellationException | ExecutionException e) {
                        nfail.incrementAndGet();

                    } catch (InterruptedException expected) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                completed.countDown();
            });

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

        assertEquals(0, nfail.get());
    }

}
