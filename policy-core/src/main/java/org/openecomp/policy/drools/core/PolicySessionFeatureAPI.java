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

package org.openecomp.policy.drools.core;

import org.kie.api.runtime.KieSession;
import org.openecomp.policy.drools.utils.OrderedService;
import org.openecomp.policy.drools.utils.OrderedServiceImpl;

/**
 * This interface provides a way to invoke optional features at various
 * points in the code. At appropriate points in the
 * application, the code iterates through this list, invoking these optional
 * methods. Most of the methods here are notification only -- these tend to
 * return a 'void' value. In other cases, such as 'activatePolicySession',
 * may 
 */
public interface PolicySessionFeatureAPI extends OrderedService
{
  /**
   * 'FeatureAPI.impl.getList()' returns an ordered list of objects
   * implementing the 'FeatureAPI' interface.
   */
  static public OrderedServiceImpl<PolicySessionFeatureAPI> impl =
	new OrderedServiceImpl<PolicySessionFeatureAPI>(PolicySessionFeatureAPI.class);

  /**
   * This method is called during initialization at a point right after
   * 'PolicyContainer' initialization has completed.
   *
   * @param args standard 'main' arguments, which are currently ignored
   * @param configDir the relative directory containing configuration files
   */
  default public void globalInit(String args[], String configDir) {}

  /**
   * This method is used to create a 'KieSession' as part of a
   * 'PolicyContainer'. The caller of this method will iterate over the
   * implementers of this interface until one returns a non-null value.
   *
   * @param policyContainer the 'PolicyContainer' instance containing this
   *	session
   * @param name the name of the KieSession (which is also the name of
   *	the associated PolicySession)
   * @param kieBaseName the name of the 'KieBase' instance containing
   *	this session
   * @return a new KieSession, if one was created, or 'null' if not
   *	(this depends on the capabilities and state of the object implementing
   *	this interface)
   */
  default public KieSession activatePolicySession
	(PolicyContainer policyContainer, String name, String kieBaseName)
  {
	return(null);
  }

  /**
   * This method is called after a new 'PolicySession' has been initialized,
   * and linked to the 'PolicyContainer'.
   *
   * @param policySession the new 'PolicySession' instance
   */
  default public void newPolicySession(PolicySession policySession) {}

  /**
   * This method is called after 'KieSession.dispose()' is called
   *
   * @param policySession the 'PolicySession' object that wrapped the
   *	'KieSession'
   */
  default public void disposeKieSession(PolicySession policySession) {}

  /**
   * This method is called after 'KieSession.destroy()' is called
   *
   * @param policySession the 'PolicySession' object that wrapped the
   *	'KieSession'
   */
  default public void destroyKieSession(PolicySession policySession) {}
}
