/*
 * ============LICENSE_START=======================================================
 * Copyright (C) 2020 AT&T Intellectual Property. All rights reserved.
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
 *
 * SPDX-License-Identifier: Apache-2.0
 * =============LICENSE_END========================================================
 */

package org.onap.policy.drools.lifecycle;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.common.endpoints.event.comm.TopicEndpointManager;
import org.onap.policy.common.endpoints.properties.PolicyEndPointProperties;
import org.onap.policy.common.utils.coder.CoderException;
import org.onap.policy.common.utils.coder.StandardCoder;
import org.onap.policy.common.utils.network.NetworkUtil;
import org.onap.policy.models.pdp.concepts.PdpStateChange;
import org.onap.policy.models.pdp.concepts.PdpUpdate;
import org.onap.policy.models.pdp.enums.PdpState;
import org.onap.policy.models.tosca.authorative.concepts.ToscaConceptIdentifier;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicy;

/**
 * Lifecycle State Active Test.
 */
public class LifecycleStateActivePoliciesTest extends LifecycleStateRunningTest {

    private static final String EXAMPLE_NATIVE_DROOLS_CONTROLLER_POLICY_NAME = "example.controller";
    private static final String EXAMPLE_NATIVE_DROOLS_ARTIFACT_POLICY_NAME = "example.artifact";
    private static final String FOO_NATIVE_DROOLS_CONTROLLER_POLICY_NAME = "foo.controller";

    private static final String EXAMPLE_NATIVE_DROOLS_POLICY_JSON =
            "src/test/resources/tosca-policy-native-controller-example.json";
    private static final String EXAMPLE_NATIVE_ARTIFACT_POLICY_JSON =
            "src/test/resources/tosca-policy-native-artifact-example.json";
    private static final String FOO_NATIVE_DROOLS_POLICY_JSON =
            "src/test/resources/tosca-policy-native-controller-foo.json";

    /**
     * Start tests in the Active state.
     */
    @Before
    public void startActive() throws CoderException {
        fsm = makeFsmWithPseudoTime();

        fsm.setStatusTimerSeconds(15);
        assertTrue(fsm.start());

        PdpStateChange change = new PdpStateChange();
        change.setPdpGroup("A");
        change.setPdpSubgroup("a");
        change.setState(PdpState.ACTIVE);
        change.setName(fsm.getName());

        fsm.setSubGroup("a");
        fsm.source.offer(new StandardCoder().encode(change));
        controllerSupport.getController().start();
    }

    @Test
    public void testMandatoryPolicyTypes() {
        assertEquals(Set.of("onap.policies.native.drools.Artifact", "onap.policies.native.drools.Controller"),
            fsm.getMandatoryPolicyTypes());
        assertEquals(fsm.getMandatoryPolicyTypes(), fsm.getCurrentPolicyTypes());
        assertTrue(fsm.isMandatoryPolicyTypesCompliant());
        assertTrue(fsm.status());

        fsm.mandatoryPolicyTypes.add("blah");
        assertEquals(Set.of("onap.policies.native.drools.Artifact", "onap.policies.native.drools.Controller", "blah"),
                fsm.getMandatoryPolicyTypes());
        assertNotEquals(fsm.getMandatoryPolicyTypes(), fsm.getCurrentPolicyTypes());
        assertFalse(fsm.isMandatoryPolicyTypesCompliant());
        assertFalse(fsm.status());
    }

