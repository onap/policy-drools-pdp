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

package org.onap.policy.drools.core.lock;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.drools.core.lock.Lock.RemoveResult;
import org.onap.policy.drools.utils.Pair;

public class LockTest {
    
    private static final String OWNER = "my.owner";
    private static final String OWNER2 = "another.owner";
    private static final String OWNER3 = "third.owner";
    
    private static final Integer ITEM2 = 10;
    private static final Integer ITEM3 = 20;
    
    private Lock<Integer> lock;
    private Pair<String, Integer> newOwner;
    
    @Before
    public void setUp() {
        lock = new Lock<>(OWNER);
        newOwner = new Pair<>(null, null);
    }
    

    @Test
    public void testLock() {
        assertEquals(OWNER, lock.getOwner());
    }

    @Test
    public void testGetOwner() {
        assertEquals(OWNER, lock.getOwner());
    }

    @Test
    public void testAdd() {
        assertTrue(lock.add(OWNER2, ITEM2));
        assertTrue(lock.add(OWNER3, ITEM3));
        
        // attempt to re-add owner2 with the same item - should fail
        assertFalse(lock.add(OWNER2, ITEM2));
        
        // attempt to re-add owner2 with a different item - should fail
        assertFalse(lock.add(OWNER2, ITEM3));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAdd_ArgEx() {
        lock.add(OWNER2, null);
    }

    @Test
    public void testAdd_AlreadyOwner() {
        assertFalse(lock.add(OWNER, ITEM2));
    }

    @Test
    public void testAdd_AlreadyInQueue() {
        lock.add(OWNER2, ITEM2);

        assertFalse(lock.add(OWNER2, ITEM2));
    }

    @Test
    public void testRemoveRequester_Owner_QueueEmpty() {
        assertEquals(RemoveResult.UNLOCKED, lock.removeRequester(OWNER, newOwner));
    }

    @Test
    public void testRemoveRequester_Owner_QueueHasOneItem() {
        lock.add(OWNER2, ITEM2);
        
        assertEquals(RemoveResult.RELOCKED, lock.removeRequester(OWNER, newOwner));
        assertEquals(OWNER2, newOwner.first());
        assertEquals(ITEM2, newOwner.second());

        assertEquals(RemoveResult.UNLOCKED, lock.removeRequester(OWNER2, newOwner));
    }

    @Test
    public void testRemoveRequester_Owner_QueueHasMultipleItems() {
        lock.add(OWNER2, ITEM2);
        lock.add(OWNER3, ITEM3);
        
        assertEquals(RemoveResult.RELOCKED, lock.removeRequester(OWNER, newOwner));
        assertEquals(OWNER2, newOwner.first());
        assertEquals(ITEM2, newOwner.second());
        
        assertEquals(RemoveResult.RELOCKED, lock.removeRequester(OWNER2, newOwner));
        assertEquals(OWNER3, newOwner.first());
        assertEquals(ITEM3, newOwner.second());

        assertEquals(RemoveResult.UNLOCKED, lock.removeRequester(OWNER3, newOwner));
    }

    @Test
    public void testRemoveRequester_InQueue() {
        lock.add(OWNER2, ITEM2);
        
        assertEquals(RemoveResult.REMOVED, lock.removeRequester(OWNER2, newOwner));
    }

    @Test
    public void testRemoveRequester_NeitherOwnerNorInQueue() {
        assertEquals(RemoveResult.NOT_FOUND, lock.removeRequester(OWNER2, newOwner));
    }

}
