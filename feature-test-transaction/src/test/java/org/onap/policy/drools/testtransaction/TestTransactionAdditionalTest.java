/*-
 * ============LICENSE_START=======================================================
 * feature-test-transaction
 * ================================================================================
 * Copyright (C) 2017-2020 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.testtransaction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.EventObject;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.core.PolicyContainer;
import org.onap.policy.drools.system.PolicyController;

public class TestTransactionAdditionalTest {

    private static final int MAX_SLEEP_COUNT = 3;
    private static final String EXPECTED = "expected exception";
    private static final String CONTROLLER1 = "controller-a";
    private static final String CONTROLLER2 = "controller-b";
    private static final String CONTROLLER3 = "controller-c";
    private static final String SESSION1 = "session-a";
    private static final String SESSION2 = "session-b";
    private static final List<String> sessions = Arrays.asList(SESSION1, SESSION2);
    private static final List<Object> facts = Arrays.asList(0L);

    private Thread theThread;
    private PolicyController controller;
    private PolicyController controller2;
    private PolicyController controller3;
    private Runnable theAction;
    private long waitJoinMs;
    private long doSleepMs;
    private DroolsController drools;
    private PolicyContainer container;
    private Map<String, TtControllerTask> name2task;
    private TtControllerTask task;
    private TtControllerTask task2;
    private TtControllerTask task3;
    private TestTransTImplTester impl;

    /**
     * Initialize objects for each test.
     */
    @Before
    public void setUp() {
        theThread = mock(Thread.class);
        controller = mock(PolicyController.class);
        controller2 = mock(PolicyController.class);
        controller3 = mock(PolicyController.class);
        theAction = null;
        waitJoinMs = -1;
        doSleepMs = -1;
        drools = mock(DroolsController.class);
        container = mock(PolicyContainer.class);
        task2 = mock(TtControllerTask.class);
        task3 = mock(TtControllerTask.class);
        name2task = new TreeMap<>();

        when(drools.getSessionNames()).thenReturn(sessions);
        when(drools.isBrained()).thenReturn(true);
        when(drools.factQuery(anyString(), anyString(), anyString(), anyBoolean())).thenReturn(facts);
        when(drools.getContainer()).thenReturn(container);

        when(controller.getName()).thenReturn(CONTROLLER1);
        when(controller.getDrools()).thenReturn(drools);
        when(controller.isAlive()).thenReturn(true);

        when(controller2.getName()).thenReturn(CONTROLLER2);
        when(controller2.getDrools()).thenReturn(drools);
        when(controller2.isAlive()).thenReturn(true);

        when(controller3.getName()).thenReturn(CONTROLLER3);
        when(controller3.getDrools()).thenReturn(drools);
        when(controller3.isAlive()).thenReturn(true);

        task = new TestTransControllerTaskTester(controller);

        name2task.put(CONTROLLER1, task);
        name2task.put(CONTROLLER2, task2);
        name2task.put(CONTROLLER3, task3);

        impl = new TestTransTImplTester();
    }

    @Test
    public void testTestTransactionImpl() {
        assertNotNull(TestTransactionConstants.getManager());
    }

    @Test
    public void testTestTransactionImplRegister_testTestTransactionImplUnregister() {
        task = mock(TtControllerTask.class);
        when(task.isAlive()).thenReturn(true);
        name2task.put(CONTROLLER1, task);

        impl.register(controller);
        impl.register(controller2);

        // re-register
        impl.register(controller);

        // re-register when task is not running

        // give controller3 same name as controller1 -> task3 replaces task
        when(controller3.getName()).thenReturn(CONTROLLER1);
        name2task.put(CONTROLLER1, task3);
        when(task.isAlive()).thenReturn(false);
        impl.register(controller3);

        impl.unregister(controller);
        verify(task, never()).stop();
        verify(task2, never()).stop();
        verify(task3).stop();

        impl.unregister(controller2);
        verify(task2).stop();

        // unregister again - stop() should not be called again
        impl.unregister(controller3);
        verify(task3).stop();

        // unregister original controller - no stop() should be called again
        impl.unregister(controller);
        verify(task, never()).stop();
        verify(task2).stop();
        verify(task3).stop();
    }

    @Test
    public void testTestTransactionControllerTaskFactory() throws Exception {
        task = new TtControllerTask(controller) {
            @Override
            protected Thread makeThread(Runnable action) {
                return theThread;
            }

            @Override
            protected void joinThread(long waitTimeMs) throws InterruptedException {
                // do nothing
            }
        };

        task.doSleep(1);
        assertEquals(Thread.currentThread(), task.getCurrentThread());
    }

    @Test
    public void testTestTransactionControllerTask() {
        assertEquals(task, theAction);
        assertTrue(task.isAlive());
        assertEquals(controller, task.getController());
        assertEquals(theThread, task.getThread());

        verify(theThread).start();
    }

    @Test
    public void testTestTransactionControllerTaskGetController() {
        assertEquals(controller, task.getController());
    }

    @Test
    public void testTestTransactionControllerTaskGetThread() {
        assertEquals(theThread, task.getThread());
    }

    @Test
    public void testTestTransactionControllerTaskStop() throws Exception {
        task.stop();
        assertFalse(task.isAlive());
        verify(theThread).interrupt();
        assertTrue(waitJoinMs > 0);

        // throw interrupt during join()
        setUp();
        task = new TestTransControllerTaskTester(controller) {
            @Override
            protected void joinThread(long waitTimeMs) throws InterruptedException {
                waitJoinMs = waitTimeMs;
                throw new InterruptedException(EXPECTED);
            }
        };
        task.stop();
        assertFalse(task.isAlive());
        verify(theThread, times(2)).interrupt();
        assertTrue(waitJoinMs > 0);
    }

    @Test
    public void testTestTransactionControllerTaskRun() {
        task.run();
        assertFalse(task.isAlive());
        verify(theThread, never()).interrupt();
        verify(controller, times(MAX_SLEEP_COUNT + 1)).isAlive();
        assertTrue(doSleepMs > 0);

        // not brained
        setUp();
        when(drools.isBrained()).thenReturn(false);
        task.run();
        assertFalse(task.isAlive());
        verify(controller, never()).isAlive();
        assertEquals(-1, doSleepMs);

        // controller not running
        setUp();
        when(controller.isAlive()).thenReturn(false);
        task.run();
        assertFalse(task.isAlive());
        assertEquals(-1, doSleepMs);

        // controller is locked
        setUp();
        when(controller.isLocked()).thenReturn(true);
        task.run();
        assertFalse(task.isAlive());
        assertEquals(-1, doSleepMs);

        // un-brain during sleep
        setUp();
        task = new TestTransControllerTaskTester(controller) {
            @Override
            protected void doSleep(long sleepMs) throws InterruptedException {
                when(drools.isBrained()).thenReturn(false);
                super.doSleep(sleepMs);
            }
        };
        task.run();
        assertFalse(task.isAlive());
        // only hit top of the loop twice
        verify(controller, times(2)).isAlive();
        assertTrue(doSleepMs > 0);

        // stop during sleep
        setUp();
        task = new TestTransControllerTaskTester(controller) {
            @Override
            protected void doSleep(long sleepMs) throws InterruptedException {
                task.stop();
                super.doSleep(sleepMs);
            }
        };
        task.run();
        assertFalse(task.isAlive());
        // only hit top of the loop twice
        verify(controller, times(2)).isAlive();
        assertTrue(doSleepMs > 0);

        // isInterrupted() returns true the first time, interrupt next time
        setUp();
        AtomicInteger count = new AtomicInteger(1);
        when(theThread.isInterrupted()).thenAnswer(args -> {
            if (count.decrementAndGet() >= 0) {
                return true;
            } else {
                throw new InterruptedException(EXPECTED);
            }
        });
        task.run();
        assertFalse(task.isAlive());
        verify(controller, times(2)).isAlive();
        // doSleep() should not be called
        assertEquals(-1, doSleepMs);

        // interrupt during sleep
        setUp();
        task = new TestTransControllerTaskTester(controller) {
            @Override
            protected void doSleep(long sleepMs) throws InterruptedException {
                super.doSleep(sleepMs);
                throw new InterruptedException(EXPECTED);
            }
        };
        task.run();
        assertFalse(task.isAlive());
        verify(theThread).interrupt();
        // only hit top of the loop once
        verify(controller).isAlive();
        assertTrue(doSleepMs > 0);

        // stop() during factQuery()
        setUp();
        when(drools.factQuery(anyString(), anyString(), anyString(), anyBoolean())).thenAnswer(args -> {
            task.stop();
            return facts;
        });
        task.run();
        assertFalse(task.isAlive());
        // only hit top of the loop once
        verify(controller).isAlive();

        // exception during isBrained() check
        setUp();
        when(drools.isBrained()).thenThrow(new IllegalArgumentException(EXPECTED));
        task.run();
        assertFalse(task.isAlive());

        // other exception during isBrained() check
        setUp();
        when(drools.isBrained()).thenThrow(new RuntimeException(EXPECTED));
        task.run();
        assertFalse(task.isAlive());
    }

    @Test
    public void testTestTransactionControllerTaskInjectTxIntoSessions() {
        task.run();
        verify(container, times(MAX_SLEEP_COUNT * sessions.size())).insert(anyString(), any(EventObject.class));

        // null facts
        setUp();
        when(drools.factQuery(anyString(), anyString(), anyString(), anyBoolean())).thenReturn(null);
        task.run();
        verify(container, never()).insert(anyString(), any());

        // empty fact list
        setUp();
        when(drools.factQuery(anyString(), anyString(), anyString(), anyBoolean())).thenReturn(Collections.emptyList());
        task.run();
        verify(container, never()).insert(anyString(), any());
    }

    @Test
    public void testTestTransactionControllerTaskToString() {
        assertTrue(task.toString().startsWith("TTControllerTask ["));
    }

    /**
     * TestTransaction with overridden methods.
     */
    private class TestTransTImplTester extends TtImpl {

        @Override
        protected TtControllerTask makeControllerTask(PolicyController controller) {
            return name2task.get(controller.getName());
        }
    }

    /**
     * Controller task with overridden methods.
     */
    private class TestTransControllerTaskTester extends TtControllerTask {
        private int sleepCount = MAX_SLEEP_COUNT;

        public TestTransControllerTaskTester(PolicyController controller) {
            super(controller);
        }

        @Override
        protected Thread makeThread(Runnable action) {
            theAction = action;
            return theThread;
        }

        @Override
        protected void joinThread(long waitTimeMs) throws InterruptedException {
            waitJoinMs = waitTimeMs;
        }

        @Override
        protected void doSleep(long sleepMs) throws InterruptedException {
            doSleepMs = sleepMs;

            if (--sleepCount <= 0) {
                when(controller.isAlive()).thenReturn(false);
            }
        }

        @Override
        protected Thread getCurrentThread() {
            return thread;
        }
    }
}
