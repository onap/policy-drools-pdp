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

package org.onap.policy.drools.system;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.features.PolicyControllerFeatureAPI;
import org.onap.policy.drools.protocol.configuration.DroolsConfiguration;
import org.onap.policy.drools.system.internal.AggregatedPolicyController;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Policy Controller Factory to manage controller creation, destruction,
 * and retrieval for management interfaces
 */
public interface PolicyControllerFactory {
	/**
	 * Build a controller from a properties file
	 * 
	 * @param name the global name of this controller
	 * @param properties input parameters in form of properties for controller
	 * initialization.
	 * 
	 * @return a Policy Controller
	 * 
	 * @throws IllegalArgumentException invalid values provided in properties
	 */
	public PolicyController build(String name, Properties properties)
			throws IllegalArgumentException;
	
	/**
	 * patches (updates) a controller from a critical configuration update.
	 * 
	 * @param name
	 * @param configController
	 * 
	 * @return a Policy Controller
	 */
	public PolicyController patch(String name, DroolsConfiguration configController);
	
	/**
	 * rebuilds (updates) a controller from a configuration update.
	 * 
	 * @param controller
	 * @param configController
	 * 
	 * @return a Policy Controller
	 */
	public PolicyController patch(PolicyController controller, 
			                      DroolsConfiguration configController);
	
	/**
	 * get PolicyController from DroolsController
	 * 
	 * @param droolsController
	 * @return
	 * @throws IllegalArgumentException
	 * @throws IllegalStateException
	 */
	public PolicyController get(DroolsController droolsController) 
			throws IllegalArgumentException, IllegalStateException;
	
	/**
	 * Makes the Policy Controller identified by controllerName not operational, but
	 * does not delete its associated data
	 * 
	 * @param controllerName  name of the policy controller
	 * @throws IllegalArgumentException invalid arguments
	 */
	public void shutdown(String controllerName) throws IllegalArgumentException;;
	
	/**
	 * Makes the Policy Controller identified by controller not operational, but
	 * does not delete its associated data
	 * 
	 * @param controller a Policy Controller
	 * @throws IllegalArgumentException invalid arguments
	 */
	public void shutdown(PolicyController controller) throws IllegalArgumentException;
	
	/**
	 * Releases all Policy Controllers from operation
	 */
	public void shutdown();

	/**
	 * Destroys this Policy Controller
	 * 
	 * @param controllerName  name of the policy controller
	 * @throws IllegalArgumentException invalid arguments
	 */
	public void destroy(String controllerName) throws IllegalArgumentException;;
	
	/**
	 * Destroys this Policy Controller
	 * 
	 * @param controller a Policy Controller
	 * @throws IllegalArgumentException invalid arguments
	 */
	public void destroy(PolicyController controller) throws IllegalArgumentException;
	
	/**
	 * Releases all Policy Controller resources
	 */
	public void destroy();
	
	/**
	 * gets the Policy Controller identified by its name
	 * 
	 * @param policyControllerName
	 * @return
	 * @throws IllegalArgumentException
	 * @throws IllegalStateException
	 */
	public PolicyController get(String policyControllerName)
		   throws IllegalArgumentException, IllegalStateException;
	
	/**
	 * gets the Policy Controller identified by group and artifact ids
	 * 
	 * @param groupId group id
	 * @param artifactId artifact id
	 * @return
	 * @throws IllegalArgumentException
	 * @throws IllegalStateException
	 */
	public PolicyController get(String groupId, String artifactId)
		   throws IllegalArgumentException, IllegalStateException;
	
	/**
	 * get features attached to the Policy Controllers
	 * @return list of features
	 */
	public List<PolicyControllerFeatureAPI> getFeatureProviders();
	
	/**
	 * get named feature attached to the Policy Controllers
	 * @return the feature
	 */
	public PolicyControllerFeatureAPI getFeatureProvider(String featureName) 
			throws IllegalArgumentException;
	
	/**
	 * get features attached to the Policy Controllers
	 * @return list of features
	 */
	public List<String> getFeatures();
	
