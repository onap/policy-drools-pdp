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

package org.onap.policy.drools.event.comm;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.onap.policy.drools.event.comm.bus.DmaapTopicSink;
import org.onap.policy.drools.event.comm.bus.DmaapTopicSource;
import org.onap.policy.drools.event.comm.bus.NoopTopicSink;
import org.onap.policy.drools.event.comm.bus.UebTopicSink;
import org.onap.policy.drools.event.comm.bus.UebTopicSource;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.onap.policy.drools.properties.Lockable;
import org.onap.policy.drools.properties.Startable;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Abstraction to managed the system's Networked Topic Endpoints,
 * sources of all events input into the System. 
 */
public interface TopicEndpoint extends Startable, Lockable {
	
	/**
	 * Add Topic Sources to the communication infrastructure initialized per
	 * properties
	 * 
	 * @param properties properties for Topic Source construction
	 * @return a generic Topic Source
	 * @throws IllegalArgumentException when invalid arguments are provided
	 */
	public List<TopicSource> addTopicSources(Properties properties);

	/**
	 * Add Topic Sinks to the communication infrastructure initialized per
	 * properties
	 * 
	 * @param properties properties for Topic Sink construction
	 * @return a generic Topic Sink
	 * @throws IllegalArgumentException when invalid arguments are provided
	 */
	public List<TopicSink> addTopicSinks(Properties properties);
	
	/**
	 * gets all Topic Sources
	 * @return the Topic Source List
	 */
	List<TopicSource> getTopicSources();
	
	/**
	 * get the Topic Sources for the given topic name
	 * 
	 * @param topicName the topic name
	 * 
	 * @return the Topic Source List
	 * @throws IllegalStateException if the entity is in an invalid state
	 * @throws IllegalArgumentException if invalid parameters are present
	 */
	public List<TopicSource> getTopicSources(List<String> topicNames);
	
	/**
	 * gets the Topic Source for the given topic name and 
	 * underlying communication infrastructure type
	 * 
	 * @param commType communication infrastructure type
	 * @param topicName the topic name
	 * 
	 * @return the Topic Source
	 * @throws IllegalStateException if the entity is in an invalid state, for
	 * example multiple TopicReaders for a topic name and communication infrastructure
	 * @throws IllegalArgumentException if invalid parameters are present
	 * @throws UnsupportedOperationException if the operation is not supported.
	 */
	public TopicSource getTopicSource(Topic.CommInfrastructure commType, 
			                          String topicName) 
			throws UnsupportedOperationException;
	
	/**
	 * get the UEB Topic Source for the given topic name
	 * 
	 * @param topicName the topic name
	 * 
	 * @return the UEB Topic Source
	 * @throws IllegalStateException if the entity is in an invalid state, for
	 * example multiple TopicReaders for a topic name and communication infrastructure
	 * @throws IllegalArgumentException if invalid parameters are present
	 */
	public UebTopicSource getUebTopicSource(String topicName);
	
	/**
	 * get the DMAAP Topic Source for the given topic name
	 * 
	 * @param topicName the topic name
	 * 
	 * @return the DMAAP Topic Source
	 * @throws IllegalStateException if the entity is in an invalid state, for
	 * example multiple TopicReaders for a topic name and communication infrastructure
	 * @throws IllegalArgumentException if invalid parameters are present
	 */
	public DmaapTopicSource getDmaapTopicSource(String topicName);
	
	/**
	 * get the Topic Sinks for the given topic name
	 * 
	 * @param topicNames the topic names
	 * @return the Topic Sink List
	 * @throws IllegalStateException
	 * @throws IllegalArgumentException
	 */
	public List<TopicSink> getTopicSinks(List<String> topicNames);
	
	/**
	 * get the Topic Sinks for the given topic name and 
	 * underlying communication infrastructure type
	 * 
	 * @param topicName the topic name
	 * @param commType communication infrastructure type
	 * 
	 * @return the Topic Sink List
	 * @throws IllegalStateException if the entity is in an invalid state, for
	 * example multiple TopicWriters for a topic name and communication infrastructure
	 * @throws IllegalArgumentException if invalid parameters are present
	 */
	public TopicSink getTopicSink(Topic.CommInfrastructure commType, 
			                      String topicName) 
			throws UnsupportedOperationException;
	
