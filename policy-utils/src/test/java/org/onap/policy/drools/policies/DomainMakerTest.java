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

package org.onap.policy.drools.policies;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.worldturner.medeia.api.ValidationFailedException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.common.utils.coder.CoderException;
import org.onap.policy.common.utils.coder.StandardCoder;
import org.onap.policy.drools.models.domains.a.DomainAPolicy;
import org.onap.policy.drools.models.domains.a.Metadata;
import org.onap.policy.drools.models.domains.a.Nested;
import org.onap.policy.drools.models.domains.a.Properties;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicy;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicyType;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicyTypeIdentifier;

public class DomainMakerTest {

    private DomainMaker domainMaker;

    @Before
    public void setUp() throws Exception {
        domainMaker = new DomainMaker();
    }

    @Test
    public void testIsConformantString() throws IOException {
        ToscaPolicyTypeIdentifier policyTypeId =
            new ToscaPolicyTypeIdentifier("policy.type.A", "1.0.0");
        String rawJsonPolicyType =
            getJsonFromFile("src/test/resources/policyA.json");

        assertTrue(domainMaker.isConformant(policyTypeId, rawJsonPolicyType));

        policyTypeId.setVersion("2.0.0");
        assertFalse(domainMaker.isConformant(policyTypeId, rawJsonPolicyType));
    }

    @Test
    public void testIsConformantToscaPolicy() throws IOException, CoderException {
        ToscaPolicy policy = getToscaPolicy("src/test/resources/policyA.json");
        assertTrue(domainMaker.isConformant(policy));

        policy.setType("policy.type.Z");
        assertFalse(domainMaker.isConformant(policy));
    }

    @Test
    public void testIsDomainConformant() {
        ToscaPolicyTypeIdentifier policyTypeId =
                new ToscaPolicyTypeIdentifier("policy.type.A", "1.0.0");

        DomainAPolicy domainAPolicy = createDomainPolicy();

        assertTrue(domainMaker.isDomainConformant(policyTypeId, domainAPolicy));

        // integer exceeding max. value
        domainAPolicy.getProperties().getNested().setNested3(999);
        assertFalse(domainMaker.isDomainConformant(policyTypeId, domainAPolicy));
        domainAPolicy.getProperties().getNested().setNested3(33); // restore good valude

        // not registered schema for policy type
        policyTypeId.setVersion("2.0.0");
        assertFalse(domainMaker.isDomainConformant(policyTypeId, domainAPolicy));
    }


    @Test
    public void testConformance() throws IOException, CoderException {
        ToscaPolicy policy1 = getToscaPolicy("src/test/resources/policyA.json");
        assertTrue(domainMaker.conformance(policy1));

        policy1.getProperties().remove("nested");
        assertThatThrownBy(() -> domainMaker.conformance(policy1))
                .isInstanceOf(ValidationFailedException.class)
                .hasMessageContaining("Required property nested is missing from object");

        DomainAPolicy domainAPolicy = createDomainPolicy();
        assertTrue(domainMaker.conformance(policy1.getTypeIdentifier(), domainAPolicy));
        assertDomainPolicy(domainAPolicy);

        domainAPolicy.getProperties().getNested().setNested1("");
        assertThatThrownBy(() -> domainMaker.conformance(policy1.getTypeIdentifier(), domainAPolicy))
                .isInstanceOf(ValidationFailedException.class)
                .hasMessageContaining("Pattern ^(.+)$ is not contained in text");
    }

    @Test
    public void testRegisterValidator() throws IOException, CoderException {
        ToscaPolicyTypeIdentifier policyTypeId =
                new ToscaPolicyTypeIdentifier("policy.type.external", "9.9.9");

        assertTrue(domainMaker.registerValidator(policyTypeId,
            getJsonFromFile("src/test/resources/policy.type.external-9.9.9.schema.json")));

        ToscaPolicy policy = getToscaPolicy("src/test/resources/policyA.json");
        policy.setType("policy.type.external");
        policy.setTypeVersion("9.9.9");
        assertTrue(domainMaker.isConformant(policy));

        policy.setTypeVersion("1.0.0");
        assertFalse(domainMaker.isConformant(policy));
    }

    @Test
    public void testConvertToDomainPolicy() throws IOException, CoderException {
        DomainAPolicy domainAPolicy =
                domainMaker.convertTo(getToscaPolicy("src/test/resources/policyA.json"), DomainAPolicy.class);
        assertDomainPolicy(domainAPolicy);

        assertNotNull(domainMaker.convertTo(getToscaPolicy("src/test/resources/policyA-no-policy-type.json"),
                DomainAPolicy.class));
    }

    @Test
    public void testConvertToSchema() {
        assertThatThrownBy(() -> domainMaker
            .convertToSchema(new ToscaPolicyType()))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void testIsRegistered() {
        ToscaPolicyTypeIdentifier policyTypeId1 =
                new ToscaPolicyTypeIdentifier("policy.type.A", "1.0.0");
        assertTrue(domainMaker.isRegistered(policyTypeId1));

        ToscaPolicyTypeIdentifier policyTypeId2 =
                new ToscaPolicyTypeIdentifier("policy.type.external", "7.7.9");
        assertFalse(domainMaker.isRegistered(policyTypeId2));

    }

    private String getJsonFromFile(String filePath) throws IOException {
        return new String(Files.readAllBytes(Paths.get(filePath)));
    }

    private ToscaPolicy getToscaPolicy(String filePath) throws CoderException, IOException {
        String policyJson = getJsonFromFile(filePath);
        return new StandardCoder().decode(policyJson, ToscaPolicy.class);
    }

    private DomainAPolicy createDomainPolicy() {
        return DomainAPolicy.builder().metadata(Metadata.builder().policyId("A").build())
                       .name("A")
                       .version("1.0.0")
                       .type("policy.type.A")
                       .typeVersion("1.0.0")
                       .properties(Properties.builder()
                           .nested(Nested.builder().nested1("nested1").nested2(true).nested3(50).build())
                           .build()).build();
    }

    private void assertDomainPolicy(DomainAPolicy domainAPolicy) {
        assertEquals("A", domainAPolicy.getName());
        assertEquals("1.0.0", domainAPolicy.getVersion());
        assertEquals("1.0.0", domainAPolicy.getTypeVersion());
        assertEquals("policy.type.A", domainAPolicy.getType());
        assertEquals("A", domainAPolicy.getMetadata().getPolicyId());
        assertEquals("nested1", domainAPolicy.getProperties().getNested().getNested1());
        assertEquals(true, domainAPolicy.getProperties().getNested().isNested2());
        assertEquals(50, domainAPolicy.getProperties().getNested().getNested3());
    }
}