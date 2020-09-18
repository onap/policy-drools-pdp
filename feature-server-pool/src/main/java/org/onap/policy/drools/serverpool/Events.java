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

import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * This interface is used to distribute notifications of various system
 * events, such as new 'Server' instances, or a server failing.
 */
public class Events {
    // set of listeners receiving event notifications
    private static final Queue<Events> listeners =
        new ConcurrentLinkedQueue<>();

    /**
     * add a listener to the set of listeners receiving events.
     *
     * @param handler the listener
     */
    public static void register(Events handler) {
        // if it is already here, remove it first
        listeners.remove(handler);

        // add to the end of the queue
        listeners.add(handler);
    }

    /**
     * remove a listener from the set of listeners.
     */
    public static boolean unregister(Events handler) {
        return listeners.remove(handler);
    }

    public static Collection<Events> getListeners() {
        return listeners;
    }

    /* ============================================================ */

    /**
     * Notification that a new server has been discovered.
     *
     * @param server this is the new server
     */
    public void newServer(Server server) {
        // do nothing
    }

    /**
     * Notification that a server has failed.
     *
     * @param server this is the server that failed
     */
    public void serverFailed(Server server) {
        // do nothing
    }

    /**
     * Notification that a new lead server has been selected.
     *
     * @param server this is the new lead server
     */
    public void newLeader(Server server) {
        // do nothing
    }

    /**
     * Notification that the lead server has gone down.
     *
     * @param server the lead server that failed
     */
    public void leaderFailed(Server server) {
        // do nothing
    }

    /**
     * Notification that a new selection just completed, but the same
     * leader has been chosen (this may be in response to a new server
     * joining earlier).
     *
     * @param server the current leader, which has been confirmed
     */
    public void leaderConfirmed(Server server) {
        // do nothing
    }
}
