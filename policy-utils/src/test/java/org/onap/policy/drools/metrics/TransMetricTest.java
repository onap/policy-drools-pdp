/*
 * ============LICENSE_START=======================================================
 *  Copyright (C) 2021 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.metrics;

import static org.junit.Assert.*;

import com.openpojo.reflection.PojoClass;
import com.openpojo.reflection.impl.PojoClassFactory;
import com.openpojo.validation.Validator;
import com.openpojo.validation.ValidatorBuilder;
import com.openpojo.validation.rule.impl.GetterMustExistRule;
import com.openpojo.validation.rule.impl.SetterMustExistRule;
import com.openpojo.validation.test.impl.GetterTester;
import com.openpojo.validation.test.impl.SetterTester;
import org.junit.Test;

public class TransMetricTest {

    @Test
    public void testPojo() {
        PojoClass metric = PojoClassFactory.getPojoClass(TransMetric.class);
        Validator val = ValidatorBuilder
                                .create()
                                .with(new SetterMustExistRule())
                                .with(new GetterMustExistRule())
                                .with(new SetterTester())
                                .with(new GetterTester())
                                .build();
        val.validate(metric);
    }

    @Test
    public void testEqualsToString() {
        TransMetric trans1 = new TransMetric();
        TransMetric trans2 = new TransMetric();

        assertEquals(trans1, trans2);

        trans1.setRequestId(null);
        assertNotEquals(trans1, trans2);

        trans1.setRequestId("a");
        trans2.setRequestId("a");
        assertEquals(trans1, trans2);

        assertTrue(trans1.toString().startsWith("TransMetric"));
    }

}