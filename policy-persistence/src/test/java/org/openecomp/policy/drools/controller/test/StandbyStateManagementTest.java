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
import java.util.ArrayList;
import java.util.Collection;
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
import org.openecomp.policy.drools.persistence.DroolsPdpsElectionHandler;
import org.openecomp.policy.drools.persistence.JpaDroolsPdpsConnector;
import org.openecomp.policy.drools.persistence.DroolsPersistenceProperties;
import org.openecomp.policy.drools.persistence.XacmlPersistenceProperties;
import org.openecomp.policy.drools.system.Main;
import org.openecomp.policy.drools.system.PolicyEngine;
import org.apache.commons.lang3.time.DateUtils;

/*
 * All JUnits are designed to run in the local development environment
 * where they have write privileges and can execute time-sensitive
 * tasks.
 * 
 * These tests can be run as JUnits, but there is some issue with running them
 * as part of a "mvn install" build.  Also, they take a very long time to run
 * due to many real time breaks.  Consequently, they are marked as @Ignore and
 * only run from the desktop.
 * 
 */
public class StandbyStateManagementTest {
			
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
	
	private static EntityManagerFactory emfx;
	private static EntityManagerFactory emfd;
	private static EntityManager emx;
	private static EntityManager emd;
	private static EntityTransaction et;

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
		//Create teh data access for xaml db
		Properties xacmlPersistenceProperties = new Properties();
		xacmlPersistenceProperties.load(new FileInputStream(new File(
				"src/test/server/config/xacmlPersistence.properties")));

		emfx = Persistence.createEntityManagerFactory("junitXacmlPU", xacmlPersistenceProperties);

		// Create an entity manager to use the DB
		emx = emfx.createEntityManager();
		
		//Create the data access for drools db
		Properties droolsPersistenceProperties = new Properties();
		droolsPersistenceProperties.load(new FileInputStream(new File(
				"src/test/server/config/droolsPersistence.properties")));

		emfd = Persistence.createEntityManagerFactory("junitDroolsPU", droolsPersistenceProperties);

