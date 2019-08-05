/*-
 * ============LICENSE_START=======================================================
 * policy-core
 * ================================================================================
 * Copyright (C) 2019 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.statemanagement;

import lombok.Getter;
import org.onap.policy.common.im.StateManagement;
import org.onap.policy.common.utils.services.OrderedServiceImpl;

public class StateManagementFeatureApiConstants {

    public static final String LOCKED = StateManagement.LOCKED;
    public static final String UNLOCKED = StateManagement.UNLOCKED;
    public static final String ENABLED = StateManagement.ENABLED;
    public static final String DISABLED = StateManagement.DISABLED;
    public static final String ENABLE_NOT_FAILED = StateManagement.ENABLE_NOT_FAILED_ACTION;
    public static final String DISABLE_FAILED = StateManagement.DISABLE_FAILED_ACTION;
    public static final String FAILED = StateManagement.FAILED;
    public static final String DEPENDENCY = StateManagement.DEPENDENCY;
    public static final String DEPENDENCY_FAILED = StateManagement.DEPENDENCY_FAILED;
    public static final String DISABLE_DEPENDENCY = StateManagement.DISABLE_DEPENDENCY_ACTION;
    public static final String ENABLE_NO_DEPENDENCY = StateManagement.ENABLE_NO_DEPENDENCY_ACTION;
    public static final String NULL_VALUE = StateManagement.NULL_VALUE;
    public static final String DO_LOCK = StateManagement.LOCK_ACTION;
    public static final String DO_UNLOCK = StateManagement.UNLOCK_ACTION;
    public static final String DO_PROMOTE = StateManagement.PROMOTE_ACTION;
    public static final String DO_DEMOTE = StateManagement.DEMOTE_ACTION;
    public static final String HOT_STANDBY = StateManagement.HOT_STANDBY;
    public static final String COLD_STANDBY = StateManagement.COLD_STANDBY;
    public static final String PROVIDING_SERVICE = StateManagement.PROVIDING_SERVICE;

    public static final String ADMIN_STATE = StateManagement.ADMIN_STATE;
    public static final String OPERATION_STATE = StateManagement.OPERATION_STATE;
    public static final String AVAILABLE_STATUS = StateManagement.AVAILABLE_STATUS;
    public static final String STANDBY_STATUS = StateManagement.STANDBY_STATUS;

    public static final Boolean ALLSEEMSWELL_STATE = Boolean.TRUE;
    public static final Boolean ALLNOTWELL_STATE = Boolean.FALSE;

    public static final int SEQ_NUM = 0;

    /**
     * 'FeatureAPI.impl.getList()' returns an ordered list of objects implementing the 'FeatureAPI'
     * interface.
     */
    @Getter
    private static final OrderedServiceImpl<StateManagementFeatureApi> impl =
            new OrderedServiceImpl<>(StateManagementFeatureApi.class);

    private StateManagementFeatureApiConstants() {
        // do nothing
    }
}
