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
import java.util.Map;
import java.util.Properties;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.openecomp.policy.drools.event.comm.bus.internal.InlineDmaapTopicSink;
import org.openecomp.policy.drools.properties.PolicyProperties;

/**
 * DMAAP Topic Sink Factory
 */
public interface DmaapTopicSinkFactory {
	public final String DME2_READ_TIMEOUT_PROPERTY = "AFT_DME2_EP_READ_TIMEOUT_MS";
	public final String DME2_EP_CONN_TIMEOUT_PROPERTY = "AFT_DME2_EP_CONN_TIMEOUT";
	public final String DME2_ROUNDTRIP_TIMEOUT_PROPERTY = "AFT_DME2_ROUNDTRIP_TIMEOUT_MS";
	public final String DME2_VERSION_PROPERTY = "Version";
	public final String DME2_ROUTE_OFFER_PROPERTY = "routeOffer";
	public final String DME2_SERVICE_NAME_PROPERTY = "ServiceName";
	public final String DME2_SUBCONTEXT_PATH_PROPERTY = "SubContextPath";
	public final String DME2_SESSION_STICKINESS_REQUIRED_PROPERTY = "sessionstickinessrequired";

	/**
	 * Instantiates a new DMAAP Topic Sink
	 * 
	 * @param servers list of servers
	 * @param topic topic name
	 * @param apiKey API Key
	 * @param apiSecret API Secret
	 * @param userName AAF user name
	 * @param password AAF password
	 * @param partitionKey Consumer Group
	 * @param environment DME2 environment
	 * @param aftEnvironment DME2 AFT environment
	 * @param partner DME2 Partner
	 * @param latitude DME2 latitude
	 * @param longitude DME2 longitude
	 * @param additionalProps additional properties to pass to DME2
	 * @param managed is this sink endpoint managed?
	 * 
	 * @return an DMAAP Topic Sink
	 * @throws IllegalArgumentException if invalid parameters are present
	 */
	public DmaapTopicSink build(List<String> servers, 
								String topic, 
								String apiKey, 
								String apiSecret,
								String userName,
								String password,
								String partitionKey,
								String environment,
								String aftEnvironment,
								String partner,
								String latitude,
								String longitude,
								Map<String,String> additionalProps,
								boolean managed,
								boolean useHttps,
								boolean allowSelfSignedCerts) ;
	
	/**
	 * Instantiates a new DMAAP Topic Sink
	 * 
	 * @param servers list of servers
	 * @param topic topic name
	 * @param apiKey API Key
	 * @param apiSecret API Secret
	 * @param userName AAF user name
	 * @param password AAF password
	 * @param partitionKey Consumer Group
	 * @param managed is this sink endpoint managed?
	 * 
	 * @return an DMAAP Topic Sink
	 * @throws IllegalArgumentException if invalid parameters are present
	 */
	public DmaapTopicSink build(List<String> servers, 
								String topic, 
								String apiKey, 
								String apiSecret,
								String userName,
								String password,
								String partitionKey,
								boolean managed,
								boolean useHttps,
								boolean allowSelfSignedCerts)
			throws IllegalArgumentException;
	
	/**
	 * Creates an DMAAP Topic Sink based on properties files
	 * 
	 * @param properties Properties containing initialization values
	 * 
	 * @return an DMAAP Topic Sink
	 * @throws IllegalArgumentException if invalid parameters are present
	 */
	public List<DmaapTopicSink> build(Properties properties)
			throws IllegalArgumentException;
	
	/**
	 * Instantiates a new DMAAP Topic Sink
	 * 
	 * @param servers list of servers
	 * @param topic topic name
	 * 
	 * @return an DMAAP Topic Sink
	 * @throws IllegalArgumentException if invalid parameters are present
	 */
	public DmaapTopicSink build(List<String> servers, String topic)
			throws IllegalArgumentException;
	
	/**
	 * Destroys an DMAAP Topic Sink based on a topic
	 * 
	 * @param topic topic name
	 * @throws IllegalArgumentException if invalid parameters are present
	 */
	public void destroy(String topic);

	/**
	 * gets an DMAAP Topic Sink based on topic name
	 * @param topic the topic name
	 * 
	 * @return an DMAAP Topic Sink with topic name
	 * @throws IllegalArgumentException if an invalid topic is provided
	 * @throws IllegalStateException if the DMAAP Topic Reader is 
	 * an incorrect state
	 */
	public DmaapTopicSink get(String topic)
			   throws IllegalArgumentException, IllegalStateException;
	
