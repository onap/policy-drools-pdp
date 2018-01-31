/*-
 * ============LICENSE_START=======================================================
 * policy-endpoints
 * ================================================================================
 * Copyright (C) 2017 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.event.comm;

/**
 * Marks a Topic entity as registerable
 */
public interface TopicRegisterable {

	/**
	 * Register for notification of events with this Topic Entity
	 *
	 * @param topicListener the listener of events
	 */
	public void register(TopicListener topicListener);

	/**
	 * Unregisters for notification of events with this Topic Entity
	 *
	 * @param topicListener the listener of events
	 */
	public void unregister(TopicListener topicListener);

}
