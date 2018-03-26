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

import java.util.Properties;
import org.onap.policy.common.utils.properties.SpecPropertyConfiguration;
import org.onap.policy.common.utils.properties.exception.PropertyException;

/**
 * Properties used by the pooling feature, specific to a controller.
 */
public class PoolingProperties extends SpecPropertyConfiguration {

    /**
     * Feature properties all begin with this prefix.
     */
    public static final String PREFIX = "pooling.";

    /*
     * These properties REQUIRE a controller name, thus they use the "{$}" form.
     */
    public static final String POOLING_TOPIC = PREFIX + "{$}.topic";

    /*
     * These properties allow the controller name to be left out, thus they use
     * the "{prefix?suffix}" form.
     */
    public static final String FEATURE_ENABLED = PREFIX + "{?.}enabled";
    public static final String OFFLINE_LIMIT = PREFIX + "{?.}offline.queue.limit";
    public static final String OFFLINE_AGE_MS = PREFIX + "{?.}offline.queue.age.milliseconds";
    public static final String START_HEARTBEAT_MS = PREFIX + "{?.}start.heartbeat.milliseconds";
    public static final String REACTIVATE_MS = PREFIX + "{?.}reactivate.milliseconds";
    public static final String IDENTIFICATION_MS = PREFIX + "{?.}identification.milliseconds";
    public static final String ACTIVE_HEARTBEAT_MS = PREFIX + "{?.}active.heartbeat.milliseconds";
    public static final String INTER_HEARTBEAT_MS = PREFIX + "{?.}inter.heartbeat.milliseconds";

    /**
     * {@code True} if the feature is enabled for the controller, {@code false}
     * otherwise.
     */
    @Property(name = FEATURE_ENABLED, defaultValue = "false")
    private boolean enabled;

    /**
     * Additional properties, if {@link #enabled} is {@code true}, {@code null}
     * otherwise.
     */
    private AdditionalProperties additional;

    /**
     * Properties from which this was constructed.
     */
    private Properties source;

    /**
     * @param controllerName the name of the controller
     * @param props set of properties used to configure this
     * @throws PropertyException if an error occurs
     * 
     */
    public PoolingProperties(String controllerName, Properties props) throws PropertyException {
        super(controllerName, props);

        additional = (enabled ? new AdditionalProperties(controllerName, props) : null);
        source = props;
    }


    public boolean isEnabled() {
        return enabled;
    }

    public Properties getSource() {
        return source;
    }


    public String getPoolingTopic() {
        return additional.getPoolingTopic();
    }


    public int getOfflineLimit() {
        return additional.getOfflineLimit();
    }


    public long getOfflineAgeMs() {
        return additional.getOfflineAgeMs();
    }


    public long getStartHeartbeatMs() {
        return additional.getStartHeartbeatMs();
    }


    public long getReactivateMs() {
        return additional.getReactivateMs();
    }


    public long getIdentificationMs() {
        return additional.getIdentificationMs();
    }


    public long getActiveHeartbeatMs() {
        return additional.getActiveHeartbeatMs();
    }


    public long getInterHeartbeatMs() {
        return additional.getInterHeartbeatMs();
    }


    /**
     * Additional feature properties, defined if the feature is enabled for the
     * controller.
     */
    private static class AdditionalProperties extends SpecPropertyConfiguration {

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
         * Time, in milliseconds, to wait for this host's heart beat during the
         * start-up state.
         */
        @Property(name = START_HEARTBEAT_MS, defaultValue = "50000")
        private long startHeartbeatMs;

        /**
         * Time, in milliseconds, to wait before attempting to re-active this
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
         * the active state.
         */
        @Property(name = INTER_HEARTBEAT_MS, defaultValue = "15000")
        private long interHeartbeatMs;

        /**
         * 
         * @param controllerName the name of the controller
         * @param props set of properties used to configure this
         * @throws PropertyException if an error occurs
         */
        public AdditionalProperties(String controllerName, Properties props) throws PropertyException {
            super(controllerName, props);
        }

        public String getPoolingTopic() {
            return poolingTopic;
        }

        public int getOfflineLimit() {
            return offlineLimit;
        }

        public long getOfflineAgeMs() {
            return offlineAgeMs;
        }

        public long getStartHeartbeatMs() {
            return startHeartbeatMs;
        }

        public long getReactivateMs() {
            return reactivateMs;
        }

        public long getIdentificationMs() {
            return identificationMs;
        }

        public long getActiveHeartbeatMs() {
            return activeHeartbeatMs;
        }

        public long getInterHeartbeatMs() {
            return interHeartbeatMs;
        }
    }
}
