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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.onap.policy.drools.event.comm.TopicEndpoint;
import org.onap.policy.drools.persistence.SystemPersistence;
import org.onap.policy.drools.properties.PolicyProperties;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.drools.system.PolicyEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class RestManagerTest {
  public static final int DEFAULT_TELEMETRY_PORT = 7887;
  private static final String HOST = "localhost";
  private static final String REST_MANAGER_PATH = "/policy/pdp";
  private static final String HOST_URL =
      "http://" + HOST + ":" + DEFAULT_TELEMETRY_PORT + REST_MANAGER_PATH;
  private static final String FOO_CONTROLLER = "foo";

  private static final String UEB_TOPIC = "UEB-TOPIC-TEST";
  private static final String DMAAP_TOPIC = "DMAAP-TOPIC-TEST";
  private static final String NOOP_TOPIC = "NOOP_TOPIC";

  private static final String UEB_SOURCE_SERVER_PROPERTY =
      PolicyProperties.PROPERTY_UEB_SOURCE_TOPICS + "." + UEB_TOPIC
          + PolicyProperties.PROPERTY_TOPIC_SERVERS_SUFFIX;
  private static final String UEB_SINK_SERVER_PROPERTY = PolicyProperties.PROPERTY_UEB_SINK_TOPICS
      + "." + UEB_TOPIC + PolicyProperties.PROPERTY_TOPIC_SERVERS_SUFFIX;
  private static final String DMAAP_SOURCE_SERVER_PROPERTY =
      PolicyProperties.PROPERTY_DMAAP_SOURCE_TOPICS + "." + DMAAP_TOPIC
          + PolicyProperties.PROPERTY_TOPIC_SERVERS_SUFFIX;
  private static final String DMAAP_SINK_SERVER_PROPERTY =
      PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS + "." + DMAAP_TOPIC
          + PolicyProperties.PROPERTY_TOPIC_SERVERS_SUFFIX;
  private static final String UEB_SERVER = "localhost";
  private static final String DMAAP_SERVER = "localhost";
  private static final String DMAAP_MECHID = "blah";
  private static final String DMAAP_PASSWD = "blah";

  private static final String DMAAP_SOURCE_MECHID_KEY =
      PolicyProperties.PROPERTY_DMAAP_SOURCE_TOPICS + "." + DMAAP_TOPIC
          + PolicyProperties.PROPERTY_TOPIC_AAF_MECHID_SUFFIX;
  private static final String DMAAP_SOURCE_PASSWD_KEY =
      PolicyProperties.PROPERTY_DMAAP_SOURCE_TOPICS + "." + DMAAP_TOPIC
          + PolicyProperties.PROPERTY_TOPIC_AAF_PASSWORD_SUFFIX;

  private static final String DMAAP_SINK_MECHID_KEY = PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS
      + "." + DMAAP_TOPIC + PolicyProperties.PROPERTY_TOPIC_AAF_MECHID_SUFFIX;
  private static final String DMAAP_SINK_PASSWD_KEY = PolicyProperties.PROPERTY_DMAAP_SINK_TOPICS
      + "." + DMAAP_TOPIC + PolicyProperties.PROPERTY_TOPIC_AAF_PASSWORD_SUFFIX;


  private static final String FOO_CONTROLLER_FILE = FOO_CONTROLLER + "-controller.properties";
  private static final String FOO_CONTROLLER_FILE_BAK = FOO_CONTROLLER_FILE + ".bak";

  private static CloseableHttpClient client;

  private static final Logger logger = LoggerFactory.getLogger(RestManagerTest.class);

  @BeforeClass
  public static void setUp() throws IOException {
    cleanUpWorkingDirs();

    SystemPersistence.manager.setConfigurationDir(null);

    /* override default port */
    final Properties engineProps = PolicyEngine.manager.defaultTelemetryConfig();
    engineProps.put(PolicyProperties.PROPERTY_HTTP_SERVER_SERVICES + "."
        + PolicyEngine.TELEMETRY_SERVER_DEFAULT_NAME + PolicyProperties.PROPERTY_HTTP_PORT_SUFFIX,
        "" + DEFAULT_TELEMETRY_PORT);

    /* other properties */
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
    
    Properties controllerProps = new Properties();
    PolicyEngine.manager.createPolicyController(FOO_CONTROLLER, controllerProps);

    
    client = HttpClients.createDefault();
    

  }

  @AfterClass
  public static void tearDown() throws IOException, InterruptedException {
	CountDownLatch latch = new CountDownLatch(1);
		
	 /* Shutdown managed resources */
    PolicyController.factory.shutdown();
    TopicEndpoint.manager.shutdown();
    PolicyEngine.manager.stop();
	latch.await(10000L, TimeUnit.MILLISECONDS);
    client.close();
    cleanUpWorkingDirs();
  }
  
 
  @Test
  public void putDeleteTest() throws ClientProtocolException, IOException, InterruptedException {
    HttpPut httpPut;
    HttpDelete httpDelete;
    CloseableHttpResponse response;
    
    /*
     * DELETE:
     *      /engine/controllers/controllerName/drools/facts/session/factType
     *      
     */
    httpDelete = new HttpDelete(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/drools/facts/session/factType");
    response = client.execute(httpDelete);
    logger.info(httpDelete.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpDelete.releaseConnection();
    
    httpDelete = new HttpDelete(HOST_URL + "/engine/controllers/controllerName/drools/facts/session/factType");
    response = client.execute(httpDelete);
    logger.info(httpDelete.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(404, response.getStatusLine().getStatusCode());
    httpDelete.releaseConnection();
    
    /*
     * PUT:
     *      /engine/switches/lock
     *      /engine/controllers/controllername/switches/lock
     * DELETE:
     *      /engine/switches/lock
     *      /engine/controllers/controllername  
     */
    httpPut = new HttpPut(HOST_URL + "/engine/switches/lock");
    response = client.execute(httpPut);
    logger.info(httpPut.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(406, response.getStatusLine().getStatusCode());
    httpPut.releaseConnection();
    
    httpDelete = new HttpDelete(HOST_URL + "/engine/switches/lock");
    response = client.execute(httpDelete);
    logger.info(httpDelete.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(406, response.getStatusLine().getStatusCode());
    httpDelete.releaseConnection();
    
    httpDelete = new HttpDelete(HOST_URL + "/engine/controllers/");
    response = client.execute(httpDelete);
    logger.info(httpDelete.getRequestLine() + " response code: {}",
            response.getStatusLine().getStatusCode());
    assertEquals(405, response.getStatusLine().getStatusCode());
    httpDelete.releaseConnection();
        
    httpDelete = new HttpDelete(HOST_URL + "/engine/controllers/" + null);
    response = client.execute(httpDelete);
    logger.info(httpDelete.getRequestLine() + " response code: {}",
    response.getStatusLine().getStatusCode());
    assertEquals(400, response.getStatusLine().getStatusCode());
    httpDelete.releaseConnection();
    
    httpPut = new HttpPut(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/switches/lock");
    response = client.execute(httpPut);
    logger.info(httpPut.getRequestLine() +" response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(406, response.getStatusLine().getStatusCode());
    httpPut.releaseConnection();
         
    httpDelete = new HttpDelete(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER);
    response = client.execute(httpDelete);
    logger.info(httpDelete.getRequestLine() + " response code: {}",
    response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpDelete.releaseConnection();
  
    httpPut = new HttpPut(HOST_URL + "/engine/switches/lock");
    response = client.execute(httpPut);
    logger.info(httpPut.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpPut.releaseConnection();
    
    httpDelete = new HttpDelete(HOST_URL + "/engine/switches/lock");
    response = client.execute(httpDelete);
    logger.info(httpDelete.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpDelete.releaseConnection();
 
    /*
     * PUT:
     *      /engine/topics/sources/ueb/topic/events
     *      /engine/topics/sources/dmaap/topic/events
     *      /engine/topics/switches/lock
     * DELETE:
     *      /engine/topics/switches/lock
     */
    httpPut = new HttpPut(HOST_URL + "/engine/topics/sources/ueb/" + UEB_TOPIC + "/events");
    httpPut.addHeader("Content-Type", "text/plain");
    httpPut.addHeader("Accept", "application/json");
    httpPut.setEntity(new StringEntity("FOOOO"));
    response = client.execute(httpPut);
    logger.info(httpPut.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpPut.releaseConnection();
    
    httpPut = new HttpPut(HOST_URL + "/engine/topics/sources/dmaap/" + DMAAP_TOPIC + "/events");
    httpPut.addHeader("Content-Type", "text/plain");
    httpPut.addHeader("Accept", "application/json");
    httpPut.setEntity(new StringEntity("FOOOO"));
    response = client.execute(httpPut);
    logger.info(httpPut.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpPut.releaseConnection();
    
    httpPut = new HttpPut(HOST_URL + "/engine/topics/sources/ueb/fiznits/events");
    httpPut.addHeader("Content-Type", "text/plain");
    httpPut.addHeader("Accept", "application/json");
    httpPut.setEntity(new StringEntity("FOOOO"));
    response = client.execute(httpPut);
    logger.info(httpPut.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(406, response.getStatusLine().getStatusCode());
    httpPut.releaseConnection();
    
    httpPut = new HttpPut(HOST_URL + "/engine/topics/sources/dmaap/fiznits/events");
    httpPut.addHeader("Content-Type", "text/plain");
    httpPut.addHeader("Accept", "application/json");
    httpPut.setEntity(new StringEntity("FOOOO"));
    response = client.execute(httpPut);
    logger.info(httpPut.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(404, response.getStatusLine().getStatusCode());
    httpPut.releaseConnection();
    
    httpPut = new HttpPut(HOST_URL + "/engine/topics/switches/lock");
    response = client.execute(httpPut);
    logger.info(httpPut.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpPut.releaseConnection();

    httpPut = new HttpPut(HOST_URL + "/engine/topics/sources/ueb/" + UEB_TOPIC + "/events");
    httpPut.addHeader("Content-Type", "text/plain");
    httpPut.addHeader("Accept", "application/json");
    httpPut.setEntity(new StringEntity("FOOOO"));
    response = client.execute(httpPut);
    logger.info(httpPut.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(406, response.getStatusLine().getStatusCode());
    httpPut.releaseConnection();
    
    httpPut = new HttpPut(HOST_URL + "/engine/topics/sources/dmaap/" + DMAAP_TOPIC + "/events");
    httpPut.addHeader("Content-Type", "text/plain");
    httpPut.addHeader("Accept", "application/json");
    httpPut.setEntity(new StringEntity("FOOOO"));
    response = client.execute(httpPut);
    logger.info(httpPut.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(406, response.getStatusLine().getStatusCode());
    httpPut.releaseConnection();

    httpDelete = new HttpDelete(HOST_URL + "/engine/topics/switches/lock");
    response = client.execute(httpDelete);
    logger.info(httpDelete.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpDelete.releaseConnection();
    
    /*
     * PUT:
     *      /engine/topics/sources/ueb/topic/switches/lock
     *      /engine/topics/sources/dmaap/topic/switches/lock
     * DELETE:
     *      /engine/topics/sources/ueb/topic/switches/lock
     *      /engine/topics/sources/dmaap/topic/switches/lock
     */
    httpPut = new HttpPut(HOST_URL + "/engine/topics/sources/ueb/" + UEB_TOPIC + "/switches/lock");
    response = client.execute(httpPut);
    logger.info(httpPut.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpPut.releaseConnection();

    httpDelete =
        new HttpDelete(HOST_URL + "/engine/topics/sources/ueb/" + UEB_TOPIC + "/switches/lock");
    response = client.execute(httpDelete);
    logger.info(httpDelete.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpDelete.releaseConnection();

    httpPut =
        new HttpPut(HOST_URL + "/engine/topics/sources/dmaap/" + DMAAP_TOPIC + "/switches/lock");
    response = client.execute(httpPut);
    logger.info(httpPut.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpPut.releaseConnection();

    httpDelete =
        new HttpDelete(HOST_URL + "/engine/topics/sources/dmaap/" + DMAAP_TOPIC + "/switches/lock");
    response = client.execute(httpDelete);
    logger.info(httpDelete.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpDelete.releaseConnection();

    /*
     * PUT:
     *     /engine/switches/activation
     * DELETE:
     *     /engine/switches/activation 
     */
   /* httpPut = new HttpPut(HOST_URL + "/engine/switches/activation");
    response = client.execute(httpPut);
    logger.info(httpPut.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpPut.releaseConnection();

    httpDelete = new HttpDelete(HOST_URL + "/engine/switches/activation");
    response = client.execute(httpDelete);
    logger.info(httpDelete.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpDelete.releaseConnection();*/
    
    /*
     * PUT:
     *      /engine/tools/loggers/logger/level
     */
    httpPut = new HttpPut(HOST_URL + "/engine/tools/loggers/ROOT/debug");
    response = client.execute(httpPut);
    logger.info(httpPut.getRequestLine() + "response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpPut.releaseConnection();
    
  }
  
  
  @Test
  public void getTest() throws ClientProtocolException, IOException, InterruptedException {
    HttpGet httpGet;
    CloseableHttpResponse response;
    String responseBody;

    /*
     * GET:
     *      /engine
     *      /engine/features
     *      /engine/features/inventory
     *      /engine/features/featurename
     *      /engine/inputs
     *      /engine/properties
     *      /engine/environment
     *      /engine/switches
     *      /engine/controllers
     */ 
    httpGet = new HttpGet(HOST_URL + "/engine");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}", response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/features");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}", response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/features/inventory");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/features/foobar");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(404, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/inputs");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}", response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/properties");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}", response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/environment");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}", response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    PolicyEngine.manager.setEnvironmentProperty("foo", "bar");
    httpGet = new HttpGet(HOST_URL + "/engine/environment/foo");
    response = client.execute(httpGet);
    responseBody = this.getResponseBody(response);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    logger.info(httpGet.getRequestLine() + " response body: {}", responseBody);
    assertEquals(200, response.getStatusLine().getStatusCode());
    assertEquals("bar", responseBody);
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/switches");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}", response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/controllers");
    response = client.execute(httpGet);
    responseBody = this.getResponseBody(response);
    logger.info(httpGet.getRequestLine() + " response code: {}", response.getStatusLine().getStatusCode());
    logger.info(httpGet.getRequestLine() + " response body: {}", responseBody);
    assertEquals(200, response.getStatusLine().getStatusCode());
    assertEquals("[\"" + FOO_CONTROLLER + "\"]", responseBody);
    httpGet.releaseConnection();

    /*
     * GET:
     *      /engine/controllers/inventory
     *      /engine/controllers/features
     *      /engine/controllers/features/inventory
     *      /engine/controllers/features/featureName
     *      /engine/controllers/controllerName
     *      
     */
    httpGet = new HttpGet(HOST_URL + "/engine/controllers/inventory");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/controllers/features");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/controllers/features/inventory");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/controllers/features/dummy");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(404, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER);
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}", response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/controllers/nonexistantcontroller");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(404, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    /*
     * GET:
     *      /engine/controllers/controllerName/properties
     *      /engine/controllers/controllerName/inputs
     *      /engine/controllers/controllerName/switches
     *      /engine/controllers/controllerName/drools
     */
    httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/properties");
    response = client.execute(httpGet);
    responseBody = this.getResponseBody(response);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    logger.info(httpGet.getRequestLine() + " response code: {}", responseBody);
    assertEquals(200, response.getStatusLine().getStatusCode());
    assertEquals("{}", responseBody);
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/controllers/nonexistantcontroller/properties");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(404, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/inputs");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/switches");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/drools");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/controllers/nonexistantcontroller/drools");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(404, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    /*
     * GET:
     *      /engine/controllers/controllerName/drools/facts
     *      /engine/controllers/controllerName/drools/facts/session
     *      /engine/controllers/controllerName/drools/facts/session/factType
     *      /engine/controllers/controllerName/drools/facts/session/query/queriedEntity
     *      
     */
    httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/drools/facts");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/controllers/nonexistantcontroller/drools/facts");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(404, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet =
        new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/drools/facts/session");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(
        HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/drools/facts/session/factType");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER
        + "/drools/facts/session/query/queriedEntity");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();
    
    httpGet = new HttpGet(HOST_URL + "/engine/controllers/dummy"
            + "/drools/facts/session/query/queriedEntity");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
            response.getStatusLine().getStatusCode());
    assertEquals(404, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();


    /*
     * GET:
     *      /engine/controllers/controllerName/decoders
     *      /engine/controllers/controllerName/decoders/filters
     *      /engine/controllers/controllerName/decoders/topic
     *      /engine/controllers/controllerName/decoders/topic/filters
     *      /engine/controllers/controllerName/decoders/topic/filters/factType
     *      /engine/controllers/controllerName/decoders/topic/filters/factType/rules
     *      /engine/controllers/controllerName/decoders/topic/filtes/factType/rules/ruleName
     *      /engine/controllers/controllerName/encoders
     */
    httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/decoders");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/controllers/nonexistantcontroller/decoders");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(404, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/decoders/filters");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/controllers/nonexistantcontroller/decoders/filters");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(404, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/decoders/topic");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(404, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet =
        new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/decoders/topic/filters");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(404, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(
        HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/decoders/topic/filters/factType");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(404, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER
        + "/decoders/topic/filters/factType/rules");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(404, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER
        + "/decoders/topic/filters/factType/rules/ruleName");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(404, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/controllers/" + FOO_CONTROLLER + "/encoders");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    /*
     * GET:
     *      /engine/topics
     *      /engine/topics/switches
     *      /engine/topics/sources
     *      /engine/topics/sinks
     *      /engine/topics/sinks/ueb
     *      /engine/topics/sources/ueb
     *      /engine/topics/sinks/dmaap
     *      /engine/topics/sources/dmaap
     *      /engine/topics/sinks/ueb/topic
     *      /engine/topics/sources/ueb/topic
     *      /engine/topics/sinks/dmaap/topic
     *      /engine/topics/sources/dmaap/topic
     *      /engine/topics/sinks/ueb/topic/events
     *      /engine/topics/sources/ueb/topic/events
     *      /engine/topics/sinks/dmaap/topic/events
     *      /engine/topics/sources/dmaap/topic/events
     *      /engine/topics/sources/ueb/topic/switches
     *      /engine/topics/sources/dmaap/topic/switches
     */
    httpGet = new HttpGet(HOST_URL + "/engine/topics");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}", response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/topics/switches");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/topics/sources");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}", response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks/ueb");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/topics/sources/ueb");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/topics/sources/dmaap");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks/dmaap");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/topics/sources/ueb/" + UEB_TOPIC);
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/topics/sources/ueb/foobar");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(500, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks/ueb/" + UEB_TOPIC);
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks/ueb/foobar");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(500, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/topics/sources/dmaap/" + DMAAP_TOPIC);
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/topics/sources/dmaap/foobar");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(500, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks/dmaap/" + DMAAP_TOPIC);
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks/dmaap/foobar");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(500, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/topics/sources/ueb/" + UEB_TOPIC + "/events");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/topics/sources/ueb/foobar/events");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(500, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks/ueb/" + UEB_TOPIC + "/events");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks/ueb/foobar/events");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(500, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/topics/sources/dmaap/" + DMAAP_TOPIC + "/events");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/topics/sources/dmaap/foobar/events");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(500, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks/dmaap/" + DMAAP_TOPIC + "/events");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks/dmaap/foobar/events");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(500, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();
    
    httpGet = new HttpGet(HOST_URL + "/engine/topics/sources/ueb/" + UEB_TOPIC + "/switches");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/topics/sources/dmaap/" + DMAAP_TOPIC + "/switches");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    /*
     * GET:
     *      /engine/topics/sinks/noop
     *      /engine/topics/sinks/noop/topic
     *      /engine/topics/sinks/noop/topic/events
     */
    httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks/noop");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks/noop/" + NOOP_TOPIC);
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/topics/sinks/noop/" + NOOP_TOPIC + "/events");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    /*
     * GET:
     *      /engine/tools/uuid
     *      /engine/tools/loggers
     *      /engine/tools/loggers/loggerName
     */
    httpGet = new HttpGet(HOST_URL + "/engine/tools/uuid");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}", response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/tools/loggers");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}", response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();

    httpGet = new HttpGet(HOST_URL + "/engine/tools/loggers/ROOT");
    response = client.execute(httpGet);
    logger.info(httpGet.getRequestLine() + " response code: {}",
        response.getStatusLine().getStatusCode());
    assertEquals(200, response.getStatusLine().getStatusCode());
    httpGet.releaseConnection();
  
  }


  public String getResponseBody(CloseableHttpResponse response) {

    HttpEntity entity;
    try {
      entity = response.getEntity();
      return EntityUtils.toString(entity);

    } catch (final IOException e) {

    }

    return null;
  }

  private static void cleanUpWorkingDirs() throws IOException {
    final Path testControllerPath =
        Paths.get(SystemPersistence.manager.getConfigurationPath().toString(), FOO_CONTROLLER_FILE);
    final Path testControllerBakPath = Paths
        .get(SystemPersistence.manager.getConfigurationPath().toString(), FOO_CONTROLLER_FILE_BAK);

    Files.deleteIfExists(testControllerPath);
    Files.deleteIfExists(testControllerBakPath);
  }

}
