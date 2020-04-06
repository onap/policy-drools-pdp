/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2019-2020 AT&T Intellectual Property. All rights reserved.
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


/**
 * Lock implementation whose operations always fail.
 */
public class AlwaysFailLock extends LockImpl {
    private static final long serialVersionUID = 1L;

    /**
     * Constructs the object.
     */
    public AlwaysFailLock() {
        super();
    }

    /**
     * Constructs the object.
     *
     * @param resourceId identifier of the resource to be locked
     * @param ownerKey information identifying the owner requesting the lock
     * @param holdSec amount of time, in seconds, for which the lock should be held once
     *        it has been granted, after which it will automatically be released
     * @param callback callback to be invoked once the lock is granted, or subsequently
     *        lost; must not be {@code null}
     */
    public AlwaysFailLock(String resourceId, String ownerKey, int holdSec, LockCallback callback) {
        super(LockState.UNAVAILABLE, resourceId, ownerKey, holdSec, callback);
    }

    /**
     * Always returns false.
     */
    @Override
    public synchronized boolean free() {
        return false;
    }

    /**
     * Always fails and invokes {@link LockCallback#lockUnavailable(Lock)}.
     */
    @Override
    public void extend(int holdSec, LockCallback callback) {
        synchronized (this) {
            setHoldSec(holdSec);
            setCallback(callback);
        }

        notifyUnavailable();
    }
}
