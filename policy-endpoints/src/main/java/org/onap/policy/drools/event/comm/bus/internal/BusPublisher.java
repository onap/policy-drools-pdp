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

package org.onap.policy.drools.event.comm.bus.internal;

import java.net.MalformedURLException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.onap.policy.drools.event.comm.bus.DmaapTopicSinkFactory;
import org.onap.policy.drools.properties.PolicyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.att.nsa.cambria.client.CambriaBatchingPublisher;
import com.att.nsa.cambria.client.CambriaClientBuilders;
import com.att.nsa.cambria.client.CambriaClientBuilders.PublisherBuilder;
import com.att.nsa.mr.client.impl.MRSimplerBatchPublisher;
import com.att.nsa.mr.client.response.MRPublisherResponse;
import com.att.nsa.mr.test.clients.ProtocolTypeConstants;
import com.fasterxml.jackson.annotation.JsonIgnore;

public interface BusPublisher {
	
	/**
	 * sends a message
	 * 
	 * @param partition id
	 * @param message the message
	 * @return true if success, false otherwise
	 * @throws IllegalArgumentException if no message provided
	 */
	public boolean send(String partitionId, String meskeySetsage) throws IllegalArgumentException;
	
	/**
	 * closes the publisher
	 */
	public void close();
	
	/**
	 * Cambria based library publisher
	 */
	public static class CambriaPublisherWrapper implements BusPublisher {
		
		private static Logger logger = LoggerFactory.getLogger(CambriaPublisherWrapper.class);

		/**
		 * The actual Cambria publisher
		 */
		@JsonIgnore
		protected volatile CambriaBatchingPublisher publisher;
		
		public CambriaPublisherWrapper(List<String> servers, String topic,
						               String apiKey,
						               String apiSecret, boolean useHttps) throws IllegalArgumentException {
			PublisherBuilder builder = new CambriaClientBuilders.PublisherBuilder();
		

			if (useHttps){
				
				builder.usingHosts(servers)
			       .onTopic(topic)
			       .usingHttps();
			}
			else{
				builder.usingHosts(servers)
			       .onTopic(topic);
			}
		
			
			if (apiKey != null && !apiKey.isEmpty() &&
				apiSecret != null && !apiSecret.isEmpty()) {
				builder.authenticatedBy(apiKey, apiSecret);
			}
			
			try {
				this.publisher = builder.build();
			} catch (MalformedURLException | GeneralSecurityException e) {
				throw new IllegalArgumentException(e);
			}
		}
		
		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean send(String partitionId, String message) 
				throws IllegalArgumentException {
			if (message == null)
				throw new IllegalArgumentException("No message provided");
			
			try {
				this.publisher.send(partitionId, message);
			} catch (Exception e) {
				logger.warn("{}: SEND of {} cannot be performed because of {}",
						    this, message, e.getMessage(), e);
				return false;
			}
			return true;			
		}
		
