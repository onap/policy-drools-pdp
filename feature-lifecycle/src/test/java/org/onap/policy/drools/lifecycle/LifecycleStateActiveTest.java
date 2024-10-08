/*
 * ============LICENSE_START=======================================================
 * Copyright (C) 2019-2022 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2021, 2024 Nordix Foundation.
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.onap.policy.common.utils.coder.CoderException;
import org.onap.policy.common.utils.coder.StandardCoder;
import org.onap.policy.drools.system.PolicyEngineConstants;
import org.onap.policy.models.pdp.concepts.PdpStateChange;
import org.onap.policy.models.pdp.concepts.PdpStatus;
import org.onap.policy.models.pdp.concepts.PdpUpdate;
import org.onap.policy.models.pdp.enums.PdpState;
import org.onap.policy.models.tosca.authorative.concepts.ToscaConceptIdentifier;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicy;

/**
 * Lifecycle State Active Test.
 */
class LifecycleStateActiveTest extends LifecycleStateRunningTest {

    private static final String POLICY_COMPLIANT_VCPE_BAD_INTEGER_JSON =
            "src/test/resources/tosca-policy-compliant-vcpe-bad-integer.json";

    /**
     * Start tests in the Active state.
     */
    @BeforeEach
    public void startActive() throws CoderException {
        fsm = makeFsmWithPseudoTime();

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
        change.setName(fsm.getPdpName());

        fsm.setSubGroup("a");
        fsm.source.offer(new StandardCoder().encode(change));
        controllerSupport.getController().start();
    }

    @Test
    void constructor() {
        assertThatIllegalArgumentException().isThrownBy(() -> new LifecycleStateActive(null));
        fsm.shutdown();
    }

    @Test
    void testStart() {
        assertActive();
        assertFalse(fsm.start());
        assertActive();

        fsm.shutdown();
    }

    private void assertActive() {
        assertEquals(PdpState.ACTIVE, fsm.state());
        assertEquals(LifecycleFsm.DEFAULT_PDP_GROUP, fsm.getGroup());
        assertEquals("a", fsm.getSubGroup());
        assertTrue(fsm.isAlive());
        waitUntil(fsm.getStatusTimerSeconds() + 1, TimeUnit.SECONDS, isStatus(PdpState.ACTIVE));
    }

    @Test
    void testStop() {
        assertTrue(fsm.stop());
        assertBasicTerminated();

        fsm.shutdown();
    }

    private void assertBasicTerminated() {
        assertEquals(PdpState.TERMINATED, fsm.state());
        assertFalse(fsm.isAlive());
        assertFalse(fsm.state.isAlive());
        waitUntil(1, TimeUnit.SECONDS, isStatus(PdpState.TERMINATED));
    }

    @Test
    void testShutdown() {
        fsm.shutdown();

        assertBasicTerminated();

        assertTrue(fsm.statusTask.isCancelled());
        assertTrue(fsm.statusTask.isDone());
    }

    @Test
    void testStatus() {
        waitUntil(fsm.getStatusTimerSeconds() + 1, TimeUnit.SECONDS, isStatus(PdpState.ACTIVE));
        int preCount = fsm.client.getSink().getRecentEvents().length;

        assertTrue(fsm.status());
        assertEquals(preCount + 1, fsm.client.getSink().getRecentEvents().length);

        fsm.start(controllerSupport.getController());
        assertTrue(fsm.status());
        assertEquals(preCount + 2, fsm.client.getSink().getRecentEvents().length);

        fsm.stop(controllerSupport.getController());
        fsm.shutdown();
    }

    @Test
    void testStateChange() throws CoderException {
        assertActive();

        /* no name and mismatching group info */
        PdpStateChange change = new PdpStateChange();
        change.setPdpGroup("B");
        change.setPdpSubgroup("b");
        change.setState(PdpState.ACTIVE);

        fsm.source.offer(new StandardCoder().encode(change));
        assertEquals(PdpState.ACTIVE, fsm.state());
        assertEquals(LifecycleFsm.DEFAULT_PDP_GROUP, fsm.getGroup());
        assertNotEquals("b", fsm.getSubGroup());

        change.setName(fsm.getPdpName());
        fsm.source.offer(new StandardCoder().encode(change));
        assertEquals(PdpState.ACTIVE, fsm.state());
        assertEquals(LifecycleFsm.DEFAULT_PDP_GROUP, fsm.getGroup());
        assertEquals("a", fsm.getSubGroup());

        change.setState(PdpState.SAFE);
        fsm.source.offer(new StandardCoder().encode(change));
        assertEquals(PdpState.ACTIVE, fsm.state());

        change.setState(PdpState.TERMINATED);
        fsm.source.offer(new StandardCoder().encode(change));
        assertEquals(PdpState.ACTIVE, fsm.state());

        change.setState(PdpState.PASSIVE);
        fsm.source.offer(new StandardCoder().encode(change));
        assertEquals(PdpState.PASSIVE, fsm.state());
        waitUntil(fsm.getStatusTimerSeconds() + 1, TimeUnit.SECONDS, isStatus(PdpState.PASSIVE));

        fsm.shutdown();
    }

