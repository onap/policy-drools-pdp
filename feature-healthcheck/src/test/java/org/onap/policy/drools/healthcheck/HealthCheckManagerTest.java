/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2022 AT&T Intellectual Property. All rights reserved.
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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import javax.ws.rs.core.Response;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.mockito.AdditionalAnswers;
import org.onap.policy.common.endpoints.http.client.HttpClient;
import org.onap.policy.common.endpoints.http.client.HttpClientFactory;
import org.onap.policy.common.endpoints.http.server.HttpServletServer;
import org.onap.policy.common.endpoints.http.server.HttpServletServerFactory;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.healthcheck.HealthCheck.Report;
import org.onap.policy.drools.healthcheck.HealthCheck.Reports;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.drools.system.PolicyControllerFactory;
import org.onap.policy.drools.system.PolicyEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HealthCheckManagerTest {

    private static final Logger logger = LoggerFactory.getLogger(HealthCheckManagerTest.class);

    protected static List<Report> select(Reports reports, String name, long code, String message) {
        return reports.getDetails().stream()
                .filter(report -> name.equals(report.getName()))
                .filter(report -> report.getCode() == code)
                .filter(report -> message.equals(report.getMessage()))
                .collect(Collectors.toList());
    }

    private static final String RPT_MSG = "report-message";
    private static final String RPT_NAME = "report-name";
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

    private PolicyControllerFactory controllerFactory;
    private PolicyController controller1;
    private PolicyController controller2;
    private DroolsController drools1;
    private DroolsController drools2;

    private PolicyEngine engineMgr;
    private HealthCheckManager monitor;

    /**
     * Initializes the object to be tested.
     */
    @Before
    public void setUp() throws Exception {
        properties = new Properties();
        mocks();

        List<HttpServletServer> servers = Arrays.asList(server1, server2);
        List<HttpClient> clients = Arrays.asList(client1, client2, client3);

        whenClients();

        when(servletFactory.build(properties)).thenReturn(servers);
        when(clientFactory.build(properties)).thenReturn(clients);

        whenControllers();

        when(engineMgr.isAlive()).thenReturn(true);

        monitor = new HealthCheckMonitorImpl();
    }

    private void whenControllers() {
        when(drools1.getGroupId()).thenReturn("1");
        when(drools2.getGroupId()).thenReturn("2");

        when(drools1.getArtifactId()).thenReturn("1");
        when(drools2.getArtifactId()).thenReturn("2");

        when(drools1.getVersion()).thenReturn("1");
        when(drools2.getVersion()).thenReturn("2");

        when(drools1.isAlive()).thenReturn(true);
        when(drools2.isAlive()).thenReturn(true);

        when(drools1.isBrained()).thenReturn(true);
        when(drools2.isBrained()).thenReturn(true);

        when(drools1.getSessionNames()).thenReturn(List.of("session1"));
        when(drools2.getSessionNames()).thenReturn(List.of("session2"));

        doAnswer(AdditionalAnswers
                .answersWithDelay(15000L, invocationOnMock -> Map.of("TIMEOUT", 1)))
                .when(drools1).factClassNames(anyString());

        when(drools2.factClassNames(anyString()))
                .thenReturn(Map.of("java.lang.Integer", 2));

        when(drools1.factCount("session1")).thenReturn(1L);
        when(drools2.factCount("session2")).thenReturn(2L);

        when(controller1.getDrools()).thenReturn(drools1);
        when(controller2.getDrools()).thenReturn(drools2);

        when(controller1.getName()).thenReturn("drools1");
        when(controller2.getName()).thenReturn("drools2");

        when(controller1.isAlive()).thenReturn(true);
        when(controller2.isAlive()).thenReturn(true);
    }

    private void whenClients() {
        when(client1.getName()).thenReturn(CLIENT_NAME1);
        when(client1.getBaseUrl()).thenReturn(CLIENT_URL1);
        when(client2.getName()).thenReturn(CLIENT_NAME2);
        when(client2.getBaseUrl()).thenReturn(CLIENT_URL2);
        when(client3.getName()).thenReturn(CLIENT_NAME3);
        when(client3.getBaseUrl()).thenReturn(CLIENT_URL3);
    }

    private void mocks() {
        servletFactory = mock(HttpServletServerFactory.class);
        server1 = mock(HttpServletServer.class);
        server2 = mock(HttpServletServer.class);
        clientFactory = mock(HttpClientFactory.class);
        client1 = mock(HttpClient.class);
        client2 = mock(HttpClient.class);
        client3 = mock(HttpClient.class);
        controllerFactory = mock(PolicyControllerFactory.class);
        controller1 = mock(PolicyController.class);
        controller2 = mock(PolicyController.class);
        drools1 = mock(DroolsController.class);
        drools2 = mock(DroolsController.class);
        engineMgr = mock(PolicyEngine.class);
    }

    @Test
    public void testHealthcheck() {
        /* engine not alive */

        when(engineMgr.isAlive()).thenReturn(false);
        assertEngineDisabled(monitor.healthCheck());

        /* engine alive + controllers + clients */

        when(engineMgr.isAlive()).thenReturn(true);
        assertEngineEnabled(monitor.healthCheck());

        monitor.controllers = List.of(controller1, controller2);

        mockClients();

        var reports = monitor.healthCheck();
        logger.info("{}", reports);

        assertSummary(reports, 6, false);
        assertClients(reports);
        assertControllers(reports);
    }

    @Test
    public void testControllerHealthcheck() {
        /* engine not alive */

        when(engineMgr.isAlive()).thenReturn(false);
        assertEngineDisabled(monitor.controllerHealthcheck());

        /* engine alive */

        when(engineMgr.isAlive()).thenReturn(true);
        assertEngineEnabled(monitor.healthCheck());

        /* engine + controllers */

        monitor.controllers = List.of(controller1, controller2);
        var reports = monitor.healthCheck();
        logger.info("{}", reports);

        assertSummary(reports, 3, false);

        assertReport(reports, true,
                HealthCheckManager.ENGINE_NAME, "engine",
                HealthCheckManager.SUCCESS_CODE,
                HealthCheckManager.ENABLED_MESSAGE);

        assertControllers(reports);

        /* with argument */

        reports = monitor.controllerHealthcheck(controller1);
        logger.info("{}", reports);

        reports = monitor.controllerHealthcheck(controller2);
        logger.info("{}", reports);

        assertSummary(reports, 2, true);
    }

    @Test
    public void testClientHealthcheck() {
        /* engine not alive */

        when(engineMgr.isAlive()).thenReturn(false);
        assertEngineDisabled(monitor.clientHealthcheck());

        /* engine alive */

        when(engineMgr.isAlive()).thenReturn(true);
        assertEngineEnabled(monitor.clientHealthcheck());

        /* engine alive + clients */

        mockClients();

        var reports = monitor.clientHealthcheck();
        logger.info("{}", reports);

        assertSummary(reports, 4, false);
        assertClients(reports);

        /* with argument */

        reports = monitor.clientHealthcheck(client1);
        logger.info("{}", reports);

        assertSummary(reports, 2, true);
    }

    @Test
    public void reportOnController() {

        /* controller not alive */

        when(controller1.isAlive()).thenReturn(false);

        var reports = monitor.controllerHealthcheck(controller1);
        assertSummary(reports, 2, false);
        assertReport(reports, true,
                HealthCheckManager.ENGINE_NAME, "engine",
                HealthCheckManager.SUCCESS_CODE,
                HealthCheckManager.ENABLED_MESSAGE);
        assertReport(reports, false,
                    controller1.getName(), controller1.getName(),
                    HealthCheckManager.DISABLED_CODE, HealthCheckManager.DISABLED_MESSAGE);

        /* drools not brained */

        when(controller1.isAlive()).thenReturn(true);
        when(drools1.isBrained()).thenReturn(false);

        reports = monitor.controllerHealthcheck(controller1);
        logger.info("{}", reports);

        assertSummary(reports, 2, true);
        assertReport(reports, true,
                HealthCheckManager.ENGINE_NAME, "engine",
                HealthCheckManager.SUCCESS_CODE,
                HealthCheckManager.ENABLED_MESSAGE);
        assertReport(reports, true,
                controller1.getName(), "1:1:1",
                HealthCheckManager.BRAINLESS_CODE, HealthCheckManager.BRAINLESS_MESSAGE);

        /* drools not alive */

        when(drools1.isBrained()).thenReturn(true);
        when(drools1.isAlive()).thenReturn(false);

        reports = monitor.controllerHealthcheck(controller1);
        logger.info("{}", reports);

        assertSummary(reports, 2, false);
        assertReport(reports, true,
                HealthCheckManager.ENGINE_NAME, "engine",
                HealthCheckManager.SUCCESS_CODE,
                HealthCheckManager.ENABLED_MESSAGE);
        assertReport(reports, false,
                controller1.getName(), "1:1:1",
                HealthCheckManager.DISABLED_CODE, HealthCheckManager.DISABLED_MESSAGE);

        /* ok */

        when(drools1.isAlive()).thenReturn(true);

        assertController2(monitor.controllerHealthcheck(controller2));
    }

    @Test
    public void testReportOnUnknown() {
        var reports = monitor.summary(monitor.engineHealthcheck(), monitor.futures(List.of(1)));
        logger.info("{}", reports);

        assertReport(reports, false,
            HealthCheckManager.UNKNOWN_ENTITY, "java.lang.Integer",
            HealthCheckManager.UNKNOWN_ENTITY_CODE, HealthCheckManager.UNKNOWN_ENTITY_MESSAGE);
    }

    @Test
    public void testStart() {
        // good start

        when(server1.start()).thenReturn(true);
        when(server1.getName()).thenReturn(HealthCheckManager.HEALTHCHECK_SERVER);
        when(server2.getName()).thenReturn(HealthCheckManager.LIVENESS_SERVER);
        assertTrue(monitor.start());

        verify(server1).start();
        verify(server2, never()).start();

        assertEquals(server1, monitor.getHealthcheckServer());
        assertEquals(server2, monitor.getLivenessServer());

        // healthcheck server start error

        when(server1.start()).thenThrow(new RuntimeException(EXPECTED));
        assertFalse(monitor.start());

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
    public void testOpen() {

        /* nothing done */

        monitor.healthCheckProperties = new Properties();
        monitor.open();
        assertEquals(List.of(), monitor.controllers);

        /* star-controllers */

        monitor.livenessServer = server1;
        monitor.healthCheckProperties = new Properties();
        monitor.healthCheckProperties.setProperty("liveness.controllers", "*");
        when(server1.start()).thenReturn(true);

        monitor.open();
        assertEquals(controllerFactory.inventory(), monitor.controllers);
        verify(server1).start();

        /* comma-list-controllers */

        monitor.controllers = new ArrayList<>();
        monitor.healthCheckProperties.setProperty("liveness.controllers", "controller1,controller2,controller3");
        when(controllerFactory.get("controller1")).thenReturn(controller1);
        when(controllerFactory.get("controller2")).thenReturn(controller2);
        when(controllerFactory.get("controller3")).thenThrow(new RuntimeException("no controller3"));
        monitor.open();
        assertEquals(List.of(controller1, controller2), monitor.controllers);
    }

    @Test
    public void testShutdown() {
        monitor.healthcheckServer = server1;
        monitor.livenessServer = server2;
        monitor.clients = List.of(client1, client2, client3);
        when(server1.stop()).thenReturn(true);
        when(server2.stop()).thenReturn(true);

        monitor.shutdown();

        verify(server1).stop();
        verify(server2).stop();
        verify(client1).stop();
        verify(client2).stop();
        verify(client3).stop();
    }

    @Test
    public void testIsAlive() {
        assertFalse(monitor.isAlive());
    }

    @Test
    public void testToString() {
        assertTrue(monitor.toString().contains("HealthCheckManager"));
    }

    private void mockClient1() {
        // first client is healthy
        Response resp = mock(Response.class);
        when(resp.getStatus()).thenReturn(HttpURLConnection.HTTP_OK);
        when(resp.readEntity(String.class)).thenReturn(RPT_MSG);
        when(resp.getStatusInfo()).thenReturn(Response.Status.OK);
        when(client1.get()).thenReturn(resp);
    }

    private void mockClient2() {
        // second client throws an exception
        when(client2.get()).thenThrow(new RuntimeException(EXPECTED));
    }

    private void mockClient3() {
        // third client is not healthy
        Response resp = mock(Response.class);
        when(resp.getStatus()).thenReturn(HttpURLConnection.HTTP_NOT_FOUND);
        when(resp.readEntity(String.class)).thenReturn(RPT_NAME);
        when(resp.getStatusInfo()).thenReturn(Response.Status.NOT_FOUND);
        when(client3.get()).thenReturn(resp);
    }

    private void mockClients() {
        monitor.clients = List.of(client1, client2, client3);

        mockClient1();
        mockClient2();
        mockClient3();
    }

    private void assertEngineEnabled(Reports summary) {
        assertEquals(1, summary.getDetails().size());
        assertReport(summary, true, HealthCheckManager.ENGINE_NAME, "engine",
                HealthCheckManager.SUCCESS_CODE, HealthCheckManager.ENABLED_MESSAGE);
    }

    private void assertEngineDisabled(Reports summary) {
        var report = summary.getDetails().get(0);
        assertFalse(summary.isHealthy());
        assertEquals(1, summary.getDetails().size());
        assertFalse(report.isHealthy());
        assertEquals(HealthCheckManager.ENGINE_NAME, report.getName());
        assertEquals(HealthCheckManager.DISABLED_CODE, report.getCode());
        assertEquals(HealthCheckManager.DISABLED_MESSAGE, report.getMessage());
        assertNotEquals(0L, report.getStartTime());
        assertNotEquals(0L, report.getEndTime());
        assertEquals(report.getEndTime() - report.getStartTime(), report.getElapsedTime());
    }

    private void assertSummary(Reports reports, int size, boolean healthy) {
        assertNotNull(reports);
        assertEquals(size, reports.getDetails().size());
        assertEquals(healthy, reports.isHealthy());
        assertNotEquals(0L, reports.getStartTime());
        assertNotEquals(0L, reports.getEndTime());
        assertEquals(reports.getEndTime() - reports.getStartTime(), reports.getElapsedTime());
    }

    private void assertReport(Reports summary,
            boolean healthy, String name, String url, long successCode, String message) {
        var report = select(summary, name, successCode, message).get(0);

        assertEquals(healthy, report.isHealthy());
        assertEquals(name, report.getName());
        assertEquals(url, report.getUrl());
        assertEquals(successCode, report.getCode());
        assertEquals(message, report.getMessage());
        assertNotEquals(0L, report.getStartTime());
        assertNotEquals(0L, report.getEndTime());
        assertEquals(report.getEndTime() - report.getStartTime(), report.getElapsedTime());
    }

    private void assertClient1(Reports reports) {
        assertReport(reports, true,
                client1.getName(), client1.getBaseUrl(),
                HttpStatus.OK_200,
                HttpStatus.getMessage(200));
    }

    private void assertClient2(Reports reports) {
        assertReport(reports, false,
                client2.getName(), client2.getBaseUrl(),
                HealthCheckManager.UNREACHABLE_CODE,
                HealthCheckManager.UNREACHABLE_MESSAGE);
    }

    private void assertClient3(Reports reports) {
        assertReport(reports, false,
                client3.getName(), client3.getBaseUrl(),
                HttpStatus.NOT_FOUND_404,
                HttpStatus.getMessage(404));
    }

    private void assertClients(Reports reports) {
        assertReport(reports, true,
                HealthCheckManager.ENGINE_NAME, "engine",
                HealthCheckManager.SUCCESS_CODE,
                HealthCheckManager.ENABLED_MESSAGE);

        assertClient1(reports);
        assertClient2(reports);
        assertClient3(reports);
    }

    private void assertController1(Reports reports) {
        assertReport(reports, false,
                controller1.getName(), "1:1:1",
                HealthCheckManager.TIMEOUT_CODE, HealthCheckManager.TIMEOUT_MESSAGE);
    }

    private void assertController2(Reports reports) {
        assertReport(reports, true,
                controller2.getName(), "2:2:2",
                2, "[session2:{java.lang.Integer=2}]");
    }

    private void assertControllers(Reports reports) {
        assertReport(reports, true,
                HealthCheckManager.ENGINE_NAME, "engine",
                HealthCheckManager.SUCCESS_CODE,
                HealthCheckManager.ENABLED_MESSAGE);

        assertController1(reports);
        assertController2(reports);
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
        protected Properties getPersistentProperties() {
            return properties;
        }

        @Override
        protected PolicyControllerFactory getControllerFactory() {
            return controllerFactory;
        }

    }
}
