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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.onap.policy.common.gson.annotation.GsonJsonIgnore;
import org.onap.policy.common.utils.coder.CoderException;
import org.onap.policy.drools.domain.models.legacy.LegacyPolicy;
import org.onap.policy.drools.domain.models.operational.OperationalPolicy;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicy;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicyTypeIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Policy Type Drools Controller that delegates to a corresponding
 * PolicyController that supports this policy type.
 */

public class PolicyTypeDroolsController implements PolicyTypeController {
    protected static final ToscaPolicyTypeIdentifier legacyType =
        new ToscaPolicyTypeIdentifier("onap.policies.controlloop.Operational", "1.0.0");

    protected static final ToscaPolicyTypeIdentifier compliantType =
        new ToscaPolicyTypeIdentifier("onap.policies.controlloop.operational.common.Drools", "1.0.0");

    private static final Logger logger = LoggerFactory.getLogger(PolicyTypeController.class);

    protected final Map<String, PolicyController> controllers = new ConcurrentHashMap<>();

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
        this.controllers.put(controller.getName(), controller);
        this.fsm = fsm;
    }

    @Override
    public boolean deploy(ToscaPolicy policy) {
        return perform(policy, (PolicyController controller) -> controller.offer(policy));
    }

    @Override
    public boolean undeploy(ToscaPolicy policy) {
        return perform(policy, (PolicyController controller) -> controller.getDrools().delete(policy));
    }

    private boolean perform(ToscaPolicy policy, Function<PolicyController, Boolean> operation) {
        try {
            List<PolicyController> selected = selectControllers(policy);
            boolean success = true;
            for (PolicyController controller : selected) {
                try {
                    success = operation.apply(controller) && success;
                } catch (RuntimeException r) {
                    logger.warn("invalid offer to controller: {}", controller);
                    success = false;
                }
            }
            return success && !selected.isEmpty();
        } catch (CoderException e) {
            logger.warn("perform: invalid formatted policy: {}", policy, e);
            return false;
        }
    }

    private List<PolicyController> selectControllers(ToscaPolicy policy) throws CoderException {
        List<PolicyController> selected;
        if (legacyType.equals(policyType)) {
            selected = controllers(
                fsm.getDomainMaker().convertTo(policy, LegacyPolicy.class)
                    .getProperties()
                    .getControllerName());
        } else if (compliantType.equals(policyType)) {
            selected = controllers(
                fsm.getDomainMaker().convertTo(policy, OperationalPolicy.class)
                    .getProperties()
                    .getControllerName());
        } else {
            selected = List.copyOf(controllers.values());
        }
        return selected;
    }

    private List<PolicyController> controllers(String controllerName) {
        if (StringUtils.isBlank(controllerName)) {
            /* this policy applies to all controllers */
            return controllers();
        }

        if (!this.controllers.containsKey(controllerName)) {
            return List.of();
        }

        return List.of(this.controllers.get(controllerName));
    }

    /**
     * Get all controllers that support the policy type.
     */
    public List<PolicyController> controllers() {
        return List.copyOf(controllers.values());
    }
}