		// Create an entity manager to use the DB
		emd = emfd.createEntityManager();
	}

	@After
	public void tearDown() throws Exception {
				
	}
	
	public void cleanXacmlDb(){
		et = emx.getTransaction();
		
		et.begin();
		// Make sure we leave the DB clean
		emx.createQuery("DELETE FROM StateManagementEntity").executeUpdate();
		emx.createQuery("DELETE FROM ResourceRegistrationEntity").executeUpdate();
		emx.createQuery("DELETE FROM ForwardProgressEntity").executeUpdate();
		emx.createQuery("DELETE FROM IntegrityAuditEntity").executeUpdate();
		emx.flush();
		et.commit();
	}
	
	public void cleanDroolsDb(){
		et = emd.getTransaction();
		
		et.begin();
		// Make sure we leave the DB clean
		emd.createQuery("DELETE FROM DroolsPdpEntity").executeUpdate();
		emd.createQuery("DELETE FROM DroolsSessionEntity").executeUpdate();
		emd.createQuery("DELETE FROM SessionInfo").executeUpdate();
		emd.createQuery("DELETE FROM WorkItemInfo").executeUpdate();
		emd.createQuery("DELETE FROM IntegrityAuditEntity").executeUpdate();
		emd.flush();
		et.commit();
	}
	
	@Ignore
	@Test
	public void runAllTests() throws Exception {
		//testColdStandby();
		//testHotStandby1();
		//testHotStandby2();
		//testLocking1();
		//testLocking2();
		testSanitizeDesignatedList();
		testComputeMostRecentPrimary();
		testComputeDesignatedPdp();
	}
	
	//@Ignore
	//@Test
	public void testSanitizeDesignatedList() throws Exception {

		PolicyLogger.debug("\n\ntestSanitizeDesignatedList: Entering\n\n");
		
		/*
		 * Get a DroolsPdpsConnector
		 */
		PolicyLogger.debug("testSanitizeDesignatedList: Reading droolsPersistenceProperties");
		Properties droolsPersistenceProperties = new Properties();
		droolsPersistenceProperties.load(new FileInputStream(new File(
				"src/test/server/config/droolsPersistence.properties")));
		DroolsPersistenceProperties.initProperties(droolsPersistenceProperties);

		PolicyLogger.debug("testSanitizeDesignatedList: Creating emfDrools");
		EntityManagerFactory emfDrools = Persistence.createEntityManagerFactory(
				"junitDroolsPU", droolsPersistenceProperties);
		
		DroolsPdpsConnector droolsPdpsConnector = new JpaDroolsPdpsConnector(emfDrools);
		
		/*
		 * Create 4 pdpd all not designated
		 */
		DroolsPdp pdp1 = new DroolsPdpImpl("pdp1", false, 4, new Date());
		DroolsPdp pdp2 = new DroolsPdpImpl("pdp2", false, 4, new Date());
		DroolsPdp pdp3 = new DroolsPdpImpl("pdp3", false, 4, new Date());
		DroolsPdp pdp4 = new DroolsPdpImpl("pdp4", false, 4, new Date());
		
		ArrayList<DroolsPdp> listOfDesignated = new ArrayList<DroolsPdp>();
		listOfDesignated.add(pdp1);
		listOfDesignated.add(pdp2);
		listOfDesignated.add(pdp3);
		listOfDesignated.add(pdp4);
		
		DroolsPDPIntegrityMonitor droolsPDPIntegrityMonitor;
		try{
			droolsPDPIntegrityMonitor = DroolsPDPIntegrityMonitor.init("src/test/server/config");
		}catch(Exception e){
			//If it already exists, just get it
			droolsPDPIntegrityMonitor = DroolsPDPIntegrityMonitor.getInstance();
		}
		DroolsPdpsElectionHandler droolsPdpsElectionHandler =  new DroolsPdpsElectionHandler(droolsPdpsConnector, pdp1, droolsPDPIntegrityMonitor);
		
		listOfDesignated = droolsPdpsElectionHandler.santizeDesignatedList(listOfDesignated);
		
		PolicyLogger.debug("\n\ntestSanitizeDesignatedList: listOfDesignated.size = " + listOfDesignated.size() + "\n\n");
		
		assertTrue(listOfDesignated.size()==4);
		
		/*
		 * Now make 2 designated
		 */
		pdp1.setDesignated(true);
		pdp2.setDesignated(true);
		
		listOfDesignated = droolsPdpsElectionHandler.santizeDesignatedList(listOfDesignated);
		
		PolicyLogger.debug("\n\ntestSanitizeDesignatedList: listOfDesignated.size after 2 designated = " + listOfDesignated.size() + "\n\n");
		
		assertTrue(listOfDesignated.size()==2);
		assertTrue(listOfDesignated.contains(pdp1));
		assertTrue(listOfDesignated.contains(pdp2));
		
		/*
		 * Now all are designated.  But, we have to add back the previously non-designated nodes
		 */
		pdp3.setDesignated(true);
		pdp4.setDesignated(true);
		listOfDesignated.add(pdp3);
		listOfDesignated.add(pdp4);
		
		listOfDesignated = droolsPdpsElectionHandler.santizeDesignatedList(listOfDesignated);
		
		PolicyLogger.debug("\n\ntestSanitizeDesignatedList: listOfDesignated.size after all designated = " + listOfDesignated.size() + "\n\n");
		
		assertTrue(listOfDesignated.size()==4);
		
	}
	
	//@Ignore
	//@Test
	public void testComputeMostRecentPrimary() throws Exception {

		PolicyLogger.debug("\n\ntestComputeMostRecentPrimary: Entering\n\n");
		
		/*
		 * Get a DroolsPdpsConnector
		 */
		PolicyLogger.debug("testComputeMostRecentPrimary: Reading droolsPersistenceProperties");
		Properties droolsPersistenceProperties = new Properties();
		droolsPersistenceProperties.load(new FileInputStream(new File(
				"src/test/server/config/droolsPersistence.properties")));
		DroolsPersistenceProperties.initProperties(droolsPersistenceProperties);

		PolicyLogger.debug("testComputeMostRecentPrimary: Creating emfDrools");
		EntityManagerFactory emfDrools = Persistence.createEntityManagerFactory(
				"junitDroolsPU", droolsPersistenceProperties);
		
		DroolsPdpsConnector droolsPdpsConnector = new JpaDroolsPdpsConnector(emfDrools);
		
		/*
		 * Create 4 pdpd all not designated
		 */
		long designatedDateMS = new Date().getTime();
		DroolsPdp pdp1 = new DroolsPdpImpl("pdp1", false, 4, new Date());
		pdp1.setDesignatedDate(new Date(designatedDateMS - 2));
		
		DroolsPdp pdp2 = new DroolsPdpImpl("pdp2", false, 4, new Date());
		//oldest
		pdp2.setDesignatedDate(new Date(designatedDateMS - 3));
		
		DroolsPdp pdp3 = new DroolsPdpImpl("pdp3", false, 4, new Date());
		pdp3.setDesignatedDate(new Date(designatedDateMS - 1));

		DroolsPdp pdp4 = new DroolsPdpImpl("pdp4", false, 4, new Date());
		//most recent
		pdp4.setDesignatedDate(new Date(designatedDateMS));
		
		ArrayList<DroolsPdp> listOfAllPdps = new ArrayList<DroolsPdp>();
		listOfAllPdps.add(pdp1);
		listOfAllPdps.add(pdp2);
		listOfAllPdps.add(pdp3);
		listOfAllPdps.add(pdp4);
				
		
		ArrayList<DroolsPdp> listOfDesignated = new ArrayList<DroolsPdp>();
		listOfDesignated.add(pdp1);
		listOfDesignated.add(pdp2);
		listOfDesignated.add(pdp3);
		listOfDesignated.add(pdp4);
		
		
		/*
		 * Because the way we sanitize the listOfDesignated, it will always contain all hot standby 
		 * or all designated members.
		 */
		DroolsPDPIntegrityMonitor droolsPDPIntegrityMonitor;
		try{
			droolsPDPIntegrityMonitor = DroolsPDPIntegrityMonitor.init("src/test/server/config");
		}catch(Exception e){
			//If it already exists, just get it
			droolsPDPIntegrityMonitor = DroolsPDPIntegrityMonitor.getInstance();
		}
		DroolsPdpsElectionHandler droolsPdpsElectionHandler =  new DroolsPdpsElectionHandler(droolsPdpsConnector, pdp1, droolsPDPIntegrityMonitor);
	
		DroolsPdp mostRecentPrimary = droolsPdpsElectionHandler.computeMostRecentPrimary(listOfAllPdps, listOfDesignated);
		
		PolicyLogger.debug("\n\ntestComputeMostRecentPrimary: mostRecentPrimary.getPdpId() = " + mostRecentPrimary.getPdpId() + "\n\n");
		
		/*
		 * If all of the pdps are included in the listOfDesignated and none are designated, it will choose 
		 * the one which has the most recent designated date.
		 */
		assertTrue(mostRecentPrimary.getPdpId().equals("pdp4"));
		
		/*
		 * Now let's designate all of those on the listOfDesignated.  It will choose the first one designated
		 */
		pdp1.setDesignated(true);
		pdp2.setDesignated(true);
		pdp3.setDesignated(true);
		pdp4.setDesignated(true);
		
		mostRecentPrimary = droolsPdpsElectionHandler.computeMostRecentPrimary(listOfAllPdps, listOfDesignated);
		
		PolicyLogger.debug("\n\ntestComputeMostRecentPrimary: All designated all on list, mostRecentPrimary.getPdpId() = " + mostRecentPrimary.getPdpId() + "\n\n");
		
		/*
		 * If all of the pdps are included in the listOfDesignated and all are designated, it will choose 
		 * the one which was designated first
		 */
		assertTrue(mostRecentPrimary.getPdpId().equals("pdp2"));
		
		/*
		 * Now we will designate only 2 and put just them in the listOfDesignated.  The algorithm will now
		 * look for the most recently designated pdp which is not currently designated.
		 */
		pdp3.setDesignated(false);
		pdp4.setDesignated(false);
		
		listOfDesignated.remove(pdp3);
		listOfDesignated.remove(pdp4);
		
		mostRecentPrimary = droolsPdpsElectionHandler.computeMostRecentPrimary(listOfAllPdps, listOfDesignated);
		
		PolicyLogger.debug("\n\ntestComputeMostRecentPrimary: mostRecentPrimary.getPdpId() = " + mostRecentPrimary.getPdpId() + "\n\n");
		
		assertTrue(mostRecentPrimary.getPdpId().equals("pdp4"));
		
		
		/*
		 * Now we will have none designated and put two of them in the listOfDesignated.  The algorithm will now
		 * look for the most recently designated pdp regardless of whether it is currently marked as designated.
		 */
		pdp1.setDesignated(false);
		pdp2.setDesignated(false);
		
		mostRecentPrimary = droolsPdpsElectionHandler.computeMostRecentPrimary(listOfAllPdps, listOfDesignated);
		
		PolicyLogger.debug("\n\ntestComputeMostRecentPrimary: 2 on list mostRecentPrimary.getPdpId() = " + mostRecentPrimary.getPdpId() + "\n\n");
		
		assertTrue(mostRecentPrimary.getPdpId().equals("pdp4"));
		
		/*
		 * If we have only one pdp on in the listOfDesignated, the most recently designated pdp will be chosen, regardless
		 * of its designation status
		 */
		listOfDesignated.remove(pdp1);
		
		mostRecentPrimary = droolsPdpsElectionHandler.computeMostRecentPrimary(listOfAllPdps, listOfDesignated);
		
		PolicyLogger.debug("\n\ntestComputeMostRecentPrimary: 1 on list mostRecentPrimary.getPdpId() = " + mostRecentPrimary.getPdpId() + "\n\n");
		
		assertTrue(mostRecentPrimary.getPdpId().equals("pdp4"));
		
		/*
		 * Finally, if none are on the listOfDesignated, it will again choose the most recently designated pdp.
		 */
		listOfDesignated.remove(pdp2);

		mostRecentPrimary = droolsPdpsElectionHandler.computeMostRecentPrimary(listOfAllPdps, listOfDesignated);
		
		PolicyLogger.debug("\n\ntestComputeMostRecentPrimary: 0 on list mostRecentPrimary.getPdpId() = " + mostRecentPrimary.getPdpId() + "\n\n");
		
		assertTrue(mostRecentPrimary.getPdpId().equals("pdp4"));
		
	}
	
	//@Ignore
	//@Test
	public void testComputeDesignatedPdp() throws Exception{
		
		PolicyLogger.debug("\n\ntestComputeDesignatedPdp: Entering\n\n");
		
		/*
		 * Get a DroolsPdpsConnector
		 */
		PolicyLogger.debug("testComputeDesignatedPdp: Reading droolsPersistenceProperties");
		Properties droolsPersistenceProperties = new Properties();
		droolsPersistenceProperties.load(new FileInputStream(new File(
				"src/test/server/config/droolsPersistence.properties")));
		DroolsPersistenceProperties.initProperties(droolsPersistenceProperties);

		PolicyLogger.debug("testComputeDesignatedPdp: Creating emfDrools");
		EntityManagerFactory emfDrools = Persistence.createEntityManagerFactory(
				"junitDroolsPU", droolsPersistenceProperties);
		
		DroolsPdpsConnector droolsPdpsConnector = new JpaDroolsPdpsConnector(emfDrools);
		
		/*
		 * Create 4 pdpd all not designated.  Two on site1. Two on site2
		 */
		long designatedDateMS = new Date().getTime();
		DroolsPdp pdp1 = new DroolsPdpImpl("pdp1", false, 4, new Date());
		pdp1.setDesignatedDate(new Date(designatedDateMS - 2));
		pdp1.setSiteName("site1");
		
		DroolsPdp pdp2 = new DroolsPdpImpl("pdp2", false, 4, new Date());
		pdp2.setDesignatedDate(new Date(designatedDateMS - 3));
		pdp2.setSiteName("site1");

		//oldest		
		DroolsPdp pdp3 = new DroolsPdpImpl("pdp3", false, 4, new Date());
		pdp3.setDesignatedDate(new Date(designatedDateMS - 4));
		pdp3.setSiteName("site2");
		
		DroolsPdp pdp4 = new DroolsPdpImpl("pdp4", false, 4, new Date());
		//most recent
		pdp4.setDesignatedDate(new Date(designatedDateMS));
		pdp4.setSiteName("site2");
		
		ArrayList<DroolsPdp> listOfAllPdps = new ArrayList<DroolsPdp>();
		listOfAllPdps.add(pdp1);
		listOfAllPdps.add(pdp2);
		listOfAllPdps.add(pdp3);
		listOfAllPdps.add(pdp4);
				
		
		ArrayList<DroolsPdp> listOfDesignated = new ArrayList<DroolsPdp>();
		
		/*
		 * We will first test an empty listOfDesignated. As we know from the previous JUnit,
		 * the pdp with the most designated date will be chosen for mostRecentPrimary  
		 */
		
		DroolsPDPIntegrityMonitor droolsPDPIntegrityMonitor;
		try{
			droolsPDPIntegrityMonitor = DroolsPDPIntegrityMonitor.init("src/test/server/config");
		}catch(Exception e){
			//If it already exists, just get it
			droolsPDPIntegrityMonitor = DroolsPDPIntegrityMonitor.getInstance();
		}
		DroolsPdpsElectionHandler droolsPdpsElectionHandler =  new DroolsPdpsElectionHandler(droolsPdpsConnector, pdp1, droolsPDPIntegrityMonitor);
		
		DroolsPdp mostRecentPrimary = pdp4;
	
		DroolsPdp designatedPdp = droolsPdpsElectionHandler.computeDesignatedPdp(listOfDesignated, mostRecentPrimary);
		
		/*
		 * The designatedPdp should be null
		 */
		assertTrue(designatedPdp==null);
		
		/*
		 * Now let's try having only one pdp in listOfDesignated, but not in the same site as the most recent primary
		 */
		
		listOfDesignated.add(pdp2);
		
		designatedPdp = droolsPdpsElectionHandler.computeDesignatedPdp(listOfDesignated, mostRecentPrimary);
		
		/*
		 * Now the designatedPdp should be the one and only selection in the listOfDesignated
		 */
		assertTrue(designatedPdp.getPdpId().equals(pdp2.getPdpId()));
		
		/*
		 * Now let's put 2 pdps in the listOfDesignated, neither in the same site as the mostRecentPrimary
		 */
		listOfDesignated.add(pdp1);
		
		designatedPdp = droolsPdpsElectionHandler.computeDesignatedPdp(listOfDesignated, mostRecentPrimary);
		
		/*
		 * The designatedPdp should now be the one with the lowest lexiographic score - pdp1
		 */
		assertTrue(designatedPdp.getPdpId().equals(pdp1.getPdpId()));
		
		/*
		 * Finally, we will have 2 pdps in the listOfDesignated, one in the same site with the mostRecentPrimary
		 */
		listOfDesignated.remove(pdp1);
		listOfDesignated.add(pdp3);
		
		designatedPdp = droolsPdpsElectionHandler.computeDesignatedPdp(listOfDesignated, mostRecentPrimary);
		
		/*
		 * The designatedPdp should now be the one on the same site as the mostRecentPrimary
		 */
		assertTrue(designatedPdp.getPdpId().equals(pdp3.getPdpId()));
	}
	
	
	//@Ignore
	//@Test
	public void testColdStandby() throws Exception {

		PolicyLogger.debug("\n\ntestColdStandby: Entering\n\n");
		cleanXacmlDb();
		cleanDroolsDb();

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
		//policyManagementRunner.stopRunner();
	
		PolicyLogger.debug("\n\ntestColdStandby: Exiting\n\n");
		Thread.sleep(interruptRecoveryTime);

	}
	
	/*
	 * Tests hot standby when there is only one PDP.
	 */
	//@Ignore
	//@Test
	public void testHotStandby1() throws Exception {
	
		PolicyLogger.debug("\n\ntestHotStandby1: Entering\n\n");
		cleanXacmlDb();
		cleanDroolsDb();
		
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
		//policyManagementRunner.stopRunner();		
	
		PolicyLogger.debug("\n\ntestHotStandby1: Exiting\n\n");
		Thread.sleep(interruptRecoveryTime);

	}

	/*
	 * Tests hot standby when two PDPs are involved.
	 */
	//@Ignore
	//@Test
	public void testHotStandby2() throws Exception {

		PolicyLogger.info("\n\ntestHotStandby2: Entering\n\n");
		cleanXacmlDb();
		cleanDroolsDb();
		
		PolicyLogger.info("testHotStandby2: Reading IntegrityMonitorProperties");
		Properties integrityMonitorProperties = new Properties();
		integrityMonitorProperties.load(new FileInputStream(new File(
				"src/test/server/config/IntegrityMonitor.properties")));
		IntegrityMonitorProperties.initProperties(integrityMonitorProperties);
		String thisPdpId = IntegrityMonitorProperties
				.getProperty(IntegrityMonitorProperties.PDP_INSTANCE_ID);
		
		PolicyLogger.info("testHotStandby2: Reading xacmlPersistenceProperties");
		Properties xacmlPersistenceProperties = new Properties();
		xacmlPersistenceProperties.load(new FileInputStream(new File(
				"src/test/server/config/xacmlPersistence.properties")));
		XacmlPersistenceProperties.initProperties(xacmlPersistenceProperties);
		
		PolicyLogger.info("testHotStandby2: Creating emfXacml");
		EntityManagerFactory emfXacml = Persistence.createEntityManagerFactory(
				"junitXacmlPU", xacmlPersistenceProperties);
		
		PolicyLogger.info("testHotStandby2: Reading droolsPersistenceProperties");
		Properties droolsPersistenceProperties = new Properties();
		droolsPersistenceProperties.load(new FileInputStream(new File(
				"src/test/server/config/droolsPersistence.properties")));
		DroolsPersistenceProperties.initProperties(droolsPersistenceProperties);

		PolicyLogger.info("testHotStandby2: Creating emfDrools");
		EntityManagerFactory emfDrools = Persistence.createEntityManagerFactory(
				"junitDroolsPU", droolsPersistenceProperties);
		
		DroolsPdpsConnector conn = new JpaDroolsPdpsConnector(emfDrools);
		
		PolicyLogger.info("testHotStandby2: Cleaning up tables");
		conn.deleteAllSessions();
		conn.deleteAllPdps();
		
		/*
		 * Insert a PDP that's designated but not current.
		 */
		String activePdpId = "pdp2";
		PolicyLogger.info("testHotStandby2: Inserting PDP=" + activePdpId + " as stale, designated PDP");
		Date yesterday = DateUtils.addDays(new Date(), -1);
		DroolsPdp pdp = new DroolsPdpImpl(activePdpId, true, 4, yesterday);
		conn.insertPdp(pdp);
		DroolsPdpEntity droolsPdpEntity = conn.getPdp(activePdpId);
		PolicyLogger.info("testHotStandby2: After insertion, PDP=" + activePdpId + ", which is not current, has DESIGNATED="
				+ droolsPdpEntity.isDesignated());
		assertTrue(droolsPdpEntity.isDesignated() == true);
		
		/*
		 * Promote the designated PDP.
		 * 
		 * We have a chicken and egg problem here: we need a StateManagement
		 * object to invoke the deleteAllStateManagementEntities method.
		 */
		PolicyLogger.info("testHotStandy2: Promoting PDP=" + activePdpId);
		StateManagement sm = new StateManagement(emfXacml, "dummy");
		sm.deleteAllStateManagementEntities();
		sm = new StateManagement(emfXacml, activePdpId);//pdp2
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
		PolicyLogger.info("testHotStandby2: Inserting PDP=" + thisPdpId + " as not designated");
		pdp = new DroolsPdpImpl(thisPdpId, false, 4, yesterday);
		conn.insertPdp(pdp);
		droolsPdpEntity = conn.getPdp(thisPdpId);
		PolicyLogger.info("testHotStandby2: After insertion, PDP=" + thisPdpId + " has DESIGNATED="
				+ droolsPdpEntity.isDesignated());
		assertTrue(droolsPdpEntity.isDesignated() == false);
		
		PolicyLogger.info("testHotStandby2: Demoting PDP=" + thisPdpId);//pdp1
		StateManagement sm2 = new StateManagement(emfXacml, thisPdpId);
		sm2.addObserver(pmStandbyStateChangeNotifier);
		
		PolicyLogger.info("testHotStandby2: Running policy-management.Main class");
		PolicyManagementRunner policyManagementRunner = new PolicyManagementRunner(); //pdp1
		policyManagementRunner.start();
		
		PolicyLogger.info("testHotStandby2: Runner started; Sleeping "
				+ interruptRecoveryTime + "ms before promoting/demoting");
		Thread.sleep(interruptRecoveryTime);

		PolicyLogger.info("testHotStandby2: Runner started; promoting PDP=" + activePdpId);//pdpd2xs
		//at this point, the newly created pdp will have set the state to disabled/failed/cold standby
		//because it is stale. So, it cannot be promoted.  We need to call sm.enableNotFailed() so we
		//can promote it and demote the other pdp - else the other pdp will just spring back to providingservice
		sm.enableNotFailed();//pdp1
		sm.promote();
		String standbyStatus = sm.getStandbyStatus(activePdpId);
		PolicyLogger.info("testHotStandby2: After promoting, PDP=" + activePdpId + " has standbyStatus="
				+ standbyStatus);
		
		// demoting PDP should ensure that state transits to hotstandby
		PolicyLogger.info("testHotStandby2: Runner started; demoting PDP=" + thisPdpId);
		sm2.demote();//pdp1
		standbyStatus = sm.getStandbyStatus(thisPdpId);
		PolicyLogger.info("testHotStandby2: After demoting, PDP=" + thisPdpId + " has standbyStatus="
				+ standbyStatus);
		
		PolicyLogger.info("testHotStandby2: Sleeping "
				+ sleepTime
				+ "ms, to allow JpaDroolsPdpsConnector time to check droolspdpentity table");
		Thread.sleep(sleepTime);
		
		/*
		 * Verify that this PDP, demoted to HOT_STANDBY, is now
		 * re-designated and providing service.
		 */
		droolsPdpEntity = conn.getPdp(thisPdpId);
		PolicyLogger.info("testHotStandby2: After demoting PDP=" + activePdpId
				+ ", DESIGNATED=" + droolsPdpEntity.isDesignated()
				+ " for PDP=" + thisPdpId);
		assertTrue(droolsPdpEntity.isDesignated() == true);
		standbyStatus = sm2.getStandbyStatus(thisPdpId);
		PolicyLogger.info("testHotStandby2: After demoting PDP=" + activePdpId
				+ ", PDP=" + thisPdpId + " has standbyStatus=" + standbyStatus);
		assertTrue(standbyStatus != null
				&& standbyStatus.equals(StateManagement.PROVIDING_SERVICE));
				
		PolicyLogger.info("testHotStandby2: Stopping policyManagementRunner");
		//policyManagementRunner.stopRunner();		

		PolicyLogger.info("\n\ntestHotStandby2: Exiting\n\n");
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
	//@Ignore
	//@Test
	public void testLocking1() throws Exception {
		PolicyLogger.debug("testLocking1: Entry");
		cleanXacmlDb();
		cleanDroolsDb();		
		
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
		
		// unlocking should cause state to transit to hot standby and then providing service
		PolicyLogger.debug("testLocking1: unlocking PDP=" + thisPdpId);
		sm.unlock();
		
		// Just to avoid any race conditions, sleep a little after locking
		PolicyLogger.debug("testLocking1: Sleeping a few millis after unlocking, to avoid race condition");
		Thread.sleep(electionWaitSleepTime);
		
		PolicyLogger.debug("testLocking1: Invoking startTransaction on unlocked PDP="
				+ thisPdpId
				+ ", designated="
				+ conn.getPdp(thisPdpId).isDesignated());
		try {
			droolsPdpIntegrityMonitor.startTransaction();
			PolicyLogger.error("testLocking1: startTransaction successful as expected");
		} catch (AdministrativeStateException e) {
			PolicyLogger.error("testLocking1: Unexpectedly caught AdministrativeStateException, message=" + e.getMessage());
			assertTrue(false);
		} catch (StandbyStatusException e) {
			PolicyLogger.debug("testLocking1: Unexpectedly caught StandbyStatusException, message=" + e.getMessage());
			assertTrue(false);
		} catch (Exception e) {
			PolicyLogger.error("testLocking1: Unexpectedly caught Exception, message=" + e.getMessage());
			assertTrue(false);
		} finally {
			droolsPdpIntegrityMonitor.endTransaction();
		}
		
		// demoting should cause state to transit to providing service
		PolicyLogger.debug("testLocking1: demoting PDP=" + thisPdpId);
		sm.demote();
		
		// Just to avoid any race conditions, sleep a little after promoting
		PolicyLogger.debug("testLocking1: Sleeping a few millis after demoting, to avoid race condition");
		Thread.sleep(100);
		
		PolicyLogger.debug("testLocking1: Invoking startTransaction on demoted PDP="
				+ thisPdpId
				+ ", designated="
				+ conn.getPdp(thisPdpId).isDesignated());
		try {
			droolsPdpIntegrityMonitor.startTransaction();
			droolsPdpIntegrityMonitor.endTransaction();
			PolicyLogger.debug("testLocking1: Unexpectedly, transaction successful");
			assertTrue(false);
		} catch (AdministrativeStateException e) {
			PolicyLogger.error("testLocking1: Unexpectedly caught AdministrativeStateException, message=" + e.getMessage());
			assertTrue(false);
		} catch (StandbyStatusException e) {
			PolicyLogger.error("testLocking1: As expected caught StandbyStatusException, message=" + e.getMessage());
		} catch (Exception e) {
			PolicyLogger.error("testLocking1: Unexpectedly caught Exception, message=" + e.getMessage());
			assertTrue(false);
		}
		
		PolicyLogger.debug("testLocking1: Stopping policyManagementRunner");
		//policyManagementRunner.stopRunner();		

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
	//@Ignore
	//@Test
	public void testLocking2() throws Exception {

		PolicyLogger.debug("\n\ntestLocking2: Entering\n\n");
		cleanXacmlDb();
		cleanDroolsDb();		
		
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
		//policyManagementRunner.stopRunner();		

		PolicyLogger.debug("\n\ntestLocking2: Exiting\n\n");
		Thread.sleep(interruptRecoveryTime);

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
				return;
			}
			PolicyLogger.info("PolicyManagementRunner.run: Exiting");
		}
		
		public void stopRunner() {
			PolicyEngine.manager.shutdown();
		}

	}
	
}
