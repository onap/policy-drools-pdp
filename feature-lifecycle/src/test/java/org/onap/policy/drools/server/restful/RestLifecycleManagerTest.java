/*-
 * ============LICENSE_START=======================================================
 * Copyright (C) 2019-2022 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2022, 2024 Nordix Foundation.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.common.endpoints.event.comm.TopicEndpointManager;
import org.onap.policy.common.endpoints.event.comm.bus.NoopTopicFactories;
import org.onap.policy.common.endpoints.event.comm.bus.internal.BusTopicParams;
import org.onap.policy.common.endpoints.http.client.HttpClient;
import org.onap.policy.common.endpoints.http.client.HttpClientFactoryInstance;
import org.onap.policy.common.endpoints.http.server.HttpServletServer;
import org.onap.policy.common.endpoints.http.server.HttpServletServerFactoryInstance;
import org.onap.policy.common.endpoints.http.server.YamlJacksonHandler;
import org.onap.policy.common.endpoints.properties.PolicyEndPointProperties;
import org.onap.policy.common.gson.JacksonHandler;
import org.onap.policy.common.utils.coder.CoderException;
import org.onap.policy.common.utils.coder.StandardCoder;
import org.onap.policy.common.utils.logging.LoggerUtils;
import org.onap.policy.common.utils.network.NetworkUtil;
import org.onap.policy.common.utils.resources.ResourceUtils;
import org.onap.policy.drools.lifecycle.ControllerSupport;
import org.onap.policy.drools.lifecycle.LifecycleFeature;
import org.onap.policy.drools.lifecycle.LifecycleFsm;
import org.onap.policy.drools.persistence.SystemPersistenceConstants;
import org.onap.policy.drools.system.PolicyControllerConstants;
import org.onap.policy.models.pdp.enums.PdpState;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicy;
import org.onap.policy.models.tosca.authorative.concepts.ToscaServiceTemplate;

/**
 * REST Lifecycle Manager Test.
 */
public class RestLifecycleManagerTest {
    // Native Drools Policy
    private static final String EXAMPLE_NATIVE_CONTROLLER_POLICY_NAME = "example.controller";
    private static final String EXAMPLE_NATIVE_CONTROLLER_POLICY_JSON =
        "src/test/resources/tosca-policy-native-controller-example.json";

    private static final String EXAMPLE_NATIVE_ARTIFACT_POLICY_NAME = "example.artifact";
    private static final String EXAMPLE_NATIVE_ARTIFACT_POLICY_JSON =
        "src/test/resources/tosca-policy-native-artifact-example.json";

    private static final String EXAMPLE_OTHER_UNVAL_POLICY_NAME = "other-unvalidated";
    private static final String EXAMPLE_OTHER_UNVAL_POLICY_JSON =
        "src/test/resources/tosca-policy-other-unvalidated.json";

    private static final String EXAMPLE_OTHER_VAL_POLICY_NAME = "other-validated";
    private static final String EXAMPLE_OTHER_VAL_POLICY_JSON =
        "src/test/resources/tosca-policy-other-validated.json";

    private static final String EXAMPLE_OTHER_VAL_ERROR_POLICY_NAME = "other-validation-error";
    private static final String EXAMPLE_OTHER_VAL_ERROR_POLICY_JSON =
        "src/test/resources/tosca-policy-other-validation-error.json";

    private static final String OP_POLICY_NAME_VCPE = "operational.restart";
    private static final String VCPE_OPERATIONAL_DROOLS_POLICY_JSON =
        "policies/vCPE.policy.operational.input.tosca.json";

    public static final String PROM_DEPLOY_REQUESTS_TOTAL_UNDEPLOY_ACCEPTED =
        "pdpd_policy_deployments_total{state=\"ACTIVE\",operation=\"undeploy\",status=\"SUCCESS\",}";
    public static final String PDPD_DEPLOY_REQUESTS_TOTAL_DEPLOY_ACCEPTED =
        "pdpd_policy_deployments_total{state=\"ACTIVE\",operation=\"deploy\",status=\"SUCCESS\",}";
    public static final String PDPD_DEPLOY_REQUESTS_TOTAL_DEPLOY_DECLINED =
        "pdpd_policy_deployments_total{state=\"ACTIVE\",operation=\"deploy\",status=\"FAIL\",}";

