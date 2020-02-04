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

/**
 * This class supports the 'rebalance' operation. Each instance is a wrapper
 * around a 'TestServer' instance, as it would be if another specific
 * server failed.
 */
public class AdjustedTestServer
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

        /**
         * need to adjust 'bucketCount' for the case where the current
         * host fails.
         */
        for (TestBucket bucket : server.primaryBackupBuckets) {
            if (bucket.owner == failedServer) {
                bucketCount += 1;
                // TBD: should 'primaryBackupBucketCount' be decremented?
            }
        }

        /**
         * need to adjust 'bucketCount' for the case where the current
         * host fails.
         */
        for (TestBucket bucket : server.secondaryBackupBuckets) {
            if (bucket.owner == failedServer) {
                bucketCount += 1;
                // TBD: should 'secondaryBackupBucketCount' be decremented?
            }
        }
    }

    /********************************************/
    /* Comparable<AdjustedTestServer> interface */
    /********************************************/

    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo(AdjustedTestServer other) {
        /**
         * Comparison order:
         * 1) minimal expected bucket count if current host fails
         *    (differences of 1 are treated as a match)
         * 2) minimal backup bucket count
         * 3) UUID order.
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
