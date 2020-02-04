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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The server pool uses an algorithmic way to map things like transactions
 * (identified by a 'requestID') and locks (identified by a string key)
 * into a server handling that transaction or lock. It does this by mapping
 * the string name into one of a set of predefined hash buckets, with each
 * bucket being assigned to one of the active servers.
 * In other words:
 *  string key -> hash bucket (fixed mapping, known to all servers)
 *  hash bucket -> server (assignments may change when servers go up or down,
 *      but remains fairly static when the system is stable)
 * With this approach, there is no global dynamic table that needs to be
 * updated as transactions, or other objects come and go.
 * Each instance of class 'Bucket' corresponds to one of the hash buckets,
 * there are static methods that provide the overall abstraction, as well
 * as some supporting classes.
 */
public class Bucket {
    private static Logger logger = LoggerFactory.getLogger(Bucket.class);

    /**
     * Listener class to handle state changes that may lead to.
     */
    // reassignments of buckets
    private static EventHandler eventHandler = new EventHandler();

    // Used to hash keywords into buckets
    private static MessageDigest messageDigest;

    static {
        // register Listener class
        Events.register(eventHandler);

        // create MD5 MessageDigest -- used to hash keywords
        try {
            messageDigest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            logger.error("{}: Can't create MessageDigest for MD5", e);
        }
    }

    /**
     * Tags for encoding of bucket data.
     */
    private static final int END_OF_PARAMETERS_TAG = 0;
    private static final int OWNER_UPDATE = 1;
    private static final int OWNER_NULL = 2;
    private static final int PRIMARY_BACKUP_UPDATE = 3;
    private static final int PRIMARY_BACKUP_NULL = 4;
    private static final int SECONDARY_BACKUP_UPDATE = 5;
    private static final int SECONDARY_BACKUP_NULL = 6;

    /**
     * This is the table itself -- the current size is fixed at 1024 buckets.
     */
    public static final int BUCKETCOUNT = 1024;
    private static Bucket[] indexToBucket = new Bucket[BUCKETCOUNT];

    static {
        /**
         * create hash bucket entries, but there are no assignments yet.
         */
        for (int i = 0 ; i < indexToBucket.length ; i += 1) {
            Bucket bucket = new Bucket(i);
            indexToBucket[i] = bucket;
        }
    }

    /**
     * this is a list of all objects registered for the 'Backup' interface.
     */
    private static List<Backup> backupList = new LinkedList<>();

    /**
     * 'rebalance' is a non-null value when rebalancing is in progress.
     */
    private static Object rebalanceLock = new Object();
    private static Rebalance rebalance = null;

    // bucket number
    private int index;

    /**
     * owner of the bucket -- this is the host where messages should be directed.
     */
    private Server owner = null;

    /**
     * this host will take over as the owner if the current owner goes down,
     * and may also contain backup data to support persistence.
     */
    private Server primaryBackup = null;

    /**
     * this is a secondary backup host, which can be used if both owner and
     * primary backup go out in quick succession.
     */
    private Server secondaryBackup = null;

    /**
     * when we are in a transient state, certain events are forwarded to
     * this object.
     */
    private State state = null;

    /**
     * storage for additional data.
     */
    private Map<Class<?>, Object> adjuncts = new HashMap<Class<?>, Object>();

    // BACKUP data (only buckets for where we are the owner, or a backup)

    // TBD: need fields for outgoing queues for application message transfers

    /**
     * This method triggers registration of 'eventHandler'.
     */
    static void startup() {
    }

    /**
     * Constructor -- called when building the 'indexToBucket' table.
     *
     * @param index the bucket number
     */
    protected Bucket(int index) {
        this.index = index;
    }