		/**
		 * {@inheritDoc}
		 */
		@Override
		public void close() {
			logger.info("{}: CLOSE", this);
			
			try {
				this.publisher.close();
			} catch (Exception e) {
				logger.warn("{}: CLOSE FAILED because of {}", 
						    this, e.getMessage(),e);
			}
		}
		
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("CambriaPublisherWrapper [").
			append("publisher.getPendingMessageCount()=").
			append(publisher.getPendingMessageCount()).
			append("]");
			return builder.toString();
		}
		
	}
	
	/**
	 * DmaapClient library wrapper
	 */
	public abstract class DmaapPublisherWrapper implements BusPublisher {
		
		private static Logger logger = LoggerFactory.getLogger(DmaapPublisherWrapper.class);
		
		/**
		 * MR based Publisher
		 */		
		protected MRSimplerBatchPublisher publisher;
		protected Properties props;
		
		/**
		 * MR Publisher Wrapper
		 *
		 * @param servers messaging bus hosts
		 * @param topic topic
		 * @param username AAF or DME2 Login
		 * @param password AAF or DME2 Password
		 */
		public DmaapPublisherWrapper(ProtocolTypeConstants protocol,
									 List<String> servers, String topic,
				                     String username,
				                     String password, boolean useHttps) throws IllegalArgumentException {

			
			if (topic == null || topic.isEmpty())
				throw new IllegalArgumentException("No topic for DMaaP");

			
			if (protocol == ProtocolTypeConstants.AAF_AUTH) {
				if (servers == null || servers.isEmpty())
					throw new IllegalArgumentException("No DMaaP servers or DME2 partner provided");
				
				ArrayList<String> dmaapServers = new ArrayList<>();
				if(useHttps){
					for (String server: servers) {
						dmaapServers.add(server + ":3905");
					}
				
				}
				else{
					for (String server: servers) {
						dmaapServers.add(server + ":3904");
					}				
				}
				
										
				this.publisher = 
					new MRSimplerBatchPublisher.Builder().
												againstUrls(dmaapServers).
												onTopic(topic).
												build();
				
				this.publisher.setProtocolFlag(ProtocolTypeConstants.AAF_AUTH.getValue());
			} else if (protocol == ProtocolTypeConstants.DME2) {
				ArrayList<String> dmaapServers = new ArrayList<>();
				dmaapServers.add("0.0.0.0:3904");
						
				this.publisher = 
					new MRSimplerBatchPublisher.Builder().
												againstUrls(dmaapServers).
												onTopic(topic).
												build();
				
				this.publisher.setProtocolFlag(ProtocolTypeConstants.DME2.getValue());
			}
			
			this.publisher.logTo(LoggerFactory.getLogger(MRSimplerBatchPublisher.class.getName()));
			
			this.publisher.setUsername(username);
			this.publisher.setPassword(password);  
			
			props = new Properties();
						
			if (useHttps) {				
				props.setProperty("Protocol", "https");
			} else {			
				props.setProperty("Protocol", "http");
			}

			props.setProperty("contenttype", "application/json");
			props.setProperty("username", username);
			props.setProperty("password", password);
			
			props.setProperty("topic", topic);
			
			this.publisher.setProps(props);
			
			if (protocol == ProtocolTypeConstants.AAF_AUTH)
				this.publisher.setHost(servers.get(0));
			
			logger.info("{}: CREATION: using protocol {}", this, protocol.getValue());
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void close() {
			logger.info("{}: CLOSE", this);
			
			try {
				this.publisher.close(1, TimeUnit.SECONDS);
			} catch (Exception e) {
				logger.warn("{}: CLOSE FAILED because of {}",
						    this, e.getMessage(), e);
			}
		}
		
		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean send(String partitionId, String message) 
				throws IllegalArgumentException {
			if (message == null)
				throw new IllegalArgumentException("No message provided");
			
			this.publisher.setPubResponse(new MRPublisherResponse());
			this.publisher.send(partitionId, message);
			MRPublisherResponse response = this.publisher.sendBatchWithResponse();
			if (response != null) {
				logger.debug("DMaaP publisher received {} : {}",
						     response.getResponseCode(), 
						     response.getResponseMessage());
			}

			return true;
		}
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("DmaapPublisherWrapper [").
			append("publisher.getAuthDate()=").append(publisher.getAuthDate()).
			append(", publisher.getAuthKey()=").append(publisher.getAuthKey()).
			append(", publisher.getHost()=").append(publisher.getHost()).
			append(", publisher.getProtocolFlag()=").append(publisher.getProtocolFlag()).
			append(", publisher.getUsername()=").append(publisher.getUsername()).
			append(", publisher.getPendingMessageCount()=").append(publisher.getPendingMessageCount()).
			append("]");
			return builder.toString();
		}
	}
	
	/**
	 * DmaapClient library wrapper
	 */
	public static class DmaapAafPublisherWrapper extends DmaapPublisherWrapper {
		/**
		 * MR based Publisher
		 */		
		protected MRSimplerBatchPublisher publisher;
		
		public DmaapAafPublisherWrapper(List<String> servers, String topic,
				                     String aafLogin,
				                     String aafPassword, boolean useHttps) {
			
			super(ProtocolTypeConstants.AAF_AUTH, servers, topic, aafLogin, aafPassword, useHttps);
		}
	}
	
	public static class DmaapDmePublisherWrapper extends DmaapPublisherWrapper {
		public DmaapDmePublisherWrapper(List<String> servers, String topic,
										String username, String password,
										String environment, String aftEnvironment, String dme2Partner,
										String latitude, String longitude, Map<String,String> additionalProps, boolean useHttps) {
			
			super(ProtocolTypeConstants.DME2, servers, topic, username, password, useHttps);
			
			
			
			
			
			
			String dme2RouteOffer = additionalProps.get(DmaapTopicSinkFactory.DME2_ROUTE_OFFER_PROPERTY);
			
			 if (environment == null || environment.isEmpty()) {
				throw new IllegalArgumentException("Missing " + PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS +
						"." + topic + PolicyProperties.PROPERTY_DMAAP_DME2_ENVIRONMENT_SUFFIX + " property for DME2 in DMaaP");
			} if (aftEnvironment == null || aftEnvironment.isEmpty()) {
				throw new IllegalArgumentException("Missing " + PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS +
						"." + topic + PolicyProperties.PROPERTY_DMAAP_DME2_AFT_ENVIRONMENT_SUFFIX + " property for DME2 in DMaaP");
			} if (latitude == null || latitude.isEmpty()) {
				throw new IllegalArgumentException("Missing " + PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS +
						"." + topic + PolicyProperties.PROPERTY_DMAAP_DME2_LATITUDE_SUFFIX + " property for DME2 in DMaaP");
			} if (longitude == null || longitude.isEmpty()) {
				throw new IllegalArgumentException("Missing " + PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS +
						"." + topic + PolicyProperties.PROPERTY_DMAAP_DME2_LONGITUDE_SUFFIX + " property for DME2 in DMaaP");
			}
			
			if ((dme2Partner == null || dme2Partner.isEmpty()) && (dme2RouteOffer == null || dme2RouteOffer.isEmpty())) {
				throw new IllegalArgumentException("Must provide at least " + PolicyProperties.PROPERTY_DMAAP_SOURCE_TOPICS +
						"." + topic + PolicyProperties.PROPERTY_DMAAP_DME2_PARTNER_SUFFIX + " or " + 
						PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS + "." + topic + PolicyProperties.PROPERTY_DMAAP_DME2_ROUTE_OFFER_SUFFIX + " for DME2");
			}
			
			String serviceName = servers.get(0);
			
			/* These are required, no defaults */
			props.setProperty("Environment", environment);
			props.setProperty("AFT_ENVIRONMENT", aftEnvironment);
			
			props.setProperty(DmaapTopicSinkFactory.DME2_SERVICE_NAME_PROPERTY, serviceName);

			if (dme2Partner != null)
				props.setProperty("Partner", dme2Partner);
			if (dme2RouteOffer != null)
				props.setProperty(DmaapTopicSinkFactory.DME2_ROUTE_OFFER_PROPERTY, dme2RouteOffer);
			
			props.setProperty("Latitude", latitude);
			props.setProperty("Longitude", longitude);
			
			// ServiceName also a default, found in additionalProps
			
			/* These are optional, will default to these values if not set in optionalProps */
			props.setProperty("AFT_DME2_EP_READ_TIMEOUT_MS", "50000");
			props.setProperty("AFT_DME2_ROUNDTRIP_TIMEOUT_MS", "240000");
			props.setProperty("AFT_DME2_EP_CONN_TIMEOUT", "15000");
			props.setProperty("Version", "1.0");
			props.setProperty("SubContextPath", "/");
			props.setProperty("sessionstickinessrequired", "no");
			
			/* These should not change */
			props.setProperty("TransportType", "DME2");
			props.setProperty("MethodType", "POST");
			
			for (Map.Entry<String,String> entry : additionalProps.entrySet()) {
				String key = entry.getKey();
				String value = entry.getValue();
				
				if (value != null)
					props.setProperty(key, value);
			}
			
			this.publisher.setProps(props);
		}
	}
}