	/**
	 * Provides a snapshot of the DMAAP Topic Sinks
	 * @return a list of the DMAAP Topic Sinks
	 */
	public List<DmaapTopicSink> inventory();

	/**
	 * Destroys all DMAAP Topic Sinks
	 */
	public void destroy();
}

/* ------------- implementation ----------------- */

/**
 * Factory of DMAAP Reader Topics indexed by topic name
 */
class IndexedDmaapTopicSinkFactory implements DmaapTopicSinkFactory {
	/**
	 * Logger
	 */
	private static Logger logger = LoggerFactory.getLogger(IndexedDmaapTopicSinkFactory.class);	
	
	/**
	 * DMAAP Topic Name Index
	 */
	protected HashMap<String, DmaapTopicSink> dmaapTopicWriters =
			new HashMap<String, DmaapTopicSink>();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public DmaapTopicSink build(List<String> servers, 
								String topic, 
								String apiKey, 
								String apiSecret,
								String userName,
								String password,
								String partitionKey,
								String environment,
								String aftEnvironment,
								String partner,
								String latitude,
								String longitude,
								Map<String,String> additionalProps,
								boolean managed,
								boolean useHttps,
								boolean allowSelfSignedCerts) 
			throws IllegalArgumentException {
		
		if (topic == null || topic.isEmpty()) {
			throw new IllegalArgumentException("A topic must be provided");
		}
		
		synchronized (this) {
			if (dmaapTopicWriters.containsKey(topic)) {
				return dmaapTopicWriters.get(topic);
			}
			
			DmaapTopicSink dmaapTopicSink = 
					new InlineDmaapTopicSink(servers, topic, 
										     apiKey, apiSecret,
										     userName, password,
										     partitionKey,
										     environment, aftEnvironment, 
										     partner, latitude, longitude, additionalProps, useHttps, allowSelfSignedCerts);
			
			if (managed)
				dmaapTopicWriters.put(topic, dmaapTopicSink);
			return dmaapTopicSink;
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public DmaapTopicSink build(List<String> servers, 
								String topic, 
								String apiKey, 
								String apiSecret,
								String userName,
								String password,
								String partitionKey,
								boolean managed,
								boolean useHttps, boolean allowSelfSignedCerts) 
			throws IllegalArgumentException {
		
		if (topic == null || topic.isEmpty()) {
			throw new IllegalArgumentException("A topic must be provided");
		}
		
		synchronized (this) {
			if (dmaapTopicWriters.containsKey(topic)) {
				return dmaapTopicWriters.get(topic);
			}
			
			DmaapTopicSink dmaapTopicSink = 
					new InlineDmaapTopicSink(servers, topic, 
										     apiKey, apiSecret,
										     userName, password,
										     partitionKey, useHttps, allowSelfSignedCerts);
			
			if (managed)
				dmaapTopicWriters.put(topic, dmaapTopicSink);
			return dmaapTopicSink;
		}
	}
	

	/**
	 * {@inheritDoc}
	 */
	@Override
	public DmaapTopicSink build(List<String> servers, String topic) throws IllegalArgumentException {
		return this.build(servers, topic, null, null, null, null, null, true, false, false);
	}
	

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<DmaapTopicSink> build(Properties properties) throws IllegalArgumentException {
		
		String writeTopics = properties.getProperty(PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS);
		if (writeTopics == null || writeTopics.isEmpty()) {
			logger.info("{}: no topic for DMaaP Sink", this);
			return new ArrayList<DmaapTopicSink>();
		}
		
		List<String> writeTopicList = new ArrayList<String>(Arrays.asList(writeTopics.split("\\s*,\\s*")));
		List<DmaapTopicSink> newDmaapTopicSinks = new ArrayList<DmaapTopicSink>();
		synchronized(this) {
			for (String topic: writeTopicList) {
				if (this.dmaapTopicWriters.containsKey(topic)) {
					newDmaapTopicSinks.add(this.dmaapTopicWriters.get(topic));
					continue;
				}
				String servers = properties.getProperty(PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS + "." + 
				                                        topic + 
				                                        PolicyProperties.PROPERTY_TOPIC_SERVERS_SUFFIX);
				
				List<String> serverList;
				if (servers != null && !servers.isEmpty()) serverList = new ArrayList<String>(Arrays.asList(servers.split("\\s*,\\s*")));
				else serverList = new ArrayList<>();
				
				String apiKey = properties.getProperty(PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS + 
						                               "." + topic + 
						                               PolicyProperties.PROPERTY_TOPIC_API_KEY_SUFFIX);		 
				String apiSecret = properties.getProperty(PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS + 
                                                          "." + topic + 
                                                          PolicyProperties.PROPERTY_TOPIC_API_SECRET_SUFFIX);
				
				String aafMechId = properties.getProperty(PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS + 
                                                          "." + topic + 
                                                          PolicyProperties.PROPERTY_TOPIC_AAF_MECHID_SUFFIX);
				String aafPassword = properties.getProperty(PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS + 
         				                                    "." + topic + 
         				                                    PolicyProperties.PROPERTY_TOPIC_AAF_PASSWORD_SUFFIX);
				
				String partitionKey = properties.getProperty(PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS + 
                                                             "." + topic + 
                                                             PolicyProperties.PROPERTY_TOPIC_SINK_PARTITION_KEY_SUFFIX);
				
				String managedString = properties.getProperty(PolicyProperties.PROPERTY_UEB_SINK_TOPICS + "." + topic +
						                                      PolicyProperties.PROPERTY_MANAGED_SUFFIX);
				
				/* DME2 Properties */
				
				String dme2Environment = properties.getProperty(PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS + "."
						+ topic + PolicyProperties.PROPERTY_DMAAP_DME2_ENVIRONMENT_SUFFIX);

				String dme2AftEnvironment = properties.getProperty(PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS + "."
						+ topic + PolicyProperties.PROPERTY_DMAAP_DME2_AFT_ENVIRONMENT_SUFFIX);

				String dme2Partner = properties.getProperty(PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS + "." + topic
						+ PolicyProperties.PROPERTY_DMAAP_DME2_PARTNER_SUFFIX);
				
				String dme2RouteOffer = properties.getProperty(PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS + "." + topic
						+ PolicyProperties.PROPERTY_DMAAP_DME2_ROUTE_OFFER_SUFFIX);

				String dme2Latitude = properties.getProperty(PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS + "." + topic
						+ PolicyProperties.PROPERTY_DMAAP_DME2_LATITUDE_SUFFIX);

				String dme2Longitude = properties.getProperty(PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS + "."
						+ topic + PolicyProperties.PROPERTY_DMAAP_DME2_LONGITUDE_SUFFIX);

				String dme2EpReadTimeoutMs = properties.getProperty(PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS + "."
						+ topic + PolicyProperties.PROPERTY_DMAAP_DME2_EP_READ_TIMEOUT_MS_SUFFIX);

				String dme2EpConnTimeout = properties.getProperty(PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS + "."
						+ topic + PolicyProperties.PROPERTY_DMAAP_DME2_EP_CONN_TIMEOUT_SUFFIX);

				String dme2RoundtripTimeoutMs = properties.getProperty(PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS
						+ "." + topic + PolicyProperties.PROPERTY_DMAAP_DME2_ROUNDTRIP_TIMEOUT_MS_SUFFIX);

				String dme2Version = properties.getProperty(PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS + "." + topic
						+ PolicyProperties.PROPERTY_DMAAP_DME2_VERSION_SUFFIX);

				String dme2SubContextPath = properties.getProperty(PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS + "."
						+ topic + PolicyProperties.PROPERTY_DMAAP_DME2_SUB_CONTEXT_PATH_SUFFIX);

				String dme2SessionStickinessRequired = properties
						.getProperty(PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS + "." + topic
								+ PolicyProperties.PROPERTY_DMAAP_DME2_SESSION_STICKINESS_REQUIRED_SUFFIX);
				
				Map<String,String> dme2AdditionalProps = new HashMap<>();
				
				if (dme2EpReadTimeoutMs != null && !dme2EpReadTimeoutMs.isEmpty())
					dme2AdditionalProps.put(DME2_READ_TIMEOUT_PROPERTY, dme2EpReadTimeoutMs);
				if (dme2EpConnTimeout != null && !dme2EpConnTimeout.isEmpty())
					dme2AdditionalProps.put(DME2_EP_CONN_TIMEOUT_PROPERTY, dme2EpConnTimeout);
				if (dme2RoundtripTimeoutMs != null && !dme2RoundtripTimeoutMs.isEmpty())
					dme2AdditionalProps.put(DME2_ROUNDTRIP_TIMEOUT_PROPERTY, dme2RoundtripTimeoutMs);
				if (dme2Version != null && !dme2Version.isEmpty())
					dme2AdditionalProps.put(DME2_VERSION_PROPERTY, dme2Version);
				if (dme2RouteOffer != null && !dme2RouteOffer.isEmpty())
					dme2AdditionalProps.put(DME2_ROUTE_OFFER_PROPERTY, dme2RouteOffer);
				if (dme2SubContextPath != null && !dme2SubContextPath.isEmpty())
					dme2AdditionalProps.put(DME2_SUBCONTEXT_PATH_PROPERTY, dme2SubContextPath);
				if (dme2SessionStickinessRequired != null && !dme2SessionStickinessRequired.isEmpty())
					dme2AdditionalProps.put(DME2_SESSION_STICKINESS_REQUIRED_PROPERTY, dme2SessionStickinessRequired);

				if (servers == null || servers.isEmpty()) {
					logger.error("{}: no DMaaP servers or DME2 ServiceName provided", this);
					continue;
				}
				
				boolean managed = true;
				if (managedString != null && !managedString.isEmpty()) {
					managed = Boolean.parseBoolean(managedString);
				}
				
				String useHttpsString = properties.getProperty(PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS + "." + topic +
						PolicyProperties.PROPERTY_HTTP_HTTPS_SUFFIX);

				//default is to use HTTP if no https property exists
				boolean useHttps = false;
				if (useHttpsString != null && !useHttpsString.isEmpty()){
					useHttps = Boolean.parseBoolean(useHttpsString);
				}
				
				
				String allowSelfSignedCertsString = properties.getProperty(PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS + "." + topic +
						PolicyProperties.PROPERTY_ALLOW_SELF_SIGNED_CERTIFICATES_SUFFIX);

					//default is to disallow self-signed certs 
				boolean allowSelfSignedCerts = false;
				if (allowSelfSignedCertsString != null && !allowSelfSignedCertsString.isEmpty()){
					allowSelfSignedCerts = Boolean.parseBoolean(allowSelfSignedCertsString);
				}				
				
				DmaapTopicSink dmaapTopicSink = this.build(serverList, topic, 
						   						           apiKey, apiSecret,
						   						           aafMechId, aafPassword,
						   						           partitionKey,
						   						           dme2Environment, dme2AftEnvironment,
						   						           dme2Partner, dme2Latitude, dme2Longitude,
						   						           dme2AdditionalProps, managed, useHttps, allowSelfSignedCerts);
				
				newDmaapTopicSinks.add(dmaapTopicSink);
			}
			return newDmaapTopicSinks;
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
		
		DmaapTopicSink dmaapTopicWriter;
		synchronized(this) {
			if (!dmaapTopicWriters.containsKey(topic)) {
				return;
			}
			
			dmaapTopicWriter = dmaapTopicWriters.remove(topic);
		}
		
		dmaapTopicWriter.shutdown();
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void destroy() {
		List<DmaapTopicSink> writers = this.inventory();
		for (DmaapTopicSink writer: writers) {
			writer.shutdown();
		}
		
		synchronized(this) {
			this.dmaapTopicWriters.clear();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public DmaapTopicSink get(String topic) 
			throws IllegalArgumentException, IllegalStateException {
		
		if (topic == null || topic.isEmpty()) {
			throw new IllegalArgumentException("A topic must be provided");
		}
		
		synchronized(this) {
			if (dmaapTopicWriters.containsKey(topic)) {
				return dmaapTopicWriters.get(topic);
			} else {
				throw new IllegalStateException("DmaapTopicSink for " + topic + " not found");
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized List<DmaapTopicSink> inventory() {
		 List<DmaapTopicSink> writers = 
				 new ArrayList<DmaapTopicSink>(this.dmaapTopicWriters.values());
		 return writers;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("IndexedDmaapTopicSinkFactory []");
		return builder.toString();
	}
	
}