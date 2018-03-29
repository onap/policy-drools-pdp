/*
 * ============LICENSE_START=======================================================
 * api-resource-locks
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

package org.onap.policy.drools.resourcelocks;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import org.junit.Before;
import org.junit.Test;

public class ResourceLockFeatureAPITest {

    private static final String RESOURCE_ID = "the resource";

    private ResourceLockFeatureAPI api;

    @Before
    public void setUp() {
        api = new ResourceLockFeatureAPI() {
            @Override
            public int getSequenceNumber() {
                return 0;
            }
        };
    }

    @Test
    public void testBeforeLock() {
        assertNull(api.beforeLock(RESOURCE_ID));
    }

    @Test
    public void testAfterLock() {
        assertFalse(api.afterLock(RESOURCE_ID, true));
        assertFalse(api.afterLock(RESOURCE_ID, false));
    }

    @Test
    public void testBeforeUnlock() {
        assertNull(api.beforeUnlock(RESOURCE_ID));
    }

    @Test
    public void testAfterUnlock() {
        assertFalse(api.afterUnlock(RESOURCE_ID, true));
        assertFalse(api.afterUnlock(RESOURCE_ID, false));
    }

    @Test
    public void testBeforeIsActive() {
        assertNull(api.beforeIsActive(RESOURCE_ID));
    }
}
