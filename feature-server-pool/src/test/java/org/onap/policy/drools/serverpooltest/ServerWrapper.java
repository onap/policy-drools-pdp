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

package org.onap.policy.drools.serverpooltest;

import java.util.Collection;
import java.util.UUID;

/**
 * This class provides base classes for accessing the various 'Server'
 * classes. There is a separate copy of the 'Server' class for each
 * adapter, and this wrapper was created to give them a common interface.
 */
public interface ServerWrapper {
    /**
     * This calls the 'Server.toString()' method
     *
     * @return a string of the form 'Server[UUID]'
     */
    public String toString();

    /**
     * This calls the 'Server.getUuid()' method
     *
     * @return the UUID associated with this Server
     */
    public UUID getUuid();

    /**
     * This calls the 'Server.isActive()' method
     *
     * @return 'true' if the this server is active, and 'false' if not
     */
    public boolean isActive();

    /* ============================================================ */

    /**
     * This class provides access to the static 'Server' methods. There are
     * multiple 'Server' classes (one for each 'Adapter'), and each has
     * a corresponding 'ServerWrapper.Static' instance. In other words, there
     * is one 'Server.Static' instance for each simulated host.
     */
    public interface Static {
        /**
         * This calls the static 'Server.getThisServer()' method
         *
         * @return a 'ServerWrapper' instance that corresponds to the Server
         *     instance associated with this simulated host
         */
        public ServerWrapper getThisServer();

        /**
         * This calls the static 'Server.getFirstServer()' method
         *
         * @return a 'ServerWrapper' instance that corresponds to the first
         *     'Server' instance in the 'servers' list (the one with the
         *     lowest UUID)
         */
        public ServerWrapper getFirstServer();

        /**
         * This calls the static 'Server.getServer(UUID)' method
         *
         * @param uuid the key to the lookup
         * @return a 'ServerWrapper' instance that corresponds to the associated
         *     'Server' instance ('null' if none)
         */
        public ServerWrapper getServer(UUID uuid);

        /**
         * This calls the static 'Server.getServerCount()' method
         *
         * @return a count of the number of servers
         */
        public int getServerCount();

        /**
         * This calls the static 'Server.getServers()' method
         *
         * @return the complete list of servers, each with a 'ServerWrapper'
         *     referring to the 'Server'
         */
        public Collection<ServerWrapper> getServers();
    }
}
