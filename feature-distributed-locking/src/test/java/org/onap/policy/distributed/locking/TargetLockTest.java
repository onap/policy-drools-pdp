/*
 * ============LICENSE_START=======================================================
 * feature-distributed-locking
 * ================================================================================
 * Copyright (C) 2018-2019 AT&T Intellectual Property. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.onap.policy.drools.core.lock.PolicyResourceLockFeatureApi.OperResult;
import org.onap.policy.drools.persistence.SystemPersistenceConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TargetLockTest {
    private static final Logger logger = LoggerFactory.getLogger(TargetLockTest.class);
    private static final int MAX_AGE_SEC = 4 * 60;
    private static final String DB_CONNECTION =
            "jdbc:h2:mem:pooling;INIT=CREATE SCHEMA IF NOT EXISTS pooling\\;SET SCHEMA pooling";
    private static final String DB_USER = "user";
    private static final String DB_PASSWORD = "password";
    private static final String EXPECTED = "expected exception";
    private static final String MY_RESOURCE = "my-resource-id";
    private static final String MY_OWNER = "my-owner";
    private static final UUID MY_UUID = UUID.randomUUID();
    private static Connection conn = null;
    private static DistributedLockingFeature distLockFeat;

    /**
     * Setup the database.
     */
    @BeforeClass
    public static void setup() {
        getDbConnection();
        createTable();
        SystemPersistenceConstants.getManager().setConfigurationDir("src/test/resources");
        distLockFeat = new DistributedLockingFeature();
        distLockFeat.afterStart(null);
    }

    /**
     * Cleanup the lock.
     */
    @AfterClass
    public static void cleanUp() {
        distLockFeat.beforeShutdown(null);
        try {
            conn.close();
        } catch (SQLException e) {
            logger.error("Error in TargetLockTest.cleanUp()", e);
        }
    }

    /**
     * Wipe the database.
     */
    @Before
    public void wipeDb() {
        try (PreparedStatement lockDelete = conn.prepareStatement("DELETE FROM pooling.locks"); ) {
            lockDelete.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error in TargetLockTest.wipeDb()", e);
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testGrabLockSuccess() throws InterruptedException, ExecutionException {
        assertEquals(
                OperResult.OPER_ACCEPTED, distLockFeat.beforeLock("resource1", "owner1", MAX_AGE_SEC));

        // attempt to grab expiredLock
        try (PreparedStatement updateStatement =
                conn.prepareStatement(
                  "UPDATE pooling.locks SET expirationTime = timestampadd(second, -1, now()) WHERE resourceId = ?"); ) {
            updateStatement.setString(1, "resource1");
            updateStatement.executeUpdate();

        } catch (SQLException e) {
            logger.error("Error in TargetLockTest.testGrabLockSuccess()", e);
            throw new RuntimeException(e);
        }

        assertEquals(
                OperResult.OPER_ACCEPTED, distLockFeat.beforeLock("resource1", "owner1", MAX_AGE_SEC));

        // cannot re-lock
        assertEquals(
                OperResult.OPER_DENIED, distLockFeat.beforeLock("resource1", "owner1", MAX_AGE_SEC));

        assertEquals(OperResult.OPER_ACCEPTED, distLockFeat.beforeIsLockedBy("resource1", "owner1"));
    }

    @Test
    public void testExpiredLocks() throws Exception {

        // grab lock
        distLockFeat.beforeLock("resource1", "owner1", MAX_AGE_SEC);

        // force lock to expire
        try (PreparedStatement lockExpire =
                conn.prepareStatement(
                        "UPDATE pooling.locks SET expirationTime = timestampadd(second, -?, now())"); ) {
            lockExpire.setInt(1, MAX_AGE_SEC + 1);
            lockExpire.executeUpdate();
        }

        assertEquals(
                OperResult.OPER_ACCEPTED, distLockFeat.beforeLock("resource1", "owner2", MAX_AGE_SEC));
    }

    @Test
    public void testGrabLockFail() throws InterruptedException, ExecutionException {

        distLockFeat.beforeLock("resource1", "owner1", MAX_AGE_SEC);

        assertEquals(
                OperResult.OPER_DENIED, distLockFeat.beforeLock("resource1", "owner2", MAX_AGE_SEC));
    }

    @Test
    public void testSecondGrab_UpdateOk() throws Exception {
        PreparedStatement grabLockInsert = mock(PreparedStatement.class);
        when(grabLockInsert.executeUpdate()).thenThrow(new SQLException(EXPECTED));

        PreparedStatement secondGrabUpdate = mock(PreparedStatement.class);
        when(secondGrabUpdate.executeUpdate()).thenReturn(1);

        Connection connMock = mock(Connection.class);
        when(connMock.prepareStatement(anyString())).thenReturn(grabLockInsert, secondGrabUpdate);

        BasicDataSource dataSrc = mock(BasicDataSource.class);
        when(dataSrc.getConnection()).thenReturn(connMock);

        assertTrue(new TargetLock(MY_RESOURCE, MY_UUID, MY_OWNER, dataSrc).lock(MAX_AGE_SEC));
    }

    @Test
    public void testSecondGrab_UpdateFail_InsertOk() throws Exception {
        PreparedStatement grabLockInsert = mock(PreparedStatement.class);
        when(grabLockInsert.executeUpdate()).thenThrow(new SQLException(EXPECTED));

        PreparedStatement secondGrabUpdate = mock(PreparedStatement.class);
        when(secondGrabUpdate.executeUpdate()).thenReturn(0);

        PreparedStatement secondGrabInsert = mock(PreparedStatement.class);
        when(secondGrabInsert.executeUpdate()).thenReturn(1);

        Connection connMock = mock(Connection.class);
        when(connMock.prepareStatement(anyString())).thenReturn(grabLockInsert, secondGrabUpdate, secondGrabInsert);

        BasicDataSource dataSrc = mock(BasicDataSource.class);
        when(dataSrc.getConnection()).thenReturn(connMock);

        assertTrue(new TargetLock(MY_RESOURCE, MY_UUID, MY_OWNER, dataSrc).lock(MAX_AGE_SEC));
    }

    @Test
    public void testSecondGrab_UpdateFail_InsertFail() throws Exception {
        PreparedStatement grabLockInsert = mock(PreparedStatement.class);
        when(grabLockInsert.executeUpdate()).thenThrow(new SQLException(EXPECTED));

        PreparedStatement secondGrabUpdate = mock(PreparedStatement.class);
        when(secondGrabUpdate.executeUpdate()).thenReturn(0);

        PreparedStatement secondGrabInsert = mock(PreparedStatement.class);
        when(secondGrabInsert.executeUpdate()).thenReturn(0);

        Connection connMock = mock(Connection.class);
        when(connMock.prepareStatement(anyString())).thenReturn(grabLockInsert, secondGrabUpdate, secondGrabInsert);

        BasicDataSource dataSrc = mock(BasicDataSource.class);
        when(dataSrc.getConnection()).thenReturn(connMock);

        assertFalse(new TargetLock(MY_RESOURCE, MY_UUID, MY_OWNER, dataSrc).lock(MAX_AGE_SEC));
    }

    @Test
    public void testUpdateLock() throws Exception {
        // not locked yet - refresh should fail
        assertEquals(
                OperResult.OPER_DENIED, distLockFeat.beforeRefresh("resource1", "owner1", MAX_AGE_SEC));

        // now lock it
        assertEquals(
                OperResult.OPER_ACCEPTED, distLockFeat.beforeLock("resource1", "owner1", MAX_AGE_SEC));

        // refresh should work now
        assertEquals(
                OperResult.OPER_ACCEPTED, distLockFeat.beforeRefresh("resource1", "owner1", MAX_AGE_SEC));

        assertEquals(OperResult.OPER_ACCEPTED, distLockFeat.beforeIsLockedBy("resource1", "owner1"));

        // expire the lock
        try (PreparedStatement updateStatement =
                conn.prepareStatement(
                  "UPDATE pooling.locks SET expirationTime = timestampadd(second, -1, now()) WHERE resourceId = ?"); ) {
            updateStatement.setString(1, "resource1");
            updateStatement.executeUpdate();
        }

        // refresh should fail now
        assertEquals(
                OperResult.OPER_DENIED, distLockFeat.beforeRefresh("resource1", "owner1", MAX_AGE_SEC));

        assertEquals(OperResult.OPER_DENIED, distLockFeat.beforeIsLockedBy("resource1", "owner1"));

        // test exception case
        BasicDataSource dataSrc = mock(BasicDataSource.class);
        when(dataSrc.getConnection()).thenThrow(new SQLException(EXPECTED));
        assertFalse(new TargetLock(MY_RESOURCE, MY_UUID, MY_OWNER, dataSrc).refresh(MAX_AGE_SEC));
    }

    @Test
    public void testUnlock() throws Exception {
        distLockFeat.beforeLock("resource1", "owner1", MAX_AGE_SEC);

        assertEquals(OperResult.OPER_ACCEPTED, distLockFeat.beforeUnlock("resource1", "owner1"));
        assertEquals(
                OperResult.OPER_ACCEPTED, distLockFeat.beforeLock("resource1", "owner2", MAX_AGE_SEC));

        // test exception case
        BasicDataSource dataSrc = mock(BasicDataSource.class);
        when(dataSrc.getConnection()).thenThrow(new SQLException(EXPECTED));
        assertFalse(new TargetLock(MY_RESOURCE, MY_UUID, MY_OWNER, dataSrc).unlock());
    }

    @Test
    public void testIsActive() throws Exception {
        assertEquals(OperResult.OPER_DENIED, distLockFeat.beforeIsLockedBy("resource1", "owner1"));
        distLockFeat.beforeLock("resource1", "owner1", MAX_AGE_SEC);
        assertEquals(OperResult.OPER_ACCEPTED, distLockFeat.beforeIsLockedBy("resource1", "owner1"));
        assertEquals(OperResult.OPER_DENIED, distLockFeat.beforeIsLockedBy("resource1", "owner2"));

        // isActive on expiredLock
        try (PreparedStatement updateStatement =
                conn.prepareStatement(
                  "UPDATE pooling.locks SET expirationTime = timestampadd(second, -5, now()) WHERE resourceId = ?"); ) {
            updateStatement.setString(1, "resource1");
            updateStatement.executeUpdate();
        }

        assertEquals(OperResult.OPER_DENIED, distLockFeat.beforeIsLockedBy("resource1", "owner1"));

        distLockFeat.beforeLock("resource1", "owner1", MAX_AGE_SEC);
        // Unlock record, next isActive attempt should fail
        distLockFeat.beforeUnlock("resource1", "owner1");
        assertEquals(OperResult.OPER_DENIED, distLockFeat.beforeIsLockedBy("resource1", "owner1"));

        // test exception case for outer "try"
        BasicDataSource dataSrc = mock(BasicDataSource.class);
        when(dataSrc.getConnection()).thenThrow(new SQLException(EXPECTED));
        assertFalse(new TargetLock(MY_RESOURCE, MY_UUID, MY_OWNER, dataSrc).isActive());

        // test exception case for inner "try"
        PreparedStatement stmt = mock(PreparedStatement.class);
        when(stmt.executeQuery()).thenThrow(new SQLException(EXPECTED));
        Connection connMock = mock(Connection.class);
        when(connMock.prepareStatement(anyString())).thenReturn(stmt);
        dataSrc = mock(BasicDataSource.class);
        when(dataSrc.getConnection()).thenReturn(connMock);
        assertFalse(new TargetLock(MY_RESOURCE, MY_UUID, MY_OWNER, dataSrc).isActive());
    }

    @Test
    public void unlockBeforeLock() {
        assertEquals(OperResult.OPER_DENIED, distLockFeat.beforeUnlock("resource1", "owner1"));
        distLockFeat.beforeLock("resource1", "owner1", MAX_AGE_SEC);
        assertEquals(OperResult.OPER_ACCEPTED, distLockFeat.beforeUnlock("resource1", "owner1"));
        assertEquals(OperResult.OPER_DENIED, distLockFeat.beforeUnlock("resource1", "owner1"));
    }

    @Test
    public void testIsLocked() throws Exception {
        assertEquals(OperResult.OPER_DENIED, distLockFeat.beforeIsLocked("resource1"));
        distLockFeat.beforeLock("resource1", "owner1", MAX_AGE_SEC);
        assertEquals(OperResult.OPER_ACCEPTED, distLockFeat.beforeIsLocked("resource1"));

        // test exception case for outer "try"
        BasicDataSource dataSrc = mock(BasicDataSource.class);
        when(dataSrc.getConnection()).thenThrow(new SQLException(EXPECTED));
        assertFalse(new TargetLock(MY_RESOURCE, MY_UUID, MY_OWNER, dataSrc).isLocked());

        // test exception case for inner "try"
        PreparedStatement stmt = mock(PreparedStatement.class);
        when(stmt.executeQuery()).thenThrow(new SQLException(EXPECTED));
        Connection connMock = mock(Connection.class);
        when(connMock.prepareStatement(anyString())).thenReturn(stmt);
        dataSrc = mock(BasicDataSource.class);
        when(dataSrc.getConnection()).thenReturn(connMock);
        assertFalse(new TargetLock(MY_RESOURCE, MY_UUID, MY_OWNER, dataSrc).isLocked());
    }

    private static void getDbConnection() {
        try {
            conn = DriverManager.getConnection(DB_CONNECTION, DB_USER, DB_PASSWORD);
        } catch (SQLException e) {
            logger.error("Error in TargetLockTest.getDBConnection()", e);
        }
    }

    private static void createTable() {
        String createString =
                "create table if not exists pooling.locks "
                + "(resourceId VARCHAR(128), host VARCHAR(128), owner VARCHAR(128), "
                + "expirationTime TIMESTAMP DEFAULT 0, PRIMARY KEY (resourceId))";
        try (PreparedStatement createStmt = conn.prepareStatement(createString); ) {
            createStmt.executeUpdate();

        } catch (SQLException e) {
            logger.error("Error in TargetLockTest.createTable()", e);
        }
    }
}
