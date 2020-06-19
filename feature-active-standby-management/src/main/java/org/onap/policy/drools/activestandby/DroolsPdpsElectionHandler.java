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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import org.onap.policy.common.im.MonitorTime;
import org.onap.policy.common.im.StateManagement;
import org.onap.policy.common.utils.time.CurrentTime;
import org.onap.policy.drools.statemanagement.StateManagementFeatureApi;
import org.onap.policy.drools.statemanagement.StateManagementFeatureApiConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DroolsPdpsElectionHandler implements ThreadRunningChecker {
    private static final String RUN_PRIMARY_MSG = "DesignatedWaiter.run mostRecentPrimary = {}";

    // get an instance of logger
    private static final Logger  logger = LoggerFactory.getLogger(DroolsPdpsElectionHandler.class);
    private DroolsPdpsConnector pdpsConnector;
    private Object checkWaitTimerLock = new Object();
    private Object designationWaiterLock = new Object();

    /*
     * Must be static, so it can be referenced by JpaDroolsPdpsConnector,
     * without requiring a reference to the election handler instantiation.
     */
    private static DroolsPdp myPdp;

    private Date waitTimerLastRunDate;

    // The interval between runs of the DesignationWaiter
    private int pdpUpdateInterval;

    private volatile boolean isDesignated;

    private String pdpdNowActive;
    private String pdpdLastActive;

    /*
     * Start allSeemsWell with a value of null so that, on the first run
     * of the checkWaitTimer it will set the value in IntegrityMonitor
     * regardless of whether it needs to be set to true or false.
     */
    private Boolean allSeemsWell = null;

    private StateManagementFeatureApi stateManagementFeature;

    private final CurrentTime currentTime = MonitorTime.getInstance();

    private static boolean isUnitTesting = false;
    private static boolean isStalled = false;

    /**
     * Constructor.
     *
     * @param pdps connectors
     * @param myPdp pdp
     */
    public DroolsPdpsElectionHandler(DroolsPdpsConnector pdps, DroolsPdp myPdp) {
        if (pdps == null) {
            logger.error("DroolsPdpsElectinHandler(): pdpsConnector==null");
            throw new IllegalArgumentException("DroolsPdpsElectinHandler(): pdpsConnector==null");
        }
        if (myPdp == null) {
            logger.error("DroolsPdpsElectinHandler(): droolsPdp==null");
            throw new IllegalArgumentException("DroolsPdpsElectinHandler(): DroolsPdp==null");
        }

        pdpdNowActive = null;
        pdpdLastActive = null;
        this.pdpsConnector = pdps;
        setMyPdp(myPdp);
        this.isDesignated = false;

        // The interval between checks of the DesignationWaiter to be sure it is running.
        int pdpCheckInterval = 3000;
        try {
            pdpCheckInterval = Integer.parseInt(ActiveStandbyProperties.getProperty(
                    ActiveStandbyProperties.PDP_CHECK_INVERVAL));
        } catch (Exception e) {
            logger.error("Could not get pdpCheckInterval property. Using default {}", pdpCheckInterval, e);
        }
        pdpUpdateInterval = 2000;
        try {
            pdpUpdateInterval = Integer.parseInt(ActiveStandbyProperties.getProperty(
                    ActiveStandbyProperties.PDP_UPDATE_INTERVAL));
        } catch (Exception e) {
            logger.error("Could not get pdpUpdateInterval property. Using default {} ", pdpUpdateInterval, e);
        }

        Date now = currentTime.getDate();

        // Retrieve the ms since the epoch
        final long nowMs = now.getTime();

        // Create the timer which will update the updateDate in DroolsPdpEntity table.
        // This is the heartbeat
        Timer updateWorker = Factory.getInstance().makeTimer();

        // Schedule the TimerUpdateClass to run at 100 ms and run at pdpCheckInterval ms thereafter
        // NOTE: The first run of the TimerUpdateClass results in myPdp being added to the
        // drools droolsPdpEntity table.
        updateWorker.scheduleAtFixedRate(new TimerUpdateClass(), 100, pdpCheckInterval);

        // Create the timer which will run the election algorithm
        Timer waitTimer = Factory.getInstance().makeTimer();

        // Schedule it to start in startMs ms
        // (so it will run after the updateWorker and run at pdpUpdateInterval ms thereafter
        long startMs = getDWaiterStartMs();
        DesignationWaiter designationWaiter = new DesignationWaiter();
        waitTimer.scheduleAtFixedRate(designationWaiter, startMs, pdpUpdateInterval);
        waitTimerLastRunDate = new Date(nowMs + startMs);

        //Get the StateManagementFeature instance

        for (StateManagementFeatureApi feature : StateManagementFeatureApiConstants.getImpl().getList()) {
            if (feature.getResourceName().equals(myPdp.getPdpId())) {
                logger.debug("DroolsPdpsElectionHandler: Found StateManagementFeature"
                                + " with resourceName: {}", myPdp.getPdpId());
                stateManagementFeature = feature;
                break;
            }
        }
        if (stateManagementFeature == null) {
            logger.error("DroolsPdpsElectionHandler failed to initialize.  "
                    + "Unable to get instance of StateManagementFeatureApi "
                    + "with resourceID: {}", myPdp.getPdpId());
        }
    }

    private static void setMyPdp(DroolsPdp myPdp) {
        DroolsPdpsElectionHandler.myPdp = myPdp;
    }

    public static void setIsUnitTesting(boolean val) {
        isUnitTesting = val;
    }

    public static void setIsStalled(boolean val) {
        isStalled = val;
    }

    /**
     * When the JpaDroolsPdpsConnector.standDown() method is invoked, it needs
     * access to myPdp, so it can keep its designation status in sync with the
     * DB.
     *
     * @param designated is designated value
     */
    public static void setMyPdpDesignated(boolean designated) {
        logger.debug("setMyPdpDesignated: designated= {}", designated);
        myPdp.setDesignated(designated);
    }

    private class DesignationWaiter extends TimerTask {
        // get an instance of logger
        private final Logger  logger = LoggerFactory.getLogger(DesignationWaiter.class);

        @Override
        public void run() {
            try {
                logger.debug("DesignatedWaiter.run: Entering");

                //This is for testing the checkWaitTimer
                if (isUnitTesting && isStalled) {
                    logger.debug("DesignatedWaiter.run: isUnitTesting = {} isStalled = {}",
                                    isUnitTesting, isStalled);
                    return;
                }

                synchronized (designationWaiterLock) {

                    logger.debug("DesignatedWaiter.run: Entering synchronized block");

                    //It is possible that multiple PDPs are designated lead.  So, we will make a list of all designated
                    //PDPs and then decide which one really should be designated at the end.
                    List<DroolsPdp> listOfDesignated = new ArrayList<>();

                    Collection<DroolsPdp> pdps = pdpsConnector.getDroolsPdps();

                    logger.debug("DesignatedWaiter.run: pdps.size= {}", pdps.size());

                    //This is only true if all designated PDPs have failed
                    allPdpsFailed(pdps, listOfDesignated);

                    /*
                     * We have checked the four combinations of isDesignated and isCurrent.  Where appropriate,
                     * we added the PDPs to the potential list of designated pdps
                     *
                     * We need to give priority to pdps on the same site that is currently being used
                     * First, however, we must sanitize the list of designated to make sure their are
                     * only designated members or non-designated members.  There should not be both in
                     * the list. Because there are real time delays, it is possible that both types could
                     * be on the list.
                     */

                    listOfDesignated = santizeDesignatedList(listOfDesignated);

                    /*
                     * We need to figure out the last pdp that was the primary so we can get the last site
                     * name and the last session numbers.  We need to create a "dummy" droolspdp since
                     * it will be used in later comparisons and cannot be null.
                     */

                    DroolsPdp mostRecentPrimary = computeMostRecentPrimary(pdps, listOfDesignated);

                    if (mostRecentPrimary != null) {
                        pdpdLastActive = mostRecentPrimary.getPdpId();
                    }


                    /*
                     * It is possible to get here with more than one pdp designated and providing service. This normally
                     * occurs when there is a race condition with multiple nodes coming up at the same time. If that is
                     * the case we must determine which one is the one that should be designated and which one should
                     * be demoted.
                     *
                     * It is possible to have 0, 1, 2 or more but not all, or all designated.
                     *   If we have one designated and current, we chose it and are done
                     *   If we have 2 or more, but not all, we must determine which one is in the same site as
                     *   the previously designated pdp.
                     */
                    DroolsPdp designatedPdp = computeDesignatedPdp(listOfDesignated, mostRecentPrimary);

                    if (designatedPdp == null) {
                        logger.warn("WARNING: DesignatedWaiter.run: No viable PDP found to be Designated. "
                            + "designatedPdp still null.");
                        designateNoPdp();
                        return;
                    }

                    pdpdNowActive = designatedPdp.getPdpId();

                    if (pdpdNowActive.equals(myPdp.getPdpId())) {
                        logger.debug("DesignatedWaiter.run: designatedPdp is PDP={}", myPdp.getPdpId());
                        designateMyPdp();
                        return;
                    }

                    isDesignated = false;

                } // end synchronized
                logger.debug("DesignatedWaiter.run: myPdp: {}; Returning, isDesignated= {}",
                                isDesignated, myPdp.getPdpId());

                Date tmpDate = currentTime.getDate();
                logger.debug("DesignatedWaiter.run (end of run) waitTimerLastRunDate = {}", tmpDate);

                waitTimerLastRunDate = tmpDate;
                myPdp.setUpdatedDate(waitTimerLastRunDate);
                pdpsConnector.update(myPdp);

            } catch (Exception e) {
                logger.error("DesignatedWaiter.run caught an unexpected exception: ", e);
            }
        } // end run

        private void allPdpsFailed(Collection<DroolsPdp> pdps, List<DroolsPdp> listOfDesignated) {
            boolean designatedPdpHasFailed = pdpsConnector.hasDesignatedPdpFailed(pdps);
            logger.debug("DesignatedWaiter.run: designatedPdpHasFailed= {}", designatedPdpHasFailed);
            for (DroolsPdp pdp : pdps) {
                logger.debug("DesignatedWaiter.run: evaluating pdp ID: {}", pdp.getPdpId());

                /*
                 * Note: side effect of isPdpCurrent is that any stale but
                 * designated PDPs will be marked as un-designated.
                 */
                boolean isCurrent = pdpsConnector.isPdpCurrent(pdp);

                /*
                 * We can't use stateManagement.getStandbyStatus() here, because
                 * we need the standbyStatus, not for this PDP, but for the PDP
                 * being processed by this loop iteration.
                 */
                String standbyStatus = stateManagementFeature.getStandbyStatus(pdp.getPdpId());
                if (standbyStatus == null) {
                    // Treat this case as a cold standby -- if we
                    // abort here, no sessions will be created in a
                    // single-node test environment.
                    standbyStatus = StateManagement.COLD_STANDBY;
                }
                logger.debug("DesignatedWaiter.run: PDP= {},  isCurrent= {}", pdp.getPdpId(), isCurrent);

                adjustPdp(pdp, isCurrent, designatedPdpHasFailed, standbyStatus, listOfDesignated);


            } // end pdps loop
        }

        private void adjustPdp(DroolsPdp pdp, boolean isCurrent, boolean designatedPdpHasFailed, String standbyStatus,
                        List<DroolsPdp> listOfDesignated) {
            /*
             * There are 4 combinations of isDesignated and isCurrent.  We will examine each one in-turn
             * and evaluate the each pdp in the list of pdps against each combination.
             */
            if (pdp.isDesignated()) {
                /*
                 * This is the first combination of isDesignated and isCurrent
                 */
                if (isCurrent) {
                    pdpDesignatedCurrent(pdp, standbyStatus, listOfDesignated);

                /*
                 * The second combination of isDesignated and isCurrent
                 *
                 * PDP is designated but not current; it has failed.
                 * So we stand it down (it doesn't matter what
                 * its standbyStatus is). None of these go on the list.
                 */
                } else {
                    logger.debug("INFO: DesignatedWaiter.run: PDP= {} is currently "
                                    + "designated but is not current; "
                                    + "it has failed.  Standing down.  standbyStatus= {}",
                                    pdp.getPdpId(), standbyStatus);
                    pdpDesignatedNotCurrent(pdp);
                }

            } else {
                // NOT designated


                /*
                 * The third combination of isDesignated and isCurrent
                 * /*
                 * If a PDP is not currently designated but is providing service
                 * (erroneous, but recoverable) or hot standby
                 * we can add it to the list of possible designated if all the designated have failed
                 */
                if (isCurrent) {
                    pdpNotDesignatedCurrent(pdp, designatedPdpHasFailed, standbyStatus,
                                    listOfDesignated);

                /*
                 * The fourth combination of isDesignated and isCurrent
                 *
                 * We are not going to put any of these on the list since it appears they have failed.
                 *
                 */
                } else {
                    logger.debug("INFO: DesignatedWaiter.run: PDP= {} "
                                    + "designated= {}, current= {}, "
                                    + "designatedPdpHasFailed= {}, "
                                    + "standbyStatus= {}", pdp.getPdpId(),
                                    pdp.isDesignated(), false, designatedPdpHasFailed, standbyStatus);
                    pdpNotDesignatedNotCurrent(pdp, standbyStatus);
                }
            }
        }

        private void pdpDesignatedCurrent(DroolsPdp pdp, String standbyStatus, List<DroolsPdp> listOfDesignated) {
            //It is current, but it could have a standbystatus=coldstandby / hotstandby
            //If so, we need to stand it down and demote it
            if (!standbyStatus.equals(StateManagement.PROVIDING_SERVICE)) {
                if (pdp.getPdpId().equals(myPdp.getPdpId())) {
                    logger.debug("\n\nDesignatedWaiter.run: myPdp {} is current and designated, "
                                    + "butstandbystatus is not providingservice. "
                                    + " Executing stateManagement.demote()" + "\n\n", myPdp.getPdpId());
                    // So, we must demote it
                    try {
                        demoteMyPdp(pdp, standbyStatus);
                    } catch (Exception e) {
                        logger.error("DesignatedWaiter.run: myPdp: {} "
                                + "Caught Exception attempting to demote myPdp,"
                                + "message= {}", myPdp.getPdpId(), e);
                    }
                } else {
                    // Don't demote a remote PDP that is current.  It should catch itself
                    logger.debug("\n\nDesignatedWaiter.run: myPdp {} is current and designated, "
                                    + "but standbystatus is not providingservice. "
                                    + " Cannot execute stateManagement.demote() "
                                    + "since it it is not myPdp\n\n",
                                    myPdp.getPdpId());
                }

            } else {
                // If we get here, it is ok to be on the list
                logger.debug("DesignatedWaiter.run: PDP= {} is designated, "
                                + "current and {} Noting PDP as "
                                + "designated, standbyStatus= {}",
                                pdp.getPdpId(), standbyStatus, standbyStatus);
                listOfDesignated.add(pdp);
            }
        }

        private void demoteMyPdp(DroolsPdp pdp, String standbyStatus) throws Exception {
            /*
             * Keep the order like this. StateManagement is last since it triggers
             * controller shutdown. This will change isDesignated and it can enter another
             * if-combination below
             */
            pdpsConnector.standDownPdp(pdp.getPdpId());
            myPdp.setDesignated(false);
            isDesignated = false;
            if (!(standbyStatus.equals(StateManagement.HOT_STANDBY)
                    || standbyStatus.equals(StateManagement.COLD_STANDBY))) {
                /*
                 * Only demote it if it appears it has not already been demoted. Don't worry
                 * about synching with the topic endpoint states.  That is done by the
                 * refreshStateAudit
                 */
                stateManagementFeature.demote();
            }
        }

        private void pdpDesignatedNotCurrent(DroolsPdp pdp) {
            /*
             * Changes designated to 0 but it is still potentially providing service.
             * Will affect isDesignated, so, it can enter an if-combination below
             */
            pdpsConnector.standDownPdp(pdp.getPdpId());

            //need to change standbystatus to coldstandby
            if (pdp.getPdpId().equals(myPdp.getPdpId())) {
                logger.debug("\n\nDesignatedWaiter.run: myPdp {} is not Current. "
                                + " Executing stateManagement.disableFailed()\n\n", myPdp.getPdpId());
                // We found that myPdp is designated but not current
                // So, we must cause it to disableFail
                try {
                    myPdp.setDesignated(false);
                    pdpsConnector.setDesignated(myPdp, false);
                    isDesignated = false;
                    stateManagementFeature.disableFailed();
                } catch (Exception e) {
                    logger.error("DesignatedWaiter.run: myPdp: {} Caught Exception "
                            + "attempting to disableFail myPdp {}, message= {}",
                            myPdp.getPdpId(), myPdp.getPdpId(), e);
                }
            } else { //it is a remote PDP that is failed
                logger.debug("\n\nDesignatedWaiter.run: PDP {} is not Current. "
                                + " Executing stateManagement.disableFailed(otherResourceName)\n\n",
                                pdp.getPdpId());
                // We found a PDP is designated but not current
                // We already called standdown(pdp) which will change designated to false
                // Now we need to disableFail it to get its states in synch.  The standbyStatus
                // should equal coldstandby
                try {
                    stateManagementFeature.disableFailed(pdp.getPdpId());
                } catch (Exception e) {
                    logger.error("DesignatedWaiter.run: for PDP {}  Caught Exception attempting to "
                            + "disableFail({}), message= {}",
                            pdp.getPdpId(), pdp.getPdpId(), e);
                }

            }
        }

        private void pdpNotDesignatedCurrent(DroolsPdp pdp, boolean designatedPdpHasFailed, String standbyStatus,
                        List<DroolsPdp> listOfDesignated) {
            if (!(StateManagement.HOT_STANDBY.equals(standbyStatus)
                    || StateManagement.COLD_STANDBY.equals(standbyStatus))) {
                logger.debug("\n\nDesignatedWaiter.run: PDP {}"
                                + " is NOT designated but IS current and"
                                + " has a standbystatus= {}", pdp.getPdpId(), standbyStatus);
                // Since it is current, we assume it can adjust its own state.
                // We will demote if it is myPdp
                if (pdp.getPdpId().equals(myPdp.getPdpId())) {
                    //demote it
                    logger.debug("DesignatedWaiter.run: PDP {} going to "
                                    + "setDesignated = false and calling stateManagement.demote",
                                    pdp.getPdpId());
                    try {
                        //Keep the order like this.
                        //StateManagement is last since it triggers controller shutdown
                        pdpsConnector.setDesignated(myPdp, false);
                        myPdp.setDesignated(false);
                        isDesignated = false;
                        //This is definitely not a redundant call.
                        //It is attempting to correct a problem
                        stateManagementFeature.demote();
                        //recheck the standbystatus
                        standbyStatus = stateManagementFeature.getStandbyStatus(pdp.getPdpId());
                    } catch (Exception e) {
                        logger.error("DesignatedWaiter.run: myPdp: {} Caught Exception "
                                + "attempting to demote myPdp {}, message = {}",  myPdp.getPdpId(),
                                myPdp.getPdpId(), e);
                    }

                }
            }
            if (StateManagement.HOT_STANDBY.equals(standbyStatus) && designatedPdpHasFailed) {
                //add it to the list
                logger.debug("INFO: DesignatedWaiter.run: PDP= {}"
                                + " is not designated but is {} and designated PDP "
                                + "has failed.  standbyStatus= {}", pdp.getPdpId(),
                                standbyStatus, standbyStatus);
                listOfDesignated.add(pdp);
            }
        }

        private void pdpNotDesignatedNotCurrent(DroolsPdp pdp, String standbyStatus) {
            if (StateManagement.COLD_STANDBY.equals(standbyStatus)) {
                return;
            }

            //stand it down
            //disableFail it
            pdpsConnector.standDownPdp(pdp.getPdpId());
            if (pdp.getPdpId().equals(myPdp.getPdpId())) {
                /*
                 * I don't actually know how this condition could
                 * happen, but if it did, we would want to declare it
                 * failed.
                 */
                logger.debug("\n\nDesignatedWaiter.run: myPdp {} is !current and !designated, "
                                + " Executing stateManagement.disableFailed()\n\n",
                                myPdp.getPdpId());
                // So, we must disableFail it
                try {
                    //Keep the order like this.
                    //StateManagement is last since it triggers controller shutdown
                    pdpsConnector.setDesignated(myPdp, false);
                    myPdp.setDesignated(false);
                    isDesignated = false;
                    stateManagementFeature.disableFailed();
                } catch (Exception e) {
                    logger.error("DesignatedWaiter.run: myPdp: {} Caught Exception attempting to "
                            + "disableFail myPdp {}, message= {}",
                            myPdp.getPdpId(), myPdp.getPdpId(), e);
                }
            } else { //it is remote
                logger.debug("\n\nDesignatedWaiter.run: myPdp {} is !current and !designated, "
                                + " Executing stateManagement.disableFailed({})\n\n",
                                myPdp.getPdpId(), pdp.getPdpId());
                // We already called standdown(pdp) which will change designated to false
                // Now we need to disableFail it to get its states in sync.
                // StandbyStatus = coldstandby
                try {
                    stateManagementFeature.disableFailed(pdp.getPdpId());
                } catch (Exception e) {
                    logger.error("DesignatedWaiter.run: for PDP {}"
                            + " Caught Exception attempting to disableFail({})"
                            + ", message=", pdp.getPdpId(), pdp.getPdpId(), e);
                }
            }
        }

        private void designateNoPdp() {
            // Just to be sure the parameters are correctly set
            myPdp.setDesignated(false);
            pdpsConnector.setDesignated(myPdp, false);
            isDesignated = false;

            waitTimerLastRunDate = currentTime.getDate();
            logger.debug("DesignatedWaiter.run (designatedPdp == null) waitTimerLastRunDate = {}",
                            waitTimerLastRunDate);
            myPdp.setUpdatedDate(waitTimerLastRunDate);
            pdpsConnector.update(myPdp);
        }

        private void designateMyPdp() {
            /*
             * update function expects myPdp.isDesignated to be true.
             */
            try {
                //Keep the order like this.  StateManagement is last since it triggers controller init
                myPdp.setDesignated(true);
                myPdp.setDesignatedDate(currentTime.getDate());
                pdpsConnector.setDesignated(myPdp, true);
                isDesignated = true;
                String standbyStatus = stateManagementFeature.getStandbyStatus();
                if (!standbyStatus.equals(StateManagement.PROVIDING_SERVICE)) {
                    /*
                     * Only call promote if it is not already in the right state.  Don't worry about
                     * synching the lower level topic endpoint states.  That is done by the
                     * refreshStateAudit.
                     * Note that we need to fetch the session list from 'mostRecentPrimary'
                     * at this point -- soon, 'mostRecentPrimary' will be set to this host.
                     */
                    //this.sessions = mostRecentPrimary.getSessions();
                    stateManagementFeature.promote();
                }
            } catch (Exception e) {
                logger.error("ERROR: DesignatedWaiter.run: Caught Exception attempting to promote PDP={}"
                        + ", message=", myPdp.getPdpId(), e);
                myPdp.setDesignated(false);
                pdpsConnector.setDesignated(myPdp, false);
                isDesignated = false;
                //If you can't promote it, demote it
                try {
                    String standbyStatus = stateManagementFeature.getStandbyStatus();
                    if (!(standbyStatus.equals(StateManagement.HOT_STANDBY)
                            || standbyStatus.equals(StateManagement.COLD_STANDBY))) {
                        /*
                         * Only call demote if it is not already in the right state.  Don't worry about
                         * synching the lower level topic endpoint states.  That is done by the
                         * refreshStateAudit.
                         */
                        stateManagementFeature.demote();
                    }
                } catch (Exception e1) {
                    logger.error("ERROR: DesignatedWaiter.run: Caught StandbyStatusException "
                            + "attempting to promote then demote PDP={}, message=",
                            myPdp.getPdpId(), e1);
                }

            }
            waitTimerLastRunDate = currentTime.getDate();
            logger.debug("DesignatedWaiter.run (designatedPdp.getPdpId().equals(myPdp.getPdpId())) "
                            + "waitTimerLastRunDate = {}", waitTimerLastRunDate);
            myPdp.setUpdatedDate(waitTimerLastRunDate);
            pdpsConnector.update(myPdp);
        }
    }

    /**
     * Sanitize designated list.
     *
     * @param listOfDesignated list of designated pdps
     * @return list of drools pdps
     */
    public List<DroolsPdp> santizeDesignatedList(List<DroolsPdp> listOfDesignated) {

        boolean containsDesignated = false;
        boolean containsHotStandby = false;
        List<DroolsPdp> listForRemoval = new ArrayList<>();
        for (DroolsPdp pdp : listOfDesignated) {
            logger.debug("DesignatedWaiter.run sanitizing: pdp = {}"
                            + " isDesignated = {}", pdp.getPdpId(), pdp.isDesignated());
            if (pdp.isDesignated()) {
                containsDesignated = true;
            } else {
                containsHotStandby = true;
                listForRemoval.add(pdp);
            }
        }
        if (containsDesignated && containsHotStandby) {
            //remove the hot standby from the list
            listOfDesignated.removeAll(listForRemoval);
        }
        return listOfDesignated;
    }

    /**
     * Compute most recent primary.
     *
     * @param pdps collection of pdps
     * @param listOfDesignated list of designated pdps
     * @return drools pdp object
     */
    public DroolsPdp computeMostRecentPrimary(Collection<DroolsPdp> pdps, List<DroolsPdp> listOfDesignated) {
        boolean containsDesignated = listOfDesignated.stream().anyMatch(DroolsPdp::isDesignated);

        DroolsPdp mostRecentPrimary = new DroolsPdpImpl(null, true, 1, new Date(0));
        mostRecentPrimary.setSite(null);
        logger.debug("DesignatedWaiter.run listOfDesignated.size() = {}", listOfDesignated.size());

        if (listOfDesignated.size() <= 1) {
            logger.debug("DesignatedWainter.run: listOfDesignated.size <=1");
            //Only one or none is designated or hot standby.  Choose the latest designated date
            mostRecentPrimary = getLatestDesignated(pdps, mostRecentPrimary);

        } else if (listOfDesignated.size() == pdps.size()) {
            logger.debug("DesignatedWainter.run: listOfDesignated.size = pdps.size() which is {}", pdps.size());
            //They are all designated or all hot standby.
            mostRecentPrimary = getBestDesignated(pdps, containsDesignated);

        } else {
            logger.debug("DesignatedWainter.run: Some but not all are designated or hot standby. ");
            logger.debug("DesignatedWainter.run: containsDesignated = {}", containsDesignated);
            //Some but not all are designated or hot standby.
            if (containsDesignated) {
                /*
                 * The list only contains designated.  This is a problem.  It is most likely a race
                 * condition that resulted in two thinking they should be designated. Choose the
                 * site with the latest designated date for the pdp not included on the designated list.
                 * This should be the site that had the last designation before this race condition
                 * occurred.
                 */
                mostRecentPrimary = getLatestUndesignated(pdps, mostRecentPrimary, listOfDesignated);

            } else {
                //The list only contains hot standby. Choose the site of the latest designated date
                mostRecentPrimary = getLatestDesignated(pdps, mostRecentPrimary);
            }
        }
        return mostRecentPrimary;
    }

    private DroolsPdp getBestDesignated(Collection<DroolsPdp> pdps, boolean containsDesignated) {
        DroolsPdp mostRecentPrimary;
        mostRecentPrimary = null;
        for (DroolsPdp pdp : pdps) {
            if (mostRecentPrimary == null) {
                mostRecentPrimary = pdp;
                continue;
            }
            if (containsDesignated) { //Choose the site of the first designated date
                if (pdp.getDesignatedDate().compareTo(mostRecentPrimary.getDesignatedDate()) < 0) {
                    mostRecentPrimary = pdp;
                    logger.debug(RUN_PRIMARY_MSG, mostRecentPrimary.getPdpId());
                }
            } else { //Choose the site with the latest designated date
                if (pdp.getDesignatedDate().compareTo(mostRecentPrimary.getDesignatedDate()) > 0) {
                    mostRecentPrimary = pdp;
                    logger.debug(RUN_PRIMARY_MSG, mostRecentPrimary.getPdpId());
                }
            }
        }
        return mostRecentPrimary;
    }

    private DroolsPdp getLatestUndesignated(Collection<DroolsPdp> pdps, DroolsPdp mostRecentPrimary,
                    List<DroolsPdp> listOfDesignated) {
        for (DroolsPdp pdp : pdps) {
            if (listOfDesignated.contains(pdp)) {
                continue; //Don't consider this entry
            }
            if (pdp.getDesignatedDate().compareTo(mostRecentPrimary.getDesignatedDate()) > 0) {
                mostRecentPrimary = pdp;
                logger.debug(RUN_PRIMARY_MSG, mostRecentPrimary.getPdpId());
            }
        }
        return mostRecentPrimary;
    }

    private DroolsPdp getLatestDesignated(Collection<DroolsPdp> pdps, DroolsPdp mostRecentPrimary) {
        for (DroolsPdp pdp : pdps) {
            logger.debug("DesignatedWaiter.run pdp = {}"
                            + " pdp.getDesignatedDate() = {}",
                            pdp.getPdpId(), pdp.getDesignatedDate());
            if (pdp.getDesignatedDate().compareTo(mostRecentPrimary.getDesignatedDate()) > 0) {
                mostRecentPrimary = pdp;
                logger.debug(RUN_PRIMARY_MSG, mostRecentPrimary.getPdpId());
            }
        }
        return mostRecentPrimary;
    }

    /**
     * Compue designated pdp.
     *
     * @param listOfDesignated list of designated pdps
     * @param mostRecentPrimary most recent primary pdpd
     * @return drools pdp object
     */
    public DroolsPdp computeDesignatedPdp(List<DroolsPdp> listOfDesignated, DroolsPdp mostRecentPrimary) {
        if (listOfDesignated.isEmpty()) {
            logger.debug("\nDesignatedWaiter.run: myPdp: {} listOfDesignated is: EMPTY.", myPdp.getPdpId());
            return null;
        }

        if (listOfDesignated.size() == 1) {
            logger.debug("\nDesignatedWaiter.run: myPdp: {} listOfDesignated "
                            + "has ONE entry. PDP ID: {}", myPdp.getPdpId(), listOfDesignated.get(0).getPdpId());
            return listOfDesignated.get(0);
        }

        logger.debug("DesignatedWaiter.run: myPdp: {} listOfDesignated.size(): {}", myPdp.getPdpId(),
                        listOfDesignated.size());
        DesignatedData data = new DesignatedData();
        for (DroolsPdp pdp : listOfDesignated) {
            DroolsPdp rejectedPdp;

            // We need to determine if another PDP is the lowest priority
            if (nullSafeEquals(pdp.getSite(), mostRecentPrimary.getSite())) {
                rejectedPdp = data.compareSameSite(pdp);
            } else {
                rejectedPdp = data.compareDifferentSite(pdp);
            }
            // If the rejectedPdp is myPdp, we need to stand it down and demote it.  Each pdp is responsible
            // for demoting itself
            if (rejectedPdp != null && nullSafeEquals(rejectedPdp.getPdpId(), myPdp.getPdpId())) {
                logger.debug("\n\nDesignatedWaiter.run: myPdp: {} listOfDesignated myPdp ID: {}"
                                + " is NOT the lowest priority.  Executing stateManagement.demote()\n\n",
                                myPdp.getPdpId(),
                                myPdp.getPdpId());
                // We found that myPdp is on the listOfDesignated and it is not the lowest priority
                // So, we must demote it
                demoteMyPdp();
            }
        }

        DroolsPdp lowestPriorityPdp = data.getLowestPriority();

        //now we have a valid value for lowestPriorityPdp
        logger.debug("\n\nDesignatedWaiter.run: myPdp: {} listOfDesignated "
                        + "found the LOWEST priority pdp ID: {} "
                        + " It is now the designatedPpd from the perspective of myPdp ID: {} \n\n",
                        myPdp.getPdpId(), lowestPriorityPdp.getPdpId(), myPdp);
        return lowestPriorityPdp;

    }

    private class DesignatedData {
        private DroolsPdp lowestPrioritySameSite = null;
        private DroolsPdp lowestPriorityDifferentSite = null;

        private DroolsPdp compareSameSite(DroolsPdp pdp) {
            if (lowestPrioritySameSite == null) {
                if (lowestPriorityDifferentSite != null) {
                    //we need to reject lowestPriorityDifferentSite
                    DroolsPdp rejectedPdp = lowestPriorityDifferentSite;
                    lowestPriorityDifferentSite = pdp;
                    return rejectedPdp;
                }
                lowestPrioritySameSite = pdp;
                return null;
            } else {
                if (pdp.getPdpId().equals((lowestPrioritySameSite.getPdpId()))) {
                    return null;    //nothing to compare
                }
                if (pdp.comparePriority(lowestPrioritySameSite) < 0) {
                    logger.debug("\nDesignatedWaiter.run: myPdp {}  listOfDesignated pdp ID: {}"
                                    + " has lower priority than pdp ID: {}", myPdp.getPdpId(), pdp.getPdpId(),
                                    lowestPrioritySameSite.getPdpId());
                    //we need to reject lowestPrioritySameSite
                    DroolsPdp rejectedPdp = lowestPrioritySameSite;
                    lowestPrioritySameSite = pdp;
                    return rejectedPdp;
                } else {
                    //we need to reject pdp and keep lowestPrioritySameSite
                    logger.debug("\nDesignatedWaiter.run: myPdp {} listOfDesignated pdp ID: {} "
                                    + " has higher priority than pdp ID: {}", myPdp.getPdpId(), pdp.getPdpId(),
                                    lowestPrioritySameSite.getPdpId());
                    return pdp;
                }
            }
        }

        private DroolsPdp compareDifferentSite(DroolsPdp pdp) {
            if (lowestPrioritySameSite != null) {
                //if we already have a candidate for same site, we don't want to bother with different sites
                return pdp;
            } else {
                if (lowestPriorityDifferentSite == null) {
                    lowestPriorityDifferentSite = pdp;
                    return null;
                }
                if (pdp.getPdpId().equals((lowestPriorityDifferentSite.getPdpId()))) {
                    return null;    //nothing to compare
                }
                if (pdp.comparePriority(lowestPriorityDifferentSite) < 0) {
                    logger.debug("\nDesignatedWaiter.run: myPdp {} listOfDesignated pdp ID: {}"
                                    + " has lower priority than pdp ID: {}", myPdp.getPdpId(), pdp.getPdpId(),
                                    lowestPriorityDifferentSite.getPdpId());
                    //we need to reject lowestPriorityDifferentSite
                    DroolsPdp rejectedPdp = lowestPriorityDifferentSite;
                    lowestPriorityDifferentSite = pdp;
                    return rejectedPdp;
                } else {
                    //we need to reject pdp and keep lowestPriorityDifferentSite
                    logger.debug("\nDesignatedWaiter.run: myPdp {} listOfDesignated pdp ID: {}"
                                    + " has higher priority than pdp ID: {}", myPdp.getPdpId(), pdp.getPdpId(),
                                    lowestPriorityDifferentSite.getPdpId());
                    return pdp;
                }
            }
        }

        private DroolsPdp getLowestPriority() {
            return (lowestPrioritySameSite != null ? lowestPrioritySameSite : lowestPriorityDifferentSite);
        }
    }

    private void demoteMyPdp() {
        try {
            //Keep the order like this.  StateManagement is last since it triggers controller shutdown
            myPdp.setDesignated(false);
            pdpsConnector.setDesignated(myPdp, false);
            isDesignated = false;
            String standbyStatus = stateManagementFeature.getStandbyStatus();
            if (!(standbyStatus.equals(StateManagement.HOT_STANDBY)
                    || standbyStatus.equals(StateManagement.COLD_STANDBY))) {
                /*
                 * Only call demote if it is not already in the right state.  Don't worry about
                 * synching the lower level topic endpoint states.  That is done by the
                 * refreshStateAudit.
                 */
                stateManagementFeature.demote();
            }
        } catch (Exception e) {
            myPdp.setDesignated(false);
            pdpsConnector.setDesignated(myPdp, false);
            isDesignated = false;
            logger.error("DesignatedWaiter.run: myPdp: {} Caught Exception attempting to "
                    + "demote myPdp {} myPdp.getPdpId(), message= {}", myPdp.getPdpId(),
                    e);
        }
    }

    private class TimerUpdateClass extends TimerTask {

        @Override
        public void run() {
            try {
                logger.debug("TimerUpdateClass.run: entry");
                checkWaitTimer();
            } catch (Exception e) {
                logger.error("TimerUpdateClass.run caught an unexpected exception: ", e);
            }
            logger.debug("TimerUpdateClass.run.exit");
        }
    }

    @Override
    public void checkThreadStatus() {
        checkWaitTimer();
    }

    private void checkWaitTimer() {
        synchronized (checkWaitTimerLock) {
            try {
                logger.debug("checkWaitTimer: entry");
                Date now = currentTime.getDate();
                long nowMs = now.getTime();
                long waitTimerMs = waitTimerLastRunDate.getTime();

                //give it 10 times leeway
                if ((nowMs - waitTimerMs)  > 10 * pdpUpdateInterval) {
                    if (allSeemsWell == null || allSeemsWell) {
                        allSeemsWell = false;
                        logger.debug("checkWaitTimer: calling allSeemsWell with ALLNOTWELL param");
                        stateManagementFeature.allSeemsWell(this.getClass().getName(),
                                StateManagementFeatureApiConstants.ALLNOTWELL_STATE,
                                "DesignationWaiter/ElectionHandler has STALLED");
                    }
                    logger.error("checkWaitTimer: nowMs - waitTimerMs = {}"
                            + ", exceeds 10* pdpUpdateInterval = {}"
                            + " DesignationWaiter is STALLED!", (nowMs - waitTimerMs), (10 * pdpUpdateInterval));
                } else if (allSeemsWell == null || !allSeemsWell) {
                    allSeemsWell = true;
                    stateManagementFeature.allSeemsWell(this.getClass().getName(),
                            StateManagementFeatureApiConstants.ALLSEEMSWELL_STATE,
                            "DesignationWaiter/ElectionHandler has RESUMED");
                    logger.info("DesignationWaiter/ElectionHandler has RESUMED");
                }
                logger.debug("checkWaitTimer: exit");
            } catch (Exception e) {
                logger.error("checkWaitTimer: caught unexpected exception: ", e);
            }
        }
    }

    private long getDWaiterStartMs() {
        Date now = currentTime.getDate();

        // Retrieve the ms since the epoch
        long nowMs = now.getTime();

        // Time since the end of the last pdpUpdateInterval multiple
        long nowModMs = nowMs % pdpUpdateInterval;

        // Time to the start of the next pdpUpdateInterval multiple
        long startMs = 2 * pdpUpdateInterval - nowModMs;

        // Give the start time a minimum of a 5 second cushion
        if (startMs < 5000) {
            // Start at the beginning  of following interval
            startMs = pdpUpdateInterval + startMs;
        }
        return startMs;
    }

    private boolean nullSafeEquals(Object one, Object two) {
        if (one == null && two == null) {
            return true;
        }
        if (one != null && two != null) {
            return one.equals(two);
        }
        return false;
    }

    public String getPdpdNowActive() {
        return pdpdNowActive;
    }

    public String getPdpdLastActive() {
        return pdpdLastActive;
    }
}
