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

package org.onap.policy.drools.pooling;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import org.onap.policy.drools.pooling.message.BucketAssignments;
import org.onap.policy.drools.pooling.message.Forward;
import org.onap.policy.drools.pooling.message.Message;
import org.onap.policy.drools.pooling.state.State;
import org.onap.policy.drools.pooling.state.StateTimerTask;

/**
 * Pooling manager for a single PolicyController.
 */
public interface PoolingManager {

    /**
     * Gets the properties used to configure the manager.
     * 
     * @return
     */
    public PoolingProperties getProperties();

    /**
     * Gets the host id.
     * 
     * @return the host id
     */
    public String getHost();

    /**
     * Gets the name of the internal DMaaP topic used by this manager to
     * communicate with its other hosts.
     * 
     * @return the name of the internal DMaaP topic
     */
    public String getTopic();

    /**
     * Indicates that communication with internal DMaaP topic failed, typically
     * due to a missed heart beat. Stops the PolicyController.
     * 
     * @return a latch that can be used to determine when the controller's
     *         stop() method has completed
     */
    public CountDownLatch internalTopicFailed();

    /**
     * Starts distributing requests according to the given bucket assignments.
     * 
     * @param assignments must <i>not</i> be {@code null}
     */
    public void startDistributing(BucketAssignments assignments);

    /**
     * Gets the current bucket assignments.
     * 
     * @return the current bucket assignments, or {@code null} if no assignments
     *         have been made
     */
    public BucketAssignments getAssignments();

    /**
     * Publishes a message to the internal topic on the administrative channel.
     * 
     * @param msg message to be published
     */
    public void publishAdmin(Message msg);

    /**
     * Publishes a message to the internal topic on a particular channel.
     * 
     * @param channel channel on which the message should be published
     * @param msg message to be published
     */
    public void publish(String channel, Message msg);

    /**
     * Handles a {@link Forward} event that was received from the internal
     * topic.
     * 
     * @param event
     */
    public void handle(Forward event);

    /**
     * Schedules a timer to fire after a delay.
     * 
     * @param delayMs delay, in milliseconds
     * @param task
     * @return a future that can be used to cancel the timer
     */
    public ScheduledFuture<?> schedule(long delayMs, StateTimerTask task);

    /**
     * Schedules a timer to fire repeatedly.
     * 
     * @param initialDelayMs initial delay, in milliseconds
     * @param delayMs delay, in milliseconds
     * @param task
     * @return a future that can be used to cancel the timer
     */
    public ScheduledFuture<?> scheduleWithFixedDelay(long initialDelayMs, long delayMs, StateTimerTask task);

    /**
     * Transitions to the "start" state.
     * 
     * @return the new state
     */
    public State goStart();

    /**
     * Transitions to the "query" state.
     * 
     * @return the new state
     */
    public State goQuery();

    /**
     * Transitions to the "active" state.
     * 
     * @return the new state
     */
    public State goActive();

    /**
     * Transitions to the "inactive" state.
     * 
     * @return the new state
     */
    public State goInactive();

}
