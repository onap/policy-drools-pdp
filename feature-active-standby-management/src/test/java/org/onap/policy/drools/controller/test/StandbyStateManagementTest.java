/*
 * ============LICENSE_START=======================================================
 * feature-active-standby-management
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

package org.onap.policy.drools.controller.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Properties;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;

import org.apache.commons.lang3.time.DateUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.onap.policy.common.im.AdministrativeStateException;
import org.onap.policy.common.im.IntegrityMonitor;
import org.onap.policy.common.im.StandbyStatusException;
import org.onap.policy.common.im.StateManagement;
import org.onap.policy.drools.activestandby.ActiveStandbyFeatureAPI;
import org.onap.policy.drools.activestandby.ActiveStandbyProperties;
import org.onap.policy.drools.activestandby.DroolsPdp;
import org.onap.policy.drools.activestandby.DroolsPdpEntity;
import org.onap.policy.drools.activestandby.DroolsPdpImpl;
import org.onap.policy.drools.activestandby.DroolsPdpsConnector;
import org.onap.policy.drools.activestandby.DroolsPdpsElectionHandler;
import org.onap.policy.drools.activestandby.JpaDroolsPdpsConnector;
import org.onap.policy.drools.activestandby.PMStandbyStateChangeNotifier;
import org.onap.policy.drools.core.PolicySessionFeatureAPI;
import org.onap.policy.drools.statemanagement.StateManagementFeatureAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * All JUnits are designed to run in the local development environment
 * where they have write privileges and can execute time-sensitive
 * tasks.
 * 
 * These tests can be run as JUnits, but there is some issue with running them
 * as part of a "mvn install" build.  Also, they take a very long time to run
 * due to many real time breaks.  Consequently, they are marked as @Ignore and
 * only run from the desktop.
 */ 
 
public class StandbyStateManagementTest {
	private static final Logger  logger = LoggerFactory.getLogger(StandbyStateManagementTest.class);
	/*
	 * Currently, the DroolsPdpsElectionHandler.DesignationWaiter is invoked every 1 seconds, starting 
	 * at the start of the next multiple of pdpUpdateInterval, but with a minimum of 5 sec cushion
	 * to ensure that we wait for the DesignationWaiter to do its job, before 
	 * checking the results. Add a few seconds for safety
	 */ 
	 
	long sleepTime = 10000;
	
	/*
	 * DroolsPdpsElectionHandler runs every 1 seconds, so a 6 second sleep should be 
	 * plenty to ensure it has time to re-promote this PDP.
	 */
	 
	long electionWaitSleepTime = 6000;
	
	/*
	 * Sleep 1 seconds after each test to allow interrupt (shutdown) recovery.
	 */
	 
	long interruptRecoveryTime = 5000;
	
	private static EntityManagerFactory emfx;
	private static EntityManagerFactory emfd;
	private static EntityManager emx;
	private static EntityManager emd;
	private static EntityTransaction et;
	
	private final String configDir = "src/test/resources";

	/*
	 * See the IntegrityMonitor.getJmxUrl() method for the rationale behind this jmx related processing.
	 */
	 
	@BeforeClass
	public static void setUpClass() throws Exception {
		
		String userDir = System.getProperty("user.dir");
		logger.debug("setUpClass: userDir={}", userDir);
		System.setProperty("com.sun.management.jmxremote.port", "9980");
		System.setProperty("com.sun.management.jmxremote.authenticate","false");
				
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
		//Create teh data access for xaml db
		Properties stateManagementProperties = new Properties();
		stateManagementProperties.load(new FileInputStream(new File(
				"src/test/resources/feature-state-management.properties")));

		emfx = Persistence.createEntityManagerFactory("junitXacmlPU", stateManagementProperties);

		// Create an entity manager to use the DB
		emx = emfx.createEntityManager();
		
		//Create the data access for drools db
		Properties activeStandbyProperties = new Properties();
		activeStandbyProperties.load(new FileInputStream(new File(
				"src/test/resources/feature-active-standby-management.properties")));

		emfd = Persistence.createEntityManagerFactory("junitDroolsPU", activeStandbyProperties);

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
		emx.flush();
		et.commit();
	}
	
	public void cleanDroolsDb(){
		et = emd.getTransaction();
		
		et.begin();
		// Make sure we leave the DB clean
		emd.createQuery("DELETE FROM DroolsPdpEntity").executeUpdate();
		emd.flush();
		et.commit();
	}
	
	/*
	 * These JUnit tests must be run one at a time in an eclipse environment 
	 * by right-clicking StandbyStateManagementTest and selecting 
	 * "Run As" -> "JUnit Test". 
	 * 
	 * They will run successfully when you run all of them under runAllTests(), 
	 * however, you will get all sorts of non-fatal errors in the log and on the 
	 * console that result from overlapping threads that are not terminated at the 
	 * end of each test. The problem is that the JUnit environment does not terminate 
	 * all the test threads between tests. This is true even if you break each JUnit
	 * into a separate file. Consequently, all the tests would have to be refactored 
	 * so all test object initializations are coordinated.  In other words, you 
	 * retrieve the ActiveStandbyFeature instance and other class instances only once 
	 * at the beginning of the JUnits and then reuse them throughout the tests. 
	 * Initialization of the state of the objects is pretty straight forward as it
	 * just amounts to manipulating the entries in StateManagementEntity and 
	 * DroolsPdpEntity tables. However, some thought needs to be given to how to
	 * "pause" the processing in ActiveStandbyFeature class.  I think we could "pause"
	 * it by calling globalInit() which will, I think, restart it. So long as it
	 * does not create a new instance, it will force it to go through an initialization
	 * cycle which includes a "pause" at the beginning of proecessing.  We just must
	 * be sure it does not create another instance - which may mean we need to add
	 * a factory interface instead of calling the constructor directly.
	 */

	
	//@Ignore
	@Test
	public void runAllTests() throws Exception {
		testColdStandby();
		testHotStandby1();
		testHotStandby2();
		testLocking1();
		testLocking2(); 
		testPMStandbyStateChangeNotifier();
		testSanitizeDesignatedList();
		testComputeMostRecentPrimary();
		testComputeDesignatedPdp();
	}
	
