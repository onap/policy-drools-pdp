/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018, 2020 AT&T Intellectual Property. All rights reserved.
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

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.onap.policy.drools.pooling.CancellableScheduledTask;
import org.onap.policy.drools.pooling.PoolingManager;
import org.onap.policy.drools.pooling.PoolingProperties;
import org.onap.policy.drools.pooling.message.BucketAssignments;
import org.onap.policy.drools.pooling.message.Forward;
import org.onap.policy.drools.pooling.message.Heartbeat;
import org.onap.policy.drools.pooling.message.Identification;
import org.onap.policy.drools.pooling.message.Leader;
import org.onap.policy.drools.pooling.message.Message;
import org.onap.policy.drools.pooling.message.Offline;
import org.onap.policy.drools.pooling.message.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A state in the finite state machine.
 *
 * <p>A state may have several timers associated with it, which must be cancelled whenever
 * the state is changed. Assumes that timers are not continuously added to the same state.
 */
public abstract class State {

    private static final Logger logger = LoggerFactory.getLogger(State.class);

    /**
     * Host pool manager.
     */
    private final PoolingManager mgr;

    /**
     * Timers added by this state.
     */
    private final List<CancellableScheduledTask> timers = new LinkedList<>();

    /**
     * Constructor.
     *
     * @param mgr pooling manager
     */
    public State(PoolingManager mgr) {
        this.mgr = mgr;
    }

    /**
     * Gets the server-side filter to use when polling the DMaaP internal topic. The
     * default method returns a filter that accepts messages on the admin channel and on
     * the host's own channel.
     *
     * @return the server-side filter to use.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getFilter() {
        return makeOr(makeEquals(MSG_CHANNEL, Message.ADMIN), makeEquals(MSG_CHANNEL, getHost()));
    }

    /**
     * Cancels the timers added by this state.
     */
    public final void cancelTimers() {
        timers.forEach(CancellableScheduledTask::cancel);
    }

    /**
     * Starts the state. The default method simply logs a message and returns.
     */
    public void start() {
        logger.info("entered {} for topic {}", getClass().getSimpleName(), getTopic());
    }

    /**
     * Transitions to the "start" state.
     *
     * @return the new state
     */
    public final State goStart() {
        return mgr.goStart();
    }

    /**
     * Transitions to the "query" state.
     *
     * @return the new state
     */
    public State goQuery() {
        return mgr.goQuery();
    }

    /**
     * Goes active with a new set of assignments.
     *
     * @param asgn new assignments
     * @return the new state, either Active or Inactive, depending on whether or not this
     *         host has an assignment
     */
    protected State goActive(BucketAssignments asgn) {
        startDistributing(asgn);

        if (asgn != null && asgn.hasAssignment(getHost())) {
            return mgr.goActive();

        } else {
            return goInactive();
        }
    }

    /**
     * Transitions to the "inactive" state.
     *
     * @return the new state
     */
    protected State goInactive() {
        return mgr.goInactive();
    }

    /**
     * Processes a message. The default method passes it to the manager to handle and
     * returns {@code null}.
     *
     * @param msg message to be processed
     * @return the new state, or {@code null} if the state is unchanged
     */
    public State process(Forward msg) {
        if (getHost().equals(msg.getChannel())) {
            logger.info("received Forward message from {} on topic {}", msg.getSource(), getTopic());
            mgr.handle(msg);

        } else {
            logger.info("discard Forward message to {} from {} on topic {}", msg.getChannel(), msg.getSource(),
                            getTopic());
        }

        return null;
    }

    /**
     * Processes a message. The default method just returns {@code null}.
     *
     * @param msg message to be processed
     * @return the new state, or {@code null} if the state is unchanged
     */
    public State process(Heartbeat msg) {
        logger.info("ignored heartbeat message from {} on topic {}", msg.getSource(), getTopic());
        return null;
    }

    /**
     * Processes a message. The default method just returns {@code null}.
     *
     * @param msg message to be processed
     * @return the new state, or {@code null} if the state is unchanged
     */
    public State process(Identification msg) {
        logger.info("ignored ident message from {} on topic {}", msg.getSource(), getTopic());
        return null;
    }

    /**
     * Processes a message. The default method copies the assignments and then returns
     * {@code null}.
     *
     * @param msg message to be processed
     * @return the new state, or {@code null} if the state is unchanged
     */
    public State process(Leader msg) {
        if (isValid(msg)) {
            logger.info("extract assignments from Leader message from {} on topic {}", msg.getSource(), getTopic());
            startDistributing(msg.getAssignments());
        }

        return null;
    }

    /**
     * Processes a message. The default method just returns {@code null}.
     *
     * @param msg message to be processed
     * @return the new state, or {@code null} if the state is unchanged
     */
    public State process(Offline msg) {
        logger.info("ignored offline message from {} on topic {}", msg.getSource(), getTopic());
        return null;
    }

