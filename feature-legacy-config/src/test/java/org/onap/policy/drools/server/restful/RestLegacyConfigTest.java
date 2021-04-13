/*
 * ============LICENSE_START=======================================================
 * Copyright (C) 2021 AT&T Intellectual Property. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Properties;
import javax.ws.rs.core.Response;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.onap.policy.common.endpoints.event.comm.bus.internal.BusTopicParams;
import org.onap.policy.common.endpoints.http.client.HttpClient;
import org.onap.policy.common.endpoints.http.client.HttpClientFactoryInstance;
import org.onap.policy.common.endpoints.http.server.HttpServletServer;
import org.onap.policy.common.endpoints.http.server.HttpServletServerFactoryInstance;
import org.onap.policy.common.endpoints.http.server.YamlJacksonHandler;
import org.onap.policy.common.gson.JacksonHandler;
import org.onap.policy.common.utils.network.NetworkUtil;
import org.onap.policy.drools.legacy.config.LegacyConfigFeature;
import org.onap.policy.drools.persistence.SystemPersistenceConstants;
import org.onap.policy.drools.system.PolicyControllerConstants;
import org.onap.policy.drools.utils.logging.LoggerUtil;

/**
 * REST Legacy Config Test.
 */
public class RestLegacyConfigTest {

    private static HttpClient client;

    /**
     * Set up.
     */
    @BeforeClass
    public static void setUp() throws Exception {
        SystemPersistenceConstants.getManager().setConfigurationDir("target/test-classes");

        LoggerUtil.setLevel(LoggerUtil.ROOT_LOGGER, "INFO");
        LoggerUtil.setLevel("org.onap.policy.common.endpoints", "WARN");
        LoggerUtil.setLevel("org.onap.policy.drools", "INFO");

        HttpServletServerFactoryInstance.getServerFactory().destroy();
        HttpClientFactoryInstance.getClientFactory().destroy();
        PolicyControllerConstants.getFactory().destroy();

        HttpClientFactoryInstance.getClientFactory().build(
                BusTopicParams.builder()
                        .clientName("legacy")
                        .hostname("localhost")
                        .port(8765)
                        .basePath("policy/pdp/engine/legacy/config")
                        .managed(true)
                        .build());

        HttpServletServer server =
                HttpServletServerFactoryInstance.getServerFactory().build("legacy", "localhost", 8765, "/",
                        true, true);
        server.setSerializationProvider(
                String.join(",", JacksonHandler.class.getName(), YamlJacksonHandler.class.getName()));
        server.addServletClass("/*", RestLegacyConfigManager.class.getName());
        server.waitedStart(5000L);

        assertTrue(NetworkUtil.isTcpPortOpen("localhost", 8765, 5, 10000L));
        client = HttpClientFactoryInstance.getClientFactory().get("legacy");
    }

    /**
     * Tear down.
     */
    @AfterClass
    public static void tearDown() {
        LegacyConfigFeature.getLegacyConfig().shutdown();
        HttpClientFactoryInstance.getClientFactory().destroy();
        HttpServletServerFactoryInstance.getServerFactory().destroy();
        SystemPersistenceConstants.getManager().setConfigurationDir(null);
    }

    @Test
    public void properties() {
        Response response = client.get("properties");
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(LegacyConfigFeature.getLegacyConfig().getProperties(),
                HttpClient.getBody(response, Properties.class));
    }

    @Test
    public void topic() {
        Response response = client.get("source");
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }
}