    /**
     * This method converts a String keyword into the corresponding bucket
     * number.
     *
     * @param value the keyword to be converted
     * @return the bucket number
     */
    public static int bucketNumber(String value) {
        /**
         * It would be possible to create a new 'MessageDigest' instance each
         * It would be possible to create a new 'MessageDigest' instance each
         * time this method is called, and avoid the need for synchronization.
         * However, past experience has taught me that this might involve a
         * considerable amount of computation, due to internal table
         * initialization, so it shouldn't be done this way for performance
         * reasons.
         * If we start running into blocking issues because there are too many
         * simultaneous calls to this method, we can initialize an array of these
         * objects, and iterate over them using an AtomicInteger index.
         */
        synchronized (messageDigest) {
            /**
             * Note that we only need the first two bytes of this, even though
             * 16 bytes are produced. There may be other operations that can be
             * used to more efficiently map keyword -> hash bucket. The only
             * issue is the same algorithm must be used on all servers, and it
             * should produce a fairly even distribution across all of the buckets.
             */
            byte[] digest = messageDigest.digest(value.getBytes());
            return ((Byte.toUnsignedInt(digest[0]) << 8)
                    | Byte.toUnsignedInt(digest[1])) & 0x3ff;
        }
    }

    /**
     * Fetch the server associated with a particular bucket number.
     *
     * @param bucketNumber a bucket number in the range 0-1023
     * @return the Server that currently handles the bucket,
     *     or 'null' if none is currently assigned
     */
    public static Server bucketToServer(int bucketNumber) {
        Bucket bucket = indexToBucket[bucketNumber];
        return bucket.owner;
    }

    /**
     * Fetch the bucket object associated with a particular bucket number.
     *
     * @param bucketNumber a bucket number in the range 0-1023
     * @return the Bucket associated with this bucket number
     */
    public static Bucket getBucket(int bucketNumber) {
        return indexToBucket[bucketNumber];
    }

    /**
     * Fetch the bucket object associated with a particular keyword.
     *
     * @param value the keyword to be converted
     * @return the Bucket associated with this keyword
     */
    public static Bucket getBucket(String value) {
        return indexToBucket[bucketNumber(value)];
    }
    
    /**
     * Fetch indexToBucket object.
     * @return indexToBucket an array of Bucket object
     */
    public static Bucket[] getIndexToBucket() {
        return indexToBucket;
    }

    /**
     * Fetch indexToBucketLength.
     * @return indexToBucketLength
     */
    public static int getIndexToBucketLength() {
        return indexToBucket.length;
    }

    /**
     * Fetch backupList object.
     * @return backupList a list of Backup object
     */
    public static List<Backup> getBackupList() {
        return backupList;
    }

    /**
     * Determine if the associated key is assigned to the current server.
     *
     * @param key the keyword to be hashed
     * @return 'true' if the associated bucket is assigned to this server,
     *     'false' if not
     */
    public static boolean isKeyOnThisServer(String key) {
        int bucketNumber = bucketNumber(key);
        Bucket bucket = indexToBucket[bucketNumber];
        return bucket.getOwner() == Server.getThisServer();
    }

    /**
     * Return the bucket number.
     *
     * @return the bucket number
     */
    public int getBucketNumber() {
        return index;
    }

    /**
     * Return the Server owning this bucket.
     *
     * @return the Server owning this bucket
     */
    public synchronized Server getOwner() {
        return owner;
    }

    /**
     * Return the State for this bucket.
     *
     * @return the State of this bucket
     */
    public synchronized State getState() {
        return state;
    }

    /**
     * Return the Server that is the primary backup for this bucket.
     *
     * @return the Server that is the primary backup for this bucket
     */
    public synchronized Server getPrimaryBackup() {
        return primaryBackup;
    }

    /**
     * Return the Server that is the secondary backup for this bucket.
     *
     * @return the Server that is the secondary backup for this bucket
     */
    public synchronized Server getSecondaryBackup() {
        return secondaryBackup;
    }

    /**
     * Set the Owner of this bucket.
     */
    public synchronized void setOwner(Server owner) {
        this.owner = owner;
    }

    /**
     * Set the Owner of this bucket.
     */
    public synchronized void setState(State state) {
        this.state = state;
    }

    /**
     * Set the primaryBackup of this bucket.
     */
    public synchronized void setPrimaryBackup(Server primaryBackup) {
        this.primaryBackup = primaryBackup;
    }

    /**
     * Set the secondaryBackup of this bucket.
     */
    public synchronized void setSecondaryBackup(Server secondaryBackup) {
        this.secondaryBackup = secondaryBackup;
    }

