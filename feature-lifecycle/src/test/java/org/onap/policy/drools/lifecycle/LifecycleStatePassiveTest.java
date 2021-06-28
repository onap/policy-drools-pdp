/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2019-2021 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2021 Nordix Foundation.
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

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.common.utils.coder.CoderException;
import org.onap.policy.common.utils.coder.StandardCoder;
import org.onap.policy.drools.system.PolicyEngineConstants;
import org.onap.policy.models.pdp.concepts.PdpStateChange;
import org.onap.policy.models.pdp.concepts.PdpStatus;
import org.onap.policy.models.pdp.concepts.PdpUpdate;
import org.onap.policy.models.pdp.enums.PdpHealthStatus;
import org.onap.policy.models.pdp.enums.PdpMessageType;
import org.onap.policy.models.pdp.enums.PdpState;
import org.onap.policy.models.tosca.authorative.concepts.ToscaConceptIdentifier;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicy;

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
    public void testController() {
        fsm.start(controllerSupport.getController());
        assertSame(controllerSupport.getController(),
            ((PolicyTypeDroolsController) fsm.getController(
                    new ToscaConceptIdentifier(
                            ControllerSupport.POLICY_TYPE_COMPLIANT_OP, ControllerSupport.POLICY_TYPE_VERSION)))
                .controllers().get(0));

        fsm.stop(controllerSupport.getController());
        assertNull(fsm.getController(new ToscaConceptIdentifier(ControllerSupport.POLICY_TYPE_COMPLIANT_OP,
                        ControllerSupport.POLICY_TYPE_VERSION)));

        fsm.shutdown();
    }

    @Test
    public void testStart() {
        assertEquals(0, fsm.client.getSink().getRecentEvents().length);
        assertFalse(fsm.start());
        assertBasicPassive();

        fsm.shutdown();
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
    public void testShutdown() throws Exception {
        simpleStop();

        fsm.shutdown();
        assertExtendedTerminated();
    }

    @Test
    public void testStatus() {
        assertTrue(fsm.client.getSink().isAlive());
        assertTrue(fsm.status());
        assertSame(1, fsm.client.getSink().getRecentEvents().length);


        fsm.start(controllerSupport.getController());
        status(PdpState.PASSIVE, 1);
        fsm.stop(controllerSupport.getController());
        fsm.shutdown();
    }

    private void status(PdpState state, int initial) {
        waitUntil(5, TimeUnit.SECONDS, isStatus(state, initial));
        waitUntil(fsm.statusTimerSeconds + 2, TimeUnit.SECONDS, isStatus(state, initial + 1));
        waitUntil(fsm.statusTimerSeconds + 2, TimeUnit.SECONDS, isStatus(state, initial + 2));
        assertTrue(fsm.status());
        waitUntil(200, TimeUnit.MILLISECONDS, isStatus(state, initial + 3));
    }

    @Test
    public void testUpdate() throws CoderException {
        controllerSupport.getController().getDrools().delete(ToscaPolicy.class);
        assertEquals(0, controllerSupport.getController().getDrools().factCount("junits"));

        PdpUpdate update = new PdpUpdate();
        update.setName(PolicyEngineConstants.PDP_NAME);
        update.setPdpGroup("Z");
        update.setPdpSubgroup("z");

        long interval = 2 * fsm.getStatusTimerSeconds();
        update.setPdpHeartbeatIntervalMs(interval * 1000L);

        assertTrue(fsm.update(update));

        int qlength = fsm.client.getSink().getRecentEvents().length;
        PdpStatus lastStatus = new StandardCoder().decode(fsm.client.getSink().getRecentEvents()[qlength - 1],
                        PdpStatus.class);
        assertEquals("foo", lastStatus.getPdpType());
        assertEquals(update.getRequestId(), lastStatus.getRequestId());
        assertEquals(update.getRequestId(), lastStatus.getResponse().getResponseTo());

        assertEquals(PdpState.PASSIVE, fsm.state());
        assertEquals(interval, fsm.getStatusTimerSeconds());
        assertEquals(LifecycleFsm.DEFAULT_PDP_GROUP, fsm.getGroup());
        assertEquals("z", fsm.getSubGroup());
        assertBasicPassive();

        ToscaPolicy toscaPolicy =
            getExamplesPolicy("policies/vCPE.policy.operational.input.tosca.json", "operational.restart");
        toscaPolicy.getProperties().put("controllerName", "lifecycle");
        update.setPoliciesToBeDeployed(List.of(toscaPolicy));

        assertFalse(fsm.update(update));

        assertEquals(PdpState.PASSIVE, fsm.state());
        assertEquals(interval, fsm.getStatusTimerSeconds());
        assertEquals(LifecycleFsm.DEFAULT_PDP_GROUP, fsm.getGroup());
        assertEquals("z", fsm.getSubGroup());
        assertBasicPassive();

        assertEquals(2, fsm.policyTypesMap.size());
        assertTrue(fsm.policiesMap.isEmpty());

        update.setPdpGroup(null);
        update.setPdpSubgroup(null);

        assertFalse(fsm.update(update));

        assertEquals(PdpState.PASSIVE, fsm.state());
        assertEquals(interval, fsm.getStatusTimerSeconds());
        assertEquals(LifecycleFsm.DEFAULT_PDP_GROUP, fsm.getGroup());
        assertNull(fsm.getSubGroup());
        assertBasicPassive();
        assertEquals(2, fsm.policyTypesMap.size());
        assertTrue(fsm.policiesMap.isEmpty());

        update.setPdpGroup("A");
        update.setPdpSubgroup("a");

        assertFalse(fsm.update(update));

        assertEquals(PdpState.PASSIVE, fsm.state());
        assertEquals(interval, fsm.getStatusTimerSeconds());
        assertEquals(LifecycleFsm.DEFAULT_PDP_GROUP, fsm.getGroup());
        assertEquals("a", fsm.getSubGroup());
        assertBasicPassive();
        assertEquals(2, fsm.policyTypesMap.size());
        assertTrue(fsm.policiesMap.isEmpty());

        fsm.start(controllerSupport.getController());
        assertEquals(3, fsm.policyTypesMap.size());
        assertTrue(fsm.policiesMap.isEmpty());

        assertTrue(fsm.update(update));
        assertEquals(3, fsm.policyTypesMap.size());
        assertEquals(1, fsm.policiesMap.size());
        assertEquals(fsm.policiesMap.get(toscaPolicy.getIdentifier()), toscaPolicy);
        assertEquals(PdpState.PASSIVE, fsm.state());
        assertEquals(interval, fsm.getStatusTimerSeconds());
        assertEquals(LifecycleFsm.DEFAULT_PDP_GROUP, fsm.getGroup());
        assertEquals("a", fsm.getSubGroup());
        assertBasicPassive();
        assertEquals(0, controllerSupport.getController().getDrools().factCount("junits"));

        // The "update" event will undeploy "toscaPolicy" and deploy "toscaPolicy2"
        ToscaPolicy toscaPolicy2 =
                getExamplesPolicy("policies/vFirewall.policy.operational.input.tosca.json", "operational.modifyconfig");
        toscaPolicy.getProperties().remove("controllerName");
        update.setPoliciesToBeUndeployed(List.of(toscaPolicy.getIdentifier()));
        update.setPoliciesToBeDeployed(List.of(toscaPolicy2));
        assertTrue(fsm.update(update));
        assertEquals(3, fsm.policyTypesMap.size());
        assertEquals(1, fsm.policiesMap.size());
        assertEquals(toscaPolicy2, fsm.policiesMap.get(toscaPolicy2.getIdentifier()));
        assertNull(fsm.policiesMap.get(toscaPolicy.getIdentifier()));
        assertEquals(0, controllerSupport.getController().getDrools().factCount("junits"));

        update.setPdpGroup(null);
        update.setPdpSubgroup(null);
        update.setPoliciesToBeUndeployed(List.of(toscaPolicy2.getIdentifier()));
        update.setPoliciesToBeDeployed(List.of());
        assertTrue(fsm.update(update));
        assertEquals(3, fsm.policyTypesMap.size());
        assertEquals(0, fsm.policiesMap.size());
        assertEquals(PdpState.PASSIVE, fsm.state());
        assertEquals(interval, fsm.getStatusTimerSeconds());
        assertEquals(LifecycleFsm.DEFAULT_PDP_GROUP, fsm.getGroup());
        assertNull(fsm.getSubGroup());
        assertBasicPassive();
        assertEquals(0, controllerSupport.getController().getDrools().factCount("junits"));

        fsm.shutdown();
    }

    @Test
    public void testStateChange() throws CoderException {
        /* no name */
        PdpStateChange change = new PdpStateChange();
        change.setPdpGroup("A");
        change.setPdpSubgroup("a");
        change.setState(PdpState.ACTIVE);

        /* invalid name */
        change.setName("test");
        fsm.source.offer(new StandardCoder().encode(change));

        assertEquals(PdpState.PASSIVE, fsm.state());
        assertEquals(LifecycleFsm.DEFAULT_PDP_GROUP, fsm.getGroup());
        assertNull(fsm.getSubGroup());

        PdpUpdate update = new PdpUpdate();
        update.setName(PolicyEngineConstants.PDP_NAME);
        update.setPdpGroup("A");
        update.setPdpSubgroup("a");

        ToscaPolicy toscaPolicy =
            getExamplesPolicy("policies/vCPE.policy.operational.input.tosca.json", "operational.restart");
        toscaPolicy.getProperties().put("controllerName", "lifecycle");
        update.setPoliciesToBeDeployed(List.of(toscaPolicy));

        controllerSupport.getController().start();
        fsm.start(controllerSupport.getController());
        assertEquals(3, fsm.policyTypesMap.size());
        assertTrue(fsm.policiesMap.isEmpty());

        assertTrue(fsm.update(update));
        assertEquals(3, fsm.policyTypesMap.size());
        assertEquals(1, fsm.policiesMap.size());
        assertEquals(fsm.policiesMap.get(toscaPolicy.getIdentifier()), toscaPolicy);
        assertEquals(PdpState.PASSIVE, fsm.state());
        assertEquals(LifecycleFsm.DEFAULT_PDP_GROUP, fsm.getGroup());
        assertEquals("a", fsm.getSubGroup());
        assertBasicPassive();
        assertEquals(0, controllerSupport.getController().getDrools().factCount("junits"));

        /* correct name */
        change.setName(fsm.getName());
        fsm.source.offer(new StandardCoder().encode(change));

        assertEquals(PdpState.ACTIVE, fsm.state());
        assertEquals(LifecycleFsm.DEFAULT_PDP_GROUP, fsm.getGroup());
        assertEquals("a", fsm.getSubGroup());

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
        assertEquals("foo", status.getPdpType());
        assertEquals(PdpState.TERMINATED, status.getState());
        assertEquals(PdpHealthStatus.HEALTHY, status.getHealthy());
        assertEquals(PolicyEngineConstants.PDP_NAME, status.getName());
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
