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

import static org.onap.policy.drools.pooling.state.FilterUtils.MSG_CHANNEL;
import static org.onap.policy.drools.pooling.state.FilterUtils.makeEquals;
import static org.onap.policy.drools.pooling.state.FilterUtils.makeOr;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import org.onap.policy.drools.pooling.message.BucketAssignments;
import org.onap.policy.drools.pooling.message.Identification;
import org.onap.policy.drools.pooling.message.Leader;
import org.onap.policy.drools.pooling.message.Message;
import org.onap.policy.drools.pooling.message.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Any state in which events are being processed locally and forwarded, as
 * appropriate.
 */
public class ProcessingState extends State {

    private static final Logger logger = LoggerFactory.getLogger(ProcessingState.class);

    /**
     * Current known leader.
     */
    private String leader;

    /**
     * Current known assignments, or {@code null} if no assignments are known
     * yet.
     */
    private BucketAssignments assignments;

    /**
     * 
     * @param oldState previous state
     * @param leader current known leader, which need not be the same as the
     *        assignment leader
     * @param assignments
     */
    public ProcessingState(State oldState, String leader, BucketAssignments assignments) {
        super(oldState);

        this.leader = leader;
        this.assignments = assignments;
    }

    /**
     * If the message source is an acceptable leader (i.e., <= {@link #leader}),
     * then it transitions to the {@link ActiveState} (or {@link InactiveState},
     * if this host has no bucket assignments).
     */
    @Override
    public State process(Leader msg) {
        BucketAssignments asgn = msg.getAssignments();
        String newLeader = asgn.getLeader();

        if (newLeader != null && newLeader.compareTo(leader) <= 0) {
            startDistributing(asgn);

            if (asgn.hasAssignment(getHost())) {
                return new ActiveState(this, newLeader, asgn);

            } else {
                return new InactiveState(this);
            }
        }

        return null;
    }

