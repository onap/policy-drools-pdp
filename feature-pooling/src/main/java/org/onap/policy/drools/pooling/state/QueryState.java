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

import java.util.TreeSet;
import org.onap.policy.drools.pooling.message.BucketAssignments;
import org.onap.policy.drools.pooling.message.Identification;
import org.onap.policy.drools.pooling.message.Leader;
import org.onap.policy.drools.pooling.message.Offline;

// TODO comment all classes
// TODO add logging

public class QueryState extends ProcessingState {

    // TODO replace constants with properties

    /**
     * Amount of time, in milliseconds, to wait for all {@link Identification} messages to arrive. At that time, if this
     * host is first in {@link #alive}, then it will become the leader.
     */
    private static final long IDENTIFICATION_MS = 0L;

    /**
     * Amount of time, in milliseconds, to wait for a leader to take control. This time is applied after
     * {@link #IDENTIFICATION_MS} has elapsed.
     */
    private static final long LEADER_MS = 0L;

    /**
     * UUID of hosts that have sent an "Identification" message. Always includes this host.
     */
    private TreeSet<String> alive = new TreeSet<>();

    public QueryState(State oldState, String leader, BucketAssignments assignments) {
        super(oldState, leader, assignments);

        alive.add(getHost());

        /*
         * Once we've waited long enough for all Identification messages to arrive, become the leader, assuming we
         * should.
         */
        schedule(IDENTIFICATION_MS, xxx -> {

            if (getHost().equals(alive.first())) {
                // "this" host is the new leader
                return becomeLeader(alive);
            }

            /*
             * We aren't the leader, thus we'll now wait for the leader to take charge.
             */

            // create a new timer, in case the leader never takes charge
            awaitLeader();

            return null;
        });
    }

    /**
     * Starts a timer to return to business as usual if no leader takes charge. Eventually a leader will take charge, or
     * someone will issue a new "Query" request.
     */
    private void awaitLeader() {

        schedule(LEADER_MS, xxx -> {

            if (getHost().equals(alive.first())) {
                // this host is the new leader
                return becomeLeader(alive);

            } else if (hasAssignment()) {
                /*
                 * this host is not the new leader, but it does have an assignment - return to the active state while we
                 * wait for the leader
                 */
                return new ActiveState(QueryState.this, getLeader(), getAssignments());

            } else {
                // not the leader and no assignment yet
                return new InactiveState(QueryState.this);
            }
        });
    }

    /**
     * Determines if this host has an assignment in the CURRENT assignments.
     * 
     * @return {@code true} if this host has an assignment, {@code false} otherwise
     */
    private boolean hasAssignment() {
        BucketAssignments asgn = getAssignments();
        return (asgn != null && asgn.hasAssignment(getHost()));
    }

    @Override
    public State process(Leader msg) {
        State ns = super.process(msg);
        if (ns != null) {
            return ns;
        }

        // the message does not have an acceptable leader, but we'll still
        // record its info, if we don't have any yet
        optSetAssignments(msg.getAssignments());

        return null;
    }

    @Override
    public State process(Identification msg) {
        // record its info, if we don't have any yet
        optSetAssignments(msg.getAssignments());

        // add this message's source host to "alive"
        String host = msg.getSource();
        if (host != null) {
            alive.add(host);
            setLeader(alive.first());
        }

        return null;
    }

    @Override
    public State process(Offline msg) {
        String host = msg.getSource();
        if (host == null) {
            return null;
        }

        if (!host.equals(getHost())) {
            alive.remove(host);
        }

        return null;
    }

    /**
     * Sets the assignments, if they are currently not set.
     * 
     * @param assignments new assignments, or {@code null}
     */
    private void optSetAssignments(BucketAssignments assignments) {
        if (getAssignments() == null) {
            setAssignments(assignments);
        }
    }
}
