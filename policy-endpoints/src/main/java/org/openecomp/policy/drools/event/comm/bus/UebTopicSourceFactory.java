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

import org.openecomp.policy.drools.event.comm.bus.internal.SingleThreadedUebTopicSource;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.openecomp.policy.drools.properties.PolicyProperties;

/**
 * UEB Topic Source Factory
 */
public interface UebTopicSourceFactory {
	
	/**
	 * Creates an UEB Topic Source based on properties files
	 * 
	 * @param properties Properties containing initialization values
	 * 
	 * @return an UEB Topic Source
	 * @throws IllegalArgumentException if invalid parameters are present
	 */
	public List<UebTopicSource> build(Properties properties)
			throws IllegalArgumentException;
	
	/**
	 * Instantiates a new UEB Topic Source
	 * 
	 * @param servers list of servers
	 * @param topic topic name
	 * @param apiKey API Key
	 * @param apiSecret API Secret
	 * @param consumerGroup Consumer Group
	 * @param consumerInstance Consumer Instance
	 * @param fetchTimeout Read Fetch Timeout
	 * @param fetchLimit Fetch Limit
	 * @param managed is this source endpoint managed?
	 * 
	 * @return an UEB Topic Source
	 * @throws IllegalArgumentException if invalid parameters are present
	 */
	public UebTopicSource build(List<String> servers, 
								String topic, 
								String apiKey, 
								String apiSecret, 
								String consumerGroup, 
								String consumerInstance,
								int fetchTimeout,
								int fetchLimit,
								boolean managed,
								boolean useHttps,
								boolean allowSelfSignedCerts)
			throws IllegalArgumentException;
	
	/**
	 * Instantiates a new UEB Topic Source
	 * 
	 * @param servers list of servers
	 * @param topic topic name
	 * @param apiKey API Key
	 * @param apiSecret API Secret
	 * 
	 * @return an UEB Topic Source
	 * @throws IllegalArgumentException if invalid parameters are present
	 */
	public UebTopicSource build(List<String> servers, 
								String topic, 
								String apiKey, 
								String apiSecret)
			throws IllegalArgumentException;

	/**
	 * Instantiates a new UEB Topic Source
	 * 
	 * @param uebTopicSourceType Implementation type
	 * @param servers list of servers
	 * @param topic topic name
	 * 
	 * @return an UEB Topic Source
	 * @throws IllegalArgumentException if invalid parameters are present
	 */
	public UebTopicSource build(List<String> servers, 
								String topic)
			throws IllegalArgumentException;	
	
	/**
	 * Destroys an UEB Topic Source based on a topic
	 * 
	 * @param topic topic name
	 * @throws IllegalArgumentException if invalid parameters are present
	 */
	public void destroy(String topic);
	
	/**
	 * Destroys all UEB Topic Sources
	 */
	public void destroy();
	
	/**
	 * gets an UEB Topic Source based on topic name
	 * @param topic the topic name
	 * @return an UEB Topic Source with topic name
	 * @throws IllegalArgumentException if an invalid topic is provided
	 * @throws IllegalStateException if the UEB Topic Source is 
	 * an incorrect state
	 */
	public UebTopicSource get(String topic)
		   throws IllegalArgumentException, IllegalStateException;
	
	/**
	 * Provides a snapshot of the UEB Topic Sources
	 * @return a list of the UEB Topic Sources
	 */
	public List<UebTopicSource> inventory();
}

/* ------------- implementation ----------------- */

/**
 * Factory of UEB Source Topics indexed by topic name
 */
class IndexedUebTopicSourceFactory implements UebTopicSourceFactory {
	/**
	 * Logger
	 */
	private static Logger logger = LoggerFactory.getLogger(IndexedUebTopicSourceFactory.class);	
	
