/*
 * ============LICENSE_START=======================================================
 * Copyright (C) 2020-2021 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2024 Nordix Foundation.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.onap.policy.common.utils.coder.CoderException;
import org.onap.policy.drools.domain.models.operational.OperationalPolicy;
import org.onap.policy.drools.system.PolicyControllerConstants;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicy;

/**
 * Drools Controller Policy Test.
 */
class PolicyTypeDroolsControllerTest extends LifecycleStateRunningTest {

    // Operational vCPE Policies
    private static final String OP_POLICY_NAME_VCPE = "operational.restart";
    private static final String VCPE_OPERATIONAL_DROOLS_POLICY_JSON =
            "policies/vCPE.policy.operational.input.tosca.json";

    private ToscaPolicy policy;
    private PolicyTypeDroolsController controller;

    /**
     * Test initialization.
     */
    @BeforeEach
    public void init() throws CoderException {
        fsm = makeFsmWithPseudoTime();
        policy = getExamplesPolicy(VCPE_OPERATIONAL_DROOLS_POLICY_JSON, OP_POLICY_NAME_VCPE);
        fsm.getDomainMaker().convertTo(policy, OperationalPolicy.class);
        controller = new PolicyTypeDroolsController(
            PolicyTypeDroolsController.compliantType, fsm, controllerSupport.getController());

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
    void testDeployUndeploy() {
        /* non-existing controller */
        assertFalse(controller.undeploy(policy));
        assertFalse(controller.deploy(policy));
        assertFalse(controllerSupport.getController().getDrools().exists(policy));
        assertEquals(0, controllerSupport.getController().getDrools().factCount(ControllerSupport.SESSION_NAME));

        policy.getProperties().remove("controllerName");

        deploy();
        deploy();  // one more time

        undeploy();
        undeploy();  // one more time

        /* existing controller */
        policy.getProperties().put("controllerName", "lifecycle");

        deploy();
        deploy();  // one more time

        undeploy();
        undeploy();  // one more time
    }

    protected void undeploy() {
        assertTrue(controller.undeploy(policy));
        assertFalse(controllerSupport.getController().getDrools().exists(policy));
        assertEquals(0, controllerSupport.getController().getDrools().factCount(ControllerSupport.SESSION_NAME));
    }

    protected void deploy() {
        assertTrue(controller.deploy(policy));
        assertTrue(controllerSupport.getController().getDrools().exists(policy));
        assertEquals(1, controllerSupport.getController().getDrools().factCount(ControllerSupport.SESSION_NAME));
    }
}