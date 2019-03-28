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

import lombok.NonNull;
import org.onap.policy.models.pdp.enums.PdpState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Support class for default functionality for operational states.
 */
public abstract class LifecycleStateDefault extends LifecycleStateUnsupported {

    private static final Logger logger = LoggerFactory.getLogger(LifecycleState.class);

    public LifecycleStateDefault(LifecycleFsm manager) {
        super(manager);
    }


    @Override
    public boolean transitionToState(@NonNull LifecycleState newState) {
        logger.info("{}: state-change from {} to {}", this, state(), newState.state());

        synchronized (fsm) {
            if (state() == newState.state()) {
                return false;
            }

            fsm.transitionToAction(newState);
            return true;
        }
    }

    @Override
    public boolean start() {
        logger.warn("{}: start", this);
        return false;
    }

    @Override
    public boolean stop() {
        synchronized (fsm) {
            boolean success = fsm.statusAction(PdpState.TERMINATED);
            success = fsm.stopAction() && success;
            return transitionToState(new LifecycleStateTerminated(fsm)) && success;
        }
    }

    @Override
    public void shutdown() {
        synchronized (fsm) {
            stop();
            fsm.shutdownAction();
        }
    }

    @Override
    public boolean isAlive() {
        return true;
    }

    @Override
    public boolean status() {
        synchronized (fsm) {
            return fsm.statusAction(state());
        }
    }
}
