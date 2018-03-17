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
 * 
 * A state may have several timers associated with it. When the state is
 * changed, the previous timers are forgotten.
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
	
	// TODO ensure this is invoked
	
	public void cancelTimers() {
		for(ScheduledFuture<?> fut: timers) {
			fut.cancel(false);
		}
	}

	/**
	 * Indicates that the finite state machine is stopping. Sends an "offline"
	 * message to the other hosts.
	 */
	public void stop() {
		publish(makeOffline());
	}

	/**
	 * Processes a message. The default method injects it into the rule engine
	 * and returns {@code null}.
	 * 
	 * @param msg
	 *            message to be processed
	 * @return the new state, or {@code null} if the state is unchanged
	 */
	public State process(Forward msg) {
		mgr.handle(msg);
		return null;
	}

	/**
	 * Processes a message. The default method just returns {@code null}.
	 * 
	 * @param msg
	 *            message to be processed
	 * @return the new state, or {@code null} if the state is unchanged
	 */
	public State process(Heartbeat msg) {
		return null;
	}

	/**
	 * Processes a message. The default method just returns {@code null}.
	 * 
	 * @param msg
	 *            message to be processed
	 * @return the new state, or {@code null} if the state is unchanged
	 */
	public State process(Identification msg) {
		return null;
	}

	/**
	 * Processes a message. The default method simply returns {@code null}.
	 * 
	 * @param msg
	 *            message to be processed
	 * @return the new state, or {@code null} if the state is unchanged
	 */
	public State process(Leader msg) {
		return null;
	}

	/**
	 * Processes a message. The default method just returns {@code null}.
	 * 
	 * @param msg
	 *            message to be processed
	 * @return the new state, or {@code null} if the state is unchanged
	 */
	public State process(Offline msg) {
		return null;
	}

	/**
	 * Processes a message. The default method just returns {@code null}.
	 * 
	 * @param msg
	 *            message to be processed
	 * @return the new state, or {@code null} if the state is unchanged
	 */
	public State process(Query msg) {
		return null;
	}

	public void publish(Identification msg) {
		mgr.publishAdmin(msg);
	}

	public void publish(Leader msg) {
		mgr.publishAdmin(msg);
	}

	public void publish(Offline msg) {
		mgr.publishAdmin(msg);
	}

	public void publish(Query msg) {
		mgr.publishAdmin(msg);
	}

	public void publish(String channel, Forward msg) {
		mgr.publish(channel, msg);
	}

	public void publish(String channel, Heartbeat msg) {
		mgr.publish(channel, msg);
	}

	public void startDistributing(BucketAssignments assignments) {
		mgr.startDistributing(assignments);
	}

	public void schedule(long delayMs, StateTimerTask task) {
		timers.add(mgr.schedule(delayMs, task));
	}
	
	public void scheduleWithFixedDelay(long initialDelayMs, long delayMs, StateTimerTask task) {
		timers.add(mgr.scheduleWithFixedDelay(initialDelayMs, delayMs, task));		
	}

	/**
	 * Makes a heart beat message.
	 * 
	 * @param target
	 *            channel on which the message will be published
	 * 
	 * @return a new message
	 */
	public Heartbeat makeHeartbeat() {
		return new Heartbeat(getHost());
	}

	/**
	 * Makes an "offline" message.
	 * 
	 * @return a new message
	 */
	public Offline makeOffline() {
		return new Offline(getHost());
	}

	/**
	 * Makes a query message.
	 * 
	 * @return a new message
	 */
	public Query makeQuery() {
		return new Query(getHost());
	}

	public String getHost() {
		return mgr.getHost();
	}

}
