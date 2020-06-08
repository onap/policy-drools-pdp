/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018 AT&T Intellectual Property. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.common.endpoints.http.client.HttpClient;
import org.onap.policy.common.endpoints.http.client.HttpClientFactory;
import org.onap.policy.common.endpoints.http.server.HttpServletServer;
import org.onap.policy.common.endpoints.http.server.HttpServletServerFactory;
import org.onap.policy.drools.healthcheck.HealthCheck.Report;
import org.onap.policy.drools.healthcheck.HealthCheck.Reports;
import org.onap.policy.drools.system.PolicyEngine;

public class HealthCheckTest {

    private static final int RPT_CODE = 100;
    private static final String RPT_MSG = "report-message";
    private static final String RPT_NAME = "report-name";
    private static final String RPT_URL = "report-url";
    private static final String EXPECTED = "expected exception";

    private static final String CLIENT_NAME1 = "name-a";
    private static final String CLIENT_URL1 = "url-a";
    private static final String CLIENT_NAME2 = "name-b";
    private static final String CLIENT_URL2 = "url-b";
    private static final String CLIENT_NAME3 = "name-c";
    private static final String CLIENT_URL3 = "url-c";

    private Properties properties;
    private HttpServletServerFactory servletFactory;
    private HttpServletServer server1;
    private HttpServletServer server2;
    private HttpClientFactory clientFactory;
    private HttpClient client1;
    private HttpClient client2;
    private HttpClient client3;
    private List<HttpServletServer> servers;
    private List<HttpClient> clients;
    private PolicyEngine engineMgr;
    private HealthCheckManager monitor;

    /**
     * Initializes the object to be tested.
     *
     * @throws Exception if an error occurs
     */
    @Before
    public void setUp() throws Exception {
        properties = new Properties();
        servletFactory = mock(HttpServletServerFactory.class);
        server1 = mock(HttpServletServer.class);
        server2 = mock(HttpServletServer.class);
        clientFactory = mock(HttpClientFactory.class);
        client1 = mock(HttpClient.class);
        client2 = mock(HttpClient.class);
        client3 = mock(HttpClient.class);
        servers = Arrays.asList(server1, server2);
        clients = Arrays.asList(client1, client2, client3);
        engineMgr = mock(PolicyEngine.class);

        when(client1.getName()).thenReturn(CLIENT_NAME1);
        when(client1.getBaseUrl()).thenReturn(CLIENT_URL1);
        when(client2.getName()).thenReturn(CLIENT_NAME2);
        when(client2.getBaseUrl()).thenReturn(CLIENT_URL2);
        when(client3.getName()).thenReturn(CLIENT_NAME3);
        when(client3.getBaseUrl()).thenReturn(CLIENT_URL3);
        when(servletFactory.build(properties)).thenReturn(servers);
        when(clientFactory.build(properties)).thenReturn(clients);
        when(engineMgr.isAlive()).thenReturn(true);

        monitor = new HealthCheckMonitorImpl();
    }

    @Test
    public void testReport() {
        Report rpt = new Report();

        // toString should work with un-populated data
        assertNotNull(rpt.toString());

        rpt.setCode(RPT_CODE);
        rpt.setHealthy(true);
        rpt.setMessage(RPT_MSG);
        rpt.setName(RPT_NAME);
        rpt.setUrl(RPT_URL);

        assertEquals(RPT_CODE, rpt.getCode());
        assertEquals(true, rpt.isHealthy());
        assertEquals(RPT_MSG, rpt.getMessage());
        assertEquals(RPT_NAME, rpt.getName());
        assertEquals(RPT_URL, rpt.getUrl());

        // flip the flag
        rpt.setHealthy(false);
        assertEquals(false, rpt.isHealthy());

        // toString should work with populated data
        assertNotNull(rpt.toString());
    }

    @Test
    public void testReports() {
        Reports reports = new Reports();

        // toString should work with un-populated data
        assertNotNull(reports.toString());

        List<Report> lst = Collections.emptyList();
        reports.setDetails(lst);
        reports.setHealthy(true);

        assertSame(lst, reports.getDetails());
        assertEquals(true, reports.isHealthy());

        // flip the flag
        reports.setHealthy(false);
        assertEquals(false, reports.isHealthy());

        // toString should work with populated data
        assertNotNull(reports.toString());
    }

