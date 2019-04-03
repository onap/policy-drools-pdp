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

import java.util.List;
import lombok.NonNull;
import org.onap.policy.models.pdp.concepts.PdpResponseDetails;
import org.onap.policy.models.pdp.concepts.PdpStateChange;
import org.onap.policy.models.pdp.concepts.PdpUpdate;
import org.onap.policy.models.pdp.enums.PdpResponseStatus;
import org.onap.policy.models.pdp.enums.PdpState;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Support class for default functionality for running states.
 */
public abstract class LifecycleStateRunning extends LifecycleStateDefault {

    private static final Logger logger = LoggerFactory.getLogger(LifecycleState.class);

    protected abstract boolean stateChangeToPassive(PdpStateChange change);

    protected abstract boolean stateChangeToActive(PdpStateChange change);

    protected LifecycleStateRunning(LifecycleFsm manager) {
        super(manager);
    }

    @Override
    public boolean start() {
        logger.warn("{}: start", this);
        return false;
    }

    @Override
    public boolean stop() {
        synchronized (fsm) {
            boolean success = fsm.statusAction(PdpState.TERMINATED, null);
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
    public boolean status() {
        synchronized (fsm) {
            return fsm.statusAction();
        }
    }

    @Override
    public boolean isAlive() {
        return true;
    }

    @Override
    public boolean stateChange(@NonNull PdpStateChange change) {
        synchronized (fsm) {
            if (change.getState() == PdpState.PASSIVE) {
                group(change.getPdpGroup(), change.getPdpSubgroup());
                return stateChangeToPassive(change);
            }

            if (change.getState() == PdpState.ACTIVE) {
                group(change.getPdpGroup(), change.getPdpSubgroup());
                return stateChangeToActive(change);
            }

            logger.warn("{}: state-change: {}", this, change);

            invalidStateChange(change);
            return false;
        }
    }

    @Override
    public boolean update(@NonNull PdpUpdate update) {
        synchronized (fsm) {
            if (!fsm.setStatusIntervalAction(update.getPdpHeartbeatIntervalMs() / 1000)) {
                fsm.statusAction(response(update.getRequestId(), PdpResponseStatus.FAIL,
                    "invalid interval: " + update.getPdpHeartbeatIntervalMs() + " seconds"));
                return false;
            }

            group(update.getPdpGroup(), update.getPdpSubgroup());

            if (!updatePolicies(update.getPolicies())) {
                fsm.statusAction(response(update.getRequestId(), PdpResponseStatus.FAIL, "cannot process policies"));
                return false;
            }

            return fsm.statusAction(response(update.getRequestId(), PdpResponseStatus.SUCCESS, null));
        }
    }

    protected boolean updatePolicies(List<ToscaPolicy> policies) {
        // TODO
        return true;
    }

    private void invalidStateChange(PdpStateChange change) {
        logger.warn("{}: state-change: {}", this, change);
        fsm.statusAction(response(change.getRequestId(), PdpResponseStatus.FAIL,
                 "invalid state change to " + change.getState()));
    }

    protected PdpResponseDetails response(String requestId, PdpResponseStatus responseStatus, String message) {
        PdpResponseDetails response = new PdpResponseDetails();
        response.setResponseTo(requestId);
        response.setResponseStatus(responseStatus);
        if (message != null) {
            response.setResponseMessage(message);
        }

        return response;
    }

    protected void group(String group, String subgroup) {
        if (group == null || subgroup == null) {
            return;
        }

        fsm.setGroupAction(group, subgroup);
    }
}
