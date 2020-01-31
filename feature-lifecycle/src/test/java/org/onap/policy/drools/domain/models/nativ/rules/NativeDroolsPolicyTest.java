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

package org.onap.policy.drools.domain.models.nativ.rules;

import com.openpojo.validation.Validator;
import com.openpojo.validation.ValidatorBuilder;
import com.openpojo.validation.rule.impl.GetterMustExistRule;
import com.openpojo.validation.rule.impl.SetterMustExistRule;
import com.openpojo.validation.test.impl.GetterTester;
import com.openpojo.validation.test.impl.SetterTester;
import org.junit.Test;

public class NativeDroolsPolicyTest {

    @Test
    public void testPackage() {
        Validator validator =
                ValidatorBuilder.create()
                        .with(new SetterMustExistRule(), new GetterMustExistRule())
                        .with(new SetterTester(), new GetterTester()).build();
        validator.validate(this.getClass().getPackageName());
    }

    @Test
    public void testBuilders() {
        new NativeDroolsPolicy()
            .withMetadata(new Metadata().withPolicyId("policy-id"))
            .withName("example")
            .withVersion("1.0.0")
            .withProperties(new Properties()
                .withController(new Controller()
                    .withName("example").withVersion("1.0.0"))
                .withRulesArtifact(new RulesArtifact()
                    .withGroupId("org.onap.policy.controlloop")
                    .withArtifactId("example")
                    .withVersion("1.0.0")))
            .withType("onap.policies.native.Drools")
            .withTypeVersion("1.0.0");
    }


}