	/**
	 * returns the current inventory of Policy Controllers
	 * 
	 * @return a list of Policy Controllers
	 */
	public List<PolicyController> inventory();
}

/**
 * Factory of Policy Controllers indexed by the name of the Policy Controller
 */
class IndexedPolicyControllerFactory implements PolicyControllerFactory {
	// get an instance of logger 
	private static Logger  logger = LoggerFactory.getLogger(PolicyControllerFactory.class);		
	
	/**
	 * Policy Controller Name Index
	 */
	protected HashMap<String,PolicyController> policyControllers =
			new HashMap<String,PolicyController>();
	
	/**
	 * Group/Artifact Ids Index
	 */
	protected HashMap<String,PolicyController> coordinates2Controller = 
			new HashMap<String,PolicyController>();
	
	/**
	 * produces key for indexing controller names
	 * 
	 * @param group group id
	 * @param artifactId artifact id
	 * @return index key
	 */
	protected String toKey(String groupId, String artifactId) {
		return groupId + ":" + artifactId;
	}
		
	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized PolicyController build(String name, Properties properties) 
			throws IllegalArgumentException {
		
		if (this.policyControllers.containsKey(name)) {
			return this.policyControllers.get(name);
		}
		
		/* A PolicyController does not exist */
		
		PolicyController controller =
				new AggregatedPolicyController(name, properties);
		
		String coordinates = toKey(controller.getDrools().getGroupId(),
				                   controller.getDrools().getArtifactId());
		
		this.policyControllers.put(name, controller);	
		

		if (controller.getDrools().isBrained())
			this.coordinates2Controller.put(coordinates, controller);
		
		return controller;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized PolicyController patch(String name, DroolsConfiguration droolsConfig) 
			throws IllegalArgumentException {
		
		if (name == null || name.isEmpty() || !this.policyControllers.containsKey(name)) {
			throw new IllegalArgumentException("Invalid " + name);
		}
		
		if (droolsConfig == null)
			throw new IllegalArgumentException("Invalid Drools Configuration");
		
		PolicyController controller = this.get(name);
		
		if (controller == null) {
			logger.warn("A POLICY CONTROLLER of name " + name + 
					    "does not exist for patch operation: " + droolsConfig);
			
			throw new IllegalArgumentException("Not a valid controller of name " + name);
		}
		
		this.patch(controller, droolsConfig);
		
		if (logger.isInfoEnabled())
			logger.info("UPDATED drools configuration: " + droolsConfig + " on " + this);
		
		return controller;
	}
	

	/**
	 * {@inheritDoc}
	 */
	@Override
	public PolicyController patch(PolicyController controller, DroolsConfiguration droolsConfig) 
			throws IllegalArgumentException {

		if (controller == null)
			throw new IllegalArgumentException("Not a valid controller:  null");
		
		if (!controller.updateDrools(droolsConfig)) {
			logger.warn("Cannot update drools configuration: " + droolsConfig + " on " + this);
			throw new IllegalArgumentException("Cannot update drools configuration Drools Configuration");
		}
		
		if (logger.isInfoEnabled())
			logger.info("UPDATED drools configuration: " + droolsConfig + " on " + this);
		
		String coordinates = toKey(controller.getDrools().getGroupId(),
				                   controller.getDrools().getArtifactId());
		
		if (controller.getDrools().isBrained())
			this.coordinates2Controller.put(coordinates, controller);
		
		return controller;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void shutdown(String controllerName) throws IllegalArgumentException {
		
		if (controllerName == null || controllerName.isEmpty()) {
			throw new IllegalArgumentException("Invalid " + controllerName);
		}
		
		synchronized(this) {
			if (!this.policyControllers.containsKey(controllerName)) {
				return;
			}
					
			PolicyController controller = this.policyControllers.get(controllerName);
			this.shutdown(controller);
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void shutdown(PolicyController controller) throws IllegalArgumentException {		
		this.unmanage(controller);
		controller.shutdown();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void shutdown() {
		List<PolicyController> controllers = this.inventory();
		for (PolicyController controller: controllers) {
			controller.shutdown();
		}
		
		synchronized(this) {
			this.policyControllers.clear();
			this.coordinates2Controller.clear();
		}
	}
	
	/**
	 * unmanage the controller
	 * 
	 * @param controller
	 * @return
	 * @throws IllegalArgumentException
	 */
	protected void unmanage(PolicyController controller) throws IllegalArgumentException {
		if (controller == null) {
			throw new IllegalArgumentException("Invalid Controller");
		}
		
		synchronized(this) {
			if (!this.policyControllers.containsKey(controller.getName())) {
				return;
			}
			controller = this.policyControllers.remove(controller.getName());
			
			String coordinates = toKey(controller.getDrools().getGroupId(),
                                       controller.getDrools().getArtifactId());
			this.coordinates2Controller.remove(coordinates);
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void destroy(String controllerName) throws IllegalArgumentException {
		
		if (controllerName == null || controllerName.isEmpty()) {
			throw new IllegalArgumentException("Invalid " + controllerName);
		}
		
		synchronized(this) {
			if (!this.policyControllers.containsKey(controllerName)) {
				return;
			}
					
			PolicyController controller = this.policyControllers.get(controllerName);
			this.destroy(controller);
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void destroy(PolicyController controller) throws IllegalArgumentException {
		this.unmanage(controller);
		controller.halt();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void destroy() {
		List<PolicyController> controllers = this.inventory();
		for (PolicyController controller: controllers) {
			controller.halt();
		}
		
		synchronized(this) {
			this.policyControllers.clear();
			this.coordinates2Controller.clear();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public PolicyController get(String name) throws IllegalArgumentException, IllegalStateException {

		if (name == null || name.isEmpty()) {
			throw new IllegalArgumentException("Invalid " + name);
		}
		
		synchronized(this) {
			if (this.policyControllers.containsKey(name)) {
				return this.policyControllers.get(name);
			} else {
				throw new IllegalArgumentException("Invalid " + name);
			}
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public PolicyController get(String groupId, String artifactId) 
			throws IllegalArgumentException, IllegalStateException {

		if (groupId == null || groupId.isEmpty() || 
			artifactId == null || artifactId.isEmpty()) {
			throw new IllegalArgumentException("Invalid group/artifact ids");
		}
		
		synchronized(this) {
			String key = toKey(groupId,artifactId);
			if (this.coordinates2Controller.containsKey(key)) {
				return this.coordinates2Controller.get(key);
			} else {
				throw new IllegalArgumentException("Invalid " + key);
			}
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public PolicyController get(DroolsController droolsController) 
			throws IllegalArgumentException, IllegalStateException {

		if (droolsController == null) {
			throw new IllegalArgumentException("No Drools Controller provided");
		}
		
		synchronized(this) {
			String key = toKey(droolsController.getGroupId(), droolsController.getArtifactId());
			if (this.coordinates2Controller.containsKey(key)) {
				return this.coordinates2Controller.get(key);
			} else {
				logger.error("Drools Controller not associated with Policy Controller " + droolsController + ":" + this);
				throw new IllegalStateException("Drools Controller not associated with Policy Controller " + droolsController + ":" + this);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<PolicyController> inventory() {
		 List<PolicyController> controllers = 
				 new ArrayList<PolicyController>(this.policyControllers.values());
		 return controllers;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override 
	public List<String> getFeatures() {
		List<String> features = new ArrayList<String>();
		for (PolicyControllerFeatureAPI feature : PolicyControllerFeatureAPI.providers.getList()) {
			features.add(feature.getName());
		}
		return features;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@JsonIgnore
	@Override
	public List<PolicyControllerFeatureAPI> getFeatureProviders() {
		return PolicyControllerFeatureAPI.providers.getList();
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public PolicyControllerFeatureAPI getFeatureProvider(String featureName) throws IllegalArgumentException {
		if (featureName == null || featureName.isEmpty())
			throw new IllegalArgumentException("A feature name must be provided");
		
		for (PolicyControllerFeatureAPI feature : PolicyControllerFeatureAPI.providers.getList()) {
			if (feature.getName().equals(featureName))
				return feature;
		}
		
		throw new IllegalArgumentException("Invalid Feature Name: " + featureName);
	}
}
