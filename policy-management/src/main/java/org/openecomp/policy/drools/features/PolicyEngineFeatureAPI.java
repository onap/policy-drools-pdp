/*-
 * ============LICENSE_START=======================================================
 * policy-engine
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

package org.openecomp.policy.drools.features;

import java.util.Properties;

import org.openecomp.policy.drools.system.PolicyEngine;
import org.openecomp.policy.drools.utils.OrderedService;
import org.openecomp.policy.drools.utils.OrderedServiceImpl;

/**
 * Policy Engine Feature API.
 * Provides Interception Points during the Policy Engine lifecycle.
 */
public interface PolicyEngineFeatureAPI extends OrderedService {
	
	  /**
	   * intercept before the Policy Engine is configured.
	   * 
	   * @return true if this feature intercepts and takes ownership
	   * of the operation preventing the invocation of 
	   * lower priority features.   False, otherwise.
	   */
	  public boolean beforeConfigure(PolicyEngine engine, Properties properties);
	  
	  /**
	   * intercept after the Policy Engine is configured.
	   * 
	   * @return true if this feature intercepts and takes ownership
	   * of the operation preventing the invocation of 
	   * lower priority features.   False, otherwise.
	   */
	  public boolean afterConfigure(PolicyEngine engine);
	  
	  /**
	   * intercept before the Policy Engine goes active.
	   * 
	   * @return true if this feature intercepts and takes ownership
	   * of the operation preventing the invocation of 
	   * lower priority features.   False, otherwise.
	   */
	  public boolean beforeActivate(PolicyEngine engine);
	  
	  /**
	   * intercept after the Policy Engine goes active.
	   * 
	   * @return true if this feature intercepts and takes ownership
	   * of the operation preventing the invocation of 
	   * lower priority features.   False, otherwise.
	   */
	  public boolean afterActivate(PolicyEngine engine);
	  
	  /**
	   * intercept before the Policy Engine goes standby.
	   * 
	   * @return true if this feature intercepts and takes ownership
	   * of the operation preventing the invocation of 
	   * lower priority features.   False, otherwise.
	   */
	  public boolean beforeDeactivate(PolicyEngine engine);
	  
	  /**
	   * intercept after the Policy Engine goes standby.
	   * 
	   * @return true if this feature intercepts and takes ownership
	   * of the operation preventing the invocation of 
	   * lower priority features.   False, otherwise.
	   */
	  public boolean afterDeactivate(PolicyEngine engine);
	  
	  /**
	   * intercept before the Policy Engine is started.
	   * 
	   * @return true if this feature intercepts and takes ownership
	   * of the operation preventing the invocation of 
	   * lower priority features.   False, otherwise.
	   */
	  public boolean beforeStart(PolicyEngine engine);
	  
	  /**
	   * intercept after the Policy Engine is started.
	   * 
	   * @return true if this feature intercepts and takes ownership
	   * of the operation preventing the invocation of 
	   * lower priority features.   False, otherwise.
	   */
	  public boolean afterStart(PolicyEngine engine);
	  
	  /**
	   * intercept before the Policy Engine is stopped.
	   * 
	   * @return true if this feature intercepts and takes ownership
	   * of the operation preventing the invocation of 
	   * lower priority features.   False, otherwise..
	   */
	  public boolean beforeStop(PolicyEngine engine);
	  
	  /**
	   * intercept after the Policy Engine is stopped
	   * 
	   * @return true if this feature intercepts and takes ownership
	   * of the operation preventing the invocation of 
	   * lower priority features.   False, otherwise.d.
	   */
	  public boolean afterStop(PolicyEngine engine);
	  
	  /**
	   * intercept before the Policy Engine is locked
	   * 
	   * @return true if this feature intercepts and takes ownership
	   * of the operation preventing the invocation of 
	   * lower priority features.   False, otherwise.
	   */
	  public boolean beforeLock(PolicyEngine engine);
	  
	  /**
	   * intercept after the Policy Engine is locked
	   * 
	   * @return true if this feature intercepts and takes ownership
	   * of the operation preventing the invocation of 
	   * lower priority features.   False, otherwise..
	   */
	  public boolean afterLock(PolicyEngine engine);
	  
	  /**
	   * intercept before the Policy Engine is locked
	   * 
	   * @return true if this feature intercepts and takes ownership
	   * of the operation preventing the invocation of 
	   * lower priority features.   False, otherwise.
	   */
	  public boolean beforeUnlock(PolicyEngine engine);
	  
	  /**
	   * intercept after the Policy Engine is locked
	   * 
	   * @return true if this feature intercepts and takes ownership
	   * of the operation preventing the invocation of 
	   * lower priority features.   False, otherwise.
	   */
	  public boolean afterUnlock(PolicyEngine engine);
	  
	  /**
	   * intercept the Policy Engine is shut down
	   * 
	   * @return true if this feature intercepts and takes ownership
	   * of the operation preventing the invocation of 
	   * lower priority features.   False, otherwise..
	   */
	  public boolean beforeShutdown(PolicyEngine engine);
	  
	  /**
	   * called after the Policy Engine is shut down
	   * 
	   * @return true if this feature intercepts and takes ownership
	   * of the operation preventing the invocation of 
	   * lower priority features.   False, otherwise.
	   */
	  public boolean afterShutdown(PolicyEngine engine);
	  
	  /**
	   * Feature providers implementing this interface
	   */
	  public static final OrderedServiceImpl<PolicyEngineFeatureAPI> providers = 
			  						new OrderedServiceImpl<PolicyEngineFeatureAPI>(PolicyEngineFeatureAPI.class);
}
