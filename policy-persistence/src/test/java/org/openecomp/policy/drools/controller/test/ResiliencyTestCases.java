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
import java.io.FileNotFoundException;
import java.io.IOException;
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
import org.junit.Ignore;
import org.junit.Test;

import org.openecomp.policy.common.logging.eelf.PolicyLogger;
import org.openecomp.policy.common.im.AdministrativeStateException;
import org.openecomp.policy.common.im.IntegrityMonitor;
import org.openecomp.policy.common.im.StandbyStatusException;
import org.openecomp.policy.common.im.StateManagement;
import org.openecomp.policy.drools.core.DroolsPDPIntegrityMonitor;
import org.openecomp.policy.drools.core.IntegrityMonitorProperties;
import org.openecomp.policy.drools.core.PolicyContainer;
import org.openecomp.policy.drools.im.PMStandbyStateChangeNotifier;
import org.openecomp.policy.drools.persistence.DroolsPdp;
import org.openecomp.policy.drools.persistence.DroolsPdpEntity;
import org.openecomp.policy.drools.persistence.DroolsPdpImpl;
import org.openecomp.policy.drools.persistence.DroolsPdpsConnector;
import org.openecomp.policy.drools.persistence.JpaDroolsPdpsConnector;
import org.openecomp.policy.drools.persistence.DroolsPersistenceProperties;
import org.openecomp.policy.drools.persistence.XacmlPersistenceProperties;
import org.openecomp.policy.drools.system.Main;
import org.openecomp.policy.drools.system.PolicyEngine;

import org.apache.commons.lang3.time.DateUtils;

/*
 * Cloned from StandbyStateManagement.java in support of US673632.
 * See MultiSite_v1-10.ppt, slide 38
 */
public class ResiliencyTestCases {
			
	/*
	 * Currently, the DroolsPdpsElectionHandler.DesignationWaiter is invoked every ten seconds, starting 
	 * at ten seconds after the minute boundary (e.g. 13:05:10). So, an 80 second sleep should be 
	 * sufficient to ensure that we wait for the DesignationWaiter to do its job, before 
	 * checking the results. 
	 */
	long sleepTime = 80000;
	
	/*
	 * DroolsPdpsElectionHandler runs every ten seconds, so a 15 second sleep should be 
	 * plenty to ensure it has time to re-promote this PDP.
	 */
	long electionWaitSleepTime = 15000;
	
	/*
	 * Sleep 5 seconds after each test to allow interrupt (shutdown) recovery.
	 */
	long interruptRecoveryTime = 5000;

