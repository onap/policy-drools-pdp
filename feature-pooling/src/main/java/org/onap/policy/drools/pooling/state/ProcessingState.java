/*
 * ============LICENSE_START=======================================================
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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;

import org.onap.policy.drools.pooling.message.BucketAssignments;
import org.onap.policy.drools.pooling.message.Forward;
import org.onap.policy.drools.pooling.message.Identification;
import org.onap.policy.drools.pooling.message.Leader;
import org.onap.policy.drools.pooling.message.Message;
import org.onap.policy.drools.pooling.message.Query;

public class ProcessingState extends State {

	/**
	 * Maximum number of times a message can be forwarded.
	 */
	private static final int MAX_HOPS = 0;

	/**
	 * Current leader.
	 */
	private String leader;

	/**
	 * Current assignments, or {@code null} if no assignments are known yet.
	 */
	private BucketAssignments assignments;

	public ProcessingState(State oldState, String leader, BucketAssignments assignments) {
		super(oldState);

		this.leader = leader;
		this.setAssignments(assignments);
	}

	/**
	 * If the message source is an acceptable leader (i.e., <= {@link #leader}),
	 * then it transitions to the {@link ActiveState} (or {@link InactiveState},
	 * if this host has no bucket assignments).
	 */
	@Override
	public State process(Leader msg) {
		BucketAssignments asgn = msg.getAssignments();
		if (asgn == null) {
			return null;
		}

		String h = asgn.getLeader();

		if (h != null && h.compareTo(leader) < 0) {

			if (asgn.hasAssignment(getHost())) {
				return new ActiveState(this, h, asgn);

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

	/**
	 * Forwards the message to the correct host, or injects it, if this is the
	 * correct host.
	 */
	@Override
	public State process(Forward msg) {
		if (assignments == null) {
			/*
			 * No assignments yet - just inject it into the rule engine for
			 * handling once an assignment is available.
			 */
			return super.process(msg);
		}

		String target = assignments.getAssignedHost(msg.getBucket());
		if (target == null) {
			// TODO log
			return null;

		} else if (target.equals(getHost())) {
			inject(msg.getSessName(), msg.getEncodedReq());

		} else if (msg.getNumHops() < MAX_HOPS) {
			// not assigned to this host - forward it again
			msg.setSource(getHost());
			msg.bumpNumHops();

			publish(target, msg);

		} else {
			// too many hops - can't forward it again
			// TODO log
		}

		return null;
	}

	@SuppressWarnings("unchecked")
	@Override
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
	 * @param assignments
	 *            new assignments, or {@code null}
	 */
	protected final void setAssignments(BucketAssignments assignments) {
		this.assignments = assignments;
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
		return (leader != null && leader.equals(getHost()));
	}

	/**
	 * Becomes the leader. Publishes a Leader message and enters the
	 * {@link ActiveState}.
	 * 
	 * @param alive
	 *            hosts that are known to be alive
	 * 
	 * @return the new state
	 */
	protected State becomeLeader(Set<String> alive) {
		String host = getHost();

		Leader msg = makeLeader(alive);
		publish(msg);

		return new ActiveState(this, host, msg.getAssignments());
	}

	/**
	 * Makes a leader message.
	 * 
	 * @param alive
	 *            hosts that are known to be alive
	 * 
	 * @return a new message
	 */
	private Leader makeLeader(Set<String> alive) {
		BucketAssignments asgn = makeAssignments(alive);
		return new Leader(getHost(), asgn);
	}

	/**
	 * Makes a set of bucket assignments. Assumes "this" host is the leader.
	 * 
	 * @param alive
	 *            hosts that are known to be alive
	 * 
	 * @return a new set of bucket assignments
	 */
	private BucketAssignments makeAssignments(Set<String> alive) {

		// TODO validate bucket2host.length == MAX_BUCKETS (not in junit tests)

		// make a working array from the CURRENT assignments
		String[] bucket2host = makeBucketArray();

		TreeSet<String> avail = new TreeSet<>(alive);

		// if we have more hosts than buckets, then remove the extra hosts
		removeExcessHosts(bucket2host.length, avail);

		// convert the array to a collection
		Collection<HostBucket> coll = makeHostBuckets(bucket2host);

		// assign buckets from dead hosts to the other hosts
		assignDeadBuckets(avail, coll);

		rebalanceBuckets(coll, bucket2host);

		return new BucketAssignments(bucket2host);
	}

	/**
	 * Removes excess hosts from the set of available hosts. Assumes "this" host
	 * is the leader, and thus appears as the first host in the set.
	 * 
	 * @param maxHosts
	 *            maximum number of hosts to be retained
	 * @param avail
	 *            available hosts
	 */
	private void removeExcessHosts(int maxHosts, TreeSet<String> avail) {
		while (avail.size() > maxHosts) {
			/*
			 * Don't remove this host, as it's the leader. Since the leader is
			 * always at the front of the sorted set, we'll just pick off hosts
			 * from the back of the set.
			 */
			String h = avail.last();
			avail.remove(h);

			// TODO log a message
		}
	}

	/**
	 * Makes a bucket array, copying the current assignments, if available.
	 * 
	 * @return a new bucket array
	 */
	private String[] makeBucketArray() {
		BucketAssignments asgn = getAssignments();
		if (asgn == null || asgn.isEmpty()) {
			return new String[BucketAssignments.MAX_BUCKETS];
		}

		String[] arr = asgn.getHost();
		if (arr.length == 0) {
			return new String[1];
		}

		String[] arr2 = new String[arr.length];
		System.arraycopy(arr, 0, arr2, 0, arr.length);

		return arr;
	}

	/**
	 * Converts an array of bucket assignments to a collection of
	 * {@link HostBucket} objects.
	 * 
	 * @param bucket2host
	 *            bucket assignments
	 * @return a collection of {@link HostBucket} objects
	 */
	private Collection<HostBucket> makeHostBuckets(String[] bucket2host) {
		Map<String, HostBucket> host2data = new HashMap<>();
		HostBucket nullbkt = new HostBucket((String) null);

		for (int x = 0; x < bucket2host.length; ++x) {
			String h = bucket2host[x];
			if (h == null) {
				nullbkt.add(x);

			} else {
				HostBucket hb = host2data.get(h);
				if (hb == null) {
					hb = new HostBucket(h);
					host2data.put(h, hb);
				}

				hb.add(x);
			}
		}

		List<HostBucket> lst = new ArrayList<>(host2data.values());

		if (!nullbkt.isEmpty()) {
			lst.add(nullbkt);
		}

		return lst;
	}

	/**
	 * Assigns dead buckets to available hosts. Removes dead hosts from the
	 * collection.
	 * 
	 * @param avail
	 *            available hosts
	 * @param coll
	 *            collection of current host-bucket assignments
	 */
	private void assignDeadBuckets(Set<String> avail, Collection<HostBucket> coll) {
		Collection<HostBucket> dead = extractDeadHosts(avail, coll);
		if (dead.isEmpty()) {
			return;
		}

		// assign buckets from the dead hosts to the hosts with the fewest
		// buckets
		PriorityQueue<HostBucket> minq = new PriorityQueue<>(coll);

		for (HostBucket dhb : dead) {
			for (Integer index : dhb.buckets) {
				// add it to the host with the short bucket list
				HostBucket newhb = minq.remove();
				newhb.add(index);
				minq.add(newhb);
			}
		}
	}

	/**
	 * Extracts dead hosts from a collection. Removes dead hosts from the
	 * collection. The collection is permitted to contain a {@link HostBucket}
	 * with a {@code null} host, which identifies unassigned buckets.
	 * 
	 * @param avail
	 *            hosts that are still available
	 * @param coll
	 *            collection from which to extract the hosts
	 * @return the dead hosts
	 */
	private Collection<HostBucket> extractDeadHosts(Set<String> avail, Collection<HostBucket> coll) {
		List<HostBucket> dead = new LinkedList<>();
		Iterator<HostBucket> it = coll.iterator();

		while (it.hasNext()) {
			HostBucket hb = it.next();
			if (hb.host == null || !avail.contains(hb.host)) {
				it.remove();
				dead.add(hb);
			}
		}

		return dead;
	}

	/**
	 * Re-balances the buckets, taking from those that have a larger count and
	 * giving to those that have a smaller count. Populates an output array with
	 * the new assignments.
	 * 
	 * @param coll
	 *            current bucket assignment
	 * @param bucket2host
	 *            array to be populated with the new assignments
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
		PriorityQueue<HostBucket> maxq = new PriorityQueue<>((hb1, hb2) -> {
			return hb2.compareTo(hb1);
		});
		addClones(maxq, coll);

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

			// put the items back in their respective queues, with their new
			// counts
			maxq.add(larger);
			minq.add(smaller);
		}

	}

	/**
	 * Fills the array with the host assignments.
	 * 
	 * @param coll
	 *            the host assignments
	 * @param bucket2host
	 *            array to be filled
	 */
	private void fillArray(Collection<HostBucket> coll, String[] bucket2host) {
		for (HostBucket hb : coll) {
			for (Integer index : hb.buckets) {
				bucket2host[index] = hb.host;
			}
		}
	}

	/**
	 * Clones each {@link HostBucket} and adds it to the queue.
	 * 
	 * @param queue
	 *            queue to which to add the items
	 * @param coll
	 *            collection of items to be cloned
	 */
	private void addClones(PriorityQueue<HostBucket> queue, Collection<HostBucket> coll) {
		for (HostBucket hb : coll) {
			queue.add(new HostBucket(hb));
		}
	}

	/**
	 * Tracks buckets that have been assigned to a host.
	 */
	public static class HostBucket implements Comparable<HostBucket> {
		private String host;
		private Queue<Integer> buckets = new LinkedList<>();

		public HostBucket(String host) {
			this.host = host;
		}

		public HostBucket(HostBucket other) {
			this.host = other.host;
			this.buckets.addAll(other.buckets);
		}

		public final Integer remove() {
			return buckets.remove();
		}

		public final void add(Integer index) {
			buckets.add(index);
		}

		public final boolean isEmpty() {
			return buckets.isEmpty();
		}

		private final int size() {
			return buckets.size();
		}

		@Override
		public final int compareTo(HostBucket other) {
			int d = buckets.size() - other.buckets.size();
			if (d == 0)
				d = host.compareTo(other.host);
			return d;
		}
	}
}
