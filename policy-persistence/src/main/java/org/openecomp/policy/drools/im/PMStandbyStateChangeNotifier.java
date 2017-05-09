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

package org.openecomp.policy.drools.im; 
 
/* 
 * Per MultiSite_v1-10.ppt:
 * 
 * Extends the StateChangeNotifier class and overwrites the abstract handleStateChange() method to get state changes 
 * and do the following: 
 * 
 * When the Standby Status changes (from providingservice) to hotstandby or coldstandby, 
 * the Active/Standby selection algorithm must stand down if the PDP-D is currently the lead/active node 
 * and allow another PDP-D to take over.  It must also call lock on all engines in the engine management.
 * 
 * When the Standby Status changes from (hotstandby) to coldstandby, the Active/Standby algorithm must NOT assume 
 * the active/lead role.
 *  
 * When the Standby Status changes (from coldstandby or providingservice) to hotstandby, 
 * the Active/Standby algorithm may assume the active/lead role if the active/lead fails.
 * 
 * When the Standby Status changes to providingservice (from hotstandby or coldstandby) call unlock on all 
 * engines in the engine management layer.
 */
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import org.openecomp.policy.common.im.StateChangeNotifier;
import org.openecomp.policy.common.im.StateManagement;
import org.openecomp.policy.drools.controller.internal.MavenDroolsController;
import org.openecomp.policy.drools.core.IntegrityMonitorProperties;
import org.openecomp.policy.drools.event.comm.TopicEndpoint;
import org.openecomp.policy.common.logging.flexlogger.FlexLogger;
import org.openecomp.policy.common.logging.flexlogger.Logger;
import org.openecomp.policy.drools.persistence.DroolsPdpsConnector;
import org.openecomp.policy.drools.persistence.PersistenceFeature;
import org.openecomp.policy.drools.system.PolicyEngine;

/*
 * Some background:
 * 
 * Originally, there was a "StandbyStateChangeNotifier" that belonged to policy-core, and this class's handleStateChange() method
 * used to take care of invoking conn.standDownPdp().   But testing revealed that when a state change to hot standby occurred 
 * from a demote() operation, first the PMStandbyStateChangeNotifier.handleStateChange() method would be invoked and then the 
 * StandbyStateChangeNotifier.handleStateChange() method would be invoked, and this ordering was creating the following problem:
 * 
 * When PMStandbyStateChangeNotifier.handleStateChange() was invoked it would take a long time to finish, because it would result
 * in SingleThreadedUebTopicSource.stop() being invoked, which can potentially do a 5 second sleep for each controller being stopped.   
 * Meanwhile, as these controller stoppages and their associated sleeps were occurring, the election handler would discover the
 * demoted PDP in hotstandby (but still designated!) and promote it, resulting in the standbyStatus going from hotstandby
 * to providingservice.  So then, by the time that PMStandbyStateChangeNotifier.handleStateChange() finished its work and
 * StandbyStateChangeNotifier.handleStateChange() started executing, the standbyStatus was no longer hotstandby (as effected by
 * the demote), but providingservice (as reset by the election handling logic) and conn.standDownPdp() would not get called!
 * 
 * To fix this bug, we consolidated StandbyStateChangeNotifier and PMStandbyStateChangeNotifier, with the standDownPdp() always 
 * being invoked prior to the TopicEndpoint.manager.lock().  In this way, when the election handling logic is invoked 
 * during the controller stoppages, the PDP is in hotstandby and the standdown occurs.
 * 
 */
public class PMStandbyStateChangeNotifier extends StateChangeNotifier {
	// get an instance of logger 
	private static Logger  logger = FlexLogger.getLogger(PMStandbyStateChangeNotifier.class);
	private Timer delayActivateTimer;
	private int pdpUpdateInterval;
	private boolean isWaitingForActivation;
	private long startTimeWaitingForActivationMs;
	private long waitInterval;
	private boolean isNowActivating;
	private String previousStandbyStatus;
	public static String NONE = "none";
	public static String UNSUPPORTED = "unsupported";
	public static String HOTSTANDBY_OR_COLDSTANDBY = "hotstandby_or_coldstandby";
		
