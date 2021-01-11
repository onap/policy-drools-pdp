/*
 * ============LICENSE_START=======================================================
 * Copyright (C) 2021 AT&T Intellectual Property. All rights reserved.
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
import java.util.concurrent.ScheduledExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.common.utils.coder.CoderException;
import org.onap.policy.common.utils.coder.StandardCoder;
import org.onap.policy.common.utils.resources.ResourceUtils;
import org.onap.policy.common.utils.time.PseudoScheduledExecutorService;
import org.onap.policy.common.utils.time.TestTimeMulti;
import org.onap.policy.drools.persistence.SystemPersistenceConstants;
import org.onap.policy.drools.utils.logging.LoggerUtil;
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
    public void init() throws CoderException, IOException {
        LoggerUtil.setLevel(LoggerUtil.ROOT_LOGGER, "INFO");
        LoggerUtil.setLevel("org.onap.policy.common.endpoints", "WARN");
        LoggerUtil.setLevel("org.onap.policy.drools", "INFO");
        SystemPersistenceConstants.getManager().setConfigurationDir("target/test-classes");

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
        fsm.deployedPolicyAction(controllerPolicy);
        fsm.deployedPolicyAction(controller2Policy);
        fsm.deployedPolicyAction(artifactPolicy);
        fsm.deployedPolicyAction(artifact2Policy);
        fsm.deployedPolicyAction(opPolicy);
        fsm.deployedPolicyAction(valPolicy);
        fsm.deployedPolicyAction(unvalPolicy);
        fsm.deployedPolicyAction(op2Policy);

        List<ToscaPolicy> expectedUndeployOrder =
                List.of(opPolicy, op2Policy, unvalPolicy, valPolicy, artifactPolicy,
                        artifact2Policy, controller2Policy, controllerPolicy);

        assertEquals(expectedUndeployOrder, fsm.getUndeployablePoliciesAction(Collections.EMPTY_LIST));
        assertEquals(expectedUndeployOrder, fsm.getUndeployablePoliciesAction(Collections.EMPTY_LIST));
        assertEquals(expectedUndeployOrder, fsm.getUndeployablePoliciesAction(Collections.EMPTY_LIST));
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