/*-
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

package org.onap.policy.drools.activestandby;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.onap.policy.common.im.AdministrativeStateException;
import org.onap.policy.common.im.IntegrityMonitor;
import org.onap.policy.common.im.IntegrityMonitorException;
import org.onap.policy.common.im.MonitorTime;
import org.onap.policy.common.im.StandbyStatusException;
import org.onap.policy.common.im.StateManagement;
import org.onap.policy.common.utils.time.CurrentTime;
import org.onap.policy.common.utils.time.PseudoTimer;
import org.onap.policy.common.utils.time.TestTimeMulti;
import org.onap.policy.drools.core.PolicySessionFeatureApi;
import org.onap.policy.drools.statemanagement.StateManagementFeatureApi;
import org.onap.policy.drools.statemanagement.StateManagementFeatureApiConstants;
import org.powermock.reflect.Whitebox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * All JUnits are designed to run in the local development environment
 * where they have write privileges and can execute time-sensitive
 * tasks.
 */

public class StandbyStateManagementTest {
    private static final Logger  logger = LoggerFactory.getLogger(StandbyStateManagementTest.class);

    private static final String MONITOR_FIELD_NAME = "instance";
    private static final String HANDLER_INSTANCE_FIELD = "electionHandler";

    /*
     * Currently, the DroolsPdpsElectionHandler.DesignationWaiter is invoked every 1 seconds, starting
     * at the start of the next multiple of pdpUpdateInterval, but with a minimum of 5 sec cushion
     * to ensure that we wait for the DesignationWaiter to do its job, before
     * checking the results. Add a few seconds for safety
     */

    private static final long SLEEP_TIME = 10000;

    /*
     * DroolsPdpsElectionHandler runs every 1 seconds, so a 6 second sleep should be
     * plenty to ensure it has time to re-promote this PDP.
     */

    private static final long ELECTION_WAIT_SLEEP_TIME = 6000;

    /*
     * Sleep a few seconds after each test to allow interrupt (shutdown) recovery.
     */

    private static final long INTERRUPT_RECOVERY_TIME = 5000;

    private static EntityManagerFactory emfx;
    private static EntityManagerFactory emfd;
    private static EntityManager emx;
    private static EntityManager emd;
    private static EntityTransaction et;

    private static final String CONFIG_DIR = "src/test/resources";

    private static CurrentTime saveTime;
    private static Factory saveFactory;

    private TestTimeMulti testTime;

    /*
     * This cannot be shared by tests, as each integrity monitor may manipulate it by
     * adding its own property values.
     */
    private Properties activeStandbyProperties;

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

        saveTime = Whitebox.getInternalState(MonitorTime.class, MONITOR_FIELD_NAME);
        saveFactory = Factory.getInstance();

        resetInstanceObjects();

        //Create the data access for xacml db
        Properties smProps = loadStateManagementProperties();

        emfx = Persistence.createEntityManagerFactory("junitXacmlPU", smProps);

        // Create an entity manager to use the DB
        emx = emfx.createEntityManager();

        //Create the data access for drools db
        Properties asbProps = loadActiveStandbyProperties();

        emfd = Persistence.createEntityManagerFactory("junitDroolsPU", asbProps);

