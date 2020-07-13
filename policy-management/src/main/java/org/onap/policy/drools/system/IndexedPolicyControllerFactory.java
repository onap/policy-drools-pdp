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

package org.onap.policy.drools.system;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.onap.policy.common.gson.annotation.GsonJsonIgnore;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.features.PolicyControllerFeatureApi;
import org.onap.policy.drools.features.PolicyControllerFeatureApiConstants;
import org.onap.policy.drools.protocol.configuration.DroolsConfiguration;
import org.onap.policy.drools.system.internal.AggregatedPolicyController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory of Policy Controllers indexed by the name of the Policy Controller.
 */
class IndexedPolicyControllerFactory implements PolicyControllerFactory {
    // get an instance of logger
    private static final Logger  logger = LoggerFactory.getLogger(IndexedPolicyControllerFactory.class);

    /**
     * Policy Controller Name Index.
     */
    private final Map<String, PolicyController> policyControllers =
            new HashMap<>();

    /**
     * Group/Artifact Ids Index.
     */
    private final Map<String, PolicyController> coordinates2Controller =
            new HashMap<>();

    /**
     * produces key for indexing controller names.
     *
     * @param groupId group id
     * @param artifactId artifact id
     * @return index key
     */
    private String toKey(String groupId, String artifactId) {
        return groupId + ":" + artifactId;
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public synchronized PolicyController build(String name, Properties properties) {

        if (this.policyControllers.containsKey(name)) {
            return this.policyControllers.get(name);
        }

        /* A PolicyController does not exist */

        PolicyController controller = newPolicyController(name, properties);

        String coordinates = toKey(controller.getDrools().getGroupId(),
                                   controller.getDrools().getArtifactId());

        this.policyControllers.put(name, controller);


        if (controller.getDrools().isBrained()) {
            this.coordinates2Controller.put(coordinates, controller);
        }

        return controller;
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public synchronized PolicyController patch(String name, DroolsConfiguration droolsConfig) {

        if (name == null || name.isEmpty() || !this.policyControllers.containsKey(name)) {
            throw makeArgEx(name);
        }

        PolicyController controller = this.get(name);

        if (controller == null) {
            logger.warn("A POLICY CONTROLLER of name {} does not exist for patch operation: {}", name, droolsConfig);

            throw new IllegalArgumentException("Not a valid controller of name " + name);
        }

        this.patch(controller, droolsConfig);

        logger.info("UPDATED drools configuration: {} on {}", droolsConfig, this);

        return controller;
    }


    /**
     * {@inheritDoc}.
     */
    @Override
    public synchronized void patch(PolicyController controller, DroolsConfiguration droolsConfig) {

        if (controller == null) {
            throw new IllegalArgumentException("Not a valid controller:  null");
        }

        if (droolsConfig == null) {
            throw new IllegalArgumentException("Invalid Drools Configuration");
        }

        if (controller.getDrools().isBrained()) {
            this.coordinates2Controller.remove(
                    toKey(controller.getDrools().getGroupId(), controller.getDrools().getArtifactId()));
        }

        if (!controller.updateDrools(droolsConfig)) {
            logger.warn("Cannot update drools configuration: {} on {}", droolsConfig, this);
            throw new IllegalArgumentException("Cannot update drools configuration Drools Configuration");
        }

        logger.info("UPDATED drools configuration: {} on {}", droolsConfig, this);
        String coordinates = toKey(controller.getDrools().getGroupId(), controller.getDrools().getArtifactId());

        if (controller.getDrools().isBrained()) {
            this.coordinates2Controller.put(coordinates, controller);
        }
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public void shutdown(String controllerName) {

        if (controllerName == null || controllerName.isEmpty()) {
            throw makeArgEx(controllerName);
        }

        synchronized (this) {
            if (!this.policyControllers.containsKey(controllerName)) {
                return;
            }

            PolicyController controller = this.policyControllers.get(controllerName);
            this.shutdown(controller);
        }
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public void shutdown(PolicyController controller) {
        this.unmanage(controller);
        controller.shutdown();
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public void shutdown() {
        List<PolicyController> controllers = this.inventory();
        for (PolicyController controller: controllers) {
            controller.shutdown();
        }

        synchronized (this) {
            this.policyControllers.clear();
            this.coordinates2Controller.clear();
        }
    }

    /**
     * unmanage the controller.
     *
     * @param controller controller
     * @throws IllegalArgumentException exception
     */
    private void unmanage(PolicyController controller) {
        PolicyController tempController = controller;
        if (tempController == null) {
            throw new IllegalArgumentException("Invalid Controller");
        }

        synchronized (this) {
            if (!this.policyControllers.containsKey(tempController.getName())) {
                return;
            }
            tempController = this.policyControllers.remove(tempController.getName());

            String coordinates = toKey(tempController.getDrools().getGroupId(),
                    tempController.getDrools().getArtifactId());
            this.coordinates2Controller.remove(coordinates);
        }
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public void destroy(String controllerName) {

        if (controllerName == null || controllerName.isEmpty()) {
            throw makeArgEx(controllerName);
        }

        synchronized (this) {
            if (!this.policyControllers.containsKey(controllerName)) {
                return;
            }

            PolicyController controller = this.policyControllers.get(controllerName);
            this.destroy(controller);
        }
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public void destroy(PolicyController controller) {
        this.unmanage(controller);
        controller.halt();
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public void destroy() {
        List<PolicyController> controllers = this.inventory();
        for (PolicyController controller: controllers) {
            controller.halt();
        }

        synchronized (this) {
            this.policyControllers.clear();
            this.coordinates2Controller.clear();
        }
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public PolicyController get(String name) {

        if (name == null || name.isEmpty()) {
            throw makeArgEx(name);
        }

        synchronized (this) {
            if (this.policyControllers.containsKey(name)) {
                return this.policyControllers.get(name);
            } else {
                throw makeArgEx(name);
            }
        }
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public PolicyController get(String groupId, String artifactId) {

        if (groupId == null || groupId.isEmpty()
            || artifactId == null || artifactId.isEmpty()) {
            throw new IllegalArgumentException("Invalid group/artifact ids");
        }

        synchronized (this) {
            String key = toKey(groupId, artifactId);
            if (this.coordinates2Controller.containsKey(key)) {
                return this.coordinates2Controller.get(key);
            } else {
                throw makeArgEx(key);
            }
        }
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public PolicyController get(DroolsController droolsController) {

        if (droolsController == null) {
            throw new IllegalArgumentException("No Drools Controller provided");
        }

        synchronized (this) {
            String key = toKey(droolsController.getGroupId(), droolsController.getArtifactId());
            if (this.coordinates2Controller.containsKey(key)) {
                return this.coordinates2Controller.get(key);
            } else {
                logger.error("Drools Controller not associated with Policy Controller {}:{}", droolsController, this);
                throw new IllegalStateException("Drools Controller not associated with Policy Controller "
                        + droolsController + ":" + this);
            }
        }
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public List<PolicyController> inventory() {
        return new ArrayList<>(this.policyControllers.values());
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public List<String> getFeatures() {
        List<String> features = new ArrayList<>();
        for (PolicyControllerFeatureApi feature : getProviders()) {
            features.add(feature.getName());
        }
        return features;
    }

    /**
     * {@inheritDoc}.
     */
    @JsonIgnore
    @GsonJsonIgnore
    @Override
    public List<PolicyControllerFeatureApi> getFeatureProviders() {
        return getProviders();
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public PolicyControllerFeatureApi getFeatureProvider(String featureName) {
        if (featureName == null || featureName.isEmpty()) {
            throw new IllegalArgumentException("A feature name must be provided");
        }

        for (PolicyControllerFeatureApi feature : getProviders()) {
            if (feature.getName().equals(featureName)) {
                return feature;
            }
        }

        throw new IllegalArgumentException("Invalid Feature Name: " + featureName);
    }

    private IllegalArgumentException makeArgEx(String argName) {
        return new IllegalArgumentException("Invalid " + argName);
    }

    // these methods can be overridden by junit tests

    protected PolicyController newPolicyController(String name, Properties properties) {
        PolicyController controller = null;
        for (PolicyControllerFeatureApi feature: getProviders()) {
            try {
                controller = feature.beforeInstance(name, properties);
                if (controller != null) {
                    logger.info("feature {} ({}) beforeInstance() has intercepted controller {}",
                            feature.getName(), feature.getSequenceNumber(), name);
                    break;
                }
            } catch (RuntimeException r) {
                logger.error("feature {} ({}) beforeInstance() of controller {} failed",
                        feature.getName(), feature.getSequenceNumber(), name, r);
            }
        }

        if (controller == null) {
            controller = new AggregatedPolicyController(name, properties);
        }

        for (PolicyControllerFeatureApi feature: getProviders()) {
            try {
                feature.afterInstance(controller, properties);
            } catch (RuntimeException r) {
                logger.error("feature {} ({}) afterInstance() of controller {} failed ",
                        feature.getName(), feature.getSequenceNumber(), name, r);
            }
        }

        return controller;
    }

    protected List<PolicyControllerFeatureApi> getProviders() {
        return PolicyControllerFeatureApiConstants.getProviders().getList();
    }
}