    /**
     * Transitions to the query state.
     */
    @Override
    public State process(Query msg) {
        publish(makeIdentification());

        return new QueryState(this, leader, assignments);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getFilter() {
        return makeOr(makeEquals(MSG_CHANNEL, Message.ADMIN), makeEquals(MSG_CHANNEL, getHost()));
    }

    /**
     * Makes an Identification message.
     * 
     * @return a new message
     */
    protected Identification makeIdentification() {
        return new Identification(getHost(), assignments);
    }

    protected BucketAssignments getAssignments() {
        return assignments;
    }

    /**
     * Sets the assignments.
     * 
     * @param assignments new assignments, or {@code null}
     */
    protected final void setAssignments(BucketAssignments assignments) {
        this.assignments = assignments;

        if (assignments != null) {
            startDistributing(assignments);
        }
    }

    public String getLeader() {
        return leader;
    }

    protected void setLeader(String leader) {
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
     * Becomes the leader. Publishes a Leader message and enters the
     * {@link ActiveState}.
     * 
     * @param alive hosts that are known to be alive
     * 
     * @return the new state
     */
    protected State becomeLeader(Set<String> alive) {
        String leader = getHost();

        Leader msg = makeLeader(alive);
        publish(msg);

        return new ActiveState(this, leader, msg.getAssignments());
    }

    /**
     * Makes a leader message. Assumes "this" host is the leader, and thus
     * appears as the first host in the set of hosts that are still alive.
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

        // remove buckets from dead hosts
        removeDeadHosts(bucket2host, alive);

        // convert the array to a collection
        Collection<HostBucket> coll = makeHostBuckets(bucket2host);

        rebalanceBuckets(coll, bucket2host);

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

        String[] arr = asgn.getHost();
        if (arr.length == 0) {
            return new String[BucketAssignments.MAX_BUCKETS];
        }

        String[] arr2 = new String[arr.length];
        System.arraycopy(arr, 0, arr2, 0, arr.length);

        return arr;
    }

    /**
     * Removes excess hosts from the set of available hosts. Assumes "this" host
     * is the leader, and thus appears as the first host in the set.
     * 
     * @param maxHosts maximum number of hosts to be retained
     * @param avail available hosts
     */
    private void removeExcessHosts(int maxHosts, TreeSet<String> avail) {
        while (avail.size() > maxHosts) {
            /*
             * Don't remove this host, as it's the leader. Since the leader is
             * always at the front of the sorted set, we'll just pick off hosts
             * from the back of the set.
             */
            String host = avail.last();
            avail.remove(host);

            logger.warn("not using extra host {} for topic {}", host, getTopic());
        }
    }

    /**
     * Removes buckets from dead hosts by assign {@code null} to the bucket.
     * 
     * @param bucket2host buckets to be updated
     * @param alive hosts known to be alive
     */
    private void removeDeadHosts(String[] bucket2host, Set<String> alive) {
        for (int x = 0; x < bucket2host.length; ++x) {
            if (!alive.contains(bucket2host[x])) {
                bucket2host[x] = null;
            }
        }
    }

    /**
     * Converts an array of bucket assignments to a collection of
     * {@link HostBucket} objects.
     * 
     * @param bucket2host bucket assignments
     * @return a collection of {@link HostBucket} objects
     */
    private Collection<HostBucket> makeHostBuckets(String[] bucket2host) {
        Map<String, HostBucket> host2data = new HashMap<>();
        Queue<Integer> nullBuckets = new LinkedList<Integer>();

        for (int x = 0; x < bucket2host.length; ++x) {
            String host = bucket2host[x];
            if (host == null) {
                nullBuckets.add(x);

            } else {
                HostBucket hb = host2data.get(host);
                if (hb == null) {
                    hb = new HostBucket(host);
                    host2data.put(host, hb);
                }

                hb.add(x);
            }
        }

        List<HostBucket> lst = new ArrayList<>(host2data.values());

        // assign the null buckets to other hosts
        assignNullBuckets(nullBuckets, lst);

        return lst;
    }

    /**
     * Assigns null buckets (i.e., those having no assignment) to available
     * hosts.
     * 
     * @param buckets available hosts
     * @param coll collection of current host-bucket assignments
     */
    private void assignNullBuckets(Queue<Integer> buckets, Collection<HostBucket> coll) {
        if (buckets.isEmpty()) {
            return;
        }

        // assign null buckets to the hosts with the fewest buckets
        PriorityQueue<HostBucket> minq = new PriorityQueue<>(coll);

        for (Integer index : buckets) {
            // add it to the host with the short bucket list
            HostBucket newhb = minq.remove();
            newhb.add(index);

            // put the item back into the queue, with its new count
            minq.add(newhb);
        }
    }

    /**
     * Re-balances the buckets, taking from those that have a larger count and
     * giving to those that have a smaller count. Populates an output array with
     * the new assignments.
     * 
     * @param coll current bucket assignment
     * @param bucket2host array to be populated with the new assignments
     */
    private void rebalanceBuckets(Collection<HostBucket> coll, String[] bucket2host) {

        // convert the collection back to an array
        fillArray(coll, bucket2host);

        /*
         * Note: as we'll be updating the counts of the HostBucket objects in
         * each of these queues, we need to create a clone of each of the
         * objects for one of the queues.
         * 
         * In addition, we don't bother updating a given host's data in both
         * queues. As we stop as soon as the counts are within 1 of each other,
         * we know that we'll never visit the items at the other end of the
         * queue. Consequently, it's ok that we don't update their counts. This
         * allows us to simply use the remove() method, which is O(log(N)),
         * instead of the remove(Object) method, which is O(N).
         */

        // use reverse comparison so items with a larger count come first
        PriorityQueue<HostBucket> maxq = new PriorityQueue<>((hb1, hb2) -> hb2.compareTo(hb1));

        // clone the buckets and put them into maxq
        coll.forEach(hb -> maxq.add(new HostBucket(hb)));

        // add the original collection into minq
        PriorityQueue<HostBucket> minq = new PriorityQueue<>(coll);

        for (;;) {
            HostBucket smaller = minq.remove();
            HostBucket larger = maxq.remove();

            if (larger.size() - smaller.size() <= 1) {
                // it's as balanced as it will get
                break;
            }

            // move the bucket from the larger to the smaller
            Integer b = larger.remove();
            smaller.add(b);

            bucket2host[b] = smaller.host;

            /*
             * Put the items back in their respective queues, with their new
             * counts
             */
            maxq.add(larger);
            minq.add(smaller);
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
    public static class HostBucket implements Comparable<HostBucket> {
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
         * Clones another host bucket.
         * 
         * @param other host bucket to be cloned
         */
        public HostBucket(HostBucket other) {
            this.host = other.host;
            this.buckets.addAll(other.buckets);
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
         * Compares host buckets, first by the number of buckets, and then by
         * the host name.
         */
        @Override
        public final int compareTo(HostBucket other) {
            int d = buckets.size() - other.buckets.size();
            if (d == 0) {
                d = host.compareTo(other.host);
            }
            return d;
        }
    }
}
