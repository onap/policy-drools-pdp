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
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import org.onap.policy.drools.pooling.state.ProcessingState.HostBucket;

public class ProcessingStateTest extends SupportBasicStateTester {

    private ProcessingState state;
    private HostBucket hostBucket;

    /**
     * Setup.
     */
    @Before
    public void setUp() throws Exception {
        super.setUp();

        state = new ProcessingState(mgr, MY_HOST);
        hostBucket = new HostBucket(MY_HOST);
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
    public void testProcessQuery() {
        State next = mock(State.class);
        when(mgr.goQuery()).thenReturn(next);

        assertEquals(next, state.process(new Query()));

        Identification ident = captureAdminMessage(Identification.class);
        assertEquals(MY_HOST, ident.getSource());
        assertEquals(ASGN3, ident.getAssignments());
    }

    @Test
    public void testProcessingState() {
        /*
         * Null assignments should be OK.
         */
        when(mgr.getAssignments()).thenReturn(null);
        state = new ProcessingState(mgr, LEADER);

        /*
         * Empty assignments should be OK.
         */
        when(mgr.getAssignments()).thenReturn(EMPTY_ASGN);
        state = new ProcessingState(mgr, LEADER);
        assertEquals(MY_HOST, state.getHost());
        assertEquals(LEADER, state.getLeader());
        assertEquals(EMPTY_ASGN, state.getAssignments());

        /*
         * Now try something with assignments.
         */
        when(mgr.getAssignments()).thenReturn(ASGN3);
        state = new ProcessingState(mgr, LEADER);

        /*
         * Prove the state is attached to the manager by invoking getHost(), which
         * delegates to the manager.
         */
        assertEquals(MY_HOST, state.getHost());

        assertEquals(LEADER, state.getLeader());
        assertEquals(ASGN3, state.getAssignments());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testProcessingState_NullLeader() {
        when(mgr.getAssignments()).thenReturn(EMPTY_ASGN);
        state = new ProcessingState(mgr, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testProcessingState_ZeroLengthHostArray() {
        when(mgr.getAssignments()).thenReturn(new BucketAssignments(new String[] {}));
        state = new ProcessingState(mgr, LEADER);
    }

    @Test
    public void testGetAssignments() {
        // assignments from constructor
        assertEquals(ASGN3, state.getAssignments());

        // null assignments - no effect
        state.setAssignments(null);
        assertEquals(ASGN3, state.getAssignments());

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
        when(mgr.goActive()).thenReturn(next);

        assertEquals(next, state.becomeLeader(sortHosts(MY_HOST, HOST2)));

        Leader msg = captureAdminMessage(Leader.class);

        verify(mgr).startDistributing(msg.getAssignments());
        verify(mgr).goActive();
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
        when(mgr.getAssignments()).thenReturn(null);
        state = new ProcessingState(mgr, MY_HOST);
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
         * All hosts are still alive, so it should have the exact same assignments as it
         * had to start.
         */
        state.setAssignments(ASGN3);
        state.becomeLeader(sortHosts(HOST_ARR3));

        String[] arr = captureHostArray();

        assertNotSame(arr, HOST_ARR3);
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

    @Test
    public void testHostBucketRemove_testHostBucketAdd_testHostBucketSize() {
        assertEquals(0, hostBucket.size());

        hostBucket.add(20);
        hostBucket.add(30);
        hostBucket.add(40);
        assertEquals(3, hostBucket.size());

        assertEquals(20, hostBucket.remove().intValue());
        assertEquals(30, hostBucket.remove().intValue());
        assertEquals(1, hostBucket.size());

        // add more before taking the last item
        hostBucket.add(50);
        hostBucket.add(60);
        assertEquals(3, hostBucket.size());

        assertEquals(40, hostBucket.remove().intValue());
        assertEquals(50, hostBucket.remove().intValue());
        assertEquals(60, hostBucket.remove().intValue());
        assertEquals(0, hostBucket.size());

        // add more AFTER taking the last item
        hostBucket.add(70);
        assertEquals(70, hostBucket.remove().intValue());
        assertEquals(0, hostBucket.size());
    }

    @Test
    public void testHostBucketCompareTo() {
        HostBucket hb1 = new HostBucket(PREV_HOST);
        HostBucket hb2 = new HostBucket(MY_HOST);

        assertEquals(0, hb1.compareTo(hb1));
        assertEquals(0, hb1.compareTo(new HostBucket(PREV_HOST)));

        // both empty
        assertTrue(hb1.compareTo(hb2) < 0);
        assertTrue(hb2.compareTo(hb1) > 0);

        // hb1 has one bucket, so it should not be larger
        hb1.add(100);
        assertTrue(hb1.compareTo(hb2) > 0);
        assertTrue(hb2.compareTo(hb1) < 0);

        // hb1 has two buckets, so it should still be larger
        hb1.add(200);
        assertTrue(hb1.compareTo(hb2) > 0);
        assertTrue(hb2.compareTo(hb1) < 0);

        // hb1 has two buckets, hb2 has one, so hb1 should still be larger
        hb2.add(1000);
        assertTrue(hb1.compareTo(hb2) > 0);
        assertTrue(hb2.compareTo(hb1) < 0);

        // same number of buckets, so hb2 should now be larger
        hb2.add(2000);
        assertTrue(hb1.compareTo(hb2) < 0);
        assertTrue(hb2.compareTo(hb1) > 0);

        // hb2 has more buckets, it should still be larger
        hb2.add(3000);
        assertTrue(hb1.compareTo(hb2) < 0);
        assertTrue(hb2.compareTo(hb1) > 0);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testHostBucketHashCode() {
        hostBucket.hashCode();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testHostBucketEquals() {
        hostBucket.equals(hostBucket);
    }
}
