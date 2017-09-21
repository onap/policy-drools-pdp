/*-
 * ============LICENSE_START=======================================================
 * feature-state-management
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

package org.onap.policy.drools.statemanagement;

import java.io.IOException;
import java.util.Observer;
import java.util.Properties;
import org.onap.policy.common.im.StandbyStatusException;
import org.onap.policy.common.im.StateManagement;
import org.onap.policy.drools.statemanagement.StateManagementFeatureAPI;
import org.onap.policy.drools.system.PolicyEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.onap.policy.drools.features.PolicyEngineFeatureAPI;
import org.onap.policy.drools.utils.PropertyUtil;

/**
 * If this feature is supported, there is a single instance of it.
 * It adds persistence to Drools sessions, but it is also intertwined with
 * active/standby state management and IntegrityMonitor. For now, they are
 * all treated as a single feature, but it would be nice to separate them.
 *
 * The bulk of the code here was once in other classes, such as
 * 'PolicyContainer' and 'Main'. It was moved here as part of making this
 * a separate optional feature.
 */

public class StateManagementFeature implements StateManagementFeatureAPI,PolicyEngineFeatureAPI 
{
	// get an instance of logger
	private static final Logger logger =
			LoggerFactory.getLogger(StateManagementFeature.class);
	
	private DroolsPDPIntegrityMonitor droolsPdpIntegrityMonitor = null;
	private StateManagement stateManagement = null;
	public static String configDir = null;

	/**************************/
	/* 'FeatureAPI' interface */
	/**************************/

	public StateManagementFeature(){
		if(logger.isDebugEnabled()){
			logger.debug("StateManagementFeature() constructor");
		}
	}
	
	/**
	 * {@inheritDoc}
	 * @return 
	 */
	@Override
	public boolean afterStart(PolicyEngine policyEngine){
		if(logger.isDebugEnabled()){
			logger.debug("StateManagementFeature.afterStart({}) entry", configDir);
		}
		
		if (configDir == null){
			logger.error("StateManagementFeature.afterStart({}) configDir is null");
			return false;
		}

		try
		{
			droolsPdpIntegrityMonitor = DroolsPDPIntegrityMonitor.init(configDir);
		}
		catch (Exception e)
		{
			if(logger.isDebugEnabled()){
				logger.debug("DroolsPDPIntegrityMonitor initialization exception: ", e);
			}
			logger.error("DroolsPDPIntegrityMonitor.init() threw exception:", e);
			return false;
		}

		try{
			initializeProperties(configDir);
		}catch(IOException e1){
			logger.error("initializeProperties(configDir) threw IOException: ", e1);
			return false;
		}

		//At this point the DroolsPDPIntegrityMonitor instance must exist. Let's check it.
		try {
			droolsPdpIntegrityMonitor = DroolsPDPIntegrityMonitor.getInstance();
			stateManagement = droolsPdpIntegrityMonitor.getStateManager();
			if(logger.isDebugEnabled()){
				logger.debug("StateManagementFeature.afterStart(): "
					+ "stateManagement.getAdminState(): {}", stateManagement.getAdminState());
			}
			if(stateManagement == null){
				logger.error("StateManagementFeature.afterStart(): stateManagement is NULL!");
				return false;
			}
		} catch (Exception e1) {
			if(logger.isDebugEnabled()){
				logger.debug("StateManagementFeature.afterStart(): DroolsPDPIntegrityMonitor"
					+ " initialization failed with exception:", e1);
			}
			logger.error("StateManagementFeature.afterStart(): Failed to get DroolsPDPIntegrityMonitor instance"
					+ "or StateManagement instance.  Exception: ", e1);
			return false;
		}
		//If you make it here, all has succeeded
		return true;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void addObserver(Observer stateChangeObserver) {
		if(logger.isDebugEnabled()){
			logger.debug("StateManagementFeature.addObserver() entry\n"
					+ "StateManagementFeature.addObserver(): "
					+ "stateManagement.getAdminState(): {}", stateManagement.getAdminState());
		}
		stateManagement.addObserver(stateChangeObserver);
		if(logger.isDebugEnabled()){
			logger.debug("StateManagementFeature.addObserver() exit");
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getAdminState() {
		return stateManagement.getAdminState();
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getOpState() {
		return stateManagement.getOpState();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getAvailStatus() {
		return stateManagement.getAvailStatus();
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getStandbyStatus() {
		return stateManagement.getStandbyStatus();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getStandbyStatus(String resourceName) {
		return stateManagement.getStandbyStatus(resourceName);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void disableFailed(String resourceName) throws Exception {
		stateManagement.disableFailed(resourceName);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void disableFailed() throws Exception {
		stateManagement.disableFailed();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void promote() throws StandbyStatusException, Exception {
		stateManagement.promote();		
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void demote() throws Exception {
		stateManagement.demote();
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getResourceName() {
		return StateManagementProperties.getProperty(StateManagementProperties.NODE_NAME);
	}
	
	/**
	 * {@inheritDoc}
	 * @return 
	 */
	@Override
	public boolean lock(){
		try{
			stateManagement.lock();
		}catch(Exception e){
			logger.error("StateManagementFeature.lock() failed with exception: {}", e);
			return false;
		}
		return true;
	}
	
	/**
	 * {@inheritDoc}
	 * @throws Exception 
	 */
	@Override
	public boolean unlock(){
		try{
			stateManagement.unlock();
		}catch(Exception e){
			logger.error("StateManagementFeature.unlock() failed with exception: {}", e);
			return false;
		}
		return true;
	}
	
	/**
	 * {@inheritDoc}
	 * @throws Exception 
	 */
	@Override
	public boolean isLocked(){
		String admin = stateManagement.getAdminState();
		if(admin.equals(StateManagement.LOCKED)){
			return true;
		}else{
			return false;
		}
	}
	
	@Override
	public int getSequenceNumber() {
		return SEQ_NUM;
	}

	/**
	 * Read in the properties and initialize the StateManagementProperties.
	 * @throws IOException 
	 */
	private static void initializeProperties(String configDir) throws IOException
	{
		//Get the state management properties 
		try {
			Properties pIm =
					PropertyUtil.getProperties(configDir + "/feature-state-management.properties");
			StateManagementProperties.initProperties(pIm);
			logger.info("initializeProperties: resourceName= {}", StateManagementProperties.getProperty(StateManagementProperties.NODE_NAME));
		} catch (IOException e1) {
			logger.error("initializeProperties caught exception from StateMaangement", e1);
			throw new IOException(e1);
		}
	}
}