    @Test
    public void testHealthCheckMonitor_HealthCheck() {
        monitor.start();

        // first client is healthy
        Response resp = mock(Response.class);
        when(resp.getStatus()).thenReturn(HttpURLConnection.HTTP_OK);
        when(resp.readEntity(String.class)).thenReturn(RPT_MSG);
        when(client1.get()).thenReturn(resp);

        // second client throws an exception
        when(client2.get()).thenThrow(new RuntimeException(EXPECTED));

        // third client is not healthy
        resp = mock(Response.class);
        when(resp.getStatus()).thenReturn(HttpURLConnection.HTTP_INTERNAL_ERROR);
        when(resp.readEntity(String.class)).thenReturn(RPT_NAME);
        when(client3.get()).thenReturn(resp);

        Reports reports = monitor.healthCheck();
        assertNotNull(reports);
        assertEquals(4, reports.getDetails().size());
        assertFalse(reports.isHealthy());

        int index = 0;

        Report report = reports.getDetails().get(index++);
        assertEquals(true, report.isHealthy());
        assertEquals("PDP-D", report.getName());
        assertEquals("self", report.getUrl());
        assertEquals("alive", report.getMessage());
        assertEquals(HttpURLConnection.HTTP_OK, report.getCode());

        report = reports.getDetails().get(index++);
        assertEquals(true, report.isHealthy());
        assertEquals(client1.getName(), report.getName());
        assertEquals(client1.getBaseUrl(), report.getUrl());
        assertEquals(RPT_MSG, report.getMessage());
        assertEquals(HttpURLConnection.HTTP_OK, report.getCode());

        report = reports.getDetails().get(index++);
        assertEquals(false, report.isHealthy());
        assertEquals(client2.getName(), report.getName());
        assertEquals(client2.getBaseUrl(), report.getUrl());

        report = reports.getDetails().get(index++);
        assertEquals(false, report.isHealthy());
        assertEquals(client3.getName(), report.getName());
        assertEquals(client3.getBaseUrl(), report.getUrl());
        assertEquals(RPT_NAME, report.getMessage());
        assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR, report.getCode());

        // indicate that engine is no longer healthy and re-run health check
        when(engineMgr.isAlive()).thenReturn(false);

        reports = monitor.healthCheck();
        report = reports.getDetails().get(0);

        assertEquals(false, report.isHealthy());
        assertEquals("not alive", report.getMessage());
        assertEquals(500, report.getCode());
    }

    @Test
    public void testHealthCheckMonitor_Start() {
        // arrange for one server to throw an exception
        when(server1.start()).thenThrow(new RuntimeException(EXPECTED));

        assertTrue(monitor.start());

        verify(server1).start();
        verify(server2).start();

        /*
         * Generate exception during building.
         */

        // new monitor
        monitor = new HealthCheckMonitorImpl() {
            @Override
            protected HttpServletServerFactory getServerFactory() {
                throw new RuntimeException(EXPECTED);
            }
        };

        assertFalse(monitor.start());
    }

    @Test
    public void testHealthCheckMonitor_Stop() {
        monitor.start();

        // arrange for one server and one client to throw an exception
        when(server1.stop()).thenThrow(new RuntimeException(EXPECTED));
        when(client2.stop()).thenThrow(new RuntimeException(EXPECTED));

        assertTrue(monitor.stop());

        verify(server1).stop();
        verify(server2).stop();
        verify(client1).stop();
        verify(client2).stop();
        verify(client3).stop();
    }

    @Test
    public void testHealthCheckMonitor_Shutdown() {
        monitor.start();
        monitor.shutdown();

        // at least one "stop" should have been called
        verify(server1).stop();
    }

    @Test
    public void testHealthCheckMonitor_IsAlive() {
        assertFalse(monitor.isAlive());

        monitor.start();
        assertTrue(monitor.isAlive());
    }

    @Test
    public void testHealthCheckMonitor_GetServers_GetClients() {
        monitor.start();
        assertEquals(servers, monitor.getServers());
        assertEquals(clients, monitor.getClients());
    }

    @Test
    public void testHealthCheckMonitor_GetHttpBody() {
        Response response = mock(Response.class);
        when(response.readEntity(String.class)).thenReturn(RPT_MSG);
        assertEquals(RPT_MSG, monitor.getHttpBody(response, client1));

        // readEntity() throws an exception
        when(response.readEntity(String.class)).thenThrow(new RuntimeException(EXPECTED));
        assertEquals(null, monitor.getHttpBody(response, client1));
    }

    @Test
    public void testHealthCheckMonitor_StartServer() {
        monitor.startServer(server1);
        verify(server1).start();

        // force start() to throw an exception - monitor should still work
        when(server1.start()).thenThrow(new RuntimeException(EXPECTED));
        monitor.startServer(server1);
    }

    @Test
    public void testHealthCheckMonitor_ToString() {
        assertTrue(monitor.toString().startsWith("HealthCheckMonitor ["));
    }

    @Test
    public void testHealthCheckMonitor_GetEngineManager() {
        assertNotNull(new HealthCheckManager().getEngineManager());
    }

    @Test
    public void testHealthCheckMonitor_GetServerFactory() {
        assertNotNull(new HealthCheckManager().getServerFactory());
    }

    @Test
    public void testHealthCheckMonitor_GetClientFactory() {
        assertNotNull(new HealthCheckManager().getClientFactory());
    }

    /**
     * Monitor with overrides.
     */
    private class HealthCheckMonitorImpl extends HealthCheckManager {

        @Override
        protected PolicyEngine getEngineManager() {
            return engineMgr;
        }

        @Override
        protected HttpServletServerFactory getServerFactory() {
            return servletFactory;
        }

        @Override
        protected HttpClientFactory getClientFactory() {
            return clientFactory;
        }

        @Override
        protected Properties getPersistentProperties(String propertyName) {
            return properties;
        }

    }
}