    /**
     * This method is called to start a 'rebalance' operation in a background
     * thread, but it only does this on the lead server. Being balanced means
     * the following:
     * 1) Each server owns approximately the same number of buckets
     * 2) If any server were to fail, and the designated primaries take over
     *    for all of that server's buckets, all remaining servers would still
     *    own approximately the same number of buckets.
     * 3) If any two servers were to fail, and the designated primaries were
     *    to take over for the failed server's buckets (secondaries would take
     *    for buckets where the owner and primary are OOS), all remaining
     *    servers would still own approximately the same number of buckets.
     * 4) Each server should have approximately the same number of
     *    (primary-backup + secondary-backup) buckets that it is responsible for.
     * 5) The primary backup for each bucket must be on the same site as the
     *    owner, and the secondary backup must be on a different site.
     */
    protected static void rebalance() {
        if (Leader.getLeader() == Server.getThisServer()) {
            Rebalance rb = new Rebalance();
            synchronized (rebalanceLock) {
                // the most recent 'Rebalance' instance is the only valid one
                rebalance = rb;
            }

            new Thread("BUCKET REBALANCER") {
                @Override
                public void run() {
                    /**
                     * copy bucket and host data,
                     * generating a temporary internal table.
                     */
                    rb.copyData();

                    /**
                     * allocate owners for all buckets without an owner,
                     * and rebalance bucket owners, if necessary --
                     * this takes card of item #1, above.
                     */
                    rb.allocateBuckets();

                    /**
                     * make sure that primary backups always have the same site
                     * as the owner, and secondary backups always have a different
                     * site -- this takes care of #5, above.
                     */
                    rb.checkSiteValues();

                    /**
                     * adjust primary backup lists to take care of item #2, above
                     * (taking #5 into account).
                     */
                    rb.rebalancePrimaryBackups();

                    /**
                     * allocate secondary backups, and take care of items
                     * #3 and #4, above (taking #5 into account).
                     */
                    rb.rebalanceSecondaryBackups();

                    try {
                        synchronized (rebalanceLock) {
                            /**
                             * if another 'Rebalance' instance has started in the
                             * mean time, don't do the update.
                             */
                            if (rebalance == rb) {
                                /**
                                 * build a message containing all of the updated bucket
                                 * information, process it internally in this host
                                 * (lead server), and send it out to others in the
                                 * "notify list".
                                 */
                                rb.generateBucketMessage();
                                rebalance = null;
                            }
                        }
                    } catch (IOException e) {
                        logger.error("Exception in Rebalance.generateBucketMessage",
                                     e);
                    }
                }
            }.start();
        }
    }

    /**
     * Handle an incoming /bucket/update REST message.
     *
     * @param data base64-encoded data, containing all bucket updates
     */
    public static void updateBucket(byte[] data) {
        final byte[] packet = Base64.getDecoder().decode(data);
        Runnable task = () -> {
            try {
                /**
                 * process the packet, handling any updates
                 */
                if (updateBucketInternal(packet)) {
                    /**
                     * updates have occurred -- forward this packet to
                     * all servers in the "notify list"
                     */
                    logger.info("One or more bucket updates occurred");
                    Entity<String> entity =
                        Entity.entity(new String(data, StandardCharsets.UTF_8),
                                      MediaType.APPLICATION_OCTET_STREAM_TYPE);
                    for (Server server : Server.getNotifyList()) {
                        server.post("bucket/update", entity);
                    }
                }
            } catch (Exception e) {
                logger.error("Exception in Bucket.updateBucket", e);
            }
        };
        MainLoop.queueWork(new Thread(task));
    }

