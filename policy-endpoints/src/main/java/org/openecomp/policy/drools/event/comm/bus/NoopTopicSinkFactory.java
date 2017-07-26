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
package org.openecomp.policy.drools.event.comm.bus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.openecomp.policy.drools.properties.PolicyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Noop Topic Sink Factory
 */
public interface NoopTopicSinkFactory {
	
	/**
	 * Creates noop topic sinks based on properties files
	 * 
	 * @param properties Properties containing initialization values
	 * 
	 * @return a noop topic sink
	 * @throws IllegalArgumentException if invalid parameters are present
	 */
	public List<NoopTopicSink> build(Properties properties) 
			throws IllegalArgumentException;

	/**
	 * builds a noop sink
	 *  
	 * @param servers list of servers
	 * @param topic topic name
	 * @param managed is this sink endpoint managed?
	 * @return a noop topic sink
	 * @throws IllegalArgumentException if invalid parameters are present
	 */
	public NoopTopicSink build(List<String> servers, String topic, boolean managed) 
			throws IllegalArgumentException;
	
	/**
	 * Destroys a sink based on the topic
	 * 
	 * @param topic topic name
	 * @throws IllegalArgumentException if invalid parameters are present
	 */
	public void destroy(String topic);

	/**
	 * gets a sink based on topic name
	 * @param topic the topic name
	 * 
	 * @return a sink with topic name
	 * @throws IllegalArgumentException if an invalid topic is provided
	 * @throws IllegalStateException if the sink is in an incorrect state
	 */
	public NoopTopicSink get(String topic)
			   throws IllegalArgumentException, IllegalStateException;
	
	/**
	 * Provides a snapshot of the UEB Topic Writers
	 * @return a list of the UEB Topic Writers
	 */
	public List<NoopTopicSink> inventory();

	/**
	 * Destroys all sinks
	 */
	public void destroy();
}

/* ------------- implementation ----------------- */

/**
 * Factory of noop sinks
 */
class IndexedNoopTopicSinkFactory implements NoopTopicSinkFactory {
	/**
	 * Logger 
	 */
	private static Logger logger = LoggerFactory.getLogger(IndexedUebTopicSinkFactory.class);	
	
	/**
	 * noop topic sinks map
	 */
	protected HashMap<String, NoopTopicSink> noopTopicSinks = new HashMap<String, NoopTopicSink>();

	@Override
	public List<NoopTopicSink> build(Properties properties) throws IllegalArgumentException {
		
		String sinkTopics = properties.getProperty(PolicyProperties.PROPERTY_NOOP_SINK_TOPICS);
		if (sinkTopics == null || sinkTopics.isEmpty()) {
			logger.info("{}: no topic for noop sink", this);
			return new ArrayList<NoopTopicSink>();
		}
		
		List<String> sinkTopicList = new ArrayList<String>(Arrays.asList(sinkTopics.split("\\s*,\\s*")));
		List<NoopTopicSink> newSinks = new ArrayList<NoopTopicSink>();
		synchronized(this) {
			for (String topic: sinkTopicList) {
				if (this.noopTopicSinks.containsKey(topic)) {
					newSinks.add(this.noopTopicSinks.get(topic));
					continue;
				}
				
				String servers = properties.getProperty(PolicyProperties.PROPERTY_NOOP_SINK_TOPICS + "." + 
				                                        topic + 
				                                        PolicyProperties.PROPERTY_TOPIC_SERVERS_SUFFIX);
				
				if (servers == null || servers.isEmpty()) 
					servers = "noop";
				
				List<String> serverList = new ArrayList<String>(Arrays.asList(servers.split("\\s*,\\s*")));
				
				String managedString = properties.getProperty(PolicyProperties.PROPERTY_UEB_SINK_TOPICS + "." + topic +
						                                      PolicyProperties.PROPERTY_MANAGED_SUFFIX);
				boolean managed = true;
				if (managedString != null && !managedString.isEmpty()) {
					managed = Boolean.parseBoolean(managedString);
				}			
				
				NoopTopicSink noopSink = this.build(serverList, topic, managed);
				newSinks.add(noopSink);
			}
			return newSinks;
		}
	}

	@Override
	public NoopTopicSink build(List<String> servers, String topic, boolean managed) throws IllegalArgumentException {
		if (servers == null) {
			servers = new ArrayList<>();
		}
		
		if (servers.isEmpty()) {
			servers.add("noop");
		}

		if (topic == null || topic.isEmpty()) {
			throw new IllegalArgumentException("A topic must be provided");
		}
		
		synchronized (this) {
			if (noopTopicSinks.containsKey(topic)) {
				return noopTopicSinks.get(topic);
			}
			
			NoopTopicSink sink = 
					new NoopTopicSink(servers, topic);
			
			if (managed)
				noopTopicSinks.put(topic, sink);
			
			return sink;
		}
	}

	@Override
	public void destroy(String topic) {		
		if (topic == null || topic.isEmpty()) {
			throw new IllegalArgumentException("A topic must be provided");
		}
		
		NoopTopicSink noopSink;
		synchronized(this) {
			if (!noopTopicSinks.containsKey(topic)) {
				return;
			}
			
			noopSink = noopTopicSinks.remove(topic);
		}
		
		noopSink.shutdown();
	}
	
	@Override
	public void destroy() {
		List<NoopTopicSink> sinks = this.inventory();
		for (NoopTopicSink sink: sinks) {
			sink.shutdown();
		}
		
		synchronized(this) {
			this.noopTopicSinks.clear();
		}
	}

	@Override
	public NoopTopicSink get(String topic) throws IllegalArgumentException, IllegalStateException {
		if (topic == null || topic.isEmpty()) {
			throw new IllegalArgumentException("A topic must be provided");
		}
		
		synchronized(this) {
			if (noopTopicSinks.containsKey(topic)) {
				return noopTopicSinks.get(topic);
			} else {
				throw new IllegalStateException("DmaapTopicSink for " + topic + " not found");
			}
		}
	}

	@Override
	public List<NoopTopicSink> inventory() {
		 return new ArrayList<>(this.noopTopicSinks.values());
	}
}
