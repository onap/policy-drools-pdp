/*-
 * ============LICENSE_START=======================================================
 * api-active-standby-management
 * ================================================================================
 * Copyright (C) 2017 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.activestandby;

import org.onap.policy.drools.utils.OrderedService;
import org.onap.policy.drools.utils.OrderedServiceImpl;

/**
 * This interface provides a way to invoke optional features at various points in the code. At
 * appropriate points in the application, the code iterates through this list, invoking these
 * optional methods.
 */
public interface ActiveStandbyFeatureAPI extends OrderedService {
    /**
     * 'FeatureAPI.impl.getList()' returns an ordered list of objects implementing the 'FeatureAPI'
     * interface.
     */
    public static OrderedServiceImpl<ActiveStandbyFeatureAPI> impl =
            new OrderedServiceImpl<>(ActiveStandbyFeatureAPI.class);

    public String getPdpdNowActive();

    public String getPdpdLastActive();

    public String getResourceName();
}