    /**
     * This method supports the 'updateBucket' method, and runs entirely within
     * the 'MainLoop' thread.
     */
    protected static boolean updateBucketInternal(byte[] packet) throws IOException {
        boolean changes = false;

        ByteArrayInputStream bis = new ByteArrayInputStream(packet);
        DataInputStream dis = new DataInputStream(bis);

        // the packet contains a sequence of bucket updates
        while (dis.available() != 0) {
            // first parameter = bucket number
            int index = dis.readUnsignedShort();

            // locate the corresponding 'Bucket' object
            Bucket bucket = indexToBucket[index];

            // indicates whether changes occurred to the bucket
            boolean bucketChanges = false;

            /**
             * the remainder of the information for this bucket consists of
             * a sequence of '<tag> [ <associated-data> ]' followed by the tag
             * value 'END_OF_PARAMETERS_TAG'.
             */
            int tag;
            while ((tag = dis.readUnsignedByte()) != END_OF_PARAMETERS_TAG) {
                switch (tag) {
                    case OWNER_UPDATE: {
                        // <OWNER_UPDATE> <owner-uuid> -- owner UUID specified
                        Server newOwner = Server.getServer(Util.readUuid(dis));
                        if (bucket.owner != newOwner) {
                            logger.info("Bucket {} owner: {}->{}",
                                        index, bucket.owner, newOwner);
                            bucketChanges = true;
                            bucketOwnerUpdate(bucket, newOwner);
                        }
                        break;
                    }

                    case OWNER_NULL: {
                        // <OWNER_NULL> -- owner UUID should be set to 'null'
                        if (bucket.owner != null) {
                            logger.info("Bucket {} owner: {}->null",
                                        index, bucket.owner);
                            bucketChanges = true;
                            bucket.setOwner(null);
                            bucket.setState(null);
                        }
                        break;
                    }

                    case PRIMARY_BACKUP_UPDATE: {
                        /**
                         * <PRIMARY_BACKUP_UPDATE> <primary-backup-uuid> --
                         * primary backup UUID specified.
                         */
                        Server newPrimaryBackup =
                            Server.getServer(Util.readUuid(dis));
                        if (bucket.primaryBackup != newPrimaryBackup) {
                            logger.info("Bucket {} primary backup: {}->{}", index,
                                        bucket.primaryBackup, newPrimaryBackup);
                            bucketChanges = true;
                            bucket.primaryBackup = newPrimaryBackup;
                        }
                        break;
                    }

                    case PRIMARY_BACKUP_NULL: {
                        /**
                         * <PRIMARY_BACKUP_NULL> --
                         * primary backup should be set to 'null'.
                         */
                        if (bucket.primaryBackup != null) {
                            logger.info("Bucket {} primary backup: {}->null",
                                        index, bucket.primaryBackup);
                            bucketChanges = true;
                            bucket.primaryBackup = null;
                        }
                        break;
                    }

                    case SECONDARY_BACKUP_UPDATE: {
                        /**
                         * <SECONDARY_BACKUP_UPDATE> <secondary-backup-uuid> --
                         * secondary backup UUID specified.
                         */
                        Server newSecondaryBackup =
                            Server.getServer(Util.readUuid(dis));
                        if (bucket.secondaryBackup != newSecondaryBackup) {
                            logger.info("Bucket {} secondary backup: {}->{}", index,
                                        bucket.secondaryBackup, newSecondaryBackup);
                            bucketChanges = true;
                            bucket.secondaryBackup = newSecondaryBackup;
                        }
                        break;
                    }

                    case SECONDARY_BACKUP_NULL: {
                        /**
                         * <SECONDARY_BACKUP_NULL> --
                         * secondary backup should be set to 'null'.
                         */
                        if (bucket.secondaryBackup != null) {
                            logger.info("Bucket {} secondary backup: {}->null",
                                        index, bucket.secondaryBackup);
                            bucketChanges = true;
                            bucket.secondaryBackup = null;
                        }
                        break;
                    }

                    default:
                        logger.error("Illegal tag: {}", tag);
                        break;
                }
            }
            if (bucketChanges) {
                // give audit a chance to run
                changes = true;
                bucket.stateChanged();
            }
        }
        return changes;
    }

    /**
     * Update bucket owner information.
     *
     * @param bucket the bucket in process
     * @param newOwner new server own this bucket
     */
    private static void bucketOwnerUpdate(Bucket bucket, Server newOwner) {
        Server thisServer = Server.getThisServer();
        Server oldOwner = bucket.getOwner();
        bucket.setOwner(newOwner);
        if (thisServer == oldOwner) {
            // the current server is the old owner
            if (bucket.getState() == null) {
                bucket.setState(new OldOwner(newOwner, bucket));
            }
        } else if (thisServer == newOwner) {
            // the current server the new owner
            if (bucket.getState() == null) {
                bucket.setState(new NewOwner(true, oldOwner, bucket));
            } else {
                // new owner has been confirmed
                // orig bucket.state.newOwner();
                new NewOwner(true, oldOwner, bucket).newOwner();
            }
        }
    }

