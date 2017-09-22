/*-
 * ============LICENSE_START=======================================================
 * policy-management
 * ================================================================================
 * Copyright (C) 2017 AT&T Intellectual Property. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Properties;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.onap.policy.drools.properties.PolicyProperties;
import org.onap.policy.drools.system.PolicyEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

public class RestManagerTest {
    

    public static final int DEFAULT_TELEMETRY_PORT = 9698;
    private static final String HOST = "localhost";
    private static final String REST_MANAGER_PATH = "/policy/pdp";
    private static final String HOST_URL = "http://" + HOST + ":" + DEFAULT_TELEMETRY_PORT + REST_MANAGER_PATH;
    private static final String FOO_CONTROLLER = "foo-controller";
    
    private static final String UEB_TOPIC = "PDPD-CONFIGURATION";
    private static final String DMAAP_TOPIC = "com.att.ecomp-policy.DCAE_CL_EVENT_TEST";
    private static final String NOOP_TOPIC = "NOOP_TOPIC";
    
    private static final String UEB_SOURCE_SERVER_PROPERTY = PolicyProperties.PROPERTY_UEB_SOURCE_TOPICS +
            "." + UEB_TOPIC + PolicyProperties.PROPERTY_TOPIC_SERVERS_SUFFIX;
    private static final String UEB_SINK_SERVER_PROPERTY = PolicyProperties.PROPERTY_UEB_SINK_TOPICS +
            "." + UEB_TOPIC + PolicyProperties.PROPERTY_TOPIC_SERVERS_SUFFIX;
    private static final String DMAAP_SOURCE_SERVER_PROPERTY = PolicyProperties.PROPERTY_DMAAP_SOURCE_TOPICS +
            "." + DMAAP_TOPIC + PolicyProperties.PROPERTY_TOPIC_SERVERS_SUFFIX;
    private static final String DMAAP_SINK_SERVER_PROPERTY = PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS +
            "." + DMAAP_TOPIC + PolicyProperties.PROPERTY_TOPIC_SERVERS_SUFFIX;
    private static final String UEB_SERVER = "uebsb91sfdc.it.att.com";
    private static final String DMAAP_SERVER = "olsd005.wnsnet.attws.com";
    private static final String DMAAP_MECHID = "m03822@ecomp-policy.att.com";
    private static final String DMAAP_PASSWD = "Ec0mpP0l1cy";
    
    private static final String DMAAP_SOURCE_MECHID_KEY = PolicyProperties.PROPERTY_DMAAP_SOURCE_TOPICS + 
            "." + DMAAP_TOPIC + PolicyProperties.PROPERTY_TOPIC_AAF_MECHID_SUFFIX;
    private static final String DMAAP_SOURCE_PASSWD_KEY = PolicyProperties.PROPERTY_DMAAP_SOURCE_TOPICS + 
            "." + DMAAP_TOPIC + PolicyProperties.PROPERTY_TOPIC_AAF_PASSWORD_SUFFIX;
    
    private static final String DMAAP_SINK_MECHID_KEY = PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS + 
            "." + DMAAP_TOPIC + PolicyProperties.PROPERTY_TOPIC_AAF_MECHID_SUFFIX;
    private static final String DMAAP_SINK_PASSWD_KEY = PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS + 
            "." + DMAAP_TOPIC + PolicyProperties.PROPERTY_TOPIC_AAF_PASSWORD_SUFFIX;
    
    
    private static CloseableHttpClient client;
    
    private static final Logger logger = LoggerFactory.getLogger(RestManagerTest.class);
    
    @BeforeClass
    public static void setUp() {
        /* override default port */
        final Properties engineProps = PolicyEngine.manager.defaultTelemetryConfig();
        engineProps.put(PolicyProperties.PROPERTY_HTTP_SERVER_SERVICES + "."
                + PolicyEngine.TELEMETRY_SERVER_DEFAULT_NAME + PolicyProperties.PROPERTY_HTTP_PORT_SUFFIX,
                "" + DEFAULT_TELEMETRY_PORT);
        engineProps.put(PolicyProperties.PROPERTY_UEB_SOURCE_TOPICS, UEB_TOPIC);
        engineProps.put(PolicyProperties.PROPERTY_UEB_SINK_TOPICS, UEB_TOPIC);
        engineProps.put(PolicyProperties.PROPERTY_DMAAP_SOURCE_TOPICS, DMAAP_TOPIC);
        engineProps.put(PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS, DMAAP_TOPIC);
        engineProps.put(UEB_SOURCE_SERVER_PROPERTY, UEB_SERVER);
        engineProps.put(UEB_SINK_SERVER_PROPERTY, UEB_SERVER);
        engineProps.put(DMAAP_SOURCE_SERVER_PROPERTY, DMAAP_SERVER);
        engineProps.put(DMAAP_SINK_SERVER_PROPERTY, DMAAP_SERVER);
        engineProps.put(DMAAP_SOURCE_MECHID_KEY, DMAAP_MECHID);
        engineProps.put(DMAAP_SOURCE_PASSWD_KEY, DMAAP_PASSWD);
        engineProps.put(DMAAP_SINK_MECHID_KEY, DMAAP_MECHID);
        engineProps.put(DMAAP_SINK_PASSWD_KEY, DMAAP_PASSWD);
        engineProps.put(PolicyProperties.PROPERTY_NOOP_SINK_TOPICS, NOOP_TOPIC);

        
            PolicyEngine.manager.configure(engineProps);
            PolicyEngine.manager.start();
    
        client = HttpClients.createDefault();
        
    }
    
    @AfterClass
    public static void tearDown() {
        PolicyEngine.manager.shutdown();

    }
    
    
    @Test
    public void mainTest() {
        try {
            assertTrue(PolicyEngine.manager.isAlive());
            GETTest();
            PUT_DELETE_TEST();
            
        } catch (IOException | InterruptedException e) {
           logger.error("RestManagerTest threw exeption", e);
        }
        
    }
    public void PUT_DELETE_TEST() throws ClientProtocolException, IOException, InterruptedException {
        HttpPut httpPut;
        HttpDelete httpDelete;
        CloseableHttpResponse response;
        
        httpPut = new HttpPut(HOST_URL + "/engine/topics/sources/ueb/" + UEB_TOPIC + "/events");
        httpPut.addHeader("Content-Type", "text/plain");
        httpPut.addHeader("Accept", "application/json");
        httpPut.setEntity(new StringEntity("FOOOO"));
        response = client.execute(httpPut);
        logger.info("/engine/topics/sources/ueb/{}/events response code: {}", UEB_TOPIC, response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpPut.releaseConnection();        
        
        httpPut = new HttpPut(HOST_URL + "/engine/topics/switches/lock");
        response = client.execute(httpPut);
        logger.info("/engine/topics/switches/lock response code: {}", response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpPut.releaseConnection();
        
        httpPut = new HttpPut(HOST_URL + "/engine/topics/sources/ueb/" + UEB_TOPIC + "/events");
        httpPut.addHeader("Content-Type", "text/plain");
        httpPut.addHeader("Accept", "application/json");
        httpPut.setEntity(new StringEntity("FOOOO"));
        response = client.execute(httpPut);
        logger.info("/engine/topics/sources/ueb/{}/events response code: {}", UEB_TOPIC, response.getStatusLine().getStatusCode());
        assertEquals(406, response.getStatusLine().getStatusCode());
        httpPut.releaseConnection();  
        
        httpDelete = new HttpDelete(HOST_URL + "/engine/topics/switches/lock");
        response = client.execute(httpDelete);
        logger.info("/engine/topics/switches/lock response code: {}", response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpDelete.releaseConnection();
        
        httpPut = new HttpPut(HOST_URL + "/engine/topics/sources/ueb/" + UEB_TOPIC + "/switches/lock");
        response = client.execute(httpPut);
        logger.info("/engine/topics/sources/ueb/{}/switches/lock: {}", UEB_TOPIC, response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpPut.releaseConnection();
        
        httpDelete = new HttpDelete(HOST_URL + "/engine/topics/sources/ueb/" + UEB_TOPIC + "/switches/lock");
        response = client.execute(httpDelete);
        logger.info("/engine/topics/sources/ueb/{}/switches/lock: {}", UEB_TOPIC, response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpDelete.releaseConnection();
        
        httpPut = new HttpPut(HOST_URL + "/engine/topics/sources/dmaap/" + DMAAP_TOPIC + "/switches/lock");
        response = client.execute(httpPut);
        logger.info("/engine/topics/sources/dmaap/{}/switches/lock: {}", DMAAP_TOPIC, response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpPut.releaseConnection();
        
        httpDelete = new HttpDelete(HOST_URL + "/engine/topics/sources/dmaap/" + DMAAP_TOPIC + "/switches/lock");
        response = client.execute(httpDelete);
        logger.info("/engine/topics/sources/dmaap/{}/switches/lock: {}", DMAAP_TOPIC, response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpDelete.releaseConnection();
        
        httpPut = new HttpPut(HOST_URL + "/engine/switches/activation");
        response = client.execute(httpPut);
        logger.info("/engine/switches/activation response code: {}", response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpPut.releaseConnection();
        
        httpDelete = new HttpDelete(HOST_URL + "/engine/switches/activation");
        response = client.execute(httpDelete);
        logger.info("/engine/switches/activation response code: {}", response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpDelete.releaseConnection();

    }
    public void GETTest() throws ClientProtocolException, IOException, InterruptedException {
      
        HttpGet httpGet;
        CloseableHttpResponse response;
        String responseBody;
        
        httpGet = new HttpGet(HOST_URL + "/engine");
        response = client.execute(httpGet);
        logger.info("/engine response code: {}", response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/features");
        response = client.execute(httpGet);
        logger.info("/engine/features response code: {}", response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/features/inventory");
        response = client.execute(httpGet);
        logger.info("/engine/features/inventory response code: {}", response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/features/foobar");
        response = client.execute(httpGet);
        logger.info("/engine/features/foobar response code: {}",response.getStatusLine().getStatusCode());
        assertEquals(404, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/inputs");
        response = client.execute(httpGet);
        logger.info("/engine/inputs response code: {}",response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/properties");
        response = client.execute(httpGet);
        logger.info("/engine/properties response code: {}",response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/environment");
        response = client.execute(httpGet);
        logger.info("/engine/environment response code: {}",response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
 
        PolicyEngine.manager.setEnvironmentProperty("foo", "bar"); 
        httpGet = new HttpGet(HOST_URL + "/engine/environment/foo");
        response = client.execute(httpGet);
        responseBody = getResponseBody(response);
        logger.info("/engine/environment/foo response code: {}",response.getStatusLine().getStatusCode());
        logger.info("/engine/environment/foo response body: {}",responseBody);
        assertEquals(200, response.getStatusLine().getStatusCode());
        assertEquals("bar", responseBody);
        httpGet.releaseConnection();
        

        httpGet = new HttpGet(HOST_URL + "/engine/switches");
        response = client.execute(httpGet);
        logger.info("/engine/switches response code: {}",response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
       
        Properties controllerProps = new Properties();
        PolicyEngine.manager.createPolicyController(FOO_CONTROLLER, controllerProps);
        httpGet = new HttpGet(HOST_URL + "/engine/controllers");
        response = client.execute(httpGet);
        responseBody = getResponseBody(response);
        logger.info("/engine/controllers response code: {}",response.getStatusLine().getStatusCode());
        logger.info("/engine/controllers response body: {}",responseBody);
        assertEquals(200, response.getStatusLine().getStatusCode());
        assertEquals("[\"" + FOO_CONTROLLER +"\"]", responseBody);
        httpGet.releaseConnection();
       
        
        httpGet = new HttpGet(HOST_URL + "/engine/controllers/inventory");
        response = client.execute(httpGet);
        logger.info("/engine/controllers/inventory response code: {}",response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        
        httpGet = new HttpGet(HOST_URL + "/engine/controllers/features");
        response = client.execute(httpGet);
        logger.info("/engine/controllers/features response code: {}",response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        
        httpGet = new HttpGet(HOST_URL + "/engine/controllers/features/inventory");
        response = client.execute(httpGet);
        logger.info("/engine/controllers/features/inventory response code: {}",response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        
        httpGet = new HttpGet(HOST_URL + "/engine/controllers/features/dummy");
        response = client.execute(httpGet);
        logger.info("/engine/controllers/features/dummy response code: {}",response.getStatusLine().getStatusCode());
        assertEquals(404, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        
        httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER);
        response = client.execute(httpGet);
        logger.info("/engine/controllers/ response code: {}",response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/controllers/nonexistantcontroller");
        response = client.execute(httpGet);
        logger.info("/engine/controllers/nonexistantcontroller response code: {}",response.getStatusLine().getStatusCode());
        assertEquals(404, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        

        httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/properties");
        response = client.execute(httpGet);
        responseBody = getResponseBody(response);
        logger.info("/engine/controllers/contoller/properties response code: {}", response.getStatusLine().getStatusCode());
        logger.info("/engine/controllers/contoller/properties response code: {}", responseBody);
        assertEquals(200, response.getStatusLine().getStatusCode());
        assertEquals("{}", responseBody);
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/controllers/nonexistantcontroller/properties");
        response = client.execute(httpGet);
        logger.info("/engine/controllers/nonexistantcontroller/properties response code: {}",response.getStatusLine().getStatusCode());
        assertEquals(404, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        
        httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/inputs");
        response = client.execute(httpGet);
        logger.info("/engine/controllers/controller/inputs response code: {}",response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/switches");
        response = client.execute(httpGet);
        logger.info("/engine/controllers/controller/switches response code: {}",response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
       
        httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/drools");
        response = client.execute(httpGet);
        logger.info("/engine/controllers/controller/drools response code: {}",response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/controllers/nonexistantcontroller/drools");
        response = client.execute(httpGet);
        logger.info("/engine/controllers/nonexistantcontroller/drools response code: {}",response.getStatusLine().getStatusCode());
        assertEquals(404, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        
        httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/drools/facts");
        response = client.execute(httpGet);
        logger.info("/engine/controllers/controller/drools/facts response code: {}",response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/controllers/nonexistantcontroller/drools/facts");
        response = client.execute(httpGet);
        logger.info("/engine/controllers/nonexistantcontroller/drools/facts response code: {}",response.getStatusLine().getStatusCode());
        assertEquals(404, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/drools/facts/dummy");
        response = client.execute(httpGet);
        logger.info("/engine/controllers/controller/drools/facts/fact response code: {}",response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        
        httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/drools/facts/dummy/dummy");
        response = client.execute(httpGet);
        logger.info("/engine/controllers/controller/drools/facts/fact response code: {}",response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/drools/facts/session/query/queriedEntity");
        response = client.execute(httpGet);
        logger.info("/engine/controllers/controller/drools/facts/session/query/queriedEntity response code: {}",response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
       
        httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/decoders");
        response = client.execute(httpGet);
        logger.info("/engine/controllers/controller/decoders response code: {}",response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/controllers/nonexistantcontroller/decoders");
        response = client.execute(httpGet);
        logger.info("/engine/controllers/nonexistantcontroller/decoders response code: {}",response.getStatusLine().getStatusCode());
        assertEquals(404, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/decoders/filters");
        response = client.execute(httpGet);
        logger.info("/engine/controllers/controllers/decoders/filters response code: {}",response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/controllers/nonexistantcontroller/decoders/filters");
        response = client.execute(httpGet);
        logger.info("engine/controllers/nonexistantcontroller/decoders/filters response code: {}",response.getStatusLine().getStatusCode());
        assertEquals(404, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        
        httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/decoders/topic");
        response = client.execute(httpGet);      
        logger.info("/engine/controllers/controllers/decoders/topics response code: {}",response.getStatusLine().getStatusCode());
        assertEquals(404, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        
        httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/decoders/topic/filters");
        response = client.execute(httpGet);      
        logger.info("/engine/controllers/controllers/decoders/topic/filters response code: {}",response.getStatusLine().getStatusCode());
        assertEquals(404, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/decoders/topic/filters/factType");
        response = client.execute(httpGet);      
        logger.info("/engine/controllers/controller/decoders/topic/filters/factType response code: {}",response.getStatusLine().getStatusCode());
        assertEquals(404, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
  
        httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/decoders/topic/filters/factType/rules");
        response = client.execute(httpGet);      
        logger.info("/engine/controllers/controllers/decoders/topic/filters/factType/rules response code: {}",response.getStatusLine().getStatusCode());
        assertEquals(404, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/decoders/topic/filters/factType/rules/ruleName");
        response = client.execute(httpGet);      
        logger.info("/engine/controllers/controllers/decoders/topic/filters/factType/rules/ruleName response code: {}",response.getStatusLine().getStatusCode());
        assertEquals(404, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/encoders");
        response = client.execute(httpGet);
        logger.info("/engine/controllers/controller/encoders response code: {}",response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        
        httpGet = new HttpGet(HOST_URL + "/engine/topics");
        response = client.execute(httpGet);
        logger.info("/engine/topics response code: {}",response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/topics/switches");
        response = client.execute(httpGet);
        logger.info("/engine/topics/switches response code: {}",response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/topics/sources");
        response = client.execute(httpGet);
        logger.info("/engine/topics/sources response code: {}",response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks");
        response = client.execute(httpGet);
        logger.info("/engine/topics/sinks response code: {}",response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks/ueb");
        response = client.execute(httpGet);
        logger.info("/engine/topics/sinks/ueb response code: {}",response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/topics/sources/ueb");
        response = client.execute(httpGet);
        logger.info("/engine/topics/sources/ueb response code: {}",response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/topics/sources/dmaap");
        response = client.execute(httpGet);
        logger.info("/engine/topics/sources/dmaap response code: {}",response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks/dmaap");
        response = client.execute(httpGet);
        logger.info("/engine/topics/sinks/dmaap response code: {}",response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/topics/sources/ueb/" + UEB_TOPIC);
        response = client.execute(httpGet);
        logger.info("engine/topics/sources/ueb/{} response code: {}", UEB_TOPIC,response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/topics/sources/ueb/foobar");
        response = client.execute(httpGet);
        logger.info("engine/topics/sources/ueb/foobar response code: {}", response.getStatusLine().getStatusCode());
        assertEquals(500, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks/ueb/" + UEB_TOPIC);
        response = client.execute(httpGet);
        logger.info("engine/topics/sources/ueb/{} response code: {}", UEB_TOPIC,response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks/ueb/foobar");
        response = client.execute(httpGet);
        logger.info("engine/topics/sinks/ueb/foobar response code: {}", response.getStatusLine().getStatusCode());
        assertEquals(500, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/topics/sources/dmaap/" + DMAAP_TOPIC);
        response = client.execute(httpGet);
        logger.info("engine/topics/sources/dmaap/{} response code: {}", DMAAP_TOPIC,response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/topics/sources/dmaap/foobar");
        response = client.execute(httpGet);
        logger.info("engine/topics/sources/dmaap/foobar response code: {}", response.getStatusLine().getStatusCode());
        assertEquals(500, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks/dmaap/" + DMAAP_TOPIC);
        response = client.execute(httpGet);
        logger.info("engine/topics/sources/dmaap/{} response code: {}", DMAAP_TOPIC,response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks/dmaap/foobar");
        response = client.execute(httpGet);
        logger.info("engine/topics/sinks/dmaap/foobar response code: {}", response.getStatusLine().getStatusCode());
        assertEquals(500, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/topics/sources/ueb/" + UEB_TOPIC + "/events");
        response = client.execute(httpGet);
        logger.info("engine/topics/sources/ueb/{}/events response code: {}", UEB_TOPIC,response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/topics/sources/ueb/foobar/events");
        response = client.execute(httpGet);
        logger.info("engine/topics/sources/ueb/foobar/events response code: {}", response.getStatusLine().getStatusCode());
        assertEquals(500, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks/ueb/" + UEB_TOPIC + "/events");
        response = client.execute(httpGet);
        logger.info("engine/topics/sinks/ueb/{}/events response code: {}", UEB_TOPIC,response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks/ueb/foobar/events");
        response = client.execute(httpGet);
        logger.info("engine/topics/sinks/ueb/foobar/events response code: {}", response.getStatusLine().getStatusCode());
        assertEquals(500, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/topics/sources/dmaap/" + DMAAP_TOPIC + "/events");
        response = client.execute(httpGet);
        logger.info("engine/topics/sources/dmaap/{}/events response code: {}", DMAAP_TOPIC,response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/topics/sources/dmaap/foobar/events");
        response = client.execute(httpGet);
        logger.info("engine/topics/sources/dmaap/foobar/events response code: {}", response.getStatusLine().getStatusCode());
        assertEquals(500, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks/dmaap/" + DMAAP_TOPIC + "/events");
        response = client.execute(httpGet);
        logger.info("engine/topics/sinks/dmaap/{}/events response code: {}", DMAAP_TOPIC,response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();

        httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks/dmaap/foobar/events");
        response = client.execute(httpGet);
        logger.info("engine/topics/sinks/dmaap/foobar/events response code: {}", response.getStatusLine().getStatusCode());
        assertEquals(500, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks/noop");
        response = client.execute(httpGet);
        logger.info("engine/topics/sinks/noop response code: {}", response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks/noop/" + NOOP_TOPIC);
        response = client.execute(httpGet);
        logger.info("engine/topics/sinks/noop/{} response code: {}", NOOP_TOPIC, response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks/noop/" + NOOP_TOPIC + "/events");
        response = client.execute(httpGet);
        logger.info("engine/topics/sinks/noop/{}/events response code: {}", NOOP_TOPIC, response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/topics/sources/ueb/" + UEB_TOPIC + "/switches");
        response = client.execute(httpGet);
        logger.info("engine/topics/sources/ueb/{}/switches response code: {}", UEB_TOPIC, response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/topics/sources/dmaap/" + DMAAP_TOPIC + "/switches");
        response = client.execute(httpGet);
        logger.info("engine/topics/sources/dmaap/{}/switches response code: {}", DMAAP_TOPIC, response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/tools/uuid");
        response = client.execute(httpGet);
        logger.info("engine/tools/uuid response code: {}", response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/tools/loggers");
        response = client.execute(httpGet);
        logger.info("engine/tools/loggers response code: {}", response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
        httpGet = new HttpGet(HOST_URL + "/engine/tools/loggers/ROOT");
        response = client.execute(httpGet);
        logger.info("engine/tools/loggers/ROOT response code: {}", response.getStatusLine().getStatusCode());
        assertEquals(200, response.getStatusLine().getStatusCode());
        httpGet.releaseConnection();
        
    }


    public String getResponseBody(CloseableHttpResponse response) {
        
        HttpEntity entity;
        try {
            entity = response.getEntity();      
            return EntityUtils.toString(entity);
            
        } catch (IOException e) {
        
        }
        
        return null;
    }
 
}
