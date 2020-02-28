/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
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
 * ============LICENSE_END=========================================================
 */

package org.onap.policy.drools.lifecycle;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.common.utils.coder.CoderException;
import org.onap.policy.common.utils.coder.StandardCoder;
import org.onap.policy.drools.controller.DroolsControllerConstants;
import org.onap.policy.drools.controller.internal.MavenDroolsController;
import org.onap.policy.drools.controller.internal.NullDroolsController;
import org.onap.policy.drools.domain.models.nativ.rules.NativeDroolsPolicy;
import org.onap.policy.drools.system.PolicyControllerConstants;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicy;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicyTypeIdentifier;
import org.onap.policy.models.tosca.authorative.concepts.ToscaServiceTemplate;

/**
 * Rules Controller Test.
 */
public class PolicyTypeRulesControllerTest extends LifecycleStateRunningTest {
    private static final StandardCoder coder = new StandardCoder();

    // Native Drools Policy
    private static final String EXAMPLE_NATIVE_DROOLS_POLICY_NAME = "example";
    private static final String EXAMPLE_NATIVE_DROOLS_POLICY_JSON =
            "src/test/resources/example.policy.native.drools.tosca.json";

    private ToscaPolicy policy;
    private NativeDroolsPolicy nativePolicy;
    private PolicyTypeRulesController controller;

    /**
     * Test Set initialization.
     */
    @Before
    public void init() throws IOException, CoderException {
        fsm = makeFsmWithPseudoTime();
        policy = getPolicyFromFile(EXAMPLE_NATIVE_DROOLS_POLICY_JSON, EXAMPLE_NATIVE_DROOLS_POLICY_NAME);
        nativePolicy = fsm.getDomainMaker().convertTo(policy, NativeDroolsPolicy.class);
        controller =
                new PolicyTypeRulesController(fsm,
                        new ToscaPolicyTypeIdentifier("onap.policies.native.Drools", "1.0.0"));

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
        assertSame(controllerSupport.getController(),
                PolicyControllerConstants.getFactory().get(
                        nativePolicy.getProperties().getRulesArtifact().getGroupId(),
                        nativePolicy.getProperties().getRulesArtifact().getArtifactId()));
        assertEquals(controllerSupport.getController().getDrools().getGroupId(),
                nativePolicy.getProperties().getRulesArtifact().getGroupId());
        assertEquals(controllerSupport.getController().getDrools().getArtifactId(),
                nativePolicy.getProperties().getRulesArtifact().getArtifactId());
        assertEquals(controllerSupport.getController().getDrools().getVersion(),
                nativePolicy.getProperties().getRulesArtifact().getVersion());
    }

    @Test
    public void testUndeployDeploy() {
        undeploy();
        deploy();

        PolicyControllerConstants.getFactory().destroy("lifecycle");
        assertThatIllegalArgumentException().isThrownBy(() -> PolicyControllerConstants.getFactory().get("lifecycle"));
    }

    private void undeploy() {
        assertTrue(controller.undeploy(policy));
        assertUndeployed();

        /* idempotence */
        assertTrue(controller.undeploy(policy));
        assertUndeployed();
    }


    private void deploy() {
        assertTrue(controller.deploy(policy));
        assertDeployed();

        /* idempotence */
        assertTrue(controller.deploy(policy));
        assertDeployed();

        // TODO: test a point version upgrade
    }

    private void assertUndeployed() {
        assertFalse(controllerSupport.getController().getDrools().isBrained());
        assertFalse(controllerSupport.getController().getDrools().isAlive());
        assertTrue(controllerSupport.getController().isAlive());
        assertSame(controllerSupport.getController(), PolicyControllerConstants.getFactory().get("lifecycle"));
        assertThatIllegalArgumentException().isThrownBy(() -> PolicyControllerConstants.getFactory()
                                                                      .get(nativePolicy.getProperties()
                                                                                   .getRulesArtifact().getGroupId(),
                                                                              nativePolicy.getProperties()
                                                                                      .getRulesArtifact()
                                                                                      .getArtifactId()));
        assertTrue(controllerSupport.getController().getDrools() instanceof NullDroolsController);
        assertEquals(DroolsControllerConstants.NO_GROUP_ID, controllerSupport.getController().getDrools().getGroupId());
        assertEquals(DroolsControllerConstants.NO_ARTIFACT_ID,
                controllerSupport.getController().getDrools().getArtifactId());
        assertEquals(DroolsControllerConstants.NO_VERSION, controllerSupport.getController().getDrools().getVersion());
    }

    private void assertDeployed() {
        assertTrue(controllerSupport.getController().getDrools().isBrained());
        assertTrue(controllerSupport.getController().getDrools().isAlive());
        assertTrue(controllerSupport.getController().isAlive());
        assertSame(controllerSupport.getController(), PolicyControllerConstants.getFactory().get("lifecycle"));
        assertSame(controllerSupport.getController(),
                PolicyControllerConstants.getFactory().get(
                        nativePolicy.getProperties().getRulesArtifact().getGroupId(),
                        nativePolicy.getProperties().getRulesArtifact().getArtifactId()));
        assertTrue(controllerSupport.getController().getDrools() instanceof MavenDroolsController);
    }

    private ToscaPolicy getPolicyFromFile(String filePath, String policyName) throws CoderException, IOException {
        String policyJson = new String(Files.readAllBytes(Paths.get(filePath)));
        ToscaServiceTemplate serviceTemplate = coder.decode(policyJson, ToscaServiceTemplate.class);
        return serviceTemplate.getToscaTopologyTemplate().getPolicies().get(0).get(policyName);
    }

}