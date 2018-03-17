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

package org.onap.policy.drools.pooling.state;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.onap.policy.drools.pooling.PoolingManager;
import org.onap.policy.drools.pooling.message.BucketAssignments;
import org.onap.policy.drools.pooling.message.Heartbeat;
import org.onap.policy.drools.pooling.message.Identification;
import org.onap.policy.drools.pooling.message.Leader;
import org.onap.policy.drools.pooling.message.Query;
import org.onap.policy.drools.pooling.state.State.StateFactory;

public class QueryStateTest extends BasicStateTester {
    
    private QueryState state;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        state = new QueryState(prevState, MY_HOST, ASGN3);
    }

    @Test
    public void testStart() {
        ScheduledFuture<?> fut = mock(ScheduledFuture.class);

        when(mgr.schedule(anyLong(), any(StateTimerTask.class))).thenAnswer(xxx -> fut);

        state.start();
        
        Pair<Long,StateTimerTask> timer = captureSchedule();

        assertEquals(QueryState.IDENTIFICATION_MS, timer.first.longValue());
        assertNotNull(timer.second);
    }

    @Test
    public void testProcessIdentification_NullSource() {
        assertNull(state.process(new Identification()));
        
        assertEquals(MY_HOST, state.getLeader());
    }

    @Test
    public void testProcessIdentification_NewLeader() {
        assertNull(state.process(new Identification(PREV_HOST, null)));
        
        assertEquals(PREV_HOST, state.getLeader());
    }

    @Test
    public void testProcessIdentification_NotNewLeader() {
        assertNull(state.process(new Identification(HOST2, null)));
        
        assertEquals(MY_HOST, state.getLeader());
    }

    @Test
    public void testProcessLeader_NewLeader() {
        String[] arr = {HOST2, PREV_HOST};
        BucketAssignments asgn = new BucketAssignments(arr);
        Leader msg = new Leader(PREV_HOST, asgn);

        State next = mock(State.class);
        when(factory.goInactive(state)).thenReturn(next);

        assertEquals(next, state.process(msg));
    }

    @Test
    public void testProcessLeader_NotNewLeader() {
        BucketAssignments asgn = new BucketAssignments(new String[] { HOST1 });
        Leader msg = new Leader(HOST1, asgn);

        state = new QueryState(prevState, MY_HOST, null);
        
        assertNull(state.process(msg));
        assertEquals(asgn, state.getAssignments());
    }

    @Test
    public void testProcessOffline_NullHost() {
        fail("Not yet implemented");
    }

    @Test
    public void testProcessOffline_SameHost() {
        fail("Not yet implemented");
    }

    @Test
    public void testProcessOffline_DiffHost_IdentNotComplete() {
        fail("Not yet implemented");
    }

    @Test
    public void testProcessOffline_DiffHost_NotLeader() {
        fail("Not yet implemented");
    }

    @Test
    public void testProcessOffline_DiffHost_IdentComplete_Leader() {
        fail("Not yet implemented");
    }

    @Test
    public void testProcessQuery() {
        BucketAssignments asgn = new BucketAssignments(new String[] {HOST1, HOST2});
        state = new QueryState(prevState, HOST1, asgn);
        
        State next = mock(State.class);
        when(factory.goQuery(state, HOST1, asgn)).thenReturn(next);
        
        assertEquals(next, state.process(new Query()));
        
        verify(mgr).publishAdmin(any(Identification.class));
    }

    @Test
    public void testQueryState() {
        /*
         * Prove the state is attached to the manager by invoking getHost(),
         * which delegates to the manager.
         */
        assertEquals(MY_HOST, state.getHost());
    }

    @Test
    public void testAwaitIdentification() {
        fail("Not yet implemented");
    }

    @Test
    public void testAwaitLeader() {
        fail("Not yet implemented");
    }

    @Test
    public void testHasAssignment() {
        // null assignment
        state.setAssignments(null);
        assertFalse(state.hasAssignment());

        // not in assignments
        state.setAssignments(new BucketAssignments(new String[] {HOST3}));
        assertFalse(state.hasAssignment());

        // it IS in the assignments
        state.setAssignments(new BucketAssignments(new String[] {MY_HOST}));
        assertTrue(state.hasAssignment());
    }

    @Test
    public void testOptSetAssignments_NewIsNull() {
        state.setAssignments(ASGN3);
        state.process(new Identification(HOST1, null));
        
        assertEquals(ASGN3, state.getAssignments());
    }

    @Test
    public void testOptSetAssignments_OldIsNull() {
        state.setAssignments(null);

        state.process(new Identification(HOST1, ASGN3));
        
        assertEquals(ASGN3, state.getAssignments());
    }

    @Test
    public void testOptSetAssignments_NewLeaderPreceedsOld() {
        state.setAssignments(ASGN3);
        state.setLeader(MY_HOST);

        BucketAssignments asgn = new BucketAssignments(new String[] {PREV_HOST, MY_HOST, HOST2});
        state.process(new Identification(HOST3, asgn));
        
        assertEquals(asgn, state.getAssignments());
    }

    @Test
    public void testOptSetAssignments_NewLeaderEqualsOld() {
        state.setAssignments(ASGN3);
        state.setLeader(MY_HOST);

        BucketAssignments asgn = new BucketAssignments(new String[] {HOST1, MY_HOST, HOST2});
        state.process(new Identification(HOST3, asgn));
        
        assertEquals(ASGN3, state.getAssignments());
    }

    @Test
    public void testOptSetAssignments_NewLeaderSucceedsOld() {
        state.setAssignments(ASGN3);
        state.setLeader(MY_HOST);

        BucketAssignments asgn = new BucketAssignments(new String[] {HOST2, HOST3});
        state.process(new Identification(HOST3, asgn));
        
        assertEquals(ASGN3, state.getAssignments());
    }

}