	//@Ignore
	//@Test
	public void testPMStandbyStateChangeNotifier() throws Exception {
		logger.debug("\n\ntestPMStandbyStateChangeNotifier: Entering\n\n");
		cleanXacmlDb();
		
		logger.debug("testPMStandbyStateChangeNotifier: Reading activeStandbyProperties");
		
		Properties activeStandbyProperties = new Properties();
		activeStandbyProperties.load(new FileInputStream(new File(
				configDir + "/feature-active-standby-management.properties")));
		ActiveStandbyProperties.initProperties(activeStandbyProperties);
		String thisPdpId = ActiveStandbyProperties.getProperty(ActiveStandbyProperties.NODE_NAME);
		
		logger.debug("testPMStandbyStateChangeNotifier: Getting StateManagementFeatureAPI");

		StateManagementFeatureAPI sm = null;
		for (StateManagementFeatureAPI feature : StateManagementFeatureAPI.impl.getList())
		{
			((PolicySessionFeatureAPI) feature).globalInit(null, configDir);
			sm = feature;
			logger.debug("testPMStandbyStateChangeNotifier stateManagementFeature.getResourceName(): {}", sm.getResourceName());
			break;
		}
		if(sm == null){
			logger.error("testPMStandbyStateChangeNotifier failed to initialize.  "
					+ "Unable to get instance of StateManagementFeatureAPI "
					+ "with resourceID: {}", thisPdpId);
			logger.debug("testPMStandbyStateChangeNotifier failed to initialize.  "
					+ "Unable to get instance of StateManagementFeatureAPI "
					+ "with resourceID: {}", thisPdpId);
		}

		//Create an instance of the Observer
		PMStandbyStateChangeNotifier pmNotifier = new PMStandbyStateChangeNotifier();

		//Register the PMStandbyStateChangeNotifier Observer
		sm.addObserver(pmNotifier);
	
		//At this point the standbystatus = 'null'
		sm.lock();
		assertTrue(pmNotifier.getPreviousStandbyStatus().equals(StateManagement.NULL_VALUE));

		sm.unlock();
		assertTrue(pmNotifier.getPreviousStandbyStatus().equals(StateManagement.NULL_VALUE));

		//Adding standbystatus=hotstandby
		sm.demote();
		System.out.println(pmNotifier.getPreviousStandbyStatus());
		assertTrue(pmNotifier.getPreviousStandbyStatus().equals(PMStandbyStateChangeNotifier.HOTSTANDBY_OR_COLDSTANDBY));

		//Now making standbystatus=coldstandby
		sm.lock();
		assertTrue(pmNotifier.getPreviousStandbyStatus().equals(PMStandbyStateChangeNotifier.HOTSTANDBY_OR_COLDSTANDBY));

		//standbystatus = hotstandby
		sm.unlock();
		assertTrue(pmNotifier.getPreviousStandbyStatus().equals(PMStandbyStateChangeNotifier.HOTSTANDBY_OR_COLDSTANDBY));

		//standbystatus = providingservice
		sm.promote();
		//The previousStandbyStatus is not updated until after the delay activation expires
		assertTrue(pmNotifier.getPreviousStandbyStatus().equals(PMStandbyStateChangeNotifier.HOTSTANDBY_OR_COLDSTANDBY));

		//Sleep long enough for the delayActivationTimer to run
		sleep(5000);
		assertTrue(pmNotifier.getPreviousStandbyStatus().equals(StateManagement.PROVIDING_SERVICE));

		//standbystatus = providingservice
		sm.promote();
		assertTrue(pmNotifier.getPreviousStandbyStatus().equals(StateManagement.PROVIDING_SERVICE));
		
		//standbystatus = coldstandby
		sm.lock();
		assertTrue(pmNotifier.getPreviousStandbyStatus().equals(PMStandbyStateChangeNotifier.HOTSTANDBY_OR_COLDSTANDBY));

		//standbystatus = hotstandby
		sm.unlock();
		assertTrue(pmNotifier.getPreviousStandbyStatus().equals(PMStandbyStateChangeNotifier.HOTSTANDBY_OR_COLDSTANDBY));

		//standbystatus = hotstandby
		sm.demote();
		assertTrue(pmNotifier.getPreviousStandbyStatus().equals(PMStandbyStateChangeNotifier.HOTSTANDBY_OR_COLDSTANDBY));
	}

	//@Ignore
	//@Test
	public void testSanitizeDesignatedList() throws Exception {

		logger.debug("\n\ntestSanitizeDesignatedList: Entering\n\n");
		
		// Get a DroolsPdpsConnector
		
		logger.debug("testSanitizeDesignatedList: Reading activeStandbyProperties");
		Properties activeStandbyProperties = new Properties();
		activeStandbyProperties.load(new FileInputStream(new File(
				configDir + "/feature-active-standby-management.properties")));
		String thisPdpId = activeStandbyProperties
				.getProperty(ActiveStandbyProperties.NODE_NAME);

		logger.debug("testSanitizeDesignatedList: Creating emfDrools");
		EntityManagerFactory emfDrools = Persistence.createEntityManagerFactory(
				"junitDroolsPU", activeStandbyProperties);
		
		DroolsPdpsConnector droolsPdpsConnector = new JpaDroolsPdpsConnector(emfDrools);
		
		// Create 4 pdpd all not designated
		
		DroolsPdp pdp1 = new DroolsPdpImpl("pdp1", false, 4, new Date());
		DroolsPdp pdp2 = new DroolsPdpImpl("pdp2", false, 4, new Date());
		DroolsPdp pdp3 = new DroolsPdpImpl("pdp3", false, 4, new Date());
		DroolsPdp pdp4 = new DroolsPdpImpl("pdp4", false, 4, new Date());
		
		ArrayList<DroolsPdp> listOfDesignated = new ArrayList<DroolsPdp>();
		listOfDesignated.add(pdp1);
		listOfDesignated.add(pdp2);
		listOfDesignated.add(pdp3);
		listOfDesignated.add(pdp4);
		
		// Now we want to create a StateManagementFeature and initialize it.  It will be
		// discovered by the ActiveStandbyFeature when the election handler initializes.

		StateManagementFeatureAPI stateManagementFeature = null;
		for (StateManagementFeatureAPI feature : StateManagementFeatureAPI.impl.getList())
		{
			((PolicySessionFeatureAPI) feature).globalInit(null, configDir);
			stateManagementFeature = feature;
			logger.debug("testColdStandby stateManagementFeature.getResourceName(): {}", stateManagementFeature.getResourceName());
			break;
		}
		if(stateManagementFeature == null){
			logger.error("testColdStandby failed to initialize.  "
					+ "Unable to get instance of StateManagementFeatureAPI "
					+ "with resourceID: {}", thisPdpId);
			logger.debug("testColdStandby failed to initialize.  "
					+ "Unable to get instance of StateManagementFeatureAPI "
					+ "with resourceID: {}", thisPdpId);
		}
		
		
		DroolsPdpsElectionHandler droolsPdpsElectionHandler =  new DroolsPdpsElectionHandler(droolsPdpsConnector, pdp1);
		
		listOfDesignated = droolsPdpsElectionHandler.santizeDesignatedList(listOfDesignated);
		
		logger.debug("\n\ntestSanitizeDesignatedList: listOfDesignated.size = {}\n\n",listOfDesignated.size());
		
		assertTrue(listOfDesignated.size()==4);
		
		// Now make 2 designated
		 
		pdp1.setDesignated(true);
		pdp2.setDesignated(true);
		
		listOfDesignated = droolsPdpsElectionHandler.santizeDesignatedList(listOfDesignated);
		
		logger.debug("\n\ntestSanitizeDesignatedList: listOfDesignated.size after 2 designated = {}\n\n", listOfDesignated.size());
		
		assertTrue(listOfDesignated.size()==2);
		assertTrue(listOfDesignated.contains(pdp1));
		assertTrue(listOfDesignated.contains(pdp2));
		
		
		// Now all are designated.  But, we have to add back the previously non-designated nodes
		 
		pdp3.setDesignated(true);
		pdp4.setDesignated(true);
		listOfDesignated.add(pdp3);
		listOfDesignated.add(pdp4);
		
		listOfDesignated = droolsPdpsElectionHandler.santizeDesignatedList(listOfDesignated);
		
		logger.debug("\n\ntestSanitizeDesignatedList: listOfDesignated.size after all designated = {}\n\n", listOfDesignated.size());
		
		assertTrue(listOfDesignated.size()==4);
		
	}


