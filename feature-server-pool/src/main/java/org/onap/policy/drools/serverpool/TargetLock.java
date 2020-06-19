/*
 * ============LICENSE_START=======================================================
 * feature-server-pool
 * ================================================================================
 * Copyright (C) 2020 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.serverpool;

import static org.onap.policy.drools.serverpool.ServerPoolProperties.DEFAULT_LOCK_AUDIT_GRACE_PERIOD;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DEFAULT_LOCK_AUDIT_PERIOD;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DEFAULT_LOCK_AUDIT_RETRY_DELAY;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DEFAULT_LOCK_TIME_TO_LIVE;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.LOCK_AUDIT_GRACE_PERIOD;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.LOCK_AUDIT_PERIOD;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.LOCK_AUDIT_RETRY_DELAY;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.LOCK_TIME_TO_LIVE;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.getProperty;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import lombok.EqualsAndHashCode;
import org.onap.policy.drools.core.DroolsRunnable;
import org.onap.policy.drools.core.PolicyContainer;
import org.onap.policy.drools.core.PolicySession;
import org.onap.policy.drools.core.lock.Lock;
import org.onap.policy.drools.core.lock.LockCallback;
import org.onap.policy.drools.core.lock.PolicyResourceLockManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides a locking mechanism based upon a string key that
 * identifies the lock, and another string key that identifies the owner.
 * The existence of the 'TargetLock' instance doesn't mean that the
 * corresponding lock has been acquired -- this is only the case if the
 * instance is in the 'ACTIVE' state.
 * A lock in the ACTIVE or WAITING state exists in two sets of tables,
 * which may be on different hosts:
 * LocalLocks - these two tables are associated with the owner key of the
 *     lock. They are in an adjunct to the bucket associated with this key,
 *     and the bucket is owned by the host containing the entry.
 *  GlobalLocks - this table is associated with the lock key. It is an
 *     adjunct to the bucket associated with this key, and the bucket is
 *     owned by the host containing the entry.
 */
public class TargetLock implements Lock, Serializable {
    private static final long serialVersionUID = 1L;

    private static Logger logger = LoggerFactory.getLogger(TargetLock.class);

    // Listener class to handle state changes that require restarting the audit
    private static EventHandler eventHandler = new EventHandler();

    static {
        // register Listener class
        Events.register(eventHandler);
    }

    // this is used to locate ACTIVE 'TargetLock' instances that have been
    // abandoned -- as the GC cleans up the 'WeakReference' instances referring
    // to these locks, we use that information to clean them up
    private static ReferenceQueue<TargetLock> abandoned = new ReferenceQueue<>();

    // some status codes
    static final int ACCEPTED = 202; //Response.Status.ACCEPTED.getStatusCode()
    static final int NO_CONTENT = 204; //Response.Status.NO_CONTENT.getStatusCode()
    static final int LOCKED = 423;

    // Values extracted from properties

    private static String timeToLive;
    private static long auditPeriod;
    private static long auditGracePeriod;
    private static long auditRetryDelay;

    // lock states:
    // WAITING - in line to acquire the lock
    // ACTIVE - currently holding the lock
    // FREE - WAITING/ACTIVE locks that were explicitly freed
    // LOST - could occur when a de-serialized ACTIVE lock can't be made
    //     ACTIVE because there is already an ACTIVE holder of the lock
    public enum State {
        WAITING, ACTIVE, FREE, LOST
    }

    // this contains information that is placed in the 'LocalLocks' tables,
    // and has a one-to-one correspondence with the 'TargetLock' instance
    private Identity identity;

    // this is the only field that can change after initialization
    private State state;

    // this is used to notify the application when a lock is available,
    // or if it is not available
    private volatile LockCallback owner;

    // This is what is actually called by the infrastructure to do the owner
    // notification. The owner may be running in a Drools session, in which case
    // the actual notification should be done within that thread -- the 'context'
    // object ensures that it happens this way.
    private volatile LockCallback context;

    // HTTP query parameters
    private static final String QP_KEY = "key";
    private static final String QP_OWNER = "owner";
    private static final String QP_UUID = "uuid";
    private static final String QP_WAIT = "wait";
    private static final String QP_SERVER = "server";
    private static final String QP_TTL = "ttl";

    /**
     * This method triggers registration of 'eventHandler', and also extracts
     * property values.
     */
    static void startup() {
        int intTimeToLive =
            getProperty(LOCK_TIME_TO_LIVE, DEFAULT_LOCK_TIME_TO_LIVE);
        timeToLive = String.valueOf(intTimeToLive);
        auditPeriod = getProperty(LOCK_AUDIT_PERIOD, DEFAULT_LOCK_AUDIT_PERIOD);
        auditGracePeriod =
            getProperty(LOCK_AUDIT_GRACE_PERIOD, DEFAULT_LOCK_AUDIT_GRACE_PERIOD);
        auditRetryDelay =
            getProperty(LOCK_AUDIT_RETRY_DELAY, DEFAULT_LOCK_AUDIT_RETRY_DELAY);
    }

    /**
     * Shutdown threads.
     */
    static void shutdown() {
        AbandonedHandler ah = abandonedHandler;

        if (ah != null) {
            abandonedHandler = null;
            ah.interrupt();
        }
    }

    /**
     * Constructor - initializes the 'TargetLock' instance, and tries to go
     * ACTIVE. The lock is initially placed in the WAITING state, and the owner
     * and the owner will be notified when the success or failure of the lock
     * attempt is determined.
     *
     * @param key string key identifying the lock
     * @param ownerKey string key identifying the owner, which must hash to
     *     a bucket owned by the current host (it is typically a 'RequestID')
     * @param owner owner of the lock (will be notified when going from
     *     WAITING to ACTIVE)
     */
    public TargetLock(String key, String ownerKey, LockCallback owner) {
        this(key, ownerKey, owner, true);
    }

