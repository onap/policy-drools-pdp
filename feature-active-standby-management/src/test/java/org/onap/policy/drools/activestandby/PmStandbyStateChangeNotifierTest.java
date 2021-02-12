/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2019-2021 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.activestandby;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.onap.policy.common.im.StateManagement;
import org.onap.policy.drools.system.PolicyEngine;

@RunWith(MockitoJUnitRunner.class)
public class PmStandbyStateChangeNotifierTest {
    private static final String UNSUPPORTED_STATUS = "unsupported status";
    private static final String PDP_ID = "my-pdp";
    private static final long UPDATE_INTERVAL = 100;
    private static final long WAIT_INTERVAL = 2 * UPDATE_INTERVAL + 2000;

    private static Factory saveFactory;

    @Mock
    private Factory factory;

    @Mock
    private PolicyEngine engmgr;

    @Mock
    private Timer timer;

    @Mock
    private StateManagement mgmt;

    private PmStandbyStateChangeNotifier notifier;

    /**
     * Initializes the properties.
     */
    @BeforeClass
    public static void setUpBeforeClass() {
        Properties props = new Properties();
        props.setProperty(ActiveStandbyProperties.NODE_NAME, PDP_ID);
        props.setProperty(ActiveStandbyProperties.PDP_UPDATE_INTERVAL, String.valueOf(UPDATE_INTERVAL));

        ActiveStandbyProperties.initProperties(props);

        saveFactory = Factory.getInstance();
    }

    @AfterClass
    public static void tearDownAfterClass() {
        Factory.setInstance(saveFactory);
    }

    /**
     * Initializes objects, including the notifier.
     */
    @Before
    public void setUp() {
        Factory.setInstance(factory);
        when(factory.makeTimer()).thenReturn(timer);

        notifier = new MyNotifier();
    }

    @Test
    public void testHandleStateChange_Null() {
        notifier.update(mgmt, null);
        verify(engmgr).deactivate();
        assertEquals(StateManagement.NULL_VALUE, notifier.getPreviousStandbyStatus());

        // repeat - nothing else should be done
        when(mgmt.getStandbyStatus()).thenReturn(StateManagement.NULL_VALUE);
        notifier.update(mgmt, null);
        verify(engmgr, times(1)).deactivate();
        assertEquals(StateManagement.NULL_VALUE, notifier.getPreviousStandbyStatus());
    }

    @Test
    public void testHandleStateChange_Null_Ex() {
        doThrow(new MyException()).when(engmgr).deactivate();

        // should not throw an exception
        notifier.update(mgmt, null);
        assertEquals(PmStandbyStateChangeNotifier.NONE, notifier.getPreviousStandbyStatus());
    }

    @Test
    public void testHandleStateChange_HotOrCold() {
        when(mgmt.getStandbyStatus()).thenReturn(StateManagement.HOT_STANDBY);
        notifier.update(mgmt, null);
        verify(engmgr).deactivate();
        assertEquals(PmStandbyStateChangeNotifier.HOTSTANDBY_OR_COLDSTANDBY, notifier.getPreviousStandbyStatus());

        // repeat - nothing else should be done
        when(mgmt.getStandbyStatus()).thenReturn(StateManagement.COLD_STANDBY);
        notifier.update(mgmt, null);
        verify(engmgr, times(1)).deactivate();
        assertEquals(PmStandbyStateChangeNotifier.HOTSTANDBY_OR_COLDSTANDBY, notifier.getPreviousStandbyStatus());
    }

    @Test
    public void testHandleStateChange_HotOrCold_Ex() {
        doThrow(new MyException()).when(engmgr).deactivate();

        // should not throw an exception
        when(mgmt.getStandbyStatus()).thenReturn(StateManagement.HOT_STANDBY);
        notifier.update(mgmt, null);
        assertEquals(PmStandbyStateChangeNotifier.NONE, notifier.getPreviousStandbyStatus());
    }

    @Test
    public void testHandleStateChange_ProvidingService() {
        when(mgmt.getStandbyStatus()).thenReturn(StateManagement.PROVIDING_SERVICE);
        notifier.update(mgmt, null);
        verify(engmgr, never()).activate();
        assertEquals(PmStandbyStateChangeNotifier.NONE, notifier.getPreviousStandbyStatus());

        ArgumentCaptor<TimerTask> captor = ArgumentCaptor.forClass(TimerTask.class);
        verify(timer).schedule(captor.capture(), eq(WAIT_INTERVAL));

        // execute the timer task
        captor.getValue().run();

        verify(engmgr).activate();
        assertEquals(StateManagement.PROVIDING_SERVICE, notifier.getPreviousStandbyStatus());

        // repeat - nothing else should be done
        notifier.update(mgmt, null);
        verify(engmgr, never()).deactivate();
        verify(engmgr, times(1)).activate();
        verify(timer, times(1)).schedule(captor.capture(), eq(WAIT_INTERVAL));
        assertEquals(StateManagement.PROVIDING_SERVICE, notifier.getPreviousStandbyStatus());
    }

