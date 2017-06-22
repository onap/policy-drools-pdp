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

import java.util.List;

/**
 * Essential Topic Data
 */
public interface Topic {
	
	public static final String NETWORK_LOGGER = "network";
	
	/**
	 * Underlying Communication infrastructure Types
	 */
	public enum CommInfrastructure {
		/**
		 * UEB Communication Infrastructure
		 */
		UEB,
		/**
		 * DMAAP Communication Infrastructure
		 */		
		DMAAP,
		/**
		 * REST Communication Infrastructure
		 */				
		REST
	}
	
	/**
	 * gets the topic name
	 * 
	 * @return topic name
	 */
	public String getTopic();
	
	/**
	 * gets the communication infrastructure type
	 * @return
	 */
	public CommInfrastructure getTopicCommInfrastructure();
	
	/**
	 * return list of servers
	 * @return bus servers
	 */
	public List<String> getServers();	

	/**
	 * get the more recent events in this topic entity
	 * 
	 * @return list of most recent events
	 */
	public String[] getRecentEvents();

}
