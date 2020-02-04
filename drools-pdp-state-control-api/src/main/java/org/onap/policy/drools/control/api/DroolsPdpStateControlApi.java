/*-
 * ============LICENSE_START=======================================================
 * drools-pdp-state-control-api
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

package org.onap.policy.drools.control.api;

import org.onap.policy.common.utils.services.OrderedService;
import org.onap.policy.common.utils.services.OrderedServiceImpl;

public interface DroolsPdpStateControlApi extends OrderedService {

    /**
     * 'FeatureAPI.impl.getList()' returns an ordered list of objects
     * implementing the 'FeatureAPI' interface.
     */
    public static OrderedServiceImpl<DroolsPdpStateControlApi> impl =
        new OrderedServiceImpl<>(DroolsPdpStateControlApi.class);

    /**
     * This method is called when wanting to interrupt the operation of the
     * drools pdp.  It locks the endpoints, stops the message processing
     * and removes the instance of the drools pdp from the pool.
     */
    public void shutdown();

    /**
     * This method is called when wanting to resume the operation of the
     * drools pdp.  It unlocks the endpoints, resumes message processing
     * and adds the instance of the drools pdp to the pool.
     */
    public void restart();
}
