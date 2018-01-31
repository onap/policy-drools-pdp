/*-
 * ============LICENSE_START=======================================================
 * feature-test-transaction
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

import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.system.PolicyController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

  /**
   * register a controller for monitoring test transactions
   *
   * @param controller policy controller
   */
  public void register(PolicyController controller);

  /**
   * unregisters a controller for monitoring test transactions
   *
   * @param controller policy controller
   */
  public void unregister(PolicyController controller);
}


/**
 * Implementation of TestTransaction interface. Controls the registering/unregistering of
 * PolicyController objects and the management of their related TTControllerTask threads.
 */
class TTImpl implements TestTransaction {

  protected final Map<String, TTControllerTask> controllers = new HashMap<>();

  @Override
  public synchronized void register(PolicyController controller) {
    if (this.controllers.containsValue(controller)) {
      final TTControllerTask controllerTask = this.controllers.get(controller.getName());
      if (controllerTask.isAlive())
        return;

      // continue : unregister, register operation
    }

    final TTControllerTask controllerTask = new TTControllerTask(controller);
    this.controllers.put(controller.getName(), controllerTask);
  }

  @Override
  public synchronized void unregister(PolicyController controller) {
    if (!this.controllers.containsValue(controller))
      return;

    final TTControllerTask controllerTask = this.controllers.get(controller.getName());
    controllerTask.stop();

    this.controllers.remove(controller.getName());
  }
}


/**
 * TTControllerTask implements the Runnabale interface Carries out the injection of an event into a
 * drools session and subsequent query of a counter to ensure that forward progress is occuring.
 *
 */
class TTControllerTask implements Runnable {

  // get an instance of logger
  private static final Logger logger = LoggerFactory.getLogger(TTControllerTask.class);

  protected final PolicyController controller;

  protected volatile boolean alive = true;
  protected final Thread thread = new Thread(this);

  public TTControllerTask(PolicyController controller) {
    this.controller = controller;
    this.thread.setName("tt-controller-task-" + controller.getName());
    this.thread.start();
  }

  public PolicyController getController() {
    return this.controller;
  }

  public synchronized boolean isAlive() {
    return this.alive;
  }

  public synchronized void stop() {
    this.alive = false;
    this.thread.interrupt();
    try {
      this.thread.join(1000);
    } catch (final InterruptedException e) {
      logger.error("TestTransaction thread threw", e);
      this.thread.interrupt();
    }
  }

  public Thread getThread() {
    return this.thread;
  }

  @Override
  public void run() {
    try {
      final List<String> sessions = this.controller.getDrools().getSessionNames();

      if (!(this.controller.getDrools().isBrained())) {
        this.alive = false;
        logger.error(this + ": unknown drools controller");
        return;
      }

      final DroolsController drools = this.controller.getDrools();

      final HashMap<String, Long> fpcs = new HashMap<>();
      for (final String session : sessions) {
        fpcs.put(session, -1L);
      }

      while (this.controller.isAlive() && !this.controller.isLocked() && drools.isBrained()
          && this.alive) {

        injectTxIntoSessions(sessions, fpcs, drools);

        if (!this.alive)
          return;

        if (!Thread.currentThread().isInterrupted())
          Thread.sleep(TestTransaction.DEFAULT_TT_TASK_SLEEP);
      }
    } catch (final InterruptedException e) {
      logger.info("{}: stopping ...", this, e);
      Thread.currentThread().interrupt();
    } catch (final IllegalArgumentException e) {
      logger.error("{}: controller {} has not been enabled for testing: ", this,
          this.controller.getName(), e.getMessage(), e);
    } catch (final Exception e) {
      logger.error("Controller: {} is not testable - TestTransaction caught exception: {} ",
          this.controller.getName(), e.getMessage());
      logger.error("TestTransaction thread threw", e);
    } finally {
      logger.info("Exiting: {}", this);
      this.alive = false;
    }
  }

  private void injectTxIntoSessions(List<String> sessions, HashMap<String, Long> fpcs, DroolsController drools) {

      for (final String session : sessions) {
        final List<Object> facts = this.controller.getDrools().factQuery(session,
            TestTransaction.TT_FPC, TestTransaction.TT_COUNTER, false);
        if (facts == null || facts.size() != 1) {
          /*
           * unexpected something wrong here, can't expect to recover note this exception is
           * caught right below at the exit of run()
           */
          logger.error(
              "Controller: {}, with rules artifact: (group) {}, (artifact) {}, (version) {} - FPC query failed after EventObject insertion! ",
              this.controller.getName(), this.controller.getDrools().getGroupId(),
              this.controller.getDrools().getArtifactId(),
              this.controller.getDrools().getVersion());
          break;
        }
        logger.debug("Facts: {}", facts);

        final long fpc = (Long) facts.get(0);
        if (fpc != fpcs.get(session))
          logger.info("Controller: {} , session {}  - Forward progress successful: {} -> {}",
              this.controller.getName(), session, fpcs.get(session), fpc);
        else
          logger.error("Controller: {}, session {} - Forward progress failure: {}",
              this.controller.getName(), session, fpc);

        fpcs.put(session, fpc);
        drools.getContainer().insert(session, new EventObject(TestTransaction.TT_UUID));
      }

  }


  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("TTControllerTask [controller=");
    builder.append(this.controller);
    builder.append(", alive=");
    builder.append(this.alive);
    builder.append(", thread=");
    builder.append(this.thread.getName());
    builder.append("]");
    return builder.toString();
  }
}
