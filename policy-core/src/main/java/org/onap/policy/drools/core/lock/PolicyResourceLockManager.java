/*
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

package org.onap.policy.drools.core.lock;

import org.onap.policy.common.capabilities.Lockable;
import org.onap.policy.common.capabilities.Startable;

/**
 * Manager of resource locks.
 */
public interface PolicyResourceLockManager extends Startable, Lockable {

    /**
     * Requests a lock on a resource. Typically, the lock is not immediately granted,
     * though a "lock" object is always returned. Once the lock has been granted (or
     * denied), the callback will be invoked to indicate the result.
     *
     * <p/>
     * Notes:
     * <dl>
     * <li>The callback may be invoked <i>before</i> this method returns</li>
     * <li>The implementation need not honor waitForLock={@code true}</li>
     * </dl>
     *
     * @param resourceId identifier of the resource to be locked
     * @param ownerKey information identifying the owner requesting the lock
     * @param holdSec amount of time, in seconds, for which the lock should be held once
     *        it has been granted, after which it will automatically be released
     * @param callback callback to be invoked once the lock is granted, or subsequently
     *        lost; must not be {@code null}
     * @param waitForLock {@code true} to wait for the lock, if it is currently locked,
     *        {@code false} otherwise
     * @return a new lock
     */
    Lock createLock(String resourceId, String ownerKey, int holdSec, LockCallback callback,
        boolean waitForLock);
}
