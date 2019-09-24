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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
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
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.onap.policy.drools.persistence.SystemPersistenceConstants;
import org.onap.policy.drools.system.PolicyEngine;

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

    private static Connection conn = null;

    @Mock
    private ScheduledExecutorService exsvc;

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

        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM pooling.locks")) {
            stmt.executeUpdate();
        }

        feature = new DistributedLockingFeature() {
            @Override
            protected ScheduledExecutorService makeThreadPool(int nthreads) {
                return exsvc;
            }
        };

        feature.afterStart((PolicyEngine) null);

        Properties props = new Properties();
        props.setProperty(DistributedLockingFeature.EXTRACTOR_PREFIX + MyOwner.class.getName(), "v.key");
        assertNull(feature.beforeCreate(null, props));
    }

    @After
    public void tearDown() {
        shutdownFeature();
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

        feature = new DistributedLockingFeature() {
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

        feature = new DistributedLockingFeature() {
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

        feature = new DistributedLockingFeature() {
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

        feature = new DistributedLockingFeature() {
            @Override
            protected BasicDataSource makeDataSource() throws Exception {
                return datasrc;
            }

            @Override
            protected ScheduledExecutorService makeThreadPool(int nthreads) {
                return exsvc;
            }
        };

        feature.afterStart((PolicyEngine) null);

        shutdownFeature();
    }

    @Test
    public void testLock() {
        fail("Not yet implemented");
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
    public void testFree() {
        fail("Not yet implemented");
    }

    @Test
    public void testExtend() {
        fail("Not yet implemented");
    }

    @Test
    public void testDistributedLock() {
        fail("Not yet implemented");
    }

    @Test
    public void testGrant() {
        fail("Not yet implemented");
    }

    @Test
    public void testDeny() {
        fail("Not yet implemented");
    }

    @Test
    public void testScheduleRequest() {
        fail("Not yet implemented");
    }

    @Test
    public void testDoRequest() {
        fail("Not yet implemented");
    }

    @Test
    public void testDoLock() {
        fail("Not yet implemented");
    }

    @Test
    public void testDoUnlock() {
        fail("Not yet implemented");
    }

    @Test
    public void testDoExtend() {
        fail("Not yet implemented");
    }

    @Test
    public void testDoInsert() {
        fail("Not yet implemented");
    }

    @Test
    public void testDoUpdate() {
        fail("Not yet implemented");
    }

    @Test
    public void testDoDelete() {
        fail("Not yet implemented");
    }

    @Test
    public void testRemoveFromMap() {
        fail("Not yet implemented");
    }

    @Test
    public void testNotifyAvailable() {
        fail("Not yet implemented");
    }

    @Test
    public void testNotifyUnavailable() {
        fail("Not yet implemented");
    }

    @Test
    public void testMakeThreadPool() {
        fail("Not yet implemented");
    }


    @Getter
    public static class MyOwner {
        private String key = OWNER_KEY;
    }
}
