/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018, 2020 AT&T Intellectual Property. All rights reserved.
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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.drools.pooling.CancellableScheduledTask;
import org.onap.policy.drools.pooling.PoolingManager;
import org.onap.policy.drools.pooling.message.BucketAssignments;
import org.onap.policy.drools.pooling.message.Forward;
import org.onap.policy.drools.pooling.message.Heartbeat;
import org.onap.policy.drools.pooling.message.Identification;
import org.onap.policy.drools.pooling.message.Leader;
import org.onap.policy.drools.pooling.message.Message;
import org.onap.policy.drools.pooling.message.Offline;
import org.onap.policy.drools.pooling.message.Query;

public class StateTest extends SupportBasicStateTester {

    private State state;

    /**
     * Setup.
     */
    @Before
    public void setUp() throws Exception {
        super.setUp();

        state = new MyState(mgr);
    }

    @Test
    public void testStatePoolingManager() {
        /*
         * Prove the state is attached to the manager by invoking getHost(), which
         * delegates to the manager.
         */
        assertEquals(MY_HOST, state.getHost());
    }

    @Test
    public void testStateState() {
        // allocate a new state, copying from the old state
        state = new MyState(mgr);

        /*
         * Prove the state is attached to the manager by invoking getHost(), which
         * delegates to the manager.
         */
        assertEquals(MY_HOST, state.getHost());
    }

    @Test
    public void testCancelTimers() {
        int delay = 100;
        int initDelay = 200;

        /*
         * Create three tasks tasks.
         */

        StateTimerTask task1 = mock(StateTimerTask.class);
        StateTimerTask task2 = mock(StateTimerTask.class);
        StateTimerTask task3 = mock(StateTimerTask.class);

        // two tasks via schedule()
        state.schedule(delay, task1);
        state.schedule(delay, task2);

        // one task via scheduleWithFixedDelay()
        state.scheduleWithFixedDelay(initDelay, delay, task3);

        // ensure all were scheduled, but not yet canceled
        verify(mgr).schedule(delay, task1);
        verify(mgr).schedule(delay, task2);
        verify(mgr).scheduleWithFixedDelay(initDelay, delay, task3);

        CancellableScheduledTask sched1 = onceSchedules.removeFirst();
        CancellableScheduledTask sched2 = onceSchedules.removeFirst();
        CancellableScheduledTask sched3 = repeatedSchedules.removeFirst();

        verify(sched1, never()).cancel();
        verify(sched2, never()).cancel();
        verify(sched3, never()).cancel();

        /*
         * Cancel the timers.
         */
        state.cancelTimers();

        // verify that all were cancelled
        verify(sched1).cancel();
        verify(sched2).cancel();
        verify(sched3).cancel();
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
        assertThatCode(() -> state.start()).doesNotThrowAnyException();
    }

    @Test
    public void testGoStart() {
        State next = mock(State.class);
        when(mgr.goStart()).thenReturn(next);

        State next2 = state.goStart();
        assertEquals(next, next2);
    }

    @Test
    public void testGoQuery() {
        State next = mock(State.class);
        when(mgr.goQuery()).thenReturn(next);

        State next2 = state.goQuery();
        assertEquals(next, next2);
    }

    @Test
    public void testGoActive_WithAssignment() {
        State act = mock(State.class);
        State inact = mock(State.class);

        when(mgr.goActive()).thenReturn(act);
        when(mgr.goInactive()).thenReturn(inact);

        String[] arr = {HOST2, PREV_HOST, MY_HOST};
        BucketAssignments asgn = new BucketAssignments(arr);

        assertEquals(act, state.goActive(asgn));

        verify(mgr).startDistributing(asgn);
    }

    @Test
    public void testGoActive_WithoutAssignment() {
        State act = mock(State.class);
        State inact = mock(State.class);

        when(mgr.goActive()).thenReturn(act);
        when(mgr.goInactive()).thenReturn(inact);

        String[] arr = {HOST2, PREV_HOST};
        BucketAssignments asgn = new BucketAssignments(arr);

        assertEquals(inact, state.goActive(asgn));

        verify(mgr).startDistributing(asgn);
    }

    @Test
    public void testGoActive_NullAssignment() {
        State act = mock(State.class);
        State inact = mock(State.class);

        when(mgr.goActive()).thenReturn(act);
        when(mgr.goInactive()).thenReturn(inact);

        assertEquals(inact, state.goActive(null));

        verify(mgr, never()).startDistributing(any());
    }

    @Test
    public void testGoInactive() {
        State next = mock(State.class);
        when(mgr.goInactive()).thenReturn(next);

        State next2 = state.goInactive();
        assertEquals(next, next2);
    }

    @Test
    public void testProcessForward() {
        Forward msg = new Forward();
        assertNull(state.process(msg));

        verify(mgr, never()).handle(msg);

        msg.setChannel(MY_HOST);
        assertNull(state.process(msg));

        verify(mgr).handle(msg);
    }

    @Test
    public void testProcessHeartbeat() {
        assertNull(state.process(new Heartbeat()));
    }

    @Test
    public void testProcessIdentification() {
        assertNull(state.process(new Identification()));
    }

    @Test
    public void testProcessLeader() {
        String[] arr = {HOST2, HOST1};
        BucketAssignments asgn = new BucketAssignments(arr);
        Leader msg = new Leader(HOST1, asgn);

        // should ignore it
        assertEquals(null, state.process(msg));
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
    }

    @Test
    public void testIsValidLeader_NullAssignment() {
        assertFalse(state.isValid(new Leader(PREV_HOST, null)));
    }

    @Test
    public void testIsValidLeader_NullSource() {
        String[] arr = {HOST2, PREV_HOST, MY_HOST};
        BucketAssignments asgn = new BucketAssignments(arr);
        assertFalse(state.isValid(new Leader(null, asgn)));
    }

