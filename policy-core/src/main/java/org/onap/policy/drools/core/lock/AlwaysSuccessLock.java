/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2021 AT&T Intellectual Property. All rights reserved.
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

import lombok.NonNull;

/**
 * Lock implementation whose operations always succeed.
 */
public class AlwaysSuccessLock extends LockImpl {
    private static final long serialVersionUID = 1L;

    /**
     * Overrides parent constructor.
     */
    public AlwaysSuccessLock() {
        super();
        setState(LockState.ACTIVE);
    }

    /**
     * Overrides parent constructor.
     */
    public AlwaysSuccessLock(@NonNull LockState state, @NonNull String resourceId,
            @NonNull String ownerKey, int holdSec, @NonNull LockCallback callback) {
        super(state, resourceId, ownerKey, holdSec, callback);
        if (getState() != LockState.ACTIVE) {
            throw new IllegalArgumentException("AlwaysSuccessLock can only be created in the active state");
        }
    }

    /**
     * Constructs the object.
     */
    public AlwaysSuccessLock(@NonNull String resourceId, @NonNull String ownerKey,
            int holdSec, @NonNull LockCallback callback) {
        super(LockState.ACTIVE, resourceId, ownerKey, holdSec, callback);
    }

    /**
     * Always returns true.
     */
    @Override
    public synchronized boolean free() {
        return true;
    }
}
