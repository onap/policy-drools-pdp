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

package org.onap.policy.drools.features.lock;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.onap.policy.common.utils.properties.exception.PropertyException;
import org.onap.policy.common.utils.time.CurrentTime;
import org.onap.policy.drools.core.lock.AlwaysFailLock;
import org.onap.policy.drools.core.lock.Lock;
import org.onap.policy.drools.core.lock.LockCallback;
import org.onap.policy.drools.core.lock.LockImpl;
import org.onap.policy.drools.core.lock.LockState;
import org.onap.policy.drools.core.lock.PolicyResourceLockFeatureApi;
import org.onap.policy.drools.features.PolicyControllerFeatureApi;
import org.onap.policy.drools.features.PolicyEngineFeatureApi;
import org.onap.policy.drools.persistence.SystemPersistenceConstants;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.drools.system.PolicyEngine;
import org.onap.policy.drools.system.PolicyEngineConstants;
import org.onap.policy.drools.utils.Extractor;
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
public class SimpleLockingFeature
                implements PolicyResourceLockFeatureApi, PolicyEngineFeatureApi, PolicyControllerFeatureApi {

    private static final Logger logger = LoggerFactory.getLogger(SimpleLockingFeature.class);

    private static final String CONFIGURATION_PROPERTIES_NAME = "feature-simple-locking";
    private static final String NOT_LOCKED_MSG = "not locked";
    private static final String LOCK_LOST_MSG = "lock lost";

    /**
     * Property prefix for extractor definitions.
     */
    public static final String EXTRACTOR_PREFIX = "locking.extractor.";

    /**
     * Provider of current time. May be overridden by junit tests.
     */
    private static CurrentTime currentTime = new CurrentTime();

    @Getter(AccessLevel.PROTECTED)
    @Setter(AccessLevel.PROTECTED)
    private static SimpleLockingFeature latestInstance = null;


    /**
     * Feature properties.
     */
    private SimpleLockingProperties featProps;

    /**
     * Maps a resource to the lock that owns it.
     */
    private final Map<String, SimpleLock> resource2lock = new ConcurrentHashMap<>();

    /**
     * Used to extract the owner key from the ownerInfo.
     */
    private final Extractor extractor = new Extractor();

    /**
     * Thread pool used to check for lock expiration and to notify owners when locks are
     * lost.
     */
    private ScheduledExecutorService exsvc = null;

    /**
     * Used to cancel the expiration checker on shutdown.
     */
    private ScheduledFuture<?> checker = null;


    /**
     * Constructs the object.
     */
    public SimpleLockingFeature() {
        super();
    }

    @Override
    public int getSequenceNumber() {
        // low priority
        return 1000;
    }

    @Override
    public PolicyController beforeCreate(String name, Properties properties) {
        extractor.register(properties, EXTRACTOR_PREFIX);
        return null;
    }

    @Override
    public synchronized boolean afterStart(PolicyEngine engine) {

        try {
            featProps = new SimpleLockingProperties(getFeatureProperties());

            exsvc = getThreadPool();

            checker = exsvc.scheduleWithFixedDelay(this::checkExpired, featProps.getExpireCheckSec(),
                            featProps.getExpireCheckSec(), TimeUnit.SECONDS);

            setLatestInstance(this);

        } catch (PropertyException e) {
            throw new SimpleLockingFeatureException(e);
        }

        return false;
    }

    /**
     * Gets the feature properties from a file.
     *
     * @return the feature properties, if the file can be read, an empty property set
     *         otherwise
     */
    protected Properties getFeatureProperties() {
        try {
            return getPropertiesFromFile(CONFIGURATION_PROPERTIES_NAME);

        } catch (IllegalArgumentException e) {
            logger.warn("cannot read simple locking config file - using defaults", e);
            return new Properties();
        }
    }

    /**
     * Stops the expiration checker. Does <i>not</i> invoke any lock call-backs.
     */
    @Override
    public synchronized boolean afterStop(PolicyEngine engine) {
        if (checker != null) {
            checker.cancel(true);
            checker = null;
        }

        return false;
    }

    @Override
    public Lock beforeCreateLock(String resourceId, Object ownerInfo, int holdSec, LockCallback callback,
                    boolean waitForLock) {

        if (latestInstance != this) {
            AlwaysFailLock lock = new AlwaysFailLock(resourceId, ownerInfo, null, holdSec, callback);
            lock.notifyUnavailable();
            return lock;
        }

        SimpleLock lock = makeLock(LockState.WAITING, resourceId, ownerInfo, holdSec, callback);

        SimpleLock existingLock = resource2lock.putIfAbsent(resourceId, lock);

        if (existingLock == null) {
            lock.grant();
        } else {
            lock.deny("resource is busy");
        }

        return lock;
    }

    @Override
    public boolean afterCreateLock(Lock lock, int holdSec, LockCallback callback, boolean waitForLock) {
        return false;
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

    /**
     * Simple Lock implementation.
     */
    public static class SimpleLock extends LockImpl {
        private static final long serialVersionUID = 1L;

        /**
         * Time, in milliseconds, when the lock expires.
         */
        @Getter
        private long holdUntilMs;

        /**
         * Feature containing this lock. May be {@code null} until the feature is
         * identified. Note: this can only be null if the lock has been de-serialized.
         */
        private transient SimpleLockingFeature feature;

        /**
         * Constructs the object.
         */
        public SimpleLock() {
            this.holdUntilMs = 0;
            this.feature = null;
        }

        /**
         * Constructs the object.
         *
         * @param state initial state of the lock
         * @param resourceId identifier of the resource to be locked
         * @param ownerInfo information identifying the owner requesting the lock
         * @param holdSec amount of time, in seconds, for which the lock should be held,
         *        after which it will automatically be released
         * @param callback callback to be invoked once the lock is granted, or
         *        subsequently lost; must not be {@code null}
         * @param feature feature containing this lock
         */
        public SimpleLock(LockState state, String resourceId, Object ownerInfo, int holdSec, LockCallback callback,
                        SimpleLockingFeature feature) {
            /*
             * Get the owner key via the extractor. This is only used for logging, so OK
             * if it fails (i.e., returns null).
             */
            super(state, resourceId, ownerInfo, feature.extractor.extract(ownerInfo), holdSec, callback);

            this.feature = feature;
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

        /**
         * Grants this lock. The notification is <i>always</i> invoked via a background
         * thread.
         */
        protected synchronized void grant() {
            if (isUnavailable()) {
                return;
            }

            setState(LockState.ACTIVE);
            holdUntilMs = currentTime.getMillis() + getHoldSec();

            logger.info("lock granted: {}", this);

            feature.exsvc.execute(this::notifyAvailable);
        }

        /**
         * Permanently denies this lock. The notification is invoked via a background
         * thread, if a feature instance is attached, otherwise it uses the foreground
         * thread.
         *
         * @param reason the reason the lock was denied
         */
        protected void deny(String reason) {
            synchronized (this) {
                setState(LockState.UNAVAILABLE);
            }

            logger.info("{}: {}", reason, this);

            if (feature == null) {
                notifyUnavailable();

            } else {
                feature.exsvc.execute(this::notifyUnavailable);
            }
        }

        @Override
        public boolean free() {
            // do a quick check of the state
            if (isUnavailable()) {
                return false;
            }

            logger.info("releasing lock: {}", this);

            if (!attachFeature()) {
                setState(LockState.UNAVAILABLE);
                return false;
            }

            AtomicBoolean result = new AtomicBoolean(false);

            feature.resource2lock.computeIfPresent(getResourceId(), (resourceId, curlock) -> {

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
            if (holdSec < 0) {
                throw new IllegalArgumentException("holdSec is negative");
            }

            setHoldSec(holdSec);
            setCallback(callback);

            // do a quick check of the state
            if (isUnavailable() || !attachFeature()) {
                deny(LOCK_LOST_MSG);
                return;
            }

            if (feature.resource2lock.get(getResourceId()) == this) {
                grant();
            } else {
                deny(NOT_LOCKED_MSG);
            }
        }

        /**
         * Attaches to the feature instance, if not already attached.
         *
         * @return {@code true} if the lock is now attached to a feature, {@code false}
         *         otherwise
         */
        private synchronized boolean attachFeature() {
            if (feature != null) {
                // already attached
                return true;
            }

            feature = latestInstance;
            if (feature == null) {
                logger.warn("no feature yet for {}", this);
                return false;
            }

            // put this lock into the map
            feature.resource2lock.putIfAbsent(getResourceId(), this);

            return true;
        }

        @Override
        public String toString() {
            return "SimpleLock [state=" + getState() + ", resourceId=" + getResourceId() + ", ownerKey=" + getOwnerKey()
                            + ", holdSec=" + getHoldSec() + ", holdUntilMs=" + holdUntilMs + "]";
        }
    }

    // these may be overridden by junit tests

    protected Properties getPropertiesFromFile(String fileName) {
        return SystemPersistenceConstants.getManager().getProperties(fileName);
    }

    protected ScheduledExecutorService getThreadPool() {
        return PolicyEngineConstants.getManager().getExecutorService();
    }

    protected SimpleLock makeLock(LockState waiting, String resourceId, Object ownerInfo, int holdSec,
                    LockCallback callback) {
        return new SimpleLock(waiting, resourceId, ownerInfo, holdSec, callback, this);
    }
}
