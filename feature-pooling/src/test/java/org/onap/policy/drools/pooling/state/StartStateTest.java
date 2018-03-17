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
import static org.mockito.Matchers.anyLong;
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
import org.onap.policy.drools.pooling.message.Heartbeat;
import org.onap.policy.drools.pooling.message.Query;
import org.onap.policy.drools.pooling.state.State.StateFactory;

public class StartStateTest {
    private static final String MY_HOST = "myHost";
    private static final String MY_TOPIC = "myTopic";

    private static StateFactory saveFactory;

    private StateFactory factory;
    private PoolingManager mgr;
    private StartState state;

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

        state = new StartState(mgr);
    }

    @Test
    public void testGetFilter() {
        Map<String, Object> filter = state.getFilter();

        FilterUtilsTest utils = new FilterUtilsTest();

        utils.checkArray(FilterUtils.CLASS_AND, 2, filter);
        utils.checkEquals(FilterUtils.MSG_TIMESTAMP, String.valueOf(state.getHbTimestampMs()),
                        utils.getItem(filter, 0));
        utils.checkEquals(FilterUtils.MSG_CHANNEL, MY_HOST, utils.getItem(filter, 1));
    }

    @Test
    public void testStart() {
        ScheduledFuture<?> fut = mock(ScheduledFuture.class);

        when(mgr.schedule(anyLong(), any(StateTimerTask.class))).thenAnswer(xxx -> fut);

        state.start();

        // capture the publishing parameters
        ArgumentCaptor<String> sourceCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Heartbeat> msgCap = ArgumentCaptor.forClass(Heartbeat.class);

        verify(mgr).publish(sourceCap.capture(), msgCap.capture());

        assertEquals(MY_HOST, sourceCap.getValue());
        assertEquals(state.getHbTimestampMs(), msgCap.getValue().getTimestampMs());

        // capture the scheduling parameters
        ArgumentCaptor<Long> delayCap = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<StateTimerTask> taskCap = ArgumentCaptor.forClass(StateTimerTask.class);

        verify(mgr).schedule(delayCap.capture(), taskCap.capture());

        assertEquals(StartState.HEARTBEAT_MS, delayCap.getValue().longValue());

        // invoke the task - it should go to the state returned by the factory
        State next = mock(State.class);
        when(factory.goInactive(state)).thenReturn(next);

        assertEquals(next, taskCap.getValue().fire(null));

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
        when(factory.goQuery(state, MY_HOST, null)).thenReturn(next);

        assertEquals(next, state.process(msg));

        verify(mgr).publishAdmin(any(Query.class));
    }

    @Test
    public void testGetHbTimestampMs() {
        long tcurrent = System.currentTimeMillis();
        assertTrue(new StartState(mgr).getHbTimestampMs() >= tcurrent);

        tcurrent = System.currentTimeMillis();
        assertTrue(new StartState(state).getHbTimestampMs() >= tcurrent);
    }

}
