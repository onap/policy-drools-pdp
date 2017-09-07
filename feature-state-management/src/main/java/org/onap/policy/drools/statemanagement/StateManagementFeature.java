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

import org.onap.policy.drools.statemanagement.StateManagementFeatureAPI;
import org.onap.policy.common.im.StandbyStatusException;
import org.onap.policy.common.im.StateManagement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.onap.policy.drools.core.PolicySessionFeatureAPI;
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

public class StateManagementFeature implements StateManagementFeatureAPI, 
				PolicySessionFeatureAPI, PolicyEngineFeatureAPI
{
	// get an instance of logger
	private static final Logger logger =
			LoggerFactory.getLogger(StateManagementFeature.class);
	
	private DroolsPDPIntegrityMonitor droolsPdpIntegrityMonitor = null;
	private StateManagement stateManagement = null;

	/**************************/
	/* 'FeatureAPI' interface */
	/**************************/

	public StateManagementFeature(){
		if(logger.isDebugEnabled()){
			logger.debug("StateManagementFeature() constructor");
		}
	}
	
	@Override
	public void globalInit(String args[], String configDir)
	{
		// Initialization code associated with 'PolicyContainer'
		if(logger.isDebugEnabled()){
			logger.debug("StateManagementFeature.globalInit({}) entry", configDir);
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
			logger.error("DroolsPDPIntegrityMonitor.init()", e);
		}

		initializeProperties(configDir);

		//At this point the DroolsPDPIntegrityMonitor instance must exist. Let's check it.
		try {
			droolsPdpIntegrityMonitor = DroolsPDPIntegrityMonitor.getInstance();
			stateManagement = droolsPdpIntegrityMonitor.getStateManager();
			if(logger.isDebugEnabled()){
				logger.debug("StateManagementFeature.globalInit(): "
					+ "stateManagement.getAdminState(): {}", stateManagement.getAdminState());
			}
			if(stateManagement == null){
				if(logger.isDebugEnabled()){
					logger.debug("StateManagementFeature.globalInit(): stateManagement is NULL!");
				}
			}
		} catch (Exception e1) {
			String msg = "  \n";
			if(logger.isDebugEnabled()){
				logger.debug("StateManagementFeature.globalInit(): DroolsPDPIntegrityMonitor"
					+ " initialization failed with exception:", e1);
			}
			logger.error("DroolsPDPIntegrityMonitor.init(): StateManagementFeature startup failed "
					+ "to get DroolsPDPIntegrityMonitor instance:", e1);
			e1.printStackTrace();
		}
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
	 */
	private static void initializeProperties(String configDir)
	{
		//Get the state management properties 
		try {
			Properties pIm =
					PropertyUtil.getProperties(configDir + "/feature-state-management.properties");
			StateManagementProperties.initProperties(pIm);
			logger.info("initializeProperties: resourceName= {}", StateManagementProperties.getProperty(StateManagementProperties.NODE_NAME));
		} catch (IOException e1) {
			logger.error("initializeProperties", e1);
		}
	}
}
