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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.common.utils.coder.CoderException;
import org.onap.policy.common.utils.coder.StandardCoder;
import org.onap.policy.common.utils.network.NetworkUtil;
import org.onap.policy.models.pdp.concepts.PdpStateChange;
import org.onap.policy.models.pdp.concepts.PdpStatus;
import org.onap.policy.models.pdp.concepts.PdpUpdate;
import org.onap.policy.models.pdp.enums.PdpHealthStatus;
import org.onap.policy.models.pdp.enums.PdpMessageType;
import org.onap.policy.models.pdp.enums.PdpState;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicy;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicyTypeIdentifier;

/**
 * Lifecycle State Passive Tests.
 */
public class LifecycleStatePassiveTest extends LifecycleStateRunningTest {

    /**
     * Start tests in the Passive state.
     */
    @Before
    public void startPassive() {
        /* start every test in passive mode */
        fsm = makeFsmWithPseudoTime();
        fsm.setStatusTimerSeconds(15L);
        simpleStart();
    }

    @Test
    public void constructor() {
        assertThatIllegalArgumentException().isThrownBy(() -> new LifecycleStatePassive(null));
        fsm.shutdown();
    }

    @Test
    public void controller() {
        fsm.start(controllerSupport.getController());
        assertSame(controllerSupport.getController(),
                        fsm.getController(new ToscaPolicyTypeIdentifier(ControllerSupport.POLICY_TYPE,
                                        ControllerSupport.POLICY_TYPE_VERSION)));

        fsm.stop(controllerSupport.getController());
        assertNull(fsm.getController(new ToscaPolicyTypeIdentifier(ControllerSupport.POLICY_TYPE,
                        ControllerSupport.POLICY_TYPE_VERSION)));

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
            PdpStatus status = new StandardCoder().decode(events[events.length - 1], PdpStatus.class);

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
    public void shutdown() throws Exception {
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
        waitUntil(5, TimeUnit.SECONDS, isStatus(state, 1));

        waitUntil(fsm.statusTimerSeconds + 2, TimeUnit.SECONDS, isStatus(state, 2));

        waitUntil(fsm.statusTimerSeconds + 2, TimeUnit.SECONDS, isStatus(state, 3));

        assertTrue(fsm.status());
        waitUntil(200, TimeUnit.MILLISECONDS, isStatus(state, 4));
    }

    @Test
    public void update() throws IOException, CoderException {
        controllerSupport.getController().getDrools().delete(ToscaPolicy.class);
        assertEquals(0, controllerSupport.getController().getDrools().factCount("junits"));

        PdpUpdate update = new PdpUpdate();
        update.setName(NetworkUtil.getHostname());
        update.setPdpGroup("Z");
        update.setPdpSubgroup("z");
        update.setPolicies(Collections.emptyList());

        long interval = 2 * fsm.getStatusTimerSeconds();
        update.setPdpHeartbeatIntervalMs(interval * 1000L);

        assertTrue(fsm.update(update));

        int qlength = fsm.client.getSink().getRecentEvents().length;
        PdpStatus lastStatus = new StandardCoder().decode(fsm.client.getSink().getRecentEvents()[qlength - 1],
                        PdpStatus.class);
        assertEquals(update.getRequestId(), lastStatus.getRequestId());
        assertEquals(update.getRequestId(), lastStatus.getResponse().getResponseTo());

        assertEquals(PdpState.PASSIVE, fsm.state());
        assertEquals(interval, fsm.getStatusTimerSeconds());
        assertEquals("Z", fsm.getGroup());
        assertEquals("z", fsm.getSubgroup());
        assertBasicPassive();

        String rawPolicy = new String(
                        Files.readAllBytes(Paths.get("src/test/resources/tosca-policy-operational-restart.json")));
        ToscaPolicy toscaPolicy = new StandardCoder().decode(rawPolicy, ToscaPolicy.class);
        update.setPolicies(Arrays.asList(toscaPolicy));

        assertFalse(fsm.update(update));

        assertEquals(PdpState.PASSIVE, fsm.state());
        assertEquals(interval, fsm.getStatusTimerSeconds());
        assertEquals("Z", fsm.getGroup());
        assertEquals("z", fsm.getSubgroup());
        assertBasicPassive();

        assertTrue(fsm.policyTypesMap.isEmpty());
        assertTrue(fsm.policiesMap.isEmpty());

        update.setPdpGroup(null);
        update.setPdpSubgroup(null);

        assertFalse(fsm.update(update));

        assertEquals(PdpState.PASSIVE, fsm.state());
        assertEquals(interval, fsm.getStatusTimerSeconds());
        assertNull(fsm.getGroup());
        assertNull(fsm.getSubgroup());
        assertBasicPassive();
        assertTrue(fsm.policyTypesMap.isEmpty());
        assertTrue(fsm.policiesMap.isEmpty());

        update.setPdpGroup("A");
        update.setPdpSubgroup("a");

        assertFalse(fsm.update(update));

        assertEquals(PdpState.PASSIVE, fsm.state());
        assertEquals(interval, fsm.getStatusTimerSeconds());
        assertEquals("A", fsm.getGroup());
        assertEquals("a", fsm.getSubgroup());
        assertBasicPassive();
        assertTrue(fsm.policyTypesMap.isEmpty());
        assertTrue(fsm.policiesMap.isEmpty());

        fsm.start(controllerSupport.getController());
        assertEquals(1, fsm.policyTypesMap.size());
        assertTrue(fsm.policiesMap.isEmpty());

        assertTrue(fsm.update(update));
        assertEquals(1, fsm.policyTypesMap.size());
        assertEquals(1, fsm.policiesMap.size());
        assertEquals(fsm.policiesMap.get(toscaPolicy.getIdentifier()), toscaPolicy);
        assertEquals(PdpState.PASSIVE, fsm.state());
        assertEquals(interval, fsm.getStatusTimerSeconds());
        assertEquals("A", fsm.getGroup());
        assertEquals("a", fsm.getSubgroup());
        assertBasicPassive();
        assertEquals(0, controllerSupport.getController().getDrools().factCount("junits"));

        update.setPdpGroup(null);
        update.setPdpSubgroup(null);
        update.setPolicies(Collections.emptyList());
        assertTrue(fsm.update(update));
        assertEquals(1, fsm.policyTypesMap.size());
        assertEquals(0, fsm.policiesMap.size());
        assertEquals(PdpState.PASSIVE, fsm.state());
        assertEquals(interval, fsm.getStatusTimerSeconds());
        assertNull(fsm.getGroup());
        assertNull(fsm.getSubgroup());
        assertBasicPassive();
        assertEquals(0, controllerSupport.getController().getDrools().factCount("junits"));

        fsm.shutdown();
    }

    @Test
    public void stateChange() throws CoderException, IOException {
        /* no name */
        PdpStateChange change = new PdpStateChange();
        change.setPdpGroup("A");
        change.setPdpSubgroup("a");
        change.setState(PdpState.ACTIVE);

        /* invalid name */
        change.setName("test");
        fsm.source.offer(new StandardCoder().encode(change));

        assertEquals(PdpState.PASSIVE, fsm.state());
        assertNull(fsm.getGroup());
        assertNull(fsm.getSubgroup());

        PdpUpdate update = new PdpUpdate();
        update.setName(NetworkUtil.getHostname());
        update.setPdpGroup("A");
        update.setPdpSubgroup("a");

        String rawPolicy = new String(
                        Files.readAllBytes(Paths.get("src/test/resources/tosca-policy-operational-restart.json")));
        ToscaPolicy toscaPolicy = new StandardCoder().decode(rawPolicy, ToscaPolicy.class);
        update.setPolicies(Arrays.asList(toscaPolicy));

        controllerSupport.getController().start();
        fsm.start(controllerSupport.getController());
        assertEquals(1, fsm.policyTypesMap.size());
        assertTrue(fsm.policiesMap.isEmpty());

        assertTrue(fsm.update(update));
        assertEquals(1, fsm.policyTypesMap.size());
        assertEquals(1, fsm.policiesMap.size());
        assertEquals(fsm.policiesMap.get(toscaPolicy.getIdentifier()), toscaPolicy);
        assertEquals(PdpState.PASSIVE, fsm.state());
        assertEquals("A", fsm.getGroup());
        assertEquals("a", fsm.getSubgroup());
        assertBasicPassive();
        assertEquals(0, controllerSupport.getController().getDrools().factCount("junits"));

        /* correct name */
        change.setName(fsm.getName());
        fsm.source.offer(new StandardCoder().encode(change));

        assertEquals(PdpState.ACTIVE, fsm.state());
        assertEquals("A", fsm.getGroup());
        assertEquals("a", fsm.getSubgroup());

        waitUntil(5, TimeUnit.SECONDS, () -> controllerSupport.getController().getDrools().factCount("junits") == 1);

        assertTrue(controllerSupport.getController().getDrools().delete(ToscaPolicy.class));
        assertEquals(0, controllerSupport.getController().getDrools().factCount("junits"));

        fsm.shutdown();
    }

    private void assertBasicTerminated() {
        assertEquals(PdpState.TERMINATED, fsm.state.state());
        assertFalse(fsm.isAlive());
        assertFalse(fsm.state.isAlive());
    }

    private void assertExtendedTerminated() throws Exception {
        assertBasicTerminated();
        assertTrue(fsm.statusTask.isCancelled());
        assertTrue(fsm.statusTask.isDone());

        // verify there are no outstanding tasks that might change the state
        assertTrue(time.isEmpty());

        assertFalse(fsm.client.getSink().isAlive());

        String[] events = fsm.client.getSink().getRecentEvents();
        PdpStatus status = new StandardCoder().decode(events[events.length - 1], PdpStatus.class);
        assertEquals("drools", status.getPdpType());
        assertEquals(PdpState.TERMINATED, status.getState());
        assertEquals(PdpHealthStatus.HEALTHY, status.getHealthy());
        assertEquals(NetworkUtil.getHostname(), status.getName());
        assertEquals(fsm.getName(), status.getName());
        assertEquals(PdpMessageType.PDP_STATUS, status.getMessageName());
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
