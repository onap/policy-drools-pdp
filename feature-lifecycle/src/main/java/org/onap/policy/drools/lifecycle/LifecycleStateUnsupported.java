/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2019, 2021 AT&T Intellectual Property. All rights reserved.
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
import org.onap.policy.models.pdp.concepts.PdpStateChange;
import org.onap.policy.models.pdp.concepts.PdpUpdate;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicy;

/**
 * Support class for those unsupported states not yet implemented.
 */
public abstract class LifecycleStateUnsupported extends LifecycleState {

    /**
     * Constructor.
     * @param manager Lifecycle Manager.
     */
    protected LifecycleStateUnsupported(LifecycleFsm manager) {
        super(manager);
    }

    @Override
    public boolean transitionToState(LifecycleState newState) {
        throw new UnsupportedOperationException("transitionToAction: " + this);
    }

    @Override
    public boolean start() {
        throw new UnsupportedOperationException("start: " + this);
    }

    @Override
    public boolean stop() {
        throw new UnsupportedOperationException("stop: " + this);
    }

    @Override
    public void shutdown() {
        throw new UnsupportedOperationException("shutdown: " + this);
    }

    @Override
    public boolean isAlive() {
        throw new UnsupportedOperationException("isAlive: " + this);
    }

    @Override
    public boolean status() {
        throw new UnsupportedOperationException("status: " + this);
    }

    @Override
    public boolean update(PdpUpdate update) {
        throw new UnsupportedOperationException("update: " + this);
    }

    @Override
    public boolean stateChange(PdpStateChange change) {
        throw new UnsupportedOperationException("stateChange: " + this);
    }

    @Override
    public boolean updatePolicies(List<ToscaPolicy> toscaPolicies) {
        throw new UnsupportedOperationException("updatePolicies: " + this);
    }
}
