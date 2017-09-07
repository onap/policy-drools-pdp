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

package org.onap.policy.drools.statemanagement.test;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.onap.policy.common.im.StateManagement;
import org.onap.policy.drools.core.PolicySessionFeatureAPI;
import org.onap.policy.drools.statemanagement.StateManagementFeatureAPI;
import org.onap.policy.drools.statemanagement.StateManagementProperties;

public class StateManagementTest {
		
	// get an instance of logger 
	private static Logger  logger = LoggerFactory.getLogger(StateManagementTest.class);	
	
	/*
	 * Sleep 5 seconds after each test to allow interrupt (shutdown) recovery.
	 */
	 
	private long interruptRecoveryTime = 1000;
	
	StateManagementFeatureAPI stateManagementFeature;
	
	/*
	 * All you need to do here is create an instance of StateManagementFeature class.  Then,
	 * check it initial state and the state after diableFailed() and promote()
	 */
	
	@BeforeClass
	public static void setUpClass() throws Exception {
		
		logger.info("setUpClass: Entering");

		String userDir = System.getProperty("user.dir");
		logger.debug("setUpClass: userDir=" + userDir);
		System.setProperty("com.sun.management.jmxremote.port", "9980");
		System.setProperty("com.sun.management.jmxremote.authenticate","false");
				
		initializeDb();
		
		logger.info("setUpClass: Exiting");
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
	 * Verifies that StateManagementFeature starts and runs successfully.
	 */
	 
	//@Ignore
	@Test
	public void testStateManagementOperation() throws Exception {
		
		logger.debug("\n\ntestStateManagementOperation: Entering\n\n");

		logger.debug("testStateManagementOperation: Reading StateManagementProperties");

		String configDir = "src/test/resources";
		
		Properties fsmProperties = new Properties();
		fsmProperties.load(new FileInputStream(new File(
				configDir + "/feature-state-management.properties")));
		String thisPdpId = fsmProperties
				.getProperty(StateManagementProperties.NODE_NAME);

		StateManagementFeatureAPI stateManagementFeature = null;
		for (StateManagementFeatureAPI feature : StateManagementFeatureAPI.impl.getList())
		{
			((PolicySessionFeatureAPI) feature).globalInit(null, configDir);
			stateManagementFeature = feature;
			logger.debug("testStateManagementOperation stateManagementFeature.getResourceName(): " + stateManagementFeature.getResourceName());
			break;
		}
		if(stateManagementFeature == null){
			String msg = "testStateManagementOperation failed to initialize.  "
					+ "Unable to get instance of StateManagementFeatureAPI "
					+ "with resourceID: " + thisPdpId;
			logger.error(msg);
			logger.debug(msg);
		}
		
		Thread.sleep(interruptRecoveryTime);
		
		String admin = stateManagementFeature.getAdminState();
		String oper = stateManagementFeature.getOpState();
		String avail = stateManagementFeature.getAvailStatus();
		String standby = stateManagementFeature.getStandbyStatus();
		
		logger.debug("admin = {}", admin);
		System.out.println("admin = " + admin);
		logger.debug("oper = {}", oper);
		System.out.println("oper = " + oper);
		logger.debug("avail = {}", avail);
		System.out.println("avail = " + avail);
		logger.debug("standby = {}", standby);
		System.out.println("standby = " + standby);
		
		assertTrue("Admin state not unlocked after initialization", admin.equals(StateManagement.UNLOCKED));
		assertTrue("Operational state not enabled after initialization", oper.equals(StateManagement.ENABLED));
		
		try{
			stateManagementFeature.disableFailed();
		}catch(Exception e){
			logger.error(e.getMessage());
			System.out.println(e.getMessage());
			assertTrue(e.getMessage(), false);
		}
		
		Thread.sleep(interruptRecoveryTime);
		
		admin = stateManagementFeature.getAdminState();
		oper = stateManagementFeature.getOpState();
		avail = stateManagementFeature.getAvailStatus();
		standby = stateManagementFeature.getStandbyStatus();
		
		logger.debug("after disableFailed()");
		System.out.println("after disableFailed()");
		logger.debug("admin = {}", admin);
		System.out.println("admin = " + admin);
		logger.debug("oper = {}", oper);
		System.out.println("oper = " + oper);
		logger.debug("avail = {}", avail);
		System.out.println("avail = " + avail);
		logger.debug("standby = {}", standby);
		System.out.println("standby = " + standby);
		
		assertTrue("Operational state not disabled after disableFailed()", oper.equals(StateManagement.DISABLED));
		assertTrue("Availability status not failed after disableFailed()", avail.equals(StateManagement.FAILED));
		
		
		try{
			stateManagementFeature.promote();
		}catch(Exception e){
			logger.debug(e.getMessage());
			System.out.println(e.getMessage());
		}
		
		Thread.sleep(interruptRecoveryTime);
		
		admin = stateManagementFeature.getAdminState();
		oper = stateManagementFeature.getOpState();
		avail = stateManagementFeature.getAvailStatus();
		standby = stateManagementFeature.getStandbyStatus();
		
		logger.debug("after promote()");
		System.out.println("after promote()");
		logger.debug("admin = {}", admin);
		System.out.println("admin = " + admin);
		logger.debug("oper = {}", oper);
		System.out.println("oper = " + oper);
		logger.debug("avail = {}", avail);
		System.out.println("avail = " + avail);
		logger.debug("standby = {}", standby);
		System.out.println("standby = " + standby);

		assertTrue("Standby status not coldstandby after promote()", standby.equals(StateManagement.COLD_STANDBY));
				
		logger.debug("\n\ntestStateManagementOperation: Exiting\n\n");
	}	
	
    /*
     * This method initializes and cleans the DB so that PDP-D will be able to 
     * store fresh records in the DB.
     */
     
	public static void initializeDb(){
		
		logger.debug("initializeDb: Entering");
		
    	Properties cleanProperties = new Properties();
    	cleanProperties.put(StateManagementProperties.DB_DRIVER,"org.h2.Driver");
    	cleanProperties.put(StateManagementProperties.DB_URL, "jdbc:h2:file:./sql/statemanagement");
    	cleanProperties.put(StateManagementProperties.DB_USER, "sa");
    	cleanProperties.put(StateManagementProperties.DB_PWD, "");

    	EntityManagerFactory emf = Persistence.createEntityManagerFactory("junitPU", cleanProperties);
		
		EntityManager em = emf.createEntityManager();
		// Start a transaction
		EntityTransaction et = em.getTransaction();

		et.begin();

		// Clean up the DB
		em.createQuery("Delete from StateManagementEntity").executeUpdate();
		em.createQuery("Delete from ForwardProgressEntity").executeUpdate();
		em.createQuery("Delete from ResourceRegistrationEntity").executeUpdate();

		// commit transaction
		et.commit();
		em.close();
		
		logger.debug("initializeDb: Exiting");
	}	
}
