/*
 * ============LICENSE_START=======================================================
 *  Copyright (C) 2020 AT&T Intellectual Property. All rights reserved.
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
 * ============LICENSE_END=========================================================
 */

package org.onap.policy.drools.domain.models;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.worldturner.medeia.api.ValidationFailedException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.common.utils.coder.CoderException;
import org.onap.policy.common.utils.coder.StandardCoder;
import org.onap.policy.common.utils.resources.ResourceUtils;
import org.onap.policy.drools.domain.models.artifact.NativeArtifactController;
import org.onap.policy.drools.domain.models.artifact.NativeArtifactPolicy;
import org.onap.policy.drools.domain.models.artifact.NativeArtifactProperties;
import org.onap.policy.drools.domain.models.artifact.NativeArtifactRulesArtifact;
import org.onap.policy.drools.domain.models.controller.ControllerPolicy;
import org.onap.policy.drools.policies.DomainMaker;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicy;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicyTypeIdentifier;
import org.onap.policy.models.tosca.authorative.concepts.ToscaServiceTemplate;

public class DomainPolicyTypesTest {

    // Policy Types
    private static final String OPERATIONAL_DROOLS_POLICY_TYPE = "onap.policies.controlloop.operational.common.Drools";
    private static final String NATIVE_DROOLS_POLICY_TYPE = "onap.policies.native.drools.Artifact";

    // Operational vCPE Policy
    private static final String OP_POLICY_NAME_VCPE = "operational.restart";
    private static final String VCPE_OPERATIONAL_DROOLS_POLICY_JSON =
            "policies/vCPE.policy.operational.input.tosca.json";
    public static final String VCPE_OPERATIONAL_DROOLS_LEGACY_POLICY_JSON =
            "src/test/resources/tosca-policy-operational-restart.json";

    // Native Drools Policy
    private static final String EXAMPLE_NATIVE_DROOLS_POLICY_NAME = "example";
    private static final String EXAMPLE_NATIVE_DROOLS_POLICY_JSON =
            "src/test/resources/tosca-policy-native-artifact-example.json";

    // Controller Drools Policy
    private static final String EXAMPLE_CONTROLLER_DROOLS_POLICY_NAME = "example";
    private static final String EXAMPLE_CONTROLLER_DROOLS_POLICY_JSON =
            "src/test/resources/tosca-policy-native-controller-example.json";

    private DomainMaker domainMaker;
    private StandardCoder nonValCoder;

    @Before
    public void setUp() {
        domainMaker = new DomainMaker();
        nonValCoder = new StandardCoder();
    }

