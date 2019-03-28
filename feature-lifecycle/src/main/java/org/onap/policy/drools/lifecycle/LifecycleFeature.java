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

import org.onap.policy.drools.features.DroolsControllerFeatureAPI;
import org.onap.policy.drools.features.PolicyControllerFeatureAPI;
import org.onap.policy.drools.features.PolicyEngineFeatureAPI;
import org.onap.policy.drools.system.PolicyEngine;

/**
 * This class hooks the Lifecycle State Machine to the PDP-D.
 */
public class LifecycleFeature
    implements PolicyEngineFeatureAPI, DroolsControllerFeatureAPI, PolicyControllerFeatureAPI {
    /**
     * Lifecycle FSM.
     */
    protected static final LifecycleFsm fsm = new LifecycleFsm();

    @Override
    public int getSequenceNumber() {
        return 10;
    }

    /**
     * The 'afterStart' hook on the Policy Engine tell us when the engine is functional.
     */
    @Override
    public boolean afterStart(PolicyEngine engine) {
        fsm.start();
        return false;
    }

    /**
     * The 'afterStop' hook on the Policy Engine tell us when the engine is stopping.
     */
    @Override
    public boolean afterStop(PolicyEngine engine) {
        fsm.stop();
        return false;
    }

    /**
     * The 'beforeShutdown' hook on the Policy Engine tell us when the engine is going away.
     */
    @Override
    public boolean beforeShutdown(PolicyEngine engine) {
        fsm.shutdown();
        return false;
    }
}