	//@Ignore
	//@Test
	public void testComputeMostRecentPrimary() throws Exception {

		logger.debug("\n\ntestComputeMostRecentPrimary: Entering\n\n");
		
		logger.debug("testComputeMostRecentPrimary: Reading activeStandbyProperties");
		Properties activeStandbyProperties = new Properties();
		activeStandbyProperties.load(new FileInputStream(new File(
				configDir + "/feature-active-standby-management.properties")));
		String thisPdpId = activeStandbyProperties
				.getProperty(ActiveStandbyProperties.NODE_NAME);

		logger.debug("testComputeMostRecentPrimary: Creating emfDrools");
		EntityManagerFactory emfDrools = Persistence.createEntityManagerFactory(
				"junitDroolsPU", activeStandbyProperties);
		
		DroolsPdpsConnector droolsPdpsConnector = new JpaDroolsPdpsConnector(emfDrools);
		
		
		// Create 4 pdpd all not designated
		 
		 
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
		
		// Because the way we sanitize the listOfDesignated, it will always contain all hot standby 
		// or all designated members.
		 
		// Now we want to create a StateManagementFeature and initialize it.  It will be
		// discovered by the ActiveStandbyFeature when the election handler initializes.

		StateManagementFeatureAPI stateManagementFeature = null;
		for (StateManagementFeatureAPI feature : StateManagementFeatureAPI.impl.getList())
		{
			((PolicySessionFeatureAPI) feature).globalInit(null, configDir);
			stateManagementFeature = feature;
			logger.debug("testComputeMostRecentPrimary stateManagementFeature.getResourceName(): {}", stateManagementFeature.getResourceName());
			break;
		}
		if(stateManagementFeature == null){
			logger.error("testComputeMostRecentPrimary failed to initialize.  "
					+ "Unable to get instance of StateManagementFeatureAPI "
					+ "with resourceID: {}", thisPdpId);
			logger.debug("testComputeMostRecentPrimary failed to initialize.  "
					+ "Unable to get instance of StateManagementFeatureAPI "
					+ "with resourceID: {}", thisPdpId);
		}
		
		DroolsPdpsElectionHandler droolsPdpsElectionHandler =  new DroolsPdpsElectionHandler(droolsPdpsConnector, pdp1);
	
		DroolsPdp mostRecentPrimary = droolsPdpsElectionHandler.computeMostRecentPrimary(listOfAllPdps, listOfDesignated);
		
		logger.debug("\n\ntestComputeMostRecentPrimary: mostRecentPrimary.getPdpId() = {}\n\n", mostRecentPrimary.getPdpId());
		
		
		// If all of the pdps are included in the listOfDesignated and none are designated, it will choose 
		// the one which has the most recent designated date.
		 
		 
		assertTrue(mostRecentPrimary.getPdpId().equals("pdp4"));
		
		
		// Now let's designate all of those on the listOfDesignated.  It will choose the first one designated
		 
		 
		pdp1.setDesignated(true);
		pdp2.setDesignated(true);
		pdp3.setDesignated(true);
		pdp4.setDesignated(true);
		
		mostRecentPrimary = droolsPdpsElectionHandler.computeMostRecentPrimary(listOfAllPdps, listOfDesignated);
		
		logger.debug("\n\ntestComputeMostRecentPrimary: All designated all on list, mostRecentPrimary.getPdpId() = {}\n\n", mostRecentPrimary.getPdpId());
		
		
		// If all of the pdps are included in the listOfDesignated and all are designated, it will choose 
		// the one which was designated first
		 
		 
		assertTrue(mostRecentPrimary.getPdpId().equals("pdp2"));
		
		
		// Now we will designate only 2 and put just them in the listOfDesignated.  The algorithm will now
		// look for the most recently designated pdp which is not currently designated.
		 
		 
		pdp3.setDesignated(false);
		pdp4.setDesignated(false);
		
		listOfDesignated.remove(pdp3);
		listOfDesignated.remove(pdp4);
		
		mostRecentPrimary = droolsPdpsElectionHandler.computeMostRecentPrimary(listOfAllPdps, listOfDesignated);
		
		logger.debug("\n\ntestComputeMostRecentPrimary: mostRecentPrimary.getPdpId() = {}\n\n", mostRecentPrimary.getPdpId());
		
		assertTrue(mostRecentPrimary.getPdpId().equals("pdp4"));
		
		
		
		// Now we will have none designated and put two of them in the listOfDesignated.  The algorithm will now
		// look for the most recently designated pdp regardless of whether it is currently marked as designated.
		 
		 
		pdp1.setDesignated(false);
		pdp2.setDesignated(false);
		
		mostRecentPrimary = droolsPdpsElectionHandler.computeMostRecentPrimary(listOfAllPdps, listOfDesignated);
		
		logger.debug("\n\ntestComputeMostRecentPrimary: 2 on list mostRecentPrimary.getPdpId() = {}\n\n", mostRecentPrimary.getPdpId());
		
		assertTrue(mostRecentPrimary.getPdpId().equals("pdp4"));
		
		
		// If we have only one pdp on in the listOfDesignated, the most recently designated pdp will be chosen, regardless
		// of its designation status
		 
		 
		listOfDesignated.remove(pdp1);
		
		mostRecentPrimary = droolsPdpsElectionHandler.computeMostRecentPrimary(listOfAllPdps, listOfDesignated);
		
		logger.debug("\n\ntestComputeMostRecentPrimary: 1 on list mostRecentPrimary.getPdpId() = {}\n\n", mostRecentPrimary.getPdpId());
		
		assertTrue(mostRecentPrimary.getPdpId().equals("pdp4"));
		
		
		// Finally, if none are on the listOfDesignated, it will again choose the most recently designated pdp.
		 
		 
		listOfDesignated.remove(pdp2);

		mostRecentPrimary = droolsPdpsElectionHandler.computeMostRecentPrimary(listOfAllPdps, listOfDesignated);
		
		logger.debug("\n\ntestComputeMostRecentPrimary: 0 on list mostRecentPrimary.getPdpId() = {}\n\n", mostRecentPrimary.getPdpId());
		
		assertTrue(mostRecentPrimary.getPdpId().equals("pdp4"));
		
	}

	//@Ignore
	//@Test
	public void testComputeDesignatedPdp() throws Exception{
		
		logger.debug("\n\ntestComputeDesignatedPdp: Entering\n\n");
		
		logger.debug("testComputeDesignatedPdp: Reading activeStandbyProperties");
		Properties activeStandbyProperties = new Properties();
		activeStandbyProperties.load(new FileInputStream(new File(
				configDir + "/feature-active-standby-management.properties")));
		String thisPdpId = activeStandbyProperties
				.getProperty(ActiveStandbyProperties.NODE_NAME);
		 

		logger.debug("testComputeDesignatedPdp: Creating emfDrools");
		EntityManagerFactory emfDrools = Persistence.createEntityManagerFactory(
				"junitDroolsPU", activeStandbyProperties);
		
		DroolsPdpsConnector droolsPdpsConnector = new JpaDroolsPdpsConnector(emfDrools);
		
		
		// Create 4 pdpd all not designated.  Two on site1. Two on site2
		 
		 
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
		
		
		// We will first test an empty listOfDesignated. As we know from the previous JUnit,
		// the pdp with the most designated date will be chosen for mostRecentPrimary
		
		// Now we want to create a StateManagementFeature and initialize it.  It will be
		// discovered by the ActiveStandbyFeature when the election handler initializes.

		StateManagementFeatureAPI stateManagementFeature = null;
		for (StateManagementFeatureAPI feature : StateManagementFeatureAPI.impl.getList())
		{
			((PolicySessionFeatureAPI) feature).globalInit(null, configDir);
			stateManagementFeature = feature;
			logger.debug("testComputeDesignatedPdp stateManagementFeature.getResourceName(): {}", stateManagementFeature.getResourceName());
			break;
		}
		if(stateManagementFeature == null){
			logger.error("testComputeDesignatedPdp failed to initialize.  "
					+ "Unable to get instance of StateManagementFeatureAPI "
					+ "with resourceID: {}", thisPdpId);
			logger.debug("testComputeDesignatedPdp failed to initialize.  "
					+ "Unable to get instance of StateManagementFeatureAPI "
					+ "with resourceID: {}", thisPdpId);
		}
		
	
		DroolsPdpsElectionHandler droolsPdpsElectionHandler =  new DroolsPdpsElectionHandler(droolsPdpsConnector, pdp1);
		
		DroolsPdp mostRecentPrimary = pdp4;
	
		DroolsPdp designatedPdp = droolsPdpsElectionHandler.computeDesignatedPdp(listOfDesignated, mostRecentPrimary);
		
		
		// The designatedPdp should be null
		 
		assertTrue(designatedPdp==null);
		
		
		// Now let's try having only one pdp in listOfDesignated, but not in the same site as the most recent primary
		 
		listOfDesignated.add(pdp2);
		
		designatedPdp = droolsPdpsElectionHandler.computeDesignatedPdp(listOfDesignated, mostRecentPrimary);
		
		
		// Now the designatedPdp should be the one and only selection in the listOfDesignated
		 
		 
		assertTrue(designatedPdp.getPdpId().equals(pdp2.getPdpId()));
		
		
		// Now let's put 2 pdps in the listOfDesignated, neither in the same site as the mostRecentPrimary
		 
		 
		listOfDesignated.add(pdp1);
		
		designatedPdp = droolsPdpsElectionHandler.computeDesignatedPdp(listOfDesignated, mostRecentPrimary);
		
		
		// The designatedPdp should now be the one with the lowest lexiographic score - pdp1
		 
		 
		assertTrue(designatedPdp.getPdpId().equals(pdp1.getPdpId()));
		
		
		// Finally, we will have 2 pdps in the listOfDesignated, one in the same site with the mostRecentPrimary
		 
		 
		listOfDesignated.remove(pdp1);
		listOfDesignated.add(pdp3);
		
		designatedPdp = droolsPdpsElectionHandler.computeDesignatedPdp(listOfDesignated, mostRecentPrimary);
		
		
		// The designatedPdp should now be the one on the same site as the mostRecentPrimary
		 
		 
		assertTrue(designatedPdp.getPdpId().equals(pdp3.getPdpId()));
	}
	