	/**
	 * get the Topic Sinks for the given topic name and 
	 * all the underlying communication infrastructure type
	 * 
	 * @param topicName the topic name
	 * @param commType communication infrastructure type
	 * 
	 * @return the Topic Sink List
	 * @throws IllegalStateException if the entity is in an invalid state, for
	 * example multiple TopicWriters for a topic name and communication infrastructure
	 * @throws IllegalArgumentException if invalid parameters are present
	 */
	public List<TopicSink> getTopicSinks(String topicName);
	
	/**
	 * get the UEB Topic Source for the given topic name
	 * 
	 * @param topicName the topic name
	 * 
	 * @return the Topic Source
	 * @throws IllegalStateException if the entity is in an invalid state, for
	 * example multiple TopicReaders for a topic name and communication infrastructure
	 * @throws IllegalArgumentException if invalid parameters are present
	 */
	public UebTopicSink getUebTopicSink(String topicName);
	
	/**
	 * get the no-op Topic Sink for the given topic name
	 * 
	 * @param topicName the topic name
	 * 
	 * @return the Topic Source
	 * @throws IllegalStateException if the entity is in an invalid state, for
	 * example multiple TopicReaders for a topic name and communication infrastructure
	 * @throws IllegalArgumentException if invalid parameters are present
	 */
	public NoopTopicSink getNoopTopicSink(String topicName);
	
	/**
	 * get the DMAAP Topic Source for the given topic name
	 * 
	 * @param topicName the topic name
	 * 
	 * @return the Topic Source
	 * @throws IllegalStateException if the entity is in an invalid state, for
	 * example multiple TopicReaders for a topic name and communication infrastructure
	 * @throws IllegalArgumentException if invalid parameters are present
	 */
	public DmaapTopicSink getDmaapTopicSink(String topicName);
	
	/**
	 * gets only the UEB Topic Sources
	 * @return the UEB Topic Source List
	 */
	public List<UebTopicSource> getUebTopicSources();
	
	/**
	 * gets only the DMAAP Topic Sources
	 * @return the DMAAP Topic Source List
	 */
	public List<DmaapTopicSource> getDmaapTopicSources();
	
	/**
	 * gets all Topic Sinks
	 * @return the Topic Sink List
	 */
	public List<TopicSink> getTopicSinks();
	
	/**
	 * gets only the UEB Topic Sinks
	 * @return the UEB Topic Sink List
	 */
	public List<UebTopicSink> getUebTopicSinks();
	
	/**
	 * gets only the DMAAP Topic Sinks
	 * @return the DMAAP Topic Sink List
	 */
	public List<DmaapTopicSink> getDmaapTopicSinks();
	
	/**
	 * gets only the NOOP Topic Sinks
	 * @return the NOOP Topic Sinks List
	 */
	public List<NoopTopicSink> getNoopTopicSinks();
	
	/**
	 * singleton for global access
	 */
	public static final TopicEndpoint manager = new ProxyTopicEndpointManager();
}

/*
 * ----------------- implementation -------------------
 */

/**
 * This implementation of the Topic Endpoint Manager, proxies operations to appropriate
 * implementations according to the communication infrastructure that are supported
 */
class ProxyTopicEndpointManager implements TopicEndpoint {
	/**
	 * Logger
	 */
	private static Logger logger = LoggerFactory.getLogger(ProxyTopicEndpointManager.class);
	/**
	 * Is this element locked?
	 */
	protected volatile boolean locked = false;
	
	/**
	 * Is this element alive?
	 */
	protected volatile boolean alive = false;
	
	@Override
	public List<TopicSource> addTopicSources(Properties properties) {
		
		// 1. Create UEB Sources
		// 2. Create DMAAP Sources
		
		List<TopicSource> sources = new ArrayList<>();	
		
		sources.addAll(UebTopicSource.factory.build(properties));
		sources.addAll(DmaapTopicSource.factory.build(properties));
		
		if (this.isLocked()) {
			for (TopicSource source : sources) {
				source.lock();
			}
		}
		
		return sources;
	}
	
	@Override
	public List<TopicSink> addTopicSinks(Properties properties) {
		// 1. Create UEB Sinks
		// 2. Create DMAAP Sinks
		
		List<TopicSink> sinks = new ArrayList<>();	
		
		sinks.addAll(UebTopicSink.factory.build(properties));
		sinks.addAll(DmaapTopicSink.factory.build(properties));
		sinks.addAll(NoopTopicSink.factory.build(properties));
		
		if (this.isLocked()) {
			for (TopicSink sink : sinks) {
				sink.lock();
			}
		}
		
		return sinks;
	}