        // Create an entity manager to use the DB
        emd = emfd.createEntityManager();
    }

    /**
     * Restores the system state.
     *
     * @throws IntegrityMonitorException if the integrity monitor cannot be shut down
     */
    @AfterClass
    public static void tearDownClass() throws IntegrityMonitorException {
        resetInstanceObjects();

        Whitebox.setInternalState(MonitorTime.class, MONITOR_FIELD_NAME, saveTime);
        Factory.setInstance(saveFactory);

        emd.close();
        emfd.close();

        emx.close();
        emfx.close();
    }

    /**
     * Setup.
     *
     * @throws Exception exception
     */
    @Before
    public void setUp() throws Exception {
        resetInstanceObjects();
        cleanXacmlDb();
        cleanDroolsDb();

        /*
         * set test time
         *
         * All threads use the test time object for sleeping.  As a result, we don't have
         * to wait more than an instant for them to complete their work, thus we'll use
         * a very small REAL wait time in the constructor.
         */
        testTime = new TestTimeMulti(5);
        Whitebox.setInternalState(MonitorTime.class, MONITOR_FIELD_NAME, testTime);

        Factory factory = mock(Factory.class);
        when(factory.makeTimer()).thenAnswer(ans -> new PseudoTimer(testTime));
        Factory.setInstance(factory);

        activeStandbyProperties = loadActiveStandbyProperties();
    }

    private static void resetInstanceObjects() throws IntegrityMonitorException {
        IntegrityMonitor.setUnitTesting(true);
        IntegrityMonitor.deleteInstance();
        IntegrityMonitor.setUnitTesting(false);

        Whitebox.setInternalState(ActiveStandbyFeature.class, HANDLER_INSTANCE_FIELD, (Object) null);

    }

    /**
     * Clean up the xacml database.
     *
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
     * Clean up the drools db.
     */
    public void cleanDroolsDb() {
        et = emd.getTransaction();

        et.begin();
        // Make sure we leave the DB clean
        emd.createQuery("DELETE FROM DroolsPdpEntity").executeUpdate();
        emd.flush();
        et.commit();
    }

    /**
     * Test the standby state change notifier.
     *
     * @throws Exception exception
     */
    @Test
    public void testPmStandbyStateChangeNotifier() throws Exception {
        logger.debug("\n\ntestPmStandbyStateChangeNotifier: Entering\n\n");

        logger.debug("testPmStandbyStateChangeNotifier: Reading activeStandbyProperties");

        String resourceName = "testPMS";
        activeStandbyProperties.setProperty("resource.name", resourceName);
        ActiveStandbyProperties.initProperties(activeStandbyProperties);

        logger.debug("testPmStandbyStateChangeNotifier: Getting StateManagement instance");

        StateManagement sm = new StateManagement(emfx, resourceName);

        //Create an instance of the Observer
        PmStandbyStateChangeNotifier pmNotifier = new PmStandbyStateChangeNotifier();

        //Register the PmStandbyStateChangeNotifier Observer
        sm.addObserver(pmNotifier);

        //At this point the standbystatus = 'null'
        sm.lock();
        assertEquals(StateManagement.NULL_VALUE, pmNotifier.getPreviousStandbyStatus());

        sm.unlock();
        assertEquals(StateManagement.NULL_VALUE, pmNotifier.getPreviousStandbyStatus());

        //Adding standbystatus=hotstandby
        sm.demote();
        System.out.println(pmNotifier.getPreviousStandbyStatus());
        assertEquals(PmStandbyStateChangeNotifier.HOTSTANDBY_OR_COLDSTANDBY,
                pmNotifier.getPreviousStandbyStatus());

        //Now making standbystatus=coldstandby
        sm.lock();
        assertEquals(PmStandbyStateChangeNotifier.HOTSTANDBY_OR_COLDSTANDBY,
                pmNotifier.getPreviousStandbyStatus());

        //standbystatus = hotstandby
        sm.unlock();
        assertEquals(PmStandbyStateChangeNotifier.HOTSTANDBY_OR_COLDSTANDBY,
                pmNotifier.getPreviousStandbyStatus());

        //standbystatus = providingservice
        sm.promote();
        //The previousStandbyStatus is not updated until after the delay activation expires
        assertEquals(PmStandbyStateChangeNotifier.HOTSTANDBY_OR_COLDSTANDBY,
                pmNotifier.getPreviousStandbyStatus());

        //Sleep long enough for the delayActivationTimer to run
        sleep(5000);
        assertEquals(StateManagement.PROVIDING_SERVICE, pmNotifier.getPreviousStandbyStatus());

        //standbystatus = providingservice
        sm.promote();
        assertEquals(StateManagement.PROVIDING_SERVICE, pmNotifier.getPreviousStandbyStatus());

        //standbystatus = coldstandby
        sm.lock();
        assertEquals(PmStandbyStateChangeNotifier.HOTSTANDBY_OR_COLDSTANDBY,
                pmNotifier.getPreviousStandbyStatus());

        //standbystatus = hotstandby
        sm.unlock();
        assertEquals(PmStandbyStateChangeNotifier.HOTSTANDBY_OR_COLDSTANDBY,
                pmNotifier.getPreviousStandbyStatus());

        //standbystatus = hotstandby
        sm.demote();
        assertEquals(PmStandbyStateChangeNotifier.HOTSTANDBY_OR_COLDSTANDBY,
                pmNotifier.getPreviousStandbyStatus());
    }

    /**
     * Test sanitize designated list.
     *
     * @throws Exception exception
     */
    @Test
    public void testSanitizeDesignatedList() throws Exception {

        logger.debug("\n\ntestSanitizeDesignatedList: Entering\n\n");

        // Get a DroolsPdpsConnector

        final DroolsPdpsConnector droolsPdpsConnector = new JpaDroolsPdpsConnector(emfd);

        // Create 4 pdpd all not designated

        DroolsPdp pdp1 = new DroolsPdpImpl("pdp1", false, 4, testTime.getDate());
        DroolsPdp pdp2 = new DroolsPdpImpl("pdp2", false, 4, testTime.getDate());
        DroolsPdp pdp3 = new DroolsPdpImpl("pdp3", false, 4, testTime.getDate());
        DroolsPdp pdp4 = new DroolsPdpImpl("pdp4", false, 4, testTime.getDate());

        List<DroolsPdp> listOfDesignated = new ArrayList<DroolsPdp>();
        listOfDesignated.add(pdp1);
        listOfDesignated.add(pdp2);
        listOfDesignated.add(pdp3);
        listOfDesignated.add(pdp4);

        // Now we want to create a StateManagementFeature and initialize it.  It will be
        // discovered by the ActiveStandbyFeature when the election handler initializes.

        StateManagementFeatureApi stateManagementFeature = null;
        for (StateManagementFeatureApi feature : StateManagementFeatureApiConstants.getImpl().getList()) {
            ((PolicySessionFeatureApi) feature).globalInit(null, CONFIG_DIR);
            stateManagementFeature = feature;
            logger.debug("testColdStandby stateManagementFeature.getResourceName(): {}",
                    stateManagementFeature.getResourceName());
            break;
        }
        assertNotNull(stateManagementFeature);


        DroolsPdpsElectionHandler droolsPdpsElectionHandler =  new DroolsPdpsElectionHandler(droolsPdpsConnector, pdp1);

        listOfDesignated = droolsPdpsElectionHandler.santizeDesignatedList(listOfDesignated);

        logger.debug("\n\ntestSanitizeDesignatedList: listOfDesignated.size = {}\n\n",listOfDesignated.size());

        assertEquals(4, listOfDesignated.size());

        // Now make 2 designated

        pdp1.setDesignated(true);
        pdp2.setDesignated(true);

        listOfDesignated = droolsPdpsElectionHandler.santizeDesignatedList(listOfDesignated);

        logger.debug("\n\ntestSanitizeDesignatedList: listOfDesignated.size after 2 designated = {}\n\n",
                listOfDesignated.size());

        assertEquals(2, listOfDesignated.size());
        assertTrue(listOfDesignated.contains(pdp1));
        assertTrue(listOfDesignated.contains(pdp2));


        // Now all are designated.  But, we have to add back the previously non-designated nodes

        pdp3.setDesignated(true);
        pdp4.setDesignated(true);
        listOfDesignated.add(pdp3);
        listOfDesignated.add(pdp4);

        listOfDesignated = droolsPdpsElectionHandler.santizeDesignatedList(listOfDesignated);

        logger.debug("\n\ntestSanitizeDesignatedList: listOfDesignated.size after all designated = {}\n\n",
                listOfDesignated.size());

        assertEquals(4, listOfDesignated.size());

    }

    /**
    *  Test Compute most recent primary.
    *
    * @throws Exception exception
    */
    @Test
    public void testComputeMostRecentPrimary() throws Exception {

        logger.debug("\n\ntestComputeMostRecentPrimary: Entering\n\n");

        final DroolsPdpsConnector droolsPdpsConnector = new JpaDroolsPdpsConnector(emfd);


        // Create 4 pdpd all not designated


        long designatedDateMs = testTime.getMillis();
        DroolsPdp pdp1 = new DroolsPdpImpl("pdp1", false, 4, testTime.getDate());
        pdp1.setDesignatedDate(new Date(designatedDateMs - 2));

        DroolsPdp pdp2 = new DroolsPdpImpl("pdp2", false, 4, testTime.getDate());
        //oldest
        pdp2.setDesignatedDate(new Date(designatedDateMs - 3));

        DroolsPdp pdp3 = new DroolsPdpImpl("pdp3", false, 4, testTime.getDate());
        pdp3.setDesignatedDate(new Date(designatedDateMs - 1));

        DroolsPdp pdp4 = new DroolsPdpImpl("pdp4", false, 4, testTime.getDate());
        //most recent
        pdp4.setDesignatedDate(new Date(designatedDateMs));

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

        StateManagementFeatureApi stateManagementFeature = null;
        for (StateManagementFeatureApi feature : StateManagementFeatureApiConstants.getImpl().getList()) {
            ((PolicySessionFeatureApi) feature).globalInit(null, CONFIG_DIR);
            stateManagementFeature = feature;
            logger.debug("testComputeMostRecentPrimary stateManagementFeature.getResourceName(): {}",
                    stateManagementFeature.getResourceName());
            break;
        }
        assertNotNull(stateManagementFeature);

        DroolsPdpsElectionHandler droolsPdpsElectionHandler =  new DroolsPdpsElectionHandler(droolsPdpsConnector, pdp1);

        DroolsPdp mostRecentPrimary = droolsPdpsElectionHandler.computeMostRecentPrimary(
                listOfAllPdps, listOfDesignated);

        logger.debug("\n\ntestComputeMostRecentPrimary: mostRecentPrimary.getPdpId() = {}\n\n",
                mostRecentPrimary.getPdpId());


        // If all of the pdps are included in the listOfDesignated and none are designated, it will choose
        // the one which has the most recent designated date.


        assertEquals("pdp4", mostRecentPrimary.getPdpId());


        // Now let's designate all of those on the listOfDesignated.  It will choose the first one designated


        pdp1.setDesignated(true);
        pdp2.setDesignated(true);
        pdp3.setDesignated(true);
        pdp4.setDesignated(true);

        mostRecentPrimary = droolsPdpsElectionHandler.computeMostRecentPrimary(listOfAllPdps, listOfDesignated);

        logger.debug("\n\ntestComputeMostRecentPrimary: All designated all on list, "
                + "mostRecentPrimary.getPdpId() = {}\n\n",
                mostRecentPrimary.getPdpId());


        // If all of the pdps are included in the listOfDesignated and all are designated, it will choose
        // the one which was designated first


        assertEquals("pdp2", mostRecentPrimary.getPdpId());


        // Now we will designate only 2 and put just them in the listOfDesignated.  The algorithm will now
        // look for the most recently designated pdp which is not currently designated.


        pdp3.setDesignated(false);
        pdp4.setDesignated(false);

        listOfDesignated.remove(pdp3);
        listOfDesignated.remove(pdp4);

        mostRecentPrimary = droolsPdpsElectionHandler.computeMostRecentPrimary(listOfAllPdps, listOfDesignated);

        logger.debug("\n\ntestComputeMostRecentPrimary: mostRecentPrimary.getPdpId() = {}\n\n",
                mostRecentPrimary.getPdpId());

        assertEquals("pdp4", mostRecentPrimary.getPdpId());



        // Now we will have none designated and put two of them in the listOfDesignated.  The algorithm will now
        // look for the most recently designated pdp regardless of whether it is currently marked as designated.


        pdp1.setDesignated(false);
        pdp2.setDesignated(false);

        mostRecentPrimary = droolsPdpsElectionHandler.computeMostRecentPrimary(listOfAllPdps, listOfDesignated);

        logger.debug("\n\ntestComputeMostRecentPrimary: 2 on list mostRecentPrimary.getPdpId() = {}\n\n",
                mostRecentPrimary.getPdpId());

        assertEquals("pdp4", mostRecentPrimary.getPdpId());


        // If we have only one pdp on in the listOfDesignated,
        // the most recently designated pdp will be chosen, regardless
        // of its designation status


        listOfDesignated.remove(pdp1);

        mostRecentPrimary = droolsPdpsElectionHandler.computeMostRecentPrimary(listOfAllPdps, listOfDesignated);

        logger.debug("\n\ntestComputeMostRecentPrimary: 1 on list mostRecentPrimary.getPdpId() = {}\n\n",
                mostRecentPrimary.getPdpId());

        assertEquals("pdp4", mostRecentPrimary.getPdpId());


        // Finally, if none are on the listOfDesignated, it will again choose the most recently designated pdp.


        listOfDesignated.remove(pdp2);

        mostRecentPrimary = droolsPdpsElectionHandler.computeMostRecentPrimary(listOfAllPdps, listOfDesignated);

        logger.debug("\n\ntestComputeMostRecentPrimary: 0 on list mostRecentPrimary.getPdpId() = {}\n\n",
                mostRecentPrimary.getPdpId());

        assertEquals("pdp4", mostRecentPrimary.getPdpId());

    }

    /**
     * Test compute designated PDP.
     *
     * @throws Exception exception
     */
    @Test
    public void testComputeDesignatedPdp() throws Exception {

        logger.debug("\n\ntestComputeDesignatedPdp: Entering\n\n");

        final DroolsPdpsConnector droolsPdpsConnector = new JpaDroolsPdpsConnector(emfd);


        // Create 4 pdpd all not designated.  Two on site1. Two on site2


        long designatedDateMs = testTime.getMillis();
        DroolsPdp pdp1 = new DroolsPdpImpl("pdp1", false, 4, testTime.getDate());
        pdp1.setDesignatedDate(new Date(designatedDateMs - 2));
        pdp1.setSite("site1");

        DroolsPdp pdp2 = new DroolsPdpImpl("pdp2", false, 4, testTime.getDate());
        pdp2.setDesignatedDate(new Date(designatedDateMs - 3));
        pdp2.setSite("site1");

        //oldest
        DroolsPdp pdp3 = new DroolsPdpImpl("pdp3", false, 4, testTime.getDate());
        pdp3.setDesignatedDate(new Date(designatedDateMs - 4));
        pdp3.setSite("site2");

        DroolsPdp pdp4 = new DroolsPdpImpl("pdp4", false, 4, testTime.getDate());
        //most recent
        pdp4.setDesignatedDate(new Date(designatedDateMs));
        pdp4.setSite("site2");

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

        StateManagementFeatureApi stateManagementFeature = null;
        for (StateManagementFeatureApi feature : StateManagementFeatureApiConstants.getImpl().getList()) {
            ((PolicySessionFeatureApi) feature).globalInit(null, CONFIG_DIR);
            stateManagementFeature = feature;
            logger.debug("testComputeDesignatedPdp stateManagementFeature.getResourceName(): {}",
                    stateManagementFeature.getResourceName());
            break;
        }
        assertNotNull(stateManagementFeature);


        DroolsPdpsElectionHandler droolsPdpsElectionHandler =  new DroolsPdpsElectionHandler(droolsPdpsConnector, pdp1);

        DroolsPdp mostRecentPrimary = pdp4;

        DroolsPdp designatedPdp = droolsPdpsElectionHandler.computeDesignatedPdp(listOfDesignated, mostRecentPrimary);


        // The designatedPdp should be null

        assertNull(designatedPdp);


        // Now let's try having only one pdp in listOfDesignated, but not in the same site as the most recent primary

        listOfDesignated.add(pdp2);

        designatedPdp = droolsPdpsElectionHandler.computeDesignatedPdp(listOfDesignated, mostRecentPrimary);


        // Now the designatedPdp should be the one and only selection in the listOfDesignated


        assertEquals(designatedPdp.getPdpId(), pdp2.getPdpId());


        // Now let's put 2 pdps in the listOfDesignated, neither in the same site as the mostRecentPrimary


        listOfDesignated.add(pdp1);

        designatedPdp = droolsPdpsElectionHandler.computeDesignatedPdp(listOfDesignated, mostRecentPrimary);


        // The designatedPdp should now be the one with the lowest lexiographic score - pdp1


        assertEquals(designatedPdp.getPdpId(), pdp1.getPdpId());


        // Finally, we will have 2 pdps in the listOfDesignated, one in the same site with the mostRecentPrimary


        listOfDesignated.remove(pdp1);
        listOfDesignated.add(pdp3);

        designatedPdp = droolsPdpsElectionHandler.computeDesignatedPdp(listOfDesignated, mostRecentPrimary);


        // The designatedPdp should now be the one on the same site as the mostRecentPrimary


        assertEquals(designatedPdp.getPdpId(), pdp3.getPdpId());
    }

    /**
     * Test cold standby.
     *
     * @throws Exception exception
     */
    @Test
    public void testColdStandby() throws Exception {

        logger.debug("\n\ntestColdStandby: Entering\n\n");

        final String thisPdpId = activeStandbyProperties.getProperty(ActiveStandbyProperties.NODE_NAME);

        DroolsPdpsConnector conn = new JpaDroolsPdpsConnector(emfd);

        logger.debug("testColdStandby: Inserting PDP={} as designated", thisPdpId);
        DroolsPdp pdp = new DroolsPdpImpl(thisPdpId, true, 4, testTime.getDate());
        conn.insertPdp(pdp);
        DroolsPdpEntity droolsPdpEntity = conn.getPdp(thisPdpId);
        logger.debug("testColdStandby: After insertion, DESIGNATED= {} "
                + "for PDP= {}", droolsPdpEntity.isDesignated(), thisPdpId);
        assertTrue(droolsPdpEntity.isDesignated());

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

        StateManagement sm = new StateManagement(emfx, "dummy");
        sm.deleteAllStateManagementEntities();

        // Now we want to create a StateManagementFeature and initialize it.  It will be
        // discovered by the ActiveStandbyFeature when the election handler initializes.

        StateManagementFeatureApi smf = null;
        for (StateManagementFeatureApi feature : StateManagementFeatureApiConstants.getImpl().getList()) {
            ((PolicySessionFeatureApi) feature).globalInit(null, CONFIG_DIR);
            smf = feature;
            logger.debug("testColdStandby stateManagementFeature.getResourceName(): {}", smf.getResourceName());
            break;
        }
        assertNotNull(smf);

        // Create an ActiveStandbyFeature and initialize it. It will discover the StateManagementFeature
        // that has been created.
        ActiveStandbyFeatureApi activeStandbyFeature = null;
        for (ActiveStandbyFeatureApi feature : ActiveStandbyFeatureApiConstants.getImpl().getList()) {
            ((PolicySessionFeatureApi) feature).globalInit(null, CONFIG_DIR);
            activeStandbyFeature = feature;
            logger.debug("testColdStandby activeStandbyFeature.getResourceName(): {}",
                    activeStandbyFeature.getResourceName());
            break;
        }
        assertNotNull(activeStandbyFeature);

        // Artificially putting a PDP into service is really a two step process, 1)
        // inserting it as designated and 2) promoting it so that its standbyStatus
        // is providing service.

        logger.debug("testColdStandby: Runner started; Sleeping "
                + INTERRUPT_RECOVERY_TIME + "ms before promoting PDP= {}",
                thisPdpId);
        sleep(INTERRUPT_RECOVERY_TIME);

        logger.debug("testColdStandby: Promoting PDP={}", thisPdpId);
        smf.promote();

        String standbyStatus = sm.getStandbyStatus(thisPdpId);
        logger.debug("testColdStandby: Before locking, PDP= {}  has standbyStatus= {}",
                thisPdpId, standbyStatus);

        logger.debug("testColdStandby: Locking smf");
        smf.lock();

        sleep(INTERRUPT_RECOVERY_TIME);

        // Verify that the PDP is no longer designated.

        droolsPdpEntity = conn.getPdp(thisPdpId);
        logger.debug("testColdStandby: After lock sm.lock() invoked, "
                + "DESIGNATED= {} for PDP={}", droolsPdpEntity.isDesignated(), thisPdpId);
        assertFalse(droolsPdpEntity.isDesignated());

        logger.debug("\n\ntestColdStandby: Exiting\n\n");
    }

    // Tests hot standby when there is only one PDP.

    /**
     * Test hot standby 1.
     *
     * @throws Exception exception
     */
    @Test
    public void testHotStandby1() throws Exception {

        logger.debug("\n\ntestHotStandby1: Entering\n\n");

        final String thisPdpId = activeStandbyProperties
                .getProperty(ActiveStandbyProperties.NODE_NAME);

        DroolsPdpsConnector conn = new JpaDroolsPdpsConnector(emfd);

        /*
         * Insert this PDP as not designated.  Initial standby state will be
         * either null or cold standby.   Demoting should transit state to
         * hot standby.
         */

        logger.debug("testHotStandby1: Inserting PDP={} as not designated", thisPdpId);
        Date yesterday = DateUtils.addDays(testTime.getDate(), -1);
        DroolsPdpImpl pdp = new DroolsPdpImpl(thisPdpId, false, 4, yesterday);
        conn.insertPdp(pdp);
        DroolsPdpEntity droolsPdpEntity = conn.getPdp(thisPdpId);
        logger.debug("testHotStandby1: After insertion, PDP={} has DESIGNATED={}",
                thisPdpId, droolsPdpEntity.isDesignated());
        assertFalse(droolsPdpEntity.isDesignated());

        logger.debug("testHotStandby1: Instantiating stateManagement object");
        StateManagement sm = new StateManagement(emfx, "dummy");
        sm.deleteAllStateManagementEntities();


        // Now we want to create a StateManagementFeature and initialize it.  It will be
        // discovered by the ActiveStandbyFeature when the election handler initializes.

        StateManagementFeatureApi smf = null;
        for (StateManagementFeatureApi feature : StateManagementFeatureApiConstants.getImpl().getList()) {
            ((PolicySessionFeatureApi) feature).globalInit(null, CONFIG_DIR);
            smf = feature;
            logger.debug("testHotStandby1 stateManagementFeature.getResourceName(): {}", smf.getResourceName());
            break;
        }
        assertNotNull(smf);

        // Create an ActiveStandbyFeature and initialize it. It will discover the StateManagementFeature
        // that has been created.
        ActiveStandbyFeatureApi activeStandbyFeature = null;
        for (ActiveStandbyFeatureApi feature : ActiveStandbyFeatureApiConstants.getImpl().getList()) {
            ((PolicySessionFeatureApi) feature).globalInit(null, CONFIG_DIR);
            activeStandbyFeature = feature;
            logger.debug("testHotStandby1 activeStandbyFeature.getResourceName(): {}",
                    activeStandbyFeature.getResourceName());
            break;
        }
        assertNotNull(activeStandbyFeature);


        logger.debug("testHotStandby1: Demoting PDP={}", thisPdpId);
        // demoting should cause state to transit to hotstandby
        smf.demote();


        logger.debug("testHotStandby1: Sleeping {} ms, to allow JpaDroolsPdpsConnector "
                + "time to check droolspdpentity table", SLEEP_TIME);
        sleep(SLEEP_TIME);


        // Verify that this formerly un-designated PDP in HOT_STANDBY is now designated and providing service.

        droolsPdpEntity = conn.getPdp(thisPdpId);
        logger.debug("testHotStandby1: After sm.demote() invoked, DESIGNATED= {} "
                + "for PDP= {}", droolsPdpEntity.isDesignated(), thisPdpId);
        assertTrue(droolsPdpEntity.isDesignated());
        String standbyStatus = smf.getStandbyStatus(thisPdpId);
        logger.debug("testHotStandby1: After demotion, PDP= {} "
                + "has standbyStatus= {}", thisPdpId, standbyStatus);
        assertTrue(standbyStatus != null  &&  standbyStatus.equals(StateManagement.PROVIDING_SERVICE));

        logger.debug("testHotStandby1: Stopping policyManagementRunner");

        logger.debug("\n\ntestHotStandby1: Exiting\n\n");
    }

    /*
     * Tests hot standby when two PDPs are involved.
     */

    /**
     * Test hot standby 2.
     *
     * @throws Exception exception
     */
    @Test
    public void testHotStandby2() throws Exception {

        logger.info("\n\ntestHotStandby2: Entering\n\n");

        final String thisPdpId = activeStandbyProperties
                .getProperty(ActiveStandbyProperties.NODE_NAME);

        DroolsPdpsConnector conn = new JpaDroolsPdpsConnector(emfd);


        // Insert a PDP that's designated but not current.

        String activePdpId = "pdp2";
        logger.info("testHotStandby2: Inserting PDP={} as stale, designated PDP", activePdpId);
        Date yesterday = DateUtils.addDays(testTime.getDate(), -1);
        DroolsPdp pdp = new DroolsPdpImpl(activePdpId, true, 4, yesterday);
        conn.insertPdp(pdp);
        DroolsPdpEntity droolsPdpEntity = conn.getPdp(activePdpId);
        logger.info("testHotStandby2: After insertion, PDP= {}, which is "
                + "not current, has DESIGNATED= {}", activePdpId, droolsPdpEntity.isDesignated());
        assertTrue(droolsPdpEntity.isDesignated());

        /*
         * Promote the designated PDP.
         *
         * We have a chicken and egg problem here: we need a StateManagement
         * object to invoke the deleteAllStateManagementEntities method.
         */


        logger.info("testHotStandby2: Promoting PDP={}", activePdpId);
        StateManagement sm = new StateManagement(emfx, "dummy");
        sm.deleteAllStateManagementEntities();


        sm = new StateManagement(emfx, activePdpId);//pdp2

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
        assertFalse(droolsPdpEntity.isDesignated());


        // Now we want to create a StateManagementFeature and initialize it.  It will be
        // discovered by the ActiveStandbyFeature when the election handler initializes.

        StateManagementFeatureApi sm2 = null;
        for (StateManagementFeatureApi feature : StateManagementFeatureApiConstants.getImpl().getList()) {
            ((PolicySessionFeatureApi) feature).globalInit(null, CONFIG_DIR);
            sm2 = feature;
            logger.debug("testHotStandby2 stateManagementFeature.getResourceName(): {}", sm2.getResourceName());
            break;
        }
        assertNotNull(sm2);

        // Create an ActiveStandbyFeature and initialize it. It will discover the StateManagementFeature
        // that has been created.
        ActiveStandbyFeatureApi activeStandbyFeature = null;
        for (ActiveStandbyFeatureApi feature : ActiveStandbyFeatureApiConstants.getImpl().getList()) {
            ((PolicySessionFeatureApi) feature).globalInit(null, CONFIG_DIR);
            activeStandbyFeature = feature;
            logger.debug("testHotStandby2 activeStandbyFeature.getResourceName(): {}",
                    activeStandbyFeature.getResourceName());
            break;
        }
        assertNotNull(activeStandbyFeature);

        logger.info("testHotStandby2: Runner started; Sleeping {} "
                + "ms before promoting/demoting", INTERRUPT_RECOVERY_TIME);
        sleep(INTERRUPT_RECOVERY_TIME);

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
                + "time to check droolspdpentity table", SLEEP_TIME);
        sleep(SLEEP_TIME);

        /*
         * Verify that this PDP, demoted to HOT_STANDBY, is now
         * re-designated and providing service.
         */

        droolsPdpEntity = conn.getPdp(thisPdpId);
        logger.info("testHotStandby2: After demoting PDP={}"
                + ", DESIGNATED= {}"
                + " for PDP= {}", activePdpId, droolsPdpEntity.isDesignated(), thisPdpId);
        assertTrue(droolsPdpEntity.isDesignated());
        standbyStatus = sm2.getStandbyStatus(thisPdpId);
        logger.info("testHotStandby2: After demoting PDP={}"
                + ", PDP={} has standbyStatus= {}",
                activePdpId, thisPdpId, standbyStatus);
        assertTrue(standbyStatus != null
                && standbyStatus.equals(StateManagement.PROVIDING_SERVICE));

        logger.info("testHotStandby2: Stopping policyManagementRunner");

        logger.info("\n\ntestHotStandby2: Exiting\n\n");
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

    /**
     * Test locking.
     *
     * @throws Exception exception
     */
    @Test
    public void testLocking1() throws Exception {
        logger.debug("testLocking1: Entry");

        final String thisPdpId = activeStandbyProperties
                .getProperty(ActiveStandbyProperties.NODE_NAME);

        DroolsPdpsConnector conn = new JpaDroolsPdpsConnector(emfd);

        /*
         * Insert this PDP as designated.  Initial standby state will be
         * either null or cold standby.
         */

        logger.debug("testLocking1: Inserting PDP= {} as designated", thisPdpId);
        DroolsPdpImpl pdp = new DroolsPdpImpl(thisPdpId, true, 4, testTime.getDate());
        conn.insertPdp(pdp);
        DroolsPdpEntity droolsPdpEntity = conn.getPdp(thisPdpId);
        logger.debug("testLocking1: After insertion, PDP= {} has DESIGNATED= {}",
                thisPdpId, droolsPdpEntity.isDesignated());
        assertTrue(droolsPdpEntity.isDesignated());

        logger.debug("testLocking1: Instantiating stateManagement object");
        StateManagement smDummy = new StateManagement(emfx, "dummy");
        smDummy.deleteAllStateManagementEntities();

        // Now we want to create a StateManagementFeature and initialize it.  It will be
        // discovered by the ActiveStandbyFeature when the election handler initializes.

        StateManagementFeatureApi sm = null;
        for (StateManagementFeatureApi feature : StateManagementFeatureApiConstants.getImpl().getList()) {
            ((PolicySessionFeatureApi) feature).globalInit(null, CONFIG_DIR);
            sm = feature;
            logger.debug("testLocking1 stateManagementFeature.getResourceName(): {}", sm.getResourceName());
            break;
        }
        assertNotNull(sm);

        // Create an ActiveStandbyFeature and initialize it. It will discover the StateManagementFeature
        // that has been created.
        ActiveStandbyFeatureApi activeStandbyFeature = null;
        for (ActiveStandbyFeatureApi feature : ActiveStandbyFeatureApiConstants.getImpl().getList()) {
            ((PolicySessionFeatureApi) feature).globalInit(null, CONFIG_DIR);
            activeStandbyFeature = feature;
            logger.debug("testLocking1 activeStandbyFeature.getResourceName(): {}",
                    activeStandbyFeature.getResourceName());
            break;
        }
        assertNotNull(activeStandbyFeature);

        logger.debug("testLocking1: Runner started; Sleeping "
                + INTERRUPT_RECOVERY_TIME + "ms before promoting PDP={}",
                thisPdpId);
        sleep(INTERRUPT_RECOVERY_TIME);

        logger.debug("testLocking1: Promoting PDP={}", thisPdpId);
        sm.promote();

        logger.debug("testLocking1: Sleeping {} ms, to allow time for "
                + "policy-management.Main class to come up, designated= {}",
                SLEEP_TIME, conn.getPdp(thisPdpId).isDesignated());
        sleep(SLEEP_TIME);

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

        logger.debug("testLocking1: sleeping" + ELECTION_WAIT_SLEEP_TIME
                + " to allow election handler to re-promote PDP={}", thisPdpId);
        sleep(ELECTION_WAIT_SLEEP_TIME);

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
        sleep(ELECTION_WAIT_SLEEP_TIME);

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

    /**
     * Test locking 2.
     *
     * @throws Exception exception
     */
    @Test
    public void testLocking2() throws Exception {

        logger.debug("\n\ntestLocking2: Entering\n\n");

        final String thisPdpId = activeStandbyProperties
                .getProperty(ActiveStandbyProperties.NODE_NAME);

        DroolsPdpsConnector conn = new JpaDroolsPdpsConnector(emfd);

        /*
         * Insert this PDP as designated.  Initial standby state will be
         * either null or cold standby.   Demoting should transit state to
         * hot standby.
         */

        logger.debug("testLocking2: Inserting PDP= {} as designated", thisPdpId);
        DroolsPdpImpl pdp = new DroolsPdpImpl(thisPdpId, true, 3, testTime.getDate());
        conn.insertPdp(pdp);
        DroolsPdpEntity droolsPdpEntity = conn.getPdp(thisPdpId);
        logger.debug("testLocking2: After insertion, PDP= {} has DESIGNATED= {}",
                thisPdpId, droolsPdpEntity.isDesignated());
        assertTrue(droolsPdpEntity.isDesignated());

        logger.debug("testLocking2: Instantiating stateManagement object and promoting PDP={}", thisPdpId);
        StateManagement smDummy = new StateManagement(emfx, "dummy");
        smDummy.deleteAllStateManagementEntities();

        // Now we want to create a StateManagementFeature and initialize it.  It will be
        // discovered by the ActiveStandbyFeature when the election handler initializes.

        StateManagementFeatureApi sm = null;
        for (StateManagementFeatureApi feature : StateManagementFeatureApiConstants.getImpl().getList()) {
            ((PolicySessionFeatureApi) feature).globalInit(null, CONFIG_DIR);
            sm = feature;
            logger.debug("testLocking2 stateManagementFeature.getResourceName(): {}", sm.getResourceName());
            break;
        }
        assertNotNull(sm);

        // Create an ActiveStandbyFeature and initialize it. It will discover the StateManagementFeature
        // that has been created.
        ActiveStandbyFeatureApi activeStandbyFeature = null;
        for (ActiveStandbyFeatureApi feature : ActiveStandbyFeatureApiConstants.getImpl().getList()) {
            ((PolicySessionFeatureApi) feature).globalInit(null, CONFIG_DIR);
            activeStandbyFeature = feature;
            logger.debug("testLocking2 activeStandbyFeature.getResourceName(): {}",
                    activeStandbyFeature.getResourceName());
            break;
        }
        assertNotNull(activeStandbyFeature);

        /*
         * Insert another PDP as not designated.  Initial standby state will be
         * either null or cold standby.   Demoting should transit state to
         * hot standby.
         */

        String standbyPdpId = "pdp2";
        logger.debug("testLocking2: Inserting PDP= {} as not designated", standbyPdpId);
        Date yesterday = DateUtils.addDays(testTime.getDate(), -1);
        pdp = new DroolsPdpImpl(standbyPdpId, false, 4, yesterday);
        conn.insertPdp(pdp);
        droolsPdpEntity = conn.getPdp(standbyPdpId);
        logger.debug("testLocking2: After insertion, PDP={} has DESIGNATED= {}",
                standbyPdpId, droolsPdpEntity.isDesignated());
        assertFalse(droolsPdpEntity.isDesignated());

        logger.debug("testLocking2: Demoting PDP= {}", standbyPdpId);
        final StateManagement sm2 = new StateManagement(emfx, standbyPdpId);

        logger.debug("testLocking2: Runner started; Sleeping {} ms "
                + "before promoting/demoting", INTERRUPT_RECOVERY_TIME);
        sleep(INTERRUPT_RECOVERY_TIME);

        logger.debug("testLocking2: Promoting PDP= {}", thisPdpId);
        sm.promote();

        // demoting PDP should ensure that state transits to hotstandby
        logger.debug("testLocking2: Demoting PDP={}", standbyPdpId);
        sm2.demote();

        logger.debug("testLocking2: Sleeping {} ms, to allow time for to come up", SLEEP_TIME);
        sleep(SLEEP_TIME);

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
                + " to allow election handler to re-promote PDP={}", ELECTION_WAIT_SLEEP_TIME, thisPdpId);
        sleep(ELECTION_WAIT_SLEEP_TIME);

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
        assertFalse(conn.getPdp(standbyPdpId).isDesignated());

        logger.debug("\n\ntestLocking2: Exiting\n\n");
    }

    private static Properties loadStateManagementProperties() throws IOException {
        try (FileInputStream input = new FileInputStream(CONFIG_DIR + "/feature-state-management.properties")) {
            Properties props = new Properties();
            props.load(input);
            return props;
        }
    }

    private static Properties loadActiveStandbyProperties() throws IOException {
        try (FileInputStream input =
                        new FileInputStream(CONFIG_DIR + "/feature-active-standby-management.properties")) {
            Properties props = new Properties();
            props.load(input);
            return props;
        }
    }

    private void sleep(long sleepms) throws InterruptedException {
        testTime.waitFor(sleepms);
    }
}
