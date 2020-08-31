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

import static org.onap.policy.drools.serverpool.ServerPoolProperties.BUCKET_CONFIRMED_TIMEOUT;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.BUCKET_TIME_TO_LIVE;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.BUCKET_UNCONFIRMED_GRACE_PERIOD;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.BUCKET_UNCONFIRMED_TIMEOUT;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DEFAULT_BUCKET_CONFIRMED_TIMEOUT;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DEFAULT_BUCKET_TIME_TO_LIVE;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DEFAULT_BUCKET_UNCONFIRMED_GRACE_PERIOD;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DEFAULT_BUCKET_UNCONFIRMED_TIMEOUT;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.getProperty;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Random;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
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

@Getter
@Setter
public class Bucket {
    private static Logger logger = LoggerFactory.getLogger(Bucket.class);

    /*
     * Listener class to handle state changes that may lead to
     * reassignments of buckets
     */
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
            throw new ExceptionInInitializerError(e);
        }
    }

    /*
     * Values extracted from properties
     */

    private static String timeToLive;
    private static long confirmedTimeout;
    private static long unconfirmedTimeout;
    private static long unconfirmedGracePeriod;

    /*
     * Tags for encoding of bucket data
     */
    private static final int END_OF_PARAMETERS_TAG = 0;
    private static final int OWNER_UPDATE = 1;
    private static final int OWNER_NULL = 2;
    private static final int PRIMARY_BACKUP_UPDATE = 3;
    private static final int PRIMARY_BACKUP_NULL = 4;
    private static final int SECONDARY_BACKUP_UPDATE = 5;
    private static final int SECONDARY_BACKUP_NULL = 6;

    // This is the table itself -- the current size is fixed at 1024 buckets
    public static final int BUCKETCOUNT = 1024;
    private static Bucket[] indexToBucket = new Bucket[BUCKETCOUNT];

    static {
        // create hash bucket entries, but there are no assignments yet
        for (int i = 0; i < indexToBucket.length; i += 1) {
            Bucket bucket = new Bucket(i);
            indexToBucket[i] = bucket;
        }
    }

    // this is a list of all objects registered for the 'Backup' interface
    private static List<Backup> backupList = new LinkedList<>();

    // 'rebalance' is a non-null value when rebalancing is in progress
    private static Object rebalanceLock = new Object();
    private static Rebalance rebalance = null;

    // bucket number
    private volatile int index;

    // owner of the bucket -- this is the host where messages should be directed
    private volatile Server owner = null;

    // this host will take over as the owner if the current owner goes down,
    // and may also contain backup data to support persistence
    private volatile Server primaryBackup = null;

    // this is a secondary backup host, which can be used if both owner and
    // primary backup go out in quick succession
    private volatile Server secondaryBackup = null;

    // when we are in a transient state, certain events are forwarded to
    // this object
    private volatile State state = null;

    // storage for additional data
    private Map<Class<?>, Object> adjuncts = new HashMap<>();

    // HTTP query parameters
    private static final String QP_BUCKET = "bucket";
    private static final String QP_KEYWORD = "keyword";
    private static final String QP_DEST = "dest";
    private static final String QP_TTL = "ttl";
    private static final String OWNED_STR = "Owned";

    // BACKUP data (only buckets for where we are the owner, or a backup)

    // TBD: need fields for outgoing queues for application message transfers

    /**
     * This method triggers registration of 'eventHandler', and also extracts
     * property values.
     */
    static void startup() {
        int intTimeToLive =
            getProperty(BUCKET_TIME_TO_LIVE, DEFAULT_BUCKET_TIME_TO_LIVE);
        timeToLive = String.valueOf(intTimeToLive);
        confirmedTimeout =
            getProperty(BUCKET_CONFIRMED_TIMEOUT, DEFAULT_BUCKET_CONFIRMED_TIMEOUT);
        unconfirmedTimeout =
            getProperty(BUCKET_UNCONFIRMED_TIMEOUT,
                        DEFAULT_BUCKET_UNCONFIRMED_TIMEOUT);
        unconfirmedGracePeriod =
            getProperty(BUCKET_UNCONFIRMED_GRACE_PERIOD,
                        DEFAULT_BUCKET_UNCONFIRMED_GRACE_PERIOD);
    }

    /**
     * Constructor -- called when building the 'indexToBucket' table.
     *
     * @param index the bucket number
     */
    private Bucket(int index) {
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
        /*
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
            /*
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
        return bucket.getOwner();
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
     * Handle an incoming /bucket/update REST message.
     *
     * @param data base64-encoded data, containing all bucket updates
     */
    static void updateBucket(byte[] data) {
        final byte[] packet = Base64.getDecoder().decode(data);
        Runnable task = () -> {
            try {
                /*
                 * process the packet, handling any updates
                 */
                if (updateBucketInternal(packet)) {
                    /*
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
        MainLoop.queueWork(task);
    }

    /**
     * This method supports the 'updateBucket' method, and runs entirely within
     * the 'MainLoop' thread.
     */
    private static boolean updateBucketInternal(byte[] packet) throws IOException {
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

            /*
             * the remainder of the information for this bucket consists of
             * a sequence of '<tag> [ <associated-data> ]' followed by the tag
             * value 'END_OF_PARAMETERS_TAG'.
             */
            int tag;
            while ((tag = dis.readUnsignedByte()) != END_OF_PARAMETERS_TAG) {
                switch (tag) {
                    case OWNER_UPDATE:
                        // <OWNER_UPDATE> <owner-uuid> -- owner UUID specified
                        bucketChanges = updateBucketInternalOwnerUpdate(bucket, dis, index);
                        break;

                    case OWNER_NULL:
                        // <OWNER_NULL> -- owner UUID should be set to 'null'
                        bucketChanges = nullifyOwner(index, bucket, bucketChanges);
                        break;

                    case PRIMARY_BACKUP_UPDATE:
                        // <PRIMARY_BACKUP_UPDATE> <primary-backup-uuid> --
                        // primary backup UUID specified
                        bucketChanges = updatePrimaryBackup(dis, index, bucket, bucketChanges);
                        break;

                    case PRIMARY_BACKUP_NULL:
                        // <PRIMARY_BACKUP_NULL> --
                        // primary backup should be set to 'null'
                        bucketChanges = nullifyPrimaryBackup(index, bucket, bucketChanges);
                        break;

                    case SECONDARY_BACKUP_UPDATE:
                        // <SECONDARY_BACKUP_UPDATE> <secondary-backup-uuid> --
                        // secondary backup UUID specified
                        bucketChanges = updateSecondaryBackup(dis, index, bucket, bucketChanges);
                        break;

                    case SECONDARY_BACKUP_NULL:
                        // <SECONDARY_BACKUP_NULL> --
                        // secondary backup should be set to 'null'
                        bucketChanges = nullifySecondaryBackup(index, bucket, bucketChanges);
                        break;

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

    private static boolean nullifyOwner(int index, Bucket bucket, boolean bucketChanges) {
        if (bucket.getOwner() != null) {
            logger.info("Bucket {} owner: {}->null",
                        index, bucket.getOwner());
            bucketChanges = true;
            synchronized (bucket) {
                bucket.setOwner(null);
                bucket.setState(null);
            }
        }
        return bucketChanges;
    }

    private static boolean updatePrimaryBackup(DataInputStream dis, int index, Bucket bucket, boolean bucketChanges)
                    throws IOException {
        Server newPrimaryBackup =
            Server.getServer(Util.readUuid(dis));
        if (bucket.primaryBackup != newPrimaryBackup) {
            logger.info("Bucket {} primary backup: {}->{}", index,
                        bucket.primaryBackup, newPrimaryBackup);
            bucketChanges = true;
            bucket.primaryBackup = newPrimaryBackup;
        }
        return bucketChanges;
    }

    private static boolean nullifyPrimaryBackup(int index, Bucket bucket, boolean bucketChanges) {
        if (bucket.primaryBackup != null) {
            logger.info("Bucket {} primary backup: {}->null",
                        index, bucket.primaryBackup);
            bucketChanges = true;
            bucket.primaryBackup = null;
        }
        return bucketChanges;
    }

    private static boolean updateSecondaryBackup(DataInputStream dis, int index, Bucket bucket, boolean bucketChanges)
                    throws IOException {
        Server newSecondaryBackup =
            Server.getServer(Util.readUuid(dis));
        if (bucket.secondaryBackup != newSecondaryBackup) {
            logger.info("Bucket {} secondary backup: {}->{}", index,
                        bucket.secondaryBackup, newSecondaryBackup);
            bucketChanges = true;
            bucket.secondaryBackup = newSecondaryBackup;
        }
        return bucketChanges;
    }

    private static boolean nullifySecondaryBackup(int index, Bucket bucket, boolean bucketChanges) {
        if (bucket.secondaryBackup != null) {
            logger.info("Bucket {} secondary backup: {}->null",
                        index, bucket.secondaryBackup);
            bucketChanges = true;
            bucket.secondaryBackup = null;
        }
        return bucketChanges;
    }

    /**
     * Update bucket owner information.
     *
     * @param bucket the bucket in process
     * @param dis data input stream contains the update
     * @param index the bucket number
     * @return a value indicate bucket changes
     */
    private static boolean updateBucketInternalOwnerUpdate(Bucket bucket, DataInputStream dis,
            int index) throws IOException {
        boolean bucketChanges = false;
        Server newOwner = Server.getServer(Util.readUuid(dis));
        if (bucket.getOwner() != newOwner) {
            logger.info("Bucket {} owner: {}->{}",
                        index, bucket.getOwner(), newOwner);
            bucketChanges = true;

            Server thisServer = Server.getThisServer();
            Server oldOwner = bucket.getOwner();
            bucket.setOwner(newOwner);
            if (thisServer == oldOwner) {
                // the current server is the old owner
                if (bucket.getState() == null) {
                    bucket.state = bucket.new OldOwner(newOwner);
                }
            } else if (thisServer == newOwner) {
                // the current server the new owner
                if (bucket.getState() == null) {
                    bucket.state = bucket.new NewOwner(true, oldOwner);
                } else {
                    // new owner has been confirmed
                    bucket.state.newOwner();
                }
            }
        }
        return bucketChanges;
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

        synchronized (bucket) {
            if (bucket.state != null) {
                // we are in a transient state -- the handling is state-specific
                return bucket.state.forward(message);
            }
            server = bucket.getOwner();
        }

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
        /*
         * we aren't really doing a 'Rebalance' here, but the 'copyData' method
         * is useful for extracting the data, and determining the buckets
         * associated with each server.
         */
        Rebalance rb = new Rebalance();
        rb.copyData();

        /*
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
     * @throws IOException when error occurred
     */
    public static void bucketMessage(
        final PrintStream out, final String keyword, String message) {

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
            /*
             * selected bucket has no server assigned -- this should only be a
             * transient situation, until 'rebalance' is run.
             */
            out.format("Bucket is %d, which has no owner%n", bucketNumber);
        } else if (server == Server.getThisServer()) {
            /*
             * the selected bucket is associated with this particular server --
             * no forwarding is needed.
             */
            out.format("Bucket is %d, which is owned by this server: %s%n",
                       bucketNumber, server.getUuid());
        } else {
            /*
             * the selected bucket is assigned to a different server -- forward
             * the message.
             */
            out.format("Bucket is %d: sending from%n"
                       + "    %s to%n"
                       + "    %s%n",
                       bucketNumber, Server.getThisServer().getUuid(), server.getUuid());

            // do a POST call of /bucket/bucketResponse to the remoote server
            Entity<String> entity =
                Entity.entity(new String(message.getBytes(), StandardCharsets.UTF_8),
                        MediaType.TEXT_PLAIN);

            /*
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
                    // we need to include the 'bucket' and 'keyword' parameters
                    // in the POST that we are sending out
                    return webTarget
                           .queryParam(QP_BUCKET, bucketNumber)
                           .queryParam(QP_KEYWORD, keyword);
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void response(Response response) {
                    // this is the POST response --
                    // pass it back to the calling thread
                    responseQueue.put(response);
                }
            });

            try {
                // this is the calling thread -- wait for the POST response
                Response response = responseQueue.poll(60, TimeUnit.SECONDS);
                if (response == null) {
                    out.println("Timed out waiting for a response");
                } else {
                    out.format("Received response code %s%nEntity = %s%n",
                               response.getStatus(), response.readEntity(String.class));
                }
            } catch (InterruptedException e) {
                out.println(e);
                Thread.currentThread().interrupt();
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
            /*
             * this isn't expected, and either indicates we are out-of-sync with
             * pthe originating server, or this operation was triggered while in
             * a transient state.
             */
            out.println("ERROR: " + thisServer.toString() + ": bucket " + bucket
                        + "is owned by\n    " + server);
        } else {
            /*
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
            /*
             * Choose a destination host at random, other than the current owner.
             * Step a random count in the range of 1 to (n-1) relative to the
             * current host.
             */
            UUID key = oldHost.uuid;
            /*
             * Disabling sonar, because this Random() is not used for security purposes.
             */
            int randomStart = new Random().nextInt(rb.testServers.size() - 1);  // NOSONAR
            for (int count = randomStart; count >= 0; count -= 1) {
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

        updateOwner(out, bucket, oldHost, newHost);

        try {
            /*
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

    private static void updateOwner(PrintStream out, TestBucket bucket, TestServer oldHost, TestServer newHost) {
        /*
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

    static void sessionData(int bucketNumber, UUID dest, int ttl, byte[] data) {
        logger.info("Bucket.sessionData: bucket={}, data length={}",
                    bucketNumber, data.length);

        if (dest != null && !dest.equals(Server.getThisServer().getUuid())) {
            // the message needs to be forwarded to the intended destination
            Server server;
            WebTarget webTarget;

            ttl -= 1;
            if (ttl > 0
                    && (server = Server.getServer(dest)) != null
                    && (webTarget = server.getWebTarget("bucket/sessionData")) != null) {
                logger.info("Forwarding 'bucket/sessionData' to uuid {}",
                            server.getUuid());
                Entity<String> entity =
                    Entity.entity(new String(data, StandardCharsets.UTF_8),
                                  MediaType.APPLICATION_OCTET_STREAM_TYPE);
                Response response =
                    webTarget
                    .queryParam(QP_BUCKET, bucketNumber)
                    .queryParam(QP_DEST, dest)
                    .queryParam(QP_TTL, String.valueOf(ttl))
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

        if (bucket.state == null) {
            /*
             * We received the serialized data prior to being notified
             * that we are the owner -- this happens sometimes. Behave as
             * though we are the new owner, but intidate it is unconfirmed.
             */
            logger.info("Bucket {} session data received unexpectedly",
                        bucketNumber);
            bucket.state = bucket.new NewOwner(false, bucket.getOwner());
        }
        bucket.state.bulkSerializedData(decodedData);
    }

    /**
     * This method is called whenever the bucket's state has changed in a
     * way that it should be audited.
     */
    private synchronized void stateChanged() {
        if (state != null) {
            return;
        }
        // the audit should be run
        Server thisServer = Server.getThisServer();
        boolean isOwner = (thisServer == owner);
        boolean isBackup = (!isOwner && (thisServer == primaryBackup
                                         || thisServer == secondaryBackup));

        // invoke 'TargetLock' directly
        TargetLock.auditBucket(this, isOwner);
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
        Object adj = adjuncts.computeIfAbsent(clazz, key -> {
            try {
                // create the adjunct, if needed
                return clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                logger.error("Can't create adjunct of {}", clazz, e);
                return null;
            }
        });
        return clazz.cast(adj);
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
            Class<?> clazz = adj.getClass();
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

    /* ============================================================ */

    /**
     * There is a single instance of this class (Bucket.eventHandler), which
     * is registered to listen for notifications of state transitions. Note
     * that all of these methods are running within the 'MainLoop' thread.
     */
    private static class EventHandler implements Events {
        /**
         * {@inheritDoc}
         */
        @Override
        public void serverFailed(Server server) {
            // remove this server from any bucket where it is referenced

            Server thisServer = Server.getThisServer();
            for (Bucket bucket : indexToBucket) {
                synchronized (bucket) {
                    boolean changes = false;
                    if (bucket.getOwner() == server) {
                        // the failed server owns this bucket --
                        // move to the primary backup
                        bucket.setOwner(bucket.getPrimaryBackup());
                        bucket.primaryBackup = null;
                        changes = true;

                        if (bucket.getOwner() == null) {
                            // bucket owner is still null -- presumably, we had no
                            // primary backup, so use the secondary backup instead
                            bucket.setOwner(bucket.getSecondaryBackup());
                            bucket.setSecondaryBackup(null);
                        }
                    }
                    if (bucket.getPrimaryBackup() == server) {
                        // the failed server was a primary backup to this bucket --
                        // mark the entry as 'null'
                        bucket.setPrimaryBackup(null);
                        changes = true;
                    }
                    if (bucket.getSecondaryBackup() == server) {
                        // the failed server was a secondary backup to this bucket --
                        // mark the entry as 'null'
                        bucket.setSecondaryBackup(null);
                        changes = true;
                    }

                    if (bucket.owner == thisServer && bucket.state == null) {
                        // the current server is the new owner
                        bucket.setState(bucket.new NewOwner(false, null));
                        changes = true;
                    }

                    if (changes) {
                        // may give audits a chance to run
                        bucket.stateChanged();
                    }
                }
            }

            // trigger a rebalance (only happens if we are the lead server)
            rebalance();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void newLeader(Server server) {
            // trigger a rebalance (only happens if we are the new lead server)
            rebalance();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void leaderConfirmed(Server server) {
            // trigger a rebalance (only happens if we are the lead server)
            rebalance();
        }

        /**
         * This method is called to start a 'rebalance' operation in a background
         * thread, but it only does this on the lead server. Being balanced means
         * the following:
         * 1) Each server owns approximately the same number of buckets
         * 2) If any server were to fail, and the designated primaries take over
         *     for all of that server's buckets, all remaining servers would still
         *     own approximately the same number of buckets.
         * 3) If any two servers were to fail, and the designated primaries were
         *     to take over for the failed server's buckets (secondaries would take
         *     for buckets where the owner and primary are OOS), all remaining
         *     servers would still own approximately the same number of buckets.
         * 4) Each server should have approximately the same number of
         *     (primary-backup + secondary-backup) buckets that it is responsible for.
         * 5) The primary backup for each bucket must be on the same site as the
         *     owner, and the secondary backup must be on a different site.
         */
        private void rebalance() {
            if (Leader.getLeader() == Server.getThisServer()) {
                Rebalance rb = new Rebalance();
                synchronized (rebalanceLock) {
                    // the most recent 'Rebalance' instance is the only valid one
                    rebalance = rb;
                }

                new Thread("BUCKET REBALANCER") {
                    @Override
                    public void run() {
                        /*
                         * copy bucket and host data,
                         * generating a temporary internal table.
                         */
                        rb.copyData();

                        /*
                         * allocate owners for all buckets without an owner,
                         * and rebalance bucket owners, if necessary --
                         * this takes card of item #1, above.
                         */
                        rb.allocateBuckets();

                        /*
                         * make sure that primary backups always have the same site
                         * as the owner, and secondary backups always have a different
                         * site -- this takes care of #5, above.
                         */
                        rb.checkSiteValues();

                        /*
                         * adjust primary backup lists to take care of item #2, above
                         * (taking #5 into account).
                         */
                        rb.rebalancePrimaryBackups();

                        /*
                         * allocate secondary backups, and take care of items
                         * #3 and #4, above (taking #5 into account).
                         */
                        rb.rebalanceSecondaryBackups();

                        try {
                            synchronized (rebalanceLock) {
                                /*
                                 * if another 'Rebalance' instance has started in the
                                 * mean time, don't do the update.
                                 */
                                if (rebalance == rb) {
                                    /*
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
    }

    /* ============================================================ */

    /**
     * Instances of this class are created as part of the 'rebalance'
     * operation on the lead server, or as part of a 'dumpBuckets' operation
     * on any server.
     * Each instance of this class corresponds to a 'Bucket' instance.
     */
    @EqualsAndHashCode
    private static class TestBucket implements Comparable<TestBucket> {
        // bucket number
        int index;

        // owner of the bucket
        TestServer owner;

        // primary backup for this bucket

        TestServer primaryBackup;

        // secondary backup for this bucket
        TestServer secondaryBackup;

        /**
         * Constructor -- initialize the 'TestBucket' instance.
         *
         * @param index the bucket number
         */
        TestBucket(int index) {
            this.index = index;
        }

        /**
         * Update the owner of a bucket, which also involves updating the
         * backward links in the 'TestServer' instances.
         *
         * @param newOwner the new owner of the bucket
         */
        void setOwner(TestServer newOwner) {
            if (owner != newOwner) {
                // the 'owner' field does need to be changed
                if (owner != null) {
                    // remove this bucket from the 'buckets' list of the old owner
                    owner.buckets.remove(this);
                }
                if (newOwner != null) {
                    // add this bucket to the 'buckets' list of the new owner
                    newOwner.buckets.add(this);
                }
                // update the 'owner' field in the bucket
                owner = newOwner;
            }
        }

        /**
         * Update the primary backup of a bucket, which also involves updating
         * the backward links in the 'TestServer' instances.
         *
         * @param newPrimaryBackup the new primary of the bucket
         */
        void setPrimaryBackup(TestServer newPrimaryBackup) {
            if (primaryBackup != newPrimaryBackup) {
                // the 'primaryBackup' field does need to be changed
                if (primaryBackup != null) {
                    // remove this bucket from the 'buckets' list of the
                    // old primary backup
                    primaryBackup.primaryBackupBuckets.remove(this);
                }
                if (newPrimaryBackup != null) {
                    // add this bucket to the 'buckets' list of the
                    // new primary backup
                    newPrimaryBackup.primaryBackupBuckets.add(this);
                }
                // update the 'primaryBackup' field in the bucket
                primaryBackup = newPrimaryBackup;
            }
        }

        /**
         * Update the secondary backup of a bucket, which also involves updating
         * the backward links in the 'TestServer' instances.
         *
         * @param newSecondaryBackup the new secondary of the bucket
         */
        void setSecondaryBackup(TestServer newSecondaryBackup) {
            if (secondaryBackup != newSecondaryBackup) {
                // the 'secondaryBackup' field does need to be changed
                if (secondaryBackup != null) {
                    // remove this bucket from the 'buckets' list of the
                    // old secondary backup
                    secondaryBackup.secondaryBackupBuckets.remove(this);
                }
                if (newSecondaryBackup != null) {
                    // add this bucket to the 'buckets' list of the
                    // new secondary backup
                    newSecondaryBackup.secondaryBackupBuckets.add(this);
                }
                // update the 'secondaryBackup' field in the bucket
                secondaryBackup = newSecondaryBackup;
            }
        }

        /*==================================*/
        /* Comparable<TestBucket> interface */
        /*==================================*/

        /**
         * Compare two 'TestBucket' instances, for use in a 'TreeSet'.
         *
         * @param other the other 'TestBucket' instance to compare to
         */
        @Override
        public int compareTo(TestBucket other) {
            return index - other.index;
        }

        /**
         * Return a string representation of this 'TestBucket' instance.
         *
         * @return a string representation of this 'TestBucket' instance
         */
        @Override
        public String toString() {
            return "TestBucket[" + index + "]";
        }
    }

    /* ============================================================ */

    /**
     * Instances of this class are created as part of the 'rebalance'
     * operation on the lead server, or as part of a 'dumpBuckets' operation
     * on any server.
     * Each instance of this class corresponds to a 'Server' instance.
     * Unlike the actual 'Server' instances, each 'TestServer' instance
     * contains back links to all of the buckets it is associated with.
     */
    private static class TestServer {
        // unique id for this server
        // (matches the one in the corresponding 'Server' entry)
        final UUID uuid;

        // site socket information (matches 'Server' entry)
        final InetSocketAddress siteSocketAddress;

        // the set of all 'TestBucket' instances,
        // where this 'TestServer' is listed as 'owner'
        final TreeSet<TestBucket> buckets = new TreeSet<>();

        // the set of all 'TestBucket' instances,
        // where this 'TestServer' is listed as 'primaryBackup'
        final TreeSet<TestBucket> primaryBackupBuckets = new TreeSet<>();

        // the set of all 'TestBucket' instances,
        // where this 'TestServer' is listed as 'secondaryBackup'
        final TreeSet<TestBucket> secondaryBackupBuckets = new TreeSet<>();

        /**
         * Constructor.
         *
         * @param uuid uuid of this 'TestServer' instance
         * @param siteSocketAddress matches the value in the corresponding 'Server'
         */
        TestServer(UUID uuid, InetSocketAddress siteSocketAddress) {
            this.uuid = uuid;
            this.siteSocketAddress = siteSocketAddress;
        }

        /**
         * Return a string representation of this 'TestServer' instance.
         *
         * @return a string representation of this 'TestServer' instance
         */
        @Override
        public String toString() {
            return "TestServer[" + uuid + "]";
        }
    }

    /* ============================================================ */

    /**
     * This class supports the 'rebalance' operation. Each instance is a wrapper
     * around a 'TestServer' instance, as it would be if another specific
     * server failed.
     */
    @EqualsAndHashCode
    private static class AdjustedTestServer
        implements Comparable<AdjustedTestServer> {
        TestServer server;

        // simulated fail on this server
        TestServer failedServer;

        // expected bucket count if 'failedServer' fails
        int bucketCount;

        // total number of primary backup buckets used by this host
        int primaryBackupBucketCount;

        // total number of secondary backup buckets used by this host
        int secondaryBackupBucketCount;

        /**
         * Constructor.
         *
         * @param server the server this 'AdjustedTestServer' instance represents
         * @param failedServer the server going through a simulated failure --
         *     the 'bucketCount' value is adjusted based upon this
         */
        AdjustedTestServer(TestServer server, TestServer failedServer) {
            this.server = server;
            this.failedServer = failedServer;

            this.bucketCount = server.buckets.size();
            this.primaryBackupBucketCount = server.primaryBackupBuckets.size();
            this.secondaryBackupBucketCount = server.secondaryBackupBuckets.size();

            // need to adjust 'bucketCount' for the case where the current
            // host fails
            for (TestBucket bucket : server.primaryBackupBuckets) {
                if (bucket.owner == failedServer) {
                    bucketCount += 1;
                    // TBD: should 'primaryBackupBucketCount' be decremented?
                }
            }

            // need to adjust 'bucketCount' for the case where the current
            // host fails
            for (TestBucket bucket : server.secondaryBackupBuckets) {
                if (bucket.owner == failedServer) {
                    bucketCount += 1;
                    // TBD: should 'secondaryBackupBucketCount' be decremented?
                }
            }
        }

        /* ****************************************** */
        /* Comparable<AdjustedTestServer> interface   */
        /* ****************************************** */

        /**
         * {@inheritDoc}
         */
        @Override
        public int compareTo(AdjustedTestServer other) {
            /*
             * Comparison order:
             * 1) minimal expected bucket count if current host fails
             *    (differences of 1 are treated as a match)
             * 2) minimal backup bucket count
             * 3) UUID order
             */
            int rval = bucketCount - other.bucketCount;
            if (rval <= 1 && rval >= -1) {
                rval = (primaryBackupBucketCount + secondaryBackupBucketCount)
                       - (other.primaryBackupBucketCount
                          + other.secondaryBackupBucketCount);
                if (rval == 0) {
                    rval = -Util.uuidComparator.compare(server.uuid, other.server.uuid);
                }
            }
            return rval;
        }
    }

    /* ============================================================ */

    /**
     * This class is primarily used to do a 'Rebalance' operation on the
     * lead server, which is then distributed to all of the other servers.
     * Part of it is also useful for implementing the /cmd/dumpBuckets
     * REST message handler.
     */
    private static class Rebalance {
        // this table resembles the 'Bucket.indexToBucket' table
        TestBucket[] buckets = new TestBucket[indexToBucket.length];

        // this table resembles the 'Server.servers' table
        TreeMap<UUID, TestServer> testServers = new TreeMap<>(Util.uuidComparator);

        /* this is a special server instance, which is allocated any
         * owned, primary, or secondary buckets that haven't been allocated to
         * any of the real servers
         */
        TestServer nullServer = new TestServer(null, null);

        /**
         * Copy all of the bucket data in the 'buckets' table, and also return
         * a copy of the 'Server.servers' table
         */
        void copyData() {
            // will contain a copy of the 'Bucket' table
            final Bucket[] bucketSnapshot = new Bucket[indexToBucket.length];

            /*
             * This method is running within the 'MainLoop' thread,
             * and builds a copy of the 'Bucket' table, as well as the
             * list of active servers -- these can then be examined
             * in a different thread, without potentially distrupting
             * the 'MainLoop' thread.
             *
             * @return 0 (the return value is not significant at present)
             */
            Callable<Integer> callable = () -> {
                // copy the 'Bucket' table
                for (int i = 0; i < indexToBucket.length; i += 1) {
                    // makes a snapshot of the bucket information
                    Bucket bucket = indexToBucket[i];

                    Bucket tmpBucket = new Bucket(i);
                    tmpBucket.setOwner(bucket.getOwner());
                    tmpBucket.setPrimaryBackup(bucket.getPrimaryBackup());
                    tmpBucket.setSecondaryBackup(bucket.getSecondaryBackup());
                    bucketSnapshot[i] = tmpBucket;
                }

                /*
                 * At this point, 'bucketSnapshot' and 'servers' should be
                 * complete. The next step is to create a 'TestServer' entry
                 * that matches each 'Server' entry.
                 */
                for (Server server : Server.getServers()) {
                    UUID uuid = server.getUuid();
                    testServers.put(uuid, new TestServer(uuid, server.getSiteSocketAddress()));
                }

                return 0;
            };
            FutureTask<Integer> ft = new FutureTask<>(callable);
            MainLoop.queueWork(ft);
            try {
                ft.get(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                logger.error("Interrupted", e);
                Thread.currentThread().interrupt();
                return;
            } catch (ExecutionException | TimeoutException e) {
                logger.error("Exception in Rebalance.copyData", e);
                return;
            }

            makeTestBucket(bucketSnapshot);
        }

        private void makeTestBucket(final Bucket[] bucketSnapshot) {
            /*
             * Now, create a 'TestBucket' table that mirrors the 'Bucket' table.
             * Unlike the standard 'Bucket' and 'Server' tables, the 'TestServer'
             * entries contain links referring back to the 'TestBucket' entries.
             * This information is useful when rebalancing.
             */
            for (Bucket bucket : bucketSnapshot) {
                int index = bucket.index;
                TestBucket testBucket = new TestBucket(index);

                // populate the 'owner' field
                if (bucket.getOwner() != null) {
                    testBucket.setOwner(testServers.get(bucket.getOwner().getUuid()));
                } else {
                    testBucket.setOwner(nullServer);
                }

                // populate the 'primaryBackup' field
                if (bucket.primaryBackup != null) {
                    testBucket.setPrimaryBackup(
                        testServers.get(bucket.primaryBackup.getUuid()));
                } else {
                    testBucket.setPrimaryBackup(nullServer);
                }

                // populate the 'secondaryBackup' field
                if (bucket.secondaryBackup != null) {
                    testBucket.setSecondaryBackup(
                        testServers.get(bucket.secondaryBackup.getUuid()));
                } else {
                    testBucket.setSecondaryBackup(nullServer);
                }
                buckets[index] = testBucket;
            }
        }

        /**
         * Allocate unowned 'TestBucket' entries across all of the 'TestServer'
         * entries. When every 'TestBucket' has an owner, rebalance as needed,
         * so the 'TestServer' entry with the most buckets has at most one more
         * bucket than the 'TestServer' entry with the least.
         */
        void allocateBuckets() {
            /*
             * the following 'Comparator' is used to control the order of the
             * 'needBuckets' TreeSet: those with the fewest buckets allocated are
             * at the head of the list.
             */
            Comparator<TestServer> bucketCount = (s1, s2) -> {
                int rval = s1.buckets.size() - s2.buckets.size();
                if (rval == 0) {
                    rval = Util.uuidComparator.compare(s1.uuid, s2.uuid);
                }
                return rval;
            };

            // sort servers according to the order in which they can
            // take on ownership of buckets
            TreeSet<TestServer> needBuckets = new TreeSet<>(bucketCount);
            for (TestServer ts : testServers.values()) {
                needBuckets.add(ts);
            }

            // go through all of the unowned buckets, and allocate them
            for (TestBucket bucket : new LinkedList<TestBucket>(nullServer.buckets)) {
                // take first entry off of sorted server list
                TestServer ts = needBuckets.first();
                needBuckets.remove(ts);

                // add this bucket to the 'buckets' list
                bucket.setOwner(ts);

                // place it back in the list, possibly in a new position
                // (it's attributes have changed)
                needBuckets.add(ts);
            }
            nullServer.buckets.clear();

            // there may still be rebalancing needed -- no host should contain
            // 2 or more buckets more than any other host
            for ( ; ; ) {
                TestServer first = needBuckets.first();
                TestServer last = needBuckets.last();

                if (last.buckets.size() - first.buckets.size() <= 1) {
                    // no more rebalancing needed
                    break;
                }

                // remove both from sorted list
                needBuckets.remove(first);
                needBuckets.remove(last);

                // take one bucket from 'last', and assign it to 'first'
                last.buckets.first().setOwner(first);

                // place back in sorted list
                needBuckets.add(first);
                needBuckets.add(last);
            }
        }

        /**
         * Make sure that the primary backups have the same site as the owner,
         * and the secondary backups have a different site.
         */
        void checkSiteValues() {
            for (TestBucket bucket : buckets) {
                if (bucket.owner != null) {
                    InetSocketAddress siteSocketAddress =
                        bucket.owner.siteSocketAddress;
                    TestServer primaryBackup = bucket.primaryBackup;
                    TestServer secondaryBackup = bucket.secondaryBackup;

                    validateSiteOwner(bucket, siteSocketAddress,
                            primaryBackup, secondaryBackup);
                }
            }
        }

        /**
         * Validate primary site owner and secondary site owner are valid.
         * @param bucket TestBucket
         * @param siteSocketAddress site socket address
         * @param primaryBackup primary backups
         * @param secondaryBackup secondary backups
         */
        private void validateSiteOwner(TestBucket bucket, InetSocketAddress siteSocketAddress,
                TestServer primaryBackup, TestServer secondaryBackup) {
            if (primaryBackup != null
                    && !Objects.equals(siteSocketAddress,
                                       primaryBackup.siteSocketAddress)) {
                /*
                 * primary backup is from the wrong site -- see if we can
                 *  use the secondary.
                 */
                if (secondaryBackup != null
                            && Objects.equals(siteSocketAddress,
                                              secondaryBackup.siteSocketAddress)) {
                    // swap primary and secondary
                    bucket.setPrimaryBackup(secondaryBackup);
                    bucket.setSecondaryBackup(primaryBackup);
                } else {
                    // just invalidate primary backup
                    bucket.setPrimaryBackup(null);
                }
            } else if (secondaryBackup != null
                       && Objects.equals(siteSocketAddress,
                                         secondaryBackup.siteSocketAddress)) {
                // secondary backup is from the wrong site
                bucket.setSecondaryBackup(null);
                if (primaryBackup == null) {
                    // we can use this as the primary
                    bucket.setPrimaryBackup(secondaryBackup);
                }
            }
        }

        /**
         * Allocate and rebalance the primary backups.
         */
        void rebalancePrimaryBackups() {
            for (TestServer failedServer : testServers.values()) {
                /*
                 * to allocate primary backups for this server,
                 * simulate a failure, and balance the backup hosts
                 */

                // get siteSocketAddress from server
                InetSocketAddress siteSocketAddress = failedServer.siteSocketAddress;

                // populate a 'TreeSet' of 'AdjustedTestServer' instances based
                // the failure of 'failedServer'
                TreeSet<AdjustedTestServer> adjustedTestServers = new TreeSet<>();
                for (TestServer server : testServers.values()) {
                    if (server == failedServer
                            || !Objects.equals(siteSocketAddress,
                                               server.siteSocketAddress)) {
                        continue;
                    }
                    adjustedTestServers.add(new AdjustedTestServer(server, failedServer));
                }

                if (adjustedTestServers.isEmpty()) {
                    // this is presumably the only server -- there is no other server
                    // to act as primary backup, and no rebalancing can occur
                    continue;
                }

                // we need a backup host for each bucket
                for (TestBucket bucket : failedServer.buckets) {
                    if (bucket.primaryBackup == null
                            || bucket.primaryBackup == nullServer) {
                        // need a backup host for this bucket -- remove the first
                        // entry from 'adjustedTestServers', which is most favored
                        AdjustedTestServer backupHost = adjustedTestServers.first();
                        adjustedTestServers.remove(backupHost);

                        // update add this bucket to the list
                        bucket.setPrimaryBackup(backupHost.server);

                        // update counts in 'AdjustedTestServer'
                        backupHost.bucketCount += 1;
                        backupHost.primaryBackupBucketCount += 1;

                        // place it back in the table, possibly in a new position
                        // (it's attributes have changed)
                        adjustedTestServers.add(backupHost);
                    }
                }

                // TBD: Is additional rebalancing needed?
            }
        }

        /**
         * Allocate and rebalance the secondary backups.
         */
        void rebalanceSecondaryBackups() {
            for (TestServer failedServer : testServers.values()) {
                /*
                 * to allocate secondary backups for this server,
                 * simulate a failure, and balance the backup hosts
                 */

                // get siteSocketAddress from server
                InetSocketAddress siteSocketAddress = failedServer.siteSocketAddress;

                // populate a 'TreeSet' of 'AdjustedTestServer' instances based
                // the failure of 'failedServer'
                TreeSet<AdjustedTestServer> adjustedTestServers =
                    new TreeSet<>();
                for (TestServer server : testServers.values()) {
                    if (server == failedServer
                            || Objects.equals(siteSocketAddress,
                                              server.siteSocketAddress)) {
                        continue;
                    }
                    adjustedTestServers.add(new AdjustedTestServer(server, failedServer));
                }

                if (adjustedTestServers.isEmpty()) {
                    // this is presumably the only server -- there is no other server
                    // to act as secondary backup, and no rebalancing can occur
                    continue;
                }

                // we need a backup host for each bucket
                for (TestBucket bucket : failedServer.buckets) {
                    if (bucket.secondaryBackup == null
                            || bucket.secondaryBackup == nullServer) {
                        // need a backup host for this bucket -- remove the first
                        // entry from 'adjustedTestServers', which is most favored
                        AdjustedTestServer backupHost = adjustedTestServers.first();
                        adjustedTestServers.remove(backupHost);

                        // update add this bucket to the list
                        bucket.setSecondaryBackup(backupHost.server);

                        // update counts in 'AdjustedTestServer'
                        backupHost.bucketCount += 1;
                        backupHost.secondaryBackupBucketCount += 1;

                        // place it back in the table, possibly in a new position
                        // (it's attributes have changed)
                        adjustedTestServers.add(backupHost);
                    }
                }

                // TBD: Is additional rebalancing needed?
            }
        }

        /**
         * Generate a message with all of the bucket updates, process it locally,
         * and send it to all servers in the "Notify List".
         */
        void generateBucketMessage() throws IOException {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);

            // go through the entire 'buckets' table
            for (int i = 0; i < buckets.length; i += 1) {
                // fetch the 'TestBucket' associated with index 'i'
                TestBucket testBucket = buckets[i];

                /*
                 * Get the UUID of the owner, primary backup, and secondary backup
                 * for this bucket. If the associated value does not exist, 'null'
                 * is used.
                 */
                UUID newOwner = null;
                UUID newPrimary = null;
                UUID newSecondary = null;

                if (testBucket.owner != nullServer && testBucket.owner != null) {
                    newOwner = testBucket.owner.uuid;
                }
                if (testBucket.primaryBackup != nullServer
                        && testBucket.primaryBackup != null) {
                    newPrimary = testBucket.primaryBackup.uuid;
                }
                if (testBucket.secondaryBackup != nullServer
                        && testBucket.secondaryBackup != null) {
                    newSecondary = testBucket.secondaryBackup.uuid;
                }

                // write bucket number
                dos.writeShort(i);

                // 'owner' field
                writeOwner(dos, newOwner);

                // 'primaryBackup' field
                writePrimary(dos, newPrimary);

                // 'secondaryBackup' field
                writeSecondary(dos, newSecondary);

                dos.writeByte(END_OF_PARAMETERS_TAG);
            }

            // get the unencoded 'packet'
            final byte[] packet = bos.toByteArray();

            // create an 'Entity' containing the encoded packet
            final Entity<String> entity =
                Entity.entity(new String(Base64.getEncoder().encode(packet),
                    StandardCharsets.UTF_8), MediaType.APPLICATION_OCTET_STREAM_TYPE);
            /*
             * This method is running within the 'MainLoop' thread.
             */
            Runnable task = () -> {
                try {
                    /*
                     * update the buckets on this host,
                     * which is presumably the lead server.
                     */
                    Bucket.updateBucketInternal(packet);
                } catch (Exception e) {
                    logger.error("Exception updating buckets", e);
                }

                // send a message to all servers on the notify list
                for (Server server : Server.getNotifyList()) {
                    server.post("bucket/update", entity);
                }
            };
            MainLoop.queueWork(task);
        }

        private void writeOwner(DataOutputStream dos, UUID newOwner) throws IOException {
            if (newOwner != null) {
                dos.writeByte(OWNER_UPDATE);
                Util.writeUuid(dos, newOwner);
            } else {
                dos.writeByte(OWNER_NULL);
            }
        }

        private void writePrimary(DataOutputStream dos, UUID newPrimary) throws IOException {
            if (newPrimary != null) {
                dos.writeByte(PRIMARY_BACKUP_UPDATE);
                Util.writeUuid(dos, newPrimary);
            } else {
                dos.writeByte(PRIMARY_BACKUP_NULL);
            }
        }

        private void writeSecondary(DataOutputStream dos, UUID newSecondary) throws IOException {
            if (newSecondary != null) {
                dos.writeByte(SECONDARY_BACKUP_UPDATE);
                Util.writeUuid(dos, newSecondary);
            } else {
                dos.writeByte(SECONDARY_BACKUP_NULL);
            }
        }

        /**
         * Supports the '/cmd/dumpBuckets' REST message -- this isn't part of
         * a 'rebalance' operation, but it turned out to be a convenient way
         * to dump out the bucket table.
         *
         * @param out the output stream
         */
        private void dumpBucketsInternal(PrintStream out) {
            // xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx xxxxxxxxx *
            // UUID                                 Type      Buckets
            String format = "%-36s %-9s %5s %s\n";

            int totalOwner = 0;
            int totalPrimary = 0;
            int totalSecondary = 0;

            out.printf(format, "UUID", "Type", "Count", "Buckets");
            out.printf(format, "----", "----", "-----", "-------");
            for (TestServer ts : testServers.values()) {
                // dump out 'owned' bucket information
                if (ts.buckets.isEmpty()) {
                    // no buckets owned by this server
                    out.printf(format, ts.uuid, OWNED_STR, 0, "");
                } else {
                    // dump out primary buckets information
                    totalOwner +=
                        dumpBucketsSegment(out, format, ts.buckets, ts.uuid.toString(), OWNED_STR);
                }
                // optionally dump out primary buckets information
                totalPrimary +=
                    dumpBucketsSegment(out, format, ts.primaryBackupBuckets, "", "Primary");
                // optionally dump out secondary buckets information
                totalSecondary +=
                    dumpBucketsSegment(out, format, ts.secondaryBackupBuckets, "", "Secondary");
            }

            if (!nullServer.buckets.isEmpty()
                    || !nullServer.primaryBackupBuckets.isEmpty()
                    || !nullServer.secondaryBackupBuckets.isEmpty()) {
                /*
                 * There are some owned, primary, or secondary buckets that are
                 * unassigned. It is displayed in a manner similar to buckets that
                 * do have a server, but the UUID field is marked as 'UNASSIGNED'
                 * in the first line of the display.
                 */
                String uuidField = "UNASSIGNED";

                // optionally dump out unassigned owned buckets information
                if (dumpBucketsSegment(out, format, nullServer.buckets,
                                       uuidField, OWNED_STR) != 0) {
                    uuidField = "";
                }
                // optionally dump out unassigned primary backup buckets information
                if (dumpBucketsSegment(out, format, nullServer.primaryBackupBuckets,
                                       uuidField, "Primary") != 0) {
                    uuidField = "";
                }
                // optionally dump out unassigned secondary backup buckets information
                dumpBucketsSegment(out, format, nullServer.secondaryBackupBuckets,
                                   uuidField, "Secondary");
            }
            out.println("\nTotal assigned: owner = " + totalOwner
                        + ", primary = " + totalPrimary
                        + ", secondary = " + totalSecondary);
        }

        /**
         * Supports the 'dumpBucketsInternal' command, and indirectly, the
         * '/cmd/dumpBuckets' REST message. It formats one segment of bucket data
         * (owned, primary backup, or secondary backup), and dumps out the
         * associated bucket data in segments of 8. Note: if the size of 'buckets'
         * is 0, nothing is displayed.
         *
         * @param out the output stream
         * @param format the message format string
         * @param buckets the entire set of buckets to be displayed
         * @param uuid string to display under the 'UUID' header
         * @param segmentDescription string to display under the 'Type' header
         * @return the size of the 'buckets' set
         */
        private static int dumpBucketsSegment(
            PrintStream out, String format, TreeSet<TestBucket> buckets,
            String uuid, String segmentDescription) {

            int size = buckets.size();
            if (size != 0) {
                // generate a linked list of the bucket data to display
                LinkedList<String> data = new LinkedList<>();
                StringBuilder sb = new StringBuilder();
                int count = 8;

                for (TestBucket bucket : buckets) {
                    if (sb.length() != 0) {
                        // this is not the first bucket in the line --
                        // prepend a space
                        sb.append(' ');
                    }

                    // add the bucket number
                    sb.append(String.format("%4s", bucket.index));
                    count -= 1;
                    if (count <= 0) {
                        // filled up a row --
                        // add it to the list, and start a new line
                        data.add(sb.toString());
                        sb = new StringBuilder();
                        count = 8;
                    }
                }
                if (sb.length() != 0) {
                    // there is a partial line remaining -- add it to the list
                    data.add(sb.toString());
                }

                /*
                 * The first line displayed includes the UUID and size information,
                 * and the first line of bucket data (owned, primary, or secondary).
                 * The remaining lines of bucket data are displayed alone,
                 * without any UUID or size information.
                 */
                out.printf(format, uuid, segmentDescription, buckets.size(),
                           data.removeFirst());
                while (!data.isEmpty()) {
                    out.printf(format, "", "", "", data.removeFirst());
                }
            }
            return size;
        }
    }

    /* ============================================================ */

    /**
     * This interface is an abstraction for all messages that are routed
     * through buckets. It exists, so that messages may be queued while
     * bucket migration is taking place, and it makes it possible to support
     * multiple types of messages (routed UEB/DMAAP messages, or lock messages)
     */
    public static interface Message {
        /**
         * Process the current message -- this may mean delivering it locally,
         * or forwarding it.
         */
        public void process();

        /**
         * Send the message to another host for processing.
         *
         * @param server the destination host (although it could end up being
         *     forwarded again)
         * @param bucketNumber the bucket number determined by extracting the
         *     current message's keyword
         */
        public void sendToServer(Server server, int bucketNumber);
    }

    /* ============================================================ */

    /**
     * This interface implements a type of backup; for example, there is one
     * for backing up Drools objects within sessions, and another for
     * backing up lock data.
     */
    public static interface Backup {
        /**
         * This method is called to add a 'Backup' instance to the registered list.
         *
         * @param backup an object implementing the 'Backup' interface
         */
        public static void register(Backup backup) {
            synchronized (backupList) {
                if (!backupList.contains(backup)) {
                    backupList.add(backup);
                }
            }
        }

        /**
         * Generate Serializable backup data for the specified bucket.
         *
         * @param bucketNumber the bucket number to back up
         * @return a Serializable object containing backkup data
         */
        public Restore generate(int bucketNumber);
    }

    /* ============================================================ */

    /**
     * Objects implementing this interface may be serialized, and restored
     * on a different host.
     */
    public static interface Restore extends Serializable {
        /**
         * Restore from deserialized data.
         *
         * @param bucketNumber the bucket number being restored
         */
        void restore(int bucketNumber);
    }

    /* ============================================================ */

    /**
     * This interface corresponds to a transient state within a Bucket.
     */
    private interface State {
        /**
         * This method allows state-specific handling of the
         * 'Bucket.forward()' methods
         *
         * @param message the message to be forwarded/processed
         * @return a value of 'true' indicates the message has been "handled"
         *      (forwarded or queued), and 'false' indicates it has not, and needs
         *      to be handled locally.
         */
        boolean forward(Message message);

        /**
         * This method indicates that the current server is the new owner
         * of the current bucket.
         */
        void newOwner();

        /**
         * This method indicates that serialized data has been received,
         * presumably from the old owner of the bucket. The data could correspond
         * to Drools objects within sessions, as well as global locks.
         *
         * @param data serialized data associated with this bucket (at present,
         *      this is assumed to be complete, all within a single message)
         */
        void bulkSerializedData(byte[] data);
    }

    /* ============================================================ */

    /**
     * Each state instance is associated with a bucket, and is used when
     * that bucket is in a transient state where it is the new owner of a
     * bucket, or is presumed to be the new owner, based upon other events
     * that have occurred.
     */
    private class NewOwner extends Thread implements State {
        /*
         * this value is 'true' if we have explicitly received a 'newOwner'
         * indication, and 'false' if there was another trigger for entering this
         * transient state (e.g. receiving serialized data)
         */
        boolean confirmed;

        // when 'System.currentTimeMillis()' reaches this value, we time out
        long endTime;

        // If not 'null', we are queueing messages for this bucket
        // otherwise, we are sending them through.
        Queue<Message> messages = new ConcurrentLinkedQueue<>();

        // this is used to signal the thread that we have data available
        CountDownLatch dataAvailable = new CountDownLatch(1);

        // this is the data
        byte[] data = null;

        // this is the old owner of the bucket
        Server oldOwner;

        /**
         * Constructor - a transient state, where we are expecting to receive
         * bulk data from the old owner.
         *
         * @param confirmed 'true' if we were explicitly notified that we
         *      are the new owner of the bucket, 'false' if not
         */
        NewOwner(boolean confirmed, Server oldOwner) {
            super("New Owner for Bucket " + index);
            this.confirmed = confirmed;
            this.oldOwner = oldOwner;
            if (oldOwner == null) {
                // we aren't expecting any data -- this is indicated by 0-length data
                bulkSerializedData(new byte[0]);
            }
            endTime = System.currentTimeMillis()
                      + (confirmed ? confirmedTimeout : unconfirmedTimeout);
            start();
        }

        /**
         * Return the 'confirmed' indicator.
         *
         * @return the 'confirmed' indicator
         */
        private boolean getConfirmed() {
            synchronized (Bucket.this) {
                return confirmed;
            }
        }

        /**
         * This returns the timeout delay, which will always be less than or
         * equal to 1 second. This allows us to periodically check whether the
         * old server is still active.
         *
         * @return the timeout delay, which is the difference between the
         *      'endTime' value and the current time or 1 second
         *      (whichever is less)
         */
        private long getTimeout() {
            long lclEndTime;
            synchronized (Bucket.this) {
                lclEndTime = endTime;
            }
            return Math.min(lclEndTime - System.currentTimeMillis(), 1000L);
        }

        /**
         * Return the current value of the 'data' field.
         *
         * @return the current value of the 'data' field
         */
        private byte[] getData() {
            synchronized (Bucket.this) {
                return data;
            }
        }

        /* ******************* */
        /* 'State' interface   */
        /* ******************* */

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean forward(Message message) {
            // the caller of this method is synchronized on 'Bucket.this'
            if (messages != null && Thread.currentThread() != this) {
                // just queue the message
                messages.add(message);
                return true;
            } else {
                /*
                 * Either:
                 *
                 * 1) We are in a grace period, where 'state' is still set, but
                 *    we are no longer forwarding messages.
                 * 2) We are calling 'message.process()' from this thread
                 *    in the 'finally' block of 'NewOwner.run()'.
                 *
                 * In either case, messages should be processed locally.
                 */
                return false;
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void newOwner() {
            // the caller of this method is synchronized on 'Bucket.this'
            if (!confirmed) {
                confirmed = true;
                endTime += (confirmedTimeout - unconfirmedTimeout);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void bulkSerializedData(byte[] data) {
            // the caller of this method is synchronized on 'Bucket.this'
            if (this.data == null) {
                this.data = data;
                dataAvailable.countDown();
            }
        }

        /* ******************** */
        /* 'Thread' interface   */
        /* ******************** */

        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
            logger.info("{}: 'run' method invoked", this);
            try {
                byte[] lclData;
                long delay;

                while ((lclData = getData()) == null
                        && oldOwner.isActive()
                        && (delay = getTimeout()) > 0) {
                    // ignore return value -- 'data' will indicate the result
                    if (!dataAvailable.await(delay, TimeUnit.MILLISECONDS)) {
                        logger.error("CountDownLatch await time reached");
                    }
                }
                if (lclData == null) {
                    // no data available -- log an error, and abort
                    if (getConfirmed()) {
                        // we never received the data, but we are the new owner
                        logger.error("{}: never received session data", this);
                    } else {
                        /*
                         * no data received, and it was never confirmed --
                         * assume that the forwarded message that triggered this was
                         * erroneus
                         */
                        logger.error("{}: no confirmation or data received -- aborting", this);
                        return;
                    }
                } else {
                    logger.info("{}: {} bytes of data available",
                                this, lclData.length);
                }

                // if we reach this point, this server is the new owner
                if (lclData == null || lclData.length == 0) {
                    // see if any features can do the restore
                    for (ServerPoolApi feature : ServerPoolApi.impl.getList()) {
                        feature.restoreBucket(Bucket.this);
                    }
                } else {
                    // deserialize data
                    Object obj = Util.deserialize(lclData);
                    restoreBucketData(obj);
                }
            } catch (Exception e) {
                logger.error("Exception in {}", this, e);
            } finally {
                runCleanup();
            }
        }

        private void runCleanup() {
            /*
             * cleanly leave state -- we want to make sure that messages
             * are processed in order, so the queue needs to remain until
             * it is empty
             */
            logger.info("{}: entering cleanup state", this);
            for ( ; ; ) {
                Message message = messages.poll();
                if (message == null) {
                    // no messages left, but this could change
                    synchronized (Bucket.this) {
                        message = messages.poll();
                        if (message == null) {
                            // no messages left
                            noMoreMessages();
                            break;
                        }
                    }
                }
                // this doesn't work -- it ends up right back in the queue
                // if 'messages' is defined
                message.process();
            }
            if (messages == null) {
                // this indicates we need to enter a grace period before cleanup,
                sleepBeforeCleanup();
            }
            logger.info("{}: exiting cleanup state", this);
        }

        private void noMoreMessages() {
            if (state == this) {
                if (owner == Server.getThisServer()) {
                    // we can now exit the state
                    state = null;
                    stateChanged();
                } else {
                    /*
                     * We need a grace period before we can
                     * remove the 'state' value (this can happen
                     * if we receive and process the bulk data
                     * before receiving official confirmation
                     * that we are owner of the bucket.
                     */
                    messages = null;
                }
            }
        }

        private void sleepBeforeCleanup() {
            try {
                logger.info("{}: entering grace period before terminating",
                            this);
                Thread.sleep(unconfirmedGracePeriod);
            } catch (InterruptedException e) {
                // we are exiting in any case
                Thread.currentThread().interrupt();
            } finally {
                synchronized (Bucket.this) {
                    // Do we need to confirm that we really are the owner?
                    // What does it mean if we are not?
                    if (state == this) {
                        state = null;
                        stateChanged();
                    }
                }
            }
        }

        /**
         * Return a useful value to display in log messages.
         *
         * @return a useful value to display in log messages
         */
        public String toString() {
            return "Bucket.NewOwner(" + index + ")";
        }

        /**
         * Restore bucket data.
         *
         * @param obj deserialized bucket data
         */
        private void restoreBucketData(Object obj) {
            if (obj instanceof List) {
                for (Object entry : (List<?>) obj) {
                    if (entry instanceof Restore) {
                        // entry-specific 'restore' operation
                        ((Restore) entry).restore(Bucket.this.index);
                    } else {
                        logger.error("{}: Expected '{}' but got '{}'",
                                     this, Restore.class.getName(),
                                     entry.getClass().getName());
                    }
                }
            } else {
                logger.error("{}: expected 'List' but got '{}'",
                             this, obj.getClass().getName());
            }
        }
    }


    /* ============================================================ */

    /**
     * Each state instance is associated with a bucket, and is used when
     * that bucket is in a transient state where it is the old owner of
     * a bucket, and the data is being transferred to the new owner.
     */
    private class OldOwner extends Thread implements State {
        Server newOwner;

        OldOwner(Server newOwner) {
            super("Old Owner for Bucket " + index);
            this.newOwner = newOwner;
            start();
        }

        /* ******************* */
        /* 'State' interface   */
        /* ******************* */

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean forward(Message message) {
            // forward message to new owner
            message.sendToServer(newOwner, index);
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void newOwner() {
            // shouldn't happen -- just log an error
            logger.error("{}: 'newOwner()' shouldn't be called in this state", this);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void bulkSerializedData(byte[] data) {
            // shouldn't happen -- just log an error
            logger.error("{}: 'bulkSerializedData()' shouldn't be called in this state", this);
        }

        /* ******************** */
        /* 'Thread' interface   */
        /* ******************** */

        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
            logger.info("{}: 'run' method invoked", this);
            try {
                // go through all of the entries in the list, collecting restore data
                List<Restore> restoreData = new LinkedList<>();
                for (Backup backup : backupList) {
                    Restore restore = backup.generate(index);
                    if (restore != null) {
                        restoreData.add(restore);
                    }
                }

                // serialize all of the objects,
                // and send what we have to the new owner
                Entity<String> entity = Entity.entity(
                    new String(Base64.getEncoder().encode(Util.serialize(restoreData))),
                    MediaType.APPLICATION_OCTET_STREAM_TYPE);
                newOwner.post("bucket/sessionData", entity, new Server.PostResponse() {
                    @Override
                    public WebTarget webTarget(WebTarget webTarget) {
                        return webTarget
                               .queryParam(QP_BUCKET, index)
                               .queryParam(QP_DEST, newOwner.getUuid())
                               .queryParam(QP_TTL, timeToLive);
                    }

                    @Override
                    public void response(Response response) {
                        logger.info("/bucket/sessionData response code = {}",
                                    response.getStatus());
                    }
                });
            } catch (Exception e) {
                logger.error("Exception in {}", this, e);
            } finally {
                synchronized (Bucket.this) {
                    // restore the state
                    if (state == this) {
                        state = null;
                        stateChanged();
                    }
                }
            }
        }

        /**
         * Return a useful value to display in log messages.
         *
         * @return a useful value to display in log messages
         */
        public String toString() {
            return "Bucket.OldOwner(" + index + ")";
        }
    }
}
