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

import java.net.MalformedURLException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.openecomp.policy.common.logging.eelf.PolicyLogger;
import com.att.nsa.cambria.client.CambriaBatchingPublisher;
import com.att.nsa.cambria.client.CambriaClientBuilders;
import com.att.nsa.cambria.client.CambriaClientBuilders.PublisherBuilder;
import com.att.nsa.mr.client.impl.MRSimplerBatchPublisher;
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
	public boolean send(String partitionId, String message) throws IllegalArgumentException;
	
	/**
	 * closes the publisher
	 */
	public void close();
	
	/**
	 * Cambria based library publisher
	 */
	public static class CambriaPublisherWrapper implements BusPublisher {

		/**
		 * The actual Cambria publisher
		 */
		@JsonIgnore
		protected volatile CambriaBatchingPublisher publisher;
		
		public CambriaPublisherWrapper(List<String> servers, String topic,
						               String apiKey,
						               String apiSecret) 
		       throws IllegalArgumentException {
			PublisherBuilder builder = new CambriaClientBuilders.PublisherBuilder();
			
			builder.usingHosts(servers)
			       .onTopic(topic);
			
				   // Only supported in 0.2.4 version
			       // .logSendFailuresAfter(DEFAULT_LOG_SEND_FAILURES_AFTER);
			
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
				PolicyLogger.warn(CambriaPublisherWrapper.class.getName(), 
		                          "SEND of " + message + " IN " +
		                          this + " cannot be performed because of " + 
						          e.getMessage());
				return false;
			}
			return true;			
		}
		
		/**
		 * {@inheritDoc}
		 */
		@Override
		public void close() {
			if (PolicyLogger.isInfoEnabled())
				PolicyLogger.info(CambriaPublisherWrapper.class.getName(), 
				                  "CREATION: " + this);
			
			try {
				this.publisher.close();
			} catch (Exception e) {
				PolicyLogger.warn(CambriaPublisherWrapper.class.getName(), 
				                  "CLOSE on " + this + " FAILED because of " + 
								  e.getMessage());
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
	public static class DmaapPublisherWrapper implements BusPublisher {
		/**
		 * MR based Publisher
		 */		
		protected MRSimplerBatchPublisher publisher;
		
		public DmaapPublisherWrapper(List<String> servers, String topic,
				                     String aafLogin,
				                     String aafPassword) {
			
			ArrayList<String> dmaapServers = new ArrayList<String>();
			for (String server: servers) {
				dmaapServers.add(server + ":3904");
			}
					
			this.publisher = 
				new MRSimplerBatchPublisher.Builder().
			                                againstUrls(dmaapServers).
			                                onTopic(topic).
			                                build();
			
			this.publisher.setProtocolFlag(ProtocolTypeConstants.AAF_AUTH.getValue());
			
			this.publisher.setUsername(aafLogin);
			this.publisher.setPassword(aafPassword);  
			
			Properties props = new Properties();
			props.setProperty("Protocol", "http");
			props.setProperty("contenttype", "application/json");
			
			this.publisher.setProps(props);
			
			this.publisher.setHost(servers.get(0));
			
			if (PolicyLogger.isInfoEnabled())
				PolicyLogger.info(DmaapPublisherWrapper.class.getName(), 
						          "CREATION: " + this);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void close() {
			if (PolicyLogger.isInfoEnabled())
				PolicyLogger.info(DmaapPublisherWrapper.class.getName(), 
				                  "CREATION: " + this);
			
			try {
				this.publisher.close(1, TimeUnit.SECONDS);
			} catch (Exception e) {
				PolicyLogger.warn(DmaapPublisherWrapper.class.getName(), 
				                  "CLOSE: " + this + " because of " + 
								  e.getMessage());
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
			
			this.publisher.send(partitionId, message);
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

}
