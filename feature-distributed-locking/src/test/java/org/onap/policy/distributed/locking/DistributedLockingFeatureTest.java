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

package org.onap.policy.distributed.locking;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.onap.policy.distributed.locking.DistributedLockingFeature.DistributedLock;
import org.onap.policy.drools.core.lock.LockCallback;
import org.onap.policy.drools.core.lock.LockState;
import org.onap.policy.drools.persistence.SystemPersistenceConstants;
import org.onap.policy.drools.system.PolicyEngine;
import org.powermock.reflect.Whitebox;

public class DistributedLockingFeatureTest {
    private static final long EXPIRE_SEC = 900L;
    private static final long RETRY_SEC = 60L;
    private static final String OTHER_OWNER = "other-owner";
    private static final String EXPECTED_EXCEPTION = "expected exception";
    private static final String DB_CONNECTION =
                    "jdbc:h2:mem:pooling;INIT=CREATE SCHEMA IF NOT EXISTS pooling\\;SET SCHEMA pooling";
    private static final String DB_USER = "user";
    private static final String DB_PASSWORD = "password";
    private static final String OWNER_KEY = "my key";
    private static final String RESOURCE = "my resource";
    private static final String RESOURCE2 = "my resource #2";
    private static final String RESOURCE3 = "my resource #3";
    private static final String RESOURCE4 = "my resource #4";
    private static final int HOLD_SEC = 100;
    private static final int HOLD_SEC2 = 120;
    private static final int MAX_THREADS = 20;
    private static final int MAX_LOOPS = 100;
    private static final int TRANSIENT = 500;
    private static final int PERMANENT = 600;

    // number of execute() calls before the first lock attempt
    private static final int PRE_LOCK_EXECS = 1;

    // number of execute() calls before the first schedule attempt
    private static final int PRE_SCHED_EXECS = 1;

    private static Connection conn = null;

    private ScheduledExecutorService exsvc;

    @Mock
    private LockCallback callback;

    private MyOwner owner;

    private DistributedLock lock;

    private DistributedLockingFeature feature;


    /**
     * Configures the location of the property files and creates the DB.
     *
     * @throws SQLException if the DB cannot be created
     */
    @BeforeClass
    public static void setUpBeforeClass() throws SQLException {
        SystemPersistenceConstants.getManager().setConfigurationDir("src/test/resources");

        conn = DriverManager.getConnection(DB_CONNECTION, DB_USER, DB_PASSWORD);

        try (PreparedStatement createStmt = conn.prepareStatement("create table pooling.locks "
                        + "(resourceId VARCHAR(128), host VARCHAR(128), owner VARCHAR(128), "
                        + "expirationTime TIMESTAMP DEFAULT 0, PRIMARY KEY (resourceId))");) {
            createStmt.executeUpdate();
        }
    }

    @AfterClass
    public static void tearDownAfterClass() throws SQLException {
        conn.close();
    }

    /**
     * Initializes the mocks and creates a feature that uses {@link #exsvc} to execute
     * tasks.
     *
     * @throws SQLException if the lock records cannot be deleted from the DB
     */
    @Before
    public void setUp() throws SQLException {
        MockitoAnnotations.initMocks(this);

        owner = new MyOwner();

        cleanDb();

        feature = new MyLockingFeature(true);

        Properties props = new Properties();
        props.setProperty(DistributedLockingFeature.EXTRACTOR_PREFIX + MyOwner.class.getName(), "v.key");
        assertNull(feature.beforeCreate(null, props));
    }

    @After
    public void tearDown() throws SQLException {
        shutdownFeature();
        cleanDb();
    }

