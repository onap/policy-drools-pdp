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

package org.onap.policy.drools.serverpool.persistence;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import org.kie.api.event.rule.ObjectDeletedEvent;
import org.kie.api.event.rule.ObjectInsertedEvent;
import org.kie.api.event.rule.ObjectUpdatedEvent;
import org.kie.api.event.rule.RuleRuntimeEventListener;
import org.kie.api.runtime.KieSession;
import org.onap.policy.drools.core.DroolsRunnable;
import org.onap.policy.drools.core.PolicyContainer;
import org.onap.policy.drools.core.PolicySession;
import org.onap.policy.drools.core.PolicySessionFeatureApi;
import org.onap.policy.drools.serverpool.Bucket;
import org.onap.policy.drools.serverpool.Keyword;
import org.onap.policy.drools.serverpool.Server;
import org.onap.policy.drools.serverpool.ServerPoolApi;
import org.onap.policy.drools.serverpool.TargetLock.GlobalLocks;
import org.onap.policy.drools.serverpool.Util;
import org.onap.policy.drools.system.PolicyControllerConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides a persistence implementation for 'feature-server-pool',
 * backing up the data of selected Drools sessions and server-side 'TargetLock'
 * data on separate hosts.
 */
public class Persistence extends ServerPoolApi implements PolicySessionFeatureApi {
    private static Logger logger = LoggerFactory.getLogger(Persistence.class);

    // HTTP query parameters
    private static final String QP_BUCKET = "bucket";
    private static final String QP_SESSION = "session";
    private static final String QP_COUNT = "count";
    private static final String QP_DEST = "dest";

    /* ************************************* */
    /* 'PolicySessionFeatureApi' interface   */
    /* ************************************* */

