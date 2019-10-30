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

package org.onap.policy.drools.system.internal;

import java.util.concurrent.ScheduledExecutorService;
import org.onap.policy.drools.core.lock.LockCallback;
import org.onap.policy.drools.core.lock.LockImpl;
import org.onap.policy.drools.core.lock.LockState;
import org.onap.policy.drools.system.PolicyEngineConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lock implementation used by locking features.
 */
public abstract class FeatureLockImpl extends LockImpl {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(FeatureLockImpl.class);

    public static final String LOCK_LOST_MSG = "lock lost";

    /**
     * {@code True} if this lock is attached to a feature, {@code false} if it is not.
     */
    private transient boolean attached;

    /**
     * Constructs the object.
     */
    public FeatureLockImpl() {
        this.attached = false;
    }

    /**
     * Constructs the object.
     *
     * @param state initial state of the lock
     * @param resourceId identifier of the resource to be locked
     * @param ownerKey information identifying the owner requesting the lock
     * @param holdSec amount of time, in seconds, for which the lock should be held, after
     *        which it will automatically be released
     * @param callback callback to be invoked once the lock is granted, or subsequently
     *        lost; must not be {@code null}
     */
    public FeatureLockImpl(LockState state, String resourceId, String ownerKey, int holdSec, LockCallback callback) {
        super(state, resourceId, ownerKey, holdSec, callback);
        this.attached = true;
    }

    /**
     * Grants this lock. The notification is <i>always</i> invoked via a background
     * thread.
     * @param foreground {@code true} if to invoke the callback in the foreground thread,
     *        {@code false} otherwise
     */
    protected synchronized void grant(boolean foreground) {
        if (isUnavailable()) {
            return;
        }

        setState(LockState.ACTIVE);
        updateGrant();

        logger.info("lock granted: {}", this);

        if (foreground) {
            notifyAvailable();
        } else {
            getThreadPool().execute(this::notifyAvailable);
        }
    }

    /**
     * Permanently denies this lock.
     *
     * @param reason the reason the lock was denied
     * @param foreground {@code true} if to invoke the callback in the foreground thread,
     *        {@code false} otherwise
     */
    public void deny(String reason, boolean foreground) {
        synchronized (this) {
            setState(LockState.UNAVAILABLE);
        }

        logger.info("{}: {}", reason, this);

        if (foreground) {
            notifyUnavailable();
        } else {
            getThreadPool().execute(this::notifyUnavailable);
        }
    }

    @Override
    public abstract boolean free();

    /**
     * Determines if the lock can be freed.
     *
     * @return {@code true} if the lock can be freed, {@code false} if the lock is
     *         unavailable
     */
    protected boolean freeAllowed() {
        // do a quick check of the state
        if (isUnavailable()) {
            return false;
        }

        logger.info("releasing lock: {}", this);

        if (!attachFeature()) {
            setState(LockState.UNAVAILABLE);
            return false;
        }

        return true;
    }

    @Override
    public abstract void extend(int holdSec, LockCallback callback);

    /**
     * Determines if the lock can be extended.
     *
     * @param holdSec the additional amount of time to hold the lock, in seconds
     * @param callback callback to be invoked when the extension completes
     * @return {@code true} if the lock can be extended, {@code false} if the lock is
     *         unavailable
     */
    protected boolean extendAllowed(int holdSec, LockCallback callback) {
        if (holdSec < 0) {
            throw new IllegalArgumentException("holdSec is negative");
        }

        setHoldSec(holdSec);
        setCallback(callback);

        // do a quick check of the state
        if (isUnavailable() || !attachFeature()) {
            deny(LOCK_LOST_MSG, true);
            return false;
        }

        return true;
    }

    /**
     * Attaches to the feature instance, if not already attached.
     *
     * @return {@code true} if the lock is now attached to a feature, {@code false}
     *         otherwise
     */
    private synchronized boolean attachFeature() {
        if (!attached) {
            attached = addToFeature();
        }

        return attached;
    }

    /**
     * Updates a lock when it is granted. The default method does nothing.
     */
    protected void updateGrant() {
        // do nothing
    }

    /**
     * Adds the lock to the relevant feature.
     *
     * @return {@code true} if the lock was added, {@code false} if it could not be added
     *         (e.g., because there is no feature yet)
     */
    protected abstract boolean addToFeature();

    /**
     * Gets the thread pool.
     *
     * @return the thread pool
     */
    protected ScheduledExecutorService getThreadPool() {
        return PolicyEngineConstants.getManager().getExecutorService();
    }
}
