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
import org.openecomp.policy.drools.event.comm.bus.DmaapTopicSink;
import org.openecomp.policy.common.logging.flexlogger.FlexLogger;
import org.openecomp.policy.common.logging.flexlogger.Logger;

/**
 * This implementation publishes events for the associated DMAAP topic,
 * inline with the calling thread.
 */
public class InlineDmaapTopicSink extends InlineBusTopicSink implements DmaapTopicSink {
	
	protected static Logger logger = 
			FlexLogger.getLogger(InlineDmaapTopicSink.class);
	
	protected final String userName;
	protected final String password;
	
	public InlineDmaapTopicSink(List<String> servers, String topic, 
			                    String apiKey, String apiSecret,
	                            String userName, String password,
			                    String partitionKey) 
		throws IllegalArgumentException {
		
		super(servers, topic, apiKey, apiSecret, partitionKey);
		
		this.userName = userName;
		this.password = password;
	}
	

	@Override
	public void init() {
		this.publisher = 
				new BusPublisher.DmaapPublisherWrapper(this.servers, 
						                               this.topic, 
						                               this.userName, 
						                               this.password);
		if (logger.isInfoEnabled())
			logger.info("DMAAP SINK TOPIC created " + this);
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
