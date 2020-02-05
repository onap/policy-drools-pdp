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

import java.util.Collections;
import lombok.NonNull;
import lombok.ToString;
import org.onap.policy.models.pdp.concepts.PdpStateChange;
import org.onap.policy.models.pdp.enums.PdpResponseStatus;
import org.onap.policy.models.pdp.enums.PdpState;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicy;

/**
 * Lifecycle Active State.
 */
@ToString
public class LifecycleStateActive extends LifecycleStateRunning {
    protected LifecycleStateActive(LifecycleFsm manager) {
        super(manager);
    }

    @Override
    public PdpState state() {
        return PdpState.ACTIVE;
    }

    @Override
    protected boolean stateChangeToActive(PdpStateChange change) {
        return fsm.statusAction(response(change.getRequestId(), PdpResponseStatus.SUCCESS, null));
    }

    @Override
    protected boolean stateChangeToPassive(PdpStateChange change) {
        undeployPolicies(Collections.emptyList());
        fsm.transitionToAction(new LifecycleStatePassive(fsm));
        return fsm.statusAction(response(change.getRequestId(), PdpResponseStatus.SUCCESS, null));
    }

    @Override
    protected boolean deployPolicy(@NonNull PolicyTypeController controller, @NonNull ToscaPolicy policy) {
        if (!controller.deploy(policy)) {
            return false;
        }

        fsm.deployedPolicyAction(policy);
        return true;
    }

    @Override
    protected boolean undeployPolicy(@NonNull PolicyTypeController controller, @NonNull ToscaPolicy policy) {
        controller.undeploy(policy);
        fsm.undeployedPolicyAction(policy);
        return true;
    }
}
