/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018, 2021 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.pooling;

import java.util.Properties;
import lombok.Getter;
import lombok.Setter;
import org.onap.policy.common.utils.properties.BeanConfigurator;
import org.onap.policy.common.utils.properties.Property;
import org.onap.policy.common.utils.properties.SpecProperties;
import org.onap.policy.common.utils.properties.exception.PropertyException;

/**
 * Properties used by the pooling feature, specific to a controller.
 */
@Getter
@Setter
public class PoolingProperties {

    /**
     * The feature name, used to retrieve properties.
     */
    public static final String FEATURE_NAME = "feature-pooling-messages";

    /**
     * Feature properties all begin with this prefix.
     */
    public static final String PREFIX = "pooling.";

    public static final String FEATURE_ENABLED = PREFIX + "enabled";
    public static final String POOLING_TOPIC = PREFIX + "topic";
    public static final String OFFLINE_LIMIT = PREFIX + "offline.queue.limit";
    public static final String OFFLINE_AGE_MS = PREFIX + "offline.queue.age.milliseconds";
    public static final String OFFLINE_PUB_WAIT_MS = PREFIX + "offline.publish.wait.milliseconds";
    public static final String START_HEARTBEAT_MS = PREFIX + "start.heartbeat.milliseconds";
    public static final String REACTIVATE_MS = PREFIX + "reactivate.milliseconds";
    public static final String IDENTIFICATION_MS = PREFIX + "identification.milliseconds";
    public static final String ACTIVE_HEARTBEAT_MS = PREFIX + "active.heartbeat.milliseconds";
    public static final String INTER_HEARTBEAT_MS = PREFIX + "inter.heartbeat.milliseconds";

    /**
     * Type of item that the extractors will be extracting.
     */
    public static final String EXTRACTOR_TYPE = "requestId";

    /**
     * Prefix for extractor properties.
     */
    public static final String PROP_EXTRACTOR_PREFIX = "extractor." + EXTRACTOR_TYPE;

    /**
     * Properties from which this was constructed.
     */
    private Properties source;

    /**
     * Topic used for inter-host communication.
     */
    @Property(name = POOLING_TOPIC)
    private String poolingTopic;

    /**
     * Maximum number of events to retain in the queue while waiting for
     * buckets to be assigned.
     */
    @Property(name = OFFLINE_LIMIT, defaultValue = "1000")
    private int offlineLimit;

    /**
     * Maximum age, in milliseconds, of events to be retained in the queue.
     * Events older than this are discarded.
     */
    @Property(name = OFFLINE_AGE_MS, defaultValue = "60000")
    private long offlineAgeMs;

    /**
     * Time, in milliseconds, to wait for an "Offline" message to be published
     * to topic.
     */
    @Property(name = OFFLINE_PUB_WAIT_MS, defaultValue = "3000")
    private long offlinePubWaitMs;

    /**
     * Time, in milliseconds, to wait for this host's heart beat during the
     * start-up state.
     */
    @Property(name = START_HEARTBEAT_MS, defaultValue = "100000")
    private long startHeartbeatMs;

    /**
     * Time, in milliseconds, to wait before attempting to reactivate this
     * host when it has no bucket assignments.
     */
    @Property(name = REACTIVATE_MS, defaultValue = "50000")
    private long reactivateMs;

    /**
     * Time, in milliseconds, to wait for all Identification messages to
     * arrive during the query state.
     */
    @Property(name = IDENTIFICATION_MS, defaultValue = "50000")
    private long identificationMs;

    /**
     * Time, in milliseconds, to wait for heart beats from this host, or its
     * predecessor, during the active state.
     */
    @Property(name = ACTIVE_HEARTBEAT_MS, defaultValue = "50000")
    private long activeHeartbeatMs;

    /**
     * Time, in milliseconds, to wait between heart beat generations during
     * the active and start-up states.
     */
    @Property(name = INTER_HEARTBEAT_MS, defaultValue = "15000")
    private long interHeartbeatMs;

    /**
     * Constructor.
     *
     * @param controllerName the name of the controller
     * @param props set of properties used to configure this
     * @throws PropertyException if an error occurs
     *
     */
    public PoolingProperties(String controllerName, Properties props) throws PropertyException {
        source = props;

        new BeanConfigurator().configureFromProperties(this, new SpecProperties(PREFIX, controllerName, props));
    }
}