    private static final StandardCoder coder = new StandardCoder();
    private static final ControllerSupport controllerSupport = new ControllerSupport("lifecycle");

    private LifecycleFsm fsm;
    private HttpClient client;

    /**
     * Set up.
     */
    @Before
    public void setUp() throws Exception {

        SystemPersistenceConstants.getManager().setConfigurationDir("target/test-classes");
        fsm = newFsmInstance();

        LoggerUtils.setLevel(LoggerUtils.ROOT_LOGGER, "INFO");
        LoggerUtils.setLevel("org.onap.policy.common.endpoints", "WARN");
        LoggerUtils.setLevel("org.onap.policy.drools", "INFO");

        HttpServletServerFactoryInstance.getServerFactory().destroy();
        HttpClientFactoryInstance.getClientFactory().destroy();
        PolicyControllerConstants.getFactory().destroy();

        HttpClientFactoryInstance.getClientFactory().build(
            BusTopicParams.builder()
                .clientName("lifecycle")
                .hostname("localhost")
                .port(8765)
                .basePath("policy/pdp/engine/lifecycle")
                .managed(true)
                .build());

        HttpServletServer server =
            HttpServletServerFactoryInstance.getServerFactory().build("lifecycle", "localhost", 8765, "/",
                true, true);
        server.setPrometheus("/policy/pdp/engine/lifecycle/metrics");
        server.setSerializationProvider(
            String.join(",", JacksonHandler.class.getName(), YamlJacksonHandler.class.getName()));
        server.addServletClass("/*", RestLifecycleManager.class.getName());
        server.waitedStart(5000L);

        assertTrue(NetworkUtil.isTcpPortOpen("localhost", 8765, 5, 10000L));

        controllerSupport.installArtifact();

        Properties noopTopicProperties = new Properties();
        noopTopicProperties.put(PolicyEndPointProperties.PROPERTY_NOOP_SOURCE_TOPICS,
            String.join(",", TestConstants.DCAE_TOPIC, TestConstants.APPC_CL_TOPIC,
                TestConstants.APPC_LCM_WRITE_TOPIC, TestConstants.SDNR_CL_RSP_TOPIC));
        noopTopicProperties.put(PolicyEndPointProperties.PROPERTY_NOOP_SINK_TOPICS,
            String.join(",", TestConstants.APPC_CL_TOPIC, TestConstants.APPC_LCM_READ_TOPIC,
                TestConstants.POLICY_CL_MGT_TOPIC, TestConstants.SDNR_CL_TOPIC, TestConstants.DCAE_CL_RSP_TOPIC));
        TopicEndpointManager.getManager().addTopics(noopTopicProperties);

        client = HttpClientFactoryInstance.getClientFactory().get("lifecycle");
    }

    /**
     * Tear down.
     */
    @After
    public void tearDown() {
        fsm.shutdown();

        NoopTopicFactories.getSourceFactory().destroy();
        NoopTopicFactories.getSinkFactory().destroy();

        HttpClientFactoryInstance.getClientFactory().destroy();
        HttpServletServerFactoryInstance.getServerFactory().destroy();

        PolicyControllerConstants.getFactory().destroy();
        SystemPersistenceConstants.getManager().setConfigurationDir(null);

    }

