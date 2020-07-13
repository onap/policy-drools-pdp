/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2017-2020 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.features;

import java.util.Properties;
import org.onap.policy.common.endpoints.event.comm.Topic.CommInfrastructure;
import org.onap.policy.common.utils.services.OrderedService;
import org.onap.policy.drools.protocol.configuration.DroolsConfiguration;
import org.onap.policy.drools.system.PolicyController;

public interface PolicyControllerFeatureApi extends OrderedService {

    /**
     * called before creating a controller with name 'name' and
     * properties 'properties'.
     *
     * @param name name of the the controller
     * @param properties configuration properties
     *
     * @return a policy controller.   A take over of the creation operation
     *     is performed by returning a non-null policy controller.
     *     'null' indicates that no take over has taken place, and processing should
     *     continue to the next feature provider.
     */
    default PolicyController beforeCreate(String name, Properties properties) {
        return null;
    }

    /**
     * called after creating a controller with name 'name'.
     *
     * @param controller controller
     *
     * @return true if this feature intercepts and takes ownership
     *      of the operation preventing the invocation of
     *      lower priority features.   False, otherwise.
     */
    default boolean afterCreate(PolicyController controller) {
        return false;
    }

    /**
     * called before the actual instantiation of a Policy Controller.
     *
     * @param name name of the controller
     * @param properties configuration parameters in the form of properties
     *
     * @return a Policy Controller or 'null for no intercept.
     */
    default PolicyController beforeInstance(String name, Properties properties) {
        return null;
    }

    /**
     * called after the Policy Controller is instantiated.
     *
     * @param controller policy controller
     * @param properties configuration parameters in the form of properties
     *
     * @return true if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise.
     */
    default boolean afterInstance(PolicyController controller, Properties properties) {
        return false;
    }

    /**
     * intercept before the Policy Controller is started.
     *
     * @return true if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise.
     */
    default boolean beforeStart(PolicyController controller) {
        return false;
    }

    /**
     * intercept after the Policy Controller is started.
     *
     * @return true if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise.
     */
    default boolean afterStart(PolicyController controller) {
        return false;
    }

    /**
     * intercept before the Policy Controller is stopped.
     *
     * @return true if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise.
     */
    default boolean beforeStop(PolicyController controller) {
        return false;
    }

    /**
     * intercept after the Policy Controller is stopped.
     *
     * @return true if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise.d.
     */
    default boolean afterStop(PolicyController controller) {
        return false;
    }

    /**
     * intercept before the Policy Controller is patched.
     *
     * @return true if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise.
     */
    default boolean beforePatch(
        PolicyController controller, DroolsConfiguration oldConfiguration, DroolsConfiguration newConfiguration) {
        return false;
    }

    /**
     * intercept after the Policy Controller is patched.
     *
     * @return true if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise.
     */
    default boolean afterPatch(
        PolicyController controller, DroolsConfiguration oldConfiguration,
        DroolsConfiguration newConfiguration, boolean success) {
        return false;
    }

    /**
     * intercept before the Policy Controller is locked.
     *
     * @return true if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise.
     */
    default boolean beforeLock(PolicyController controller) {
        return false;
    }

    /**
     * intercept after the Policy Controller is locked.
     *
     * @return true if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise..
     */
    default boolean afterLock(PolicyController controller) {
        return false;
    }

    /**
     * intercept before the Policy Controller is locked.
     *
     * @return true if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise.
     */
    default boolean beforeUnlock(PolicyController controller) {
        return false;
    }

    /**
     * intercept after the Policy Controller is locked.
     *
     * @return true if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise.
     */
    default boolean afterUnlock(PolicyController controller) {
        return false;
    }

    /**
     * intercept before the Policy Controller is shut down.
     *
     * @return true if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise..
     */
    default boolean beforeShutdown(PolicyController controller) {
        return false;
    }

    /**
     * called after the Policy Controller is shut down.
     *
     * @return true if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise.
     */
    default boolean afterShutdown(PolicyController controller) {
        return false;
    }

    /**
     * intercept before the Policy Controller is halted.
     *
     * @return true if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise..
     */
    default boolean beforeHalt(PolicyController controller) {
        return false;
    }

    /**
     * called after the Policy Controller is halted.
     *
     * @return true if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise.
     */
    default boolean afterHalt(PolicyController controller) {
        return false;
    }


    /*
     * intercept before the Policy Controller is offered an event.
     *
     * @return true if this feature intercepts and takes ownership.
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise.
     */
    default boolean beforeOffer(PolicyController controller,
        CommInfrastructure protocol,
        String topic,
        String event) {
        return false;
    }

    /**
     * intercept before the Policy Controller delivers (posts) an event.
     *
     * @return true if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise.
     */
    default <T> boolean beforeOffer(PolicyController controller, T event) {
        return false;
    }

    /**
     * called after the Policy Controller processes an event offer.
     *
     * @return true if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise.
     */
    default boolean afterOffer(PolicyController controller,
        CommInfrastructure protocol,
        String topic,
        String event,
        boolean success) {
        return false;
    }

    /**
     * called after the Policy Controller delivers (posts) an event.
     *
     * @return true if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise.
     */
    default <T> boolean afterOffer(PolicyController controller, T event, boolean success) {
        return false;
    }

    /**
     * intercept before the Policy Controller delivers (posts) an event.
     *
     * @return true if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise.
     */
    default boolean beforeDeliver(PolicyController controller,
        CommInfrastructure protocol,
        String topic,
        Object event) {
        return false;
    }

    /**
     * called after the Policy Controller delivers (posts) an event.
     *
     * @return true if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise.
     */
    default boolean afterDeliver(PolicyController controller,
        CommInfrastructure protocol,
        String topic,
        Object event,
        boolean success) {
        return false;
    }
}
