/*-
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
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
 * ============LICENSE_END=========================================================
 */

package org.onap.policy.drools.domain.models.operational;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.common.utils.coder.CoderException;
import org.onap.policy.common.utils.coder.StandardCoder;
import org.onap.policy.common.utils.resources.ResourceUtils;
import org.onap.policy.drools.domain.models.Metadata;
import org.onap.policy.drools.policies.DomainMaker;
import org.onap.policy.models.tosca.authorative.concepts.ToscaConceptIdentifier;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicy;
import org.onap.policy.models.tosca.authorative.concepts.ToscaServiceTemplate;

public class OperationalPolicyTest {
    // Policy Types
    private static final String OPERATIONAL_DROOLS_POLICY_TYPE = "onap.policies.controlloop.operational.common.Drools";

    // Operational vCPE Policies
    private static final String OP_POLICY_NAME_VCPE = "operational.restart";
    private static final String VCPE_OPERATIONAL_DROOLS_POLICY_JSON =
                                "policies/vCPE.policy.operational.input.tosca.json";

    private DomainMaker domainMaker;
    private StandardCoder nonValCoder;

    @Before
    public void setUp() {
        domainMaker = new DomainMaker();
        nonValCoder = new StandardCoder();
    }

    @Test
    public void testToscaCompliantOperationalPolicyType() throws CoderException {
        String rawVcpeToscaPolicy = getExamplesPolicyString(VCPE_OPERATIONAL_DROOLS_POLICY_JSON, OP_POLICY_NAME_VCPE);

        // valid "known" policy type with implicit schema
        ToscaConceptIdentifier operationalCompliantType =
                new ToscaConceptIdentifier(OPERATIONAL_DROOLS_POLICY_TYPE, "1.0.0");
        assertTrue(domainMaker.isConformant(operationalCompliantType, rawVcpeToscaPolicy));
        assertNotNull(domainMaker.convertTo(operationalCompliantType, rawVcpeToscaPolicy, OperationalPolicy.class));
    }

    @Test
    public void testOperationalCompliantModel() {
        // @formatter:off
        OperationalPolicy policy =
                OperationalPolicy.builder()
                    .metadata(Metadata.builder().policyId(OP_POLICY_NAME_VCPE).build())
                    .name(OP_POLICY_NAME_VCPE)
                    .type(OPERATIONAL_DROOLS_POLICY_TYPE)
                    .typeVersion("1.0.0")
                    .version("1.0.0")
                    .properties(
                            OperationalProperties.builder()
                                    .id("ControlLoop-vCPE-48f0c2c3-a172-4192-9ae3-052274181b6e")
                                    .abatement(true)
                                    .trigger("unique-policy-id-1-restart")
                                    .operations(
                                            List.of(Operation.builder()
                                                    .id("unique-policy-id-1-restart")
                                                    .description("Restart the VM")
                                                    .timeout(60)
                                                    .retries(3)
                                                    .actorOperation(ActorOperation.builder()
                                                        .operation("Restart")
                                                        .actor("APPC")
                                                        .target(OperationalTarget.builder().targetType("VNF").build())
                                                        .build())
                                                    .build()))
                                    .controllerName("usecases")
                                    .build())
                    .build();
        // @formatter:on

        assertNotNull(policy);
    }

    private String getJsonFromResource(String resourcePath) {
        return ResourceUtils.getResourceAsString(resourcePath);
    }

    private ToscaPolicy getExamplesPolicy(String resourcePath, String policyName) throws CoderException {
        String policyJson = getJsonFromResource(resourcePath);
        ToscaServiceTemplate serviceTemplate = new StandardCoder().decode(policyJson, ToscaServiceTemplate.class);
        return serviceTemplate.getToscaTopologyTemplate().getPolicies().get(0).get(policyName);
    }

    private String getExamplesPolicyString(String resourcePath, String policyName) throws CoderException {
        return nonValCoder.encode(getExamplesPolicy(resourcePath, policyName));
    }
}