    @Test
    public void testIsValidLeader_EmptyAssignment() {
        assertFalse(state.isValid(new Leader(PREV_HOST, new BucketAssignments())));
    }

    @Test
    public void testIsValidLeader_FromSelf() {
        String[] arr = {HOST2, MY_HOST};
        BucketAssignments asgn = new BucketAssignments(arr);

        assertFalse(state.isValid(new Leader(MY_HOST, asgn)));
    }

    @Test
    public void testIsValidLeader_WrongLeader() {
        String[] arr = {HOST2, HOST3};
        BucketAssignments asgn = new BucketAssignments(arr);

        assertFalse(state.isValid(new Leader(HOST1, asgn)));
    }

    @Test
    public void testIsValidLeader() {
        String[] arr = {HOST2, HOST1};
        BucketAssignments asgn = new BucketAssignments(arr);

        assertTrue(state.isValid(new Leader(HOST1, asgn)));
    }

    @Test
    public void testProcessOffline() {
        assertNull(state.process(new Offline()));
    }

    @Test
    public void testProcessQuery() {
        assertNull(state.process(new Query()));
    }

    @Test
    public void testPublishIdentification() {
        Identification msg = new Identification();
        state.publish(msg);

        verify(mgr).publishAdmin(msg);
    }

    @Test
    public void testPublishLeader() {
        Leader msg = new Leader();
        state.publish(msg);

        verify(mgr).publishAdmin(msg);
    }

    @Test
    public void testPublishOffline() {
        Offline msg = new Offline();
        state.publish(msg);

        verify(mgr).publishAdmin(msg);
    }

    @Test
    public void testPublishQuery() {
        Query msg = new Query();
        state.publish(msg);

        verify(mgr).publishAdmin(msg);
    }

    @Test
    public void testPublishStringForward() {
        String chnl = "channelF";
        Forward msg = new Forward();

        state.publish(chnl, msg);

        verify(mgr).publish(chnl, msg);
    }

    @Test
    public void testPublishStringHeartbeat() {
        String chnl = "channelH";
        Heartbeat msg = new Heartbeat();

        state.publish(chnl, msg);

        verify(mgr).publish(chnl, msg);
    }

    @Test
    public void testStartDistributing() {
        BucketAssignments asgn = new BucketAssignments();
        state.startDistributing(asgn);

        verify(mgr).startDistributing(asgn);
    }

    @Test
    public void testStartDistributing_NullAssignments() {
        state.startDistributing(null);

        verify(mgr, never()).startDistributing(any());
    }

    @Test
    public void testSchedule() {
        int delay = 100;

        StateTimerTask task = mock(StateTimerTask.class);

        state.schedule(delay, task);

        CancellableScheduledTask sched = onceSchedules.removeFirst();

        // scheduled, but not canceled yet
        verify(mgr).schedule(delay, task);
        verify(sched, never()).cancel();

        /*
         * Ensure the state added the timer to its list by telling it to cancel its timers
         * and then seeing if this timer was canceled.
         */
        state.cancelTimers();
        verify(sched).cancel();
    }

    @Test
    public void testScheduleWithFixedDelay() {
        int initdel = 100;
        int delay = 200;

        StateTimerTask task = mock(StateTimerTask.class);

        state.scheduleWithFixedDelay(initdel, delay, task);

        CancellableScheduledTask sched = repeatedSchedules.removeFirst();

        // scheduled, but not canceled yet
        verify(mgr).scheduleWithFixedDelay(initdel, delay, task);
        verify(sched, never()).cancel();

        /*
         * Ensure the state added the timer to its list by telling it to cancel its timers
         * and then seeing if this timer was canceled.
         */
        state.cancelTimers();
        verify(sched).cancel();
    }

    @Test
    public void testMissedHeartbeat() {
        State next = mock(State.class);
        when(mgr.goStart()).thenReturn(next);

        State next2 = state.missedHeartbeat();
        assertEquals(next, next2);

        // should continue to distribute
        verify(mgr, never()).startDistributing(null);

        Offline msg = captureAdminMessage(Offline.class);
        assertEquals(MY_HOST, msg.getSource());
    }

    @Test
    public void testInternalTopicFailed() {
        State next = mock(State.class);
        when(mgr.goInactive()).thenReturn(next);

        State next2 = state.internalTopicFailed();
        assertEquals(next, next2);

        // should stop distributing
        verify(mgr).startDistributing(null);

        Offline msg = captureAdminMessage(Offline.class);
        assertEquals(MY_HOST, msg.getSource());
    }

    @Test
    public void testMakeHeartbeat() {
        long timestamp = 30000L;
        Heartbeat msg = state.makeHeartbeat(timestamp);

        assertEquals(MY_HOST, msg.getSource());
        assertEquals(timestamp, msg.getTimestampMs());
    }

    @Test
    public void testMakeIdentification() {
        Identification ident = state.makeIdentification();
        assertEquals(MY_HOST, ident.getSource());
        assertEquals(ASGN3, ident.getAssignments());
    }

    @Test
    public void testMakeOffline() {
        Offline msg = state.makeOffline();

        assertEquals(MY_HOST, msg.getSource());
    }

    @Test
    public void testMakeQuery() {
        Query msg = state.makeQuery();

        assertEquals(MY_HOST, msg.getSource());
    }

    @Test
    public void testGetHost() {
        assertEquals(MY_HOST, state.getHost());
    }

    @Test
    public void testGetTopic() {
        assertEquals(MY_TOPIC, state.getTopic());
    }

    /**
     * State used for testing purposes, with abstract methods implemented.
     */
    private class MyState extends State {

        public MyState(PoolingManager mgr) {
            super(mgr);
        }
    }
}