    @Test
    public void testToscaOperationalPolicyType() throws CoderException, IOException {
        String rawVcpeToscaPolicy = getExamplesPolicyString(VCPE_OPERATIONAL_DROOLS_POLICY_JSON, OP_POLICY_NAME_VCPE);
        String rawVcpeLegacyToscaPolicy = getJsonFromFile(VCPE_OPERATIONAL_DROOLS_LEGACY_POLICY_JSON);

        // valid "known" policy type with implicit schema
        assertTrue(domainMaker
            .isConformant(new ToscaPolicyTypeIdentifier(OPERATIONAL_DROOLS_POLICY_TYPE, "1.0.0"),
                rawVcpeToscaPolicy));

        // policy type without schema
        assertFalse(domainMaker
            .isConformant(new ToscaPolicyTypeIdentifier("blah.blah", "1.0.0"), rawVcpeToscaPolicy));

        // known policy type but invalid json (legacy).
        assertFalse(domainMaker
            .isConformant(new ToscaPolicyTypeIdentifier(OPERATIONAL_DROOLS_POLICY_TYPE, "1.0.0"),
                rawVcpeLegacyToscaPolicy));

        ToscaPolicy vcpeToscaPolicy = getExamplesPolicy(VCPE_OPERATIONAL_DROOLS_POLICY_JSON, OP_POLICY_NAME_VCPE);
        assertTrue(domainMaker.isConformant(vcpeToscaPolicy));
        assertTrue(domainMaker.conformance(vcpeToscaPolicy));

        // set an invalid value in the Tosca Policy (timeout less than minimum value).
        final int timeout = (int) vcpeToscaPolicy.getProperties().get("timeout");
        vcpeToscaPolicy.getProperties().put("timeout", 0);
        assertFalse(domainMaker.isConformant(vcpeToscaPolicy));
        assertThatThrownBy(() ->
                                   domainMaker.conformance(vcpeToscaPolicy))
                .isInstanceOf(ValidationFailedException.class)
                .hasMessageContaining("Value 0 is smaller than minimum 1");

        // put back the original timeout value in the Tosca Policy
        vcpeToscaPolicy.getProperties().put("timeout", timeout);
        assertTrue(domainMaker.isConformant(vcpeToscaPolicy));
        assertTrue(domainMaker.conformance(vcpeToscaPolicy));

        // remove required element
        final Object operations = vcpeToscaPolicy.getProperties().remove("operations");
        assertFalse(domainMaker.isConformant(vcpeToscaPolicy));
        assertThatThrownBy(() ->
                                   domainMaker.conformance(vcpeToscaPolicy))
                .isInstanceOf(ValidationFailedException.class)
                .hasMessageContaining("Required property operations is missing from object");

        // put back the original operations value in the Tosca Policy
        vcpeToscaPolicy.getProperties().put("operations", operations);
        assertTrue(domainMaker.isConformant(vcpeToscaPolicy));
        assertTrue(domainMaker.conformance(vcpeToscaPolicy));
    }

    @Test
    public void testToscaNativeDroolsPolicy() throws CoderException, IOException {
        String rawNativeDroolsPolicy =
            getPolicyFromFileString(EXAMPLE_NATIVE_DROOLS_POLICY_JSON, EXAMPLE_NATIVE_DROOLS_POLICY_NAME);
        ToscaPolicy toscaPolicy =
            getExamplesPolicy(EXAMPLE_NATIVE_DROOLS_POLICY_JSON, EXAMPLE_NATIVE_DROOLS_POLICY_NAME);

        ToscaPolicyTypeIdentifier policyTypeId =
            new ToscaPolicyTypeIdentifier(NATIVE_DROOLS_POLICY_TYPE, "1.0.0");
        domainMaker.isConformant(policyTypeId, rawNativeDroolsPolicy);

        assertTrue(domainMaker.isConformant(toscaPolicy));
        NativeArtifactPolicy domainDroolsPolicy = domainMaker.convertTo(toscaPolicy, NativeArtifactPolicy.class);
        assertEquals("org.onap.policy.drools.test", domainDroolsPolicy.getProperties().getRulesArtifact().getGroupId());
        assertEquals("lifecycle", domainDroolsPolicy.getProperties().getRulesArtifact().getArtifactId());
        assertEquals("1.0.0", domainDroolsPolicy.getProperties().getRulesArtifact().getVersion());

        String policyId = toscaPolicy.getMetadata().remove("policy-id");
        assertThatThrownBy(() -> domainMaker.convertTo(toscaPolicy, NativeArtifactPolicy.class))
                .isInstanceOf(CoderException.class).hasCauseInstanceOf(ValidationFailedException.class);

        toscaPolicy.getMetadata().put("policy-id", policyId);

        assertTrue(domainMaker.isDomainConformant(policyTypeId, domainDroolsPolicy));
        assertTrue(domainMaker.conformance(policyTypeId, domainDroolsPolicy));

        domainDroolsPolicy.setName("");
        assertFalse(domainMaker.isDomainConformant(policyTypeId, domainDroolsPolicy));
        assertThatThrownBy(() -> domainMaker.conformance(policyTypeId, domainDroolsPolicy))
                .isInstanceOf(ValidationFailedException.class)
                .hasMessageContaining("Pattern ^(.+)$ is not contained in text");

        // @formatter:off
        NativeArtifactPolicy domainDroolsPolicy2 =
                NativeArtifactPolicy.builder().metadata(Metadata.builder().policyId("policy-id").build())
            .name("example")
            .version("1.0.0").properties(
                NativeArtifactProperties.builder().controller(
                        NativeArtifactController.builder().name("example").build())
                        .rulesArtifact(
                                NativeArtifactRulesArtifact.builder().groupId("org.onap.policy.controlloop")
                                        .artifactId("example").version("example").build()).build())
            .type("onap.policies.native.drools.Artifact")
            .typeVersion("1.0.0").build();
        // @formatter:on

        assertTrue(domainMaker
            .isDomainConformant(
                    new ToscaPolicyTypeIdentifier(domainDroolsPolicy2.getType(), domainDroolsPolicy2.getTypeVersion()),
                    domainDroolsPolicy2));
    }

