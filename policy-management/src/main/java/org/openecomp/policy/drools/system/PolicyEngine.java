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

package org.openecomp.policy.drools.system;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openecomp.policy.drools.controller.DroolsController;
import org.openecomp.policy.drools.core.jmx.PdpJmxListener;
import org.openecomp.policy.drools.event.comm.Topic;
import org.openecomp.policy.drools.event.comm.Topic.CommInfrastructure;
import org.openecomp.policy.drools.event.comm.TopicEndpoint;
import org.openecomp.policy.drools.event.comm.TopicListener;
import org.openecomp.policy.drools.event.comm.TopicSink;
import org.openecomp.policy.drools.event.comm.TopicSource;
import org.openecomp.policy.drools.features.PolicyControllerFeatureAPI;
import org.openecomp.policy.drools.features.PolicyEngineFeatureAPI;
import org.openecomp.policy.drools.http.server.HttpServletServer;
import org.openecomp.policy.drools.persistence.SystemPersistence;
import org.openecomp.policy.drools.properties.Lockable;
import org.openecomp.policy.drools.properties.PolicyProperties;
import org.openecomp.policy.drools.properties.Startable;
import org.openecomp.policy.drools.protocol.coders.EventProtocolCoder;
import org.openecomp.policy.drools.protocol.configuration.ControllerConfiguration;
import org.openecomp.policy.drools.protocol.configuration.PdpdConfiguration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Policy Engine, the top abstraction for the Drools PDP Policy Engine.
 * It abstracts away a Drools PDP Engine from management purposes.
 * This is the best place to looking at the code from a top down approach. 
 * Other managed entities can be obtained from the PolicyEngine, hierarchically. 
 * <br>
 * PolicyEngine 1 --- * PolicyController 1 --- 1 DroolsController 1 --- 1 PolicyContainer 1 --- * PolicySession
 * <br>
 * PolicyEngine 1 --- 1 TopicEndpointManager 1 -- * TopicReader 1 --- 1 UebTopicReader
 * <br>
 * PolicyEngine 1 --- 1 TopicEndpointManager 1 -- * TopicReader 1 --- 1 DmaapTopicReader
 * <br>
 * PolicyEngine 1 --- 1 TopicEndpointManager 1 -- * TopicWriter 1 --- 1 DmaapTopicWriter
 * <br>
 * PolicyEngine 1 --- 1 TopicEndpointManager 1 -- * TopicReader 1 --- 1 RestTopicReader
 * <br>
 * PolicyEngine 1 --- 1 TopicEndpointManager 1 -- * TopicWriter 1 --- 1 RestTopicWriter
 * <br>
 * PolicyEngine 1 --- 1 ManagementServer
 */
public interface PolicyEngine extends Startable, Lockable, TopicListener {
	
	/**
	 * Default Config Server Port
	 */
	public static final int CONFIG_SERVER_DEFAULT_PORT = 9696;
	
	/**
	 * Default Config Server Hostname
	 */
	public static final String CONFIG_SERVER_DEFAULT_HOST = "localhost";
	
	/**
	 * configure the policy engine according to the given properties
	 * 
	 * @param properties Policy Engine properties
	 * @throws IllegalArgumentException when invalid or insufficient 
	 *         properties are provided
	 */
	public void configure(Properties properties)  throws IllegalArgumentException;

	/**
	 * registers a new Policy Controller with the Policy Engine
	 * initialized per properties.
	 * 
	 * @param controller name
	 * @param properties properties to initialize the Policy Controller
	 * @throws IllegalArgumentException when invalid or insufficient 
	 *         properties are provided
	 * @throws IllegalStateException when the engine is in a state where
	 *         this operation is not permitted.
	 * @return the newly instantiated Policy Controller
	 */
	public PolicyController createPolicyController(String name, Properties properties)
		throws IllegalArgumentException, IllegalStateException;
	
	/**
	 * updates the Policy Engine with the given configuration
	 * 
	 * @param configuration the configuration
	 * @return success or failure
	 * @throws IllegalArgumentException if invalid argument provided
	 * @throws IllegalStateException if the system is in an invalid state
	 */
	public boolean configure(PdpdConfiguration configuration)
		throws IllegalArgumentException, IllegalStateException;
	
	/**
	 * updates a set of Policy Controllers with configuration information
	 * 
	 * @param configuration
	 * @return
	 * @throws IllegalArgumentException
	 * @throws IllegalStateException
	 */
	public List<PolicyController> updatePolicyControllers(List<ControllerConfiguration> configuration)
		throws IllegalArgumentException, IllegalStateException;
	
	/**
	 * updates an already existing Policy Controller with configuration information
	 * 
	 * @param configuration configuration
	 * 
	 * @return the updated Policy Controller
	 * @throws IllegalArgumentException in the configuration is invalid
	 * @throws IllegalStateException if the controller is in a bad state
	 * @throws Exception any other reason
	 */
	public PolicyController updatePolicyController(ControllerConfiguration configuration)
		throws Exception;