	/**
	 * UEB Topic Name Index
	 */
	protected HashMap<String, UebTopicSource> uebTopicSources =
			new HashMap<String, UebTopicSource>();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public UebTopicSource build(List<String> servers, 
								String topic, 
								String apiKey, 
								String apiSecret, 
								String consumerGroup, 
								String consumerInstance,
								int fetchTimeout,
								int fetchLimit,
								boolean managed,
								boolean useHttps,
								boolean allowSelfSignedCerts) 
	throws IllegalArgumentException {
		if (servers == null || servers.isEmpty()) {
			throw new IllegalArgumentException("UEB Server(s) must be provided");
		}
		
		if (topic == null || topic.isEmpty()) {
			throw new IllegalArgumentException("A topic must be provided");
		}
		
		synchronized(this) {
			if (uebTopicSources.containsKey(topic)) {
				return uebTopicSources.get(topic);
			}
			
			UebTopicSource uebTopicSource = 
					new SingleThreadedUebTopicSource(servers, topic, 
													 apiKey, apiSecret,
													 consumerGroup, consumerInstance, 
													 fetchTimeout, fetchLimit, useHttps, allowSelfSignedCerts);
			
			if (managed)
				uebTopicSources.put(topic, uebTopicSource);
			
			return uebTopicSource;
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<UebTopicSource> build(Properties properties) 
			throws IllegalArgumentException {
		
		String readTopics = properties.getProperty(PolicyProperties.PROPERTY_UEB_SOURCE_TOPICS);
		if (readTopics == null || readTopics.isEmpty()) {
			logger.info("{}: no topic for UEB Source", this);
			return new ArrayList<UebTopicSource>();
		}
		List<String> readTopicList = new ArrayList<String>(Arrays.asList(readTopics.split("\\s*,\\s*")));		
		
		List<UebTopicSource> newUebTopicSources = new ArrayList<UebTopicSource>();
		synchronized(this) {
			for (String topic: readTopicList) {
				if (this.uebTopicSources.containsKey(topic)) {
					newUebTopicSources.add(this.uebTopicSources.get(topic));
					continue;
				}
				
				String servers = properties.getProperty(PolicyProperties.PROPERTY_UEB_SOURCE_TOPICS + "." + 
                                                        topic + 
                                                        PolicyProperties.PROPERTY_TOPIC_SERVERS_SUFFIX);
				
				if (servers == null || servers.isEmpty()) {
					logger.error("{}: no UEB servers configured for sink {}", this, topic);
					continue;
				}
				
				List<String> serverList = new ArrayList<String>(Arrays.asList(servers.split("\\s*,\\s*")));
				
				String apiKey = properties.getProperty(PolicyProperties.PROPERTY_UEB_SOURCE_TOPICS + 
                        							   "." + topic + 
                                                       PolicyProperties.PROPERTY_TOPIC_API_KEY_SUFFIX);
				
				String apiSecret = properties.getProperty(PolicyProperties.PROPERTY_UEB_SOURCE_TOPICS + 
						                                  "." + topic + 
                                                          PolicyProperties.PROPERTY_TOPIC_API_SECRET_SUFFIX);
				
				String consumerGroup = properties.getProperty(PolicyProperties.PROPERTY_UEB_SOURCE_TOPICS + 
						                                      "." + topic + 
                                                              PolicyProperties.PROPERTY_TOPIC_SOURCE_CONSUMER_GROUP_SUFFIX);
				
				String consumerInstance = properties.getProperty(PolicyProperties.PROPERTY_UEB_SOURCE_TOPICS + 
						                                         "." + topic + 
                                                                 PolicyProperties.PROPERTY_TOPIC_SOURCE_CONSUMER_INSTANCE_SUFFIX);
				
				String fetchTimeoutString = properties.getProperty(PolicyProperties.PROPERTY_UEB_SOURCE_TOPICS + 
						                                           "." + topic + 
                                                                   PolicyProperties.PROPERTY_TOPIC_SOURCE_FETCH_TIMEOUT_SUFFIX);
				int fetchTimeout = UebTopicSource.DEFAULT_TIMEOUT_MS_FETCH;
				if (fetchTimeoutString != null && !fetchTimeoutString.isEmpty()) {
					try {
						fetchTimeout = Integer.parseInt(fetchTimeoutString);
					} catch (NumberFormatException nfe) {
						logger.warn("{}: fetch timeout {} is in invalid format for topic {} ", 
							        this, fetchTimeoutString, topic);
					}
				}
					
				String fetchLimitString = properties.getProperty(PolicyProperties.PROPERTY_UEB_SOURCE_TOPICS + 
                                                                 "." + topic + 
                                                                 PolicyProperties.PROPERTY_TOPIC_SOURCE_FETCH_TIMEOUT_SUFFIX);
				int fetchLimit = UebTopicSource.DEFAULT_LIMIT_FETCH;
				if (fetchLimitString != null && !fetchLimitString.isEmpty()) {
					try {
						fetchLimit = Integer.parseInt(fetchLimitString);
					} catch (NumberFormatException nfe) {
						logger.warn("{}: fetch limit {} is in invalid format for topic {} ", 
						            this, fetchLimitString, topic);
					}
				}
				
				String managedString = properties.getProperty(PolicyProperties.PROPERTY_UEB_SOURCE_TOPICS + "." +
						                                      topic + PolicyProperties.PROPERTY_MANAGED_SUFFIX);
				boolean managed = true;
				if (managedString != null && !managedString.isEmpty()) {
					managed = Boolean.parseBoolean(managedString);
				}
			
				String useHttpsString = properties.getProperty(PolicyProperties.PROPERTY_UEB_SOURCE_TOPICS + "." + topic +
						PolicyProperties.PROPERTY_HTTP_HTTPS_SUFFIX);

					//default is to use HTTP if no https property exists
				boolean useHttps = false;
				if (useHttpsString != null && !useHttpsString.isEmpty()){
					useHttps = Boolean.parseBoolean(useHttpsString);
				}

				String allowSelfSignedCertsString = properties.getProperty(PolicyProperties.PROPERTY_UEB_SOURCE_TOPICS + "." + topic +
						PolicyProperties.PROPERTY_ALLOW_SELF_SIGNED_CERTIFICATES_SUFFIX);

					//default is to disallow self-signed certs 
				boolean allowSelfSignedCerts = false;
				if (allowSelfSignedCertsString != null && !allowSelfSignedCertsString.isEmpty()){
					allowSelfSignedCerts = Boolean.parseBoolean(allowSelfSignedCertsString);
				}
				
				UebTopicSource uebTopicSource = this.build(serverList, topic, 
						   						           apiKey, apiSecret,
						   						           consumerGroup, consumerInstance, 
						   						           fetchTimeout, fetchLimit, managed, useHttps, allowSelfSignedCerts);
				newUebTopicSources.add(uebTopicSource);
			}
		}
		return newUebTopicSources;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public UebTopicSource build(List<String> servers, 
								String topic,
								String apiKey, 
								String apiSecret) {
		
		return this.build(servers, topic, 
				  		  apiKey, apiSecret,
				  		  null, null,
				  		  UebTopicSource.DEFAULT_TIMEOUT_MS_FETCH,
				  		  UebTopicSource.DEFAULT_LIMIT_FETCH, true, false, true);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public UebTopicSource build(List<String> servers, String topic) {
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
		
		UebTopicSource uebTopicSource;
		
		synchronized(this) {
			if (!uebTopicSources.containsKey(topic)) {
				return;
			}
			
			uebTopicSource = uebTopicSources.remove(topic);
		}
		
		uebTopicSource.shutdown();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public UebTopicSource get(String topic) 
	       throws IllegalArgumentException, IllegalStateException {
		
		if (topic == null || topic.isEmpty()) {
			throw new IllegalArgumentException("A topic must be provided");
		}
		
		synchronized(this) {
			if (uebTopicSources.containsKey(topic)) {
				return uebTopicSources.get(topic);
			} else {
				throw new IllegalStateException("UebTopiceSource for " + topic + " not found");
			}
		}
	}

	@Override
	public synchronized List<UebTopicSource> inventory() {
		 List<UebTopicSource> readers = 
				 new ArrayList<UebTopicSource>(this.uebTopicSources.values());
		 return readers;
	}

	@Override
	public void destroy() {
		List<UebTopicSource> readers = this.inventory();
		for (UebTopicSource reader: readers) {
			reader.shutdown();
		}
		
		synchronized(this) {
			this.uebTopicSources.clear();
		}
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("IndexedUebTopicSourceFactory []");
		return builder.toString();
	}
	
}
