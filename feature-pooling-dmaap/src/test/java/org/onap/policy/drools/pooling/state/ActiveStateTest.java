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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.drools.pooling.message.BucketAssignments;
import org.onap.policy.drools.pooling.message.Heartbeat;
import org.onap.policy.drools.pooling.message.Leader;
import org.onap.policy.drools.pooling.message.Message;
import org.onap.policy.drools.pooling.message.Offline;
import org.onap.policy.drools.pooling.message.Query;

public class ActiveStateTest extends SupportBasicStateTester {

    private ActiveState state;

    /**
     * Setup.
     */
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        state = new ActiveState(mgr);
    }

    @Test
    public void testStart() {
        state.start();

        // ensure the timers were created
        verify(mgr, atLeast(1)).scheduleWithFixedDelay(anyLong(), anyLong(), any(StateTimerTask.class));

        // ensure a heart beat was generated
        Pair<String, Heartbeat> msg = capturePublishedMessage(Heartbeat.class);
        assertEquals(MY_HOST, msg.getRight().getSource());
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
    public void testProcessHeartbeat_NullHost() {
        assertNull(state.process(new Heartbeat()));

        assertFalse(state.isMyHeartbeatSeen());
        assertFalse(state.isPredHeartbeatSeen());

        verify(mgr, never()).goInactive();
        verify(mgr, never()).goQuery();
    }

    @Test
    public void testProcessHeartbeat_MyHost() {
        assertNull(state.process(new Heartbeat(MY_HOST, 0L)));

        assertTrue(state.isMyHeartbeatSeen());
        assertFalse(state.isPredHeartbeatSeen());

        verify(mgr, never()).goInactive();
        verify(mgr, never()).goQuery();
    }

    @Test
    public void testProcessHeartbeat_Predecessor() {
        assertNull(state.process(new Heartbeat(HOST2, 0L)));

        assertFalse(state.isMyHeartbeatSeen());
        assertTrue(state.isPredHeartbeatSeen());

        verify(mgr, never()).goInactive();
        verify(mgr, never()).goQuery();
    }

    @Test
    public void testProcessHeartbeat_OtherHost() {
        assertNull(state.process(new Heartbeat(HOST3, 0L)));

        assertFalse(state.isMyHeartbeatSeen());
        assertFalse(state.isPredHeartbeatSeen());

        verify(mgr, never()).goInactive();
        verify(mgr, never()).goQuery();
    }

    @Test
    public void testProcessOffline_NullHost() {
        // should be ignored
        assertNull(state.process(new Offline()));
    }

    @Test
    public void testProcessOffline_UnassignedHost() {
        // HOST4 is not in the assignment list - should be ignored
        assertNull(state.process(new Offline(HOST4)));
    }

    @Test
    public void testProcessOffline_IAmLeader() {
        // configure the next state
        State next = mock(State.class);
        when(mgr.goActive()).thenReturn(next);

        // one of the assigned hosts went offline
        assertEquals(next, state.process(new Offline(HOST1)));

        // should have sent a new Leader message
        Leader msg = captureAdminMessage(Leader.class);

        assertEquals(MY_HOST, msg.getSource());

        // check new bucket assignments
        assertEquals(Arrays.asList(MY_HOST, MY_HOST, HOST2), Arrays.asList(msg.getAssignments().getHostArray()));
    }

    @Test
    public void testProcessOffline_PredecessorIsLeaderNowOffline() {
        // configure the next state
        State next = mock(State.class);
        when(mgr.goActive()).thenReturn(next);

        // I am not the leader, but my predecessor was
        mgr.startDistributing(new BucketAssignments(new String[] {PREV_HOST, MY_HOST, HOST1}));
        state = new ActiveState(mgr);

        // my predecessor went offline
        assertEquals(next, state.process(new Offline(PREV_HOST)));

        // should have sent a new Leader message
        Leader msg = captureAdminMessage(Leader.class);

        assertEquals(MY_HOST, msg.getSource());

        // check new bucket assignments
        assertEquals(Arrays.asList(MY_HOST, MY_HOST, HOST1), Arrays.asList(msg.getAssignments().getHostArray()));
    }

    @Test
    public void testProcessOffline__PredecessorIsNotLeaderNowOffline() {
        // I am not the leader, and neither is my predecessor
        mgr.startDistributing(new BucketAssignments(new String[] {PREV_HOST, MY_HOST, PREV_HOST2}));
        state = new ActiveState(mgr);

        /*
         *
         * PREV_HOST2 has buckets and is my predecessor, but it isn't the leader thus
         * should be ignored.
         */
        assertNull(state.process(new Offline(PREV_HOST2)));
    }

    @Test
    public void testProcessOffline_OtherAssignedHostOffline() {
        // I am not the leader
        mgr.startDistributing(new BucketAssignments(new String[] {PREV_HOST, MY_HOST, HOST1}));
        state = new ActiveState(mgr);

        /*
         * HOST1 has buckets, but it isn't the leader and it isn't my predecessor, thus
         * should be ignored.
         */
        assertNull(state.process(new Offline(HOST1)));
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
    public void testProcessLeader_BadLeader() {
        String[] arr = {HOST2, HOST1};
        BucketAssignments asgn = new BucketAssignments(arr);

        // now send a Leader message for that leader
        Leader msg = new Leader(HOST1, asgn);

        State next = mock(State.class);
        when(mgr.goQuery()).thenReturn(next);

        // should go Query, but not start distributing
        assertEquals(next, state.process(msg));
        verify(mgr, never()).startDistributing(asgn);
    }

    @Test
    public void testProcessLeader_GoodLeader() {
        String[] arr = {HOST2, PREV_HOST, MY_HOST};
        BucketAssignments asgn = new BucketAssignments(arr);

        // now send a Leader message for that leader
        Leader msg = new Leader(PREV_HOST, asgn);

        State next = mock(State.class);
        when(mgr.goActive()).thenReturn(next);

        // should go Active and start distributing
        assertEquals(next, state.process(msg));
        verify(mgr).startDistributing(asgn);
    }

    @Test
    public void testActiveState() {
        assertEquals(MY_HOST, state.getLeader());
        assertEquals(ASGN3, state.getAssignments());

        // verify that it determined its neighbors
        assertEquals(HOST1, state.getSuccHost());
        assertEquals(HOST2, state.getPredHost());
    }

    @Test
    public void testDetmNeighbors() {
        // if only one host (i.e., itself)
        mgr.startDistributing(new BucketAssignments(new String[] {MY_HOST, MY_HOST}));
        state = new ActiveState(mgr);
        assertEquals(null, state.getSuccHost());
        assertEquals("", state.getPredHost());

        // two hosts
        mgr.startDistributing(new BucketAssignments(new String[] {MY_HOST, HOST2}));
        state = new ActiveState(mgr);
        assertEquals(HOST2, state.getSuccHost());
        assertEquals(HOST2, state.getPredHost());

        // three hosts
        mgr.startDistributing(new BucketAssignments(new String[] {HOST3, MY_HOST, HOST2}));
        state = new ActiveState(mgr);
        assertEquals(HOST2, state.getSuccHost());
        assertEquals(HOST3, state.getPredHost());

        // more hosts
        mgr.startDistributing(new BucketAssignments(new String[] {HOST3, MY_HOST, HOST2, HOST4}));
        state = new ActiveState(mgr);
        assertEquals(HOST2, state.getSuccHost());
        assertEquals(HOST4, state.getPredHost());
    }

    @Test
    public void testAddTimers_WithPredecessor() {
        // invoke start() to add the timers
        state.start();

        assertEquals(3, repeatedSchedules.size());

        Triple<Long, Long, StateTimerTask> timer;

        // heart beat generator
        timer = repeatedTasks.remove();
        assertEquals(STD_INTER_HEARTBEAT_MS, timer.getLeft().longValue());
        assertEquals(STD_INTER_HEARTBEAT_MS, timer.getMiddle().longValue());

        // my heart beat checker
        timer = repeatedTasks.remove();
        assertEquals(STD_ACTIVE_HEARTBEAT_MS, timer.getLeft().longValue());
        assertEquals(STD_ACTIVE_HEARTBEAT_MS, timer.getMiddle().longValue());

        // predecessor's heart beat checker
        timer = repeatedTasks.remove();
        assertEquals(STD_ACTIVE_HEARTBEAT_MS, timer.getLeft().longValue());
        assertEquals(STD_ACTIVE_HEARTBEAT_MS, timer.getMiddle().longValue());
    }

    @Test
    public void testAddTimers_SansPredecessor() {
        // only one host, thus no predecessor
        mgr.startDistributing(new BucketAssignments(new String[] {MY_HOST, MY_HOST}));
        state = new ActiveState(mgr);

        // invoke start() to add the timers
        state.start();

        assertEquals(2, repeatedSchedules.size());

        Triple<Long, Long, StateTimerTask> timer;

        // heart beat generator
        timer = repeatedTasks.remove();
        assertEquals(STD_INTER_HEARTBEAT_MS, timer.getLeft().longValue());
        assertEquals(STD_INTER_HEARTBEAT_MS, timer.getMiddle().longValue());

        // my heart beat checker
        timer = repeatedTasks.remove();
        assertEquals(STD_ACTIVE_HEARTBEAT_MS, timer.getLeft().longValue());
        assertEquals(STD_ACTIVE_HEARTBEAT_MS, timer.getMiddle().longValue());
    }

    @Test
    public void testAddTimers_HeartbeatGenerator() {
        // only one host so we only have to look at one heart beat at a time
        mgr.startDistributing(new BucketAssignments(new String[] {MY_HOST}));
        state = new ActiveState(mgr);

        // invoke start() to add the timers
        state.start();

        Triple<Long, Long, StateTimerTask> task = repeatedTasks.remove();

        verify(mgr).publish(anyString(), any(Heartbeat.class));

        // fire the task
        assertNull(task.getRight().fire());

        // should have generated a second pair of heart beats
        verify(mgr, times(2)).publish(anyString(), any(Heartbeat.class));

        Pair<String, Heartbeat> msg = capturePublishedMessage(Heartbeat.class);
        assertEquals(MY_HOST, msg.getLeft());
        assertEquals(MY_HOST, msg.getRight().getSource());
    }

    @Test
    public void testAddTimers_MyHeartbeatSeen() {
        // invoke start() to add the timers
        state.start();

        Triple<Long, Long, StateTimerTask> task = repeatedTasks.get(1);

        // indicate that this host is still alive
        state.process(new Heartbeat(MY_HOST, 0L));

        // set up next state
        State next = mock(State.class);
        when(mgr.goInactive()).thenReturn(next);

        // fire the task - should not transition
        assertNull(task.getRight().fire());

        verify(mgr, never()).publishAdmin(any(Query.class));
    }

    @Test
    public void testAddTimers_MyHeartbeatMissed() {
        // invoke start() to add the timers
        state.start();

        Triple<Long, Long, StateTimerTask> task = repeatedTasks.get(1);

        // set up next state
        State next = mock(State.class);
        when(mgr.goStart()).thenReturn(next);

        // fire the task - should transition
        assertEquals(next, task.getRight().fire());

        // should continue to distribute
        verify(mgr, never()).startDistributing(null);

        // should publish an offline message
        Offline msg = captureAdminMessage(Offline.class);
        assertEquals(MY_HOST, msg.getSource());
    }

    @Test
    public void testAddTimers_PredecessorHeartbeatSeen() {
        // invoke start() to add the timers
        state.start();

        Triple<Long, Long, StateTimerTask> task = repeatedTasks.get(2);

        // indicate that the predecessor is still alive
        state.process(new Heartbeat(HOST2, 0L));

        // set up next state, just in case
        State next = mock(State.class);
        when(mgr.goQuery()).thenReturn(next);

        // fire the task - should NOT transition
        assertNull(task.getRight().fire());

        verify(mgr, never()).publishAdmin(any(Query.class));
    }

    @Test
    public void testAddTimers_PredecessorHeartbeatMissed() {
        // invoke start() to add the timers
        state.start();

        Triple<Long, Long, StateTimerTask> task = repeatedTasks.get(2);

        // set up next state
        State next = mock(State.class);
        when(mgr.goQuery()).thenReturn(next);

        // fire the task - should transition
        assertEquals(next, task.getRight().fire());

        verify(mgr).publishAdmin(any(Query.class));
    }

    @Test
    public void testGenHeartbeat_OneHost() {
        // only one host (i.e., itself)
        mgr.startDistributing(new BucketAssignments(new String[] {MY_HOST}));
        state = new ActiveState(mgr);

        state.start();

        verify(mgr, times(1)).publish(any(), any());

        Pair<String, Heartbeat> msg = capturePublishedMessage(Heartbeat.class);
        assertEquals(MY_HOST, msg.getLeft());
        assertEquals(MY_HOST, msg.getRight().getSource());
    }

    @Test
    public void testGenHeartbeat_MultipleHosts() {
        state.start();

        verify(mgr, times(2)).publish(any(), any());

        Pair<String, Heartbeat> msg;
        int index = 0;

        // this message should go to itself
        msg = capturePublishedMessage(Heartbeat.class, index++);
        assertEquals(MY_HOST, msg.getLeft());
        assertEquals(MY_HOST, msg.getRight().getSource());

        // this message should go to its successor
        msg = capturePublishedMessage(Heartbeat.class, index++);
        assertEquals(HOST1, msg.getLeft());
        assertEquals(MY_HOST, msg.getRight().getSource());
    }

}
