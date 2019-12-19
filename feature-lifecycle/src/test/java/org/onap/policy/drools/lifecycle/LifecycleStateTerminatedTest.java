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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.onap.policy.common.utils.network.NetworkUtil;
import org.onap.policy.drools.persistence.SystemPersistenceConstants;
import org.onap.policy.drools.utils.logging.LoggerUtil;
import org.onap.policy.models.pdp.concepts.PdpStateChange;
import org.onap.policy.models.pdp.concepts.PdpUpdate;
import org.onap.policy.models.pdp.enums.PdpState;

/**
 * Lifecycle State Terminated Tests.
 */
public class LifecycleStateTerminatedTest {
    private LifecycleFsm fsm = new LifecycleFsm();

    @BeforeClass
    public static void setUp() {
        SystemPersistenceConstants.getManager().setConfigurationDir("src/test/resources");
        LoggerUtil.setLevel("org.onap.policy.common.endpoints", "WARN");
    }

    @AfterClass
    public static void tearDown() {
        SystemPersistenceConstants.getManager().setConfigurationDir(null);
    }

    @Test
    public void testConstructor() {
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
    public void testStop() {
        assertEquals(PdpState.TERMINATED, fsm.state.state());
        assertFalse(fsm.isAlive());

        simpleStop();
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
    public void testBounce() {
        assertBasicTerminated();
        simpleStart();
        simpleStop();

        assertFalse(fsm.source.isAlive());
        assertFalse(fsm.client.getSink().isAlive());
        assertExtendedTerminated();
    }

    @Test
    public void doubleBounce() {
        testBounce();
        testBounce();
    }

    @Test
    public void testDoubleStartBounce() {
        simpleStart();
        assertFalse(fsm.start());
        assertBasicPassive();
        simpleStop();
    }

    @Test
    public void testShutdown() {
        assertBasicTerminated();
        fsm.shutdown();
        assertBasicTerminated();

        fsm = new LifecycleFsm();
    }

    @Test
    public void testStatus() {
        assertBasicTerminated();
        assertFalse(fsm.status());
        assertBasicTerminated();
    }

    @Test
    public void changeState() {
        assertFalse(fsm.state.transitionToState(new LifecycleStateTerminated(fsm)));
        assertEquals(PdpState.TERMINATED, fsm.state.state());
    }

    @Test
    public void testUpdate() {
        PdpUpdate update = new PdpUpdate();
        update.setName(NetworkUtil.getHostname());
        update.setPdpGroup("A");
        update.setPdpSubgroup("a");
        update.setPolicies(Collections.emptyList());
        update.setPdpHeartbeatIntervalMs(4 * 600000L);

        assertFalse(fsm.update(update));

        assertEquals(PdpState.TERMINATED, fsm.state.state());
        assertNotEquals((4 * 60000L) / 1000L, fsm.getStatusTimerSeconds());
    }

    @Test
    public void testStateChange() {
        PdpStateChange change = new PdpStateChange();
        change.setPdpGroup("A");
        change.setPdpSubgroup("a");
        change.setState(PdpState.ACTIVE);
        change.setName("test");

        fsm.stateChange(change);

        assertEquals(PdpState.TERMINATED, fsm.state.state());
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