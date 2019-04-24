/*-
 * ============LICENSE_START=======================================================
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

package org.onap.policy.drools.server.restful;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.common.endpoints.event.comm.bus.internal.BusTopicParams;
import org.onap.policy.common.endpoints.http.client.HttpClient;
import org.onap.policy.common.endpoints.http.server.HttpServletServer;
import org.onap.policy.common.utils.network.NetworkUtil;
import org.onap.policy.drools.persistence.SystemPersistence;
import org.onap.policy.models.pdp.enums.PdpState;

/**
 * REST Lifecycle Manager Test.
 */
public class RestLifecycleManagerTest {

    /**
     * Set up.
     */
    @Before
     public void setUp() throws Exception {
        HttpServletServer.factory.destroy();
        HttpClient.factory.destroy();

        SystemPersistence.manager.setConfigurationDir("target/test-classes");

        HttpClient.factory.build(
            BusTopicParams.builder()
                .clientName("lifecycle")
                .hostname("localhost")
                .port(8765)
                .basePath("policy/pdp/engine/lifecycle")
                .managed(true)
                .build());

        HttpServletServer server =
            HttpServletServer.factory.build("lifecycle", "localhost", 8765, "/", true, true);
        server.addServletClass("/*", RestLifecycleManager.class.getName());
        server.setSerializationProvider("org.onap.policy.common.gson.JacksonHandler");
        server.waitedStart(5000L);

        Assert.assertTrue(NetworkUtil.isTcpPortOpen("localhost", 8765, 5, 10000L));

    }

    /**
     * Tear down.
     */
    @After
    public void tearDown() {
        HttpServletServer.factory.destroy();
        HttpClient.factory.destroy();
    }

    @Test
    public void fsm() {
        Response response = HttpClient.factory.get("lifecycle").get("fsm");
        assertNotNull(HttpClient.getBody(response, String.class));
        assertEquals(Status.OK.getStatusCode(), response.getStatus());

        response = HttpClient.factory.get("lifecycle").get("fsm/state");
        assertEquals(PdpState.TERMINATED, HttpClient.getBody(response, PdpState.class));
        assertEquals(Status.OK.getStatusCode(), response.getStatus());
    }
}