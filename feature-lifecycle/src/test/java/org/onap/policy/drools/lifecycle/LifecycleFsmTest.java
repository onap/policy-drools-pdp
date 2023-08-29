/*
 * ============LICENSE_START=======================================================
 * Copyright (C) 2021-2022 AT&T Intellectual Property. All rights reserved.
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

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.common.utils.coder.CoderException;
import org.onap.policy.common.utils.coder.StandardCoder;
import org.onap.policy.common.utils.logging.LoggerUtils;
import org.onap.policy.common.utils.resources.ResourceUtils;
import org.onap.policy.common.utils.time.PseudoScheduledExecutorService;
import org.onap.policy.common.utils.time.TestTimeMulti;
import org.onap.policy.drools.persistence.SystemPersistenceConstants;
import org.onap.policy.drools.system.PolicyEngineConstants;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicy;
import org.onap.policy.models.tosca.authorative.concepts.ToscaServiceTemplate;

/**
 * Lifecycle FSM Test.
 */
public class LifecycleFsmTest {

    private static final String EXAMPLE_NATIVE_CONTROLLER_POLICY_NAME = "example.controller";
    private static final String EXAMPLE_NATIVE_CONTROLLER_POLICY_JSON =
            "src/test/resources/tosca-policy-native-controller-example.json";

    private static final String EXAMPLE_NATIVE_ARTIFACT_POLICY_NAME = "example.artifact";
    private static final String EXAMPLE_NATIVE_ARTIFACT_POLICY_JSON =
            "src/test/resources/tosca-policy-native-artifact-example.json";

    private static final String EXAMPLE_OTHER_UNVAL_POLICY_NAME = "other-unvalidated";
    private static final String EXAMPLE_OTHER_UNVAL_POLICY_JSON =
            "src/test/resources/tosca-policy-other-unvalidated.json";

    private static final String EXAMPLE_OTHER_VAL_POLICY_NAME = "other-validated";
    private static final String EXAMPLE_OTHER_VAL_POLICY_JSON =
            "src/test/resources/tosca-policy-other-validated.json";

    private static final String VCPE_OP_POLICY_NAME = "operational.restart";
    private static final String VCPE_OPERATIONAL_DROOLS_POLICY_JSON =
            "policies/vCPE.policy.operational.input.tosca.json";

    private static final String VFW_OP_POLICY_NAME = "operational.modifyconfig";
    private static final String VFW_OPERATIONAL_DROOLS_POLICY_JSON =
            "policies/vFirewall.policy.operational.input.tosca.json";

    private static final String USECASES_NATIVE_CONTROLLER_POLICY_NAME = "usecases";
    private static final String USECASES_NATIVE_CONTROLLER_JSON =
            "policies/usecases.native.controller.policy.input.tosca.json";

    private static final String USECASES_NATIVE_ARTIFACT_POLICY_NAME = "usecases.artifacts";
    private static final String USECASES_NATIVE_ARTIFACT_JSON =
            "policies/usecases.native.artifact.policy.input.tosca.json";

    private static final StandardCoder coder = new StandardCoder();

    protected LifecycleFsm fsm;
    private ToscaPolicy opPolicy;
    private ToscaPolicy op2Policy;
    private ToscaPolicy valPolicy;
    private ToscaPolicy unvalPolicy;
    private ToscaPolicy controllerPolicy;
    private ToscaPolicy controller2Policy;
    private ToscaPolicy artifactPolicy;
    private ToscaPolicy artifact2Policy;