    /**
     * Forward a message to the specified bucket number. If the bucket is
     * in a transient state (the value of 'state' is not 'null'), the handling
     * is determined by that state.
     *
     * @param bucketNumber the bucket number determined by extracting the
     *     keyword from 'message'
     * @param message the message to be forwarded/processed
     * @return a value of 'true' indicates the message has been "handled"
     *     (forwarded or queued), and 'false' indicates it has not, and needs
     *     to be handled locally.
     */
    public static boolean forward(int bucketNumber, Message message) {
        Bucket bucket = indexToBucket[bucketNumber];
        Server server;
        if (bucket.getState() != null) {
            // we are in a transient state -- the handling is state-specific
            return bucket.state.forward(message);
        }
        server = bucket.getOwner();

        if (server == null || server == Server.getThisServer()) {
            // this needs to be processed locally
            return false;
        } else {
            // send message to remote server
            message.sendToServer(server, bucketNumber);
            return true;
        }
    }

    /**
     * This is a convenience method, which forwards a message through the
     * bucket associated with the specified keyword.
     *
     * @param keyword the keyword extracted from 'message'
     *     keyword from 'message'
     * @param message the message to be forwarded/processed
     * @return a value of 'true' indicates the message has been "handled"
     *     (forwarded or queued), and 'false' indicates it has not, and needs
     *     to be handled locally.
     */
    public static boolean forward(String keyword, Message message) {
        return forward(bucketNumber(keyword), message);
    }

    /**
     * Forward a message to the specified bucket number. If the bucket is
     * in a transient state (the value of 'state' is not 'null'), the handling
     * is determined by that state. This is a variant of the 'forward' method,
     * which handles local processing, instead of just returning 'false'.
     *
     * @param bucketNumber the bucket number determined by extracting the
     *     keyword from 'message'
     * @param message the message to be forwarded/processed
     */
    public static void forwardAndProcess(int bucketNumber, Message message) {
        if (!forward(bucketNumber, message)) {
            message.process();
        }
    }

    /**
     * Forward a message to the specified bucket number. If the bucket is
     * in a transient state (the value of 'state' is not 'null'), the handling
     * is determined by that state. This is a variant of the 'forward' method,
     * which handles local processing, instead of just returning 'false'.
     *
     * @param keyword the keyword extracted from 'message'
     *     keyword from 'message'
     * @param message the message to be forwarded/processed
     */
    public static void forwardAndProcess(String keyword, Message message) {
        forwardAndProcess(bucketNumber(keyword), message);
    }

    /**
     * Handle an incoming /cmd/dumpBuckets REST message.
     *
     * @param out the 'PrintStream' to use for displaying information
     */
    public static void dumpBuckets(final PrintStream out) {
        /**
         * we aren't really doing a 'Rebalance' here, but the 'copyData' method
         * is useful for extracting the data, and determining the buckets
         * associated with each server.
         */
        Rebalance rb = new Rebalance();
        rb.copyData();

        /**
         * this method is not accessing anything in the 'Server' or 'Bucket'
         * table, so it doesn't need to run within the 'MainLoop' thread.
         */
        rb.dumpBucketsInternal(out);
    }

