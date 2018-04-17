/*-
 * ============LICENSE_START=======================================================
 * policy-endpoints
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

package org.onap.policy.drools.http.server.test;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import javax.ws.rs.core.Response;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.onap.policy.drools.http.client.HttpClient;
import org.onap.policy.drools.http.server.HttpServletServer;
import org.onap.policy.drools.properties.PolicyProperties;
import org.onap.policy.drools.utils.NetworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpClientTest {

  private static Logger logger = LoggerFactory.getLogger(HttpClientTest.class);

  @BeforeClass
  public static void setUp() throws InterruptedException, IOException {
    logger.info("-- setup() --");

    /* echo server */

    final HttpServletServer echoServerNoAuth =
        HttpServletServer.factory.build("echo", "localhost", 6666, "/", false, true);
    echoServerNoAuth.addServletPackage("/*", HttpClientTest.class.getPackage().getName());
    echoServerNoAuth.waitedStart(5000);

    if (!NetworkUtil.isTcpPortOpen("localhost", echoServerNoAuth.getPort(), 5, 10000L))
      throw new IllegalStateException("cannot connect to port " + echoServerNoAuth.getPort());

    /* no auth echo server */

    final HttpServletServer echoServerAuth =
        HttpServletServer.factory.build("echo", "localhost", 6667, "/", false, true);
    echoServerAuth.setBasicAuthentication("x", "y", null);
    echoServerAuth.addServletPackage("/*", HttpClientTest.class.getPackage().getName());
    echoServerAuth.waitedStart(5000);

    if (!NetworkUtil.isTcpPortOpen("localhost", echoServerAuth.getPort(), 5, 10000L))
      throw new IllegalStateException("cannot connect to port " + echoServerAuth.getPort());
  }

  @AfterClass
  public static void tearDown() {
    logger.info("-- tearDown() --");

    HttpServletServer.factory.destroy();
    HttpClient.factory.destroy();
  }

  @Test
  public void testHttpNoAuthClient() throws Exception {
    logger.info("-- testHttpNoAuthClient() --");

    final HttpClient client = HttpClient.factory.build("testHttpNoAuthClient", false, false,
        "localhost", 6666, "junit/echo", null, null, true);
    final Response response = client.get("hello");
    final String body = HttpClient.getBody(response, String.class);

    assertTrue(response.getStatus() == 200);
    assertTrue(body.equals("hello"));
  }

  @Test
  public void testHttpAuthClient() throws Exception {
    logger.info("-- testHttpAuthClient() --");

    final HttpClient client = HttpClient.factory.build("testHttpAuthClient", false, false,
        "localhost", 6667, "junit/echo", "x", "y", true);
    final Response response = client.get("hello");
    final String body = HttpClient.getBody(response, String.class);

    assertTrue(response.getStatus() == 200);
    assertTrue(body.equals("hello"));
  }

  @Test
  public void testHttpAuthClient401() throws Exception {
    logger.info("-- testHttpAuthClient401() --");

    final HttpClient client = HttpClient.factory.build("testHttpAuthClient401", false, false,
        "localhost", 6667, "junit/echo", null, null, true);
    final Response response = client.get("hello");
    assertTrue(response.getStatus() == 401);
  }

  @Test
  public void testHttpAuthClientProps() throws Exception {
    logger.info("-- testHttpAuthClientProps() --");

    final Properties httpProperties = new Properties();

    httpProperties.setProperty(PolicyProperties.PROPERTY_HTTP_SERVER_SERVICES, "PAP,PDP");
    httpProperties.setProperty(PolicyProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + "PAP"
        + PolicyProperties.PROPERTY_HTTP_HOST_SUFFIX, "localhost");
    httpProperties.setProperty(PolicyProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + "PAP"
        + PolicyProperties.PROPERTY_HTTP_PORT_SUFFIX, "7777");
    httpProperties.setProperty(PolicyProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + "PAP"
        + PolicyProperties.PROPERTY_HTTP_AUTH_USERNAME_SUFFIX, "testpap");
    httpProperties.setProperty(PolicyProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + "PAP"
        + PolicyProperties.PROPERTY_HTTP_AUTH_PASSWORD_SUFFIX, "alpha123");
    httpProperties.setProperty(
        PolicyProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + "PAP"
            + PolicyProperties.PROPERTY_HTTP_REST_CLASSES_SUFFIX,
        RestMockHealthCheck.class.getName());
    httpProperties.setProperty(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "PAP"
        + PolicyProperties.PROPERTY_MANAGED_SUFFIX, "true");

    httpProperties.setProperty(PolicyProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + "PDP"
        + PolicyProperties.PROPERTY_HTTP_HOST_SUFFIX, "localhost");
    httpProperties.setProperty(PolicyProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + "PDP"
        + PolicyProperties.PROPERTY_HTTP_PORT_SUFFIX, "7778");
    httpProperties.setProperty(PolicyProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + "PDP"
        + PolicyProperties.PROPERTY_HTTP_AUTH_USERNAME_SUFFIX, "testpdp");
    httpProperties.setProperty(PolicyProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + "PDP"
        + PolicyProperties.PROPERTY_HTTP_AUTH_PASSWORD_SUFFIX, "alpha123");
    httpProperties.setProperty(
        PolicyProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + "PDP"
            + PolicyProperties.PROPERTY_HTTP_REST_CLASSES_SUFFIX,
        RestMockHealthCheck.class.getName());
    httpProperties.setProperty(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "PAP"
        + PolicyProperties.PROPERTY_MANAGED_SUFFIX, "true");

    httpProperties.setProperty(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES, "PAP,PDP");
    httpProperties.setProperty(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "PAP"
        + PolicyProperties.PROPERTY_HTTP_HOST_SUFFIX, "localhost");
    httpProperties.setProperty(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "PAP"
        + PolicyProperties.PROPERTY_HTTP_PORT_SUFFIX, "7777");
    httpProperties.setProperty(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "PAP"
        + PolicyProperties.PROPERTY_HTTP_URL_SUFFIX, "pap/test");
    httpProperties.setProperty(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "PAP"
        + PolicyProperties.PROPERTY_HTTP_HTTPS_SUFFIX, "false");
    httpProperties.setProperty(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "PAP"
        + PolicyProperties.PROPERTY_HTTP_AUTH_USERNAME_SUFFIX, "testpap");
    httpProperties.setProperty(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "PAP"
        + PolicyProperties.PROPERTY_HTTP_AUTH_PASSWORD_SUFFIX, "alpha123");
    httpProperties.setProperty(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "PAP"
        + PolicyProperties.PROPERTY_MANAGED_SUFFIX, "true");

    httpProperties.setProperty(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "PDP"
        + PolicyProperties.PROPERTY_HTTP_HOST_SUFFIX, "localhost");
    httpProperties.setProperty(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "PDP"
        + PolicyProperties.PROPERTY_HTTP_PORT_SUFFIX, "7778");
    httpProperties.setProperty(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "PDP"
        + PolicyProperties.PROPERTY_HTTP_URL_SUFFIX, "pdp");
    httpProperties.setProperty(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "PDP"
        + PolicyProperties.PROPERTY_HTTP_HTTPS_SUFFIX, "false");
    httpProperties.setProperty(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "PDP"
        + PolicyProperties.PROPERTY_HTTP_AUTH_USERNAME_SUFFIX, "testpdp");
    httpProperties.setProperty(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "PDP"
        + PolicyProperties.PROPERTY_HTTP_AUTH_PASSWORD_SUFFIX, "alpha123");
    httpProperties.setProperty(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "PDP"
        + PolicyProperties.PROPERTY_MANAGED_SUFFIX, "true");

    final List<HttpServletServer> servers = HttpServletServer.factory.build(httpProperties);
    assertTrue(servers.size() == 2);

    final List<HttpClient> clients = HttpClient.factory.build(httpProperties);
    assertTrue(clients.size() == 2);

    for (final HttpServletServer server : servers) {
      server.waitedStart(10000);
    }

    final HttpClient clientPAP = HttpClient.factory.get("PAP");
    final Response response = clientPAP.get();
    assertTrue(response.getStatus() == 200);

    final HttpClient clientPDP = HttpClient.factory.get("PDP");
    final Response response2 = clientPDP.get("test");
    assertTrue(response2.getStatus() == 500);
  }


}
