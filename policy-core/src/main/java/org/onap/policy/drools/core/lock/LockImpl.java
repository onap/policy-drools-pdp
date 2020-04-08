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

import java.io.Serializable;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lock implementation.
 */
@Getter
@Setter
@ToString(exclude = {"callback"})
public class LockImpl implements Lock, Serializable {
    private static final long serialVersionUID = 1L;

    private static final Logger logger = LoggerFactory.getLogger(LockImpl.class);

    private LockState state;
    private final String resourceId;
    private final String ownerKey;
    private transient LockCallback callback;
    private int holdSec;

    /**
     * Constructs the object.
     */
    public LockImpl() {
        this.state = LockState.UNAVAILABLE;
        this.resourceId = null;
        this.ownerKey = null;
        this.callback = null;
        this.holdSec = 0;
    }

    /**
     * Constructs the object.
     *
     * @param state the initial lock state
     * @param resourceId identifier of the resource to be locked
     * @param ownerKey information identifying the owner requesting the lock
     * @param holdSec amount of time, in seconds, for which the lock should be held once
     *        it has been granted, after which it will automatically be released
     * @param callback callback to be invoked once the lock is granted, or subsequently
     *        lost; must not be {@code null}
     */
    public LockImpl(@NonNull LockState state, @NonNull String resourceId, @NonNull String ownerKey, int holdSec,
                    @NonNull LockCallback callback) {

        if (holdSec < 0) {
            throw new IllegalArgumentException("holdSec is negative");
        }

        this.state = state;
        this.resourceId = resourceId;
        this.ownerKey = ownerKey;
        this.callback = callback;
        this.holdSec = holdSec;
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

    /**
     * This method always succeeds, unless the lock is already unavailable.
     */
    @Override
    public boolean free() {
        synchronized (this) {
            if (isUnavailable()) {
                return false;
            }

            logger.info("releasing lock: {}", this);
            setState(LockState.UNAVAILABLE);
        }

        return true;
    }

    /**
     * This method always succeeds, unless the lock is already unavailable.
     */
    @Override
    public void extend(int holdSec, LockCallback callback) {
        synchronized (this) {
            if (isUnavailable()) {
                return;
            }

            logger.info("lock granted: {}", this);
            setState(LockState.ACTIVE);
            setHoldSec(holdSec);
            setCallback(callback);
        }

        notifyAvailable();
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
