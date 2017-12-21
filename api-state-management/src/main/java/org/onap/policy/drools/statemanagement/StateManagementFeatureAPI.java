/*-
 * ============LICENSE_START=======================================================
 * policy-core
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

package org.onap.policy.drools.statemanagement;

import java.util.Observer;

import javax.validation.constraints.NotNull;

import org.onap.policy.common.im.AllSeemsWellException;
import org.onap.policy.common.im.StandbyStatusException;
import org.onap.policy.common.im.StateManagement;
import org.onap.policy.drools.properties.Lockable;
import org.onap.policy.drools.utils.OrderedService;
import org.onap.policy.drools.utils.OrderedServiceImpl;

/**
 * This interface provides a way to invoke optional features at various
 * points in the code. At appropriate points in the
 * application, the code iterates through this list, invoking these optional
 * methods. Most of the methods here are notification only -- these tend to
 * return a 'void' value. In other cases, such as 'activatePolicySession',
 * may 
 */
public interface StateManagementFeatureAPI extends OrderedService, Lockable
{
	
	public static final String LOCKED               = StateManagement.LOCKED;
	  public static final String UNLOCKED             = StateManagement.UNLOCKED;
	  public static final String ENABLED              = StateManagement.ENABLED;
	  public static final String DISABLED             = StateManagement.DISABLED;
	  public static final String ENABLE_NOT_FAILED    = StateManagement.ENABLE_NOT_FAILED;
	  public static final String DISABLE_FAILED       = StateManagement.DISABLE_FAILED;
	  public static final String FAILED               = StateManagement.FAILED;
	  public static final String DEPENDENCY           = StateManagement.DEPENDENCY;
	  public static final String DEPENDENCY_FAILED    = StateManagement.DEPENDENCY_FAILED;
	  public static final String DISABLE_DEPENDENCY   = StateManagement.DISABLE_DEPENDENCY;
	  public static final String ENABLE_NO_DEPENDENCY = StateManagement.ENABLE_NO_DEPENDENCY;
	  public static final String NULL_VALUE           = StateManagement.NULL_VALUE;
	  public static final String LOCK                 = StateManagement.LOCK;
	  public static final String UNLOCK               = StateManagement.UNLOCK;
	  public static final String PROMOTE              = StateManagement.PROMOTE;
	  public static final String DEMOTE               = StateManagement.DEMOTE;
	  public static final String HOT_STANDBY          = StateManagement.HOT_STANDBY;
	  public static final String COLD_STANDBY         = StateManagement.COLD_STANDBY;
	  public static final String PROVIDING_SERVICE    = StateManagement.PROVIDING_SERVICE;
	  
	  public static final String ADMIN_STATE     = StateManagement.ADMIN_STATE;
	  public static final String OPERATION_STATE = StateManagement.OPERATION_STATE;
	  public static final String AVAILABLE_STATUS= StateManagement.AVAILABLE_STATUS;
	  public static final String STANDBY_STATUS  = StateManagement.STANDBY_STATUS;
	  
	  static public final Boolean ALLSEEMSWELL = Boolean.TRUE;
	  static public final Boolean ALLNOTWELL = Boolean.FALSE;
	  
	  public static final int SEQ_NUM = 0;
  /**
   * 'FeatureAPI.impl.getList()' returns an ordered list of objects
   * implementing the 'FeatureAPI' interface.
   */
  public static OrderedServiceImpl<StateManagementFeatureAPI> impl =
	new OrderedServiceImpl<>(StateManagementFeatureAPI.class);

