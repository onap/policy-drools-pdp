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

package org.onap.policy.simple.locking;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Getter;
import org.onap.policy.common.utils.time.CurrentTime;
import org.onap.policy.drools.core.Extractor;
import org.onap.policy.drools.core.lock.Lock;
import org.onap.policy.drools.core.lock.LockCallback;
import org.onap.policy.drools.core.lock.LockImpl;
import org.onap.policy.drools.core.lock.PolicyResourceLockFeatureApi;
import org.onap.policy.drools.features.PolicyControllerFeatureApi;
import org.onap.policy.drools.features.PolicyEngineFeatureApi;
import org.onap.policy.drools.persistence.SystemPersistenceConstants;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.drools.system.PolicyEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Simple implementation of the Lock Feature. Locks do not span across instances of this
 * object (i.e., locks do not span across servers).
 *
 * <p/>
 * Note: this implementation does not honor the waitForLocks={@code true} parameter.
 */
public class SimpleLockingFeature
                implements PolicyResourceLockFeatureApi, PolicyEngineFeatureApi, PolicyControllerFeatureApi {

    private static final Logger logger = LoggerFactory.getLogger(SimpleLockingFeature.class);

    private static final String CONFIGURATION_PROPERTIES_NAME = "feature-simple-locking";

    /**
     * Property prefix for extractor definitions.
     */
    public static final String EXTRACTOR_PREFIX = "locking.extractor.";

    /**
     * Provider of current time. May be overridden by junit tests.
     */
    private static CurrentTime currentTime = new CurrentTime();


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
     * Constructs the object.
     */
    public SimpleLockingFeature() {
        super();
    }

    @Override
    public int getSequenceNumber() {
        // lowest priority, as this is the default implementation
        return 0;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public PolicyController beforeCreate(String name, Properties properties) {
        extractor.register(properties, EXTRACTOR_PREFIX);
        return null;
    }

    @Override
    public boolean afterStart(PolicyEngine engine) {

        try {
            featProps = new SimpleLockingProperties(getProperties(CONFIGURATION_PROPERTIES_NAME));

        } catch (Exception e) {
            throw new SimpleLockingFeatureException(e);
        }

        return false;
    }

    /**
     * Shuts down the thread pool. Does <i>not</i> invoke any lock call-backs.
     */
    @Override
    public boolean beforeShutdown(PolicyEngine engine) {
        if (exsvc != null) {
            exsvc.shutdown();
        }

        return false;
    }

    @Override
    public SimpleLock lock(String resourceId, Object ownerInfo, LockCallback callback, int holdSec,
                    boolean waitForLock) {

        // lazy initialization
        initThreadPool();

        SimpleLock lock = new SimpleLock(Lock.State.WAITING, resourceId, ownerInfo, callback, holdSec);

        resource2lock.compute(resourceId, (key, curlock) -> {
            if (curlock == null) {
                // no data yet - implies that the lock is available
                lock.grant();
                return lock;

            } else {
                lock.deny("resource is busy");
                return curlock;
            }
        });

        return lock;
    }

    /**
     * Initializes {@link #exsvc}, if it hasn't been initialized yet, scheduling periodic
     * checks for expired locks.
     */
    private synchronized void initThreadPool() {
        if (exsvc == null) {
            exsvc = makeThreadPool(featProps.getMaxThreads());

            exsvc.scheduleWithFixedDelay(this::checkExpired, featProps.getExpireCheckSec(),
                            featProps.getExpireCheckSec(), TimeUnit.SECONDS);
        }
    }

    /**
     * Checks for expired locks.
     */
    private void checkExpired() {
        long currentMs = currentTime.getMillis();
        logger.info("checking for expired locks at {}", currentMs);

        for (Entry<String, SimpleLock> ent : resource2lock.entrySet()) {
            if (!ent.getValue().expired(currentMs)) {
                continue;
            }

            resource2lock.compute(ent.getKey(), (resourceId, lock) -> {
                if (lock != null && lock.expired(currentMs)) {
                    lock.deny("lock expired");
                    return null;
                }

                return lock;
            });
        }
    }

    /**
     * Simple Lock implementation.
     *
     * <p/>
     * Note: the state of the lock should not be changed except within a
     * resource2lock.compute() call.
     */
    protected class SimpleLock extends LockImpl {

        /**
         * Time, in milliseconds, when the lock expires.
         */
        @Getter
        private long holdUntilMs;


        /**
         * Constructs the object.
         *
         * @param state initial state of the lock
         * @param resourceId identifier of the resource to be locked
         * @param ownerInfo information identifying the owner requesting the lock
         * @param callback callback to be invoked once the lock is granted, or
         *        subsequently lost; must not be {@code null}
         * @param holdSec amount of time, in seconds, for which the lock should be held,
         *        after which it will automatically be released
         */
        public SimpleLock(State state, String resourceId, Object ownerInfo, LockCallback callback, int holdSec) {

            /*
             * Get the owner key via the extractor. This is only used for logging, so OK
             * if it fails (i.e., returns null).
             */
            super(state, resourceId, ownerInfo, extractor.extract(ownerInfo), callback, holdSec);

            if (holdSec < 0) {
                throw new IllegalArgumentException("holdSec is negative");
            }
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
         * Grants this lock.
         */
        protected void grant() {
            setState(Lock.State.ACTIVE);
            holdUntilMs = currentTime.getMillis() + getHoldSec();

            logger.info("lock granted: {}", this);

            notifyAvailable();
        }

        /**
         * Permanently denies this lock.
         *
         * @param reason the reason the lock was denied
         */
        protected void deny(String reason) {
            setState(Lock.State.UNAVAILABLE);

            logger.info("{}: {}", reason, this);

            notifyUnavailable();
        }

        @Override
        public boolean free() {
            // do a quick check of the state
            State curstate = getState();
            if (curstate != Lock.State.ACTIVE) {
                return false;
            }

            logger.info("releasing lock: {}", this);

            AtomicBoolean result = new AtomicBoolean(false);

            resource2lock.compute(getResourceId(), (resourceId, curlock) -> {

                if (curlock == this) {
                    // this lock was the owner - resource is now available
                    result.set(true);
                    setState(Lock.State.UNAVAILABLE);
                    return null;

                } else {
                    return curlock;
                }
            });

            return result.get();
        }

        @Override
        public boolean extend(int holdSec) {
            if (holdSec < 0) {
                throw new IllegalArgumentException("holdSec is negative");
            }

            // do a quick check of the state
            if (getState() != Lock.State.ACTIVE) {
                return false;
            }

            AtomicBoolean result = new AtomicBoolean(false);

            resource2lock.compute(getResourceId(), (resourceId, curlock) -> {

                if (curlock == this) {
                    result.set(true);
                    setHoldSec(holdSec);
                    grant();
                }

                /*
                 * ELSE: this lock is not the owner - no change; the notification must
                 * have already been sent
                 */

                return curlock;
            });

            return result.get();
        }

        @Override
        public SimpleLock notifyAvailable() {
            exsvc.execute(() -> getCallback().lockAvailable(this));
            return this;
        }

        @Override
        public SimpleLock notifyUnavailable() {
            exsvc.execute(() -> getCallback().lockUnavailable(this));
            return this;
        }
    }

    // these may be overridden by junit tests

    protected Properties getProperties(String fileName) {
        return SystemPersistenceConstants.getManager().getProperties(fileName);
    }

    protected ScheduledExecutorService makeThreadPool(int nthreads) {
        return Executors.newScheduledThreadPool(nthreads);
    }
}
