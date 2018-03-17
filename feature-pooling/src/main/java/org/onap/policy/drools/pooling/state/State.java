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
import org.onap.policy.drools.pooling.message.BucketAssignments;
import org.onap.policy.drools.pooling.message.Forward;
import org.onap.policy.drools.pooling.message.Heartbeat;
import org.onap.policy.drools.pooling.message.Identification;
import org.onap.policy.drools.pooling.message.Leader;
import org.onap.policy.drools.pooling.message.Offline;
import org.onap.policy.drools.pooling.message.Query;

/**
 * A state in the finite state machine.
 * <p/>
 * A state may have several timers associated with it. When the state is changed, the previous timers must be cancelled.
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

    public State(PoolingManager mgr) {
        this.mgr = mgr;
    }

    public State(State oldState) {
        this.mgr = oldState.mgr;
    }

    /**
     * Gets the server-side filter to use when polling the DMaaP internal topic.
     * 
     * @return the server-side filter to use.
     */
    public abstract Map<String, Object> getFilter();

    public void cancelTimers() {
        for (ScheduledFuture<?> fut : timers) {
            fut.cancel(false);
        }
    }

    /**
     * Indicates that the finite state machine is stopping. Sends an "offline" message to the other hosts.
     */
    public void stop() {
        publish(makeOffline());
    }

    /**
     * Processes a message. The default method passes it to the manager to handle and returns {@code null}.
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
     * Processes a message. The default method replaces the bucket assignments with the new assignments, and then
     * returns {@code null}.
     * 
     * @param msg message to be processed
     * @return the new state, or {@code null} if the state is unchanged
     */
    public State process(Leader msg) {
        BucketAssignments asgn = msg.getAssignments();
        if (asgn == null) {
            return null;
        }

        if (asgn.getLeader() != null) {
            startDistributing(asgn);
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

    protected void publish(Identification msg) {
        mgr.publishAdmin(msg);
    }

    protected void publish(Leader msg) {
        mgr.publishAdmin(msg);
    }

    protected void publish(Offline msg) {
        mgr.publishAdmin(msg);
    }

    protected void publish(Query msg) {
        mgr.publishAdmin(msg);
    }

    protected void publish(String channel, Forward msg) {
        mgr.publish(channel, msg);
    }

    protected void publish(String channel, Heartbeat msg) {
        mgr.publish(channel, msg);
    }

    protected void startDistributing(BucketAssignments assignments) {
        mgr.startDistributing(assignments);
    }

    protected void schedule(long delayMs, StateTimerTask task) {
        timers.add(mgr.schedule(delayMs, task));
    }

    protected void scheduleWithFixedDelay(long initialDelayMs, long delayMs, StateTimerTask task) {
        timers.add(mgr.scheduleWithFixedDelay(initialDelayMs, delayMs, task));
    }

    protected State internalTopicFailed() {
        mgr.internalTopicFailed();
        return new InactiveState(this);
    }

    public String getTopic() {
        return mgr.getTopic();
    }

    /**
     * Makes a heart beat message.
     * 
     * @return a new message
     */
    protected Heartbeat makeHeartbeat() {
        return new Heartbeat(getHost());
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

    public String getHost() {
        return mgr.getHost();
    }

}
