/*-
 * ============LICENSE_START=======================================================
 * policy-endpoints
 * ================================================================================
 * Copyright (C) 2017-2018 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.event.comm.bus;

import org.onap.policy.drools.event.comm.TopicSource;

/**
 * Generic Topic Source for UEB/DMAAP Communication Infrastructure
 *
 */
public interface BusTopicSource extends ApiKeyEnabled, TopicSource {
	
	/**
	 * Default Consumer Instance Value
	 */
    @Deprecated
	public static String DEFAULT_CONSUMER_INSTANCE = "0";
	
	/**
	 * Default Timeout fetching in milliseconds
	 */
	public static int DEFAULT_TIMEOUT_MS_FETCH = 15000;
	
	/**
	 * Default maximum number of messages fetch at the time
	 */
	public static int DEFAULT_LIMIT_FETCH = 100;
	
	/**
	 * Definition of No Timeout fetching
	 */
	public static int NO_TIMEOUT_MS_FETCH = -1;
	
	/**
	 * Definition of No limit fetching
	 */
	public static int NO_LIMIT_FETCH = -1;
	
	/**
	 * gets the consumer group
	 * 
	 * @return consumer group
	 */
	public String getConsumerGroup();
	
	/**
	 * gets the consumer instance
	 * 
	 * @return consumer instance
	 */
	public String getConsumerInstance();
	
	/**
	 * gets the fetch timeout
	 * 
	 * @return fetch timeout
	 */
	public int getFetchTimeout();
	
	/**
	 * gets the fetch limit
	 * 
	 * @return fetch limit
	 */
	public int getFetchLimit();
}