    @Test
    public void testHandleStateChange_ProvidingService_BeforeActivation() {
        when(mgmt.getStandbyStatus()).thenReturn(StateManagement.PROVIDING_SERVICE);
        notifier.update(mgmt, null);

        // repeat - nothing else should be done
        notifier.update(mgmt, null);
        verify(engmgr, never()).deactivate();
        verify(engmgr, never()).activate();

        verify(timer, times(1)).schedule(any(), eq(WAIT_INTERVAL));
        assertEquals(PmStandbyStateChangeNotifier.NONE, notifier.getPreviousStandbyStatus());
    }

    @Test
    public void testHandleStateChange_ProvidingService_Ex() {
        when(factory.makeTimer()).thenThrow(new MyException());

        when(mgmt.getStandbyStatus()).thenReturn(StateManagement.PROVIDING_SERVICE);
        notifier.update(mgmt, null);

        assertEquals(PmStandbyStateChangeNotifier.NONE, notifier.getPreviousStandbyStatus());
    }

    @Test
    public void testHandleStateChange_Unsupported() {
        when(mgmt.getStandbyStatus()).thenReturn(UNSUPPORTED_STATUS);
        notifier.update(mgmt, null);

        verify(engmgr).deactivate();
        assertEquals(PmStandbyStateChangeNotifier.UNSUPPORTED, notifier.getPreviousStandbyStatus());

        // repeat - nothing else should be done
        notifier.update(mgmt, null);
        verify(engmgr, times(1)).deactivate();
        assertEquals(PmStandbyStateChangeNotifier.UNSUPPORTED, notifier.getPreviousStandbyStatus());
    }

    @Test
    public void testHandleStateChange_Unsupported_Ex() {
        doThrow(new MyException()).when(engmgr).deactivate();

        // should not throw an exception
        when(mgmt.getStandbyStatus()).thenReturn(UNSUPPORTED_STATUS);
        notifier.update(mgmt, null);
        assertEquals(PmStandbyStateChangeNotifier.NONE, notifier.getPreviousStandbyStatus());
    }

    @Test
    public void testCancelTimer() {
        when(mgmt.getStandbyStatus()).thenReturn(StateManagement.PROVIDING_SERVICE);
        notifier.update(mgmt, null);

        when(mgmt.getStandbyStatus()).thenReturn(null);
        notifier.update(mgmt, null);

        verify(timer).cancel();
    }

    @Test
    public void testDelayActivateClass() {
        when(mgmt.getStandbyStatus()).thenReturn(StateManagement.PROVIDING_SERVICE);
        notifier.update(mgmt, null);
        verify(engmgr, never()).activate();
        assertEquals(PmStandbyStateChangeNotifier.NONE, notifier.getPreviousStandbyStatus());

        ArgumentCaptor<TimerTask> captor = ArgumentCaptor.forClass(TimerTask.class);
        verify(timer).schedule(captor.capture(), eq(WAIT_INTERVAL));

        // execute the timer task
        captor.getValue().run();

        verify(engmgr).activate();
        assertEquals(StateManagement.PROVIDING_SERVICE, notifier.getPreviousStandbyStatus());
    }

    @Test
    public void testDelayActivateClass_Ex() {
        when(mgmt.getStandbyStatus()).thenReturn(StateManagement.PROVIDING_SERVICE);
        notifier.update(mgmt, null);
        verify(engmgr, never()).activate();
        assertEquals(PmStandbyStateChangeNotifier.NONE, notifier.getPreviousStandbyStatus());

        ArgumentCaptor<TimerTask> captor = ArgumentCaptor.forClass(TimerTask.class);
        verify(timer).schedule(captor.capture(), eq(WAIT_INTERVAL));

        doThrow(new MyException()).when(engmgr).activate();

        // execute the timer task
        captor.getValue().run();

        assertEquals(PmStandbyStateChangeNotifier.NONE, notifier.getPreviousStandbyStatus());
    }

    @Test
    public void testGetPolicyEngineManager() {
        // use real object with real method - no exception expected
        assertThatCode(() -> new PmStandbyStateChangeNotifier().getPolicyEngineManager()).doesNotThrowAnyException();
    }

    private class MyNotifier extends PmStandbyStateChangeNotifier {
        @Override
        protected PolicyEngine getPolicyEngineManager() {
            return engmgr;
        }
    }

    private static class MyException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public MyException() {
            super("expected exception");
        }
    }
}
