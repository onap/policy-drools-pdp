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
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

public class RestManagerTest {
    

    public static final int DEFAULT_TELEMETRY_PORT = 9698;
    private static final String HOST = "localhost";
    private static final String REST_MANAGER_PATH = "/policy/pdp";
    private static final String HOST_URL = "http://" + HOST + ":" + DEFAULT_TELEMETRY_PORT + REST_MANAGER_PATH;
    private static final String FOO_CONTROLLER = "foo-controller";
    private static CloseableHttpClient client;
    
    private static final Logger logger = LoggerFactory.getLogger(RestManagerTest.class);
    
    @BeforeClass
    public static void setUp() {
        /* override default port */
        final Properties engineProps = PolicyEngine.manager.defaultTelemetryConfig();
        engineProps.put(PolicyProperties.PROPERTY_HTTP_SERVER_SERVICES + "."
                + PolicyEngine.TELEMETRY_SERVER_DEFAULT_NAME + PolicyProperties.PROPERTY_HTTP_PORT_SUFFIX,
                "" + DEFAULT_TELEMETRY_PORT);
            PolicyEngine.manager.configure(engineProps);
            PolicyEngine.manager.start();
    
        client = HttpClients.createDefault();
        
    }
    
    @AfterClass
    public static void tearDown() {
        PolicyEngine.manager.shutdown();
 
    }
    
    
    @Test
    public void GETTest() throws ClientProtocolException, IOException, InterruptedException {
        assertTrue(PolicyEngine.manager.isAlive());
        
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