    @Test
    public void testMultiPolicyFlow() throws IOException, CoderException {
        /* group assignments */

        group();
        subgroup();

        /* other resources */

        properties();
        topics();

        /* status interval */

        status();

        /* start up configuration */

        resourceLists("policyTypes", 2);
        get("policyTypes/onap.policies.native.drools.Artifact/1.0.0", Status.OK.getStatusCode());
        get("policyTypes/onap.policies.native.drools.Controller/1.0.0", Status.OK.getStatusCode());
        get("policyTypes/onap.policies.controlloop.operational.common.Drools/1.0.0", Status.NOT_FOUND.getStatusCode());

        resourceLists("policies", 0);
        get("policies/example.controller/1.0.0", Status.NOT_FOUND.getStatusCode());
        get("policies/example.artifact/1.0.0", Status.NOT_FOUND.getStatusCode());

        /* start lifecycle */

        assertTrue(fsm.start());

        booleanPut("state/ACTIVE", "", Status.OK.getStatusCode(), Boolean.TRUE);
        assertEquals(PdpState.ACTIVE,
            HttpClient.getBody(get("state", Status.OK.getStatusCode()), PdpState.class));

        /* add native controller policy */

        ToscaPolicy nativeControllerPolicy =
            getPolicyFromFile(EXAMPLE_NATIVE_CONTROLLER_POLICY_JSON, EXAMPLE_NATIVE_CONTROLLER_POLICY_NAME);
        booleanPost("policies", toString(nativeControllerPolicy), Status.OK.getStatusCode(), Boolean.TRUE);

        assertTrue(PolicyControllerConstants.getFactory().get("lifecycle").isAlive());
        assertFalse(PolicyControllerConstants.getFactory().get("lifecycle").getDrools().isBrained());
        assertFalse(PolicyControllerConstants.getFactory().get("lifecycle").getDrools().isAlive());

        get("policyTypes/onap.policies.controlloop.operational.common.Drools/1.0.0", Status.NOT_FOUND.getStatusCode());

        resourceLists("policies", 1);
        get("policies/example.controller/1.0.0", Status.OK.getStatusCode());

        /* add native artifact policy */

        ToscaPolicy nativeArtifactPolicy =
            getPolicyFromFile(EXAMPLE_NATIVE_ARTIFACT_POLICY_JSON, EXAMPLE_NATIVE_ARTIFACT_POLICY_NAME);
        booleanPost("policies", toString(nativeArtifactPolicy), Status.OK.getStatusCode(), Boolean.TRUE);

        assertTrue(PolicyControllerConstants.getFactory().get("lifecycle").isAlive());
        assertTrue(PolicyControllerConstants.getFactory().get("lifecycle").getDrools().isBrained());
        assertTrue(PolicyControllerConstants.getFactory().get("lifecycle").getDrools().isAlive());

        /* verify new supported operational policy types */

        resourceLists("policyTypes", 5);
        get("policyTypes/onap.policies.native.drools.Artifact/1.0.0", Status.OK.getStatusCode());
        get("policyTypes/onap.policies.native.drools.Controller/1.0.0", Status.OK.getStatusCode());
        get("policyTypes/onap.policies.controlloop.operational.common.Drools/1.0.0", Status.OK.getStatusCode());
        get("policyTypes/onap.policies.type1.type2/1.0.0", Status.OK.getStatusCode());
        get("policyTypes/onap.policies.typeA/1.0.0", Status.OK.getStatusCode());

        /* verify controller and artifact policies */

        resourceLists("policies", 2);
        get("policies/example.controller/1.0.0", Status.OK.getStatusCode());
        get("policies/example.artifact/1.0.0", Status.OK.getStatusCode());

        /* add tosca compliant operational policy */

        ToscaPolicy opPolicy = getExamplesPolicy();
        opPolicy.getProperties().put("controllerName", "lifecycle");
        if (StringUtils.isBlank(opPolicy.getName())) {
            opPolicy.setName("" + opPolicy.getMetadata().get("policy-id"));
        }
        testNotNativePolicy(opPolicy);

        /* add tosca policy "other-unvalidated" of policy type "type1.type2" with no attached type schema */

        testNotNativePolicy(getPolicyFromFile(EXAMPLE_OTHER_UNVAL_POLICY_JSON, EXAMPLE_OTHER_UNVAL_POLICY_NAME));

        /* add tosca policy "other-validated" of policy type "typeA" with an attached type schema */

        testNotNativePolicy(getPolicyFromFile(EXAMPLE_OTHER_VAL_POLICY_JSON, EXAMPLE_OTHER_VAL_POLICY_NAME));

        /* try to add invalid tosca policy "other-validation-error" of policy type "typeA" */

        ToscaPolicy toscaPolicyValError =
            getPolicyFromFile(EXAMPLE_OTHER_VAL_ERROR_POLICY_JSON, EXAMPLE_OTHER_VAL_ERROR_POLICY_NAME);
        assertThat(
            listPost("policies/operations/validation", toString(toscaPolicyValError),
                Status.NOT_ACCEPTABLE.getStatusCode())).isNotEmpty();

        booleanPost("policies", toString(toscaPolicyValError),
            Status.NOT_ACCEPTABLE.getStatusCode(), Boolean.FALSE);

        /* individual deploy/undeploy operations */

        resourceLists("policies/operations", 3);

        booleanPost("policies/operations/deployment", toString(opPolicy), Status.OK.getStatusCode(), Boolean.TRUE);
        assertEquals(1,
            PolicyControllerConstants
                .getFactory().get("lifecycle").getDrools().facts("junits", ToscaPolicy.class).size());

        resourceLists("policies", 2);
        get("policies/" + opPolicy.getName() + "/" + opPolicy.getVersion(), Status.NOT_FOUND.getStatusCode());
        get("policies/example.controller/1.0.0", Status.OK.getStatusCode());
        get("policies/example.artifact/1.0.0", Status.OK.getStatusCode());

        booleanPost(
            "policies/operations/undeployment", toString(opPolicy), Status.OK.getStatusCode(), Boolean.TRUE);
        assertEquals(0,
            PolicyControllerConstants
                .getFactory().get("lifecycle").getDrools().facts("junits", ToscaPolicy.class).size());

        resourceLists("policies", 2);
        get("policies/" + opPolicy.getName() + "/" + opPolicy.getVersion(), Status.NOT_FOUND.getStatusCode());
        get("policies/example.controller/1.0.0", Status.OK.getStatusCode());
        get("policies/example.artifact/1.0.0", Status.OK.getStatusCode());

        /* delete native artifact policy */

        booleanDelete("policies/example.artifact/1.0.0", Status.OK.getStatusCode(), Boolean.TRUE);
        assertTrue(PolicyControllerConstants.getFactory().get("lifecycle").isAlive());
        assertFalse(PolicyControllerConstants.getFactory().get("lifecycle").getDrools().isBrained());

        resourceLists("policyTypes", 2);
        get("policyTypes/onap.policies.native.drools.Artifact/1.0.0", Status.OK.getStatusCode());
        get("policyTypes/onap.policies.native.drools.Controller/1.0.0", Status.OK.getStatusCode());
        get("policyTypes/onap.policies.controlloop.operational.common.Drools/1.0.0", Status.NOT_FOUND.getStatusCode());
        get("policyTypes/onap.policies.type1.type2/1.0.0", Status.NOT_FOUND.getStatusCode());
        get("policyTypes/onap.policies.typeA/1.0.0", Status.NOT_FOUND.getStatusCode());

        resourceLists("policies", 1);
        get("policies/" + opPolicy.getName() + "/" + opPolicy.getVersion(), Status.NOT_FOUND.getStatusCode());
        get("policies/example.artifact/1.0.0", Status.NOT_FOUND.getStatusCode());
        get("policies/example.controller/1.0.0", Status.OK.getStatusCode());

        /* delete native controller policy */

        booleanDelete("policies/example.controller/1.0.0", Status.OK.getStatusCode(), Boolean.TRUE);

        resourceLists("policyTypes", 2);
        get("policyTypes/onap.policies.native.drools.Artifact/1.0.0", Status.OK.getStatusCode());
        get("policyTypes/onap.policies.native.drools.Controller/1.0.0", Status.OK.getStatusCode());
        get("policyTypes/onap.policies.controlloop.operational.common.Drools/1.0.0", Status.NOT_FOUND.getStatusCode());
        get("policyTypes/onap.policies.type1.type2/1.0.0", Status.NOT_FOUND.getStatusCode());
        get("policyTypes/onap.policies.typeA/1.0.0", Status.NOT_FOUND.getStatusCode());

        resourceLists("policies", 0);
        get("policies/" + opPolicy.getName() + "/" + opPolicy.getVersion(), Status.NOT_FOUND.getStatusCode());
        get("policies/example.artifact/1.0.0", Status.NOT_FOUND.getStatusCode());
        get("policies/example.controller/1.0.0", Status.NOT_FOUND.getStatusCode());

        assertThatIllegalArgumentException().isThrownBy(() -> PolicyControllerConstants.getFactory().get("lifecycle"));
        opPolicy.getMetadata().remove("policy-id");
        assertThat(
            listPost("policies/operations/validation", toString(opPolicy),
                Status.NOT_ACCEPTABLE.getStatusCode())).isNotEmpty();

        metrics();
    }

