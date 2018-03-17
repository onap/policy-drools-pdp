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
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.onap.policy.drools.event.comm.Topic.CommInfrastructure;
import org.onap.policy.drools.pooling.message.BucketAssignments;
import org.onap.policy.drools.pooling.message.Forward;
import org.onap.policy.drools.pooling.message.Heartbeat;
import org.onap.policy.drools.pooling.message.Identification;
import org.onap.policy.drools.pooling.message.Query;

public class ActiveStateTest extends BasicStateTester {

    /*
     * Futures returned by scheduleWithFixedDelay().
     */
    private ScheduledFuture<?> fut;
    private ScheduledFuture<?> fut2;
    private ScheduledFuture<?> fut3;

    private ActiveState state;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        fut = mock(ScheduledFuture.class);
        fut2 = mock(ScheduledFuture.class);
        fut3 = mock(ScheduledFuture.class);

        /*
         * Arrange to return the futures, one after another, with each invocation
         * of scheduleWithFixedDelay().
         */
        LinkedList<ScheduledFuture<?>> lst = new LinkedList<>(Arrays.asList(fut, fut2, fut3));
        
        when(mgr.scheduleWithFixedDelay(anyLong(), anyLong(), any(StateTimerTask.class))).then(new Answer<ScheduledFuture<?>>() {

            @Override
            public ScheduledFuture<?> answer(InvocationOnMock invocation) throws Throwable {
                return lst.remove(0);
            }            
        });
        

        state = new ActiveState(prevState, ASGN3);
    }

    @Test
    public void testStart() {
        state.start();

        // ensure the timers were created
        verify(mgr, atLeast(1)).scheduleWithFixedDelay(anyLong(), anyLong(), any(StateTimerTask.class));

        // ensure a heart beat was generated
        Pair<String, Heartbeat> msg = capturePublishedMessage(Heartbeat.class);
        assertEquals(MY_HOST, msg.second.getSource());
    }

    @Test
    public void testProcessHeartbeat_NullHost() {
        assertNull(state.process(new Heartbeat()));
        
        assertFalse(state.isMyHeartbeatSeen());
        assertFalse(state.isPredHeartbeatSeen());
        
        verify(factory, never()).goInactive(state);
        verify(factory, never()).goQuery(state, ASGN3);
    }

    @Test
    public void testProcessHeartbeat_MyHost() {        
        assertNull(state.process(new Heartbeat(MY_HOST, 0L)));
        
        assertTrue(state.isMyHeartbeatSeen());
        assertFalse(state.isPredHeartbeatSeen());
        
        verify(factory, never()).goInactive(state);
        verify(factory, never()).goQuery(state, ASGN3);
    }

    @Test
    public void testProcessHeartbeat_Predecessor() {    
        assertNull(state.process(new Heartbeat(HOST2, 0L)));
        
        assertFalse(state.isMyHeartbeatSeen());
        assertTrue(state.isPredHeartbeatSeen());
        
        verify(factory, never()).goInactive(state);
        verify(factory, never()).goQuery(state, ASGN3);
    }

    @Test
    public void testProcessHeartbeat_OtherHost() {  
        assertNull(state.process(new Heartbeat(HOST3, 0L)));
        
        assertFalse(state.isMyHeartbeatSeen());
        assertFalse(state.isPredHeartbeatSeen());
        
        verify(factory, never()).goInactive(state);
        verify(factory, never()).goQuery(state, ASGN3);
    }

    @Test
    public void testProcessOffline() {
        fail("Not yet implemented");
    }

    @Test
    public void testProcessQuery() {
        // set up the state to which it "should" go next
        State next = mock(State.class);
        when(factory.goQuery(state, ASGN3)).thenReturn(next);

        assertEquals(next, state.process(new Query()));

        Identification msg = this.captureAdminMessage(Identification.class);
        assertEquals(MY_HOST, msg.getSource());
        assertEquals(ASGN3, msg.getAssignments());
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
        state = new ActiveState(prevState, new BucketAssignments(new String[] {MY_HOST, MY_HOST}));
        assertEquals(null, state.getSuccHost());
        assertEquals("", state.getPredHost());

        // two hosts
        state = new ActiveState(prevState, new BucketAssignments(new String[] {MY_HOST, HOST2}));
        assertEquals(HOST2, state.getSuccHost());
        assertEquals(HOST2, state.getPredHost());

        // three hosts
        state = new ActiveState(prevState, new BucketAssignments(new String[] {HOST3, MY_HOST, HOST2}));
        assertEquals(HOST2, state.getSuccHost());
        assertEquals(HOST3, state.getPredHost());

        // more hosts
        state = new ActiveState(prevState, new BucketAssignments(new String[] {HOST3, MY_HOST, HOST2, HOST4}));
        assertEquals(HOST2, state.getSuccHost());
        assertEquals(HOST4, state.getPredHost());
    }

    @Test
    public void testAddTimers() {
        fail("Not yet implemented");
    }

    @Test
    public void testGenHeartbeat_OneHost() {
        // if only one host (i.e., itself)
        state = new ActiveState(prevState, new BucketAssignments(new String[] {MY_HOST}));

        state.start();

        verify(mgr, times(1)).publish(any(), any());

        Pair<String, Heartbeat> msg = capturePublishedMessage(Heartbeat.class);
        assertEquals(MY_HOST, msg.first);
        assertEquals(MY_HOST, msg.second.getSource());
    }

    @Test
    public void testGenHeartbeat_MultipleHosts() {
        state.start();

        verify(mgr, times(2)).publish(any(), any());

        Pair<String, Heartbeat> msg;
        int index = 0;

        // this message should go to itself
        msg = capturePublishedMessage(Heartbeat.class, index++);
        assertEquals(MY_HOST, msg.first);
        assertEquals(MY_HOST, msg.second.getSource());

        // this message should go to its successor
        msg = capturePublishedMessage(Heartbeat.class, index++);
        assertEquals(HOST1, msg.first);
        assertEquals(MY_HOST, msg.second.getSource());
    }

}
