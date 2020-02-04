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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is primarily used to do a 'Rebalance' operation on the
 * lead server, which is then distributed to all of the other servers.
 * Part of it is also useful for implementing the /cmd/dumpBuckets
 * REST message handler.
 */
public class Rebalance {
    private static Logger logger = LoggerFactory.getLogger(Rebalance.class);
    // this table resembles the 'Bucket.indexToBucket' table
    int indexToBucketLength = Bucket.getIndexToBucketLength();
    TestBucket[] buckets = new TestBucket[indexToBucketLength];
    Bucket[] indexToBucket = Bucket.getIndexToBucket();

    // this table resembles the 'Server.servers' table
    TreeMap<UUID,TestServer> testServers = new TreeMap<>(Util.uuidComparator);

    /**
     * this is a special server instance, which is allocated any
     *  owned, primary, or secondary buckets that haven't been allocated to
     *  any of the real servers.
     */
    TestServer nullServer = new TestServer(null, null);
    
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
     * Copy all of the bucket data in the 'buckets' table, and also return
     * a copy of the 'Server.servers' table.
     */
    void copyData() {
        // will contain a copy of the 'Bucket' table
        final Bucket[] bucketSnapshot = new Bucket[indexToBucketLength];
        /**
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
            for (int i = 0 ; i < indexToBucketLength; i += 1) {
                // makes a snapshot of the bucket information
                Bucket bucket = indexToBucket[i];

                Bucket tmpBucket = new Bucket(i);
                tmpBucket.setOwner(bucket.getOwner());
                tmpBucket.setPrimaryBackup(bucket.getPrimaryBackup());
                tmpBucket.setSecondaryBackup(bucket.getSecondaryBackup());
                bucketSnapshot[i] = tmpBucket;
            }

            /**
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
        FutureTask<Integer> ft = new FutureTask(callable);
        MainLoop.queueWork(ft);
        try {
            ft.get(60, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            logger.error("Exception in Rebalance.copyData", e);
            return;
        }

        /**
         * Now, create a 'TestBucket' table that mirrors the 'Bucket' table.
         * Unlike the standard 'Bucket' and 'Server' tables, the 'TestServer'
         * entries contain links referring back to the 'TestBucket' entries.
         * This information is useful when rebalancing.
         */
        for (Bucket bucket : bucketSnapshot) {
            int index = bucket.getBucketNumber();
            TestBucket testBucket = new TestBucket(index);

            // populate the 'owner' field
            if (bucket.getOwner() != null) {
                testBucket.setOwner(testServers.get(bucket.getOwner().getUuid()));
            } else {
                testBucket.setOwner(nullServer);
            }

            // populate the 'primaryBackup' field
            if (bucket.getPrimaryBackup() != null) {
                testBucket.setPrimaryBackup(
                    testServers.get(bucket.getPrimaryBackup().getUuid()));
            } else {
                testBucket.setPrimaryBackup(nullServer);
            }

            // populate the 'secondaryBackup' field
            if (bucket.getSecondaryBackup() != null) {
                testBucket.setSecondaryBackup(
                    testServers.get(bucket.getSecondaryBackup().getUuid()));
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
        /**
         * the following 'Comparator' is used to control the order of the
         * 'needBuckets' TreeSet: those with the fewest buckets allocated are
         * at the head of the list.
         */
        Comparator<TestServer> bucketCount = new Comparator<TestServer>() {
            @Override
            public int compare(TestServer s1, TestServer s2) {
                int rval = s1.buckets.size() - s2.buckets.size();
                if (rval == 0) {
                    rval = Util.uuidComparator.compare(s1.uuid, s2.uuid);
                }
                return rval;
            }
        };

        /**
         * Sort servers according to the order in which they can
         * take on ownership of buckets.
         */
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

            /**
             * place it back in the list, possibly in a new position
             * (it's attributes have changed).
             */
            needBuckets.add(ts);
        }
        nullServer.buckets.clear();

        /**
         * there may still be rebalancing needed -- no host should contain
         * 2 or more buckets more than any other host
         */
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
            if (bucket.owner == null) {
                return; 
            }
            InetSocketAddress siteSocketAddress =
                bucket.owner.siteSocketAddress;
            TestServer primaryBackup = bucket.primaryBackup;
            TestServer secondaryBackup = bucket.secondaryBackup;

            validateSiteOwner(bucket, siteSocketAddress,
                primaryBackup, secondaryBackup);
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
            /**
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
            /**
             * to allocate primary backups for this server,
             * simulate a failure, and balance the backup hosts.
             */

            // get siteSocketAddress from server
            InetSocketAddress siteSocketAddress = failedServer.siteSocketAddress;

            /**
             * populate a 'TreeSet' of 'AdjustedTestServer' instances based
             * the failure of 'failedServer'.
             */
            TreeSet<AdjustedTestServer> adjustedTestServers =
                new TreeSet<AdjustedTestServer>();
            for (TestServer server : testServers.values()) {
                if (server == failedServer
                        || !Objects.equals(siteSocketAddress,
                                           server.siteSocketAddress)) {
                    continue;
                }
                adjustedTestServers.add(new AdjustedTestServer(server, failedServer));
            }

            if (adjustedTestServers.isEmpty()) {
                /**
                 * this is presumably the only server -- there is no other server
                 * to act as primary backup, and no rebalancing can occur.
                 */
                continue;
            }

            // we need a backup host for each bucket
            for (TestBucket bucket : failedServer.buckets) {
                if (bucket.primaryBackup == null
                        || bucket.primaryBackup == nullServer) {
                    /**
                     * need a backup host for this bucket -- remove the first
                     * entry from 'adjustedTestServers', which is most favored.
                     */
                    AdjustedTestServer backupHost = adjustedTestServers.first();
                    adjustedTestServers.remove(backupHost);

                    // update add this bucket to the list
                    bucket.setPrimaryBackup(backupHost.server);

                    // update counts in 'AdjustedTestServer'
                    backupHost.bucketCount += 1;
                    backupHost.primaryBackupBucketCount += 1;

                    /**
                     * place it back in the table, possibly in a new position
                     * (it's attributes have changed).
                     */
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
            /**
             * to allocate secondary backups for this server,
             * simulate a failure, and balance the backup hosts.
             */

            // get siteSocketAddress from server
            InetSocketAddress siteSocketAddress = failedServer.siteSocketAddress;

            /**
             * populate a 'TreeSet' of 'AdjustedTestServer' instances based
             * the failure of 'failedServer'.
             */
            TreeSet<AdjustedTestServer> adjustedTestServers =
                new TreeSet<AdjustedTestServer>();
            for (TestServer server : testServers.values()) {
                if (server == failedServer
                        || Objects.equals(siteSocketAddress,
                                          server.siteSocketAddress)) {
                    continue;
                }
                adjustedTestServers.add(new AdjustedTestServer(server, failedServer));
            }

            if (adjustedTestServers.isEmpty()) {
                /**
                 * this is presumably the only server -- there is no other server
                 * to act as secondary backup, and no rebalancing can occur.
                 */
                continue;
            }

            // we need a backup host for each bucket
            for (TestBucket bucket : failedServer.buckets) {
                if (bucket.secondaryBackup == null
                        || bucket.secondaryBackup == nullServer) {
                    /**
                     * need a backup host for this bucket -- remove the first
                     * entry from 'adjustedTestServers', which is most favored.
                     */
                    AdjustedTestServer backupHost = adjustedTestServers.first();
                    adjustedTestServers.remove(backupHost);

                    // update add this bucket to the list
                    bucket.setSecondaryBackup(backupHost.server);

                    // update counts in 'AdjustedTestServer'
                    backupHost.bucketCount += 1;
                    backupHost.secondaryBackupBucketCount += 1;

                    /**
                     * place it back in the table, possibly in a new position
                     * (it's attributes have changed).
                     */
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
        for (int i = 0 ; i < buckets.length ; i += 1) {
            // fetch the 'TestBucket' associated with index 'i'
            TestBucket testBucket = buckets[i];

            /**
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
            if (newOwner != null) {
                dos.writeByte(OWNER_UPDATE);
                Util.writeUuid(dos, newOwner);
            } else {
                dos.writeByte(OWNER_NULL);
            }

            // 'primaryBackup' field
            if (newPrimary != null) {
                dos.writeByte(PRIMARY_BACKUP_UPDATE);
                Util.writeUuid(dos, newPrimary);
            } else {
                dos.writeByte(PRIMARY_BACKUP_NULL);
            }

            // 'secondaryBackup' field
            if (newSecondary != null) {
                dos.writeByte(SECONDARY_BACKUP_UPDATE);
                Util.writeUuid(dos, newSecondary);
            } else {
                dos.writeByte(SECONDARY_BACKUP_NULL);
            }

            dos.writeByte(END_OF_PARAMETERS_TAG);
        }

        // get the unencoded 'packet'
        final byte[] packet = bos.toByteArray();

        // create an 'Entity' containing the encoded packet
        final Entity<String> entity =
            Entity.entity(new String(Base64.getEncoder().encode(packet), StandardCharsets.UTF_8),
                          MediaType.APPLICATION_OCTET_STREAM_TYPE);
        /**
         * This method is running within the 'MainLoop' thread.
         */
        Runnable task = () -> {
            try {
                /**
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
        MainLoop.queueWork(new Thread(task));
    }

    /**
     * Supports the '/cmd/dumpBuckets' REST message -- this isn't part of
     * a 'rebalance' operation, but it turned out to be a convenient way
     * to dump out the bucket table.
     *
     * @param out the output stream
     */
    protected void dumpBucketsInternal(PrintStream out) {
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
                out.printf(format, ts.uuid, "Owned", 0, "");
            } else {
                // dump out primary buckets information
                totalOwner +=
                    dumpBucketsSegment(out, format, ts.buckets, ts.uuid.toString(), "Owned");
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
            /**
             * There are some owned, primary, or secondary buckets that are
             * unassigned. It is displayed in a manner similar to buckets that
             * do have a server, but the UUID field is marked as 'UNASSIGNED'
             * in the first line of the display.
             */
            String uuidField = "UNASSIGNED";

            // optionally dump out unassigned owned buckets information
            if (dumpBucketsSegment(out, format, nullServer.buckets,
                                   uuidField, "Owned") != 0) {
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
            LinkedList<String> data = new LinkedList<String>();
            StringBuilder sb = new StringBuilder();
            int count = 8;

            for (TestBucket bucket : buckets) {
                if (sb.length() != 0) {
                    /**
                     * this is not the first bucket in the line --
                     * prepend a space
                     */
                    sb.append(' ');
                }

                // add the bucket number
                sb.append(String.format("%4s", bucket.index));
                if ((count -= 1) <= 0) {
                    /**
                     * filled up a row --
                     * add it to the list, and start a new line.
                     */
                    data.add(sb.toString());
                    sb = new StringBuilder();
                    count = 8;
                }
            }
            if (sb.length() != 0) {
                // there is a partial line remaining -- add it to the list
                data.add(sb.toString());
            }

            /**
             * The first line displayed includes the UUID and size information,
             *  and the first line of bucket data (owned, primary, or secondary).
             *  The remaining lines of bucket data are displayed alone,
             *  without any UUID or size information.
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
