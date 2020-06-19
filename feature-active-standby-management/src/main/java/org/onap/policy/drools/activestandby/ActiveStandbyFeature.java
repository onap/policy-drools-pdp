/*
 * ============LICENSE_START=======================================================
 * feature-active-standby-management
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

package org.onap.policy.drools.activestandby;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import org.onap.policy.common.im.MonitorTime;
import org.onap.policy.drools.core.PolicySessionFeatureApi;
import org.onap.policy.drools.features.PolicyEngineFeatureApi;
import org.onap.policy.drools.statemanagement.StateManagementFeatureApi;
import org.onap.policy.drools.statemanagement.StateManagementFeatureApiConstants;
import org.onap.policy.drools.system.PolicyEngine;
import org.onap.policy.drools.system.PolicyEngineConstants;
import org.onap.policy.drools.utils.PropertyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * If this feature is supported, there is a single instance of it.
 * It adds persistence to Drools sessions, but it is also intertwined with
 * active/standby state management and IntegrityMonitor. For now, they are
 * all treated as a single feature, but it would be nice to separate them.
 *
 * <p>The bulk of the code here was once in other classes, such as
 * 'PolicyContainer' and 'Main'. It was moved here as part of making this
 * a separate optional feature.
 */
public class ActiveStandbyFeature implements ActiveStandbyFeatureApi,
    PolicySessionFeatureApi, PolicyEngineFeatureApi {
    // get an instance of logger
    private static final Logger logger =
            LoggerFactory.getLogger(ActiveStandbyFeature.class);

    private static DroolsPdp myPdp;
    private static Object myPdpSync = new Object();
    private static DroolsPdpsElectionHandler electionHandler;

    private StateManagementFeatureApi stateManagementFeature;

    public static final int SEQ_NUM = 1;


    /*========================*/
    /* 'FeatureAPI' interface */
    /*========================*/

    /**
     * {@inheritDoc}.
     */
    @Override
    public int getSequenceNumber() {
        return SEQ_NUM;
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public void globalInit(String[] args, String configDir) {
        // This must come first since it initializes myPdp
        initializePersistence(configDir);

        for (StateManagementFeatureApi feature : StateManagementFeatureApiConstants.getImpl().getList()) {
            if (feature.getResourceName().equals(myPdp.getPdpId())) {
                logger.debug("ActiveStandbyFeature.globalInit: Found StateManagementFeature"
                                + " with resourceName: {}", myPdp.getPdpId());
                stateManagementFeature = feature;
                break;
            }
        }
        if (stateManagementFeature == null) {
            logger.debug("ActiveStandbyFeature failed to initialize.  "
                            + "Unable to get instance of StateManagementFeatureApi "
                            + "with resourceID: {}", myPdp.getPdpId());
            logger.error("ActiveStandbyFeature failed to initialize.  "
                    + "Unable to get instance of StateManagementFeatureApi "
                    + "with resourceID: {}", myPdp.getPdpId());
            //
            // Cannot add observer since stateManagementFeature is null
            //
            return;
        }



        //Create an instance of the Observer
        PmStandbyStateChangeNotifier pmNotifier = new PmStandbyStateChangeNotifier();

        //Register the PMStandbyStateChangeNotifier Observer
        stateManagementFeature.addObserver(pmNotifier);
        logger.debug("ActiveStandbyFeature.globalInit() exit");
    }


    /**
     * {@inheritDoc}.
     */
    @Override
    public boolean afterStart(PolicyEngine engine) {
        // ASSERTION: engine == PolicyEngine.manager
        PolicyEngineConstants.getManager().lock();
        return false;
    }

    /**
     * Read in the persistence properties, determine whether persistence is
     * enabled or disabled, and initialize persistence if enabled.
     */
    private static void initializePersistence(String configDir) {
        //Get the Active Standby properties
        try {
            Properties activeStandbyProperties =
                    PropertyUtil.getProperties(configDir + "/feature-active-standby-management.properties");
            ActiveStandbyProperties.initProperties(activeStandbyProperties);
            logger.info("initializePersistence: ActiveStandbyProperties success");
        } catch (IOException e) {
            logger.error("ActiveStandbyFeature: initializePersistence ActiveStandbyProperties", e);
        }

        DroolsPdpsConnector conn = getDroolsPdpsConnector("activeStandbyPU");
        String resourceName = ActiveStandbyProperties.getProperty(ActiveStandbyProperties.NODE_NAME);
        if (resourceName == null) {
            throw new NullPointerException();
        }

        /*
         * In a JUnit test environment, one or more PDPs may already have been
         * inserted in the DB, so we need to check for this.
         */
        DroolsPdp existingPdp = conn.getPdp(resourceName);
        if (existingPdp != null) {
            logger.info("Found existing PDP record, pdpId={} isDesignated={}, updatedDate={}",
                     existingPdp.getPdpId(), existingPdp.isDesignated(), existingPdp.getUpdatedDate());
            myPdp = existingPdp;
        }

        synchronized (myPdpSync) {
            if (myPdp == null) {

                myPdp = new DroolsPdpImpl(resourceName, false, 4, MonitorTime.getInstance().getDate());
            }
            String siteName = ActiveStandbyProperties.getProperty(ActiveStandbyProperties.SITE_NAME);
            if (siteName == null) {
                siteName = "";
            } else {
                siteName = siteName.trim();
            }
            myPdp.setSite(siteName);
            if (electionHandler == null) {
                electionHandler = new DroolsPdpsElectionHandler(conn, myPdp);
            }
        }
        logger.info("\n\nThis controller is a standby, waiting to be chosen as primary...\n\n");
    }


    /**
     * Moved code to instantiate a JpaDroolsPdpsConnector object from main() to
     * this method, so it can also be accessed from StandbyStateChangeNotifier
     * class.
     *
     * @param pu string
     * @return connector object
     */
    public static DroolsPdpsConnector getDroolsPdpsConnector(String pu) {

        Map<String, Object> propMap = new HashMap<>();
        propMap.put("javax.persistence.jdbc.driver", ActiveStandbyProperties
                .getProperty(ActiveStandbyProperties.DB_DRIVER));
        propMap.put("javax.persistence.jdbc.url",
                ActiveStandbyProperties.getProperty(ActiveStandbyProperties.DB_URL));
        propMap.put("javax.persistence.jdbc.user", ActiveStandbyProperties
                .getProperty(ActiveStandbyProperties.DB_USER));
        propMap.put("javax.persistence.jdbc.password",
                ActiveStandbyProperties.getProperty(ActiveStandbyProperties.DB_PWD));

        EntityManagerFactory emf = Persistence.createEntityManagerFactory(
                pu, propMap);
        return new JpaDroolsPdpsConnector(emf);
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public String getPdpdNowActive() {
        return electionHandler.getPdpdNowActive();
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public String getPdpdLastActive() {
        return electionHandler.getPdpdLastActive();
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public String getResourceName() {
        return myPdp.getPdpId();
    }
}