  /**
   * ALL SEEMS/NOT WELL
   * This interface is used to support the concept of All Seems/Not Well.  It provides
   * a way for client code to indicate to the DroolsPDPIntegrityMonitor that an event 
   * has occurred which is disabling (or enabling) for the Drools PDP.  The call is
   * actually implemented in the common modules IntegrityMonitor where it will cause
   * the testTransaction to fail if any module has set the value ALLNOTWELL, stopping
   * the forward progress counter and eventually causing the operational state to 
   * become disabled.
   * 
   * ALLSEEMSWELL is passed to the method when the client is healthy
   * ALLNOTWELL is passed to the method when the client is disabled
   * 
   * @param key - This should be a unique identifier for the entity making the call (e.g., class name)
   * @param asw - This is the indicator of health. See constants: ALLSEEMSWELL or ALLNOTWELL
   * @param msg - A message is required.  It should indicate why all is not well or a message indicating
   * that a component has been restored to health (perhaps indicating the problem that has resolved).
   * @throws IllegalArgumentException
   * @throws AllSeemsWellException
   */
  public void allSeemsWell(@NotNull String key, @NotNull Boolean asw, @NotNull String msg)
		  throws IllegalArgumentException, AllSeemsWellException;
  
  /**
   * This method is called to add an Observer to receive notifications of state changes
   * 
   * @param stateChangeObserver
   */
  public void addObserver(Observer stateChangeObserver);

  /**
   * This method returns the X.731 Administrative State for this resource
   * 
   * @return String (locked, unlocked)
   */
  public String getAdminState();
  
  /**
   * This method returns the X.731 Operational State for this resource
   * 
   * @return String (enabled, disabled)
   */
  public String getOpState();
  
  /**
   * This method returns the X.731 Availability Status for this resource
   * 
   * @return String (failed; dependency; dependency,failed)
   */
  public String getAvailStatus();
    
  /**
   * This method returns the X.731 Standby Status for this resource
   * 
   * @return String (providingservice, hotstandby or coldstandby)
   */
  public String getStandbyStatus();
  
  /**
   * This method returns the X.731 Standby Status for the named resource
   * @param String (resourceName)
   * @return String (providingservice, hotstandby or coldstandby)
   */
  public String getStandbyStatus(String resourceName);
  
  /**
   * This method moves the X.731 Operational State for the named resource
   * into a value of disabled and the Availability Status to a value of failed.
   * As a consequence the Standby Status value will take a value of coldstandby.
   * 
   * @param String (resourceName)
   * @throws Exception 
   */
  public void disableFailed(String resourceName) throws Exception;
  
  /**
   * This method moves the X.731 Operational State for this resource
   * into a value of disabled and the Availability Status to a value of failed.
   * As a consequence the Standby Status value will take a value of coldstandby.
   * 
   * @param String (resourceName)
   * @throws Exception 
   */
  public void disableFailed() throws Exception;
  
  /**
   * This method moves the X.731 Standby Status for this resource from hotstandby 
   * to providingservice. If the current value is coldstandby, no change is made.
   * If the current value is null, it will move to providingservice assuming the
   * Operational State is enabled and Administrative State is unlocked.
   * @throws Exception 
   * @throws StandbyStatusException 
   */
  public void promote() throws StandbyStatusException, Exception;
  
  /**
   * This method moves the X.731 Standby Status for this resource from providingservice 
   * to hotstandby. If the current value is null, it will move to hotstandby assuming the
   * Operational State is enabled and Administrative State is unlocked. Else, it will move
   * to coldstandby
   * @throws Exception 
   */
  public void demote() throws Exception;
  
  /**
   * This method returns the resourceName associated with this instance of the StateManagementFeature 
   * @return String (resourceName)
   */
  public String getResourceName();

  /**
   * This Lockable method will lock the StateManagement object Admin state
   * @return true if successfull, false otherwise
   */
  @Override
  public boolean lock();
  
  /**
   * This Lockable method will unlock the StateManagement object Admin state
   * @return true if successfull, false otherwise
   */
  @Override
  public boolean unlock();

  /**
   * This Lockable method indicates the Admin state StateManagement object 
   * @return true if locked, false otherwise
   */
  @Override
  public boolean isLocked();
}
