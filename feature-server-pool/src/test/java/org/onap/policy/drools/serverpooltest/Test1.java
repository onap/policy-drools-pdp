/*
 * ============LICENSE_START=======================================================
 * feature-server-pool
 * ================================================================================
 * Copyright (C) 2020 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.serverpooltest;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.kie.api.runtime.KieSession;
import org.onap.policy.drools.core.DroolsRunnable;
import org.onap.policy.drools.serverpool.BucketWrapperImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Test1 {
    private static Logger logger = LoggerFactory.getLogger(Test1.class);

    // indicates that Drools containers need to be initialized
    private static boolean needControllerInit = true;

    private static void threadList(String header, boolean stackTrace) {
        logger.info("***** threadList: {} *****", header);
        Thread[] thr = new Thread[1000];
        int count = Thread.enumerate(thr);

        if (count > thr.length) {
            count = thr.length;
        }
        for (int i = 0 ; i < count ; i += 1) {
            StringBuilder sb = new StringBuilder();
            sb.append("    ").append(thr[i]);
            if (stackTrace) {
                for (StackTraceElement ste : thr[i].getStackTrace()) {
                    sb.append("\n        ").append(ste);
                }
            }
            logger.info(sb.toString());
        }
        logger.info("***** end threadList: {}, count = {} *****", header, count);
    }

    /**
     * Set up environment prior to running tests.
     */
    @BeforeClass
    public static void init() throws Exception {
        threadList("BeforeClass", false);

        // create 6 adapters, corresponding to 6 'Server' instances
        Adapter.ensureInit();

        // make sure initialization has completed
        long endTime = System.currentTimeMillis() + 60000L;
        for (Adapter adapter : Adapter.adapters) {
            assertTrue(adapter.toString() + ": Bucket assignments incomplete",
                       adapter.waitForInit(endTime));
        }
    }

    /**
     * Clean up after tests have finished.
     */
    @AfterClass
    public static void finish() throws InterruptedException {
        threadList("AfterClass", false);
        if (!needControllerInit) {
            // shut down Server Pools and DMAAP Simulator
            Adapter.ensureShutdown();

            // updates for persistence may still be in progress -- wait 5 seconds
            threadList("AfterEnsureShutdown", false);
            Thread.sleep(5000);
            threadList("AfterSleep", true);

            // look at KieSession objects
            for (Adapter adapter : Adapter.adapters) {
                StringBuilder sb = new StringBuilder();
                sb.append(adapter.toString())
                    .append(": ")
                    .append(adapter.getKieSession().getObjects().size())
                    .append(" objects");
                for (Object o : adapter.getKieSession().getObjects()) {
                    sb.append("\n    ").append(o);
                }
                LinkedBlockingQueue<String> lbq = adapter.notificationQueue();
                if (!lbq.isEmpty()) {
                    sb.append("\n")
                        .append(adapter.toString())
                        .append(": ")
                        .append(lbq.size())
                        .append(" queued entries");
                    for (String string : lbq) {
                        sb.append("\n    ").append(string);
                    }
                }
                logger.info(sb.toString());
            }

            // this was used during test debugging to verify that no adjuncts
            // were created on the 'null' host -- there shouldn't be any
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            PrintStream out = new PrintStream(bos, true);
            new BucketWrapperImpl.Static().dumpAdjuncts(out);
            logger.info(out.toString());
        }
    }

    /**
     * Initialize all Drools controllers, if needed.
     */
    static void ensureControllersInitialized() {
        if (needControllerInit) {
            needControllerInit = false;
            for (Adapter adapter : Adapter.adapters) {
                String rval = adapter.createController();
                logger.info("{}: Got the following from PolicyController:\n{}",
                            adapter, rval);
            }
        }
    }

    /**
     * make sure all servers have agreed on a lead server.
     */
    @Test
    public void checkLeadServer() {
        Adapter firstAdapter = Adapter.adapters[0];
        UUID leaderUuid = firstAdapter.getLeader().getUuid();
        for (Adapter adapter : Adapter.adapters) {
            UUID uuid = adapter.getLeader().getUuid();
            assertTrue(adapter.toString() + " has UUID " + uuid
                       + " (expected UUID " + leaderUuid + ")",
                       uuid.equals(leaderUuid));
        }
    }

    /**
     * make sure all servers agree on bucket distribution.
     */
    @Test
    public void startup() throws Exception {
        Adapter firstAdapter = Adapter.adapters[0];
        BucketWrapper.Static firstBucketStatic = firstAdapter.getBucketStatic();

        for (Adapter adapter : Adapter.adapters) {
            BucketWrapper.Static bucketStatic = adapter.getBucketStatic();
            if (adapter == firstAdapter) {
                // make sure an owner and primary backup have been chosen
                // for each bucket (secondary backups aren't implemented yet)
                for (int i = 0 ; i < bucketStatic.getBucketCount() ; i += 1) {
                    BucketWrapper bucket = bucketStatic.getBucket(i);
                    assertNotNull(bucket.getOwner());
                    assertNotNull(bucket.getPrimaryBackup());
                }
            } else {
                // make sure the bucket assignments are consistent with
                // the primary backup
                for (int i = 0 ; i < bucketStatic.getBucketCount() ; i += 1) {
                    BucketWrapper firstBucket = firstBucketStatic.getBucket(i);
                    BucketWrapper bucket = bucketStatic.getBucket(i);
                    assertEquals(firstBucket.getOwner().getUuid(),
                                 bucket.getOwner().getUuid());
                    assertEquals(firstBucket.getPrimaryBackup().getUuid(),
                                 bucket.getPrimaryBackup().getUuid());
                }
            }
        }
    }

    // test 'TargetLock'
    @Test
    public void testTargetLock() throws InterruptedException {
        // test locks on different hosts
        lockTests(Adapter.adapters[5], Adapter.adapters[0]);

        // test locks on the same host
        lockTests(Adapter.adapters[2], Adapter.adapters[2]);

        Adapter adapter0 = Adapter.adapters[0];
        Adapter adapter5 = Adapter.adapters[5];
        String ownerKey = adapter0.findKey("owner");
        String key = adapter5.findKey("key");
        LockOwner owner = new LockOwner();

        // some exceptions
        try {
            adapter0.newTargetLock(null, ownerKey, owner);
            fail();
        } catch (IllegalArgumentException e) {
            // this is expected
            assertTrue(e.toString().contains("'key' can't be null"));
        }
        try {
            adapter0.newTargetLock(key, null, owner);
            fail();
        } catch (IllegalArgumentException e) {
            // this is expected
            assertTrue(e.toString().contains("'ownerKey' can't be null"));
        }
        try {
            adapter5.newTargetLock(key, ownerKey, owner);
            fail();
        } catch (IllegalArgumentException e) {
            // this is expected
            assertTrue(e.toString()
                       .contains("not currently assigned to this server"));
        }
        try {
            adapter0.newTargetLock(key, ownerKey, null);
            fail();
        } catch (IllegalArgumentException e) {
            // this is expected
            assertTrue(e.toString().contains("'owner' can't be null"));
        }
    }

    /**
     * Run some 'TargetLock' tests.
     *
     * @param keyAdapter this is the adapter for the key, which determines
     *     where the server-side data will reside
     * @param ownerAdapter this is the adapter associated with the requestor
     */
    void lockTests(Adapter keyAdapter, Adapter ownerAdapter) throws InterruptedException {
        // choose 'key' and 'ownerKey' values that map to buckets owned
        // by their respective adapters
        String key = keyAdapter.findKey("key");
        String ownerKey = ownerAdapter.findKey("owner");

        // this receives and queues callback notifications
        LockOwner owner = new LockOwner();

        // first lock -- should succeed
        TargetLockWrapper tl1 = ownerAdapter.newTargetLock(key, ownerKey, owner);
        assertArrayEquals(new Object[] {"lockAvailable", tl1},
                          owner.poll(5, TimeUnit.SECONDS));
        assertNull(owner.poll());
        assertTrue(tl1.isActive());
        assertEquals(TargetLockWrapper.State.ACTIVE, tl1.getState());
        assertEquals(ownerKey, tl1.getOwnerKey());

        // second lock -- should fail (lock in use)
        TargetLockWrapper tl2 =
            ownerAdapter.newTargetLock(key, ownerKey, owner, false);
        assertArrayEquals(new Object[] {"lockUnavailable", tl2},
                          owner.poll(5, TimeUnit.SECONDS));
        assertNull(owner.poll());
        assertFalse(tl2.isActive());
        assertEquals(TargetLockWrapper.State.FREE, tl2.getState());
        assertEquals(ownerKey, tl2.getOwnerKey());

        // third and fourth locks -- should wait
        TargetLockWrapper tl3 = ownerAdapter.newTargetLock(key, ownerKey, owner);
        TargetLockWrapper tl4 = ownerAdapter.newTargetLock(key, ownerKey, owner);
        assertNull(owner.poll(5, TimeUnit.SECONDS)); // nothing immediately
        assertFalse(tl3.isActive());
        assertFalse(tl4.isActive());
        assertEquals(TargetLockWrapper.State.WAITING, tl3.getState());
        assertEquals(TargetLockWrapper.State.WAITING, tl4.getState());

        // free third lock before ever getting a callback
        assertTrue(tl3.free());
        assertFalse(tl3.isActive());
        assertEquals(TargetLockWrapper.State.FREE, tl3.getState());
        assertFalse(tl3.free());

        // free first lock
        assertTrue(tl1.free());
        assertFalse(tl1.isActive());
        assertEquals(TargetLockWrapper.State.FREE, tl1.getState());
        assertFalse(tl1.free()); // already free

        // fourth lock should be active now (or soon)
        assertArrayEquals(new Object[] {"lockAvailable", tl4},
                          owner.poll(5, TimeUnit.SECONDS));
        assertNull(owner.poll());
        assertTrue(tl4.isActive());
        assertEquals(TargetLockWrapper.State.ACTIVE, tl4.getState());

        // free fourth lock
        assertTrue(tl4.free());
        assertFalse(tl4.isActive());
        assertEquals(TargetLockWrapper.State.FREE, tl4.getState());
    }

    /**
     * Test sending of intra-server and inter-server messages.
     */
    @Test
    public void topicSendTests() throws InterruptedException {
        ensureControllersInitialized();

        // sender and receiver are the same
        topicSendTest(Adapter.adapters[2], Adapter.adapters[2], false);

        // sender and receiver are different
        topicSendTest(Adapter.adapters[5], Adapter.adapters[4], false);
    }

    /**
     * Send a message from 'sender' to 'receiver' -- the message is delivered
     * as an incoming 'TopicListener' event, is processed by
     * 'FeatureServerPool.beforeOffer' (PolicyControllerFeatureApi), which
     * will route it based upon keyword. At the destination end, it should
     * be converted to an instance of 'TestDroolsObject', and inserted into
     * the Drools session. The associated Drools rule should fire, and the
     * message is placed in the notification queue. The message is then
     * retracted, unless the string 'SAVE' appears within the message, which
     * is the case if the 'save' parameter is set.
     *
     * @param sender the adapter associated with the sending end
     * @param receiver the adapter associated with the receiving end
     * @param save if 'true' the message is not retracted, if 'false' it is
     *     retracted
     */
    String topicSendTest(Adapter sender, Adapter receiver, boolean save) throws InterruptedException {
        // generate base message -- include 'SAVE' in the message if 'save' is set
        String message = "From " + sender.toString() + " to "
                         + receiver.toString();
        if (save) {
            message += " (SAVE)";
        }
        message += ": " + UUID.randomUUID().toString() + ".";

        // add a numeric suffix to the message, such that it will be routed
        // to 'receiver'
        message = receiver.findKey(message);

        // send the message
        sender.sendEvent(message);

        // verify that it has been received
        assertEquals(message,
                     receiver.notificationQueue().poll(30, TimeUnit.SECONDS));
        return message;
    }

    /**
     * Return the Adapter associated with the current lead server.
     *
     * @return the Adapter associated with the current lead server
     *     ('null' if there is no leader)
     */
    private Adapter getLeader() {
        for (Adapter adapter : Adapter.adapters) {
            if (adapter.getLeader() == adapter.getServerStatic().getThisServer()) {
                // we have located the leader
                return adapter;
            }
        }
        fail("Can't determine leader");
        return null;
    }

    /**
     * Test migration of sessions from one server to another.
     */
    @Test
    public void sessionMigrationTest() throws InterruptedException {
        ensureControllersInitialized();

        // select adapters for roles
        Adapter sender = Adapter.adapters[1];
        Adapter receiver = Adapter.adapters[3];
        Adapter newReceiver = Adapter.adapters[5];

        // determine current leader
        Adapter leader = getLeader();

        // send message from 'sender' to 'receiver', and save it
        String message = topicSendTest(sender, receiver, true);

        // verify where the bucket is and is not
        assertTrue(receiver.getBucketStatic().isKeyOnThisServer(message));
        assertFalse(newReceiver.getBucketStatic().isKeyOnThisServer(message));

        // move to the new host
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bos, true);
        leader.getBucketStatic().moveBucket(
            out, receiver.getBucketStatic().bucketNumber(message),
            newReceiver.getServerStatic().getThisServer().getUuid().toString());
        logger.info(bos.toString());

        // poll up to 10 seconds for the bucket to be updated
        boolean found = false;
        TestDroolsObject matchingObject = new TestDroolsObject(message);
        for (int i = 0 ; i < 10 && !found ; i += 1) {
            Thread.sleep(1000);
            if (new ArrayList<Object>(newReceiver.getKieSession().getObjects())
                    .contains(matchingObject)) {
                found = true;
                break;
            }
        }
        assertTrue(found);

        // verify where the bucket is and is not
        assertFalse(receiver.getBucketStatic().isKeyOnThisServer(message));
        assertTrue(newReceiver.getBucketStatic().isKeyOnThisServer(message));
    }

    /**
     * Test migration of locks from one server to another.
     */ 
    @Test
    public void lockMigrationTest() throws InterruptedException {
        ensureControllersInitialized();

        // select adapters for roles -- '*Server' refers to the 'key' end,
        // and '*Client' refers to the 'ownerKey' end
        final Adapter oldServer = Adapter.adapters[0];
        final Adapter newServer = Adapter.adapters[1];
        final Adapter oldClient = Adapter.adapters[2];
        final Adapter newClient = Adapter.adapters[3];

        // determine the current leader
        final Adapter leader = getLeader();

        // choose 'key' and 'ownerKey' values associated with
        // 'oldServer' and 'oldClient', respectively
        String key = oldServer.findKey("key");
        String ownerKey = oldClient.findKey("owner");
        LockOwner owner = new LockOwner();

        // allocate lock 1
        TargetLockWrapper tl1 = oldClient.newTargetLock(key, ownerKey, owner);
        assertArrayEquals(new Object[] {"lockAvailable", tl1},
                          owner.poll(5, TimeUnit.SECONDS));

        // allocate a lock 2, which should be in the 'WAITING' state
        TargetLockWrapper tl2 = oldClient.newTargetLock(key, ownerKey, owner);
        assertNull(owner.poll(5, TimeUnit.SECONDS)); // nothing immediately

        // verify lock states
        assertEquals(TargetLockWrapper.State.ACTIVE, tl1.getState());
        assertEquals(TargetLockWrapper.State.WAITING, tl2.getState());

        // verify key buckets (before)
        assertTrue(oldServer.getBucketStatic().isKeyOnThisServer(key));
        assertFalse(newServer.getBucketStatic().isKeyOnThisServer(key));

        // move key buckets to new host (server side)
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bos, true);
        leader.getBucketStatic().moveBucket(
            out, oldServer.getBucketStatic().bucketNumber(key),
            newServer.getServerStatic().getThisServer().getUuid().toString());
        logger.info(bos.toString());

        Thread.sleep(3000); // TBD: somehow test for this, rather than sleep

        // verify key buckets (after)
        assertFalse(oldServer.getBucketStatic().isKeyOnThisServer(key));
        assertTrue(newServer.getBucketStatic().isKeyOnThisServer(key));

        // we should be able to free lock1 now, and lock2 should go active,
        // indicating that the server side is still working
        assertTrue(tl1.free());
        assertArrayEquals(new Object[] {"lockAvailable", tl2},
                          owner.poll(5, TimeUnit.SECONDS));
        assertEquals(TargetLockWrapper.State.ACTIVE, tl2.getState());

        // create a third lock
        TargetLockWrapper tl3 = oldClient.newTargetLock(key, ownerKey, owner);
        assertNull(owner.poll(5, TimeUnit.SECONDS)); // nothing immediately
        assertEquals(TargetLockWrapper.State.WAITING, tl3.getState());

        // insert active objects in Drools session, which is about to be moved
        // (if we don't do this, the client objects won't be relocated)
        oldClient.getKieSession().insert(new KeywordWrapper(ownerKey, "lmt.owner", owner));
        oldClient.getKieSession().insert(new KeywordWrapper(ownerKey, "lmt.tl2", tl2));
        oldClient.getKieSession().insert(new KeywordWrapper(ownerKey, "lmt.tl3", tl3));

        // dumping out some state information as part of debugging --
        // I see no reason to remove it now
        {
            bos = new ByteArrayOutputStream();
            out = new PrintStream(bos, true);
            out.println("BEFORE: tl2=" + tl2 + "\ntl3=" + tl3);
            oldClient.dumpLocks(out, true);
            oldClient.getBucketStatic().dumpAdjuncts(out);
            logger.debug(bos.toString());
        }

        // don't need these any more -- we will get them back on the new host
        tl1 = tl2 = tl3 = null;
        owner = null;

        // verify ownerKey buckets (before)
        assertTrue(oldClient.getBucketStatic().isKeyOnThisServer(ownerKey));
        assertFalse(newClient.getBucketStatic().isKeyOnThisServer(ownerKey));

        // move ownerKey buckets to new host (client side)
        bos = new ByteArrayOutputStream();
        out = new PrintStream(bos, true);
        leader.getBucketStatic().moveBucket(
            out, oldClient.getBucketStatic().bucketNumber(ownerKey),
            newClient.getServerStatic().getThisServer().getUuid().toString());
        logger.info(bos.toString());

        Thread.sleep(3000); // TBD: somehow test for this, rather than sleep

        // verify ownerKey buckets (before)
        assertFalse(oldClient.getBucketStatic().isKeyOnThisServer(ownerKey));
        assertTrue(newClient.getBucketStatic().isKeyOnThisServer(ownerKey));

        // now, we need to locate 'tl2', 'tl3', and 'owner' in Drools memory
        KieSession kieSession = newClient.getKieSession();
        for (Object obj : new ArrayList<Object>(kieSession.getObjects())) {
            if (obj instanceof KeywordWrapper) {
                KeywordWrapper kw = (KeywordWrapper)obj;

                if ("lmt.owner".equals(kw.id)) {
                    owner = kw.getObject(LockOwner.class);
                } else if ("lmt.tl2".equals(kw.id)) {
                    tl2 = kw.getObject(TargetLockWrapper.class);
                } else if ("lmt.tl3".equals(kw.id)) {
                    tl3 = kw.getObject(TargetLockWrapper.class);
                }
                kieSession.delete(kieSession.getFactHandle(obj));
            }
        }

        // make sure we found everything
        assertNotNull(tl2);
        assertNotNull(tl3);
        assertNotNull(owner);
        assertFalse(newClient.isForeign(tl2, tl3, owner));

        // verify the states of 'tl2' and 'tl3'
        assertEquals(TargetLockWrapper.State.ACTIVE, tl2.getState());
        assertEquals(TargetLockWrapper.State.WAITING, tl3.getState());

        // dumping out some state information as part of debugging --
        // I see no reason to remove it now
        {
            bos = new ByteArrayOutputStream();
            out = new PrintStream(bos, true);
            out.println("AFTER: tl2=" + tl2 + "\ntl3=" + tl3);
            newClient.dumpLocks(out, true);
            newClient.getBucketStatic().dumpAdjuncts(out);
            logger.debug(bos.toString());
        }

        // now, we should be able to free 'tl2', and 'tl3' should go active
        assertNull(owner.poll(5, TimeUnit.SECONDS)); // nothing immediately
        assertTrue(tl2.free());
        assertArrayEquals(new Object[] {"lockAvailable", tl3},
            owner.poll(5, TimeUnit.SECONDS));
        assertEquals(TargetLockWrapper.State.ACTIVE, tl3.getState());
        assertTrue(tl3.free());
    }

    /**
     * Test cleanup of locks that have been abandoned.
     */
    @Test
    public void abandonedLocks() throws InterruptedException {
        // choose adapters
        Adapter keyAdapter = Adapter.adapters[3];
        Adapter ownerAdapter = Adapter.adapters[4];

        // generate compatible keys
        String key = keyAdapter.findKey("abandonedLocks.key");
        String ownerKey = ownerAdapter.findKey("abandonedLocks.owner");

        // receiver of callback notifications
        LockOwner owner = new LockOwner();

        // first lock -- should succeed
        TargetLockWrapper tl1 = ownerAdapter.newTargetLock(key, ownerKey, owner);
        assertArrayEquals(new Object[] {"lockAvailable", tl1},
                          owner.poll(5, TimeUnit.SECONDS));

        // second lock -- should wait
        final TargetLockWrapper tl2 = ownerAdapter.newTargetLock(key, ownerKey, owner);
        assertNull(owner.poll(5, TimeUnit.SECONDS)); // nothing immediately

        // abandon first lock, and do a GC cycle -- tl2 should go active
        tl1 = null;
        System.gc();
        assertArrayEquals(new Object[] {"lockAvailable", tl2},
                          owner.poll(5, TimeUnit.SECONDS));
        assertTrue(tl2.isActive());
        assertEquals(TargetLockWrapper.State.ACTIVE, tl2.getState());

        // free tl2
        assertTrue(tl2.free());
        assertFalse(tl2.isActive());
        assertEquals(TargetLockWrapper.State.FREE, tl2.getState());
    }

    /**
     * Test locks within Drools sessions.
     */
    @Test
    public void locksWithinDrools() throws InterruptedException {
        ensureControllersInitialized();

        // choose adapters
        Adapter keyAdapter = Adapter.adapters[3];
        Adapter ownerAdapter = Adapter.adapters[4];

        // generate compatible keys
        final String key = keyAdapter.findKey("locksWithinDrools.key");
        final String ownerKey = ownerAdapter.findKey("locksWithinDrools.owner");

        // need a 'LockOwner' variant
        final LockOwner owner = new LockOwner() {
            /**
             * {@inheritDoc}
             */
            @Override
            public void lockAvailable(TargetLockWrapper lock) {
                // insert notification in 'LinkedBlockingQueue'
                add(new Object[] {"lockAvailable", lock, Thread.currentThread()});
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void lockUnavailable(TargetLockWrapper lock) {
                // insert notification in 'LinkedBlockingQueue'
                add(new Object[] {"lockUnavailable", lock, Thread.currentThread()});
            }
        };

        // generate first lock outside of Drools
        final TargetLockWrapper tl1 = ownerAdapter.newTargetLock(key, ownerKey, owner);
        Object[] response = owner.poll(5, TimeUnit.SECONDS);
        assertNotNull(response);
        assertEquals(3, response.length);
        assertEquals("lockAvailable", response[0]);
        assertEquals(tl1, response[1]);

        // now, generate one from within Drools
        ownerAdapter.getKieSession().insert(new DroolsRunnable() {
            @Override
            public void run() {
                // create lock, which should block
                TargetLockWrapper tl2 =
                    ownerAdapter.newTargetLock(key, ownerKey, owner);
                owner.add(new Object[] {"tl2Data", tl2, Thread.currentThread()});
            }
        });

        // fetch data from Drools thread
        response = owner.poll(5, TimeUnit.SECONDS);
        assertNotNull(response);
        assertEquals(3, response.length);
        assertEquals("tl2Data", response[0]);

        TargetLockWrapper tl2 = null;
        Thread droolsThread = null;

        if (response[1] instanceof TargetLockWrapper) {
            tl2 = (TargetLockWrapper) response[1];
        }
        if (response[2] instanceof Thread) {
            droolsThread = (Thread) response[2];
        }

        assertNotNull(tl2);
        assertNotNull(droolsThread);

        // tl2 should still be waiting
        assertNull(owner.poll(5, TimeUnit.SECONDS));
        assertFalse(tl2.isActive());
        assertEquals(TargetLockWrapper.State.WAITING, tl2.getState());

        // free tl1
        assertTrue(tl1.free());

        // verify that 'tl2' is now available,
        // and the call back ran in the Drools thread
        assertArrayEquals(new Object[] {"lockAvailable", tl2, droolsThread},
                          owner.poll(5, TimeUnit.SECONDS));
        assertTrue(tl2.isActive());
        assertEquals(TargetLockWrapper.State.ACTIVE, tl2.getState());

        // free tl2
        assertTrue(tl2.free());
    }

    /**
     * Test insertion of objects into Drools memory.
     */
    @Test
    public void insertDrools() throws InterruptedException {
        Adapter adapter1 = Adapter.adapters[1];
        final Adapter adapter2 = Adapter.adapters[2];
        KieSession kieSession;
        boolean found;

        // check whether we can insert objects locally (adapter1 -> adapter1)
        String key1 = adapter1.findKey("insertDrools1-");
        adapter1.insertDrools(new KeywordWrapper(key1, "insertDroolsLocal", null));

        found = false;
        kieSession = adapter1.getKieSession();
        for (Object obj : new ArrayList<Object>(kieSession.getObjects())) {
            if (obj instanceof KeywordWrapper
                    && "insertDroolsLocal".equals(((KeywordWrapper) obj).id)) {
                found = true;
                kieSession.delete(kieSession.getFactHandle(obj));
                break;
            }
        }
        assertTrue(found);

        // check whether we can insert objects remotely (adapter1 -> adapter2)
        String key2 = adapter2.findKey("insertDrools2-");
        adapter1.insertDrools(new KeywordWrapper(key2, "insertDroolsRemote", null));

        // it would be nice to test for this, rather than sleep
        Thread.sleep(3000);

        found = false;
        kieSession = adapter2.getKieSession();
        for (Object obj : new ArrayList<Object>(kieSession.getObjects())) {
            if (obj instanceof KeywordWrapper
                    && "insertDroolsRemote".equals(((KeywordWrapper) obj).id)) {
                found = true;
                kieSession.delete(kieSession.getFactHandle(obj));
                break;
            }
        }
        assertTrue(found);
    }

    /* ============================================================ */

    /**
     * This class implements the 'LockCallback' interface, and
     * makes callback responses available via a 'LinkedBlockingQueue'.
     */
    public static class LockOwner extends LinkedBlockingQueue<Object[]>
        implements TargetLockWrapper.Owner, Serializable {
        /**
         * Constructor -- initialize the 'LinkedBlockingQueue'.
         */
        public LockOwner() {
            super();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void lockAvailable(TargetLockWrapper lock) {
            // insert notification in 'LinkedBlockingQueue'
            add(new Object[] {"lockAvailable", lock});
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void lockUnavailable(TargetLockWrapper lock) {
            // insert notification in 'LinkedBlockingQueue'
            add(new Object[] {"lockUnavailable", lock});
        }
    }

    /* ============================================================ */

    /**
     * This class is used to insert objects in Drools memory to support
     * testing.
     */
    public static class KeywordWrapper implements Serializable {
        // this is the keyword, which determines the associated bucket,
        // which then determines when this object is migrated
        public String key;

        // this is an identifier, which can be used to select objects
        // on the receiving end
        public String id;

        // this is the object being wrapped
        public Serializable obj;

        /**
         * Constructor -- initialize fields.
         *
         * @param key keyword, which determines the associated bucket
         * @param id string identifier, used to match objects from the sending
         *     to the receiving end
         * @param obj the object being wrapped
         */
        public KeywordWrapper(String key, String id, Serializable obj) {
            this.key = key;
            this.id = id;
            this.obj = obj;
        }

        /**
         * This is used to extract objects on the receiving end. If the class
         * matches, we get the expected object. If the class does not match,
         * we get 'null', and the test should fail.
         *
         * @param clazz the expected class of the 'obj' field
         * @return the object (if 'clazz' matches), 'null' if it does not
         */
        public <T> T getObject(Class<T> clazz) {
            return clazz.isInstance(obj) ? clazz.cast(obj) : null;
        }
    }
}
