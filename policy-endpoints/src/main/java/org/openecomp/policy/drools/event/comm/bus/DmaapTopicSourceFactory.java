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

import org.openecomp.policy.drools.event.comm.bus.internal.SingleThreadedDmaapTopicSource;
import org.openecomp.policy.common.logging.flexlogger.FlexLogger;
import org.openecomp.policy.common.logging.flexlogger.Logger;
import org.openecomp.policy.drools.properties.PolicyProperties;
/**
 * DMAAP Topic Source Factory
 */
public interface DmaapTopicSourceFactory {
	
	/**
	 * Creates an DMAAP Topic Source based on properties files
	 * 
	 * @param properties Properties containing initialization values
	 * 
	 * @return an DMAAP Topic Source
	 * @throws IllegalArgumentException if invalid parameters are present
	 */
	public List<DmaapTopicSource> build(Properties properties)
			throws IllegalArgumentException;
	
	/**
	 * Instantiates a new DMAAP Topic Source
	 * 
	 * @param servers list of servers
	 * @param topic topic name
	 * @param apiKey API Key
	 * @param apiSecret API Secret
	 * @param userName user name
	 * @param password password
	 * @param consumerGroup Consumer Group
	 * @param consumerInstance Consumer Instance
	 * @param fetchTimeout Read Fetch Timeout
	 * @param fetchLimit Fetch Limit
	 * @param managed is this endpoind managed?
	 * 
	 * @return an DMAAP Topic Source
	 * @throws IllegalArgumentException if invalid parameters are present
	 */
	public DmaapTopicSource build(List<String> servers, 
								String topic, 
								String apiKey, 
								String apiSecret, 
								String userName, 
								String password,
								String consumerGroup, 
								String consumerInstance,
								int fetchTimeout,
								int fetchLimit,
								boolean managed)
			throws IllegalArgumentException;
	
	/**
	 * Instantiates a new DMAAP Topic Source
	 * 
	 * @param servers list of servers
	 * @param topic topic name
	 * @param apiKey API Key
	 * @param apiSecret API Secret
	 * 
	 * @return an DMAAP Topic Source
	 * @throws IllegalArgumentException if invalid parameters are present
	 */
	public DmaapTopicSource build(List<String> servers, 
								String topic, 
								String apiKey, 
								String apiSecret)
			throws IllegalArgumentException;

	/**
	 * Instantiates a new DMAAP Topic Source
	 * 
	 * @param uebTopicReaderType Implementation type
	 * @param servers list of servers
	 * @param topic topic name
	 * 
	 * @return an DMAAP Topic Source
	 * @throws IllegalArgumentException if invalid parameters are present
	 */
	public DmaapTopicSource build(List<String> servers, 
								String topic)
			throws IllegalArgumentException;	
	
	/**
	 * Destroys an DMAAP Topic Source based on a topic
	 * 
	 * @param topic topic name
	 * @throws IllegalArgumentException if invalid parameters are present
	 */
	public void destroy(String topic);
	
	/**
	 * Destroys all DMAAP Topic Sources
	 */
	public void destroy();
	
	/**
	 * gets an DMAAP Topic Source based on topic name
	 * @param topic the topic name
	 * @return an DMAAP Topic Source with topic name
	 * @throws IllegalArgumentException if an invalid topic is provided
	 * @throws IllegalStateException if the DMAAP Topic Source is 
	 * an incorrect state
	 */
	public DmaapTopicSource get(String topic)
		   throws IllegalArgumentException, IllegalStateException;
	
	/**
	 * Provides a snapshot of the DMAAP Topic Sources
	 * @return a list of the DMAAP Topic Sources
	 */
	public List<DmaapTopicSource> inventory();
}


/* ------------- implementation ----------------- */

/**
 * Factory of DMAAP Source Topics indexed by topic name
 */

