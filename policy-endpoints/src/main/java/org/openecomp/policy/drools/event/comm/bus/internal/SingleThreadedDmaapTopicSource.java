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

import org.openecomp.policy.drools.event.comm.Topic;
import org.openecomp.policy.drools.event.comm.bus.DmaapTopicSource;
import org.openecomp.policy.common.logging.eelf.PolicyLogger;

/**
 * This topic reader implementation specializes in reading messages
 * over DMAAP topic and notifying its listeners
 */
public class SingleThreadedDmaapTopicSource extends SingleThreadedBusTopicSource
                                            implements DmaapTopicSource, Runnable {
	
	protected final String userName;
	protected final String password;
	private String className = SingleThreadedDmaapTopicSource.class.getName();

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
	 * @throws IllegalArgumentException An invalid parameter passed in
	 */
	public SingleThreadedDmaapTopicSource(List<String> servers, String topic, 
			                              String apiKey, String apiSecret,
			                              String userName, String password,
			                              String consumerGroup, String consumerInstance, 
			                              int fetchTimeout, int fetchLimit)
			throws IllegalArgumentException {
		
		
		super(servers, topic, apiKey, apiSecret, 
			  consumerGroup, consumerInstance, 
			  fetchTimeout, fetchLimit);
		
		this.userName = userName;
		this.password = password;		
		
		try {
			this.init();
		} catch (Exception e) {
			e.printStackTrace();
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
							                           this.fetchTimeout, this.fetchLimit);			
		} else {
			this.consumer =
					new BusConsumer.DmaapConsumerWrapper(this.servers, this.topic, 
							                            this.apiKey, this.apiSecret,
							                            this.userName, this.password,
							                            this.consumerGroup, this.consumerInstance,
							                            this.fetchTimeout, this.fetchLimit);
		}
			
		PolicyLogger.info(className, "CREATION: " + this);
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
