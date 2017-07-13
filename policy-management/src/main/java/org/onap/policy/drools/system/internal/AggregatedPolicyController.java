/*-
 * ============LICENSE_START=======================================================
 * policy-management
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

package org.onap.policy.drools.system.internal;

import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.event.comm.Topic;
import org.onap.policy.drools.event.comm.TopicEndpoint;
import org.onap.policy.drools.event.comm.TopicListener;
import org.onap.policy.drools.event.comm.TopicSink;
import org.onap.policy.drools.event.comm.TopicSource;
import org.onap.policy.drools.features.PolicyControllerFeatureAPI;
import org.onap.policy.drools.persistence.SystemPersistence;
import org.onap.policy.drools.properties.PolicyProperties;
import org.onap.policy.drools.protocol.configuration.DroolsConfiguration;
import org.onap.policy.drools.system.PolicyController;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * This implementation of the Policy Controller merely aggregates and tracks for
 * management purposes all underlying resources that this controller depends upon.
 */
public class AggregatedPolicyController implements PolicyController, 
                                                   TopicListener {
	
	/**
	 * Logger
	 */
	private static Logger logger = LoggerFactory.getLogger(AggregatedPolicyController.class); 
	
	/**
	 * identifier for this policy controller
	 */
	protected final String name;
	
	/**
	 * Abstracted Event Sources List regardless communication
	 * technology
	 */
	protected final List<? extends TopicSource> sources;
	
	/**
	 * Abstracted Event Sinks List regardless communication
	 * technology
	 */
	protected final List<? extends TopicSink> sinks;
	
	/**
	 * Mapping topics to sinks
	 */
	@JsonIgnore
	protected final HashMap<String, TopicSink> topic2Sinks =
		new HashMap<String, TopicSink>();
	
	/**
	 * Is this Policy Controller running (alive) ?
	 * reflects invocation of start()/stop() only
	 */
	protected volatile boolean alive;
	
	/**
	 * Is this Policy Controller locked ?
	 * reflects if i/o controller related operations and start 
	 * are permitted,
	 * more specifically: start(), deliver() and onTopicEvent().
	 * It does not affect the ability to stop the
	 * underlying drools infrastructure 
	 */
	protected volatile boolean locked;
	
	/**
	 * Policy Drools Controller
	 */
	protected volatile DroolsController droolsController;
	
	/**
	 * Properties used to initialize controller
	 */
	protected final Properties properties;
	
	/**
	 * Constructor version mainly used for bootstrapping at initialization time
	 * a policy engine controller
	 * 
	 * @param name controller name
	 * @param properties
	 * 
	 * @throws IllegalArgumentException when invalid arguments are provided
	 */
	public AggregatedPolicyController(String name, Properties properties) 
			throws IllegalArgumentException {
		
		this.name = name;
		
		/*
		 * 1. Register read topics with network infrastructure (ueb, dmaap, rest)
		 * 2. Register write topics with network infrastructure (ueb, dmaap, rest)
		 * 3. Register with drools infrastructure
		 */
		
		// Create/Reuse Readers/Writers for all event sources endpoints
		
		this.sources = TopicEndpoint.manager.addTopicSources(properties);
		this.sinks = TopicEndpoint.manager.addTopicSinks(properties);
		
		initDrools(properties);		
		initSinks();		
		
		/* persist new properties */
		SystemPersistence.manager.storeController(name, properties);	
		this.properties = properties;
	}
	
	/**
	 * initialize drools layer
	 * @throws IllegalArgumentException if invalid parameters are passed in
	 */
	protected void initDrools(Properties properties) throws IllegalArgumentException {
		try {
			// Register with drools infrastructure
			this.droolsController = DroolsController.factory.build(properties, sources, sinks);
		} catch (Exception | LinkageError e) {
			logger.error("{}: cannot init-drools because of {}", this, e.getMessage(), e);
			throw new IllegalArgumentException(e);
		}
	}
	
	/**
	 * initialize sinks
	 * @throws IllegalArgumentException if invalid parameters are passed in
	 */
	protected void initSinks() throws IllegalArgumentException {
		this.topic2Sinks.clear();
		for (TopicSink sink: sinks) {
			this.topic2Sinks.put(sink.getTopic(), sink);
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean updateDrools(DroolsConfiguration newDroolsConfiguration) {
		
		DroolsConfiguration oldDroolsConfiguration =
				new DroolsConfiguration(this.droolsController.getArtifactId(),
										this.droolsController.getGroupId(), 
				                        this.droolsController.getVersion());
		
		if (oldDroolsConfiguration.getGroupId().equalsIgnoreCase(newDroolsConfiguration.getGroupId()) &&
			oldDroolsConfiguration.getArtifactId().equalsIgnoreCase(newDroolsConfiguration.getArtifactId()) &&
			oldDroolsConfiguration.getVersion().equalsIgnoreCase(newDroolsConfiguration.getVersion())) {
			logger.warn("{}: cannot update-drools: identical configuration {} vs {}", 
					    this, oldDroolsConfiguration, newDroolsConfiguration);
			return true;
		}
		
		try {
			/* Drools Controller created, update initialization properties for restarts */
			
			this.properties.setProperty(PolicyProperties.RULES_GROUPID, newDroolsConfiguration.getGroupId());
			this.properties.setProperty(PolicyProperties.RULES_ARTIFACTID, newDroolsConfiguration.getArtifactId());
			this.properties.setProperty(PolicyProperties.RULES_VERSION, newDroolsConfiguration.getVersion());
					
			SystemPersistence.manager.storeController(name, this.properties);
			
			this.initDrools(this.properties);
			
			/* set drools controller to current locked status */
			
			if (this.isLocked())
				this.droolsController.lock();
			else
				this.droolsController.unlock();
			
			/* set drools controller to current alive status */
			
			if (this.isAlive())
				this.droolsController.start();
			else
				this.droolsController.stop();
			
		} catch (IllegalArgumentException e) {
			logger.error("{}: cannot update-drools because of {}", this, e.getMessage(), e);
			return false;
		}	
		
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getName() {
		return this.name;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean start() throws IllegalStateException {		
		logger.info("{}: start", this);
		
		for (PolicyControllerFeatureAPI feature : PolicyControllerFeatureAPI.providers.getList()) {
			try {
				if (feature.beforeStart(this))
					return true;
			} catch (Exception e) {
				logger.error("{}: feature {} before-start failure because of {}",  
			                 this, feature.getClass().getName(), e.getMessage(), e);
			}
		}
		
		if (this.isLocked())
			throw new IllegalStateException("Policy Controller " + name  + " is locked");

		synchronized(this) {
			if (this.alive)
				return true;
			
			this.alive = true;
		}

		boolean success = this.droolsController.start();
		
		// register for events
		
		for (TopicSource source: sources) {
			source.register(this);
		}
		
		for (TopicSink sink: sinks) {
			try {
				sink.start();
			} catch (Exception e) {
				logger.error("{}: cannot start {} because of {}",  
		                     this, sink, e.getMessage(), e);
			}
		}
		
		for (PolicyControllerFeatureAPI feature : PolicyControllerFeatureAPI.providers.getList()) {
			try {
				if (feature.afterStart(this))
					return true;
			} catch (Exception e) {
				logger.error("{}: feature {} after-start failure because of {}",  
		                     this, feature.getClass().getName(), e.getMessage(), e);
			}
		}
		
		return success;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean stop() {
		logger.info("{}: stop", this);
		
		for (PolicyControllerFeatureAPI feature : PolicyControllerFeatureAPI.providers.getList()) {
			try {
				if (feature.beforeStop(this))
					return true;
			} catch (Exception e) {
				logger.error("{}: feature {} before-stop failure because of {}",  
		                     this, feature.getClass().getName(), e.getMessage(), e);
			}
		}
		
		/* stop regardless locked state */
		
		synchronized(this) {
			if (!this.alive)
				return true;
			
			this.alive = false;
		}
		
		// 1. Stop registration
		
		for (TopicSource source: sources) {
			source.unregister(this);
		}
		
		boolean success = this.droolsController.stop();
		
		for (PolicyControllerFeatureAPI feature : PolicyControllerFeatureAPI.providers.getList()) {
			try {
				if (feature.afterStop(this))
					return true;
			} catch (Exception e) {
				logger.error("{}: feature {} after-stop failure because of {}",  
	                         this, feature.getClass().getName(), e.getMessage(), e);
			}
		}
		
		return success;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void shutdown() throws IllegalStateException {
		logger.info("{}: shutdown", this);
		
		for (PolicyControllerFeatureAPI feature : PolicyControllerFeatureAPI.providers.getList()) {
			try {
				if (feature.beforeShutdown(this))
					return;
			} catch (Exception e) {
				logger.error("{}: feature {} before-shutdown failure because of {}",  
	                         this, feature.getClass().getName(), e.getMessage(), e);
			}
		}
		
		this.stop();
		
		DroolsController.factory.shutdown(this.droolsController);
		
		for (PolicyControllerFeatureAPI feature : PolicyControllerFeatureAPI.providers.getList()) {
			try {
				if (feature.afterShutdown(this))
					return;
			} catch (Exception e) {
				logger.error("{}: feature {} after-shutdown failure because of {}",  
                             this, feature.getClass().getName(), e.getMessage(), e);
			}
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void halt() throws IllegalStateException {
		logger.info("{}: halt", this);
		
		for (PolicyControllerFeatureAPI feature : PolicyControllerFeatureAPI.providers.getList()) {
			try {
				if (feature.beforeHalt(this))
					return;
			} catch (Exception e) {
				logger.error("{}: feature {} before-halt failure because of {}",  
                             this, feature.getClass().getName(), e.getMessage(), e);
			}
		}
		
		this.stop();	
		DroolsController.factory.destroy(this.droolsController);
		SystemPersistence.manager.deleteController(this.name);
		
		for (PolicyControllerFeatureAPI feature : PolicyControllerFeatureAPI.providers.getList()) {
			try {
				if (feature.afterHalt(this))
					return;
			} catch (Exception e) {
				logger.error("{}: feature {} after-halt failure because of {}",  
                             this, feature.getClass().getName(), e.getMessage(), e);
			}
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onTopicEvent(Topic.CommInfrastructure commType, 
			                    String topic, String event) {

		if (logger.isDebugEnabled())
			logger.debug("{}: event offered from {}:{}: {}", this, commType, topic, event);
		
		for (PolicyControllerFeatureAPI feature : PolicyControllerFeatureAPI.providers.getList()) {
			try {
				if (feature.beforeOffer(this, commType, topic, event))
					return;
			} catch (Exception e) {
				logger.error("{}: feature {} before-offer failure because of {}",  
                             this, feature.getClass().getName(), e.getMessage(), e);
			}
		}
		
		if (this.locked)
			return;
		
		if (!this.alive)
			return;
		
		boolean success = this.droolsController.offer(topic, event);
		
		for (PolicyControllerFeatureAPI feature : PolicyControllerFeatureAPI.providers.getList()) {
			try {
				if (feature.afterOffer(this, commType, topic, event, success))
					return;
			} catch (Exception e) {
				logger.error("{}: feature {} after-offer failure because of {}",  
                             this, feature.getClass().getName(), e.getMessage(), e);
			}
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean deliver(Topic.CommInfrastructure commType, 
			               String topic, Object event)
		throws IllegalArgumentException, IllegalStateException,
               UnsupportedOperationException {	
		
		if (logger.isDebugEnabled())
			logger.debug("{}: deliver event to {}:{}: {}", this, commType, topic, event);
		
		for (PolicyControllerFeatureAPI feature : PolicyControllerFeatureAPI.providers.getList()) {
			try {
				if (feature.beforeDeliver(this, commType, topic, event))
					return true;
			} catch (Exception e) {
				logger.error("{}: feature {} before-deliver failure because of {}",  
                             this, feature.getClass().getName(), e.getMessage(), e);
			}
		}
		
		if (topic == null || topic.isEmpty())
			throw new IllegalArgumentException("Invalid Topic");
		
		if (event == null)
			throw new IllegalArgumentException("Invalid Event");
		
		if (!this.isAlive())
			throw new IllegalStateException("Policy Engine is stopped");
		
		if (this.isLocked())
			throw new IllegalStateException("Policy Engine is locked");
		
		if (!this.topic2Sinks.containsKey(topic)) {
			logger.warn("{}: cannot deliver event because the sink {}:{} is not registered: {}", 
					     this, commType, topic, event);
			throw new IllegalArgumentException("Unsuported topic " + topic + " for delivery");
		}
		
		boolean success = this.droolsController.deliver(this.topic2Sinks.get(topic), event);
		
		for (PolicyControllerFeatureAPI feature : PolicyControllerFeatureAPI.providers.getList()) {
			try {
				if (feature.afterDeliver(this, commType, topic, event, success))
					return success;
			} catch (Exception e) {
				logger.error("{}: feature {} after-deliver failure because of {}",  
                             this, feature.getClass().getName(), e.getMessage(), e);
			}
		}
		
		return success;
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
	public boolean lock() {
		logger.info("{}: lock", this);
		
		for (PolicyControllerFeatureAPI feature : PolicyControllerFeatureAPI.providers.getList()) {
			try {
				if (feature.beforeLock(this))
					return true;
			} catch (Exception e) {
				logger.error("{}: feature {} before-lock failure because of {}",  
                             this, feature.getClass().getName(), e.getMessage(), e);
			}
		}
		
		synchronized(this) {
			if (this.locked)
				return true;
			
			this.locked = true;
		}
		
		// it does not affect associated sources/sinks, they are
		// autonomous entities
		
		boolean success = this.droolsController.lock();
		
		for (PolicyControllerFeatureAPI feature : PolicyControllerFeatureAPI.providers.getList()) {
			try {
				if (feature.afterLock(this))
					return true;
			} catch (Exception e) {
				logger.error("{}: feature {} after-lock failure because of {}",  
                             this, feature.getClass().getName(), e.getMessage(), e);
			}
		}
		
		return success;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean unlock() {
		
		logger.info("{}: unlock", this);
		
		for (PolicyControllerFeatureAPI feature : PolicyControllerFeatureAPI.providers.getList()) {
			try {
				if (feature.beforeUnlock(this))
					return true;
			} catch (Exception e) {
				logger.error("{}: feature {} before-unlock failure because of {}",  
                             this, feature.getClass().getName(), e.getMessage(), e);
			}
		}
		
		synchronized(this) {
			if (!this.locked)
				return true;
			
			this.locked = false;
		}
		
		boolean success = this.droolsController.unlock();
		
		for (PolicyControllerFeatureAPI feature : PolicyControllerFeatureAPI.providers.getList()) {
			try {
				if (feature.afterUnlock(this))
					return true;
			} catch (Exception e) {
				logger.error("{}: feature {} after-unlock failure because of {}",  
                             this, feature.getClass().getName(), e.getMessage(), e);
			}
		}
		
		return success;
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
	public List<? extends TopicSource> getTopicSources() {
		return this.sources;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<? extends TopicSink> getTopicSinks() {
		return this.sinks;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public DroolsController getDrools() {
		return this.droolsController;
	}
	

	/**
	 * {@inheritDoc}
	 */
	@Override
	@JsonIgnore
	public Properties getProperties() {
		return this.properties;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("AggregatedPolicyController [name=").append(name).append(", alive=").append(alive).append(", locked=").append(locked)
				.append(", droolsController=").append(droolsController).append("]");
		return builder.toString();
	}

}

