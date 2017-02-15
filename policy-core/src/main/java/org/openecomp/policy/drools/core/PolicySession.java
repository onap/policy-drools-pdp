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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Properties;

import org.kie.api.runtime.KieSession;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.AgendaEventListener;
import org.kie.api.event.rule.AgendaGroupPoppedEvent;
import org.kie.api.event.rule.AgendaGroupPushedEvent;
import org.kie.api.event.rule.BeforeMatchFiredEvent;
import org.kie.api.event.rule.MatchCancelledEvent;
import org.kie.api.event.rule.MatchCreatedEvent;
import org.kie.api.event.rule.ObjectDeletedEvent;
import org.kie.api.event.rule.ObjectInsertedEvent;
import org.kie.api.event.rule.ObjectUpdatedEvent;
import org.kie.api.event.rule.RuleRuntimeEventListener;
import org.kie.api.event.rule.RuleFlowGroupActivatedEvent;
import org.kie.api.event.rule.RuleFlowGroupDeactivatedEvent;

import org.openecomp.policy.drools.core.jmx.PdpJmx;
import org.openecomp.policy.common.logging.flexlogger.FlexLogger;
import org.openecomp.policy.common.logging.flexlogger.Logger;
import org.openecomp.policy.common.logging.eelf.MessageCodes;

/**
 * This class is a wrapper around 'KieSession', which adds the following:
 *
 * 1) A thread running 'KieSession.fireUntilHalt()'
 * 2) Access to UEB
 * 3) Logging of events
 */
