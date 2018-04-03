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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.drools.pooling.message.BucketAssignments;
import org.onap.policy.drools.pooling.message.Identification;
import org.onap.policy.drools.pooling.message.Leader;
import org.onap.policy.drools.pooling.message.Message;
import org.onap.policy.drools.pooling.message.Offline;
import org.onap.policy.drools.pooling.message.Query;

public class QueryStateTest extends BasicStateTester {

    private QueryState state;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        state = new QueryState(mgr);
    }

    @Test
    public void testGetFilter() {
        Map<String, Object> filter = state.getFilter();

        FilterUtilsTest utils = new FilterUtilsTest();

        utils.checkArray(FilterUtils.CLASS_OR, 2, filter);
        utils.checkEquals(FilterUtils.MSG_CHANNEL, Message.ADMIN, utils.getItem(filter, 0));
        utils.checkEquals(FilterUtils.MSG_CHANNEL, MY_HOST, utils.getItem(filter, 1));
    }

    @Test
    public void testStart() {
        state.start();

        Pair<Long, StateTimerTask> timer = onceTasks.remove();

        assertEquals(STD_IDENTIFICATION_MS, timer.first.longValue());
        assertNotNull(timer.second);
    }

    @Test
    public void testGoQuery() {
        assertNull(state.process(new Query()));
        assertEquals(ASGN3, state.getAssignments());
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
    public void testProcessLeader_NullAssignment() {
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
    public void testProcessLeader_NullSource() {
        String[] arr = {HOST2, PREV_HOST, MY_HOST};
        BucketAssignments asgn = new BucketAssignments(arr);
        Leader msg = new Leader(null, asgn);

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
    public void testProcessLeader_SourceIsNotAssignmentLeader() {
        String[] arr = {HOST2, PREV_HOST, MY_HOST};
        BucketAssignments asgn = new BucketAssignments(arr);
        Leader msg = new Leader(HOST2, asgn);

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
    public void testProcessLeader_EmptyAssignment() {
        Leader msg = new Leader(PREV_HOST, new BucketAssignments());

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
    public void testProcessLeader_BetterLeader() {
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
    public void testProcessQuery() {
        BucketAssignments asgn = new BucketAssignments(new String[] {HOST1, HOST2});
        mgr.startDistributing(asgn);
        state = new QueryState(mgr);

        State next = mock(State.class);
        when(mgr.goQuery()).thenReturn(next);

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
        when(mgr.goActive()).thenReturn(next);

        assertEquals(next, timer.second.fire(null));

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

        // tell it the leader is still active
        state.process(new Identification(PREV_HOST, asgn));

        Pair<Long, StateTimerTask> timer = onceTasks.remove();

        assertEquals(STD_IDENTIFICATION_MS, timer.first.longValue());
        assertNotNull(timer.second);

        // set up active state, as that's what it should return
        State next = mock(State.class);
        when(mgr.goActive()).thenReturn(next);

        assertEquals(next, timer.second.fire(null));

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

        // tell it the leader is still active
        state.process(new Identification(PREV_HOST, asgn));

        Pair<Long, StateTimerTask> timer = onceTasks.remove();

        assertEquals(STD_IDENTIFICATION_MS, timer.first.longValue());
        assertNotNull(timer.second);

        // set up inactive state, as that's what it should return
        State next = mock(State.class);
        when(mgr.goInactive()).thenReturn(next);

        assertEquals(next, timer.second.fire(null));

        // should NOT have published a Leader message
        assertTrue(admin.isEmpty());
    }

    @Test
    public void testHasAssignment() {
        // null assignment
        mgr.startDistributing(null);
        assertFalse(state.hasAssignment());

        // not in assignments
        state.setAssignments(new BucketAssignments(new String[] {HOST3}));
        assertFalse(state.hasAssignment());

        // it IS in the assignments
        state.setAssignments(new BucketAssignments(new String[] {MY_HOST}));
        assertTrue(state.hasAssignment());
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
