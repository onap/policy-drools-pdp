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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;

import org.junit.Test;
import org.onap.policy.drools.http.server.HttpServletServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HttpServletServer JUNIT tests
 */
public class HttpServerTest {
	
	/**
	 * Logger
	 */
	private static Logger logger = LoggerFactory.getLogger(HttpServerTest.class);

	@Test
	public void testSingleServer() throws Exception {
		logger.info("-- testSingleServer() --");
		
		HttpServletServer server = HttpServletServer.factory.build("echo", "localhost", 5678, "/", false, true);
		server.addServletPackage("/*", this.getClass().getPackage().getName());
		server.waitedStart(5000);
		
		assertTrue(HttpServletServer.factory.get(5678).isAlive());
		
		String response =
				http(HttpServletServer.factory.get(5678), "http://localhost:5678/junit/echo/hello");
		assertTrue("hello".equals(response));
	
		response = null;
		try {
			response = http(HttpServletServer.factory.get(5678), "http://localhost:5678/swagger.json");
		} catch (IOException e) {
			// Expected
		}
		assertTrue(response == null);
		
		assertTrue(HttpServletServer.factory.get(5678).isAlive());
		assertTrue(HttpServletServer.factory.inventory().size() == 1);
		
		HttpServletServer.factory.destroy(5678);	
		assertTrue(HttpServletServer.factory.inventory().size() == 0);
	}
	
	@Test
	public void testMultipleServers() throws Exception {
		logger.info("-- testMultipleServers() --");
		
		HttpServletServer server1 = HttpServletServer.factory.build("echo-1", "localhost", 5688, "/", true, true);
		server1.addServletPackage("/*", this.getClass().getPackage().getName());
		server1.waitedStart(5000);
		
		HttpServletServer server2 = HttpServletServer.factory.build("echo-2", "localhost", 5689, "/", false, true);
		server2.addServletPackage("/*", this.getClass().getPackage().getName());
		server2.waitedStart(5000);
		
		assertTrue(HttpServletServer.factory.get(5688).isAlive());
		assertTrue(HttpServletServer.factory.get(5689).isAlive());

		String response =
				http(HttpServletServer.factory.get(5688), "http://localhost:5688/junit/echo/hello");
		assertTrue("hello".equals(response));
		
		response =
				http(HttpServletServer.factory.get(5688), "http://localhost:5688/swagger.json");
		assertTrue(response != null);

		response =
				http(HttpServletServer.factory.get(5689), "http://localhost:5689/junit/echo/hello");
		assertTrue("hello".equals(response));	
		
		response = null;
		try {
			response = http(HttpServletServer.factory.get(5689), "http://localhost:5689/swagger.json");
		} catch (IOException e) {
			// Expected
		}
		assertTrue(response == null);
		
		HttpServletServer.factory.destroy();		
		assertTrue(HttpServletServer.factory.inventory().size() == 0);
	}
	
	@Test
	public void testMultiServicePackage() throws Exception {
		logger.info("-- testMultiServicePackage() --");
		
		String randomName = UUID.randomUUID().toString();
		
		HttpServletServer server = HttpServletServer.factory.build(randomName, "localhost", 5668, "/", false, true);
		server.addServletPackage("/*", this.getClass().getPackage().getName());
		server.waitedStart(5000);
		
		assertTrue(HttpServletServer.factory.get(5668).isAlive());
		
		String response =
				http(HttpServletServer.factory.get(5668), "http://localhost:5668/junit/echo/hello");
		assertTrue("hello".equals(response));
		
		response =
				http(HttpServletServer.factory.get(5668), "http://localhost:5668/junit/endpoints/http/servers");
		assertTrue(response.contains(randomName));
		
		HttpServletServer.factory.destroy();		
		assertTrue(HttpServletServer.factory.inventory().size() == 0);
	}
	
	@Test
	public void testServiceClass() throws Exception {
		logger.info("-- testServiceClass() --");
		String randomName = UUID.randomUUID().toString();
		
		HttpServletServer server = HttpServletServer.factory.build(randomName, "localhost", 5658, "/", false, true);
		server.addServletClass("/*", RestEchoService.class.getCanonicalName());
		server.waitedStart(5000);
		
		assertTrue(HttpServletServer.factory.get(5658).isAlive());
		
		String response =
				http(HttpServletServer.factory.get(5658), "http://localhost:5658/junit/echo/hello");
		assertTrue("hello".equals(response));
		
		HttpServletServer.factory.destroy();		
		assertTrue(HttpServletServer.factory.inventory().size() == 0);
	}
	
	@Test
	public void testMultiServiceClass() throws Exception {
		logger.info("-- testMultiServiceClass() --");
		
		String randomName = UUID.randomUUID().toString();
		
		HttpServletServer server = HttpServletServer.factory.build(randomName, "localhost", 5648, "/", false, true);
		server.addServletClass("/*", RestEchoService.class.getCanonicalName());
		server.addServletClass("/*", RestEndpoints.class.getCanonicalName());
		server.waitedStart(5000);
		
		assertTrue(HttpServletServer.factory.get(5648).isAlive());
		
		String response =
				http(HttpServletServer.factory.get(5648), "http://localhost:5648/junit/echo/hello");
		assertTrue("hello".equals(response));
		
		response =
				http(HttpServletServer.factory.get(5648), "http://localhost:5648/junit/endpoints/http/servers");
		assertTrue(response.contains(randomName));
		
		HttpServletServer.factory.destroy();		
		assertTrue(HttpServletServer.factory.inventory().size() == 0);
	}

	/**
	 * performs an http request 
	 * 
	 * @throws MalformedURLException
	 * @throws IOException
	 * @throws InterruptedException 
	 */
	protected String http(HttpServletServer server, String aUrl) 
			throws MalformedURLException, IOException, InterruptedException {
		URL url = new URL(aUrl);
		String response = null;
		int numRetries = 1, maxNumberRetries = 5;
		while (numRetries <= maxNumberRetries) {
			try {
				response = response(url);
				break;
			} catch (ConnectException e) {
				logger.warn("http server {} @ {} ({}) - cannot connect yet ..", 
							server, aUrl, numRetries, e);
				numRetries++;
				Thread.sleep(10000L);
			} catch (Exception e) {
				throw e;
			} 
		}
		
		return response;
	}

	/**
	 * gets http response 
	 * 
	 * @param url url
	 * 
	 * @throws IOException
	 */
	protected String response(URL url) throws IOException {
		String response = "";
		try (BufferedReader ioReader = new BufferedReader(new InputStreamReader(url.openStream()))) {
			String line;
			while ((line = ioReader.readLine()) != null) {
				response += line; 
			}
		} 
		return response;
	}
	
	

}
