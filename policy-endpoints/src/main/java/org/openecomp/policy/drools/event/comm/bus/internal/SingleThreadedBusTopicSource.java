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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.log4j.Logger;

import org.openecomp.policy.drools.event.comm.TopicListener;
import org.openecomp.policy.drools.event.comm.bus.BusTopicSource;
import org.openecomp.policy.common.logging.eelf.MessageCodes;
import org.openecomp.policy.common.logging.eelf.PolicyLogger;

/**
 * This topic source implementation specializes in reading messages
 * over a bus topic source and notifying its listeners
 */
public abstract class SingleThreadedBusTopicSource 
       extends BusTopicBase
       implements Runnable, BusTopicSource {
	   
	private String className = SingleThreadedBusTopicSource.class.getName();
	/**
	 * Not to be converted to PolicyLogger.
	 * This will contain all instract /out traffic and only that in a single file in a concise format.
	 */
	protected static final Logger networkLogger = Logger.getLogger(NETWORK_LOGGER);
	
	/**
	 * Bus consumer group
	 */
	protected final String consumerGroup;
	
	/**
	 * Bus consumer instance
	 */
	protected final String consumerInstance;
	
	/**
	 * Bus fetch timeout
	 */
	protected final int fetchTimeout;
	
	/**
	 * Bus fetch limit
	 */
	protected final int fetchLimit;
	
	/**
	 * Message Bus Consumer
	 */
	protected BusConsumer consumer;
	
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
	 * Independent thread reading message over my topic
	 */
	protected Thread busPollerThread;
	
	/**
	 * All my subscribers for new message notifications
	 */
	protected final ArrayList<TopicListener> topicListeners = new ArrayList<TopicListener>();
	

	/**
	 * 
	 * @param servers Bus servers
	 * @param topic Bus Topic to be monitored
	 * @param apiKey Bus API Key (optional)
	 * @param apiSecret Bus API Secret (optional)
	 * @param consumerGroup Bus Reader Consumer Group
	 * @param consumerInstance Bus Reader Instance
	 * @param fetchTimeout Bus fetch timeout
	 * @param fetchLimit Bus fetch limit
	 * @param useHttps does the bus use https
	 * @param allowSelfSignedCerts are self-signed certificates allowed
	 * @throws IllegalArgumentException An invalid parameter passed in
	 */
	public SingleThreadedBusTopicSource(List<String> servers, 
										String topic, 
			                  			String apiKey, 
			                  			String apiSecret, 
			                  			String consumerGroup, 
			                  			String consumerInstance,
			                  			int fetchTimeout,
			                  			int fetchLimit,
			                  			boolean useHttps,
			                  			boolean allowSelfSignedCerts) 
	throws IllegalArgumentException {
		
		super(servers, topic, apiKey, apiSecret, useHttps, allowSelfSignedCerts);
		
		if (consumerGroup == null || consumerGroup.isEmpty()) {
			this.consumerGroup = UUID.randomUUID ().toString();
		} else {
			this.consumerGroup = consumerGroup;
		}
		
		if (consumerInstance == null || consumerInstance.isEmpty()) {
			this.consumerInstance = DEFAULT_CONSUMER_INSTANCE;
		} else {
			this.consumerInstance = consumerInstance;
		}
		
		if (fetchTimeout <= 0) {
			this.fetchTimeout = NO_TIMEOUT_MS_FETCH;
		} else {
			this.fetchTimeout = fetchTimeout;
		}
		
		if (fetchLimit <= 0) {
			this.fetchLimit = NO_LIMIT_FETCH;
		} else {
			this.fetchLimit = fetchLimit;
		}
		
	}
	
	/**
	 * Initialize the Bus client
	 */
	public abstract void init() throws Exception;
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void register(TopicListener topicListener) 
		throws IllegalArgumentException {		
		
		PolicyLogger.info(className,"REGISTER: " + topicListener + " INTO " + this);
		
		synchronized(this) {
			if (topicListener == null)
				throw new IllegalArgumentException("TopicListener must be provided");
			
			/* check that this listener is not registered already */
			for (TopicListener listener: this.topicListeners) {
				if (listener == topicListener) {
					// already registered
					return;
				}
			}
			
			this.topicListeners.add(topicListener);
		}
		
		try {
			this.start();
		} catch (Exception e) {
			PolicyLogger.info(className, "new registration of " + topicListener +  
					          ",but can't start source because of " + e.getMessage());
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void unregister(TopicListener topicListener) {
		
		PolicyLogger.info(className, "UNREGISTER: " + topicListener + " FROM " + this);
		
		boolean stop = false;
		synchronized (this) {
			if (topicListener == null)
				throw new IllegalArgumentException("TopicListener must be provided");
			
			this.topicListeners.remove(topicListener);
			stop = (this.topicListeners.isEmpty());
		}
		
		if (stop) {		
			this.stop();
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean lock() {	
		PolicyLogger.info(className, "LOCK: " + this);
		
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
		PolicyLogger.info(className, "UNLOCK: " + this);
		
		synchronized(this) {
			if (!this.locked)
				return true;
			
			this.locked = false;
		}
		
		try {
			return this.start();
		} catch (Exception e) {
			PolicyLogger.warn("can't start after unlocking " + this + 
					          " because of " + e.getMessage());
			return false;
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean start() throws IllegalStateException {
		
		PolicyLogger.info(className, "START: " + this);
		
		synchronized(this) {
			
			if (alive) {
				return true;
			}
			
			if (locked) {
				throw new IllegalStateException(this + " is locked.");
			}
			
			if (this.busPollerThread == null || 
				!this.busPollerThread.isAlive() || 
				this.consumer == null) {
				
				try {
					this.init();
					this.alive = true;
					this.busPollerThread = new Thread(this);
					this.busPollerThread.setName(this.getTopicCommInfrastructure() + "-source-" + this.getTopic());
					busPollerThread.start();
				} catch (Exception e) {
					e.printStackTrace();
					throw new IllegalStateException(e);
				}
			}
		}
		
		return this.alive;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean stop() {
		PolicyLogger.info(className, "STOP: " + this);
		
		synchronized(this) {
			BusConsumer consumerCopy = this.consumer;
			
			this.alive = false;
			this.consumer = null;
			
			if (consumerCopy != null) {
				try {
					consumerCopy.close();
				} catch (Exception e) {
					PolicyLogger.warn(MessageCodes.EXCEPTION_ERROR, e, "CONSUMER.CLOSE", this.toString());
				}
			}
		}
							
		Thread.yield();
				
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isLocked() {
		return this.locked;
	}
	
	/**
	 * broadcast event to all listeners
	 * 
	 * @param message the event
	 * @return true if all notifications are performed with no error, false otherwise
	 */
	protected boolean broadcast(String message) {
		
		/* take a snapshot of listeners */
		List<TopicListener> snapshotListeners = this.snapshotTopicListeners();
		
		boolean success = true;
		for (TopicListener topicListener: snapshotListeners) {
			try {
				topicListener.onTopicEvent(this.getTopicCommInfrastructure(), this.topic, message);
			} catch (Exception e) {
				PolicyLogger.warn(this.className, "ERROR notifying " + topicListener.toString() + 
						          " because of " + e.getMessage() + " @ " + this.toString());
				success = false;
			}
		}
		return success;
	}
	
	/**
	 * take a snapshot of current topic listeners
	 * 
	 * @return the topic listeners
	 */
	protected synchronized List<TopicListener> snapshotTopicListeners() {
		@SuppressWarnings("unchecked")
		List<TopicListener> listeners = (List<TopicListener>) topicListeners.clone();
		return listeners;
	}
	
	/**
	 * Run thread method for the Bus Reader
	 */
	@Override
	public void run() {
		while (this.alive) {
			try {
				for (String event: this.consumer.fetch()) {					
					synchronized (this) {
						this.recentEvents.add(event);
					}
					
					if (networkLogger.isInfoEnabled()) {
						networkLogger.info("IN[" + this.getTopicCommInfrastructure() + "|" + 
						                   this.topic + "]:" + 
						                   event);
					}
					
					PolicyLogger.info(className, this.topic + " <-- " + event);
					broadcast(event);
					
					if (!this.alive)
						break;
				}
			} catch (Exception e) {
				PolicyLogger.error( MessageCodes.EXCEPTION_ERROR, className, e, "CONSUMER.FETCH", this.toString());
			}
		}
		
		PolicyLogger.warn(this.className, "Exiting: " + this);
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean offer(String event) {
		PolicyLogger.info(className, "OFFER: " + event + " TO " + this);
		
		if (!this.alive) {
			throw new IllegalStateException(this + " is not alive.");
		}
		
		synchronized (this) {
			this.recentEvents.add(event);
		}
		
		if (networkLogger.isInfoEnabled()) {
			networkLogger.info("IN[" + this.getTopicCommInfrastructure() + "|" + 
			                    this.topic + "]:" + 
			                    event);
		}
		
		
		return broadcast(event);
	}
	

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("SingleThreadedBusTopicSource [consumerGroup=").append(consumerGroup)
				.append(", consumerInstance=").append(consumerInstance).append(", fetchTimeout=").append(fetchTimeout)
				.append(", fetchLimit=").append(fetchLimit)
				.append(", consumer=").append(this.consumer).append(", alive=")
				.append(alive).append(", locked=").append(locked).append(", uebThread=").append(busPollerThread)
				.append(", topicListeners=").append(topicListeners.size()).append(", toString()=").append(super.toString())
				.append("]");
		return builder.toString();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isAlive() {
		return alive;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getConsumerGroup() {
		return consumerGroup;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getConsumerInstance() {
		return consumerInstance;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void shutdown() throws IllegalStateException {
		this.stop();
		this.topicListeners.clear();
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getFetchTimeout() {
		return fetchTimeout;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getFetchLimit() {
		return fetchLimit;
	}

}
