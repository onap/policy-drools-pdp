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

import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.drools.pooling.message.BucketAssignments;
import org.onap.policy.drools.pooling.message.Forward;
import org.onap.policy.drools.pooling.message.Heartbeat;
import org.onap.policy.drools.pooling.message.Identification;
import org.onap.policy.drools.pooling.message.Leader;
import org.onap.policy.drools.pooling.message.Message;
import org.onap.policy.drools.pooling.message.Offline;
import org.onap.policy.drools.pooling.message.Query;

public class IdleStateTest extends BasicStateTester {

    private IdleState state;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        state = new IdleState(mgr);
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
    public void testStop() {
        state.stop();
        verifyNothingPublished();
    }

    @Test
    public void testProcessForward() {
        Forward msg = new Forward();
        assertNull(state.process(msg));

        verify(mgr).handle(msg);
    }

    @Test
    public void testProcessHeartbeat() {
        assertNull(state.process(new Heartbeat(PREV_HOST, 0L)));
        verifyNothingPublished();
    }

    @Test
    public void testProcessIdentification() {
        assertNull(state.process(new Identification(PREV_HOST, null)));
        verifyNothingPublished();
    }

    @Test
    public void testProcessLeader() {
        BucketAssignments asgn = new BucketAssignments(new String[] {HOST2, PREV_HOST, MY_HOST});
        Leader msg = new Leader(PREV_HOST, asgn);

        State next = mock(State.class);
        when(mgr.goActive(state, asgn)).thenReturn(next);

        // should stay in current state, but start distributing
        assertNull(state.process(msg));
        verify(mgr).startDistributing(asgn);
    }

    @Test
    public void testProcessOffline() {
        assertNull(state.process(new Offline(PREV_HOST)));
        verifyNothingPublished();
    }

    @Test
    public void testProcessQuery() {
        assertNull(state.process(new Query()));
        verifyNothingPublished();
    }

    /**
     * Verifies that nothing was published on either channel.
     */
    private void verifyNothingPublished() {
        verify(mgr, never()).publish(any(), any());
        verify(mgr, never()).publishAdmin(any());
    }
}