	//@Ignore
	//@Test
	public void testColdStandby() throws Exception {

		logger.debug("\n\ntestColdStandby: Entering\n\n");
		cleanXacmlDb();
		cleanDroolsDb();

		logger.debug("testColdStandby: Reading stateManagementProperties");
		Properties stateManagementProperties = new Properties();
		stateManagementProperties.load(new FileInputStream(new File(
				configDir + "/feature-state-management.properties")));
		
		logger.debug("testColdStandby: Creating emfXacml");
		EntityManagerFactory emfXacml = Persistence.createEntityManagerFactory(
				"junitXacmlPU", stateManagementProperties);
		
		logger.debug("testColdStandby: Reading activeStandbyProperties");
		Properties activeStandbyProperties = new Properties();
		activeStandbyProperties.load(new FileInputStream(new File(
				configDir + "/feature-active-standby-management.properties")));
		String thisPdpId = activeStandbyProperties.getProperty(ActiveStandbyProperties.NODE_NAME);

		logger.debug("testColdStandby: Creating emfDrools");
		EntityManagerFactory emfDrools = Persistence.createEntityManagerFactory(
				"junitDroolsPU", activeStandbyProperties);
		
		DroolsPdpsConnector conn = new JpaDroolsPdpsConnector(emfDrools);
		
		logger.debug("testColdStandby: Cleaning up tables");
		conn.deleteAllPdps();
	
		logger.debug("testColdStandby: Inserting PDP={} as designated", thisPdpId);
		DroolsPdp pdp = new DroolsPdpImpl(thisPdpId, true, 4, new Date());
		conn.insertPdp(pdp);
		DroolsPdpEntity droolsPdpEntity = conn.getPdp(thisPdpId);
		logger.debug("testColdStandby: After insertion, DESIGNATED= {} "
				+ "for PDP= {}", droolsPdpEntity.isDesignated(), thisPdpId);
		assertTrue(droolsPdpEntity.isDesignated() == true);

		/*
		 * When the Standby Status changes (from providingservice) to hotstandby
		 * or coldstandby,the Active/Standby selection algorithm must stand down
		 * if thePDP-D is currently the lead/active node and allow another PDP-D
		 * to take over.
		 * 
		 * It must also call lock on all engines in the engine management.
		 */
		
		
		/*
		 * Yes, this is kludgy, but we have a chicken and egg problem here: we
		 * need a StateManagement object to invoke the
		 * deleteAllStateManagementEntities method.
		 */
		logger.debug("testColdStandby: Instantiating stateManagement object");
		
		StateManagement sm = new StateManagement(emfXacml, "dummy");
		sm.deleteAllStateManagementEntities();
			
		// Now we want to create a StateManagementFeature and initialize it.  It will be
		// discovered by the ActiveStandbyFeature when the election handler initializes.

		StateManagementFeatureAPI smf = null;
		for (StateManagementFeatureAPI feature : StateManagementFeatureAPI.impl.getList())
		{
			((PolicySessionFeatureAPI) feature).globalInit(null, configDir);
			smf = feature;
			logger.debug("testColdStandby stateManagementFeature.getResourceName(): {}", smf.getResourceName());
			break;
		}
		if(smf == null){
			logger.error("testColdStandby failed to initialize.  "
					+ "Unable to get instance of StateManagementFeatureAPI "
					+ "with resourceID: {}", thisPdpId);
			logger.debug("testColdStandby failed to initialize.  "
					+ "Unable to get instance of StateManagementFeatureAPI "
					+ "with resourceID: {}", thisPdpId);
		}
		
		// Create an ActiveStandbyFeature and initialize it. It will discover the StateManagementFeature
		// that has been created.
		ActiveStandbyFeatureAPI activeStandbyFeature = null;
		for (ActiveStandbyFeatureAPI feature : ActiveStandbyFeatureAPI.impl.getList())
		{
			((PolicySessionFeatureAPI) feature).globalInit(null, configDir);
			activeStandbyFeature = feature;
			logger.debug("testColdStandby activeStandbyFeature.getResourceName(): {}", activeStandbyFeature.getResourceName());
			break;
		}
		if(activeStandbyFeature == null){
			logger.error("testColdStandby failed to initialize.  "
					+ "Unable to get instance of ActiveStandbyFeatureAPI "
					+ "with resourceID:{}", thisPdpId);
			logger.debug("testColdStandby failed to initialize.  "
					+ "Unable to get instance of ActiveStandbyFeatureAPI "
					+ "with resourceID:{}", thisPdpId);
		}

		// Artificially putting a PDP into service is really a two step process, 1)
		// inserting it as designated and 2) promoting it so that its standbyStatus
		// is providing service.
		
		logger.debug("testColdStandby: Runner started; Sleeping "
				+ interruptRecoveryTime + "ms before promoting PDP= {}",
				thisPdpId);
		sleep(interruptRecoveryTime);

		logger.debug("testColdStandby: Promoting PDP={}", thisPdpId);
		smf.promote();		
		
		String standbyStatus = sm.getStandbyStatus(thisPdpId);
		logger.debug("testColdStandby: Before locking, PDP= {}  has standbyStatus= {}",
				 thisPdpId, standbyStatus);
		
		logger.debug("testColdStandby: Locking smf");
		smf.lock();
		
		sleep(interruptRecoveryTime);
		
		// Verify that the PDP is no longer designated.
		 
		droolsPdpEntity = conn.getPdp(thisPdpId);
		logger.debug("testColdStandby: After lock sm.lock() invoked, "
				+ "DESIGNATED= {} for PDP={}", droolsPdpEntity.isDesignated(), thisPdpId);
		assertTrue(droolsPdpEntity.isDesignated() == false);
		
		logger.debug("\n\ntestColdStandby: Exiting\n\n");
		sleep(interruptRecoveryTime);

	}

	// Tests hot standby when there is only one PDP.
	 
