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
import java.util.Map;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.openecomp.policy.drools.event.comm.Topic;
import org.openecomp.policy.drools.event.comm.bus.DmaapTopicSink;

/**
 * This implementation publishes events for the associated DMAAP topic,
 * inline with the calling thread.
 */
public class InlineDmaapTopicSink extends InlineBusTopicSink implements DmaapTopicSink {
	
	protected static Logger logger = 
			LoggerFactory.getLogger(InlineDmaapTopicSink.class);
	
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
	public InlineDmaapTopicSink(List<String> servers, String topic, 
											String apiKey, String apiSecret,
											String userName, String password,
											String partitionKey,
											String environment, String aftEnvironment, String partner,
											String latitude, String longitude, Map<String,String> additionalProps,
											boolean useHttps, boolean allowSelfSignedCerts)
			throws IllegalArgumentException {
			
		super(servers, topic, apiKey, apiSecret, partitionKey, useHttps, allowSelfSignedCerts);
		
		this.userName = userName;
		this.password = password;
		
		this.environment = environment;
		this.aftEnvironment = aftEnvironment;
		this.partner = partner;
		
		this.latitude = latitude;
		this.longitude = longitude;
		
		this.additionalProps = additionalProps;
		
		this.init();
	}
	
	public InlineDmaapTopicSink(List<String> servers, String topic, 
			                    String apiKey, String apiSecret,
	                            String userName, String password,
			                    String partitionKey, boolean useHttps,  boolean allowSelfSignedCerts) 
		throws IllegalArgumentException {
		
		super(servers, topic, apiKey, apiSecret, partitionKey, useHttps, allowSelfSignedCerts);
		
		this.userName = userName;
		this.password = password;
	}
	

	@Override
	public void init() {
		if ((this.environment == null	|| this.environment.isEmpty()) &&
		   (this.aftEnvironment == null || this.aftEnvironment.isEmpty()) &&
		   (this.latitude == null		|| this.latitude.isEmpty()) &&
		   (this.longitude == null		|| this.longitude.isEmpty()) &&
		   (this.partner == null		|| this.partner.isEmpty())) {
			this.publisher = 
				new BusPublisher.DmaapAafPublisherWrapper(this.servers, 
						                               this.topic, 
						                               this.userName, 
						                               this.password, this.useHttps);
		} else {
			this.publisher = 
				new BusPublisher.DmaapDmePublisherWrapper(this.servers, this.topic, 
						                               this.userName, this.password,
						                               this.environment, this.aftEnvironment,
						                               this.partner, this.latitude, this.longitude,
						                               this.additionalProps, this.useHttps);
		}
		
		logger.info("{}: DMAAP SINK created", this);
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
		builder.append("InlineDmaapTopicSink [userName=").append(userName).append(", password=").append(password)
				.append(", getTopicCommInfrastructure()=").append(getTopicCommInfrastructure()).append(", toString()=")
				.append(super.toString()).append("]");
		return builder.toString();
	}

}