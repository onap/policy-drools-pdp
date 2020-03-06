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
import org.onap.policy.common.utils.coder.CoderException;
import org.onap.policy.drools.controller.DroolsControllerConstants;
import org.onap.policy.drools.domain.models.artifact.NativeArtifactPolicy;
import org.onap.policy.drools.protocol.configuration.DroolsConfiguration;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.drools.system.PolicyControllerConstants;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicy;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicyTypeIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PolicyTypeNativeArtifactController implements PolicyTypeController {
    private static final Logger logger = LoggerFactory.getLogger(PolicyTypeNativeArtifactController.class);

    @Getter
    protected final ToscaPolicyTypeIdentifier policyType;

    @GsonJsonIgnore
    @JsonIgnore
    protected final transient LifecycleFsm fsm;

    public PolicyTypeNativeArtifactController(LifecycleFsm fsm, ToscaPolicyTypeIdentifier policyType) {
        this.policyType = policyType;
        this.fsm = fsm;
    }

    @Override
    public boolean deploy(ToscaPolicy policy) {
        NativeArtifactPolicy nativePolicy;
        PolicyController controller;
        try {
            nativePolicy = fsm.getDomainMaker().convertTo(policy, NativeArtifactPolicy.class);
            controller =
                    PolicyControllerConstants.getFactory().get(nativePolicy.getProperties().getController().getName());
        } catch (CoderException e) {
            logger.warn("Invalid Policy: {}", policy);
            return false;
        }

        DroolsConfiguration newConfig =
                new DroolsConfiguration(
                        nativePolicy.getProperties().getRulesArtifact().getArtifactId(),
                        nativePolicy.getProperties().getRulesArtifact().getGroupId(),
                        nativePolicy.getProperties().getRulesArtifact().getVersion());

        PolicyControllerConstants.getFactory().patch(controller, newConfig);
        return true;
    }

    @Override
    public boolean undeploy(ToscaPolicy policy) {
        PolicyController controller;
        try {
            NativeArtifactPolicy nativePolicy = fsm.getDomainMaker().convertTo(policy, NativeArtifactPolicy.class);
            controller =
                    PolicyControllerConstants.getFactory().get(nativePolicy.getProperties().getController().getName());
        } catch (RuntimeException | CoderException e) {
            logger.warn("Invalid Policy: {}", policy);
            return false;
        }

        DroolsConfiguration noConfig =
                new DroolsConfiguration(
                        DroolsControllerConstants.NO_ARTIFACT_ID,
                        DroolsControllerConstants.NO_GROUP_ID,
                        DroolsControllerConstants.NO_VERSION);

        PolicyControllerConstants.getFactory().patch(controller, noConfig);
        return true;
    }
}
