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

package org.onap.policy.drools.activestandby;

import java.util.Timer;
import java.util.TimerTask;
import org.onap.policy.common.im.MonitorTime;
import org.onap.policy.common.im.StateChangeNotifier;
import org.onap.policy.common.im.StateManagement;
import org.onap.policy.common.utils.time.CurrentTime;
import org.onap.policy.drools.system.PolicyEngine;
import org.onap.policy.drools.system.PolicyEngineConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Some background:
 *
 * Originally, there was a "StandbyStateChangeNotifier" that belonged to policy-core, and this class's
 * handleStateChange() method used to take care of invoking conn.standDownPdp().
 *
 * But testing revealed that when a state change to hot standby
 * occurred from a demote() operation, first the PMStandbyStateChangeNotifier.handleStateChange() method
 * would be invoked and then the StandbyStateChangeNotifier.handleStateChange() method would be invoked,
 * and this ordering was creating the following problem:
 *
 * When PMStandbyStateChangeNotifier.handleStateChange() was invoked it would take a long time to finish,
 * because it would result in SingleThreadedUebTopicSource.stop() being invoked, which can potentially do a
 * 5 second sleep for each controller being stopped.
 *
 * Meanwhile, as these controller stoppages and their associated sleeps were occurring, the election handler
 * would discover the demoted PDP in hotstandby (but still designated!) and promote it, resulting in the
 * standbyStatus going from hotstandby to providingservice.  So then, by the time that
 * PMStandbyStateChangeNotifier.handleStateChange() finished its work and
 * StandbyStateChangeNotifier.handleStateChange() started executing, the standbyStatus was no longer hotstandby
 * (as effected by the demote), but providingservice (as reset by the election handling logic) and
 * conn.standDownPdp() would not get called!
 *
 * To fix this bug, we consolidated StandbyStateChangeNotifier and PMStandbyStateChangeNotifier,
 * with the standDownPdp() always
 * being invoked prior to the TopicEndpoint.manager.lock().  In this way, when the election handling logic is invoked
 * during the controller stoppages, the PDP is in hotstandby and the standdown occurs.
 *
 */
public class PmStandbyStateChangeNotifier extends StateChangeNotifier {
    // get an instance of logger
    private static final Logger logger = LoggerFactory.getLogger(PmStandbyStateChangeNotifier.class);
    private Timer delayActivateTimer;
    private int pdpUpdateInterval;
    private boolean isWaitingForActivation;
    private long startTimeWaitingForActivationMs;
    private long waitInterval;
    private boolean isNowActivating;
    private String previousStandbyStatus;
    private final CurrentTime currentTime = MonitorTime.getInstance();
    private final Factory timerFactory = Factory.getInstance();
    public static final String NONE = "none";
    public static final String UNSUPPORTED = "unsupported";
    public static final String HOTSTANDBY_OR_COLDSTANDBY = "hotstandby_or_coldstandby";

    /**
     * Constructor.
     *
     */
    public PmStandbyStateChangeNotifier() {
        pdpUpdateInterval =
                Integer.parseInt(ActiveStandbyProperties.getProperty(ActiveStandbyProperties.PDP_UPDATE_INTERVAL));
        isWaitingForActivation = false;
        startTimeWaitingForActivationMs = currentTime.getMillis();
        // delay the activate so the DesignatedWaiter can run twice - give it an extra 2 seconds
        waitInterval = 2 * pdpUpdateInterval + 2000L;
        isNowActivating = false;
        previousStandbyStatus = PmStandbyStateChangeNotifier.NONE;
    }

