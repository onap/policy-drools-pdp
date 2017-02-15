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

package org.openecomp.policy.drools.event.comm.bus.internal;

import java.util.List;

import org.apache.commons.collections4.queue.CircularFifoQueue;

import org.openecomp.policy.drools.event.comm.Topic;
import org.openecomp.policy.drools.event.comm.bus.BusTopic;

public abstract class BusTopicBase implements BusTopic, Topic {
	
	protected List<String> servers;

	protected String topic;
	
	protected String apiKey;
	protected String apiSecret;
	
	protected CircularFifoQueue<String> recentEvents = new CircularFifoQueue<String>(10);
	
	public BusTopicBase(List<String> servers, 
						  String topic, 
						  String apiKey, 
						  String apiSecret) 
	throws IllegalArgumentException {
		
		if (servers == null || servers.isEmpty()) {
			throw new IllegalArgumentException("UEB Server(s) must be provided");
		}
		
		if (topic == null || topic.isEmpty()) {
			throw new IllegalArgumentException("An UEB Topic must be provided");
		}
		
		this.servers = servers;
		this.topic = topic;
		
		this.apiKey = apiKey;
		this.apiSecret = apiSecret;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getTopic() {
		return topic;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<String> getServers() {
		return servers;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getApiKey() {
		return apiKey;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getApiSecret() {
		return apiSecret;
	}
	
	/**
	 * @return the recentEvents
	 */
	@Override
	public synchronized String[] getRecentEvents() {
		String[] events = new String[recentEvents.size()];
		return recentEvents.toArray(events);
	}


	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("UebTopicBase [servers=").append(servers).append(", topic=").append(topic).append(", apiKey=")
				.append(apiKey).append(", apiSecret=").append(apiSecret).append("]");
		return builder.toString();
	}

}