    @Test
    public void testToscaControllerPolicy() throws CoderException {
        ToscaPolicy toscaPolicy =
                getExamplesPolicy(EXAMPLE_CONTROLLER_DROOLS_POLICY_JSON, EXAMPLE_CONTROLLER_DROOLS_POLICY_NAME);

        assertTrue(domainMaker.isConformant(toscaPolicy));
        ControllerPolicy controllerPolicy = domainMaker.convertTo(toscaPolicy, ControllerPolicy.class);

        assertEquals("example", controllerPolicy.getName());
        assertEquals("1.0.0", controllerPolicy.getVersion());
        assertEquals("onap.policies.native.drools.Controller", controllerPolicy.getType());
        assertEquals("1.0.0", controllerPolicy.getTypeVersion());
        assertEquals("example", controllerPolicy.getMetadata().getPolicyId());
        assertEquals("lifecycle", controllerPolicy.getProperties().getControllerName());
        assertEquals("DCAE_TOPIC", controllerPolicy.getProperties().getSourceTopics().get(0).getTopicName());
        assertEquals("org.onap.policy.controlloop.CanonicalOnset",
            controllerPolicy.getProperties().getSourceTopics().get(0).getEvents().get(0).getEventClass());
        assertEquals("[?($.closedLoopEventStatus == 'ONSET')]",
                controllerPolicy.getProperties().getSourceTopics().get(0).getEvents().get(0).getEventFilter());
        assertEquals("org.onap.policy.controlloop.util.Serialization",
                controllerPolicy.getProperties().getSourceTopics().get(0).getEvents().get(0)
                        .getCustomSerialization().getCustomSerializerClass());
        assertEquals("gson",
                controllerPolicy.getProperties().getSourceTopics().get(0).getEvents().get(0)
                        .getCustomSerialization().getJsonParser());
        assertEquals("APPC-CL", controllerPolicy.getProperties().getSinkTopics().get(0).getTopicName());
        assertEquals("org.onap.policy.appc.Response",
                controllerPolicy.getProperties().getSinkTopics().get(0).getEvents().get(0).getEventClass());
        assertEquals("[?($.CommonHeader && $.Status)]",
                controllerPolicy.getProperties().getSinkTopics().get(0).getEvents().get(0).getEventFilter());
        assertEquals("org.onap.policy.appc.util.Serialization",
                controllerPolicy.getProperties().getSinkTopics().get(0).getEvents().get(0)
                        .getCustomSerialization().getCustomSerializerClass());
        assertEquals("gsonPretty",
                controllerPolicy.getProperties().getSinkTopics().get(0).getEvents().get(0)
                        .getCustomSerialization().getJsonParser());
        assertEquals("value1", controllerPolicy.getProperties().getCustomConfig().get("field1"));
    }

    private String getJsonFromFile(String filePath) throws IOException {
        return Files.readString(Paths.get(filePath));
    }

    private String getJsonFromResource(String resourcePath) {
        return ResourceUtils.getResourceAsString(resourcePath);
    }

    private String getPolicyFromFileString(String filePath, String policyName) throws CoderException, IOException {
        String policyJson = getJsonFromFile(filePath);
        ToscaServiceTemplate serviceTemplate = new StandardCoder().decode(policyJson, ToscaServiceTemplate.class);
        return nonValCoder.encode(serviceTemplate.getToscaTopologyTemplate().getPolicies().get(0).get(policyName));
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