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

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.onap.policy.models.pdp.enums.PdpState;

/**
 * TEST State Junits.
 */
public class LifecycleStateSafeTest extends LifecycleStateUnsupportedTest {

    public LifecycleStateSafeTest() {
        super(new LifecycleStateSafe(new LifecycleFsm()));
    }

    @Override
    public LifecycleState create(LifecycleFsm fsm) {
        return new LifecycleStateSafe(fsm);
    }

    @Test
    public void constructor() {
        super.constructor();
        assertEquals(PdpState.SAFE, new LifecycleStateSafe(new LifecycleFsm()).state());
    }

    @Test
    public void state() {
        assertEquals(PdpState.SAFE, state.state());
    }
}