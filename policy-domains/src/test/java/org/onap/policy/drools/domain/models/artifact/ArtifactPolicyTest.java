/*
 * ============LICENSE_START=======================================================
 *  Copyright (C) 2020 AT&T Intellectual Property. All rights reserved.
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
 * ============LICENSE_END=========================================================
 */

package org.onap.policy.drools.domain.models.artifact;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.onap.policy.drools.domain.models.Metadata;

class ArtifactPolicyTest {

    @Test
    void testBuildDomainPolicyNativeArtifact() {
        /* manually create a native drools policy */

        // @formatter:off
        assertNotNull(NativeArtifactPolicy.builder()
            .metadata(Metadata.builder().policyId("policy-id").build())
            .name("example")
            .type("onap.policies.native.drools.Artifact")
            .typeVersion("1.0.0")
            .version("1.0.0")
            .properties(
                NativeArtifactProperties.builder().controller(
                        NativeArtifactController.builder().name("example").build())
                        .rulesArtifact(
                                NativeArtifactRulesArtifact.builder().groupId("org.onap.policy.controlloop")
                                        .artifactId("example").version("example").build()).build())
            .build());
        // @formatter:on
    }

}