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

package org.onap.policy.distributed.locking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTransientException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.kie.api.runtime.KieSession;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.onap.policy.common.utils.services.OrderedServiceImpl;
import org.onap.policy.distributed.locking.DistributedLockManager.DistributedLock;
import org.onap.policy.drools.core.PolicySession;
import org.onap.policy.drools.core.lock.Lock;
import org.onap.policy.drools.core.lock.LockCallback;
import org.onap.policy.drools.core.lock.LockState;
import org.onap.policy.drools.features.PolicyEngineFeatureApi;
import org.onap.policy.drools.persistence.SystemPersistenceConstants;
import org.onap.policy.drools.system.PolicyEngine;
import org.onap.policy.drools.system.PolicyEngineConstants;
import org.powermock.reflect.Whitebox;

public class DistributedLockManagerTest {
    private static final long EXPIRE_SEC = 900L;
    private static final long RETRY_SEC = 60L;
    private static final String POLICY_ENGINE_EXECUTOR_FIELD = "executorService";
    private static final String OTHER_HOST = "other-host";
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
    private static final String RESOURCE5 = "my resource #5";
    private static final int HOLD_SEC = 100;
    private static final int HOLD_SEC2 = 120;
    private static final int MAX_THREADS = 5;
    private static final int MAX_LOOPS = 100;
    private static final boolean TRANSIENT = true;
    private static final boolean PERMANENT = false;

    // number of execute() calls before the first lock attempt
    private static final int PRE_LOCK_EXECS = 1;

    // number of execute() calls before the first schedule attempt
    private static final int PRE_SCHED_EXECS = 1;

    private static Connection conn = null;
    private static ScheduledExecutorService saveExec;
    private static ScheduledExecutorService realExec;

    @Mock
    private PolicyEngine engine;

    @Mock
    private KieSession kieSess;

    @Mock
    private ScheduledExecutorService exsvc;

    @Mock
    private ScheduledFuture<?> checker;

    @Mock
    private LockCallback callback;

    @Mock
    private BasicDataSource datasrc;

    private DistributedLock lock;
    private PolicySession session;

