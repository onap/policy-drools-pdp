/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2020 Nordix Foundation
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.drools.pooling.message.BucketAssignments;
import org.onap.policy.drools.pooling.message.Identification;
import org.onap.policy.drools.pooling.message.Leader;
import org.onap.policy.drools.pooling.message.Offline;

public class QueryStateTest extends SupportBasicStateTester {

    private QueryState state;

    /**
     * Setup.
     */
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        state = new QueryState(mgr);
    }

    @Test
    public void testGoQuery() {
        assertNull(state.goQuery());
    }

    @Test
    public void testStart() {
        state.start();

        Pair<Long, StateTimerTask> timer = onceTasks.remove();

        assertEquals(STD_IDENTIFICATION_MS, timer.getLeft().longValue());
        assertNotNull(timer.getRight());
    }

    @Test
    public void testProcessIdentification_SameSource() {
        String[] arr = {HOST2, PREV_HOST, MY_HOST};
        BucketAssignments asgn = new BucketAssignments(arr);

        assertNull(state.process(new Identification(MY_HOST, asgn)));

        // info should be unchanged
        assertEquals(MY_HOST, state.getLeader());
        verify(mgr, never()).startDistributing(asgn);
    }

    @Test
    public void testProcessIdentification_DiffSource() {
        String[] arr = {HOST2, PREV_HOST, MY_HOST};
        BucketAssignments asgn = new BucketAssignments(arr);

        assertNull(state.process(new Identification(HOST2, asgn)));

        // leader should be unchanged
        assertEquals(MY_HOST, state.getLeader());

        // should have picked up the assignments
        verify(mgr).startDistributing(asgn);
    }

    @Test
    public void testProcessLeader_Invalid() {
        Leader msg = new Leader(PREV_HOST, null);

        // should stay in the same state, and not start distributing
        assertNull(state.process(msg));
        verify(mgr, never()).startDistributing(any());
        verify(mgr, never()).goActive();
        verify(mgr, never()).goInactive();

        // info should be unchanged
        assertEquals(MY_HOST, state.getLeader());
        assertEquals(ASGN3, state.getAssignments());
    }

    @Test
    public void testProcessLeader_SameLeader() {
        String[] arr = {HOST2, PREV_HOST, MY_HOST};
        BucketAssignments asgn = new BucketAssignments(arr);

        // identify a leader that's better than my host
        assertEquals(null, state.process(new Identification(PREV_HOST, asgn)));

        // now send a Leader message for that leader
        Leader msg = new Leader(PREV_HOST, asgn);

        State next = mock(State.class);
        when(mgr.goActive()).thenReturn(next);

        // should go Active and start distributing
        assertEquals(next, state.process(msg));
        verify(mgr, never()).goInactive();

        // Ident msg + Leader msg = times(2)
        verify(mgr, times(2)).startDistributing(asgn);
    }

    @Test
    public void testProcessLeader_BetterLeaderWithAssignment() {
        String[] arr = {HOST2, PREV_HOST, MY_HOST};
        BucketAssignments asgn = new BucketAssignments(arr);
        Leader msg = new Leader(PREV_HOST, asgn);

        State next = mock(State.class);
        when(mgr.goActive()).thenReturn(next);

        // should go Active and start distributing
        assertEquals(next, state.process(msg));
        verify(mgr).startDistributing(asgn);
        verify(mgr, never()).goInactive();
    }

    @Test
    public void testProcessLeader_BetterLeaderWithoutAssignment() {
        String[] arr = {HOST2, PREV_HOST, HOST1};
        BucketAssignments asgn = new BucketAssignments(arr);
        Leader msg = new Leader(PREV_HOST, asgn);

        State next = mock(State.class);
        when(mgr.goInactive()).thenReturn(next);

        // should go Inactive, but start distributing
        assertEquals(next, state.process(msg));
        verify(mgr).startDistributing(asgn);
        verify(mgr, never()).goActive();
    }

    @Test
    public void testProcessLeader_NotABetterLeader() {
        // no assignments yet
        mgr.startDistributing(null);
        state = new QueryState(mgr);

        BucketAssignments asgn = new BucketAssignments(new String[] {HOST1, HOST2});
        Leader msg = new Leader(HOST1, asgn);

        State next = mock(State.class);
        when(mgr.goInactive()).thenReturn(next);

        // should stay in the same state
        assertNull(state.process(msg));
        verify(mgr, never()).goActive();
        verify(mgr, never()).goInactive();

        // should have started distributing
        verify(mgr).startDistributing(asgn);

        // this host should still be the leader
        assertEquals(MY_HOST, state.getLeader());

        // new assignments
        assertEquals(asgn, state.getAssignments());
    }

    @Test
    public void testProcessOffline_NullHost() {
        assertNull(state.process(new Offline()));
        assertEquals(MY_HOST, state.getLeader());
    }

    @Test
    public void testProcessOffline_SameHost() {
        assertNull(state.process(new Offline(MY_HOST)));
        assertEquals(MY_HOST, state.getLeader());
    }

    @Test
    public void testProcessOffline_DiffHost() {
        BucketAssignments asgn = new BucketAssignments(new String[] {PREV_HOST, HOST1});
        mgr.startDistributing(asgn);
        state = new QueryState(mgr);

        // tell it that the hosts are alive
        state.process(new Identification(PREV_HOST, asgn));
        state.process(new Identification(HOST1, asgn));

        // #2 goes offline
        assertNull(state.process(new Offline(HOST1)));

        // #1 should still be the leader
        assertEquals(PREV_HOST, state.getLeader());

        // #1 goes offline
        assertNull(state.process(new Offline(PREV_HOST)));

        // this should still be the leader now
        assertEquals(MY_HOST, state.getLeader());
    }

    @Test
    public void testQueryState() {
        /*
         * Prove the state is attached to the manager by invoking getHost(), which
         * delegates to the manager.
         */
        assertEquals(MY_HOST, state.getHost());
    }

    @Test
    public void testAwaitIdentification_MissingSelfIdent() {
        state.start();

        Pair<Long, StateTimerTask> timer = onceTasks.remove();

        assertEquals(STD_IDENTIFICATION_MS, timer.getLeft().longValue());
        assertNotNull(timer.getRight());

        // should published an Offline message and go inactive

        State next = mock(State.class);
        when(mgr.goStart()).thenReturn(next);

        assertEquals(next, timer.getRight().fire());

        // should continue distributing
        verify(mgr, never()).startDistributing(null);

        Offline msg = captureAdminMessage(Offline.class);
        assertEquals(MY_HOST, msg.getSource());
    }

    @Test
    public void testAwaitIdentification_Leader() {
        state.start();
        state.process(new Identification(MY_HOST, null));

        Pair<Long, StateTimerTask> timer = onceTasks.remove();

        assertEquals(STD_IDENTIFICATION_MS, timer.getLeft().longValue());
        assertNotNull(timer.getRight());

        State next = mock(State.class);
        when(mgr.goActive()).thenReturn(next);

        assertEquals(next, timer.getRight().fire());

        // should have published a Leader message
        Leader msg = captureAdminMessage(Leader.class);
        assertEquals(MY_HOST, msg.getSource());
        assertTrue(msg.getAssignments().hasAssignment(MY_HOST));
    }

    @Test
    public void testAwaitIdentification_HasAssignment() {
        // not the leader, but has an assignment
        BucketAssignments asgn = new BucketAssignments(new String[] {PREV_HOST, MY_HOST, HOST2});
        mgr.startDistributing(asgn);
        state = new QueryState(mgr);

        state.start();
        state.process(new Identification(MY_HOST, null));

        // tell it the leader is still active
        state.process(new Identification(PREV_HOST, asgn));

        Pair<Long, StateTimerTask> timer = onceTasks.remove();

        assertEquals(STD_IDENTIFICATION_MS, timer.getLeft().longValue());
        assertNotNull(timer.getRight());

        // set up active state, as that's what it should return
        State next = mock(State.class);
        when(mgr.goActive()).thenReturn(next);

        assertEquals(next, timer.getRight().fire());

        // should NOT have published a Leader message
        assertTrue(admin.isEmpty());

        // should have gone active with the current assignments
        verify(mgr).goActive();
    }

    @Test
    public void testAwaitIdentification_NoAssignment() {
        // not the leader and no assignment
        BucketAssignments asgn = new BucketAssignments(new String[] {HOST1, HOST2});
        mgr.startDistributing(asgn);
        state = new QueryState(mgr);

        state.start();
        state.process(new Identification(MY_HOST, null));

        // tell it the leader is still active
        state.process(new Identification(PREV_HOST, asgn));

        Pair<Long, StateTimerTask> timer = onceTasks.remove();

        assertEquals(STD_IDENTIFICATION_MS, timer.getLeft().longValue());
        assertNotNull(timer.getRight());

        // set up inactive state, as that's what it should return
        State next = mock(State.class);
        when(mgr.goInactive()).thenReturn(next);

        assertEquals(next, timer.getRight().fire());

        // should NOT have published a Leader message
        assertTrue(admin.isEmpty());
    }

    @Test
    public void testRecordInfo_NullSource() {
        state.setAssignments(ASGN3);
        state.setLeader(MY_HOST);

        BucketAssignments asgn = new BucketAssignments(new String[] {PREV_HOST, MY_HOST, HOST2});
        state.process(new Identification(null, asgn));

        // leader unchanged
        assertEquals(MY_HOST, state.getLeader());

        // assignments still updated
        assertEquals(asgn, state.getAssignments());
    }

    @Test
    public void testRecordInfo_SourcePreceedsMyHost() {
        state.setAssignments(ASGN3);
        state.setLeader(MY_HOST);

        BucketAssignments asgn = new BucketAssignments(new String[] {PREV_HOST, MY_HOST, HOST2});
        state.process(new Identification(PREV_HOST, asgn));

        // new leader
        assertEquals(PREV_HOST, state.getLeader());

        // assignments still updated
        assertEquals(asgn, state.getAssignments());
    }

    @Test
    public void testRecordInfo_SourceFollowsMyHost() {
        mgr.startDistributing(null);
        state.setLeader(MY_HOST);

        BucketAssignments asgn = new BucketAssignments(new String[] {HOST1, HOST2});
        state.process(new Identification(HOST1, asgn));

        // leader unchanged
        assertEquals(MY_HOST, state.getLeader());

        // assignments still updated
        assertEquals(asgn, state.getAssignments());
    }

    @Test
    public void testRecordInfo_NewIsNull() {
        state.setAssignments(ASGN3);
        state.process(new Identification(HOST1, null));

        assertEquals(ASGN3, state.getAssignments());
    }

    @Test
    public void testRecordInfo_NewIsEmpty() {
        state.setAssignments(ASGN3);
        state.process(new Identification(PREV_HOST, new BucketAssignments()));

        assertEquals(ASGN3, state.getAssignments());
    }

    @Test
    public void testRecordInfo_OldIsNull() {
        mgr.startDistributing(null);

        BucketAssignments asgn = new BucketAssignments(new String[] {HOST1, HOST2});
        state.process(new Identification(HOST1, asgn));

        assertEquals(asgn, state.getAssignments());
    }

    @Test
    public void testRecordInfo_OldIsEmpty() {
        state.setAssignments(new BucketAssignments());

        BucketAssignments asgn = new BucketAssignments(new String[] {HOST1, HOST2});
        state.process(new Identification(HOST1, asgn));

        assertEquals(asgn, state.getAssignments());
    }

    @Test
    public void testRecordInfo_NewLeaderPreceedsOld() {
        state.setAssignments(ASGN3);
        state.setLeader(MY_HOST);

        BucketAssignments asgn = new BucketAssignments(new String[] {PREV_HOST, MY_HOST, HOST2});
        state.process(new Identification(HOST3, asgn));

        assertEquals(asgn, state.getAssignments());
    }

    @Test
    public void testRecordInfo_NewLeaderSucceedsOld() {
        state.setAssignments(ASGN3);
        state.setLeader(MY_HOST);

        BucketAssignments asgn = new BucketAssignments(new String[] {HOST2, HOST3});
        state.process(new Identification(HOST3, asgn));

        // should be unchanged
        assertEquals(ASGN3, state.getAssignments());
    }

}