	/*
	 * See the IntegrityMonitor.getJmxUrl() method for the rationale behind this jmx related processing.
	 */
	@BeforeClass
	public static void setUpClass() throws Exception {
		
		String userDir = System.getProperty("user.dir");
		PolicyLogger.debug("setUpClass: userDir=" + userDir);
		System.setProperty("com.sun.management.jmxremote.port", "9980");
		System.setProperty("com.sun.management.jmxremote.authenticate","false");
				
		// Make sure path to config directory is set correctly in PolicyContainer.main
		// Also make sure we ignore HTTP server failures resulting from port conflicts.
		PolicyContainer.isUnitTesting = true;
		
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
	
	public void cleanDroolsDB() throws Exception{
		PolicyLogger.debug("\n\ncleanDroolsDB: Entering\n\n");

		PolicyLogger.debug("cleanDroolsDB: Reading droolsPersistenceProperties");
		Properties droolsPersistenceProperties = new Properties();
		droolsPersistenceProperties.load(new FileInputStream(new File(
				"src/test/server/config/droolsPersistence.properties")));

		PolicyLogger.debug("cleanDroolsDB: Creating emfDrools");
		EntityManagerFactory emf = Persistence.createEntityManagerFactory(
				"junitDroolsPU", droolsPersistenceProperties);
		
		PolicyLogger.debug("cleanDroolsDB: Cleaning up tables");
		
		EntityManager em = emf.createEntityManager();
		EntityTransaction et = em.getTransaction();
		et.begin();
		
		// Make sure the DB is clean
		PolicyLogger.debug("cleanDroolsDB: clean DroolsPdpEntity");
		em.createQuery("DELETE FROM DroolsPdpEntity").executeUpdate();
		PolicyLogger.debug("cleanDroolsDB: clean DroolsSessionEntity");
		em.createQuery("DELETE FROM DroolsSessionEntity").executeUpdate();
		
		em.flush(); 
		PolicyLogger.debug("cleanDroolsDB: after flush");

		et.commit(); 
		
		PolicyLogger.debug("\n\ncleanDroolsDB: Exiting\n\n");
	}
	
	public void cleanXacmlDB() throws Exception {
		PolicyLogger.debug("\n\ncleanXacmlDB: Entering\n\n");

		PolicyLogger.debug("cleanXacmlDB: Reading IntegrityMonitorProperties");

		PolicyLogger.debug("cleanXacmlDB: Reading xacmlPersistenceProperties");
		Properties xacmlPersistenceProperties = new Properties();
		xacmlPersistenceProperties.load(new FileInputStream(new File(
				"src/test/server/config/xacmlPersistence.properties")));
		
		PolicyLogger.debug("cleanXacmlDB: Creating emf");
		EntityManagerFactory emf = Persistence.createEntityManagerFactory(
				"junitXacmlPU", xacmlPersistenceProperties);
		
		EntityManager em = emf.createEntityManager();
		EntityTransaction et = em.getTransaction();
		et.begin();
		
		// Make sure the DB is clean
		PolicyLogger.debug("cleanXacmlDB: clean StateManagementEntity");
		em.createQuery("DELETE FROM StateManagementEntity").executeUpdate();
		PolicyLogger.debug("cleanXacmlDB: clean ResourceRegistrationEntity");
		em.createQuery("DELETE FROM ResourceRegistrationEntity").executeUpdate();
		PolicyLogger.debug("cleanXacmlDB: clean ForwardProgressEntity");
		em.createQuery("DELETE FROM ForwardProgressEntity").executeUpdate();
		
		em.flush(); 
		PolicyLogger.debug("cleandXacmlDB: after flush");

		et.commit(); 
		
		PolicyLogger.debug("\n\ncleanXacmlDB: Exiting\n\n");
		
	}
	
	@Ignore
	@Test
	public void singleNodeTests() throws Exception{
		//snNewInstall();
		snNewInstallBadDepData();
		/*snRecoveryFromBadDepData();
		snLock();
		snLockRestart();
		snUnlock();
		snUnlockRestart();*/
	}
	
	@Ignore
	@Test
	public void twoNodeTests() throws Exception{
		tnNewInstall();
		tnLockActive();
		tnUnlockColdStandby();
		tnFailActive();
		tnRecoverFailed();
	}
	
	@Ignore
	@Test
	public void twoSitesTwoNodesPerSiteTests() throws Exception{
		tstnNewInstall();
		tstnLock1Site1();
		tstnLock2Site1();
		tstnFailActiveSite2();
		tstnRecoverFailedSite2();
		tstnUnlockSite1();
		tstnFailSite2();
	}
	

	/*
	 * Single Node Tests
	 */
	public void snNewInstall() throws Exception{
		PolicyLogger.debug("\n\nsnNewInstall: Entry\n\n");
		cleanDroolsDB();
		cleanXacmlDB();
		
		//*******************************************
		
		PolicyLogger.debug("snNewInstall: Reading IntegrityMonitorProperties");
		Properties integrityMonitorProperties = new Properties();
		integrityMonitorProperties.load(new FileInputStream(new File(
				"src/test/server/config/IntegrityMonitor.properties")));
		IntegrityMonitorProperties.initProperties(integrityMonitorProperties);
		String thisPdpId = IntegrityMonitorProperties
				.getProperty(IntegrityMonitorProperties.PDP_INSTANCE_ID);

		PolicyLogger.debug("snNewInstall: Reading xacmlPersistenceProperties");
		Properties xacmlPersistenceProperties = new Properties();
		xacmlPersistenceProperties.load(new FileInputStream(new File(
				"src/test/server/config/xacmlPersistence.properties")));
		XacmlPersistenceProperties.initProperties(xacmlPersistenceProperties);
		
		PolicyLogger.debug("snNewInstall: Creating emfXacml");
		EntityManagerFactory emfXacml = Persistence.createEntityManagerFactory(
				"junitXacmlPU", xacmlPersistenceProperties);
		
		PolicyLogger.debug("snNewInstall: Reading droolsPersistenceProperties");
		Properties droolsPersistenceProperties = new Properties();
		droolsPersistenceProperties.load(new FileInputStream(new File(
				"src/test/server/config/droolsPersistence.properties")));
		DroolsPersistenceProperties.initProperties(droolsPersistenceProperties);

		PolicyLogger.debug("snNewInstall: Creating emfDrools");
		EntityManagerFactory emfDrools = Persistence.createEntityManagerFactory(
				"junitDroolsPU", droolsPersistenceProperties);
		
		DroolsPdpsConnector conn = new JpaDroolsPdpsConnector(emfDrools);
	
		PolicyLogger.debug("snNewInstall: Inserting PDP=" + thisPdpId + " as designated");
		DroolsPdp pdp = new DroolsPdpImpl(thisPdpId, true, 4, new Date());
		conn.insertPdp(pdp);
		DroolsPdpEntity droolsPdpEntity = conn.getPdp(thisPdpId);
		PolicyLogger.debug("snNewInstall: After insertion, DESIGNATED="
				+ droolsPdpEntity.isDesignated() + " for PDP=" + thisPdpId);
		assertTrue(droolsPdpEntity.isDesignated() == true);

		/*
		 * When the Standby Status changes (from providingservice) to hotstandby
		 * or coldstandby,the Active/Standby selection algorithm must stand down
		 * if thePDP-D is currently the lead/active node and allow another PDP-D
		 * to take over.
		 * 
		 * It must also call lock on all engines in the engine management.
		 * 
		 */
		PolicyLogger.debug("snNewInstall: Instantiating stateManagement object");
		StateManagement sm = new StateManagement(emfXacml, thisPdpId);
		PMStandbyStateChangeNotifier pmStandbyStateChangeNotifier = new PMStandbyStateChangeNotifier();
		sm.addObserver(pmStandbyStateChangeNotifier);
		
		// Artificially putting a PDP into service is really a two step process, 1)
		// inserting it as designated and 2) promoting it so that its standbyStatus
		// is providing service.
		
		PolicyLogger.debug("snNewInstall: Running policy-management.Main class");
		PolicyManagementRunner policyManagementRunner = new PolicyManagementRunner();
		policyManagementRunner.start();
		
		PolicyLogger.debug("snNewInstall: Runner started; Sleeping "
				+ interruptRecoveryTime + "ms before promoting PDP="
				+ thisPdpId);
		Thread.sleep(interruptRecoveryTime);

		PolicyLogger.debug("snNewInstall: Promoting PDP=" + thisPdpId);
		sm.promote();		
		
		String standbyStatus = sm.getStandbyStatus(thisPdpId);
		PolicyLogger.debug("snNewInstall: Before locking, PDP=" + thisPdpId + " has standbyStatus="
				+ standbyStatus);
		
		PolicyLogger.debug("snNewInstall: Locking sm");
		sm.lock();
		
		Thread.sleep(interruptRecoveryTime);
		/*
		 * Verify that the PDP is no longer designated.
		 */
		droolsPdpEntity = conn.getPdp(thisPdpId);
		PolicyLogger.debug("snNewInstall: After lock sm.lock() invoked, DESIGNATED="
				+ droolsPdpEntity.isDesignated() + " for PDP=" + thisPdpId);
		assertTrue(droolsPdpEntity.isDesignated() == false);
		
		PolicyLogger.debug("snNewInstall: Stopping policyManagementRunner");
		policyManagementRunner.stopRunner();
	
		PolicyLogger.debug("\n\nsnNewInstall: Exiting\n\n");
		Thread.sleep(interruptRecoveryTime);
		
		//********************************************

		PolicyLogger.debug("\n\nsnNewInstall: Exit\n\n");
	}
	
	public void snNewInstallBadDepData() throws Exception{
		PolicyLogger.debug("\n\nsnNewInstallBadDepData: Entry\n\n");
		cleanDroolsDB();
		cleanXacmlDB();
		
		//*******************************************
		
		PolicyLogger.debug("snNewInstallBadDepData: Reading IntegrityMonitor_BadDependencyData.properties");
		Properties integrityMonitorProperties = new Properties();
		integrityMonitorProperties.load(new FileInputStream(new File(
				"src/test/server/config/IntegrityMonitor_BadDependencyData.properties")));
		IntegrityMonitorProperties.initProperties(integrityMonitorProperties);
		String thisPdpId = IntegrityMonitorProperties
				.getProperty(IntegrityMonitorProperties.PDP_INSTANCE_ID);

		PolicyLogger.debug("snNewInstallBadDepData: Reading xacmlPersistenceProperties");
		Properties xacmlPersistenceProperties = new Properties();
		xacmlPersistenceProperties.load(new FileInputStream(new File(
				"src/test/server/config/xacmlPersistence.properties")));
		XacmlPersistenceProperties.initProperties(xacmlPersistenceProperties);
		
		PolicyLogger.debug("snNewInstallBadDepData: Creating emfXacml");
		EntityManagerFactory emfXacml = Persistence.createEntityManagerFactory(
				"junitXacmlPU", xacmlPersistenceProperties);
		
		PolicyLogger.debug("snNewInstallBadDepData: Reading droolsPersistenceProperties");
		Properties droolsPersistenceProperties = new Properties();
		droolsPersistenceProperties.load(new FileInputStream(new File(
				"src/test/server/config/droolsPersistence.properties")));
		DroolsPersistenceProperties.initProperties(droolsPersistenceProperties);

		PolicyLogger.debug("snNewInstallBadDepData: Creating emfDrools");
		EntityManagerFactory emfDrools = Persistence.createEntityManagerFactory(
				"junitDroolsPU", droolsPersistenceProperties);
		
		DroolsPdpsConnector conn = new JpaDroolsPdpsConnector(emfDrools);
	
		PolicyLogger.debug("snNewInstallBadDepData: Inserting PDP=" + thisPdpId + " as designated");
		DroolsPdp pdp = new DroolsPdpImpl(thisPdpId, true, 4, new Date());
		conn.insertPdp(pdp);
		DroolsPdpEntity droolsPdpEntity = conn.getPdp(thisPdpId);
		//PolicyLogger.debug
		System.out.println
		("\n\nsnNewInstallBadDepData: After insertion, DESIGNATED="
				+ droolsPdpEntity.isDesignated() + " for PDP=" + thisPdpId + "\n\n********************");
		assertTrue(droolsPdpEntity.isDesignated() == true);
		
		/*
		 * When the Standby Status changes (from providingservice) to hotstandby
		 * or coldstandby,the Active/Standby selection algorithm must stand down
		 * if thePDP-D is currently the lead/active node and allow another PDP-D
		 * to take over.
		 */
		PolicyLogger.debug("snNewInstall: Instantiating stateManagement object");
		StateManagement sm = new StateManagement(emfXacml, thisPdpId);
		PMStandbyStateChangeNotifier pmStandbyStateChangeNotifier = new PMStandbyStateChangeNotifier();
		sm.addObserver(pmStandbyStateChangeNotifier);
		
		// Artificially putting a PDP into service is really a two step process, 1)
		// inserting it as designated and 2) promoting it so that its standbyStatus
		// is providing service.
		
		PolicyLogger.debug("snNewInstall: Running policy-management.Main class");
		PolicyManagementRunner policyManagementRunner = new PolicyManagementRunner();
		policyManagementRunner.start();
		
		PolicyLogger.debug("snNewInstall: Runner started; Sleeping "
				+ interruptRecoveryTime + "ms before promoting PDP="
				+ thisPdpId);
		Thread.sleep(interruptRecoveryTime);

		PolicyLogger.debug("snNewInstall: Promoting PDP=" + thisPdpId);
		sm.promote();		
		
		String standbyStatus = sm.getStandbyStatus(thisPdpId);
		PolicyLogger.debug("snNewInstall: Before locking, PDP=" + thisPdpId + " has standbyStatus="
				+ standbyStatus);
		
		/*
		 * Verify that the PDP is no longer designated.
		 */
		droolsPdpEntity = conn.getPdp(thisPdpId);
		PolicyLogger.debug("snNewInstall: After lock sm.lock() invoked, DESIGNATED="
				+ droolsPdpEntity.isDesignated() + " for PDP=" + thisPdpId);
		assertTrue(droolsPdpEntity.isDesignated() == false);
		
		PolicyLogger.debug("snNewInstall: Stopping policyManagementRunner");
		policyManagementRunner.stopRunner();
	
		PolicyLogger.debug("\n\nsnNewInstall: Exiting\n\n");
		Thread.sleep(interruptRecoveryTime);
		
		//********************************************

		PolicyLogger.debug("\n\nsnNewInstallBadDepData: Exit\n\n");
	}
	
	public void snRecoveryFromBadDepData() throws Exception{
		
	}
	
	public void snLock() throws Exception {
		
	}
	
	public void snLockRestart() throws Exception {
		
	}
	
	public void snUnlock() throws Exception {
		
	}
	
	public void snUnlockRestart() throws Exception {
		
	}
	
	/*
	 * Two Nodes tests
	 */
	public void tnNewInstall() throws Exception {
		
	}
	
	public void tnLockActive() throws Exception {
		
	}
	
	public void tnUnlockColdStandby() throws Exception {
		
	}
	
	public void tnFailActive() throws Exception {
		
	}
	
	public void tnRecoverFailed() throws Exception {
		
	}
	
	/*
	 * Two Sites, Two Nodes Each Site tests
	 */
	
	public void tstnNewInstall() throws Exception {
		
	}
	
	public void tstnLock1Site1() throws Exception {
		
	}
	
	public void tstnLock2Site1() throws Exception {
		
	}
	
	public void tstnFailActiveSite2() throws Exception {
		
	}
	
	public void tstnRecoverFailedSite2() throws Exception {
		
	}
	
	public void tstnUnlockSite1() throws Exception {
		
	}
	
	public void tstnFailSite2() throws Exception {
		
	}
	
	
	@Ignore
	@Test
	public void testColdStandby() throws Exception {

		PolicyLogger.debug("\n\ntestColdStandby: Entering\n\n");

		PolicyLogger.debug("testColdStandby: Reading IntegrityMonitorProperties");
		Properties integrityMonitorProperties = new Properties();
		integrityMonitorProperties.load(new FileInputStream(new File(
				"src/test/server/config/IntegrityMonitor.properties")));
		IntegrityMonitorProperties.initProperties(integrityMonitorProperties);
		String thisPdpId = IntegrityMonitorProperties
				.getProperty(IntegrityMonitorProperties.PDP_INSTANCE_ID);

		PolicyLogger.debug("testColdStandby: Reading xacmlPersistenceProperties");
		Properties xacmlPersistenceProperties = new Properties();
		xacmlPersistenceProperties.load(new FileInputStream(new File(
				"src/test/server/config/xacmlPersistence.properties")));
		XacmlPersistenceProperties.initProperties(xacmlPersistenceProperties);
		
		PolicyLogger.debug("testColdStandby: Creating emfXacml");
		EntityManagerFactory emfXacml = Persistence.createEntityManagerFactory(
				"junitXacmlPU", xacmlPersistenceProperties);
		
		PolicyLogger.debug("testColdStandby: Reading droolsPersistenceProperties");
		Properties droolsPersistenceProperties = new Properties();
		droolsPersistenceProperties.load(new FileInputStream(new File(
				"src/test/server/config/droolsPersistence.properties")));
		DroolsPersistenceProperties.initProperties(droolsPersistenceProperties);

		PolicyLogger.debug("testColdStandby: Creating emfDrools");
		EntityManagerFactory emfDrools = Persistence.createEntityManagerFactory(
				"junitDroolsPU", droolsPersistenceProperties);
		
		DroolsPdpsConnector conn = new JpaDroolsPdpsConnector(emfDrools);
		
		PolicyLogger.debug("testColdStandby: Cleaning up tables");
		conn.deleteAllSessions();
		conn.deleteAllPdps();
	
		PolicyLogger.debug("testColdStandby: Inserting PDP=" + thisPdpId + " as designated");
		DroolsPdp pdp = new DroolsPdpImpl(thisPdpId, true, 4, new Date());
		conn.insertPdp(pdp);
		DroolsPdpEntity droolsPdpEntity = conn.getPdp(thisPdpId);
		PolicyLogger.debug("testColdStandby: After insertion, DESIGNATED="
				+ droolsPdpEntity.isDesignated() + " for PDP=" + thisPdpId);
		assertTrue(droolsPdpEntity.isDesignated() == true);

		/*
		 * When the Standby Status changes (from providingservice) to hotstandby
		 * or coldstandby,the Active/Standby selection algorithm must stand down
		 * if thePDP-D is currently the lead/active node and allow another PDP-D
		 * to take over.
		 * 
		 * It must also call lock on all engines in the engine management.
		 * 
		 * Yes, this is kludgy, but we have a chicken and egg problem here: we
		 * need a StateManagement object to invoke the
		 * deleteAllStateManagementEntities method.
		 */
		PolicyLogger.debug("testColdStandby: Instantiating stateManagement object");
		StateManagement sm = new StateManagement(emfXacml, "dummy");
		sm.deleteAllStateManagementEntities();
		sm = new StateManagement(emfXacml, thisPdpId);
		PMStandbyStateChangeNotifier pmStandbyStateChangeNotifier = new PMStandbyStateChangeNotifier();
		sm.addObserver(pmStandbyStateChangeNotifier);
		
		// Artificially putting a PDP into service is really a two step process, 1)
		// inserting it as designated and 2) promoting it so that its standbyStatus
		// is providing service.
		
		PolicyLogger.debug("testColdStandby: Running policy-management.Main class");
		PolicyManagementRunner policyManagementRunner = new PolicyManagementRunner();
		policyManagementRunner.start();
		
		PolicyLogger.debug("testColdStandby: Runner started; Sleeping "
				+ interruptRecoveryTime + "ms before promoting PDP="
				+ thisPdpId);
		Thread.sleep(interruptRecoveryTime);

		PolicyLogger.debug("testColdStandby: Promoting PDP=" + thisPdpId);
		sm.promote();		
		
		String standbyStatus = sm.getStandbyStatus(thisPdpId);
		PolicyLogger.debug("testColdStandby: Before locking, PDP=" + thisPdpId + " has standbyStatus="
				+ standbyStatus);
		
		PolicyLogger.debug("testColdStandby: Locking sm");
		sm.lock();
		
		Thread.sleep(interruptRecoveryTime);
		/*
		 * Verify that the PDP is no longer designated.
		 */
		droolsPdpEntity = conn.getPdp(thisPdpId);
		PolicyLogger.debug("testColdStandby: After lock sm.lock() invoked, DESIGNATED="
				+ droolsPdpEntity.isDesignated() + " for PDP=" + thisPdpId);
		assertTrue(droolsPdpEntity.isDesignated() == false);
		
		PolicyLogger.debug("testColdStandby: Stopping policyManagementRunner");
		policyManagementRunner.stopRunner();
	
		PolicyLogger.debug("\n\ntestColdStandby: Exiting\n\n");
		Thread.sleep(interruptRecoveryTime);

	}
	
	/*
	 * Tests hot standby when there is only one PDP.
	 */
	@Ignore
	@Test
	public void testHotStandby1() throws Exception {
	
		PolicyLogger.debug("\n\ntestHotStandby1: Entering\n\n");
		
		PolicyLogger.debug("testHotStandby1: Reading IntegrityMonitorProperties");
		Properties integrityMonitorProperties = new Properties();
		integrityMonitorProperties.load(new FileInputStream(new File(
				"src/test/server/config/IntegrityMonitor.properties")));
		IntegrityMonitorProperties.initProperties(integrityMonitorProperties);
		String thisPdpId = IntegrityMonitorProperties
				.getProperty(IntegrityMonitorProperties.PDP_INSTANCE_ID);
		
		PolicyLogger.debug("testHotStandby1: Reading xacmlPersistenceProperties");
		Properties xacmlPersistenceProperties = new Properties();
		xacmlPersistenceProperties.load(new FileInputStream(new File(
				"src/test/server/config/xacmlPersistence.properties")));
		XacmlPersistenceProperties.initProperties(xacmlPersistenceProperties);
		
		PolicyLogger.debug("testHotStandby1: Creating emfXacml");
		EntityManagerFactory emfXacml = Persistence.createEntityManagerFactory(
				"junitXacmlPU", xacmlPersistenceProperties);
		
		PolicyLogger.debug("testHotStandby1: Reading droolsPersistenceProperties");
		Properties droolsPersistenceProperties = new Properties();
		droolsPersistenceProperties.load(new FileInputStream(new File(
				"src/test/server/config/droolsPersistence.properties")));
		DroolsPersistenceProperties.initProperties(droolsPersistenceProperties);

		PolicyLogger.debug("testHotStandby1: Creating emfDrools");
		EntityManagerFactory emfDrools = Persistence.createEntityManagerFactory(
				"junitDroolsPU", droolsPersistenceProperties);
		
		DroolsPdpsConnector conn = new JpaDroolsPdpsConnector(emfDrools);
		
		PolicyLogger.debug("testHotStandby1: Cleaning up tables");
		conn.deleteAllSessions();
		conn.deleteAllPdps();
					
		/*
		 * Insert this PDP as not designated.  Initial standby state will be 
		 * either null or cold standby.   Demoting should transit state to
		 * hot standby.
		 */
		PolicyLogger.debug("testHotStandby1: Inserting PDP=" + thisPdpId + " as not designated");
		Date yesterday = DateUtils.addDays(new Date(), -1);
		DroolsPdpImpl pdp = new DroolsPdpImpl(thisPdpId, false, 4, yesterday);
		conn.insertPdp(pdp);
		DroolsPdpEntity droolsPdpEntity = conn.getPdp(thisPdpId);
		PolicyLogger.debug("testHotStandby1: After insertion, PDP=" + thisPdpId + " has DESIGNATED="
				+ droolsPdpEntity.isDesignated());
		assertTrue(droolsPdpEntity.isDesignated() == false);
		
		PolicyLogger.debug("testHotStandby1: Instantiating stateManagement object");
		StateManagement sm = new StateManagement(emfXacml, "dummy");
		sm.deleteAllStateManagementEntities();
		sm = new StateManagement(emfXacml, thisPdpId);
		PMStandbyStateChangeNotifier pmStandbyStateChangeNotifier = new PMStandbyStateChangeNotifier();
		sm.addObserver(pmStandbyStateChangeNotifier);

		PolicyLogger.debug("testHotStandby1: Demoting PDP=" + thisPdpId);
		// demoting should cause state to transit to hotstandby
		sm.demote();
		
		PolicyLogger.debug("testHotStandby1: Running policy-management.Main class");
		PolicyManagementRunner policyManagementRunner = new PolicyManagementRunner();
		policyManagementRunner.start();
				
		PolicyLogger.debug("testHotStandby1: Sleeping "
				+ sleepTime
				+ "ms, to allow JpaDroolsPdpsConnector time to check droolspdpentity table");
		Thread.sleep(sleepTime);
		
		/*
		 * Verify that this formerly un-designated PDP in HOT_STANDBY is now designated and providing service.
		 */
		droolsPdpEntity = conn.getPdp(thisPdpId);
		PolicyLogger.debug("testHotStandby1: After sm.demote() invoked, DESIGNATED="
				+ droolsPdpEntity.isDesignated() + " for PDP=" + thisPdpId);
		assertTrue(droolsPdpEntity.isDesignated() == true);
		String standbyStatus = sm.getStandbyStatus(thisPdpId);
		PolicyLogger.debug("testHotStandby1: After demotion, PDP=" + thisPdpId + " has standbyStatus="
				+ standbyStatus);
		assertTrue(standbyStatus != null  &&  standbyStatus.equals(StateManagement.PROVIDING_SERVICE));
				
		PolicyLogger.debug("testHotStandby1: Stopping policyManagementRunner");
		policyManagementRunner.stopRunner();		
	
		PolicyLogger.debug("\n\ntestHotStandby1: Exiting\n\n");
		Thread.sleep(interruptRecoveryTime);

	}

	/*
	 * Tests hot standby when two PDPs are involved.
	 */
	@Ignore
	@Test
	public void testHotStandby2() throws Exception {

		PolicyLogger.debug("\n\ntestHotStandby2: Entering\n\n");
		
		PolicyLogger.debug("testHotStandby2: Reading IntegrityMonitorProperties");
		Properties integrityMonitorProperties = new Properties();
		integrityMonitorProperties.load(new FileInputStream(new File(
				"src/test/server/config/IntegrityMonitor.properties")));
		IntegrityMonitorProperties.initProperties(integrityMonitorProperties);
		String thisPdpId = IntegrityMonitorProperties
				.getProperty(IntegrityMonitorProperties.PDP_INSTANCE_ID);
		
		PolicyLogger.debug("testHotStandby2: Reading xacmlPersistenceProperties");
		Properties xacmlPersistenceProperties = new Properties();
		xacmlPersistenceProperties.load(new FileInputStream(new File(
				"src/test/server/config/xacmlPersistence.properties")));
		XacmlPersistenceProperties.initProperties(xacmlPersistenceProperties);
		
		PolicyLogger.debug("testHotStandby2: Creating emfXacml");
		EntityManagerFactory emfXacml = Persistence.createEntityManagerFactory(
				"junitXacmlPU", xacmlPersistenceProperties);
		
		PolicyLogger.debug("testHotStandby2: Reading droolsPersistenceProperties");
		Properties droolsPersistenceProperties = new Properties();
		droolsPersistenceProperties.load(new FileInputStream(new File(
				"src/test/server/config/droolsPersistence.properties")));
		DroolsPersistenceProperties.initProperties(droolsPersistenceProperties);

		PolicyLogger.debug("testHotStandby2: Creating emfDrools");
		EntityManagerFactory emfDrools = Persistence.createEntityManagerFactory(
				"junitDroolsPU", droolsPersistenceProperties);
		
		DroolsPdpsConnector conn = new JpaDroolsPdpsConnector(emfDrools);
		
		PolicyLogger.debug("testHotStandby2: Cleaning up tables");
		conn.deleteAllSessions();
		conn.deleteAllPdps();
		
		/*
		 * Insert a PDP that's designated but not current.
		 */
		String activePdpId = "pdp2";
		PolicyLogger.debug("testHotStandby2: Inserting PDP=" + activePdpId + " as stale, designated PDP");
		Date yesterday = DateUtils.addDays(new Date(), -1);
		DroolsPdp pdp = new DroolsPdpImpl(activePdpId, true, 4, yesterday);
		conn.insertPdp(pdp);
		DroolsPdpEntity droolsPdpEntity = conn.getPdp(activePdpId);
		PolicyLogger.debug("testHotStandby2: After insertion, PDP=" + activePdpId + ", which is not current, has DESIGNATED="
				+ droolsPdpEntity.isDesignated());
		assertTrue(droolsPdpEntity.isDesignated() == true);
		
		/*
		 * Promote the designated PDP.
		 * 
		 * We have a chicken and egg problem here: we need a StateManagement
		 * object to invoke the deleteAllStateManagementEntities method.
		 */
		PolicyLogger.debug("testHotStandy2: Promoting PDP=" + activePdpId);
		StateManagement sm = new StateManagement(emfXacml, "dummy");
		sm.deleteAllStateManagementEntities();
		sm = new StateManagement(emfXacml, activePdpId);
		PMStandbyStateChangeNotifier pmStandbyStateChangeNotifier = new PMStandbyStateChangeNotifier();
		sm.addObserver(pmStandbyStateChangeNotifier);
		
		// Artificially putting a PDP into service is really a two step process, 1)
		// inserting it as designated and 2) promoting it so that its standbyStatus
		// is providing service.
				
		/*
		 * Insert this PDP as not designated.  Initial standby state will be 
		 * either null or cold standby.   Demoting should transit state to
		 * hot standby.
		 */
		PolicyLogger.debug("testHotStandby2: Inserting PDP=" + thisPdpId + " as not designated");
		pdp = new DroolsPdpImpl(thisPdpId, false, 4, yesterday);
		conn.insertPdp(pdp);
		droolsPdpEntity = conn.getPdp(thisPdpId);
		PolicyLogger.debug("testHotStandby2: After insertion, PDP=" + thisPdpId + " has DESIGNATED="
				+ droolsPdpEntity.isDesignated());
		assertTrue(droolsPdpEntity.isDesignated() == false);
		
		PolicyLogger.debug("testHotStandby2: Demoting PDP=" + thisPdpId);
		StateManagement sm2 = new StateManagement(emfXacml, thisPdpId);
		sm2.addObserver(pmStandbyStateChangeNotifier);
		
		PolicyLogger.debug("testHotStandby2: Running policy-management.Main class");
		PolicyManagementRunner policyManagementRunner = new PolicyManagementRunner();
		policyManagementRunner.start();
		
		PolicyLogger.debug("testHotStandby2: Runner started; Sleeping "
				+ interruptRecoveryTime + "ms before promoting/demoting");
		Thread.sleep(interruptRecoveryTime);

		PolicyLogger.debug("testHotStandby2: Runner started; promoting PDP=" + activePdpId);
		sm.promote();
		String standbyStatus = sm.getStandbyStatus(activePdpId);
		PolicyLogger.debug("testHotStandby2: After promoting, PDP=" + activePdpId + " has standbyStatus="
				+ standbyStatus);
		
		// demoting PDP should ensure that state transits to hotstandby
		PolicyLogger.debug("testHotStandby2: Runner started; demoting PDP=" + thisPdpId);
		sm2.demote();
		standbyStatus = sm.getStandbyStatus(thisPdpId);
		PolicyLogger.debug("testHotStandby2: After demoting, PDP=" + thisPdpId + " has standbyStatus="
				+ standbyStatus);
		
		PolicyLogger.debug("testHotStandby2: Sleeping "
				+ sleepTime
				+ "ms, to allow JpaDroolsPdpsConnector time to check droolspdpentity table");
		Thread.sleep(sleepTime);
		
		/*
		 * Verify that this PDP, demoted to HOT_STANDBY, is now
		 * re-designated and providing service.
		 */
		droolsPdpEntity = conn.getPdp(thisPdpId);
		PolicyLogger.debug("testHotStandby2: After demoting PDP=" + activePdpId
				+ ", DESIGNATED=" + droolsPdpEntity.isDesignated()
				+ " for PDP=" + thisPdpId);
		assertTrue(droolsPdpEntity.isDesignated() == true);
		standbyStatus = sm2.getStandbyStatus(thisPdpId);
		PolicyLogger.debug("testHotStandby2: After demoting PDP=" + activePdpId
				+ ", PDP=" + thisPdpId + " has standbyStatus=" + standbyStatus);
		assertTrue(standbyStatus != null
				&& standbyStatus.equals(StateManagement.PROVIDING_SERVICE));
				
		PolicyLogger.debug("testHotStandby2: Stopping policyManagementRunner");
		policyManagementRunner.stopRunner();		

		PolicyLogger.debug("\n\ntestHotStandby2: Exiting\n\n");
		Thread.sleep(interruptRecoveryTime);

	}
	
	/*
	 * 1) Inserts and designates this PDP, then verifies that startTransaction
	 * is successful.
	 * 
	 * 2) Demotes PDP, and verifies that because there is only one PDP, it will
	 * be immediately re-promoted, thus allowing startTransaction to be
	 * successful.
	 * 
	 * 3) Locks PDP and verifies that startTransaction results in
	 * AdministrativeStateException.
	 * 
	 * 4) Unlocks PDP and verifies that startTransaction results in
	 * StandbyStatusException.
	 * 
	 * 5) Promotes PDP and verifies that startTransaction is once again
	 * successful.
	 */
	@Ignore
	@Test
	public void testLocking1() throws Exception {
				
		PolicyLogger.debug("testLocking1: Reading IntegrityMonitorProperties");
		Properties integrityMonitorProperties = new Properties();
		integrityMonitorProperties.load(new FileInputStream(new File(
				"src/test/server/config/IntegrityMonitor.properties")));
		IntegrityMonitorProperties.initProperties(integrityMonitorProperties);
		String thisPdpId = IntegrityMonitorProperties
				.getProperty(IntegrityMonitorProperties.PDP_INSTANCE_ID);

		PolicyLogger.debug("testLocking1: Reading xacmlPersistenceProperties");
		Properties xacmlPersistenceProperties = new Properties();
		xacmlPersistenceProperties.load(new FileInputStream(new File(
				"src/test/server/config/xacmlPersistence.properties")));
		XacmlPersistenceProperties.initProperties(xacmlPersistenceProperties);
		
		PolicyLogger.debug("testLocking1: Creating emfXacml");
		EntityManagerFactory emfXacml = Persistence.createEntityManagerFactory(
				"junitXacmlPU", xacmlPersistenceProperties);
		
		PolicyLogger.debug("testLocking1: Reading droolsPersistenceProperties");
		Properties droolsPersistenceProperties = new Properties();
		droolsPersistenceProperties.load(new FileInputStream(new File(
				"src/test/server/config/droolsPersistence.properties")));
		DroolsPersistenceProperties.initProperties(droolsPersistenceProperties);

		PolicyLogger.debug("testLocking1: Creating emfDrools");
		EntityManagerFactory emfDrools = Persistence.createEntityManagerFactory(
				"junitDroolsPU", droolsPersistenceProperties);
		
		DroolsPdpsConnector conn = new JpaDroolsPdpsConnector(emfDrools);
		
		PolicyLogger.debug("testLocking1: Cleaning up tables");
		conn.deleteAllSessions();
		conn.deleteAllPdps();
		
		/*
		 * Insert this PDP as designated.  Initial standby state will be 
		 * either null or cold standby.   
		 */
		PolicyLogger.debug("testLocking1: Inserting PDP=" + thisPdpId + " as designated");
		DroolsPdpImpl pdp = new DroolsPdpImpl(thisPdpId, true, 4, new Date());
		conn.insertPdp(pdp);
		DroolsPdpEntity droolsPdpEntity = conn.getPdp(thisPdpId);
		PolicyLogger.debug("testLocking1: After insertion, PDP=" + thisPdpId + " has DESIGNATED="
				+ droolsPdpEntity.isDesignated());
		assertTrue(droolsPdpEntity.isDesignated() == true);
		
		PolicyLogger.debug("testLocking1: Instantiating stateManagement object");
		StateManagement sm = new StateManagement(emfXacml, "dummy");
		sm.deleteAllStateManagementEntities();
		sm = new StateManagement(emfXacml, thisPdpId);
		PMStandbyStateChangeNotifier pmStandbyStateChangeNotifier = new PMStandbyStateChangeNotifier();
		sm.addObserver(pmStandbyStateChangeNotifier);
				
		PolicyLogger.debug("testLocking1: Running policy-management.Main class, designated="
				+ conn.getPdp(thisPdpId).isDesignated());
		PolicyManagementRunner policyManagementRunner = new PolicyManagementRunner();
		policyManagementRunner.start();
		
		PolicyLogger.debug("testLocking1: Runner started; Sleeping "
				+ interruptRecoveryTime + "ms before promoting PDP="
				+ thisPdpId);
		Thread.sleep(interruptRecoveryTime);

		PolicyLogger.debug("testLocking1: Promoting PDP=" + thisPdpId);
		sm.promote();

		PolicyLogger.debug("testLocking1: Sleeping "
				+ sleepTime
				+ "ms, to allow time for policy-management.Main class to come up, designated="
				+ conn.getPdp(thisPdpId).isDesignated());
		Thread.sleep(sleepTime);
		
		PolicyLogger.debug("testLocking1: Waking up and invoking startTransaction on active PDP="
				+ thisPdpId
				+ ", designated="
				+ conn.getPdp(thisPdpId).isDesignated());
		DroolsPDPIntegrityMonitor droolsPdpIntegrityMonitor = (DroolsPDPIntegrityMonitor) IntegrityMonitor
				.getInstance();
		try {
			droolsPdpIntegrityMonitor.startTransaction();
			droolsPdpIntegrityMonitor.endTransaction();
			PolicyLogger.debug("testLocking1: As expected, transaction successful");
		} catch (AdministrativeStateException e) {
			PolicyLogger.error("testLocking1: Unexpectedly caught AdministrativeStateException, message=" + e.getMessage());
			assertTrue(false);
		} catch (StandbyStatusException e) {
			PolicyLogger.error("testLocking1: Unexpectedly caught StandbyStatusException, message=" + e.getMessage());
			assertTrue(false);
		} catch (Exception e) {
			PolicyLogger.error("testLocking1: Unexpectedly caught Exception, message=" + e.getMessage());
			assertTrue(false);
		}
		
		// demoting should cause state to transit to hotstandby, followed by re-promotion,
		// since there is only one PDP.
		PolicyLogger.debug("testLocking1: demoting PDP=" + thisPdpId);
		sm = droolsPdpIntegrityMonitor.getStateManager();
		sm.demote();
		
		PolicyLogger.debug("testLocking1: sleeping" + electionWaitSleepTime
				+ " to allow election handler to re-promote PDP=" + thisPdpId);
		Thread.sleep(electionWaitSleepTime);
								
		PolicyLogger.debug("testLocking1: Invoking startTransaction on re-promoted PDP="
				+ thisPdpId
				+ ", designated="
				+ conn.getPdp(thisPdpId).isDesignated());
		try {
			droolsPdpIntegrityMonitor.startTransaction();
			droolsPdpIntegrityMonitor.endTransaction();
			PolicyLogger.debug("testLocking1: As expected, transaction successful");
		} catch (AdministrativeStateException e) {
			PolicyLogger.error("testLocking1: Unexpectedly caught AdministrativeStateException, message=" + e.getMessage());
			assertTrue(false);
		} catch (StandbyStatusException e) {
			PolicyLogger.error("testLocking1: Unexpectedly caught StandbyStatusException, message=" + e.getMessage());
			assertTrue(false);
		} catch (Exception e) {
			PolicyLogger.error("testLocking1: Unexpectedly caught Exception, message=" + e.getMessage());
			assertTrue(false);
		}
		
		// locking should cause state to transit to cold standby
		PolicyLogger.debug("testLocking1: locking PDP=" + thisPdpId);
		sm.lock();
		
		// Just to avoid any race conditions, sleep a little after locking
		PolicyLogger.debug("testLocking1: Sleeping a few millis after locking, to avoid race condition");
		Thread.sleep(100);
		
		PolicyLogger.debug("testLocking1: Invoking startTransaction on locked PDP="
				+ thisPdpId
				+ ", designated="
				+ conn.getPdp(thisPdpId).isDesignated());
		try {
			droolsPdpIntegrityMonitor.startTransaction();
			PolicyLogger.error("testLocking1: startTransaction unexpectedly successful");
			assertTrue(false);
		} catch (AdministrativeStateException e) {
			PolicyLogger.debug("testLocking1: As expected, caught AdministrativeStateException, message=" + e.getMessage());
		} catch (StandbyStatusException e) {
			PolicyLogger.error("testLocking1: Unexpectedly caught StandbyStatusException, message=" + e.getMessage());
			assertTrue(false);
		} catch (Exception e) {
			PolicyLogger.error("testLocking1: Unexpectedly caught Exception, message=" + e.getMessage());
			assertTrue(false);
		} finally {
			droolsPdpIntegrityMonitor.endTransaction();
		}		
		
		// unlocking should cause state to transit to hot standby
		PolicyLogger.debug("testLocking1: unlocking PDP=" + thisPdpId);
		sm.unlock();
		
		// Just to avoid any race conditions, sleep a little after locking
		PolicyLogger.debug("testLocking1: Sleeping a few millis after unlocking, to avoid race condition");
		Thread.sleep(100);
		
		PolicyLogger.debug("testLocking1: Invoking startTransaction on unlocked PDP="
				+ thisPdpId
				+ ", designated="
				+ conn.getPdp(thisPdpId).isDesignated());
		try {
			droolsPdpIntegrityMonitor.startTransaction();
			PolicyLogger.error("testLocking1: startTransaction unexpectedly successful");
			assertTrue(false);
		} catch (AdministrativeStateException e) {
			PolicyLogger.error("testLocking1: Unexpectedly caught AdministrativeStateException, message=" + e.getMessage());
			assertTrue(false);
		} catch (StandbyStatusException e) {
			PolicyLogger.debug("testLocking1: As expected, caught StandbyStatusException, message=" + e.getMessage());
		} catch (Exception e) {
			PolicyLogger.error("testLocking1: Unexpectedly caught Exception, message=" + e.getMessage());
			assertTrue(false);
		} finally {
			droolsPdpIntegrityMonitor.endTransaction();
		}
		
		// promoting should cause state to transit to providing service
		PolicyLogger.debug("testLocking1: promoting PDP=" + thisPdpId);
		sm.promote();
		
		// Just to avoid any race conditions, sleep a little after promoting
		PolicyLogger.debug("testLocking1: Sleeping a few millis after promoting, to avoid race condition");
		Thread.sleep(100);
		
		PolicyLogger.debug("testLocking1: Invoking startTransaction on promoted PDP="
				+ thisPdpId
				+ ", designated="
				+ conn.getPdp(thisPdpId).isDesignated());
		try {
			droolsPdpIntegrityMonitor.startTransaction();
			droolsPdpIntegrityMonitor.endTransaction();
			PolicyLogger.debug("testLocking1: As expected, transaction successful");
		} catch (AdministrativeStateException e) {
			PolicyLogger.error("testLocking1: Unexpectedly caught AdministrativeStateException, message=" + e.getMessage());
			assertTrue(false);
		} catch (StandbyStatusException e) {
			PolicyLogger.error("testLocking1: Unexpectedly caught StandbyStatusException, message=" + e.getMessage());
			assertTrue(false);
		} catch (Exception e) {
			PolicyLogger.error("testLocking1: Unexpectedly caught Exception, message=" + e.getMessage());
			assertTrue(false);
		}
		
		PolicyLogger.debug("testLocking1: Stopping policyManagementRunner");
		policyManagementRunner.stopRunner();		

		PolicyLogger.debug("\n\ntestLocking1: Exiting\n\n");
		Thread.sleep(interruptRecoveryTime);

	}
	
	/*
	 * 1) Inserts and designates this PDP, then verifies that startTransaction
	 * is successful.
	 * 
	 * 2) Inserts another PDP in hotstandby.
	 * 
	 * 3) Demotes this PDP, and verifies 1) that other PDP is not promoted (because one
	 * PDP cannot promote another PDP) and 2) that this PDP is re-promoted.
	 */
	@Ignore
	@Test
	public void testLocking2() throws Exception {

		PolicyLogger.debug("\n\ntestLocking2: Entering\n\n");
		
		PolicyLogger.debug("testLocking2: Reading IntegrityMonitorProperties");
		Properties integrityMonitorProperties = new Properties();
		integrityMonitorProperties.load(new FileInputStream(new File(
				"src/test/server/config/IntegrityMonitor.properties")));
		IntegrityMonitorProperties.initProperties(integrityMonitorProperties);
		String thisPdpId = IntegrityMonitorProperties
				.getProperty(IntegrityMonitorProperties.PDP_INSTANCE_ID);

		PolicyLogger.debug("testLocking2: Reading xacmlPersistenceProperties");
		Properties xacmlPersistenceProperties = new Properties();
		xacmlPersistenceProperties.load(new FileInputStream(new File(
				"src/test/server/config/xacmlPersistence.properties")));
		XacmlPersistenceProperties.initProperties(xacmlPersistenceProperties);
		
		PolicyLogger.debug("testLocking2: Creating emfXacml");
		EntityManagerFactory emfXacml = Persistence.createEntityManagerFactory(
				"junitXacmlPU", xacmlPersistenceProperties);
		
		PolicyLogger.debug("testLocking2: Reading droolsPersistenceProperties");
		Properties droolsPersistenceProperties = new Properties();
		droolsPersistenceProperties.load(new FileInputStream(new File(
				"src/test/server/config/droolsPersistence.properties")));
		DroolsPersistenceProperties.initProperties(droolsPersistenceProperties);

		PolicyLogger.debug("testLocking2: Creating emfDrools");
		EntityManagerFactory emfDrools = Persistence.createEntityManagerFactory(
				"junitDroolsPU", droolsPersistenceProperties);
		
		DroolsPdpsConnector conn = new JpaDroolsPdpsConnector(emfDrools);
		
		PolicyLogger.debug("testLocking2: Cleaning up tables");
		conn.deleteAllSessions();
		conn.deleteAllPdps();
		
		/*
		 * Insert this PDP as designated.  Initial standby state will be 
		 * either null or cold standby.   Demoting should transit state to
		 * hot standby.
		 */
		PolicyLogger.debug("testLocking2: Inserting PDP=" + thisPdpId + " as designated");
		DroolsPdpImpl pdp = new DroolsPdpImpl(thisPdpId, true, 3, new Date());
		conn.insertPdp(pdp);
		DroolsPdpEntity droolsPdpEntity = conn.getPdp(thisPdpId);
		PolicyLogger.debug("testLocking2: After insertion, PDP=" + thisPdpId + " has DESIGNATED="
				+ droolsPdpEntity.isDesignated());
		assertTrue(droolsPdpEntity.isDesignated() == true);
		
		PolicyLogger.debug("testLocking2: Instantiating stateManagement object and promoting PDP=" + thisPdpId);
		StateManagement sm = new StateManagement(emfXacml, "dummy");
		sm.deleteAllStateManagementEntities();
		sm = new StateManagement(emfXacml, thisPdpId);
		PMStandbyStateChangeNotifier pmStandbyStateChangeNotifier = new PMStandbyStateChangeNotifier();
		sm.addObserver(pmStandbyStateChangeNotifier);
				
		/*
		 * Insert another PDP as not designated.  Initial standby state will be 
		 * either null or cold standby.   Demoting should transit state to
		 * hot standby.
		 */
		String standbyPdpId = "pdp2";
		PolicyLogger.debug("testLocking2: Inserting PDP=" + standbyPdpId + " as not designated");
		Date yesterday = DateUtils.addDays(new Date(), -1);
		pdp = new DroolsPdpImpl(standbyPdpId, false, 4, yesterday);
		conn.insertPdp(pdp);
		droolsPdpEntity = conn.getPdp(standbyPdpId);
		PolicyLogger.debug("testLocking2: After insertion, PDP=" + standbyPdpId + " has DESIGNATED="
				+ droolsPdpEntity.isDesignated());
		assertTrue(droolsPdpEntity.isDesignated() == false);
		
		PolicyLogger.debug("testLocking2: Demoting PDP=" + standbyPdpId);
		StateManagement sm2 = new StateManagement(emfXacml, standbyPdpId);
		sm2.addObserver(pmStandbyStateChangeNotifier);
				
		PolicyLogger.debug("testLocking2: Running policy-management.Main class");
		PolicyManagementRunner policyManagementRunner = new PolicyManagementRunner();
		policyManagementRunner.start();
		
		PolicyLogger.debug("testLocking2: Runner started; Sleeping "
				+ interruptRecoveryTime + "ms before promoting/demoting");
		Thread.sleep(interruptRecoveryTime);

		PolicyLogger.debug("testLocking2: Promoting PDP=" + thisPdpId);
		sm.promote();

		// demoting PDP should ensure that state transits to hotstandby
		PolicyLogger.debug("testLocking2: Demoting PDP=" + standbyPdpId);
		sm2.demote();
		
		PolicyLogger.debug("testLocking2: Sleeping "
				+ sleepTime
				+ "ms, to allow time for policy-management.Main class to come up");
		Thread.sleep(sleepTime);
		
		PolicyLogger.debug("testLocking2: Waking up and invoking startTransaction on active PDP="
				+ thisPdpId
				+ ", designated="
				+ conn.getPdp(thisPdpId).isDesignated());
		DroolsPDPIntegrityMonitor droolsPdpIntegrityMonitor = (DroolsPDPIntegrityMonitor) IntegrityMonitor
				.getInstance();
		try {
			droolsPdpIntegrityMonitor.startTransaction();
			droolsPdpIntegrityMonitor.endTransaction();
			PolicyLogger.debug("testLocking2: As expected, transaction successful");
		} catch (AdministrativeStateException e) {
			PolicyLogger.error("testLocking2: Unexpectedly caught AdministrativeStateException, message=" + e.getMessage());
			assertTrue(false);
		} catch (StandbyStatusException e) {
			PolicyLogger.error("testLocking2: Unexpectedly caught StandbyStatusException, message=" + e.getMessage());
			assertTrue(false);
		} catch (Exception e) {
			PolicyLogger.error("testLocking2: Unexpectedly caught Exception, message=" + e.getMessage());
			assertTrue(false);
		}
		
		// demoting should cause state to transit to hotstandby followed by re-promotion.
		PolicyLogger.debug("testLocking2: demoting PDP=" + thisPdpId);
		sm = droolsPdpIntegrityMonitor.getStateManager();
		sm.demote();
		
		PolicyLogger.debug("testLocking2: sleeping" + electionWaitSleepTime
				+ " to allow election handler to re-promote PDP=" + thisPdpId);
		Thread.sleep(electionWaitSleepTime);
		
		PolicyLogger.debug("testLocking2: Waking up and invoking startTransaction on re-promoted PDP="
				+ thisPdpId + ", designated="
				+ conn.getPdp(thisPdpId).isDesignated());
		try {
			droolsPdpIntegrityMonitor.startTransaction();
			droolsPdpIntegrityMonitor.endTransaction();
			PolicyLogger.debug("testLocking2: As expected, transaction successful");
		} catch (AdministrativeStateException e) {
			PolicyLogger.error("testLocking2: Unexpectedly caught AdministrativeStateException, message=" + e.getMessage());
			assertTrue(false);
		} catch (StandbyStatusException e) {
			PolicyLogger.error("testLocking2: Unexpectedly caught StandbyStatusException, message=" + e.getMessage());
			assertTrue(false);
		} catch (Exception e) {
			PolicyLogger.error("testLocking2: Unexpectedly caught Exception, message=" + e.getMessage());
			assertTrue(false);
		}
		
		PolicyLogger.debug("testLocking2: Verifying designated status for PDP="
				+ standbyPdpId);
		boolean standbyPdpDesignated = conn.getPdp(standbyPdpId).isDesignated();
		assertTrue(standbyPdpDesignated == false);
		
		PolicyLogger.debug("testLocking2: Stopping policyManagementRunner");
		policyManagementRunner.stopRunner();		

		PolicyLogger.debug("\n\ntestLocking2: Exiting\n\n");
		Thread.sleep(interruptRecoveryTime);

	}
	
	private class PolicyManagementRunner extends Thread {

		public void run() {
			PolicyLogger.debug("PolicyManagementRunner.run: Entering");
			String args[] = { "src/main/server/config" };
			try {
				Main.main(args);
			} catch (Exception e) {
				PolicyLogger
						.debug("PolicyManagementRunner.run: Exception thrown from Main.main(), message="
								+ e.getMessage());
			}
			PolicyLogger.debug("PolicyManagementRunner.run: Exiting");
		}
		
		public void stopRunner() {
			PolicyEngine.manager.shutdown();
		}

	}
	
}
