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
import java.util.Arrays;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.drools.pooling.message.BucketAssignments;
import org.onap.policy.drools.pooling.message.Identification;
import org.onap.policy.drools.pooling.message.Leader;
import org.onap.policy.drools.pooling.message.Message;
import org.onap.policy.drools.pooling.message.Query;

public class ProcessingStateTest extends BasicStateTester {
    
    private ProcessingState state;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        state = new ProcessingState(prevState, MY_HOST, ASGN3);
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
    public void testProcessLeader_NullAssignment() {
        Leader msg = new Leader(PREV_HOST, null);

        // should stay in the same state, and not start distributing
        assertNull(state.process(msg));
        verify(mgr, never()).startDistributing(any());
        verify(factory, never()).goActive(any(), any());
        verify(factory, never()).goInactive(any());
    }

    @Test
    public void testProcessLeader_EmptyAssignment() {
        Leader msg = new Leader(PREV_HOST, new BucketAssignments());

        // should stay in the same state, and not start distributing
        assertNull(state.process(msg));
        verify(mgr, never()).startDistributing(any());
        verify(factory, never()).goActive(any(), any());
        verify(factory, never()).goInactive(any());
    }

    @Test
    public void testProcessLeader_LeaderFollowsMyHost() {
        String[] arr = {HOST2, HOST3};
        BucketAssignments asgn = new BucketAssignments(arr);
        Leader msg = new Leader(HOST2, asgn);

        // should stay in the same state, and not start distributing
        assertNull(state.process(msg));
        verify(mgr, never()).startDistributing(any());
        verify(factory, never()).goActive(any(), any());
        verify(factory, never()).goInactive(any());
    }

    @Test
    public void testProcessLeader_MyHostAssigned() {
        String[] arr = {HOST2, PREV_HOST, MY_HOST};
        BucketAssignments asgn = new BucketAssignments(arr);
        Leader msg = new Leader(PREV_HOST, asgn);

        State next = mock(State.class);
        when(factory.goActive(state, asgn)).thenReturn(next);

        // should go Active and start distributing
        assertEquals(next, state.process(msg));
        verify(mgr).startDistributing(asgn);
        verify(factory, never()).goInactive(any());
    }

    @Test
    public void testProcessLeader_MyHostUnassigned() {
        String[] arr = {HOST2, PREV_HOST};
        BucketAssignments asgn = new BucketAssignments(arr);
        Leader msg = new Leader(PREV_HOST, asgn);

        State next = mock(State.class);
        when(factory.goInactive(state)).thenReturn(next);

        // should go Inactive and start distributing
        assertEquals(next, state.process(msg));
        verify(mgr).startDistributing(asgn);
        verify(factory, never()).goActive(any(), any());
    }

    @Test
    public void testProcessQuery() {
        assertNull(state.process(new Query()));

        Identification ident = captureAdminMessage(Identification.class);
        assertEquals(MY_HOST, ident.getSource());
        assertEquals(ASGN3, ident.getAssignments());
    }

