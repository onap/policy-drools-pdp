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

import org.onap.policy.common.utils.services.OrderedServiceImpl;

public class DroolsPdpStateControlApiConstants {

    /**
     * 'FeatureAPI.impl.getList()' returns an ordered list of objects
     * implementing the 'FeatureAPI' interface.
     */
    public static OrderedServiceImpl<DroolsPdpStateControlApi> impl =
        new OrderedServiceImpl<>(DroolsPdpStateControlApi.class);

    private DroolsPdpStateControlApiConstants() {
        // do nothing
    }
}