class IndexedDmaapTopicSourceFactory implements DmaapTopicSourceFactory {
	// get an instance of logger 
	private static Logger  logger = FlexLogger.getLogger(IndexedDmaapTopicSourceFactory.class);		
	/**
	 * UEB Topic Name Index
	 */
	protected HashMap<String, DmaapTopicSource> dmaapTopicSources =
			new HashMap<String, DmaapTopicSource>();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public DmaapTopicSource build(List<String> servers, 
								String topic, 
								String apiKey, 
								String apiSecret, 
								String userName, 
								String password,
								String consumerGroup, 
								String consumerInstance,
								int fetchTimeout,
								int fetchLimit,
								boolean managed) 
			throws IllegalArgumentException {
		
		if (topic == null || topic.isEmpty()) {
			throw new IllegalArgumentException("A topic must be provided");
		}
		
		synchronized(this) {
			if (dmaapTopicSources.containsKey(topic)) {
				return dmaapTopicSources.get(topic);
			}
			
			DmaapTopicSource dmaapTopicSource = 
					new SingleThreadedDmaapTopicSource(servers, topic, 
													 apiKey, apiSecret, userName, password,
													 consumerGroup, consumerInstance, 
													 fetchTimeout, fetchLimit);
			
			if (managed)
				dmaapTopicSources.put(topic, dmaapTopicSource);
			
			return dmaapTopicSource;
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<DmaapTopicSource> build(Properties properties) 
			throws IllegalArgumentException {
		
		String readTopics = properties.getProperty(PolicyProperties.PROPERTY_DMAAP_SOURCE_TOPICS);
		if (readTopics == null || readTopics.isEmpty()) {
			logger.warn("No topic for UEB Source " + properties);
			return new ArrayList<DmaapTopicSource>();
		}
		List<String> readTopicList = new ArrayList<String>(Arrays.asList(readTopics.split("\\s*,\\s*")));		
		
		List<DmaapTopicSource> dmaapTopicSource_s = new ArrayList<DmaapTopicSource>();
		synchronized(this) {
			for (String topic: readTopicList) {
				
				String servers = properties.getProperty(PolicyProperties.PROPERTY_DMAAP_SOURCE_TOPICS + "." + 
                                                        topic + 
                                                        PolicyProperties.PROPERTY_TOPIC_SERVERS_SUFFIX);
				
				if (servers == null || servers.isEmpty()) {
					logger.error("No UEB servers provided in " + properties);
					continue;
				}
				
				List<String> serverList = new ArrayList<String>(Arrays.asList(servers.split("\\s*,\\s*")));
				
				String apiKey = properties.getProperty(PolicyProperties.PROPERTY_DMAAP_SOURCE_TOPICS + 
                        							   "." + topic + 
                                                       PolicyProperties.PROPERTY_TOPIC_API_KEY_SUFFIX);
				
				String apiSecret = properties.getProperty(PolicyProperties.PROPERTY_DMAAP_SOURCE_TOPICS + 
						                                  "." + topic + 
                                                          PolicyProperties.PROPERTY_TOPIC_API_SECRET_SUFFIX);
				
				String aafMechId = properties.getProperty(PolicyProperties.PROPERTY_DMAAP_SOURCE_TOPICS + 
								                          "." + topic + 
								                          PolicyProperties.PROPERTY_TOPIC_AAF_MECHID_SUFFIX);

				String aafPassword = properties.getProperty(PolicyProperties.PROPERTY_DMAAP_SOURCE_TOPICS + 
				                           				  "." + topic + 
				                           				  PolicyProperties.PROPERTY_TOPIC_AAF_PASSWORD_SUFFIX);
				
				String consumerGroup = properties.getProperty(PolicyProperties.PROPERTY_DMAAP_SOURCE_TOPICS + 
						                                      "." + topic + 
                                                              PolicyProperties.PROPERTY_TOPIC_SOURCE_CONSUMER_GROUP_SUFFIX);
				
				String consumerInstance = properties.getProperty(PolicyProperties.PROPERTY_DMAAP_SOURCE_TOPICS + 
						                                         "." + topic + 
                                                                 PolicyProperties.PROPERTY_TOPIC_SOURCE_CONSUMER_INSTANCE_SUFFIX);
				
				String fetchTimeoutString = properties.getProperty(PolicyProperties.PROPERTY_DMAAP_SOURCE_TOPICS + 
						                                           "." + topic + 
                                                                   PolicyProperties.PROPERTY_TOPIC_SOURCE_FETCH_TIMEOUT_SUFFIX);
				int fetchTimeout = DmaapTopicSource.DEFAULT_TIMEOUT_MS_FETCH;
				if (fetchTimeoutString != null && !fetchTimeoutString.isEmpty()) {
					try {
						fetchTimeout = Integer.parseInt(fetchTimeoutString);
					} catch (NumberFormatException nfe) {
						logger.warn("Fetch Timeout in invalid format for topic " + topic + ": " + fetchTimeoutString);
					}
				}
					
				String fetchLimitString = properties.getProperty(PolicyProperties.PROPERTY_DMAAP_SOURCE_TOPICS + 
                                                                 "." + topic + 
                                                                 PolicyProperties.PROPERTY_TOPIC_SOURCE_FETCH_TIMEOUT_SUFFIX);
				int fetchLimit = DmaapTopicSource.DEFAULT_LIMIT_FETCH;
				if (fetchLimitString != null && !fetchLimitString.isEmpty()) {
					try {
						fetchLimit = Integer.parseInt(fetchLimitString);
					} catch (NumberFormatException nfe) {
						logger.warn("Fetch Limit in invalid format for topic " + topic + ": " + fetchLimitString);
					}
				}
				
				String managedString = properties.getProperty(PolicyProperties.PROPERTY_DMAAP_SOURCE_TOPICS + 
                                                               "." + topic + 
                                                              PolicyProperties.PROPERTY_MANAGED_SUFFIX);
				boolean managed = true;
				if (managedString != null && !managedString.isEmpty()) {
					managed = Boolean.parseBoolean(managedString);
				}
				
				DmaapTopicSource uebTopicSource = this.build(serverList, topic, 
						   						           apiKey, apiSecret, aafMechId, aafPassword,
						   						           consumerGroup, consumerInstance, 
						   						           fetchTimeout, fetchLimit, managed);
				dmaapTopicSource_s.add(uebTopicSource);
			}
		}
		return dmaapTopicSource_s;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public DmaapTopicSource build(List<String> servers, 
								String topic,
								String apiKey, 
								String apiSecret) {
		return this.build(servers, topic, 
				  		  apiKey, apiSecret, null, null,
				  		  null, null,
				  		  DmaapTopicSource.DEFAULT_TIMEOUT_MS_FETCH,
				  		  DmaapTopicSource.DEFAULT_LIMIT_FETCH,
				  		  true);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public DmaapTopicSource build(List<String> servers, String topic) {
		return this.build(servers, topic, null, null);
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
		
		DmaapTopicSource uebTopicSource;
		
		synchronized(this) {
			if (!dmaapTopicSources.containsKey(topic)) {
				return;
			}
			
			uebTopicSource = dmaapTopicSources.remove(topic);
		}
		
		uebTopicSource.shutdown();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public DmaapTopicSource get(String topic) 
	       throws IllegalArgumentException, IllegalStateException {
		
		if (topic == null || topic.isEmpty()) {
			throw new IllegalArgumentException("A topic must be provided");
		}
		
		synchronized(this) {
			if (dmaapTopicSources.containsKey(topic)) {
				return dmaapTopicSources.get(topic);
			} else {
				throw new IllegalArgumentException("DmaapTopicSource for " + topic + " not found");
			}
		}
	}

	@Override
	public synchronized List<DmaapTopicSource> inventory() {
		 List<DmaapTopicSource> readers = 
				 new ArrayList<DmaapTopicSource>(this.dmaapTopicSources.values());
		 return readers;
	}

	@Override
	public void destroy() {
		List<DmaapTopicSource> readers = this.inventory();
		for (DmaapTopicSource reader: readers) {
			reader.shutdown();
		}
		
		synchronized(this) {
			this.dmaapTopicSources.clear();
		}
	}
	
}