    @Test
    public void testProcessingState() {
        /*
         * Null assignments should be OK.
         */
        state = new ProcessingState(prevState, LEADER, null);

        /*
         * Empty assignments should be OK.
         */
        state = new ProcessingState(prevState, LEADER, EMPTY_ASGN);
        assertEquals(MY_HOST, state.getHost());
        assertEquals(LEADER, state.getLeader());
        assertEquals(EMPTY_ASGN, state.getAssignments());

        /*
         * Now try something with assignments.
         */
        state = new ProcessingState(prevState, LEADER, ASGN3);

        /*
         * Prove the state is attached to the manager by invoking getHost(),
         * which delegates to the manager.
         */
        assertEquals(MY_HOST, state.getHost());

        assertEquals(LEADER, state.getLeader());
        assertEquals(ASGN3, state.getAssignments());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testProcessingState_NullLeader() {
        state = new ProcessingState(prevState, null, EMPTY_ASGN);
    }

    @Test
    public void testMakeIdentification() {
        Identification ident = state.makeIdentification();
        assertEquals(MY_HOST, ident.getSource());
        assertEquals(ASGN3, ident.getAssignments());
    }

    @Test
    public void testGetAssignments() {
        // assignments from constructor
        assertEquals(ASGN3, state.getAssignments());

        // null assignments
        state.setAssignments(null);
        assertNull(state.getAssignments());

        // empty assignments
        state.setAssignments(EMPTY_ASGN);
        assertEquals(EMPTY_ASGN, state.getAssignments());

        // non-empty assignments
        state.setAssignments(ASGN3);
        assertEquals(ASGN3, state.getAssignments());
    }

    @Test
    public void testSetAssignments() {
        state.setAssignments(null);
        verify(mgr, never()).startDistributing(any());

        state.setAssignments(ASGN3);
        verify(mgr).startDistributing(ASGN3);
    }

    @Test
    public void testGetLeader() {
        // check value from constructor
        assertEquals(MY_HOST, state.getLeader());

        state.setLeader(HOST2);
        assertEquals(HOST2, state.getLeader());

        state.setLeader(HOST3);
        assertEquals(HOST3, state.getLeader());
    }

    @Test
    public void testSetLeader() {
        state.setLeader(MY_HOST);
        assertEquals(MY_HOST, state.getLeader());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetLeader_Null() {
        state.setLeader(null);
    }

    @Test
    public void testIsLeader() {
        state.setLeader(MY_HOST);
        assertTrue(state.isLeader());

        state.setLeader(HOST2);
        assertFalse(state.isLeader());
    }

    @Test
    public void testBecomeLeader() {
        State next = mock(State.class);
        when(factory.goActive(any(), any())).thenReturn(next);

        assertEquals(next, state.becomeLeader(sortHosts(MY_HOST, HOST2)));

        Leader msg = captureAdminMessage(Leader.class);

        verify(factory).goActive(state, msg.getAssignments());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBecomeLeader_NotFirstAlive() {
        // alive list contains something before my host name
        state.becomeLeader(sortHosts(PREV_HOST, MY_HOST));
    }

    @Test
    public void testMakeLeader() throws Exception {
        state.becomeLeader(sortHosts(MY_HOST, HOST2));

        Leader msg = captureAdminMessage(Leader.class);

        // need a channel before invoking checkValidity()
        msg.setChannel(Message.ADMIN);

        msg.checkValidity();

        assertEquals(MY_HOST, msg.getSource());
        assertNotNull(msg.getAssignments());
        assertTrue(msg.getAssignments().hasAssignment(MY_HOST));
        assertTrue(msg.getAssignments().hasAssignment(HOST2));

        // this one wasn't in the list of hosts, so it should have been removed
        assertFalse(msg.getAssignments().hasAssignment(HOST1));
    }

    @Test
    public void testMakeAssignments() throws Exception {
        state.becomeLeader(sortHosts(MY_HOST, HOST2));

        captureAssignments().checkValidity();
    }

    @Test
    public void testMakeBucketArray_NullAssignments() {
        state = new ProcessingState(prevState, MY_HOST, null);
        state.becomeLeader(sortHosts(MY_HOST));

        String[] arr = captureHostArray();

        assertEquals(BucketAssignments.MAX_BUCKETS, arr.length);

        assertTrue(Arrays.asList(arr).stream().allMatch(host -> MY_HOST.equals(host)));
    }

    @Test
    public void testMakeBucketArray_ZeroAssignments() {
        // bucket assignment with a zero-length array
        state.setAssignments(new BucketAssignments(new String[0]));

        state.becomeLeader(sortHosts(MY_HOST));

        String[] arr = captureHostArray();

        assertEquals(BucketAssignments.MAX_BUCKETS, arr.length);

        assertTrue(Arrays.asList(arr).stream().allMatch(host -> MY_HOST.equals(host)));
    }

    @Test
    public void testMakeBucketArray() {
        /*
         * All hosts are still alive, so it should have the exact same
         * assignments as it had to start.
         */
        state.setAssignments(ASGN3);
        state.becomeLeader(sortHosts(HOST_ARR3));

        String[] arr = captureHostArray();

        assertTrue(arr != HOST_ARR3);
        assertEquals(Arrays.asList(HOST_ARR3), Arrays.asList(arr));
    }

    @Test
    public void testRemoveExcessHosts() {
        /**
         * All hosts are still alive, plus some others.
         */
        state.setAssignments(ASGN3);
        state.becomeLeader(sortHosts(MY_HOST, HOST1, HOST2, HOST3, HOST4));

        // assignments should be unchanged
        assertEquals(Arrays.asList(HOST_ARR3), captureHostList());
    }

    @Test
    public void testAddIndicesToHostBuckets() {
        // some are null, some hosts are no longer alive
        String[] asgn = {null, MY_HOST, HOST3, null, HOST4, HOST1, HOST2};

        state.setAssignments(new BucketAssignments(asgn));
        state.becomeLeader(sortHosts(MY_HOST, HOST1, HOST2));

        // every bucket should be assigned to one of the three hosts
        String[] expected = {MY_HOST, MY_HOST, HOST1, HOST2, MY_HOST, HOST1, HOST2};
        assertEquals(Arrays.asList(expected), captureHostList());
    }

    @Test
    public void testAssignNullBuckets() {
        /*
         * Ensure buckets are assigned to the host with the fewest buckets.
         */
        String[] asgn = {MY_HOST, HOST1, MY_HOST, null, null, null, null, null, MY_HOST};

        state.setAssignments(new BucketAssignments(asgn));
        state.becomeLeader(sortHosts(MY_HOST, HOST1, HOST2));

        String[] expected = {MY_HOST, HOST1, MY_HOST, HOST2, HOST1, HOST2, HOST1, HOST2, MY_HOST};
        assertEquals(Arrays.asList(expected), captureHostList());
    }

    @Test
    public void testRebalanceBuckets() {
        /**
         * Some are very lopsided.
         */
        String[] asgn = {MY_HOST, HOST1, MY_HOST, MY_HOST, MY_HOST, MY_HOST, HOST1, HOST2, HOST1, HOST3};

        state.setAssignments(new BucketAssignments(asgn));
        state.becomeLeader(sortHosts(MY_HOST, HOST1, HOST2, HOST3));

        String[] expected = {HOST2, HOST1, HOST3, MY_HOST, MY_HOST, MY_HOST, HOST1, HOST2, HOST1, HOST3};
        assertEquals(Arrays.asList(expected), captureHostList());
    }

}
