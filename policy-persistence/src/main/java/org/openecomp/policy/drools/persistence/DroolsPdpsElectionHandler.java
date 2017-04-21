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

package org.openecomp.policy.drools.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.openecomp.policy.common.im.StandbyStatusException;
import org.openecomp.policy.common.im.StateManagement;
import org.openecomp.policy.drools.core.DroolsPDPIntegrityMonitor;
import org.openecomp.policy.drools.core.IntegrityMonitorProperties;
import org.openecomp.policy.common.logging.flexlogger.FlexLogger;
import org.openecomp.policy.common.logging.flexlogger.Logger;
import org.openecomp.policy.common.logging.eelf.MessageCodes;

public class DroolsPdpsElectionHandler implements ThreadRunningChecker {
	// get an instance of logger 
	private final static Logger  logger = FlexLogger.getLogger(DroolsPdpsElectionHandler.class);	
	private DroolsPdpsConnector pdpsConnector;
	private Object pdpsConnectorLock = new Object();
	private Object checkUpdateWorkerLock = new Object();
	private Object checkWaitTimerLock = new Object();
	private Object designationWaiterLock = new Object();
	
	/*
	 * Must be static, so it can be referenced by JpaDroolsPdpsConnector,
	 * without requiring a reference to the election handler instantiation.
	 */
	private static DroolsPdp myPdp;
	
	private DesignationWaiter designationWaiter;
	private Timer updateWorker;
	private Timer waitTimer;
	private Date updateWorkerLastRunDate;
	private Date waitTimerLastRunDate;
	private int pdpCheckInterval;
	private int pdpUpdateInterval;
	private volatile boolean isDesignated;
	DroolsPDPIntegrityMonitor droolsPdpIntegrityMonitor;
	StateManagement stateManagement;
	
	public DroolsPdpsElectionHandler(DroolsPdpsConnector pdps, DroolsPdp myPdp, DroolsPDPIntegrityMonitor droolsPdpIntegrityMonitor){
		this.pdpsConnector = pdps;
		DroolsPdpsElectionHandler.myPdp = myPdp;
		this.isDesignated = false;
		this.droolsPdpIntegrityMonitor = droolsPdpIntegrityMonitor;
		this.stateManagement = droolsPdpIntegrityMonitor.getStateManager();				
		pdpCheckInterval = 3000;
		try{
			pdpCheckInterval = Integer.parseInt(IntegrityMonitorProperties.getProperty(IntegrityMonitorProperties.PDP_CHECK_INVERVAL));
		}catch(Exception e){
			logger.error
			//System.out.println
			(MessageCodes.EXCEPTION_ERROR ,e, "Could not get pdpCheckInterval property. Using default");
		}
		pdpUpdateInterval = 2000;
		try{
			pdpUpdateInterval = Integer.parseInt(IntegrityMonitorProperties.getProperty(IntegrityMonitorProperties.PDP_UPDATE_INTERVAL));
		}catch(Exception e){
			logger.error
			//System.out.println
			(MessageCodes.EXCEPTION_ERROR, e, "Could not get pdpUpdateInterval property. Using default");
		}	
		
		Date now = new Date();
		
		// Retrieve the ms since the epoch
		long nowMs = now.getTime();

		// Create the timer which will update the updateDate in DroolsPdpEntity table.
		// This is the heartbeat 
		updateWorker = new Timer();
		
		// Schedule the heartbeat to start in 100 ms and run at pdpCheckInterval ms thereafter
		updateWorker.scheduleAtFixedRate(new TimerUpdateClass(), 100, pdpCheckInterval);
		updateWorkerLastRunDate = new Date(nowMs + 100);
		
		// Create the timer which will run the election algorithm
		waitTimer = new Timer();

		// Schedule it to start in startMs ms (so it will run after the updateWorker and run at pdpUpdateInterval ms thereafter
		long startMs = getDWaiterStartMs();
		designationWaiter = new DesignationWaiter();
		waitTimer.scheduleAtFixedRate(designationWaiter, startMs, pdpUpdateInterval);
		waitTimerLastRunDate = new Date(nowMs + startMs);
	}
	
