/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2019 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.lifecycle;

import lombok.ToString;
import org.onap.policy.models.pdp.concepts.PdpStateChange;
import org.onap.policy.models.pdp.enums.PdpState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lifecycle Terminated State.
 */

@ToString
public class LifecycleStateTerminated extends LifecycleStateDefault {

    private static final Logger logger = LoggerFactory.getLogger(LifecycleStateTerminated.class);

    protected LifecycleStateTerminated(LifecycleFsm manager) {
        super(manager);
    }

    @Override
    public PdpState state() {
        return PdpState.TERMINATED;
    }

    @Override
    public boolean start() {
        synchronized (fsm) {
            boolean success = fsm.startAction();
            if (success) {
                return transitionToState(new LifecycleStatePassive(fsm));
            }

            return false;
        }
    }

    @Override
    public boolean stop() {
        logger.warn("{}: stop", this);
        return true;
    }

    @Override
    public void shutdown() {
        logger.warn("{}: shutdown", this);
    }

    @Override
    public boolean isAlive() {
        return false;
    }

    @Override
    public boolean status() {
        logger.warn("{}: status", this);
        return false;
    }

    @Override
    public void stateChange(PdpStateChange change) {
        logger.warn("{}: state-change: {}", this, change);
        return;
    }
}
