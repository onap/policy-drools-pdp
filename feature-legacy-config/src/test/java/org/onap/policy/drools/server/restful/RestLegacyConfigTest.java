/*
 * ============LICENSE_START=======================================================
 * Copyright (C) 2021 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.server.restful;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.ws.rs.core.Response;
import java.util.Properties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.onap.policy.common.endpoints.http.client.HttpClient;
import org.onap.policy.common.endpoints.http.client.HttpClientFactoryInstance;
import org.onap.policy.common.endpoints.http.server.HttpServletServer;
import org.onap.policy.common.endpoints.http.server.HttpServletServerFactoryInstance;
import org.onap.policy.common.endpoints.http.server.YamlJacksonHandler;
import org.onap.policy.common.gson.JacksonHandler;
import org.onap.policy.common.parameters.topic.BusTopicParams;
import org.onap.policy.common.utils.network.NetworkUtil;
import org.onap.policy.drools.legacy.config.LegacyConfigFeature;
import org.onap.policy.drools.persistence.SystemPersistenceConstants;
import org.onap.policy.drools.system.PolicyControllerConstants;

public class RestLegacyConfigTest {

    private static HttpClient client;

    /**
     * Set up.
     */
    @BeforeAll
    public static void setUp() throws Exception {
        SystemPersistenceConstants.getManager().setConfigurationDir("target/test-classes");

        HttpServletServerFactoryInstance.getServerFactory().destroy();
        HttpClientFactoryInstance.getClientFactory().destroy();
        PolicyControllerConstants.getFactory().destroy();

        int port = NetworkUtil.allocPort();

        HttpClientFactoryInstance.getClientFactory().build(
                BusTopicParams.builder()
                        .clientName("legacy")
                        .hostname("localhost")
                        .port(port)
                        .basePath("policy/pdp/engine/legacy/config")
                        .managed(true)
                        .build());

        HttpServletServer server =
                HttpServletServerFactoryInstance.getServerFactory().build("legacy", "localhost", port, "/",
                        true, true);
        server.setSerializationProvider(
                String.join(",", JacksonHandler.class.getName(), YamlJacksonHandler.class.getName()));
        server.addServletClass("/*", RestLegacyConfigManager.class.getName());
        server.waitedStart(5000L);

        assertTrue(NetworkUtil.isTcpPortOpen("localhost", port, 40, 250L));
        client = HttpClientFactoryInstance.getClientFactory().get("legacy");
    }

    /**
     * Tear down.
     */
    @AfterAll
    public static void tearDown() {
        LegacyConfigFeature.getLegacyConfig().shutdown();
        HttpClientFactoryInstance.getClientFactory().destroy();
        HttpServletServerFactoryInstance.getServerFactory().destroy();
        SystemPersistenceConstants.getManager().setConfigurationDir(null);
    }

    @Test
    void properties() {
        Response response = client.get("properties");
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(LegacyConfigFeature.getLegacyConfig().getProperties(),
                HttpClient.getBody(response, Properties.class));
    }

    @Test
    void topic() {
        Response response = client.get("source");
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }
}



