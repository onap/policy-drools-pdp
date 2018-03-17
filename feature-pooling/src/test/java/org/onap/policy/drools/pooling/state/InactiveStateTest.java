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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.mock;
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
import org.onap.policy.drools.pooling.message.Message;
import org.onap.policy.drools.pooling.state.State.StateFactory;

public class InactiveStateTest {
    private static final String MY_HOST = "myHost";
    private static final String MY_TOPIC = "myTopic";

    private static StateFactory saveFactory;

    private StateFactory factory;
    private PoolingManager mgr;
    private InactiveState state;

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

        state = new InactiveState(new State(mgr) {
            @Override
            public Map<String, Object> getFilter() {
                throw new UnsupportedOperationException("cannot filter");
            }
        });
    }

    @Test
    public void testGetFilter() {
        new FilterUtilsTest().checkEquals(FilterUtils.MSG_CHANNEL, Message.UNKNOWN, state.getFilter());
    }

    @Test
    public void testStart() {
        ScheduledFuture<?> fut = mock(ScheduledFuture.class);

        when(mgr.schedule(anyLong(), any(StateTimerTask.class))).thenAnswer(xxx -> fut);

        state.start();

        // capture the scheduling parameters
        ArgumentCaptor<Long> delayCap = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<StateTimerTask> taskCap = ArgumentCaptor.forClass(StateTimerTask.class);

        verify(mgr).schedule(delayCap.capture(), taskCap.capture());

        assertEquals(InactiveState.REACTIVATE_MS, delayCap.getValue().longValue());

        // invoke the task - it should go to the state returned by the factory
        State next = mock(State.class);
        when(factory.goStart(state)).thenReturn(next);

        assertEquals(next, taskCap.getValue().fire(null));
    }

    @Test
    public void testInactiveState() {
        /*
         * Prove the state is attached to the manager by invoking getHost(),
         * which delegates to the manager.
         */
        assertEquals(MY_HOST, state.getHost());
    }

}
