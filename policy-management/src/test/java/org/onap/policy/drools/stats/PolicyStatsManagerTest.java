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

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.onap.policy.drools.metrics.Metric;

public class PolicyStatsManagerTest {

    @Test
    public void testStat() {
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
}