    @Test
    void testUpdate() throws IOException, CoderException {

        PdpUpdate update = new PdpUpdate();
        update.setName(PolicyEngineConstants.getManager().getPdpName());
        update.setPdpGroup("W");
        update.setPdpSubgroup("w");

        fsm.start(controllerSupport.getController());
        assertTrue(fsm.update(update));

        assertEquals(PdpState.ACTIVE, fsm.state());
        assertEquals(LifecycleFsm.DEFAULT_PDP_GROUP, fsm.getGroup());
        assertEquals("w", fsm.getSubGroup());

        ToscaPolicy toscaPolicyRestartV1 =
            getExamplesPolicy("policies/vCPE.policy.operational.input.tosca.json", "operational.restart");
        toscaPolicyRestartV1.getProperties().put("controllerName", "lifecycle");
        update.setPoliciesToBeDeployed(List.of(toscaPolicyRestartV1));

        int qlength = fsm.client.getSink().getRecentEvents().length;

        // update with an operational.restart policy

        assertTrue(fsm.update(update));
        assertEquals(qlength + 1, fsm.client.getSink().getRecentEvents().length);
        assertEquals(3, fsm.policyTypesMap.size());
        assertNotNull(fsm.getPolicyTypesMap().get(
                new ToscaConceptIdentifier("onap.policies.native.drools.Controller", "1.0.0")));
        assertNotNull(fsm.getPolicyTypesMap().get(
                new ToscaConceptIdentifier("onap.policies.native.drools.Artifact", "1.0.0")));
        assertNotNull(fsm.getPolicyTypesMap().get(
                new ToscaConceptIdentifier("onap.policies.controlloop.operational.common.Drools",
                "1.0.0")));
        PdpStatus cachedStatus = new StandardCoder()
                                    .decode(fsm.client.getSink().getRecentEvents()[qlength], PdpStatus.class);
        assertEquals("foo", cachedStatus.getPdpType());
        assertEquals(new ArrayList<>(fsm.policiesMap.keySet()), cachedStatus.getPolicies());

        List<ToscaPolicy> factPolicies = controllerSupport.getFacts(ToscaPolicy.class);
        assertEquals(1, factPolicies.size());
        assertEquals(toscaPolicyRestartV1, factPolicies.get(0));
        assertEquals(1, fsm.policiesMap.size());

        // dup update with the same operational.restart policy - nothing changes

        assertTrue(fsm.update(update));
        assertEquals(qlength + 2, fsm.client.getSink().getRecentEvents().length);
        assertEquals(3, fsm.policyTypesMap.size());
        cachedStatus = new StandardCoder()
            .decode(fsm.client.getSink().getRecentEvents()[qlength + 1], PdpStatus.class);
        assertEquals(new ArrayList<>(fsm.policiesMap.keySet()), cachedStatus.getPolicies());

        factPolicies = controllerSupport.getFacts(ToscaPolicy.class);
        assertEquals(1, factPolicies.size());
        assertEquals(toscaPolicyRestartV1, factPolicies.get(0));
        assertEquals(1, fsm.policiesMap.size());

        // undeploy operational.restart policy

        update.setPoliciesToBeDeployed(List.of());
        update.setPoliciesToBeUndeployed(List.of(toscaPolicyRestartV1.getIdentifier()));
        assertTrue(fsm.update(update));
        assertEquals(qlength + 3, fsm.client.getSink().getRecentEvents().length);
        assertEquals(3, fsm.policyTypesMap.size());
        cachedStatus = new StandardCoder()
            .decode(fsm.client.getSink().getRecentEvents()[qlength + 2], PdpStatus.class);
        assertEquals(new ArrayList<>(fsm.policiesMap.keySet()), cachedStatus.getPolicies());

        factPolicies = controllerSupport.getFacts(ToscaPolicy.class);
        assertEquals(0, factPolicies.size());
        assertEquals(0, fsm.policiesMap.size());

        // redeploy operational.restart policy

        update.setPoliciesToBeUndeployed(List.of());
        update.setPoliciesToBeDeployed(List.of(toscaPolicyRestartV1));
        assertTrue(fsm.update(update));
        assertEquals(qlength + 4, fsm.client.getSink().getRecentEvents().length);
        assertEquals(3, fsm.policyTypesMap.size());
        cachedStatus = new StandardCoder()
            .decode(fsm.client.getSink().getRecentEvents()[qlength + 3], PdpStatus.class);
        assertEquals(new ArrayList<>(fsm.policiesMap.keySet()), cachedStatus.getPolicies());

        factPolicies = controllerSupport.getFacts(ToscaPolicy.class);
        assertEquals(1, factPolicies.size());
        assertEquals(toscaPolicyRestartV1, factPolicies.get(0));
        assertEquals(1, fsm.policiesMap.size());

        // deploy a new version of the operational.restart policy

        ToscaPolicy toscaPolicyRestartV2 =
            getExamplesPolicy("policies/vCPE.policy.operational.input.tosca.json", "operational.restart");
        toscaPolicyRestartV2.setVersion("2.0.0");
        toscaPolicyRestartV2.getProperties().put("controllerName", "lifecycle");
        update.setPoliciesToBeUndeployed(List.of(toscaPolicyRestartV1.getIdentifier()));
        update.setPoliciesToBeDeployed(List.of(toscaPolicyRestartV2));
        assertTrue(fsm.update(update));
        assertEquals(qlength + 5, fsm.client.getSink().getRecentEvents().length);
        assertEquals(3, fsm.policyTypesMap.size());
        cachedStatus = new StandardCoder()
            .decode(fsm.client.getSink().getRecentEvents()[qlength + 4], PdpStatus.class);
        assertEquals(new ArrayList<>(fsm.policiesMap.keySet()), cachedStatus.getPolicies());

        factPolicies = controllerSupport.getFacts(ToscaPolicy.class);
        assertEquals(1, factPolicies.size());
        assertNotEquals(toscaPolicyRestartV1, factPolicies.get(0));
        assertEquals(toscaPolicyRestartV2, factPolicies.get(0));
        assertEquals(1, fsm.policiesMap.size());

        // deploy another policy : firewall

        ToscaPolicy toscaPolicyFirewall =
            getExamplesPolicy("policies/vFirewall.policy.operational.input.tosca.json", "operational.modifyconfig");
        toscaPolicyFirewall.getProperties().put("controllerName", "lifecycle");
        update.setPoliciesToBeUndeployed(List.of());
        update.setPoliciesToBeDeployed(List.of(toscaPolicyRestartV2, toscaPolicyFirewall));
        assertTrue(fsm.update(update));
        assertEquals(qlength + 6, fsm.client.getSink().getRecentEvents().length);
        assertEquals(3, fsm.policyTypesMap.size());
        cachedStatus = new StandardCoder()
            .decode(fsm.client.getSink().getRecentEvents()[qlength + 5], PdpStatus.class);
        assertEquals(new ArrayList<>(fsm.policiesMap.keySet()), cachedStatus.getPolicies());

        factPolicies = controllerSupport.getFacts(ToscaPolicy.class);
        assertEquals(2, factPolicies.size());
        assertTrue(factPolicies.stream().noneMatch(ff -> Objects.equals(toscaPolicyRestartV1, ff)));
        assertTrue(factPolicies.stream().anyMatch(ff -> Objects.equals(toscaPolicyRestartV2, ff)));
        assertTrue(factPolicies.stream().anyMatch(ff -> Objects.equals(toscaPolicyFirewall, ff)));
        assertEquals(2, fsm.policiesMap.size());

        long originalInterval = fsm.getStatusTimerSeconds();
        long interval = 10 * originalInterval;
        update.setPdpHeartbeatIntervalMs(interval * 1000L);

        update.setPoliciesToBeUndeployed(List.of());
        update.setPoliciesToBeDeployed(List.of());
        assertTrue(fsm.update(update));

        assertEquals(PdpState.ACTIVE, fsm.state());
        assertEquals(interval, fsm.getStatusTimerSeconds());

        // bad policy deployment

        String badIntegerPolicy =
            Files.readString(Paths.get(POLICY_COMPLIANT_VCPE_BAD_INTEGER_JSON), StandardCharsets.UTF_8);
        ToscaPolicy toscaPolicyRestartBad = new StandardCoder().decode(badIntegerPolicy, ToscaPolicy.class);
        update.setPoliciesToBeUndeployed(List.of(toscaPolicyRestartV2.getIdentifier(),
                toscaPolicyFirewall.getIdentifier()));
        update.setPoliciesToBeDeployed(List.of(toscaPolicyRestartBad));
        assertFalse(fsm.update(update));

        assertTrue(controllerSupport.getController().getDrools().delete(ToscaPolicy.class));

        fsm.shutdown();
    }

}
