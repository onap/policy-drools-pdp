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
import java.util.List;
import java.util.Properties;

import com.att.nsa.cambria.client.CambriaClientBuilders;
import com.att.nsa.cambria.client.CambriaConsumer;
import com.att.nsa.mr.client.impl.MRConsumerImpl;
import com.att.nsa.mr.test.clients.ProtocolTypeConstants;
import com.att.nsa.cambria.client.CambriaClientBuilders.ConsumerBuilder;

/**
 * Wrapper around libraries to consume from message bus
 *
 */
public interface BusConsumer {
	
	/**
	 * fetch messages
	 * 
	 * @return list of messages
	 * @throws Exception when error encountered by underlying libraries
	 */
	public Iterable<String> fetch() throws Exception;
	
	/**
	 * close underlying library consumer
	 */
	public void close();

	/**
	 * Cambria based consumer
	 */
	public static class CambriaConsumerWrapper implements BusConsumer {
		/**
		 * Cambria client
		 */
		protected CambriaConsumer consumer;
		
		/**
		 * Cambria Consumer Wrapper
		 * 
		 * @param servers messaging bus hosts
		 * @param topic topic
		 * @param apiKey API Key
		 * @param apiSecret API Secret
		 * @param consumerGroup Consumer Group
		 * @param consumerInstance Consumer Instance
		 * @param fetchTimeout Fetch Timeout
		 * @param fetchLimit Fetch Limit
		 * @throws GeneralSecurityException 
		 * @throws MalformedURLException 
		 */
		public CambriaConsumerWrapper(List<String> servers, String topic, 
								  String apiKey, String apiSecret,
								  String consumerGroup, String consumerInstance,
								  int fetchTimeout, int fetchLimit) 
		       throws IllegalArgumentException {
			
			ConsumerBuilder builder = 
					new CambriaClientBuilders.ConsumerBuilder();
			
			builder.knownAs(consumerGroup, consumerInstance)
			       .usingHosts(servers)
			       .onTopic(topic)
			       .waitAtServer(fetchTimeout)
			       .receivingAtMost(fetchLimit);
			
			if (apiKey != null && !apiKey.isEmpty() &&
				apiSecret != null && !apiSecret.isEmpty()) {
				builder.authenticatedBy(apiKey, apiSecret);
			}
					
			try {
				this.consumer = builder.build();
			} catch (MalformedURLException | GeneralSecurityException e) {
				throw new IllegalArgumentException(e);
			}
		}
		
		/**
		 * {@inheritDoc}
		 */
		public Iterable<String> fetch() throws Exception {
			return this.consumer.fetch();
		}
		
		/**
		 * {@inheritDoc}
		 */
		public void close() {
			this.consumer.close();
		}
		
		@Override
		public String toString() {
			return "CambriaConsumerWrapper []";
		}
	}
	
	/**
	 * MR based consumer
	 */
	public static class DmaapConsumerWrapper implements BusConsumer {
		
		/**
		 * MR Consumer
		 */
		protected MRConsumerImpl consumer;
		
		/**
		 * MR Consumer Wrapper
		 * 
		 * @param servers messaging bus hosts
		 * @param topic topic
		 * @param apiKey API Key
		 * @param apiSecret API Secret
		 * @param aafLogin AAF Login
		 * @param aafPassword AAF Password
		 * @param consumerGroup Consumer Group
		 * @param consumerInstance Consumer Instance
		 * @param fetchTimeout Fetch Timeout
		 * @param fetchLimit Fetch Limit
		 */
		public DmaapConsumerWrapper(List<String> servers, String topic, 
								String apiKey, String apiSecret,
								String aafLogin, String aafPassword,
								String consumerGroup, String consumerInstance,
								int fetchTimeout, int fetchLimit) 
		throws Exception {
					
			this.consumer = new MRConsumerImpl(servers, topic, 
											   consumerGroup, consumerInstance, 
											   fetchTimeout, fetchLimit, 
									           null, apiKey, apiSecret);
			
			this.consumer.setUsername(aafLogin);
			this.consumer.setPassword(aafPassword);
			
			this.consumer.setProtocolFlag(ProtocolTypeConstants.AAF_AUTH.getValue());
			
			Properties props = new Properties();
			props.setProperty("Protocol", "http");
			this.consumer.setProps(props);
			this.consumer.setHost(servers.get(0) + ":3904");;
		}
		
		/**
		 * {@inheritDoc}
		 */
		public Iterable<String> fetch() throws Exception {
			return this.consumer.fetch();
		}
		
		/**
		 * {@inheritDoc}
		 */
		public void close() {
			this.consumer.close();
		}
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.
			append("DmaapConsumerWrapper [").
			append("consumer.getAuthDate()=").append(consumer.getAuthDate()).
			append(", consumer.getAuthKey()=").append(consumer.getAuthKey()).
			append(", consumer.getHost()=").append(consumer.getHost()).
			append(", consumer.getProtocolFlag()=").append(consumer.getProtocolFlag()).
			append(", consumer.getUsername()=").append(consumer.getUsername()).
			append("]");
			return builder.toString();
		}
	}

	
}




