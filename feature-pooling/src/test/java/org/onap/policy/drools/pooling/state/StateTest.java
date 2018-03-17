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
import static org.junit.Assert.assertNull;
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
import org.onap.policy.drools.pooling.message.Forward;
import org.onap.policy.drools.pooling.message.Heartbeat;
import org.onap.policy.drools.pooling.message.Identification;
import org.onap.policy.drools.pooling.message.Leader;
import org.onap.policy.drools.pooling.message.Offline;
import org.onap.policy.drools.pooling.message.Query;
import org.onap.policy.drools.pooling.state.State.StateFactory;

public class StateTest {
    private static final String MY_HOST = "myHost";
    private static final String MY_TOPIC = "myTopic";

    private static StateFactory saveFactory;

    private StateFactory factory;
    private PoolingManager mgr;
    private State state;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        saveFactory = State.getFactory();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        State.setFactory(saveFactory);
    }

    @Before
    public void setUp() throws Exception {
        factory = mock(StateFactory.class);
        mgr = mock(PoolingManager.class);

        when(mgr.getHost()).thenReturn(MY_HOST);
        when(mgr.getTopic()).thenReturn(MY_TOPIC);

        State.setFactory(factory);

        state = new MyState(mgr);
    }

    @Test
    public void testStatePoolingManager() {
        /*
         * Prove the state is attached to the manager by invoking getHost(),
         * which delegates to the manager.
         */
        assertEquals(MY_HOST, state.getHost());
    }

    @Test
    public void testStateState() {
        // allocate a new state, copying from the old state
        state = new MyState(state);

        /*
         * Prove the state is attached to the manager by invoking getHost(),
         * which delegates to the manager.
         */
        assertEquals(MY_HOST, state.getHost());
    }

    @Test
    public void testCancelTimers() {
        int delay = 100;

        /*
         * Create two tasks.
         */

        // task #1
        StateTimerTask task1 = mock(StateTimerTask.class);
        ScheduledFuture<?> fut1 = mock(ScheduledFuture.class);

        when(mgr.schedule(delay, task1)).thenAnswer(xxx -> fut1);

        // task #2
        StateTimerTask task2 = mock(StateTimerTask.class);
        ScheduledFuture<?> fut2 = mock(ScheduledFuture.class);

        when(mgr.schedule(delay, task2)).thenAnswer(xxx -> fut2);

        /*
         * Insert both tasks.
         */
        state.schedule(delay, task1);
        state.schedule(delay, task2);

        // ensure both were scheduled, but not yet canceled
        verify(mgr).schedule(delay, task1);
        verify(mgr).schedule(delay, task2);

        verify(fut1, never()).cancel(false);
        verify(fut2, never()).cancel(false);

        /*
         * Cancel the timers.
         */
        state.cancelTimers();

        // verify that both were cancelled
        verify(fut1).cancel(false);
        verify(fut2).cancel(false);
    }

    @Test
    public void testStart() {
        state.start();
    }

    @Test
    public void testStop() {
        state.stop();

        ArgumentCaptor<Offline> msgcap = ArgumentCaptor.forClass(Offline.class);

        verify(mgr).publishAdmin(msgcap.capture());

        assertEquals(MY_HOST, msgcap.getValue().getSource());
    }

    @Test
    public void testGoStart() {
        State next = mock(State.class);
        when(factory.goStart(state)).thenReturn(next);

        State next2 = state.goStart();
        assertEquals(next, next2);
    }

    @Test
    public void testGoQuery() {
        String leader = "leaderA";
        BucketAssignments asgn = new BucketAssignments();
        
        State next = mock(State.class);
        when(factory.goQuery(state, leader, asgn)).thenReturn(next);

        State next2 = state.goQuery(leader, asgn);
        assertEquals(next, next2);
    }

    @Test
    public void testGoInactive() {
        State next = mock(State.class);
        when(factory.goInactive(state)).thenReturn(next);

        State next2 = state.goInactive();
        assertEquals(next, next2);
    }

    @Test
    public void testProcessForward() {
        Forward msg = new Forward();
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
        Leader msg = new Leader();
        BucketAssignments asgn = new BucketAssignments();
        msg.setAssignments(asgn);

        assertNull(state.process(msg));

        verify(mgr).startDistributing(asgn);
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
    public void testSchedule() {
        int delay = 100;

        StateTimerTask task = mock(StateTimerTask.class);
        ScheduledFuture<?> fut = mock(ScheduledFuture.class);

        when(mgr.schedule(delay, task)).thenAnswer(xxx -> fut);

        state.schedule(delay, task);

        // scheduled, but not canceled yet
        verify(mgr).schedule(delay, task);
        verify(fut, never()).cancel(false);

        /*
         * Ensure the state added the timer to its list by telling it to cancel
         * its timers and then seeing if this timer was canceled.
         */
        state.cancelTimers();
        verify(fut).cancel(false);
    }

    @Test
    public void testScheduleWithFixedDelay() {
        int initdel = 100;
        int delay = 200;

        StateTimerTask task = mock(StateTimerTask.class);
        ScheduledFuture<?> fut = mock(ScheduledFuture.class);

        when(mgr.scheduleWithFixedDelay(initdel, delay, task)).thenAnswer(xxx -> fut);

        state.scheduleWithFixedDelay(initdel, delay, task);

        // scheduled, but not canceled yet
        verify(mgr).scheduleWithFixedDelay(initdel, delay, task);
        verify(fut, never()).cancel(false);

        /*
         * Ensure the state added the timer to its list by telling it to cancel
         * its timers and then seeing if this timer was canceled.
         */
        state.cancelTimers();
        verify(fut).cancel(false);
    }

    @Test
    public void testInternalTopicFailed() {
        State next = mock(State.class);
        when(factory.goInactive(state)).thenReturn(next);

        State next2 = state.internalTopicFailed();
        assertEquals(next, next2);

        verify(mgr).internalTopicFailed();
    }

    @Test
    public void testMakeHeartbeat() {
        long timestamp = 30000L;
        Heartbeat msg = state.makeHeartbeat(timestamp);

        assertEquals(MY_HOST, msg.getSource());
        assertEquals(timestamp, msg.getTimestampMs());
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

        public MyState(State oldState) {
            super(oldState);
        }

        @Override
        public Map<String, Object> getFilter() {
            throw new UnsupportedOperationException("cannot get filter");
        }
    }
}
