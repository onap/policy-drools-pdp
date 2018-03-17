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
import org.onap.policy.drools.pooling.message.Query;

// TODO add logging

/**
 * The Query state. In this state, the host waits for the other hosts to
 * identify themselves. Eventually, a leader should come forth. If not, it will
 * transition to the active or inactive state, depending on whether or not it
 * has an assignment in the current bucket assignments. The other possibility is
 * that it may <i>become</i> the leader, in which case it will also transition
 * to the active state.
 */
public class QueryState extends ProcessingState {

    // TODO replace constants with properties

    /**
     * Amount of time, in milliseconds, to wait for all {@link Identification}
     * messages to arrive. At that time, if this host is first in
     * {@link #alive}, then it will become the leader.
     */
    public static final long IDENTIFICATION_MS = 0L;

    /**
     * Amount of time, in milliseconds, to wait for a leader to take control.
     * This time is applied after {@link #IDENTIFICATION_MS} has elapsed.
     */
    public static final long LEADER_MS = 0L;

    /**
     * Hosts that have sent an "Identification" message. Always includes this
     * host.
     */
    private TreeSet<String> alive = new TreeSet<>();

    /**
     * {@code True} if we've finished the identification process and are simply
     * awaiting the leader, {@code false} otherwise.
     */
    private boolean identComplete = false;

    /**
     * 
     * @param oldState previous state
     * @param leader the currently know leader, or {@code null} if the leader is
     *        known
     * @param assignments the currently known bucket assignments, or
     *        {@code null} if there are no known assignments
     */
    public QueryState(State oldState, String leader, BucketAssignments assignments) {
        super(oldState, leader, assignments);

        alive.add(getHost());
    }

    @Override
    public void start() {

        super.start();

        // start identification timer
        awaitIdentification();
    }

    /**
     * Starts a timer to wait for all Identification messages to arrive.
     */
    private void awaitIdentification() {
        
        /*
         * Once we've waited long enough for all Identification messages to
         * arrive, become the leader, assuming we should.
         */
        
        schedule(IDENTIFICATION_MS, xxx -> {

            if (isLeader()) {
                // "this" host is the new leader
                return becomeLeader(alive);
            }

            /*
             * We aren't the leader, so give the leader a little longer to take
             * charge.
             */

            identComplete = true;

            // create a new timer, in case the leader never takes charge
            awaitLeader();

            return null;
        });
    }

    /**
     * Starts a timer to return to business as usual if no leader takes charge.
     * Eventually a leader will take charge, or someone will issue a new "Query"
     * request.
     */
    private void awaitLeader() {

        schedule(LEADER_MS, xxx -> {

            if (hasAssignment()) {
                /*
                 * this host is not the new leader, but it does have an
                 * assignment - return to the active state while we wait for the
                 * leader
                 */
                return goActive(getLeader(), getAssignments());

            } else {
                // not the leader and no assignment yet
                return goInactive();
            }
        });
    }

    /**
     * Determines if this host has an assignment in the CURRENT assignments.
     * 
     * @return {@code true} if this host has an assignment, {@code false}
     *         otherwise
     */
    protected boolean hasAssignment() {
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

        if (host != null && !host.equals(getHost())) {
            alive.remove(host);
            setLeader(alive.first());

            if (identComplete && isLeader()) {
                /*
                 * We were waiting for the leader, but it just went offline, so
                 * now we're the leader.
                 */
                return becomeLeader(alive);
            }
        }

        return null;
    }

    /**
     * Transitions to the query state.
     */
    @Override
    public State process(Query msg) {
        State next = super.process(msg);
        if (next != null) {
            return next;
        }

        return goQuery(getLeader(), getAssignments());
    }

    /**
     * Sets the assignments, if they are not currently set or if the new
     * assignments have a better leader.
     * 
     * @param assignments new assignments, or {@code null}
     */
    private void optSetAssignments(BucketAssignments assignments) {
        BucketAssignments current = getAssignments();

        if (assignments != null && (current == null || assignments.getLeader().compareTo(current.getLeader()) < 0)) {
            setAssignments(assignments);
        }
    }
}
