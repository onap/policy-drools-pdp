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

package org.onap.policy.drools.core;

import java.util.ArrayList;
import org.kie.api.runtime.KieSession;

/**
 * This class supports 'DroolsContainerTest' by implementing
 * 'PolicySessionFeatureAPI', and providing a means to indicate
 * which hooks have been invoked.
 */
public class TestPolicySessionFeatureAPI implements PolicySessionFeatureAPI
{
  // contains the log entries since the most recent 'getLog()' call
  static private ArrayList<String> log = new ArrayList<>();

  // if 'true', trigger an exception right after doing the log,
  // to verify that exceptions are handled
  static private boolean exceptionTrigger = false;

  /**
   * @return the current contents of the log, and clear the log
   */
  static public ArrayList<String> getLog()
  {
	synchronized(log)
	  {
		ArrayList<String> rval = new ArrayList<String>(log);
		log.clear();
		return(rval);
	  }
  }

  /**
   * This method controls whether these hooks trigger an exception after
   * being invoked.
   *
   * @param indicator if 'true', subsequent hook method calls will trigger
   *	an exception; if 'false', no exception is triggered
   */
  static public void setExceptionTrigger(boolean indicator)
  {
	exceptionTrigger = indicator;
  }

  /**
   * This method adds an entry to the log, and possibly triggers an exception
   *
   * @param arg value to add to the log
   */
  static private void addLog(String arg)
  {
	if (exceptionTrigger)
	  {
		// the log entry will include a '-exception' appended to the end
		synchronized(log)
		  {
			log.add(arg + "-exception");
		  }
		System.out.println("*** " + arg + "-exception invoked ***");

		// throw an exception -- it is up to the invoking code to catch it
		throw(new IllegalStateException("Triggered from " + arg));
	  }
	else
	  {
		// create a log entry, and display to standard output
		synchronized(log)
		  {
			log.add(arg);
		  }
		System.out.println("*** " + arg + " invoked ***");
	  }
  }

  /***************************************/
  /* 'PolicySessionFeatureAPI' interface */
  /***************************************/

  /**
   * {@inheritDoc}
   */
  public int getSequenceNumber()
  {
	return(1);
  }

  /**
   * {@inheritDoc}
   */
  public void globalInit(String args[], String configDir)
  {
	addLog("globalInit");
  }

  /**
   * {@inheritDoc}
   */
  public KieSession activatePolicySession
	(PolicyContainer policyContainer, String name, String kieBaseName)
  {
	addLog("activatePolicySession");
	return(null);
  }

  /**
   * {@inheritDoc}
   */
  public void newPolicySession(PolicySession policySession)
  {
	addLog("newPolicySession");
  }

  /**
   * {@inheritDoc}
   */
  public PolicySession.ThreadModel selectThreadModel(PolicySession session)
  {
	addLog("selectThreadModel");
	return(null);
  }

  /**
   * {@inheritDoc}
   */
  public void disposeKieSession(PolicySession policySession)
  {
	addLog("disposeKieSession");
  }

  /**
   * {@inheritDoc}
   */
  public void destroyKieSession(PolicySession policySession)
  {
	addLog("destroyKieSession");
  }
}
