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
import static org.mockito.Mockito.when;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.drools.pooling.message.Message;

public class InactiveStateTest extends BasicStateTester {
    
    private InactiveState state;

    @Before
    public void setUp() throws Exception {
        super.setUp();

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

        Pair<Long,StateTimerTask> timer = captureSchedule();

        assertEquals(InactiveState.REACTIVATE_MS, timer.first.longValue());

        // invoke the task - it should go to the state returned by the factory
        State next = mock(State.class);
        when(factory.goStart(state)).thenReturn(next);

        assertEquals(next, timer.second.fire(null));
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
