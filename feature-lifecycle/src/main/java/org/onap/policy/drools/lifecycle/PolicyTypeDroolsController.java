/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2020-2021 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2021 Nordix Foundation.
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import lombok.Getter;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.onap.policy.common.gson.annotation.GsonJsonIgnore;
import org.onap.policy.common.utils.coder.CoderException;
import org.onap.policy.drools.domain.models.operational.OperationalPolicy;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.models.tosca.authorative.concepts.ToscaConceptIdentifier;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Policy Type Drools Controller that delegates to a corresponding
 * PolicyController that supports this policy type.
 */

public class PolicyTypeDroolsController implements PolicyTypeController {

    protected static final ToscaConceptIdentifier compliantType =
        new ToscaConceptIdentifier("onap.policies.controlloop.operational.common.Drools", "1.0.0");

    private static final Logger logger = LoggerFactory.getLogger(PolicyTypeDroolsController.class);

    @Getter
    protected final Map<String, PolicyController> controllers = new ConcurrentHashMap<>();

    @Getter
    protected final ToscaConceptIdentifier policyType;

    @GsonJsonIgnore
    protected final LifecycleFsm fsm;

    /**
     * Creates a Policy Type Drools Controller.
     */
    public PolicyTypeDroolsController(
            LifecycleFsm fsm, ToscaConceptIdentifier policyType, PolicyController controller) {
        this.policyType = policyType;
        this.controllers.put(controller.getName(), controller);
        this.fsm = fsm;
    }

    @Override
    public boolean deploy(@NonNull ToscaPolicy policy) {
        return perform(policy, controller -> {
            if (!controller.getDrools().exists(policy)) {
                return controller.offer(policy);
            }

            logger.warn("policy {} is not deployed into {} as it already exists",
                    policy.getIdentifier(), controller.getName());

            // provided that the presence of the given policy is the
            // desired final state of the operation, return success
            return true;
        });
    }

    /**
     * Adds a controller to support this policy type.
     */
    public void add(@NonNull PolicyController controller) {
        if (!controller.getPolicyTypes().contains(this.policyType)) {
            throw new IllegalArgumentException(
                "controller " + controller.getName() + " does not support " + this.policyType);
        }
        controllers.put(controller.getName(), controller);
    }

    /**
     * Removes a controller from this policy type.
     */
    public void remove(@NonNull PolicyController controller) {
        controllers.remove(controller.getName());
    }

    @Override
    public boolean undeploy(@NonNull ToscaPolicy policy) {
        return perform(policy, (PolicyController controller) -> {
            if (controller.getDrools().exists(policy)) {
                return controller.getDrools().delete(policy);
            }
            logger.warn("policy {} is not undeployed from {} as it does not exist",
                    policy.getIdentifier(), controller.getName());

            // provided that the no presence of the policy is the
            // desired final state of the operation, return success
            return true;
        });
    }

    /**
     * Get all controllers that support the policy type.
     */
    public List<PolicyController> controllers() {
        return List.copyOf(controllers.values());
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

    private boolean perform(ToscaPolicy policy, Predicate<PolicyController> operation) {
        try {
            List<PolicyController> selected = selectControllers(policy);
            boolean success = true;
            for (PolicyController controller : selected) {
                success = modifyController(operation, controller) && success;
            }
            return success && !selected.isEmpty();
        } catch (CoderException e) {
            logger.warn("perform: invalid formatted policy: {}", policy, e);
            return false;
        }
    }

    private boolean modifyController(Predicate<PolicyController> operation, PolicyController controller) {
        try {
            return operation.test(controller);
        } catch (RuntimeException r) {
            logger.warn("invalid operation {} applied to controller: {}", operation, controller);
            return false;
        }
    }

    private List<PolicyController> selectControllers(ToscaPolicy policy) throws CoderException {
        if (compliantType.equals(policyType)) {
            return controllers(
                fsm.getDomainMaker().convertTo(policy, OperationalPolicy.class)
                    .getProperties()
                    .getControllerName());
        }

        return List.copyOf(controllers.values());
    }
}