    private void testNotNativePolicy(ToscaPolicy toscaPolicy) throws CoderException {
        assertThat(
            listPost("policies/operations/validation", toString(toscaPolicy),
                Status.OK.getStatusCode())).isEmpty();

        booleanPost("policies", toString(toscaPolicy), Status.OK.getStatusCode(), Boolean.TRUE);
        assertTrue(PolicyControllerConstants.getFactory().get("lifecycle").isAlive());
        assertTrue(PolicyControllerConstants.getFactory().get("lifecycle").getDrools().isBrained());
        assertEquals(1,
            PolicyControllerConstants
                .getFactory().get("lifecycle").getDrools().facts("junits", ToscaPolicy.class).size());

        resourceLists("policies", 3);
        get("policies/" + toscaPolicy.getName() + "/" + toscaPolicy.getVersion(), Status.OK.getStatusCode());
        get("policies/example.controller/1.0.0", Status.OK.getStatusCode());
        get("policies/example.artifact/1.0.0", Status.OK.getStatusCode());

        booleanDelete("policies/" + toscaPolicy.getName() + "/" + toscaPolicy.getVersion(),
            Status.OK.getStatusCode(), Boolean.TRUE);
        assertEquals(0,
            PolicyControllerConstants
                .getFactory().get("lifecycle").getDrools().facts("junits", ToscaPolicy.class).size());

        resourceLists("policies", 2);
        get("policies/" + toscaPolicy.getName() + "/" + toscaPolicy.getVersion(), Status.NOT_FOUND.getStatusCode());
        get("policies/example.controller/1.0.0", Status.OK.getStatusCode());
        get("policies/example.artifact/1.0.0", Status.OK.getStatusCode());
    }

