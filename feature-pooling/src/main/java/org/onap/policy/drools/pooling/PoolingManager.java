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
import org.onap.policy.drools.pooling.message.Forward;
import org.onap.policy.drools.pooling.message.Message;
import org.onap.policy.drools.pooling.state.StateTimerTask;

public interface PoolingManager {

	/**
	 * Gets the host id.
	 * 
	 * @return the host id
	 */
	public String getHost();

	public String getTopic();
	
	public void internalTopicFailed();

	/**
	 * Starts distributing requests according to the given bucket assignments.
	 * 
	 * @param assignments
	 *            must <i>not</i> be {@code null}
	 */
	public void startDistributing(BucketAssignments assignments);

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

	public void handle(Forward event);

	/**
	 * Schedules a timer to fire after a delay.
	 * 
	 * @param delayMs
	 * @param task
	 * @return a future that can be used to cancel the timer
	 */
	public ScheduledFuture<?> schedule(long delayMs, StateTimerTask task);

	/**
	 * Schedules a timer to fire repeatedly.
	 * 
	 * @param initialDelayMs
	 * @param delayMs
	 * @param task
	 * @return
	 */
	public ScheduledFuture<?> scheduleWithFixedDelay(long initialDelayMs, long delayMs, StateTimerTask task);

}
