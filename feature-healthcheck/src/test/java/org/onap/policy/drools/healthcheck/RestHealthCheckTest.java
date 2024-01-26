/*-
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2022 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2024 Nordix Foundation.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.onap.policy.common.endpoints.event.comm.bus.internal.BusTopicParams;
import org.onap.policy.common.endpoints.http.client.HttpClient;
import org.onap.policy.common.endpoints.http.client.HttpClientFactory;
import org.onap.policy.common.endpoints.http.client.HttpClientFactoryInstance;
import org.onap.policy.common.endpoints.http.server.HttpServletServer;
import org.onap.policy.common.endpoints.http.server.HttpServletServerFactoryInstance;
import org.onap.policy.common.endpoints.http.server.YamlJacksonHandler;
import org.onap.policy.common.gson.JacksonHandler;
import org.onap.policy.common.utils.logging.LoggerUtils;
import org.onap.policy.common.utils.network.NetworkUtil;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.drools.system.PolicyControllerFactory;

/**
 * REST Healthcheck Tests.
 */
public class RestHealthCheckTest {

    private static HttpClientFactory clientFactory;
    private static PolicyControllerFactory controllerFactory;
    private static HealthCheckManager healthcheckManager;
    private static PolicyController controller1;
    private static HttpClient client1;

    private static HealthCheck.Reports summary;
    private static HttpClient client;

    /**
     * Set up.
     */

    @BeforeAll
    public static void setUp() throws Exception {
        LoggerUtils.setLevel("org.onap.policy.common.endpoints", "WARN");
        LoggerUtils.setLevel("org.eclipse", "ERROR");
        LoggerUtils.setLevel("org.onap.policy.drools.healthcheck", "DEBUG");
        LoggerUtils.setLevel("ROOT", "INFO");

        clientFactory = mock(HttpClientFactory.class);
        controllerFactory = mock(PolicyControllerFactory.class);
        healthcheckManager = mock(HealthCheckManager.class);
        controller1 = mock(PolicyController.class);
        client1 = mock(HttpClient.class);

        summary = new HealthCheck.Reports();

        client = HttpClientFactoryInstance.getClientFactory().build(
                    BusTopicParams.builder()
                        .clientName("healthcheck")
                        .hostname("localhost")
                        .port(8768)
                        .basePath("healthcheck")
                        .managed(true)
                        .build());

        HttpServletServer server =
            HttpServletServerFactoryInstance
                .getServerFactory()
                .build("lifecycle", "localhost", 8768, "/",
                    true, true);

        server.setSerializationProvider(
                String.join(",", JacksonHandler.class.getName(),
                        YamlJacksonHandler.class.getName()));
        server.addServletClass("/*", RestMockHealthcheck.class.getName());
        server.waitedStart(5000L);

        assertTrue(NetworkUtil.isTcpPortOpen("localhost", 8768, 5, 10000L));
    }

    /**
     * Tear down.
     */

    @AfterAll
    public static void tearDown() {
        HttpClientFactoryInstance.getClientFactory().destroy();
        HttpServletServerFactoryInstance.getServerFactory().destroy();
    }

    @Test
    void healthcheck() {
        when(healthcheckManager.healthCheck()).thenReturn(summary);
        assertHttp("/");
    }

    @Test
    void engine() {
        when(healthcheckManager.engineHealthcheck()).thenReturn(summary);
        assertHttp("engine");
    }

    @Test
    void controllers() {
        when(healthcheckManager.controllerHealthcheck()).thenReturn(summary);
        assertHttp("controllers");

        when(controllerFactory.get("controller1")).thenReturn(controller1);
        when(healthcheckManager.controllerHealthcheck(controller1)).thenReturn(summary);
        assertHttp("controllers/controller1");

        when(controllerFactory.get("controller1")).thenThrow(new IllegalArgumentException("expected"));
        Response resp = client.get("controllers/controller1");
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), resp.getStatus());

        when(controllerFactory.get("controller2")).thenThrow(new IllegalStateException("expected"));
        resp = client.get("controllers/controller2");
        assertEquals(Response.Status.NOT_ACCEPTABLE.getStatusCode(), resp.getStatus());
    }

    @Test
    void clients() {
        when(healthcheckManager.clientHealthcheck()).thenReturn(summary);
        assertHttp("clients");

        when(clientFactory.get("client1")).thenReturn(client1);
        when(healthcheckManager.clientHealthcheck(client1)).thenReturn(summary);
        assertHttp("clients/client1");

        when(clientFactory.get("client2")).thenThrow(new IllegalArgumentException("expected"));
        Response resp = client.get("clients/client2");
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), resp.getStatus());
    }

    private void assertHttp(String url) {
        summary.setHealthy(true);
        var resp = client.get(url);
        assertEquals(Response.Status.OK.getStatusCode(), resp.getStatus());

        summary.setHealthy(false);
        resp = client.get(url);
        assertEquals(Response.Status.SERVICE_UNAVAILABLE.getStatusCode(), resp.getStatus());
    }

    public static class RestMockHealthcheck extends RestHealthCheck {
        @Override
        protected PolicyControllerFactory getControllerFactory() {
            return controllerFactory;
        }

        @Override
        protected HttpClientFactory getClientFactory() {
            return clientFactory;
        }

        @Override
        protected HealthCheck getHealthcheckManager() {
            return healthcheckManager;
        }

    }
}