	//@Ignore
	//@Test
	public void testHotStandby1() throws Exception {
	
		logger.debug("\n\ntestHotStandby1: Entering\n\n");
		cleanXacmlDb();
		cleanDroolsDb();
		
		logger.debug("testHotStandby1: Reading stateManagementProperties");
		Properties stateManagementProperties = new Properties();
		stateManagementProperties.load(new FileInputStream(new File(
				configDir + "/feature-state-management.properties")));

		logger.debug("testHotStandby1: Creating emfXacml");
		EntityManagerFactory emfXacml = Persistence.createEntityManagerFactory(
				"junitXacmlPU", stateManagementProperties);
		
		logger.debug("testHotStandby1: Reading activeStandbyProperties");
		Properties activeStandbyProperties = new Properties();
		activeStandbyProperties.load(new FileInputStream(new File(
				configDir + "/feature-active-standby-management.properties")));
		String thisPdpId = activeStandbyProperties
				.getProperty(ActiveStandbyProperties.NODE_NAME);

		logger.debug("testHotStandby1: Creating emfDrools");
		EntityManagerFactory emfDrools = Persistence.createEntityManagerFactory(
				"junitDroolsPU", activeStandbyProperties);
		
		DroolsPdpsConnector conn = new JpaDroolsPdpsConnector(emfDrools);
		
		logger.debug("testHotStandby1: Cleaning up tables");
		conn.deleteAllPdps();
					
		/*
		 * Insert this PDP as not designated.  Initial standby state will be 
		 * either null or cold standby.   Demoting should transit state to
		 * hot standby.
		 */
		 
		logger.debug("testHotStandby1: Inserting PDP={} as not designated", thisPdpId);
		Date yesterday = DateUtils.addDays(new Date(), -1);
		DroolsPdpImpl pdp = new DroolsPdpImpl(thisPdpId, false, 4, yesterday);
		conn.insertPdp(pdp);
		DroolsPdpEntity droolsPdpEntity = conn.getPdp(thisPdpId);
		logger.debug("testHotStandby1: After insertion, PDP={} has DESIGNATED={}",
				thisPdpId, droolsPdpEntity.isDesignated());
		assertTrue(droolsPdpEntity.isDesignated() == false);
		
		logger.debug("testHotStandby1: Instantiating stateManagement object");
		StateManagement sm = new StateManagement(emfXacml, "dummy");
		sm.deleteAllStateManagementEntities();
		
		
		// Now we want to create a StateManagementFeature and initialize it.  It will be
		// discovered by the ActiveStandbyFeature when the election handler initializes.

		StateManagementFeatureAPI smf = null;
		for (StateManagementFeatureAPI feature : StateManagementFeatureAPI.impl.getList())
		{
			((PolicySessionFeatureAPI) feature).globalInit(null, configDir);
			smf = feature;
			logger.debug("testHotStandby1 stateManagementFeature.getResourceName(): {}", smf.getResourceName());
			break;
		}
		if(smf == null){
			logger.error("testHotStandby1 failed to initialize.  "
					+ "Unable to get instance of StateManagementFeatureAPI "
					+ "with resourceID: {}", thisPdpId);
			logger.debug("testHotStandby1 failed to initialize.  "
					+ "Unable to get instance of StateManagementFeatureAPI "
					+ "with resourceID: {}", thisPdpId);
		}
		
		// Create an ActiveStandbyFeature and initialize it. It will discover the StateManagementFeature
		// that has been created.
		ActiveStandbyFeatureAPI activeStandbyFeature = null;
		for (ActiveStandbyFeatureAPI feature : ActiveStandbyFeatureAPI.impl.getList())
		{
			((PolicySessionFeatureAPI) feature).globalInit(null, configDir);
			activeStandbyFeature = feature;
			logger.debug("testHotStandby1 activeStandbyFeature.getResourceName(): {}", activeStandbyFeature.getResourceName());
			break;
		}
		if(activeStandbyFeature == null){
			logger.error("testHotStandby1 failed to initialize.  "
					+ "Unable to get instance of ActiveStandbyFeatureAPI "
					+ "with resourceID: {}", thisPdpId);
			logger.debug("testHotStandby1 failed to initialize.  "
					+ "Unable to get instance of ActiveStandbyFeatureAPI "
					+ "with resourceID: {}", thisPdpId);
		}
		

		logger.debug("testHotStandby1: Demoting PDP={}", thisPdpId);
		// demoting should cause state to transit to hotstandby
		smf.demote();
		
				
		logger.debug("testHotStandby1: Sleeping {} ms, to allow JpaDroolsPdpsConnector "
				+ "time to check droolspdpentity table", sleepTime);
		sleep(sleepTime);
		
		
		// Verify that this formerly un-designated PDP in HOT_STANDBY is now designated and providing service.
		 
		droolsPdpEntity = conn.getPdp(thisPdpId);
		logger.debug("testHotStandby1: After sm.demote() invoked, DESIGNATED= {} "
				+ "for PDP= {}", droolsPdpEntity.isDesignated(), thisPdpId);
		assertTrue(droolsPdpEntity.isDesignated() == true);
		String standbyStatus = smf.getStandbyStatus(thisPdpId);
		logger.debug("testHotStandby1: After demotion, PDP= {} "
				+ "has standbyStatus= {}", thisPdpId, standbyStatus);
		assertTrue(standbyStatus != null  &&  standbyStatus.equals(StateManagement.PROVIDING_SERVICE));
				
		logger.debug("testHotStandby1: Stopping policyManagementRunner");
		//policyManagementRunner.stopRunner();		
	
		logger.debug("\n\ntestHotStandby1: Exiting\n\n");
		sleep(interruptRecoveryTime);

	}

	/*
	 * Tests hot standby when two PDPs are involved.
	 */
		 
