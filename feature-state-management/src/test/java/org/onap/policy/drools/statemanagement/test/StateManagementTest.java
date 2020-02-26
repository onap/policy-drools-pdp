/*-
 * ============LICENSE_START=======================================================
 * policy-persistence
 * ================================================================================
 * Copyright (C) 2017-2020 AT&T Intellectual Property. All rights reserved.
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
import java.io.IOException;
import java.util.Properties;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;
import javax.ws.rs.core.Response;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.onap.policy.common.im.StateManagement;
import org.onap.policy.drools.core.PolicySessionFeatureApi;
import org.onap.policy.drools.statemanagement.DbAudit;
import org.onap.policy.drools.statemanagement.IntegrityMonitorRestManager;
import org.onap.policy.drools.statemanagement.RepositoryAudit;
import org.onap.policy.drools.statemanagement.StateManagementFeatureApi;
import org.onap.policy.drools.statemanagement.StateManagementFeatureApiConstants;
import org.onap.policy.drools.statemanagement.StateManagementProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StateManagementTest {

    // get an instance of logger
    private static Logger  logger = LoggerFactory.getLogger(StateManagementTest.class);

    private static EntityManagerFactory emf;
    private static EntityManager em;

    StateManagementFeatureApi stateManagementFeature;

    /**
     * Setup the class.
     * All you need to do here is create an instance of StateManagementFeature class.  Then,
     * check it initial state and the state after diableFailed() and promote()
     *
     * @throws Exception exception
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
        em.close();
        emf.close();
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

        DbAudit.setIsJunit(true);

        Properties fsmProperties = new Properties();
        fsmProperties.load(new FileInputStream(new File(
                configDir + "/feature-state-management.properties")));
        String thisPdpId = fsmProperties
                .getProperty(StateManagementProperties.NODE_NAME);

        StateManagementFeatureApi stateManagementFeature = null;
        for (StateManagementFeatureApi feature : StateManagementFeatureApiConstants.getImpl().getList()) {
            ((PolicySessionFeatureApi) feature).globalInit(null, configDir);
            stateManagementFeature = feature;
            logger.debug("testStateManagementOperation stateManagementFeature.getResourceName(): "
                + stateManagementFeature.getResourceName());
            break;
        }
        if (stateManagementFeature == null) {
            String msg = "testStateManagementOperation failed to initialize.  "
                    + "Unable to get instance of StateManagementFeatureApi "
                    + "with resourceID: " + thisPdpId;
            logger.error(msg);
            logger.debug(msg);
        }

        String admin = stateManagementFeature.getAdminState();
        String oper = stateManagementFeature.getOpState();
        String avail = stateManagementFeature.getAvailStatus();
        String standby = stateManagementFeature.getStandbyStatus();

        logger.debug("admin = {}", admin);
        logger.debug("oper = {}", oper);
        logger.debug("avail = {}", avail);
        logger.debug("standby = {}", standby);

        assertTrue("Admin state not unlocked after initialization", admin.equals(StateManagement.UNLOCKED));
        assertTrue("Operational state not enabled after initialization", oper.equals(StateManagement.ENABLED));

        try {
            stateManagementFeature.disableFailed();
        } catch (Exception e) {
            logger.error(e.getMessage());
            assertTrue(e.getMessage(), false);
        }

        admin = stateManagementFeature.getAdminState();
        oper = stateManagementFeature.getOpState();
        avail = stateManagementFeature.getAvailStatus();
        standby = stateManagementFeature.getStandbyStatus();

        logger.debug("after disableFailed()");
        logger.debug("admin = {}", admin);
        logger.debug("oper = {}", oper);
        logger.debug("avail = {}", avail);
        logger.debug("standby = {}", standby);

        assertTrue("Operational state not disabled after disableFailed()", oper.equals(StateManagement.DISABLED));
        assertTrue("Availability status not failed after disableFailed()", avail.equals(StateManagement.FAILED));


        try {
            stateManagementFeature.promote();
        } catch (Exception e) {
            logger.debug(e.getMessage());
        }

        admin = stateManagementFeature.getAdminState();
        oper = stateManagementFeature.getOpState();
        avail = stateManagementFeature.getAvailStatus();
        standby = stateManagementFeature.getStandbyStatus();

        logger.debug("after promote()");
        logger.debug("admin = {}", admin);
        logger.debug("oper = {}", oper);
        logger.debug("avail = {}", avail);
        logger.debug("standby = {}", standby);

        assertTrue("Standby status not coldstandby after promote()", standby.equals(StateManagement.COLD_STANDBY));

        /**************Repository Audit Test. **************/
        logger.debug("\n\ntestStateManagementOperation: Repository Audit\n\n");
        try {
            StateManagementProperties.initProperties(fsmProperties);
            RepositoryAudit repositoryAudit = (RepositoryAudit) RepositoryAudit.getInstance();
            repositoryAudit.invoke(fsmProperties);

            //Should not throw an IOException in Linux Foundation env
            assertTrue(true);
        } catch (IOException e) {
            //Note: this catch is here because in a local environment mvn will not run in
            //in the temp directory
            logger.debug("testSubsytemTest RepositoryAudit IOException", e);
        } catch (InterruptedException e) {
            assertTrue(false);
            logger.debug("testSubsytemTest RepositoryAudit InterruptedException", e);
        }

        /*****************Db Audit Test. ***************/
        logger.debug("\n\ntestStateManagementOperation: DB Audit\n\n");

        try {
            DbAudit dbAudit = (DbAudit) DbAudit.getInstance();
            dbAudit.invoke(fsmProperties);

            assertTrue(true);
        } catch (Exception e) {
            assertTrue(false);
            logger.debug("testSubsytemTest DbAudit exception", e);
        }

        /*************IntegrityMonitorRestManager Test. *************/
        logger.debug("\n\ntestStateManagementOperation: IntegrityMonitorRestManager\n\n");
        IntegrityMonitorRestManager integrityMonitorRestManager = new IntegrityMonitorRestManager();

        Response response = integrityMonitorRestManager.test();
        logger.debug("\n\nIntegrityMonitorRestManager response: " + response.toString());

        assertTrue(response.toString().contains("status=500"));

        //All done
        logger.debug("\n\ntestStateManagementOperation: Exiting\n\n");
    }

    /**
     * This method initializes and cleans the DB so that PDP-D will be able to
     * store fresh records in the DB.
     */
    public static void initializeDb() {

        logger.debug("initializeDb: Entering");

        Properties cleanProperties = new Properties();
        cleanProperties.put(StateManagementProperties.DB_DRIVER,"org.h2.Driver");
        cleanProperties.put(StateManagementProperties.DB_URL, "jdbc:h2:mem:statemanagement");
        cleanProperties.put(StateManagementProperties.DB_USER, "sa");
        cleanProperties.put(StateManagementProperties.DB_PWD, "");

        emf = Persistence.createEntityManagerFactory("junitPU", cleanProperties);

        em = emf.createEntityManager();
        // Start a transaction
        EntityTransaction et = em.getTransaction();

        et.begin();

        // Clean up the DB
        em.createQuery("Delete from StateManagementEntity").executeUpdate();
        em.createQuery("Delete from ForwardProgressEntity").executeUpdate();
        em.createQuery("Delete from ResourceRegistrationEntity").executeUpdate();

        // commit transaction
        et.commit();

        logger.debug("initializeDb: Exiting");
    }
}
