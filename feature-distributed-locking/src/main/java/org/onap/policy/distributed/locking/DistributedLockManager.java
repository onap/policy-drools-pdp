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
import java.sql.SQLTransientException;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.dbcp2.BasicDataSourceFactory;
import org.onap.policy.common.utils.network.NetworkUtil;
import org.onap.policy.drools.core.lock.LockCallback;
import org.onap.policy.drools.core.lock.LockState;
import org.onap.policy.drools.core.lock.PolicyResourceLockManager;
import org.onap.policy.drools.features.PolicyEngineFeatureApi;
import org.onap.policy.drools.persistence.SystemPersistenceConstants;
import org.onap.policy.drools.system.PolicyEngine;
import org.onap.policy.drools.system.PolicyEngineConstants;
import org.onap.policy.drools.system.internal.FeatureLockImpl;
import org.onap.policy.drools.system.internal.LockManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Distributed implementation of the Lock Feature. Maintains locks across servers using a
 * shared DB.
 *
 * <p/>
 * Note: this implementation does <i>not</i> honor the waitForLocks={@code true}
 * parameter.
 *
 * <p/>
 * Additional Notes:
 * <dl>
 * <li>The <i>owner</i> field in the DB is not derived from the lock's owner info, but is
 * instead populated with the {@link #uuidString}.</li>
 * <li>A periodic check of the DB is made to determine if any of the locks have
 * expired.</li>
 * <li>When a lock is deserialized, it will not initially appear in this feature's map; it
 * will be added to the map once free() or extend() is invoked, provided there isn't
 * already an entry. In addition, it initially has the host and UUID of the feature
 * instance that created it. However, as soon as doExtend() completes successfully, the
 * host and UUID of the lock will be updated to reflect the values within this feature
 * instance.</li>
 * </dl>
 */
public class DistributedLockManager extends LockManager<DistributedLockManager.DistributedLock>
                implements PolicyEngineFeatureApi {

    private static final Logger logger = LoggerFactory.getLogger(DistributedLockManager.class);

    private static final String CONFIGURATION_PROPERTIES_NAME = "feature-distributed-locking";

    @Getter(AccessLevel.PROTECTED)
    @Setter(AccessLevel.PROTECTED)
    private static DistributedLockManager latestInstance = null;


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
    private final Map<String, DistributedLock> resource2lock;

    /**
     * Thread pool used to check for lock expiration and to notify owners when locks are
     * granted or lost.
     */
    private ScheduledExecutorService exsvc = null;

    /**
     * Used to cancel the expiration checker on shutdown.
     */
    private ScheduledFuture<?> checker = null;

    /**
     * Feature properties.
     */
    private DistributedLockProperties featProps;

    /**
     * Data source used to connect to the DB.
     */
    private BasicDataSource dataSource = null;


    /**
     * Constructs the object.
     */
    public DistributedLockManager() {
        this.hostName = NetworkUtil.getHostname();
        this.resource2lock = getResource2lock();
    }

    @Override
    public int getSequenceNumber() {
        return 1000;
    }

    @Override
    public PolicyResourceLockManager beforeCreateLockManager(PolicyEngine engine, Properties properties) {

        try {
            this.featProps = new DistributedLockProperties(getProperties(CONFIGURATION_PROPERTIES_NAME));
            this.dataSource = makeDataSource();

            return this;

        } catch (Exception e) {
            throw new DistributedLockManagerException(e);
        }
    }

    @Override
    public boolean afterStart(PolicyEngine engine) {

        try {
            exsvc = PolicyEngineConstants.getManager().getExecutorService();
            exsvc.execute(this::deleteExpiredDbLocks);
            checker = exsvc.schedule(this::checkExpired, featProps.getExpireCheckSec(), TimeUnit.SECONDS);

            setLatestInstance(this);

        } catch (Exception e) {
            throw new DistributedLockManagerException(e);
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
     * Closes the data source. Does <i>not</i> invoke any lock call-backs.
     */
    @Override
    public boolean afterStop(PolicyEngine engine) {
        exsvc = null;

        if (checker != null) {
            checker.cancel(true);
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
    protected boolean hasInstanceChanged() {
        return (getLatestInstance() != this);
    }

    @Override
    protected void finishLock(DistributedLock lock) {
        lock.scheduleRequest(lock::doLock);
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

            checker = exsvc.schedule(this::checkExpired, featProps.getExpireCheckSec(), TimeUnit.SECONDS);

        } catch (RejectedExecutionException e) {
            logger.warn("thread pool is no longer accepting requests", e);

        } catch (SQLException | RuntimeException e) {
            logger.error("error checking expired locks", e);

            if (isAlive()) {
                checker = exsvc.schedule(this::checkExpired, featProps.getRetrySec(), TimeUnit.SECONDS);
            }
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
        /*
         * We could query for host and UUIDs that actually appear within the locks, but
         * those might change while the query is running so no real value in doing that.
         * On the other hand, there's only a brief instance between the time a
         * deserialized lock is added to this feature instance and its doExtend() method
         * updates its host and UUID to match this feature instance. If this happens to
         * run during that brief instance, then the lock will be lost and the callback
         * invoked. It isn't worth complicating this code further to handle those highly
         * unlikely cases.
         */

        // @formatter:off
        try (Connection conn = dataSource.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(
                        "SELECT resourceId FROM pooling.locks WHERE host=? AND owner=? AND expirationTime > now()")) {
            // @formatter:on

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
                lock.deny(FeatureLockImpl.LOCK_LOST_MSG);
            }
        }
    }

    /**
     * Distributed Lock implementation.
     */
    public static class DistributedLock extends FeatureLockImpl {
        private static final String SQL_FAILED_MSG = "request failed for lock: {}";

        private static final long serialVersionUID = 1L;

        /**
         * Feature containing this lock. May be {@code null} until the feature is
         * identified. Note: this can only be null if the lock has been de-serialized.
         */
        private transient DistributedLockManager feature;

        /**
         * Host name from the feature instance that created this object. Replaced with the
         * host name from the current feature instance whenever the lock is successfully
         * extended.
         */
        private String hostName;

        /**
         * UUID string from the feature instance that created this object. Replaced with
         * the UUID string from the current feature instance whenever the lock is
         * successfully extended.
         */
        private String uuidString;

        /**
         * {@code True} if the lock is busy making a request, {@code false} otherwise.
         */
        private transient boolean busy = false;

        /**
         * Request to be performed.
         */
        private transient RunnableWithEx request = null;

        /**
         * Number of times we've retried a request.
         */
        private transient int nretries = 0;

        /**
         * Constructs the object.
         */
        public DistributedLock() {
            this.feature = null;
            this.hostName = "";
            this.uuidString = "";
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
        public DistributedLock(LockState state, String resourceId, String ownerKey, int holdSec, LockCallback callback,
                        DistributedLockManager feature) {
            super(state, resourceId, ownerKey, holdSec, callback);

            this.feature = feature;
            this.hostName = feature.hostName;
            this.uuidString = feature.uuidString;
        }

        @Override
        public boolean free() {
            if (!freeAllowed()) {
                return false;
            }

            AtomicBoolean result = new AtomicBoolean(false);

            feature.resource2lock.computeIfPresent(getResourceId(), (resourceId, curlock) -> {
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
            if (!extendAllowed(holdSec, callback)) {
                return;
            }

            AtomicBoolean success = new AtomicBoolean(false);

            feature.resource2lock.computeIfPresent(getResourceId(), (resourceId, curlock) -> {
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
                deny(NOT_LOCKED_MSG);
            }
        }

        @Override
        protected boolean addToFeature() {
            feature = getLatestInstance();
            if (feature == null) {
                logger.warn("no feature yet for {}", this);
                return false;
            }

            // put this lock into the map
            feature.resource2lock.putIfAbsent(getResourceId(), this);

            return true;
        }

        /**
         * Schedules a request for execution.
         *
         * @param schedreq the request that should be scheduled
         */
        private synchronized void scheduleRequest(RunnableWithEx schedreq) {
            logger.debug("schedule lock action {}", this);
            nretries = 0;
            request = schedreq;
            getThreadPool().execute(this::doRequest);
        }

        /**
         * Reschedules a request for execution, if there is not already a request in the
         * queue, and if the retry count has not been exhausted.
         *
         * @param req request to be rescheduled
         */
        private void rescheduleRequest(RunnableWithEx req) {
            synchronized (this) {
                if (request != null) {
                    // a new request has already been scheduled - it supersedes "req"
                    logger.debug("not rescheduling lock action {}", this);
                    return;
                }

                if (nretries++ < feature.featProps.getMaxRetries()) {
                    logger.debug("reschedule for {}s {}", feature.featProps.getRetrySec(), this);
                    request = req;
                    getThreadPool().schedule(this::doRequest, feature.featProps.getRetrySec(), TimeUnit.SECONDS);
                    return;
                }
            }

            logger.warn("retry count {} exhausted for lock: {}", feature.featProps.getMaxRetries(), this);
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
        private synchronized RunnableWithEx getNextRequest(RunnableWithEx prevReq) {
            if (request == null || request == prevReq) {
                logger.debug("no more requests for {}", this);
                busy = false;
                return null;
            }

            RunnableWithEx req = request;
            request = null;

            return req;
        }

        /**
         * Executes the current request, if none are currently executing.
         */
        private void doRequest() {
            synchronized (this) {
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
                if (feature.resource2lock.get(getResourceId()) != this) {
                    /*
                     * no longer in the map - don't apply the action, as it may interfere
                     * with any newly added Lock object
                     */
                    logger.debug("discard lock action {}", this);
                    synchronized (this) {
                        busy = false;
                    }
                    return;
                }

                try {
                    /*
                     * Run the request. If it throws an exception, then it will be
                     * rescheduled for execution a little later.
                     */
                    req.run();

                } catch (SQLException e) {
                    logger.warn(SQL_FAILED_MSG, this, e);

                    if (e.getCause() instanceof SQLTransientException) {
                        // retry the request a little later
                        rescheduleRequest(req);
                    } else {
                        removeFromMap();
                    }

                } catch (RuntimeException e) {
                    logger.warn(SQL_FAILED_MSG, this, e);
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
            if (!isWaiting()) {
                logger.debug("discard doLock {}", this);
                return;
            }

            /*
             * There is a small window in which a client could invoke free() before the DB
             * is updated. In that case, doUnlock will be added to the queue to run after
             * this, which will delete the record, as desired. In addition, grant() will
             * not do anything, because the lock state will have been set to UNAVAILABLE
             * by free().
             */

            logger.debug("doLock {}", this);
            try (Connection conn = feature.dataSource.getConnection()) {
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
            try (Connection conn = feature.dataSource.getConnection()) {
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
            if (!isWaiting()) {
                logger.debug("discard doExtend {}", this);
                return;
            }

            /*
             * There is a small window in which a client could invoke free() before the DB
             * is updated. In that case, doUnlock will be added to the queue to run after
             * this, which will delete the record, as desired. In addition, grant() will
             * not do anything, because the lock state will have been set to UNAVAILABLE
             * by free().
             */

            logger.debug("doExtend {}", this);
            try (Connection conn = feature.dataSource.getConnection()) {
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
                stmt.setString(2, feature.hostName);
                stmt.setString(3, feature.uuidString);
                stmt.setInt(4, getHoldSec());

                stmt.executeUpdate();

                this.hostName = feature.hostName;
                this.uuidString = feature.uuidString;

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
                stmt.setString(2, feature.hostName);
                stmt.setString(3, feature.uuidString);
                stmt.setInt(4, getHoldSec());

                stmt.setString(5, getResourceId());
                stmt.setString(6, this.hostName);
                stmt.setString(7, this.uuidString);

                if (stmt.executeUpdate() != 1) {
                    return false;
                }

                this.hostName = feature.hostName;
                this.uuidString = feature.uuidString;

                return true;
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
            try (PreparedStatement stmt = conn
                            .prepareStatement("DELETE FROM pooling.locks WHERE resourceId=? AND host=? AND owner=?")) {

                stmt.setString(1, getResourceId());
                stmt.setString(2, this.hostName);
                stmt.setString(3, this.uuidString);

                stmt.executeUpdate();
            }
        }

        /**
         * Removes the lock from the map, and sends a notification using the current
         * thread.
         */
        private void removeFromMap() {
            logger.debug("remove lock from map {}", this);
            feature.resource2lock.remove(getResourceId(), this);

            synchronized (this) {
                if (!isUnavailable()) {
                    deny(LOCK_LOST_MSG);
                }
            }
        }

        @Override
        public String toString() {
            return "DistributedLock [state=" + getState() + ", resourceId=" + getResourceId() + ", ownerKey="
                            + getOwnerKey() + ", holdSec=" + getHoldSec() + ", hostName=" + hostName + ", uuidString="
                            + uuidString + "]";
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

    protected DistributedLock makeLock(LockState state, String resourceId, String ownerKey, int holdSec,
                    LockCallback callback) {
        return new DistributedLock(state, resourceId, ownerKey, holdSec, callback, this);
    }
}
