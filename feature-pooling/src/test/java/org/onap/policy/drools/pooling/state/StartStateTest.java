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
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.drools.pooling.message.Forward;
import org.onap.policy.drools.pooling.message.Heartbeat;
import org.onap.policy.drools.pooling.message.Identification;
import org.onap.policy.drools.pooling.message.Leader;
import org.onap.policy.drools.pooling.message.Message;
import org.onap.policy.drools.pooling.message.Offline;
import org.onap.policy.drools.pooling.message.Query;

public class StartStateTest extends BasicStateTester {

    private StartState state;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        state = new StartState(mgr);
    }

    @Test
    public void testGetFilter() {
        Map<String, Object> filter = state.getFilter();

        FilterUtilsTest utils = new FilterUtilsTest();

        utils.checkArray(FilterUtils.CLASS_OR, 2, filter);
        utils.checkEquals(FilterUtils.MSG_CHANNEL, Message.ADMIN, utils.getItem(filter, 0));
        
        // get the sub-filter
        filter = utils.getItem(filter, 1);

        utils.checkArray(FilterUtils.CLASS_AND, 2, filter);
        utils.checkEquals(FilterUtils.MSG_CHANNEL, MY_HOST, utils.getItem(filter, 0));
        utils.checkEquals(FilterUtils.MSG_TIMESTAMP, String.valueOf(state.getHbTimestampMs()),
                        utils.getItem(filter, 1));
    }

    @Test
    public void testStart() {
        state.start();

        Pair<String, Heartbeat> msg = capturePublishedMessage(Heartbeat.class);

        assertEquals(MY_HOST, msg.first);
        assertEquals(state.getHbTimestampMs(), msg.second.getTimestampMs());

        Pair<Long, StateTimerTask> timer = onceTasks.removeFirst();

        assertEquals(STD_HEARTBEAT_WAIT_MS, timer.first.longValue());

        // invoke the task - it should go to the state returned by the mgr
        State next = mock(State.class);
        when(mgr.goInactive(state)).thenReturn(next);

        assertEquals(next, timer.second.fire(null));

        verify(mgr).internalTopicFailed();
    }

    @Test
    public void testStartStatePoolingManager() {
        /*
         * Prove the state is attached to the manager by invoking getHost(),
         * which delegates to the manager.
         */
        assertEquals(MY_HOST, state.getHost());
    }

    @Test
    public void testStartStateState() {
        // create a new state from the current state
        state = new StartState(state);

        /*
         * Prove the state is attached to the manager by invoking getHost(),
         * which delegates to the manager.
         */
        assertEquals(MY_HOST, state.getHost());
    }

    @Test
    public void testProcessForward() {
        assertNull(state.process(new Forward()));
    }

    @Test
    public void testProcessHeartbeat() {
        Heartbeat msg = new Heartbeat();

        // no matching data in heart beat
        assertNull(state.process(msg));
        verify(mgr, never()).publishAdmin(any());

        // same source, different time stamp
        msg.setSource(MY_HOST);
        msg.setTimestampMs(state.getHbTimestampMs() - 1);
        assertNull(state.process(msg));
        verify(mgr, never()).publishAdmin(any());

        // same time stamp, different source
        msg.setSource("unknown");
        msg.setTimestampMs(state.getHbTimestampMs());
        assertNull(state.process(msg));
        verify(mgr, never()).publishAdmin(any());

        // matching heart beat
        msg.setSource(MY_HOST);
        msg.setTimestampMs(state.getHbTimestampMs());

        State next = mock(State.class);
        when(mgr.goQuery(state, null)).thenReturn(next);

        assertEquals(next, state.process(msg));

        verify(mgr).publishAdmin(any(Query.class));
    }

    @Test
    public void testProcessIdentification() {
        assertNull(state.process(new Identification(MY_HOST, null)));
    }

    @Test
    public void testProcessLeader() {
        assertNull(state.process(new Leader(MY_HOST, null)));
    }

    @Test
    public void testProcessOffline() {
        assertNull(state.process(new Offline(HOST1)));
    }

    @Test
    public void testProcessQuery() {
        assertNull(state.process(new Query()));
    }

    @Test
    public void testGetHbTimestampMs() {
        long tcurrent = System.currentTimeMillis();
        assertTrue(new StartState(mgr).getHbTimestampMs() >= tcurrent);

        tcurrent = System.currentTimeMillis();
        assertTrue(new StartState(state).getHbTimestampMs() >= tcurrent);
    }

}
