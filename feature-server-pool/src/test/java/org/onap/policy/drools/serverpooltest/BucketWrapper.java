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

package org.onap.policy.drools.serverpooltest;

import java.io.PrintStream;

/**
 * This class provides base classes for accessing the various 'Bucket'
 * classes. There is a separate copy of the 'Bucket' class for each
 * adapter, and this wrapper was created to give them a common interface.
 */
public interface BucketWrapper {
    /**
     * This calls the 'Bucket.getBucketNumber()' method
     *
     * @return the bucket number
     */
    public int getBucketNumber();

    /**
     * This calls the 'Bucket.getOwner()' method
     *
     * @return a 'ServerWrapper' instance that corresponds to the owner
     *     of the bucket ('null' if unassigned)
     */
    public ServerWrapper getOwner();

    /**
     * This calls the 'Bucket.getPrimaryBackup()' method
     *
     * @return a 'ServerWrapper' instance that corresponds to the primary backup
     *     host for the bucket ('null' if unassigned)
     */
    public ServerWrapper getPrimaryBackup();

    /**
     * This calls the 'Bucket.getPrimaryBackup()' method
     *
     * @return a 'ServerWrapper' instance that corresponds to the secondary
     *     backup host for the bucket ('null' if unassigned)
     */
    public ServerWrapper getSecondaryBackup();

    /* ============================================================ */

    /**
     * This class provides access to the static 'Bucket' methods. There are
     * multiple 'Bucket' classes (one for each 'Adapter'), and each has
     * a corresponding 'BucketWrapper.Static' instance. In other words, there
     * is one 'Bucket.Static' instance for each simulated host.
     */
    public interface Static {
        /**
         * This returns the value of 'Bucket.BUCKETCOUNT'
         *
         * @return the number of Bucket instances in the bucket table
         */
        public int getBucketCount();

        /**
         * This calls the static 'Bucket.bucketNumber(String)' method
         *
         * @param value the keyword to be converted
         * @return the bucket number
         */
        public int bucketNumber(String value);

        /**
         * This calls the static 'Bucket.bucketToServer(int)' method
         *
         * @param bucketNumber a bucket number in the range 0-1023
         * @return a 'ServerWrapper' for the server that currently handles the
         *     bucket, or 'null' if none is currently assigned
         */
        public ServerWrapper bucketToServer(int bucketNumber);

        /**
         * This calls the static 'Bucket.getBucket(int)' method
         *
         * @param bucketNumber a bucket number in the range 0-1023
         * @return A 'BucketWrapper' for the Bucket associated with
         *     this bucket number
         */
        public BucketWrapper getBucket(int bucketNumber);

        /**
         * This calls the static 'Bucket.isKeyOnThisServer(String)' method
         *
         * @param key the keyword to be hashed
         * @return 'true' if the associated bucket is assigned to this server,
         *     'false' if not
         */
        public boolean isKeyOnThisServer(String key);

        /**
         * This calls the static 'Bucket.moveBucket(PrintStream, int, String)'
         * method (the one associated with the '/cmd/moveBucket' REST call).
         *
         * @param out the 'PrintStream' to use for displaying information
         * @param bucketNumber the bucket number to be moved
         * @param newHostUuid the UUID of the destination host (if 'null', a
         *     destination host will be chosen at random)
         */
        public void moveBucket(PrintStream out, int bucketNumber, String newHostUuid);

        /**
         * This calls the static 'Bucket.dumpAdjuncts(PrintStream)' method
         * (the one associated with the '/cmd/dumpBucketAdjuncts' REST call).
         *
         * @param out the 'PrintStream' to use for displaying information
         */
        public void dumpAdjuncts(PrintStream out);
    }
}
