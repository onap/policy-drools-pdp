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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Properties;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.common.endpoints.event.comm.TopicEndpointManager;
import org.onap.policy.common.endpoints.properties.PolicyEndPointProperties;
import org.onap.policy.common.utils.coder.CoderException;
import org.onap.policy.drools.domain.models.controller.ControllerPolicy;
import org.onap.policy.drools.system.PolicyControllerConstants;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicy;

/**
 * Native Controller Policy Test.
 */
public class PolicyTypeNativeDroolsControllerTest extends LifecycleStateRunningTest {
    // Native Drools Policy
    private static final String EXAMPLE_NATIVE_DROOLS_POLICY_NAME = "example";
    private static final String EXAMPLE_NATIVE_DROOLS_POLICY_JSON =
            "src/test/resources/tosca-policy-native-controller-example.json";

    private ToscaPolicy policy;
    private ControllerPolicy controllerPolicy;
    private PolicyTypeNativeDroolsController controller;

    /**
     * Test initialization.
     */
    @Before
    public void init() throws IOException, CoderException {
        fsm = makeFsmWithPseudoTime();
        policy = getPolicyFromFile(EXAMPLE_NATIVE_DROOLS_POLICY_JSON, EXAMPLE_NATIVE_DROOLS_POLICY_NAME);
        controllerPolicy = fsm.getDomainMaker().convertTo(policy, ControllerPolicy.class);
        controller = new PolicyTypeNativeDroolsController(fsm, policy.getTypeIdentifier());

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
    public void testUndeployDeploy() {
        assertTrue(controller.undeploy(policy));
        assertThatIllegalArgumentException().isThrownBy(
            () -> PolicyControllerConstants.getFactory().get(controllerPolicy.getName()));

        assertFalse(controller.deploy(policy));

        Properties noopTopicProperties = new Properties();
        noopTopicProperties.put(PolicyEndPointProperties.PROPERTY_NOOP_SOURCE_TOPICS, "DCAE_TOPIC");
        noopTopicProperties.put(PolicyEndPointProperties.PROPERTY_NOOP_SINK_TOPICS, "APPC-CL");
        TopicEndpointManager.getManager().addTopics(noopTopicProperties);

        controller.deploy(policy);

        return;
    }
}