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

package org.onap.policy.drools.pooling.state;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.onap.policy.drools.pooling.PoolingManager;
import org.onap.policy.drools.pooling.message.BucketAssignments;
import org.onap.policy.drools.pooling.message.Leader;
import org.onap.policy.drools.pooling.message.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Any state in which events are being processed locally and forwarded, as appropriate.
 */
public class ProcessingState extends State {

    private static final Logger logger = LoggerFactory.getLogger(ProcessingState.class);

    /**
     * Current known leader, never {@code null}.
     */
    private String leader;

    /**
     * 
     * @param mgr
     * @param leader current known leader, which need not be the same as the assignment
     *        leader. Never {@code null}
     * @throws IllegalArgumentException if an argument is invalid
     */
    public ProcessingState(PoolingManager mgr, String leader) {
        super(mgr);

        if (leader == null) {
            throw new IllegalArgumentException("null leader");
        }

        BucketAssignments assignments = mgr.getAssignments();

        if (assignments != null) {
            String[] arr = assignments.getHostArray();
            if (arr != null && arr.length == 0) {
                throw new IllegalArgumentException("zero-length bucket assignments");
            }
        }

        this.leader = leader;
    }

    /**
     * Generates an Identification message and goes to the query state.
     */
    @Override
    public State process(Query msg) {
        logger.info("received Query message on topic {}", getTopic());
        publish(makeIdentification());
        return goQuery();
    }

    /**
     * Sets the assignments.
     * 
     * @param assignments new assignments, or {@code null}
     */
    protected final void setAssignments(BucketAssignments assignments) {
        if (assignments != null) {
            startDistributing(assignments);
        }
    }

    public String getLeader() {
        return leader;
    }

    /**
     * Sets the leader.
     * 
     * @param leader the new leader
     * @throws IllegalArgumentException if an argument is invalid
     */
    protected void setLeader(String leader) {
        if (leader == null) {
            throw new IllegalArgumentException("null leader");
        }

        this.leader = leader;
    }

    /**
     * Determines if this host is the leader, based on the current assignments.
     * 
     * @return {@code true} if this host is the leader, {@code false} otherwise
     */
    public boolean isLeader() {
        return getHost().equals(leader);
    }

    /**
     * Becomes the leader. Publishes a Leader message and enters the {@link ActiveState}.
     * 
     * @param alive hosts that are known to be alive
     * 
     * @return the new state
     */
    protected State becomeLeader(SortedSet<String> alive) {
        String newLeader = getHost();

        if (!newLeader.equals(alive.first())) {
            throw new IllegalArgumentException(newLeader + " cannot replace " + alive.first());
        }

        Leader msg = makeLeader(alive);
        logger.info("{}/{} hosts have an assignment", msg.getAssignments().getAllHosts().size(), alive.size());

        publish(msg);

        return goActive(msg.getAssignments());
    }

    /**
     * Makes a leader message. Assumes "this" host is the leader, and thus appears as the
     * first host in the set of hosts that are still alive.
     * 
     * @param alive hosts that are known to be alive
     * 
     * @return a new message
     */
    private Leader makeLeader(Set<String> alive) {
        return new Leader(getHost(), makeAssignments(alive));
    }

    /**
     * Makes a set of bucket assignments. Assumes "this" host is the leader.
     * 
     * @param alive hosts that are known to be alive
     * 
     * @return a new set of bucket assignments
     */
    private BucketAssignments makeAssignments(Set<String> alive) {

        // make a working array from the CURRENT assignments
        String[] bucket2host = makeBucketArray();

        TreeSet<String> avail = new TreeSet<>(alive);

        // if we have more hosts than buckets, then remove the extra hosts
        removeExcessHosts(bucket2host.length, avail);

        // create a host bucket for each available host
        Map<String, HostBucket> host2hb = new HashMap<>();
        avail.forEach(host -> host2hb.put(host, new HostBucket(host)));

        // add bucket indices to the appropriate host bucket
        addIndicesToHostBuckets(bucket2host, host2hb);

        // convert the collection back to an array
        fillArray(host2hb.values(), bucket2host);

        // update bucket2host with new assignments
        rebalanceBuckets(host2hb.values(), bucket2host);

        return new BucketAssignments(bucket2host);
    }

    /**
     * Makes a bucket array, copying the current assignments, if available.
     * 
     * @return a new bucket array
     */
    private String[] makeBucketArray() {
        BucketAssignments asgn = getAssignments();
        if (asgn == null) {
            return new String[BucketAssignments.MAX_BUCKETS];
        }

        String[] oldArray = asgn.getHostArray();
        if (oldArray.length == 0) {
            return new String[BucketAssignments.MAX_BUCKETS];
        }

        String[] newArray = new String[oldArray.length];
        System.arraycopy(oldArray, 0, newArray, 0, oldArray.length);

        return newArray;
    }

