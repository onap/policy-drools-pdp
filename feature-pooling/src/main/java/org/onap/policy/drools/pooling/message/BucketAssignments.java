/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.pooling.message;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.onap.policy.drools.pooling.PoolingFeatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Bucket assignments, which is simply an array of host names.
 */
public class BucketAssignments {

    @JsonIgnore
    private static final Logger logger = LoggerFactory.getLogger(BucketAssignments.class);

    /**
     * Number of buckets.
     */
    public static final int MAX_BUCKETS = 1024;

    /**
     * Identifies the host serving a particular bucket.
     */
    private String[] hostArray = null;

    /**
     * 
     */
    public BucketAssignments() {

    }

    /**
     * 
     * @param hostArray maps a bucket number (i.e., array index) to a host. All
     *        values must be non-null
     */
    public BucketAssignments(String[] hostArray) {
        this.hostArray = hostArray;
    }

    public String[] getHostArray() {
        return hostArray;
    }

    public void setHostArray(String[] hostArray) {
        this.hostArray = hostArray;
    }

    /**
     * Gets the leader, which is the host with the minimum UUID.
     * 
     * @return the assignment leader
     */
    @JsonIgnore
    public String getLeader() {
        if (hostArray == null) {
            return null;
        }

        String leader = null;

        for (String host : hostArray) {
            if (host != null && (leader == null || host.compareTo(leader) < 0)) {
                leader = host;
            }
        }

        return leader;

    }

    /**
     * Determines if a host has an assignment.
     * 
     * @param host host to be checked
     * @return {@code true} if the host has an assignment, {@code false}
     *         otherwise
     */
    @JsonIgnore
    public boolean hasAssignment(String host) {
        if (hostArray == null) {
            return false;
        }

        for (String host2 : hostArray) {
            if (host.equals(host2)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Gets all of the hosts that have an assignment.
     * 
     * @return all of the hosts that have an assignment
     */
    @JsonIgnore
    public Set<String> getAllHosts() {
        Set<String> set = new HashSet<>();
        if (hostArray == null) {
            return set;
        }

        for (String host : hostArray) {
            if (host != null) {
                set.add(host);
            }
        }

        return set;
    }

    /**
     * Gets the host assigned to a given bucket.
     * 
     * @param bucket bucket number
     * @return the assigned host, or {@code null} if the bucket has no assigned
     *         host
     */
    @JsonIgnore
    public String getAssignedHost(int bucket) {
        if (hostArray == null) {
            logger.error("no buckets have been assigned");
            return null;
        }

        if (bucket < 0 || bucket >= hostArray.length) {
            logger.error("invalid bucket number {} maximum {}", bucket, hostArray.length);
            return null;
        }

        return hostArray[bucket];
    }

    // TODO add final

    /**
     * Gets the number of buckets.
     * 
     * @return the number of buckets
     */
    @JsonIgnore
    public int size() {
        return (hostArray != null ? hostArray.length : 0);
    }

    /**
     * Checks the validity of the assignments, verifying that all buckets have
     * been assigned to a host.
     * 
     * @throws PoolingFeatureException if the assignments are invalid
     */
    @JsonIgnore
    public void checkValidity() throws PoolingFeatureException {
        if (hostArray == null || hostArray.length == 0) {
            throw new PoolingFeatureException("missing hosts in message bucket assignments");
        }

        if (hostArray.length > MAX_BUCKETS) {
            throw new PoolingFeatureException("too many hosts in message bucket assignments");
        }

        for (int x = 0; x < hostArray.length; ++x) {
            if (hostArray[x] == null) {
                throw new PoolingFeatureException("bucket " + x + " has no assignment");
            }
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(hostArray);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        BucketAssignments other = (BucketAssignments) obj;
        if (!Arrays.equals(hostArray, other.hostArray))
            return false;
        return true;
    }
}