	/**
	 * removes the Policy Controller identified by its name from the Policy Engine
	 * 
	 * @param name name of the Policy Controller
	 * @return the removed Policy Controller
	 */
	public void removePolicyController(String name);
	
	/**
	 * removes a Policy Controller from the Policy Engine
	 * @param controller the Policy Controller to remove from the Policy Engine
	 */
	public void removePolicyController(PolicyController controller);

	/**
	 * returns a list of the available Policy Controllers
	 * 
	 * @return list of Policy Controllers
	 */
	public List<PolicyController> getPolicyControllers();
	

	/**
	 * get policy controller names
	 * 
	 * @return list of controller names
	 */
	public List<String> getPolicyControllerIds();
	
	/**
	 * get unmanaged sources
	 * 
	 * @return unmanaged sources
	 */
	public List<TopicSource> getSources();
	
	/**
	 * get unmanaged sinks
	 * 
	 * @return unmanaged sinks
	 */
	public List<TopicSink> getSinks();
	
	/**
	 * get unmmanaged http servers list
	 * @return http servers
	 */
	public List<HttpServletServer> getHttpServers();
	
	/**
	 * get properties configuration
	 * 
	 * @return properties objects
	 */
	public Properties getProperties();
	
	/**
	 * get features attached to the Policy Engine
	 * @return list of features
	 */
	public List<PolicyEngineFeatureAPI> getFeatureProviders();
	
	/**
	 * get named feature attached to the Policy Engine
	 * @return the feature
	 */
	public PolicyEngineFeatureAPI getFeatureProvider(String featureName) 
			throws IllegalArgumentException;
	
	/**
	 * get features attached to the Policy Engine
	 * @return list of features
	 */
	public List<String> getFeatures();
	
	/**
	 * Attempts the dispatching of an "event" object
	 * 
	 * @param topic topic
	 * @param event the event object to send
	 * 
	 * @return true if successful, false if a failure has occurred.
	 * @throws IllegalArgumentException when invalid or insufficient 
	 *         properties are provided
	 * @throws IllegalStateException when the engine is in a state where
	 *         this operation is not permitted (ie. locked or stopped).
	 */
	public boolean deliver(String topic, Object event)
			throws IllegalArgumentException, IllegalStateException;
	
	/**
	 * Attempts the dispatching of an "event" object over communication 
	 * infrastructure "busType"
	 * 
	 * @param eventBus Communication infrastructure identifier
	 * @param topic topic
	 * @param event the event object to send
	 * 
	 * @return true if successful, false if a failure has occurred.
	 * @throws IllegalArgumentException when invalid or insufficient 
	 *         properties are provided
	 * @throws IllegalStateException when the engine is in a state where
	 *         this operation is not permitted (ie. locked or stopped).
	 * @throws UnsupportedOperationException when the engine cannot deliver due
	 *         to the functionality missing (ie. communication infrastructure
	 *         not supported.
	 */
	public boolean deliver(String busType, String topic, Object event)
			throws IllegalArgumentException, IllegalStateException, 
			       UnsupportedOperationException;
	
	/**
	 * Attempts the dispatching of an "event" object over communication 
	 * infrastructure "busType"
	 * 
	 * @param eventBus Communication infrastructure enum
	 * @param topic topic
	 * @param event the event object to send
	 * 
	 * @return true if successful, false if a failure has occurred.
	 * @throws IllegalArgumentException when invalid or insufficient 
	 *         properties are provided
	 * @throws IllegalStateException when the engine is in a state where
	 *         this operation is not permitted (ie. locked or stopped).
	 * @throws UnsupportedOperationException when the engine cannot deliver due
	 *         to the functionality missing (ie. communication infrastructure
	 *         not supported.
	 */
	public boolean deliver(CommInfrastructure busType, String topic, Object event)
			throws IllegalArgumentException, IllegalStateException, 
			       UnsupportedOperationException;
	
	/**
	 * Attempts delivering of an String over communication 
	 * infrastructure "busType"
	 * 
	 * @param eventBus Communication infrastructure identifier
	 * @param topic topic
	 * @param event the event object to send
	 * 
	 * @return true if successful, false if a failure has occurred.
	 * @throws IllegalArgumentException when invalid or insufficient 
	 *         properties are provided
	 * @throws IllegalStateException when the engine is in a state where
	 *         this operation is not permitted (ie. locked or stopped).
	 * @throws UnsupportedOperationException when the engine cannot deliver due
	 *         to the functionality missing (ie. communication infrastructure
	 *         not supported.
	 */
	public boolean deliver(CommInfrastructure busType, String topic, 
			               String event)
			throws IllegalArgumentException, IllegalStateException, 
			       UnsupportedOperationException;
	
	/**
	 * Invoked when the host goes into the active state.
	 */
	public void activate();

	/**
	 * Invoked when the host goes into the standby state.
	 */
	public void deactivate();
	
