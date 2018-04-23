/*
 * ============LICENSE_START=======================================================
 * policy-endpoints
 * ================================================================================
 * Copyright (C) 2017-2018 AT&T Intellectual Property. All rights reserved.
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

import org.onap.policy.drools.event.comm.Topic;
import org.onap.policy.drools.event.comm.bus.UebTopicSource;

/**
 * This topic source implementation specializes in reading messages
 * over an UEB Bus topic source and notifying its listeners
 */
public class SingleThreadedUebTopicSource extends SingleThreadedBusTopicSource 
                                          implements UebTopicSource {

	/**
	 * 
	 * @param servers UEB servers
	 * @param topic UEB Topic to be monitored
	 * @param apiKey UEB API Key (optional)
	 * @param apiSecret UEB API Secret (optional)
	 * @param consumerGroup UEB Reader Consumer Group
	 * @param consumerInstance UEB Reader Instance
	 * @param fetchTimeout UEB fetch timeout
	 * @param fetchLimit UEB fetch limit
	 * @param useHttps does topicSource use HTTPS?
	 * @param allowSelfSignedCerts does topicSource allow self-signed certs?
	 * 
	 * @throws IllegalArgumentException An invalid parameter passed in
	 */
	
		
	public SingleThreadedUebTopicSource(List<String> servers, String topic, 
			                            String apiKey, String apiSecret,
			                            String consumerGroup, String consumerInstance, 
			                            int fetchTimeout, int fetchLimit, boolean useHttps, boolean allowSelfSignedCerts) {
		
		super(servers, topic, apiKey, apiSecret, 
			  consumerGroup, consumerInstance, 
			  fetchTimeout, fetchLimit, useHttps, allowSelfSignedCerts);
		
		this.allowSelfSignedCerts = allowSelfSignedCerts;
		
		this.init();
	}
	
	/**
	 * Initialize the Cambria client
	 */
	@Override
	public void init() {
		this.consumer =
			new BusConsumer.CambriaConsumerWrapper(this.servers, this.topic, 
					                           this.apiKey, this.apiSecret,
					                           this.consumerGroup, this.consumerInstance,
					                           this.fetchTimeout, this.fetchLimit, this.useHttps, this.allowSelfSignedCerts);
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public CommInfrastructure getTopicCommInfrastructure() {
		return Topic.CommInfrastructure.UEB;
	}
	

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("SingleThreadedUebTopicSource [getTopicCommInfrastructure()=")
				.append(getTopicCommInfrastructure()).append(", toString()=").append(super.toString()).append("]");
		return builder.toString();
	}

}
