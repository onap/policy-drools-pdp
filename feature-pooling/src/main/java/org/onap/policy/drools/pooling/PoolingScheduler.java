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

package org.onap.policy.drools.pooling;

import java.util.concurrent.ScheduledFuture;

import org.onap.policy.drools.pooling.message.BucketAssignments;
import org.onap.policy.drools.pooling.message.Message;
import org.onap.policy.drools.pooling.state.StateTimerTask;

public interface PoolingScheduler {

	/**
	 * Gets the host id.
	 * 
	 * @return the host id
	 */
	public String getHost();

	/**
	 * Starts distributing requests according to the given bucket assignments.
	 * 
	 * @param assignments
	 */
	void startDistributing(BucketAssignments assignments);

	/**
	 * Stops distributing requests.
	 */
	public void stopDistributing();

	/**
	 * Publishes a message, on the administrative channel, to the internal
	 * topic.
	 * 
	 * @param msg
	 *            message to be published
	 */
	public void publishAdmin(Message msg);

	/**
	 * Publishes a message, on a particular channel, to the internal topic.
	 * 
	 * @param channel
	 *            channel on which the message should be published
	 * @param msg
	 *            message to be published
	 */
	public void publish(String channel, Message msg);

	/**
	 * Injects an event into the rule engine.
	 * 
	 * @param sessName
	 * @param encodedReq
	 */
	public void inject(String sessName, String encodedReq);

	/**
	 * Schedules a timer to fire a task one time, after a delay.
	 * 
	 * @param delay
	 * @param task
	 * @return a future that can be used to cancel the timer
	 */
	public ScheduledFuture<?> schedule(long delay, StateTimerTask task);

	/**
	 * Schedules a timer to fire a task repeatedly.
	 * 
	 * @param initialDelay
	 * @param delay
	 * @param task
	 * @return a future that can be used to cancel the timer
	 */
	public ScheduledFuture<?> scheduleWithFixedDelay(long initialDelay, long delay, StateTimerTask task);

}
