/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2019 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.lifecycle;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.onap.policy.drools.persistence.SystemPersistence;
import org.onap.policy.models.pdp.enums.PdpState;

/**
 * Lifecycle State Terminated Tests.
 */
public class LifecycleStateTerminatedTest {
    private LifecycleFsm fsm = new LifecycleFsm();

    @BeforeClass
    public static void setUp() {
        SystemPersistence.manager.setConfigurationDir("src/test/resources");
    }

    @AfterClass
    public static void tearDown() {
        SystemPersistence.manager.setConfigurationDir(null);
    }

    @Test
    public void constructor() {
        assertThatIllegalArgumentException().isThrownBy(() -> new LifecycleStateTerminated(null));

        LifecycleState state = new LifecycleStateTerminated(new LifecycleFsm());
        assertNull(state.fsm.source);
        assertNull(state.fsm.client);
        assertNull(state.fsm.statusTask);

        assertEquals(PdpState.TERMINATED, state.state());
        assertEquals(PdpState.TERMINATED, state.fsm.state.state());
        assertFalse(state.isAlive());
    }

    @Test
    public void start_stop() {
        assertBasicTerminated();
        simpleStart();
        simpleStop();

        assertFalse(fsm.source.isAlive());
        assertFalse(fsm.client.getSink().isAlive());

        assertExtendedTerminated();
    }

    private void simpleStart() {
        assertTrue(fsm.start());
        assertBasicPassive();
    }

    private void simpleStop() {
        assertTrue(fsm.stop());
        assertBasicTerminated();
    }

    @Test
    public void stop() {
        assertEquals(PdpState.TERMINATED, fsm.state.state());
        assertFalse(fsm.isAlive());

        simpleStop();
    }

    @Test
    public void bounce() {
        start_stop();
        start_stop();
    }

    @Test
    public void shutdown() {
        assertBasicTerminated();
        fsm.shutdown();
        assertBasicTerminated();

        fsm = new LifecycleFsm();
    }

    @Test
    public void status() {
        assertBasicTerminated();
        assertFalse(fsm.status());
        assertBasicTerminated();
    }

    @Test
    public void changeState() {
        assertFalse(fsm.state.changeState(new LifecycleStateTerminated(fsm)));
        assertEquals(PdpState.TERMINATED, fsm.state.state());
    }

    @Test
    public void update() {
        // TODO
    }

    @Test
    public void stateChange() {
        // TODO
    }

    private void assertBasicTerminated() {
        assertEquals(PdpState.TERMINATED, fsm.state.state());
        assertFalse(fsm.isAlive());
        assertFalse(fsm.state.isAlive());
    }

    private void assertExtendedTerminated() {
        assertBasicTerminated();
        assertTrue(fsm.statusTask.isCancelled());
        assertTrue(fsm.statusTask.isDone());
        assertFalse(fsm.scheduler.isShutdown());
    }

    private void assertBasicPassive() {
        assertEquals(PdpState.PASSIVE, fsm.state.state());
        assertNotNull(fsm.source);
        assertNotNull(fsm.client);
        assertNotNull(fsm.statusTask);

        assertTrue(fsm.isAlive());
        assertTrue(fsm.source.isAlive());
        assertTrue(fsm.client.getSink().isAlive());

        assertFalse(fsm.statusTask.isCancelled());
        assertFalse(fsm.statusTask.isDone());
    }
}