    /**
     * Test initialization.
     */
    @Before
    public void beforeTest() throws CoderException, IOException {
        LoggerUtils.setLevel(LoggerUtils.ROOT_LOGGER, "INFO");
        LoggerUtils.setLevel("org.onap.policy.common.endpoints", "WARN");
        LoggerUtils.setLevel("org.onap.policy.drools", "INFO");
        SystemPersistenceConstants.getManager().setConfigurationDir("target/test-classes");
        PolicyEngineConstants.getManager().configure(new Properties());

        fsm = new LifecycleFsm() {
            @Override
            protected ScheduledExecutorService makeExecutor() {
                return new PseudoScheduledExecutorService(new TestTimeMulti());
            }
        };

        opPolicy = getExamplesPolicy(VFW_OPERATIONAL_DROOLS_POLICY_JSON, VFW_OP_POLICY_NAME);
        op2Policy = getExamplesPolicy(VCPE_OPERATIONAL_DROOLS_POLICY_JSON, VCPE_OP_POLICY_NAME);
        controllerPolicy =
            getPolicyFromFile(EXAMPLE_NATIVE_CONTROLLER_POLICY_JSON, EXAMPLE_NATIVE_CONTROLLER_POLICY_NAME);
        controller2Policy = getExamplesPolicy(USECASES_NATIVE_CONTROLLER_JSON, USECASES_NATIVE_CONTROLLER_POLICY_NAME);
        artifactPolicy =
            getPolicyFromFile(EXAMPLE_NATIVE_ARTIFACT_POLICY_JSON, EXAMPLE_NATIVE_ARTIFACT_POLICY_NAME);
        artifact2Policy = getExamplesPolicy(USECASES_NATIVE_ARTIFACT_JSON, USECASES_NATIVE_ARTIFACT_POLICY_NAME);
        valPolicy =
            getPolicyFromFile(EXAMPLE_OTHER_VAL_POLICY_JSON, EXAMPLE_OTHER_VAL_POLICY_NAME);
        unvalPolicy =
            getPolicyFromFile(EXAMPLE_OTHER_UNVAL_POLICY_JSON, EXAMPLE_OTHER_UNVAL_POLICY_NAME);

        fsm.resetPoliciesAction();
        resetExecutionStats();
    }

    @AfterClass
    public static void afterClass() {
        resetExecutionStats();
    }

    private static void resetExecutionStats() {
        PolicyEngineConstants.getManager().getStats().getGroupStat().setPolicyExecutedCount(0L);
        PolicyEngineConstants.getManager().getStats().getGroupStat().setPolicyExecutedFailCount(0L);
        PolicyEngineConstants.getManager().getStats().getGroupStat().setPolicyExecutedSuccessCount(0L);
    }

    private void setExecutionCounts() {
        PolicyEngineConstants.getManager().getStats().getGroupStat().setPolicyExecutedCount(7L);
        PolicyEngineConstants.getManager().getStats().getGroupStat().setPolicyExecutedFailCount(2L);
        PolicyEngineConstants.getManager().getStats().getGroupStat().setPolicyExecutedSuccessCount(5L);
    }

    @Test
    public void testGetDeployableActions() {
        List<ToscaPolicy> expectedDeployOrder =
            List.of(controllerPolicy, controller2Policy, artifact2Policy, artifactPolicy,
                op2Policy, opPolicy, unvalPolicy, valPolicy);

        assertEquals(expectedDeployOrder, fsm.getDeployablePoliciesAction(expectedDeployOrder));
        assertEquals(expectedDeployOrder,
            fsm.getDeployablePoliciesAction(
                List.of(op2Policy, artifact2Policy, valPolicy, opPolicy, unvalPolicy, artifactPolicy,
                    controllerPolicy, controller2Policy)));
        assertEquals(expectedDeployOrder,
                fsm.getDeployablePoliciesAction(
                    List.of(artifact2Policy, op2Policy, artifactPolicy, controllerPolicy, opPolicy,
                        controller2Policy, valPolicy, unvalPolicy)));
    }

    @Test
    public void testGetUndeployableActions() {
        deployAllPolicies();
        List<ToscaPolicy> expectedUndeployOrder =
                List.of(opPolicy, op2Policy, unvalPolicy, valPolicy, artifactPolicy,
                        artifact2Policy, controller2Policy, controllerPolicy);

        assertEquals(expectedUndeployOrder, fsm.getUndeployablePoliciesAction(Collections.emptyList()));
        assertEquals(expectedUndeployOrder, fsm.getUndeployablePoliciesAction(Collections.emptyList()));
        assertEquals(expectedUndeployOrder, fsm.getUndeployablePoliciesAction(Collections.emptyList()));
    }

