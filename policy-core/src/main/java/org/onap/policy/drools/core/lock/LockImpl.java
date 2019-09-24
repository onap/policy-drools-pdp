/*
 * ============LICENSE_START=======================================================
 * ONAP
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

package org.onap.policy.drools.core.lock;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Lock implementation.
 */
@Getter
@ToString(exclude = {"ownerInfo", "callback"})
public class LockImpl implements Lock {

    @Setter
    private Lock.State state;

    private final String resourceId;
    private final Object ownerInfo;
    private final String ownerKey;
    private final LockCallback callback;

    @Setter(AccessLevel.PROTECTED)
    private int holdSec;

    /**
     * Constructs the object.
     *
     * @param state the initial lock state
     * @param resourceId identifier of the resource to be locked
     * @param ownerInfo information identifying the owner requesting the lock
     * @param ownerKey key extracted from the ownerInfo, or {@code null}
     * @param callback callback to be invoked once the lock is granted, or subsequently
     *        lost; must not be {@code null}
     * @param holdSec amount of time, in seconds, for which the lock should be held once
     *        it has been granted, after which it will automatically be released
     */
    public LockImpl(Lock.State state, String resourceId, Object ownerInfo, String ownerKey, LockCallback callback,
                    int holdSec) {

        this.state = state;
        this.resourceId = resourceId;
        this.ownerInfo = ownerInfo;
        this.ownerKey = ownerKey;
        this.callback = callback;
        this.holdSec = holdSec;
    }

    /**
     * This default method simply returns {@code false}.
     */
    @Override
    public boolean free() {
        return false;
    }

    /**
     * This default method simply returns {@code false}.
     */
    @Override
    public boolean extend(int holdSec) {
        return false;
    }

    /**
     * Invokes the {@link LockCallback#lockUnavailable(Lock)}, <i>from the current
     * thread</i>.
     *
     * @return this lock
     */
    public LockImpl notifyUnavailable() {
        callback.lockUnavailable(this);
        return this;
    }
}
