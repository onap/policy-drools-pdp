/*-
 * ============LICENSE_START=======================================================
 * policy-persistence
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

package org.openecomp.policy.drools.statemanagement;

import java.io.IOException;
import java.util.Observer;
import java.util.Properties;

import org.openecomp.policy.common.im.StandbyStatusException;
import org.openecomp.policy.common.im.StateManagement;
import org.openecomp.policy.common.logging.eelf.MessageCodes;
import org.openecomp.policy.common.logging.flexlogger.FlexLogger;
import org.openecomp.policy.common.logging.flexlogger.Logger;
import org.openecomp.policy.common.logging.flexlogger.PropertyUtil;
import org.openecomp.policy.drools.core.PolicySessionFeatureAPI;
import org.openecomp.policy.drools.features.PolicyEngineFeatureAPI;
import org.openecomp.policy.drools.statemanagement.StateManagementFeatureAPI;
import org.openecomp.policy.drools.system.PolicyEngine;

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
	private static Logger logger =
			FlexLogger.getLogger(StateManagementFeature.class);
	
	private DroolsPDPIntegrityMonitor droolsPdpIntegrityMonitor = null;
	private StateManagement stateManagement = null;

	/**************************/
	/* 'FeatureAPI' interface */
	/**************************/

	public StateManagementFeature(){
		logger.debug("StateManagementFeature() constructor");
	}
	
	@Override
	public void globalInit(String args[], String configDir)
	{
		// Initialization code associated with 'PolicyContainer'
		logger.debug("StateManagementFeature.globalInit(" + configDir + ") entry");

		try
		{
			droolsPdpIntegrityMonitor = DroolsPDPIntegrityMonitor.init(configDir);
		}
		catch (Exception e)
		{
			logger.debug("DroolsPDPIntegrityMonitor initialization exception: " + e);
			logger.error(MessageCodes.EXCEPTION_ERROR, e,
					"main", "DroolsPDPIntegrityMonitor.init()");
		}

		initializePersistence(configDir);

		//At this point the DroolsPDPIntegrityMonitor instance must exist. Let's check it.
		try {
			droolsPdpIntegrityMonitor = DroolsPDPIntegrityMonitor.getInstance();
			stateManagement = droolsPdpIntegrityMonitor.getStateManager();
			logger.debug("StateManagementFeature.globalInit(): "
					+ "stateManagement.getAdminState(): " + stateManagement.getAdminState());
			if(stateManagement == null){
				logger.debug("StateManagementFeature.globalInit(): stateManagement is NULL!");
			}
		} catch (Exception e1) {
			String msg = "StateManagementFeature startup failed to get DroolsPDPIntegrityMonitor instance:  \n" + e1;
			System.out.println(msg);
			e1.printStackTrace();
			logger.debug("StateManagementFeature.globalInit(): DroolsPDPIntegrityMonitor"
					+ " initialization failed with exception:" + msg);
			logger.error(MessageCodes.EXCEPTION_ERROR, e1,
					"main", "DroolsPDPIntegrityMonitor.init():" + msg);
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void addObserver(Observer stateChangeObserver) {
		logger.debug("StateManagementFeature.addObserver() entry");
		logger.debug("StateManagementFeature.addObserver(): stateManagement.getAdminState(): " + stateManagement.getAdminState());
		stateManagement.addObserver(stateChangeObserver);
		logger.debug("StateManagementFeature.addObserver() exit");
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
	 * @throws Exception 
	 */
	@Override
	public void lock() throws Exception{
		stateManagement.lock();
	}
	
	/**
	 * {@inheritDoc}
	 * @throws Exception 
	 */
	@Override
	public void unlock() throws Exception{
		stateManagement.unlock();
	}
	
	@Override
	public int getSequenceNumber() {
		return SEQ_NUM;
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean afterStart(PolicyEngine engine) 
	{
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean beforeStart(PolicyEngine engine) {return false;}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean beforeShutdown(PolicyEngine engine) {return false;}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean afterShutdown(PolicyEngine engine) {return false;}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean beforeConfigure(PolicyEngine engine, Properties properties) {return false;}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean afterConfigure(PolicyEngine engine) {return false;}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean beforeActivate(PolicyEngine engine) {return false;}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean afterActivate(PolicyEngine engine) {return false;}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean beforeDeactivate(PolicyEngine engine) {return false;}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean afterDeactivate(PolicyEngine engine) {return false;}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean beforeStop(PolicyEngine engine) {return false;}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean afterStop(PolicyEngine engine) {return false;}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean beforeLock(PolicyEngine engine) {return false;}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean afterLock(PolicyEngine engine) {return false;}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean beforeUnlock(PolicyEngine engine) {return false;}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean afterUnlock(PolicyEngine engine) {return false;}

	/**************************/


	/**
	 * Read in the persistence properties, determine whether persistence is
	 * enabled or disabled, and initialize persistence if enabled.
	 */
	private static void initializePersistence(String configDir)
	{
		//Get the Drools persistence properties
		Properties pDrools = null;
		try {
			pDrools = PropertyUtil.getProperties(configDir
					+ "/droolsPersistence.properties");
		} catch (IOException e) {
			logger.error("StateManagement failed to initialize.  Unable to get droolsPersistence.properties");
			e.printStackTrace();
		}
		DroolsPersistenceProperties.initProperties(pDrools);

		//Get the Integrity Monitor properties 
		try {
			Properties pIm =
					PropertyUtil.getProperties(configDir + "/IntegrityMonitor.properties");
			StateManagementProperties.initProperties(pIm);
			logger.info("initializePersistence: resourceName=" 
			+ StateManagementProperties.getProperty(StateManagementProperties.NODE_NAME));
		} catch (IOException e1) {
			logger.error(MessageCodes.MISS_PROPERTY_ERROR, e1, "initializePersistence");
		}
	}



}
