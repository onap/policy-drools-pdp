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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Getter;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.dbcp2.BasicDataSourceFactory;
import org.onap.policy.common.utils.network.NetworkUtil;
import org.onap.policy.drools.core.Extractor;
import org.onap.policy.drools.core.lock.LockCallback;
import org.onap.policy.drools.core.lock.LockImpl;
import org.onap.policy.drools.core.lock.LockState;
import org.onap.policy.drools.core.lock.PolicyResourceLockFeatureApi;
import org.onap.policy.drools.features.PolicyControllerFeatureApi;
import org.onap.policy.drools.features.PolicyEngineFeatureApi;
import org.onap.policy.drools.persistence.SystemPersistenceConstants;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.drools.system.PolicyEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Distributed implementation of the Lock Feature. Maintains locks across servers using a
 * shared DB.
 *
 * <p/>
 * Notes:
 * <dl>
 * <li>This implementation does not honor the waitForLocks={@code true} parameter.</li>
 * <li>The <i>owner</i> field in the DB is not derived from the lock's owner info, but is
 * instead populated with the {@link #uuidString}.</li>
 * <li>A periodic check of the DB is made to determine if any of the locks have expired.</li>
 * </dl>
 */
public class DistributedLockingFeature
                implements PolicyResourceLockFeatureApi, PolicyEngineFeatureApi, PolicyControllerFeatureApi {

    private static final Logger logger = LoggerFactory.getLogger(DistributedLockingFeature.class);

    private static final String CONFIGURATION_PROPERTIES_NAME = "feature-distributed-locking";
    private static final String LOCK_LOST_MSG = "lock lost";
    private static final String NOT_LOCKED_MSG = "not locked";

    /**
     * Property prefix for extractor definitions.
     */
    public static final String EXTRACTOR_PREFIX = "locking.extractor.";

    /**
     * Feature properties.
     */
    private DistributedLockingProperties featProps;


    /**
     * Name of the host on which this JVM is running.
     */
    @Getter
    private final String hostName;

    /**
     * UUID of this object.
     */
    @Getter
    private final String uuidString = UUID.randomUUID().toString();

    /**
     * Maps a resource to the lock that owns it, or is awaiting a request for it. Once a
     * lock is added to the map, it remains in the map until the lock is lost or until the
     * unlock request completes.
     */
    private final Map<String, DistributedLock> resource2lock = new ConcurrentHashMap<>();

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
     * Data source used to connect to the DB.
     */
    private BasicDataSource dataSource;


    /**
     * Constructs the object.
     */
    public DistributedLockingFeature() {
        hostName = NetworkUtil.getHostname();
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

    @Override
    public PolicyController beforeCreate(String name, Properties properties) {
        extractor.register(properties, EXTRACTOR_PREFIX);
        return null;
    }

    @Override
    public boolean afterStart(PolicyEngine engine) {

        try {
            featProps = new DistributedLockingProperties(getProperties(CONFIGURATION_PROPERTIES_NAME));
            dataSource = makeDataSource();

            exsvc = makeThreadPool(featProps.getMaxThreads());
            exsvc.execute(this::deleteExpiredDbLocks);
            exsvc.schedule(this::checkExpired, featProps.getExpireCheckSec(), TimeUnit.SECONDS);

        } catch (Exception e) {
            closeDataSource();
            throw new DistributedLockingFeatureException(e);
        }

        return false;
    }

    /**
     * Make data source.
     *
     * @return a new, pooled data source
     * @throws Exception exception
     */
    protected BasicDataSource makeDataSource() throws Exception {
        Properties props = new Properties();
        props.put("driverClassName", featProps.getDbDriver());
        props.put("url", featProps.getDbUrl());
        props.put("username", featProps.getDbUser());
        props.put("password", featProps.getDbPwd());
        props.put("testOnBorrow", "true");
        props.put("poolPreparedStatements", "true");

        // additional properties are listed in the GenericObjectPool API

        return BasicDataSourceFactory.createDataSource(props);
    }

    /**
     * Deletes expired locks from the DB.
     */
    private void deleteExpiredDbLocks() {
        logger.info("deleting all expired locks from the DB");

        try (Connection conn = dataSource.getConnection();
                        PreparedStatement stmt = conn
                                        .prepareStatement("DELETE FROM pooling.locks WHERE expirationTime <= now()")) {

            int ndel = stmt.executeUpdate();
            logger.info("deleted {} expired locks from the DB", ndel);

        } catch (SQLException e) {
            logger.warn("failed to delete expired locks from the DB", e);
        }
    }

    /**
     * Shuts down the thread pool.
     */
    @Override
    public boolean beforeShutdown(PolicyEngine engine) {
        if (exsvc != null) {
            exsvc.shutdown();
            exsvc = null;
        }

        closeDataSource();

        return false;
    }

    /**
     * Closes {@link #dataSource} and sets it to {@code null}.
     */
    private void closeDataSource() {
        try {
            if (dataSource != null) {
                dataSource.close();
            }

        } catch (SQLException e) {
            logger.error("cannot close the distributed locking DB", e);
        }

        dataSource = null;
    }

    @Override
    public DistributedLock lock(String resourceId, Object ownerInfo, int holdSec, LockCallback callback,
                    boolean waitForLock) {

        DistributedLock lock = makeLock(LockState.WAITING, resourceId, ownerInfo, holdSec, callback);

        DistributedLock existingLock = resource2lock.putIfAbsent(resourceId, lock);

        // do these outside of compute() to avoid blocking other map operations
        if (existingLock == null) {
            logger.debug("added lock to map {}", lock);
            lock.scheduleRequest(lock::doLock);
        } else {
            lock.deny("resource is busy", true);
        }

        return lock;
    }

    /**
     * Checks for expired locks.
     */
    private void checkExpired() {

        try {
            logger.info("checking for expired locks");
            Set<String> expiredIds = new HashSet<>(resource2lock.keySet());
            identifyDbLocks(expiredIds);
            expireLocks(expiredIds);

            exsvc.schedule(this::checkExpired, featProps.getExpireCheckSec(), TimeUnit.SECONDS);

        } catch (RejectedExecutionException e) {
            logger.warn("thread pool is no longer accepting requests", e);

        } catch (SQLException | RuntimeException e) {
            logger.error("error checking expired locks", e);
            exsvc.schedule(this::checkExpired, featProps.getRetrySec(), TimeUnit.SECONDS);
        }

        logger.info("done checking for expired locks");
    }

    /**
     * Identifies this feature instance's locks that the DB indicates are still active.
     *
     * @param expiredIds IDs of resources that have expired locks. If a resource is still
     *        locked, it's ID is removed from this set
     * @throws SQLException if a DB error occurs
     */
    private void identifyDbLocks(Set<String> expiredIds) throws SQLException {
        try (Connection conn = dataSource.getConnection();
                        PreparedStatement stmt = conn.prepareStatement("SELECT resourceId FROM pooling.locks "
                                        + "WHERE host=? AND owner=? AND expirationTime > now()")) {

            stmt.setString(1, hostName);
            stmt.setString(2, uuidString);

            try (ResultSet resultSet = stmt.executeQuery()) {
                while (resultSet.next()) {
                    String resourceId = resultSet.getString(1);

                    // we have now seen this resource id
                    expiredIds.remove(resourceId);
                }
            }
        }
    }

    /**
     * Expires locks for the resources that no longer appear within the DB.
     *
     * @param expiredIds IDs of resources that have expired locks
     */
    private void expireLocks(Set<String> expiredIds) {
        for (String resourceId : expiredIds) {
            AtomicReference<DistributedLock> lockref = new AtomicReference<>(null);

            resource2lock.computeIfPresent(resourceId, (key, lock) -> {
                if (lock.isActive()) {
                    // it thinks it's active, but it isn't - remove from the map
                    lockref.set(lock);
                    return null;
                }

                return lock;
            });

            DistributedLock lock = lockref.get();
            if (lock != null) {
                logger.debug("removed lock from map {}", lock);
                lock.deny(LOCK_LOST_MSG, false);
            }
        }
    }

    /**
     * Distributed Lock implementation.
     */
    protected class DistributedLock extends LockImpl {

        /**
         * Lock on which to synchronize when updating the following fields.
         */
        private Object locket = new Object();

        /**
         * {@code True} if the lock is busy making a request, {@code false} otherwise.
         */
        private boolean busy = false;

        /**
         * Request to be performed.
         */
        private RunnableWithEx request = null;

        /**
         * Number of times we've retried a request.
         */
        private int nretries = 0;

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
         */
        public DistributedLock(LockState state, String resourceId, Object ownerInfo, int holdSec,
                        LockCallback callback) {

            /*
             * Get the owner key via the extractor. This is only used for logging, so OK
             * if it fails (i.e., returns null).
             */
            super(state, resourceId, ownerInfo, extractor.extract(ownerInfo), holdSec, callback, true);

            if (holdSec < 0) {
                throw new IllegalArgumentException("holdSec is negative");
            }
        }

        /**
         * Grants this lock.
         */
        protected void grant() {
            setState(LockState.ACTIVE);

            logger.info("lock granted: {}", this);

            notifyAvailable();
        }

        /**
         * Permanently denies this lock.
         *
         * @param reason the reason the lock was denied
         * @param foreground {@code true} if the callback can be invoked in the current
         *        (i.e., foreground) thread, {@code false} if it should be invoked via the
         *        executor
         */
        protected void deny(String reason, boolean foreground) {
            setState(LockState.UNAVAILABLE);

            logger.info("{}: {}", reason, this);

            if (foreground) {
                notifyUnavailable();

            } else {
                exsvc.execute(this::notifyUnavailable);
            }
        }

        @Override
        public boolean free() {
            // do a quick check of the state
            if (isUnavailable()) {
                return false;
            }

            logger.info("releasing lock: {}", this);

            AtomicBoolean result = new AtomicBoolean(false);

            resource2lock.computeIfPresent(getResourceId(), (resourceId, curlock) -> {
                if (curlock == this && !isUnavailable()) {
                    // this lock was the owner
                    result.set(true);
                    setState(LockState.UNAVAILABLE);

                    /*
                     * NOTE: do NOT return null; curlock must remain until doUnlock
                     * completes.
                     */
                }

                return curlock;
            });

            if (result.get()) {
                scheduleRequest(this::doUnlock);
                return true;
            }

            return false;
        }

        @Override
        public void extend(int holdSec, LockCallback callback) {
            if (holdSec < 0) {
                throw new IllegalArgumentException("holdSec is negative");
            }

            setHoldSec(holdSec);
            setCallback(callback);

            // do a quick check of the state
            if (isUnavailable()) {
                deny(NOT_LOCKED_MSG, true);
                return;
            }

            AtomicBoolean success = new AtomicBoolean(false);

            resource2lock.computeIfPresent(getResourceId(), (resourceId, curlock) -> {
                if (curlock == this && !isUnavailable()) {
                    success.set(true);
                    setState(LockState.WAITING);
                }

                // note: leave it in the map until doUnlock() removes it

                return curlock;
            });

            if (success.get()) {
                scheduleRequest(this::doExtend);

            } else {
                deny(NOT_LOCKED_MSG, true);
            }
        }

        /**
         * Schedules a request for execution.
         *
         * @param schedreq the request that should be scheduled
         */
        private void scheduleRequest(RunnableWithEx schedreq) {
            synchronized (locket) {
                logger.debug("schedule lock action {}", this);
                nretries = 0;
                request = schedreq;
                exsvc.execute(this::doRequest);
            }
        }

        /**
         * Reschedules a request for execution, if there is not already a request in the
         * queue, and if the retry count has not been exhausted.
         *
         * @param req request to be rescheduled
         */
        private void rescheduleRequest(RunnableWithEx req) {
            synchronized (locket) {
                if (request != null) {
                    // a new request has already been scheduled - it supersedes "req"
                    logger.debug("not rescheduling lock action {}", this);
                    return;
                }

                if (nretries++ < featProps.getMaxRetries()) {
                    logger.debug("reschedule for {}s {}", featProps.getRetrySec(), this);
                    request = req;
                    exsvc.schedule(this::doRequest, featProps.getRetrySec(), TimeUnit.SECONDS);
                    return;
                }
            }

            logger.warn("retry count {} exhausted for lock: {}", featProps.getMaxRetries(), this);
            removeFromMap();
        }

        /**
         * Gets, and removes, the next request from the queue. Clears {@link #busy} if
         * there are no more requests in the queue.
         *
         * @param prevReq the previous request that was just run
         *
         * @return the next request, or {@code null} if the queue is empty
         */
        private RunnableWithEx getNextRequest(RunnableWithEx prevReq) {
            synchronized (locket) {
                if (request == null || request == prevReq) {
                    logger.debug("no more requests for {}", this);
                    busy = false;
                    return null;
                }

                RunnableWithEx req = request;
                request = null;

                return req;
            }
        }

        /**
         * Executes the current request, if none are currently executing.
         */
        private void doRequest() {
            synchronized (locket) {
                if (busy) {
                    // another thread is already processing the request(s)
                    return;
                }
                busy = true;
            }

            /*
             * There is a race condition wherein this thread could invoke run() while the
             * next scheduled thread checks the busy flag and finds that work is being
             * done and returns, leaving the next work item in "request". In that case,
             * the next work item may never be executed, thus we use a loop here, instead
             * of just executing a single request.
             */
            RunnableWithEx req = null;
            while ((req = getNextRequest(req)) != null) {
                if (resource2lock.get(getResourceId()) != this) {
                    /*
                     * no longer in the map - don't apply the action, as it may interfere
                     * with any newly added Lock object
                     */
                    logger.debug("discard lock action {}", this);
                    return;
                }

                try {
                    /*
                     * Run the request. If it throws an exception, then it will be
                     * rescheduled for execution a little later.
                     */
                    req.run();

                } catch (SQLException e) {
                    logger.warn("request failed for lock: {}", this, e);

                    if (featProps.isTransient(e.getErrorCode())) {
                        // retry the request a little later
                        rescheduleRequest(req);
                    } else {
                        removeFromMap();
                    }

                } catch (RuntimeException e) {
                    logger.warn("request failed for lock: {}", this, e);
                    removeFromMap();
                }
            }
        }

        /**
         * Attempts to add a lock to the DB. Generates a callback, indicating success or
         * failure.
         *
         * @throws SQLException if a DB error occurs
         */
        private void doLock() throws SQLException {
            logger.debug("lock {}", this);
            try (Connection conn = dataSource.getConnection()) {
                boolean success = false;
                try {
                    success = doDbInsert(conn);

                } catch (SQLException e) {
                    logger.info("failed to insert lock record - attempting update: {}", this, e);
                    success = doDbUpdate(conn);
                }

                if (success) {
                    grant();
                    return;
                }
            }

            removeFromMap();
        }

        /**
         * Attempts to remove a lock from the DB. Does <i>not</i> generate a callback if
         * it fails, as this should only be executed in response to a call to
         * {@link #free()}.
         *
         * @throws SQLException if a DB error occurs
         */
        private void doUnlock() throws SQLException {
            logger.debug("unlock {}", this);
            try (Connection conn = dataSource.getConnection()) {
                doDbDelete(conn);
            }

            removeFromMap();
        }

        /**
         * Attempts to extend a lock in the DB. Generates a callback, indicating success
         * or failure.
         *
         * @throws SQLException if a DB error occurs
         */
        private void doExtend() throws SQLException {
            logger.debug("extend lock {}", this);
            try (Connection conn = dataSource.getConnection()) {
                /*
                 * invoker may have called extend() before free() had a chance to insert
                 * the record, thus we have to try to insert, if the update fails
                 */
                if (doDbUpdate(conn) || doDbInsert(conn)) {
                    grant();
                    return;
                }
            }

            removeFromMap();
        }

        /**
         * Inserts the lock into the DB.
         *
         * @param conn DB connection
         * @return {@code true} if a record was successfully inserted, {@code false}
         *         otherwise
         * @throws SQLException if a DB error occurs
         */
        protected boolean doDbInsert(Connection conn) throws SQLException {
            logger.debug("insert lock record {}", this);
            try (PreparedStatement stmt =
                            conn.prepareStatement("INSERT INTO pooling.locks (resourceId, host, owner, expirationTime) "
                                            + "values (?, ?, ?, timestampadd(second, ?, now()))")) {

                stmt.setString(1, getResourceId());
                stmt.setString(2, hostName);
                stmt.setString(3, uuidString);
                stmt.setInt(4, getHoldSec());

                stmt.executeUpdate();
                return true;
            }
        }

        /**
         * Updates the lock in the DB.
         *
         * @param conn DB connection
         * @return {@code true} if a record was successfully updated, {@code false}
         *         otherwise
         * @throws SQLException if a DB error occurs
         */
        protected boolean doDbUpdate(Connection conn) throws SQLException {
            logger.debug("update lock record {}", this);
            try (PreparedStatement stmt =
                            conn.prepareStatement("UPDATE pooling.locks SET resourceId=?, host=?, owner=?,"
                                            + " expirationTime=timestampadd(second, ?, now()) WHERE resourceId=?"
                                            + " AND ((host=? AND owner=?) OR expirationTime < now())")) {

                stmt.setString(1, getResourceId());
                stmt.setString(2, hostName);
                stmt.setString(3, uuidString);
                stmt.setInt(4, getHoldSec());

                stmt.setString(5, getResourceId());
                stmt.setString(6, hostName);
                stmt.setString(7, uuidString);

                return (stmt.executeUpdate() == 1);
            }
        }

        /**
         * Deletes the lock from the DB.
         *
         * @param conn DB connection
         * @throws SQLException if a DB error occurs
         */
        protected void doDbDelete(Connection conn) throws SQLException {
            logger.debug("delete lock record {}", this);
            try (PreparedStatement stmt =
                            conn.prepareStatement("DELETE pooling.locks WHERE resourceId=? AND host=? AND owner=?")) {

                stmt.setString(1, getResourceId());
                stmt.setString(2, hostName);
                stmt.setString(3, uuidString);

                stmt.executeUpdate();
            }
        }

        /**
         * Removes the lock from the map, and sends a notification using the current
         * thread.
         */
        private void removeFromMap() {
            resource2lock.computeIfPresent(getResourceId(), (key, curlock) -> {
                if (curlock == this) {
                    logger.debug("remove lock from map {}", this);
                    return null;
                }

                return curlock;
            });

            if (!isUnavailable()) {
                deny(LOCK_LOST_MSG, true);
            }
        }

        @Override
        public String toString() {
            return "DistributedLock [state=" + getState() + ", resourceId=" + getResourceId()
                            + ", ownerKey=" + getOwnerKey() + ", holdSec=" + getHoldSec() + "]";
        }
    }

    @FunctionalInterface
    private static interface RunnableWithEx {
        void run() throws SQLException;
    }

    // these may be overridden by junit tests

    protected Properties getProperties(String fileName) {
        return SystemPersistenceConstants.getManager().getProperties(fileName);
    }

    protected ScheduledExecutorService makeThreadPool(int nthreads) {
        return Executors.newScheduledThreadPool(nthreads);
    }

    protected DistributedLock makeLock(LockState state, String resourceId, Object ownerInfo, int holdSec,
                    LockCallback callback) {
        return new DistributedLock(state, resourceId, ownerInfo, holdSec, callback);
    }
}
