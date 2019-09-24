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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lock implementation.
 */
@Getter
@ToString(exclude = {"ownerInfo", "callback", "succeed"})
public class LockImpl implements Lock {

    private static final Logger logger = LoggerFactory.getLogger(LockImpl.class);

    @Setter(AccessLevel.PROTECTED)
    private LockState state;

    private final String resourceId;
    private final Object ownerInfo;
    private final String ownerKey;

    @Setter(AccessLevel.PROTECTED)
    private LockCallback callback;

    @Setter(AccessLevel.PROTECTED)
    private int holdSec;

    /**
     * {@code True} if requests should generally succeed, {@code false} if they should
     * always fail.
     */
    private final boolean succeed;

    /**
     * Constructs the object.
     *
     * @param state the initial lock state
     * @param resourceId identifier of the resource to be locked
     * @param ownerInfo information identifying the owner requesting the lock
     * @param ownerKey key extracted from the ownerInfo, or {@code null}
     * @param holdSec amount of time, in seconds, for which the lock should be held once
     *        it has been granted, after which it will automatically be released
     * @param callback callback to be invoked once the lock is granted, or subsequently
     *        lost; must not be {@code null}
     * @param succeed {@code true} if requests should generally succeed, {@code false} if
     *        they should always fail
     */
    public LockImpl(LockState state, String resourceId, Object ownerInfo, String ownerKey, int holdSec,
                    LockCallback callback, boolean succeed) {

        this.state = state;
        this.resourceId = resourceId;
        this.ownerInfo = ownerInfo;
        this.ownerKey = ownerKey;
        this.callback = callback;
        this.holdSec = holdSec;
        this.succeed = succeed;
    }

    @Override
    public boolean isActive() {
        return (getState() == LockState.ACTIVE);
    }

    @Override
    public boolean isUnavailable() {
        return (getState() == LockState.UNAVAILABLE);
    }

    @Override
    public boolean isWaiting() {
        return (getState() == LockState.WAITING);
    }

    @Override
    public boolean free() {
        if (!succeed || isUnavailable()) {
            return false;
        }

        state = LockState.UNAVAILABLE;
        return true;
    }

    @Override
    public void extend(int holdSec, LockCallback callback) {
        this.callback = callback;

        if (succeed && !isUnavailable()) {
            notifyAvailable();

        } else {
            notifyUnavailable();
        }
    }

    /**
     * Invokes the {@link LockCallback#lockAvailable(Lock)}, <i>from the current
     * thread</i>. Note: subclasses may choose to invoke the callback from other threads.
     */
    public void notifyAvailable() {
        try {
            callback.lockAvailable(this);

        } catch (RuntimeException e) {
            logger.warn("lock callback threw an exception", e);
        }
    }

    /**
     * Invokes the {@link LockCallback#lockUnavailable(Lock)}, <i>from the current
     * thread</i>. Note: subclasses may choose to invoke the callback from other threads.
     */
    public void notifyUnavailable() {
        try {
            callback.lockUnavailable(this);

        } catch (RuntimeException e) {
            logger.warn("lock callback threw an exception", e);
        }
    }
}