public class PolicySession
  implements AgendaEventListener, RuleRuntimeEventListener
{
	// get an instance of logger 
  private static Logger  logger = FlexLogger.getLogger(PolicySession.class);		
  // name of the 'PolicySession' and associated 'KieSession'
  private String name;

  // the associated 'PolicyContainer', which may have additional
  // 'PolicySession' instances in addition to this one
  private PolicyContainer container;

  // associated 'KieSession' instance
  private KieSession kieSession;

  // if not 'null', this is the thread running 'kieSession.fireUntilHalt()'
  private Thread thread = null;

  // supports 'getCurrentSession()' method
  static private ThreadLocal<PolicySession> policySession =
	new ThreadLocal<PolicySession>();

  /**
   * Internal constructor - create a 'PolicySession' instance
   *
   * @param name the name of this 'PolicySession' (and 'kieSession')
   * @param container the 'PolicyContainer' instance containing this session
   * @param kieSession the associated 'KieSession' instance
   */
  protected PolicySession(String name,
						  PolicyContainer container, KieSession kieSession)
  {
	this.name = name;
	this.container = container;
	this.kieSession = kieSession;
	kieSession.addEventListener((AgendaEventListener)this);
	kieSession.addEventListener((RuleRuntimeEventListener)this);
  }

  /**
   * @return the 'PolicyContainer' object containing this session
   */
  public PolicyContainer getPolicyContainer()
  {
	return(container);
  }

  /**
   * @return the associated 'KieSession' instance
   */
  public KieSession getKieSession()
  {
	return(kieSession);
  }

  /**
   * @return the local name of this session, which should either match the
   * name specified in 'kmodule.xml' file associated with this session, or the
   * name passed on the 'PolicyContainer.adoptKieSession' method.
   */
  public String getName()
  {
	return(name);
  }

  /**
   * @return the 'PolicyContainer' name, followed by ':', followed by the
   * local name of the session. It should be useful in log messages.
   */
  public String getFullName()
  {
	return(container.getName() + ":" + name);
  }

  /**
   * this starts a separate thread, which invokes 'KieSession.fireUntilHalt()'.
   * It does nothing if the thread already exists.
   */
  public synchronized void startThread()
  {
	if (thread == null)
	  {
		logger.info("startThread with name " + getFullName());
		thread = new Thread("Session " + getFullName())
		  {
			public void run()
			  {
				// set thread local variable
				policySession.set(PolicySession.this);

				// We want to continue, despite any exceptions that occur
				// while rules are fired.
				boolean repeat = true;
				while (repeat)
				  {
					if(this.isInterrupted()){
						break;
					}
					try
					  {
						kieSession.fireAllRules();
						
					  }
					catch (Throwable e)
					  {
						logger.error(MessageCodes.EXCEPTION_ERROR, e, "startThread", "kieSession.fireUntilHalt");							
					  }
					try {
						Thread.sleep(5000);
					} catch (InterruptedException e) {
						break;
					}
				  }
				logger.info("fireUntilHalt() returned");
			  }
		  };
		thread.start();
	  }
  }

  /**
   * if a thread is currently running, this invokes 'KieSession.halt()' to
   * stop it.
   */
  public synchronized void stopThread()
  {
	if (thread != null)
	  {
		// this should cause the thread to exit		
		thread.interrupt();
		try
		  {
			// wait for the thread to stop
			thread.join();
		  }
		catch (Exception e)
		  {
			logger.error(MessageCodes.EXCEPTION_ERROR, e, "stopThread", "thread.join");
		  }
		thread = null;
	  }
  }

  /**
   * @return the 'PolicySession' instance associated with the current thread
   *	(Note that this only works if the current thread is the one running
   *	'kieSession.fireUntilHalt()'.)
   */
  public static PolicySession getCurrentSession()
  {
	return(policySession.get());
  }

  /***********************************/
  /* 'AgendaEventListener' interface */
  /***********************************/

  /**
   * {@inheritDoc}
   */
  @Override
	public void afterMatchFired(AfterMatchFiredEvent event)
  {
	logger.debug("afterMatchFired: " + getFullName()
			   + ": AgendaEventListener.afterMatchFired(" + event + ")");
	PdpJmx.getInstance().ruleFired();
 }

  /**
   * {@inheritDoc}
   */
  @Override
	public void afterRuleFlowGroupActivated(RuleFlowGroupActivatedEvent event)
  {
	logger.debug("afterRuleFlowGroupActivated: " + getFullName()
			   + ": AgendaEventListener.afterRuleFlowGroupActivated(" + event + ")");
  }

  /**
   * {@inheritDoc}
   */
  @Override
	public void afterRuleFlowGroupDeactivated
	(RuleFlowGroupDeactivatedEvent event)
  {
	logger.debug("afterRuleFlowGroupDeactivated: " + getFullName()
			   + ": AgendaEventListener.afterRuleFlowGroupDeactivated(" + event + ")");
  }

  /**
   * {@inheritDoc}
   */
  @Override
	public void agendaGroupPopped(AgendaGroupPoppedEvent event)
  {
	logger.debug("agendaGroupPopped: " + getFullName()
			   + ": AgendaEventListener.agendaGroupPopped(" + event + ")");
  }

  /**
   * {@inheritDoc}
   */
  @Override
	public void agendaGroupPushed(AgendaGroupPushedEvent event)
  {
	logger.debug("agendaGroupPushed: " + getFullName()
			   + ": AgendaEventListener.agendaGroupPushed(" + event + ")");
  }

  /**
   * {@inheritDoc}
   */
  @Override
	public void beforeMatchFired(BeforeMatchFiredEvent event)
  {
	logger.debug("beforeMatchFired: " + getFullName()
			   + ": AgendaEventListener.beforeMatchFired(" + event + ")");
  }

  /**
   * {@inheritDoc}
   */
  @Override
	public void beforeRuleFlowGroupActivated
	(RuleFlowGroupActivatedEvent event)
  {
	logger.debug("beforeRuleFlowGroupActivated: " + getFullName()
			   + ": AgendaEventListener.beforeRuleFlowGroupActivated(" + event + ")");
  }

  /**
   * {@inheritDoc}
   */
  @Override
	public void beforeRuleFlowGroupDeactivated
	(RuleFlowGroupDeactivatedEvent event)
  {
	logger.debug("beforeRuleFlowGroupDeactivated: " + getFullName()
			   + ": AgendaEventListener.beforeRuleFlowGroupDeactivated(" + event + ")");
  }

  /**
   * {@inheritDoc}
   */
  @Override
	public void matchCancelled(MatchCancelledEvent event)
  {
	logger.debug("matchCancelled: " + getFullName()
			   + ": AgendaEventListener.matchCancelled(" + event + ")");
  }

  /**
   * {@inheritDoc}
   */
  @Override
	public void matchCreated(MatchCreatedEvent event)
  {
	logger.debug("matchCreated: " + getFullName()
			   + ": AgendaEventListener.matchCreated(" + event + ")");
  }

  /****************************************/
  /* 'RuleRuntimeEventListener' interface */
  /****************************************/

  /**
   * {@inheritDoc}
   */
  @Override
	public void objectDeleted(ObjectDeletedEvent event)
  {
	logger.debug("objectDeleted: " + getFullName()
			   + ": AgendaEventListener.objectDeleted(" + event + ")");
  }

  /**
   * {@inheritDoc}
   */
  @Override
	public void objectInserted(ObjectInsertedEvent event)
  {
	logger.debug("objectInserted: " + getFullName()
			   + ": AgendaEventListener.objectInserted(" + event + ")");
  }

  /**
   * {@inheritDoc}
   */
  @Override
	public void objectUpdated(ObjectUpdatedEvent event)
  {
	logger.debug("objectUpdated: " + getFullName()
			   + ": AgendaEventListener.objectUpdated(" + event + ")");
  }
}
