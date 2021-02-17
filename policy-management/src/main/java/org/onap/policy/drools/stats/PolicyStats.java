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

import static org.slf4j.LoggerFactory.getLogger;

import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.ToString;
import org.onap.policy.drools.metrics.TransMetric;
import org.slf4j.Logger;

/**
 * Basic policy execution statistics.
 */

@Data
@NoArgsConstructor
@EqualsAndHashCode
@ToString
public class PolicyStats {

    private static final Logger logger = getLogger(PolicyStats.class);

    /**
     * Number of executed policy transactions.
     */
    private volatile long policyExecutedCount;

    /**
     * Number of successfully executed policy transactions.
     */
    private volatile long policyExecutedSuccessCount;

    /**
     * Number of failed executions of policy transactions.
     */
    private volatile long policyExecutedFailCount;

    /**
     * Last time the policy transaction was executed.
     */
    private volatile long lastExecutionTime;

    /**
     * Average execution time of a policy transaction.
     */
    private volatile double averageExecutionTime;

    /**
     * Total policy execution times.
     */
    private volatile double totalElapsedTime;

    /**
     * Uptime of the entity holding the stats.
     */
    private long birthTime = Instant.now().toEpochMilli();

    /**
     * Time last transaction was started.
     */
    private volatile long lastStart;

    /**
     * add a stat transaction record.
     */
    public synchronized void stat(@NonNull TransMetric trans) {
        policyExecutedCount++;
        if (trans.isSuccess()) {
            policyExecutedSuccessCount++;
        } else {
            policyExecutedFailCount++;
        }

        // make sure transaction has values that we care about

        if (trans.getStartTime() == null) {
            logger.warn("policy transaction contains no start time: {}", trans);
            trans.setStartTime(null);
        }

        if (trans.getEndTime() == null) {
            logger.warn("policy transaction contains no end time: {}", trans);
            trans.setEndTime(null);
        }

        if (trans.getElapsedTime() == null) {
            logger.warn("policy transaction contains no elapsed time: {}", trans);
            trans.setElapsedTime(null);
        }

        // compute after the preconditions are satisfied

        lastExecutionTime = trans.getEndTime().toEpochMilli();
        totalElapsedTime += trans.getElapsedTime();
        averageExecutionTime = totalElapsedTime / policyExecutedCount;
        lastStart = trans.getStartTime().toEpochMilli();
    }
}
