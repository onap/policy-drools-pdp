/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2019 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.healthcheck;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.ws.rs.core.Response;
import org.onap.policy.common.endpoints.http.client.HttpClient;
import org.onap.policy.common.endpoints.http.client.HttpClientFactory;
import org.onap.policy.common.endpoints.http.client.HttpClientFactoryInstance;
import org.onap.policy.common.endpoints.http.server.HttpServletServer;
import org.onap.policy.common.endpoints.http.server.HttpServletServerFactory;
import org.onap.policy.common.endpoints.http.server.HttpServletServerFactoryInstance;
import org.onap.policy.drools.persistence.SystemPersistenceConstants;
import org.onap.policy.drools.system.PolicyEngine;
import org.onap.policy.drools.system.PolicyEngineConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Healthcheck Monitor.
 */
public class HealthCheckManager implements HealthCheck {

    /**
     * Logger.
     */
    private static Logger logger = LoggerFactory.getLogger(HealthCheckManager.class);

    /**
     * Attached http servers.
     */
    protected List<HttpServletServer> servers = new ArrayList<>();

    /**
     * Attached http clients.
     */
    protected List<HttpClient> clients = new ArrayList<>();

    /**
     * Healthcheck configuration.
     */
    protected Properties healthCheckProperties = null;

    /**
     * {@inheritDoc}.
     */
    @Override
    public Reports healthCheck() {
        Reports reports = new Reports();
        boolean thisEngineIsAlive = getEngineManager().isAlive();
        reports.setHealthy(thisEngineIsAlive);

        HealthCheck.Report engineReport = new Report();
        engineReport.setHealthy(thisEngineIsAlive);
        engineReport.setName("PDP-D");
        engineReport.setUrl("self");
        engineReport.setCode(thisEngineIsAlive ? 200 : 500);
        engineReport.setMessage(thisEngineIsAlive ? "alive" : "not alive");
        reports.getDetails().add(engineReport);

        for (HttpClient client : clients) {
            HealthCheck.Report report = new Report();
            report.setName(client.getName());
            report.setUrl(client.getBaseUrl());
            report.setHealthy(true);
            try {
                Response response = client.get();
                report.setCode(response.getStatus());
                if (report.getCode() != 200) {
                    report.setHealthy(false);
                    reports.setHealthy(false);
                }

                report.setMessage(getHttpBody(response, client));
            } catch (Exception e) {
                logger.warn("{}: cannot contact http-client {}", this, client, e);

                report.setHealthy(false);
                reports.setHealthy(false);
            }
            reports.getDetails().add(report);
        }
        return reports;
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public boolean start() {

        try {
            this.healthCheckProperties = getPersistentProperties(HealthCheckFeature.CONFIGURATION_PROPERTIES_NAME);
            this.servers = getServerFactory().build(healthCheckProperties);
            this.clients = getClientFactory().build(healthCheckProperties);

            for (HttpServletServer server : servers) {
                if (server.isAaf()) {
                    server.addFilterClass(null, AafHealthCheckFilter.class.getName());
                }
                startServer(server);
            }
        } catch (Exception e) {
            logger.warn("{}: cannot start", this, e);
            return false;
        }

        return true;
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public boolean stop() {

        for (HttpServletServer server : servers) {
            try {
                server.stop();
            } catch (Exception e) {
                logger.warn("{}: cannot stop http-server {}", this, server, e);
            }
        }

        for (HttpClient client : clients) {
            try {
                client.stop();
            } catch (Exception e) {
                logger.warn("{}: cannot stop http-client {}", this, client, e);
            }
        }

        return true;
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public void shutdown() {
        this.stop();
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public synchronized boolean isAlive() {
        return this.healthCheckProperties != null;
    }

    /**
     * Get servers.
     *
     * @return list of attached Http Servers
     */
    public List<HttpServletServer> getServers() {
        return this.servers;
    }

    /**
     * Get clients.
     *
     * @return list of attached Http Clients
     */
    public List<HttpClient> getClients() {
        return this.clients;
    }

    /**
     * Gets the body from the response.
     *
     * @param response response from which to get the body
     * @param client HTTP client from which the response was received
     * @return the response body
     */
    public String getHttpBody(Response response, HttpClient client) {

        String body = null;
        try {
            body = HttpClient.getBody(response, String.class);
        } catch (Exception e) {
            logger.info("{}: cannot get body from http-client {}", this, client, e);
        }

        return body;
    }

    /**
     * Starts an HTTP server.
     *
     * @param server server to be started
     */
    public void startServer(HttpServletServer server) {
        try {
            server.start();
        } catch (Exception e) {
            logger.warn("{}: cannot start http-server {}", this, server, e);
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("HealthCheckMonitor [servers=");
        builder.append(servers);
        builder.append(", clients=");
        builder.append(clients);
        builder.append("]");
        return builder.toString();
    }

    // the following methods may be overridden by junit tests

    protected PolicyEngine getEngineManager() {
        return PolicyEngineConstants.getManager();
    }

    protected HttpServletServerFactory getServerFactory() {
        return HttpServletServerFactoryInstance.getServerFactory();
    }

    protected HttpClientFactory getClientFactory() {
        return HttpClientFactoryInstance.getClientFactory();
    }

    protected Properties getPersistentProperties(String propertyName) {
        return SystemPersistenceConstants.getManager().getProperties(propertyName);
    }
}
