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
import lombok.NonNull;
import org.onap.policy.common.capabilities.Startable;
import org.onap.policy.common.gson.annotation.GsonJsonIgnore;
import org.onap.policy.models.pdp.concepts.PdpStateChange;
import org.onap.policy.models.pdp.concepts.PdpUpdate;
import org.onap.policy.models.pdp.enums.PdpState;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicy;

/**
 * Lifecycle State.
 */
public abstract class LifecycleState implements Startable {

    @GsonJsonIgnore
    protected final LifecycleFsm fsm;

    /**
     * Constructor.
     */
    protected LifecycleState(@NonNull LifecycleFsm manager) {
        this.fsm = manager;
    }

    /**
     * change state.
     */
    public abstract boolean transitionToState(@NonNull LifecycleState newState);

    /**
     * current state.
     */
    public abstract PdpState state();

    /**
     * status event.
     */
    public abstract boolean status();

    /**
     *  update event.
     */
    public abstract boolean update(@NonNull PdpUpdate update);

    /**
     * state change event.
     */
    public abstract boolean stateChange(@NonNull PdpStateChange change);

    /**
     * update policies with the current list.
     */
    public abstract boolean updatePolicies(List<ToscaPolicy> toscaPolicies);
}