/*
 * ============LICENSE_START=======================================================
 * feature-active-standby-management
 * ================================================================================
 * Copyright (C) 2017-2019 AT&T Intellectual Property. All rights reserved.
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
import java.util.Date;
import java.util.Properties;
import java.util.function.Supplier;
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
import org.onap.policy.common.im.StateManagement;
import org.onap.policy.drools.activestandby.ActiveStandbyFeatureAPI;
import org.onap.policy.drools.activestandby.ActiveStandbyProperties;
import org.onap.policy.drools.activestandby.DroolsPdpEntity;
import org.onap.policy.drools.activestandby.DroolsPdpImpl;
import org.onap.policy.drools.activestandby.DroolsPdpsConnector;
import org.onap.policy.drools.activestandby.DroolsPdpsElectionHandler;
import org.onap.policy.drools.activestandby.JpaDroolsPdpsConnector;
import org.onap.policy.drools.core.PolicySessionFeatureAPI;
import org.onap.policy.drools.statemanagement.StateManagementFeatureApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Testing the allSeemsWell interface to verify that it correctly affects the
 * operational state.
 */

public class AllSeemsWellTest {
    private static final Logger  logger = LoggerFactory.getLogger(AllSeemsWellTest.class);
    /*
     * Currently, the DroolsPdpsElectionHandler.DesignationWaiter is invoked every 1 seconds, starting
     * at the start of the next multiple of pdpUpdateInterval, but with a minimum of 5 sec cushion
     * to ensure that we wait for the DesignationWaiter to do its job, before
     * checking the results. Add a few seconds for safety
     */

    private static int SLEEP_TIME_SEC = 10;

    /*
     * DroolsPdpsElectionHandler runs every 1 seconds, so it takes 10 seconds for the
     * checkWaitTimer() method to time out and call allSeemsWell which then requires
     * the forward progress counter to go stale which should add an additional 5 sec.
     */

    private static int STALLED_ELECTION_HANDLER_SLEEP_TIME_SEC = 15;

    /*
     * As soon as the election hander successfully runs, it will resume the forward progress.
     * If the election handler runs ever 1 sec and test transaction is run every 1 sec and
     * then fpc is written every 1 sec and then the fpc is checked every 2 sec, that could
     * take a total of 5 sec to recognize the resumption of progress.  So, add 1 for safety.
     */
    private static int RESUMED_ELECTION_HANDLER_SLEEP_TIME_SEC = 6;

    private static EntityManagerFactory emfx;
    private static EntityManagerFactory emfd;
    private static EntityManager emx;
    private static EntityManager emd;
    private static EntityTransaction et;

    private final String configDir = "src/test/resources/asw";

    /*
     * See the IntegrityMonitor.getJmxUrl() method for the rationale behind this jmx related processing.
     */

    /**
     * Setup the class.
     *
     * @throws Exception exception
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

    /**
     * Setup.
     *
     * @throws Exception exception
     */
    @Before
    public void setUp() throws Exception {
        //Create teh data access for xaml db
        Properties stateManagementProperties = new Properties();
        stateManagementProperties.load(new FileInputStream(new File(
                configDir + "/feature-state-management.properties")));

        emfx = Persistence.createEntityManagerFactory("junitXacmlPU", stateManagementProperties);

        // Create an entity manager to use the DB
        emx = emfx.createEntityManager();

        //Create the data access for drools db
        Properties activeStandbyProperties = new Properties();
        activeStandbyProperties.load(new FileInputStream(new File(
                configDir + "/feature-active-standby-management.properties")));

        emfd = Persistence.createEntityManagerFactory("junitDroolsPU", activeStandbyProperties);

        // Create an entity manager to use the DB
        emd = emfd.createEntityManager();

        DroolsPdpsElectionHandler.setIsUnitTesting(true);
    }

    @After
    public void tearDown() throws Exception {

    }

    /**
     * Clean the xacml database.
     */
    public void cleanXacmlDb() {
        et = emx.getTransaction();

        et.begin();
        // Make sure we leave the DB clean
        emx.createQuery("DELETE FROM StateManagementEntity").executeUpdate();
        emx.createQuery("DELETE FROM ResourceRegistrationEntity").executeUpdate();
        emx.createQuery("DELETE FROM ForwardProgressEntity").executeUpdate();
        emx.flush();
        et.commit();
    }

