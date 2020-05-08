/*
 * ============LICENSE_START=======================================================
 * feature-server-pool
 * ================================================================================
 * Copyright (C) 2020 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.serverpool;

import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerPoolProperties {
    // 'Server' port listener
    public static final String SERVER_IP_ADDRESS = "server.pool.server.ipAddress";
    public static final String SERVER_PORT = "server.pool.server.port";
    public static final String SERVER_HTTPS = "server.pool.server.https";
    public static final String SERVER_SELF_SIGNED_CERTIFICATES =
        "server.pool.server.selfSignedCerts";

    // 'site' information
    public static final String SITE_IP_ADDRESS = "server.pool.server.site.ip";
    public static final String SITE_PORT = "server.pool.server.site.port";

    // the default is to listen to all IP addresses on the host
    public static final String DEFAULT_SERVER_IP_ADDRESS = "0.0.0.0";

    // the default is to dynamically select a port
    public static final int DEFAULT_SERVER_PORT = 0;

    // the default is to have HTTPS disabled
    public static final boolean DEFAULT_HTTPS = false;

    // the default is to not use self-signed certificates
    public static final boolean DEFAULT_SELF_SIGNED_CERTIFICATES = false;

    // list of remote server names to use in HTTP/HTTPS messages
    // (instead of host names)
    public static final String HOST_LIST = "server.pool.server.hostlist";

    // 'Server' timeouts
    public static final String SERVER_INITIAL_ALLOWED_GAP = "server.pool.server.allowedGap";
    public static final String SERVER_ADAPTIVE_GAP_ADJUST =
        "server.adaptiveGapAdjust";
    public static final String SERVER_CONNECT_TIMEOUT = "server.pool.server.connectTimeout";
    public static final String SERVER_READ_TIMEOUT = "server.pool.server.readTimeout";

    // at startup, initially allow 30 seconds between pings
    public static final long DEFAULT_SERVER_INITIAL_ALLOWED_GAP = 30000;

    // when doing the adaptive calculation of the allowed gap between pings,
    // adjust the time by adding 5 seconds (by default)
    public static final long DEFAULT_SERVER_ADAPTIVE_GAP_ADJUST = 5000;

    // the default is to allow 10 seconds for a TCP connect
    public static final long DEFAULT_SERVER_CONNECT_TIMEOUT = 10000;

    // the default is to allow 10 seconds for a TCP read response
    public static final long DEFAULT_SERVER_READ_TIMEOUT = 10000;

    // outgoing per-server thread pool parameters
    public static final String SERVER_THREADS_CORE_POOL_SIZE =
        "server.pool.server.threads.corePoolSize";
    public static final String SERVER_THREADS_MAXIMUM_POOL_SIZE =
        "server.pool.server.threads.maximumPoolSize";
    public static final String SERVER_THREADS_KEEP_ALIVE_TIME =
        "server.pool.server.threads.keepAliveTime";

    public static final int DEFAULT_SERVER_THREADS_CORE_POOL_SIZE = 5;
    public static final int DEFAULT_SERVER_THREADS_MAXIMUM_POOL_SIZE = 10;
    public static final long DEFAULT_SERVER_THREADS_KEEP_ALIVE_TIME = 5000;

    /*================*/
    /* Host Discovery */
    /*================*/

    public static final String DISCOVERY_SERVERS = "server.pool.discovery.servers";
    public static final String DISCOVERY_TOPIC = "server.pool.discovery.topic";

    // HTTP authentication
    public static final String DISCOVERY_USERNAME = "server.pool.discovery.username";
    public static final String DISCOVERY_PASSWORD = "server.pool.discovery.password";

    // Cambria authentication
    public static final String DISCOVERY_API_KEY = "server.pool.discovery.apiKey";
    public static final String DISCOVERY_API_SECRET = "server.pool.discovery.apiSecret";

    // timeouts
    public static final String DISCOVERY_FETCH_TIMEOUT =
        "server.pool.discovery.fetchTimeout";

    // this value is passed to the UEB/DMAAP server, and controls how long
    // a 'fetch' request will wait when there are no incoming messages
    public static final String DEFAULT_DISCOVERY_FETCH_TIMEOUT = "60000";

    // maximum message fetch limit
    public static final String DISCOVERY_FETCH_LIMIT = "server.pool.discovery.fetchLimit";

    // this value is passed to the UEB/DMAAP server, and controls how many
    // requests may be returned in a single fetch
    public static final String DEFAULT_DISCOVERY_FETCH_LIMIT = "100";

    // publisher thread cycle time
    public static final String DISCOVER_PUBLISHER_LOOP_CYCLE_TIME =
        "discovery.publisherLoopCycleTime";

    // default cycle time is 5 seconds
    public static final long DEFAULT_DISCOVER_PUBLISHER_LOOP_CYCLE_TIME = 5000;

    // encryption
    public static final String DISCOVERY_HTTPS = "server.pool.discovery.https";
    public static final String DISCOVERY_ALLOW_SELF_SIGNED_CERTIFICATES =
        "server.pool.discovery.selfSignedCertificates";

    /*============================*/
    /* Leader Election Parameters */
    /*============================*/

    public static final String LEADER_STABLE_IDLE_CYCLES =
        "server.pool.leader.stableIdleCycles";
    public static final String LEADER_STABLE_VOTING_CYCLES =
        "server.pool.leader.stableVotingCycles";

    // by default, wait for 5 cycles (seconds) of stability before voting starts
    public static final int DEFAULT_LEADER_STABLE_IDLE_CYCLES = 5;

    // by default, wait for 5 cycles of stability before declaring a winner
    public static final int DEFAULT_LEADER_STABLE_VOTING_CYCLES = 5;

    /*=====================*/
    /* MainLoop Parameters */
    /*=====================*/

    public static final String MAINLOOP_CYCLE = "server.pool.mainLoop.cycle";

    // by default, the main loop cycle is 1 second
    public static final long DEFAULT_MAINLOOP_CYCLE = 1000;

    /*=============================*/
    /* Bucket Migration Parameters */
    /*=============================*/

    // time-to-live controls how many hops a 'TargetLock' message can take
    public static final String BUCKET_TIME_TO_LIVE = "bucket.ttl";

    // bucket migration timeout when a server has been notified that it
    // is the new owner of the bucket
    public static final String BUCKET_CONFIRMED_TIMEOUT =
        "bucket.confirmed.timeout";

    // bucket migration timeout when a server has inferred that it may be
    // the new owner, but it hasn't yet been confirmed
    public static final String BUCKET_UNCONFIRMED_TIMEOUT =
        "bucket.unconfirmed.timeout";

    // timeout for operation run within a Drools session
    public static final String BUCKET_DROOLS_TIMEOUT =
        "bucket.drools.timeout";

    // when a new owner of a bucket has completed the takeover of the
    // bucket, but it hasn't yet been confirmed, there is an additional
    // grace period before leaving the 'NewOwner' state
    public static final String BUCKET_UNCONFIRMED_GRACE_PERIOD =
        "bucket.unconfirmed.graceperiod";

    // time-to-live = 5 hops
    public static final int DEFAULT_BUCKET_TIME_TO_LIVE = 5;

    // 30 seconds timeout if it has been confirmed that we are the new owner
    public static final long DEFAULT_BUCKET_CONFIRMED_TIMEOUT = 30000;

    // 10 seconds timeout if it has not been confirmed that we are the new owner
    public static final long DEFAULT_BUCKET_UNCONFIRMED_TIMEOUT = 10000;

    // 10 seconds timeout waiting for a drools operation to complete
    public static final long DEFAULT_BUCKET_DROOLS_TIMEOUT = 10000;

    // 10 seconds timeout waiting to be confirmed that we are the new owner
    public static final long DEFAULT_BUCKET_UNCONFIRMED_GRACE_PERIOD = 10000;

    /*=======================*/
    /* TargetLock Parameters */
    /*=======================*/

    // time-to-live controls how many hops a 'TargetLock' message can take
    public static final String LOCK_TIME_TO_LIVE = "lock.ttl";

    // how frequently should the audit run?
    public static final String LOCK_AUDIT_PERIOD = "lock.audit.period";

    // when the audit is rescheduled (e.g. due to a new server joining), this
    // is the initial grace period, to allow time for bucket assignments, etc.
    public static final String LOCK_AUDIT_GRACE_PERIOD =
        "lock.audit.gracePeriod";

    // there may be audit mismatches detected that are only due to the transient
    // nature of the lock state -- we check the mismatches on both sides after
    // this delay to see if we are still out-of-sync
    public static final String LOCK_AUDIT_RETRY_DELAY = "lock.audit.retryDelay";

    // time-to-live = 5 hops
    public static final int DEFAULT_LOCK_TIME_TO_LIVE = 5;

    // run the audit every 5 minutes
    public static final long DEFAULT_LOCK_AUDIT_PERIOD = 300000;

    // wait at least 60 seconds after an event before running the audit
    public static final long DEFAULT_LOCK_AUDIT_GRACE_PERIOD = 60000;

    // wait 5 seconds to see if the mismatches still exist
    public static final long DEFAULT_LOCK_AUDIT_RETRY_DELAY = 5000;

    /* ============================================================ */

    private static Logger logger =
        LoggerFactory.getLogger(ServerPoolProperties.class);

    // save initial set of properties
    private static Properties properties = new Properties();

    /**
     * Hide implicit public constructor.
     */
    private ServerPoolProperties() {
        // everything here is static -- no instances of this class are created
    }

    /**
     * Store the application properties values.
     *
     * @param properties the properties to save
     */
    public static void setProperties(Properties properties) {
        ServerPoolProperties.properties = properties;
    }

    /**
     * Return the properties used when starting this server.
     *
     * @return the properties used when starting this server.
     */
    public static Properties getProperties() {
        return properties;
    }

    /**
     * Convenience method to fetch a 'long' property.
     *
     * @param name the property name
     * @param defaultValue the value to use if the property is not defined,
     *     or has an illegal value
     * @return the property value
     */
    public static long getProperty(String name, long defaultValue) {
        long rval = defaultValue;
        String value = properties.getProperty(name);
        if (StringUtils.isNotBlank(value)) {
            // try to convert to a 'long' -- log a message in case of failure
            try {
                rval = Long.parseLong(value);
            } catch (NumberFormatException e) {
                logger.error("Property {}=\"{}\": illegal long -- "
                             + "using default of {}", name, value, defaultValue);
            }
        }
        return rval;
    }

    /**
     * Convenience method to fetch an 'int' property.
     *
     * @param name the property name
     * @param defaultValue the value to use if the property is not defined,
     *     or has an illegal value
     * @return the property value
     */
    public static int getProperty(String name, int defaultValue) {
        int rval = defaultValue;
        String value = properties.getProperty(name);
        if (StringUtils.isNotBlank(value)) {
            // try to convert to an 'int' -- log a message in case of failure
            try {
                rval = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                logger.error("Property {}=\"{}\": illegal int -- "
                             + "using default of {}", name, value, defaultValue);
            }
        }
        return rval;
    }

    /**
     * Convenience method to fetch a 'boolean' property.
     *
     * @param name the property name
     * @param defaultValue the value to use if the property is not defined,
     *     or has an illegal value
     * @return the property value
     */
    public static boolean getProperty(String name, boolean defaultValue) {
        boolean rval = defaultValue;
        String value = properties.getProperty(name);
        if (StringUtils.isNotBlank(value)) {
            // try to convert to an 'boolean' -- log a message in case of failure
            rval = Boolean.parseBoolean(value);
        }
        return rval;
    }

    /**
     * Convenience method to fetch a 'String' property
     * (provided for consistency with 'long' and 'int' versions).
     *
     * @param name the property name
     * @param defaultValue the value to use if the property is not defined,
     *     or has an illegal value
     * @return the property value
     */
    public static String getProperty(String name, String defaultValue) {
        String value = properties.getProperty(name);
        return (StringUtils.isNotBlank(value)) ? value : defaultValue;
    }
}
