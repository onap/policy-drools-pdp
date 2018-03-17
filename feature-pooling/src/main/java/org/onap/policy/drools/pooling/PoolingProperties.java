/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.pooling;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import org.onap.policy.common.utils.properties.PropertyConfiguration;
import org.onap.policy.common.utils.properties.exception.PropertyException;

/**
 * Properties used by the pooling feature.
 */
public class PoolingProperties extends PropertyConfiguration {

    /**
     * Feature properties all begin with this prefix.
     */
    public static final String PREFIX = "pooling.feature.";

    /**
     * Properties specific to a controller's pooling feature start with this
     * pattern. Group(1) is the controller name found in the feature, and
     * group(2) is the property suffix.
     */
    public static final Pattern CONTROLLER_PATTERN =
                    Pattern.compile(PREFIX.replaceAll(".", "[.]") + "controller.([^.])[.](.+)");

    // standard properties
    public static final String FEATURE_ENABLED = PREFIX + "enabled";
    public static final String POOLING_TOPIC = PREFIX + "topic";
    public static final String OFFLINE_LIMIT = PREFIX + "offline.queue.limit";
    public static final String OFFLINE_AGE_MS = PREFIX + "offline.queue.age.milliseconds";
    public static final String QUERY_HEARTBEAT_MS = PREFIX + "query.heartbeat.milliseconds";
    public static final String REACTIVATE_MS = PREFIX + "reactivate.milliseconds";
    public static final String IDENTIFICATION_MS = PREFIX + "identification.milliseconds";
    public static final String LEADER_MS = PREFIX + "leader.milliseconds";
    public static final String ACTIVE_HEARTBEAT_MS = PREFIX + "active.heartbeat.milliseconds";
    public static final String INTER_HEARTBEAT_MS = PREFIX + "inter.heartbeat.milliseconds";

    /**
     * Properties that should not be common across controllers. These should not
     * appear without a controller name.
     */
    public static final List<String> UNCOMMON_PROPERTIES = Collections.unmodifiableList(Arrays.asList(POOLING_TOPIC));

    /**
     * Properties required for each controller.
     */
    public static final List<String> REQUIRED_PROPERTIES =
                    Collections.unmodifiableList(Arrays.asList(FEATURE_ENABLED, POOLING_TOPIC));

    @Property(name = POOLING_TOPIC)
    private String poolingTopic;

    @Property(name = OFFLINE_LIMIT)
    private int offlineLimit;

    @Property(name = OFFLINE_AGE_MS, defaultValue = "1000")
    private int offlineAgeMs;

    @Property(name = QUERY_HEARTBEAT_MS, defaultValue = "50000")
    private int queryHeartbeatMs;

    @Property(name = REACTIVATE_MS, defaultValue = "50000")
    private int reactivateMs;

    @Property(name = IDENTIFICATION_MS, defaultValue = "50000")
    private int identificationMs;

    @Property(name = LEADER_MS, defaultValue = "70000")
    private int leaderMs;

    @Property(name = ACTIVE_HEARTBEAT_MS, defaultValue = "50000")
    private int activeHeartbeatMs;

    @Property(name = INTER_HEARTBEAT_MS, defaultValue = "15000")
    private int interHeartbeatMs;

    /**
     * @param props set of properties used to configure this
     * @throws PropertyException
     * 
     */
    public PoolingProperties(Properties props) throws PropertyException {
        super(props);
    }

    public String getPoolingTopic() {
        return poolingTopic;
    }

    public int getOfflineLimit() {
        return offlineLimit;
    }

    public int getOfflineAgeMs() {
        return offlineAgeMs;
    }

    public int getQueryHeartbeatMs() {
        return queryHeartbeatMs;
    }

    public int getReactivateMs() {
        return reactivateMs;
    }

    public int getIdentificationMs() {
        return identificationMs;
    }

    public int getLeaderMs() {
        return leaderMs;
    }

    public int getActiveHeartbeatMs() {
        return activeHeartbeatMs;
    }

    public int getInterHeartbeatMs() {
        return interHeartbeatMs;
    }

}
