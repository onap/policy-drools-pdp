/*
 * ============LICENSE_START=======================================================
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
 *
 * SPDX-License-Identifier: Apache-2.0
 * =============LICENSE_END========================================================
 */

package org.onap.policy.drools.lifecycle;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
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
import org.onap.policy.models.pdp.concepts.PdpUpdate;
import org.onap.policy.models.pdp.enums.PdpMessageType;
import org.onap.policy.models.pdp.enums.PdpState;

/**
 * Lifecycle State Active Test.
 */
public class LifecycleStateActiveTest {

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
     * Start tests in the Active state.
     */
    @Before
    public void startActive() throws CoderException {
        fsm = new LifecycleFsm();
        fsm.setStatusTimerSeconds(15);
        assertTrue(fsm.start());

        goActive();
        assertActive();
    }

    private void goActive() throws CoderException {
        PdpStateChange change = new PdpStateChange();
        change.setPdpGroup("A");
        change.setPdpSubgroup("a");
        change.setState(PdpState.ACTIVE);
        change.setName(fsm.getName());

        fsm.source.offer(new StandardCoder().encode(change));
    }

    @Test
    public void constructor() {
        assertThatIllegalArgumentException().isThrownBy(() -> new LifecycleStateActive(null));
        fsm.shutdown();
    }

    @Test
    public void start() {
        assertActive();
        assertFalse(fsm.start());
        assertActive();

        fsm.shutdown();
    }

    private void assertActive() {
        assertEquals(PdpState.ACTIVE, fsm.state());
        assertEquals("A", fsm.getGroup());
        assertEquals("a", fsm.getSubgroup());
        assertTrue(fsm.isAlive());
        await().atMost(fsm.getStatusTimerSeconds() + 1, TimeUnit.SECONDS).until(isStatus(PdpState.ACTIVE));
    }

    @Test
    public void stop() {
        assertTrue(fsm.stop());
        assertBasicTerminated();

        fsm.shutdown();
    }

    private void assertBasicTerminated() {
        assertEquals(PdpState.TERMINATED, fsm.state());
        assertFalse(fsm.isAlive());
        assertFalse(fsm.state.isAlive());
        await().atMost(1, TimeUnit.SECONDS).until(isStatus(PdpState.TERMINATED));
    }

    @Test
    public void shutdown() {
        fsm.shutdown();

        assertBasicTerminated();

        assertTrue(fsm.statusTask.isCancelled());
        assertTrue(fsm.statusTask.isDone());
    }

    private Callable<Boolean> isStatus(PdpState state) {
        return () -> {
            if (fsm.client.getSink().getRecentEvents().length == 0) {
                return false;
            }

            List<String> events = Arrays.asList(fsm.client.getSink().getRecentEvents());
            PdpStatus status =
                new StandardCoder().decode(events.get(events.size() - 1), PdpStatus.class);

            return status.getMessageName() == PdpMessageType.PDP_STATUS && state == status.getState();
        };
    }

    @Test
    public void status() {
        await().atMost(fsm.getStatusTimerSeconds() + 1, TimeUnit.SECONDS).until(isStatus(PdpState.ACTIVE));
        int preCount = fsm.client.getSink().getRecentEvents().length;

        assertTrue(fsm.status());
        assertEquals(preCount + 1, fsm.client.getSink().getRecentEvents().length);

        fsm.shutdown();
    }

    @Test
    public void stateChange() throws CoderException {
        assertActive();

        /* no name and mismatching group info */
        PdpStateChange change = new PdpStateChange();
        change.setPdpGroup("B");
        change.setPdpSubgroup("b");
        change.setState(PdpState.ACTIVE);

        fsm.source.offer(new StandardCoder().encode(change));
        assertEquals(PdpState.ACTIVE, fsm.state());
        assertNotEquals("B", fsm.getGroup());
        assertNotEquals("b", fsm.getSubgroup());

        change.setName(fsm.getName());
        fsm.source.offer(new StandardCoder().encode(change));
        assertEquals(PdpState.ACTIVE, fsm.state());
        assertEquals("B", fsm.getGroup());
        assertEquals("b", fsm.getSubgroup());

        change.setState(PdpState.SAFE);
        fsm.source.offer(new StandardCoder().encode(change));
        assertEquals(PdpState.ACTIVE, fsm.state());

        change.setState(PdpState.TERMINATED);
        fsm.source.offer(new StandardCoder().encode(change));
        assertEquals(PdpState.ACTIVE, fsm.state());

        change.setState(PdpState.PASSIVE);
        fsm.source.offer(new StandardCoder().encode(change));
        assertEquals(PdpState.PASSIVE, fsm.state());
        await().atMost(fsm.getStatusTimerSeconds() + 1, TimeUnit.SECONDS).until(isStatus(PdpState.PASSIVE));

        fsm.shutdown();
    }

    @Test
    public void update() {
        PdpUpdate update = new PdpUpdate();
        update.setName(NetworkUtil.getHostname());
        update.setPdpGroup("Z");
        update.setPdpSubgroup("z");
        update.setPolicies(Collections.emptyList());

        long originalInterval = fsm.getStatusTimerSeconds();
        long interval = 2 * originalInterval;
        update.setPdpHeartbeatIntervalMs(interval * 1000L);

        assertTrue(fsm.update(update));

        assertEquals(PdpState.ACTIVE, fsm.state());
        assertEquals(interval, fsm.getStatusTimerSeconds());
        assertEquals("Z", fsm.getGroup());
        assertEquals("z", fsm.getSubgroup());

        fsm.shutdown();
    }
}