	@Override
	public List<TopicSource> getTopicSources() {
	
		List<TopicSource> sources = new ArrayList<>();
		
		sources.addAll(UebTopicSource.factory.inventory());
		sources.addAll(DmaapTopicSource.factory.inventory());
		
		return sources;
	}

	@Override
	public List<TopicSink> getTopicSinks() {
		
		List<TopicSink> sinks = new ArrayList<>();	
		
		sinks.addAll(UebTopicSink.factory.inventory());
		sinks.addAll(DmaapTopicSink.factory.inventory());
		sinks.addAll(NoopTopicSink.factory.inventory());
		
		return sinks;
	}

	@JsonIgnore
	@Override
	public List<UebTopicSource> getUebTopicSources() {
		return UebTopicSource.factory.inventory();
	}
	
	@JsonIgnore
	@Override
	public List<DmaapTopicSource> getDmaapTopicSources() {
		return DmaapTopicSource.factory.inventory();
	}

	@JsonIgnore
	@Override
	public List<UebTopicSink> getUebTopicSinks() {
		return UebTopicSink.factory.inventory();
	}
	
	@JsonIgnore
	@Override
	public List<DmaapTopicSink> getDmaapTopicSinks() {
		return DmaapTopicSink.factory.inventory();
	}
	
	@JsonIgnore
	@Override
	public List<NoopTopicSink> getNoopTopicSinks() {
		return NoopTopicSink.factory.inventory();
	}

	@Override
	public boolean start() {
		
		synchronized (this) {
			if (this.locked) {
				throw new IllegalStateException(this + " is locked");
			}
			
			if (this.alive) {
				return true;
			}
			
			this.alive = true;
		}
		
		List<Startable> endpoints = getEndpoints();
		
		boolean success = true;
		for (Startable endpoint: endpoints) {
			try {
				success = endpoint.start() && success;
			} catch (Exception e) {
				success = false;
				logger.error("Problem starting endpoint: {}", endpoint, e);
			}
		}
		
		return success;
	}


	@Override
	public boolean stop() {
		
		/* 
		 * stop regardless if it is locked, in other
		 * words, stop operation has precedence over
		 * locks.
		 */
		synchronized (this) {			
			this.alive = false;
		}
		
		List<Startable> endpoints = getEndpoints();
		
		boolean success = true;
		for (Startable endpoint: endpoints) {
			try {
				success = endpoint.stop() && success;
			} catch (Exception e) {
				success = false;
				logger.error("Problem stopping endpoint: {}", endpoint, e);
			}
		}
		
		return success;
	}
	
	/**
	 * 
	 * @return list of managed endpoints
	 */
	@JsonIgnore
	protected List<Startable> getEndpoints() {
		List<Startable> endpoints = new ArrayList<>();

		endpoints.addAll(this.getTopicSources());
		endpoints.addAll(this.getTopicSinks());
		
		return endpoints;
	}
	
	@Override
	public void shutdown() {
		UebTopicSource.factory.destroy();
		UebTopicSink.factory.destroy();
		
		DmaapTopicSource.factory.destroy();
		DmaapTopicSink.factory.destroy();
	}

	@Override
	public boolean isAlive() {
		return this.alive;
	}

	@Override
	public boolean lock() {
		
		synchronized (this) {
			if (locked)
				return true;
			
			this.locked = true;
		}
		
		for (TopicSource source: this.getTopicSources()) {
			source.lock();
		}
		
		for (TopicSink sink: this.getTopicSinks()) {
			sink.lock();
		}
		
		return true;
	}

	@Override
	public boolean unlock() {
		synchronized (this) {
			if (!locked)
				return true;
			
			this.locked = false;
		}
		
		for (TopicSource source: this.getTopicSources()) {
			source.unlock();
		}
		
		for (TopicSink sink: this.getTopicSinks()) {
			sink.unlock();
		}
		
		return true;
	}

	@Override
	public boolean isLocked() {
		return this.locked;
	}

