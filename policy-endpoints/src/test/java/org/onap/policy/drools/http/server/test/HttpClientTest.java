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

import java.util.ArrayList;
import java.util.Properties;

import javax.ws.rs.core.Response;

import org.junit.Test;
import org.onap.policy.drools.http.client.HttpClient;
import org.onap.policy.drools.http.server.HttpServletServer;
import org.onap.policy.drools.properties.PolicyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpClientTest {
	
	private static Logger logger = LoggerFactory.getLogger(HttpClientTest.class);
	
	@Test
	public void testHttpNoAuthClient() throws Exception {		
		logger.info("-- testHttpNoAuthClient() --");

		HttpServletServer server = HttpServletServer.factory.build("echo", "localhost", 6666, "/", false, true);
		server.addServletPackage("/*", this.getClass().getPackage().getName());
		server.waitedStart(5000);
		
		HttpClient client = HttpClient.factory.build("testHttpNoAuthClient", false, false, 
				                                     "localhost", 6666, "junit/echo", 
				                                     null, null, true);
		Response response = client.get("hello");
		String body = HttpClient.getBody(response, String.class);
		
		assertTrue(response.getStatus() == 200);
		assertTrue(body.equals("hello"));
		
		HttpServletServer.factory.destroy();
		HttpClient.factory.destroy();
	}
	
	@Test
	public void testHttpAuthClient() throws Exception {		
		logger.info("-- testHttpAuthClient() --");

		HttpServletServer server = HttpServletServer.factory.build("echo", "localhost", 6666, "/", false, true);
		server.setBasicAuthentication("x", "y", null);
		server.addServletPackage("/*", this.getClass().getPackage().getName());
		server.waitedStart(5000);
		
		HttpClient client = HttpClient.factory.build("testHttpAuthClient",false, false, 
				                                     "localhost", 6666, "junit/echo", 
				                                     "x", "y", true);
		Response response = client.get("hello");
		String body = HttpClient.getBody(response, String.class);
		
		assertTrue(response.getStatus() == 200);
		assertTrue(body.equals("hello"));
		
		HttpServletServer.factory.destroy();
		HttpClient.factory.destroy();
	}
	
	@Test
	public void testHttpAuthClient401() throws Exception {		
		logger.info("-- testHttpAuthClient401() --");

		HttpServletServer server = HttpServletServer.factory.build("echo", "localhost", 6666, "/", false, true);
		server.setBasicAuthentication("x", "y", null);
		server.addServletPackage("/*", this.getClass().getPackage().getName());
		server.waitedStart(5000);
		
		HttpClient client = HttpClient.factory.build("testHttpAuthClient401",false, false, 
				                                     "localhost", 6666, "junit/echo", 
				                                     null, null, true);
		Response response = client.get("hello");
		assertTrue(response.getStatus() == 401);
		
		HttpServletServer.factory.destroy();
		HttpClient.factory.destroy();
	}
	
  //@Test 
   public void testHttpAuthClientHttps() throws Exception {                             
	   logger.info("-- testHttpAuthClientHttps() --");

		HttpClient client = HttpClient.factory.build("testHttpAuthClientHttps", true, true, "somehost.somewhere.com",
				9091, "pap/test", "testpap", "alpha123", true);
		Response response = client.get();
		assertTrue(response.getStatus() == 200);

		HttpClient client2 = HttpClient.factory.build("testHttpAuthClientHttps2", true, true, "somehost.somewhere.com",
				8081, "pdp", "testpdp", "alpha123", true);
		Response response2 = client2.get("test");
		assertTrue(response2.getStatus() == 500);

		HttpServletServer.factory.destroy();
		HttpClient.factory.destroy();
    }
    
    //@Test
    public void testHttpAuthClientProps() throws Exception {
    	logger.info("-- testHttpAuthClientProps() --");
		
		Properties httpProperties = new Properties();
		
		httpProperties.setProperty(PolicyProperties.PROPERTY_HTTP_SERVER_SERVICES, "PAP,PDP");
		httpProperties.setProperty
			(PolicyProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + "PAP" + PolicyProperties.PROPERTY_HTTP_HOST_SUFFIX, 
			 "localhost");
		httpProperties.setProperty
			(PolicyProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + "PAP" + PolicyProperties.PROPERTY_HTTP_PORT_SUFFIX, 
			 "9091");
		httpProperties.setProperty
			(PolicyProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + "PAP" + PolicyProperties.PROPERTY_HTTP_AUTH_USERNAME_SUFFIX, 
			 "testpap");
		httpProperties.setProperty
			(PolicyProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + "PAP" + PolicyProperties.PROPERTY_HTTP_AUTH_PASSWORD_SUFFIX, 
			 "alpha123");
		httpProperties.setProperty
			(PolicyProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + "PAP" + PolicyProperties.PROPERTY_HTTP_REST_CLASSES_SUFFIX, 
			 "org.onap.policy.drools.http.server.test.RestMockHealthCheck");
		httpProperties.setProperty
			(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "PAP" + PolicyProperties.PROPERTY_MANAGED_SUFFIX, 
			 "true");
		
		httpProperties.setProperty
			(PolicyProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + "PDP" + PolicyProperties.PROPERTY_HTTP_HOST_SUFFIX, 
			 "localhost");
		httpProperties.setProperty
			(PolicyProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + "PDP" + PolicyProperties.PROPERTY_HTTP_PORT_SUFFIX, 
			 "8081");
		httpProperties.setProperty
			(PolicyProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + "PDP" + PolicyProperties.PROPERTY_HTTP_AUTH_USERNAME_SUFFIX, 
			 "testpdp");
		httpProperties.setProperty
			(PolicyProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + "PDP" + PolicyProperties.PROPERTY_HTTP_AUTH_PASSWORD_SUFFIX, 
			 "alpha123");
		httpProperties.setProperty
			(PolicyProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + "PDP" + PolicyProperties.PROPERTY_HTTP_REST_CLASSES_SUFFIX, 
			 "org.onap.policy.drools.http.server.test.RestMockHealthCheck");
		httpProperties.setProperty
			(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "PAP" + PolicyProperties.PROPERTY_MANAGED_SUFFIX, 
			 "true");
		
		httpProperties.setProperty(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES, "PAP,PDP");
		httpProperties.setProperty
			(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "PAP" + PolicyProperties.PROPERTY_HTTP_HOST_SUFFIX, 
			 "localhost");
		httpProperties.setProperty
			(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "PAP" + PolicyProperties.PROPERTY_HTTP_PORT_SUFFIX, 
			 "9091");
		httpProperties.setProperty
			(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "PAP" + PolicyProperties.PROPERTY_HTTP_URL_SUFFIX, 
			 "pap/test");
		httpProperties.setProperty
			(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "PAP" + PolicyProperties.PROPERTY_HTTP_HTTPS_SUFFIX, 
			 "false");
		httpProperties.setProperty
			(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "PAP" + PolicyProperties.PROPERTY_HTTP_AUTH_USERNAME_SUFFIX, 
			 "testpap");
		httpProperties.setProperty
			(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "PAP" + PolicyProperties.PROPERTY_HTTP_AUTH_PASSWORD_SUFFIX, 
			 "alpha123");
		httpProperties.setProperty
			(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "PAP" + PolicyProperties.PROPERTY_MANAGED_SUFFIX, 
			 "true");
		
		httpProperties.setProperty
			(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "PDP" + PolicyProperties.PROPERTY_HTTP_HOST_SUFFIX, 
			 "localhost");
		httpProperties.setProperty
			(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "PDP" + PolicyProperties.PROPERTY_HTTP_PORT_SUFFIX, 
			 "8081");
		httpProperties.setProperty
			(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "PDP" + PolicyProperties.PROPERTY_HTTP_URL_SUFFIX, 
			 "pdp");
		httpProperties.setProperty
			(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "PDP" + PolicyProperties.PROPERTY_HTTP_HTTPS_SUFFIX, 
			 "false");
		httpProperties.setProperty
			(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "PDP" + PolicyProperties.PROPERTY_HTTP_AUTH_USERNAME_SUFFIX, 
			 "testpdp");
		httpProperties.setProperty
			(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "PDP" + PolicyProperties.PROPERTY_HTTP_AUTH_PASSWORD_SUFFIX, 
			 "alpha123");
		httpProperties.setProperty
			(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "PDP" + PolicyProperties.PROPERTY_MANAGED_SUFFIX, 
			 "true");
		
		ArrayList<HttpServletServer> servers = HttpServletServer.factory.build(httpProperties);
		assertTrue(servers.size() == 2);
		
		ArrayList<HttpClient> clients = HttpClient.factory.build(httpProperties);
		assertTrue(clients.size() == 2);
		
		for (HttpServletServer server: servers) {
			server.waitedStart(5000);
		}
		
		HttpClient clientPAP = HttpClient.factory.get("PAP");
		Response response = clientPAP.get();
		assertTrue(response.getStatus() == 200);

		HttpClient clientPDP = HttpClient.factory.get("PDP");
		Response response2 = clientPDP.get("test");
		assertTrue(response2.getStatus() == 500);

		HttpServletServer.factory.destroy();
		HttpClient.factory.destroy();    	
    }


}
