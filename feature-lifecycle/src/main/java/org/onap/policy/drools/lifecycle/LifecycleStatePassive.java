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
 * Lifecycle Passive State.
 */

@ToString
public class LifecycleStatePassive extends LifecycleStateDefault {

    private static final Logger logger = LoggerFactory.getLogger(LifecycleStatePassive.class);

    /**
     * Constructor.
     * @param manager fsm
     */
    public LifecycleStatePassive(LifecycleFsm manager) {
        super(manager);
    }

    @Override
    public PdpState state() {
        return PdpState.PASSIVE;
    }

    @Override
    public void stateChange(PdpStateChange change) {
        synchronized (fsm) {
            if (change.getState() != PdpState.ACTIVE) {
                logger.warn("{}: state-change: {}", this, change);
                return;
            }

            fsm.setGroupAction(change.getPdpGroup(), change.getPdpSubgroup());
            fsm.transitionToAction(new LifecycleStateActive(fsm));
        }
    }
}