    @Override
    public void handleStateChange() {
        /*
         * A note on synchronization: This method is not synchronized because the caller,
         * stateManagememt, has synchronize all of its methods. Only one stateManagement operation
         * can occur at a time. Thus, only one handleStateChange() call will ever be made at a time.
         */
        logger.debug("handleStateChange: Entering, message={}, standbyStatus={}", super.getMessage(),
                        super.getStateManagement().getStandbyStatus());
        String standbyStatus = super.getStateManagement().getStandbyStatus();
        String pdpId = ActiveStandbyProperties.getProperty(ActiveStandbyProperties.NODE_NAME);

        logger.debug("handleStateChange: previousStandbyStatus = {}; standbyStatus = {}",
                previousStandbyStatus, standbyStatus);

        if (standbyStatus == null || standbyStatus.equals(StateManagement.NULL_VALUE)) {
            logger.debug("handleStateChange: standbyStatus is null; standing down PDP={}", pdpId);
            standDownPdpNull(pdpId);

        } else if (standbyStatus.equals(StateManagement.HOT_STANDBY)
                || standbyStatus.equals(StateManagement.COLD_STANDBY)) {
            logger.debug("handleStateChange: standbyStatus={}; standing down PDP={}", standbyStatus, pdpId);
            standDownPdp(pdpId, standbyStatus);

        } else if (standbyStatus.equals(StateManagement.PROVIDING_SERVICE)) {
            logger.debug("handleStateChange: standbyStatus= {} scheduling activation of PDP={}", standbyStatus,
                            pdpId);
            schedulePdpActivation(pdpId, standbyStatus);

        } else {
            logger.error("handleStateChange: Unsupported standbyStatus={}; standing down PDP={}", standbyStatus, pdpId);
            standDownPdpUnsupported(pdpId, standbyStatus);
        }

        logger.debug("handleStateChange: Exiting");
    }

    private void standDownPdpNull(String pdpId) {
        if (previousStandbyStatus.equals(StateManagement.NULL_VALUE)) {
            // We were just here and did this successfully
            logger.debug("handleStateChange: "
                            + "Is returning because standbyStatus is null and was previously 'null'; PDP={}",
                            pdpId);
            return;
        }

        isWaitingForActivation = false;
        try {
            logger.debug("handleStateChange: null:  cancelling delayActivationTimer.");
            cancelTimer();
            // Only want to lock the endpoints, not the controllers.
            getPolicyEngineManager().deactivate();
            // The operation was fully successful, but you cannot assign it a real null value
            // because later we might try to execute previousStandbyStatus.equals() and get
            // a null pointer exception.
            previousStandbyStatus = StateManagement.NULL_VALUE;
        } catch (Exception e) {
            logger.warn("handleStateChange: standbyStatus == null caught exception: ", e);
        }
    }

    private void standDownPdp(String pdpId, String standbyStatus) {
        if (previousStandbyStatus.equals(PmStandbyStateChangeNotifier.HOTSTANDBY_OR_COLDSTANDBY)) {
            // We were just here and did this successfully
            logger.debug("handleStateChange: Is returning because standbyStatus is {}"
                            + " and was previously {}; PDP= {}", standbyStatus, previousStandbyStatus, pdpId);
            return;
        }

        isWaitingForActivation = false;
        try {
            logger.debug("handleStateChange: HOT_STNDBY || COLD_STANDBY:  cancelling delayActivationTimer.");
            cancelTimer();
            // Only want to lock the endpoints, not the controllers.
            getPolicyEngineManager().deactivate();
            // The operation was fully successful
            previousStandbyStatus = PmStandbyStateChangeNotifier.HOTSTANDBY_OR_COLDSTANDBY;
        } catch (Exception e) {
            logger.warn("handleStateChange: standbyStatus = {} caught exception: {}", standbyStatus, e.getMessage(),
                    e);
        }
    }

    private void schedulePdpActivation(String pdpId, String standbyStatus) {
        if (previousStandbyStatus.equals(StateManagement.PROVIDING_SERVICE)) {
            // We were just here and did this successfully
            logger.debug("handleStateChange: Is returning because standbyStatus is {}"
                            + "and was previously {}; PDP={}", standbyStatus, previousStandbyStatus, pdpId);
            return;
        }

        try {
            // UnLock all the endpoints
            logger.debug("handleStateChange: standbyStatus={}; controllers must be unlocked.", standbyStatus);
            /*
             * Only endpoints should be unlocked. Controllers have not been locked. Because,
             * sometimes, it is possible for more than one PDP-D to become active (race
             * conditions) we need to delay the activation of the topic endpoint interfaces to
             * give the election algorithm time to resolve the conflict.
             */
            logger.debug("handleStateChange: PROVIDING_SERVICE isWaitingForActivation= {}",
                            isWaitingForActivation);

            // Delay activation for 2*pdpUpdateInterval+2000 ms in case of an election handler
            // conflict.
            // You could have multiple election handlers thinking they can take over.

            // First let's check that the timer has not died
            checkTimerStatus();

            if (!isWaitingForActivation) {
                // Just in case there is an old timer hanging around
                logger.debug("handleStateChange: PROVIDING_SERVICE cancelling delayActivationTimer.");
                cancelTimer();
                delayActivateTimer = timerFactory.makeTimer();
                // delay the activate so the DesignatedWaiter can run twice
                delayActivateTimer.schedule(new DelayActivateClass(), waitInterval);
                isWaitingForActivation = true;
                startTimeWaitingForActivationMs = currentTime.getMillis();
                logger.debug("handleStateChange: PROVIDING_SERVICE scheduling delayActivationTimer in {} ms",
                                waitInterval);
            } else {
                logger.debug("handleStateChange: PROVIDING_SERVICE delayActivationTimer is "
                                + "waiting for activation.");
            }

        } catch (Exception e) {
            logger.warn("handleStateChange: PROVIDING_SERVICE standbyStatus == providingservice caught exception: ",
                    e);
        }
    }

