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

package org.onap.policy.drools.core.lock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Before;
import org.junit.Test;
import org.onap.policy.drools.core.lock.PolicyResourceLockFeatureAPI.OperResult;

public class PolicyResourceLockFeatureAPITest {

    private static final String RESOURCE_ID = "the resource";
    private static final String OWNER = "the owner";

    private PolicyResourceLockFeatureAPI api;

    /**
     * set up.
     */
    @Before
    public void setUp() {
        api = new PolicyResourceLockFeatureAPI() {
            @Override
            public int getSequenceNumber() {
                return 0;
            }
        };
    }

    @Test
    public void testBeforeLock() {
        assertEquals(OperResult.OPER_UNHANDLED, api.beforeLock(RESOURCE_ID, OWNER, 0));
    }

    @Test
    public void testAfterLock() {
        assertFalse(api.afterLock(RESOURCE_ID, OWNER, true));
        assertFalse(api.afterLock(RESOURCE_ID, OWNER, false));
    }

    @Test
    public void testBeforeRefresh() {
        assertEquals(OperResult.OPER_UNHANDLED, api.beforeRefresh(RESOURCE_ID, OWNER, 0));
    }

    @Test
    public void testAfterRefresh() {
        assertFalse(api.afterRefresh(RESOURCE_ID, OWNER, true));
        assertFalse(api.afterRefresh(RESOURCE_ID, OWNER, false));
    }

    @Test
    public void testBeforeUnlock() {
        assertEquals(OperResult.OPER_UNHANDLED, api.beforeUnlock(RESOURCE_ID, OWNER));
    }

    @Test
    public void testAfterUnlock() {
        assertFalse(api.afterUnlock(RESOURCE_ID, OWNER, true));
        assertFalse(api.afterUnlock(RESOURCE_ID, OWNER, false));
    }

    @Test
    public void testBeforeIsLocked() {
        assertEquals(OperResult.OPER_UNHANDLED, api.beforeIsLocked(RESOURCE_ID));
    }

    @Test
    public void testBeforeIsLockedBy() {
        assertEquals(OperResult.OPER_UNHANDLED, api.beforeIsLockedBy(RESOURCE_ID, OWNER));
    }
}
