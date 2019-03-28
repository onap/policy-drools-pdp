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
import org.onap.policy.common.capabilities.Startable;
import org.onap.policy.common.gson.annotation.GsonJsonIgnore;
import org.onap.policy.models.pdp.concepts.PdpStateChange;
import org.onap.policy.models.pdp.concepts.PdpUpdate;
import org.onap.policy.models.pdp.enums.PdpState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lifecycle State.
 */
public abstract class LifecycleState implements Startable {

    private static final Logger logger = LoggerFactory.getLogger(LifecycleState.class);

    @GsonJsonIgnore
    protected LifecycleFsm fsm;

    /**
     * Constructor.
     * @param manager Lifecycle Manager.
     */
    public LifecycleState(@NonNull LifecycleFsm manager) {
        this.fsm = manager;
    }

    /**
     * change state.
     * @param newState new state
     */
    public abstract boolean changeState(@NonNull LifecycleState newState);

    /**
     * current state.
     * @return state
     */
    public abstract PdpState state();

    /**
     * status event.
     */
    public abstract boolean status();

    /**
     *  update event.
     * @param update message
     */
    public abstract void update(@NonNull PdpUpdate update);

    /**
     * state change event .
     * @param change message
     */
    public abstract void stateChange(@NonNull PdpStateChange change);
}