    private void checkTimerStatus() {
        if (isWaitingForActivation) {
            logger.debug("handleStateChange: PROVIDING_SERVICE isWaitingForActivation = {}",
                            isWaitingForActivation);
            long now = currentTime.getMillis();
            long waitTimeMs = now - startTimeWaitingForActivationMs;
            if (waitTimeMs > 3 * waitInterval) {
                logger.debug("handleStateChange: PROVIDING_SERVICE looks like the activation wait timer "
                                + "may be hung, waitTimeMs = {} and allowable waitInterval = {}"
                                + " Checking whether it is currently in activation. isNowActivating = {}",
                                waitTimeMs, waitInterval, isNowActivating);
                // Now check that it is not currently executing an activation
                if (!isNowActivating) {
                    logger.debug("handleStateChange: PROVIDING_SERVICE looks like the activation "
                                    + "wait timer died");
                    // This will assure the timer is cancelled and rescheduled.
                    isWaitingForActivation = false;
                }
            }
        }
    }

    private void standDownPdpUnsupported(String pdpId, String standbyStatus) {
        if (previousStandbyStatus.equals(PmStandbyStateChangeNotifier.UNSUPPORTED)) {
            // We were just here and did this successfully
            logger.debug("handleStateChange: Is returning because standbyStatus is "
                            + "UNSUPPORTED and was previously {}; PDP={}", previousStandbyStatus, pdpId);
            return;
        }

        // Only want to lock the endpoints, not the controllers.
        isWaitingForActivation = false;
        try {
            logger.debug("handleStateChange: unsupported standbystatus:  cancelling delayActivationTimer.");
            cancelTimer();
            getPolicyEngineManager().deactivate();
            // We know the standbystatus is unsupported
            previousStandbyStatus = PmStandbyStateChangeNotifier.UNSUPPORTED;
        } catch (Exception e) {
            logger.warn("handleStateChange: Unsupported standbyStatus = {} " + "caught exception: {} ",
                    standbyStatus, e.getMessage(), e);
        }
    }

    private void cancelTimer() {
        if (delayActivateTimer != null) {
            delayActivateTimer.cancel();
        }
    }

    private class DelayActivateClass extends TimerTask {

        private Object delayActivateLock = new Object();


        @Override
        public void run() {
            isNowActivating = true;
            try {
                logger.debug("DelayActivateClass.run: entry");
                synchronized (delayActivateLock) {
                    getPolicyEngineManager().activate();
                    // The state change fully succeeded
                    previousStandbyStatus = StateManagement.PROVIDING_SERVICE;
                    // We want to set this to false here because the activate call can take a while
                    isWaitingForActivation = false;
                    isNowActivating = false;
                }
                logger.debug("DelayActivateClass.run.exit");
            } catch (Exception e) {
                isWaitingForActivation = false;
                isNowActivating = false;
                logger.warn("DelayActivateClass.run: caught an unexpected exception "
                        + "calling PolicyEngineConstants.getManager().activate: ", e);
            }
        }
    }

    public String getPreviousStandbyStatus() {
        return previousStandbyStatus;
    }

    // these may be overridden by junit tests

    protected PolicyEngine getPolicyEngineManager() {
        return PolicyEngineConstants.getManager();
    }
}
