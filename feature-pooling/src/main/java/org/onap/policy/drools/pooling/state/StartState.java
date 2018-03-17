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

public class StartState extends State {

    /**
     * Amount of time, in milliseconds, to wait for a heart beat.
     */
    private static final long HEARTBEAT_MS = 0L;

    /**
     * Time stamp from our heart beat message.
     */
    private long hbTimestampMs;

    public StartState(PoolingManager mgr) {
        super(mgr);
        init();
    }

    public StartState(State oldState) {
        super(oldState);
        init();

    }

    private void init() {
        Heartbeat hb = makeHeartbeat();
        hbTimestampMs = hb.getTimestampMs();

        publish(getHost(), hb);

        schedule(HEARTBEAT_MS, xxx -> {
            return internalTopicFailed();
        });
    }

    /**
     * Transitions to the query state if the heart beat originated from this host and its time stamp matches.
     */
    @Override
    public QueryState process(Heartbeat msg) {
        String me = getHost();
        if (msg.getTimestampMs() == hbTimestampMs && me.equals(msg.getSource())) {
            // saw our own heart beat - transition to query state
            publish(makeQuery());
            return new QueryState(StartState.this, me, null);
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Object> getFilter() {
        // ignore everything except our most recent heart beat message
        return makeAnd(makeEquals(MSG_TIMESTAMP, String.valueOf(hbTimestampMs)), makeEquals(MSG_CHANNEL, getHost()));

    }

}
