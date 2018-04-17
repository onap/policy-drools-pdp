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
import org.onap.policy.drools.pooling.PoolingManager;
import org.onap.policy.drools.pooling.message.BucketAssignments;
import org.onap.policy.drools.pooling.message.Identification;
import org.onap.policy.drools.pooling.message.Leader;
import org.onap.policy.drools.pooling.message.Offline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Query state. In this state, the host waits for the other hosts to identify
 * themselves. Eventually, a leader should come forth. If not, it will transition to the
 * active or inactive state, depending on whether or not it has an assignment in the
 * current bucket assignments. The other possibility is that it may <i>become</i> the
 * leader, in which case it will also transition to the active state.
 */
public class QueryState extends ProcessingState {

    private static final Logger logger = LoggerFactory.getLogger(QueryState.class);

    /**
     * Hosts that have sent an "Identification" message. Always includes this host.
     */
    private TreeSet<String> alive = new TreeSet<>();

    /**
     * {@code True} if we saw our own Identification method, {@code false} otherwise.
     */
    private boolean sawSelfIdent = false;

    /**
     * 
     * @param mgr
     */
    public QueryState(PoolingManager mgr) {
        // this host is the leader, until a better candidate identifies itself
        super(mgr, mgr.getHost());

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
         * Once we've waited long enough for all Identification messages to arrive, become
         * the leader, assuming we should.
         */

        schedule(getProperties().getIdentificationMs(), () -> {

            if (!sawSelfIdent) {
                // didn't see our identification
                logger.error("missed our own Ident message on topic {}", getTopic());
                return missedHeartbeat();

            } else if (isLeader()) {
                // "this" host is the new leader
                logger.info("this host is the new leader for topic {}", getTopic());
                return becomeLeader(alive);

            } else {
                // not the leader - return to previous state
                logger.info("no new leader on topic {}", getTopic());
                return goActive(getAssignments());
            }
        });
    }

    @Override
    public State goQuery() {
        return null;
    }

    @Override
    public State process(Identification msg) {

        if (getHost().equals(msg.getSource())) {
            logger.info("saw our own Ident message on topic {}", getTopic());
            sawSelfIdent = true;

        } else {
            logger.info("received Ident message from {} on topic {}", msg.getSource(), getTopic());
            recordInfo(msg.getSource(), msg.getAssignments());
        }

        return null;
    }

    /**
     * If the message leader is better than the leader we have, then go active with it.
     * Otherwise, simply treat it like an {@link Identification} message.
     */
    @Override
    public State process(Leader msg) {
        if (!isValid(msg)) {
            return null;
        }

        String source = msg.getSource();
        BucketAssignments asgn = msg.getAssignments();

        // go active, if this has a leader that's the same or better than the one we have
        if (source.compareTo(getLeader()) <= 0) {
            logger.warn("leader with {} on topic {}", source, getTopic());
            return goActive(asgn);
        }

        /*
         * The message does not have an acceptable leader, but we'll still record its
         * info.
         */
        logger.info("record leader info from {} on topic {}", source, getTopic());
        recordInfo(source, asgn);

        return null;
    }

    @Override
    public State process(Offline msg) {
        String host = msg.getSource();

        if (host != null && !host.equals(getHost())) {
            logger.warn("host {} offline on topic {}", host, getTopic());
            alive.remove(host);
            setLeader(alive.first());

        } else {
            logger.info("ignored offline message from {} on topic {}", msg.getSource(), getTopic());
        }

        return null;
    }

    /**
     * Records info from a message, adding the source host name to {@link #alive}, and
     * updating the bucket assignments.
     * 
     * @param source the message's source host
     * @param assignments assignments, or {@code null}
     */
    private void recordInfo(String source, BucketAssignments assignments) {
        // add this message's source host to "alive"
        if (source != null) {
            alive.add(source);
            setLeader(alive.first());
        }

        if (assignments == null || assignments.getLeader() == null) {
            return;
        }

        // record assignments, if we don't have any yet
        BucketAssignments current = getAssignments();
        if (current == null) {
            logger.info("received initial assignments on topic {}", getTopic());
            setAssignments(assignments);
            return;
        }

        /*
         * Record assignments, if the new assignments have a better (i.e., lesser) leader.
         */
        String curldr = current.getLeader();
        if (curldr == null || assignments.getLeader().compareTo(curldr) < 0) {
            logger.info("use new assignments from {} on topic {}", source, getTopic());
            setAssignments(assignments);
        }
    }
}
