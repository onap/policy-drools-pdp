/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2020 AT&T Intellectual Property. All rights reserved.
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

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;

import org.onap.policy.common.gson.annotation.GsonJsonIgnore;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicy;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicyTypeIdentifier;

/**
 * Policy Type Drools Controller that delegates to a corresponding
 * PolicyController that supports this policy type.
 */

public class PolicyTypeDroolsController implements PolicyTypeController {

    @Getter
    protected final PolicyController controller;

    @Getter
    protected final ToscaPolicyTypeIdentifier policyType;

    @GsonJsonIgnore
    @JsonIgnore
    protected final transient LifecycleFsm fsm;

    /**
     * Creates a Policy Type Drools Controller.
     */
    public PolicyTypeDroolsController(
            LifecycleFsm fsm, ToscaPolicyTypeIdentifier policyType, PolicyController controller) {
        this.policyType = policyType;
        this.controller = controller;
        this.fsm = fsm;
    }

    @Override
    public boolean deploy(ToscaPolicy policy) {
        return fsm.getDomainMaker().isConformant(policy) && this.controller.offer(policy);
    }

    @Override
    public boolean undeploy(ToscaPolicy policy) {
        return controller.getDrools().delete(policy);
    }
}