    /**
     * Processes a message. The default method just returns {@code null}.
     *
     * @param msg message to be processed
     * @return the new state, or {@code null} if the state is unchanged
     */
    public State process(Query msg) {
        logger.info("ignored Query message from {} on topic {}", msg.getSource(), getTopic());
        return null;
    }

    /**
     * Determines if a message is valid and did not originate from this host.
     *
     * @param msg message to be validated
     * @return {@code true} if the message is valid, {@code false} otherwise
     */
    protected boolean isValid(Leader msg) {
        BucketAssignments asgn = msg.getAssignments();
        if (asgn == null) {
            logger.warn("Leader message from {} has no assignments for topic {}", msg.getSource(), getTopic());
            return false;
        }

        // ignore Leader messages from ourself
        String source = msg.getSource();
        if (source == null || source.equals(getHost())) {
            logger.debug("ignore Leader message from {} for topic {}", msg.getSource(), getTopic());
            return false;
        }

        // the new leader must equal the source
        boolean result = source.equals(asgn.getLeader());

        if (!result) {
            logger.warn("Leader message from {} has an invalid assignment for topic {}", msg.getSource(), getTopic());
        }

        return result;
    }

    /**
     * Publishes a message.
     *
     * @param msg message to be published
     */
    protected final void publish(Identification msg) {
        mgr.publishAdmin(msg);
    }

    /**
     * Publishes a message.
     *
     * @param msg message to be published
     */
    protected final void publish(Leader msg) {
        mgr.publishAdmin(msg);
    }

    /**
     * Publishes a message.
     *
     * @param msg message to be published
     */
    protected final void publish(Offline msg) {
        mgr.publishAdmin(msg);
    }

    /**
     * Publishes a message.
     *
     * @param msg message to be published
     */
    protected final void publish(Query msg) {
        mgr.publishAdmin(msg);
    }

    /**
     * Publishes a message on the specified channel.
     *
     * @param channel channel
     * @param msg message to be published
     */
    protected final void publish(String channel, Forward msg) {
        mgr.publish(channel, msg);
    }

    /**
     * Publishes a message on the specified channel.
     *
     * @param channel channel
     * @param msg message to be published
     */
    protected final void publish(String channel, Heartbeat msg) {
        mgr.publish(channel, msg);
    }

    /**
     * Starts distributing messages using the specified bucket assignments.
     *
     * @param assignments assignments
     */
    protected final void startDistributing(BucketAssignments assignments) {
        if (assignments != null) {
            mgr.startDistributing(assignments);
        }
    }

    /**
     * Schedules a timer to fire after a delay.
     *
     * @param delayMs delay in ms
     * @param task task
     */
    protected final void schedule(long delayMs, StateTimerTask task) {
        timers.add(mgr.schedule(delayMs, task));
    }

    /**
     * Schedules a timer to fire repeatedly.
     *
     * @param initialDelayMs initial delay ms
     * @param delayMs delay ms
     * @param task task
     */
    protected final void scheduleWithFixedDelay(long initialDelayMs, long delayMs, StateTimerTask task) {
        timers.add(mgr.scheduleWithFixedDelay(initialDelayMs, delayMs, task));
    }

    /**
     * Indicates that we failed to see our own heartbeat; must be a problem with the
     * internal topic. Assumes the problem is temporary and continues to use the current
     * bucket assignments.
     *
     * @return a new {@link StartState}
     */
    protected final State missedHeartbeat() {
        publish(makeOffline());

        return mgr.goStart();
    }

    /**
     * Indicates that the internal topic failed; this should only be invoked from the
     * StartState. Discards bucket assignments and begins processing everything locally.
     *
     * @return a new {@link InactiveState}
     */
    protected final State internalTopicFailed() {
        publish(makeOffline());
        mgr.startDistributing(null);

        return mgr.goInactive();
    }

    /**
     * Makes a heart beat message.
     *
     * @param timestampMs time, in milliseconds, associated with the message
     *
     * @return a new message
     */
    protected final Heartbeat makeHeartbeat(long timestampMs) {
        return new Heartbeat(getHost(), timestampMs);
    }

    /**
     * Makes an Identification message.
     *
     * @return a new message
     */
    protected Identification makeIdentification() {
        return new Identification(getHost(), getAssignments());
    }

    /**
     * Makes an "offline" message.
     *
     * @return a new message
     */
    protected final Offline makeOffline() {
        return new Offline(getHost());
    }

    /**
     * Makes a query message.
     *
     * @return a new message
     */
    protected final Query makeQuery() {
        return new Query(getHost());
    }

    public final BucketAssignments getAssignments() {
        return mgr.getAssignments();
    }

    public final String getHost() {
        return mgr.getHost();
    }

    public final String getTopic() {
        return mgr.getTopic();
    }

    public final PoolingProperties getProperties() {
        return mgr.getProperties();
    }
}
