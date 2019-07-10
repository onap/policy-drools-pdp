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

package org.onap.policy.drools.testtransaction;

import java.util.HashMap;
import java.util.Map;
import org.onap.policy.drools.system.PolicyController;

/**
 * Implementation of TestTransaction interface. Controls the registering/unregistering of
 * PolicyController objects and the management of their related TTControllerTask threads.
 */
public class TtImpl implements TestTransaction {

    protected final Map<String, TtControllerTask> controllers = new HashMap<>();

    @Override
    public synchronized void register(PolicyController controller) {
        TtControllerTask controllerTask = this.controllers.get(controller.getName());
        if (controllerTask != null && controllerTask.isAlive()) {
            return;
        }

        // continue : unregister, register operation

        controllerTask = makeControllerTask(controller);
        this.controllers.put(controller.getName(), controllerTask);
    }

    @Override
    public synchronized void unregister(PolicyController controller) {
        final TtControllerTask controllerTask = this.controllers.remove(controller.getName());
        if (controllerTask != null) {
            controllerTask.stop();
        }
    }

    // these may be overridden by junit tests

    protected TtControllerTask makeControllerTask(PolicyController controller) {
        return new TtControllerTask(controller);
    }
}