    private Response get(String contextPath, int statusCode) {
        Response response = client.get(contextPath);
        assertEquals(statusCode, response.getStatus());
        return response;
    }

    private void booleanResponse(Response response, int statusCode, Boolean bool) {
        assertEquals(statusCode, response.getStatus());
        assertEquals(bool, HttpClient.getBody(response, Boolean.class));
    }

    private void booleanPut(String contextPath, String body, int statusCode, Boolean bool) {
        Response response = client.put(contextPath, Entity.json(body), Collections.emptyMap());
        booleanResponse(response, statusCode, bool);
    }

    private List<?> listPost(String contextPath, String body, int statusCode) {
        Response response = client.post(contextPath, Entity.json(body), Collections.emptyMap());
        assertEquals(statusCode, response.getStatus());
        return HttpClient.getBody(response, List.class);
    }

    private void booleanPost(String contextPath, String body, int statusCode, Boolean bool) {
        Response response = client.post(contextPath, Entity.json(body), Collections.emptyMap());
        booleanResponse(response, statusCode, bool);
    }

    private void booleanDelete(String contextPath, int statusCode, Boolean bool) {
        Response response = client.delete(contextPath, Collections.emptyMap());
        booleanResponse(response, statusCode, bool);
    }

    private void resourceLists(String resource, int size) {
        Response response = client.get(resource);
        assertEquals(Status.OK.getStatusCode(), response.getStatus());
        assertEquals(size, HttpClient.getBody(response, List.class).size());
    }

