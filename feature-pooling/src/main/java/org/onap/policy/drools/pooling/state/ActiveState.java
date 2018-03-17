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

import java.util.Arrays;
import java.util.TreeSet;
import org.onap.policy.drools.pooling.message.BucketAssignments;
import org.onap.policy.drools.pooling.message.Heartbeat;
import org.onap.policy.drools.pooling.message.Offline;

public class ActiveState extends ProcessingState {

    /**
     * Amount of time, in milliseconds, to wait for a heart beat.
     */
    private static final long HEARTBEAT_MS = 0L;

    /**
     * Amount of time, in milliseconds, to wait before sending another heart beat.
     */
    private static final long INTER_HEARTBEAT_MS = 0L;

    /**
     * Set of hosts that have been assigned a bucket.
     */
    private final TreeSet<String> assigned = new TreeSet<>();

    /**
     * Host that comes after this host, or {@code null} if it has no successor.
     */
    private String succHost = null;

    /**
     * Host that comes before this host, or "" if it has no predecessor.
     */
    private String predHost = "";

    /**
     * {@code True} if saw this host's heart beat since the last check, {@code false} otherwise.
     */
    private boolean sawMyHeartbeat = false;

    /**
     * {@code True} if saw the predecessor's heart beat since the last check, {@code false} otherwise.
     */
    private boolean sawPredHeartbeat = false;

    public ActiveState(State oldState, String leader, BucketAssignments assignments) {
        super(oldState, leader, assignments);

        if (assignments != null) {
            assigned.addAll(Arrays.asList(assignments.getHost()));
        }

        detmNeighbors();
        addTimers();
        genHeartbeat();
    }

    /**
     * Determine this host's neighbors based on the order of the host UUIDs. Updates {@link #succHost} and
     * {@link #predHost}.
     */
    private void detmNeighbors() {
        if (assigned.size() < 2) {
            /*
             * this host is the only one with any assignments - it has no neighbors
             */
            succHost = null;
            predHost = "";
            return;
        }

        if ((succHost = assigned.higher(getHost())) == null) {
            // wrapped around - successor is the first host in the set
            succHost = assigned.first();
        }

        if ((predHost = assigned.lower(getHost())) == null) {
            // wrapped around - predecessor is the last host in the set
            predHost = assigned.last();
        }
    }

    /**
     * Adds the timers.
     */
    private void addTimers() {

        /*
         * heart beat generator
         */
        scheduleWithFixedDelay(0, INTER_HEARTBEAT_MS, xxx -> {
            genHeartbeat();
            return null;
        });

        /*
         * my heart beat checker
         */
        scheduleWithFixedDelay(HEARTBEAT_MS, HEARTBEAT_MS, xxx -> {
            if (sawMyHeartbeat) {
                sawMyHeartbeat = false;
                return null;
            }

            // missed my heart beat

            if (succHost != null) {
                publish(makeOffline());
            }

            return internalTopicFailed();
        });

        /*
         * predecessor heart beat checker
         */
        if (!predHost.isEmpty()) {
            scheduleWithFixedDelay(HEARTBEAT_MS, HEARTBEAT_MS, xxx -> {
                if (sawPredHeartbeat) {
                    sawPredHeartbeat = false;
                    return null;
                }

                // missed the predecessor's heart beat
                publish(makeQuery());

                return new QueryState(ActiveState.this, getLeader(), getAssignments());
            });
        }
    }

    /**
     * Generates a heart beat for this host and its successor.
     */
    private void genHeartbeat() {
        Heartbeat msg = makeHeartbeat();
        publish(getHost(), msg);

        if (succHost != null) {
            publish(succHost, msg);
        }
    }

    @Override
    public State process(Heartbeat msg) {
        String src = msg.getSource();

        if (src.equals(getHost())) {
            sawMyHeartbeat = true;

        } else if (src.equals(predHost)) {
            sawPredHeartbeat = true;

        }

        return null;
    }

    @Override
    public State process(Offline msg) {

        if (!assigned.contains(msg.getSource())) {
            /*
             * the offline host wasn't assigned any buckets, so just ignore the message
             */
            return null;

        } else if (isLeader() || (predHost.equals(msg.getSource()) && predHost.equals(assigned.first()))) {
            /*
             * Case 1: We are the leader.
             * 
             * Case 2: Our predecessor was the leader and it has gone offline - we should become the leader.
             * 
             * In either case, we are now the leader and we must re-balance the buckets since one of the hosts has gone
             * offline.
             */

            assigned.remove(msg.getSource());

            return becomeLeader(assigned);

        } else {
            /*
             * Otherwise, we don't care right now - we'll wait for the leader to tell us it's been removed.
             */
            return null;
        }
    }

}