    /**
     * {@inheritDoc}
     */
    @Override
    public int getSequenceNumber() {
        return 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void newPolicySession(PolicySession policySession) {
        // a new Drools session is being created -- look at the properties
        // 'persistence.<session-name>.type' and 'persistence.type' to determine
        // whether persistence is enabled for this session

        // fetch properties file
        PolicyContainer container = policySession.getPolicyContainer();
        Properties properties = PolicyControllerConstants.getFactory().get(
            container.getGroupId(), container.getArtifactId()).getProperties();

        // look at 'persistence.<session-name>.type', and 'persistence.type'
        String type = properties.getProperty("persistence." + policySession.getName() + ".type");
        if (type == null) {
            type = properties.getProperty("persistence.type");
        }

        if ("auto".equals(type) || "native".equals(type)) {
            // persistence is enabled this session
            policySession.setAdjunct(PersistenceRunnable.class,
                                     new PersistenceRunnable(policySession));
        }
    }

    /* *************************** */
    /* 'ServerPoolApi' interface   */
    /* *************************** */

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<Class<?>> servletClasses() {
        // the nested class 'Rest' contains additional REST calls
        List<Class<?>> classes = new LinkedList<>();
        classes.add(Rest.class);
        return classes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void restoreBucket(Bucket bucket) {
        // if we reach this point, no data was received from the old server, which
        // means we just initialized, or we did not have a clean bucket migration

        ReceiverBucketData rbd = bucket.removeAdjunct(ReceiverBucketData.class);
        if (rbd != null) {
            // there is backup data -- do a restore
            rbd.restoreBucket(bucket);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void lockUpdate(Bucket bucket, GlobalLocks globalLocks) {
        // we received a notification from 'TargetLock' that 'GlobalLocks' data
        // has changed (TBD: should any attempt be made to group updates that
        // occur in close succession?)

        sendLockDataToBackups(bucket, globalLocks);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void auditBucket(Bucket bucket, boolean isOwner, boolean isBackup) {
        if (isOwner) {
            // it may be that backup hosts have changed --
            // send out lock and session data

            // starting with lock data
            GlobalLocks globalLocks =
                bucket.getAdjunctDontCreate(GlobalLocks.class);
            if (globalLocks != null) {
                sendLockDataToBackups(bucket, globalLocks);
            }

            // now, session data
            SenderBucketData sbd =
                bucket.getAdjunctDontCreate(SenderBucketData.class);
            if (sbd != null) {
                synchronized (sbd) {
                    // go through all of the sessions where we have persistent data
                    for (PolicySession session : sbd.sessionData.keySet()) {
                        Object obj = session.getAdjunct(PersistenceRunnable.class);
                        if (obj instanceof PersistenceRunnable) {
                            PersistenceRunnable pr = (PersistenceRunnable) obj;
                            synchronized (pr.modifiedBuckets) {
                                // mark bucket associated with this session
                                // as modified
                                pr.modifiedBuckets.add(bucket);
                            }
                        }
                    }
                }
            }
        } else if (bucket.removeAdjunct(SenderBucketData.class) != null) {
            logger.warn("Bucket {}: Removed superfluous "
                        + "'SenderBucketData' adjunct",
                        bucket.getIndex());
        }
        if (!isBackup && bucket.removeAdjunct(ReceiverBucketData.class) != null) {
            logger.warn("Bucket {}: Removed superfluous "
                        + "'ReceiverBucketData' adjunct",
                        bucket.getIndex());
        }
    }

    /**
     * This method supports 'lockUpdate' -- it has been moved to a separate
     * 'static' method, so it can also be called after restoring 'GlobalLocks',
     * so it can be backed up on its new servers.
     *
     * @param bucket the bucket containing the 'GlobalLocks' adjunct
     * @param globalLocks the 'GlobalLocks' adjunct
     */
    private static void sendLockDataToBackups(final Bucket bucket, final GlobalLocks globalLocks) {
        final int bucketNumber = bucket.getIndex();
        SenderBucketData sbd = bucket.getAdjunct(SenderBucketData.class);
        int lockCount = 0;

        // serialize the 'globalLocks' instance
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            synchronized (globalLocks) {
                // the 'GlobalLocks' instance and counter are tied together
                oos.writeObject(globalLocks);
                lockCount = sbd.getLockCountAndIncrement();
            }
            oos.close();
        } catch (IOException e) {
            logger.error("Persistence.LockUpdate({})", bucketNumber, e);
            return;
        }

        // convert to Base64, and generate an 'Entity' for the REST call
        byte[] serializedData = Base64.getEncoder().encode(bos.toByteArray());
        final Entity<String> entity =
            Entity.entity(new String(serializedData),
                          MediaType.APPLICATION_OCTET_STREAM_TYPE);
        final int count = lockCount;

        sendLocksToBackupServers(bucketNumber, entity, count, bucket.getBackups());
    }

    private static void sendLocksToBackupServers(final int bucketNumber, final Entity<String> entity, final int count,
                    Set<Server> servers) {
        for (final Server server : servers) {
            if (server != null) {
                // send out REST command
                server.getThreadPool().execute(() -> {
                    WebTarget webTarget =
                        server.getWebTarget("persistence/lock");
                    if (webTarget != null) {
                        webTarget
                        .queryParam(QP_BUCKET, bucketNumber)
                        .queryParam(QP_COUNT, count)
                        .queryParam(QP_DEST, server.getUuid())
                        .request().post(entity);
                    }
                });
            }
        }
    }

    /* ============================================================ */

    /**
     * One instance of this class exists for every Drools session that is
     * being backed up. It implements the 'RuleRuntimeEventListener' interface,
     * so it receives notifications of Drools object changes, and also implements
     * the 'DroolsRunnable' interface, so it can run within the Drools session
     * thread, which should reduce the chance of catching an object in a
     * transient state.
     */
    static class PersistenceRunnable implements DroolsRunnable,
        RuleRuntimeEventListener {
        // this is the Drools session associated with this instance
        private PolicySession session;

        // this is the string "<groupId>:<artifactId>:<sessionName>"
        private String encodedSessionName;

        // the buckets in this session which have modifications that still
        // need to be backed up
        private Set<Bucket> modifiedBuckets = new HashSet<>();

        /**
         * Constructor - save the session information, and start listing for
         * updates.
         */
        PersistenceRunnable(PolicySession session) {
            PolicyContainer pc = session.getPolicyContainer();

            this.session = session;
            this.encodedSessionName =
                pc.getGroupId() + ":" + pc.getArtifactId() + ":" + session.getName();
            session.getKieSession().addEventListener(this);
        }

        /* **************************** */
        /* 'DroolsRunnable' interface   */
        /* **************************** */

        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
            try {
                // save a snapshot of 'modifiedBuckets'
                Set<Bucket> saveModifiedBuckets;
                synchronized (modifiedBuckets) {
                    saveModifiedBuckets = new HashSet<>(modifiedBuckets);
                    modifiedBuckets.clear();
                }

                // iterate over all of the modified buckets, sending update data
                // to all of the backup servers
                for (Bucket bucket : saveModifiedBuckets) {
                    SenderBucketData sbd =
                        bucket.getAdjunctDontCreate(SenderBucketData.class);
                    if (sbd != null) {
                        // serialization occurs within the Drools session thread
                        SenderSessionBucketData ssbd = sbd.getSessionData(session);
                        byte[] serializedData =
                            ssbd.getLatestEncodedSerializedData();
                        final int count = ssbd.getCount();
                        final Entity<String> entity =
                            Entity.entity(new String(serializedData),
                                          MediaType.APPLICATION_OCTET_STREAM_TYPE);

                        // build list of backup servers
                        Set<Server> servers = new HashSet<>();
                        synchronized (bucket) {
                            servers.add(bucket.getPrimaryBackup());
                            servers.add(bucket.getSecondaryBackup());
                        }
                        sendBucketToBackupServers(bucket, count, entity, servers);
                    }
                }
            } catch (Exception e) {
                logger.error("Persistence.PersistenceRunnable.run:", e);
            }
        }

        private void sendBucketToBackupServers(Bucket bucket, final int count, final Entity<String> entity,
                        Set<Server> servers) {

            for (final Server server : servers) {
                if (server != null) {
                    // send out REST command
                    server.getThreadPool().execute(() -> {
                        WebTarget webTarget =
                            server.getWebTarget("persistence/session");
                        if (webTarget != null) {
                            webTarget
                            .queryParam(QP_BUCKET,
                                        bucket.getIndex())
                            .queryParam(QP_SESSION,
                                        encodedSessionName)
                            .queryParam(QP_COUNT, count)
                            .queryParam(QP_DEST, server.getUuid())
                            .request().post(entity);
                        }
                    });
                }
            }
        }

        /* ************************************** */
        /* 'RuleRuntimeEventListener' interface   */
        /* ************************************** */

        /**
         * {@inheritDoc}
         */
        @Override
        public void objectDeleted(ObjectDeletedEvent event) {
            // determine Drools object that was deleted
            Object object = event.getOldObject();

            // determine keyword, if any
            String keyword = Keyword.lookupKeyword(object);
            if (keyword == null) {
                // no keyword, so there is no associated bucket
                return;
            }

            // locate bucket and associated data
            // (don't create adjunct if it isn't there -- there's nothing to delete)
            Bucket bucket = Bucket.getBucket(keyword);
            SenderBucketData sbd =
                bucket.getAdjunctDontCreate(SenderBucketData.class);
            if (sbd != null) {
                // add bucket to 'modified' list
                synchronized (modifiedBuckets) {
                    modifiedBuckets.add(bucket);
                }

                // update set of Drools objects in this bucket
                sbd.getSessionData(session).objectDeleted(object);

                // insert this 'DroolsRunnable' to do the backup (note that it
                // may already be inserted from a previous update to this
                // DroolsSession -- eventually, the rule will fire, and the 'run'
                // method will be called)
                session.getKieSession().insert(this);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void objectInserted(ObjectInsertedEvent event) {
            objectChanged(event.getObject());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void objectUpdated(ObjectUpdatedEvent event) {
            objectChanged(event.getObject());
        }

        /**
         * A Drools session object was either inserted or updated
         * (both are treated the same way).
         *
         * @param object the object being inserted or updated
         */
        private void objectChanged(Object object) {
            // determine keyword, if any
            String keyword = Keyword.lookupKeyword(object);
            if (keyword == null) {
                // no keyword, so there is no associated bucket
                return;
            }

            // locate bucket and associated data
            Bucket bucket = Bucket.getBucket(keyword);
            SenderBucketData sbd = bucket.getAdjunct(SenderBucketData.class);

            // add bucket to 'modified' list
            synchronized (modifiedBuckets) {
                modifiedBuckets.add(bucket);
            }

            // update set of Drools objects in this bucket
            sbd.getSessionData(session).objectChanged(object);

            // insert this 'DroolsRunnable' to do the backup (note that it
            // may already be inserted from a previous update to this
            // DroolsSession -- eventually, the rule will fire, and the 'run'
            // method will be called)
            session.getKieSession().insert(this);
        }
    }

    /* ============================================================ */

    /**
     * Per-session data for a single bucket on the sender's side.
     */
    static class SenderSessionBucketData {
        // the set of all objects in the session associated with this bucket
        Map<Object, Object> droolsObjects = new IdentityHashMap<>();

        // used by the receiver to determine whether an update is really newer
        int count = 0;

        // serialized base64 form of 'droolsObjects'
        // (TBD: determine if we are getting any benefit from caching this)
        byte[] encodedSerializedData = null;

        // 'true' means that 'encodedSerializedData' is out-of-date
        boolean rebuildNeeded = true;

        /**
         * Notification that a Drools object associated with this bucket
         * was deleted.
         *
         * @param object the object that was deleted
         */
        synchronized void objectDeleted(Object object) {
            if (droolsObjects.remove(object) != null) {
                rebuildNeeded = true;
            }
        }

        /**
         * Notification that a Drools object associated with this bucket
         * was inserted or updated.
         *
         * @param object the object that was updated
         */
        synchronized void objectChanged(Object object) {
            droolsObjects.put(object, object);
            rebuildNeeded = true;
        }

        /**
         * Serialize and base64-encode the objects in this Drools session.
         *
         * @return a byte array containing the encoded serialized objects
         */
        synchronized byte[] getLatestEncodedSerializedData() {
            if (rebuildNeeded) {
                try {
                    // this should be run in the Drools session thread in order
                    // to avoid transient data
                    encodedSerializedData =
                        Base64.getEncoder().encode(Util.serialize(droolsObjects));
                    count += 1;
                } catch (IOException e) {
                    logger.error("Persistence.SenderSessionBucketData."
                                 + "getLatestEncodedSerializedData: ", e);
                }
                rebuildNeeded = false;
            }
            return encodedSerializedData;
        }

        /**
         * Return a counter that will be used for update comparison.
         *
         * @return the value of a counter that can be used to determine whether
         *     an update is really newer than the previous update
         */
        synchronized int getCount() {
            return count;
        }
    }

    /* ============================================================ */

    /**
     * Data for a single bucket on the sender's side.
     */
    public static class SenderBucketData {
        // maps session name into SenderSessionBucketData
        Map<PolicySession, SenderSessionBucketData> sessionData =
            new IdentityHashMap<>();

        // used by the receiver to determine whether an update is really newer
        int lockCount = 0;

        /**
         * Create or fetch the 'SenderSessionBucketData' instance associated
         * with the specified session.
         *
         * @param session the 'PolicySession' object
         * @return the associated 'SenderSessionBucketData' instance
         */
        synchronized SenderSessionBucketData getSessionData(PolicySession session) {
            return sessionData.computeIfAbsent(session, key -> new SenderSessionBucketData());
        }

        /**
         * Return a counter that will be used for update comparison.
         *
         * @return the value of a counter that can be used to determine whether
         *     an update is really newer than the previous update
         */
        int getLockCountAndIncrement() {
            // note that this is synchronized using the 'GlobalLocks' instance
            // within the same bucket
            return lockCount++;
        }
    }

    /* ============================================================ */

    /**
     * Data for a single bucket and session on the receiver's side.
     */
    static class ReceiverSessionBucketData {
        // used to determine whether an update is really newer
        int count = -1;

        // serialized base64 form of 'droolsObjects'
        byte[] encodedSerializedData = null;
    }

    /* ============================================================ */

    /**
     * Data for a single bucket on the receiver's side -- this adjunct is used
     * to store encoded data on a backup host. It will only be needed if the
     * bucket owner fails.
     */
    public static class ReceiverBucketData {
        static final String RESTORE_BUCKET_ERROR =
            "Persistence.ReceiverBucketData.restoreBucket: ";

        // maps session name into encoded data
        Map<String, ReceiverSessionBucketData> sessionData = new HashMap<>();

        // used by the receiver to determine whether an update is really newer
        int lockCount = -1;

        // encoded lock data
        byte[] lockData = null;

        /**
         * This method is called in response to the '/persistence/session'
         * REST message. It stores the base64-encoded and serialized data
         * for a particular bucket and session.
         *
         * @param bucketNumber identifies the bucket
         * @param sessionName identifies the Drools session
         * @param count counter used to determine whether data is really newer
         * @param data base64-encoded serialized data for this bucket and session
         */
        static void receiveSession(int bucketNumber, String sessionName, int count, byte[] data) {
            // fetch the bucket
            Bucket bucket = Bucket.getBucket(bucketNumber);

            // create/fetch the 'ReceiverBucketData' adjunct
            ReceiverBucketData rbd = bucket.getAdjunct(ReceiverBucketData.class);
            synchronized (rbd) {
                // update the session data
                ReceiverSessionBucketData rsbd = rbd.sessionData.get(sessionName);
                if (rsbd == null) {
                    rsbd = new ReceiverSessionBucketData();
                    rbd.sessionData.put(sessionName, rsbd);
                }

                if ((count - rsbd.count) > 0 || count == 0) {
                    // this is new data
                    rsbd.count = count;
                    rsbd.encodedSerializedData = data;
                }
            }
        }

        /**
         * This method is called in response to the '/persistence/lock'
         * REST message. It stores the base64-encoded and serialized
         * server-side lock data associated with this bucket.
         *
         * @param bucketNumber identifies the bucket
         * @param count counter used to determine whether data is really newer
         * @param data base64-encoded serialized lock data for this bucket
         */
        static void receiveLockData(int bucketNumber, int count, byte[] data) {
            // fetch the bucket
            Bucket bucket = Bucket.getBucket(bucketNumber);

            // create/fetch the 'ReceiverBucketData' adjunct
            ReceiverBucketData rbd = bucket.getAdjunct(ReceiverBucketData.class);
            synchronized (rbd) {
                // update the lock data
                if ((count - rbd.lockCount) > 0 || count == 0) {
                    rbd.lockCount = count;
                    rbd.lockData = data;
                }
            }
        }

        /**
         * This method is called when a bucket is being restored from persistent
         * data, which indicates that a clean migration didn't occur.
         * Drools session and/or lock data is restored.
         *
         * @param bucket the bucket being restored
         */
        synchronized void restoreBucket(Bucket bucket) {
            // one entry for each Drools session being restored --
            // indicates when the restore is complete (restore runs within
            // the Drools session thread)
            List<CountDownLatch> sessionLatches = restoreBucketDroolsSessions();

            // restore lock data
            restoreBucketLocks(bucket);

            // wait for all of the sessions to update
            try {
                for (CountDownLatch sessionLatch : sessionLatches) {
                    if (!sessionLatch.await(10000L, TimeUnit.MILLISECONDS)) {
                        logger.error("{}: timed out waiting for session latch",
                                     this);
                    }
                }
            } catch (InterruptedException e) {
                logger.error("Exception in {}", this, e);
                Thread.currentThread().interrupt();
            }
        }

        private List<CountDownLatch> restoreBucketDroolsSessions() {
            List<CountDownLatch> sessionLatches = new LinkedList<>();
            for (Map.Entry<String, ReceiverSessionBucketData> entry : sessionData.entrySet()) {
                restoreBucketDroolsSession(sessionLatches, entry);
            }
            return sessionLatches;
        }

        private void restoreBucketDroolsSession(List<CountDownLatch> sessionLatches,
                        Entry<String, ReceiverSessionBucketData> entry) {

            String sessionName = entry.getKey();
            ReceiverSessionBucketData rsbd = entry.getValue();

            PolicySession policySession = detmPolicySession(sessionName);
            if (policySession == null) {
                logger.error(RESTORE_BUCKET_ERROR
                             + "Can't find PolicySession{}", sessionName);
                return;
            }

            final Map<?, ?> droolsObjects = deserializeMap(sessionName, rsbd, policySession);
            if (droolsObjects == null) {
                return;
            }

            // if we reach this point, we have decoded the persistent data

            // signal when restore is complete
            final CountDownLatch sessionLatch = new CountDownLatch(1);

            // 'KieSession' object
            final KieSession kieSession = policySession.getKieSession();

            // run the following within the Drools session thread
            DroolsRunnable insertDroolsObjects = () -> {
                try {
                    // insert all of the Drools objects into the session
                    for (Object droolsObj : droolsObjects.keySet()) {
                        kieSession.insert(droolsObj);
                    }
                } finally {
                    // signal completion
                    sessionLatch.countDown();
                }
            };
            kieSession.insert(insertDroolsObjects);

            // add this to the set of 'CountDownLatch's we are waiting for
            sessionLatches.add(sessionLatch);
        }

        private PolicySession detmPolicySession(String sessionName) {
            // [0]="<groupId>" [1]="<artifactId>", [2]="<sessionName>"
            String[] nameSegments = sessionName.split(":");

            // locate the 'PolicyContainer' and 'PolicySession'
            if (nameSegments.length == 3) {
                // step through all 'PolicyContainer' instances looking
                // for a matching 'artifactId' & 'groupId'
                for (PolicyContainer pc : PolicyContainer.getPolicyContainers()) {
                    if (nameSegments[1].equals(pc.getArtifactId())
                            && nameSegments[0].equals(pc.getGroupId())) {
                        // 'PolicyContainer' matches -- try to fetch the session
                        return pc.getPolicySession(nameSegments[2]);
                    }
                }
            }
            return null;
        }

        private Map<?, ?> deserializeMap(String sessionName, ReceiverSessionBucketData rsbd,
                        PolicySession policySession) {
            Object obj;

            try {
                // deserialization needs to use the correct 'ClassLoader'
                obj = Util.deserialize(Base64.getDecoder().decode(rsbd.encodedSerializedData),
                    policySession.getPolicyContainer().getClassLoader());
            } catch (IOException | ClassNotFoundException | IllegalArgumentException e) {
                logger.error(RESTORE_BUCKET_ERROR
                             + "Failed to read data for session '{}'",
                             sessionName, e);

                // can't decode -- skip this session
                return null;
            }

            if (!(obj instanceof Map)) {
                logger.error(RESTORE_BUCKET_ERROR
                             + "Session '{}' data has class {}, expected 'Map'",
                             sessionName, obj.getClass().getName());

                // wrong object type decoded -- skip this session
                return null;
            }

            // if we reach this point, we have decoded the persistent data

            return (Map<?, ?>) obj;
        }

        private void restoreBucketLocks(Bucket bucket) {
            if (lockData != null) {
                Object obj = null;
                try {
                    // decode lock data
                    obj = Util.deserialize(Base64.getDecoder().decode(lockData));
                    if (obj instanceof GlobalLocks) {
                        bucket.putAdjunct(obj);

                        // send out updated date
                        sendLockDataToBackups(bucket, (GlobalLocks) obj);
                    } else {
                        logger.error(RESTORE_BUCKET_ERROR
                                     + "Expected 'GlobalLocks', got '{}'",
                                     obj.getClass().getName());
                    }
                } catch (IOException | ClassNotFoundException | IllegalArgumentException e) {
                    logger.error(RESTORE_BUCKET_ERROR
                                 + "Failed to read lock data", e);
                    // skip the lock data
                }

            }
        }
    }

    /* ============================================================ */

    @Path("/")
    public static class Rest {
        /**
         * Handle the '/persistence/session' REST call.
         */
        @POST
        @Path("/persistence/session")
        @Consumes(MediaType.APPLICATION_OCTET_STREAM)
        public void receiveSession(@QueryParam(QP_BUCKET) int bucket,
                                   @QueryParam(QP_SESSION) String sessionName,
                                   @QueryParam(QP_COUNT) int count,
                                   @QueryParam(QP_DEST) UUID dest,
                                   byte[] data) {
            logger.debug("/persistence/session: (bucket={},session={},count={}) "
                         + "got {} bytes of data",
                         bucket, sessionName, count, data.length);
            if (dest == null || dest.equals(Server.getThisServer().getUuid())) {
                ReceiverBucketData.receiveSession(bucket, sessionName, count, data);
            } else {
                // This host is not the intended destination -- this could happen
                // if it was sent from another site. Leave off the 'dest' param
                // when forwarding the message, to ensure that we don't have
                // an infinite forwarding loop, if the site data happens to be bad.
                Server server;
                WebTarget webTarget;

                if ((server = Server.getServer(dest)) != null
                        && (webTarget =
                                server.getWebTarget("persistence/session")) != null) {
                    Entity<String> entity =
                        Entity.entity(new String(data),
                                      MediaType.APPLICATION_OCTET_STREAM_TYPE);
                    webTarget
                    .queryParam(QP_BUCKET, bucket)
                    .queryParam(QP_SESSION, sessionName)
                    .queryParam(QP_COUNT, count)
                    .request().post(entity);
                }
            }
        }

        /**
         * Handle the '/persistence/lock' REST call.
         */
        @POST
        @Path("/persistence/lock")
        @Consumes(MediaType.APPLICATION_OCTET_STREAM)
        public void receiveLockData(@QueryParam(QP_BUCKET) int bucket,
                                    @QueryParam(QP_COUNT) int count,
                                    @QueryParam(QP_DEST) UUID dest,
                                    byte[] data) {
            logger.debug("/persistence/lock: (bucket={},count={}) "
                         + "got {} bytes of data", bucket, count, data.length);
            if (dest == null || dest.equals(Server.getThisServer().getUuid())) {
                ReceiverBucketData.receiveLockData(bucket, count, data);
            } else {
                // This host is not the intended destination -- this could happen
                // if it was sent from another site. Leave off the 'dest' param
                // when forwarding the message, to ensure that we don't have
                // an infinite forwarding loop, if the site data happens to be bad.
                Server server;
                WebTarget webTarget;

                if ((server = Server.getServer(dest)) != null
                        && (webTarget = server.getWebTarget("persistence/lock")) != null) {
                    Entity<String> entity =
                        Entity.entity(new String(data),
                                      MediaType.APPLICATION_OCTET_STREAM_TYPE);
                    webTarget
                    .queryParam(QP_BUCKET, bucket)
                    .queryParam(QP_COUNT, count)
                    .request().post(entity);
                }
            }
        }
    }
}