    private void status() {
        Response response = client.put("status/interval/240", Entity.json(""), Collections.emptyMap());
        assertEquals(Status.OK.getStatusCode(), response.getStatus());
        assertEquals(Long.valueOf(240L), HttpClient.getBody(response, Long.class));

        response = client.get("status/interval");
        assertEquals(Status.OK.getStatusCode(), response.getStatus());
        assertEquals(Long.valueOf(240L), HttpClient.getBody(response, Long.class));
    }

    private void topics() {
        assertEquals(Status.OK.getStatusCode(), client.get("topic/source").getStatus());
        assertEquals(Status.OK.getStatusCode(), client.get("topic/sink").getStatus());
    }

    private void properties() {
        Response response = client.get("properties");
        assertEquals(Status.OK.getStatusCode(), response.getStatus());
        assertEquals(fsm.getProperties(), HttpClient.getBody(response, Properties.class));
    }

    private void subgroup() {
        Response response = client.put("subgroup/YY", Entity.json(""), Collections.emptyMap());
        assertEquals(Status.OK.getStatusCode(), response.getStatus());
        assertEquals("YY", HttpClient.getBody(response, String.class));

        response = client.get("subgroup");
        assertEquals(Status.OK.getStatusCode(), response.getStatus());
        assertEquals("YY", HttpClient.getBody(response, String.class));
    }

    private void group() {
        Response response = client.put("group/GG", Entity.json(""), Collections.emptyMap());
        assertEquals(Status.OK.getStatusCode(), response.getStatus());
        assertEquals("GG", HttpClient.getBody(response, String.class));

        response = HttpClientFactoryInstance.getClientFactory().get("lifecycle").get("group");
        assertEquals(Status.OK.getStatusCode(), response.getStatus());
        assertEquals("GG", HttpClient.getBody(response, String.class));
    }

    private void metrics() {
        Response response = client.get("metrics");
        assertEquals(Status.OK.getStatusCode(), response.getStatus());
        String body = HttpClient.getBody(response, String.class);
        assertThat(body).contains(PROM_DEPLOY_REQUESTS_TOTAL_UNDEPLOY_ACCEPTED);
        assertThat(body).contains(PDPD_DEPLOY_REQUESTS_TOTAL_DEPLOY_ACCEPTED);
        assertThat(body).contains(PDPD_DEPLOY_REQUESTS_TOTAL_DEPLOY_DECLINED);
    }

    private LifecycleFsm newFsmInstance() throws NoSuchFieldException, IllegalAccessException {
        LifecycleFsm fsm = new LifecycleFsm();
        ControllerSupport.setStaticField(LifecycleFeature.class, "fsm", fsm);
        return fsm;
    }

    protected ToscaPolicy getPolicyFromFile(String filePath, String policyName) throws CoderException, IOException {
        String policyJson = Files.readString(Paths.get(filePath));
        ToscaServiceTemplate serviceTemplate = coder.decode(policyJson, ToscaServiceTemplate.class);
        return serviceTemplate.getToscaTopologyTemplate().getPolicies().get(0).get(policyName);
    }

    protected String toString(ToscaPolicy policy) throws CoderException {
        return coder.encode(policy);
    }

    private ToscaPolicy getExamplesPolicy() throws CoderException {
        String policyJson = ResourceUtils.getResourceAsString(
            RestLifecycleManagerTest.VCPE_OPERATIONAL_DROOLS_POLICY_JSON);
        ToscaServiceTemplate serviceTemplate = new StandardCoder().decode(policyJson, ToscaServiceTemplate.class);
        return serviceTemplate.getToscaTopologyTemplate().getPolicies().get(0).get(
            RestLifecycleManagerTest.OP_POLICY_NAME_VCPE);
    }
}
