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

package org.onap.policy.drools.event.comm.bus;

import org.onap.policy.drools.event.comm.TopicSink;

/**
 * Topic Sink over Bus Infrastructure (DMAAP/UEB)
 */
public interface BusTopicSink extends ApiKeyEnabled, TopicSink {
	/**
	 * Log Failures after X number of retries
	 */
	public static final int DEFAULT_LOG_SEND_FAILURES_AFTER = 1;
	
	/**
	 * Sets the UEB partition key for published messages
	 * 
	 * @param partitionKey the partition key
	 */
	public void setPartitionKey(String partitionKey);
	
	/**
	 * return the partition key in used by the system to publish messages
	 * 
	 * @return the partition key
	 */
	public String getPartitionKey();
}
