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

import static org.onap.policy.drools.pooling.state.FilterUtils.MSG_CHANNEL;
import static org.onap.policy.drools.pooling.state.FilterUtils.MSG_TIMESTAMP;
import static org.onap.policy.drools.pooling.state.FilterUtils.makeAnd;
import static org.onap.policy.drools.pooling.state.FilterUtils.makeEquals;
import java.util.Map;
import org.onap.policy.drools.pooling.PoolingManager;
import org.onap.policy.drools.pooling.message.Heartbeat;
import org.onap.policy.drools.pooling.message.Identification;
import org.onap.policy.drools.pooling.message.Leader;
import org.onap.policy.drools.pooling.message.Offline;
import org.onap.policy.drools.pooling.message.Query;

/**
 * The start state. Upon entry, a heart beat is generated and the event filter
 * is changed to look for just that particular message. Once the message is
 * seen, it goes into the {@link QueryState}.
 */
public class StartState extends State {

    /**
     * Time stamp inserted into the heart beat message.
     */
    private long hbTimestampMs = System.currentTimeMillis();

    /**
     * 
     * @param mgr
     */
    public StartState(PoolingManager mgr) {
        super(mgr);
    }

    /**
     * 
     * @param oldState previous state
     */
    public StartState(State oldState) {
        super(oldState);
    }

    /**
     * 
     * @return the time stamp inserted into the heart beat message
     */
    public long getHbTimestampMs() {
        return hbTimestampMs;
    }

    @Override
    public void start() {

        super.start();

        publish(getHost(), makeHeartbeat(hbTimestampMs));

        schedule(getProperties().getStartHeartbeatMs(), xxx -> internalTopicFailed());
    }

    /**
     * Transitions to the query state if the heart beat originated from this
     * host and its time stamp matches.
     */
    @Override
    public State process(Heartbeat msg) {
        if (msg.getTimestampMs() == hbTimestampMs && getHost().equals(msg.getSource())) {
            // saw our own heart beat - transition to query state
            publish(makeQuery());

            // no assignments yet
            return goQuery(null);
        }

        return null;
    }

    /**
     * Discards the message.
     */
    @Override
    public State process(Identification msg) {
        return null;
    }

    /**
     * Discards the message.
     */
    @Override
    public State process(Leader msg) {
        return null;
    }

    /**
     * Discards the message.
     */
    @Override
    public State process(Offline msg) {
        return null;
    }

    /**
     * Discards the message.
     */
    @Override
    public State process(Query msg) {
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Object> getFilter() {
        // ignore everything except our most recent heart beat message
        return makeAnd(makeEquals(MSG_CHANNEL, getHost()), makeEquals(MSG_TIMESTAMP, String.valueOf(hbTimestampMs)));

    }

}
