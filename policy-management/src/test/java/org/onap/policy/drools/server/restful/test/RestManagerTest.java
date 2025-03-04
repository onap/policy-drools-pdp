/*-
 * ============LICENSE_START=======================================================
 * policy-management
 * ================================================================================
 * Copyright (C) 2017-2021 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.server.restful.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.onap.policy.common.endpoints.properties.PolicyEndPointProperties.PROPERTY_HTTP_AUTH_PASSWORD_SUFFIX;
import static org.onap.policy.common.endpoints.properties.PolicyEndPointProperties.PROPERTY_HTTP_AUTH_USERNAME_SUFFIX;
import static org.onap.policy.common.endpoints.properties.PolicyEndPointProperties.PROPERTY_HTTP_PORT_SUFFIX;
import static org.onap.policy.common.endpoints.properties.PolicyEndPointProperties.PROPERTY_HTTP_SERIALIZATION_PROVIDER;
import static org.onap.policy.common.endpoints.properties.PolicyEndPointProperties.PROPERTY_HTTP_SERVER_SERVICES;
import static org.onap.policy.common.message.bus.properties.MessageBusProperties.PROPERTY_KAFKA_SINK_TOPICS;
import static org.onap.policy.common.message.bus.properties.MessageBusProperties.PROPERTY_KAFKA_SOURCE_TOPICS;
import static org.onap.policy.common.message.bus.properties.MessageBusProperties.PROPERTY_NOOP_SINK_TOPICS;
import static org.onap.policy.common.message.bus.properties.MessageBusProperties.PROPERTY_NOOP_SOURCE_TOPICS;
import static org.onap.policy.common.message.bus.properties.MessageBusProperties.PROPERTY_TOPIC_SERVERS_SUFFIX;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import org.apache.http.HttpEntity;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.onap.policy.common.endpoints.http.server.YamlJacksonHandler;
import org.onap.policy.common.gson.JacksonHandler;
import org.onap.policy.common.message.bus.event.TopicEndpointManager;
import org.onap.policy.common.utils.network.NetworkUtil;
import org.onap.policy.drools.persistence.SystemPersistenceConstants;
import org.onap.policy.drools.system.PolicyControllerConstants;
import org.onap.policy.drools.system.PolicyEngineConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@TestMethodOrder(MethodOrderer.DisplayName.class)
public class RestManagerTest {
    private static final int DEFAULT_TELEMETRY_PORT = 7887;
    private static final String HOST = "localhost";
    private static final String REST_MANAGER_PATH = "/policy/pdp";
    private static final String HOST_URL = "http://" + HOST + ":" + DEFAULT_TELEMETRY_PORT + REST_MANAGER_PATH;
    private static final String TELEMETRY_USER = "x";
    private static final String TELEMETRY_PASSWORD = "y";
    private static final String FOO_CONTROLLER = "foo";
    private static final String KAFKA_TOPIC = "kafka-topic-test";
    private static final String NOOP_TOPIC = "noop_topic";
    private static final String KAFKA_SOURCE_SERVER_PROPERTY = PROPERTY_KAFKA_SOURCE_TOPICS
        + "." + KAFKA_TOPIC + PROPERTY_TOPIC_SERVERS_SUFFIX;
    private static final String KAFKA_SINK_SERVER_PROPERTY = PROPERTY_KAFKA_SINK_TOPICS + "."
        + KAFKA_TOPIC + PROPERTY_TOPIC_SERVERS_SUFFIX;
    private static final String KAFKA_SERVER = "localhost:9092";

    private static final String FOO_CONTROLLER_FILE = FOO_CONTROLLER + "-controller.properties";
    private static final String FOO_CONTROLLER_FILE_BAK = FOO_CONTROLLER_FILE + ".bak";
    private static final String PDP_CONFIGURATION_JSON =
        "src/test/resources/org/onap/policy/drools/server/restful/PdpConfiguration.json";

    private static CloseableHttpClient client;

    private static final Logger logger = LoggerFactory.getLogger(RestManagerTest.class);

    /**
     * Set up.
     *
     * @throws IOException throws an IO exception
     */
    @BeforeAll
    public static void setUp() throws IOException, InterruptedException {
        cleanUpWorkingDirs();

        SystemPersistenceConstants.getManager().setConfigurationDir(null);

        /* override default port */
        final Properties engineProps = PolicyEngineConstants.getManager().defaultTelemetryConfig();
        engineProps.put(PROPERTY_HTTP_SERVER_SERVICES + "."
            + PolicyEngineConstants.TELEMETRY_SERVER_DEFAULT_NAME
            + PROPERTY_HTTP_PORT_SUFFIX, "" + DEFAULT_TELEMETRY_PORT);
        engineProps.put(PROPERTY_HTTP_SERVER_SERVICES + "."
                + PolicyEngineConstants.TELEMETRY_SERVER_DEFAULT_NAME
                + PROPERTY_HTTP_AUTH_USERNAME_SUFFIX,
            TELEMETRY_USER);
        engineProps.put(PROPERTY_HTTP_SERVER_SERVICES + "."
                + PolicyEngineConstants.TELEMETRY_SERVER_DEFAULT_NAME
                + PROPERTY_HTTP_AUTH_PASSWORD_SUFFIX,
            TELEMETRY_PASSWORD);
        engineProps.put(PROPERTY_HTTP_SERVER_SERVICES + "."
                + PolicyEngineConstants.TELEMETRY_SERVER_DEFAULT_NAME
                + PROPERTY_HTTP_SERIALIZATION_PROVIDER,
            String.join(",", JacksonHandler.class.getName(), YamlJacksonHandler.class.getName()));

        /* other properties */
        engineProps.put(PROPERTY_KAFKA_SOURCE_TOPICS, KAFKA_TOPIC);
        engineProps.put(PROPERTY_KAFKA_SINK_TOPICS, KAFKA_TOPIC);
        engineProps.put(KAFKA_SOURCE_SERVER_PROPERTY, KAFKA_SERVER);
        engineProps.put(KAFKA_SINK_SERVER_PROPERTY, KAFKA_SERVER);

        PolicyEngineConstants.getManager().configure(engineProps);
        PolicyEngineConstants.getManager().start();

        Properties controllerProps = new Properties();
        PolicyEngineConstants.getManager().createPolicyController(FOO_CONTROLLER, controllerProps);

        CredentialsProvider provider = new BasicCredentialsProvider();
        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(TELEMETRY_USER, TELEMETRY_PASSWORD);
        provider.setCredentials(AuthScope.ANY, credentials);

        client = HttpClientBuilder.create().setDefaultCredentialsProvider(provider).build();

        if (!NetworkUtil.isTcpPortOpen("localhost", DEFAULT_TELEMETRY_PORT, 5, 10000L)) {
            throw new IllegalStateException("cannot connect to port " + DEFAULT_TELEMETRY_PORT);
        }

        Properties noopProperties = new Properties();
        noopProperties.put(PROPERTY_NOOP_SOURCE_TOPICS, NOOP_TOPIC);
        noopProperties.put(PROPERTY_NOOP_SINK_TOPICS, NOOP_TOPIC);
        TopicEndpointManager.getManager().addTopics(noopProperties);
    }

    /**
     * Tear down.
     *
     * @throws IOException IO exception
     */
    @AfterAll
    public static void tearDown() throws IOException {
        try {
            client.close();
        } catch (IOException ex) {
            logger.warn("cannot close HTTP client connection", ex);
        }

        /* Shutdown managed resources */
        PolicyControllerConstants.getFactory().shutdown();
        TopicEndpointManager.getManager().shutdown();
        PolicyEngineConstants.getManager().stop();
        cleanUpWorkingDirs();
    }


    @Test
    void testPutDelete() throws IOException {
        putTest(HOST_URL + "/engine/switches/lock", 406);
        deleteTest(HOST_URL + "/engine/switches/lock", 406);

        putDeleteEngineControllers();

        putTest(HOST_URL + "/engine/switches/lock", 200);
        deleteTest(HOST_URL + "/engine/switches/lock", 200);

        putDeleteTopicsSources();
        putDeleteTopicSwitches();

        putTest(HOST_URL + "/engine/tools/loggers/ROOT/debug", 200);
        putTest(HOST_URL + "/engine/environment/XX", 200, "WARN", ContentType.DEFAULT_TEXT);
        deleteTest(HOST_URL + "/engine/switches/activation", 200);
        putTest(HOST_URL + "/engine/switches/activation", 200);
    }

    private void putDeleteTopicSwitches() throws IOException {
        putDeleteSwitch("/engine/topics/sources/kafka/", KAFKA_TOPIC, "lock");
        putDeleteSwitch("/engine/topics/sources/noop/", NOOP_TOPIC, "lock");
        putDeleteSwitch("/engine/topics/sinks/kafka/", KAFKA_TOPIC, "lock");
        putDeleteSwitch("/engine/topics/sinks/noop/", NOOP_TOPIC, "lock");

        putDeleteSwitch("/engine/topics/sources/kafka/", KAFKA_TOPIC, "activation");
        putDeleteSwitch("/engine/topics/sources/noop/", NOOP_TOPIC, "activation");
        putDeleteSwitch("/engine/topics/sinks/kafka/", KAFKA_TOPIC, "activation");
        putDeleteSwitch("/engine/topics/sinks/noop/", NOOP_TOPIC, "activation");

        putSwitch("/engine/topics/sources/kafka/", KAFKA_TOPIC, "activation");
        putSwitch("/engine/topics/sources/noop/", NOOP_TOPIC, "activation");
        putSwitch("/engine/topics/sinks/kafka/", KAFKA_TOPIC, "activation");
        putSwitch("/engine/topics/sinks/noop/", NOOP_TOPIC, "activation");
    }

    private void putDeleteTopicsSources() throws IOException {
        putTest(HOST_URL + "/engine/topics/sources/noop/" + NOOP_TOPIC + "/events", 200,
            "{x:y}", ContentType.TEXT_PLAIN);
        putTest(HOST_URL + "/engine/topics/sources/kafka/" + KAFKA_TOPIC + "/events", 200,
            "FOOOO", ContentType.TEXT_PLAIN);
        putTest(HOST_URL + "/engine/topics/sources/kafka/fiznits/events", 406,
            "FOOOO", ContentType.TEXT_PLAIN);
        putTest(HOST_URL + "/engine/topics/switches/lock", 200);
        putTest(HOST_URL + "/engine/topics/sources/kafka/" + KAFKA_TOPIC + "/events",
            406, "FOOOO", ContentType.TEXT_PLAIN);
        deleteTest(HOST_URL + "/engine/topics/switches/lock", 200);
    }

    private void putDeleteEngineControllers() throws IOException {
        deleteTest(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/drools/facts/session/factType", 200);
        deleteTest(HOST_URL + "/engine/controllers/controllerName/drools/facts/session/factType", 404);
        deleteTest(HOST_URL + "/engine/controllers/", 405);
        deleteTest(HOST_URL + "/engine/controllers/" + null, 400);
        putTest(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/switches/lock", 406);
        deleteTest(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER, 200);
    }

    private void postTest(String uri, int statusCode, String payload) throws IOException {
        HttpPost post = new HttpPost(uri);
        if (ContentType.APPLICATION_JSON != null) {
            post.setEntity(new StringEntity(payload, ContentType.APPLICATION_JSON));
        }
        requestTest(post, statusCode);
    }

    private void putTest(String uri, int statusCode) throws IOException {
        requestTest(new HttpPut(uri), statusCode);
    }

    private void putTest(String uri, int statusCode, String payload, ContentType contentType) throws IOException {
        HttpPut put = new HttpPut(uri);
        if (contentType != null) {
            put.setEntity(new StringEntity(payload, contentType));
        }
        requestTest(put, statusCode);
    }

    private void deleteTest(String uri, int statusCode) throws IOException {
        requestTest(new HttpDelete(uri), statusCode);
    }

    private void requestTest(HttpRequestBase request, int statusCode) throws IOException {
        CloseableHttpResponse resp = client.execute(request);
        logger.info("{} response code: {}", request.getRequestLine(), resp.getStatusLine().getStatusCode());
        assertEquals(statusCode, resp.getStatusLine().getStatusCode());
        request.releaseConnection();
    }

    private void putDeleteSwitch(String urlPrefix, String topic, String control) throws IOException {
        putSwitch(urlPrefix, topic, control);
        deleteSwitch(urlPrefix, topic, control);
    }

    private void deleteSwitch(String urlPrefix, String topic, String control) throws IOException {
        deleteTest(HOST_URL + urlPrefix + topic + "/switches/" + control, 200);
    }

    private void putSwitch(String urlPrefix, String topic, String control) throws IOException {
        putTest(HOST_URL + urlPrefix + topic + "/switches/" + control, 200);
    }

    @Test
    void testPost() throws IOException {
        postTest(HOST_URL + "/engine/inputs/configuration", 406,
            Files.readString(Paths.get(PDP_CONFIGURATION_JSON)));

        postTest(HOST_URL + "/engine/controllers", 400, "{}");

        postTest(HOST_URL + "/engine/controllers", 304, "{controller.name : foo}");

        postTest(HOST_URL + "/engine/controllers", 206, "{controller.name : new}");

        deleteTest(HOST_URL + "/engine/controllers/new", 200);

        postTest(HOST_URL + "/engine/controllers/foo/drools/facts/session1/query1/entity1", 200, "[{f:v}]");

        postTest(HOST_URL + "/engine/controllers/new/drools/facts/session1/query1/entity1", 404, "[{f:v}]");
    }

    @Test
    void testGetSwagger() throws IOException {
        HttpGet httpGet;
        CloseableHttpResponse response;
        httpGet = new HttpGet(HOST_URL + "/engine/swagger");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
    }

    @Test
    void testGetEngine() throws IOException {
        HttpGet httpGet;
        CloseableHttpResponse response;

        /*
         * GET: /engine /engine/features /engine/features/inventory /engine/features/featurename
         * /engine/inputs /engine/properties /engine/environment /engine/switches
         * /engine/controllers
         */

        httpGet = new HttpGet(HOST_URL + "/engine");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/features");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/features/inventory");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/features/foobar");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(404, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/inputs");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/properties");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/environment");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        PolicyEngineConstants.getManager().setEnvironmentProperty("foo", "bar");
        httpGet = new HttpGet(HOST_URL + "/engine/environment/foo");
        response = client.execute(httpGet);
        String responseBody = this.getResponseBody(response);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        logger.info("{} response body: {}", httpGet.getRequestLine(), responseBody);
        assertEquals(200, response.getStatusLine().getStatusCode());
        assertEquals("bar", responseBody);
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/switches");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/controllers");
        response = client.execute(httpGet);
        responseBody = this.getResponseBody(response);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        logger.info("{} response body: {}", httpGet.getRequestLine(), responseBody);
        assertEquals(200, response.getStatusLine().getStatusCode());
        assertEquals("[\"" + FOO_CONTROLLER + "\"]", responseBody);
        httpGet.releaseConnection();
    }

    @Test
    void testGet_EngineControllers() throws IOException {
        HttpGet httpGet;
        CloseableHttpResponse response;

        /*
         * GET: /engine/controllers/inventory /engine/controllers/features
         * /engine/controllers/features/inventory /engine/controllers/features/featureName
         * /engine/controllers/controllerName
         *
         */
        httpGet = new HttpGet(HOST_URL + "/engine/controllers/inventory");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/controllers/features");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/controllers/features/inventory");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/controllers/features/dummy");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(404, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER);
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/controllers/nonexistantcontroller");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(404, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
    }

    @Test
    void testGet() throws IOException {
        HttpGet httpGet;
        CloseableHttpResponse response;
        String responseBody;

        /*
         * GET: /engine/controllers/controllerName/properties
         * /engine/controllers/controllerName/inputs /engine/controllers/controllerName/switches
         * /engine/controllers/controllerName/drools
         */
        httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/properties");
        response = client.execute(httpGet);
        responseBody = this.getResponseBody(response);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        logger.info("{} response code: {}", httpGet.getRequestLine(), responseBody);
        assertEquals(200, response.getStatusLine().getStatusCode());
        assertEquals("{}", responseBody);
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/controllers/nonexistantcontroller/properties");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(404, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/inputs");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/switches");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/drools");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/controllers/nonexistantcontroller/drools");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(404, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
    }

    @Test
    void testGetDroolsControllers() throws IOException {
        CloseableHttpResponse response;
        HttpGet httpGet;
        /*
         * GET: /engine/controllers/controllerName/drools/facts
         * /engine/controllers/controllerName/drools/facts/session
         * /engine/controllers/controllerName/drools/facts/session/factType
         * /engine/controllers/controllerName/drools/facts/session/query/queriedEntity
         *
         */
        httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/drools/facts");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/controllers/nonexistantcontroller/drools/facts");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(404, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/drools/facts/session");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/drools/facts/session/factType");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(
            HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/drools/facts/session/query/queriedEntity");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/controllers/dummy" + "/drools/facts/session/query/queriedEntity");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(404, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
    }

    @Test
    void testGetEngineTools() throws IOException {
        CloseableHttpResponse response;
        HttpGet httpGet;
        /*
         * GET: /engine/tools/uuid /engine/tools/loggers /engine/tools/loggers/loggerName
         */
        httpGet = new HttpGet(HOST_URL + "/engine/tools/uuid");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/tools/loggers");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/tools/loggers/ROOT");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
    }

    @Test
    void testGetControllersDecoders() throws IOException {
        HttpGet httpGet;
        CloseableHttpResponse response;
        /*
         * GET: /engine/controllers/controllerName/decoders
         * /engine/controllers/controllerName/decoders/filters
         * /engine/controllers/controllerName/decoders/topic
         * /engine/controllers/controllerName/decoders/topic/filters
         * /engine/controllers/controllerName/decoders/topic/filters/factType
         * /engine/controllers/controllerName/decoders/topic/filters/factType/rules
         * /engine/controllers/controllerName/decoders/topic/filtes/factType/rules/ruleName
         * /engine/controllers/controllerName/encoders
         */
        httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/decoders");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/controllers/nonexistantcontroller/decoders");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(404, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/decoders/filters");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/controllers/nonexistantcontroller/decoders/filters");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(404, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/decoders/topic");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(404, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/decoders/topic/filters");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(404, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/decoders/topic/filters/factType");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(404, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(
            HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/decoders/topic/filters/factType/rules");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(404, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(
            HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/decoders/topic/filters/factType/rules/ruleName");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(404, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/encoders");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
    }

    @Test
    void testGetKafkaTopics() throws IOException {
        CloseableHttpResponse response;
        HttpGet httpGet;
        /*
         * GET: /engine/topics
         * /engine/topics/switches
         * /engine/topics/sources
         * /engine/topics/sinks
         * /engine/topics/sinks/kafka
         * /engine/topics/sources/kafka
         * /engine/topics/sinks/kafka/topic
         * /engine/topics/sources/kafka/topic
         * /engine/topics/sinks/kafka/topic/events
         * /engine/topics/sources/kafka/topic/events
         * /engine/topics/sources/kafka/topic/switches
         */
        httpGet = new HttpGet(HOST_URL + "/engine/topics");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/topics/switches");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/topics/sources");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/topics/sources/kafka");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks/kafka");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/topics/sources/kafka/" + KAFKA_TOPIC);
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/topics/sources/kafka/foobar");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(500, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks/kafka/" + KAFKA_TOPIC);
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks/kafka/foobar");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(500, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/topics/sources/kafka/" + KAFKA_TOPIC + "/events");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/topics/sources/kafka/foobar/events");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(500, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks/kafka/" + KAFKA_TOPIC + "/events");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks/kafka/foobar/events");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(500, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/topics/sources/kafka/" + KAFKA_TOPIC + "/switches");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks/kafka/" + KAFKA_TOPIC + "/switches");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
    }

    @Test
    void testGetNoopTopics() throws IOException {
        CloseableHttpResponse response;
        HttpGet httpGet;
        /*
         * GET: /engine/topics
         * /engine/topics/sources/noop
         * /engine/topics/sinks/noop
         * /engine/topics/sources/noop/topic
         * /engine/topics/sinks/noop/topic
         * /engine/topics/sources/noop/topic/events
         * /engine/topics/sinks/noop/topic/events
         * /engine/topics/sources/noop/topic/switches
         * /engine/topics/sinks/noop/topic/switches
         */

        httpGet = new HttpGet(HOST_URL + "/engine/topics/sources/noop");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks/noop");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/topics/sources/noop/" + NOOP_TOPIC);
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/topics/sources/noop/foobar");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(500, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks/noop/" + NOOP_TOPIC);
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks/noop/foobar");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(500, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/topics/sources/noop/" + NOOP_TOPIC + "/events");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/topics/sources/noop/foobar/events");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(500, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks/noop/" + NOOP_TOPIC + "/events");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks/noop/foobar/events");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(500, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/topics/sources/noop/" + NOOP_TOPIC + "/switches");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks/noop/" + NOOP_TOPIC + "/switches");
        response = client.execute(httpGet);
        logger.info("{} response code: {}", httpGet.getRequestLine(), response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
    }

    /**
     * Get response body.
     *
     * @param response incoming response
     * @return the body or null
     */
    private String getResponseBody(CloseableHttpResponse response) {

        HttpEntity entity;
        try {
            entity = response.getEntity();
            return EntityUtils.toString(entity);

        } catch (final IOException e) {
            logger.info(e.toString());
        }

        return null;
    }

    private static void cleanUpWorkingDirs() throws IOException {
        final Path testControllerPath = Paths.get(
            SystemPersistenceConstants.getManager().getConfigurationPath().toString(), FOO_CONTROLLER_FILE);
        final Path testControllerBakPath =
            Paths.get(SystemPersistenceConstants.getManager().getConfigurationPath().toString(),
                FOO_CONTROLLER_FILE_BAK);

        Files.deleteIfExists(testControllerPath);
        Files.deleteIfExists(testControllerBakPath);
    }

}
