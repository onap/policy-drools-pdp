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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
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

/**
 * A state in the finite state machine.
 * <p>
 * A state may have several timers associated with it, which must be cancelled whenever
 * the state is changed. Assumes that timers are not continuously added to the same state.
 */
public abstract class State {

    /**
     * Host pool manager.
     */
    private final PoolingManager mgr;

    /**
     * Timers added by this state.
     */
    private final List<ScheduledFuture<?>> timers = new LinkedList<>();

    /**
     * 
     * @param mgr
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
        for (ScheduledFuture<?> fut : timers) {
            fut.cancel(false);
        }
    }

    /**
     * Starts the state.
     */
    public void start() {

    }

    /**
     * Indicates that the finite state machine is stopping. Sends an "offline" message to
     * the other hosts.
     */
    public void stop() {
        publish(makeOffline());
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
    public final State goQuery() {
        return mgr.goQuery();
    }

    /**
     * Transitions to the "active" state.
     * 
     * @return the new state
     */
    public final State goActive() {
        return mgr.goActive();
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
        mgr.handle(msg);
        return null;
    }

    /**
     * Processes a message. The default method just returns {@code null}.
     * 
     * @param msg message to be processed
     * @return the new state, or {@code null} if the state is unchanged
     */
    public State process(Heartbeat msg) {
        return null;
    }

    /**
     * Processes a message. The default method just returns {@code null}.
     * 
     * @param msg message to be processed
     * @return the new state, or {@code null} if the state is unchanged
     */
    public State process(Identification msg) {
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
        if(isValid(msg)) {
            startDistributing(msg.getAssignments());            
        }

        return null;
    }

    /**
     * Determines if a message is valid and did not originate
     * from this host.
     * @param msg   message to be validated
     * @return {@code true} if the message is valid, {@code false} otherwise
     */
    protected boolean isValid(Leader msg) {
        BucketAssignments asgn = msg.getAssignments();
        if (asgn == null) {
            return false;
        }

        // ignore Leader messages from ourself
        String source = msg.getSource();
        if (source == null || source.equals(getHost())) {
            return false;
        }

        // the new leader must equal the source
        return source.equals(asgn.getLeader());
    }

    /**
     * Processes a message. The default method just returns {@code null}.
     * 
     * @param msg message to be processed
     * @return the new state, or {@code null} if the state is unchanged
     */
    public State process(Offline msg) {
        return null;
    }

    /**
     * Processes a message. The default method just returns {@code null}.
     * 
     * @param msg message to be processed
     * @return the new state, or {@code null} if the state is unchanged
     */
    public State process(Query msg) {
        return null;
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
     * @param channel
     * @param msg message to be published
     */
    protected final void publish(String channel, Forward msg) {
        mgr.publish(channel, msg);
    }

    /**
     * Publishes a message on the specified channel.
     * 
     * @param channel
     * @param msg message to be published
     */
    protected final void publish(String channel, Heartbeat msg) {
        mgr.publish(channel, msg);
    }

    /**
     * Starts distributing messages using the specified bucket assignments.
     * 
     * @param assignments
     */
    protected final void startDistributing(BucketAssignments assignments) {
        if (assignments != null) {
            mgr.startDistributing(assignments);
        }
    }

    /**
     * Schedules a timer to fire after a delay.
     * 
     * @param delayMs
     * @param task
     */
    protected final void schedule(long delayMs, StateTimerTask task) {
        timers.add(mgr.schedule(delayMs, task));
    }

    /**
     * Schedules a timer to fire repeatedly.
     * 
     * @param initialDelayMs
     * @param delayMs
     * @param task
     */
    protected final void scheduleWithFixedDelay(long initialDelayMs, long delayMs, StateTimerTask task) {
        timers.add(mgr.scheduleWithFixedDelay(initialDelayMs, delayMs, task));
    }

    /**
     * Indicates that the internal topic failed.
     * 
     * @return a new {@link InactiveState}
     */
    protected final State internalTopicFailed() {
        publish(makeOffline());
        mgr.internalTopicFailed();

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