    private AtomicInteger nactive;
    private AtomicInteger nsuccesses;
    private DistributedLockManager feature;


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
                        + "expirationTime TIMESTAMP DEFAULT 0, PRIMARY KEY (resourceId))")) {
            createStmt.executeUpdate();
        }

        saveExec = Whitebox.getInternalState(PolicyEngineConstants.getManager(), POLICY_ENGINE_EXECUTOR_FIELD);

        realExec = Executors.newScheduledThreadPool(3);
    }

    /**
     * Restores static fields.
     */
    @AfterClass
    public static void tearDownAfterClass() throws SQLException {
        Whitebox.setInternalState(PolicyEngineConstants.getManager(), POLICY_ENGINE_EXECUTOR_FIELD, saveExec);
        realExec.shutdown();
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

        // grant() and deny() calls will come through here and be immediately executed
        session = new PolicySession(null, null, kieSess) {
            @Override
            public void insertDrools(Object object) {
                ((Runnable) object).run();
            }
        };

        session.setPolicySession();

        nactive = new AtomicInteger(0);
        nsuccesses = new AtomicInteger(0);

        cleanDb();

        feature = new MyLockingFeature(true);
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
            feature.afterStop(engine);
            feature = null;
        }
    }

    /**
     * Tests that the feature is found in the expected service sets.
     */
    @Test
    public void testServiceApis() {
        assertTrue(new OrderedServiceImpl<>(PolicyEngineFeatureApi.class).getList().stream()
                        .anyMatch(obj -> obj instanceof DistributedLockManager));
    }

    @Test
    public void testGetSequenceNumber() {
        assertEquals(1000, feature.getSequenceNumber());
    }

    @Test
    public void testBeforeCreateLockManager() {
        assertSame(feature, feature.beforeCreateLockManager(engine, new Properties()));
    }

    /**
     * Tests beforeCreate(), when getProperties() throws a runtime exception.
     */
    @Test
    public void testBeforeCreateLockManagerEx() {
        shutdownFeature();

        feature = new MyLockingFeature(false) {
            @Override
            protected Properties getProperties(String fileName) {
                throw new IllegalArgumentException(EXPECTED_EXCEPTION);
            }
        };

        Properties props = new Properties();
        assertThatThrownBy(() -> feature.beforeCreateLockManager(engine, props))
                        .isInstanceOf(DistributedLockManagerException.class);
    }

    @Test
    public void testAfterStart() {
        // verify that cleanup & expire check are both added to the queue
        verify(exsvc).execute(any());
        verify(exsvc).schedule(any(Runnable.class), anyLong(), any());
    }

    /**
     * Tests afterStart(), when thread pool throws a runtime exception.
     */
    @Test
    public void testAfterStartExInThreadPool() {
        shutdownFeature();

        feature = new MyLockingFeature(false);

        doThrow(new IllegalArgumentException(EXPECTED_EXCEPTION)).when(exsvc).execute(any());

        assertThatThrownBy(() -> feature.afterStart(engine)).isInstanceOf(DistributedLockManagerException.class);
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

    /**
     * Tests deleteExpiredDbLocks(), when getConnection() throws an exception.
     *
     * @throws SQLException if an error occurs
     */
    @Test
    public void testDeleteExpiredDbLocksEx() throws SQLException {
        feature = new InvalidDbLockingFeature(TRANSIENT);

        // get the clean-up function and execute it
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(exsvc).execute(captor.capture());

        Runnable action = captor.getValue();

        // should not throw an exception
        action.run();
    }

    @Test
    public void testAfterStop() {
        shutdownFeature();
        verify(checker).cancel(anyBoolean());

        feature = new DistributedLockManager();

        // shutdown without calling afterStart()

        shutdownFeature();
    }

    /**
     * Tests afterStop(), when the data source throws an exception when close() is called.
     *
     * @throws SQLException if an error occurs
     */
    @Test
    public void testAfterStopEx() throws SQLException {
        shutdownFeature();

        // use a data source that throws an exception when closed
        feature = new InvalidDbLockingFeature(TRANSIENT);

        assertThatCode(() -> shutdownFeature()).doesNotThrowAnyException();
    }

    @Test
    public void testCreateLock() throws SQLException {
        verify(exsvc).execute(any());

        lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);
        assertTrue(lock.isWaiting());

        verify(exsvc, times(PRE_LOCK_EXECS + 1)).execute(any());

        // this lock should fail
        LockCallback callback2 = mock(LockCallback.class);
        DistributedLock lock2 = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback2, false);
        assertTrue(lock2.isUnavailable());
        verify(callback2, never()).lockAvailable(lock2);
        verify(callback2).lockUnavailable(lock2);

        // this should fail, too
        LockCallback callback3 = mock(LockCallback.class);
        DistributedLock lock3 = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback3, false);
        assertTrue(lock3.isUnavailable());
        verify(callback3, never()).lockAvailable(lock3);
        verify(callback3).lockUnavailable(lock3);

        // no change to first
        assertTrue(lock.isWaiting());

        // no callbacks to the first lock
        verify(callback, never()).lockAvailable(lock);
        verify(callback, never()).lockUnavailable(lock);

        assertTrue(lock.isWaiting());
        assertEquals(0, getRecordCount());

        runLock(0, 0);
        assertTrue(lock.isActive());
        assertEquals(1, getRecordCount());

        verify(callback).lockAvailable(lock);
        verify(callback, never()).lockUnavailable(lock);

        // this should succeed
        DistributedLock lock4 = getLock(RESOURCE2, OWNER_KEY, HOLD_SEC, callback, false);
        assertTrue(lock4.isWaiting());

        // after running checker, original records should still remain
        runChecker(0, 0, EXPIRE_SEC);
        assertEquals(1, getRecordCount());
        verify(callback, never()).lockUnavailable(lock);
    }

    /**
     * Tests createLock() when the feature is not the latest instance.
     */
    @Test
    public void testCreateLockNotLatestInstance() {
        DistributedLockManager.setLatestInstance(null);

        Lock lock = feature.createLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);
        assertTrue(lock.isUnavailable());
        verify(callback, never()).lockAvailable(any());
        verify(callback).lockUnavailable(lock);
    }

    @Test
    public void testCheckExpired() throws SQLException {
        lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);
        runLock(0, 0);

        LockCallback callback2 = mock(LockCallback.class);
        final DistributedLock lock2 = getLock(RESOURCE2, OWNER_KEY, HOLD_SEC, callback2, false);
        runLock(1, 0);

        LockCallback callback3 = mock(LockCallback.class);
        final DistributedLock lock3 = getLock(RESOURCE3, OWNER_KEY, HOLD_SEC, callback3, false);
        runLock(2, 0);

        LockCallback callback4 = mock(LockCallback.class);
        final DistributedLock lock4 = getLock(RESOURCE4, OWNER_KEY, HOLD_SEC, callback4, false);
        runLock(3, 0);

        LockCallback callback5 = mock(LockCallback.class);
        final DistributedLock lock5 = getLock(RESOURCE5, OWNER_KEY, HOLD_SEC, callback5, false);
        runLock(4, 0);

        assertEquals(5, getRecordCount());

        // expire one record
        updateRecord(RESOURCE, feature.getHostName(), feature.getUuidString(), -1);

        // change host of another record
        updateRecord(RESOURCE3, OTHER_HOST, feature.getUuidString(), HOLD_SEC);

        // change uuid of another record
        updateRecord(RESOURCE5, feature.getHostName(), OTHER_OWNER, HOLD_SEC);


        // run the checker
        runChecker(0, 0, EXPIRE_SEC);


        // check lock states
        assertTrue(lock.isUnavailable());
        assertTrue(lock2.isActive());
        assertTrue(lock3.isUnavailable());
        assertTrue(lock4.isActive());
        assertTrue(lock5.isUnavailable());

        // allow callbacks
        runLock(2, 2);
        runLock(3, 1);
        runLock(4, 0);
        verify(callback).lockUnavailable(lock);
        verify(callback3).lockUnavailable(lock3);
        verify(callback5).lockUnavailable(lock5);

        verify(callback2, never()).lockUnavailable(lock2);
        verify(callback4, never()).lockUnavailable(lock4);


        // another check should have been scheduled, with the normal interval
        runChecker(1, 0, EXPIRE_SEC);
    }

    /**
     * Tests checkExpired(), when schedule() throws an exception.
     */
    @Test
    public void testCheckExpiredExecRejected() {
        // arrange for execution to be rejected
        when(exsvc.schedule(any(Runnable.class), anyLong(), any()))
                        .thenThrow(new RejectedExecutionException(EXPECTED_EXCEPTION));

        runChecker(0, 0, EXPIRE_SEC);
    }

    /**
     * Tests checkExpired(), when getConnection() throws an exception.
     */
    @Test
    public void testCheckExpiredSqlEx() {
        // use a data source that throws an exception when getConnection() is called
        feature = new InvalidDbLockingFeature(TRANSIENT);

        runChecker(0, 0, EXPIRE_SEC);

        // it should have scheduled another check, sooner
        runChecker(0, 0, RETRY_SEC);
    }

    /**
     * Tests checkExpired(), when getConnection() throws an exception and the feature is
     * no longer alive.
     */
    @Test
    public void testCheckExpiredSqlExFeatureStopped() {
        // use a data source that throws an exception when getConnection() is called
        feature = new InvalidDbLockingFeature(TRANSIENT) {
            @Override
            protected SQLException makeEx() {
                this.stop();
                return super.makeEx();
            }
        };

        runChecker(0, 0, EXPIRE_SEC);

        // it should NOT have scheduled another check
        verify(exsvc, times(1)).schedule(any(Runnable.class), anyLong(), any());
    }

    @Test
    public void testExpireLocks() throws SQLException {
        AtomicReference<DistributedLock> freeLock = new AtomicReference<>(null);

        feature = new MyLockingFeature(true) {
            @Override
            protected BasicDataSource makeDataSource() throws Exception {
                // get the real data source
                BasicDataSource src2 = super.makeDataSource();

                when(datasrc.getConnection()).thenAnswer(answer -> {
                    DistributedLock lck = freeLock.getAndSet(null);
                    if (lck != null) {
                        // free it
                        lck.free();

                        // run its doUnlock
                        runLock(4, 0);
                    }

                    return src2.getConnection();
                });

                return datasrc;
            }
        };

        lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);
        runLock(0, 0);

        LockCallback callback2 = mock(LockCallback.class);
        final DistributedLock lock2 = getLock(RESOURCE2, OWNER_KEY, HOLD_SEC, callback2, false);
        runLock(1, 0);

        LockCallback callback3 = mock(LockCallback.class);
        final DistributedLock lock3 = getLock(RESOURCE3, OWNER_KEY, HOLD_SEC, callback3, false);
        // don't run doLock for lock3 - leave it in the waiting state

        LockCallback callback4 = mock(LockCallback.class);
        final DistributedLock lock4 = getLock(RESOURCE4, OWNER_KEY, HOLD_SEC, callback4, false);
        runLock(3, 0);

        assertEquals(3, getRecordCount());

        // expire one record
        updateRecord(RESOURCE, feature.getHostName(), feature.getUuidString(), -1);

        // arrange to free lock4 while the checker is running
        freeLock.set(lock4);

        // run the checker
        runChecker(0, 0, EXPIRE_SEC);


        // check lock states
        assertTrue(lock.isUnavailable());
        assertTrue(lock2.isActive());
        assertTrue(lock3.isWaiting());
        assertTrue(lock4.isUnavailable());

        runLock(4, 0);
        verify(exsvc, times(PRE_LOCK_EXECS + 5)).execute(any());

        verify(callback).lockUnavailable(lock);
        verify(callback2, never()).lockUnavailable(lock2);
        verify(callback3, never()).lockUnavailable(lock3);
        verify(callback4, never()).lockUnavailable(lock4);
    }

    @Test
    public void testDistributedLockNoArgs() {
        DistributedLock lock = new DistributedLock();
        assertNull(lock.getResourceId());
        assertNull(lock.getOwnerKey());
        assertNull(lock.getCallback());
        assertEquals(0, lock.getHoldSec());
    }

    @Test
    public void testDistributedLock() {
        assertThatIllegalArgumentException()
                        .isThrownBy(() -> feature.createLock(RESOURCE, OWNER_KEY, -1, callback, false))
                        .withMessageContaining("holdSec");

        // should generate no exception
        feature.createLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);
    }

    @Test
    public void testDistributedLockSerializable() throws Exception {
        DistributedLock lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);
        lock = roundTrip(lock);

        assertTrue(lock.isWaiting());

        assertEquals(RESOURCE, lock.getResourceId());
        assertEquals(OWNER_KEY, lock.getOwnerKey());
        assertNull(lock.getCallback());
        assertEquals(HOLD_SEC, lock.getHoldSec());
    }

    @Test
    public void testGrant() {
        lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);
        assertFalse(lock.isActive());

        // execute the doLock() call
        runLock(0, 0);

        assertTrue(lock.isActive());

        // the callback for the lock should have been run in the foreground thread
        verify(callback).lockAvailable(lock);
    }

    @Test
    public void testDistributedLockDeny() {
        // get a lock
        feature.createLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);

        // get another lock - should fail
        lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);

        assertTrue(lock.isUnavailable());

        // the callback for the second lock should have been run in the foreground thread
        verify(callback).lockUnavailable(lock);

        // should only have a request for the first lock
        verify(exsvc, times(PRE_LOCK_EXECS + 1)).execute(any());
    }

    @Test
    public void testDistributedLockFree() {
        lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);

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
        DistributedLock lock2 = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);
        assertNotSame(lock2, lock);
        assertTrue(lock2.isWaiting());
    }

    /**
     * Tests that free() works on a serialized lock with a new feature.
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void testDistributedLockFreeSerialized() throws Exception {
        DistributedLock lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);

        feature = new MyLockingFeature(true);

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
    public void testDistributedLockFreeNoFeature() throws Exception {
        DistributedLock lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);

        DistributedLockManager.setLatestInstance(null);

        lock = roundTrip(lock);
        assertFalse(lock.free());
        assertTrue(lock.isUnavailable());
    }

    /**
     * Tests the case where the lock is freed and doUnlock called between the call to
     * isUnavailable() and the call to compute().
     */
    @Test
    public void testDistributedLockFreeUnlocked() {
        feature = new FreeWithFreeLockingFeature(true);

        lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);

        assertFalse(lock.free());
        assertTrue(lock.isUnavailable());
    }

    /**
     * Tests the case where the lock is freed, but doUnlock is not completed, between the
     * call to isUnavailable() and the call to compute().
     */
    @Test
    public void testDistributedLockFreeLockFreed() {
        feature = new FreeWithFreeLockingFeature(false);

        lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);

        assertFalse(lock.free());
        assertTrue(lock.isUnavailable());
    }

    @Test
    public void testDistributedLockExtend() {
        lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);

        // lock2 should be denied - called back by this thread
        DistributedLock lock2 = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);
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
     * Tests that extend() works on a serialized lock with a new feature.
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void testDistributedLockExtendSerialized() throws Exception {
        DistributedLock lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);

        // run doLock
        runLock(0, 0);
        assertTrue(lock.isActive());

        feature = new MyLockingFeature(true);

        lock = roundTrip(lock);
        assertTrue(lock.isActive());

        LockCallback scallback = mock(LockCallback.class);

        lock.extend(HOLD_SEC, scallback);
        assertTrue(lock.isWaiting());

        // run doExtend (in new feature)
        runLock(0, 0);
        assertTrue(lock.isActive());

        verify(scallback).lockAvailable(lock);
        verify(scallback, never()).lockUnavailable(lock);
    }

    /**
     * Tests extend() on a serialized lock without a feature.
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void testDistributedLockExtendNoFeature() throws Exception {
        DistributedLock lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);

        // run doLock
        runLock(0, 0);
        assertTrue(lock.isActive());

        DistributedLockManager.setLatestInstance(null);

        lock = roundTrip(lock);
        assertTrue(lock.isActive());

        LockCallback scallback = mock(LockCallback.class);

        lock.extend(HOLD_SEC, scallback);
        assertTrue(lock.isUnavailable());

        verify(scallback, never()).lockAvailable(lock);
        verify(scallback).lockUnavailable(lock);
    }

    /**
     * Tests the case where the lock is freed and doUnlock called between the call to
     * isUnavailable() and the call to compute().
     */
    @Test
    public void testDistributedLockExtendUnlocked() {
        feature = new FreeWithFreeLockingFeature(true);

        lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);

        lock.extend(HOLD_SEC2, callback);
        assertTrue(lock.isUnavailable());
        verify(callback).lockUnavailable(lock);
    }

    /**
     * Tests the case where the lock is freed, but doUnlock is not completed, between the
     * call to isUnavailable() and the call to compute().
     */
    @Test
    public void testDistributedLockExtendLockFreed() {
        feature = new FreeWithFreeLockingFeature(false);

        lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);

        lock.extend(HOLD_SEC2, callback);
        assertTrue(lock.isUnavailable());
        verify(callback).lockUnavailable(lock);
    }

    @Test
    public void testDistributedLockScheduleRequest() {
        lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);
        runLock(0, 0);

        verify(callback).lockAvailable(lock);
    }

    @Test
    public void testDistributedLockRescheduleRequest() throws SQLException {
        // use a data source that throws an exception when getConnection() is called
        InvalidDbLockingFeature invfeat = new InvalidDbLockingFeature(TRANSIENT);
        feature = invfeat;

        lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);

        // invoke doLock - should fail and reschedule
        runLock(0, 0);

        // should still be waiting
        assertTrue(lock.isWaiting());
        verify(callback, never()).lockUnavailable(lock);

        // free the lock while doLock is executing
        invfeat.freeLock = true;

        // try scheduled request - should just invoke doUnlock
        runSchedule(0, 0);

        // should still be waiting
        assertTrue(lock.isUnavailable());
        verify(callback, never()).lockUnavailable(lock);

        // should have scheduled a retry of doUnlock
        verify(exsvc, times(PRE_SCHED_EXECS + 2)).schedule(any(Runnable.class), anyLong(), any());
    }

    @Test
    public void testDistributedLockGetNextRequest() {
        lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);

        /*
         * run doLock. This should cause getNextRequest() to be called twice, once with a
         * request in the queue, and the second time with request=null.
         */
        runLock(0, 0);
    }

    /**
     * Tests getNextRequest(), where the same request is still in the queue the second
     * time it's called.
     */
    @Test
    public void testDistributedLockGetNextRequestSameRequest() {
        // force reschedule to be invoked
        feature = new InvalidDbLockingFeature(TRANSIENT);

        lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);

        /*
         * run doLock. This should cause getNextRequest() to be called twice, once with a
         * request in the queue, and the second time with the same request again.
         */
        runLock(0, 0);

        verify(exsvc, times(PRE_SCHED_EXECS + 1)).schedule(any(Runnable.class), anyLong(), any());
    }

    @Test
    public void testDistributedLockDoRequest() throws SQLException {
        lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);

        assertTrue(lock.isWaiting());

        // run doLock via doRequest
        runLock(0, 0);

        assertTrue(lock.isActive());
    }

    /**
     * Tests doRequest(), when doRequest() is already running within another thread.
     */
    @Test
    public void testDistributedLockDoRequestBusy() {
        /*
         * this feature will invoke a request in a background thread while it's being run
         * in a foreground thread.
         */
        AtomicBoolean running = new AtomicBoolean(false);
        AtomicBoolean returned = new AtomicBoolean(false);

        feature = new MyLockingFeature(true) {
            @Override
            protected DistributedLock makeLock(LockState state, String resourceId, String ownerKey, int holdSec,
                            LockCallback callback) {
                return new DistributedLock(state, resourceId, ownerKey, holdSec, callback, feature) {
                    private static final long serialVersionUID = 1L;

                    @Override
                    protected boolean doDbInsert(Connection conn) throws SQLException {
                        if (running.get()) {
                            // already inside the thread - don't recurse any further
                            return super.doDbInsert(conn);
                        }

                        running.set(true);

                        Thread thread = new Thread(() -> {
                            // run doLock from within the new thread
                            runLock(0, 0);
                        });
                        thread.setDaemon(true);
                        thread.start();

                        // wait for the background thread to complete before continuing
                        try {
                            thread.join(5000);
                        } catch (InterruptedException ignore) {
                            Thread.currentThread().interrupt();
                        }

                        returned.set(!thread.isAlive());

                        return super.doDbInsert(conn);
                    }
                };
            }
        };

        lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);

        // run doLock
        runLock(0, 0);

        assertTrue(returned.get());
    }

    /**
     * Tests doRequest() when an exception occurs while the lock is in the WAITING state.
     *
     * @throws SQLException if an error occurs
     */
    @Test
    public void testDistributedLockDoRequestRunExWaiting() throws SQLException {
        // throw run-time exception
        when(datasrc.getConnection()).thenThrow(new IllegalStateException(EXPECTED_EXCEPTION));

        // use a data source that throws an exception when getConnection() is called
        feature = new MyLockingFeature(true) {
            @Override
            protected BasicDataSource makeDataSource() throws Exception {
                return datasrc;
            }
        };

        lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);

        // invoke doLock - should NOT reschedule
        runLock(0, 0);

        assertTrue(lock.isUnavailable());
        verify(callback).lockUnavailable(lock);

        verify(exsvc, times(PRE_SCHED_EXECS)).schedule(any(Runnable.class), anyLong(), any());
    }

    /**
     * Tests doRequest() when an exception occurs while the lock is in the UNAVAILABLE
     * state.
     *
     * @throws SQLException if an error occurs
     */
    @Test
    public void testDistributedLockDoRequestRunExUnavailable() throws SQLException {
        // throw run-time exception
        when(datasrc.getConnection()).thenAnswer(answer -> {
            lock.free();
            throw new IllegalStateException(EXPECTED_EXCEPTION);
        });

        // use a data source that throws an exception when getConnection() is called
        feature = new MyLockingFeature(true) {
            @Override
            protected BasicDataSource makeDataSource() throws Exception {
                return datasrc;
            }
        };

        lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);

        // invoke doLock - should NOT reschedule
        runLock(0, 0);

        assertTrue(lock.isUnavailable());
        verify(callback, never()).lockUnavailable(lock);

        verify(exsvc, times(PRE_SCHED_EXECS)).schedule(any(Runnable.class), anyLong(), any());
    }

    /**
     * Tests doRequest() when the retry count gets exhausted.
     */
    @Test
    public void testDistributedLockDoRequestRetriesExhaustedWhileLocking() {
        // use a data source that throws an exception when getConnection() is called
        feature = new InvalidDbLockingFeature(TRANSIENT);

        lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);

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

    /**
     * Tests doRequest() when a non-transient DB exception is thrown.
     */
    @Test
    public void testDistributedLockDoRequestNotTransient() {
        /*
         * use a data source that throws a PERMANENT exception when getConnection() is
         * called
         */
        feature = new InvalidDbLockingFeature(PERMANENT);

        lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);

        // invoke doLock - should fail
        runLock(0, 0);

        assertTrue(lock.isUnavailable());
        verify(callback).lockUnavailable(lock);

        // should not have scheduled anything new
        verify(exsvc, times(PRE_LOCK_EXECS + 1)).execute(any());
        verify(exsvc, times(PRE_SCHED_EXECS)).schedule(any(Runnable.class), anyLong(), any());
    }

    @Test
    public void testDistributedLockDoLock() throws SQLException {
        lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);

        // invoke doLock - should simply do an insert
        long tbegin = System.currentTimeMillis();
        runLock(0, 0);

        assertEquals(1, getRecordCount());
        assertTrue(recordInRange(RESOURCE, feature.getUuidString(), HOLD_SEC, tbegin));
        verify(callback).lockAvailable(lock);
    }

    /**
     * Tests doLock() when the lock is freed before doLock runs.
     *
     * @throws SQLException if an error occurs
     */
    @Test
    public void testDistributedLockDoLockFreed() throws SQLException {
        lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);

        lock.setState(LockState.UNAVAILABLE);

        // invoke doLock - should do nothing
        runLock(0, 0);

        assertEquals(0, getRecordCount());

        verify(callback, never()).lockAvailable(lock);
    }

    /**
     * Tests doLock() when a DB exception is thrown.
     */
    @Test
    public void testDistributedLockDoLockEx() {
        // use a data source that throws an exception when getConnection() is called
        feature = new InvalidDbLockingFeature(PERMANENT);

        lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);

        // invoke doLock - should simply do an insert
        runLock(0, 0);

        // lock should have failed due to exception
        verify(callback).lockUnavailable(lock);
    }

    /**
     * Tests doLock() when an (expired) record already exists, thus requiring doUpdate()
     * to be called.
     */
    @Test
    public void testDistributedLockDoLockNeedingUpdate() throws SQLException {
        // insert an expired record
        insertRecord(RESOURCE, feature.getUuidString(), 0);

        lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);

        // invoke doLock - should simply do an update
        runLock(0, 0);
        verify(callback).lockAvailable(lock);
    }

    /**
     * Tests doLock() when a locked record already exists.
     */
    @Test
    public void testDistributedLockDoLockAlreadyLocked() throws SQLException {
        // insert an expired record
        insertRecord(RESOURCE, OTHER_OWNER, HOLD_SEC);

        lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);

        // invoke doLock
        runLock(0, 0);

        // lock should have failed because it's already locked
        verify(callback).lockUnavailable(lock);
    }

    @Test
    public void testDistributedLockDoUnlock() throws SQLException {
        lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);

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

    /**
     * Tests doUnlock() when a DB exception is thrown.
     *
     * @throws SQLException if an error occurs
     */
    @Test
    public void testDistributedLockDoUnlockEx() throws SQLException {
        feature = new InvalidDbLockingFeature(PERMANENT);

        lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);

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
        lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);
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

    /**
     * Tests doExtend() when the lock is freed before doExtend runs.
     *
     * @throws SQLException if an error occurs
     */
    @Test
    public void testDistributedLockDoExtendFreed() throws SQLException {
        lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);
        lock.extend(HOLD_SEC2, callback);

        lock.setState(LockState.UNAVAILABLE);

        // invoke doExtend - should do nothing
        runLock(1, 0);

        assertEquals(0, getRecordCount());

        verify(callback, never()).lockAvailable(lock);
    }

    /**
     * Tests doExtend() when the lock record is missing from the DB, thus requiring an
     * insert.
     *
     * @throws SQLException if an error occurs
     */
    @Test
    public void testDistributedLockDoExtendInsertNeeded() throws SQLException {
        lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);
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

    /**
     * Tests doExtend() when both update and insert fail.
     *
     * @throws SQLException if an error occurs
     */
    @Test
    public void testDistributedLockDoExtendNeitherSucceeds() throws SQLException {
        /*
         * this feature will create a lock that returns false when doDbUpdate() is
         * invoked, or when doDbInsert() is invoked a second time
         */
        feature = new MyLockingFeature(true) {
            @Override
            protected DistributedLock makeLock(LockState state, String resourceId, String ownerKey, int holdSec,
                            LockCallback callback) {
                return new DistributedLock(state, resourceId, ownerKey, holdSec, callback, feature) {
                    private static final long serialVersionUID = 1L;
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

        lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);
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

    /**
     * Tests doExtend() when an exception occurs.
     *
     * @throws SQLException if an error occurs
     */
    @Test
    public void testDistributedLockDoExtendEx() throws SQLException {
        lock = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false);
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
    public void testDistributedLockToString() {
        String text = getLock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, false).toString();
        assertNotNull(text);
        assertThat(text).doesNotContain("ownerInfo").doesNotContain("callback");
    }

    @Test
    public void testMakeThreadPool() {
        // use a REAL feature to test this
        feature = new DistributedLockManager();

        // this should create a thread pool
        feature.beforeCreateLockManager(engine, new Properties());
        feature.afterStart(engine);

        assertThatCode(() -> shutdownFeature()).doesNotThrowAnyException();
    }

    /**
     * Performs a multi-threaded test of the locking facility.
     *
     * @throws InterruptedException if the current thread is interrupted while waiting for
     *         the background threads to complete
     */
    @Test
    public void testMultiThreaded() throws InterruptedException {
        Whitebox.setInternalState(PolicyEngineConstants.getManager(), POLICY_ENGINE_EXECUTOR_FIELD, realExec);

        feature = new DistributedLockManager();
        feature.beforeCreateLockManager(PolicyEngineConstants.getManager(), new Properties());
        feature.afterStart(PolicyEngineConstants.getManager());

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

    private DistributedLock getLock(String resource, String ownerKey, int holdSec, LockCallback callback,
                    boolean waitForLock) {
        return (DistributedLock) feature.createLock(resource, ownerKey, holdSec, callback, waitForLock);
    }

    private DistributedLock roundTrip(DistributedLock lock) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(lock);
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        try (ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (DistributedLock) ois.readObject();
        }
    }

    /**
     * Runs the checkExpired() action.
     *
     * @param nskip number of actions in the work queue to skip
     * @param nadditional number of additional actions that appear in the work queue
     *        <i>after</i> the checkExpired action
     * @param schedSec number of seconds for which the checker should have been scheduled
     */
    private void runChecker(int nskip, int nadditional, long schedSec) {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(exsvc, times(nskip + nadditional + 1)).schedule(captor.capture(), eq(schedSec), eq(TimeUnit.SECONDS));
        Runnable action = captor.getAllValues().get(nskip);
        action.run();
    }

    /**
     * Runs a lock action (e.g., doLock, doUnlock).
     *
     * @param nskip number of actions in the work queue to skip
     * @param nadditional number of additional actions that appear in the work queue
     *        <i>after</i> the desired action
     */
    void runLock(int nskip, int nadditional) {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(exsvc, times(PRE_LOCK_EXECS + nskip + nadditional + 1)).execute(captor.capture());

        Runnable action = captor.getAllValues().get(PRE_LOCK_EXECS + nskip);
        action.run();
    }

    /**
     * Runs a scheduled action (e.g., "retry" action).
     *
     * @param nskip number of actions in the work queue to skip
     * @param nadditional number of additional actions that appear in the work queue
     *        <i>after</i> the desired action
     */
    void runSchedule(int nskip, int nadditional) {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(exsvc, times(PRE_SCHED_EXECS + nskip + nadditional + 1)).schedule(captor.capture(), anyLong(), any());

        Runnable action = captor.getAllValues().get(PRE_SCHED_EXECS + nskip);
        action.run();
    }

    /**
     * Gets a count of the number of lock records in the DB.
     *
     * @return the number of lock records in the DB
     * @throws SQLException if an error occurs accessing the DB
     */
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

    /**
     * Determines if there is a record for the given resource whose expiration time is in
     * the expected range.
     *
     * @param resourceId ID of the resource of interest
     * @param uuidString UUID string of the owner
     * @param holdSec seconds for which the lock was to be held
     * @param tbegin earliest time, in milliseconds, at which the record could have been
     *        inserted into the DB
     * @return {@code true} if a record is found, {@code false} otherwise
     * @throws SQLException if an error occurs accessing the DB
     */
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

    /**
     * Inserts a record into the DB.
     *
     * @param resourceId ID of the resource of interest
     * @param uuidString UUID string of the owner
     * @param expireOffset offset, in seconds, from "now", at which the lock should expire
     * @throws SQLException if an error occurs accessing the DB
     */
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

    /**
     * Updates a record in the DB.
     *
     * @param resourceId ID of the resource of interest
     * @param newUuid UUID string of the <i>new</i> owner
     * @param expireOffset offset, in seconds, from "now", at which the lock should expire
     * @throws SQLException if an error occurs accessing the DB
     */
    private void updateRecord(String resourceId, String newHost, String newUuid, int expireOffset) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("UPDATE pooling.locks SET host=?, owner=?,"
                        + " expirationTime=timestampadd(second, ?, now()) WHERE resourceId=?")) {

            stmt.setString(1, newHost);
            stmt.setString(2, newUuid);
            stmt.setInt(3, expireOffset);
            stmt.setString(4, resourceId);

            assertEquals(1, stmt.executeUpdate());
        }
    }

    /**
     * Feature that uses <i>exsvc</i> to execute requests.
     */
    private class MyLockingFeature extends DistributedLockManager {

        public MyLockingFeature(boolean init) {
            shutdownFeature();

            exsvc = mock(ScheduledExecutorService.class);
            when(exsvc.schedule(any(Runnable.class), anyLong(), any())).thenAnswer(ans -> checker);
            Whitebox.setInternalState(PolicyEngineConstants.getManager(), POLICY_ENGINE_EXECUTOR_FIELD, exsvc);

            if (init) {
                beforeCreateLockManager(engine, new Properties());
                start();
                afterStart(engine);
            }
        }
    }

    /**
     * Feature whose data source all throws exceptions.
     */
    private class InvalidDbLockingFeature extends MyLockingFeature {
        private boolean isTransient;
        private boolean freeLock = false;

        public InvalidDbLockingFeature(boolean isTransient) {
            // pass "false" because we have to set the error code BEFORE calling
            // afterStart()
            super(false);

            this.isTransient = isTransient;

            this.beforeCreateLockManager(engine, new Properties());
            this.start();
            this.afterStart(engine);
        }

        @Override
        protected BasicDataSource makeDataSource() throws Exception {
            when(datasrc.getConnection()).thenAnswer(answer -> {
                if (freeLock) {
                    freeLock = false;
                    lock.free();
                }

                throw makeEx();
            });

            doThrow(makeEx()).when(datasrc).close();

            return datasrc;
        }

        protected SQLException makeEx() {
            if (isTransient) {
                return new SQLException(new SQLTransientException(EXPECTED_EXCEPTION));

            } else {
                return new SQLException(EXPECTED_EXCEPTION);
            }
        }
    }

    /**
     * Feature whose locks free themselves while free() is already running.
     */
    private class FreeWithFreeLockingFeature extends MyLockingFeature {
        private boolean relock;

        public FreeWithFreeLockingFeature(boolean relock) {
            super(true);
            this.relock = relock;
        }

        @Override
        protected DistributedLock makeLock(LockState state, String resourceId, String ownerKey, int holdSec,
                        LockCallback callback) {

            return new DistributedLock(state, resourceId, ownerKey, holdSec, callback, feature) {
                private static final long serialVersionUID = 1L;
                private boolean checked = false;

                @Override
                public boolean isUnavailable() {
                    if (checked) {
                        return super.isUnavailable();
                    }

                    checked = true;

                    // release and relock
                    free();

                    if (relock) {
                        // run doUnlock
                        runLock(1, 0);

                        // relock it
                        createLock(RESOURCE, getOwnerKey(), HOLD_SEC, mock(LockCallback.class), false);
                    }

                    return false;
                }
            };
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
