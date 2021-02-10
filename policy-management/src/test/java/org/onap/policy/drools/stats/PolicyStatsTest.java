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

import org.junit.Test;
import org.onap.policy.drools.metrics.TransMetric;

public class PolicyStatsTest {

    @Test
    public void testStat() {
        TransMetric trans1 = createTrans();
        trans1.setSuccess(true);

        PolicyStats stats = new PolicyStats();
        stats.stat(trans1);

        assertEquals(1, stats.getPolicyExecutedCount());
        assertEquals(trans1.getStartTime().toEpochMilli(), stats.getLastStart());
        assertEquals((double) trans1.getElapsedTime(), stats.getAverageExecutionTime(), 0.0d);
        assertEquals(trans1.getEndTime().toEpochMilli(), stats.getLastExecutionTime());
        assertEquals(0, stats.getPolicyExecutedFailCount());
        assertEquals(1, stats.getPolicyExecutedSuccessCount());
        assertThat(stats.getBirthTime()).isGreaterThanOrEqualTo(trans1.getStartTime().toEpochMilli());

        TransMetric trans2 = createTrans();
        trans2.setSuccess(false);
        trans2.setEndTime(trans2.getStartTime().plusMillis(5));
        trans2.setElapsedTime(null);
        stats.stat(trans2);

        assertEquals(2, stats.getPolicyExecutedCount());
        assertEquals(trans2.getStartTime().toEpochMilli(), stats.getLastStart());
        assertEquals((5 + 1) / 2d, stats.getAverageExecutionTime(), 0.0d);
        assertEquals(trans2.getEndTime().toEpochMilli(), stats.getLastExecutionTime());
        assertEquals(1, stats.getPolicyExecutedFailCount());
        assertEquals(1, stats.getPolicyExecutedSuccessCount());
        assertThat(stats.getBirthTime()).isLessThanOrEqualTo(trans2.getStartTime().toEpochMilli());

        TransMetric trans3 = createTrans();
        trans3.setSuccess(false);
        trans3.setEndTime(trans3.getStartTime().plusMillis(9));
        trans3.setElapsedTime(null);
        stats.stat(trans3);

        assertEquals(3, stats.getPolicyExecutedCount());
        assertEquals(trans3.getStartTime().toEpochMilli(), stats.getLastStart());
        assertEquals((5 + 1 + 9) / 3d, stats.getAverageExecutionTime(), 0.0d);
        assertEquals(trans3.getEndTime().toEpochMilli(), stats.getLastExecutionTime());
        assertEquals(2, stats.getPolicyExecutedFailCount());
        assertEquals(1, stats.getPolicyExecutedSuccessCount());
        assertThat(stats.getBirthTime()).isLessThanOrEqualTo(trans2.getStartTime().toEpochMilli());
    }

    private TransMetric createTrans() {
        TransMetric trans = new TransMetric();
        trans.setStartTime(null);
        trans.setEndTime(trans.getStartTime().plusMillis(1));

        trans.setElapsedTime(null);
        assertEquals(1L, trans.getElapsedTime().longValue());
        return trans;
    }

}