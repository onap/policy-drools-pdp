/*
 * ============LICENSE_START=======================================================
 * Copyright (C) 2019-2020 AT&T Intellectual Property. All rights reserved.
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.onap.policy.common.utils.coder.CoderException;
import org.onap.policy.common.utils.coder.StandardCoder;
import org.onap.policy.common.utils.resources.ResourceUtils;
import org.onap.policy.drools.domain.models.operational.OperationalPolicy;
import org.onap.policy.drools.system.PolicyControllerConstants;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicy;
import org.onap.policy.models.tosca.authorative.concepts.ToscaServiceTemplate;

/**
 * Drools Controller Policy Test.
 */
public class PolicyTypeDroolsControllerTest extends LifecycleStateRunningTest {

    // Operational vCPE Policies
    private static final String OP_POLICY_NAME_VCPE = "operational.restart";
    private static final String VCPE_OPERATIONAL_DROOLS_POLICY_JSON =
            "policies/vCPE.policy.operational.input.tosca.json";

    private ToscaPolicy policy;
    private OperationalPolicy operationalPolicy;
    private PolicyTypeDroolsController controller;

    /**
     * Test initialization.
     */
    @Before
    public void init() throws CoderException {
        fsm = makeFsmWithPseudoTime();
        policy = getExamplesPolicy(VCPE_OPERATIONAL_DROOLS_POLICY_JSON, OP_POLICY_NAME_VCPE);
        operationalPolicy = fsm.getDomainMaker().convertTo(policy, OperationalPolicy.class);
        controller = new PolicyTypeDroolsController(fsm, PolicyTypeDroolsController.compliantType, controllerSupport.getController());

        assertTrue(controllerSupport.getController().getDrools().isBrained());
        assertFalse(controllerSupport.getController().isAlive());
        assertFalse(controllerSupport.getController().getDrools().isAlive());
        assertSame(controllerSupport.getController(), PolicyControllerConstants.getFactory().get("lifecycle"));

        /* start controller */
        assertTrue(controllerSupport.getController().start());

        assertTrue(controllerSupport.getController().isAlive());
        assertTrue(controllerSupport.getController().getDrools().isAlive());
        assertTrue(controllerSupport.getController().getDrools().isBrained());
        assertSame(controllerSupport.getController(), PolicyControllerConstants.getFactory().get("lifecycle"));
    }

    @Test
    public void testDeployUndeploy() {
        /* non-existing controller */
        assertFalse(controller.undeploy(policy));
        assertFalse(controller.deploy(policy));

        policy.getProperties().remove("controllerName");
        assertTrue(controller.deploy(policy));
        assertTrue(controller.undeploy(policy));
        assertFalse(controller.undeploy(policy));

        /* existing controller */
        policy.getProperties().put("controllerName", "lifecycle");
        assertTrue(controller.deploy(policy));
        assertTrue(controller.undeploy(policy));
        assertFalse(controller.undeploy(policy));
    }

    private ToscaPolicy getExamplesPolicy(String resourcePath, String policyName) throws CoderException {
        String policyJson = ResourceUtils.getResourceAsString(resourcePath);
        ToscaServiceTemplate serviceTemplate = new StandardCoder().decode(policyJson, ToscaServiceTemplate.class);
        return serviceTemplate.getToscaTopologyTemplate().getPolicies().get(0).get(policyName);
    }

}