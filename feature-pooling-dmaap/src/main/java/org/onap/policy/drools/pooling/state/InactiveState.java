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
import org.onap.policy.drools.pooling.message.Leader;
import org.onap.policy.drools.pooling.message.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The inactive state. In this state, we just wait a bit and then try to re-activate. In
 * the meantime, all messages are ignored.
 */
public class InactiveState extends State {

    private static final Logger logger = LoggerFactory.getLogger(InactiveState.class);

    /**
     * 
     * @param mgr
     */
    public InactiveState(PoolingManager mgr) {
        super(mgr);
    }

    @Override
    public void start() {
        super.start();
        schedule(getProperties().getReactivateMs(), this::goStart);
    }

    @Override
    public State process(Leader msg) {
        if (isValid(msg)) {
            logger.info("received Leader message from {} on topic {}", msg.getSource(), getTopic());
            return goActive(msg.getAssignments());
        }

        return null;
    }

    /**
     * Generates an Identification message and goes to the query state.
     */
    @Override
    public State process(Query msg) {
        logger.info("received Query message on topic {}", getTopic());
        publish(makeIdentification());
        return goQuery();
    }

    /**
     * Remains in this state, without resetting any timers.
     */
    @Override
    protected State goInactive() {
        return null;
    }

}
