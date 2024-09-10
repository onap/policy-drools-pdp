/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2021 AT&T Intellectual Property. All rights reserved.
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
 * ============LICENSE_END=========================================================
 */

package org.onap.policy.drools.stats;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.onap.policy.drools.metrics.Metric;

class PolicyStatsManagerTest {

    @Test
    void testStat() {
        PolicyStatsManager stats = new PolicyStatsManager();
        assertEquals(0, stats.getGroupStat().getPolicyExecutedCount());

        Metric trans = new Metric();
        stats.stat("foo", trans);
        stats.stat("blah", trans);
        stats.stat("blah", trans);
        assertEquals(3, stats.getGroupStat().getPolicyExecutedCount());

        assertEquals(1, stats.getSubgroupStats().get("foo").getPolicyExecutedFailCount());
        assertEquals(2, stats.getSubgroupStats().get("blah").getPolicyExecutedFailCount());
    }

    @Test
    void test_Exceptions() {
        PolicyStatsManager stats = new PolicyStatsManager();
        assertThatThrownBy(() -> stats.stat("foo", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("transaction is marked non-null but is nul");

        Metric trans = new Metric();
        assertThatThrownBy(() -> stats.stat(null, trans))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("subGroupName is marked non-null but is null");
    }
}