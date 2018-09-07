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
import static org.onap.policy.drools.core.lock.UtilsTest.expectException;

import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
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

    // Note: this must be a multiple of four
    private static final int MAX_AGE_SEC = 4 * 60;
    private static final int MAX_AGE_MS = MAX_AGE_SEC * 1000;
    
    private static final String EXPECTED_EXCEPTION = "expected exception";
    
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
    private SimpleLockManager mgr;
    
    @BeforeClass
    public static void setUpBeforeClass() {
        savedTime = Whitebox.getInternalState(SimpleLockManager.class, TIME_FIELD);
    }
    
    @AfterClass
    public static void tearDownAfterClass() {
        Whitebox.setInternalState(SimpleLockManager.class, TIME_FIELD, savedTime);
    }

    /**
     * Set up.
     */
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
        assertTrue(mgr.lock(RESOURCE_A, OWNER1, MAX_AGE_SEC));

        assertTrue(mgr.isLocked(RESOURCE_A));
        assertTrue(mgr.isLockedBy(RESOURCE_A, OWNER1));
        assertFalse(mgr.isLocked(RESOURCE_B));
        assertFalse(mgr.isLockedBy(RESOURCE_A, OWNER2));

        // different owner and resource - should succeed
        assertTrue(mgr.lock(RESOURCE_C, OWNER3, MAX_AGE_SEC));

        // different owner - already locked
        assertFalse(mgr.lock(RESOURCE_A, OWNER3, MAX_AGE_SEC));
    }

    @Test
    public void testLock_ExtendLock() throws Exception {
        mgr.lock(RESOURCE_A, OWNER1, MAX_AGE_SEC);
        
        // sleep half of the cycle
        testTime.sleep(MAX_AGE_MS / 2);
        assertTrue(mgr.isLockedBy(RESOURCE_A, OWNER1));
        
        // extend the lock
        mgr.refresh(RESOURCE_A, OWNER1, MAX_AGE_SEC);
        
        // verify still locked after sleeping the other half of the cycle
        testTime.sleep(MAX_AGE_MS / 2 + 1);
        assertTrue(mgr.isLockedBy(RESOURCE_A, OWNER1));
        
        // and should release after another half cycle
        testTime.sleep(MAX_AGE_MS / 2);
        assertFalse(mgr.isLockedBy(RESOURCE_A, OWNER1));
    }

    @Test
    public void testLock_AlreadyLocked() throws Exception {
        mgr.lock(RESOURCE_A, OWNER1, MAX_AGE_SEC);
        
        // same owner
        assertFalse(mgr.lock(RESOURCE_A, OWNER1, MAX_AGE_SEC));

        // different owner
        assertFalse(mgr.lock(RESOURCE_A, OWNER2, MAX_AGE_SEC));
    }

    @Test
    public void testLock_ArgEx() {
        IllegalArgumentException ex =
                        expectException(IllegalArgumentException.class, () -> mgr.lock(null, OWNER1, MAX_AGE_SEC));
        assertEquals(NULL_RESOURCE_ID, ex.getMessage());

        ex = expectException(IllegalArgumentException.class, () -> mgr.lock(RESOURCE_A, null, MAX_AGE_SEC));
        assertEquals(NULL_OWNER, ex.getMessage());

        // this should not throw an exception
        mgr.lock(RESOURCE_A, OWNER1, MAX_AGE_SEC);
    }

    @Test
    public void testRefresh() throws Exception {
        // don't own the lock yet - cannot refresh
        assertFalse(mgr.refresh(RESOURCE_A, OWNER1, MAX_AGE_SEC));

        assertTrue(mgr.lock(RESOURCE_A, OWNER1, MAX_AGE_SEC));

        // now the lock is owned
        assertTrue(mgr.refresh(RESOURCE_A, OWNER1, MAX_AGE_SEC));

        // refresh again
        assertTrue(mgr.refresh(RESOURCE_A, OWNER1, MAX_AGE_SEC + 1));

        assertTrue(mgr.isLocked(RESOURCE_A));
        assertTrue(mgr.isLockedBy(RESOURCE_A, OWNER1));
        assertFalse(mgr.isLocked(RESOURCE_B));
        assertFalse(mgr.isLockedBy(RESOURCE_A, OWNER2));

        // different owner
        assertFalse(mgr.refresh(RESOURCE_A, OWNER3, MAX_AGE_SEC));

        // different resource
        assertFalse(mgr.refresh(RESOURCE_C, OWNER1, MAX_AGE_SEC));
    }

    @Test
    public void testRefresh_ExtendLock() throws Exception {
        mgr.lock(RESOURCE_A, OWNER1, MAX_AGE_SEC);

        // sleep half of the cycle
        testTime.sleep(MAX_AGE_MS / 2);
        assertTrue(mgr.isLockedBy(RESOURCE_A, OWNER1));

        // extend the lock
        mgr.refresh(RESOURCE_A, OWNER1, MAX_AGE_SEC);

        // verify still locked after sleeping the other half of the cycle
        testTime.sleep(MAX_AGE_MS / 2 + 1);
        assertTrue(mgr.isLockedBy(RESOURCE_A, OWNER1));

        // and should release after another half cycle
        testTime.sleep(MAX_AGE_MS / 2);
        
        // cannot refresh expired lock
        assertFalse(mgr.refresh(RESOURCE_A, OWNER1, MAX_AGE_SEC));
        
        assertFalse(mgr.isLockedBy(RESOURCE_A, OWNER1));
    }

    @Test
    public void testRefresh_AlreadyLocked() throws Exception {
        mgr.lock(RESOURCE_A, OWNER1, MAX_AGE_SEC);

        // same owner
        assertTrue(mgr.refresh(RESOURCE_A, OWNER1, MAX_AGE_SEC));

        // different owner
        assertFalse(mgr.refresh(RESOURCE_A, OWNER2, MAX_AGE_SEC));
        assertFalse(mgr.lock(RESOURCE_A, OWNER2, MAX_AGE_SEC));
    }

    @Test
    public void testRefresh_ArgEx() {
        IllegalArgumentException ex =
                        expectException(IllegalArgumentException.class, () -> mgr.refresh(null, OWNER1, MAX_AGE_SEC));
        assertEquals(NULL_RESOURCE_ID, ex.getMessage());

        ex = expectException(IllegalArgumentException.class, () -> mgr.refresh(RESOURCE_A, null, MAX_AGE_SEC));
        assertEquals(NULL_OWNER, ex.getMessage());

        // this should not throw an exception
        mgr.refresh(RESOURCE_A, OWNER1, MAX_AGE_SEC);
    }

    @Test
    public void testUnlock() throws Exception {
        mgr.lock(RESOURCE_A, OWNER1, MAX_AGE_SEC);

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
        mgr.lock(RESOURCE_A, OWNER1, MAX_AGE_SEC);
        assertFalse(mgr.unlock(RESOURCE_A, OWNER2));
    }

    @Test
    public void testIsLocked() {
        assertFalse(mgr.isLocked(RESOURCE_A));

        mgr.lock(RESOURCE_A, OWNER1, MAX_AGE_SEC);
        mgr.lock(RESOURCE_B, OWNER1, MAX_AGE_SEC);

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

        mgr.lock(RESOURCE_A, OWNER1, MAX_AGE_SEC);

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
        mgr.lock(RESOURCE_A, OWNER1, MAX_AGE_SEC);

        // different resource, thus no lock
        assertFalse(mgr.isLockedBy(RESOURCE_B, OWNER1));
    }

    @Test
    public void testIsLockedBy_LockedButNotOwner() {
        mgr.lock(RESOURCE_A, OWNER1, MAX_AGE_SEC);

        // different owner
        assertFalse(mgr.isLockedBy(RESOURCE_A, OWNER2));
    }
    
    @Test
    public void testCleanUpLocks() throws Exception {
        // note: this assumes that MAX_AGE_MS is divisible by 4
        mgr.lock(RESOURCE_A, OWNER1, MAX_AGE_SEC);
        assertTrue(mgr.isLocked(RESOURCE_A));
        
        testTime.sleep(10);
        mgr.lock(RESOURCE_B, OWNER1, MAX_AGE_SEC);
        assertTrue(mgr.isLocked(RESOURCE_A));
        assertTrue(mgr.isLocked(RESOURCE_B));
        
        testTime.sleep(MAX_AGE_MS / 4);
        mgr.lock(RESOURCE_C, OWNER1, MAX_AGE_SEC);
        assertTrue(mgr.isLocked(RESOURCE_A));
        assertTrue(mgr.isLocked(RESOURCE_B));
        assertTrue(mgr.isLocked(RESOURCE_C));
        
        testTime.sleep(MAX_AGE_MS / 4);
        mgr.lock(RESOURCE_D, OWNER1, MAX_AGE_SEC);
        assertTrue(mgr.isLocked(RESOURCE_A));
        assertTrue(mgr.isLocked(RESOURCE_B));
        assertTrue(mgr.isLocked(RESOURCE_C));
        assertTrue(mgr.isLocked(RESOURCE_D));
        
        // sleep remainder of max age - first two should expire
        testTime.sleep(MAX_AGE_MS / 2);
        assertFalse(mgr.isLocked(RESOURCE_A));
        assertFalse(mgr.isLocked(RESOURCE_B));
        assertTrue(mgr.isLocked(RESOURCE_C));
        assertTrue(mgr.isLocked(RESOURCE_D));
        
        // another quarter - next one should expire
        testTime.sleep(MAX_AGE_MS / 4);
        assertFalse(mgr.isLocked(RESOURCE_C));
        assertTrue(mgr.isLocked(RESOURCE_D));
        
        // another quarter - last one should expire
        testTime.sleep(MAX_AGE_MS / 4);
        assertFalse(mgr.isLocked(RESOURCE_D));
    }

    @Test
    public void testMakeNullArgException() {
        IllegalArgumentException ex = SimpleLockManager.makeNullArgException(EXPECTED_EXCEPTION);
        assertEquals(EXPECTED_EXCEPTION, ex.getMessage());
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
        Data dataDiffExpire = new Data(OWNER1, RESOURCE_A, ttime + 1);
        
        assertEquals(0, data.compareTo(data));
        assertEquals(0, data.compareTo(dataSame));
        
        assertTrue(data.compareTo(dataDiffExpire) < 0);
        assertTrue(dataDiffExpire.compareTo(data) > 0);
        
        Data dataDiffOwner = new Data(OWNER2, RESOURCE_A, ttime);
        Data dataDiffResource = new Data(OWNER1, RESOURCE_B, ttime);

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
        Data dataDiffExpire = new Data(OWNER1, RESOURCE_A, ttime + 1);
        Data dataDiffOwner = new Data(OWNER2, RESOURCE_A, ttime);
        
        int hc1 = data.hashCode();
        assertEquals(hc1, dataSame.hashCode());

        assertTrue(hc1 != dataDiffExpire.hashCode());
        assertTrue(hc1 != dataDiffOwner.hashCode());

        Data dataDiffResource = new Data(OWNER1, RESOURCE_B, ttime);
        Data dataNullOwner = new Data(null, RESOURCE_A, ttime);
        Data dataNullResource = new Data(OWNER1, null, ttime);
        
        assertTrue(hc1 != dataDiffResource.hashCode());
        assertTrue(hc1 != dataNullOwner.hashCode());
        assertTrue(hc1 != dataNullResource.hashCode());
    }
    
    @Test
    public void testDataEquals() {
        long ttime = System.currentTimeMillis() + 50;
        Data data = new Data(OWNER1, RESOURCE_A, ttime);
        Data dataSame = new Data(OWNER1, RESOURCE_A, ttime);
        Data dataDiffExpire = new Data(OWNER1, RESOURCE_A, ttime + 1);
        
        assertTrue(data.equals(data));
        assertTrue(data.equals(dataSame));

        Data dataDiffOwner = new Data(OWNER2, RESOURCE_A, ttime);
        Data dataDiffResource = new Data(OWNER1, RESOURCE_B, ttime);

        assertFalse(data.equals(dataDiffExpire));
        assertFalse(data.equals(dataDiffOwner));
        assertFalse(data.equals(dataDiffResource));

        assertFalse(data.equals(null));
        assertFalse(data.equals("string"));

        Data dataNullOwner = new Data(null, RESOURCE_A, ttime);
        Data dataNullResource = new Data(OWNER1, null, ttime);

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

        final AtomicInteger nfail = new AtomicInteger(0);

        CountDownLatch stopper = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(nthreads);

        for (int x = 0; x < nthreads; ++x) {
            String owner = "owner." + x;

            Thread thread = new Thread(() -> {

                for (int y = 0; y < nlocks; ++y) {
                    String res = resources[y % resources.length];

                    try {
                        // some locks will be acquired, some denied
                        mgr.lock(res, owner, MAX_AGE_SEC);

                        // do some "work"
                        stopper.await(1L, TimeUnit.MILLISECONDS);

                        mgr.unlock(res, owner);

                    } catch (InterruptedException expected) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                completed.countDown();
            });

            thread.setDaemon(true);
            threads.add(thread);
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
