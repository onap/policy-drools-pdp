/*-
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018-2020 AT&T Intellectual Property. All rights reserved.
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

import java.util.List;
import java.util.Properties;
import org.onap.policy.common.endpoints.event.comm.TopicSink;
import org.onap.policy.common.utils.services.OrderedService;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.protocol.coders.TopicCoderFilterConfiguration;

/**
 * Drools Controller Feature API.   Hooks into the Drools Controller operations.
 */
public interface DroolsControllerFeatureApi extends OrderedService {

    /**
     * intercepts the instantiation of a DroolsController.
     *
     * @param properties controller properties
     * @param groupId group id coordinate
     * @param artifactId artifact id coordinate
     * @param version version coordinate
     * @param decoderConfigurations decoder configurations
     * @param encoderConfigurations encoder configurations
     *
     * @return a Drools Controller or 'null' for no intercept
     */
    default DroolsController beforeInstance(Properties properties,
        String groupId, String artifactId, String version,
        List<TopicCoderFilterConfiguration> decoderConfigurations,
        List<TopicCoderFilterConfiguration> encoderConfigurations) {
        return null;
    }

    /**
     * called after a DroolsController is instantiated.
     *
     * @param droolsController drools controller
     * @param properties controller properties
     *
     * @return True if this feature intercepts and takes ownership of the operation
     *         preventing the invocation of lower priority features. False, otherwise
     */
    default boolean afterInstance(DroolsController droolsController, Properties properties) {
        return false;
    }

    /**
     * intercepts before the Drools Controller gives the Policy Container a fact to
     * insert into its Policy Sessions.
     *
     * @return true if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise.
     */
    default boolean beforeInsert(DroolsController controller, Object fact) {
        return false;
    }

    /**
     * called after a fact is injected into the Policy Container.
     *
     * @return true if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise.
     */
    default boolean afterInsert(DroolsController controller, Object fact, boolean successInsert) {
        return false;
    }

    /**
     * Intercept before the Drools Controller delivers (posts) an event.
     *
     * @return True if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features. False, otherwise
     */
    default boolean beforeDeliver(DroolsController controller, TopicSink sink, Object fact) {
        return false;
    }

    /**
     * Called after the Drools Controller delivers (posts) an event.
     *
     * @return True if this feature intercepts and takes ownership of the operation
     *         preventing the invocation of lower priority features. False, otherwise
     */
    default boolean afterDeliver(DroolsController controller, TopicSink sink, Object fact, String json,
                    boolean success) {
        return false;
    }
}
