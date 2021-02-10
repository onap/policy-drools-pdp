/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2021 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.openpojo.reflection.PojoClass;
import com.openpojo.reflection.impl.PojoClassFactory;
import com.openpojo.validation.Validator;
import com.openpojo.validation.ValidatorBuilder;
import com.openpojo.validation.rule.impl.GetterMustExistRule;
import com.openpojo.validation.rule.impl.SetterMustExistRule;
import com.openpojo.validation.test.impl.GetterTester;
import com.openpojo.validation.test.impl.SetterTester;
import org.junit.Test;
import org.onap.policy.drools.metrics.TransMetric;

public class PolicyStatsManagerTest {

    @Test
    public void testPojo() {
        PojoClass metric = PojoClassFactory.getPojoClass(PolicyStatsManager.class);
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
        PolicyStatsManager stats1 = new PolicyStatsManager();
        PolicyStatsManager stats2 = new PolicyStatsManager();
        assertEquals(stats1, stats2);

        stats1.stat("foo", new TransMetric());
        assertNotEquals(stats1, stats2);

        assertThat(stats1.toString()).startsWith("PolicyStatsManager");
    }

    @Test
    public void testStat() {
        PolicyStatsManager stats = new PolicyStatsManager();
        assertEquals(0, stats.getGroupStat().policyExecutedCount);

        TransMetric trans = new TransMetric();
        stats.stat("foo", trans);
        stats.stat("blah", trans);
        stats.stat("blah", trans);
        assertEquals(3, stats.getGroupStat().policyExecutedCount);

        assertEquals(1, stats.getSubgroupStats().get("foo").getPolicyExecutedFailCount());
        assertEquals(2, stats.getSubgroupStats().get("blah").getPolicyExecutedFailCount());
    }
}