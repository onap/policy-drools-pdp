/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2019-2020 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2019 Bell Canada.
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
import java.util.function.BiPredicate;
import lombok.NonNull;
import org.onap.policy.models.pdp.concepts.PdpResponseDetails;
import org.onap.policy.models.pdp.concepts.PdpStateChange;
import org.onap.policy.models.pdp.concepts.PdpUpdate;
import org.onap.policy.models.pdp.enums.PdpResponseStatus;
import org.onap.policy.models.pdp.enums.PdpState;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicy;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicyTypeIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Support class for default functionality for running states.
 */
public abstract class LifecycleStateRunning extends LifecycleStateDefault {

    private static final Logger logger = LoggerFactory.getLogger(LifecycleStateRunning.class);

    protected LifecycleStateRunning(LifecycleFsm manager) {
        super(manager);
    }

    protected abstract boolean stateChangeToPassive(@NonNull PdpStateChange change);

    protected abstract boolean stateChangeToActive(@NonNull PdpStateChange change);

    protected abstract boolean deployPolicy(@NonNull PolicyTypeController controller, @NonNull ToscaPolicy policy);

    protected abstract boolean undeployPolicy(@NonNull PolicyTypeController controller, @NonNull ToscaPolicy policy);

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
            if (fsm.getPolicyTypesMap().isEmpty()) {
                return true;
            } else {
                return fsm.statusAction();
            }
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
                return stateChangeToPassive(change);
            }

            if (change.getState() == PdpState.ACTIVE) {
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
            if (update.getPdpHeartbeatIntervalMs() != null
                    && !fsm.setStatusIntervalAction(update.getPdpHeartbeatIntervalMs() / 1000)) {
                fsm.statusAction(response(update.getRequestId(), PdpResponseStatus.FAIL,
                    "invalid interval: " + update.getPdpHeartbeatIntervalMs() + " seconds"));
                return false;
            }

            fsm.setSubGroupAction(update.getPdpSubgroup());

            if (!updatePolicies(update.getPolicies())) {
                fsm.statusAction(response(update.getRequestId(), PdpResponseStatus.FAIL, "cannot process policies"));
                return false;
            }

            return fsm.statusAction(response(update.getRequestId(), PdpResponseStatus.SUCCESS, null));
        }
    }

    @Override
    public boolean updatePolicies(List<ToscaPolicy> policies) {
        if (policies == null) {
            return true;
        }

        // Note that PAP sends the list of all ACTIVE policies with every
        // UPDATE message.   First, we will undeploy all policies that are
        // running but are not present in this list.  This will include
        // policies that are overridden by a different version.   Second,
        // we will deploy those policies that are not installed but
        // included in this list.

        boolean success = undeployPolicies(policies);
        return deployPolicies(policies) && success;
    }

    protected boolean deployPolicies(List<ToscaPolicy> policies) {
        return syncPolicies(fsm.getDeployablePoliciesAction(policies), this::deployPolicy);
    }

    protected boolean undeployPolicies(List<ToscaPolicy> policies) {
        return syncPolicies(fsm.getUndeployablePoliciesAction(policies), this::undeployPolicy);
    }

    protected boolean syncPolicies(List<ToscaPolicy> policies,
                                   BiPredicate<PolicyTypeController, ToscaPolicy> sync) {
        boolean success = true;
        for (ToscaPolicy policy : policies) {
            ToscaPolicyTypeIdentifier policyType = policy.getTypeIdentifier();
            PolicyTypeController controller = fsm.getController(policyType);
            if (controller == null) {
                logger.warn("no controller found for {}", policyType);
                success = false;
                continue;
            }

            success = sync.test(controller, policy) && success;
        }

        return success;
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
}
