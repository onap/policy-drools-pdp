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
import java.util.Iterator;
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
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.dbcp2.BasicDataSourceFactory;
import org.onap.policy.common.utils.network.NetworkUtil;
import org.onap.policy.drools.core.lock.Lock;
import org.onap.policy.drools.core.lock.LockCallback;
import org.onap.policy.drools.core.lock.LockImpl;
import org.onap.policy.drools.core.lock.PolicyResourceLockFeatureApi;
import org.onap.policy.drools.features.PolicyControllerFeatureApi;
import org.onap.policy.drools.features.PolicyEngineFeatureApi;
import org.onap.policy.drools.persistence.SystemPersistenceConstants;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.drools.system.PolicyEngine;
import org.onap.policy.drools.utils.Extractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Distributed implementation of the Lock Feature. Maintains locks across servers using a
 * shared DB.
 *
 * <p/>
 * Note: this implementation does not honor the waitForLocks={@code true} parameter.
 */
public class DistributedLockingFeature
                implements PolicyResourceLockFeatureApi, PolicyEngineFeatureApi, PolicyControllerFeatureApi {

    private static final Logger logger = LoggerFactory.getLogger(DistributedLockingFeature.class);

    private static final String CONFIGURATION_PROPERTIES_NAME = "feature-distributed-locking";
    private static final String LOCK_LOST_MSG = "lock lost";
    private static final String HOST_NAME = NetworkUtil.getHostname();

    /**
     * Property prefix for extractor definitions.
     */
    private static final String EXTRACTOR_PREFIX = "locking.extractor.";

    /**
     * UUID of this object.
     */
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
     * Feature properties.
     */
    private DistributedLockingProperties featProps;

    /**
     * Thread pool used to check for lock expiration and to notify owners when locks are
     * granted or lost.
     */
    private ScheduledExecutorService exsvc = null;

    /**
     * Number of times we've attempted to check expired locks.
     */
    private int nattempts = 0;

    /**
     * Data source used to connect to the DB.
     */
    private BasicDataSource dataSource;


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

    @Override
    public PolicyController beforeCreate(String name, Properties properties) {
        extractor.register(properties, EXTRACTOR_PREFIX);
        return null;
    }

    @Override
    public boolean afterStart(PolicyEngine engine) {

        try {
            featProps = new DistributedLockingProperties(
                            SystemPersistenceConstants.getManager().getProperties(CONFIGURATION_PROPERTIES_NAME));
            dataSource = makeDataSource();

        } catch (Exception e) {
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
     * Shuts down the thread pool and deletes locks, owned by this feature, from the DB.
     */
    @Override
    public boolean beforeShutdown(PolicyEngine engine) {
        if (exsvc != null) {
            exsvc.shutdown();
        }

        try (Connection conn = dataSource.getConnection();
                        PreparedStatement stmt =
                                        conn.prepareStatement("DELETE FROM pooling.locks WHERE host=? AND owner=?")) {

            stmt.setString(1, HOST_NAME);
            stmt.setString(2, uuidString);

            stmt.executeUpdate();

        } catch (SQLException e) {
            logger.warn("cannot delete locks from the DB");
        }

        return false;
    }

    @Override
    public Lock lock(String resourceId, Object ownerInfo, LockCallback callback, int holdSec, boolean waitForLock) {

        // lazy initialization
        init();

        DistributedLock lock = new DistributedLock(Lock.State.WAITING, resourceId, ownerInfo, callback, holdSec);

        resource2lock.compute(resourceId, (key, curlock) -> {
            if (curlock == null) {
                // no lock yet - we can try to grab it
                lock.scheduleRequest(lock::doLock);
                return lock;

            } else {
                lock.deny("resource is busy");
                return curlock;
            }
        });

        return lock;
    }

    /**
     * Initializes {@link #exsvc}, if it hasn't been initialized yet, scheduling a check
     * for expired locks.
     */
    private synchronized void init() {
        if (exsvc == null) {
            exsvc = makeThreadPool();
            exsvc.schedule(this::checkExpired, featProps.getExpireCheckSec(), TimeUnit.SECONDS);
        }
    }

    /**
     * Checks for expired locks.
     */
    private void checkExpired() {

        try {
            if (++nattempts > featProps.getMaxRetries()) {
                logger.error("max retry count exhausted - all locks lost");
                expireAllLocks();

            } else {
                logger.info("checking for expired locks");
                deleteExpiredDbLocks();

                Set<String> unseen = new HashSet<>(resource2lock.keySet());
                identifyDbLocks(unseen);
                expireLocks(unseen);
            }

            // reset this AFTER the above work succeeds
            nattempts = 0;
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
     * Expires all of the locks that are currently owned by this feature.
     */
    protected void expireAllLocks() {
        Iterator<DistributedLock> it = resource2lock.values().iterator();
        while (it.hasNext()) {
            DistributedLock lock = it.next();

            if (lock.isActive()) {
                it.remove();
                lock.deny(LOCK_LOST_MSG);
            }
        }
    }

    /**
     * Deletes expired locks from the DB.
     *
     * @throws SQLException if a DB error occurs
     */
    private void deleteExpiredDbLocks() throws SQLException {
        try (Connection conn = dataSource.getConnection();
                        PreparedStatement stmt = conn
                                        .prepareStatement("DELETE FROM pooling.locks WHERE expirationTime <= now()")) {

            stmt.executeUpdate();
        }
    }

    /**
     * Identifies locks that the DB indicates are still active.
     *
     * @param unseen IDs of resources that have not been examined yet
     * @throws SQLException if a DB error occurs
     */
    protected void identifyDbLocks(Set<String> unseen) throws SQLException {
        // @formatter:off
        try (Connection conn = dataSource.getConnection();
                        PreparedStatement stmt = conn.prepareStatement(
                            "SELECT resourceId, host, owner FROM pooling.locks WHERE expirationTime > now()");
                        ResultSet resultSet = stmt.executeQuery()) {
            // @formatter:on

            while (resultSet.next()) {
                String resourceId = resultSet.getString(1);
                String host = resultSet.getString(2);
                String dbuuid = resultSet.getString(3);

                if (!unseen.remove(resourceId)) {
                    // not a resource of interest
                    continue;
                }

                resource2lock.compute(resourceId, (key, lock) -> {
                    if (lock != null && (!uuidString.equals(dbuuid) || !HOST_NAME.equals(host))) {
                        lock.deny(LOCK_LOST_MSG);
                        return null;
                    }

                    return lock;
                });
            }
        }
    }

    /**
     * Expires locks for the resources that no longer appear within the DB.
     *
     * @param unseen IDs of resources that have not been examined yet
     */
    protected void expireLocks(Set<String> unseen) {
        for (String resourceId : unseen) {
            resource2lock.compute(resourceId, (key, lock) -> {
                if (lock != null && lock.isActive()) {
                    // it thinks it's active, but it isn't
                    lock.deny(LOCK_LOST_MSG);
                    return null;
                }

                return lock;
            });
        }
    }

    /**
     * Distributed Lock implementation.
     */
    protected class DistributedLock extends LockImpl {

        /**
         * {@code True} if the lock is busy making a request, {@code false} otherwise.
         */
        private AtomicBoolean busy = new AtomicBoolean(false);

        /**
         * Request to be performed.
         */
        private AtomicReference<RunnableWithEx> request = new AtomicReference<>(null);

        /**
         * Number of times we've attempted a request.
         */
        private int nattempts = 0;

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

            resource2lock.compute(getResourceId(), (resourceId, curlock) -> {
                if (curlock == this) {
                    // this lock was the owner
                    result.set(true);
                    setState(Lock.State.UNAVAILABLE);
                    scheduleRequest(this::doUnlock);

                    // NOTE: do NOT return null; it must remain until doUnlock completes
                }

                return curlock;
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
                    setState(Lock.State.WAITING);
                    setHoldSec(holdSec);
                    scheduleRequest(this::doExtend);
                }
                // else: this lock is not the owner - no change

                return curlock;
            });

            return result.get();
        }

        /**
         * Schedules a request for execution.
         *
         * @param schedreq the request that should be scheduled
         */
        private void scheduleRequest(RunnableWithEx schedreq) {
            nattempts = 0;
            request.set(schedreq);
            exsvc.execute(this::doRequest);
        }

        /**
         * Executes the current request, if none are currently executing.
         */
        private void doRequest() {
            if (!busy.compareAndSet(false, true)) {
                // already busy
                return;
            }

            try {
                if (++nattempts > featProps.getMaxRetries()) {
                    logger.warn("retry count exhausted for lock: {}", this);
                    removeFromMap(getState() == Lock.State.UNAVAILABLE ? null : LOCK_LOST_MSG);
                    return;
                }

                RunnableWithEx req;
                while ((req = request.getAndSet(null)) != null) {
                    req.run();
                }

            } catch (SQLException e) {
                logger.warn("request failed for lock: {}", this, e);
                exsvc.schedule(this::doRequest, featProps.getRetrySec(), TimeUnit.SECONDS);

            } finally {
                busy.set(false);
            }
        }

        /**
         * Attempts to add a lock to the DB. Generates a callback, indicating success or
         * failure.
         *
         * @throws SQLException if a DB error occurs
         */
        private void doLock() throws SQLException {
            try (Connection conn = dataSource.getConnection()) {
                boolean success = false;
                try {
                    success = doInsert(conn);

                } catch (SQLException e) {
                    logger.info("failed to insert lock record - attempting update: {}", this, e);
                    success = doUpdate(conn);
                }

                if (success) {
                    grant();
                    return;
                }
            }

            removeFromMap(LOCK_LOST_MSG);
        }

        /**
         * Attempts to remove a lock from the DB. Does <i>not</i> generate a callback if
         * it fails, as this should only be executed in response to a call to
         * {@link #free()}.
         *
         * @throws SQLException if a DB error occurs
         */
        private void doUnlock() throws SQLException {
            try (Connection conn = dataSource.getConnection()) {
                doDelete(conn);
            }

            removeFromMap(null);
        }

        /**
         * Attempts to extend a lock in the DB. Generates a callback, indicating success
         * or failure.
         *
         * @throws SQLException if a DB error occurs
         */
        private void doExtend() throws SQLException {
            try (Connection conn = dataSource.getConnection()) {
                if (doUpdate(conn)) {
                    grant();
                    return;
                }
            }

            removeFromMap(LOCK_LOST_MSG);
        }

        /**
         * Inserts the lock into the DB.
         *
         * @param conn DB connection
         * @return {@code true} if a record was successfully inserted, {@code false}
         *         otherwise
         * @throws SQLException if a DB error occurs
         */
        private boolean doInsert(Connection conn) throws SQLException {
            try (PreparedStatement stmt =
                            conn.prepareStatement("INSERT INTO pooling.locks (resourceId, host, owner, expirationTime) "
                                            + "values (?, ?, ?, timestampadd(second, ?, now()))")) {

                stmt.setString(1, getResourceId());
                stmt.setString(2, HOST_NAME);
                stmt.setString(3, uuidString);
                stmt.setInt(4, getHoldSec());

                return (stmt.executeUpdate() == 1);
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
        private boolean doUpdate(Connection conn) throws SQLException {
            try (PreparedStatement stmt =
                            conn.prepareStatement("UPDATE pooling.locks SET resourceId=?, host=?, owner=?,"
                                            + " expirationTime=timestampadd(second, ?, now()) "
                                            + "WHERE resourceId=? AND (owner=? OR expirationTime < now())")) {

                stmt.setString(1, getResourceId());
                stmt.setString(2, HOST_NAME);
                stmt.setString(3, uuidString);
                stmt.setInt(4, getHoldSec());

                stmt.setString(5, getResourceId());
                stmt.setString(6, uuidString);

                return (stmt.executeUpdate() == 1);
            }
        }

        /**
         * Deletes the lock from the DB.
         *
         * @param conn DB connection
         * @throws SQLException if a DB error occurs
         */
        private void doDelete(Connection conn) throws SQLException {
            try (PreparedStatement stmt =
                            conn.prepareStatement("DELETE pooling.locks WHERE resourceId=? AND host=? AND owner=?")) {

                stmt.setString(1, getResourceId());
                stmt.setString(2, HOST_NAME);
                stmt.setString(3, uuidString);

                stmt.executeUpdate();
            }
        }

        /**
         * Removes the lock from the map.
         *
         * @param reason reason for which the lock was denied, {@code null} if no callback
         *        should be generated
         */
        private void removeFromMap(String reason) {
            resource2lock.remove(getResourceId(), this);
            request.set(null);

            if (reason != null) {
                deny(reason);
            }
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

    @FunctionalInterface
    private static interface RunnableWithEx {
        void run() throws SQLException;
    }

    // these may be overridden by junit tests

    protected ScheduledExecutorService makeThreadPool() {
        return Executors.newScheduledThreadPool(featProps.getMaxThreads());
    }
}
