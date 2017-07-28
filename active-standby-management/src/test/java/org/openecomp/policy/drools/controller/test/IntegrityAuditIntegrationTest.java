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

package org.openecomp.policy.drools.controller.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.openecomp.policy.common.ia.IntegrityAudit;
import org.openecomp.policy.common.logging.eelf.MessageCodes;
import org.openecomp.policy.common.logging.eelf.PolicyLogger;
import org.openecomp.policy.drools.activestandby.ActiveStandbyFeature;
import org.openecomp.policy.drools.activestandby.ActiveStandbyFeatureAPI;
import org.openecomp.policy.drools.core.PolicySessionFeatureAPI;
import org.openecomp.policy.drools.statemanagement.DroolsPersistenceProperties;
import org.openecomp.policy.drools.statemanagement.StateManagementFeatureAPI;
import org.openecomp.policy.drools.statemanagement.StateManagementProperties;

public class IntegrityAuditIntegrationTest {
		
	
	public static final String INTEGRITY_MONITOR_PROPERTIES_FILE="src/test/server/config/IntegrityMonitor.properties";
		
	
	/*
	 * Sleep 5 seconds after each test to allow interrupt (shutdown) recovery.
	 */
	 
	private long interruptRecoveryTime = 5000;
	
	StateManagementFeatureAPI stateManagementFeature;
	
	/*
	 * All you need to do here is create an instance of StateManagementFeature class and then check
	 * to see if IntegrityAudit was initialized.  That will test the initialization of StateManagementFeature
	 * as well as the initialization of DroolsPpdsIntegrityMonitor, IntegrityMonitor, StateManagement and
	 * IntegrityAudit.
	 */
	
	@BeforeClass
	public static void setUpClass() throws Exception {
		
		PolicyLogger.info("setUpClass: Entering");

		String userDir = System.getProperty("user.dir");
		PolicyLogger.debug("setUpClass: userDir=" + userDir);
		System.setProperty("com.sun.management.jmxremote.port", "9980");
		System.setProperty("com.sun.management.jmxremote.authenticate","false");
				
		/* 
		 * Setting isUnitTesting to true ensures
		 * 
		 * 1) That we load test version of properties files
		 * 
		 * and
		 * 
		 * 2) that we use dbAuditSimulate() method, because all we care about
		 * for this JUnit testing is that the audits are executed.
		 */
		 
		IntegrityAudit.isUnitTesting = true;
		
		initializeDb();
		
		PolicyLogger.info("setUpClass: Exiting");
		
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
				
	}

	@Before
	public void setUp() throws Exception {
		
	}

	@After
	public void tearDown() throws Exception {
		
	}

	
	/*
	 * Verifies that audit thread starts successfully.
	 */
	 
	//@Ignore
	@Test
	public void testAuditInit() throws Exception {
		
		PolicyLogger.debug("\n\ntestAuditInit: Entering\n\n");

		PolicyLogger.debug("testAuditInit: Reading IntegrityMonitorProperties");

		String configDir = "src/test/server/config";
		
		Properties integrityMonitorProperties = new Properties();
		integrityMonitorProperties.load(new FileInputStream(new File(
				configDir + "/IntegrityMonitor.properties")));
		String thisPdpId = integrityMonitorProperties
				.getProperty(StateManagementProperties.NODE_NAME);


		// We want to create a StateManagementFeature and initialize it. It will be
		// discovered by the ActiveStandbyFeature when the election handler
		// initializes.

		StateManagementFeatureAPI smf = null;
		for (StateManagementFeatureAPI feature : StateManagementFeatureAPI.impl
				.getList()) {
			((PolicySessionFeatureAPI) feature).globalInit(null, configDir);
			smf = feature;
			PolicyLogger.debug("testColdStandby stateManagementFeature.getResourceName(): "
					+ smf.getResourceName());
			break;
		}
		if (smf == null) {
			String msg = "testColdStandby failed to initialize.  "
					+ "Unable to get instance of StateManagementFeatureAPI "
					+ "with resourceID: " + thisPdpId;
			PolicyLogger.error(MessageCodes.GENERAL_ERROR, msg);
			PolicyLogger.debug(msg);
		}

		// Create an ActiveStandbyFeature and initialize it. It will discover
		// the StateManagementFeature that has been created.
		ActiveStandbyFeatureAPI activeStandbyFeature = null;
		for (ActiveStandbyFeatureAPI feature : ActiveStandbyFeatureAPI.impl
				.getList()) {
			((PolicySessionFeatureAPI) feature).globalInit(null, configDir);
			activeStandbyFeature = feature;
			PolicyLogger.debug("testColdStandby activeStandbyFeature.getResourceName(): "
					+ activeStandbyFeature.getResourceName());
			break;
		}
		if (activeStandbyFeature == null) {
			String msg = "testColdStandby failed to initialize.  "
					+ "Unable to get instance of ActiveStandbyFeatureAPI "
					+ "with resourceID: " + thisPdpId;
			PolicyLogger.error(MessageCodes.GENERAL_ERROR, msg);
			PolicyLogger.debug(msg);
		}

		Thread.sleep(interruptRecoveryTime);

		IntegrityAudit integrityAudit = ActiveStandbyFeature.getIntegrityAudit();
		PolicyLogger.debug("testAuditInit: isThreadInitialized=" + integrityAudit.isThreadInitialized());
		assertTrue("AuditThread not initialized!?",integrityAudit.isThreadInitialized());

		/*
		 * This will interrupt thread.  However, the thread will not die.  It keeps on ticking and trying to
		 * run the audit.
		 */
		PolicyLogger.debug("testAuditInit: Stopping auditThread");
		integrityAudit.stopAuditThread();
		Thread.sleep(1000);
		assertTrue("AuditThread not still running after stopAuditThread invoked!?",integrityAudit.isThreadInitialized());

		PolicyLogger.debug("\n\ntestAuditInit: Exiting\n\n");
	}	
	
    /*
     * This method initializes and cleans the DB so that PDP-D will be able to 
     * store IntegrityAuditEntity in the DB.
     */
     
	public static void initializeDb(){
		
		PolicyLogger.debug("initializeDb: Entering");
		
    	Properties cleanProperties = new Properties();
    	cleanProperties.put(DroolsPersistenceProperties.DB_DRIVER,"org.h2.Driver");
    	cleanProperties.put(DroolsPersistenceProperties.DB_URL, "jdbc:h2:file:./sql/drools");
    	cleanProperties.put(DroolsPersistenceProperties.DB_USER, "sa");
    	cleanProperties.put(DroolsPersistenceProperties.DB_PWD, "");

    	EntityManagerFactory emf = Persistence.createEntityManagerFactory("junitDroolsPU", cleanProperties);
		
		EntityManager em = emf.createEntityManager();
		// Start a transaction
		EntityTransaction et = em.getTransaction();

		et.begin();

		// Clean up the DB
		em.createQuery("Delete from IntegrityAuditEntity").executeUpdate();

		// commit transaction
		et.commit();
		em.close();
		
		PolicyLogger.debug("initializeDb: Exiting");
	}	
}
