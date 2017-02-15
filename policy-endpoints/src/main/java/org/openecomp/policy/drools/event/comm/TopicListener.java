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

package org.openecomp.policy.drools.event.comm;

/**
 * Listener for event messages entering the Policy Engine
 */
public interface TopicListener {
	
	/**
	 * Notification of a new Event over a given Topic
	 * 
	 * @param commType communication infrastructure type
	 * @param topic topic name
	 * @param event event message as a string
	 * 
	 * @return boolean.  True if the invoking event dispatcher should continue 
	 * dispatching the event to subsequent listeners.  False if it is requested
	 * to the invoking event dispatcher to stop dispatching the same event to
	 * other listeners of less priority.   This mechanism is generally not used.
	 */
	public boolean onTopicEvent(Topic.CommInfrastructure commType, String topic, String event);

}
