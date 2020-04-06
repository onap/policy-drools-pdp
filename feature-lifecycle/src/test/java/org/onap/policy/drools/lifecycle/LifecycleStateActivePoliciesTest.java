/*
 * ============LICENSE_START=======================================================
 * Copyright (C) 2020 AT&T Intellectual Property. All rights reserved.
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
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
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicy;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicyTypeIdentifier;

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

        fsm.setSubGroupAction("a");
        fsm.source.offer(new StandardCoder().encode(change));
        controllerSupport.getController().start();
    }

    @Test
    public void testUpdatePolicies() throws IOException, CoderException {
        assertEquals(2, fsm.policyTypesMap.size());
        assertNotNull(fsm.getPolicyTypesMap().get(
                new ToscaPolicyTypeIdentifier("onap.policies.native.drools.Controller", "1.0.0")));
        assertNotNull(fsm.getPolicyTypesMap().get(
                new ToscaPolicyTypeIdentifier("onap.policies.native.drools.Artifact", "1.0.0")));

        //
        // create controller using native policy
        //

        ToscaPolicy policyNativeController =
            getPolicyFromFile(EXAMPLE_NATIVE_DROOLS_POLICY_JSON, EXAMPLE_NATIVE_DROOLS_CONTROLLER_POLICY_NAME);

        PdpUpdate update = new PdpUpdate();
        update.setName(NetworkUtil.getHostname());
        update.setPdpGroup("W");
        update.setPdpSubgroup("w");
        update.setPolicies(List.of(policyNativeController));

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
        update.setPolicies(List.of(policyNativeController, policyNativeArtifact));
        assertFalse(fsm.update(update));

        // add a registered controller

        controllerMap.put("name", "lifecycle");
        update.setPolicies(List.of(policyNativeController, policyNativeArtifact));
        assertTrue(fsm.update(update));

        assertEquals(2, fsm.getPoliciesMap().size());
        assertEquals(policyNativeController, fsm.getPoliciesMap().get(policyNativeController.getIdentifier()));
        assertEquals(policyNativeArtifact, fsm.getPoliciesMap().get(policyNativeArtifact.getIdentifier()));

        //
        // add a legacy operational policy
        //

        String restart = Files.readString(Paths.get("src/test/resources/tosca-policy-operational-restart.json"));
        ToscaPolicy opPolicyRestart = new StandardCoder().decode(restart, ToscaPolicy.class);
        update.setPolicies(List.of(policyNativeController, policyNativeArtifact, opPolicyRestart));
        assertFalse(fsm.update(update));

        assertEquals(2, fsm.getPoliciesMap().size());
        assertEquals(policyNativeController, fsm.getPoliciesMap().get(policyNativeController.getIdentifier()));
        assertEquals(policyNativeArtifact, fsm.getPoliciesMap().get(policyNativeArtifact.getIdentifier()));

        // register controller

        fsm.start(controllerSupport.getController());

        assertEquals(4, fsm.policyTypesMap.size());
        assertNotNull(fsm.getPolicyTypesMap().get(
                new ToscaPolicyTypeIdentifier("onap.policies.native.drools.Controller", "1.0.0")));
        assertNotNull(fsm.getPolicyTypesMap().get(
                new ToscaPolicyTypeIdentifier("onap.policies.native.drools.Artifact", "1.0.0")));
        assertNotNull(fsm.getPolicyTypesMap().get(
                new ToscaPolicyTypeIdentifier("onap.policies.controlloop.Operational", "1.0.0")));
        assertNotNull(fsm.getPolicyTypesMap().get(
                new ToscaPolicyTypeIdentifier("onap.policies.controlloop.operational.common.Drools",
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

        String restartV2 = Files.readString(
            Paths.get("src/test/resources/tosca-policy-operational-restart.v2.json"));
        ToscaPolicy opPolicyRestartV2 = new StandardCoder().decode(restartV2, ToscaPolicy.class);
        opPolicyRestartV2.getProperties().put("controllerName", "lifecycle");
        update.setPolicies(List.of(policyNativeController, policyNativeArtifact, opPolicyRestartV2));
        assertTrue(fsm.update(update));

        assertEquals(3, fsm.getPoliciesMap().size());
        assertEquals(policyNativeController, fsm.getPoliciesMap().get(policyNativeController.getIdentifier()));
        assertEquals(policyNativeArtifact, fsm.getPoliciesMap().get(policyNativeArtifact.getIdentifier()));
        assertEquals(opPolicyRestartV2, fsm.getPoliciesMap().get(opPolicyRestartV2.getIdentifier()));
        assertNull(fsm.getPoliciesMap().get(opPolicyRestart.getIdentifier()));

        factPolicies = controllerSupport.getFacts(ToscaPolicy.class);
        assertEquals(1, factPolicies.size());
        assertEquals(opPolicyRestartV2, factPolicies.get(0));

        update.setPolicies(List.of(policyNativeController, policyNativeArtifact));
        assertTrue(fsm.update(update));

        assertEquals(2, fsm.getPoliciesMap().size());
        assertEquals(policyNativeController, fsm.getPoliciesMap().get(policyNativeController.getIdentifier()));
        assertEquals(policyNativeArtifact, fsm.getPoliciesMap().get(policyNativeArtifact.getIdentifier()));
        assertNull(fsm.getPoliciesMap().get(opPolicyRestartV2.getIdentifier()));
        assertNull(fsm.getPoliciesMap().get(opPolicyRestart.getIdentifier()));

        factPolicies = controllerSupport.getFacts(ToscaPolicy.class);
        assertEquals(0, factPolicies.size());
        assertTrue(controllerSupport.getController().getDrools().isBrained());

        update.setPolicies(List.of(policyNativeController));
        assertTrue(fsm.update(update));
        assertFalse(controllerSupport.getController().getDrools().isBrained());
        assertEquals(1, fsm.getPoliciesMap().size());
        assertEquals(policyNativeController, fsm.getPoliciesMap().get(policyNativeController.getIdentifier()));
        assertNull(fsm.getPoliciesMap().get(policyNativeArtifact.getIdentifier()));
        assertNull(fsm.getPoliciesMap().get(opPolicyRestartV2.getIdentifier()));
        assertNull(fsm.getPoliciesMap().get(opPolicyRestart.getIdentifier()));

        ToscaPolicy policyNativeFooController =
                getPolicyFromFile(FOO_NATIVE_DROOLS_POLICY_JSON, FOO_NATIVE_DROOLS_CONTROLLER_POLICY_NAME);
        update.setPolicies(List.of(policyNativeController, policyNativeFooController));
        assertTrue(fsm.update(update));
        assertEquals(2, fsm.getPoliciesMap().size());
        assertEquals(policyNativeController, fsm.getPoliciesMap().get(policyNativeController.getIdentifier()));
        assertEquals(policyNativeFooController, fsm.getPoliciesMap().get(policyNativeFooController.getIdentifier()));

        update.setPolicies(Collections.emptyList());
        assertTrue(fsm.update(update));
        assertThatIllegalArgumentException().isThrownBy(() -> controllerSupport.getController().getDrools());
        assertNull(fsm.getPoliciesMap().get(policyNativeController.getIdentifier()));
        assertNull(fsm.getPoliciesMap().get(policyNativeArtifact.getIdentifier()));
        assertNull(fsm.getPoliciesMap().get(opPolicyRestartV2.getIdentifier()));
        assertNull(fsm.getPoliciesMap().get(opPolicyRestart.getIdentifier()));

        fsm.shutdown();
    }
}
