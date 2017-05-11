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
import java.util.Date;
import java.util.Properties;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openecomp.policy.common.ia.IntegrityAudit;
import org.openecomp.policy.common.im.StateManagement;
import org.openecomp.policy.common.logging.eelf.PolicyLogger;
import org.openecomp.policy.drools.core.IntegrityMonitorProperties;
import org.openecomp.policy.drools.core.PolicyContainer;
import org.openecomp.policy.drools.im.PMStandbyStateChangeNotifier;
import org.openecomp.policy.drools.persistence.DroolsPdpEntity;
import org.openecomp.policy.drools.persistence.DroolsPdpImpl;
import org.openecomp.policy.drools.persistence.DroolsPdpsConnector;
import org.openecomp.policy.drools.persistence.DroolsPersistenceProperties;
import org.openecomp.policy.drools.persistence.JpaDroolsPdpsConnector;
import org.openecomp.policy.drools.persistence.PersistenceFeature;
import org.openecomp.policy.drools.persistence.XacmlPersistenceProperties;
import org.openecomp.policy.drools.system.Main;
import org.openecomp.policy.drools.system.PolicyEngine;

/*
 * Cloned from StandbyStateManagement.java in support of US673632.
 * See MultiSite_v1-10.ppt, slide 38
 */
public class IntegrityAuditIntegrationTest {
		
	
	public static final String INTEGRITY_MONITOR_PROPERTIES_FILE="src/test/server/config/IntegrityMonitor.properties";
		
	/*
	 * Sleep 5 seconds after each test to allow interrupt (shutdown) recovery.
	 */
	private long interruptRecoveryTime = 5000;
	
	/*
	 * See the IntegrityMonitor.getJmxUrl() method for the rationale behind this jmx related processing.
	 */
	@BeforeClass
	public static void setUpClass() throws Exception {
		
		PolicyLogger.info("setUpClass: Entering");

		String userDir = System.getProperty("user.dir");
		PolicyLogger.debug("setUpClass: userDir=" + userDir);
		System.setProperty("com.sun.management.jmxremote.port", "9980");
		System.setProperty("com.sun.management.jmxremote.authenticate","false");
				
		// Make sure path to config directory is set correctly in PolicyContainer.main
		// Also make sure we ignore HTTP server failures resulting from port conflicts.
		PolicyContainer.isUnitTesting = true;
		
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
		Properties integrityMonitorProperties = new Properties();
		integrityMonitorProperties.load(new FileInputStream(new File(
				INTEGRITY_MONITOR_PROPERTIES_FILE)));
		IntegrityMonitorProperties.initProperties(integrityMonitorProperties);
		String thisPdpId = IntegrityMonitorProperties
				.getProperty(IntegrityMonitorProperties.PDP_INSTANCE_ID);
		
		PolicyLogger.debug("testAuditInit: Reading xacmlPersistenceProperties");
		Properties xacmlPersistenceProperties = new Properties();
		xacmlPersistenceProperties.load(new FileInputStream(new File(
				"src/test/server/config/xacmlPersistence.properties")));
		XacmlPersistenceProperties.initProperties(xacmlPersistenceProperties);
		
		PolicyLogger.debug("testAuditInit: Creating emfXacml");
		EntityManagerFactory emfXacml = Persistence.createEntityManagerFactory(
				"junitXacmlPU", xacmlPersistenceProperties);
		
		PolicyLogger.debug("testAuditInit: Reading droolsPersistenceProperties");
		Properties droolsPersistenceProperties = new Properties();
		droolsPersistenceProperties.load(new FileInputStream(new File(
				"src/test/server/config/droolsPersistence.properties")));
		DroolsPersistenceProperties.initProperties(droolsPersistenceProperties);

		PolicyLogger.debug("testAuditInit: Creating emfDrools");
		EntityManagerFactory emfDrools = Persistence.createEntityManagerFactory(
				"junitDroolsPU", droolsPersistenceProperties);
		
		DroolsPdpsConnector conn = new JpaDroolsPdpsConnector(emfDrools);
		
		PolicyLogger.debug("testAuditInit: Cleaning up tables");
		conn.deleteAllSessions();
		conn.deleteAllPdps();
		
		/*
		 * Insert this PDP as designated.  Initial standby state will be 
		 * either null or cold standby.   
		 */
		PolicyLogger.debug("testAuditInit: Inserting PDP=" + thisPdpId + " as designated");
		DroolsPdpImpl pdp = new DroolsPdpImpl(thisPdpId, true, 4, new Date());
		conn.insertPdp(pdp);
		DroolsPdpEntity droolsPdpEntity = conn.getPdp(thisPdpId);
		PolicyLogger.debug("testAuditInit: After insertion, PDP=" + thisPdpId + " has DESIGNATED="
				+ droolsPdpEntity.isDesignated());
		assertTrue(droolsPdpEntity.isDesignated() == true);
		
		PolicyLogger.debug("testAuditInit: Instantiating stateManagement object");
		StateManagement sm = new StateManagement(emfXacml, "dummy");
		sm.deleteAllStateManagementEntities();
		sm = new StateManagement(emfXacml, thisPdpId);
		PMStandbyStateChangeNotifier pmStandbyStateChangeNotifier = new PMStandbyStateChangeNotifier();
		sm.addObserver(pmStandbyStateChangeNotifier);
				
		PolicyLogger.debug("testAuditInit: Running policy-management.Main class, designated="
				+ conn.getPdp(thisPdpId).isDesignated());
		PolicyManagementRunner policyManagementRunner = new PolicyManagementRunner();
		policyManagementRunner.start();
		
		PolicyLogger.debug("testAuditInit: Runner started; Sleeping "
				+ interruptRecoveryTime + "ms before promoting PDP="
				+ thisPdpId);
		Thread.sleep(interruptRecoveryTime);
		
		IntegrityAudit integrityAudit = PersistenceFeature.getIntegrityAudit();
		PolicyLogger.debug("testAuditInit: isThreadInitialized=" + integrityAudit.isThreadInitialized());
		assertTrue("AuditThread not initialized!?",integrityAudit.isThreadInitialized());
				
		PolicyLogger.debug("testAuditInit: Stopping auditThread");
		integrityAudit.stopAuditThread();
		Thread.sleep(1000);
		//This will interrupt thread.  However, the thread will not die.  It keeps on ticking and trying to
		//run the audit.
		assertTrue("AuditThread not still running after stopAuditThread invoked!?",integrityAudit.isThreadInitialized());

		PolicyLogger.debug("testAuditInit: Stopping policyManagementRunner");
		policyManagementRunner.stopRunner();
		
		PolicyLogger.debug("\n\ntestAuditInit: Exiting\n\n");
		Thread.sleep(interruptRecoveryTime);

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
    	//EntityManagerFactory emf = Persistence.createEntityManagerFactory("schemaPU", cleanProperties);
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
	
	private class PolicyManagementRunner extends Thread {

		public void run() {
			PolicyLogger.info("PolicyManagementRunner.run: Entering");
			String args[] = { "src/main/server/config" };
			try {
				Main.main(args);
			} catch (Exception e) {
				PolicyLogger
						.info("PolicyManagementRunner.run: Exception thrown from Main.main(), message="
								+ e.getMessage());
			}
			PolicyLogger.info("PolicyManagementRunner.run: Exiting");
		}
		
		public void stopRunner() {
			PolicyEngine.manager.shutdown();
		}

	}
	
}
