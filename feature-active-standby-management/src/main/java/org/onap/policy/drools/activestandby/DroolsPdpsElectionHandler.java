/*-
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

package org.onap.policy.drools.activestandby;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import org.onap.policy.common.im.StateManagement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.onap.policy.drools.statemanagement.StateManagementFeatureAPI;

public class DroolsPdpsElectionHandler implements ThreadRunningChecker {
	// get an instance of logger 
	private final static Logger  logger = LoggerFactory.getLogger(DroolsPdpsElectionHandler.class);	
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

	private String pdpdNowActive;
	private String pdpdLastActive;
	
	private StateManagementFeatureAPI stateManagementFeature;
	
	public DroolsPdpsElectionHandler(DroolsPdpsConnector pdps, DroolsPdp myPdp){
		pdpdNowActive = null;
		pdpdLastActive = null;
		this.pdpsConnector = pdps;
		DroolsPdpsElectionHandler.myPdp = myPdp;
		this.isDesignated = false;
		pdpCheckInterval = 3000;
		try{
			pdpCheckInterval = Integer.parseInt(ActiveStandbyProperties.getProperty(ActiveStandbyProperties.PDP_CHECK_INVERVAL));
		}catch(Exception e){
			logger.error
			("Could not get pdpCheckInterval property. Using default", e);
		}
		pdpUpdateInterval = 2000;
		try{
			pdpUpdateInterval = Integer.parseInt(ActiveStandbyProperties.getProperty(ActiveStandbyProperties.PDP_UPDATE_INTERVAL));
		}catch(Exception e){
			logger.error
			("Could not get pdpUpdateInterval property. Using default", e);
		}	

		Date now = new Date();

		// Retrieve the ms since the epoch
		long nowMs = now.getTime();

		// Create the timer which will update the updateDate in DroolsPdpEntity table.
		// This is the heartbeat 
		updateWorker = new Timer();

		// Schedule the heartbeat to start in 100 ms and run at pdpCheckInterval ms thereafter
		// NOTE: The first run of the TimerUpdateClass results in myPdp being added to the 
		// drools droolsPdpEntity table.
		updateWorker.scheduleAtFixedRate(new TimerUpdateClass(), 100, pdpCheckInterval);
		updateWorkerLastRunDate = new Date(nowMs + 100);

		// Create the timer which will run the election algorithm
		waitTimer = new Timer();

		// Schedule it to start in startMs ms (so it will run after the updateWorker and run at pdpUpdateInterval ms thereafter
		long startMs = getDWaiterStartMs();
		designationWaiter = new DesignationWaiter();
		waitTimer.scheduleAtFixedRate(designationWaiter, startMs, pdpUpdateInterval);
		waitTimerLastRunDate = new Date(nowMs + startMs);

		//Get the StateManagementFeature instance
		
		for (StateManagementFeatureAPI feature : StateManagementFeatureAPI.impl.getList())
		{
			if (feature.getResourceName().equals(myPdp.getPdpId()))
			{
				if(logger.isDebugEnabled()){
					logger.debug("DroolsPdpsElectionHandler: Found StateManagementFeature"
						+ " with resourceName: {}", myPdp.getPdpId());
				}
				stateManagementFeature = feature;
				break;
			}
		}
		if(stateManagementFeature == null){
			logger.error("DroolsPdpsElectionHandler failed to initialize.  "
					+ "Unable to get instance of StateManagementFeatureAPI "
					+ "with resourceID: {}", myPdp.getPdpId());
		}
	}
	
	/*
	 * When the JpaDroolsPdpsConnector.standDown() method is invoked, it needs
	 * access to myPdp, so it can keep its designation status in sync with the
	 * DB.
	 */
	public static void setMyPdpDesignated(boolean designated) {
		if(logger.isDebugEnabled()){
			logger.debug
			("setMyPdpDesignated: designated= {}", designated);
		}
		myPdp.setDesignated(designated);
	}
	
	private class DesignationWaiter extends TimerTask {
		// get an instance of logger 
		private final Logger  logger = LoggerFactory.getLogger(DesignationWaiter.class);

		@Override
		public void run() {
			try{
				if(logger.isDebugEnabled()){
					logger.debug
					("DesignatedWaiter.run: Entering");
				}
				
				// just here initially so code still works
				if (pdpsConnector == null) {
					waitTimerLastRunDate = new Date();
					if(logger.isDebugEnabled()){
						logger.debug("DesignatedWaiter.run (pdpsConnector==null) waitTimerLastRunDate = {}", waitTimerLastRunDate);
					}
					
					return;
				}

				synchronized (designationWaiterLock) {

					if(logger.isDebugEnabled()){
						logger.debug
						("DesignatedWaiter.run: Entering synchronized block");
					}

					checkUpdateWorkerTimer();
					
					//It is possible that multiple PDPs are designated lead.  So, we will make a list of all designated
					//PDPs and then decide which one really should be designated at the end.
					ArrayList<DroolsPdp> listOfDesignated = new ArrayList<DroolsPdp>();

					Collection<DroolsPdp> pdps = pdpsConnector.getDroolsPdps();
					DroolsPdp designatedPdp = null;

					if(logger.isDebugEnabled()){
						logger.debug
						("DesignatedWaiter.run: pdps.size= {}", pdps.size());
					}

					//This is only true if all designated PDPs have failed
					boolean designatedPdpHasFailed = pdpsConnector.hasDesignatedPdpFailed(pdps);
					if(logger.isDebugEnabled()){
						logger.debug
						("DesignatedWaiter.run: designatedPdpHasFailed= {}", designatedPdpHasFailed);
					}
					for (DroolsPdp pdp : pdps) {
						if(logger.isDebugEnabled()){
							logger.debug
							("DesignatedWaiter.run: evaluating pdp ID: {}", pdp.getPdpId());
						}

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
						if(standbyStatus==null){
							// Treat this case as a cold standby -- if we
							// abort here, no sessions will be created in a
							// single-node test environment.
							standbyStatus = StateManagement.COLD_STANDBY;
						}
						if(logger.isDebugEnabled()){
							logger.debug
							("DesignatedWaiter.run: PDP= {},  isCurrent= {}", pdp.getPdpId(), isCurrent);
						}

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
									if(logger.isDebugEnabled()){
										logger.debug
										("\n\nDesignatedWaiter.run: myPdp {} is current and designated, "
											+ "butstandbystatus is not providingservice. "
											+ " Executing stateManagement.demote()" + "\n\n", myPdp.getPdpId());
									}
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
											stateManagementFeature.demote();
										}
										//update the standbystatus to check in a later combination of isDesignated and isCurrent
										standbyStatus=stateManagementFeature.getStandbyStatus(pdp.getPdpId());
									} catch (Exception e) {
										logger.error
										("DesignatedWaiter.run: myPdp: {} "
												+ "Caught Exception attempting to demote myPdp,"
												+ "message= {}", myPdp.getPdpId(), e);
									}
								}else{
									// Don't demote a remote PDP that is current.  It should catch itself
									if(logger.isDebugEnabled()){
										logger.debug
										("\n\nDesignatedWaiter.run: myPdp {} is current and designated, "
											+ "but standbystatus is not providingservice. "
											+ " Cannot execute stateManagement.demote() since it it is not myPdp\n\n", myPdp.getPdpId());
									}
								}

							}else{
								// If we get here, it is ok to be on the list
								if(logger.isDebugEnabled()){
									logger.debug
									("DesignatedWaiter.run: PDP= {} is designated, current and {} Noting PDP as "
										+ "designated, standbyStatus= {}", pdp.getPdpId(), standbyStatus, standbyStatus);
								}
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
							if(logger.isDebugEnabled()){
								logger.debug
								("INFO: DesignatedWaiter.run: PDP= {} is currently designated but is not current; "
									+ "it has failed.  Standing down.  standbyStatus= {}", pdp.getPdpId(), standbyStatus);
							}
							/*
							 * Changes designated to 0 but it is still potentially providing service
							 * Will affect isDesignated, so, it can enter an if(combination) below
							 */
							pdpsConnector.standDownPdp(pdp.getPdpId()); 

							//need to change standbystatus to coldstandby
							if (pdp.getPdpId().equals(myPdp.getPdpId())){
								if(logger.isDebugEnabled()){
									logger.debug
									("\n\nDesignatedWaiter.run: myPdp {} is not Current. "
										+ " Executing stateManagement.disableFailed()\n\n", myPdp.getPdpId());
								}
								// We found that myPdp is designated but not current
								// So, we must cause it to disableFail
								try {
									myPdp.setDesignated(false);
									pdpsConnector.setDesignated(myPdp, false);
									isDesignated = false;
									stateManagementFeature.disableFailed();
								} catch (Exception e) {
									logger.error
									("DesignatedWaiter.run: myPdp: {} Caught Exception "
											+ "attempting to disableFail myPdp {}, message= {}",
											myPdp.getPdpId(), myPdp.getPdpId(), e);
								}
							} else { //it is a remote PDP that is failed
								if(logger.isDebugEnabled()){
									logger.debug
									("\n\nDesignatedWaiter.run: PDP {} is not Current. "
										+ " Executing stateManagement.disableFailed(otherResourceName)\n\n", pdp.getPdpId() );
								}
								// We found a PDP is designated but not current
								// We already called standdown(pdp) which will change designated to false
								// Now we need to disableFail it to get its states in synch.  The standbyStatus
								// should equal coldstandby
								try {
									stateManagementFeature.disableFailed(pdp.getPdpId());
								} catch (Exception e) {
									logger.error
									("DesignatedWaiter.run: for PDP {}  Caught Exception attempting to "
											+ "disableFail({}), message= {}",
											pdp.getPdpId(), pdp.getPdpId(), e);
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
								if(logger.isDebugEnabled()){
									logger.debug("\n\nDesignatedWaiter.run: PDP {}"
										+ " is NOT designated but IS current and"
										+ " has a standbystatus= {}", pdp.getPdpId(), standbyStatus);
								}
								// Since it is current, we assume it can adjust its own state.
								// We will demote if it is myPdp
								if(pdp.getPdpId().equals(myPdp.getPdpId())){
									//demote it
									if(logger.isDebugEnabled()){
										logger.debug("DesignatedWaiter.run: PDP {} going to "
											+ "setDesignated = false and calling stateManagement.demote",  pdp.getPdpId());
									}
									try {
										//Keep the order like this.  StateManagement is last since it triggers controller shutdown
										pdpsConnector.setDesignated(myPdp, false);
										myPdp.setDesignated(false);
										isDesignated = false;
										//This is definitely not a redundant call.  It is attempting to correct a problem
										stateManagementFeature.demote();
										//recheck the standbystatus
										standbyStatus = stateManagementFeature.getStandbyStatus(pdp.getPdpId());
									} catch (Exception e) {
										logger.error
										("DesignatedWaiter.run: myPdp: {} Caught Exception "
												+ "attempting to demote myPdp {}, message = {}",  myPdp.getPdpId(),
												myPdp.getPdpId(), e);
									}

								}
							}
							if(standbyStatus.equals(StateManagement.HOT_STANDBY) && designatedPdpHasFailed){
								//add it to the list
								if(logger.isDebugEnabled()){
									logger.debug
									("INFO: DesignatedWaiter.run: PDP= {}"
										+ " is not designated but is {} and designated PDP "
										+ "has failed.  standbyStatus= {}", pdp.getPdpId(), 
										standbyStatus, standbyStatus);
								}
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
							if(logger.isDebugEnabled()){
								logger.debug
								("INFO: DesignatedWaiter.run: PDP= {} "
									+ "designated= {}, current= {}, "
									+ "designatedPdpHasFailed= {}, "
									+ "standbyStatus= {}",pdp.getPdpId(), 
									pdp.isDesignated(), isCurrent, designatedPdpHasFailed, standbyStatus);
							}
							if(!standbyStatus.equals(StateManagement.COLD_STANDBY)){
								//stand it down
								//disableFail it
								pdpsConnector.standDownPdp(pdp.getPdpId()); 
								if(pdp.getPdpId().equals(myPdp.getPdpId())){
									/*
									 * I don't actually know how this condition could happen, but if it did, we would want
									 * to declare it failed.
									 */
									if(logger.isDebugEnabled()){
										logger.debug
										("\n\nDesignatedWaiter.run: myPdp {} is !current and !designated, "
											+ " Executing stateManagement.disableFailed()\n\n", myPdp.getPdpId());
									}
									// So, we must disableFail it
									try {
										//Keep the order like this.  StateManagement is last since it triggers controller shutdown
										pdpsConnector.setDesignated(myPdp, false);
										myPdp.setDesignated(false);
										isDesignated = false;
										stateManagementFeature.disableFailed();
									} catch (Exception e) {
										logger.error
										("DesignatedWaiter.run: myPdp: {} Caught Exception attempting to "
												+ "disableFail myPdp {}, message= {}", 
												 myPdp.getPdpId(), myPdp.getPdpId(), e);
									}
								}else{//it is remote
									if(logger.isDebugEnabled()){
										logger.debug
										("\n\nDesignatedWaiter.run: myPdp {} is !current and !designated, "
											+ " Executing stateManagement.disableFailed({})\n\n", 
											myPdp.getPdpId(), pdp.getPdpId());
									}
									// We already called standdown(pdp) which will change designated to false
									// Now we need to disableFail it to get its states in sync.  StandbyStatus = coldstandby
									try {
										stateManagementFeature.disableFailed(pdp.getPdpId());
									} catch (Exception e) {
										logger.error
										("DesignatedWaiter.run: for PDP {}" 
												+ " Caught Exception attempting to disableFail({})"
												+ ", message=", pdp.getPdpId(), pdp.getPdpId(), e);
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
					 * it will be used in later comparisons and cannot be null.
					 */
					
					DroolsPdp mostRecentPrimary = computeMostRecentPrimary(pdps, listOfDesignated);
					
					if(mostRecentPrimary != null){
						pdpdLastActive = mostRecentPrimary.getPdpId();
					}
					
					
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
					if(designatedPdp != null){
						pdpdNowActive = designatedPdp.getPdpId();
					}

					if (designatedPdp == null) {
						logger.warn
						("WARNING: DesignatedWaiter.run: No viable PDP found to be Designated. designatedPdp still null.");
						// Just to be sure the parameters are correctly set
						myPdp.setDesignated(false);
						pdpsConnector.setDesignated(myPdp,false);
						isDesignated = false;
						
						waitTimerLastRunDate = new Date();
						if(logger.isDebugEnabled()){
							logger.debug("DesignatedWaiter.run (designatedPdp == null) waitTimerLastRunDate = {}", waitTimerLastRunDate);
						}
						
						return;
						
					} else if (designatedPdp.getPdpId().equals(myPdp.getPdpId())) {
						if(logger.isDebugEnabled()){
							logger.debug
							("DesignatedWaiter.run: designatedPdp is PDP={}", myPdp.getPdpId());
						}
						/*
						 * update function expects myPdp.isDesignated to be true.
						 */
						try {
							//Keep the order like this.  StateManagement is last since it triggers controller init
							myPdp.setDesignated(true);
							myPdp.setDesignatedDate(new Date());
							pdpsConnector.setDesignated(myPdp, true);
							isDesignated = true;
							String standbyStatus = stateManagementFeature.getStandbyStatus();
							if(!standbyStatus.equals(StateManagement.PROVIDING_SERVICE)){
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
							logger.error
							("ERROR: DesignatedWaiter.run: Caught Exception attempting to promote PDP={}"
									+ ", message=", myPdp.getPdpId(), e);
							myPdp.setDesignated(false);
							pdpsConnector.setDesignated(myPdp,false);
							isDesignated = false;
							//If you can't promote it, demote it
							try {
								String standbyStatus = stateManagementFeature.getStandbyStatus();
								if(!(standbyStatus.equals(StateManagement.HOT_STANDBY) || 
										standbyStatus.equals(StateManagement.COLD_STANDBY))){
									/*
									 * Only call demote if it is not already in the right state.  Don't worry about
									 * synching the lower level topic endpoint states.  That is done by the
									 * refreshStateAudit.
									 */
									stateManagementFeature.demote();
								}
							} catch (Exception e1) {
								logger.error
								("ERROR: DesignatedWaiter.run: Caught StandbyStatusException "
										+ "attempting to promote then demote PDP={}, message=",
										myPdp.getPdpId(), e1);
							}

						} 
						waitTimerLastRunDate = new Date();
						if(logger.isDebugEnabled()){
							logger.debug("DesignatedWaiter.run (designatedPdp.getPdpId().equals(myPdp.getPdpId())) "
								+ "waitTimerLastRunDate = " + waitTimerLastRunDate);
						}

						return;
					}
					isDesignated = false;

				} // end synchronized
				if(logger.isDebugEnabled()){
					logger.debug
					("DesignatedWaiter.run: myPdp: {}; Returning, isDesignated= {}",
						isDesignated, myPdp.getPdpId());
				}

				Date tmpDate = new Date();
				if(logger.isDebugEnabled()){
					logger.debug("DesignatedWaiter.run (end of run) waitTimerLastRunDate = {}", tmpDate);
				}
				
				waitTimerLastRunDate = tmpDate;
				
			}catch(Exception e){
				logger.error("DesignatedWaiter.run caught an unexpected exception: ", e);
			}
		} // end run
	}
	
	public ArrayList<DroolsPdp> santizeDesignatedList(ArrayList<DroolsPdp> listOfDesignated){

		boolean containsDesignated = false;
		boolean containsHotStandby = false;
		ArrayList<DroolsPdp> listForRemoval = new ArrayList<DroolsPdp>();
		for(DroolsPdp pdp : listOfDesignated){
			if(logger.isDebugEnabled()){
				logger.debug
				("DesignatedWaiter.run sanitizing: pdp = {}" 
					+ " isDesignated = {}",pdp.getPdpId(), pdp.isDesignated());
			}
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
		if(logger.isDebugEnabled()){
			logger.debug
			("DesignatedWaiter.run listOfDesignated.size() = {}", listOfDesignated.size());
		}
		if(listOfDesignated.size() <=1){
			if(logger.isDebugEnabled()){
				logger.debug("DesignatedWainter.run: listOfDesignated.size <=1");
			}
			//Only one or none is designated or hot standby.  Choose the latest designated date
			for(DroolsPdp pdp : pdps){
				if(logger.isDebugEnabled()){
					logger.debug
					("DesignatedWaiter.run pdp = {}" 
						+ " pdp.getDesignatedDate() = {}", pdp.getPdpId(), pdp.getDesignatedDate());
				}
				if(pdp.getDesignatedDate().compareTo(mostRecentPrimary.getDesignatedDate()) > 0){
					mostRecentPrimary = pdp;
					if(logger.isDebugEnabled()){
						logger.debug
						("DesignatedWaiter.run mostRecentPrimary = {}", mostRecentPrimary.getPdpId());
					}
				}
			}
		}else if(listOfDesignated.size() == pdps.size()){
			if(logger.isDebugEnabled()){
				logger.debug("DesignatedWainter.run: listOfDesignated.size = pdps.size() which is {}", pdps.size());
			}
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
						if(logger.isDebugEnabled()){
							logger.debug
							("DesignatedWaiter.run mostRecentPrimary = {}", mostRecentPrimary.getPdpId());
						}
					}
				}else{ //Choose the site with the latest designated date
					if(pdp.getDesignatedDate().compareTo(mostRecentPrimary.getDesignatedDate()) > 0){
						mostRecentPrimary = pdp;
						if(logger.isDebugEnabled()){
							logger.debug
							("DesignatedWaiter.run mostRecentPrimary = {}", mostRecentPrimary.getPdpId());
						}
					}
				}
			}
		}else{
			if(logger.isDebugEnabled()){
				logger.debug("DesignatedWainter.run: Some but not all are designated or hot standby. ");
			}
			//Some but not all are designated or hot standby. 
			if(containsDesignated){
				if(logger.isDebugEnabled()){
					logger.debug("DesignatedWainter.run: containsDesignated = {}", containsDesignated);
				}
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
						if(logger.isDebugEnabled()){
							logger.debug
							("DesignatedWaiter.run mostRecentPrimary = {}", mostRecentPrimary.getPdpId());
						}
					}
				}
			}else{
				if(logger.isDebugEnabled()){
					logger.debug("DesignatedWainter.run: containsDesignated = {}", containsDesignated);
				}
				//The list only contains hot standby. Choose the site of the latest designated date
				for(DroolsPdp pdp : pdps){
					if(pdp.getDesignatedDate().compareTo(mostRecentPrimary.getDesignatedDate()) > 0){
						mostRecentPrimary = pdp;
						if(logger.isDebugEnabled()){
							logger.debug
							("DesignatedWaiter.run mostRecentPrimary = {}", mostRecentPrimary.getPdpId());
						}
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
			if(logger.isDebugEnabled()){
				logger.debug
				("DesignatedWaiter.run: myPdp: {} listOfDesignated.size(): {}", myPdp.getPdpId(), listOfDesignated.size());
			}
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
							if(logger.isDebugEnabled()){
								logger.debug
								("\nDesignatedWaiter.run: myPdp {}  listOfDesignated pdp ID: {}" 
									+ " has lower priority than pdp ID: {}",myPdp.getPdpId(), pdp.getPdpId(),
									lowestPrioritySameSite.getPdpId());
							}
							//we need to reject lowestPrioritySameSite
							rejectedPdp = lowestPrioritySameSite;
							lowestPrioritySameSite = pdp;
						} else{
							//we need to reject pdp and keep lowestPrioritySameSite
							if(logger.isDebugEnabled()){
								logger.debug
								("\nDesignatedWaiter.run: myPdp {} listOfDesignated pdp ID: {} " 
									+ " has higher priority than pdp ID: {}", myPdp.getPdpId(),pdp.getPdpId(),
									lowestPrioritySameSite.getPdpId());
							}
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
							if(logger.isDebugEnabled()){
								logger.debug
								("\nDesignatedWaiter.run: myPdp {} listOfDesignated pdp ID: {}" 
									+ " has lower priority than pdp ID: {}", myPdp.getPdpId(), pdp.getPdpId(),
									lowestPriorityDifferentSite.getPdpId());
							}
							//we need to reject lowestPriorityDifferentSite
							rejectedPdp = lowestPriorityDifferentSite;
							lowestPriorityDifferentSite = pdp;
						} else{
							//we need to reject pdp and keep lowestPriorityDifferentSite
							if(logger.isDebugEnabled()){
								logger.debug
								("\nDesignatedWaiter.run: myPdp {} listOfDesignated pdp ID: {}" 
									+ " has higher priority than pdp ID: {}", myPdp.getPdpId(), pdp.getPdpId(),
									lowestPriorityDifferentSite.getPdpId());
							}
							rejectedPdp = pdp;
						}
					}
				}
				// If the rejectedPdp is myPdp, we need to stand it down and demote it.  Each pdp is responsible
				// for demoting itself
				if(rejectedPdp != null && nullSafeEquals(rejectedPdp.getPdpId(),myPdp.getPdpId())){
					if(logger.isDebugEnabled()){
						logger.debug
						("\n\nDesignatedWaiter.run: myPdp: {} listOfDesignated myPdp ID: {}" 
							+ " is NOT the lowest priority.  Executing stateManagement.demote()\n\n", myPdp.getPdpId(),
							 myPdp.getPdpId());
					}
					// We found that myPdp is on the listOfDesignated and it is not the lowest priority
					// So, we must demote it
					try {
						//Keep the order like this.  StateManagement is last since it triggers controller shutdown
						myPdp.setDesignated(false);
						pdpsConnector.setDesignated(myPdp, false);
						isDesignated = false;
						String standbyStatus = stateManagementFeature.getStandbyStatus();
						if(!(standbyStatus.equals(StateManagement.HOT_STANDBY) || 
								standbyStatus.equals(StateManagement.COLD_STANDBY))){
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
						logger.error
						("DesignatedWaiter.run: myPdp: {} Caught Exception attempting to "
								+ "demote myPdp {} myPdp.getPdpId(), message= {}", myPdp.getPdpId(), 
								e);
					}
				}
			} //end: for(DroolsPdp pdp : listOfDesignated)
			if(lowestPrioritySameSite != null){
				lowestPriorityPdp = lowestPrioritySameSite;
			} else {
				lowestPriorityPdp = lowestPriorityDifferentSite;
			}
			//now we have a valid value for lowestPriorityPdp
			if(logger.isDebugEnabled()){
				logger.debug
				("\n\nDesignatedWaiter.run: myPdp: {} listOfDesignated "
					+ "found the LOWEST priority pdp ID: {} " 
					+ " It is now the designatedPpd from the perspective of myPdp ID: {} \n\n",
					 myPdp.getPdpId(), lowestPriorityPdp.getPdpId(), myPdp);
			}
			designatedPdp = lowestPriorityPdp;

		} else if(listOfDesignated.isEmpty()){
			if(logger.isDebugEnabled()){
				logger.debug
				("\nDesignatedWaiter.run: myPdp: {} listOfDesignated is: EMPTY.", myPdp.getPdpId());
			}
			designatedPdp = null;
		} else{ //only one in listOfDesignated
			if(logger.isDebugEnabled()){
				logger.debug
				("\nDesignatedWaiter.run: myPdp: {} listOfDesignated "
					+ "has ONE entry. PDP ID: {}", myPdp.getPdpId(), listOfDesignated.get(0).getPdpId());
			}
			designatedPdp = listOfDesignated.get(0);
		}
		return designatedPdp;

	}
	
	private class TimerUpdateClass extends TimerTask{

		@Override
		public void run() {
			try{
				if(logger.isDebugEnabled()){
					logger.debug("TimerUpdateClass.run: entry");
				}
				checkWaitTimer();
				synchronized(pdpsConnectorLock){
					
					myPdp.setUpdatedDate(new Date());
					/*
					Redundant with DesignationWaiter and this updates the date every
					cycle instead of just when the state changes.			
					if(myPdp.isDesignated()){
						myPdp.setDesignatedDate(new Date());
					}
 					*/					
					pdpsConnector.update(myPdp);
					
					Date tmpDate = new Date();
					if(logger.isDebugEnabled()){
						logger.debug("TimerUpdateClass.run: updateWorkerLastRunDate = {}", tmpDate);
					}
					
					updateWorkerLastRunDate = tmpDate;
				}
				if(logger.isDebugEnabled()){
					logger.debug("TimerUpdateClass.run.exit");
				}
			}catch(Exception e){
				logger.error("TimerUpdateClass.run caught an unexpected exception: ", e);
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
				if(logger.isDebugEnabled()){
					logger.debug("checkUpdateWorkerTimer: entry");
				}
				Date now = new Date();
				long nowMs = now.getTime();
				long updateWorkerMs = updateWorkerLastRunDate.getTime();
				//give it 2 second cushion
				if((nowMs - updateWorkerMs)  > pdpCheckInterval + 2000){
					logger.error("checkUpdateWorkerTimer: nowMs - updateWorkerMs = {} " 
							+ ", exceeds pdpCheckInterval + 2000 = {} "
							+ "Will reschedule updateWorker timer",(nowMs - updateWorkerMs), (pdpCheckInterval + 2000));

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
						if(logger.isDebugEnabled()){
							logger.debug("checkUpdateWorkerTimer: Scheduling updateWorker timer to start in 100 ms ");
						}
					}catch(Exception e){
						logger.error("checkUpdateWorkerTimer: Caught unexpected Exception: ", e);
						// Recalculate the time because this is a synchronized section and the thread could have
						// been blocked.
						now = new Date();
						nowMs = now.getTime();
						updateWorker = new Timer();
						updateWorkerLastRunDate = new Date(nowMs + 100);
						updateWorker.scheduleAtFixedRate(new TimerUpdateClass(), 100, pdpCheckInterval);
						if(logger.isDebugEnabled()){
							logger.debug("checkUpdateWorkerTimer: Attempting to schedule updateWorker timer in 100 ms");
						}
					}

				}
				if(logger.isDebugEnabled()){
					logger.debug("checkUpdateWorkerTimer: exit");
				}
			}catch(Exception e){
				logger.error("checkUpdateWorkerTimer: caught unexpected exception: ", e);
			}
		}
	}

	private void checkWaitTimer(){
		synchronized(checkWaitTimerLock){
			try{
				if(logger.isDebugEnabled()){
					logger.debug("checkWaitTimer: entry");
				}
				Date now = new Date();
				long nowMs = now.getTime();
				long waitTimerMs = waitTimerLastRunDate.getTime();

				//give it 10 times leeway  
				if((nowMs - waitTimerMs)  > 10*pdpUpdateInterval){
					logger.error("checkWaitTimer: nowMs - waitTimerMs = {}" 
							+ ", exceeds 10* pdpUpdateInterval = {}"
							+ "Will reschedule waitTimer timer", (nowMs - waitTimerMs), (10*pdpUpdateInterval));

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
						if(logger.isDebugEnabled()){
							logger.debug("checkWaitTimer: Scheduling waitTimer timer to start in {} ms", startMs);
						}
					}catch(Exception e){
						logger.error("checkWaitTimer: Caught unexpected Exception: ", e);
						// Recalculate since the thread could have been stalled on the synchronize()
						nowMs = (new Date()).getTime();
						// Time to the start of the next pdpUpdateInterval multiple
						long startMs = getDWaiterStartMs();
						designationWaiter = new DesignationWaiter();
						waitTimer = new Timer();
						waitTimerLastRunDate = new Date(nowMs + startMs);
						waitTimer.scheduleAtFixedRate(designationWaiter, startMs, pdpUpdateInterval);
						if(logger.isDebugEnabled()){
							logger.debug("checkWaitTimer: Scheduling waitTimer timer in {} ms", startMs);
						}
					}

				}
				if(logger.isDebugEnabled()){
					logger.debug("checkWaitTimer: exit");
				}
			}catch(Exception e){
				logger.error("checkWaitTimer: caught unexpected exception: ", e);
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
	
	public String getPdpdNowActive(){
		return pdpdNowActive;
	}
	
	public String getPdpdLastActive(){
		return pdpdLastActive;
	}
}
