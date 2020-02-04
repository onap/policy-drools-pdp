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

import java.net.InetSocketAddress;
import java.util.TreeSet;
import java.util.UUID;

import org.onap.policy.drools.serverpool.TestBucket;

/**
 * Instances of this class are created as part of the 'rebalance'
 * operation on the lead server, or as part of a 'dumpBuckets' operation
 * on any server.
 * Each instance of this class corresponds to a 'Server' instance.
 * Unlike the actual 'Server' instances, each 'TestServer' instance
 * contains back links to all of the buckets it is associated with.
 */
public class TestServer {
    /**
     * unique id for this server
     * (matches the one in the corresponding 'Server' entry).
     */
    UUID uuid;

    // site socket information (matches 'Server' entry)
    InetSocketAddress siteSocketAddress;

    /**
     * the set of all 'TestBucket' instances,
     * where this 'TestServer' is listed as 'owner'.
     */
    TreeSet<TestBucket> buckets = new TreeSet<>();

    /**
     *  the set of all 'TestBucket' instances,
     *  where this 'TestServer' is listed as 'primaryBackup'.
     */
    final TreeSet<TestBucket> primaryBackupBuckets = new TreeSet<>();

    /**
     * the set of all 'TestBucket' instances,
     *  where this 'TestServer' is listed as 'secondaryBackup'.
     */
    final TreeSet<TestBucket> secondaryBackupBuckets = new TreeSet<>();

    /**
     * Constructor.
     *
     * @param uuid uuid of this 'TestServer' instance
     * @param siteSocketAddress matches the value in the corresponding 'Server'.
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