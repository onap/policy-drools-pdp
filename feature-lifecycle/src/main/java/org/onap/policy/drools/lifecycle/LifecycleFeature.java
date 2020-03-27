/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2019-2020 AT&T Intellectual Property. All rights reserved.
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

import org.onap.policy.drools.features.DroolsControllerFeatureApi;
import org.onap.policy.drools.features.PolicyControllerFeatureApi;
import org.onap.policy.drools.features.PolicyEngineFeatureApi;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.drools.system.PolicyEngine;

/**
 * This class hooks the Lifecycle State Machine to the PDP-D.
 */
public class LifecycleFeature
    implements PolicyEngineFeatureApi, DroolsControllerFeatureApi, PolicyControllerFeatureApi {
    /**
     * Lifecycle FSM.
     */
    public static final LifecycleFsm fsm = new LifecycleFsm();

    @Override
    public int getSequenceNumber() {
        return 1;
    }

    @Override
    public boolean afterStart(PolicyEngine engine) {
        return fsmStart();
    }

    @Override
    public boolean afterStart(PolicyController controller) {
        return fsmStart(controller);
    }

    @Override
    public boolean afterPatch(PolicyController controller, boolean success) {
        return fsmPatch(controller);
    }

    @Override
    public boolean beforeStop(PolicyEngine engine) {
        return fsmStop();
    }

    @Override
    public boolean beforeStop(PolicyController controller) {
        return fsmStop(controller);
    }

    @Override
    public boolean beforeShutdown(PolicyEngine engine) {
        return fsmShutdown(engine);
    }

    @Override
    public boolean beforeHalt(PolicyController controller) {
        return fsmStop(controller);
    }

    @Override
    public boolean beforeLock(PolicyController controller) {
        return fsmStop(controller);
    }

    @Override
    public boolean afterUnlock(PolicyController controller) {
        return fsmStart(controller);
    }

    private boolean fsmStart() {
        fsm.start();
        return false;
    }

    private boolean fsmStart(PolicyController controller) {
        fsm.start(controller);
        return false;
    }

    private boolean fsmStop() {
        fsm.stop();
        return false;
    }

    private boolean fsmStop(PolicyController controller) {
        fsm.stop(controller);
        return false;
    }

    private boolean fsmPatch(PolicyController controller) {
        fsm.patch(controller);
        return false;
    }

    private boolean fsmShutdown(PolicyEngine engine) {
        fsm.shutdown();
        return false;
    }
}
