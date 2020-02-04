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

import java.io.PrintStream;
import java.util.IdentityHashMap;

import org.onap.policy.drools.serverpooltest.BucketWrapper;
import org.onap.policy.drools.serverpooltest.ServerWrapper;

/**
 * This class implements the 'BucketWrapper' interface. There is one
 * 'BucketWrapperImpl' class for each simulated host.
 */
public class BucketWrapperImpl implements BucketWrapper {
    // this maps a 'Bucket' instance on this host to an associated wrapper
    private static IdentityHashMap<Bucket, BucketWrapperImpl> bucketToWrapper =
        new IdentityHashMap<>();

    // this is the 'Bucket' instance associated with the wrapper
    private Bucket bucket;

    /**
     * This method maps a 'Bucket' instance into a 'BucketWrapperImpl'
     * instance. The goal is to have only a single 'BucketWrapperImpl' instance
     * for each 'Bucket' instance, so that testing for identity will work
     * as expected.
     *
     * @param bucket the 'Bucket' instance
     * @return the associated 'BucketWrapperImpl' instance
     */
    static synchronized BucketWrapperImpl getWrapper(Bucket bucket) {
        if (bucket == null) {
            return null;
        }
        BucketWrapperImpl rval = bucketToWrapper.get(bucket);
        if (rval == null) {
            // a matching entry does not yet exist -- create one
            rval = new BucketWrapperImpl(bucket);
            bucketToWrapper.put(bucket, rval);
        }
        return rval;
    }

    /**
     * Constructor - initialize the 'bucket' field.
     */
    BucketWrapperImpl(Bucket bucket) {
        this.bucket = bucket;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getBucketNumber() {
        return bucket.getBucketNumber();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServerWrapper getOwner() {
        return ServerWrapperImpl.getWrapper(bucket.getOwner());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServerWrapper getPrimaryBackup() {
        return ServerWrapperImpl.getWrapper(bucket.getPrimaryBackup());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServerWrapper getSecondaryBackup() {
        return ServerWrapperImpl.getWrapper(bucket.getSecondaryBackup());
    }

    /* ============================================================ */

    /**
     * This class implements the 'BucketWrapper.Static' interface. There is
     * one 'BucketWrapperImpl.Static' class, and one instance for each
     * simulated host
     */
    public static class Static implements BucketWrapper.Static {
        /**
         * {@inheritDoc}
         */
        @Override
        public int getBucketCount() {
            return Bucket.BUCKETCOUNT;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int bucketNumber(String value) {
            return Bucket.bucketNumber(value);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ServerWrapper bucketToServer(int bucketNumber) {
            return ServerWrapperImpl.getWrapper(Bucket.bucketToServer(bucketNumber));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public BucketWrapper getBucket(int bucketNumber) {
            return getWrapper(Bucket.getBucket(bucketNumber));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isKeyOnThisServer(String key) {
            return Bucket.isKeyOnThisServer(key);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void moveBucket(PrintStream out, int bucketNumber, String newHostUuid) {
            ClassLoader save = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(
                    BucketWrapperImpl.class.getClassLoader());
                Bucket.moveBucket(out, bucketNumber, newHostUuid);
            } finally {
                Thread.currentThread().setContextClassLoader(save);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void dumpAdjuncts(PrintStream out) {
            Bucket.dumpAdjuncts(out);
        }
    }
}
