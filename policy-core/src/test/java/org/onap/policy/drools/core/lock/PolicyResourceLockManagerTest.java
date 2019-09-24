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

package org.onap.policy.drools.core.lock;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PolicyResourceLockManagerTest {
    private static final String EXPECTED_EXCEPTION = "expected exception";
    private static final String RESOURCE = "my resource";
    private static final String OWNER = "my owner";
    private static final int HOLD_SEC = 100;

    @Mock
    private LockCallback callback;

    @Mock
    private PolicyResourceLockFeatureApi provider1;

    @Mock
    private PolicyResourceLockFeatureApi provider2;

    @Mock
    private PolicyResourceLockFeatureApi provider3;

    @Mock
    private Lock lock1;

    @Mock
    private Lock lock2;

    @Mock
    private Lock lock3;

    private PolicyResourceLockManager mgr;


    /**
     * Initializes the mocks and creates a manager that uses the mock providers.
     */
    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mgr = new PolicyResourceLockManager() {
            @Override
            protected List<PolicyResourceLockFeatureApi> getProviders() {
                return Arrays.asList(provider1, provider2, provider3);
            }
        };
    }

    @Test
    public void testGetInstance() {
        assertNotNull(PolicyResourceLockManager.getInstance());
    }

    @Test
    public void testCreateLock() {
        // only provider2 is enabled
        when(provider2.beforeCreateLock(RESOURCE, OWNER, HOLD_SEC, callback, false)).thenReturn(lock1);

        // provider2 should have intercepted the call
        assertSame(lock1, mgr.createLock(RESOURCE, OWNER, HOLD_SEC, callback, false));

        // should have called beforeCreate only on the first two providers
        verify(provider1).beforeCreateLock(RESOURCE, OWNER, HOLD_SEC, callback, false);
        verify(provider2).beforeCreateLock(RESOURCE, OWNER, HOLD_SEC, callback, false);
        verify(provider3, never()).beforeCreateLock(any(), any(), anyInt(), any(), anyBoolean());

        // afterCreate should *NEVER* have been called
        verify(provider1, never()).afterCreateLock(any(), anyInt(), any(), anyBoolean());
        verify(provider2, never()).afterCreateLock(any(), anyInt(), any(), anyBoolean());
        verify(provider3, never()).afterCreateLock(any(), anyInt(), any(), anyBoolean());

        // intercepted, thus no callbacks
        verify(callback, never()).lockAvailable(any());
        verify(callback, never()).lockUnavailable(any());
    }

    /**
     * Tests createLock() when there are no providers.
     */
    @Test
    public void testCreateLockNoProviders() {
        Lock lock = mgr.createLock(RESOURCE, OWNER, HOLD_SEC, callback, false);
        assertTrue(lock instanceof AlwaysFailLock);

        // should have called beforeCreate on all providers
        verify(provider1).beforeCreateLock(RESOURCE, OWNER, HOLD_SEC, callback, false);
        verify(provider2).beforeCreateLock(RESOURCE, OWNER, HOLD_SEC, callback, false);
        verify(provider3).beforeCreateLock(RESOURCE, OWNER, HOLD_SEC, callback, false);

        // afterCreate *SHOULD* have been called
        verify(provider1).afterCreateLock(lock, HOLD_SEC, callback, false);
        verify(provider2).afterCreateLock(lock, HOLD_SEC, callback, false);
        verify(provider3).afterCreateLock(lock, HOLD_SEC, callback, false);

        // lock always fails - should have invoked the callback
        verify(callback, never()).lockAvailable(any());
        verify(callback).lockUnavailable(any());
    }

    /**
     * Tests createLock() when the provider throws an exception when beforeCreate() is
     * called.
     */
    @Test
    public void testCreateLockEx() {
        when(provider2.beforeCreateLock(any(), any(), anyInt(), any(), anyBoolean()))
                        .thenThrow(new IllegalStateException(EXPECTED_EXCEPTION));

        Lock lock = mgr.createLock(RESOURCE, OWNER, HOLD_SEC, callback, false);
        assertTrue(lock instanceof AlwaysFailLock);

        // should have called beforeCreate only on the first two providers
        verify(provider1).beforeCreateLock(RESOURCE, OWNER, HOLD_SEC, callback, false);
        verify(provider2).beforeCreateLock(RESOURCE, OWNER, HOLD_SEC, callback, false);
        verify(provider3, never()).beforeCreateLock(any(), any(), anyInt(), any(), anyBoolean());

        // afterCreate *SHOULD* have been called
        verify(provider1).afterCreateLock(lock, HOLD_SEC, callback, false);
        verify(provider2).afterCreateLock(lock, HOLD_SEC, callback, false);
        verify(provider3).afterCreateLock(lock, HOLD_SEC, callback, false);

        // lock always fails - should have invoked the callback
        verify(callback, never()).lockAvailable(any());
        verify(callback).lockUnavailable(any());
    }

    /**
     * Tests createLock() when a provider handles afterCreate.
     */
    @Test
    public void testCreateLockAfterLockHandled() {
        when(provider2.afterCreateLock(any(), anyInt(), any(), anyBoolean())).thenReturn(true);

        Lock lock = mgr.createLock(RESOURCE, OWNER, HOLD_SEC, callback, false);
        assertTrue(lock instanceof AlwaysFailLock);

        // afterCreate *SHOULD* have been called, except for the last provider
        verify(provider1).afterCreateLock(lock, HOLD_SEC, callback, false);
        verify(provider2).afterCreateLock(lock, HOLD_SEC, callback, false);
        verify(provider3, never()).afterCreateLock(lock, HOLD_SEC, callback, false);

        // provider is expected to handle callback, so nothing expected here
        verify(callback, never()).lockAvailable(any());
        verify(callback, never()).lockUnavailable(any());
    }

    /**
     * Tests createLock() when afterCreate throws an exception.
     */
    @Test
    public void testCreateLockAfterLockEx() {
        when(provider2.afterCreateLock(any(), anyInt(), any(), anyBoolean()))
                        .thenThrow(new IllegalStateException(EXPECTED_EXCEPTION));

        Lock lock = mgr.createLock(RESOURCE, OWNER, HOLD_SEC, callback, false);
        assertTrue(lock instanceof AlwaysFailLock);

        // afterCreate *SHOULD* have been called, for all providers
        verify(provider1).afterCreateLock(lock, HOLD_SEC, callback, false);
        verify(provider2).afterCreateLock(lock, HOLD_SEC, callback, false);
        verify(provider3).afterCreateLock(lock, HOLD_SEC, callback, false);

        /*
         * providers didn't handle callback, thus the manager should have invoked the
         * callback
         */
        verify(callback, never()).lockAvailable(any());
        verify(callback).lockUnavailable(any());
    }

    @Test
    public void testGetProviders() {
        assertNotNull(new PolicyResourceLockManager().getProviders());
    }
}
