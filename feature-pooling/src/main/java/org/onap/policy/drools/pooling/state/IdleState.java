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
import org.onap.policy.drools.pooling.message.Identification;
import org.onap.policy.drools.pooling.message.Leader;
import org.onap.policy.drools.pooling.message.Offline;
import org.onap.policy.drools.pooling.message.Query;

/**
 * Idle state, used when offline.
 */
public class IdleState extends State {

    public IdleState(PoolingManager mgr) {
        super(mgr);
    }

    @Override
    public void stop() {
        // do nothing - don't even send of "offline" message
    }

    /**
     * Discards the message.
     */
    @Override
    public State process(Heartbeat msg) {
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
     * Copies the assignments, but doesn't change states.
     */
    @Override
    public State process(Leader msg) {
        super.process(msg);
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

}