    /**
     * Constructor - initializes the 'TargetLock' instance, and tries to go
     * ACTIVE. The lock is initially placed in the WAITING state, and the owner
     * and the owner will be notified when the success or failure of the lock
     * attempt is determined.
     *
     * @param key string key identifying the lock
     * @param ownerKey string key identifying the owner, which must hash to
     *     a bucket owned by the current host (it is typically a 'RequestID')
     * @param owner owner of the lock (will be notified when going from
     *     WAITING to ACTIVE)
     * @param waitForLock this controls the behavior when 'key' is already
     *     locked - 'true' means wait for it to be freed, 'false' means fail
     */
    public TargetLock(final String key, final String ownerKey,
            final LockCallback owner, final boolean waitForLock) {
        if (key == null) {
            throw(new IllegalArgumentException("TargetLock: 'key' can't be null"));
        }
        if (ownerKey == null) {
            throw(new IllegalArgumentException("TargetLock: 'ownerKey' can't be null"));
        }
        if (!Bucket.isKeyOnThisServer(ownerKey)) {
            // associated bucket is assigned to a different server
            throw(new IllegalArgumentException("TargetLock: 'ownerKey=" + ownerKey
                + "' not currently assigned to this server"));
        }
        if (owner == null) {
            throw(new IllegalArgumentException("TargetLock: 'owner' can't be null"));
        }
        identity = new Identity(key, ownerKey);
        state = State.WAITING;
        this.owner = owner;

        // determine the context
        PolicySession session = PolicySession.getCurrentSession();
        if (session != null) {
            // deliver through a 'PolicySessionContext' class
            Object lcontext = session.getAdjunct(PolicySessionContext.class);
            if (!(lcontext instanceof LockCallback)) {
                context = new PolicySessionContext(session);
                session.setAdjunct(PolicySessionContext.class, context);
            } else {
                context = (LockCallback) lcontext;
            }
        } else {
            // no context to deliver through -- call back directly to owner
            context = owner;
        }

        // update 'LocalLocks' tables
        final WeakReference<TargetLock> wr = new WeakReference<>(this, abandoned);
        final LocalLocks localLocks = LocalLocks.get(ownerKey);

        synchronized (localLocks) {
            localLocks.weakReferenceToIdentity.put(wr, identity);
            localLocks.uuidToWeakReference.put(identity.uuid, wr);
        }

        // The associated 'GlobalLocks' table may or may not be on a different
        // host. Also, the following call may queue the message for later
        // processing if the bucket is in a transient state.
        Bucket.forwardAndProcess(key, new Bucket.Message() {
            /**
             * {@inheritDoc}
             */
            @Override
            public void process() {
                // 'GlobalLocks' is on the same host
                State newState = GlobalLocks.get(key).lock(key, ownerKey, identity.uuid, waitForLock);
                logger.info("Lock lock request: key={}, owner={}, uuid={}, wait={} (resp={})",
                    key, ownerKey, identity.uuid, waitForLock, state);

                // The lock may now be ACTIVE, FREE, or WAITING -- we can notify
                // the owner of the result now for ACTIVE or FREE. Also, the callback
                // may occur while the constructor is still on the stack, although
                // this won't happen in a Drools session.
                setState(newState);
                switch (newState) {
                    case ACTIVE:
                        // lock was successful - send notification
                        context.lockAvailable(TargetLock.this);
                        break;
                    case FREE:
                        // lock attempt failed -
                        // clean up local tables, and send notification
                        synchronized (localLocks) {
                            localLocks.weakReferenceToIdentity.remove(wr);
                            localLocks.uuidToWeakReference.remove(identity.uuid);
                        }
                        wr.clear();
                        context.lockUnavailable(TargetLock.this);
                        break;

                    case WAITING:
                        break;

                    default:
                        logger.error("Unknown state: {}", newState);
                        break;
                    }
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void sendToServer(Server server, int bucketNumber) {
                // actual lock is on a remote host -- send the request as
                // a REST message
                logger.info("Sending lock request to {}: key={}, owner={}, uuid={}, wait={}",
                    server, key, ownerKey, identity.uuid, waitForLock);
                server.post("lock/lock", null, new Server.PostResponse() {
                    /**
                     * {@inheritDoc}
                     */
                    @Override
                    public WebTarget webTarget(WebTarget webTarget) {
                        return webTarget
                               .queryParam(QP_KEY, key)
                               .queryParam(QP_OWNER, ownerKey)
                               .queryParam(QP_UUID, identity.uuid.toString())
                               .queryParam(QP_WAIT, waitForLock)
                               .queryParam(QP_TTL, timeToLive);
                    }

                    /**
                     * {@inheritDoc}
                     */
                    @Override
                    public void response(Response response) {
                        logger.info("Lock response={} (code={})",
                                    response, response.getStatus());

                        /*
                         * there are three possible responses:
                         * 204 No Content - operation was successful
                         * 202 Accepted - operation is still in progress
                         * 423 (Locked) - lock in use, and 'waitForLock' is 'false'
                         */
                        switch (response.getStatus()) {
                            case NO_CONTENT:
                                // lock successful
                                setState(State.ACTIVE);
                                context.lockAvailable(TargetLock.this);
                                break;

                            case LOCKED:
                                // failed -- lock in use, and 'waitForLock == false'
                                setState(State.FREE);
                                synchronized (localLocks) {
                                    localLocks.weakReferenceToIdentity.remove(wr);
                                    localLocks.uuidToWeakReference.remove(identity.uuid);
                                }
                                wr.clear();
                                context.lockUnavailable(TargetLock.this);
                                break;

                            case ACCEPTED:
                                break;

                            default:
                                logger.error("Unknown status: {}", response.getStatus());
                                break;
                        }
                    }
                });
            }
        });
    }

    /* ****************** */
    /* 'Lock' Interface   */
    /* ****************** */

    /**
     * This method will free the current lock, or remove it from the waiting
     * list if a response is pending.
     *
     * @return 'true' if successful, 'false' if it was not locked, or there
     *     appears to be corruption in 'LocalLocks' tables
     */
    @Override
    public boolean free() {
        synchronized (this) {
            if (state != State.ACTIVE && state != State.WAITING) {
                // nothing to free
                return false;
            }
            state = State.FREE;
        }

        return identity.free();
    }

    /**
     * Return 'true' if the lock is in the ACTIVE state.
     *
     * @return 'true' if the lock is in the ACTIVE state, and 'false' if not
     */
    @Override
    public synchronized boolean isActive() {
        return state == State.ACTIVE;
    }

    /**
     * Return 'true' if the lock is not available.
     *
     * @return 'true' if the lock is in the FREE or LOST state,
     *     and 'false' if not
     */
    @Override
    public synchronized boolean isUnavailable() {
        return state == State.FREE || state == State.LOST;
    }

    /**
     * Return 'true' if the lock is in the WAITING state.
     *
     * @return 'true' if the lock is in the WAITING state, and 'false' if not
     */
    public synchronized boolean isWaiting() {
        return state == State.WAITING;
    }

    /**
     * Return the lock's key.
     *
     * @return the lock's key
     */
    @Override
    public String getResourceId() {
        return identity.key;
    }

    /**
     * Return the owner key field.
     *
     * @return the owner key field
     */
    @Override
    public String getOwnerKey() {
        return identity.ownerKey;
    }

    /**
     * Extends the lock's hold time (not implemented yet).
     */
    @Override
    public void extend(int holdSec, LockCallback callback) {
        // not implemented yet
    }

    /* ****************** */

    /**
     * Update the state.
     *
     * @param newState the new state value
     */
    private synchronized void setState(State newState) {
        state = newState;
    }

    /**
     * Return the currentstate of the lock.
     *
     * @return the current state of the lock
     */
    public synchronized State getState() {
        return state;
    }

    /**
     * This method is called when an incoming /lock/lock REST message is received.
     *
     * @param key string key identifying the lock, which must hash to a bucket
     *     owned by the current host
     * @param ownerKey string key identifying the owner
     * @param uuid the UUID that uniquely identifies the original 'TargetLock'
     * @param waitForLock this controls the behavior when 'key' is already
     *     locked - 'true' means wait for it to be freed, 'false' means fail
     * @param ttl similar to IP time-to-live -- it controls the number of hops
     *     the message may take
     * @return the Response that should be passed back to the HTTP request
     */
    static Response incomingLock(String key, String ownerKey, UUID uuid, boolean waitForLock, int ttl) {
        if (!Bucket.isKeyOnThisServer(key)) {
            // this is the wrong server -- forward to the correct one
            // (we can use this thread)
            ttl -= 1;
            if (ttl > 0) {
                Server server = Bucket.bucketToServer(Bucket.bucketNumber(key));
                if (server != null) {
                    WebTarget webTarget = server.getWebTarget("lock/lock");
                    if (webTarget != null) {
                        logger.warn("Forwarding 'lock/lock' to uuid {} "
                                    + "(key={},owner={},uuid={},wait={},ttl={})",
                                    server.getUuid(), key, ownerKey, uuid,
                                    waitForLock, ttl);
                        return webTarget
                               .queryParam(QP_KEY, key)
                               .queryParam(QP_OWNER, ownerKey)
                               .queryParam(QP_UUID, uuid.toString())
                               .queryParam(QP_WAIT, waitForLock)
                               .queryParam(QP_TTL, String.valueOf(ttl))
                               .request().get();
                    }
                }
            }

            // if we reach this point, we didn't forward for some reason --
            // return failure by indicating it is locked and unavailable
            logger.error("Couldn't forward 'lock/lock' "
                         + "(key={},owner={},uuid={},wait={},ttl={})",
                         key, ownerKey, uuid, waitForLock, ttl);
            return Response.noContent().status(LOCKED).build();
        }

        State state = GlobalLocks.get(key).lock(key, ownerKey, uuid, waitForLock);
        switch (state) {
            case ACTIVE:
                return Response.noContent().build();
            case WAITING:
                return Response.noContent().status(Response.Status.ACCEPTED).build();
            default:
                return Response.noContent().status(LOCKED).build();
        }
    }

    /**
     * This method is called when an incoming /lock/free REST message is received.
     *
     * @param key string key identifying the lock, which must hash to a bucket
     *     owned by the current host
     * @param ownerKey string key identifying the owner
     * @param uuid the UUID that uniquely identifies the original 'TargetLock'
     * @param ttl similar to IP time-to-live -- it controls the number of hops
     *     the message may take
     * @return the Response that should be passed back to the HTTP request
     */
    static Response incomingFree(String key, String ownerKey, UUID uuid, int ttl) {
        if (!Bucket.isKeyOnThisServer(key)) {
            // this is the wrong server -- forward to the correct one
            // (we can use this thread)
            ttl -= 1;
            if (ttl > 0) {
                Server server = Bucket.bucketToServer(Bucket.bucketNumber(key));
                if (server != null) {
                    WebTarget webTarget = server.getWebTarget("lock/free");
                    if (webTarget != null) {
                        logger.warn("Forwarding 'lock/free' to uuid {} "
                                    + "(key={},owner={},uuid={},ttl={})",
                                    server.getUuid(), key, ownerKey, uuid, ttl);
                        return webTarget
                               .queryParam(QP_KEY, key)
                               .queryParam(QP_OWNER, ownerKey)
                               .queryParam(QP_UUID, uuid.toString())
                               .queryParam(QP_TTL, String.valueOf(ttl))
                               .request().get();
                    }
                }
            }

            // if we reach this point, we didn't forward for some reason --
            // return failure by indicating it is locked and unavailable
            logger.error("Couldn't forward 'lock/free' "
                         + "(key={},owner={},uuid={},ttl={})",
                         key, ownerKey, uuid, ttl);
            return null;
        }

        // TBD: should this return a more meaningful response?
        GlobalLocks.get(key).unlock(key, uuid);
        return null;
    }

    /**
     * This method is called when an incoming /lock/locked message is received
     * (this is a callback to an earlier requestor that the lock is now
     * available).
     *
     * @param key string key identifying the lock
     * @param ownerKey string key identifying the owner, which must hash to
     *     a bucket owned by the current host (it is typically a 'RequestID')
     * @param uuid the UUID that uniquely identifies the original 'TargetLock'
     * @param ttl similar to IP time-to-live -- it controls the number of hops
     *     the message may take
     * @return the Response that should be passed back to the HTTP request
     */
    static Response incomingLocked(String key, String ownerKey, UUID uuid, int ttl) {
        if (!Bucket.isKeyOnThisServer(ownerKey)) {
            // this is the wrong server -- forward to the correct one
            // (we can use this thread)
            ttl -= 1;
            if (ttl > 0) {
                Server server = Bucket.bucketToServer(Bucket.bucketNumber(key));
                if (server != null) {
                    WebTarget webTarget = server.getWebTarget("lock/locked");
                    if (webTarget != null) {
                        logger.warn("Forwarding 'lock/locked' to uuid {} "
                                    + "(key={},owner={},uuid={},ttl={})",
                                    server.getUuid(), key, ownerKey, uuid, ttl);
                        return webTarget
                               .queryParam(QP_KEY, key)
                               .queryParam(QP_OWNER, ownerKey)
                               .queryParam(QP_UUID, uuid.toString())
                               .queryParam(QP_TTL, String.valueOf(ttl))
                               .request().get();
                    }
                }
            }

            // if we reach this point, we didn't forward for some reason --
            // return failure by indicating it is locked and unavailable
            logger.error("Couldn't forward 'lock/locked' "
                         + "(key={},owner={},uuid={},ttl={})",
                         key, ownerKey, uuid, ttl);
            return Response.noContent().status(LOCKED).build();
        }

        TargetLock targetLock = null;
        LocalLocks localLocks = LocalLocks.get(ownerKey);
        synchronized (localLocks) {
            WeakReference<TargetLock> wr =
                localLocks.uuidToWeakReference.get(uuid);

            if (wr != null) {
                targetLock = wr.get();
                if (targetLock == null) {
                    // lock has been abandoned
                    // (AbandonedHandler should usually find this first)
                    localLocks.weakReferenceToIdentity.remove(wr);
                    localLocks.uuidToWeakReference.remove(uuid);
                } else {
                    // the lock has been made available -- update the state
                    // TBD: This could be outside of 'synchronized (localLocks)'
                    synchronized (targetLock) {
                        if (targetLock.state == State.WAITING) {
                            targetLock.state = State.ACTIVE;
                        } else {
                            // will return a failure -- not sure how this happened
                            logger.error("incomingLocked: {} is in state {}",
                                         targetLock, targetLock.state);
                            targetLock = null;
                        }
                    }
                }
            } else {
                // clean up what we can
                localLocks.uuidToWeakReference.remove(uuid);
            }
        }
        if (targetLock == null) {
            // We can't locate the target lock
            // TBD: This probably isn't the best error code to use
            return Response.noContent().status(LOCKED).build();
        } else {
            targetLock.context.lockAvailable(targetLock);
            return Response.noContent().build();
        }
    }

    /**
     * This is called when the state of a bucket has changed, but is currently
     * stable. Note that this method is called while being synchronized on the
     * bucket.
     *
     * @param bucket the bucket to audit
     * @param owner 'true' if the current host owns the bucket
     * @param backup 'true' if the current host is a backup for the bucket
     */
    static void auditBucket(Bucket bucket, boolean isOwner, boolean isBackup) {
        if (!isOwner) {
            // we should not have any 'TargetLock' adjuncts
            if (bucket.removeAdjunct(LocalLocks.class) != null) {
                logger.warn("Bucket {}: Removed superfluous "
                            + "'TargetLock.LocalLocks' adjunct",
                            bucket.getIndex());
            }
            if (bucket.removeAdjunct(GlobalLocks.class) != null) {
                logger.warn("Bucket {}: Removed superfluous "
                            + "'TargetLock.GlobalLocks' adjunct",
                            bucket.getIndex());
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "TargetLock(key=" + identity.key
               + ", ownerKey=" + identity.ownerKey
               + ", uuid=" + identity.uuid
               + ", state=" + state + ")";
    }

    /* *************** */
    /* Serialization   */
    /* *************** */

    /**
     * This method modifies the behavior of 'TargetLock' deserialization by
     * creating the corresponding 'LocalLocks' entries.
     */
    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        if (state == State.ACTIVE || state == State.WAITING) {
            // need to build entries in 'LocalLocks'
            LocalLocks localLocks = LocalLocks.get(identity.ownerKey);
            WeakReference<TargetLock> wr = new WeakReference<>(this, abandoned);

            synchronized (localLocks) {
                localLocks.weakReferenceToIdentity.put(wr, identity);
                localLocks.uuidToWeakReference.put(identity.uuid, wr);
            }
        }
    }

    /* ============================================================ */

    private static class LockFactory implements PolicyResourceLockManager {
        /* *************************************** */
        /* 'PolicyResourceLockManager' interface   */
        /* *************************************** */

        /**
         * {@inheritDoc}
         */
        @Override
        public Lock createLock(String resourceId, String ownerKey,
                               int holdSec, LockCallback callback,
                               boolean waitForLock) {
            // 'holdSec' isn't implemented yet
            return new TargetLock(resourceId, ownerKey, callback, waitForLock);
        }

        /* *********************** */
        /* 'Startable' interface   */
        /* *********************** */

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean start() {
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean stop() {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void shutdown() {
            // nothing needs to be done
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isAlive() {
            return true;
        }

        /* ********************** */
        /* 'Lockable' interface   */
        /* ********************** */

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean lock() {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean unlock() {
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isLocked() {
            return false;
        }
    }

    private static LockFactory lockFactory = new LockFactory();

    public static PolicyResourceLockManager getLockFactory() {
        return lockFactory;
    }

    /* ============================================================ */

    /**
     * There is a single instance of class 'TargetLock.EventHandler', which is
     * registered to listen for notifications of state transitions.
     */
    private static class EventHandler implements Events {
        /**
         * {@inheritDoc}
         */
        @Override
        public void newServer(Server server) {
            // with an additional server, the offset within the audit period changes
            Audit.scheduleAudit();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void serverFailed(Server server) {
            // when one less server, the offset within the audit period changes
            Audit.scheduleAudit();
        }
    }

    /* ============================================================ */

    /**
     * This class usually has a one-to-one correspondence with a 'TargetLock'
     * instance, unless the 'TargetLock' has been abandoned.
     */
    @EqualsAndHashCode
    private static class Identity implements Serializable {
        private static final long serialVersionUID = 1L;

        // this is the key associated with the lock
        String key;

        // this is the key associated with the lock requestor
        String ownerKey;

        // this is a unique identifier assigned to the 'TargetLock'
        UUID uuid;

        /**
         * Constructor - initializes the 'Identity' instance, including the
         * generation of the unique identifier.
         *
         * @param key string key identifying the lock
         * @param ownerKey string key identifying the owner, which must hash to
         *     a bucket owned by the current host (it is typically a 'RequestID')
         */
        private Identity(String key, String ownerKey) {
            this.key = key;
            this.ownerKey = ownerKey;
            this.uuid = UUID.randomUUID();
        }

        /**
         * Constructor - initializes the 'Identity' instance, with the 'uuid'
         * value passed at initialization time (only used for auditing).
         *
         * @param key string key identifying the lock
         * @param ownerKey string key identifying the owner, which must hash to
         * @param uuid the UUID that uniquely identifies the original 'TargetLock'
         */
        private Identity(String key, String ownerKey, UUID uuid) {
            this.key = key;
            this.ownerKey = ownerKey;
            this.uuid = uuid;
        }

        /**
         * Free the lock associated with this 'Identity' instance.
         *
         * @return 'false' if the 'LocalLocks' data is not there, true' if it is
         */
        private boolean free() {
            // free the lock
            Bucket.forwardAndProcess(key, new Bucket.Message() {
                /**
                 * {@inheritDoc}
                 */
                @Override
                public void process() {
                    // the global lock entry is also on this server
                    GlobalLocks.get(key).unlock(key, uuid);
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void sendToServer(Server server, int bucketNumber) {
                    logger.info("Sending free request to {}: key={}, owner={}, uuid={}",
                        server, key, ownerKey, uuid);
                    server.post("lock/free", null, new Server.PostResponse() {
                        @Override
                        public WebTarget webTarget(WebTarget webTarget) {
                            return webTarget
                                   .queryParam(QP_KEY, key)
                                   .queryParam(QP_OWNER, ownerKey)
                                   .queryParam(QP_UUID, uuid.toString())
                                   .queryParam(QP_TTL, timeToLive);
                        }

                        @Override
                        public void response(Response response) {
                            logger.info("Free response={} (code={})",
                                        response, response.getStatus());
                            switch (response.getStatus()) {
                                case NO_CONTENT:
                                    // free successful -- don't need to do anything
                                    break;

                                case LOCKED:
                                    // free failed
                                    logger.error("TargetLock free failed, "
                                                 + "key={}, owner={}, uuid={}",
                                                 key, ownerKey, uuid);
                                    break;

                                default:
                                    logger.error("Unknown status: {}", response.getStatus());
                                    break;
                            }
                        }
                    });
                }
            });

            // clean up locallocks entry
            LocalLocks localLocks = LocalLocks.get(ownerKey);
            synchronized (localLocks) {
                WeakReference<TargetLock> wr =
                    localLocks.uuidToWeakReference.get(uuid);
                if (wr == null) {
                    return false;
                }

                localLocks.weakReferenceToIdentity.remove(wr);
                localLocks.uuidToWeakReference.remove(uuid);
                wr.clear();
            }
            return true;
        }
    }

    /* ============================================================ */

    /**
     * An instance of this class is used for 'TargetLock.context' when the
     * lock is allocated within a Drools session. Its purpose is to ensure that
     * the callback to 'TargetLock.owner' runs within the Drools thread.
     */
    private static class PolicySessionContext implements LockCallback, Serializable {
        private static final long serialVersionUID = 1L;

        // the 'PolicySession' instance in question
        PolicySession policySession;

        /**
         * Constructor - initialize the 'policySession' field.
         *
         * @param policySession the Drools session
         */
        private PolicySessionContext(PolicySession policySession) {
            this.policySession = policySession;
        }

        /* ******************* */
        /* 'Owner' interface   */
        /* ******************* */

        /**
         * {@inheritDoc}
         */
        @Override
        public void lockAvailable(final Lock lock) {
            // Run 'owner.lockAvailable' within the Drools session
            if (policySession != null) {
                DroolsRunnable callback = () -> {
                    ((TargetLock) lock).owner.lockAvailable(lock);
                };
                policySession.getKieSession().insert(callback);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void lockUnavailable(Lock lock) {
            // Run 'owner.unlockAvailable' within the Drools session
            if (policySession != null) {
                DroolsRunnable callback = () -> {
                    ((TargetLock) lock).owner.lockUnavailable(lock);
                };
                policySession.getKieSession().insert(callback);
            }
        }

        /* *************** */
        /* Serialization   */
        /* *************** */

        /**
         * Specializes serialization of 'PolicySessionContext'.
         */
        private void writeObject(ObjectOutputStream out) throws IOException {
            // 'PolicySession' can't be serialized directly --
            // store as 'groupId', 'artifactId', 'sessionName'
            PolicyContainer pc = policySession.getPolicyContainer();

            out.writeObject(pc.getGroupId());
            out.writeObject(pc.getArtifactId());
            out.writeObject(policySession.getName());
        }

        /**
         * Specializes deserialization of 'PolicySessionContext'.
         */
        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            // 'PolicySession' can't be serialized directly --
            // read in 'groupId', 'artifactId', 'sessionName'
            String groupId = String.class.cast(in.readObject());
            String artifactId = String.class.cast(in.readObject());
            String sessionName = String.class.cast(in.readObject());

            // locate the 'PolicySession' associated with
            // 'groupId', 'artifactId', and 'sessionName'
            for (PolicyContainer pc : PolicyContainer.getPolicyContainers()) {
                if (artifactId.equals(pc.getArtifactId())
                        && groupId.equals(pc.getGroupId())) {
                    // found 'PolicyContainer' -- look up the session
                    policySession = pc.getPolicySession(sessionName);
                    if (policySession == null) {
                        logger.error("TargetLock.PolicySessionContext.readObject: "
                                     + "Can't find session {}:{}:{}",
                                     groupId, artifactId, sessionName);
                    }
                }
            }
        }
    }

    /* ============================================================ */

    /**
     * This class contains two tables that have entries for any 'TargetLock'
     * in the 'ACTIVE' or 'WAITING' state. This is the "client" end of the
     * lock implementation.
     */
    static class LocalLocks {
        // this table makes it easier to clean up locks that have been
        // abandoned (see 'AbandonedHandler')
        private Map<WeakReference<TargetLock>, Identity> weakReferenceToIdentity = new IdentityHashMap<>();

        // this table is used to locate a 'TargetLock' instance from a UUID
        private Map<UUID, WeakReference<TargetLock>> uuidToWeakReference =
            new HashMap<>();

        /**
         * Fetch the 'LocalLocks' entry associated with a particular owner key
         * (it is created if necessary).
         *
         * @param ownerKey string key identifying the owner, which must hash to
         *     a bucket owned by the current host (it is typically a 'RequestID')
         * @return the associated 'LocalLocks' instance (it should never be 'null')
         */
        private static LocalLocks get(String ownerKey) {
            return Bucket.getBucket(ownerKey).getAdjunct(LocalLocks.class);
        }
    }

    /* ============================================================ */

    /**
     * This class contains the actual lock table, which is the "server" end
     * of the lock implementation.
     */
    public static class GlobalLocks implements Serializable {
        private static final long serialVersionUID = 1L;

        // this is the lock table, mapping 'key' to 'LockEntry', which indicates
        // the current lock holder, and all those waiting
        private Map<String, LockEntry> keyToEntry = new HashMap<>();

        /**
         * Fetch the 'GlobalLocks' entry associated with a particular key
         * (it is created if necessary).
         *
         * @param key string key identifying the lock
         * @return the associated 'GlobalLocks' instance
         *     (it should never be 'null')
         */
        private static GlobalLocks get(String key) {
            return Bucket.getBucket(key).getAdjunct(GlobalLocks.class);
        }

        /**
         * Do the 'lock' operation -- lock immediately, if possible. If not,
         * get on the waiting list, if requested.
         *
         * @param key string key identifying the lock, which must hash to a bucket
         *     owned by the current host
         * @param ownerKey string key identifying the owner
         * @param uuid the UUID that uniquely identifies the original 'TargetLock'
         *     (on the originating host)
         * @param waitForLock this controls the behavior when 'key' is already
         *     locked - 'true' means wait for it to be freed, 'false' means fail
         * @return the lock State corresponding to the current request
         */
        synchronized State lock(String key, String ownerKey, UUID uuid, boolean waitForLock) {
            synchronized (keyToEntry) {
                LockEntry entry = keyToEntry.get(key);
                if (entry == null) {
                    // there is no existing entry -- create one, and return ACTIVE
                    entry = new LockEntry(key, ownerKey, uuid);
                    keyToEntry.put(key, entry);
                    sendUpdate(key);
                    return State.ACTIVE;
                }
                if (waitForLock) {
                    // the requestor is willing to wait -- get on the waiting list,
                    // and return WAITING
                    entry.waitingList.add(new Waiting(ownerKey, uuid));
                    sendUpdate(key);
                    return State.WAITING;
                }

                // the requestor is not willing to wait -- return FREE,
                // which will be interpreted as a failure
                return State.FREE;
            }
        }

        /**
         * Free a lock or a pending lock request.
         *
         * @param key string key identifying the lock
         * @param uuid the UUID that uniquely identifies the original 'TargetLock'
         */
        synchronized void unlock(String key, UUID uuid) {
            synchronized (keyToEntry) {
                final LockEntry entry = keyToEntry.get(key);
                if (entry == null) {
                    logger.error("GlobalLocks.unlock: unknown lock, key={}, uuid={}",
                                 key, uuid);
                    return;
                }
                if (entry.currentOwnerUuid.equals(uuid)) {
                    // this is the current lock holder
                    if (entry.waitingList.isEmpty()) {
                        // free this lock
                        keyToEntry.remove(key);
                    } else {
                        // pass it on to the next one in the list
                        Waiting waiting = entry.waitingList.remove();
                        entry.currentOwnerKey = waiting.ownerKey;
                        entry.currentOwnerUuid = waiting.ownerUuid;

                        entry.notifyNewOwner(this);
                    }
                    sendUpdate(key);
                } else {
                    // see if one of the waiting entries is being freed
                    for (Waiting waiting : entry.waitingList) {
                        if (waiting.ownerUuid.equals(uuid)) {
                            entry.waitingList.remove(waiting);
                            sendUpdate(key);
                            break;
                        }
                    }
                }
            }
        }

        /**
         * Notify all features that an update has occurred on this GlobalLock.
         *
         * @param key the key associated with the change
         *     (used to locate the bucket)
         */
        private void sendUpdate(String key) {
            Bucket bucket = Bucket.getBucket(key);
            for (ServerPoolApi feature : ServerPoolApi.impl.getList()) {
                feature.lockUpdate(bucket, this);
            }
        }

        /*===============*/
        /* Serialization */
        /*===============*/

        private void writeObject(ObjectOutputStream out) throws IOException {
            synchronized (this) {
                out.defaultWriteObject();
            }
        }
    }

    /* ============================================================ */

    /**
     * Each instance of this object corresponds to a single key in the lock
     * table. It includes the current holder of the lock, as well as
     * any that are waiting.
     */
    private static class LockEntry implements Serializable {
        private static final long serialVersionUID = 1L;

        // string key identifying the lock
        private String key;

        // string key identifying the owner
        private String currentOwnerKey;

        // UUID identifying the original 'TargetLock
        private UUID currentOwnerUuid;

        // list of pending lock requests for this key
        private Queue<Waiting> waitingList = new LinkedList<>();

        /**
         * Constructor - initialize the 'LockEntry'.
         *
         * @param key string key identifying the lock, which must hash to a bucket
         *     owned by the current host
         * @param ownerKey string key identifying the owner
         * @param uuid the UUID that uniquely identifies the original 'TargetLock'
         */
        private LockEntry(String key, String ownerKey, UUID uuid) {
            this.key = key;
            this.currentOwnerKey = ownerKey;
            this.currentOwnerUuid = uuid;
        }

        /**
         * This method is called after the 'currentOwnerKey' and
         * 'currentOwnerUuid' fields have been updated, and it notifies the new
         * owner that they now have the lock.
         *
         * @param globalLocks the 'GlobalLocks' instance containing this entry
         */
        private void notifyNewOwner(final GlobalLocks globalLocks) {
            Bucket.forwardAndProcess(currentOwnerKey, new Bucket.Message() {
                /**
                 * {@inheritDoc}
                 */
                @Override
                public void process() {
                    // the new owner is on this host
                    incomingLocked(key, currentOwnerKey, currentOwnerUuid, 1);
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void sendToServer(Server server, int bucketNumber) {
                    // the new owner is on a remote host
                    logger.info("Sending locked notification to {}: key={}, owner={}, uuid={}",
                        server, key, currentOwnerKey, currentOwnerUuid);
                    server.post("lock/locked", null, new Server.PostResponse() {
                        @Override
                        public WebTarget webTarget(WebTarget webTarget) {
                            return webTarget
                                   .queryParam(QP_KEY, key)
                                   .queryParam(QP_OWNER, currentOwnerKey)
                                   .queryParam(QP_UUID, currentOwnerUuid.toString())
                                   .queryParam(QP_TTL, timeToLive);
                        }

                        @Override
                        public void response(Response response) {
                            logger.info("Locked response={} (code={})",
                                        response, response.getStatus());
                            if (response.getStatus() != NO_CONTENT) {
                                // notification failed -- free this one
                                globalLocks.unlock(key, currentOwnerUuid);
                            }
                        }
                    });
                }
            });

        }
    }

    /* ============================================================ */

    /**
     * This corresponds to a member of 'LockEntry.waitingList'
     */
    private static class Waiting implements Serializable {
        private static final long serialVersionUID = 1L;

        // string key identifying the owner
        String ownerKey;

        // uniquely identifies the new owner 'TargetLock'
        UUID ownerUuid;

        /**
         * Constructor.
         *
         * @param ownerKey string key identifying the owner
         * @param ownerUuid uniquely identifies the new owner 'TargetLock'
         */
        private Waiting(String ownerKey, UUID ownerUuid) {
            this.ownerKey = ownerKey;
            this.ownerUuid = ownerUuid;
        }
    }

    /* ============================================================ */

    /**
     * Backup data associated with a 'GlobalLocks' instance.
     */
    static class LockBackup implements Bucket.Backup {
        /**
         * {@inheritDoc}
         */
        @Override
        public Bucket.Restore generate(int bucketNumber) {
            Bucket bucket = Bucket.getBucket(bucketNumber);

            // just remove 'LocalLocks' -- it will need to be rebuilt from
            // 'TargetLock' instances
            bucket.removeAdjunct(LocalLocks.class);

            // global locks need to be transferred
            GlobalLocks globalLocks = bucket.removeAdjunct(GlobalLocks.class);
            return globalLocks == null ? null : new LockRestore(globalLocks);
        }
    }

    /* ============================================================ */

    /**
     * This class is used to restore a 'GlobalLocks' instance from a backup.
     */
    static class LockRestore implements Bucket.Restore, Serializable {
        private static final long serialVersionUID = 1L;

        GlobalLocks globalLocks;

        /**
         * Constructor - runs as part of backup (deserialization bypasses this constructor).
         *
         * @param globalLocks GlobalLocks instance extracted as part of backup
         */
        LockRestore(GlobalLocks globalLocks) {
            this.globalLocks = globalLocks;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void restore(int bucketNumber) {
            // fetch bucket
            Bucket bucket = Bucket.getBucket(bucketNumber);

            // update the adjunct
            if (bucket.putAdjunct(globalLocks) != null) {
                logger.error("LockRestore({}): GlobalLocks adjunct already existed",
                             bucketNumber);
            }

            // notify features of the 'globalLocks' update
            for (ServerPoolApi feature : ServerPoolApi.impl.getList()) {
                feature.lockUpdate(bucket, globalLocks);
            }
        }
    }

    /* ============================================================ */

    /**
     * This class is a deamon that monitors the 'abandoned' queue. If an
     * ACTIVE 'TargetLock' is abandoned, the GC will eventually place the
     * now-empty 'WeakReference' in this queue.
     */
    private static class AbandonedHandler extends Thread {
        AbandonedHandler() {
            super("TargetLock.AbandonedHandler");
        }

        /**
         * This method camps on the 'abandoned' queue, processing entries as
         * they are received.
         */
        @Override
        public void run() {
            while (abandonedHandler != null) {
                try {
                    Reference<? extends TargetLock> wr = abandoned.remove();

                    // At this point, we know that 'ref' is a
                    // 'WeakReference<TargetLock>' instance that has been abandoned,
                    // but we don't know what the associated 'Identity' instance
                    // is. Here, we search through every bucket looking for a
                    // matching entry. The assumption is that this is rare enough,
                    // and due to a bug, so it doesn't hurt to spend extra CPU time
                    // here. The alternative is to add some additional information
                    // to make this mapping quick, at the expense of a slight
                    // slow down of normal lock operations.
                    for (int i = 0; i < Bucket.BUCKETCOUNT; i += 1) {
                        LocalLocks localLocks =
                            Bucket.getBucket(i).getAdjunctDontCreate(LocalLocks.class);
                        if (localLocks != null) {
                            // the adjunct does exist -- see if the WeakReference
                            // instance is known to this bucket
                            synchronized (localLocks) {
                                Identity identity =
                                    localLocks.weakReferenceToIdentity.get(wr);
                                if (identity != null) {
                                    // found it
                                    logger.error("Abandoned TargetLock: bucket={}, "
                                                 + "key={}, ownerKey={}, uuid={}",
                                                 i, identity.key, identity.ownerKey,
                                                 identity.uuid);
                                    identity.free();
                                    break;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.error("TargetLock.AbandonedHandler exception", e);
                }
            }
        }
    }

    // create a single instance of 'AbandonedHandler', and start it
    private static AbandonedHandler abandonedHandler = new AbandonedHandler();

    static {
        abandonedHandler.start();
    }

    /* ============================================================ */

    /**
     * This class handles the '/cmd/dumpLocks' REST command.
     */
    static class DumpLocks {
        // indicates whether a more detailed dump should be done
        private boolean detail;

        // this table maps the 'TargetLock' UUID into an object containing
        // both client (LocalLocks) and server (GlobalLocks) information
        private Map<UUID, MergedData> mergedDataMap =
            new TreeMap<>(Util.uuidComparator);

        // this table maps the 'TargetLock' key into the associated 'LockEntry'
        // (server end)
        private Map<String, LockEntry> lockEntries = new TreeMap<>();

        // this table maps the 'TargetLock' key into entries that only exist
        // on the client end
        private Map<String, MergedData> clientOnlyEntries = new TreeMap<>();

        // display format (although it is now dynamically adjusted)
        private String format = "%-14s %-14s %-36s %-10s %s\n";

        // calculation of maximum key length for display
        private int keyLength = 10;

        // calculation of maximum owner key length for display
        private int ownerKeyLength = 10;

        // 'true' if any comments need to be displayed (affects format)
        private boolean commentsIncluded = false;

        /**
         * Entry point for the '/cmd/dumpLocks' REST command.
         *
         * @param out where the output should be displayed
         * @param detail 'true' provides additional bucket and host information
         *     (but abbreviates all UUIDs in order to avoid excessive
         *     line length)
         */
        static void dumpLocks(PrintStream out, boolean detail)
            throws InterruptedException, IOException, ClassNotFoundException {

            // the actual work is done in the constructor
            new DumpLocks(out, detail);
        }

        /**
         * Entry point for the '/lock/dumpLocksData' REST command, which generates
         * a byte stream for this particular host.
         *
         * @param serverUuid the UUID of the intended destination server
         * @param ttl similar to IP time-to-live -- it controls the number of hops
         *     the message may take
         * @return a base64-encoded byte stream containing serialized 'HostData'
         */
        static byte[] dumpLocksData(UUID serverUuid, int ttl) throws IOException {
            if (!Server.getThisServer().getUuid().equals(serverUuid)) {
                ttl -= 1;
                if (ttl > 0) {
                    Server server = Server.getServer(serverUuid);
                    if (server != null) {
                        WebTarget webTarget =
                            server.getWebTarget("lock/dumpLocksData");
                        if (webTarget != null) {
                            logger.info("Forwarding 'lock/dumpLocksData' to uuid {}",
                                        serverUuid);
                            return webTarget
                                   .queryParam(QP_SERVER, serverUuid.toString())
                                   .queryParam(QP_TTL, String.valueOf(ttl))
                                   .request().get(byte[].class);
                        }
                    }
                }

                // if we reach this point, we didn't forward for some reason

                logger.error("Couldn't forward 'lock/dumpLocksData to uuid {}",
                             serverUuid);
                return null;
            }

            return Base64.getEncoder().encode(Util.serialize(new HostData()));
        }

        /**
         * Constructor - does the '/cmd/dumpLocks' REST command.
         *
         * @param out where the output should be displayed
         */
        DumpLocks(PrintStream out, boolean detail)
            throws IOException, InterruptedException, ClassNotFoundException {

            this.detail = detail;

            // receives responses from  '/lock/dumpLocksData'
            final LinkedTransferQueue<Response> responseQueue =
                new LinkedTransferQueue<>();

            // generate a count of the number of external servers that should respond
            int pendingResponseCount = 0;

            // iterate over all of the servers
            for (final Server server : Server.getServers()) {
                if (server == Server.getThisServer()) {
                    // skip this server -- we will handle it differently
                    continue;
                }

                // keep a running count
                pendingResponseCount += 1;
                server.post("lock/dumpLocksData", null, new Server.PostResponse() {
                    @Override
                    public WebTarget webTarget(WebTarget webTarget) {
                        return webTarget
                               .queryParam(QP_SERVER, server.getUuid().toString())
                               .queryParam(QP_TTL, timeToLive);
                    }

                    @Override
                    public void response(Response response) {
                        // responses are queued, and the main thread will collect them
                        responseQueue.put(response);
                    }
                });
            }

            // this handles data associated with this server -- it also goes through
            // serialization/deserialization, which provides a deep copy of the data
            populateLockData(dumpLocksData(Server.getThisServer().getUuid(), 0));

            // now, poll for responses from all of the the other servers
            while (pendingResponseCount > 0) {
                pendingResponseCount -= 1;
                Response response = responseQueue.poll(60, TimeUnit.SECONDS);
                if (response == null) {
                    // timeout -- we aren't expecting any more responses
                    break;
                }

                // populate data associated with this server
                populateLockData(response.readEntity(byte[].class));
            }

            // we have processed all of the servers that we are going to,
            // now generate the output
            dump(out);
        }

        /**
         * process base64-encoded data from a server (local or remote).
         *
         * @param data base64-encoded data (class 'HostData')
         */
        void populateLockData(byte[] data) throws IOException, ClassNotFoundException {
            Object decodedData = Util.deserialize(Base64.getDecoder().decode(data));
            if (decodedData instanceof HostData) {
                // deserialized data
                HostData hostData = (HostData) decodedData;

                // fetch 'Server' instance associated with the responding server
                Server server = Server.getServer(hostData.hostUuid);

                // process the client-end data
                for (ClientData clientData : hostData.clientDataList) {
                    populateLockData_clientData(clientData, server);
                }

                // process the server-end data
                for (ServerData serverData : hostData.serverDataList) {
                    populateLockData_serverData(serverData, server);
                }
            } else {
                logger.error("TargetLock.DumpLocks.populateLockData: "
                             + "received data has class {}",
                             decodedData.getClass().getName());
            }
        }

        private void populateLockData_clientData(ClientData clientData, Server server) {
            // 'true' if the bucket associated with this 'ClientData'
            // doesn't belong to the remote server, as far as we can tell
            boolean serverMismatch =
                Bucket.bucketToServer(clientData.bucketNumber) != server;

            // each 'ClientDataRecord' instance corresponds to an
            // active 'Identity' (TargetLock) instance
            for (ClientDataRecord cdr : clientData.clientDataRecords) {
                // update maximum 'key' and 'ownerKey' lengths
                updateKeyLength(cdr.identity.key);
                updateOwnerKeyLength(cdr.identity.ownerKey);

                // fetch UUID
                UUID uuid = cdr.identity.uuid;

                // fetch/generate 'MergeData' instance for this UUID
                MergedData md = mergedDataMap.get(uuid);
                if (md == null) {
                    md = new MergedData(uuid);
                    mergedDataMap.put(uuid, md);
                }

                // update 'MergedData.clientDataRecord'
                if (md.clientDataRecord == null) {
                    md.clientDataRecord = cdr;
                } else {
                    md.comment("Duplicate client entry for UUID");
                }

                if (serverMismatch) {
                    // need to generate an additional error
                    md.comment(server.toString()
                               + "(client) does not own bucket "
                               + clientData.bucketNumber);
                }
            }
        }

        private void populateLockData_serverData(ServerData serverData, Server server) {
            // 'true' if the bucket associated with this 'ServerData'
            // doesn't belong to the remote server, as far as we can tell
            boolean serverMismatch =
                Bucket.bucketToServer(serverData.bucketNumber) != server;

            // each 'LockEntry' instance corresponds to the current holder
            // of a lock, and all requestors waiting for it to be freed
            for (LockEntry le : serverData.globalLocks.keyToEntry.values()) {
                // update maximum 'key' and 'ownerKey' lengths
                updateKeyLength(le.key);
                updateOwnerKeyLength(le.currentOwnerKey);

                // fetch uuid
                UUID uuid = le.currentOwnerUuid;

                // fetch/generate 'MergeData' instance for this UUID
                MergedData md = mergedDataMap.get(uuid);
                if (md == null) {
                    md = new MergedData(uuid);
                    mergedDataMap.put(uuid, md);
                }

                // update 'lockEntries' table entry
                if (lockEntries.get(le.key) != null) {
                    md.comment("Duplicate server entry for key " + le.key);
                } else {
                    lockEntries.put(le.key, le);
                }

                // update 'MergedData.serverLockEntry'
                // (leave 'MergedData.serverWaiting' as 'null', because
                // this field is only used for waiting entries)
                if (md.serverLockEntry == null) {
                    md.serverLockEntry = le;
                } else {
                    md.comment("Duplicate server entry for UUID");
                }

                if (serverMismatch) {
                    // need to generate an additional error
                    md.comment(server.toString()
                               + "(server) does not own bucket "
                               + serverData.bucketNumber);
                }

                // we need 'MergeData' entries for all waiting requests
                for (Waiting waiting : le.waitingList) {
                    populateLockData_serverData_waiting(
                        serverData, server, serverMismatch, le, waiting);
                }
            }
        }

        private void populateLockData_serverData_waiting(
            ServerData serverData, Server server, boolean serverMismatch,
            LockEntry le, Waiting waiting) {

            // update maximum 'ownerKey' length
            updateOwnerKeyLength(waiting.ownerKey);

            // fetch uuid
            UUID uuid = waiting.ownerUuid;

            // fetch/generate 'MergeData' instance for this UUID
            MergedData md = mergedDataMap.get(uuid);
            if (md == null) {
                md = new MergedData(uuid);
                mergedDataMap.put(uuid, md);
            }

            // update 'MergedData.serverLockEntry' and
            // 'MergedData.serverWaiting'
            if (md.serverLockEntry == null) {
                md.serverLockEntry = le;
                md.serverWaiting = waiting;
            } else {
                md.comment("Duplicate server entry for UUID");
            }

            if (serverMismatch) {
                // need to generate an additional error
                md.comment(server.toString()
                           + "(server) does not own bucket "
                           + serverData.bucketNumber);
            }
        }

        /**
         * Do some additional sanity checks on the 'MergedData', and then
         * display all of the results.
         *
         * @param out where the output should be displayed
         */
        void dump(PrintStream out) {
            // iterate over the 'MergedData' instances looking for problems
            for (MergedData md : mergedDataMap.values()) {
                if (md.clientDataRecord == null) {
                    md.comment("Client data missing");
                } else if (md.serverLockEntry == null) {
                    md.comment("Server data missing");
                    clientOnlyEntries.put(md.clientDataRecord.identity.key, md);
                } else if (!md.clientDataRecord.identity.key.equals(md.serverLockEntry.key)) {
                    md.comment("Client key(" + md.clientDataRecord.identity.key
                               + ") server key(" + md.serverLockEntry.key
                               + ") mismatch");
                } else {
                    String serverOwnerKey = (md.serverWaiting == null
                        ? md.serverLockEntry.currentOwnerKey : md.serverWaiting.ownerKey);
                    if (!md.clientDataRecord.identity.ownerKey.equals(serverOwnerKey)) {
                        md.comment("Client owner key("
                                   + md.clientDataRecord.identity.ownerKey
                                   + ") server owner key(" + serverOwnerKey
                                   + ") mismatch");
                    }
                    // TBD: test for state mismatch
                }
            }

            if (detail) {
                // generate format based upon the maximum key length, maximum
                // owner key length, and whether comments are included anywhere
                format = "%-" + keyLength + "s %6s %-9s  %-" + ownerKeyLength
                         + "s %6s %-9s  %-9s %-10s" + (commentsIncluded ? " %s\n" : "\n");

                // dump out the header
                out.printf(format, "Key", "Bucket", "Host UUID",
                           "Owner Key", "Bucket", "Host UUID",
                           "Lock UUID", "State", "Comments");
                out.printf(format, "---", "------", "---------",
                           "---------", "------", "---------",
                           "---------", "-----", "--------");
            } else {
                // generate format based upon the maximum key length, maximum
                // owner key length, and whether comments are included anywhere
                format = "%-" + keyLength + "s %-" + ownerKeyLength
                         + "s %-36s %-10s" + (commentsIncluded ? " %s\n" : "\n");

                // dump out the header
                out.printf(format, "Key", "Owner Key", "UUID", "State", "Comments");
                out.printf(format, "---", "---------", "----", "-----", "--------");
            }

            dump_serverTable(out);
            dump_clientOnlyEntries(out);
        }

        private void dump_serverTable(PrintStream out) {
            // iterate over the server table
            for (LockEntry le : lockEntries.values()) {
                // fetch merged data
                MergedData md = mergedDataMap.get(le.currentOwnerUuid);

                // dump out record associated with lock owner
                if (detail) {
                    out.printf(format,
                               le.key, getBucket(le.key), bucketOwnerUuid(le.key),
                               le.currentOwnerKey, getBucket(le.currentOwnerKey),
                               bucketOwnerUuid(le.currentOwnerKey),
                               abbrevUuid(le.currentOwnerUuid),
                               md.getState(), md.firstComment());
                } else {
                    out.printf(format,
                               le.key, le.currentOwnerKey, le.currentOwnerUuid,
                               md.getState(), md.firstComment());
                }
                dumpMoreComments(out, md);

                // iterate over all requests waiting for this lock
                for (Waiting waiting: le.waitingList) {
                    // fetch merged data
                    md = mergedDataMap.get(waiting.ownerUuid);

                    // dump out record associated with waiting request
                    if (detail) {
                        out.printf(format,
                                   "", "", "",
                                   waiting.ownerKey, getBucket(waiting.ownerKey),
                                   bucketOwnerUuid(waiting.ownerKey),
                                   abbrevUuid(waiting.ownerUuid),
                                   md.getState(), md.firstComment());
                    } else {
                        out.printf(format, "", waiting.ownerKey, waiting.ownerUuid,
                                   md.getState(), md.firstComment());
                    }
                    dumpMoreComments(out, md);
                }
            }
        }

        private void dump_clientOnlyEntries(PrintStream out) {
            // client records that don't have matching server entries
            for (MergedData md : clientOnlyEntries.values()) {
                ClientDataRecord cdr = md.clientDataRecord;
                if (detail) {
                    out.printf(format,
                               cdr.identity.key, getBucket(cdr.identity.key),
                               bucketOwnerUuid(cdr.identity.key),
                               cdr.identity.ownerKey,
                               getBucket(cdr.identity.ownerKey),
                               bucketOwnerUuid(cdr.identity.ownerKey),
                               abbrevUuid(cdr.identity.uuid),
                               md.getState(), md.firstComment());
                } else {
                    out.printf(format, cdr.identity.key, cdr.identity.ownerKey,
                               cdr.identity.uuid, md.getState(), md.firstComment());
                }
                dumpMoreComments(out, md);
            }
        }

        /**
         * This method converts a String keyword into the corresponding bucket
         * number.
         *
         * @param key the keyword to be converted
         * @return the bucket number
         */
        private static int getBucket(String key) {
            return Bucket.bucketNumber(key);
        }

        /**
         * Determine the abbreviated UUID associated with a key.
         *
         * @param key the keyword to be converted
         * @return the abbreviated UUID of the bucket owner
         */
        private static String bucketOwnerUuid(String key) {
            // fetch the bucket
            Bucket bucket = Bucket.getBucket(Bucket.bucketNumber(key));

            // fetch the bucket owner (may be 'null' if unassigned)
            Server owner = bucket.getOwner();

            return owner == null ? "NONE" : abbrevUuid(owner.getUuid());
        }

        /**
         * Convert a UUID to an abbreviated form, which is the
         * first 8 hex digits of the UUID, followed by the character '*'.
         *
         * @param uuid the UUID to convert
         * @return the abbreviated form
         */
        private static String abbrevUuid(UUID uuid) {
            return uuid.toString().substring(0, 8) + "*";
        }

        /**
         * If the 'MergedData' instance has more than one comment,
         * dump out comments 2-n.
         *
         * @param out where the output should be displayed
         * @param md the MergedData instance
         */
        void dumpMoreComments(PrintStream out, MergedData md) {
            if (md.comments.size() > 1) {
                Queue<String> comments = new LinkedList<>(md.comments);

                // remove the first entry, because it has already been displayed
                comments.remove();
                for (String comment : comments) {
                    if (detail) {
                        out.printf(format, "", "", "", "", "", "", "", "", comment);
                    } else {
                        out.printf(format, "", "", "", "", comment);
                    }
                }
            }
        }

        /**
         * Check the length of the specified 'key', and update 'keyLength' if
         * it exceeds the current maximum.
         *
         * @param key the key to be tested
         */
        void updateKeyLength(String key) {
            int length = key.length();
            if (length > keyLength) {
                keyLength = length;
            }
        }

        /**
         * Check the length of the specified 'ownerKey', and update
         * 'ownerKeyLength' if it exceeds the current maximum.
         *
         * @param ownerKey the owner key to be tested
         */
        void updateOwnerKeyLength(String ownerKey) {
            int length = ownerKey.length();
            if (length > ownerKeyLength) {
                ownerKeyLength = length;
            }
        }

        /* ============================== */

        /**
         * Each instance of this class corresponds to client and/or server
         * data structures, and is used to check consistency between the two.
         */
        class MergedData {
            // the client/server UUID
            UUID uuid;

            // client-side data (from LocalLocks)
            ClientDataRecord clientDataRecord = null;

            // server-side data (from GlobalLocks)
            LockEntry serverLockEntry = null;
            Waiting serverWaiting = null;

            // detected problems, such as server/client mismatches
            Queue<String> comments = new LinkedList<String>();

            /**
             * Constructor - initialize the 'uuid'.
             *
             * @param uuid the UUID that identifies the original 'TargetLock'
             */
            MergedData(UUID uuid) {
                this.uuid = uuid;
            }

            /**
             * add a comment to the list, and indicate that there are now
             * comments present.
             *
             * @param co the comment to add
             */
            void comment(String co) {
                comments.add(co);
                commentsIncluded = true;
            }

            /**
             * Return the first comment, or an empty string if there are no
             *     comments.
             *
             * @return the first comment, or an empty string if there are no
             *     comments (useful for formatting output).
             */
            String firstComment() {
                return comments.isEmpty() ? "" : comments.poll();
            }

            /**
             * Return a string description of the state.
             *
             * @return a string description of the state.
             */
            String getState() {
                return clientDataRecord == null
                    ? "MISSING" : clientDataRecord.state.toString();
            }
        }

        /**
         * This class contains all of the data sent from each host to the
         * host that is consolidating the information for display.
         */
        static class HostData implements Serializable {
            private static final long serialVersionUID = 1L;

            // the UUID of the host sending the data
            private UUID hostUuid;

            // all of the information derived from the 'LocalLocks' data
            private List<ClientData> clientDataList;

            // all of the information derived from the 'GlobalLocks' data
            private List<ServerData> serverDataList;

            /**
             * Constructor - this goes through all of the lock tables,
             * and populates 'clientDataList' and 'serverDataList'.
             */
            HostData() {
                // fetch UUID
                hostUuid = Server.getThisServer().getUuid();

                // initial storage for client and server data
                clientDataList = new ArrayList<ClientData>();
                serverDataList = new ArrayList<ServerData>();

                // go through buckets
                for (int i = 0; i < Bucket.BUCKETCOUNT; i += 1) {
                    Bucket bucket = Bucket.getBucket(i);

                    // client data
                    LocalLocks localLocks =
                        bucket.getAdjunctDontCreate(LocalLocks.class);
                    if (localLocks != null) {
                        // we have client data for this bucket
                        ClientData clientData = new ClientData(i);
                        clientDataList.add(clientData);

                        synchronized (localLocks) {
                            for (WeakReference<TargetLock> wr :
                                    localLocks.weakReferenceToIdentity.keySet()) {
                                // Note: 'targetLock' may be 'null' if it has
                                // been abandoned, and garbage collected
                                TargetLock targetLock = wr.get();

                                // fetch associated 'identity'
                                Identity identity =
                                    localLocks.weakReferenceToIdentity.get(wr);
                                if (identity != null) {
                                    // add a new 'ClientDataRecord' for this bucket
                                    clientData.clientDataRecords.add(
                                        new ClientDataRecord(identity,
                                            (targetLock == null ? null :
                                            targetLock.getState())));
                                }
                            }
                        }
                    }

                    // server data
                    GlobalLocks globalLocks =
                        bucket.getAdjunctDontCreate(GlobalLocks.class);
                    if (globalLocks != null) {
                        // server data is already in serializable form
                        serverDataList.add(new ServerData(i, globalLocks));
                    }
                }
            }
        }

        /**
         * Information derived from the 'LocalLocks' adjunct to a single bucket.
         */
        static class ClientData implements Serializable {
            private static final long serialVersionUID = 1L;

            // number of the bucket
            private int bucketNumber;

            // all of the client locks within this bucket
            private List<ClientDataRecord> clientDataRecords;

            /**
             * Constructor - initially, there are no 'clientDataRecords'.
             *
             * @param bucketNumber the bucket these records are associated with
             */
            ClientData(int bucketNumber) {
                this.bucketNumber = bucketNumber;
                clientDataRecords = new ArrayList<>();
            }
        }

        /**
         * This corresponds to the information contained within a
         * single 'TargetLock'.
         */
        static class ClientDataRecord implements Serializable {
            private static final long serialVersionUID = 1L;

            // contains key, ownerKey, uuid
            private Identity identity;

            // state field of 'TargetLock'
            // (may be 'null' if there is no 'TargetLock')
            private State state;

            /**
             * Constructor - initialize the fields.
             *
             * @param identity contains key, ownerKey, uuid
             * @param state the state if the 'TargetLock' exists, and 'null' if it
             *     has been garbage collected
             */
            ClientDataRecord(Identity identity, State state) {
                this.identity = identity;
                this.state = state;
            }
        }

        /**
         * Information derived from the 'GlobalLocks' adjunct to a single bucket.
         */
        static class ServerData implements Serializable {
            private static final long serialVersionUID = 1L;

            // number of the bucket
            private int bucketNumber;

            // server-side data associated with a single bucket
            private GlobalLocks globalLocks;

            /**
             * Constructor - initialize the fields.
             *
             * @param bucketNumber the bucket these records are associated with
             * @param globalLocks GlobalLocks instance associated with 'bucketNumber'
             */
            ServerData(int bucketNumber, GlobalLocks globalLocks) {
                this.bucketNumber = bucketNumber;
                this.globalLocks = globalLocks;
            }
        }
    }

    /* ============================================================ */

    /**
     * Instances of 'AuditData' are passed between servers as part of the
     * 'TargetLock' audit.
     */
    static class AuditData implements Serializable {
        private static final long serialVersionUID = 1L;

        // sending UUID
        private UUID hostUuid;

        // client records that currently exist, or records to be cleared
        // (depending upon message) -- client/server is from the senders side
        private List<Identity> clientData;

        // server records that currently exist, or records to be cleared
        // (depending upon message) -- client/server is from the senders side
        private List<Identity> serverData;

        /**
         * Constructor - set 'hostUuid' to the current host, and start with
         * empty lists.
         */
        AuditData() {
            hostUuid = Server.getThisServer().getUuid();
            clientData = new ArrayList<>();
            serverData = new ArrayList<>();
        }

        /**
         * This is called when we receive an incoming 'AuditData' object from
         * a remote host.
         *
         * @param includeWarnings if 'true', generate warning messages
         *     for mismatches
         * @return an 'AuditData' instance that only contains records we
         *     can't confirm
         */
        AuditData generateResponse(boolean includeWarnings) {
            AuditData response = new AuditData();

            // compare remote servers client data with our server data
            generateResponse_clientEnd(response, includeWarnings);

            // test server data
            generateResponse_serverEnd(response, includeWarnings);

            return response;
        }

        private void generateResponse_clientEnd(AuditData response, boolean includeWarnings) {
            for (Identity identity : clientData) {
                // remote end is the client, and we are the server
                Bucket bucket = Bucket.getBucket(identity.key);
                GlobalLocks globalLocks =
                    bucket.getAdjunctDontCreate(GlobalLocks.class);

                if (globalLocks != null) {
                    Map<String, LockEntry> keyToEntry = globalLocks.keyToEntry;
                    synchronized (keyToEntry) {
                        LockEntry le = keyToEntry.get(identity.key);
                        if (le != null) {
                            if (identity.uuid.equals(le.currentOwnerUuid)
                                    && identity.ownerKey.equals(le.currentOwnerKey)) {
                                // we found a match
                                continue;
                            }

                            // check the waiting list
                            boolean match = false;
                            for (Waiting waiting : le.waitingList) {
                                if (identity.uuid.equals(waiting.ownerUuid)
                                        && identity.ownerKey.equals(waiting.ownerKey)) {
                                    // we found a match on the waiting list
                                    match = true;
                                    break;
                                }
                            }
                            if (match) {
                                // there was a match on the waiting list
                                continue;
                            }
                        }
                    }
                }

                // If we reach this point, a match was not confirmed. Note that it
                // is possible that we got caught in a transient state, so we need
                // to somehow make sure that we don't "correct" a problem that
                // isn't real.

                if (includeWarnings) {
                    logger.warn("TargetLock audit issue: server match not found "
                                + "(key={},ownerKey={},uuid={})",
                                identity.key, identity.ownerKey, identity.uuid);
                }

                // it was 'clientData' to the sender, but 'serverData' to us
                response.serverData.add(identity);
            }
        }

        private void generateResponse_serverEnd(AuditData response, boolean includeWarnings) {
            for (Identity identity : serverData) {
                // remote end is the server, and we are the client
                Bucket bucket = Bucket.getBucket(identity.ownerKey);
                LocalLocks localLocks =
                    bucket.getAdjunctDontCreate(LocalLocks.class);
                if (localLocks != null) {
                    synchronized (localLocks) {
                        WeakReference<TargetLock> wr =
                            localLocks.uuidToWeakReference.get(identity.uuid);
                        if (wr != null) {
                            Identity identity2 =
                                localLocks.weakReferenceToIdentity.get(wr);
                            if (identity2 != null
                                    && identity.key.equals(identity2.key)
                                    && identity.ownerKey.equals(identity2.ownerKey)) {
                                // we have a match
                                continue;
                            }
                        }
                    }
                }

                // If we reach this point, a match was not confirmed. Note that it
                // is possible that we got caught in a transient state, so we need
                // to somehow make sure that we don't "correct" a problem that
                // isn't real.
                if (includeWarnings) {
                    logger.warn("TargetLock audit issue: client match not found "
                                + "(key={},ownerKey={},uuid={})",
                                identity.key, identity.ownerKey, identity.uuid);
                }
                response.clientData.add(identity);
            }
        }

        /**
         * The response messages contain 'Identity' objects that match those
         * in our outgoing '/lock/audit' message, but that the remote end could
         * not confirm. Again, the definition of 'client' and 'server' are
         * the remote ends' version.
         *
         * @param server the server we sent the request to
         */
        void processResponse(Server server) {
            if (clientData.isEmpty() && serverData.isEmpty()) {
                // no mismatches
                logger.info("TargetLock audit with {} completed -- no mismatches",
                            server);
                return;
            }

            // There are still mismatches -- note that 'clientData' and
            // 'serverData' are from the remote end's perspective, which is the
            // opposite of this end

            for (Identity identity : clientData) {
                // these are on our server end -- we were showing a lock on this
                // end, but the other end has no such client
                logger.error("Audit mismatch (GlobalLocks): (key={},owner={},uuid={})",
                    identity.key, identity.ownerKey, identity.uuid);

                // free the lock
                GlobalLocks.get(identity.key).unlock(identity.key, identity.uuid);
            }
            for (Identity identity : serverData) {
                // these are on our client end
                logger.error("Audit mismatch (LocalLocks): (key={},owner={},uuid={})",
                     identity.key, identity.ownerKey, identity.uuid);

                // clean up 'LocalLocks' tables
                LocalLocks localLocks = LocalLocks.get(identity.ownerKey);
                TargetLock targetLock = null;
                synchronized (localLocks) {
                    WeakReference<TargetLock> wr =
                        localLocks.uuidToWeakReference.get(identity.uuid);
                    if (wr != null) {
                        targetLock = wr.get();
                        localLocks.weakReferenceToIdentity.remove(wr);
                        localLocks.uuidToWeakReference
                        .remove(identity.uuid);
                        wr.clear();
                    }
                }

                if (targetLock != null) {
                    // may need to update state
                    synchronized (targetLock) {
                        if (targetLock.state != State.FREE) {
                            targetLock.state = State.LOST;
                        }
                    }
                }
            }
            logger.info("TargetLock audit with {} completed -- {} mismatches",
                        server, clientData.size() + serverData.size());
        }

        /**
         * Serialize and base64-encode this 'AuditData' instance, so it can
         * be sent in a message.
         *
         * @return a byte array, which can be decoded and deserialized at
         *     the other end ('null' is returned if there were any problems)
         */
        byte[] encode() {
            try {
                return Base64.getEncoder().encode(Util.serialize(this));
            } catch (IOException e) {
                logger.error("TargetLock.AuditData.encode Exception", e);
                return null;
            }
        }

        /**
         * Base64-decode and deserialize a byte array.
         *
         * @param encodedData a byte array encoded via 'AuditData.encode'
         *     (typically on the remote end of a connection)
         * @return an 'AuditData' instance if decoding was successful,
         *     and 'null' if not
         */
        static AuditData decode(byte[] encodedData) {
            try {
                Object decodedData =
                    Util.deserialize(Base64.getDecoder().decode(encodedData));
                if (decodedData instanceof AuditData) {
                    return (AuditData) decodedData;
                } else {
                    logger.error(
                        "TargetLock.AuditData.decode returned instance of class {}",
                        decodedData.getClass().getName());
                    return null;
                }
            } catch (IOException | ClassNotFoundException e) {
                logger.error("TargetLock.AuditData.decode Exception", e);
                return null;
            }
        }
    }

    /**
     * This class contains methods that control the audit. Also, sn instance of
     * 'Audit' is created for each audit that is in progress.
     */
    static class Audit {
        // if non-null, it means that we have a timer set that periodicall
        // triggers the audit
        static TimerTask timerTask = null;

        // maps 'Server' to audit data associated with that server
        Map<Server, AuditData> auditMap = new IdentityHashMap<>();

        /**
         * Run a single audit cycle.
         */
        static void runAudit() {
            logger.info("Starting TargetLock audit");
            Audit audit = new Audit();

            // populate 'auditMap' table
            audit.build();

            // send to all of the servers in 'auditMap' (may include this server)
            audit.send();
        }

        /**
         * Schedule the audit to run periodically based upon defined properties.
         */
        static void scheduleAudit() {
            scheduleAudit(auditPeriod, auditGracePeriod);
        }

        /**
         * Schedule the audit to run periodically -- all of the hosts arrange to
         * run their audit at a different time, evenly spaced across the audit
         * period.
         *
         * @param period how frequently to run the audit, in milliseconds
         * @param gracePeriod ensure that the audit doesn't run until at least
         *     'gracePeriod' milliseconds have passed from the current time
         */
        static synchronized void scheduleAudit(final long period, final long gracePeriod) {

            if (timerTask != null) {
                // cancel current timer
                timerTask.cancel();
                timerTask = null;
            }

            // this needs to run in the 'MainLoop' thread, because it is dependent
            // upon the list of servers, and our position in this list
            MainLoop.queueWork(() -> {
                // this runs in the 'MainLoop' thread

                // current list of servers
                Collection<Server> servers = Server.getServers();

                // count of the number of servers
                int count = servers.size();

                // will contain our position in this list
                int index = 0;

                // current server
                Server thisServer = Server.getThisServer();

                for (Server server : servers) {
                    if (server == thisServer) {
                        break;
                    }
                    index += 1;
                }

                // if index == count, we didn't find this server
                // (which shouldn't happen)

                if (index < count) {
                    // The servers are ordered by UUID, and 'index' is this
                    // server's position within the list. Suppose the period is
                    // 60000 (60 seconds), and there are 5 servers -- the first one
                    // will run the audit at 0 seconds after the minute, the next
                    // at 12 seconds after the minute, then 24, 36, 48.
                    long offset = (period * index) / count;

                    // the earliest time we want the audit to run
                    long time = System.currentTimeMillis() + gracePeriod;
                    long startTime = time - (time % period) + offset;
                    if (startTime <= time) {
                        startTime += period;
                    }
                    synchronized (Audit.class) {
                        if (timerTask != null) {
                            timerTask.cancel();
                        }
                        timerTask = new TimerTask() {
                            @Override
                            public void run() {
                                runAudit();
                            }
                        };

                        // now, schedule the timer
                        Util.timer.scheduleAtFixedRate(
                            timerTask, new Date(startTime), period);
                    }
                }
            });
        }

        /**
         * Handle an incoming '/lock/audit' message.
         *
         * @param serverUuid the UUID of the intended destination server
         * @param ttl similar to IP time-to-live -- it controls the number of hops
         * @param data base64-encoded data, containing a serialized 'AuditData'
         *     instance
         * @return a serialized and base64-encoded 'AuditData' response
         */
        static byte[] incomingAudit(UUID serverUuid, int ttl, byte[] encodedData) {
            if (!Server.getThisServer().getUuid().equals(serverUuid)) {
                ttl -= 1;
                if (ttl > 0) {
                    Server server = Server.getServer(serverUuid);
                    if (server != null) {
                        WebTarget webTarget = server.getWebTarget("lock/audit");
                        if (webTarget != null) {
                            logger.info("Forwarding 'lock/audit' to uuid {}",
                                        serverUuid);
                            Entity<String> entity =
                                Entity.entity(new String(encodedData),
                                              MediaType.APPLICATION_OCTET_STREAM_TYPE);
                            return webTarget
                                   .queryParam(QP_SERVER, serverUuid.toString())
                                   .queryParam(QP_TTL, String.valueOf(ttl))
                                   .request().post(entity, byte[].class);
                        }
                    }
                }

                // if we reach this point, we didn't forward for some reason

                logger.error("Couldn't forward 'lock/audit to uuid {}", serverUuid);
                return null;
            }

            AuditData auditData = AuditData.decode(encodedData);
            if (auditData != null) {
                AuditData auditResp = auditData.generateResponse(true);
                return auditResp.encode();
            }
            return null;
        }

        /**
         * This method populates the 'auditMap' table by going through all of
         * the client and server lock data, and sorting it according to the
         * remote server.
         */
        void build() {
            for (int i = 0; i < Bucket.BUCKETCOUNT; i += 1) {
                Bucket bucket = Bucket.getBucket(i);

                // client data
                buildClientData(bucket);

                // server data
                buildServerData(bucket);
            }
        }

        private void buildClientData(Bucket bucket) {
            // client data
            LocalLocks localLocks =
                bucket.getAdjunctDontCreate(LocalLocks.class);
            if (localLocks != null) {
                synchronized (localLocks) {
                    // we have client data for this bucket
                    for (Identity identity :
                            localLocks.weakReferenceToIdentity.values()) {
                        // find or create the 'AuditData' instance associated
                        // with the server owning the 'key'
                        AuditData auditData = getAuditData(identity.key);
                        if (auditData != null) {
                            auditData.clientData.add(identity);
                        }
                    }
                }
            }
        }

        private void buildServerData(Bucket bucket) {
            // server data
            GlobalLocks globalLocks =
                bucket.getAdjunctDontCreate(GlobalLocks.class);
            if (globalLocks != null) {
                // we have server data for this bucket
                Map<String, LockEntry> keyToEntry = globalLocks.keyToEntry;
                synchronized (keyToEntry) {
                    for (LockEntry le : keyToEntry.values()) {
                        // find or create the 'AuditData' instance associated
                        // with the current 'ownerKey'
                        AuditData auditData = getAuditData(le.currentOwnerKey);
                        if (auditData != null) {
                            // create an 'Identity' entry, and add it to
                            // the list associated with the remote server
                            auditData.serverData.add(
                                new Identity(le.key, le.currentOwnerKey,
                                             le.currentOwnerUuid));
                        }

                        for (Waiting waiting : le.waitingList) {
                            // find or create the 'AuditData' instance associated
                            // with the waiting entry 'ownerKey'
                            auditData = getAuditData(waiting.ownerKey);
                            if (auditData != null) {
                                // create an 'Identity' entry, and add it to
                                // the list associated with the remote server
                                auditData.serverData.add(
                                    new Identity(le.key, waiting.ownerKey,
                                                 waiting.ownerUuid));
                            }
                        }
                    }
                }
            }
        }

        /**
         * Find or create the 'AuditData' structure associated with a particular
         * key.
         */
        AuditData getAuditData(String key) {
            // map 'key -> bucket number', and then 'bucket number' -> 'server'
            Server server = Bucket.bucketToServer(Bucket.bucketNumber(key));
            if (server != null) {
                AuditData auditData =
                    auditMap.computeIfAbsent(server, sk -> new AuditData());
                return auditData;
            }

            // this happens when the bucket has not been assigned to a server yet
            return null;
        }

        /**
         * Using the collected 'auditMap', send out the messages to all of the
         * servers.
         */
        void send() {
            if (auditMap.isEmpty()) {
                logger.info("TargetLock audit: no locks on this server");
            } else {
                logger.info("TargetLock audit: sending audit information to {}",
                            auditMap.keySet());
            }

            for (final Server server : auditMap.keySet()) {
                send_server(server);
            }
        }

        private void send_server(final Server server) {
            // fetch audit data
            AuditData auditData = auditMap.get(server);

            if (server == Server.getThisServer()) {
                // process this locally
                final AuditData respData = auditData.generateResponse(true);
                if (respData.clientData.isEmpty()
                        && respData.serverData.isEmpty()) {
                    // no mismatches
                    logger.info("TargetLock.Audit.send: "
                                + "no errors from self ({})", server);
                    return;
                }

                // do the rest in a separate thread
                server.getThreadPool().execute(() -> {
                    // wait a few seconds, and see if we still know of these
                    // errors
                    if (AuditPostResponse.responseSupport(
                        respData, "self (" + server + ")",
                        "TargetLock.Audit.send")) {
                        // a return falue of 'true' either indicates the
                        // mismatches were resolved after a retry, or we
                        // received an interrupt, and need to abort
                        return;
                    }

                    // any mismatches left in 'respData' are still issues
                    respData.processResponse(server);
                });
                return;
            }

            // serialize
            byte[] encodedData = auditData.encode();
            if (encodedData == null) {
                // error has already been displayed
                return;
            }

            // generate entity
            Entity<String> entity =
                Entity.entity(new String(encodedData),
                              MediaType.APPLICATION_OCTET_STREAM_TYPE);

            server.post("lock/audit", entity, new AuditPostResponse(server));
        }
    }

    static class AuditPostResponse implements Server.PostResponse {
        private Server server;

        AuditPostResponse(Server server) {
            this.server = server;
        }

        @Override
        public WebTarget webTarget(WebTarget webTarget) {
            // include the 'uuid' keyword
            return webTarget
                   .queryParam(QP_SERVER, server.getUuid().toString())
                   .queryParam(QP_TTL, timeToLive);
        }

        @Override
        public void response(Response response) {
            // process the response here
            AuditData respData =
                AuditData.decode(response.readEntity(byte[].class));
            if (respData == null) {
                logger.error("TargetLock.Audit.send: "
                             + "couldn't process response from {}",
                             server);
                return;
            }

            // if we reach this point, we got a response
            if (respData.clientData.isEmpty()
                    && respData.serverData.isEmpty()) {
                // no mismatches
                logger.info("TargetLock.Audit.send: "
                            + "no errors from {}", server);
                return;
            }

            // wait a few seconds, and see if we still know of these
            // errors
            if (responseSupport(respData, server, "AuditPostResponse.response")) {
                // a return falue of 'true' either indicates the mismatches
                // were resolved after a retry, or we received an interrupt,
                // and need to abort
                return;
            }

            // any mismatches left in 'respData' are still there --
            // hopefully, they are transient issues on the other side
            AuditData auditData = new AuditData();
            auditData.clientData = respData.serverData;
            auditData.serverData = respData.clientData;

            // serialize
            byte[] encodedData = auditData.encode();
            if (encodedData == null) {
                // error has already been displayed
                return;
            }

            // generate entity
            Entity<String> entity =
                Entity.entity(new String(encodedData),
                              MediaType.APPLICATION_OCTET_STREAM_TYPE);

            // send new list to other end
            response = server
                       .getWebTarget("lock/audit")
                       .queryParam(QP_SERVER, server.getUuid().toString())
                       .queryParam(QP_TTL, timeToLive)
                       .request().post(entity);

            respData = AuditData.decode(response.readEntity(byte[].class));
            if (respData == null) {
                logger.error("TargetLock.auditDataBuilder.send: "
                             + "couldn't process response from {}",
                             server);
                return;
            }

            // if there are mismatches left, they are presumably real
            respData.processResponse(server);
        }

        // Handle mismatches indicated by an audit response -- a return value of
        // 'true' indicates that there were no mismatches after a retry, or
        // we received an interrupt. In either case, the caller returns.
        private static boolean responseSupport(AuditData respData, Object serverString, String caller) {
            logger.info("{}: mismatches from {}", caller, serverString);
            try {
                Thread.sleep(auditRetryDelay);
            } catch (InterruptedException e) {
                logger.error("{}: Interrupted handling audit response from {}",
                             caller, serverString);
                // just abort
                Thread.currentThread().interrupt();
                return true;
            }

            // This will check against our own data -- any mismatches
            // mean that things have changed since we sent out the
            // first message. We will remove any mismatches from
            // 'respData', and see if there are any left.
            AuditData mismatches = respData.generateResponse(false);

            respData.serverData.removeAll(mismatches.clientData);
            respData.clientData.removeAll(mismatches.serverData);

            if (respData.clientData.isEmpty()
                    && respData.serverData.isEmpty()) {
                // no mismatches --
                // there must have been transient issues on our side
                logger.info("{}: no mismatches from {} after retry",
                            caller, serverString);
                return true;
            }

            return false;
        }
    }
}
