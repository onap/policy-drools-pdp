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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.AccessLevel;
import lombok.Getter;
import org.onap.policy.drools.core.lock.AlwaysFailLock;
import org.onap.policy.drools.core.lock.Lock;
import org.onap.policy.drools.core.lock.LockCallback;
import org.onap.policy.drools.core.lock.LockState;
import org.onap.policy.drools.core.lock.PolicyResourceLockManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Super class for Lock Features.
 *
 * <p/>
 * Note: this implementation does <i>not</i> honor the waitForLocks={@code true}
 * parameter.
 *
 * <p/>
 * When a lock is deserialized, it will not initially appear in this feature's map; it
 * will be added to the map once free() or extend() is invoked, provided there isn't
 * already an entry.
 */
public abstract class LockManager<T extends FeatureLockImpl> implements PolicyResourceLockManager {

    private static final Logger logger = LoggerFactory.getLogger(LockManager.class);

    public static final String NOT_LOCKED_MSG = "not locked";

    /**
     * Maps a resource to the lock that owns it.
     */
    @Getter(AccessLevel.PROTECTED)
    private final Map<String, T> resource2lock = new ConcurrentHashMap<>();

    /**
     * {@code True} if this feature is running, {@code false} otherwise.
     */
    private boolean alive = false;

    /**
     * {@code True} if this feature is locked, {@code false} otherwise.
     */
    private boolean locked = false;


    /**
     * Constructs the object.
     */
    protected LockManager() {
        super();
    }

    @Override
    public boolean isAlive() {
        return alive;
    }

    @Override
    public synchronized boolean start() {
        if (alive) {
            return false;
        }

        alive = true;
        return true;
    }

    /**
     * Stops the expiration checker. Does <i>not</i> invoke any lock call-backs.
     */
    @Override
    public synchronized boolean stop() {
        if (!alive) {
            return false;
        }

        alive = false;
        return true;
    }

    @Override
    public void shutdown() {
        stop();
    }

    @Override
    public boolean isLocked() {
        return locked;
    }

    @Override
    public synchronized boolean lock() {
        if (locked) {
            return false;
        }

        locked = true;
        return true;
    }

    @Override
    public synchronized boolean unlock() {
        if (!locked) {
            return false;
        }

        locked = false;
        return true;
    }

    /**
     * After performing checks, this invokes
     * {@link #makeLock(LockState, String, String, int, LockCallback)} to create a lock
     * object, inserts it into the map, and then invokes {@link #finishLock(MgrLock)}.
     */
    @Override
    public Lock createLock(String resourceId, String ownerKey, int holdSec, LockCallback callback,
                    boolean waitForLock) {

        if (hasInstanceChanged()) {
            AlwaysFailLock lock = new AlwaysFailLock(resourceId, ownerKey, holdSec, callback);
            lock.notifyUnavailable();
            return lock;
        }

        T lock = makeLock(LockState.WAITING, resourceId, ownerKey, holdSec, callback);

        T existingLock = resource2lock.putIfAbsent(resourceId, lock);

        if (existingLock == null) {
            logger.debug("added lock to map {}", lock);
            finishLock(lock);
        } else {
            lock.deny("resource is busy");
        }

        return lock;
    }

    /**
     * Determines if this object is no longer the current instance of this feature type.
     *
     * @return {@code true} if this object is no longer the current instance,
     *         {@code false} otherwise
     */
    protected abstract boolean hasInstanceChanged();

    /**
     * Finishes the steps required to establish a lock, changing its state to ACTIVE, when
     * appropriate.
     *
     * @param lock the lock to be locked
     */
    protected abstract void finishLock(T lock);

    // these may be overridden by junit tests

    /**
     * Makes a lock of the appropriate type.
     *
     * @param state initial state of the lock
     * @param resourceId identifier of the resource to be locked
     * @param ownerKey information identifying the owner requesting the lock
     * @param holdSec amount of time, in seconds, for which the lock should be held, after
     *        which it will automatically be released
     * @param callback callback to be invoked once the lock is granted, or subsequently
     *        lost; must not be {@code null}
     * @return a new lock
     */
    protected abstract T makeLock(LockState waiting, String resourceId, String ownerKey, int holdSec,
                    LockCallback callback);
}