    @Test
    public void testGetNativeArtifactPolicies() {
        deployAllPolicies();

        Map<String, List<ToscaPolicy>> deployedPolicies = fsm.groupPoliciesByPolicyType(fsm.getActivePolicies());
        assertEquals(2, fsm.getNativeArtifactPolicies(deployedPolicies).size());
        assertEquals(List.of(artifactPolicy, artifact2Policy), fsm.getNativeArtifactPolicies(deployedPolicies));
    }

    @Test
    public void testSetGroup() {
        fsm.setGroup("bar");
        assertEquals("bar", fsm.getGroup());
    }

    @Test
    public void testSetSubGroup() {
        fsm.setSubGroup("foo");
        assertEquals("foo", fsm.getSubGroup());
    }

    @Test
    public void testPdpType() {
        assertEquals("foo", fsm.getPdpType());
    }

    @Test
    public void testMergePolicies() {
        assertEquals(List.of(), fsm.getActivePolicies());
        assertEquals(List.of(), fsm.mergePolicies(List.of(), List.of()));

        fsm.deployedPolicyAction(opPolicy);
        fsm.deployedPolicyAction(controllerPolicy);
        assertEquals(Set.of(opPolicy, controllerPolicy), toSet(fsm.getActivePolicies()));
        assertEquals(Set.of(opPolicy, controllerPolicy), toSet(fsm.mergePolicies(List.of(), List.of())));
        assertEquals(Set.of(opPolicy), toSet(fsm.mergePolicies(List.of(), List.of(controllerPolicy.getIdentifier()))));

        assertEquals(Set.of(controllerPolicy, op2Policy, valPolicy, opPolicy, unvalPolicy),
                toSet(fsm.mergePolicies(List.of(op2Policy, valPolicy, unvalPolicy), List.of())));
        assertEquals(Set.of(controllerPolicy, op2Policy, valPolicy, opPolicy, unvalPolicy),
                toSet(fsm.mergePolicies(List.of(controllerPolicy, opPolicy, op2Policy, valPolicy, unvalPolicy),
                        List.of())));
        assertEquals(Set.of(op2Policy, valPolicy, unvalPolicy),
                toSet(fsm.mergePolicies(List.of(op2Policy, valPolicy, unvalPolicy),
                        List.of(controllerPolicy.getIdentifier(), opPolicy.getIdentifier()))));
    }

    private Set<ToscaPolicy> toSet(List<ToscaPolicy> policies) {
        return Set.copyOf(policies);

    }

    @Test
    public void testGetPolicyIdsMessages() {
        assertEquals("[operational.modifyconfig 1.0.0, example.controller 1.0.0]",
                fsm.getPolicyIds(List.of(opPolicy, controllerPolicy)).toString());
    }

    protected void deployAllPolicies() {
        fsm.deployedPolicyAction(controllerPolicy);
        fsm.deployedPolicyAction(controller2Policy);
        fsm.deployedPolicyAction(artifactPolicy);
        fsm.deployedPolicyAction(artifact2Policy);
        fsm.deployedPolicyAction(opPolicy);
        fsm.deployedPolicyAction(valPolicy);
        fsm.deployedPolicyAction(unvalPolicy);
        fsm.deployedPolicyAction(op2Policy);
    }

    protected ToscaPolicy getPolicyFromFile(String filePath, String policyName) throws CoderException, IOException {
        String policyJson = Files.readString(Paths.get(filePath));
        ToscaServiceTemplate serviceTemplate = coder.decode(policyJson, ToscaServiceTemplate.class);
        return serviceTemplate.getToscaTopologyTemplate().getPolicies().get(0).get(policyName);
    }

    protected ToscaPolicy getExamplesPolicy(String resourcePath, String policyName) throws CoderException {
        String policyJson = ResourceUtils.getResourceAsString(resourcePath);
        ToscaServiceTemplate serviceTemplate = new StandardCoder().decode(policyJson, ToscaServiceTemplate.class);
        return serviceTemplate.getToscaTopologyTemplate().getPolicies().get(0).get(policyName);
    }

}
