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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
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
    private static final String RESOURCE = "my resource";
    private static final String OWNER = "my owner";
    private static final int HOLD_SEC = 10;

    @Mock
    private LockCallback callback;

    @Mock
    private PolicyResourceLockFeatureApi provider1;

    @Mock
    private PolicyResourceLockFeatureApi provider2;

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

        // only provider2 is enabled
        when(provider2.enabled()).thenReturn(true);

        mgr = new PolicyResourceLockManager() {
            private boolean gotten = false;

            @Override
            protected List<PolicyResourceLockFeatureApi> getProviders() {
                assertFalse(gotten);

                gotten = true;
                return Arrays.asList(provider1, provider2);
            }
        };
    }

    @Test
    public void testGetInstance() {
        assertNotNull(PolicyResourceLockManager.getInstance());
    }

    @Test
    public void testGetProvider() {
        assertNotNull(PolicyResourceLockManager.getProvider());
    }

    @Test
    public void getProvider2() {
        assertSame(provider2, mgr.getProvider2());
        verify(provider2).enabled();

        // should use cache now
        assertSame(provider2, mgr.getProvider2());
        assertSame(provider2, mgr.getProvider2());

        // should be no ore calls to enabled()
        verify(provider2).enabled();
    }

    /**
     * Tests getProvider2() when the provider throws an exception when enabled() is
     * called.
     */
    @Test
    public void getProvider2Ex() {
        when(provider1.enabled()).thenThrow(new IllegalStateException("expected exception"));

        assertSame(PolicyResourceLockManager.DEFAULT_PROVIDER, mgr.getProvider2());
    }

    @Test
    public void testDefaultProvider() {
        assertEquals(0, PolicyResourceLockManager.DEFAULT_PROVIDER.getSequenceNumber());
        assertFalse(PolicyResourceLockManager.DEFAULT_PROVIDER.enabled());

        Lock lock = PolicyResourceLockManager.DEFAULT_PROVIDER.lock(RESOURCE, OWNER, HOLD_SEC, callback, true);
        assertTrue(lock.isUnavailable());
        verify(callback, never()).lockAvailable(any());
        verify(callback).lockUnavailable(lock);
    }

    @Test
    public void testGetProviders() {
        assertNotNull(new PolicyResourceLockManager().getProviders());
    }
}
