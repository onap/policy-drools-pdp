/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018-2019 AT&T Intellectual Property. All rights reserved.
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

import org.onap.policy.drools.pooling.message.BucketAssignments;
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
     * @return pooling properties
     */
    PoolingProperties getProperties();

    /**
     * Gets the host id.
     *
     * @return the host id
     */
    String getHost();

    /**
     * Gets the name of the internal DMaaP topic used by this manager to communicate with
     * its other hosts.
     *
     * @return the name of the internal DMaaP topic
     */
    String getTopic();

    /**
     * Starts distributing requests according to the given bucket assignments.
     *
     * @param assignments must <i>not</i> be {@code null}
     */
    void startDistributing(BucketAssignments assignments);

    /**
     * Gets the current bucket assignments.
     *
     * @return the current bucket assignments, or {@code null} if no assignments have been
     *         made
     */
    BucketAssignments getAssignments();

    /**
     * Publishes a message to the internal topic on the administrative channel.
     *
     * @param msg message to be published
     */
    void publishAdmin(Message msg);

    /**
     * Publishes a message to the internal topic on a particular channel.
     *
     * @param channel channel on which the message should be published
     * @param msg message to be published
     */
    void publish(String channel, Message msg);

    /**
     * Schedules a timer to fire after a delay.
     *
     * @param delayMs delay, in milliseconds
     * @param task task
     * @return a new scheduled task
     */
    CancellableScheduledTask schedule(long delayMs, StateTimerTask task);

    /**
     * Schedules a timer to fire repeatedly.
     *
     * @param initialDelayMs initial delay, in milliseconds
     * @param delayMs delay, in milliseconds
     * @param task task
     * @return a new scheduled task
     */
    CancellableScheduledTask scheduleWithFixedDelay(long initialDelayMs, long delayMs, StateTimerTask task);

    /**
     * Transitions to the "start" state.
     *
     * @return the new state
     */
    State goStart();

    /**
     * Transitions to the "query" state.
     *
     * @return the new state
     */
    State goQuery();

    /**
     * Transitions to the "active" state.
     *
     * @return the new state
     */
    State goActive();

    /**
     * Transitions to the "inactive" state.
     *
     * @return the new state
     */
    State goInactive();

}