    private void cleanDb() throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM pooling.locks")) {
            stmt.executeUpdate();
        }
    }

    private void shutdownFeature() {
        if (feature != null) {
            feature.beforeShutdown((PolicyEngine) null);
            feature = null;
        }
    }

    @Test
    public void testGetSequenceNumber() {
        assertEquals(10, feature.getSequenceNumber());
    }

    @Test
    public void testEnabled() {
        assertTrue(feature.enabled());
    }

    @Test
    public void testAfterStart() {
        // verify that cleanup & expire check are both added to the queue
        verify(exsvc).execute(any());
        verify(exsvc).schedule(any(Runnable.class), anyLong(), any());
    }

    @Test
    public void testAfterStart_ExInProperties() {
        shutdownFeature();

        feature = new MyLockingFeature(false) {
            @Override
            protected Properties getProperties(String fileName) {
                throw new IllegalArgumentException(EXPECTED_EXCEPTION);
            }
        };

        assertThatThrownBy(() -> feature.afterStart((PolicyEngine) null))
                        .isInstanceOf(DistributedLockingFeatureException.class);
    }

    @Test
    public void testAfterStart_ExInThreadPool() {
        shutdownFeature();

        feature = new MyLockingFeature(false) {
            @Override
            protected ScheduledExecutorService makeThreadPool(int nthreads) {
                throw new IllegalArgumentException(EXPECTED_EXCEPTION);
            }
        };

        assertThatThrownBy(() -> feature.afterStart((PolicyEngine) null))
                        .isInstanceOf(DistributedLockingFeatureException.class);
    }

    @Test
    public void testAfterStart_ExWhenClosing() throws SQLException {
        shutdownFeature();

        // create a data source that throws an exception when closed
        BasicDataSource datasrc = mock(BasicDataSource.class);
        doThrow(new SQLException(EXPECTED_EXCEPTION)).when(datasrc).close();

        feature = new MyLockingFeature(false) {
            @Override
            protected BasicDataSource makeDataSource() throws Exception {
                return datasrc;
            }

            @Override
            protected ScheduledExecutorService makeThreadPool(int nthreads) {
                throw new IllegalArgumentException(EXPECTED_EXCEPTION);
            }
        };

        assertThatThrownBy(() -> feature.afterStart((PolicyEngine) null))
                        .isInstanceOf(DistributedLockingFeatureException.class);
    }

    @Test
    public void testDeleteExpiredDbLocks() throws SQLException {
        // add records: two expired, one not
        insertRecord(RESOURCE, feature.getUuidString(), -1);
        insertRecord(RESOURCE2, feature.getUuidString(), HOLD_SEC2);
        insertRecord(RESOURCE3, OTHER_OWNER, 0);
        insertRecord(RESOURCE4, OTHER_OWNER, HOLD_SEC);

        // get the clean-up function and execute it
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(exsvc).execute(captor.capture());

        long tbegin = System.currentTimeMillis();
        Runnable action = captor.getValue();
        action.run();

        assertFalse(recordInRange(RESOURCE, feature.getUuidString(), HOLD_SEC2, tbegin));
        assertTrue(recordInRange(RESOURCE2, feature.getUuidString(), HOLD_SEC2, tbegin));
        assertFalse(recordInRange(RESOURCE3, OTHER_OWNER, HOLD_SEC, tbegin));
        assertTrue(recordInRange(RESOURCE4, OTHER_OWNER, HOLD_SEC, tbegin));

        assertEquals(2, getRecordCount());
    }

    @Test
    public void testDeleteExpiredDbLocks_Ex() throws SQLException {
        feature = new InvalidDbLockingFeature(TRANSIENT);

        // get the clean-up function and execute it
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(exsvc).execute(captor.capture());

        Runnable action = captor.getValue();

        // should not throw an exception
        action.run();
    }

    @Test
    public void testBeforeShutdown() {
        shutdownFeature();
        verify(exsvc).shutdown();

        feature = new DistributedLockingFeature();

        // shutdown without calling afterStart()

        shutdownFeature();
    }

    @Test
    public void testBeforeShutdown_Ex() throws SQLException {
        shutdownFeature();

        // use a data source that throws an exception when closed
        feature = new InvalidDbLockingFeature(TRANSIENT);

        shutdownFeature();
    }

    @Test
    public void testLock() {
        verify(exsvc).execute(any());

        lock = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);
        assertTrue(lock.isWaiting());

        verify(exsvc, times(PRE_LOCK_EXECS + 1)).execute(any());

        // this lock should fail
        LockCallback callback2 = mock(LockCallback.class);
        DistributedLock lock2 = feature.lock(RESOURCE, owner, HOLD_SEC, callback2, false);
        assertTrue(lock2.isUnavailable());
        verify(callback2, never()).lockAvailable(lock2);
        verify(callback2).lockUnavailable(lock2);

        // this should fail, too
        LockCallback callback3 = mock(LockCallback.class);
        DistributedLock lock3 = feature.lock(RESOURCE, owner, HOLD_SEC, callback3, false);
        assertTrue(lock3.isUnavailable());
        verify(callback3, never()).lockAvailable(lock3);
        verify(callback3).lockUnavailable(lock3);

        // no change to first
        assertTrue(lock.isWaiting());

        // no callbacks to the first lock
        verify(callback, never()).lockAvailable(lock);
        verify(callback, never()).lockUnavailable(lock);

        assertTrue(lock.isWaiting());

        runLock(0, 0);
        assertTrue(lock.isActive());
        verify(callback).lockAvailable(lock);
        verify(callback, never()).lockUnavailable(lock);

        // this should succeed
        DistributedLock lock4 = feature.lock(RESOURCE2, owner, HOLD_SEC, callback, false);
        assertTrue(lock4.isWaiting());
    }

    @Test
    public void testCheckExpired() throws SQLException {
        lock = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);
        runLock(0, 0);

        LockCallback callback2 = mock(LockCallback.class);
        final DistributedLock lock2 = feature.lock(RESOURCE2, owner, HOLD_SEC, callback2, false);
        runLock(1, 0);

        LockCallback callback3 = mock(LockCallback.class);
        final DistributedLock lock3 = feature.lock(RESOURCE3, owner, HOLD_SEC, callback3, false);
        runLock(2, 0);

        LockCallback callback4 = mock(LockCallback.class);
        final DistributedLock lock4 = feature.lock(RESOURCE4, owner, HOLD_SEC, callback4, false);
        runLock(3, 0);

        assertEquals(4, getRecordCount());

        // expire one record
        updateRecord(RESOURCE, feature.getUuidString(), -1);

        // change uuid of another record
        updateRecord(RESOURCE3, OTHER_OWNER, HOLD_SEC);


        // run the checker
        runChecker(0, 0, EXPIRE_SEC);


        // check lock states
        assertTrue(lock.isUnavailable());
        assertTrue(lock2.isActive());
        assertTrue(lock3.isUnavailable());
        assertTrue(lock4.isActive());

        // allow callbacks
        runLock(4, 1);
        runLock(5, 0);
        verify(callback).lockUnavailable(lock);
        verify(callback3).lockUnavailable(lock3);

        verify(callback2, never()).lockUnavailable(lock2);
        verify(callback4, never()).lockUnavailable(lock4);


        // another check should have been scheduled, with the normal interval
        runChecker(1, 0, EXPIRE_SEC);
    }

    @Test
    public void testCheckExpired_RetriesExhausted() {
        fail("Not yet implemented");
    }

    @Test
    public void testCheckExpired_ExecRejected() {
        // arrange for execution to be rejected
        when(exsvc.schedule(any(Runnable.class), anyLong(), any()))
                        .thenThrow(new RejectedExecutionException(EXPECTED_EXCEPTION));

        runChecker(0, 0, EXPIRE_SEC);
    }

    @Test
    public void testCheckExpired_SqlEx() {
        // use a data source that throws an exception when getConnection() is called
        feature = new InvalidDbLockingFeature(TRANSIENT);

        runChecker(0, 0, EXPIRE_SEC);

        // it should have scheduled another check, sooner
        runChecker(0, 0, RETRY_SEC);
    }

    @Test
    public void testExpireAllLocks() {
        fail("Not yet implemented");
    }

    @Test
    public void testIdentifyDbLocksSetOfString() {
        fail("Not yet implemented");
    }

    @Test
    public void testIdentifyDbLocksSetOfStringPreparedStatement() {
        fail("Not yet implemented");
    }

    @Test
    public void testExpireLocks() {
        fail("Not yet implemented");
    }

    @Test
    public void testDistributedLock() {
        assertThatIllegalArgumentException().isThrownBy(() -> feature.lock(RESOURCE, owner, -1, callback, false))
                        .withMessageContaining("holdSec");

        // should generate no exception
        feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);
    }

    @Test
    public void testGrant() {
        lock = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);

        // execute the doLock() call
        runLock(0, 0);

        assertTrue(lock.isActive());

        // execute the callback
        runLock(1, 0);

        verify(callback).lockAvailable(lock);
    }

    @Test
    public void testDistributedLockDeny() {
        // get a lock
        feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);

        // get another lock - should fail
        lock = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);

        assertTrue(lock.isUnavailable());

        // run the callback for the second lock
        runLock(1, 0);
        verify(callback).lockUnavailable(lock);
    }

    @Test
    public void testDistributedLockFree() {
        lock = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);

        assertTrue(lock.free());
        assertTrue(lock.isUnavailable());

        // run both requests associated with the lock
        runLock(0, 1);
        runLock(1, 0);

        // should not have changed state
        assertTrue(lock.isUnavailable());

        // attempt to free it again
        assertFalse(lock.free());

        // should not have queued anything else
        verify(exsvc, times(PRE_LOCK_EXECS + 2)).execute(any());

        // new lock should succeed
        DistributedLock lock2 = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);
        assertTrue(lock2 != lock);
        assertTrue(lock2.isWaiting());
    }

    /**
     * Tests the case where the lock is freed and doUnlock called between the call to
     * isUnavailable() and the call to compute().
     */
    @Test
    public void testDistributedLockFree_Unlocked() {
        feature = new FreeWithFreeLockingFeature(true);

        lock = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);

        assertFalse(lock.free());
        assertTrue(lock.isUnavailable());
    }

    /**
     * Tests the case where the lock is freed, but doUnlock is not completed, between the
     * call to isUnavailable() and the call to compute().
     */
    @Test
    public void testDistributedLockFree_LockFreed() {
        feature = new FreeWithFreeLockingFeature(false);

        lock = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);

        assertFalse(lock.free());
        assertTrue(lock.isUnavailable());
    }

    @Test
    public void testDistributedLockExtend() {
        lock = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);

        // lock2 should be denied - called back by this thread
        DistributedLock lock2 = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);
        verify(callback, never()).lockAvailable(lock2);
        verify(callback).lockUnavailable(lock2);

        // lock2 will still be denied - called back by this thread
        lock2.extend(HOLD_SEC, callback);
        verify(callback, times(2)).lockUnavailable(lock2);

        // force lock2 to be active - should still be denied
        Whitebox.setInternalState(lock2, "state", LockState.ACTIVE);
        lock2.extend(HOLD_SEC, callback);
        verify(callback, times(3)).lockUnavailable(lock2);

        assertThatIllegalArgumentException().isThrownBy(() -> lock.extend(-1, callback))
                        .withMessageContaining("holdSec");

        // execute doLock()
        runLock(0, 0);
        assertTrue(lock.isActive());

        // now extend the first lock
        LockCallback callback2 = mock(LockCallback.class);
        lock.extend(HOLD_SEC2, callback2);
        assertTrue(lock.isWaiting());

        // execute doExtend()
        runLock(1, 0);
        lock.extend(HOLD_SEC2, callback2);
        assertEquals(HOLD_SEC2, lock.getHoldSec());
        verify(callback2).lockAvailable(lock);
        verify(callback2, never()).lockUnavailable(lock);
    }

    /**
     * Tests the case where the lock is freed and doUnlock called between the call to
     * isUnavailable() and the call to compute().
     */
    @Test
    public void testDistributedLockExtend_Unlocked() {
        feature = new FreeWithFreeLockingFeature(true);

        lock = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);

        lock.extend(HOLD_SEC2, callback);
        assertTrue(lock.isUnavailable());
        verify(callback).lockUnavailable(lock);
    }

    /**
     * Tests the case where the lock is freed, but doUnlock is not completed, between the
     * call to isUnavailable() and the call to compute().
     */
    @Test
    public void testDistributedLockExtend_LockFreed() {
        feature = new FreeWithFreeLockingFeature(false);

        lock = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);

        lock.extend(HOLD_SEC2, callback);
        assertTrue(lock.isUnavailable());
        verify(callback).lockUnavailable(lock);
    }

    @Test
    public void testDistributedLockScheduleRequest() {
        lock = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);
        runLock(0, 0);

        verify(callback).lockAvailable(lock);
    }

    @Test
    public void testDistributedLockDoRequest() {
        fail("Not yet implemented");
    }

    @Test
    public void testDistributedLockDoRequest_RetriesExhaustedWhileLocking() {
        // use a data source that throws an exception when getConnection() is called
        feature = new InvalidDbLockingFeature(TRANSIENT);

        lock = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);

        // invoke doLock - should fail and reschedule
        runLock(0, 0);

        // should still be waiting
        assertTrue(lock.isWaiting());
        verify(callback, never()).lockUnavailable(lock);

        // try again, via SCHEDULER - first retry fails
        runSchedule(0, 0);

        // should still be waiting
        assertTrue(lock.isWaiting());
        verify(callback, never()).lockUnavailable(lock);

        // try again, via SCHEDULER - final retry fails
        runSchedule(1, 0);
        assertTrue(lock.isUnavailable());

        // now callback should have been called
        verify(callback).lockUnavailable(lock);
    }

    @Test
    public void testDistributedLockDoRequest_RetriesExhaustedWhileUnlocking() {
        /*
         * this feature will create a lock that throws a transient exception when
         * doDbDelete() is invoked
         */
        feature = new MyLockingFeature(true) {
            @Override
            protected DistributedLock makeLock(LockState state, String resourceId, Object ownerInfo, int holdSec,
                            LockCallback callback) {
                return new DistributedLock(state, resourceId, ownerInfo, holdSec, callback) {
                    @Override
                    protected void doDbDelete(Connection conn) throws SQLException {
                        throw new SQLException(EXPECTED_EXCEPTION, "", TRANSIENT);
                    }
                };
            }
        };

        lock = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);
        runLock(0, 0);
        verify(callback).lockAvailable(lock);

        // now free it
        lock.free();

        assertTrue(lock.isUnavailable());

        // run doUnlock() - should fail and reschedule
        runLock(1, 0);

        // try again, via SCHEDULER - first retry fails
        runSchedule(0, 0);

        // should still be unavailable
        assertTrue(lock.isUnavailable());
        verify(callback, never()).lockUnavailable(lock);

        // try again, via SCHEDULER - final retry fails
        runSchedule(1, 0);
        assertTrue(lock.isUnavailable());

        // now callback should never have been called
        verify(callback, never()).lockUnavailable(lock);
    }

    @Test
    public void testDistributedLockDoRequest_NotTransient_WhileLocking() {
        /*
         * use a data source that throws a PERMANENT exception when getConnection() is
         * called
         */
        feature = new InvalidDbLockingFeature(PERMANENT);

        lock = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);

        // invoke doLock - should fail
        runLock(0, 0);

        assertTrue(lock.isUnavailable());
        verify(callback).lockUnavailable(lock);

        // should not have scheduled anything new
        verify(exsvc, times(PRE_LOCK_EXECS + 1)).execute(any());
        verify(exsvc, times(PRE_SCHED_EXECS)).schedule(any(Runnable.class), anyLong(), any());
    }

    @Test
    public void testDistributedLockDoRequest_NotTransient_WhileUnlocking() {
        /*
         * this feature will create a lock that throws a PERMANENT exception when
         * doDbDelete() is invoked
         */
        feature = new MyLockingFeature(true) {
            @Override
            protected DistributedLock makeLock(LockState state, String resourceId, Object ownerInfo, int holdSec,
                            LockCallback callback) {
                return new DistributedLock(state, resourceId, ownerInfo, holdSec, callback) {
                    @Override
                    protected void doDbDelete(Connection conn) throws SQLException {
                        throw new SQLException(EXPECTED_EXCEPTION, "", PERMANENT);
                    }
                };
            }
        };

        lock = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);
        runLock(0, 0);
        verify(callback).lockAvailable(lock);

        // now free it
        lock.free();

        assertTrue(lock.isUnavailable());

        // invoke doUnlock - should fail
        runLock(1, 0);

        assertTrue(lock.isUnavailable());
        verify(callback, never()).lockUnavailable(lock);

        // should not have scheduled anything new
        verify(exsvc, times(PRE_LOCK_EXECS + 2)).execute(any());
        verify(exsvc, times(PRE_SCHED_EXECS)).schedule(any(Runnable.class), anyLong(), any());
    }

    @Test
    public void testDistributedLockDoLock() throws SQLException {
        lock = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);

        // invoke doLock - should simply do an insert
        long tbegin = System.currentTimeMillis();
        runLock(0, 0);

        assertEquals(1, getRecordCount());
        assertTrue(recordInRange(RESOURCE, feature.getUuidString(), HOLD_SEC, tbegin));
        verify(callback).lockAvailable(lock);
    }

    @Test
    public void testDistributedLockDoLock_Ex() {
        // use a data source that throws an exception when getConnection() is called
        feature = new InvalidDbLockingFeature(PERMANENT);

        lock = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);

        // invoke doLock - should simply do an insert
        runLock(0, 0);

        // lock should have failed due to exception
        verify(callback).lockUnavailable(lock);
    }

    @Test
    public void testDistributedLockDoLock_NeedingUpdate() throws SQLException {
        // insert an expired record
        insertRecord(RESOURCE, feature.getUuidString(), 0);

        lock = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);

        // invoke doLock - should simply do an update
        runLock(0, 0);
        verify(callback).lockAvailable(lock);
    }

    @Test
    public void testDistributedLockDoLock_AlreadyLocked() throws SQLException {
        // insert an expired record
        insertRecord(RESOURCE, OTHER_OWNER, HOLD_SEC);

        lock = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);

        // invoke doLock
        runLock(0, 0);

        // lock should have failed because it's already locked
        verify(callback).lockUnavailable(lock);
    }

    @Test
    public void testDistributedLockDoUnlock() throws SQLException {
        lock = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);

        // invoke doLock()
        runLock(0, 0);

        lock.free();

        // invoke doUnlock()
        long tbegin = System.currentTimeMillis();
        runLock(1, 0);

        assertEquals(0, getRecordCount());
        assertFalse(recordInRange(RESOURCE, feature.getUuidString(), HOLD_SEC, tbegin));

        assertTrue(lock.isUnavailable());

        // no more callbacks should have occurred
        verify(callback, times(1)).lockAvailable(lock);
        verify(callback, never()).lockUnavailable(lock);
    }

    @Test
    public void testDistributedLockDoUnlock_Ex() throws SQLException {
        feature = new InvalidDbLockingFeature(PERMANENT);

        lock = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);

        // do NOT invoke doLock() - it will fail without a DB connection

        lock.free();

        // invoke doUnlock()
        runLock(1, 0);

        assertTrue(lock.isUnavailable());

        // no more callbacks should have occurred
        verify(callback, never()).lockAvailable(lock);
        verify(callback, never()).lockUnavailable(lock);
    }

    @Test
    public void testDistributedLockDoExtend() throws SQLException {
        lock = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);
        runLock(0, 0);

        LockCallback callback2 = mock(LockCallback.class);
        lock.extend(HOLD_SEC2, callback2);

        // call doExtend()
        long tbegin = System.currentTimeMillis();
        runLock(1, 0);

        assertEquals(1, getRecordCount());
        assertTrue(recordInRange(RESOURCE, feature.getUuidString(), HOLD_SEC2, tbegin));

        assertTrue(lock.isActive());

        // no more callbacks should have occurred
        verify(callback).lockAvailable(lock);
        verify(callback, never()).lockUnavailable(lock);

        // extension should have succeeded
        verify(callback2).lockAvailable(lock);
        verify(callback2, never()).lockUnavailable(lock);
    }

    @Test
    public void testDistributedLockDoExtend_InsertNeeded() throws SQLException {
        lock = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);
        runLock(0, 0);

        LockCallback callback2 = mock(LockCallback.class);
        lock.extend(HOLD_SEC2, callback2);

        // delete the record so it's forced to re-insert it
        cleanDb();

        // call doExtend()
        long tbegin = System.currentTimeMillis();
        runLock(1, 0);

        assertEquals(1, getRecordCount());
        assertTrue(recordInRange(RESOURCE, feature.getUuidString(), HOLD_SEC2, tbegin));

        assertTrue(lock.isActive());

        // no more callbacks should have occurred
        verify(callback).lockAvailable(lock);
        verify(callback, never()).lockUnavailable(lock);

        // extension should have succeeded
        verify(callback2).lockAvailable(lock);
        verify(callback2, never()).lockUnavailable(lock);
    }

    @Test
    public void testDistributedLockDoExtend_NeitherSucceeds() throws SQLException {
        /*
         * this feature will create a lock that returns false when doDbUpdate() is
         * invoked, or when doDbInsert() is invoked a second time
         */
        feature = new MyLockingFeature(true) {
            @Override
            protected DistributedLock makeLock(LockState state, String resourceId, Object ownerInfo, int holdSec,
                            LockCallback callback) {
                return new DistributedLock(state, resourceId, ownerInfo, holdSec, callback) {
                    private int ntimes = 0;

                    @Override
                    protected boolean doDbInsert(Connection conn) throws SQLException {
                        if (ntimes++ > 0) {
                            return false;
                        }

                        return super.doDbInsert(conn);
                    }

                    @Override
                    protected boolean doDbUpdate(Connection conn) {
                        return false;
                    }
                };
            }
        };

        lock = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);
        runLock(0, 0);

        LockCallback callback2 = mock(LockCallback.class);
        lock.extend(HOLD_SEC2, callback2);

        // call doExtend()
        runLock(1, 0);

        assertTrue(lock.isUnavailable());

        // no more callbacks should have occurred
        verify(callback).lockAvailable(lock);
        verify(callback, never()).lockUnavailable(lock);

        // extension should have failed
        verify(callback2, never()).lockAvailable(lock);
        verify(callback2).lockUnavailable(lock);
    }

    @Test
    public void testDistributedLockDoExtend_Ex() throws SQLException {
        lock = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);
        runLock(0, 0);

        /*
         * delete the record and insert one with a different owner, which will cause
         * doDbInsert() to throw an exception
         */
        cleanDb();
        insertRecord(RESOURCE, OTHER_OWNER, HOLD_SEC);

        LockCallback callback2 = mock(LockCallback.class);
        lock.extend(HOLD_SEC2, callback2);

        // call doExtend()
        runLock(1, 0);

        assertTrue(lock.isUnavailable());

        // no more callbacks should have occurred
        verify(callback).lockAvailable(lock);
        verify(callback, never()).lockUnavailable(lock);

        // extension should have failed
        verify(callback2, never()).lockAvailable(lock);
        verify(callback2).lockUnavailable(lock);
    }

    @Test
    public void testDistributedLockDoDbInsert() {
        fail("Not yet implemented");
    }

    @Test
    public void testDistributedLockDoDbUpdate() {
        fail("Not yet implemented");
    }

    @Test
    public void testDistributedLockDoDbDelete() {
        fail("Not yet implemented");
    }

    @Test
    public void testDistributedLockRemoveFromMap() {
        fail("Not yet implemented");
    }

    @Test
    public void testDistributedLockNotifyAvailable() {
        fail("Not yet implemented");
    }

    @Test
    public void testDistributedLockNotifyUnavailable() {
        fail("Not yet implemented");
    }

    @Test
    public void testMakeThreadPool() {
        // use a REAL feature to test this
        feature = new DistributedLockingFeature();

        // this should create a thread pool
        feature.afterStart((PolicyEngine) null);

        shutdownFeature();
    }

    @Test
    public void testCheckDbRecordsAfterLockOperations() {
        fail("Not yet implemented");
    }

    private void runChecker(int nskip, int nadditional, long schedSec) {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(exsvc, times(nskip + nadditional + 1)).schedule(captor.capture(), eq(schedSec), eq(TimeUnit.SECONDS));
        Runnable action = captor.getAllValues().get(nskip);
        action.run();
    }

    void runLock(int nskip, int nadditional) {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(exsvc, times(PRE_LOCK_EXECS + nskip + nadditional + 1)).execute(captor.capture());

        Runnable action = captor.getAllValues().get(PRE_LOCK_EXECS + nskip);
        action.run();
    }

    void runSchedule(int nskip, int nadditional) {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(exsvc, times(PRE_SCHED_EXECS + nskip + nadditional + 1)).schedule(captor.capture(), anyLong(), any());

        Runnable action = captor.getAllValues().get(PRE_SCHED_EXECS + nskip);
        action.run();
    }

    private int getRecordCount() throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT count(*) FROM pooling.locks");
                        ResultSet result = stmt.executeQuery()) {

            if (result.next()) {
                return result.getInt(1);

            } else {
                return 0;
            }
        }
    }

    private boolean recordInRange(String resourceId, String uuidString, int holdSec, long tbegin) throws SQLException {
        try (PreparedStatement stmt =
                        conn.prepareStatement("SELECT timestampdiff(second, now(), expirationTime) FROM pooling.locks"
                                        + " WHERE resourceId=? AND host=? AND owner=?")) {

            stmt.setString(1, resourceId);
            stmt.setString(2, feature.getHostName());
            stmt.setString(3, uuidString);

            try (ResultSet result = stmt.executeQuery()) {
                if (result.next()) {
                    int remaining = result.getInt(1);
                    long maxDiff = System.currentTimeMillis() - tbegin;
                    return (remaining >= 0 && holdSec - remaining <= maxDiff);

                } else {
                    return false;
                }
            }
        }
    }

    private void insertRecord(String resourceId, String uuidString, int expireOffset) throws SQLException {
        this.insertRecord(resourceId, feature.getHostName(), uuidString, expireOffset);
    }

    private void insertRecord(String resourceId, String hostName, String uuidString, int expireOffset)
                    throws SQLException {
        try (PreparedStatement stmt =
                        conn.prepareStatement("INSERT INTO pooling.locks (resourceId, host, owner, expirationTime) "
                                        + "values (?, ?, ?, timestampadd(second, ?, now()))")) {

            stmt.setString(1, resourceId);
            stmt.setString(2, hostName);
            stmt.setString(3, uuidString);
            stmt.setInt(4, expireOffset);

            assertEquals(1, stmt.executeUpdate());
        }
    }

    private void updateRecord(String resourceId, String newUuid, int expireOffset) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("UPDATE pooling.locks SET owner=?,"
                        + " expirationTime=timestampadd(second, ?, now()) WHERE resourceId=?")) {

            stmt.setString(1, newUuid);
            stmt.setInt(2, expireOffset);
            stmt.setString(3, resourceId);

            assertEquals(1, stmt.executeUpdate());
        }
    }

    private class MyLockingFeature extends DistributedLockingFeature {

        public MyLockingFeature(boolean init) {
            shutdownFeature();

            if (init) {
                afterStart((PolicyEngine) null);
            }
        }

        @Override
        protected ScheduledExecutorService makeThreadPool(int nthreads) {
            exsvc = mock(ScheduledExecutorService.class);
            return exsvc;
        }
    }

    private class InvalidDbLockingFeature extends MyLockingFeature {
        private int errcode;

        public InvalidDbLockingFeature(int errcode) {
            // pass "false" because we have to set the error code before calling
            // afterStart()
            super(false);

            this.errcode = errcode;

            this.afterStart((PolicyEngine) null);
        }

        @Override
        protected BasicDataSource makeDataSource() throws Exception {
            BasicDataSource datasrc = mock(BasicDataSource.class);

            when(datasrc.getConnection()).thenThrow(new SQLException(EXPECTED_EXCEPTION, "", errcode));
            doThrow(new SQLException(EXPECTED_EXCEPTION, "", errcode)).when(datasrc).close();

            return datasrc;
        }
    }

    private class FreeWithFreeLockingFeature extends MyLockingFeature {
        private boolean unlock;

        public FreeWithFreeLockingFeature(boolean unlock) {
            super(true);
            this.unlock = unlock;
        }

        @Override
        protected DistributedLock makeLock(LockState state, String resourceId, Object ownerInfo, int holdSec,
                        LockCallback callback) {

            return new DistributedLock(state, resourceId, ownerInfo, holdSec, callback) {
                private boolean checked = false;

                @Override
                public boolean isUnavailable() {
                    if (checked) {
                        return super.isUnavailable();
                    }

                    checked = true;

                    // release and run doUnlock
                    free();

                    if (unlock) {
                        runLock(1, 0);
                    }

                    return false;
                }
            };
        }
    }

    @Getter
    public static class MyOwner {
        private String key = OWNER_KEY;
    }
}