	public PMStandbyStateChangeNotifier(){
		pdpUpdateInterval = Integer.parseInt(IntegrityMonitorProperties.getProperty(IntegrityMonitorProperties.PDP_UPDATE_INTERVAL));
		isWaitingForActivation = false;
		startTimeWaitingForActivationMs = new Date().getTime();
		//delay the activate so the DesignatedWaiter can run twice - give it an extra 2 seconds
		waitInterval = 2*pdpUpdateInterval + 2000;
		isNowActivating=false;
		previousStandbyStatus = PMStandbyStateChangeNotifier.NONE;
	}

	@Override
	public void handleStateChange() {
		/*
		 * A note on synchronization: This method is not synchronized because the caller, stateManagememt, 
		 * has synchronize all of its methods. Only one stateManagement operation can occur at a time. Thus,
		 * only one handleStateChange() call will ever be made at a time.
		 */
		if(logger.isInfoEnabled()){
			logger.info("handleStateChange: Entering, message='"
				+ super.getMessage() + "', standbyStatus='"
				+ super.getStateManagement().getStandbyStatus() + "'");
		}
		String standbyStatus = super.getStateManagement().getStandbyStatus();
		String pdpId = IntegrityMonitorProperties
				.getProperty(IntegrityMonitorProperties.PDP_INSTANCE_ID);
		DroolsPdpsConnector conn = PersistenceFeature
				.getDroolsPdpsConnector("ncompPU");

		if(logger.isDebugEnabled()){
			logger.debug("handleStateChange: previousStandbyStatus = " + previousStandbyStatus
				+ "; standbyStatus = " + standbyStatus);
		}
		
		if (standbyStatus == null  || standbyStatus.equals(StateManagement.NULL_VALUE)) {
			if(logger.isInfoEnabled()){
				logger.info("handleStateChange: standbyStatus is null; standing down PDP=" + pdpId);
			}
			if(previousStandbyStatus.equals(StateManagement.NULL_VALUE)){
				//We were just here and did this successfully
				if(logger.isDebugEnabled()){
					logger.debug("handleStateChange: Is returning because standbyStatus is null and was previously 'null'; PDP=" + pdpId);
				}
				return;
			}
			isWaitingForActivation = false;
			try{
				try{
					if(logger.isInfoEnabled()){
						logger.info("handleStateChange: null:  cancelling delayActivationTimer.");
					}
					delayActivateTimer.cancel();
				}catch(Exception e){
					if(logger.isInfoEnabled()){
						logger.info("handleStateChange: null no delayActivationTimer existed.");
					}
					//If you end of here, there was no active timer
				}
				conn.standDownPdp(pdpId);
				//Only want to lock the endpoints, not the controllers.
				PolicyEngine.manager.deactivate();
				//The operation was fully successful, but you cannot assign it a real null value
				//because later we might try to execute previousStandbyStatus.equals() and get
				//a null pointer exception.
				previousStandbyStatus = StateManagement.NULL_VALUE;
			}catch(Exception e){
				logger.warn("handleStateChange: standbyStatus == null caught exception: " + e);
				e.printStackTrace();
			}
		} else if (standbyStatus.equals(StateManagement.HOT_STANDBY) || standbyStatus.equals(StateManagement.COLD_STANDBY)) {
			if(logger.isInfoEnabled()){
				logger.info("handleStateChange: standbyStatus=" + standbyStatus + "; standing down PDP=" + pdpId);
			}
			if(previousStandbyStatus.equals(PMStandbyStateChangeNotifier.HOTSTANDBY_OR_COLDSTANDBY)){
				//We were just here and did this successfully
				if(logger.isDebugEnabled()){
					logger.debug("handleStateChange: Is returning because standbyStatus is "
						+ standbyStatus + " and was previously " + previousStandbyStatus 
						+ "; PDP=" + pdpId);
				}
				return;
			}
			isWaitingForActivation = false;
			try{
				try{
					if(logger.isInfoEnabled()){
						logger.info("handleStateChange: HOT_STNDBY || COLD_STANDBY:  cancelling delayActivationTimer.");
					}
					delayActivateTimer.cancel();
				}catch(Exception e){
					if(logger.isInfoEnabled()){
						logger.info("handleStateChange: HOT_STANDBY || COLD_STANDBY no delayActivationTimer existed.");
					}
					//If you end of here, there was no active timer
				}
				//Only want to lock the endpoints, not the controllers.
				conn.standDownPdp(pdpId);
				PolicyEngine.manager.deactivate();
				//The operation was fully successful
				previousStandbyStatus = PMStandbyStateChangeNotifier.HOTSTANDBY_OR_COLDSTANDBY;
			}catch(Exception e){
				logger.warn("handleStateChange: standbyStatus == " + standbyStatus + " caught exception: " + e);
				e.printStackTrace();
			}

		} else if (standbyStatus.equals(StateManagement.PROVIDING_SERVICE)) {
			if(logger.isInfoEnabled()){
				logger.info("handleStateChange: standbyStatus=" + standbyStatus + "; scheduling activation of PDP=" + pdpId);
			}
			if(previousStandbyStatus.equals(StateManagement.PROVIDING_SERVICE)){
				//We were just here and did this successfully
				if(logger.isDebugEnabled()){
					logger.debug("handleStateChange: Is returning because standbyStatus is "
						+ standbyStatus + " and was previously " + previousStandbyStatus 
						+ "; PDP=" + pdpId);
				}
				return;
			}
			try{
				//UnLock all the endpoints
				if(logger.isInfoEnabled()){
					logger.info("handleStateChange: standbyStatus=" + standbyStatus + "; controllers must be unlocked.");
				}
				/*
				 * Only endpoints should be unlocked. Controllers have not been locked.
				 * Because, sometimes, it is possible for more than one PDP-D to become active (race conditions)
				 * we need to delay the activation of the topic endpoint interfaces to give the election algorithm
				 * time to resolve the conflict.
				 */
				if(logger.isInfoEnabled()){
					logger.info("handleStateChange: PROVIDING_SERVICE isWaitingForActivation= " +isWaitingForActivation);
				}
				//Delay activation for 2*pdpUpdateInterval+2000 ms in case of an election handler conflict.  
				//You could have multiple election handlers thinking they can take over.
				
				 // First let's check that the timer has not died
				if(isWaitingForActivation){
					if(logger.isInfoEnabled()){
						logger.info("handleStateChange: PROVIDING_SERVICE isWaitingForActivation = " + isWaitingForActivation);
					}
					long now = new Date().getTime();
					long waitTimeMs = now - startTimeWaitingForActivationMs;
					if(waitTimeMs > 3*waitInterval){
						if(logger.isInfoEnabled()){
							logger.info("handleStateChange: PROVIDING_SERVICE looks like the activation wait timer may be hung,"
								+ " waitTimeMs = " + waitTimeMs + " and allowable waitInterval = " + waitInterval
								+ " Checking whether it is currently in activation. isNowActivating = " + isNowActivating);
						}
						//Now check that it is not currently executing an activation
						if(!isNowActivating){
							if(logger.isInfoEnabled()){
								logger.info("handleStateChange: PROVIDING_SERVICE looks like the activation wait timer died");
							}
							// This will assure the timer is cancelled and rescheduled.
							isWaitingForActivation = false;
						}
					}
					
				}
				
				if(!isWaitingForActivation){
					try{
						//Just in case there is an old timer hanging around
						if(logger.isInfoEnabled()){
							logger.info("handleStateChange: PROVIDING_SERVICE cancelling delayActivationTimer.");
						}
						delayActivateTimer.cancel();
					}catch(Exception e){
						if(logger.isInfoEnabled()){
							logger.info("handleStateChange: PROVIDING_SERVICE no delayActivationTimer existed.");
						}
						//If you end of here, there was no active timer
					}
					delayActivateTimer = new Timer();
					//delay the activate so the DesignatedWaiter can run twice
					delayActivateTimer.schedule(new DelayActivateClass(), waitInterval);
					isWaitingForActivation = true;
					startTimeWaitingForActivationMs = new Date().getTime();
					if(logger.isInfoEnabled()){
						logger.info("handleStateChange: PROVIDING_SERVICE scheduling delayActivationTimer in " + waitInterval + " ms");
					}
				}else{
					if(logger.isInfoEnabled()){
						logger.info("handleStateChange: PROVIDING_SERVICE delayActivationTimer is waiting for activation.");
					}
				}
				
			}catch(Exception e){
				logger.warn("handleStateChange: PROVIDING_SERVICE standbyStatus == providingservice caught exception: " + e);
				e.printStackTrace();
			}

		} else {
			logger.error("handleStateChange: Unsupported standbyStatus=" + standbyStatus + "; standing down PDP=" + pdpId);
			if(previousStandbyStatus.equals(PMStandbyStateChangeNotifier.UNSUPPORTED)){
				//We were just here and did this successfully
				if(logger.isDebugEnabled()){
					logger.debug("handleStateChange: Is returning because standbyStatus is "
						+ "UNSUPPORTED and was previously " + previousStandbyStatus 
						+ "; PDP=" + pdpId);
				}
				return;
			}
			//Only want to lock the endpoints, not the controllers.
			isWaitingForActivation = false;
			try{
				try{
					if(logger.isInfoEnabled()){
						logger.info("handleStateChange: unsupported standbystatus:  cancelling delayActivationTimer.");
					}
					delayActivateTimer.cancel();
				}catch(Exception e){
					if(logger.isInfoEnabled()){
						logger.info("handleStateChange: unsupported standbystatus: no delayActivationTimer existed.");
					}
					//If you end of here, there was no active timer
				}
				conn.standDownPdp(pdpId);
				PolicyEngine.manager.deactivate();
				//We know the standbystatus is unsupported
				previousStandbyStatus = PMStandbyStateChangeNotifier.UNSUPPORTED;
			}catch(Exception e){
				logger.warn("handleStateChange: Unsupported standbyStatus == " + standbyStatus + "caught exception: " + e);
				e.printStackTrace();
			}
		}
		if(logger.isDebugEnabled()){
			logger.debug("handleStateChange: Exiting");
		}
	}

	private class DelayActivateClass extends TimerTask{

		private Object delayActivateLock = new Object();


		@Override
		public void run() {
			isNowActivating = true;
			try{
				if(logger.isInfoEnabled()){
					logger.info("DelayActivateClass.run: entry");
				}
				synchronized(delayActivateLock){
					PolicyEngine.manager.activate();
					// The state change fully succeeded
					previousStandbyStatus = StateManagement.PROVIDING_SERVICE;
					// We want to set this to false here because the activate call can take a while
					isWaitingForActivation = false;
					isNowActivating = false;
				}
				if(logger.isInfoEnabled()){
					logger.info("DelayActivateClass.run.exit");
				}
			}catch(Exception e){
				isWaitingForActivation = false;
				isNowActivating = false;
				logger.warn("DelayActivateClass.run: caught an unexpected exception "
						+ "calling PolicyEngine.manager.activate: " + e);
				System.out.println(new Date() + " DelayActivateClass.run: caught an unexpected exception");
				e.printStackTrace();
			}
		}
	}
	
	public String getPreviousStandbyStatus(){
		return previousStandbyStatus;
	}
}
