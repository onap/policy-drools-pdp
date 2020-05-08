/*
 * ============LICENSE_START=======================================================
 * feature-server-pool
 * ================================================================================
 * Copyright (C) 2020 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.serverpool;

import static org.onap.policy.drools.serverpool.ServerPoolProperties.DEFAULT_MAINLOOP_CYCLE;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.MAINLOOP_CYCLE;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.getProperty;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides a single thread that is used for 'Server' and 'Bucket'
 * updates. This simplifies things because it greatly reduces the need for
 * synchronization within these classes.
 */
class MainLoop extends Thread {
    private static Logger logger = LoggerFactory.getLogger(MainLoop.class);

    // this queue is used to send work to the 'MainLoop' thread, for things
    // like processing incoming messages
    private static LinkedTransferQueue<Runnable> incomingWork =
        new LinkedTransferQueue<>();

    // this is used for work that should be invoked every cycle
    private static ConcurrentLinkedQueue<Runnable> backgroundWork =
        new ConcurrentLinkedQueue<>();

    // this is the 'MainLoop' thread
    private static volatile MainLoop mainLoop = null;

    // main loop cycle time
    private static long cycleTime;

    /**
     * If it isn't already running, start the 'MainLoop' thread.
     */
    public static synchronized void startThread() {
        cycleTime = getProperty(MAINLOOP_CYCLE, DEFAULT_MAINLOOP_CYCLE);
        if (mainLoop == null) {
            mainLoop = new MainLoop();
            mainLoop.start();
        }
    }

    /**
     * If it is currently running, stop the 'MainLoop' thread.
     */
    public static synchronized void stopThread() {
        // this won't be immediate, but the thread should discover it shortly
        MainLoop saveMainLoop = mainLoop;

        mainLoop = null;
        if (saveMainLoop != null) {
            saveMainLoop.interrupt();
        }
    }

    /**
     * Add some work to the 'incomingWork' queue -- this runs once, and is
     * automatically removed from the queue.
     *
     * @param work this is the Runnable to invoke
     */
    public static void queueWork(Runnable work) {
        incomingWork.offer(work);
    }

    /**
     * Add some work to the 'backgroundWork' queue -- this runs every cycle,
     * until it is manually removed.
     *
     * @param work this is the Runnable to invoke every cycle
     */
    public static void addBackgroundWork(Runnable work) {
        // if it is already here, remove it first
        backgroundWork.remove(work);

        // add to the end of the queue
        backgroundWork.add(work);
    }

    /**
     * Remove work from the 'backgroundWork' queue.
     *
     * @param work this is the Runnable to remove from the queue
     * @return true if the background work was found, and removed
     */
    public static boolean removeBackgroundWork(Runnable work) {
        return backgroundWork.remove(work);
    }

    /**
     * Constructor.
     */
    private MainLoop() {
        super("Main Administrative Loop");
    }

    /**
     * This is the main processing loop for "administrative" messages, which
     * manage 'Server' states.
     * 1) Process incoming messages (other threads are reading in and queueing
     *    the messages), making note of information that should forwarded to
     *    other servers.
     * 2) Send out updates to all servers on the 'notify' list
     * 3) Go through list of all 'Server' entries, and see which ones have
     *    taken too long to respond -- those are treated as 'failed'
     */
    @Override
    public void run() {
        while (this == mainLoop) {
            try {
                // the following reads in messages over a period of 1 second
                handleIncomingWork();

                // send out notifications to other hosts
                Server.sendOutData();

                // search for hosts which have taken too long to respond
                Server.searchForFailedServers();

                // work that runs every cycle
                for (Runnable work : backgroundWork) {
                    try {
                        work.run();
                    } catch (Exception e) {
                        logger.error("Exception in MainLoop background work", e);
                    }
                }
            } catch (Exception e) {
                logger.error("Exception in MainLoop", e);
            }
        }
    }

    /**
     * Poll for and process incoming messages for up to 1 second.
     */
    static void handleIncomingWork() {
        long currentTime = System.currentTimeMillis();
        long wakeUpTime = currentTime + cycleTime;
        long timeDiff;

        // receive incoming messages
        while ((timeDiff = wakeUpTime - currentTime) > 0) {
            try {
                Runnable work =
                    incomingWork.poll(timeDiff, TimeUnit.MILLISECONDS);
                if (work == null) {
                    // timeout -- we are done processing messages for now
                    return;
                }
                work.run();
            } catch (InterruptedException e) {
                logger.error("Interrupted in MainLoop");
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                logger.error("Exception in MainLoop incoming work", e);
            }
            currentTime = System.currentTimeMillis();
        }
    }
}