    /**
     * Removes excess hosts from the set of available hosts. Assumes "this" host is the
     * leader, and thus appears as the first host in the set.
     * 
     * @param maxHosts maximum number of hosts to be retained
     * @param avail available hosts
     */
    private void removeExcessHosts(int maxHosts, SortedSet<String> avail) {
        while (avail.size() > maxHosts) {
            /*
             * Don't remove this host, as it's the leader. Since the leader is always at
             * the front of the sorted set, we'll just pick off hosts from the back of the
             * set.
             */
            String host = avail.last();
            avail.remove(host);

            logger.warn("not using extra host {} for topic {}", host, getTopic());
        }
    }

    /**
     * Adds bucket indices to {@link HostBucket} objects. Buckets that are unassigned or
     * assigned to a host that does not appear within the map are re-assigned to a host
     * that appears within the map.
     * 
     * @param bucket2host bucket assignments
     * @param host2data maps a host name to its {@link HostBucket}
     */
    private void addIndicesToHostBuckets(String[] bucket2host, Map<String, HostBucket> host2data) {
        LinkedList<Integer> nullBuckets = new LinkedList<>();

        for (int x = 0; x < bucket2host.length; ++x) {
            String host = bucket2host[x];
            if (host == null) {
                nullBuckets.add(x);

            } else {
                HostBucket hb = host2data.get(host);
                if (hb == null) {
                    nullBuckets.add(x);

                } else {
                    hb.add(x);
                }
            }
        }

        // assign the null buckets to other hosts
        assignNullBuckets(nullBuckets, host2data.values());
    }

    /**
     * Assigns null buckets (i.e., those having no assignment) to available hosts.
     * 
     * @param buckets buckets that still need to be assigned to hosts
     * @param coll collection of current host-bucket assignments
     */
    private void assignNullBuckets(Queue<Integer> buckets, Collection<HostBucket> coll) {
        // assign null buckets to the hosts with the fewest buckets
        TreeSet<HostBucket> assignments = new TreeSet<>(coll);

        for (Integer index : buckets) {
            // add it to the host with the shortest bucket list
            HostBucket newhb = assignments.pollFirst();
            newhb.add(index);

            // put the item back into the queue, with its new count
            assignments.add(newhb);
        }
    }

    /**
     * Re-balances the buckets, taking from those that have a larger count and giving to
     * those that have a smaller count. Populates an output array with the new
     * assignments.
     * 
     * @param coll current bucket assignment
     * @param bucket2host array to be populated with the new assignments
     */
    private void rebalanceBuckets(Collection<HostBucket> coll, String[] bucket2host) {
        if (coll.size() <= 1) {
            // only one hosts - nothing to rebalance
            return;
        }

        TreeSet<HostBucket> assignments = new TreeSet<>(coll);

        for (;;) {
            HostBucket smaller = assignments.pollFirst();
            HostBucket larger = assignments.pollLast();

            if (larger.size() - smaller.size() <= 1) {
                // it's as balanced as it will get
                break;
            }

            // move the bucket from the larger to the smaller
            Integer b = larger.remove();
            smaller.add(b);

            bucket2host[b] = smaller.host;

            // put the items back, with their new counts
            assignments.add(larger);
            assignments.add(smaller);
        }

    }

    /**
     * Fills the array with the host assignments.
     * 
     * @param coll the host assignments
     * @param bucket2host array to be filled
     */
    private void fillArray(Collection<HostBucket> coll, String[] bucket2host) {
        for (HostBucket hb : coll) {
            for (Integer index : hb.buckets) {
                bucket2host[index] = hb.host;
            }
        }
    }

    /**
     * Tracks buckets that have been assigned to a host.
     */
    protected static class HostBucket implements Comparable<HostBucket> {
        /**
         * Host to which the buckets have been assigned.
         */
        private String host;

        /**
         * Buckets that have been assigned to this host.
         */
        private Queue<Integer> buckets = new LinkedList<>();

        /**
         * 
         * @param host
         */
        public HostBucket(String host) {
            this.host = host;
        }

        /**
         * Removes the next bucket from the list.
         * 
         * @return the next bucket
         */
        public final Integer remove() {
            return buckets.remove();
        }

        /**
         * Adds a bucket to the list.
         * 
         * @param index index of the bucket to add
         */
        public final void add(Integer index) {
            buckets.add(index);
        }

        /**
         * 
         * @return the number of buckets assigned to this host
         */
        public final int size() {
            return buckets.size();
        }

        /**
         * Compares host buckets, first by the number of buckets, and then by the host
         * name.
         */
        @Override
        public final int compareTo(HostBucket other) {
            int d = buckets.size() - other.buckets.size();
            if (d == 0) {
                d = host.compareTo(other.host);
            }
            return d;
        }

        @Override
        public final int hashCode() {
            throw new UnsupportedOperationException("HostBucket cannot be hashed");
        }

        @Override
        public final boolean equals(Object obj) {
            throw new UnsupportedOperationException("cannot compare HostBuckets");
        }
    }
}
