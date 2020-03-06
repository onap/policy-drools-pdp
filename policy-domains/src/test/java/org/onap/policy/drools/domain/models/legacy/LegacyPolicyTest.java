/*-
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

package org.onap.policy.drools.domain.models.legacy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;
import org.onap.policy.common.utils.coder.CoderException;
import org.onap.policy.common.utils.coder.StandardCoder;
import org.onap.policy.drools.policies.DomainMaker;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicy;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicyTypeIdentifier;

public class LegacyPolicyTest {
    // Policy Types
    private static final String OPERATIONAL_LEGACY_POLICY_TYPE = "onap.policies.controlloop.Operational";

    // Operational vCPE Legacy Policy
    private static final String OP_POLICY_NAME_VCPE = "operational.restart";
    public static final String VCPE_OPERATIONAL_LEGACY_POLICY_JSON = "src/test/resources/tosca-legacy-vcpe.json";

    @Test
    public void testToscaLegacyOperationalPolicyType() throws IOException, CoderException {
        String rawVcpeToscaPolicy = getJsonFromFile(VCPE_OPERATIONAL_LEGACY_POLICY_JSON);

        ToscaPolicyTypeIdentifier legacyType =
            new ToscaPolicyTypeIdentifier(OPERATIONAL_LEGACY_POLICY_TYPE, "1.0.0");

        DomainMaker domainMaker = new DomainMaker();
        assertTrue(domainMaker .isConformant(legacyType, rawVcpeToscaPolicy));
        LegacyPolicy legacyPolicy = domainMaker.convertTo(legacyType, rawVcpeToscaPolicy, LegacyPolicy.class);

        ToscaPolicy policy = new StandardCoder().decode(rawVcpeToscaPolicy, ToscaPolicy.class);
        assertEquals(policy.getProperties().get("content").toString(), legacyPolicy.getProperties().getContent());
        assertEquals(policy.getProperties().get("controllerName").toString(),
                legacyPolicy.getProperties().getControllerName());
    }

    private String getJsonFromFile(String filePath) throws IOException {
        return Files.readString(Paths.get(filePath));
    }
}