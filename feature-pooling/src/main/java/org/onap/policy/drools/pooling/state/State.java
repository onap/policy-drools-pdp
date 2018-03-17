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
import org.onap.policy.drools.pooling.message.Offline;
import org.onap.policy.drools.pooling.message.Query;

/**
 * A state in the finite state machine.
 * <p>
 * A state may have several timers associated with it, which must be cancelled
 * whenever the state is changed. Assumes that timers are not continuously added
 * to the same state.
 */
public abstract class State {

    /**
     * Used to generates new states. May be changed by junit tests.
     */
    private static StateFactory factory;

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
     * Constructs the state by copying internal data from the previous state.
     * 
     * @param oldState previous state
     */
    public State(State oldState) {
        this.mgr = oldState.mgr;
    }

    /**
     * Gets the server-side filter to use when polling the DMaaP internal topic.
     * 
     * @return the server-side filter to use.
     */
    public abstract Map<String, Object> getFilter();

    /**
     * @return the factory used to generate states
     */
    protected static StateFactory getFactory() {
        return factory;
    }

    /**
     * Sets the factory used to generate states. This should only be used by
     * junit tests.
     * 
     * @param factory the new factory used to generate states
     */
    protected static void setFactory(StateFactory factory) {
        State.factory = factory;
    }

    /**
     * Cancels the timers added by this state.
     */
    public void cancelTimers() {
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
     * Indicates that the finite state machine is stopping. Sends an "offline"
     * message to the other hosts.
     */
    public void stop() {
        publish(makeOffline());
    }

    /**
     * Transitions to the "start" state.
     * 
     * @return the new state
     */
    public State goStart() {
        return factory.goStart(this);
    }

    /**
     * Transitions to the "query" state.
     * 
     * @param assignments known bucket assignments, or {@code null} if they
     *        are still unknown
     * @return the new state
     */
    public State goQuery(BucketAssignments assignments) {
        return factory.goQuery(this, assignments);
    }

    /**
     * Transitions to the "active" state.
     * 
     * @param assignments known bucket assignments, or {@code null} if they
     *        are still unknown
     * @return the new state
     */
    public State goActive(BucketAssignments assignments) {
        return factory.goActive(this, assignments);
    }

    /**
     * Transitions to the "inactive" state.
     * 
     * @return the new state
     */
    protected State goInactive() {
        return factory.goInactive(this);
    }

    /**
     * Processes a message. The default method passes it to the manager to
     * handle and returns {@code null}.
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
     * Processes a message. The default method replaces the bucket assignments
     * with the new assignments, and then returns {@code null}.
     * 
     * @param msg message to be processed
     * @return the new state, or {@code null} if the state is unchanged
     */
    public State process(Leader msg) {
        startDistributing(msg.getAssignments());
        return null;
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
    protected void publish(Identification msg) {
        mgr.publishAdmin(msg);
    }

    /**
     * Publishes a message.
     * 
     * @param msg message to be published
     */
    protected void publish(Leader msg) {
        mgr.publishAdmin(msg);
    }

    /**
     * Publishes a message.
     * 
     * @param msg message to be published
     */
    protected void publish(Offline msg) {
        mgr.publishAdmin(msg);
    }

    /**
     * Publishes a message.
     * 
     * @param msg message to be published
     */
    protected void publish(Query msg) {
        mgr.publishAdmin(msg);
    }

    /**
     * Publishes a message on the specified channel.
     * 
     * @param channel
     * @param msg message to be published
     */
    protected void publish(String channel, Forward msg) {
        mgr.publish(channel, msg);
    }

    /**
     * Publishes a message on the specified channel.
     * 
     * @param channel
     * @param msg message to be published
     */
    protected void publish(String channel, Heartbeat msg) {
        mgr.publish(channel, msg);
    }

    /**
     * Starts distributing messages using the specified bucket assignments.
     * 
     * @param assignments
     */
    protected void startDistributing(BucketAssignments assignments) {
        if(assignments != null) {
            mgr.startDistributing(assignments);
        }
    }

    /**
     * Schedules a timer to fire after a delay.
     * 
     * @param delayMs
     * @param task
     */
    protected void schedule(long delayMs, StateTimerTask task) {
        timers.add(mgr.schedule(delayMs, task));
    }

    /**
     * Schedules a timer to fire repeatedly.
     * 
     * @param initialDelayMs
     * @param delayMs
     * @param task
     */
    protected void scheduleWithFixedDelay(long initialDelayMs, long delayMs, StateTimerTask task) {
        timers.add(mgr.scheduleWithFixedDelay(initialDelayMs, delayMs, task));
    }

    /**
     * Indicates that the internal topic failed.
     * 
     * @return a new {@link InactiveState}
     */
    protected State internalTopicFailed() {
        mgr.internalTopicFailed();
        return factory.goInactive(this);
    }

    /**
     * Makes a heart beat message.
     * 
     * @param timestampMs time, in milliseconds, associated with the message
     * 
     * @return a new message
     */
    protected Heartbeat makeHeartbeat(long timestampMs) {
        return new Heartbeat(getHost(), timestampMs);
    }

    /**
     * Makes an "offline" message.
     * 
     * @return a new message
     */
    protected Offline makeOffline() {
        return new Offline(getHost());
    }

    /**
     * Makes a query message.
     * 
     * @return a new message
     */
    protected Query makeQuery() {
        return new Query(getHost());
    }

    public final String getHost() {
        return mgr.getHost();
    }

    public final String getTopic() {
        return mgr.getTopic();
    }

    public PoolingProperties getProperties() {
        return mgr.getProperties();
    }

    /**
     * Factory used to generate new states.
     */
    protected static class StateFactory {

        /**
         * Transitions to the "start" state.
         * 
         * @param oldState the old state
         * @return the new state
         */
        public State goStart(State oldState) {
            return new StartState(oldState);
        }

        /**
         * Transitions to the "query" state.
         * 
         * @param oldState the old state
         * @param assignments known bucket assignments, or {@code null} if they
         *        are still unknown
         * @return the new state
         */
        public State goQuery(State oldState, BucketAssignments assignments) {
            return new QueryState(oldState, assignments);
        }

        /**
         * Transitions to the "active" state.
         * 
         * @param oldState the old state
         * @param assignments known bucket assignments, or {@code null} if they
         *        are still unknown
         * @return the new state
         */
        public State goActive(State oldState, BucketAssignments assignments) {
            return new ActiveState(oldState, assignments);
        }

        /**
         * Transitions to the "inactive" state.
         * 
         * @param oldState the old state
         * @return the new state
         */
        public State goInactive(State oldState) {
            return new InactiveState(oldState);
        }

    }
}
