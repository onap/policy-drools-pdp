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

import org.openecomp.policy.drools.event.comm.bus.internal.InlineUebTopicSink;
import org.openecomp.policy.common.logging.flexlogger.FlexLogger;
import org.openecomp.policy.common.logging.flexlogger.Logger;
import org.openecomp.policy.drools.properties.PolicyProperties;

/**
 * UEB Topic Sink Factory
 */
public interface UebTopicSinkFactory {
	
	/**
	 * Instantiates a new UEB Topic Writer
	 * 
	 * @param servers list of servers
	 * @param topic topic name
	 * @param apiKey API Key
	 * @param apiSecret API Secret
	 * @param partitionKey Consumer Group
	 * @param managed is this sink endpoint managed?
	 * 
	 * @return an UEB Topic Writer
	 * @throws IllegalArgumentException if invalid parameters are present
	 */
	public UebTopicSink build(List<String> servers, 
								String topic, 
								String apiKey, 
								String apiSecret,
								String partitionKey,
								boolean managed)
			throws IllegalArgumentException;
	
	/**
	 * Creates an UEB Topic Writer based on properties files
	 * 
	 * @param properties Properties containing initialization values
	 * 
	 * @return an UEB Topic Writer
	 * @throws IllegalArgumentException if invalid parameters are present
	 */
	public List<UebTopicSink> build(Properties properties)
			throws IllegalArgumentException;
	
	/**
	 * Instantiates a new UEB Topic Writer
	 * 
	 * @param servers list of servers
	 * @param topic topic name
	 * 
	 * @return an UEB Topic Writer
	 * @throws IllegalArgumentException if invalid parameters are present
	 */
	public UebTopicSink build(List<String> servers, String topic)
			throws IllegalArgumentException;
	
	/**
	 * Destroys an UEB Topic Writer based on a topic
	 * 
	 * @param topic topic name
	 * @throws IllegalArgumentException if invalid parameters are present
	 */
	public void destroy(String topic);

	/**
	 * gets an UEB Topic Writer based on topic name
	 * @param topic the topic name
	 * 
	 * @return an UEB Topic Writer with topic name
	 * @throws IllegalArgumentException if an invalid topic is provided
	 * @throws IllegalStateException if the UEB Topic Reader is 
	 * an incorrect state
	 */
	public UebTopicSink get(String topic)
			   throws IllegalArgumentException, IllegalStateException;
	
	/**
	 * Provides a snapshot of the UEB Topic Writers
	 * @return a list of the UEB Topic Writers
	 */
	public List<UebTopicSink> inventory();

	/**
	 * Destroys all UEB Topic Writers
	 */
	public void destroy();
}

/* ------------- implementation ----------------- */

/**
 * Factory of UEB Reader Topics indexed by topic name
 */
class IndexedUebTopicSinkFactory implements UebTopicSinkFactory {
	// get an instance of logger 
	private static Logger  logger = FlexLogger.getLogger(IndexedUebTopicSinkFactory.class);		
	/**
	 * UEB Topic Name Index
	 */
	protected HashMap<String, UebTopicSink> uebTopicSinks =
			new HashMap<String, UebTopicSink>();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public UebTopicSink build(List<String> servers, 
								String topic, 
								String apiKey, 
								String apiSecret,
								String partitionKey,
								boolean managed) 
			throws IllegalArgumentException {
		
		if (topic == null || topic.isEmpty()) {
			throw new IllegalArgumentException("A topic must be provided");
		}
		
		synchronized (this) {
			if (uebTopicSinks.containsKey(topic)) {
				return uebTopicSinks.get(topic);
			}
			
			UebTopicSink uebTopicWriter = 
					new InlineUebTopicSink(servers, topic, 
										   apiKey, apiSecret,partitionKey);
			
			if (managed)
				uebTopicSinks.put(topic, uebTopicWriter);
			
			return uebTopicWriter;
		}
	}
	

