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

import com.openpojo.reflection.PojoClass;
import com.openpojo.reflection.filters.FilterChain;
import com.openpojo.reflection.filters.FilterClassName;
import com.openpojo.reflection.filters.FilterNonConcrete;
import com.openpojo.reflection.impl.PojoClassFactory;
import com.openpojo.validation.Validator;
import com.openpojo.validation.ValidatorBuilder;
import com.openpojo.validation.test.impl.GetterTester;
import com.openpojo.validation.test.impl.SetterTester;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.junit.Test;

public class DroolsPolicyTest {

    @Data
    @SuperBuilder
    @NoArgsConstructor
    public static class DerivedDomainPolicy extends DroolsPolicy implements Serializable {
        private static final long serialVersionUID = -1027974819756498893L;
    }

    @Test
    public void testDerivedClass() {
        /* validate model pojos */
        Validator validator = ValidatorBuilder.create()
                                      .with(new SetterTester(), new GetterTester()).build();

        validator.validate(PojoClassFactory.getPojoClass(DerivedDomainPolicy.class));
    }

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
}