	/**
	 * Policy Engine Manager
	 */
	public final static PolicyEngine manager = new PolicyEngineManager();
}

/**
 * Policy Engine Manager Implementation
 */
class PolicyEngineManager implements PolicyEngine {
	/**
	 * logger
	 */
	private static Logger  logger = LoggerFactory.getLogger(PolicyEngineManager.class);  	
	
	/**
	 * Is the Policy Engine running?
	 */
	protected boolean alive = false;
	
	/**
	 * Is the engine locked? 
	 */
	protected boolean locked = false;	
	
	/**
	 * Properties used to initialize the engine
	 */
	protected Properties properties;
	
	/**
	 * Policy Engine Sources
	 */
	protected List<? extends TopicSource> sources = new ArrayList<>();
	
	/**
	 * Policy Engine Sinks
	 */
	protected List<? extends TopicSink> sinks = new ArrayList<>();
	
	/**
	 * Policy Engine HTTP Servers
	 */
	protected List<HttpServletServer> httpServers = new ArrayList<HttpServletServer>();
	
	/**
	 * gson parser to decode configuration requests
	 */
	protected Gson decoder = new GsonBuilder().disableHtmlEscaping().create();
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void configure(Properties properties) throws IllegalArgumentException {
		
		if (properties == null) {
			logger.warn("No properties provided");
			throw new IllegalArgumentException("No properties provided");
		}
		
		/* policy-engine dispatch pre configure hook */
		for (PolicyEngineFeatureAPI feature : PolicyEngineFeatureAPI.providers.getList()) {
			try {
				if (feature.beforeConfigure(this, properties))
					return;
			} catch (Exception e) {
				logger.error("{}: feature {} before-configure failure because of {}",  
				             this, feature.getClass().getName(), e.getMessage(), e);
			}
		}
		
		this.properties = properties;
		
		try {
			this.sources = TopicEndpoint.manager.addTopicSources(properties);
			for (TopicSource source: this.sources) {
				source.register(this);
			}
		} catch (Exception e) {
			logger.error("{}: add-sources failed", this, e);
		}
		
		try {
			this.sinks = TopicEndpoint.manager.addTopicSinks(properties);
		} catch (IllegalArgumentException e) {
			logger.error("{}: add-sinks failed", this, e);
		}
		
		try {
			this.httpServers = HttpServletServer.factory.build(properties);
		} catch (IllegalArgumentException e) {
			logger.error("{}: add-http-servers failed", this, e);
		}
		
		/* policy-engine dispatch post configure hook */
		for (PolicyEngineFeatureAPI feature : PolicyEngineFeatureAPI.providers.getList()) {
			try {
				if (feature.afterConfigure(this))
					return;
			} catch (Exception e) {
				logger.error("{}: feature {} after-configure failure because of {}",  
						     this, feature.getClass().getName(), e.getMessage(), e);
			}
		}
		
		return;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized PolicyController createPolicyController(String name, Properties properties) 
			throws IllegalArgumentException, IllegalStateException {
		
		// check if a PROPERTY_CONTROLLER_NAME property is present
		// if so, override the given name
		
		String propertyControllerName = properties.getProperty(PolicyProperties.PROPERTY_CONTROLLER_NAME);
		if (propertyControllerName != null && !propertyControllerName.isEmpty())  {
			if (!propertyControllerName.equals(name)) {
				throw new IllegalStateException("Proposed name (" + name + 
						                        ") and properties name (" + propertyControllerName + 
						                        ") don't match");
			}
			name = propertyControllerName;
		}
		
		PolicyController controller;
		for (PolicyControllerFeatureAPI controllerFeature : PolicyControllerFeatureAPI.providers.getList()) {
			try {
				controller = controllerFeature.beforeCreate(name, properties);
				if (controller != null)
					return controller;
			} catch (Exception e) {
				logger.error("{}: feature {} before-controller-create failure because of {}",  
				             this, controllerFeature.getClass().getName(), e.getMessage(), e);
			}
		}
		
		controller = PolicyController.factory.build(name, properties);	
		if (this.isLocked())
			controller.lock();
		
		// feature hook
		for (PolicyControllerFeatureAPI controllerFeature : PolicyControllerFeatureAPI.providers.getList()) {
			try {
				if (controllerFeature.afterCreate(controller))
					return controller;
			} catch (Exception e) {
				logger.error("{}: feature {} after-controller-create failure because of {}",  
			                 this, controllerFeature.getClass().getName(), e.getMessage(), e);
			}
		}
		
		return controller;
	}
	

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean configure(PdpdConfiguration config) throws IllegalArgumentException, IllegalStateException {
	
		if (config == null)
			throw new IllegalArgumentException("No configuration provided");
		
		String entity = config.getEntity();
		
		switch (entity) {
		case PdpdConfiguration.CONFIG_ENTITY_CONTROLLER:
			/* only this one supported for now */
			List<ControllerConfiguration> configControllers = config.getControllers();
			if (configControllers == null || configControllers.isEmpty()) {
				if (logger.isInfoEnabled())
					logger.info("No controller configuration provided: " + config);
				return false;
			}
			List<PolicyController> policyControllers = this.updatePolicyControllers(config.getControllers());
			if (policyControllers == null || policyControllers.isEmpty())
				return false;
			else if (policyControllers.size() == configControllers.size())
				return true;
			
			return false;
		default:
			String msg = "Configuration Entity is not supported: " + entity;
			logger.warn(msg);
			throw new IllegalArgumentException(msg);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<PolicyController> updatePolicyControllers(List<ControllerConfiguration> configControllers)
			throws IllegalArgumentException, IllegalStateException {
		
		List<PolicyController> policyControllers = new ArrayList<PolicyController>();
		if (configControllers == null || configControllers.isEmpty()) {
			if (logger.isInfoEnabled())
				logger.info("No controller configuration provided: " + configControllers);
			return policyControllers;
		}
		
		for (ControllerConfiguration configController: configControllers) {
			try {
				PolicyController policyController = this.updatePolicyController(configController);
				policyControllers.add(policyController);
			} catch (Exception e) {
				logger.error("{}: cannot update-policy-controllers because of {}", this, e.getMessage(), e);
			}
		}
		
		return policyControllers;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public PolicyController updatePolicyController(ControllerConfiguration configController) 
		   throws Exception {
		
		if (configController == null) 
			throw new IllegalArgumentException("No controller configuration has been provided");
		
		String controllerName = configController.getName();	
		if (controllerName == null || controllerName.isEmpty()) {
			logger.warn("controller-name  must be provided");
			throw new IllegalArgumentException("No controller configuration has been provided");
		}
		
		PolicyController policyController = null;
		try {		
			String operation = configController.getOperation();
			if (operation == null || operation.isEmpty()) {
				logger.warn("operation must be provided");
				throw new IllegalArgumentException("operation must be provided");
			}
			
			try {
				policyController = PolicyController.factory.get(controllerName);
			} catch (IllegalArgumentException e) {
				// not found
				logger.warn("Policy Controller " + controllerName + " not found");
			}
			
			if (policyController == null) {
				
				if (operation.equalsIgnoreCase(ControllerConfiguration.CONFIG_CONTROLLER_OPERATION_LOCK) ||
					operation.equalsIgnoreCase(ControllerConfiguration.CONFIG_CONTROLLER_OPERATION_UNLOCK)) {
					throw new IllegalArgumentException(controllerName + " is not available for operation " + operation);
				}
				
				/* Recovery case */
				
				logger.warn("controller " + controllerName + " does not exist.  " +
				            "Attempting recovery from disk");	
				
				Properties properties = 
						SystemPersistence.manager.getControllerProperties(controllerName);
				
				/* 
				 * returned properties cannot be null (per implementation) 
				 * assert (properties != null)
				 */
				
				if (properties == null) {
					throw new IllegalArgumentException(controllerName + " is invalid");
				}
				
				logger.warn("controller " + controllerName + " being recovered. " +
			                "Reset controller's bad maven coordinates to brainless");
				
				/* 
				 * try to bring up bad controller in brainless mode,
				 * after having it working, apply the new create/update operation.
				 */
				properties.setProperty(PolicyProperties.RULES_GROUPID, DroolsController.NO_GROUP_ID);
				properties.setProperty(PolicyProperties.RULES_ARTIFACTID, DroolsController.NO_ARTIFACT_ID);
				properties.setProperty(PolicyProperties.RULES_VERSION, DroolsController.NO_VERSION);
				
				policyController = PolicyEngine.manager.createPolicyController(controllerName, properties);
				
				/* fall through to do brain update operation*/
			}
			
			switch (operation) {
			case ControllerConfiguration.CONFIG_CONTROLLER_OPERATION_CREATE:
				PolicyController.factory.patch(policyController, configController.getDrools());
				break;
			case ControllerConfiguration.CONFIG_CONTROLLER_OPERATION_UPDATE:
				policyController.unlock();
				PolicyController.factory.patch(policyController, configController.getDrools());
				break;
			case ControllerConfiguration.CONFIG_CONTROLLER_OPERATION_LOCK:
				policyController.lock();
				break;
			case ControllerConfiguration.CONFIG_CONTROLLER_OPERATION_UNLOCK:
				policyController.unlock();
				break;
			default:
				String msg = "Controller Operation Configuration is not supported: " + 
		                     operation + " for " + controllerName;
				logger.warn(msg);
				throw new IllegalArgumentException(msg);
			}
			
			return policyController;
		} catch (Exception e) {
			logger.error("{}: cannot update-policy-controller because of {}", this, e.getMessage(), e);
			throw e;
		} catch (LinkageError e) {
			logger.error("{}: cannot update-policy-controllers (rules) because of {}", this, e.getMessage(), e);
			throw new IllegalStateException(e);
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized boolean start() throws IllegalStateException {
		
		/* policy-engine dispatch pre start hook */
		for (PolicyEngineFeatureAPI feature : PolicyEngineFeatureAPI.providers.getList()) {
			try {
				if (feature.beforeStart(this))
					return true;
			} catch (Exception e) {
				logger.error("{}: feature {} before-start failure because of {}", 
					         this, feature.getClass().getName(), e.getMessage(), e);
			}
		}
			
		boolean success = true;
		if (this.locked)
			throw new IllegalStateException("Engine is locked");
		
		this.alive = true;

		/* Start Policy Engine exclusively-owned (unmanaged) http servers */
		
		for (HttpServletServer httpServer: this.httpServers) {
			try {
				if (!httpServer.start())
					success = false;
			} catch (Exception e) {
				logger.error("{}: cannot start http-server {} because of {}", this, 
						     httpServer, e.getMessage(), e);
			}
		}
		
		/* Start Policy Engine exclusively-owned (unmanaged) sources */
		
		for (TopicSource source: this.sources) {
			try {
				if (!source.start())
					success = false;
			} catch (Exception e) {
				logger.error("{}: cannot start topic-source {} because of {}", this, 
					         source, e.getMessage(), e);
			}
		}
		
		/* Start Policy Engine owned (unmanaged) sinks */
		
		for (TopicSink sink: this.sinks) {
			try {
				if (!sink.start())
					success = false;
			} catch (Exception e) {
				logger.error("{}: cannot start topic-sink {} because of {}", this, 
				             sink, e.getMessage(), e);
			}
		}
		
		/* Start Policy Controllers */
		
		List<PolicyController> controllers = PolicyController.factory.inventory();
		for (PolicyController controller : controllers) {
			try {
				if (!controller.start())
					success = false;
			} catch (Exception e) {
				logger.error("{}: cannot start policy-controller {} because of {}", this, 
			                 controller, e.getMessage(), e);
				success = false;
			}
		}
		
		/* Start managed Topic Endpoints */
		
		try {
			if (!TopicEndpoint.manager.start())
				success = false;			
		} catch (IllegalStateException e) {
			logger.warn("{}: Topic Endpoint Manager is in an invalid state because of {}", this, e.getMessage(), e);			
		}
		
		
		// Start the JMX listener
		
		PdpJmxListener.start();
		
		/* policy-engine dispatch after start hook */
		for (PolicyEngineFeatureAPI feature : PolicyEngineFeatureAPI.providers.getList()) {
			try {
				if (feature.afterStart(this))
					return success;
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
	public synchronized boolean stop() {
		
		/* policy-engine dispatch pre stop hook */
		for (PolicyEngineFeatureAPI feature : PolicyEngineFeatureAPI.providers.getList()) {
			try {
				if (feature.beforeStop(this))
					return true;
			} catch (Exception e) {
				logger.error("{}: feature {} before-stop failure because of {}", 
					     this, feature.getClass().getName(), e.getMessage(), e);
			}
		}
		
		/* stop regardless of the lock state */
		
		boolean success = true;
		if (!this.alive)
			return true;
		
		this.alive = false;			
	
		List<PolicyController> controllers = PolicyController.factory.inventory();
		for (PolicyController controller : controllers) {
			try {
				if (!controller.stop())
					success = false;
			} catch (Exception e) {
				logger.error("{}: cannot stop policy-controller {} because of {}", this, 
		                     controller, e.getMessage(), e);
				success = false;
			}
		}
		
		/* Stop Policy Engine owned (unmanaged) sources */
		for (TopicSource source: this.sources) {
			try {
				if (!source.stop())
					success = false;
			} catch (Exception e) {
				logger.error("{}: cannot start topic-source {} because of {}", this, 
		                     source, e.getMessage(), e);
			}
		}
		
		/* Stop Policy Engine owned (unmanaged) sinks */
		for (TopicSink sink: this.sinks) {
			try {
				if (!sink.stop())
					success = false;
			} catch (Exception e) {
				logger.error("{}: cannot start topic-sink {} because of {}", this, 
	                         sink, e.getMessage(), e);
			}
		}
		
		/* stop all managed topics sources and sinks */
		if (!TopicEndpoint.manager.stop())
			success = false;
		
		/* stop all unmanaged http servers */
		for (HttpServletServer httpServer: this.httpServers) {
			try {
				if (!httpServer.stop())
					success = false;
			} catch (Exception e) {
				logger.error("{}: cannot start http-server {} because of {}", this, 
						     httpServer, e.getMessage(), e);
			}
		}		
		
		/* policy-engine dispatch pre stop hook */
		for (PolicyEngineFeatureAPI feature : PolicyEngineFeatureAPI.providers.getList()) {
			try {
				if (feature.afterStop(this))
					return success;
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
	public synchronized void shutdown() throws IllegalStateException {
		
		/* policy-engine dispatch pre shutdown hook */
		for (PolicyEngineFeatureAPI feature : PolicyEngineFeatureAPI.providers.getList()) {
			try {
				if (feature.beforeShutdown(this))
					return;
			} catch (Exception e) {
				logger.error("{}: feature {} before-shutdown failure because of {}", 
					         this, feature.getClass().getName(), e.getMessage(), e);
			}
		}

		this.alive = false;			
	
		/* Shutdown Policy Engine owned (unmanaged) sources */
		for (TopicSource source: this.sources) {
			try {
				source.shutdown();
			} catch (Exception e) {
				logger.error("{}: cannot shutdown topic-source {} because of {}", this, 
	                         source, e.getMessage(), e);
			}
		}
		
		/* Shutdown Policy Engine owned (unmanaged) sinks */
		for (TopicSink sink: this.sinks) {
			try {
				sink.shutdown();
			} catch (Exception e) {
				logger.error("{}: cannot shutdown topic-sink {} because of {}", this, 
                             sink, e.getMessage(), e);
			}
		}
		
		/* Shutdown managed resources */
		PolicyController.factory.shutdown();
		TopicEndpoint.manager.shutdown();
		HttpServletServer.factory.destroy();
		
		// Stop the JMX listener
		
		PdpJmxListener.stop();
		
		/* policy-engine dispatch post shutdown hook */
		for (PolicyEngineFeatureAPI feature : PolicyEngineFeatureAPI.providers.getList()) {
			try {
				if (feature.afterShutdown(this))
					return;
			} catch (Exception e) {
				logger.error("{}: feature {} after-shutdown failure because of {}",  
				         this, feature.getClass().getName(), e.getMessage(), e);
			}
		}

		
		new Thread(new Runnable() {
		    @Override
		    public void run() {
		    	try {
					Thread.sleep(5000L);
				} catch (InterruptedException e) {
					logger.warn("{}: interrupted-exception while shutting down management server: ", this);
				}		    	
				
				/* shutdown all unmanaged http servers */
				for (HttpServletServer httpServer: getHttpServers()) {
					try {
						httpServer.shutdown();
					} catch (Exception e) {
						logger.error("{}: cannot shutdown http-server {} because of {}", this, 
								     httpServer, e.getMessage(), e);
					}
				} 
		    	
		    	try {
					Thread.sleep(5000L);
				} catch (InterruptedException e) {
					logger.warn("{}: interrupted-exception while shutting down management server: ", this);
				}
		    	
		    	System.exit(0);
		    }		    
		}).start();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized boolean isAlive() {
		return this.alive;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized boolean lock() {
		
		/* policy-engine dispatch pre lock hook */
		for (PolicyEngineFeatureAPI feature : PolicyEngineFeatureAPI.providers.getList()) {
			try {
				if (feature.beforeLock(this))
					return true;
			} catch (Exception e) {
				logger.error("{}: feature {} before-lock failure because of {}",  
				         this, feature.getClass().getName(), e.getMessage(), e);
			}
		}
		
		if (this.locked)
			return true;
		
		this.locked = true;			
	
		boolean success = true;
		List<PolicyController> controllers = PolicyController.factory.inventory();
		for (PolicyController controller : controllers) {
			try {
				success = controller.lock() && success;
			} catch (Exception e) {
				logger.error("{}: cannot lock policy-controller {} because of {}", this, 
						     controller, e.getMessage(), e);
				success = false;
			}
		}
		
		success = TopicEndpoint.manager.lock() && success;	
			
		/* policy-engine dispatch post lock hook */
		for (PolicyEngineFeatureAPI feature : PolicyEngineFeatureAPI.providers.getList()) {
			try {
				if (feature.afterLock(this))
					return success;
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
	public synchronized boolean unlock() {
		
		/* policy-engine dispatch pre unlock hook */
		for (PolicyEngineFeatureAPI feature : PolicyEngineFeatureAPI.providers.getList()) {
			try {
				if (feature.beforeUnlock(this))
					return true;
			} catch (Exception e) {
				logger.error("{}: feature {} before-unlock failure because of {}",  
				         this, feature.getClass().getName(), e.getMessage(), e);
			}
		}
		
		if (!this.locked)
			return true;
		
		this.locked = false;			
		
		boolean success = true;
		List<PolicyController> controllers = PolicyController.factory.inventory();
		for (PolicyController controller : controllers) {
			try {
				success = controller.unlock() && success;
			} catch (Exception e) {
				logger.error("{}: cannot unlock policy-controller {} because of {}", this, 
					         controller, e.getMessage(), e);
				success = false;
			}
		}
		
		success = TopicEndpoint.manager.unlock() && success;
		
		/* policy-engine dispatch after unlock hook */
		for (PolicyEngineFeatureAPI feature : PolicyEngineFeatureAPI.providers.getList()) {
			try {
				if (feature.afterUnlock(this))
					return success;
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
	public synchronized boolean isLocked() {
		return this.locked;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void removePolicyController(String name) {
		PolicyController.factory.destroy(name);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void removePolicyController(PolicyController controller) {
		PolicyController.factory.destroy(controller);
	}

	/**
	 * {@inheritDoc}
	 */
	@JsonIgnore
	@Override
	public List<PolicyController> getPolicyControllers() {
		return PolicyController.factory.inventory();
	}
	
	/**
	 * {@inheritDoc}
	 */
	@JsonProperty("controllers")
	@Override
	public List<String> getPolicyControllerIds() {
		List<String> controllerNames = new ArrayList<String>();
		for (PolicyController controller: PolicyController.factory.inventory()) {
			controllerNames.add(controller.getName());
		}
		return controllerNames;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	@JsonIgnore
	public Properties getProperties() {
		return this.properties;
	}
	

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	@Override
	public List<TopicSource> getSources() {
		return (List<TopicSource>) this.sources;
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	@Override
	public List<TopicSink> getSinks() {
		return (List<TopicSink>) this.sinks;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<HttpServletServer> getHttpServers() {
		return this.httpServers;
	}
	

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<String> getFeatures() {
		List<String> features = new ArrayList<String>();
		for (PolicyEngineFeatureAPI feature : PolicyEngineFeatureAPI.providers.getList()) {
			features.add(feature.getName());
		}
		return features;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@JsonIgnore
	@Override
	public List<PolicyEngineFeatureAPI> getFeatureProviders() {
		return PolicyEngineFeatureAPI.providers.getList();
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public PolicyEngineFeatureAPI getFeatureProvider(String featureName) throws IllegalArgumentException {
		if (featureName == null || featureName.isEmpty())
			throw new IllegalArgumentException("A feature name must be provided");
		
		for (PolicyEngineFeatureAPI feature : PolicyEngineFeatureAPI.providers.getList()) {
			if (feature.getName().equals(featureName))
				return feature;
		}
		
		throw new IllegalArgumentException("Invalid Feature Name: " + featureName);
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onTopicEvent(CommInfrastructure commType, String topic, String event) {
		/* configuration request */
		try {
			PdpdConfiguration configuration = this.decoder.fromJson(event, PdpdConfiguration.class);
			this.configure(configuration);
		} catch (Exception e) {
			logger.error("{}: configuration-error due to {} because of {}", 
					     this, event, e.getMessage(), e);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean deliver(String topic, Object event) 
			throws IllegalArgumentException, IllegalStateException {
		
		/*
		 * Note this entry point is usually from the DRL
		 */
		
		if (topic == null || topic.isEmpty())
			throw new IllegalArgumentException("Invalid Topic");
		
		if (event == null)
			throw new IllegalArgumentException("Invalid Event");
			
		if (!this.isAlive())
			throw new IllegalStateException("Policy Engine is stopped");
		
		if (this.isLocked())
			throw new IllegalStateException("Policy Engine is locked");
		
		List<? extends TopicSink> sinks = 
				TopicEndpoint.manager.getTopicSinks(topic);
		if (sinks == null || sinks.isEmpty() || sinks.size() > 1)
			throw new IllegalStateException
				("Cannot ensure correct delivery on topic " + topic + ": " + sinks);		

		return this.deliver(sinks.get(0).getTopicCommInfrastructure(), 
				            topic, event);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean deliver(String busType, String topic, Object event) 
			throws IllegalArgumentException, IllegalStateException,
		       UnsupportedOperationException {
		
		/*
		 * Note this entry point is usually from the DRL (one of the reasons
		 * busType is String.
		 */
		
		if (busType == null || busType.isEmpty())
			throw new IllegalArgumentException
				("Invalid Communication Infrastructure");
		
		if (topic == null || topic.isEmpty())
			throw new IllegalArgumentException("Invalid Topic");
		
		if (event == null)
			throw new IllegalArgumentException("Invalid Event");
		
		boolean valid = false;
		for (Topic.CommInfrastructure comm: Topic.CommInfrastructure.values()) {
			if (comm.name().equals(busType)) {
				valid = true;
			}
		}
		
		if (!valid)
			throw new IllegalArgumentException
				("Invalid Communication Infrastructure: " + busType);
		
		
		if (!this.isAlive())
			throw new IllegalStateException("Policy Engine is stopped");
		
		if (this.isLocked())
			throw new IllegalStateException("Policy Engine is locked");
		

		return this.deliver(Topic.CommInfrastructure.valueOf(busType), 
				            topic, event);
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean deliver(Topic.CommInfrastructure busType, 
			               String topic, Object event) 
		throws IllegalArgumentException, IllegalStateException,
		       UnsupportedOperationException {
		
		if (topic == null || topic.isEmpty())
			throw new IllegalArgumentException("Invalid Topic");
		
		if (event == null)
			throw new IllegalArgumentException("Invalid Event");
		
		if (!this.isAlive())
			throw new IllegalStateException("Policy Engine is stopped");
		
		if (this.isLocked())
			throw new IllegalStateException("Policy Engine is locked");
		
		/* Try to send through the controller, this is the
		 * preferred way, since it may want to apply additional
		 * processing
		 */
		try {
			DroolsController droolsController = 
					EventProtocolCoder.manager.getDroolsController(topic, event);
			PolicyController controller = PolicyController.factory.get(droolsController);
			if (controller != null)
				return controller.deliver(busType, topic, event);
		} catch (Exception e) {
			logger.warn("{}: cannot find policy-controller to deliver {} over {}:{} because of {}", 
					    this, event, busType, topic, e.getMessage(), e);
			
			/* continue (try without routing through the controller) */
		}
		
		/*
		 * cannot route through the controller, send directly through
		 * the topic sink
		 */
		try {			
			String json = EventProtocolCoder.manager.encode(topic, event);
			return this.deliver(busType, topic, json);

		} catch (Exception e) {
			logger.warn("{}: cannot deliver {} over {}:{} because of {}", 
				        this, event, busType, topic, e.getMessage(), e);
			throw e;
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean deliver(Topic.CommInfrastructure busType, 
			               String topic, String event) 
		throws IllegalArgumentException, IllegalStateException,
		       UnsupportedOperationException {
		
		if (topic == null || topic.isEmpty())
			throw new IllegalArgumentException("Invalid Topic");
		
		if (event == null || event.isEmpty())
			throw new IllegalArgumentException("Invalid Event");
		
		if (!this.isAlive())
			throw new IllegalStateException("Policy Engine is stopped");
		
		if (this.isLocked())
			throw new IllegalStateException("Policy Engine is locked");
		
		try {
			TopicSink sink = 
					TopicEndpoint.manager.getTopicSink
						(busType, topic);
			
			if (sink == null)
				throw new IllegalStateException("Inconsistent State: " + this);
			
			return sink.send(event);

		} catch (Exception e) {
			logger.warn("{}: cannot deliver {} over {}:{} because of {}", 
			            this, event, busType, topic, e.getMessage(), e);
			throw e;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void activate() {
		
		/* policy-engine dispatch pre activate hook */
		for (PolicyEngineFeatureAPI feature : PolicyEngineFeatureAPI.providers.getList()) {
			try {
				if (feature.beforeActivate(this))
					return;
			} catch (Exception e) {
				logger.error("{}: feature {} before-activate failure because of {}",  
				             this, feature.getClass().getName(), e.getMessage(), e);
			}
		}

		// activate 'policy-management'
		for (PolicyController policyController : getPolicyControllers()) {
			try {
				policyController.unlock();
				policyController.start();
			} catch (Exception e) {
				logger.error("{}: cannot activate of policy-controller {} because of {}", 
				             this, policyController,e.getMessage(), e);
			} catch (LinkageError e) {
				logger.error("{}: cannot activate (rules compilation) of policy-controller {} because of {}", 
			                 this, policyController,e.getMessage(), e);
			}
		}
		
		this.unlock();
		
		/* policy-engine dispatch post activate hook */
		for (PolicyEngineFeatureAPI feature : PolicyEngineFeatureAPI.providers.getList()) {
			try {
				if (feature.afterActivate(this))
					return;
			} catch (Exception e) {
				logger.error("{}: feature {} after-activate failure because of {}",  
				             this, feature.getClass().getName(), e.getMessage(), e);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void deactivate() {
		
		/* policy-engine dispatch pre deactivate hook */
		for (PolicyEngineFeatureAPI feature : PolicyEngineFeatureAPI.providers.getList()) {
			try {
				if (feature.beforeDeactivate(this))
					return;
			} catch (Exception e) {
				logger.error("{}: feature {} before-deactivate failure because of {}",  
				              this, feature.getClass().getName(), e.getMessage(), e);
			}
		}
		
		this.lock();
		
		for (PolicyController policyController : getPolicyControllers()) {
			try { 
				policyController.stop();
			} catch (Exception e) {
				logger.error("{}: cannot deactivate (stop) policy-controller {} because of {}", 
			                 this, policyController, e.getMessage(), e);
			} catch (LinkageError e) {
				logger.error("{}: cannot deactivate (stop) policy-controller {} because of {}", 
		                     this, policyController, e.getMessage(), e);
			}
		}
		
		/* policy-engine dispatch post deactivate hook */
		for (PolicyEngineFeatureAPI feature : PolicyEngineFeatureAPI.providers.getList()) {
			try {
				if (feature.afterDeactivate(this))
					return;
			} catch (Exception e) {
				logger.error("{}: feature {} after-deactivate failure because of {}",  
				             this, feature.getClass().getName(), e.getMessage(), e);
			}
		}
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("PolicyEngineManager [alive=").append(alive).append(", locked=").append(locked).append("]");
		return builder.toString();
	}
	
}


