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

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.UUID;
import org.onap.policy.drools.serverpooltest.ServerWrapper;

/**
 * This class implements the 'ServerWrapper' interface. There is one
 * 'ServerWrapperImpl' class for each simulated host.
 */
public class ServerWrapperImpl implements ServerWrapper {
    // this maps a 'Server' instance on this host to an associated wrapper
    private static IdentityHashMap<Server, ServerWrapperImpl> serverToWrapper =
        new IdentityHashMap<>();

    // this is the 'Server' instance associated with the wrapper
    private Server server;

    /**
     * This method maps a 'Server' instance into a 'ServerWrapperImpl'
     * instance. The goal is to have only a single 'ServerWrapperImpl' instance
     * for each 'Server' instance, so that testing for identity will work
     * as expected.
     *
     * @param server the 'Server' instance
     * @return the associated 'ServerWrapperImpl' instance
     *     ('null' if 'server' is 'null')
     */
    static synchronized ServerWrapperImpl getWrapper(Server server) {
        if (server == null) {
            return null;
        }
        ServerWrapperImpl rval = serverToWrapper.computeIfAbsent(server,
            (key) -> new ServerWrapperImpl(server));
        return rval;
    }

    /**
     * Constructor - initialize the 'server' field.
     */
    private ServerWrapperImpl(Server server) {
        this.server = server;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return server.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UUID getUuid() {
        return server.getUuid();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isActive() {
        return server.isActive();
    }

    /* ============================================================ */

    /**
     * This class implements the 'ServerWrapper.Static' interface. There is
     * one 'ServerWrapperImpl.Static' class, and one instance for each
     * simulated host
     */
    public static class Static implements ServerWrapper.Static {
        /**
         * {@inheritDoc}
         */
        @Override
        public ServerWrapper getThisServer() {
            return getWrapper(Server.getThisServer());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ServerWrapper getFirstServer() {
            return getWrapper(Server.getFirstServer());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ServerWrapper getServer(UUID uuid) {
            return getWrapper(Server.getServer(uuid));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getServerCount() {
            return Server.getServerCount();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Collection<ServerWrapper> getServers() {
            // build an 'ArrayList' which mirrors the set of servers
            ArrayList<ServerWrapper> rval = new ArrayList<>(Server.getServerCount());

            for (Server server : Server.getServers()) {
                rval.add(getWrapper(server));
            }
            return rval;
        }
    }
}
