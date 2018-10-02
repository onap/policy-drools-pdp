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

package org.onap.policy.distributed.locking;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.onap.policy.common.utils.properties.exception.PropertyException;
import org.onap.policy.drools.persistence.SystemPersistence;

/**
 * Partially tests DistributedLockingFeature; most of the methods are tested via
 * {@link TargetLockTest}.
 */
public class DistributedLockingFeatureTest {
    private static final String EXPECTED = "expected exception";

    private BasicDataSource dataSrc;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        SystemPersistence.manager.setConfigurationDir("src/test/resources");
    }

    @Before
    public void setUp() throws Exception {
        dataSrc = mock(BasicDataSource.class);
    }

    @Test
    public void testGetSequenceNumber() {
        assertEquals(1000, new DistributedLockingFeature().getSequenceNumber());
    }

    @Test(expected = DistributedLockingFeatureException.class)
    public void testAfterStart_PropEx() {
        new DistributedLockingFeatureImpl(new PropertyException("prop", "val")).afterStart(null);
    }

    @Test(expected = DistributedLockingFeatureException.class)
    public void testAfterStart_InterruptEx() {
        new DistributedLockingFeatureImpl(new InterruptedException(EXPECTED)).afterStart(null);
    }

    @Test(expected = DistributedLockingFeatureException.class)
    public void testAfterStart_OtherEx() {
        new DistributedLockingFeatureImpl(new RuntimeException(EXPECTED)).afterStart(null);
    }

    @Test
    public void testCleanLockTable() throws Exception {
        when(dataSrc.getConnection()).thenThrow(new SQLException(EXPECTED));

        new DistributedLockingFeatureImpl().afterStart(null);
    }

    /**
     * Feature that overrides {@link #makeDataSource()}.
     */
    private class DistributedLockingFeatureImpl extends DistributedLockingFeature {
        /**
         * Exception to throw when {@link #makeDataSource()} is invoked.
         */
        private final Exception makeEx;

        public DistributedLockingFeatureImpl() {
            makeEx = null;
        }

        public DistributedLockingFeatureImpl(Exception ex) {
            this.makeEx = ex;
        }

        @Override
        protected BasicDataSource makeDataSource() throws Exception {
            if (makeEx != null) {
                throw makeEx;
            }

            return dataSrc;
        }
    }
}
