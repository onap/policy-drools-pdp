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

import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.ToString;
import org.onap.policy.drools.metrics.TransMetric;

/**
 * Policy Stats Manager that manages PDP-D statistics.
 */

@NoArgsConstructor
@Data
@EqualsAndHashCode
@ToString
public class PolicyStatsManager {

    private final PolicyStats groupStat = new PolicyStats();
    private final Map<String, PolicyStats> subgroupStats = new HashMap<>();

    /**
     * stat a new transaction.
     */
    public synchronized void stat(@NonNull String subGroupName, @NonNull TransMetric transaction) {
        groupStat.stat(transaction);
        subgroupStats.computeIfAbsent(subGroupName, key -> new PolicyStats()).stat(transaction);
    }
}
