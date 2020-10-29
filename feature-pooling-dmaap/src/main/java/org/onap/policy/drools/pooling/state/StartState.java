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

package org.onap.policy.drools.pooling.state;

import org.onap.policy.drools.pooling.PoolingManager;
import org.onap.policy.drools.pooling.message.Heartbeat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The start state. Upon entry, a heart beat is generated and the event filter is changed
 * to look for just that particular message. Once the message is seen, it goes into the
 * {@link QueryState}.
 */
public class StartState extends State {

    private static final Logger logger = LoggerFactory.getLogger(StartState.class);

    /**
     * Time stamp inserted into the heart beat message.
     */
    private long hbTimestampMs = System.currentTimeMillis();

    /**
     * Constructor.
     *
     * @param mgr pooling manager
     */
    public StartState(PoolingManager mgr) {
        super(mgr);
    }

    /**
     * Get Heart beat time stamp in milliseconds.
     *
     * @return the time stamp inserted into the heart beat message
     */
    public long getHbTimestampMs() {
        return hbTimestampMs;
    }

    @Override
    public void start() {

        super.start();

        Heartbeat hb = makeHeartbeat(hbTimestampMs);
        publish(getHost(), hb);

        /*
         * heart beat generator
         */
        long genMs = getProperties().getInterHeartbeatMs();

        scheduleWithFixedDelay(genMs, genMs, () -> {
            publish(getHost(), hb);
            return null;
        });

        /*
         * my heart beat checker
         */
        schedule(getProperties().getStartHeartbeatMs(), () -> {
            logger.error("missed heartbeat on topic {}", getTopic());
            return internalTopicFailed();
        });
    }

    /**
     * Transitions to the query state if the heart beat originated from this host and its
     * time stamp matches.
     */
    @Override
    public State process(Heartbeat msg) {
        if (msg.getTimestampMs() == hbTimestampMs && getHost().equals(msg.getSource())) {
            // saw our own heart beat - transition to query state
            logger.info("saw our own heartbeat on topic {}", getTopic());
            publish(makeQuery());
            return goQuery();

        } else {
            logger.info("ignored old heartbeat message from {} on topic {}", msg.getSource(), getTopic());
        }

        return null;
    }
}
