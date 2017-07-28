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

package org.openecomp.policy.drools.activestandby;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import org.openecomp.policy.common.ia.IntegrityAudit;
import org.openecomp.policy.common.ia.IntegrityAuditProperties;
import org.openecomp.policy.common.logging.eelf.MessageCodes;
import org.openecomp.policy.common.logging.flexlogger.FlexLogger;
import org.openecomp.policy.common.logging.flexlogger.Logger;
import org.openecomp.policy.common.logging.flexlogger.PropertyUtil;
import org.openecomp.policy.drools.core.PolicySessionFeatureAPI;
import org.openecomp.policy.drools.features.PolicyEngineFeatureAPI;
import org.openecomp.policy.drools.activestandby.DroolsPersistenceProperties;
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
public class ActiveStandbyFeature implements ActiveStandbyFeatureAPI, 
				PolicySessionFeatureAPI, PolicyEngineFeatureAPI
{
	// get an instance of logger
	private static Logger logger =
			FlexLogger.getLogger(ActiveStandbyFeature.class);
	
	private static DroolsPdp myPdp;
	private static Object myPdpSync = new Object();
	private static DroolsPdpsElectionHandler electionHandler;
	
	private StateManagementFeatureAPI stateManagementFeature;
	/*
	 * Used by JUnit testing to verify whether or not audit is running.
	 */
	private static IntegrityAudit integrityAudit = null;
	

	/**************************/
	/* 'FeatureAPI' interface */
	/**************************/

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getSequenceNumber()
	{
		return(1);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void globalInit(String args[], String configDir)
	{
		// This must come first since it initializes myPdp
		initializePersistence(configDir);
		
		for (StateManagementFeatureAPI feature : StateManagementFeatureAPI.impl.getList())
		{
			if (feature.getResourceName().equals(myPdp.getPdpId()))
			{
				logger.debug("ActiveStandbyFeature.globalInit: Found StateManagementFeature"
						+ " with resourceName: " + myPdp.getPdpId());
				stateManagementFeature = feature;
				break;
			}
		}
		if(stateManagementFeature == null){
			String msg = "ActiveStandbyFeature failed to initialize.  "
					+ "Unable to get instance of StateManagementFeatureAPI "
					+ "with resourceID: " + myPdp.getPdpId();
			logger.debug(msg);
			logger.error(MessageCodes.GENERAL_ERROR, msg);
		}



		//Create an instance of the Observer
		PMStandbyStateChangeNotifier pmNotifier = new PMStandbyStateChangeNotifier();

		//Register the PMStandbyStateChangeNotifier Observer
		stateManagementFeature.addObserver(pmNotifier);
	
		logger.debug("ActiveStandbyFeature.globalInit() exit");
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean afterStart(PolicyEngine engine) 
	{
		// ASSERTION: engine == PolicyEngine.manager
		PolicyEngine.manager.lock();
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
	public boolean beforeActivate(PolicyEngine engine)
	{
		return(false);
	}

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
		try {
				Properties droolsPersistenceProperties = 
						PropertyUtil.getProperties(configDir + "/droolsPersistence.properties");
				DroolsPersistenceProperties.initProperties(droolsPersistenceProperties);
				logger.info("initializePersistence: DroolsPersistenceProperties success");
		} catch (IOException e) {
			logger.error(MessageCodes.MISS_PROPERTY_ERROR, e, "ActiveStandbyFeature: initializePersistence DroolsPersistenceProperties");
		}
		
		//Get the Integrity Monitor properties 
		try {
			Properties pIm =
					PropertyUtil.getProperties(configDir + "/IntegrityMonitor.properties");
			ActiveStandbyProperties.initProperties(pIm);
			logger.info("initializePersistence: resourceName=" 
			+ ActiveStandbyProperties.getProperty(ActiveStandbyProperties.NODE_NAME));
		} catch (IOException e1) {
			logger.error(MessageCodes.MISS_PROPERTY_ERROR, e1, "ActiveStandbyFeature: initializePersistence ActiveStandbyProperties");
		}
		DroolsPdpsConnector conn = getDroolsPdpsConnector("activeStandbyPU");
		String resourceName = ActiveStandbyProperties.getProperty(ActiveStandbyProperties.NODE_NAME);
		if(resourceName == null){
			throw new NullPointerException();
		}

		/*
		 * In a JUnit test environment, one or more PDPs may already have been
		 * inserted in the DB, so we need to check for this.
		 */
		DroolsPdp existingPdp = conn.getPdp(resourceName);
		if (existingPdp != null) {
			System.out.println("Found existing PDP record, pdpId="
					+ existingPdp.getPdpId() + ", isDesignated="
					+ existingPdp.isDesignated() + ", updatedDate="
					+ existingPdp.getUpdatedDate());
			myPdp = existingPdp;
		}

		synchronized(myPdpSync){
			if(myPdp == null){

				myPdp = new DroolsPdpImpl(resourceName,false,4,new Date());	
			}
			if(myPdp != null){
				String site_name = "";
				site_name = ActiveStandbyProperties.getProperty(ActiveStandbyProperties.SITE_NAME);
				if (site_name == null) {
					site_name = "";
				}else{
					site_name = site_name.trim();
				}
				myPdp.setSiteName(site_name);
			}
			if(electionHandler == null){
				electionHandler = new DroolsPdpsElectionHandler(conn,myPdp);
			}
		}
		
		/*
		 * Kick off integrity audit for Drools DB.
		 */
		startIntegrityAudit(configDir);
		
		System.out.println("\n\nThis controller is a standby, waiting to be chosen as primary...\n\n");
		logger.info("\n\nThis controller is a standby, waiting to be chosen as primary...\n\n");
	}
	
	private static void startIntegrityAudit(String configDir) {

		logger.info("startIntegrityAudit: Entering, configDir='" + configDir
				+ "'");

		/*
		 * Initialize Integrity Audit properties. file.
		 */
		try {

			String resourceName = ActiveStandbyProperties.getProperty(ActiveStandbyProperties.NODE_NAME);

			/*
			 * Load properties for auditing of Drools DB.
			 */
			Properties droolsPia = PropertyUtil.getProperties(configDir
					+ "/IntegrityMonitor.properties");

			/*
			 * Supplement properties specific to the IntegrityMonitor (e.g.
			 * site_name, node_type, resource.name) with properties specific to
			 * persisting Drools DB entities (see
			 * ../policy-core/src/main/resources/persistence.xml)
			 * 
			 * Note: integrity_audit_period_seconds is defined in
			 * IntegrityMonitor.properties, rather than creating a whole new
			 * "IntegrityAudit.properties" file for just one property.
			 */
			droolsPia.setProperty(IntegrityAuditProperties.DB_DRIVER,
					DroolsPersistenceProperties
					.getProperty(DroolsPersistenceProperties.DB_DRIVER));
			droolsPia.setProperty(IntegrityAuditProperties.DB_PWD,
					DroolsPersistenceProperties
					.getProperty(DroolsPersistenceProperties.DB_PWD));
			droolsPia.setProperty(IntegrityAuditProperties.DB_URL,
					DroolsPersistenceProperties
					.getProperty(DroolsPersistenceProperties.DB_URL));
			droolsPia.setProperty(IntegrityAuditProperties.DB_USER,
					DroolsPersistenceProperties
					.getProperty(DroolsPersistenceProperties.DB_USER));

			/*
			 * Start audit for Drools DB.
			 */
			integrityAudit = new IntegrityAudit(
					resourceName, "auditDroolsPU", droolsPia);
			integrityAudit.startAuditThread();

		} catch (IOException e1) {
			logger.error(
					MessageCodes.MISS_PROPERTY_ERROR,
					e1,
					"initializePersistence: IntegrityAuditProperties: "
							+ e1.getMessage());
		} catch (Exception e2) {
			logger.error(
					MessageCodes.EXCEPTION_ERROR,
					e2,
					"initializePersistence: IntegrityAuditProperties: "
							+ e2.getMessage());
		}

		logger.debug("startIntegrityAudit: Exiting");

	}

	/*
	 * IntegrityAudit instance is needed by JUnit testing to ascertain whether
	 * or not audit is running.
	 */
	public static IntegrityAudit getIntegrityAudit() {

		return integrityAudit;

	}


	/*
	 * Moved code to instantiate a JpaDroolsPdpsConnector object from main() to
	 * this method, so it can also be accessed from StandbyStateChangeNotifier
	 * class.
	 */
	public static DroolsPdpsConnector getDroolsPdpsConnector(String pu) {

		Map<String, Object> propMap = new HashMap<String, Object>();
		propMap.put("javax.persistence.jdbc.driver", DroolsPersistenceProperties
				.getProperty(DroolsPersistenceProperties.DB_DRIVER));
		propMap.put("javax.persistence.jdbc.url",
				DroolsPersistenceProperties.getProperty(DroolsPersistenceProperties.DB_URL));
		propMap.put("javax.persistence.jdbc.user", DroolsPersistenceProperties
				.getProperty(DroolsPersistenceProperties.DB_USER));
		propMap.put("javax.persistence.jdbc.password",
				DroolsPersistenceProperties.getProperty(DroolsPersistenceProperties.DB_PWD));

		EntityManagerFactory emf = Persistence.createEntityManagerFactory(
				pu, propMap);
		DroolsPdpsConnector conn = new JpaDroolsPdpsConnector(emf);

		return conn;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getPdpdNowActive(){
		return electionHandler.getPdpdNowActive();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getPdpdLastActive(){
		return electionHandler.getPdpdLastActive();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getResourceName() {
		return myPdp.getPdpId();
	}
}
