/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018, 2020 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2020, 2024 Nordix Foundation
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.onap.policy.drools.pooling.message.Heartbeat;
import org.onap.policy.drools.pooling.message.Identification;
import org.onap.policy.drools.pooling.message.Leader;
import org.onap.policy.drools.pooling.message.Offline;
import org.onap.policy.drools.pooling.message.Query;

class StartStateTest extends SupportBasicStateTester {

    private StartState state;

    /**
     * Setup.
     */
    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        state = new StartState(mgr);
    }

    @Test
    void testStart() {
        state.start();

        Pair<String, Heartbeat> msg = capturePublishedMessage(Heartbeat.class);

        assertEquals(MY_HOST, msg.getLeft());
        assertEquals(state.getHbTimestampMs(), msg.getRight().getTimestampMs());


        /*
         * Verify heartbeat generator
         */
        Triple<Long, Long, StateTimerTask> generator = repeatedTasks.removeFirst();

        assertEquals(STD_INTER_HEARTBEAT_MS, generator.getLeft().longValue());
        assertEquals(STD_INTER_HEARTBEAT_MS, generator.getMiddle().longValue());

        // invoke the task - it should generate another heartbeat
        assertNull(generator.getRight().fire());
        verify(mgr, times(2)).publish(MY_HOST, msg.getRight());

        // and again
        assertNull(generator.getRight().fire());
        verify(mgr, times(3)).publish(MY_HOST, msg.getRight());


        /*
         * Verify heartbeat checker
         */
        Pair<Long, StateTimerTask> checker = onceTasks.removeFirst();

        assertEquals(STD_HEARTBEAT_WAIT_MS, checker.getLeft().longValue());

        // invoke the task - it should go to the state returned by the mgr
        State next = mock(State.class);
        when(mgr.goInactive()).thenReturn(next);

        assertEquals(next, checker.getRight().fire());

        verify(mgr).startDistributing(null);
    }

    @Test
    void testStartStatePoolingManager() {
        /*
         * Prove the state is attached to the manager by invoking getHost(), which
         * delegates to the manager.
         */
        assertEquals(MY_HOST, state.getHost());
    }

    @Test
    void testStartStateState() {
        // create a new state from the current state
        state = new StartState(mgr);

        /*
         * Prove the state is attached to the manager by invoking getHost(), which
         * delegates to the manager.
         */
        assertEquals(MY_HOST, state.getHost());
    }

    @Test
    void testProcessHeartbeat() {
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
        when(mgr.goQuery()).thenReturn(next);

        assertEquals(next, state.process(msg));

        verify(mgr).publishAdmin(any(Query.class));
    }

    @Test
    void testProcessIdentification() {
        assertNull(state.process(new Identification(MY_HOST, null)));
    }

    @Test
    void testProcessLeader() {
        assertNull(state.process(new Leader(MY_HOST, null)));
    }

    @Test
    void testProcessOffline() {
        assertNull(state.process(new Offline(HOST1)));
    }

    @Test
    void testProcessQuery() {
        assertNull(state.process(new Query()));
    }

    @Test
    void testGetHbTimestampMs() {
        long tcurrent = System.currentTimeMillis();
        assertTrue(new StartState(mgr).getHbTimestampMs() >= tcurrent);

        tcurrent = System.currentTimeMillis();
        assertTrue(new StartState(mgr).getHbTimestampMs() >= tcurrent);
    }

}
