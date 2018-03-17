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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.drools.pooling.message.BucketAssignments;
import org.onap.policy.drools.pooling.message.Identification;
import org.onap.policy.drools.pooling.message.Leader;
import org.onap.policy.drools.pooling.message.Offline;
import org.onap.policy.drools.pooling.message.Query;

public class QueryStateTest extends BasicStateTester {

    private QueryState state;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        state = new QueryState(prevState, ASGN3);
    }

    @Test
    public void testStart() {
        state.start();

        Pair<Long, StateTimerTask> timer = onceTasks.remove();

        assertEquals(STD_IDENTIFICATION_MS, timer.first.longValue());
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
        BucketAssignments asgn = new BucketAssignments(new String[] {HOST1});
        Leader msg = new Leader(HOST1, asgn);

        state = new QueryState(prevState, null);

        assertNull(state.process(msg));
        assertEquals(asgn, state.getAssignments());
    }

    @Test
    public void testProcessOffline_NullHost() {
        assertNull(state.process(new Offline()));
    }

    @Test
    public void testProcessOffline_SameHost() {
        assertNull(state.process(new Offline(MY_HOST)));
    }

    @Test
    public void testProcessOffline_DiffHost_NonLeaderOffline() {
        BucketAssignments asgn = new BucketAssignments(new String[] {PREV_HOST, PREV_HOST2, HOST1});
        state = new QueryState(prevState, asgn);

        // tell it that both hosts are alive
        state.process(new Identification(PREV_HOST, asgn));
        state.process(new Identification(PREV_HOST2, asgn));

        // #2 goes offline
        assertNull(state.process(new Offline(PREV_HOST2)));

        // #1 should still be the leader
        assertEquals(PREV_HOST, state.getLeader());
    }

    @Test
    public void testProcessOffline_DiffHost_LeaderOffline() {
        BucketAssignments asgn = new BucketAssignments(new String[] {PREV_HOST, PREV_HOST2, HOST1});
        state = new QueryState(prevState, asgn);

        // tell it that both hosts are alive
        state.process(new Identification(PREV_HOST, asgn));
        state.process(new Identification(PREV_HOST2, asgn));

        // #1 goes offline
        assertNull(state.process(new Offline(PREV_HOST)));

        // #2 should be the new leader now
        assertEquals(PREV_HOST2, state.getLeader());
    }

    @Test
    public void testProcessOffline_DiffHost_IdentNotComplete() {
        /*
         * Someone else goes offline, but the Identification step is not
         * complete yet.
         */
        assertNull(state.process(new Offline(HOST1)));
    }

    @Test
    public void testProcessOffline_DiffHost_IdentComplete_NotLeader() {
        /*
         * Arrange for identification step to complete, when we're NOT the
         * leader.
         */
        BucketAssignments asgn = new BucketAssignments(new String[] {PREV_HOST});
        state = new QueryState(prevState, asgn);

        // start the state, and get the timer
        state.start();
        Pair<Long, StateTimerTask> timer = onceTasks.remove();

        // fire the timer to get us into the identification-complete state
        timer.second.fire(null);

        // tell it that the leader is still online
        state.process(new Identification(PREV_HOST, asgn));

        // take something else offline - should NOT change states
        assertNull(state.process(new Offline(HOST1)));

        assertEquals(PREV_HOST, state.getLeader());
    }

    @Test
    public void testProcessOffline_DiffHost_IdentComplete_Leader() {
        /*
         * Arrange for identification step to complete, when we're the leader.
         */
        BucketAssignments asgn = new BucketAssignments(new String[] {PREV_HOST, MY_HOST});
        state = new QueryState(prevState, asgn);

        // start the state, and get the timer
        state.start();
        Pair<Long, StateTimerTask> timer = onceTasks.remove();

        // tell it the leader is still active
        state.process(new Identification(PREV_HOST, asgn));

        // fire the timer to get us into the identification-complete state
        timer.second.fire(null);

        // set up the active state, as that is where it should transition
        State next = mock(State.class);
        when(factory.goActive(any(), any())).thenReturn(next);

        // tell it that the leader is offline - we should become the leader
        assertEquals(next, state.process(new Offline(PREV_HOST)));
    }

    @Test
    public void testProcessQuery() {
        BucketAssignments asgn = new BucketAssignments(new String[] {HOST1, HOST2});
        state = new QueryState(prevState, asgn);

        State next = mock(State.class);
        when(factory.goQuery(state, asgn)).thenReturn(next);

        assertEquals(null, state.process(new Query()));

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
    public void testAwaitIdentification_Leader() {
        state.start();

        Pair<Long, StateTimerTask> timer = onceTasks.remove();

        assertEquals(STD_IDENTIFICATION_MS, timer.first.longValue());
        assertNotNull(timer.second);

        State next = mock(State.class);
        when(factory.goActive(any(), any())).thenReturn(next);

        assertEquals(next, timer.second.fire(null));
    }

    @Test
    public void testAwaitIdentification_NotLeader() {
        BucketAssignments asgn = new BucketAssignments(new String[] {PREV_HOST, HOST2});
        state = new QueryState(prevState, asgn);

        state.start();

        // tell it the leader is still active
        state.process(new Identification(PREV_HOST, asgn));

        Pair<Long, StateTimerTask> timer = onceTasks.remove();

        assertEquals(STD_IDENTIFICATION_MS, timer.first.longValue());
        assertNotNull(timer.second);

        assertNull(timer.second.fire(null));

        // get next timer
        timer = onceTasks.remove();

        assertEquals(STD_LEADER_MS, timer.first.longValue());
        assertNotNull(timer.second);
    }

    @Test
    public void testAwaitLeader_HasAssignment() {
        // not the leader, but has an assignment
        BucketAssignments asgn = new BucketAssignments(new String[] {PREV_HOST, MY_HOST, HOST2});
        state = new QueryState(prevState, asgn);

        state.start();

        // tell it the leader is still active
        state.process(new Identification(PREV_HOST, asgn));

        Pair<Long, StateTimerTask> timer = onceTasks.remove();

        assertEquals(STD_IDENTIFICATION_MS, timer.first.longValue());
        assertNotNull(timer.second);

        assertNull(timer.second.fire(null));

        // get next timer
        timer = onceTasks.remove();

        assertEquals(STD_LEADER_MS, timer.first.longValue());
        assertNotNull(timer.second);

        // set up active state, as that's what it should return
        State next = mock(State.class);
        when(factory.goActive(state, asgn)).thenReturn(next);

        assertEquals(next, timer.second.fire(null));
    }

    @Test
    public void testAwaitLeader_NoAssignment() {
        // not the leader and no assignment
        BucketAssignments asgn = new BucketAssignments(new String[] {HOST1, HOST2});
        state = new QueryState(prevState, asgn);

        state.start();

        // tell it the leader is still active
        state.process(new Identification(PREV_HOST, asgn));

        Pair<Long, StateTimerTask> timer = onceTasks.remove();

        assertEquals(STD_IDENTIFICATION_MS, timer.first.longValue());
        assertNotNull(timer.second);

        assertNull(timer.second.fire(null));

        // get next timer
        timer = onceTasks.remove();

        assertEquals(STD_LEADER_MS, timer.first.longValue());
        assertNotNull(timer.second);

        // set up inactive state, as that's what it should return
        State next = mock(State.class);
        when(factory.goInactive(state)).thenReturn(next);

        assertEquals(next, timer.second.fire(null));
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
