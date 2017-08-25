/*-
 * ============LICENSE_START=======================================================
 * policy-test-transaction
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
package org.onap.policy.drools.testtransaction;

import java.util.EventObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.onap.policy.common.logging.flexlogger.FlexLogger;
import org.onap.policy.common.logging.flexlogger.Logger;
import org.onap.policy.drools.controller.internal.MavenDroolsController;
import org.onap.policy.drools.system.PolicyController;

/**
 * TestTransaction interface 
 *
 */
public interface TestTransaction {

	public static final String TT_FPC = "TT.FPC";
	public static final String TT_COUNTER = "$ttc";
	public static final String TT_UUID = "43868e59-d1f3-43c2-bd6f-86f89a61eea5";
	public static long DEFAULT_TT_TASK_SLEEP = 20000;
	
	public static final TestTransaction manager = new TTImpl();

	public void register(PolicyController controller);
	public void unregister(PolicyController controller);
}

/**
 * Implementation of TestTransaction interface.
 * Controls the registering/unregistering of 
 * PolicyController objects and the management
 * of their related TTControllerTask threads. 
 */
class TTImpl implements TestTransaction {
	
	protected Map<String, TTControllerTask> controllers = new HashMap<>();

	@Override
	public synchronized void register(PolicyController controller) {
		if (controllers.containsValue(controller)) {
			TTControllerTask controllerTask = 
									controllers.get(controller.getName());
			if (controllerTask.isAlive())
				return;
			
			// continue : unregister, register operation
		}
		
		TTControllerTask controllerTask = new TTControllerTask(controller);
		controllers.put(controller.getName(), controllerTask);
	}

	@Override
	public synchronized void unregister(PolicyController controller) {
		if (!controllers.containsValue(controller))
			return;
		
		TTControllerTask controllerTask = controllers.get(controller.getName());
		controllerTask.stop();
		
		controllers.remove(controller.getName());
	}
}

/**
 * TTControllerTask implements the Runnabale interface
 * Carries out the injection of an event into a drools 
 * session and subsequent query of a counter to ensure
 * that forward progress is occuring. 
 * 
 */
class TTControllerTask implements Runnable {
    // get an instance of logger 
	static private Logger logger = FlexLogger.getLogger(TTControllerTask.class); 
   
	protected final PolicyController controller;

	protected volatile boolean alive = true;
	protected final Thread thread = new Thread(this);

	public TTControllerTask(PolicyController controller) {
		this.controller = controller;
		thread.setName("tt-controller-task-" + controller.getName());
		thread.start();
	}
	
	public PolicyController getController() {
		return controller;
	}

	public synchronized boolean isAlive() {
		return alive;
	}

	public synchronized void stop() {
		this.alive = false;
		thread.interrupt();
		try {thread.join(1000);} catch (InterruptedException e) {}
	}	
	
	public Thread getThread() {
		return thread;
	}

	@Override
	public void run() {
		try {	
			List<String> sessions = 
					controller.getDrools().getSessionNames();
			
			if (!(controller.getDrools() instanceof MavenDroolsController)) {
				alive = false;
				logger.error(this + ": unknown drools controller");
				return;
			}
			
			MavenDroolsController drools = (MavenDroolsController) controller.getDrools();
			
			HashMap<String,Long> fpcs = new HashMap<>();
			for (String session: sessions) {
				fpcs.put(session, -1L);
			}
			
			while (controller.isAlive() && 
				   !controller.isLocked() &&
				   drools.isBrained() &&
				   alive) {
				
				for (String session : sessions) {
					List<Object> facts = controller.getDrools().factQuery(session,
							TestTransaction.TT_FPC,
							TestTransaction.TT_COUNTER,
							false);
					if (facts == null || facts.size() != 1) {
						/* 
						 * unexpected something wrong here, can't expect to recover 
						 * note this exception is caught right below at the exit of run()
						 */
						logger.error("Controller: "+ controller.getName() +
		                             ", with rules artifact: (group)" + controller.getDrools().getGroupId() +
		                             ", (artifact)" + controller.getDrools().getArtifactId() + 
		                             ", (version)" + controller.getDrools().getVersion() +
		                             " - FPC query failed after EventObject insertion!");
						break;
					}
					logger.debug(facts);
					
					long fpc = (Long) facts.get(0);
					if (fpc != fpcs.get(session))
						logger.info("Controller: "+ controller.getName() +
								", session" + session +
								" - Forward progress successful: " + fpcs.get(session) + 
								" -> " + fpc);
					else 
						logger.error("Controller: "+ controller.getName() +
								", session" + session +
								" - Forward progress failure: " + fpc);
					
					fpcs.put(session, fpc);									
					drools.getContainer().insert(session, new EventObject(TestTransaction.TT_UUID));
				}
				
				if (!alive)
					return;
				
				if (!Thread.currentThread().isInterrupted())
					Thread.sleep(TestTransaction.DEFAULT_TT_TASK_SLEEP);
			}
		} catch (InterruptedException e) {
			logger.info("Stopping: " + this);
		} 
		catch (IllegalArgumentException e) {
			logger.error("Controller: "+ controller.getName() + " has not been enabled for testing: " +
						 e.getMessage());
		} catch (Exception e) {
			logger.error("Controller: "+ controller.getName() + " is not testable" +
						 " - TestTransaction caught exception: " + e.getMessage());
		} finally {
			logger.info("Exiting: " + this);
			alive = false;
		}
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("TTControllerTask [controller=");
		builder.append(controller);
		builder.append(", alive=");
		builder.append(alive);
		builder.append(", thread=");
		builder.append(thread.getName());
		builder.append("]");
		return builder.toString();
	}
	
}
