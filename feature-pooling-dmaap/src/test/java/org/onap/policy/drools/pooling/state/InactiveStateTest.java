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
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.drools.pooling.message.BucketAssignments;
import org.onap.policy.drools.pooling.message.Identification;
import org.onap.policy.drools.pooling.message.Leader;
import org.onap.policy.drools.pooling.message.Message;
import org.onap.policy.drools.pooling.message.Query;

public class InactiveStateTest extends SupportBasicStateTester {

    private InactiveState state;

    /**
     * Setup.
     *
     */
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        state = new InactiveState(mgr);
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
    public void testProcessLeader() {
        State next = mock(State.class);
        when(mgr.goActive()).thenReturn(next);

        String[] arr = {PREV_HOST, MY_HOST, HOST1};
        BucketAssignments asgn = new BucketAssignments(arr);
        Leader msg = new Leader(PREV_HOST, asgn);

        assertEquals(next, state.process(msg));
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
    public void testProcessQuery() {
        State next = mock(State.class);
        when(mgr.goQuery()).thenReturn(next);

        assertEquals(next, state.process(new Query()));

        Identification ident = captureAdminMessage(Identification.class);
        assertEquals(MY_HOST, ident.getSource());
        assertEquals(ASGN3, ident.getAssignments());
    }

    @Test
    public void testGoInatcive() {
        assertNull(state.goInactive());
    }

    @Test
    public void testStart() {
        state.start();

        Pair<Long, StateTimerTask> timer = onceTasks.remove();

        assertEquals(STD_REACTIVATE_WAIT_MS, timer.getLeft().longValue());

        // invoke the task - it should go to the state returned by the mgr
        State next = mock(State.class);
        when(mgr.goStart()).thenReturn(next);

        assertEquals(next, timer.getRight().fire());
    }

    @Test
    public void testInactiveState() {
        /*
         * Prove the state is attached to the manager by invoking getHost(), which
         * delegates to the manager.
         */
        assertEquals(MY_HOST, state.getHost());
    }

}