    /**
     * Handle an incoming /cmd/bucketMessage REST message -- this is only
     * used for testing the routing of messages between servers.
     *
     * @param out the 'PrintStream' to use for displaying information
     * @param keyword the keyword that is hashed to select the bucket number
     * @param message the message to send to the remote end
     */
    public static void bucketMessage(
        final PrintStream out, final String keyword, String message)
        throws IOException {

        if (keyword == null) {
            out.println("'keyword' is mandatory");
            return;
        }
        if (message == null) {
            message = "Message generated at " + new Date();
        }
        final int bucketNumber = bucketNumber(keyword);
        Server server = bucketToServer(bucketNumber);

        if (server == null) {
            /**
             * selected bucket has no server assigned -- this should only be a
             * transient situation, until 'rebalance' is run.
             */
            out.println("Bucket is " + bucketNumber + ", which has no owner");
        } else if (server == Server.getThisServer()) {
            /**
             * the selected bucket is associated with this particular server --
             * no forwarding is needed.
             */
            out.println("Bucket is " + bucketNumber
                        + ", which is owned by this server: " + server.getUuid());
        } else {
            /**
             * the selected bucket is assigned to a different server -- forward
             * the message.
             */
            out.println("Bucket is " + bucketNumber + ": sending from\n"
                        + "    " + Server.getThisServer().getUuid() + " to \n"
                        + "    " + server.getUuid());

            /**
             * do a POST call of /bucket/bucketResponse to the remote server.
             */
            Entity<String> entity =
                Entity.entity(new String(message.getBytes(), StandardCharsets.UTF_8),
                    MediaType.TEXT_PLAIN);

            /**
             * the POST itself runs in a server-specific thread, and
             * 'responseQueue' is used to pass back the response.
             */
            final LinkedTransferQueue<Response> responseQueue =
                new LinkedTransferQueue<>();

            server.post("bucket/bucketResponse", entity, new Server.PostResponse() {
                /**
                 * {@inheritDoc}
                 */
                @Override
                public WebTarget webTarget(WebTarget webTarget) {
                    /**
                     * we need to include the 'bucket' and 'keyword' parameters
                     * in the POST that we are sending out.
                     */
                    return webTarget
                           .queryParam("bucket", bucketNumber)
                           .queryParam("keyword", keyword);
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void response(Response response) {
                    /**
                     * this is the POST response --
                     * pass it back to the calling thread.
                     */
                    responseQueue.put(response);
                }
            });

            try {
                // this is the calling thread -- wait for the POST response
                Response response = responseQueue.poll(60, TimeUnit.SECONDS);
                if (response == null) {
                    out.println("Timed out waiting for a response");
                } else {
                    out.println("Received response code " + response.getStatus());
                    out.println("Entity = " + response.readEntity(String.class));
                }
            } catch (InterruptedException e) {
                out.println(e);
                logger.error("Exception in Bucket.bucketMessage", e);
                throw new IOException(e);
            }
        }
    }

    /**
     * Handle an incoming /bucket/bucketResponse REST message -- this runs on
     * the destination host, and is the continuation of an operation triggered
     * by the /cmd/bucketMessage REST message running on the originating host.
     *
     * @param out the 'PrintStream' to use for passing back information
     *     in a human-readable form
     * @param bucket the bucket number, which should be owned by this host
     *     if we are in sync with the sending host, and didn't get caught
     *     in a transient state
     * @param keyword the keyword selected on the originating end, which should
     *     hash to 'bucket'
     * @param message the message selected on the originating end
     */
    public static void bucketResponse(
        final PrintStream out, int bucket, String keyword, byte[] message) {

        Server thisServer = Server.getThisServer();
        Server server = bucketToServer(bucket);

        if (server != thisServer) {
            /**
             * this isn't expected, and either indicates we are out-of-sync with
             * pthe originating server, or this operation was triggered while in
             * a transient state.
             */
            out.println("ERROR: " + thisServer.toString() + ": bucket " + bucket
                        + "is owned by\n    " + server);
        } else {
            /**
             * As expected, we are the owner of this bucket. Print out a message,
             * which will be returned to the originating host, and displayed.
             */
            out.println(thisServer.toString() + ":\n"
                        + "    bucket = " + bucket
                        + "\n    keyword = " + keyword
                        + "\n    message = " + new String(message));
        }
    }

    /**
     * Handle an incoming /cmd/moveBucket REST message -- this is only
     * used for testing bucket migration. It only works on the lead server.
     *
     * @param out the 'PrintStream' to use for displaying information
     * @param bucketNumber the bucket number to be moved
     * @param newHostUuid the UUID of the destination host (if 'null', a
     *     destination host will be chosen at random)
     */
    public static void moveBucket(PrintStream out, int bucketNumber, String newHostUuid) {
        Server leader = Leader.getLeader();
        if (leader != Server.getThisServer()) {
            out.println("This is not the lead server");
            return;
        }

        if (bucketNumber < 0 || bucketNumber >= indexToBucket.length) {
            out.println("Bucket number out of range");
            return;
        }

        Rebalance rb = new Rebalance();
        rb.copyData();

        TestBucket bucket = rb.buckets[bucketNumber];
        TestServer oldHost = bucket.owner;

        if (oldHost == rb.nullServer) {
            out.println("Bucket " + bucketNumber + " is currently unassigned");
            return;
        }

        TestServer newHost = null;

        if (newHostUuid != null) {
            // the UUID of a destination host has been specified
            newHost = rb.testServers.get(UUID.fromString(newHostUuid));
            if (newHost == null) {
                out.println("Can't locate UUID " + newHostUuid);
                return;
            }
        } else {
            /**
             * Choose a destination host at random, other than the current owner.
             * Step a random count in the range of 1 to (n-1) relative to the
             * current host.
             */
            UUID key = oldHost.uuid;
            for (int count = new Random().nextInt(rb.testServers.size() - 1) ;
                    count >= 0 ; count -= 1) {
                key = rb.testServers.higherKey(key);
                if (key == null) {
                    // wrap to the beginning of the list
                    key = rb.testServers.firstKey();
                }
            }
            newHost = rb.testServers.get(key);
        }
        out.println("Moving bucket " + bucketNumber + " from "
                    + oldHost + " to " + newHost);

        /**
         * update the owner, and ensure that the primary and secondary backup
         * remain different from the owner.
         */
        bucket.setOwner(newHost);
        if (newHost == bucket.primaryBackup) {
            out.println("Moving primary back from " + newHost + " to " + oldHost);
            bucket.setPrimaryBackup(oldHost);
        } else if (newHost == bucket.secondaryBackup) {
            out.println("Moving secondary back from " + newHost
                        + " to " + oldHost);
            bucket.setSecondaryBackup(oldHost);
        }

        try {
            /**
             * build a message containing all of the updated bucket
             * information, process it internally in this host
             * (lead server), and send it out to others in the
             * "notify list".
             */
            rb.generateBucketMessage();
        } catch (IOException e) {
            logger.error("Exception in Rebalance.generateBucketMessage",
                         e);
        }
    }

    /**
     * This method is called when an incoming /bucket/sessionData message is
     * received from the old owner of the bucket, which presumably means that
     * we are the new owner of the bucket.
     *
     * @param bucketNumber the bucket number
     * @param dest the UUID of the intended destination
     * @param ttl similar to IP time-to-live -- it controls the number of hops
     *     the message may take
     * @param data serialized data associated with this bucket, encoded using
     *     base64
     */

    public static void sessionData(int bucketNumber, UUID dest, int ttl, byte[] data) {
        logger.info("Bucket.sessionData: bucket={}, data length={}",
                    bucketNumber, data.length);

        if (dest != null && !dest.equals(Server.getThisServer().getUuid())) {
            // the message needs to be forwarded to the intended destination
            Server server;
            WebTarget webTarget;

            if ((ttl -= 1) > 0
                    && (server = Server.getServer(dest)) != null
                    && (webTarget = server.getWebTarget("bucket/sessionData")) != null) {
                logger.info("Forwarding 'bucket/sessionData' to uuid {}",
                            server.getUuid());
                Entity<String> entity =
                    Entity.entity(new String(data, StandardCharsets.UTF_8),
                                  MediaType.APPLICATION_OCTET_STREAM_TYPE);
                Response response =
                    webTarget
                    .queryParam("bucket", bucketNumber)
                    .queryParam("dest", dest)
                    .queryParam("ttl", String.valueOf(ttl))
                    .request().post(entity);
                logger.info("/bucket/sessionData response code = {}",
                            response.getStatus());
            } else {
                logger.error("Couldn't forward 'bucket/sessionData' to uuid {}, ttl={}",
                             dest, ttl);
            }
            return;
        }

        byte[] decodedData = Base64.getDecoder().decode(data);
        Bucket bucket = indexToBucket[bucketNumber];

        logger.info("Bucket.sessionData: decoded data length = {}",
                    decodedData.length);

        if (bucket.getState() == null) {
            /**
             * We received the serialized data prior to being notified
             * that we are the owner -- this happens sometimes. Behave as
             * though we are the new owner, but intidate it is unconfirmed.
             */
            logger.info("Bucket {} session data received unexpectedly",
                            bucketNumber);
            bucket.setState(new NewOwner(false, bucket.owner, bucket));
        }
        bucket.state.bulkSerializedData(decodedData);
    }

    /**
     * This method is called whenever the bucket's state has changed in a
     * way that it should be audited.
     */
    protected synchronized void stateChanged() {
        if (state != null) {
            return;
        }
        // the audit should be run
        Server thisServer = Server.getThisServer();
        boolean isOwner = (thisServer == owner);
        boolean isBackup = (!isOwner && (thisServer == primaryBackup
                                             || thisServer == secondaryBackup));

        // invoke 'TargetLock' directly
        TargetLock.auditBucket(this, isOwner, isBackup);
        for (ServerPoolApi feature : ServerPoolApi.impl.getList()) {
            feature.auditBucket(this, isOwner, isBackup);
        }
    }

    /**
     * Returns an adjunct of the specified class
     * (it is created if it doesn't exist).
     *
     * @param clazz this is the class of the adjunct
     * @return an adjunct of the specified class ('null' may be returned if
     *     the 'newInstance' method is unable to create the adjunct)
     */
    public <T> T getAdjunct(Class<T> clazz) {
        synchronized (adjuncts) {
            // look up the adjunct in the table
            Object adj = adjuncts.get(clazz);
            if (adj == null) {
                // lookup failed -- create one
                try {
                    // create the adjunct (may trigger an exception)
                    adj = clazz.newInstance();

                    // update the table
                    adjuncts.put(clazz, adj);
                } catch (Exception e) {
                    logger.error("Can't create adjunct of {}", clazz, e);
                }
            }
            return clazz.cast(adj);
        }
    }

    /**
     * Returns an adjunct of the specified class.
     *
     * @param clazz this is the class of the adjunct
     * @return an adjunct of the specified class, if it exists,
     *     and 'null' if it does not
     */
    public <T> T getAdjunctDontCreate(Class<T> clazz) {
        synchronized (adjuncts) {
            // look up the adjunct in the table
            return clazz.cast(adjuncts.get(clazz));
        }
    }

    /**
     * Explicitly create an adjunct -- this is useful when the adjunct
     * initialization requires that some parameters be passed.
     *
     * @param adj this is the adjunct to insert into the table
     * @return the previous adjunct of this type ('null' if none)
     */
    public Object putAdjunct(Object adj) {
        synchronized (adjuncts) {
            Class clazz = adj.getClass();
            return adjuncts.put(clazz, adj);
        }
    }

    /**
     * Remove an adjunct.
     *
     * @param clazz this is the class of adjuncts to remove
     * @return the object, if found, and 'null' if not
     */
    public <T> T removeAdjunct(Class<T> clazz) {
        synchronized (adjuncts) {
            // remove the adjunct in the table
            return clazz.cast(adjuncts.remove(clazz));
        }
    }

    /**
     * Dump out all buckets with adjuncts.
     *
     * @param out the 'PrintStream' to use for displaying information
     */
    public static void dumpAdjuncts(PrintStream out) {
        boolean noneFound = true;
        String format = "%6s %s\n";

        for (Bucket bucket : indexToBucket) {
            synchronized (bucket.adjuncts) {
                if (bucket.adjuncts.size() != 0) {
                    if (noneFound) {
                        out.printf(format, "Bucket", "Adjunct Classes");
                        out.printf(format, "------", "---------------");
                        noneFound = false;
                    }
                    boolean first = true;
                    for (Class<?> clazz : bucket.adjuncts.keySet()) {
                        if (first) {
                            out.printf(format, bucket.index, clazz.getName());
                            first = false;
                        } else {
                            out.printf(format, "", clazz.getName());
                        }
                    }
                }
            }
        }
    }
}
