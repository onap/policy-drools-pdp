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

import static org.junit.Assert.assertNotNull;

import com.openpojo.reflection.PojoClass;
import com.openpojo.reflection.filters.FilterChain;
import com.openpojo.reflection.filters.FilterClassName;
import com.openpojo.reflection.filters.FilterNonConcrete;
import com.openpojo.reflection.impl.PojoClassFactory;
import com.openpojo.validation.Validator;
import com.openpojo.validation.ValidatorBuilder;
import com.openpojo.validation.test.impl.GetterTester;
import com.openpojo.validation.test.impl.SetterTester;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.onap.policy.drools.domain.models.controller.ControllerPolicy;
import org.onap.policy.drools.domain.models.controller.ControllerProperties;
import org.onap.policy.drools.domain.models.nativ.rules.NativeDroolsController;
import org.onap.policy.drools.domain.models.nativ.rules.NativeDroolsPolicy;
import org.onap.policy.drools.domain.models.nativ.rules.NativeDroolsProperties;
import org.onap.policy.drools.domain.models.nativ.rules.NativeDroolsRulesArtifact;

public class DroolsPolicyModelsTest {

    @Test
    public void testPackage() {
        /* validate model pojos */
        List<PojoClass> pojoClasses =
                PojoClassFactory
                        .getPojoClassesRecursively("org.onap.policy.drools.domain.models",
                            new FilterChain(new FilterNonConcrete(),
                                    new FilterClassName(DroolsPolicy.class.getName())));

        Validator validator = ValidatorBuilder.create()
                                      .with(new SetterTester(), new GetterTester()).build();
        validator.validate(pojoClasses);
    }

    @Test
    public void testBuildDomainPolicyNativeDrools() {
        /* manually create a native drools policy */
        assertNotNull(NativeDroolsPolicy.builder().metadata(Metadata.builder().policyId("policy-id").build())
            .name("example")
            .type("onap.policies.native.Drools")
            .typeVersion("1.0.0")
            .version("1.0.0")
            .properties(
                NativeDroolsProperties.builder().controller(
                        NativeDroolsController.builder().name("example").version("1.0.0").build())
                        .rulesArtifact(
                                NativeDroolsRulesArtifact.builder().groupId("org.onap.policy.controlloop")
                                        .artifactId("example").version("example").build()).build())
            .build());
    }

    @Test
    public void testBuildDomainPolicyController() {
        /* manually create a controller policy */
        assertNotNull(ControllerPolicy.builder().metadata(Metadata.builder().policyId("policy-id").build())
            .name("example")
            .version("1.0.0")
            .type("onap.policies.drools.Controller")
            .typeVersion("1.0.0")
            .properties(ControllerProperties.builder().controllerName("example").sourceTopics(
                    new ArrayList<>()).sinkTopics(new ArrayList<>()).build())
            .build());
    }

}