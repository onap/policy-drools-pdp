/*
 * ============LICENSE_START=======================================================
 * api-resource-locks
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

import org.onap.policy.common.utils.services.OrderedService;

/**
 * Resource locks. Each lock has an "owner", which is intended to be unique across a
 * single instance of a running PolicyEngine.
 *
 * <p/>
 * This interface provides a way to invoke optional features at various points in the
 * code. At appropriate points in the application, the code iterates through this list,
 * invoking these optional methods. The code may choose to cache the first enabled
 * implementation for future use.
 *
 * <p/>
 * Implementers may choose to implement a level of locking appropriate to the application.
 * For instance, they may choose to implement an engine-wide locking scheme, or they may
 * choose to implement a global locking scheme (e.g., through a shared DB).
 */
public interface PolicyResourceLockFeatureApi extends OrderedService {

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
     * @param ownerInfo information identifying the owner requesting the lock
     * @param holdSec amount of time, in seconds, for which the lock should be held once
     *        it has been granted, after which it will automatically be released
     * @param callback callback to be invoked once the lock is granted, or subsequently
     *        lost; must not be {@code null}
     * @param waitForLock {@code true} to wait for the lock, if it is currently locked,
     *        {@code false} otherwise
     * @return a new lock, or {@code null} if this feature does not support the desired
     *         lock
     */
    Lock beforeCreateLock(String resourceId, Object ownerInfo, int holdSec, LockCallback callback, boolean waitForLock);

    /**
     * Called after a lock has been requested on a resource.
     *
     * @param lock lock that was created (typically, this will be a lock that always
     *        fails)
     * @param holdSec amount of time, in seconds, for which the lock should be held once
     *        it has been granted, after which it will automatically be released
     * @param callback callback to be invoked once the lock is granted, or subsequently
     *        lost; must not be {@code null}
     * @param waitForLock {@code true} to wait for the lock, if it is currently locked,
     *        {@code false} otherwise
     * @return {@code true} if the feature handled the lock, {@code false} otherwise.
     *         Note: if {@code true} is returned, then the implementation is expected to
     *         invoke the callback
     */
    boolean afterCreateLock(Lock lock, int holdSec, LockCallback callback, boolean waitForLock);
}
