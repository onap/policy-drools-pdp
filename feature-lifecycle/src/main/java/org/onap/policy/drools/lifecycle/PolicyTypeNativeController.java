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
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicy;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicyTypeIdentifier;

public class PolicyTypeNativeController implements PolicyTypeController {

    @Getter
    protected final ToscaPolicyTypeIdentifier policyType;

    @GsonJsonIgnore
    @JsonIgnore
    protected final LifecycleFsm fsm;

    public PolicyTypeNativeController(LifecycleFsm fsm, ToscaPolicyTypeIdentifier policyType) {
        this.policyType = policyType;
        this.fsm = fsm;
    }

    @Override
    public boolean deploy(ToscaPolicy policy) {
        // TODO
        return fsm.getDomainMaker().isConformant(policy);
    }

    @Override
    public boolean undeploy(ToscaPolicy policy) {
        // TODO
        return true;
    }
}
