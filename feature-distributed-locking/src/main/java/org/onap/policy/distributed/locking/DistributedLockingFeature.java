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

package org.onap.policy.distributed.locking;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * Distributed implementation of the Lock Feature. Maintains locks across servers using a
 * shared DB.
 */
public class DistributedLockingFeature
                implements PolicyResourceLockFeatureApi, PolicyEngineFeatureApi, PolicyControllerFeatureApi {

    private static final Logger logger = LoggerFactory.getLogger(DistributedLockingFeature.class);

    /**
     * Property prefix for extractor definitions.
     */
    private static final String EXTRACTOR_PREFIX = "locking.extractor.";

    /**
     * Time, in minutes, to wait between checks for expired locks.
     */
    private static final long EXPIRE_CHECK_MIN = 15;

    /**
     * Maximum number of threads in the thread pool.
     */
    // TODO move this to a property
    private static final int MAX_THREADS = 5;

    /**
     * UUID of this object.
     */
    private final UUID uuid = UUID.randomUUID();

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
    public DistributedLockingFeature() {
        super();
    }

    @Override
    public int getSequenceNumber() {
        // low priority, but still higher than the default implementation
        return 10;
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

        DistributedLock lock = new DistributedLock(Lock.State.WAITING, resourceId, ownerInfo, callback, holdSec);

        resource2data.compute(resourceId, (key, curdata) -> {
            if (curdata == null) {
                // no data yet - attempt a DB update
                return new Data(lock);
            }

            if (waitForLock) {
                curdata.add(lock);

            } else {
                lock.deny("resource is busy");
            }

            return curdata;
        });

        return lock;
    }

    /**
     * Checks for expired locks.
     */
    private void checkExpired() {
        Set<String> unseen = new HashSet<>(resource2data.keySet());

        // TODO don't enter this method twice at the same time
        // TODO handle retries when the DB connection fails

        getDbLocks(unseen);

        // process the ones that weren't seen - these can attempt to get the lock now
        for (String resourceId : unseen) {
            resource2data.compute(resourceId, (key, data) -> {
                if (data == null) {
                    return null;
                }

                if (data.owner != null) {
                    data.owner.deny("lock lost");
                    data.owner = null;
                }

                if (data.waiting.isEmpty()) {
                    return null;
                }

                exsvc.execute(data.waiting.peek()::requestLock);

                return data;
            });
        }
    }

    protected void getDbLocks(Set<String> unseen) {
        // TODO scan DB, identifying the resources that are still locked
        for (;;) {
            String resourceId = "";
            UUID dbuuid = UUID.randomUUID();

            if (!unseen.remove(resourceId)) {
                continue;
            }

            resource2data.compute(resourceId, (key, data) -> {
                if (data == null || data.owner == null) {
                    return data;
                }

                if (!uuid.equals(dbuuid)) {
                    data.owner.deny("lock lost");
                    data.owner = null;
                    if (data.waiting.isEmpty()) {
                        return null;
                    }
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
         * Lock that currently owns the resource, or {@code null} if the lock is currently
         * owned by another UUID.
         */
        private DistributedLock owner = null;

        /**
         * Locks waiting to lock the resource.
         */
        private Queue<DistributedLock> waiting = new LinkedList<>();


        /**
         * Constructs the object.
         *
         * @param lock lock that wants the resource
         */
        public Data(DistributedLock lock) {
            add(lock);
            exsvc.execute(lock::requestLock);
        }

        /**
         * Adds a lock to a resource's wait queue.
         *
         * @param lock lock to be added
         */
        public void add(DistributedLock lock) {
            logger.info("waiting for lock: {}", lock);
            waiting.add(lock);
        }

        /**
         * Removes a lock from a resource's wait queue.
         *
         * @param lock lock to be removed
         * @return {@code true} if the lock was removed, {@code false} if it was not in
         *         the queue
         */
        public boolean remove(DistributedLock lock) {
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

            if ((owner = waiting.poll()) == null) {
                // nothing in the queue - this is no longer needed
                logger.info("no locks waiting for: {}", resourceId);
                return null;
            }

            // set the hold time for the new lock
            exsvc.execute(owner::requestExtension);

            return this;
        }
    }

    /**
     * Distributed Lock implementation.
     */
    protected class DistributedLock extends LockImpl {

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
        public DistributedLock(State state, String resourceId, Object ownerInfo, LockCallback callback, int holdSec) {

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
         * Grants this lock.
         */
        protected void grant() {
            setState(Lock.State.ACTIVE);

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
                    setState(Lock.State.UNAVAILABLE);
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
                    setState(Lock.State.EXTENDING);
                    setHoldSec(holdSec);
                    exsvc.execute(this::requestExtension);
                }
                // else: this lock is not the owner - no change

                return curdata;
            });

            return result.get();
        }

        private void requestLock() {
            // TODO insert or update a record in the DB
            // TODO make two attempts

        }

        private void requestExtension() {
            // TODO update the hold time on the DB
            // TODO make two attempts

        }

        @Override
        public DistributedLock notifyAvailable() {
            exsvc.execute(() -> getCallback().lockAvailable(this));
            return this;
        }

        @Override
        public DistributedLock notifyUnavailable() {
            exsvc.execute(() -> getCallback().lockUnavailable(this));
            return this;
        }
    }

    // these may be overridden by junit tests

    protected ScheduledExecutorService makeThreadPool() {
        return Executors.newScheduledThreadPool(MAX_THREADS);
    }
}
