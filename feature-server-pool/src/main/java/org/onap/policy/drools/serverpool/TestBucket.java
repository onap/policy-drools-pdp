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

import org.onap.policy.drools.serverpool.TestServer;

/**
 * Instances of this class are created as part of the 'rebalance'
 * operation on the lead server, or as part of a 'dumpBuckets' operation
 * on any server.
 * Each instance of this class corresponds to a 'Bucket' instance.
 */
public class TestBucket implements Comparable<TestBucket> {
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
                /**
                 * remove this bucket from the 'buckets' list of the
                 * old primary backup
                 */
                primaryBackup.primaryBackupBuckets.remove(this);
            }
            if (newPrimaryBackup != null) {
                /**
                 * add this bucket to the 'buckets' list of the
                 * new primary backup
                 */
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
                /**
                 * remove this bucket from the 'buckets' list of the
                 * old secondary backup
                 */
                secondaryBackup.secondaryBackupBuckets.remove(this);
            }
            if (newSecondaryBackup != null) {
                /**
                 * add this bucket to the 'buckets' list of the
                 * new secondary backup.
                 */
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