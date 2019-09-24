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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;

public class AlwaysFailProviderTest {
    private static final String OWNER_KEY = "my key";
    private static final String RESOURCE = "my resource";
    private static final int HOLD_SEC = 100;

    private LockCallback callback;
    private AlwaysFailProvider provider;

    /**
     * Creates mock and a provider.
     */
    @Before
    public void setUp() {
        callback = mock(LockCallback.class);

        provider = new AlwaysFailProvider();
    }

    @Test
    public void testGetSequenceNumber_testEnabled() {
        assertEquals(0, provider.getSequenceNumber());
        assertTrue(provider.enabled());
    }

    @Test
    public void testLock() {
        AlwaysFailLock lock = (AlwaysFailLock) provider.lock(RESOURCE, OWNER_KEY, HOLD_SEC, callback, true);
        assertNotNull(lock);
        assertTrue(lock.isUnavailable());
        verify(callback, never()).lockAvailable(any());
        verify(callback).lockUnavailable(lock);

        assertEquals(RESOURCE, lock.getResourceId());
        assertSame(OWNER_KEY, lock.getOwnerInfo());
        assertNull(lock.getOwnerKey());
        assertEquals(HOLD_SEC, lock.getHoldSec());
        assertSame(callback, lock.getCallback());
    }
}
