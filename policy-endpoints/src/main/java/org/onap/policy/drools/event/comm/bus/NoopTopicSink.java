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

import java.util.List;

import org.onap.policy.drools.event.comm.TopicSink;
import org.onap.policy.drools.event.comm.bus.internal.TopicBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NOOP topic sink 
 */
public class NoopTopicSink extends TopicBase implements TopicSink {
	
	/**
	 * factory
	 */
	public static final NoopTopicSinkFactory factory = new IndexedNoopTopicSinkFactory();

	/**
	 * logger
	 */
	private static Logger logger = LoggerFactory.getLogger(NoopTopicSink.class);
	
	/**
	 * net logger
	 */
	private static final Logger netLogger = LoggerFactory.getLogger(NETWORK_LOGGER);
	
	/**
	 * constructor
	 * @param servers  servers
	 * @param topic topic
	 * @throws IllegalArgumentException if an invalid argument has been passed in
	 */
	public NoopTopicSink(List<String> servers, String topic) throws IllegalArgumentException {
		super(servers, topic);
	}

	@Override
	public boolean send(String message) throws IllegalArgumentException, IllegalStateException {
		
		if (message == null || message.isEmpty())
			throw new IllegalArgumentException("Message to send is empty");

		if (!this.alive)
			throw new IllegalStateException(this + " is stopped");
		
		try {
			synchronized (this) {
				this.recentEvents.add(message);
			}
			
			netLogger.info("[OUT|{}|{}]{}{}", this.getTopicCommInfrastructure(), 
			               this.topic, System.lineSeparator(), message);
			
			broadcast(message);
		} catch (Exception e) {
			logger.warn("{}: cannot send because of {}", this, e.getMessage(), e);
			return false;
		}
		
		return true;
	}

	@Override
	public CommInfrastructure getTopicCommInfrastructure() {
		return CommInfrastructure.NOOP;
	}

	@Override
	public boolean start() throws IllegalStateException {
		logger.info("{}: starting", this);
		
		synchronized(this) {
			
			if (this.alive)
				return true;
			
			if (locked)
				throw new IllegalStateException(this + " is locked.");
			
			this.alive = true;
		}
				
		return true;
	}

	@Override
	public boolean stop() throws IllegalStateException {
		synchronized(this) {
			this.alive = false;
		}
		return true;
	}

	@Override
	public void shutdown() throws IllegalStateException {
		this.stop();
	}

	@Override
	public String toString() {
		return "NoopTopicSink [toString()=" + super.toString() + "]";
	}

}
