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

import java.util.List;
import java.util.Map;

import org.onap.policy.drools.event.comm.Topic;
import org.onap.policy.drools.event.comm.bus.DmaapTopicSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This topic reader implementation specializes in reading messages
 * over DMAAP topic and notifying its listeners
 */
public class SingleThreadedDmaapTopicSource extends SingleThreadedBusTopicSource
                                            implements DmaapTopicSource, Runnable {	

	private static Logger logger = LoggerFactory.getLogger(SingleThreadedDmaapTopicSource.class);
	
	protected boolean allowSelfSignedCerts;
	protected final String userName;
	protected final String password;
	
	protected String environment = null;
	protected String aftEnvironment = null;
	protected String partner = null;
	protected String latitude = null;
	protected String longitude = null;
	
	protected Map<String,String> additionalProps = null;

	
	/**
	 * 
	 * @param servers DMaaP servers
	 * @param topic DMaaP Topic to be monitored
	 * @param apiKey DMaaP API Key (optional)
	 * @param apiSecret DMaaP API Secret (optional)
	 * @param consumerGroup DMaaP Reader Consumer Group
	 * @param consumerInstance DMaaP Reader Instance
	 * @param fetchTimeout DMaaP fetch timeout
	 * @param fetchLimit DMaaP fetch limit
	 * @param environment DME2 Environment
	 * @param aftEnvironment DME2 AFT Environment
	 * @param partner DME2 Partner
	 * @param latitude DME2 Latitude
	 * @param longitude DME2 Longitude
	 * @param additionalProps Additional properties to pass to DME2
	 * @param useHttps does connection use HTTPS?
	 * @param allowSelfSignedCerts are self-signed certificates allow
	 * 
	 * @throws IllegalArgumentException An invalid parameter passed in
	 */
	public SingleThreadedDmaapTopicSource(List<String> servers, String topic, 
											String apiKey, String apiSecret,
											String userName, String password,
											String consumerGroup, String consumerInstance,
											int fetchTimeout, int fetchLimit,
											String environment, String aftEnvironment, String partner,
											String latitude, String longitude, Map<String,String> additionalProps,
											boolean useHttps, boolean allowSelfSignedCerts)
			throws IllegalArgumentException {
			
		super(servers, topic, apiKey, apiSecret, 
			  consumerGroup, consumerInstance, 
			  fetchTimeout, fetchLimit, useHttps,allowSelfSignedCerts);
		
		this.userName = userName;
		this.password = password;
		
		this.environment = environment;
		this.aftEnvironment = aftEnvironment;
		this.partner = partner;
		
		this.latitude = latitude;
		this.longitude = longitude;
		
		this.additionalProps = additionalProps;
		try {
			this.init();
		} catch (Exception e) {
			logger.error("ERROR during init of topic {}", this.topic);
			throw new IllegalArgumentException(e);
		}
	}

	/**
	 * 
	 * @param servers DMaaP servers
	 * @param topic DMaaP Topic to be monitored
	 * @param apiKey DMaaP API Key (optional)
	 * @param apiSecret DMaaP API Secret (optional)
	 * @param consumerGroup DMaaP Reader Consumer Group
	 * @param consumerInstance DMaaP Reader Instance
	 * @param fetchTimeout DMaaP fetch timeout
	 * @param fetchLimit DMaaP fetch limit
	 * @param useHttps does connection use HTTPS?
	 * @param allowSelfSignedCerts are self-signed certificates allow
	 * @throws IllegalArgumentException An invalid parameter passed in
	 */
	public SingleThreadedDmaapTopicSource(List<String> servers, String topic, 
			                              String apiKey, String apiSecret,
			                              String userName, String password,
			                              String consumerGroup, String consumerInstance, 
			                              int fetchTimeout, int fetchLimit, boolean useHttps, boolean allowSelfSignedCerts)
			throws IllegalArgumentException {
		
		
		super(servers, topic, apiKey, apiSecret, 
			  consumerGroup, consumerInstance, 
			  fetchTimeout, fetchLimit, useHttps, allowSelfSignedCerts);
		
		this.userName = userName;
		this.password = password;		
		
		try {
			this.init();
		} catch (Exception e) {
			logger.warn("dmaap-source: cannot create topic {} because of {}", topic, e.getMessage(), e);
			throw new IllegalArgumentException(e);
		}
	}
	

	/**
	 * Initialize the Cambria or MR Client
	 */
	@Override
	public void init() throws Exception {
		if (this.userName == null || this.userName.isEmpty() || 
				this.password == null || this.password.isEmpty()) {
				this.consumer =
						new BusConsumer.CambriaConsumerWrapper(this.servers, this.topic, 
								                           this.apiKey, this.apiSecret,
								                           this.consumerGroup, this.consumerInstance,
								                           this.fetchTimeout, this.fetchLimit,
								                           this.useHttps, this.allowSelfSignedCerts);
		} else if ((this.environment == null    || this.environment.isEmpty()) &&
				   (this.aftEnvironment == null || this.aftEnvironment.isEmpty()) &&
				   (this.latitude == null	   || this.latitude.isEmpty()) &&
				   (this.longitude == null	   || this.longitude.isEmpty()) &&
				   (this.partner == null 	   || this.partner.isEmpty())) {
			this.consumer =
					new BusConsumer.DmaapAafConsumerWrapper(this.servers, this.topic, 
							                            this.apiKey, this.apiSecret,
							                            this.userName, this.password,
							                            this.consumerGroup, this.consumerInstance,
							                            this.fetchTimeout, this.fetchLimit, this.useHttps);
		} else {
			this.consumer =
					new BusConsumer.DmaapDmeConsumerWrapper(this.servers, this.topic, 
							                            this.apiKey, this.apiSecret,
							                            this.userName, this.password,
							                            this.consumerGroup, this.consumerInstance,
							                            this.fetchTimeout, this.fetchLimit,
							                            this.environment, this.aftEnvironment, this.partner,
							                            this.latitude, this.longitude, this.additionalProps, this.useHttps);
		}
			
		logger.info("{}: INITTED", this);
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public CommInfrastructure getTopicCommInfrastructure() {
		return Topic.CommInfrastructure.DMAAP;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("SingleThreadedDmaapTopicSource [userName=").append(userName).append(", password=")
				.append((password == null || password.isEmpty()) ? "-" : password.length())
				.append(", getTopicCommInfrastructure()=").append(getTopicCommInfrastructure())
				.append(", toString()=").append(super.toString()).append("]");
		return builder.toString();
	}


}
