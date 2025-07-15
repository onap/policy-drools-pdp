/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018, 2020 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2024-2025 OpenInfra Foundation Europe. All rights reserved.
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.onap.policy.drools.pooling.CancellableScheduledTask;
import org.onap.policy.drools.pooling.PoolingManager;
import org.onap.policy.drools.pooling.message.BucketAssignments;
import org.onap.policy.drools.pooling.message.Heartbeat;
import org.onap.policy.drools.pooling.message.Identification;
import org.onap.policy.drools.pooling.message.Leader;
import org.onap.policy.drools.pooling.message.Offline;
import org.onap.policy.drools.pooling.message.Query;

class StateTest extends SupportBasicStateTester {

    private State state;

    /**
     * Setup.
     */
    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        state = new MyState(mgr);
    }

    @Test
    void testStatePoolingManager() {
        /*
         * Prove the state is attached to the manager by invoking getHost(), which
         * delegates to the manager.
         */
        assertEquals(MY_HOST, state.getHost());
    }

    @Test
    void testStateState() {
        // allocate a new state, copying from the old state
        state = new MyState(mgr);

        /*
         * Prove the state is attached to the manager by invoking getHost(), which
         * delegates to the manager.
         */
        assertEquals(MY_HOST, state.getHost());
    }

    @Test
    void testCancelTimers() {
        int delay = 100;
        int initDelay = 200;

        /*
         * Create three tasks.
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
    void testStart() {
        assertThatCode(() -> state.start()).doesNotThrowAnyException();
    }

    @Test
    void testGoStart() {
        State next = mock(State.class);
        when(mgr.goStart()).thenReturn(next);

        State next2 = state.goStart();
        assertEquals(next, next2);
    }

    @Test
    void testGoQuery() {
        State next = mock(State.class);
        when(mgr.goQuery()).thenReturn(next);

        State next2 = state.goQuery();
        assertEquals(next, next2);
    }

    @Test
    void testGoActive_WithAssignment() {
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
    void testGoActive_WithoutAssignment() {
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
    void testGoActive_NullAssignment() {
        State act = mock(State.class);
        State inact = mock(State.class);

        when(mgr.goActive()).thenReturn(act);
        when(mgr.goInactive()).thenReturn(inact);

        assertEquals(inact, state.goActive(null));

        verify(mgr, never()).startDistributing(any());
    }

    @Test
    void testGoInactive() {
        State next = mock(State.class);
        when(mgr.goInactive()).thenReturn(next);

        State next2 = state.goInactive();
        assertEquals(next, next2);
    }

    @Test
    void testProcessHeartbeat() {
        assertNull(state.process(new Heartbeat()));
    }

    @Test
    void testProcessIdentification() {
        assertNull(state.process(new Identification()));
    }

    @Test
    void testProcessLeader() {
        String[] arr = {HOST2, HOST1};
        BucketAssignments asgn = new BucketAssignments(arr);
        Leader msg = new Leader(HOST1, asgn);

        // should ignore it
        assertNull(state.process(msg));
        verify(mgr).startDistributing(asgn);
    }

    @Test
    void testProcessLeader_Invalid() {
        Leader msg = new Leader(PREV_HOST, null);

        // should stay in the same state, and not start distributing
        assertNull(state.process(msg));
        verify(mgr, never()).startDistributing(any());
        verify(mgr, never()).goActive();
        verify(mgr, never()).goInactive();
    }

    @Test
    void testIsValidLeader_NullAssignment() {
        assertFalse(state.isValid(new Leader(PREV_HOST, null)));
    }

    @Test
    void testIsValidLeader_NullSource() {
        String[] arr = {HOST2, PREV_HOST, MY_HOST};
        BucketAssignments asgn = new BucketAssignments(arr);
        assertFalse(state.isValid(new Leader(null, asgn)));
    }

    @Test
    void testIsValidLeader_EmptyAssignment() {
        assertFalse(state.isValid(new Leader(PREV_HOST, new BucketAssignments())));
    }

    @Test
    void testIsValidLeader_FromSelf() {
        String[] arr = {HOST2, MY_HOST};
        BucketAssignments asgn = new BucketAssignments(arr);

        assertFalse(state.isValid(new Leader(MY_HOST, asgn)));
    }

    @Test
    void testIsValidLeader_WrongLeader() {
        String[] arr = {HOST2, HOST3};
        BucketAssignments asgn = new BucketAssignments(arr);

        assertFalse(state.isValid(new Leader(HOST1, asgn)));
    }

    @Test
    void testIsValidLeader() {
        String[] arr = {HOST2, HOST1};
        BucketAssignments asgn = new BucketAssignments(arr);

        assertTrue(state.isValid(new Leader(HOST1, asgn)));
    }

    @Test
    void testProcessOffline() {
        assertNull(state.process(new Offline()));
    }

    @Test
    void testProcessQuery() {
        assertNull(state.process(new Query()));
    }

    @Test
    void testPublishIdentification() {
        Identification msg = new Identification();
        state.publish(msg);

        verify(mgr).publishAdmin(msg);
    }

    @Test
    void testPublishLeader() {
        Leader msg = new Leader();
        state.publish(msg);

        verify(mgr).publishAdmin(msg);
    }

    @Test
    void testPublishOffline() {
        Offline msg = new Offline();
        state.publish(msg);

        verify(mgr).publishAdmin(msg);
    }

    @Test
    void testPublishQuery() {
        Query msg = new Query();
        state.publish(msg);

        verify(mgr).publishAdmin(msg);
    }

    @Test
    void testPublishStringHeartbeat() {
        String chnl = "channelH";
        Heartbeat msg = new Heartbeat();

        state.publish(chnl, msg);

        verify(mgr).publish(chnl, msg);
    }

    @Test
    void testStartDistributing() {
        BucketAssignments asgn = new BucketAssignments();
        state.startDistributing(asgn);

        verify(mgr).startDistributing(asgn);
    }

    @Test
    void testStartDistributing_NullAssignments() {
        state.startDistributing(null);

        verify(mgr, never()).startDistributing(any());
    }

    @Test
    void testSchedule() {
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
    void testScheduleWithFixedDelay() {
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
    void testMissedHeartbeat() {
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
    void testInternalTopicFailed() {
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
    void testMakeHeartbeat() {
        long timestamp = 30000L;
        Heartbeat msg = state.makeHeartbeat(timestamp);

        assertEquals(MY_HOST, msg.getSource());
        assertEquals(timestamp, msg.getTimestampMs());
    }

    @Test
    void testMakeIdentification() {
        Identification ident = state.makeIdentification();
        assertEquals(MY_HOST, ident.getSource());
        assertEquals(ASGN3, ident.getAssignments());
    }

    @Test
    void testMakeOffline() {
        Offline msg = state.makeOffline();

        assertEquals(MY_HOST, msg.getSource());
    }

    @Test
    void testMakeQuery() {
        Query msg = state.makeQuery();

        assertEquals(MY_HOST, msg.getSource());
    }

    @Test
    void testGetHost() {
        assertEquals(MY_HOST, state.getHost());
    }

    @Test
    void testGetTopic() {
        assertEquals(MY_TOPIC, state.getTopic());
    }

    /**
     * State used for testing purposes, with abstract methods implemented.
     */
    private static class MyState extends State {

        public MyState(PoolingManager mgr) {
            super(mgr);
        }
    }
}
