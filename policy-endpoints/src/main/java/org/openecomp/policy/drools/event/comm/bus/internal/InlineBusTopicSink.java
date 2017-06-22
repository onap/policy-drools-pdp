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
import java.util.UUID;

import org.openecomp.policy.drools.event.comm.bus.BusTopicSink;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 * Transport Agnostic Bus Topic Sink to carry out the core functionality
 * to interact with a sink regardless if it is UEB or DMaaP.
 *
 */
public abstract class InlineBusTopicSink extends BusTopicBase implements BusTopicSink {
	
	/**
	 * loggers
	 */
	private static Logger logger = LoggerFactory.getLogger(InlineBusTopicSink.class);
	private static final Logger netLogger = LoggerFactory.getLogger(NETWORK_LOGGER);
	
	/**
	 * The partition key to publish to
	 */
	protected String partitionId;
	
	/**
	 * Am I running?
	 * reflects invocation of start()/stop() 
	 * !locked & start() => alive
	 * stop() => !alive
	 */
	protected volatile boolean alive = false;
	
	/**
	 * Am I locked?
	 * reflects invocation of lock()/unlock() operations
	 * locked => !alive (but not in the other direction necessarily)
	 * locked => !offer, !run, !start, !stop (but this last one is obvious
	 *                                        since locked => !alive)
	 */
	protected volatile boolean locked = false;
	
	/**
	 * message bus publisher
	 */
	protected BusPublisher publisher;

	/**
	 * constructor for abstract sink
	 * 
	 * @param servers servers
	 * @param topic topic
	 * @param apiKey api secret
	 * @param apiSecret api secret
	 * @param partitionId partition id
	 * @param useHttps does connection use HTTPS?
	 * @param allowSelfSignedCerts are self-signed certificates allow
	 * @throws IllegalArgumentException in invalid parameters are passed in
	 */
	public InlineBusTopicSink(List<String> servers, String topic, 
			                  String apiKey, String apiSecret, String partitionId, boolean useHttps, boolean allowSelfSignedCerts)
			throws IllegalArgumentException {
		
		super(servers, topic, apiKey, apiSecret, useHttps, allowSelfSignedCerts);		
		
		if (partitionId == null || partitionId.isEmpty()) {
			this.partitionId = UUID.randomUUID ().toString();
		}
	}
	
	/**
	 * Initialize the Bus publisher
	 */
	public abstract void init();
	
	/**
	 * {@inheritDoc}
	 */
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
				
		this.init();
		return true;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean stop() {
		
		BusPublisher publisherCopy;
		synchronized(this) {
			this.alive = false;
			publisherCopy = this.publisher;
			this.publisher = null;
		}
		
		if (publisherCopy != null) {
			try {
				publisherCopy.close();
			} catch (Exception e) {
				logger.warn("{}: cannot stop publisher because of {}", 
						    this, e.getMessage(), e);
			}
		} else {
			logger.warn("{}: there is no publisher", this);
			return false;
		}
		
		return true;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean lock() {
		
		logger.info("{}: locking", this);	
		
		synchronized (this) {
			if (this.locked)
				return true;
			
			this.locked = true;
		}
		
		return this.stop();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean unlock() {
		
		logger.info("{}: unlocking", this);
		
		synchronized(this) {
			if (!this.locked)
				return true;
			
			this.locked = false;
		}
		
		try {
			return this.start();
		} catch (Exception e) {
			logger.warn("{}: cannot start after unlocking because of {}", 
					    this, e.getMessage(), e);
			return false;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isLocked() {
		return this.locked;
	}	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isAlive() {
		return this.alive;
	}	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean send(String message) throws IllegalArgumentException, IllegalStateException {
		
		if (message == null || message.isEmpty()) {
			throw new IllegalArgumentException("Message to send is empty");
		}

		if (!this.alive) {
			throw new IllegalStateException(this + " is stopped");
		}
		
		try {
			synchronized (this) {
				this.recentEvents.add(message);
			}
			
			netLogger.info("[OUT|{}|{}]{}{}", this.getTopicCommInfrastructure(), 
			               this.topic, System.lineSeparator(), message);
			
			publisher.send(this.partitionId, message);
		} catch (Exception e) {
			logger.warn("{}: cannot send because of {}", this, e.getMessage(), e);
			return false;
		}
		
		return true;
	}
	

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setPartitionKey(String partitionKey) {
		this.partitionId = partitionKey;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getPartitionKey() {
		return this.partitionId;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void shutdown() throws IllegalStateException {
		this.stop();
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public abstract CommInfrastructure getTopicCommInfrastructure();

}
