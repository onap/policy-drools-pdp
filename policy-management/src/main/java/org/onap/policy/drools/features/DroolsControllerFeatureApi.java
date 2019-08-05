/*-
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018-2019 AT&T Intellectual Property. All rights reserved.
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

import org.onap.policy.common.endpoints.event.comm.TopicSink;
import org.onap.policy.common.utils.services.OrderedService;
import org.onap.policy.drools.controller.DroolsController;

/**
 * Drools Controller Feature API.   Hooks into the Drools Controller operations.
 */
public interface DroolsControllerFeatureApi extends OrderedService {

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
