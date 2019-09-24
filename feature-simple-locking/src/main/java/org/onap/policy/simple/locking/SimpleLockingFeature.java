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

import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.onap.policy.common.utils.time.CurrentTime;
import org.onap.policy.drools.core.lock.Lock;
import org.onap.policy.drools.core.lock.LockCallback;
import org.onap.policy.drools.core.lock.LockImpl;
import org.onap.policy.drools.core.lock.PolicyResourceLockFeatureApi;
import org.onap.policy.drools.features.PolicyControllerFeatureApi;
import org.onap.policy.drools.features.PolicyEngineFeatureApi;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.drools.system.PolicyEngine;
import org.onap.policy.drools.utils.Extractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Simple implementation of a Lock Feature. Locks do not span across instances of this
 * object (i.e., locks do not span across servers).
 */
public class SimpleLockingFeature
                implements PolicyResourceLockFeatureApi, PolicyEngineFeatureApi, PolicyControllerFeatureApi {

    private static final Logger logger = LoggerFactory.getLogger(SimpleLockingFeature.class);

    /**
     * Property prefix for extractor definitions.
     */
    private static final String EXTRACTOR_PREFIX = "locking.extractor.";

    /**
     * Time, in minutes, to wait between checks for expired locks.
     */
    private static final long EXPIRE_CHECK_MIN = 15;

    /**
     * Provider of current time. May be overridden by junit tests.
     */
    private static CurrentTime currentTime = new CurrentTime();

    /**
     * Maps a resource to its data.
     */
    private final Map<String, Data> resource2data = new ConcurrentHashMap<>();

    /**
     * Used to extract the owner key from the ownerInfo.
     */
    private final Extractor extractor = new Extractor();

    /**
     * Thread pool used to check for lock expiration and to notify owners when locks are
     * granted or lost.
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

    /**
     * Initializes {@link #exsvc}, if it hasn't been initialized yet, scheduling periodic
     * checks for expired locks.
     */
    private synchronized void init() {
        if (exsvc == null) {
            exsvc = makeThreadPool();
            exsvc.scheduleWithFixedDelay(this::checkExpired, EXPIRE_CHECK_MIN, EXPIRE_CHECK_MIN, TimeUnit.MINUTES);
        }
    }

    @Override
    public PolicyController beforeCreate(String name, Properties properties) {
        extractor.register(properties, EXTRACTOR_PREFIX);
        return null;
    }

    /**
     * Shuts down the thread pool.
     */
    @Override
    public boolean beforeShutdown(PolicyEngine engine) {
        if (exsvc != null) {
            exsvc.shutdown();
        }

        return false;
    }

    @Override
    public Lock lock(String resourceId, Object ownerInfo, LockCallback callback, int holdSec, boolean waitForLock) {

        // lazy initialization
        init();

        SimpleLock lock = new SimpleLock(Lock.State.WAITING, resourceId, ownerInfo, callback, holdSec);

        resource2data.compute(resourceId, (key, curdata) -> {
            if (curdata == null) {
                // no data yet - implies that the lock is available
                lock.setState(Lock.State.ACTIVE);
                return new Data(lock);
            }

            if (waitForLock) {
                curdata.add(lock);
            }

            return curdata;
        });


        if (lock.getState() == Lock.State.ACTIVE) {
            logger.info("lock granted: {}", lock);
            callback.lockAvailable(lock);

        } else if (waitForLock) {
            logger.info("waiting for lock: {}", lock);

        } else {
            // based on the logic above, the state must be WAITING

            // lock is permanently unavailable since invoker does not want to wait
            logger.info("lock denied: {}", lock);
            lock.setState(Lock.State.BUSY);
            callback.lockUnavailable(lock);
        }

        return lock;
    }

    /**
     * Checks for expired locks.
     */
    private void checkExpired() {
        long currentMs = currentTime.getMillis();
        logger.info("checking for expired locks at {}", currentMs);

        for (Entry<String, Data> ent : resource2data.entrySet()) {
            if (!ent.getValue().expired(currentMs)) {
                continue;
            }

            resource2data.compute(ent.getKey(), (resourceId, data) -> {
                if (data != null && data.expired(currentMs)) {
                    logger.info("released expired lock: {}", data.owner);
                    return data.nextLock(ent.getKey());
                }

                return data;
            });
        }
    }

    /**
     * Data about a resource.
     */
    protected class Data {
        /**
         * Lock that currently owns the resource.
         */
        private SimpleLock owner;

        /**
         * Locks waiting to lock the resource.
         */
        private Queue<SimpleLock> waiting = new LinkedList<>();


        /**
         * Constructs the object.
         *
         * @param lock lock that currently owns the resource
         */
        public Data(SimpleLock lock) {
            owner = lock;
        }

        /**
         * Determines if the owner's lock has expired.
         *
         * @param currentMs current time, in milliseconds
         * @return {@code true} if the owner's lock has expired, {@code false} otherwise
         */
        public boolean expired(long currentMs) {
            return owner.expired(currentMs);
        }

        /**
         * Adds a lock to a resource's wait queue.
         *
         * @param lock lock to be added
         */
        public void add(SimpleLock lock) {
            waiting.add(lock);
        }

        /**
         * Removes a lock from a resource's wait queue.
         *
         * @param lock lock to be removed
         * @return {@code true} if the lock was removed, {@code false} if it was not in
         *         the queue
         */
        public boolean remove(SimpleLock lock) {
            return waiting.remove(lock);
        }

        /**
         * Sets the {@link #owner} to the first lock waiting in the queue.
         *
         * @param resourceId identifier of the resource for which the lock is being
         *        re-assigned
         *
         * @return {@code this}, if there was a lock waiting in the queue, {@code null}
         *         otherwise
         */
        public Data nextLock(String resourceId) {
            SimpleLock lock = waiting.poll();
            if (lock == null) {
                // nothing in the queue - this is no longer needed
                logger.info("no locks waiting for: {}", resourceId);
                return null;
            }

            lock.setState(Lock.State.ACTIVE);
            lock.setHoldUntil();
            logger.info("lock re-assigned: {}", lock);

            lock.notifyAvailable();

            owner = lock;

            return this;
        }
    }

    /**
     * Simple Lock implementation.
     */
    protected class SimpleLock extends LockImpl {

        /**
         * Time, in milliseconds, when the lock expires.
         */
        private long holdUntilMs;


        /**
         * Constructs the object.
         *
         * @param state the initial lock state
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

            setHoldUntil();
        }

        /**
         * Determines if the lock has expired.
         *
         * @param currentMs current time, in milliseconds
         * @return {@code true} if the lock has expired, {@code false} otherwise
         */
        public boolean expired(long currentMs) {
            return (holdUntilMs <= currentMs);
        }

        @Override
        public boolean free() {
            // do a quick check of the state
            State curstate = getState();
            if (curstate != Lock.State.ACTIVE && curstate != Lock.State.WAITING) {
                return false;
            }

            logger.info("releasing lock: {}", this);

            AtomicBoolean result = new AtomicBoolean(false);

            resource2data.compute(getResourceId(), (resourceId, curdata) -> {
                if (curdata == null) {
                    return null;
                }

                if (curdata.owner == this) {
                    // this lock was the owner - give the resource to the next lock
                    result.set(true);
                    setState(Lock.State.FREE);
                    return curdata.nextLock(resourceId);

                } else {
                    // this lock wasn't the owner - maybe it's in the wait queue
                    result.set(curdata.remove(this));
                }

                return curdata;
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

            resource2data.compute(getResourceId(), (resourceId, curdata) -> {
                if (curdata == null) {
                    return null;
                }

                if (curdata.owner == this) {
                    result.set(true);
                    setHoldSec(holdSec);
                    setHoldUntil();
                }
                // else: this lock is not the owner - no change

                return curdata;
            });

            return result.get();
        }

        /**
         * Invokes the {@link LockCallback#lockAvailable(Lock)} method, in a background
         * thread.
         */
        private void notifyAvailable() {
            exsvc.execute(() -> getCallback().lockAvailable(this));
        }

        @Override
        public SimpleLock notifyUnavailable() {
            exsvc.execute(() -> getCallback().lockUnavailable(this));
            return this;
        }

        /**
         * Sets the time, {@link #holdUntilMs}, when the lock will expire.
         */
        private void setHoldUntil() {
            holdUntilMs = currentTime.getMillis() + getHoldSec();
        }
    }

    // these may be overridden by junit tests

    protected ScheduledExecutorService makeThreadPool() {
        return Executors.newScheduledThreadPool(1);
    }
}
