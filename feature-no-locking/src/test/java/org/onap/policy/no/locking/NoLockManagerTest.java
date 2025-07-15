/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2021 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2024-2025 OpenInfra Foundation Europe. All rights reserved.
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

package org.onap.policy.no.locking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.onap.policy.drools.core.lock.AlwaysSuccessLock;
import org.onap.policy.drools.core.lock.Lock;
import org.onap.policy.drools.core.lock.LockCallback;
import org.onap.policy.drools.features.PolicyEngineFeatureApi;
import org.onap.policy.drools.features.PolicyEngineFeatureApiConstants;

class NoLockManagerTest {

    private static NoLockManager nlm;
    private static LockCallback callback;

    /**
     * Set up Junits.
     */
    @BeforeAll
    static void setUp() {
        List<PolicyEngineFeatureApi> engineServices = PolicyEngineFeatureApiConstants.getProviders().getList();
        assertThat(engineServices).hasSize(1);
        nlm = (NoLockManager) engineServices.get(0);
        callback = mock(LockCallback.class);
    }

    @Test
    void testLock() {
        assertTrue(nlm.lock());
    }

    @Test
    void testUnlock() {
        assertTrue(nlm.unlock());
    }

    @Test
    void testIsLocked() {
        assertFalse(nlm.isLocked());
    }

    @Test
    void testStart() {
        assertTrue(nlm.start());
    }

    @Test
    void testStop() {
        assertTrue(nlm.stop());
    }

    @Test
    void testIsAlive() {
        assertTrue(nlm.isAlive());
    }

    @Test
    void testGetSeqNo() {
        assertEquals(NoLockManager.SEQNO, nlm.getSequenceNumber());
    }

    @Test
    void testBeforeCreateLockManager() {
        assertEquals(nlm, nlm.beforeCreateLockManager());
    }

    @Test
    void testCreateLock() {
        Lock lock = nlm.createLock("x", "y", 1, callback, false);
        assertTrue(lock.isActive());
        assertInstanceOf(AlwaysSuccessLock.class, lock);
        verify(callback).lockAvailable(lock);
        verify(callback, never()).lockUnavailable(any());
    }
}