    @Test
    public void testUpdatePolicies() throws IOException, CoderException {
        assertEquals(2, fsm.policyTypesMap.size());
        assertNotNull(fsm.getPolicyTypesMap().get(
                new ToscaConceptIdentifier("onap.policies.native.drools.Controller", "1.0.0")));
        assertNotNull(fsm.getPolicyTypesMap().get(
                new ToscaConceptIdentifier("onap.policies.native.drools.Artifact", "1.0.0")));

        //
        // create controller using native policy
        //

        ToscaPolicy policyNativeController =
            getPolicyFromFile(EXAMPLE_NATIVE_DROOLS_POLICY_JSON, EXAMPLE_NATIVE_DROOLS_CONTROLLER_POLICY_NAME);

        PdpUpdate update = new PdpUpdate();
        update.setName(NetworkUtil.getHostname());
        update.setPdpGroup("W");
        update.setPdpSubgroup("w");
        update.setPoliciesToBeDeployed(List.of(policyNativeController));

        assertFalse(fsm.update(update));
        assertEquals(0, fsm.getPoliciesMap().size());

        // add topics

        Properties noopTopicProperties = new Properties();
        noopTopicProperties.put(PolicyEndPointProperties.PROPERTY_NOOP_SOURCE_TOPICS, "DCAE_TOPIC");
        noopTopicProperties.put(PolicyEndPointProperties.PROPERTY_NOOP_SINK_TOPICS, "APPC-CL");
        TopicEndpointManager.getManager().addTopics(noopTopicProperties);

        assertTrue(fsm.update(update));
        assertEquals(1, fsm.getPoliciesMap().size());

        //
        // add an artifact policy
        //

        ToscaPolicy policyNativeArtifact =
                getPolicyFromFile(EXAMPLE_NATIVE_ARTIFACT_POLICY_JSON, EXAMPLE_NATIVE_DROOLS_ARTIFACT_POLICY_NAME);

        @SuppressWarnings("unchecked")
        Map<String, String> controllerMap =
                (Map<String, String>) policyNativeArtifact.getProperties().get("controller");
        controllerMap.put("name", "xyz987");
        update.setPoliciesToBeDeployed(List.of(policyNativeController, policyNativeArtifact));
        assertFalse(fsm.update(update));

        // add a registered controller

        controllerMap.put("name", "lifecycle");
        update.setPoliciesToBeDeployed(List.of(policyNativeController, policyNativeArtifact));
        assertTrue(fsm.update(update));

        assertEquals(2, fsm.getPoliciesMap().size());
        assertEquals(policyNativeController, fsm.getPoliciesMap().get(policyNativeController.getIdentifier()));
        assertEquals(policyNativeArtifact, fsm.getPoliciesMap().get(policyNativeArtifact.getIdentifier()));

        //
        // add an operational policy
        //

        ToscaPolicy opPolicyRestart =
            getExamplesPolicy("policies/vCPE.policy.operational.input.tosca.json", "operational.restart");
        update.setPoliciesToBeDeployed(List.of(policyNativeController, policyNativeArtifact, opPolicyRestart));
        assertFalse(fsm.update(update));

        assertEquals(2, fsm.getPoliciesMap().size());
        assertEquals(policyNativeController, fsm.getPoliciesMap().get(policyNativeController.getIdentifier()));
        assertEquals(policyNativeArtifact, fsm.getPoliciesMap().get(policyNativeArtifact.getIdentifier()));

        // register controller

        fsm.start(controllerSupport.getController());

        assertEquals(3, fsm.policyTypesMap.size());
        assertNotNull(fsm.getPolicyTypesMap().get(
                new ToscaConceptIdentifier("onap.policies.native.drools.Controller", "1.0.0")));
        assertNotNull(fsm.getPolicyTypesMap().get(
                new ToscaConceptIdentifier("onap.policies.native.drools.Artifact", "1.0.0")));
        assertNotNull(fsm.getPolicyTypesMap().get(
                new ToscaConceptIdentifier("onap.policies.controlloop.operational.common.Drools",
                        "1.0.0")));

        // invalid controller name

        opPolicyRestart.getProperties().put("controllerName", "xyz987");
        assertFalse(fsm.update(update));
        assertEquals(2, fsm.getPoliciesMap().size());
        assertEquals(policyNativeController, fsm.getPoliciesMap().get(policyNativeController.getIdentifier()));
        assertEquals(policyNativeArtifact, fsm.getPoliciesMap().get(policyNativeArtifact.getIdentifier()));

        // no controller name

        opPolicyRestart.getProperties().remove("controllerName");
        assertTrue(fsm.update(update));
        assertEquals(3, fsm.getPoliciesMap().size());
        assertEquals(policyNativeController, fsm.getPoliciesMap().get(policyNativeController.getIdentifier()));
        assertEquals(policyNativeArtifact, fsm.getPoliciesMap().get(policyNativeArtifact.getIdentifier()));
        assertEquals(opPolicyRestart, fsm.getPoliciesMap().get(opPolicyRestart.getIdentifier()));

        List<ToscaPolicy> factPolicies = controllerSupport.getFacts(ToscaPolicy.class);
        assertEquals(1, factPolicies.size());
        assertEquals(opPolicyRestart, factPolicies.get(0));

        // upgrade operational policy with valid controller name

        ToscaPolicy opPolicyRestartV2 =
            getExamplesPolicy("policies/vCPE.policy.operational.input.tosca.json", "operational.restart");
        opPolicyRestartV2.setVersion("2.0.0");
        opPolicyRestartV2.getProperties().put("controllerName", "lifecycle");
        update.setPolicies(List.of());
        update.setPoliciesToBeDeployed(List.of(policyNativeController, policyNativeArtifact, opPolicyRestartV2));
        update.setPoliciesToBeUndeployed(List.of(opPolicyRestart.getIdentifier()));
        assertTrue(fsm.update(update));

        assertEquals(3, fsm.getPoliciesMap().size());
        assertEquals(policyNativeController, fsm.getPoliciesMap().get(policyNativeController.getIdentifier()));
        assertEquals(policyNativeArtifact, fsm.getPoliciesMap().get(policyNativeArtifact.getIdentifier()));
        assertEquals(opPolicyRestartV2, fsm.getPoliciesMap().get(opPolicyRestartV2.getIdentifier()));
        assertNull(fsm.getPoliciesMap().get(opPolicyRestart.getIdentifier()));

        factPolicies = controllerSupport.getFacts(ToscaPolicy.class);
        assertEquals(1, factPolicies.size());
        assertEquals(opPolicyRestartV2, factPolicies.get(0));

        update.setPolicies(List.of());
        update.setPoliciesToBeDeployed(List.of());
        update.setPoliciesToBeUndeployed(List.of(opPolicyRestartV2.getIdentifier()));
        assertTrue(fsm.update(update));

        assertEquals(2, fsm.getPoliciesMap().size());
        assertEquals(policyNativeController, fsm.getPoliciesMap().get(policyNativeController.getIdentifier()));
        assertEquals(policyNativeArtifact, fsm.getPoliciesMap().get(policyNativeArtifact.getIdentifier()));
        assertNull(fsm.getPoliciesMap().get(opPolicyRestartV2.getIdentifier()));
        assertNull(fsm.getPoliciesMap().get(opPolicyRestart.getIdentifier()));

        factPolicies = controllerSupport.getFacts(ToscaPolicy.class);
        assertEquals(0, factPolicies.size());
        assertTrue(controllerSupport.getController().getDrools().isBrained());

        update.setPolicies(List.of());
        update.setPoliciesToBeDeployed(List.of());
        update.setPoliciesToBeUndeployed(List.of(policyNativeArtifact.getIdentifier(),
                opPolicyRestartV2.getIdentifier()));
        assertTrue(fsm.update(update));
        assertFalse(controllerSupport.getController().getDrools().isBrained());
        assertEquals(1, fsm.getPoliciesMap().size());
        assertEquals(policyNativeController, fsm.getPoliciesMap().get(policyNativeController.getIdentifier()));
        assertNull(fsm.getPoliciesMap().get(policyNativeArtifact.getIdentifier()));
        assertNull(fsm.getPoliciesMap().get(opPolicyRestartV2.getIdentifier()));
        assertNull(fsm.getPoliciesMap().get(opPolicyRestart.getIdentifier()));

        ToscaPolicy policyNativeFooController =
                getPolicyFromFile(FOO_NATIVE_DROOLS_POLICY_JSON, FOO_NATIVE_DROOLS_CONTROLLER_POLICY_NAME);
        update.setPolicies(List.of());
        update.setPoliciesToBeUndeployed(List.of());
        update.setPoliciesToBeDeployed(List.of(policyNativeFooController));
        assertTrue(fsm.update(update));
        assertEquals(2, fsm.getPoliciesMap().size());
        assertEquals(policyNativeController, fsm.getPoliciesMap().get(policyNativeController.getIdentifier()));
        assertEquals(policyNativeFooController, fsm.getPoliciesMap().get(policyNativeFooController.getIdentifier()));

        update.setPolicies(List.of());
        update.setPoliciesToBeDeployed(List.of());
        update.setPoliciesToBeUndeployed(List.of(policyNativeController.getIdentifier(),
                policyNativeFooController.getIdentifier()));
        assertTrue(fsm.update(update));
        assertThatIllegalArgumentException().isThrownBy(() -> controllerSupport.getController().getDrools());
        assertNull(fsm.getPoliciesMap().get(policyNativeController.getIdentifier()));
        assertNull(fsm.getPoliciesMap().get(policyNativeArtifact.getIdentifier()));
        assertNull(fsm.getPoliciesMap().get(opPolicyRestartV2.getIdentifier()));
        assertNull(fsm.getPoliciesMap().get(opPolicyRestart.getIdentifier()));

        fsm.shutdown();
    }
}