	/**
	 * {@inheritDoc}
	 */
	@Override
	public UebTopicSink build(List<String> servers, String topic) throws IllegalArgumentException {
		return this.build(servers, topic, null, null, null, true);
	}
	

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<UebTopicSink> build(Properties properties) throws IllegalArgumentException {
		
		String writeTopics = properties.getProperty(PolicyProperties.PROPERTY_UEB_SINK_TOPICS);
		if (writeTopics == null || writeTopics.isEmpty()) {
			logger.warn("No topic for UEB Sink " + properties);
			return new ArrayList<UebTopicSink>();
		}
		List<String> writeTopicList = new ArrayList<String>(Arrays.asList(writeTopics.split("\\s*,\\s*")));
		
		synchronized(this) {
			List<UebTopicSink> uebTopicWriters = new ArrayList<UebTopicSink>();
			for (String topic: writeTopicList) {
				
				String servers = properties.getProperty(PolicyProperties.PROPERTY_UEB_SINK_TOPICS + "." + 
				                                        topic + 
				                                        PolicyProperties.PROPERTY_TOPIC_SERVERS_SUFFIX);
				if (servers == null || servers.isEmpty()) {
					logger.error("No UEB servers provided in " + properties);
					continue;
				}
				
				List<String> serverList = new ArrayList<String>(Arrays.asList(servers.split("\\s*,\\s*")));
				
				String apiKey = properties.getProperty(PolicyProperties.PROPERTY_UEB_SINK_TOPICS + 
						                               "." + topic + 
						                               PolicyProperties.PROPERTY_TOPIC_API_KEY_SUFFIX);		 
				String apiSecret = properties.getProperty(PolicyProperties.PROPERTY_UEB_SINK_TOPICS + 
                                                          "." + topic + 
                                                          PolicyProperties.PROPERTY_TOPIC_API_SECRET_SUFFIX);	
				String partitionKey = properties.getProperty(PolicyProperties.PROPERTY_UEB_SINK_TOPICS + 
                                                             "." + topic + 
                                                             PolicyProperties.PROPERTY_TOPIC_SINK_PARTITION_KEY_SUFFIX);
				
				String managedString = properties.getProperty(PolicyProperties.PROPERTY_UEB_SINK_TOPICS + "." + topic +
						                                      PolicyProperties.PROPERTY_MANAGED_SUFFIX);
				boolean managed = true;
				if (managedString != null && !managedString.isEmpty()) {
					managed = Boolean.parseBoolean(managedString);
				}
				
				UebTopicSink uebTopicWriter = this.build(serverList, topic, 
						   						         apiKey, apiSecret, 
						   						         partitionKey, managed);
				uebTopicWriters.add(uebTopicWriter);
			}
			return uebTopicWriters;
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void destroy(String topic) 
		   throws IllegalArgumentException {
		
		if (topic == null || topic.isEmpty()) {
			throw new IllegalArgumentException("A topic must be provided");
		}
		
		UebTopicSink uebTopicWriter;
		synchronized(this) {
			if (!uebTopicSinks.containsKey(topic)) {
				return;
			}
			
			uebTopicWriter = uebTopicSinks.remove(topic);
		}
		
		uebTopicWriter.shutdown();
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void destroy() {
		List<UebTopicSink> writers = this.inventory();
		for (UebTopicSink writer: writers) {
			writer.shutdown();
		}
		
		synchronized(this) {
			this.uebTopicSinks.clear();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public UebTopicSink get(String topic) 
			throws IllegalArgumentException, IllegalStateException {
		
		if (topic == null || topic.isEmpty()) {
			throw new IllegalArgumentException("A topic must be provided");
		}
		
		synchronized(this) {
			if (uebTopicSinks.containsKey(topic)) {
				return uebTopicSinks.get(topic);
			} else {
				throw new IllegalStateException("UebTopicSink for " + topic + " not found");
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized List<UebTopicSink> inventory() {
		 List<UebTopicSink> writers = 
				 new ArrayList<UebTopicSink>(this.uebTopicSinks.values());
		 return writers;
	}
	
}
