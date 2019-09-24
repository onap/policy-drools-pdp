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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
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
    private static final String EXPECTED_EXCEPTION = "expected exception";
    private static final String DB_CONNECTION =
                    "jdbc:h2:mem:pooling;INIT=CREATE SCHEMA IF NOT EXISTS pooling\\;SET SCHEMA pooling";
    private static final String DB_USER = "user";
    private static final String DB_PASSWORD = "password";
    private static final String OWNER_KEY = "my key";
    private static final String RESOURCE = "my resource";
    private static final String RESOURCE2 = "my resource #2";
    private static final String RESOURCE3 = "my resource #3";
    private static final int HOLD_SEC = 100;
    private static final int HOLD_SEC2 = 120;
    private static final int MAX_THREADS = 20;
    private static final int MAX_LOOPS = 100;

    // number of execute() calls before the first lock attempt
    private static final int PRE_LOCK_EXECS = 1;

    private static Connection conn = null;

    private ScheduledExecutorService exsvc;

    @Mock
    private LockCallback callback;

    private MyOwner owner;

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

        feature = new MyLockingFeature();
        feature.afterStart((PolicyEngine) null);

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
        feature.beforeShutdown((PolicyEngine) null);
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

        feature = new MyLockingFeature() {
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

        feature = new MyLockingFeature() {
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

        feature = new MyLockingFeature() {
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
    public void testDeleteExpiredDbLocks() {
        fail("Not yet implemented");
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

        // create a data source that throws an exception when closed
        BasicDataSource datasrc = mock(BasicDataSource.class);
        doThrow(new SQLException(EXPECTED_EXCEPTION)).when(datasrc).close();

        feature = new MyLockingFeature() {
            @Override
            protected BasicDataSource makeDataSource() throws Exception {
                return datasrc;
            }
        };

        feature.afterStart((PolicyEngine) null);

        shutdownFeature();
    }

    @Test
    public void testLock() {
        verify(exsvc).execute(any());

        DistributedLock lock = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);
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
    public void testCheckExpired() {
        fail("Not yet implemented");
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
        DistributedLock lock = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);

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
        DistributedLock lock = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);

        assertTrue(lock.isUnavailable());

        // run the callback for the second lock
        runLock(1, 0);
        verify(callback).lockUnavailable(lock);
    }

    @Test
    public void testDistributedLockFree() {
        DistributedLock lock = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);

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
        feature.afterStart((PolicyEngine) null);

        DistributedLock lock = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);

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
        feature.afterStart((PolicyEngine) null);

        DistributedLock lock = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);

        assertFalse(lock.free());
        assertTrue(lock.isUnavailable());
    }

    @Test
    public void testDistributedLockExtend() {
        DistributedLock lock = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);

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
        feature.afterStart((PolicyEngine) null);

        DistributedLock lock = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);

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
        feature.afterStart((PolicyEngine) null);

        DistributedLock lock = feature.lock(RESOURCE, owner, HOLD_SEC, callback, false);

        lock.extend(HOLD_SEC2, callback);
        assertTrue(lock.isUnavailable());
        verify(callback).lockUnavailable(lock);
    }

    @Test
    public void testDistributedLockScheduleRequest() {
        fail("Not yet implemented");
    }

    @Test
    public void testDistributedLockDoRequest() {
        fail("Not yet implemented");
    }

    @Test
    public void testDistributedLockDoLock() {
        fail("Not yet implemented");
    }

    @Test
    public void testDistributedLockDoUnlock() {
        fail("Not yet implemented");
    }

    @Test
    public void testDistributedLockDoExtend() {
        fail("Not yet implemented");
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
        fail("Not yet implemented");
    }

    @Test
    public void testCheckDbRecordsAfterLockOperations() {
        fail("Not yet implemented");
    }

    void runLock(int nskip, int nadditional) {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(exsvc, times(PRE_LOCK_EXECS + nskip + nadditional + 1)).execute(captor.capture());

        captor.getAllValues().get(PRE_LOCK_EXECS + nskip).run();
    }

    private class MyLockingFeature extends DistributedLockingFeature {

        @Override
        protected ScheduledExecutorService makeThreadPool(int nthreads) {
            exsvc = mock(ScheduledExecutorService.class);
            return exsvc;
        }
    }

    private class FreeWithFreeLockingFeature extends MyLockingFeature {
        private boolean unlock;

        public FreeWithFreeLockingFeature(boolean unlock) {
            this.unlock = unlock;
        }

        @Override
        protected DistributedLock makeLock(String resourceId, Object ownerInfo, int holdSec, LockCallback callback) {

            return new DistributedLock(LockState.WAITING, resourceId, ownerInfo, holdSec, callback) {
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