	//@Ignore
	//@Test
	public void testHotStandby2() throws Exception {

		logger.info("\n\ntestHotStandby2: Entering\n\n");
		cleanXacmlDb();
		cleanDroolsDb();
		
		logger.info("testHotStandby2: Reading stateManagementProperties");
		Properties stateManagementProperties = new Properties();
		stateManagementProperties.load(new FileInputStream(new File(
				configDir + "/feature-state-management.properties")));
		
		logger.info("testHotStandby2: Creating emfXacml");
		EntityManagerFactory emfXacml = Persistence.createEntityManagerFactory(
				"junitXacmlPU", stateManagementProperties);
		
		logger.info("testHotStandby2: Reading activeStandbyProperties");
		Properties activeStandbyProperties = new Properties();
		activeStandbyProperties.load(new FileInputStream(new File(
				configDir + "/feature-active-standby-management.properties")));
		String thisPdpId = activeStandbyProperties
				.getProperty(ActiveStandbyProperties.NODE_NAME);

		logger.info("testHotStandby2: Creating emfDrools");
		EntityManagerFactory emfDrools = Persistence.createEntityManagerFactory(
				"junitDroolsPU", activeStandbyProperties);
		
		DroolsPdpsConnector conn = new JpaDroolsPdpsConnector(emfDrools);
		
		logger.info("testHotStandby2: Cleaning up tables");
		conn.deleteAllPdps();
		
		
		// Insert a PDP that's designated but not current.
		 
		String activePdpId = "pdp2";
		logger.info("testHotStandby2: Inserting PDP={} as stale, designated PDP", activePdpId);
		Date yesterday = DateUtils.addDays(new Date(), -1);
		DroolsPdp pdp = new DroolsPdpImpl(activePdpId, true, 4, yesterday);
		conn.insertPdp(pdp);
		DroolsPdpEntity droolsPdpEntity = conn.getPdp(activePdpId);
		logger.info("testHotStandby2: After insertion, PDP= {}, which is "
				+ "not current, has DESIGNATED= {}", activePdpId, droolsPdpEntity.isDesignated());
		assertTrue(droolsPdpEntity.isDesignated() == true);
		
		/*
		 * Promote the designated PDP.
		 * 
		 * We have a chicken and egg problem here: we need a StateManagement
		 * object to invoke the deleteAllStateManagementEntities method.
		 */
		 
		 
		logger.info("testHotStandby2: Promoting PDP={}", activePdpId);
		StateManagement sm = new StateManagement(emfXacml, "dummy");
		sm.deleteAllStateManagementEntities();
		
		
		sm = new StateManagement(emfXacml, activePdpId);//pdp2
		
		// Artificially putting a PDP into service is really a two step process, 1)
		// inserting it as designated and 2) promoting it so that its standbyStatus
		// is providing service.
				
		/*
		 * Insert this PDP as not designated.  Initial standby state will be 
		 * either null or cold standby.   Demoting should transit state to
		 * hot standby.
		 */
		 
		 
		logger.info("testHotStandby2: Inserting PDP= {} as not designated", thisPdpId);
		pdp = new DroolsPdpImpl(thisPdpId, false, 4, yesterday);
		conn.insertPdp(pdp);
		droolsPdpEntity = conn.getPdp(thisPdpId);
		logger.info("testHotStandby2: After insertion, PDP={} "
				+ "has DESIGNATED= {}", thisPdpId, droolsPdpEntity.isDesignated());
		assertTrue(droolsPdpEntity.isDesignated() == false);
		
		
		// Now we want to create a StateManagementFeature and initialize it.  It will be
		// discovered by the ActiveStandbyFeature when the election handler initializes.

		StateManagementFeatureAPI sm2 = null;
		for (StateManagementFeatureAPI feature : StateManagementFeatureAPI.impl.getList())
		{
			((PolicySessionFeatureAPI) feature).globalInit(null, configDir);
			sm2 = feature;
			logger.debug("testHotStandby2 stateManagementFeature.getResourceName(): {}", sm2.getResourceName());
			break;
		}
		if(sm2 == null){
			logger.error("testHotStandby2 failed to initialize.  "
					+ "Unable to get instance of StateManagementFeatureAPI "
					+ "with resourceID: {}", thisPdpId);
			logger.debug("testHotStandby2 failed to initialize.  "
					+ "Unable to get instance of StateManagementFeatureAPI "
					+ "with resourceID: {}", thisPdpId);
		}
		
		// Create an ActiveStandbyFeature and initialize it. It will discover the StateManagementFeature
		// that has been created.
		ActiveStandbyFeatureAPI activeStandbyFeature = null;
		for (ActiveStandbyFeatureAPI feature : ActiveStandbyFeatureAPI.impl.getList())
		{
			((PolicySessionFeatureAPI) feature).globalInit(null, configDir);
			activeStandbyFeature = feature;
			logger.debug("testHotStandby2 activeStandbyFeature.getResourceName(): {}", activeStandbyFeature.getResourceName());
			break;
		}
		if(activeStandbyFeature == null){
			logger.error("testHotStandby2 failed to initialize.  "
					+ "Unable to get instance of ActiveStandbyFeatureAPI "
					+ "with resourceID: {}", thisPdpId);
			logger.debug("testHotStandby2 failed to initialize.  "
					+ "Unable to get instance of ActiveStandbyFeatureAPI "
					+ "with resourceID: {}", thisPdpId);
		}
		
		logger.info("testHotStandby2: Runner started; Sleeping {} "
				+ "ms before promoting/demoting", interruptRecoveryTime);
		sleep(interruptRecoveryTime);

		logger.info("testHotStandby2: Runner started; promoting PDP={}", activePdpId);
		//At this point, the newly created pdp will have set the state to disabled/failed/cold standby
		//because it is stale. So, it cannot be promoted.  We need to call sm.enableNotFailed() so we
		//can promote it and demote the other pdp - else the other pdp will just spring back to providingservice
		sm.enableNotFailed();//pdp2
		sm.promote();
		String standbyStatus = sm.getStandbyStatus(activePdpId);
		logger.info("testHotStandby2: After promoting, PDP= {} has standbyStatus= {}", activePdpId, standbyStatus);
		
		// demoting PDP should ensure that state transits to hotstandby
		logger.info("testHotStandby2: Runner started; demoting PDP= {}", thisPdpId);
		sm2.demote();//pdp1
		standbyStatus = sm.getStandbyStatus(thisPdpId);
		logger.info("testHotStandby2: After demoting, PDP={} has standbyStatus= {}",thisPdpId , standbyStatus);
		
		logger.info("testHotStandby2: Sleeping {} ms, to allow JpaDroolsPdpsConnector "
				+ "time to check droolspdpentity table", sleepTime);
		sleep(sleepTime);
		
		/*
		 * Verify that this PDP, demoted to HOT_STANDBY, is now
		 * re-designated and providing service.
		 */
		 
		droolsPdpEntity = conn.getPdp(thisPdpId);
		logger.info("testHotStandby2: After demoting PDP={}"
				+ ", DESIGNATED= {}"
				+ " for PDP= {}", activePdpId, droolsPdpEntity.isDesignated(), thisPdpId);
		assertTrue(droolsPdpEntity.isDesignated() == true);
		standbyStatus = sm2.getStandbyStatus(thisPdpId);
		logger.info("testHotStandby2: After demoting PDP={}"
				+ ", PDP={} has standbyStatus= {}",
				activePdpId, thisPdpId, standbyStatus);
		assertTrue(standbyStatus != null
				&& standbyStatus.equals(StateManagement.PROVIDING_SERVICE));
				
		logger.info("testHotStandby2: Stopping policyManagementRunner");
		//policyManagementRunner.stopRunner();		

		logger.info("\n\ntestHotStandby2: Exiting\n\n");
		sleep(interruptRecoveryTime);

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
		logger.debug("testLocking1: Entry");
		cleanXacmlDb();
		cleanDroolsDb();		
		
		logger.debug("testLocking1: Reading stateManagementProperties");
		Properties stateManagementProperties = new Properties();
		stateManagementProperties.load(new FileInputStream(new File(
				configDir + "/feature-state-management.properties")));

		logger.debug("testLocking1: Creating emfXacml");
		EntityManagerFactory emfXacml = Persistence.createEntityManagerFactory(
				"junitXacmlPU", stateManagementProperties);
		
		logger.debug("testLocking1: Reading activeStandbyProperties");
		Properties activeStandbyProperties = new Properties();
		activeStandbyProperties.load(new FileInputStream(new File(
				configDir + "/feature-active-standby-management.properties")));
		String thisPdpId = activeStandbyProperties
				.getProperty(ActiveStandbyProperties.NODE_NAME);

		logger.debug("testLocking1: Creating emfDrools");
		EntityManagerFactory emfDrools = Persistence.createEntityManagerFactory(
				"junitDroolsPU", activeStandbyProperties);
		
		DroolsPdpsConnector conn = new JpaDroolsPdpsConnector(emfDrools);
		
		logger.debug("testLocking1: Cleaning up tables");
		conn.deleteAllPdps();
		
		/*
		 * Insert this PDP as designated.  Initial standby state will be 
		 * either null or cold standby.
		 */   
		 
		logger.debug("testLocking1: Inserting PDP= {} as designated", thisPdpId);
		DroolsPdpImpl pdp = new DroolsPdpImpl(thisPdpId, true, 4, new Date());
		conn.insertPdp(pdp);
		DroolsPdpEntity droolsPdpEntity = conn.getPdp(thisPdpId);
		logger.debug("testLocking1: After insertion, PDP= {} has DESIGNATED= {}",
				 thisPdpId, droolsPdpEntity.isDesignated());
		assertTrue(droolsPdpEntity.isDesignated() == true);
		
		logger.debug("testLocking1: Instantiating stateManagement object");
		StateManagement smDummy = new StateManagement(emfXacml, "dummy");
		smDummy.deleteAllStateManagementEntities();
		
		// Now we want to create a StateManagementFeature and initialize it.  It will be
		// discovered by the ActiveStandbyFeature when the election handler initializes.

		StateManagementFeatureAPI sm = null;
		for (StateManagementFeatureAPI feature : StateManagementFeatureAPI.impl.getList())
		{
			((PolicySessionFeatureAPI) feature).globalInit(null, configDir);
			sm = feature;
			logger.debug("testLocking1 stateManagementFeature.getResourceName(): {}", sm.getResourceName());
			break;
		}
		if(sm == null){
			logger.error("testLocking1 failed to initialize.  "
					+ "Unable to get instance of StateManagementFeatureAPI "
					+ "with resourceID: {}", thisPdpId);
			logger.debug("testLocking1 failed to initialize.  "
					+ "Unable to get instance of StateManagementFeatureAPI "
					+ "with resourceID: {}", thisPdpId);
		}
		
		// Create an ActiveStandbyFeature and initialize it. It will discover the StateManagementFeature
		// that has been created.
		ActiveStandbyFeatureAPI activeStandbyFeature = null;
		for (ActiveStandbyFeatureAPI feature : ActiveStandbyFeatureAPI.impl.getList())
		{
			((PolicySessionFeatureAPI) feature).globalInit(null, configDir);
			activeStandbyFeature = feature;
			logger.debug("testLocking1 activeStandbyFeature.getResourceName(): {}", activeStandbyFeature.getResourceName());
			break;
		}
		if(activeStandbyFeature == null){
			logger.error("testLocking1 failed to initialize.  "
					+ "Unable to get instance of ActiveStandbyFeatureAPI "
					+ "with resourceID: {}", thisPdpId);
			logger.debug("testLocking1 failed to initialize.  "
					+ "Unable to get instance of ActiveStandbyFeatureAPI "
					+ "with resourceID: {}", thisPdpId);
		}
		
		logger.debug("testLocking1: Runner started; Sleeping "
				+ interruptRecoveryTime + "ms before promoting PDP={}",
				thisPdpId);
		sleep(interruptRecoveryTime);

		logger.debug("testLocking1: Promoting PDP={}", thisPdpId);
		sm.promote();

		logger.debug("testLocking1: Sleeping {} ms, to allow time for "
				+ "policy-management.Main class to come up, designated= {}", 
				 sleepTime, conn.getPdp(thisPdpId).isDesignated());
		sleep(sleepTime);
		
		logger.debug("testLocking1: Waking up and invoking startTransaction on active PDP={}"
				+ ", designated= {}",thisPdpId, conn.getPdp(thisPdpId).isDesignated());


		IntegrityMonitor droolsPdpIntegrityMonitor = IntegrityMonitor.getInstance();
		try {
			droolsPdpIntegrityMonitor.startTransaction();
			droolsPdpIntegrityMonitor.endTransaction();
			logger.debug("testLocking1: As expected, transaction successful");
		} catch (AdministrativeStateException e) {
			logger.error("testLocking1: Unexpectedly caught AdministrativeStateException, ", e);
			assertTrue(false);
		} catch (StandbyStatusException e) {
			logger.error("testLocking1: Unexpectedly caught StandbyStatusException, ", e);
			assertTrue(false);
		} catch (Exception e) {
			logger.error("testLocking1: Unexpectedly caught Exception, ", e);
			assertTrue(false);
		}
		
		// demoting should cause state to transit to hotstandby, followed by re-promotion,
		// since there is only one PDP.
		logger.debug("testLocking1: demoting PDP={}", thisPdpId);
		sm.demote();
		
		logger.debug("testLocking1: sleeping" + electionWaitSleepTime
				+ " to allow election handler to re-promote PDP={}", thisPdpId);
		sleep(electionWaitSleepTime);
								
		logger.debug("testLocking1: Invoking startTransaction on re-promoted PDP={}"
				+ ", designated={}", thisPdpId, conn.getPdp(thisPdpId).isDesignated());
		try {
			droolsPdpIntegrityMonitor.startTransaction();
			droolsPdpIntegrityMonitor.endTransaction();
			logger.debug("testLocking1: As expected, transaction successful");
		} catch (AdministrativeStateException e) {
			logger.error("testLocking1: Unexpectedly caught AdministrativeStateException, ", e);
			assertTrue(false);
		} catch (StandbyStatusException e) {
			logger.error("testLocking1: Unexpectedly caught StandbyStatusException, ", e);
			assertTrue(false);
		} catch (Exception e) {
			logger.error("testLocking1: Unexpectedly caught Exception, ", e);
			assertTrue(false);
		}
		
		// locking should cause state to transit to cold standby
		logger.debug("testLocking1: locking PDP={}", thisPdpId);
		sm.lock();
		
		// Just to avoid any race conditions, sleep a little after locking
		logger.debug("testLocking1: Sleeping a few millis after locking, to avoid race condition");
		sleep(100);
		
		logger.debug("testLocking1: Invoking startTransaction on locked PDP= {}"
				+ ", designated= {}",thisPdpId, conn.getPdp(thisPdpId).isDesignated());
		try {
			droolsPdpIntegrityMonitor.startTransaction();
			logger.error("testLocking1: startTransaction unexpectedly successful");
			assertTrue(false);
		} catch (AdministrativeStateException e) {
			logger.debug("testLocking1: As expected, caught AdministrativeStateException, ", e);
		} catch (StandbyStatusException e) {
			logger.error("testLocking1: Unexpectedly caught StandbyStatusException, ", e);
			assertTrue(false);
		} catch (Exception e) {
			logger.error("testLocking1: Unexpectedly caught Exception, ", e);
			assertTrue(false);
		} finally {
			droolsPdpIntegrityMonitor.endTransaction();
		}		
		
		// unlocking should cause state to transit to hot standby and then providing service
		logger.debug("testLocking1: unlocking PDP={}", thisPdpId);
		sm.unlock();
		
		// Just to avoid any race conditions, sleep a little after locking
		logger.debug("testLocking1: Sleeping a few millis after unlocking, to avoid race condition");
		sleep(electionWaitSleepTime);
		
		logger.debug("testLocking1: Invoking startTransaction on unlocked PDP="
				+ thisPdpId
				+ ", designated="
				+ conn.getPdp(thisPdpId).isDesignated());
		try {
			droolsPdpIntegrityMonitor.startTransaction();
			logger.error("testLocking1: startTransaction successful as expected");
		} catch (AdministrativeStateException e) {
			logger.error("testLocking1: Unexpectedly caught AdministrativeStateException, ", e);
			assertTrue(false);
		} catch (StandbyStatusException e) {
			logger.debug("testLocking1: Unexpectedly caught StandbyStatusException, ", e);
			assertTrue(false);
		} catch (Exception e) {
			logger.error("testLocking1: Unexpectedly caught Exception, ", e);
			assertTrue(false);
		} finally {
			droolsPdpIntegrityMonitor.endTransaction();
		}
		
		// demoting should cause state to transit to hot standby
		logger.debug("testLocking1: demoting PDP={}", thisPdpId);
		sm.demote();
		
		// Just to avoid any race conditions, sleep a little after promoting
		logger.debug("testLocking1: Sleeping a few millis after demoting, to avoid race condition");
		sleep(100);
		
		logger.debug("testLocking1: Invoking startTransaction on demoted PDP={}"
				+ ", designated={}", thisPdpId, conn.getPdp(thisPdpId).isDesignated());
		try {
			droolsPdpIntegrityMonitor.startTransaction();
			droolsPdpIntegrityMonitor.endTransaction();
			logger.debug("testLocking1: Unexpectedly, transaction successful");
			assertTrue(false);
		} catch (AdministrativeStateException e) {
			logger.error("testLocking1: Unexpectedly caught AdministrativeStateException, ", e);
			assertTrue(false);
		} catch (StandbyStatusException e) {
			logger.error("testLocking1: As expected caught StandbyStatusException, ", e);
		} catch (Exception e) {
			logger.error("testLocking1: Unexpectedly caught Exception, ", e);
			assertTrue(false);
		}
		
		logger.debug("\n\ntestLocking1: Exiting\n\n");
		sleep(interruptRecoveryTime);

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

		logger.debug("\n\ntestLocking2: Entering\n\n");
		cleanXacmlDb();
		cleanDroolsDb();		
		
		logger.debug("testLocking2: Reading stateManagementProperties");
		Properties stateManagementProperties = new Properties();
		stateManagementProperties.load(new FileInputStream(new File(
				configDir + "/feature-state-management.properties")));

		logger.debug("testLocking2: Creating emfXacml");
		EntityManagerFactory emfXacml = Persistence.createEntityManagerFactory(
				"junitXacmlPU", stateManagementProperties);
		
		logger.debug("testLocking2: Reading activeStandbyProperties");
		Properties activeStandbyProperties = new Properties();
		activeStandbyProperties.load(new FileInputStream(new File(
				configDir + "/feature-active-standby-management.properties")));
		String thisPdpId = activeStandbyProperties
				.getProperty(ActiveStandbyProperties.NODE_NAME);

		logger.debug("testLocking2: Creating emfDrools");
		EntityManagerFactory emfDrools = Persistence.createEntityManagerFactory(
				"junitDroolsPU", activeStandbyProperties);
		
		DroolsPdpsConnector conn = new JpaDroolsPdpsConnector(emfDrools);
		
		logger.debug("testLocking2: Cleaning up tables");
		conn.deleteAllPdps();
		
		/*
		 * Insert this PDP as designated.  Initial standby state will be 
		 * either null or cold standby.   Demoting should transit state to
		 * hot standby.
		 */
		 
		logger.debug("testLocking2: Inserting PDP= {} as designated", thisPdpId);
		DroolsPdpImpl pdp = new DroolsPdpImpl(thisPdpId, true, 3, new Date());
		conn.insertPdp(pdp);
		DroolsPdpEntity droolsPdpEntity = conn.getPdp(thisPdpId);
		logger.debug("testLocking2: After insertion, PDP= {} has DESIGNATED= {}",
				thisPdpId, droolsPdpEntity.isDesignated());
		assertTrue(droolsPdpEntity.isDesignated() == true);
		
		logger.debug("testLocking2: Instantiating stateManagement object and promoting PDP={}", thisPdpId);
		StateManagement smDummy = new StateManagement(emfXacml, "dummy");
		smDummy.deleteAllStateManagementEntities();
		
		// Now we want to create a StateManagementFeature and initialize it.  It will be
		// discovered by the ActiveStandbyFeature when the election handler initializes.

		StateManagementFeatureAPI sm = null;
		for (StateManagementFeatureAPI feature : StateManagementFeatureAPI.impl.getList())
		{
			((PolicySessionFeatureAPI) feature).globalInit(null, configDir);
			sm = feature;
			logger.debug("testLocking2 stateManagementFeature.getResourceName(): {}", sm.getResourceName());
			break;
		}
		if(sm == null){
			logger.error("testLocking2 failed to initialize.  "
					+ "Unable to get instance of StateManagementFeatureAPI "
					+ "with resourceID: {}", thisPdpId);
			logger.debug("testLocking2 failed to initialize.  "
					+ "Unable to get instance of StateManagementFeatureAPI "
					+ "with resourceID: {}", thisPdpId);
		}
		
		// Create an ActiveStandbyFeature and initialize it. It will discover the StateManagementFeature
		// that has been created.
		ActiveStandbyFeatureAPI activeStandbyFeature = null;
		for (ActiveStandbyFeatureAPI feature : ActiveStandbyFeatureAPI.impl.getList())
		{
			((PolicySessionFeatureAPI) feature).globalInit(null, configDir);
			activeStandbyFeature = feature;
			logger.debug("testLocking2 activeStandbyFeature.getResourceName(): {}", activeStandbyFeature.getResourceName());
			break;
		}
		if(activeStandbyFeature == null){
			logger.error("testLocking2 failed to initialize.  "
					+ "Unable to get instance of ActiveStandbyFeatureAPI "
					+ "with resourceID: {}", thisPdpId);
			logger.debug("testLocking2 failed to initialize.  "
					+ "Unable to get instance of ActiveStandbyFeatureAPI "
					+ "with resourceID: {}", thisPdpId);
		}
		
		/*
		 * Insert another PDP as not designated.  Initial standby state will be 
		 * either null or cold standby.   Demoting should transit state to
		 * hot standby.
		 */
		 
		String standbyPdpId = "pdp2";
		logger.debug("testLocking2: Inserting PDP= {} as not designated", standbyPdpId);
		Date yesterday = DateUtils.addDays(new Date(), -1);
		pdp = new DroolsPdpImpl(standbyPdpId, false, 4, yesterday);
		conn.insertPdp(pdp);
		droolsPdpEntity = conn.getPdp(standbyPdpId);
		logger.debug("testLocking2: After insertion, PDP={} has DESIGNATED= {}", 
				 standbyPdpId, droolsPdpEntity.isDesignated());
		assertTrue(droolsPdpEntity.isDesignated() == false);
		
		logger.debug("testLocking2: Demoting PDP= {}", standbyPdpId);
		StateManagement sm2 = new StateManagement(emfXacml, standbyPdpId);
				
		logger.debug("testLocking2: Runner started; Sleeping {} ms "
				+ "before promoting/demoting", interruptRecoveryTime);
		sleep(interruptRecoveryTime);

		logger.debug("testLocking2: Promoting PDP= {}", thisPdpId);
		sm.promote();

		// demoting PDP should ensure that state transits to hotstandby
		logger.debug("testLocking2: Demoting PDP={}", standbyPdpId);
		sm2.demote();
		
		logger.debug("testLocking2: Sleeping {} ms, to allow time for to come up", sleepTime);
		sleep(sleepTime);
		
		logger.debug("testLocking2: Waking up and invoking startTransaction on active PDP={}"
				+ ", designated= {}", thisPdpId, conn.getPdp(thisPdpId).isDesignated());

		IntegrityMonitor droolsPdpIntegrityMonitor = IntegrityMonitor.getInstance();

		try {
			droolsPdpIntegrityMonitor.startTransaction();
			droolsPdpIntegrityMonitor.endTransaction();
			logger.debug("testLocking2: As expected, transaction successful");
		} catch (AdministrativeStateException e) {
			logger.error("testLocking2: Unexpectedly caught AdministrativeStateException, ", e);
			assertTrue(false);
		} catch (StandbyStatusException e) {
			logger.error("testLocking2: Unexpectedly caught StandbyStatusException, ", e);
			assertTrue(false);
		} catch (Exception e) {
			logger.error("testLocking2: Unexpectedly caught Exception, ", e);
			assertTrue(false);
		}
		
		// demoting should cause state to transit to hotstandby followed by re-promotion.
		logger.debug("testLocking2: demoting PDP={}", thisPdpId);
		sm.demote();
		
		logger.debug("testLocking2: sleeping {}"
				+ " to allow election handler to re-promote PDP={}", electionWaitSleepTime, thisPdpId);
		sleep(electionWaitSleepTime);
		
		logger.debug("testLocking2: Waking up and invoking startTransaction "
				+ "on re-promoted PDP= {}, designated= {}",
				 thisPdpId, conn.getPdp(thisPdpId).isDesignated());
		try {
			droolsPdpIntegrityMonitor.startTransaction();
			droolsPdpIntegrityMonitor.endTransaction();
			logger.debug("testLocking2: As expected, transaction successful");
		} catch (AdministrativeStateException e) {
			logger.error("testLocking2: Unexpectedly caught AdministrativeStateException, ", e);
			assertTrue(false);
		} catch (StandbyStatusException e) {
			logger.error("testLocking2: Unexpectedly caught StandbyStatusException, ", e);
			assertTrue(false);
		} catch (Exception e) {
			logger.error("testLocking2: Unexpectedly caught Exception, ", e);
			assertTrue(false);
		}
		
		logger.debug("testLocking2: Verifying designated status for PDP= {}", standbyPdpId);
		boolean standbyPdpDesignated = conn.getPdp(standbyPdpId).isDesignated();
		assertTrue(standbyPdpDesignated == false);

		logger.debug("\n\ntestLocking2: Exiting\n\n");
		sleep(interruptRecoveryTime);
	}

	private void sleep(long sleepms) throws InterruptedException {
		Thread.sleep(sleepms);
	}
}