	public List<DroolsSessionEntity> waitForDesignation(){
		while(isDesignated == false){
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				return null;
			}
		}
		return designationWaiter.getSessions();

	}
	public List<DroolsSessionEntity> getSessions(){
		return designationWaiter.getSessions();
	}
	public void updateMyPdp(){
		synchronized(pdpsConnectorLock){
			myPdp.setUpdatedDate(new Date());
			pdpsConnector.update(myPdp);
		}
	}
	
	/*
	 * When the JpaDroolsPdpsConnector.standDown() method is invoked, it needs
	 * access to myPdp, so it can keep its designation status in sync with the
	 * DB.
	 */
	public static void setMyPdpDesignated(boolean designated) {
		logger.debug
		//System.out.println
			("setMyPdpDesignated: designated=" + designated);
		myPdp.setDesignated(designated);
	}
	
	private class DesignationWaiter extends TimerTask {
		// get an instance of logger 
		private Logger  logger = FlexLogger.getLogger(DesignationWaiter.class);
		private List<DroolsSessionEntity> sessions = null;

		public List<DroolsSessionEntity> getSessions(){
			if(sessions != null){
				return sessions;
			}
			return new LinkedList<DroolsSessionEntity>();
		}
		public void run() {
			try{
				logger.debug
				//System.out.println
				("DesignatedWaiter.run: Entering");
				
				// just here initially so code still works
				if (pdpsConnector == null) {
					waitTimerLastRunDate = new Date();
					logger.info("DesignatedWaiter.run (pdpsConnector==null) waitTimerLastRunDate = " + waitTimerLastRunDate);
					
					return;
				}

				synchronized (designationWaiterLock) {

					logger.debug
					//System.out.println
					("DesignatedWaiter.run: Entering synchronized block");

					checkUpdateWorkerTimer();
					
					//It is possible that multiple PDPs are designated lead.  So, we will make a list of all designated
					//PDPs and then decide which one really should be designated at the end.
					ArrayList<DroolsPdp> listOfDesignated = new ArrayList<DroolsPdp>();

					Collection<DroolsPdp> pdps = pdpsConnector.getDroolsPdps();
					DroolsPdp designatedPdp = null;
					DroolsPdp lowestPriorityPdp = null;

					logger.debug
					//System.out.println
					("DesignatedWaiter.run: pdps.size="
							+ pdps.size());

					//This is only true if all designated PDPs have failed
					boolean designatedPdpHasFailed = pdpsConnector.hasDesignatedPdpFailed(pdps);
					logger.debug
					//System.out.println
					("DesignatedWaiter.run: designatedPdpHasFailed="
							+ designatedPdpHasFailed);
					for (DroolsPdp pdp : pdps) {
						logger.debug
						//System.out.println
						("DesignatedWaiter.run: evaluating pdp ID: " + pdp.getPdpId());

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
						String standbyStatus = stateManagement.getStandbyStatus(pdp.getPdpId());
						if(standbyStatus==null){
							// Treat this case as a cold standby -- if we
							// abort here, no sessions will be created in a
							// single-node test environment.
							standbyStatus = StateManagement.COLD_STANDBY;
						}

						logger.debug
						//System.out.println
						("DesignatedWaiter.run: PDP="
								+ pdp.getPdpId() + ", isCurrent=" + isCurrent);

						/*
						 * There are 4 combinations of isDesignated and isCurrent.  We will examine each one in-turn
						 * and evaluate the each pdp in the list of pdps against each combination.
						 * 
						 * This is the first combination of isDesignated and isCurrent
						 */
						if (pdp.isDesignated()  &&  isCurrent) { 
							//It is current, but it could have a standbystatus=coldstandby / hotstandby
							//If so, we need to stand it down and demote it
							if(!standbyStatus.equals(StateManagement.PROVIDING_SERVICE)){
								if(pdp.getPdpId().equals(myPdp.getPdpId())){
									logger.debug
									//System.out.println
									("\n\nDesignatedWaiter.run: myPdp " + myPdp.getPdpId() + " is current and designated, "
											+ "butstandbystatus is not providingservice. "
											+ " Executing stateManagement.demote()" + "\n\n");
									// So, we must demote it
									try {
										//Keep the order like this.  StateManagement is last since it triggers controller shutdown
										//This will change isDesignated and it can enter another if(combination) below
										pdpsConnector.standDownPdp(pdp.getPdpId()); 
										myPdp.setDesignated(false);
										isDesignated = false;
										if(!(standbyStatus.equals(StateManagement.HOT_STANDBY) || 
												standbyStatus.equals(StateManagement.COLD_STANDBY))){
											/*
											 * Only demote it if it appears it has not already been demoted. Don't worry
											 * about synching with the topic endpoint states.  That is done by the 
											 * refreshStateAudit
											 */
											stateManagement.demote();
										}
										//update the standbystatus to check in a later combination of isDesignated and isCurrent
										standbyStatus=stateManagement.getStandbyStatus(pdp.getPdpId());
									} catch (Exception e) {
										logger.error
										//System.out.println
										("DesignatedWaiter.run: myPdp: " + myPdp.getPdpId() + " Caught Exception attempting to demote myPdp'"
												+ myPdp.getPdpId()
												+ "', message="
												+ e.getMessage());
										System.out.println(new Date() + " DesignatedWaiter.run: caught unexpected exception "
												+ "from stateManagement.demote()");
										e.printStackTrace();
									}
								}else{
									// Don't demote a remote PDP that is current.  It should catch itself
									logger.debug
									//System.out.println
									("\n\nDesignatedWaiter.run: myPdp " + myPdp.getPdpId() + " is current and designated, "
											+ "but standbystatus is not providingservice. "
											+ " Cannot execute stateManagement.demote() since it it is not myPdp\n\n");
								}

							}else{
								// If we get here, it is ok to be on the list
								logger.debug
								//System.out.println
								("DesignatedWaiter.run: PDP="
										+ pdp.getPdpId()
										+ " is designated, current and " + standbyStatus +".  Noting PDP as designated.  standbyStatus=" + standbyStatus);
								listOfDesignated.add(pdp);
							}


						}


						/*
						 * The second combination of isDesignated and isCurrent
						 * 					
						 * PDP is designated but not current; it has failed.   So we stand it down (it doesn't matter what
						 * its standbyStatus is). None of these go on the list.
						 */
						if (pdp.isDesignated()  &&  !isCurrent) {
							logger.info
							//System.out.println
							("INFO: DesignatedWaiter.run: PDP="
									+ pdp.getPdpId()
									+ " is currently designated but is not current; it has failed.  Standing down.  standbyStatus=" + standbyStatus);

							/*
							 * Changes designated to 0 but it is still potentially providing service
							 * Will affect isDesignated, so, it can enter an if(combination) below
							 */
							pdpsConnector.standDownPdp(pdp.getPdpId()); 

							//need to change standbystatus to coldstandby
							if (pdp.getPdpId().equals(myPdp.getPdpId())){
								logger.debug
								//System.out.println
								("\n\nDesignatedWaiter.run: myPdp " + myPdp.getPdpId() + " is not Current. "
										+ " Executing stateManagement.disableFailed()" + "\n\n");
								// We found that myPdp is designated but not current
								// So, we must cause it to disableFail
								try {
									myPdp.setDesignated(false);
									//pdpsConnector.setDesignated(myPdp, false);//not needed?
									isDesignated = false;
									stateManagement.disableFailed();
									//stateManagement.demote();
								} catch (Exception e) {
									logger.error
									//System.out.println
									("DesignatedWaiter.run: myPdp: " + myPdp.getPdpId() + " Caught Exception attempting to disableFail myPdp'"
											+ myPdp.getPdpId()
											+ "', message="
											+ e.getMessage());
									System.out.println(new Date() + " DesignatedWaiter.run: caught unexpected exception "
											+ "from stateManagement.disableFailed()");
									e.printStackTrace();
								}
							} else { //it is a remote PDP that is failed
								logger.debug
								//System.out.println
								("\n\nDesignatedWaiter.run: PDP " + pdp.getPdpId() + " is not Current. "
										+ " Executing stateManagement.disableFailed(otherResourceName)" + "\n\n");
								// We found a PDP is designated but not current
								// We already called standdown(pdp) which will change designated to false
								// Now we need to disableFail it to get its states in synch.  The standbyStatus
								// should equal coldstandby
								try {
									stateManagement.disableFailed(pdp.getPdpId());
									//stateManagement.demote(pdp.getPdpId());
								} catch (Exception e) {
									logger.error
									//System.out.println
									("DesignatedWaiter.run: for PDP" + pdp.getPdpId() 
											+ " Caught Exception attempting to disableFail(" + pdp.getPdpId() + ")'"
											+ pdp.getPdpId()
											+ "', message="
											+ e.getMessage());
									System.out.println(new Date() + " DesignatedWaiter.run: caught unexpected exception "
											+ "from stateManagement.disableFailed()");
									e.printStackTrace();
								}

							}
							continue; //we are not going to do anything else with this pdp
						} 

						/*
						 * The third combination of isDesignated and isCurrent
						 * /*
						 * If a PDP is not currently designated but is providing service (erroneous, but recoverable) or hot standby 
						 * we can add it to the list of possible designated if all the designated have failed
						 */
						if (!pdp.isDesignated() && isCurrent){
							if(!(standbyStatus.equals(StateManagement.HOT_STANDBY) ||
									standbyStatus.equals(StateManagement.COLD_STANDBY))){
								logger.info("\n\nDesignatedWaiter.run: PDP " + pdp.getPdpId()
										+ " is NOT designated but IS current and"
										+ " has a standbystatus=" + standbyStatus);
								// Since it is current, we assume it can adjust its own state.
								// We will demote if it is myPdp
								if(pdp.getPdpId().equals(myPdp.getPdpId())){
									//demote it
									logger.info("DesignatedWaiter.run: PDP " + pdp.getPdpId() + " going to "
											+ "setDesignated = false and calling stateManagement.demote");
									try {
										//Keep the order like this.  StateManagement is last since it triggers controller shutdown
										pdpsConnector.setDesignated(myPdp, false);
										myPdp.setDesignated(false);
										isDesignated = false;
										//This is definitely not a redundant call.  It is attempting to correct a problem
										stateManagement.demote();
										//recheck the standbystatus
										standbyStatus = stateManagement.getStandbyStatus(pdp.getPdpId());
									} catch (Exception e) {
										logger.error
										//System.out.println
										("DesignatedWaiter.run: myPdp: " + myPdp.getPdpId() + " Caught Exception attempting to demote myPdp'"
												+ myPdp.getPdpId()
												+ "', message="
												+ e.getMessage());
										System.out.println(new Date() + " DesignatedWaiter.run: caught unexpected exception "
												+ "from stateManagement.demote()");
										e.printStackTrace();
									}

								}
							}
							if(standbyStatus.equals(StateManagement.HOT_STANDBY) && designatedPdpHasFailed){
								//add it to the list
								logger.info
								//System.out.println
								("INFO: DesignatedWaiter.run: PDP=" + pdp.getPdpId()
										+ " is not designated but is " + standbyStatus + " and designated PDP has failed.  standbyStatus=" 
										+ standbyStatus);
								logger.info
								//System.out.println
								("DesignatedWaiter.run: Designating PDP=" + pdp.getPdpId());
								listOfDesignated.add(pdp);
							}
							continue; //done with this one
						}

						/*
						 * The fourth combination of isDesignated and isCurrent
						 * 
						 * We are not going to put any of these on the list since it appears they have failed.

						 * 
						 */
						if(!pdp.isDesignated() && !isCurrent) {
							logger.info
							//System.out.println
							("INFO: DesignatedWaiter.run: PDP="
									+ pdp.getPdpId() + ", designated="
									+ pdp.isDesignated() + ", current="
									+ isCurrent
									+ ", designatedPdpHasFailed="
									+ designatedPdpHasFailed
									+ ",  standbyStatus=" + standbyStatus);
							if(!standbyStatus.equals(StateManagement.COLD_STANDBY)){
								//stand it down
								//disableFail it
								pdpsConnector.standDownPdp(pdp.getPdpId()); 
								if(pdp.getPdpId().equals(myPdp.getPdpId())){
									/*
									 * I don't actually know how this condition could happen, but if it did, we would want
									 * to declare it failed.
									 */
									logger.debug
									//System.out.println
									("\n\nDesignatedWaiter.run: myPdp " + myPdp.getPdpId() + " is !current and !designated, "
											+ " Executing stateManagement.disableFailed()" + "\n\n");
									// So, we must disableFail it
									try {
										//Keep the order like this.  StateManagement is last since it triggers controller shutdown
										myPdp.setDesignated(false);
										isDesignated = false;
										stateManagement.disableFailed();
										//stateManagement.demote();
									} catch (Exception e) {
										logger.error
										//System.out.println
										("DesignatedWaiter.run: myPdp: " + myPdp.getPdpId() + " Caught Exception attempting to disableFail myPdp'"
												+ myPdp.getPdpId()
												+ "', message="
												+ e.getMessage());
										System.out.println(new Date() + " DesignatedWaiter.run: caught unexpected exception "
												+ "from stateManagement.disableFailed()");
										e.printStackTrace();
									}
								}else{//it is remote
									logger.debug
									//System.out.println
									("\n\nDesignatedWaiter.run: myPdp " + myPdp.getPdpId() + " is !current and !designated, "
											+ " Executing stateManagement.disableFailed(" + pdp.getPdpId() + ")" + "\n\n");
									// We already called standdown(pdp) which will change designated to false
									// Now we need to disableFail it to get its states in sync.  StandbyStatus = coldstandby
									try {
										stateManagement.disableFailed(pdp.getPdpId());
										//stateManagement.demote(pdp.getPdpId());
									} catch (Exception e) {
										logger.error
										//System.out.println
										("DesignatedWaiter.run: for PDP" + pdp.getPdpId() 
												+ " Caught Exception attempting to disableFail(" + pdp.getPdpId() + ")'"
												+ pdp.getPdpId()
												+ "', message="
												+ e.getMessage());
										System.out.println(new Date() + " DesignatedWaiter.run: caught unexpected exception "
												+ "from stateManagement.disableFailed()");
										e.printStackTrace();
									}
								}
							}
						}


					} // end pdps loop

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
					 * it will be used in later comparrisons and cannot be null.
					 */
					
					DroolsPdp mostRecentPrimary = computeMostRecentPrimary(pdps, listOfDesignated);
					
					
					/*
					 * It is possible to get here with more than one pdp designated and providingservice. This normally
					 * occurs when there is a race condition with multiple nodes coming up at the same time. If that is
					 * the case we must determine which one is the one that should be designated and which one should
					 * be demoted.
					 * 
					 * It is possible to have 0, 1, 2 or more but not all, or all designated.  
					 *   If we have one designated and current, we chose it and are done
					 *   If we have 2 or more, but not all, we must determine which one is in the same site as
					 *   the previously designated pdp.
					 */
					
					designatedPdp = computeDesignatedPdp(listOfDesignated, mostRecentPrimary);


					if (designatedPdp == null) {
						logger.warn
						//System.out.println
						("WARNING: DesignatedWaiter.run: No viable PDP found to be Designated. designatedPdp still null.");
						// Just to be sure the parameters are correctly set
						myPdp.setDesignated(false);
						pdpsConnector.setDesignated(myPdp,false);
						isDesignated = false;
						
						waitTimerLastRunDate = new Date();
						logger.info("DesignatedWaiter.run (designatedPdp == null) waitTimerLastRunDate = " + waitTimerLastRunDate);
						
						return;
						
					} else if (designatedPdp.getPdpId().equals(myPdp.getPdpId())) {
						logger.debug
						//System.out.println
						("DesignatedWaiter.run: designatedPdp is PDP=" + myPdp.getPdpId());
						/*
						 * update function expects myPdp.isDesignated to be true.
						 */
						try {
							//Keep the order like this.  StateManagement is last since it triggers controller init
							myPdp.setDesignated(true);
							pdpsConnector.setDesignated(myPdp, true);
							isDesignated = true;
							String standbyStatus = stateManagement.getStandbyStatus();
							if(!standbyStatus.equals(StateManagement.PROVIDING_SERVICE)){
								/*
								 * Only call promote if it is not already in the right state.  Don't worry about
								 * synching the lower level topic endpoint states.  That is done by the
								 * refreshStateAudit.
								 * Note that we need to fetch the session list from 'mostRecentPrimary'
								 * at this point -- soon, 'mostRecentPrimary' will be set to this host.
								 */
								this.sessions = mostRecentPrimary.getSessions();
								stateManagement.promote();
							}
						} catch (StandbyStatusException e) {
							logger.error
							//System.out.println
							("ERROR: DesignatedWaiter.run: Caught StandbyStatusException attempting to promote PDP='"
									+ myPdp.getPdpId()
									+ "', message="
									+ e.getMessage());
							myPdp.setDesignated(false);
							pdpsConnector.setDesignated(myPdp,false);
							isDesignated = false;
							//If you can't promote it, demote it
							try {
								String standbyStatus = stateManagement.getStandbyStatus();
								if(!(standbyStatus.equals(StateManagement.HOT_STANDBY) || 
										standbyStatus.equals(StateManagement.COLD_STANDBY))){
									/*
									 * Only call demote if it is not already in the right state.  Don't worry about
									 * synching the lower level topic endpoint states.  That is done by the
									 * refreshStateAudit.
									 */
									stateManagement.demote();
								}
							} catch (Exception e1) {
								logger.error
								//System.out.println
								("ERROR: DesignatedWaiter.run: Caught StandbyStatusException attempting to promote then demote PDP='"
										+ myPdp.getPdpId()
										+ "', message="
										+ e1.getMessage());
								System.out.println(new Date() + " DesignatedWaiter.run: caught unexpected exception "
										+ "from stateManagement.demote()");
								e1.printStackTrace();
							}

						} catch (Exception e) {
							logger.error
							//System.out.println
							("ERROR: DesignatedWaiter.run: Caught Exception attempting to promote PDP='"
									+ myPdp.getPdpId()
									+ "', message="
									+ e.getMessage());
							myPdp.setDesignated(false);
							pdpsConnector.setDesignated(myPdp,false);
							isDesignated = false;
							//If you can't promote it, demote it
							try {
								String standbyStatus = stateManagement.getStandbyStatus();
								if(!(standbyStatus.equals(StateManagement.HOT_STANDBY) || 
										standbyStatus.equals(StateManagement.COLD_STANDBY))){
									/*
									 * Only call demote if it is not already in the right state.  Don't worry about
									 * synching the lower level topic endpoint states.  That is done by the
									 * refreshStateAudit.
									 */
									stateManagement.demote();
								}
							} catch (Exception e1) {
								logger.error
								//System.out.println
								("ERROR: DesignatedWaiter.run: Caught StandbyStatusException attempting to promote then demote PDP='"
										+ myPdp.getPdpId()
										+ "', message="
										+ e1.getMessage());
								System.out.println(new Date() + " DesignatedWaiter.run: caught unexpected exception "
										+ "from stateManagement.demote()");
								e1.printStackTrace();
							}

						}
						waitTimerLastRunDate = new Date();
						logger.info("DesignatedWaiter.run (designatedPdp.getPdpId().equals(myPdp.getPdpId())) waitTimerLastRunDate = " + waitTimerLastRunDate);

						return;
					}
					isDesignated = false;

				} // end synchronized

				logger.debug
				//System.out.println
				("DesignatedWaiter.run: myPdp: " + myPdp.getPdpId() + "; Returning, isDesignated=" + isDesignated);

				Date tmpDate = new Date();
				logger.info("DesignatedWaiter.run (end of run) waitTimerLastRunDate = " + tmpDate);
				
				waitTimerLastRunDate = tmpDate;
				
			}catch(Exception e){
				logger.error("DesignatedWaiter.run caught an unexpected exception: " + e);
				System.out.println(new Date() + " DesignatedWaiter.run: caught unexpected exception");
				e.printStackTrace();
			}
		} // end run
	}
	
	public ArrayList<DroolsPdp> santizeDesignatedList(ArrayList<DroolsPdp> listOfDesignated){

		boolean containsDesignated = false;
		boolean containsHotStandby = false;
		ArrayList<DroolsPdp> listForRemoval = new ArrayList<DroolsPdp>();
		for(DroolsPdp pdp : listOfDesignated){
			logger.debug
			//System.out.println
			("DesignatedWaiter.run sanitizing: pdp = " + pdp.getPdpId() 
					+ " isDesignated = " + pdp.isDesignated());
			if(pdp.isDesignated()){
				containsDesignated = true;
			}else {
				containsHotStandby = true;
				listForRemoval.add(pdp);
			}
		}
		if(containsDesignated && containsHotStandby){
			//remove the hot standby from the list
			listOfDesignated.removeAll(listForRemoval);
			containsHotStandby = false;
		}
		return listOfDesignated;
	}
	
	public DroolsPdp computeMostRecentPrimary(Collection<DroolsPdp> pdps, ArrayList<DroolsPdp> listOfDesignated){
		boolean containsDesignated = false;
		for(DroolsPdp pdp : listOfDesignated){
			if(pdp.isDesignated()){
				containsDesignated = true;
			}
		}
		DroolsPdp mostRecentPrimary = new DroolsPdpImpl(null, true, 1, new Date(0));
		mostRecentPrimary.setSiteName(null);
		logger.debug
		//System.out.println
		("DesignatedWaiter.run listOfDesignated.size() = " + listOfDesignated.size());
		if(listOfDesignated.size() <=1){
			logger.debug("DesignatedWainter.run: listOfDesignated.size <=1");
			//Only one or none is designated or hot standby.  Choose the latest designated date
			for(DroolsPdp pdp : pdps){
				logger.debug
				//System.out.println
				("DesignatedWaiter.run pdp = " + pdp.getPdpId() 
						+ " pdp.getDesignatedDate() = "	+ pdp.getDesignatedDate());
				if(pdp.getDesignatedDate().compareTo(mostRecentPrimary.getDesignatedDate()) > 0){
					mostRecentPrimary = pdp;
					logger.debug
					//System.out.println
					("DesignatedWaiter.run mostRecentPrimary = " + mostRecentPrimary.getPdpId());
				}
			}
		}else if(listOfDesignated.size() == pdps.size()){
			logger.debug("DesignatedWainter.run: listOfDesignated.size = pdps.size() which is " + pdps.size());
			//They are all designated or all hot standby.
			mostRecentPrimary = null;
			for(DroolsPdp pdp : pdps){
				if(mostRecentPrimary == null){
					mostRecentPrimary = pdp;
					continue;
				}
				if(containsDesignated){ //Choose the site of the first designated date
					if(pdp.getDesignatedDate().compareTo(mostRecentPrimary.getDesignatedDate()) < 0){
						mostRecentPrimary = pdp;
						logger.debug
						//System.out.println
						("DesignatedWaiter.run mostRecentPrimary = " + mostRecentPrimary.getPdpId());
					}
				}else{ //Choose the site with the latest designated date
					if(pdp.getDesignatedDate().compareTo(mostRecentPrimary.getDesignatedDate()) > 0){
						mostRecentPrimary = pdp;
						logger.debug
						//System.out.println
						("DesignatedWaiter.run mostRecentPrimary = " + mostRecentPrimary.getPdpId());
					}
				}
			}
		}else{
			logger.debug("DesignatedWainter.run: Some but not all are designated or hot standby. ");
			//Some but not all are designated or hot standby. 
			if(containsDesignated){
				logger.debug("DesignatedWainter.run: containsDesignated = " + containsDesignated);
				/*
				 * The list only contains designated.  This is a problem.  It is most likely a race
				 * condition that resulted in two thinking they should be designated. Choose the 
				 * site with the latest designated date for the pdp not included on the designated list.
				 * This should be the site that had the last designation before this race condition
				 * occurred.
				 */
				for(DroolsPdp pdp : pdps){
					if(listOfDesignated.contains(pdp)){
						continue; //Don't consider this entry
					}
					if(pdp.getDesignatedDate().compareTo(mostRecentPrimary.getDesignatedDate()) > 0){
						mostRecentPrimary = pdp;
						logger.debug
						//System.out.println
						("DesignatedWaiter.run mostRecentPrimary = " + mostRecentPrimary.getPdpId());
					}
				}
			}else{
				logger.debug("DesignatedWainter.run: containsDesignated = " + containsDesignated);
				//The list only contains hot standby. Choose the site of the latest designated date
				for(DroolsPdp pdp : pdps){
					if(pdp.getDesignatedDate().compareTo(mostRecentPrimary.getDesignatedDate()) > 0){
						mostRecentPrimary = pdp;
						logger.debug
						//System.out.println
						("DesignatedWaiter.run mostRecentPrimary = " + mostRecentPrimary.getPdpId());
					}
				}
			}
		}
		return mostRecentPrimary;
	}
	
	public DroolsPdp computeDesignatedPdp(ArrayList<DroolsPdp> listOfDesignated, DroolsPdp mostRecentPrimary){
		DroolsPdp designatedPdp = null;
		DroolsPdp lowestPriorityPdp = null;
		if(listOfDesignated.size() > 1){
			logger.debug
			//System.out.println
			("DesignatedWaiter.run: myPdp: " + myPdp.getPdpId() + " listOfDesignated.size():  " + listOfDesignated.size());					
			DroolsPdp rejectedPdp = null;
			DroolsPdp lowestPrioritySameSite = null;
			DroolsPdp lowestPriorityDifferentSite = null;
			for(DroolsPdp pdp : listOfDesignated){
				// We need to determine if another PDP is the lowest priority
				if(nullSafeEquals(pdp.getSiteName(),mostRecentPrimary.getSiteName())){
					if(lowestPrioritySameSite == null){
						if(lowestPriorityDifferentSite != null){
							rejectedPdp = lowestPriorityDifferentSite;
						}
						lowestPrioritySameSite = pdp;									
					}else{
						if(pdp.getPdpId().equals((lowestPrioritySameSite.getPdpId()))){
							continue;//nothing to compare
						}
						if(pdp.comparePriority(lowestPrioritySameSite) <0){
							logger.debug
							//System.out.println
							("\nDesignatedWaiter.run: myPdp" + myPdp.getPdpId() + " listOfDesignated pdp ID: " + pdp.getPdpId() 
									+ " has lower priority than pdp ID: " + lowestPrioritySameSite.getPdpId());

							//we need to reject lowestPrioritySameSite
							rejectedPdp = lowestPrioritySameSite;
							lowestPrioritySameSite = pdp;
						} else{
							//we need to reject pdp and keep lowestPrioritySameSite
							logger.debug
							//System.out.println
							("\nDesignatedWaiter.run: myPdp" + myPdp.getPdpId() + " listOfDesignated pdp ID: " + pdp.getPdpId() 
									+ " has higher priority than pdp ID: " + lowestPrioritySameSite.getPdpId());
							rejectedPdp = pdp;
						}
					}
				} else{
					if(lowestPrioritySameSite != null){
						//if we already have a candidate for same site, we don't want to bother with different sites
						rejectedPdp = pdp;
					} else{
						if(lowestPriorityDifferentSite == null){
							lowestPriorityDifferentSite = pdp;
							continue;
						}
						if(pdp.getPdpId().equals((lowestPriorityDifferentSite.getPdpId()))){
							continue;//nothing to compare
						}
						if(pdp.comparePriority(lowestPriorityDifferentSite) <0){
							logger.debug
							//System.out.println
							("\nDesignatedWaiter.run: myPdp" + myPdp.getPdpId() + " listOfDesignated pdp ID: " + pdp.getPdpId() 
									+ " has lower priority than pdp ID: " + lowestPriorityDifferentSite.getPdpId());

							//we need to reject lowestPriorityDifferentSite
							rejectedPdp = lowestPriorityDifferentSite;
							lowestPriorityDifferentSite = pdp;
						} else{
							//we need to reject pdp and keep lowestPriorityDifferentSite
							logger.debug
							//System.out.println
							("\nDesignatedWaiter.run: myPdp" + myPdp.getPdpId() + " listOfDesignated pdp ID: " + pdp.getPdpId() 
									+ " has higher priority than pdp ID: " + lowestPriorityDifferentSite.getPdpId());
							rejectedPdp = pdp;
						}
					}
				}
				// If the rejectedPdp is myPdp, we need to stand it down and demote it.  Each pdp is responsible
				// for demoting itself
				if(rejectedPdp != null && nullSafeEquals(rejectedPdp.getPdpId(),myPdp.getPdpId())){
					logger.debug
					//System.out.println
					("\n\nDesignatedWaiter.run: myPdp: " + myPdp.getPdpId() + " listOfDesignated myPdp ID: " + myPdp.getPdpId() 
							+ " is NOT the lowest priority.  Executing stateManagement.demote()" + "\n\n");
					// We found that myPdp is on the listOfDesignated and it is not the lowest priority
					// So, we must demote it
					try {
						//Keep the order like this.  StateManagement is last since it triggers controller shutdown
						myPdp.setDesignated(false);
						pdpsConnector.setDesignated(myPdp, false);
						isDesignated = false;
						String standbyStatus = stateManagement.getStandbyStatus();
						if(!(standbyStatus.equals(StateManagement.HOT_STANDBY) || 
								standbyStatus.equals(StateManagement.COLD_STANDBY))){
							/*
							 * Only call demote if it is not already in the right state.  Don't worry about
							 * synching the lower level topic endpoint states.  That is done by the
							 * refreshStateAudit.
							 */
							stateManagement.demote();
						}
					} catch (Exception e) {
						myPdp.setDesignated(false);
						pdpsConnector.setDesignated(myPdp, false);
						isDesignated = false;
						logger.error
						//System.out.println
						("DesignatedWaiter.run: myPdp: " + myPdp.getPdpId() + " Caught Exception attempting to demote myPdp'"
								+ myPdp.getPdpId()
								+ "', message="
								+ e.getMessage());
						System.out.println(new Date() + " DesignatedWaiter.run: caught unexpected exception "
								+ "from stateManagement.demote()");
						e.printStackTrace();
					}
				}
			} //end: for(DroolsPdp pdp : listOfDesignated)
			if(lowestPrioritySameSite != null){
				lowestPriorityPdp = lowestPrioritySameSite;
			} else {
				lowestPriorityPdp = lowestPriorityDifferentSite;
			}
			//now we have a valid value for lowestPriorityPdp
			logger.debug
			//System.out.println
			("\n\nDesignatedWaiter.run: myPdp: " + myPdp.getPdpId() + " listOfDesignated found the LOWEST priority pdp ID: " 
					+ lowestPriorityPdp.getPdpId() 
					+ " It is now the designatedPpd from the perspective of myPdp ID: " + myPdp + "\n\n");
			designatedPdp = lowestPriorityPdp;

		} else if(listOfDesignated.isEmpty()){
			logger.debug
			//System.out.println
			("\nDesignatedWaiter.run: myPdp: " + myPdp.getPdpId() + " listOfDesignated is: EMPTY.");
			designatedPdp = null;
		} else{ //only one in listOfDesignated
			logger.debug
			//System.out.println
			("\nDesignatedWaiter.run: myPdp: " + myPdp.getPdpId() + " listOfDesignated has ONE entry. PDP ID: "
					+ listOfDesignated.get(0).getPdpId());
			designatedPdp = listOfDesignated.get(0);
		}
		return designatedPdp;

	}
	
	private class TimerUpdateClass extends TimerTask{

		@Override
		public void run() {
			try{
				logger.info("TimerUpdateClass.run: entry");
				checkWaitTimer();
				synchronized(pdpsConnectorLock){
					
					myPdp.setUpdatedDate(new Date());
					if(myPdp.isDesignated()){
						myPdp.setDesignatedDate(new Date());
					}
					pdpsConnector.update(myPdp);
					
					Date tmpDate = new Date();
					logger.info("TimerUpdateClass.run: updateWorkerLastRunDate = " + tmpDate);
					
					updateWorkerLastRunDate = tmpDate;
				}
				logger.info("TimerUpdateClass.run.exit");
			}catch(Exception e){
				logger.error("TimerUpdateClass.run caught an unexpected exception: " + e);
				System.out.println(new Date() + " TimerUpdateClass.run caught an unexpected exception");
				e.printStackTrace();
			}
		}
	}
	@Override
	public void checkThreadStatus() {
		checkUpdateWorkerTimer();
		checkWaitTimer();
	}

	private void checkUpdateWorkerTimer(){
		synchronized(checkUpdateWorkerLock){
			try{
				logger.debug("checkUpdateWorkerTimer: entry");
				Date now = new Date();
				long nowMs = now.getTime();
				long updateWorkerMs = updateWorkerLastRunDate.getTime();
				//give it 2 second cushion
				if((nowMs - updateWorkerMs)  > pdpCheckInterval + 2000){
					logger.error("checkUpdateWorkerTimer: nowMs - updateWorkerMs = " + (nowMs - updateWorkerMs) 
							+ ", exceeds pdpCheckInterval + 2000 = " + (pdpCheckInterval + 2000) + " Will reschedule updateWorker timer");

					try{
						updateWorker.cancel();
						// Recalculate the time because this is a synchronized section and the thread could have
						// been blocked.
						now = new Date();
						nowMs = now.getTime();
						updateWorker = new Timer();
						// reset the updateWorkerLastRunDate
						updateWorkerLastRunDate = new Date(nowMs + 100);
						//execute the first time in 100 ms
						updateWorker.scheduleAtFixedRate(new TimerUpdateClass(), 100, pdpCheckInterval);
						logger.info("checkUpdateWorkerTimer: Scheduling updateWorker timer to start in 100 ms ");
					}catch(Exception e){
						logger.error("checkUpdateWorkerTimer: Caught unexpected Exception: " + e);
						System.out.println(new Date() + " checkUpdateWorkerTimer caught an unexpected exception");
						e.printStackTrace();
						// Recalculate the time because this is a synchronized section and the thread could have
						// been blocked.
						now = new Date();
						nowMs = now.getTime();
						updateWorker = new Timer();
						updateWorkerLastRunDate = new Date(nowMs + 100);
						updateWorker.scheduleAtFixedRate(new TimerUpdateClass(), 100, pdpCheckInterval);
						logger.info("checkUpdateWorkerTimer: Attempting to schedule updateWorker timer in 100 ms");
					}

				}
				logger.debug("checkUpdateWorkerTimer: exit");
			}catch(Exception e){
				logger.error("checkUpdateWorkerTimer: caught unexpected exception: " + e);
				System.out.println(new Date() + " checkUpdateWorkerTimer - top level - caught an unexpected exception");
				e.printStackTrace();
			}
		}
	}

	private void checkWaitTimer(){
		synchronized(checkWaitTimerLock){
			try{
				logger.debug("checkWaitTimer: entry");
				Date now = new Date();
				long nowMs = now.getTime();
				long waitTimerMs = waitTimerLastRunDate.getTime();

				//give it 2 times leeway  
				if((nowMs - waitTimerMs)  > 2*pdpUpdateInterval){
					logger.error("checkWaitTimer: nowMs - waitTimerMs = " + (nowMs - waitTimerMs) 
							+ ", exceeds pdpUpdateInterval + 2000 = " + (2*pdpUpdateInterval) + " Will reschedule waitTimer timer");


					try{
						// Recalculate since the thread could have been stalled on the synchronize()
						nowMs = (new Date()).getTime();
						// Time to the start of the next pdpUpdateInterval multiple
						long startMs = getDWaiterStartMs();
						waitTimer.cancel();
						designationWaiter = new DesignationWaiter();
						waitTimer = new Timer();
						waitTimerLastRunDate = new Date(nowMs + startMs);
						waitTimer.scheduleAtFixedRate(designationWaiter, startMs, pdpUpdateInterval);
						logger.info("checkWaitTimer: Scheduling waitTimer timer to start in " + startMs + " ms");
					}catch(Exception e){
						logger.error("checkWaitTimer: Caught unexpected Exception: " + e);
						System.out.println(new Date() + " checkWaitTimer caught an unexpected exception");
						e.printStackTrace();
						// Recalculate since the thread could have been stalled on the synchronize()
						nowMs = (new Date()).getTime();
						// Time to the start of the next pdpUpdateInterval multiple
						long startMs = getDWaiterStartMs();
						designationWaiter = new DesignationWaiter();
						waitTimer = new Timer();
						waitTimerLastRunDate = new Date(nowMs + startMs);
						waitTimer.scheduleAtFixedRate(designationWaiter, startMs, pdpUpdateInterval);
						logger.info("checkWaitTimer: Scheduling waitTimer timer in " + startMs + " ms");
					}

				}
				logger.debug("checkWaitTimer: exit");
			}catch(Exception e){
				logger.error("checkWaitTimer: caught unexpected exception: " + e);
				System.out.println(new Date() + " checkWaitTimer caught an unexpected exception");
				e.printStackTrace();
			}
		}
	}
	
	private long getDWaiterStartMs(){
		Date now = new Date();
		
		// Retrieve the ms since the epoch
		long nowMs = now.getTime();
		
		// Time since the end of the last pdpUpdateInterval multiple
		long nowModMs = nowMs % pdpUpdateInterval;
		
		// Time to the start of the next pdpUpdateInterval multiple
		long startMs = 2*pdpUpdateInterval - nowModMs;

		// Give the start time a minimum of a 5 second cushion
		if(startMs < 5000){
			// Start at the beginning  of following interval
			startMs = pdpUpdateInterval + startMs;
		}
		return startMs;
	}
	
	private boolean nullSafeEquals(Object one, Object two){
		if(one == null && two == null){
			return true;
		}
		if(one != null && two != null){
			return one.equals(two);
		}
		return false;
	}
}
