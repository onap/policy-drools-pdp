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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.onap.policy.common.utils.coder.CoderException;
import org.onap.policy.common.utils.coder.StandardCoder;
import org.onap.policy.common.utils.network.NetworkUtil;
import org.onap.policy.drools.persistence.SystemPersistence;
import org.onap.policy.drools.utils.logging.LoggerUtil;
import org.onap.policy.models.pdp.concepts.PdpStateChange;
import org.onap.policy.models.pdp.concepts.PdpStatus;
import org.onap.policy.models.pdp.enums.PdpHealthStatus;
import org.onap.policy.models.pdp.enums.PdpMessageType;
import org.onap.policy.models.pdp.enums.PdpState;

/**
 * Lifecycle State Passive Tests.
 */
public class LifecycleStatePassiveTest {

    private LifecycleFsm fsm;

    @BeforeClass
    public static void setUp() {
        SystemPersistence.manager.setConfigurationDir("src/test/resources");
        LoggerUtil.setLevel("org.onap.policy.common.endpoints", "WARN");
    }

    @AfterClass
    public static void tearDown() {
        SystemPersistence.manager.setConfigurationDir(null);
    }

    /**
     * Start tests in the Passive state.
     */
    @Before
    public void startPassive() {
        /* start every test in passive mode */
        fsm = new LifecycleFsm();
        fsm.setStatusTimerSeconds(15L);
        simpleStart();

        assertEquals(0, fsm.client.getSink().getRecentEvents().length);
    }

    @Test
    public void constructor() {
        assertThatIllegalArgumentException().isThrownBy(() -> new LifecycleStatePassive(null));
        fsm.shutdown();
    }

    @Test
    public void start() {
        assertEquals(0, fsm.client.getSink().getRecentEvents().length);
        assertFalse(fsm.start());
        assertBasicPassive();

        fsm.shutdown();
    }

    private Callable<Boolean> isStatus(PdpState state, int count) {
        return () -> {
            if (!fsm.client.getSink().isAlive()) {
                return false;
            }

            if (fsm.client.getSink().getRecentEvents().length != count) {
                return false;
            }

            String[] events = fsm.client.getSink().getRecentEvents();
            PdpStatus status =
                new StandardCoder().decode(events[events.length - 1], PdpStatus.class);

            return status.getMessageName() == PdpMessageType.PDP_STATUS && state == status.getState();
        };
    }

    @Test
    public void stop() {
        simpleStop();
        assertBasicTerminated();
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
    public void shutdown() throws CoderException {
        simpleStop();

        fsm.shutdown();
        assertExtendedTerminated();
    }

    @Test
    public void status() {
        status(PdpState.PASSIVE);
        fsm.shutdown();
    }

    private void status(PdpState state) {
        await()
            .atMost(5, TimeUnit.SECONDS)
            .until(isStatus(state, 1));

        await()
            .atMost(fsm.statusTimerSeconds + 2, TimeUnit.SECONDS)
            .until(isStatus(state, 2));

        await()
            .atMost(fsm.statusTimerSeconds + 2, TimeUnit.SECONDS)
            .until(isStatus(state, 3));

        assertTrue(fsm.status());
        await()
            .atMost(200, TimeUnit.MILLISECONDS)
            .until(isStatus(state, 4));
    }

    @Test
    public void update() {
        // TODO
        fsm.shutdown();
    }

    @Test
    public void stateChange() throws CoderException {
        PdpStateChange change = new PdpStateChange();
        change.setPdpGroup("A");
        change.setPdpSubgroup("a");
        change.setState(PdpState.ACTIVE);
        change.setName("test");

        fsm.source.offer(new StandardCoder().encode(change));
        assertEquals(PdpState.ACTIVE, fsm.state.state());
        assertEquals("A", fsm.pdpGroup);
        assertEquals("a", fsm.pdpSubgroup);

        fsm.shutdown();
    }

    private void assertBasicTerminated() {
        assertEquals(PdpState.TERMINATED, fsm.state.state());
        assertFalse(fsm.isAlive());
        assertFalse(fsm.state.isAlive());
    }

    private void assertExtendedTerminated() throws CoderException {
        assertBasicTerminated();
        assertTrue(fsm.statusTask.isCancelled());
        assertTrue(fsm.statusTask.isDone());

        assertEquals(1, fsm.client.getSink().getRecentEvents().length);
        PdpStatus status = new StandardCoder().decode(fsm.client.getSink().getRecentEvents()[0], PdpStatus.class);
        assertEquals("drools", status.getPdpType());
        assertEquals(PdpState.TERMINATED, status.getState());
        assertEquals(PdpHealthStatus.HEALTHY, status.getHealthy());
        assertEquals(NetworkUtil.getHostname(), status.getInstance());
        assertEquals(PdpMessageType.PDP_STATUS, status.getMessageName());

        assertThatThrownBy( () -> await()
            .atMost(fsm.statusTimerSeconds + 5, TimeUnit.SECONDS)
            .until(isStatus(PdpState.TERMINATED, 2))).isInstanceOf(ConditionTimeoutException.class);
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