	@Override
	public List<TopicSource> getTopicSources(List<String> topicNames) {
		
		if (topicNames == null) {
			throw new IllegalArgumentException("must provide a list of topics");
		}
		
		List<TopicSource> sources = new ArrayList<>();
		for (String topic: topicNames) {
			try {
				TopicSource uebSource = this.getUebTopicSource(topic);
				if (uebSource != null)
					sources.add(uebSource);
			} catch (Exception e) {
				logger.info("No UEB source for topic: {}", topic, e);
			}
			
			try {
				TopicSource dmaapSource = this.getDmaapTopicSource(topic);
				if (dmaapSource != null)
					sources.add(dmaapSource);
			} catch (Exception e) {
				logger.info("No DMAAP source for topic: {}", topic, e);
			}
		}
		return sources;
	}

	@Override
	public List<TopicSink> getTopicSinks(List<String> topicNames) {
		
		if (topicNames == null) {
			throw new IllegalArgumentException("must provide a list of topics");
		}
		
		List<TopicSink> sinks = new ArrayList<>();
		for (String topic: topicNames) {
			try {
				TopicSink uebSink = this.getUebTopicSink(topic);
				if (uebSink != null)
					sinks.add(uebSink);
			} catch (Exception e) {
				logger.info("No UEB sink for topic: {}", topic, e);
			}
			
			try {
				TopicSink dmaapSink = this.getDmaapTopicSink(topic);
				if (dmaapSink != null)
					sinks.add(dmaapSink);
			} catch (Exception e) {
				logger.info("No DMAAP sink for topic: {}", topic, e);
			}
		}
		return sinks;
	}

	@Override
	public TopicSource getTopicSource(Topic.CommInfrastructure commType, String topicName)
			throws UnsupportedOperationException {
		
		if (commType == null) {
			throw new IllegalArgumentException
				("Invalid parameter: a communication infrastructure required to fetch " + topicName);
		}
		
		if (topicName == null) {
			throw new IllegalArgumentException
				("Invalid parameter: a communication infrastructure required to fetch " + topicName);
		}
		
		switch (commType) {
		case UEB:
			return this.getUebTopicSource(topicName);
		case DMAAP:
			return this.getDmaapTopicSource(topicName);
		case REST:
		default:
			throw new UnsupportedOperationException("Unsupported " + commType.name());
		}
	}

	@Override
	public TopicSink getTopicSink(Topic.CommInfrastructure commType, String topicName)
			throws UnsupportedOperationException {
		if (commType == null) {
			throw new IllegalArgumentException
				("Invalid parameter: a communication infrastructure required to fetch " + topicName);
		}
		
		if (topicName == null) {
			throw new IllegalArgumentException
				("Invalid parameter: a communication infrastructure required to fetch " + topicName);
		}
		
		switch (commType) {
		case UEB:
			return this.getUebTopicSink(topicName);
		case DMAAP:
			return this.getDmaapTopicSink(topicName);
		case REST:
		default:
			throw new UnsupportedOperationException("Unsupported " + commType.name());
		}
	}
	
	@Override
	public List<TopicSink> getTopicSinks(String topicName) {

		if (topicName == null) {
			throw new IllegalArgumentException
				("Invalid parameter: a communication infrastructure required to fetch " + topicName);
		}
		
		List<TopicSink> sinks = new ArrayList<>();
		
		try {
			sinks.add(this.getUebTopicSink(topicName));
		} catch (Exception e) {
			logger.debug("No sink for topic: {}", topicName, e);
		}
		
		try {
			sinks.add(this.getDmaapTopicSink(topicName));
		} catch (Exception e) {
			logger.debug("No sink for topic: {}", topicName, e);
		}
		
		return sinks;
	}

	@Override
	public UebTopicSource getUebTopicSource(String topicName) {
		return UebTopicSource.factory.get(topicName);
	}

	@Override
	public UebTopicSink getUebTopicSink(String topicName) {
		return UebTopicSink.factory.get(topicName);
	}

	@Override
	public DmaapTopicSource getDmaapTopicSource(String topicName) {
		return DmaapTopicSource.factory.get(topicName);
	}

	@Override
	public DmaapTopicSink getDmaapTopicSink(String topicName) {
		return DmaapTopicSink.factory.get(topicName);
	}

	@Override
	public NoopTopicSink getNoopTopicSink(String topicName) {
		return NoopTopicSink.factory.get(topicName);
	}
	
}
