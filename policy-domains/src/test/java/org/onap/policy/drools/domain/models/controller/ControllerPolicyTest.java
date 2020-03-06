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

package org.onap.policy.drools.domain.models.controller;

import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import org.junit.Test;
import org.onap.policy.drools.domain.models.Metadata;

public class ControllerPolicyTest {

    @Test
    public void testBuildDomainPolicyController() {
        /* manually create a controller policy */

        // @formatter:off
        assertNotNull(ControllerPolicy.builder()
            .metadata(Metadata.builder().policyId("policy-id").build())
            .name("example")
            .version("1.0.0")
            .type("onap.policies.drools.Controller")
            .typeVersion("1.0.0")
            .properties(ControllerProperties.builder().controllerName("example").sourceTopics(
                    new ArrayList<>()).sinkTopics(new ArrayList<>()).build())
            .build());
        // @formatter:on
    }

}