    /**
     * Clean the drools database.
     */
    public void cleanDroolsDb() {
        et = emd.getTransaction();

        et.begin();
        // Make sure we leave the DB clean
        emd.createQuery("DELETE FROM DroolsPdpEntity").executeUpdate();
        emd.flush();
        et.commit();
    }


    // Tests hot standby when there is only one PDP.

    //@Ignore
    @Test
    public void testAllSeemsWell() throws Exception {

        logger.debug("\n\ntestAllSeemsWell: Entering\n\n");
        cleanXacmlDb();
        cleanDroolsDb();

        logger.debug("testAllSeemsWell: Reading stateManagementProperties");
        Properties stateManagementProperties = new Properties();
        stateManagementProperties.load(new FileInputStream(new File(
                configDir + "/feature-state-management.properties")));

        logger.debug("testAllSeemsWell: Creating emfXacml");
        final EntityManagerFactory emfXacml = Persistence.createEntityManagerFactory(
                "junitXacmlPU", stateManagementProperties);

        logger.debug("testAllSeemsWell: Reading activeStandbyProperties");
        Properties activeStandbyProperties = new Properties();
        activeStandbyProperties.load(new FileInputStream(new File(
                configDir + "/feature-active-standby-management.properties")));
        final String thisPdpId = activeStandbyProperties
                .getProperty(ActiveStandbyProperties.NODE_NAME);

        logger.debug("testAllSeemsWell: Creating emfDrools");
        EntityManagerFactory emfDrools = Persistence.createEntityManagerFactory(
                "junitDroolsPU", activeStandbyProperties);

        DroolsPdpsConnector conn = new JpaDroolsPdpsConnector(emfDrools);

        logger.debug("testAllSeemsWell: Cleaning up tables");
        conn.deleteAllPdps();

        /*
         * Insert this PDP as not designated.  Initial standby state will be
         * either null or cold standby.   Demoting should transit state to
         * hot standby.
         */

        logger.debug("testAllSeemsWell: Inserting PDP={} as not designated", thisPdpId);
        Date yesterday = DateUtils.addDays(new Date(), -1);
        DroolsPdpImpl pdp = new DroolsPdpImpl(thisPdpId, false, 4, yesterday);
        conn.insertPdp(pdp);
        DroolsPdpEntity droolsPdpEntity = conn.getPdp(thisPdpId);
        logger.debug("testAllSeemsWell: After insertion, PDP={} has DESIGNATED={}",
                thisPdpId, droolsPdpEntity.isDesignated());
        assertTrue(droolsPdpEntity.isDesignated() == false);

        logger.debug("testAllSeemsWell: Instantiating stateManagement object");
        StateManagement sm = new StateManagement(emfXacml, "dummy");
        sm.deleteAllStateManagementEntities();


        // Now we want to create a StateManagementFeature and initialize it.  It will be
        // discovered by the ActiveStandbyFeature when the election handler initializes.

        StateManagementFeatureApi stateManagementFeatureApi = null;
        for (StateManagementFeatureApi feature : stateManagementFeatureApi.impl.getList()) {
            ((PolicySessionFeatureAPI) feature).globalInit(null, configDir);
            stateManagementFeatureApi = feature;
            logger.debug("testAllSeemsWell stateManagementFeature.getResourceName(): {}",
                stateManagementFeatureApi.getResourceName());
            break;
        }
        if (stateManagementFeatureApi == null) {
            logger.error("testAllSeemsWell failed to initialize.  "
                    + "Unable to get instance of StateManagementFeatureApi "
                    + "with resourceID: {}", thisPdpId);
            logger.debug("testAllSeemsWell failed to initialize.  "
                    + "Unable to get instance of StateManagementFeatureApi "
                    + "with resourceID: {}", thisPdpId);
        }
        final StateManagementFeatureApi smf = stateManagementFeatureApi;

        // Create an ActiveStandbyFeature and initialize it. It will discover the StateManagementFeature
        // that has been created.
        ActiveStandbyFeatureAPI activeStandbyFeature = null;
        for (ActiveStandbyFeatureAPI feature : ActiveStandbyFeatureAPI.impl.getList()) {
            ((PolicySessionFeatureAPI) feature).globalInit(null, configDir);
            activeStandbyFeature = feature;
            logger.debug("testAllSeemsWell activeStandbyFeature.getResourceName(): {}",
                    activeStandbyFeature.getResourceName());
            break;
        }
        if (activeStandbyFeature == null) {
            logger.error("testAllSeemsWell failed to initialize.  "
                    + "Unable to get instance of ActiveStandbyFeatureAPI "
                    + "with resourceID: {}", thisPdpId);
            logger.debug("testAllSeemsWell failed to initialize.  "
                    + "Unable to get instance of ActiveStandbyFeatureAPI "
                    + "with resourceID: {}", thisPdpId);
        }


        logger.debug("testAllSeemsWell: Demoting PDP={}", thisPdpId);
        // demoting should cause state to transit to hotstandby
        smf.demote();


        logger.debug("testAllSeemsWell: Sleeping {} s, to allow JpaDroolsPdpsConnector "
            + "time to check droolspdpentity table", SLEEP_TIME_SEC);
        waitForCondition(()-> conn.getPdp(thisPdpId).isDesignated(),  SLEEP_TIME_SEC);

        // Verify that this formerly un-designated PDP in HOT_STANDBY is now designated and providing service.

        droolsPdpEntity = conn.getPdp(thisPdpId);
        logger.debug("testAllSeemsWell: After sm.demote() invoked, DESIGNATED= {} "
                + "for PDP= {}", droolsPdpEntity.isDesignated(), thisPdpId);
        assertTrue(droolsPdpEntity.isDesignated() == true);
        String standbyStatus = smf.getStandbyStatus(thisPdpId);
        logger.debug("testAllSeemsWell: After demotion, PDP= {} "
                + "has standbyStatus= {}", thisPdpId, standbyStatus);
        assertTrue(standbyStatus != null  &&  standbyStatus.equals(StateManagement.PROVIDING_SERVICE));

        //Now we want to stall the election handler and see the if AllSeemsWell will make the
        //standbystatus = coldstandby

        DroolsPdpsElectionHandler.setIsStalled(true);

        logger.debug("testAllSeemsWell: Sleeping {} s, to allow checkWaitTimer to recognize "
                + "the election handler has stalled and for the testTransaction to fail to "
                + "increment forward progress and for the lack of forward progress to be recognized.",
            STALLED_ELECTION_HANDLER_SLEEP_TIME_SEC);


        //It takes 10x the update interval (1 sec) before the watcher will declare the election handler dead
        //and that just stops forward progress counter.  So, the fp monitor must then run to determine
        //if the fpc has stalled.  That will take about another 5 sec.
        waitForCondition(()-> smf.getStandbyStatus().equals(StateManagement.COLD_STANDBY),
            STALLED_ELECTION_HANDLER_SLEEP_TIME_SEC);

        logger.debug("testAllSeemsWell: After isStalled=true, PDP= {} "
                + "has standbyStatus= {}", thisPdpId, smf.getStandbyStatus(thisPdpId));

        assertTrue(smf.getStandbyStatus().equals(StateManagement.COLD_STANDBY));

        //Now lets resume the election handler
        DroolsPdpsElectionHandler.setIsStalled(false);

        waitForCondition(() -> smf.getStandbyStatus().equals(StateManagement.PROVIDING_SERVICE),
            RESUMED_ELECTION_HANDLER_SLEEP_TIME_SEC);

        logger.debug("testAllSeemsWell: After isStalled=false, PDP= {} "
                + "has standbyStatus= {}", thisPdpId, smf.getStandbyStatus(thisPdpId));

        assertTrue(smf.getStandbyStatus().equals(StateManagement.PROVIDING_SERVICE));

        //resumedElectionHandlerSleepTime = 5000;
        logger.debug("\n\ntestAllSeemsWell: Exiting\n\n");

    }

    private void waitForCondition(Supplier<Boolean> testCondition, int timeoutInSeconds) throws InterruptedException {
        int maxIterations = timeoutInSeconds * 10;
        int iterations = 0;
        while (!testCondition.get() && iterations < maxIterations) {
            iterations++;
            Thread.sleep(100);
        }
    }
}
