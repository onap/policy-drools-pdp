/*-
 * ============LICENSE_START=======================================================
 * Copyright (C) 2019-2020 AT&T Intellectual Property. All rights reserved.
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

import java.util.Collections;
import java.util.List;
import java.util.Properties;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.common.endpoints.event.comm.bus.internal.BusTopicParams;
import org.onap.policy.common.endpoints.http.client.HttpClient;
import org.onap.policy.common.endpoints.http.client.HttpClientFactoryInstance;
import org.onap.policy.common.endpoints.http.server.HttpServletServer;
import org.onap.policy.common.endpoints.http.server.HttpServletServerFactoryInstance;
import org.onap.policy.common.utils.network.NetworkUtil;
import org.onap.policy.drools.lifecycle.LifecycleFeature;
import org.onap.policy.drools.persistence.SystemPersistenceConstants;
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
        HttpServletServerFactoryInstance.getServerFactory().destroy();
        HttpClientFactoryInstance.getClientFactory().destroy();

        SystemPersistenceConstants.getManager().setConfigurationDir("target/test-classes");

        HttpClientFactoryInstance.getClientFactory().build(
            BusTopicParams.builder()
                .clientName("lifecycle")
                .hostname("localhost")
                .port(8765)
                .basePath("policy/pdp/engine/lifecycle")
                .managed(true)
                .build());

        HttpServletServer server =
            HttpServletServerFactoryInstance.getServerFactory().build("lifecycle", "localhost", 8765, "/", true, true);
        server.addServletClass("/*", RestLifecycleManager.class.getName());
        server.waitedStart(5000L);

        Assert.assertTrue(NetworkUtil.isTcpPortOpen("localhost", 8765, 5, 10000L));

    }

    /**
     * Tear down.
     */
    @After
    public void tearDown() {
        HttpServletServerFactoryInstance.getServerFactory().destroy();
        HttpClientFactoryInstance.getClientFactory().destroy();
    }

    @Test
    public void testFsm() {

        HttpClient client = HttpClientFactoryInstance.getClientFactory().get("lifecycle");
        Response response;

        /* group */

        response = client.put("group/GG", Entity.json(""), Collections.emptyMap());
        assertEquals(Status.OK.getStatusCode(), response.getStatus());
        assertEquals("GG", HttpClient.getBody(response, String.class));

        response = HttpClientFactoryInstance.getClientFactory().get("lifecycle").get("group");
        assertEquals(Status.OK.getStatusCode(), response.getStatus());
        assertEquals(LifecycleFeature.fsm.getGroup(), HttpClient.getBody(response, String.class));

        /* subgroup */

        response = client.put("subgroup/YY", Entity.json(""), Collections.emptyMap());
        assertEquals(Status.OK.getStatusCode(), response.getStatus());
        assertEquals("YY", HttpClient.getBody(response, String.class));

        response = client.get("subgroup");
        assertEquals(Status.OK.getStatusCode(), response.getStatus());
        assertEquals(LifecycleFeature.fsm.getSubgroup(), HttpClient.getBody(response, String.class));

        /* properties */

        response = client.get("properties");
        assertEquals(Status.OK.getStatusCode(), response.getStatus());
        assertEquals(LifecycleFeature.fsm.getProperties(), HttpClient.getBody(response, Properties.class));

        /* state (disallowed state change as has not been started) */

        response = client.put("state/PASSIVE", Entity.json(""), Collections.emptyMap());
        assertEquals(Status.OK.getStatusCode(), response.getStatus());
        assertEquals(Boolean.FALSE, HttpClient.getBody(response, Boolean.class));

        response = client.get("state");
        assertEquals(Status.OK.getStatusCode(), response.getStatus());
        assertEquals(PdpState.TERMINATED, HttpClient.getBody(response, PdpState.class));

        /* topics */

        assertEquals(Status.OK.getStatusCode(), client.get("topic/source").getStatus());
        assertEquals(Status.OK.getStatusCode(), client.get("topic/sink").getStatus());

        /* status interval */

        response = client.put("status/interval/1000", Entity.json(""), Collections.emptyMap());
        assertEquals(Status.OK.getStatusCode(), response.getStatus());
        assertEquals(Long.valueOf(1000L), HttpClient.getBody(response, Long.class));

        response = client.get("status/interval");
        assertEquals(Status.OK.getStatusCode(), response.getStatus());
        assertEquals(Long.valueOf(1000L), HttpClient.getBody(response, Long.class));

        /* policy types */

        response = client.get("policyTypes");
        assertEquals(Status.OK.getStatusCode(), response.getStatus());
        assertEquals(2, HttpClient.getBody(response, List.class).size());

        response = client.get("policyTypes/onap.policies.native.drools.Artifact/1.0.0");
        assertEquals(Status.OK.getStatusCode(), response.getStatus());
        assertNotNull(HttpClient.getBody(response, String.class));

        response = client.get("policyTypes/onap.policies.native.drools.Controller/1.0.0");
        assertEquals(Status.OK.getStatusCode(), response.getStatus());
        assertNotNull(HttpClient.getBody(response, String.class));

        /* policies */

        response = client.get("policies");
        assertEquals(Status.OK.getStatusCode(), response.getStatus());
        assertEquals(0, HttpClient.getBody(response, List.class).size());

        response = client.get("policies/onap.policies.controlloop.Operational");
        assertEquals(Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }
}
