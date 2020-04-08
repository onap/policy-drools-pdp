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

package org.onap.policy.drools.system.internal;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.onap.policy.common.utils.properties.exception.PropertyException;
import org.onap.policy.common.utils.time.CurrentTime;
import org.onap.policy.drools.core.lock.LockCallback;
import org.onap.policy.drools.core.lock.LockState;
import org.onap.policy.drools.system.PolicyEngine;
import org.onap.policy.drools.system.PolicyEngineConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Simple implementation of the Lock Feature. Locks do not span across instances of this
 * object (i.e., locks do not span across servers).
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
public class SimpleLockManager extends LockManager<SimpleLockManager.SimpleLock> {

    private static final Logger logger = LoggerFactory.getLogger(SimpleLockManager.class);

    /**
     * Provider of current time. May be overridden by junit tests.
     */
    private static CurrentTime currentTime = new CurrentTime();

    @Getter(AccessLevel.PROTECTED)
    @Setter(AccessLevel.PROTECTED)
    private static SimpleLockManager latestInstance = null;

    /**
     * Feature properties.
     */
    private final SimpleLockProperties featProps;

    /**
     * Maps a resource to the lock that owns it.
     */
    private final Map<String, SimpleLock> resource2lock;

    /**
     * Used to cancel the expiration checker on shutdown.
     */
    private ScheduledFuture<?> checker = null;


    /**
     * Constructs the object.
     *
     * @param engine engine with which this manager is associated
     * @param properties properties used to configure the manager
     */
    public SimpleLockManager(PolicyEngine engine, Properties properties) {
        try {
            this.featProps = new SimpleLockProperties(properties);
            this.resource2lock = getResource2lock();

        } catch (PropertyException e) {
            throw new SimpleLockManagerException(e);
        }
    }

    @Override
    public synchronized boolean start() {
        if (isAlive()) {
            return false;
        }

        checker = PolicyEngineConstants.getManager().getExecutorService().scheduleWithFixedDelay(this::checkExpired,
                        featProps.getExpireCheckSec(), featProps.getExpireCheckSec(), TimeUnit.SECONDS);

        setLatestInstance(this);

        return super.start();
    }

    /**
     * Stops the expiration checker. Does <i>not</i> invoke any lock call-backs.
     */
    @Override
    public synchronized boolean stop() {
        if (!super.stop()) {
            return false;
        }

        ScheduledFuture<?> checker2 = checker;
        checker = null;

        checker2.cancel(true);

        return true;
    }

    /**
     * Checks for expired locks.
     */
    private void checkExpired() {
        long currentMs = currentTime.getMillis();
        logger.info("checking for expired locks at {}", currentMs);

        /*
         * Could do this via an iterator, but using compute() guarantees that the lock
         * doesn't get extended while it's being removed from the map.
         */
        for (Entry<String, SimpleLock> ent : resource2lock.entrySet()) {
            if (!ent.getValue().expired(currentMs)) {
                continue;
            }

            AtomicReference<SimpleLock> lockref = new AtomicReference<>(null);

            resource2lock.computeIfPresent(ent.getKey(), (resourceId, lock) -> {
                if (lock.expired(currentMs)) {
                    lockref.set(lock);
                    return null;
                }

                return lock;
            });

            SimpleLock lock = lockref.get();
            if (lock != null) {
                lock.deny("lock expired");
            }
        }
    }

    @Override
    protected void finishLock(SimpleLock lock) {
        lock.grant();
    }

    @Override
    protected boolean hasInstanceChanged() {
        return (getLatestInstance() != this);
    }

    /**
     * Simple Lock implementation.
     */
    public static class SimpleLock extends FeatureLockImpl {
        private static final long serialVersionUID = 1L;

        /**
         * Time, in milliseconds, when the lock expires.
         */
        @Getter
        private long holdUntilMs;

        /**
         * Map that should hold this lock.
         */
        private transient Map<String, SimpleLock> resource2lock;

        /**
         * Constructs the object.
         */
        public SimpleLock() {
            this.holdUntilMs = 0;
            this.resource2lock = null;
        }

        /**
         * Constructs the object.
         *
         * @param state initial state of the lock
         * @param resourceId identifier of the resource to be locked
         * @param ownerKey information identifying the owner requesting the lock
         * @param holdSec amount of time, in seconds, for which the lock should be held,
         *        after which it will automatically be released
         * @param callback callback to be invoked once the lock is granted, or
         *        subsequently lost; must not be {@code null}
         * @param feature feature containing this lock
         */
        public SimpleLock(LockState state, String resourceId, String ownerKey, int holdSec, LockCallback callback,
                        SimpleLockManager feature) {
            super(state, resourceId, ownerKey, holdSec, callback);
            this.resource2lock = feature.resource2lock;
        }

        /**
         * Determines if the owner's lock has expired.
         *
         * @param currentMs current time, in milliseconds
         * @return {@code true} if the owner's lock has expired, {@code false} otherwise
         */
        public boolean expired(long currentMs) {
            return (holdUntilMs <= currentMs);
        }

        @Override
        public boolean free() {
            if (!freeAllowed()) {
                return false;
            }

            AtomicBoolean result = new AtomicBoolean(false);

            resource2lock.computeIfPresent(getResourceId(), (resourceId, curlock) -> {

                if (curlock == this) {
                    // this lock was the owner - resource is now available
                    result.set(true);
                    setState(LockState.UNAVAILABLE);
                    return null;

                } else {
                    return curlock;
                }
            });

            return result.get();
        }

        @Override
        public void extend(int holdSec, LockCallback callback) {
            if (!extendAllowed(holdSec, callback)) {
                return;
            }

            if (resource2lock.get(getResourceId()) == this) {
                grant();
            } else {
                deny(NOT_LOCKED_MSG);
            }
        }

        @Override
        protected void updateGrant() {
            holdUntilMs = currentTime.getMillis() + TimeUnit.SECONDS.toMillis(getHoldSec());
        }

        @Override
        protected boolean addToFeature() {
            SimpleLockManager feature = getLatestInstance();
            if (feature == null) {
                logger.warn("no feature yet for {}", this);
                return false;
            }

            // put this lock into the map
            resource2lock = feature.resource2lock;
            resource2lock.putIfAbsent(getResourceId(), this);

            return true;
        }

        @Override
        public String toString() {
            return "SimpleLock [state=" + getState() + ", resourceId=" + getResourceId() + ", ownerKey=" + getOwnerKey()
                            + ", holdSec=" + getHoldSec() + ", holdUntilMs=" + holdUntilMs + "]";
        }
    }

    // these may be overridden by junit tests

    protected SimpleLock makeLock(LockState waiting, String resourceId, String ownerKey, int holdSec,
                    LockCallback callback) {
        return new SimpleLock(waiting, resourceId, ownerKey, holdSec, callback, this);
    }
}
