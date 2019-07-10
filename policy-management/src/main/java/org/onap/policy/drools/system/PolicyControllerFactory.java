/*
 * ============LICENSE_START=======================================================
 * policy-management
 * ================================================================================
 * Copyright (C) 2017-2019 AT&T Intellectual Property. All rights reserved.
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

import java.util.List;
import java.util.Properties;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.features.PolicyControllerFeatureApi;
import org.onap.policy.drools.protocol.configuration.DroolsConfiguration;


/**
 * Policy Controller Factory to manage controller creation, destruction,
 * and retrieval for management interfaces.
 */
public interface PolicyControllerFactory {
    /**
     * Build a controller from a properties file.
     *
     * @param name the global name of this controller
     * @param properties input parameters in form of properties for controller
     *     initialization.
     *
     * @return a Policy Controller
     *
     * @throws IllegalArgumentException invalid values provided in properties
     */
    PolicyController build(String name, Properties properties);

    /**
     * patches (updates) a controller from a critical configuration update.
     *
     * @param name name
     * @param configController config controller
     *
     * @return a Policy Controller
     */
    PolicyController patch(String name, DroolsConfiguration configController);

    /**
     * rebuilds (updates) a controller from a configuration update.
     *
     * @param controller controller
     * @param configController config controller
     */
    void patch(PolicyController controller,
               DroolsConfiguration configController);

    /**
     * get PolicyController from DroolsController.
     *
     * @param droolsController drools controller
     * @return policy controller
     * @throws IllegalArgumentException exception
     * @throws IllegalStateException exception
     */
    PolicyController get(DroolsController droolsController);

    /**
     * gets the Policy Controller identified by its name.
     *
     * @param policyControllerName name of policy controller
     * @return policy controller object
     * @throws IllegalArgumentException exception
     * @throws IllegalStateException exception
     */
    PolicyController get(String policyControllerName);

    /**
     * gets the Policy Controller identified by group and artifact ids.
     *
     * @param groupId group id
     * @param artifactId artifact id
     * @return policy controller object
     * @throws IllegalArgumentException exception
     * @throws IllegalStateException exception
     */
    PolicyController get(String groupId, String artifactId);

    /**
     * Makes the Policy Controller identified by controllerName not operational, but
     * does not delete its associated data.
     *
     * @param controllerName  name of the policy controller
     * @throws IllegalArgumentException invalid arguments
     */
    void shutdown(String controllerName);

    /**
     * Makes the Policy Controller identified by controller not operational, but
     * does not delete its associated data.
     *
     * @param controller a Policy Controller
     * @throws IllegalArgumentException invalid arguments
     */
    void shutdown(PolicyController controller);

    /**
     * Releases all Policy Controllers from operation.
     */
    void shutdown();

    /**
     * Destroys this Policy Controller.
     *
     * @param controllerName  name of the policy controller
     * @throws IllegalArgumentException invalid arguments
     */
    void destroy(String controllerName);

    /**
     * Destroys this Policy Controller.
     *
     * @param controller a Policy Controller
     * @throws IllegalArgumentException invalid arguments
     */
    void destroy(PolicyController controller);

    /**
     * Releases all Policy Controller resources.
     */
    void destroy();

    /**
     * get features attached to the Policy Controllers.
     *
     * @return list of features
     */
    List<PolicyControllerFeatureApi> getFeatureProviders();

    /**
     * get named feature attached to the Policy Controllers.
     *
     * @return the feature
     */
    PolicyControllerFeatureApi getFeatureProvider(String featureName);

    /**
     * get features attached to the Policy Controllers.
     *
     * @return list of features
     */
    List<String> getFeatures();

    /**
     * returns the current inventory of Policy Controllers.
     *
     * @return a list of Policy Controllers
     */
    List<PolicyController> inventory();
}
