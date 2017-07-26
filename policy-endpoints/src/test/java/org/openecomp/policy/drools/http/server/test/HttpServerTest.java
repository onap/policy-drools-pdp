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
package org.openecomp.policy.drools.http.server.test;

import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.UUID;

import org.junit.Test;
import org.openecomp.policy.drools.http.server.HttpServletServer;
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
		
		String echo = "hello";
		URL url = new URL("http://localhost:5678/junit/echo/" + echo);
		String response = response(url);
		assertTrue(response.equals(echo));
	
		String responseSwagger =  null;
		try {
			URL urlSwagger = new URL("http://localhost:5678/swagger.json" + echo);
			responseSwagger = response(urlSwagger);
		} catch(IOException ioe) {
			// Expected
		}
		
		assertTrue(responseSwagger == null);
		
		assertTrue(HttpServletServer.factory.get(5678).isAlive());
		assertTrue(HttpServletServer.factory.inventory().size() == 1);
		
		HttpServletServer.factory.destroy(5678);	
		assertTrue(HttpServletServer.factory.inventory().size() == 0);
	}
	
	@Test
	public void testMultipleServers() throws Exception {
		logger.info("-- testMultipleServers() --");
		
		HttpServletServer server1 = HttpServletServer.factory.build("echo-1", "localhost", 5678, "/", true, true);
		server1.addServletPackage("/*", this.getClass().getPackage().getName());
		server1.waitedStart(5000);
		
		HttpServletServer server2 = HttpServletServer.factory.build("echo-2", "localhost", 5679, "/", false, true);
		server2.addServletPackage("/*", this.getClass().getPackage().getName());
		server2.waitedStart(5000);
		
		assertTrue(HttpServletServer.factory.get(5678).isAlive());
		assertTrue(HttpServletServer.factory.get(5679).isAlive());
		
		String echo = "hello";
		
		URL url1 = new URL("http://localhost:5678/junit/echo/" + echo);
		String response1 = response(url1);
		assertTrue(response1.equals(echo));
		
		URL urlSwagger = new URL("http://localhost:5678/swagger.json");
		String responseSwagger = response(urlSwagger);	
		assertTrue(responseSwagger != null);
		
		URL url2 = new URL("http://localhost:5679/junit/echo/" + echo);
		String response2 = response(url2);
		assertTrue(response2.equals(echo));
		
		String responseSwagger2 =  null;
		try {
			URL urlSwagger2 = new URL("http://localhost:5679/swagger.json");
			responseSwagger2 = response(urlSwagger2);
		} catch(IOException ioe) {
			// Expected
		}
		assertTrue(responseSwagger2 == null);
		
		HttpServletServer.factory.destroy();		
		assertTrue(HttpServletServer.factory.inventory().size() == 0);
	}
	
	@Test
	public void testMultiServicePackage() throws Exception {
		logger.info("-- testMultiServicePackage() --");
		
		String randomName = UUID.randomUUID().toString();
		
		HttpServletServer server = HttpServletServer.factory.build(randomName, "localhost", 5678, "/", false, true);
		server.addServletPackage("/*", this.getClass().getPackage().getName());
		server.waitedStart(5000);
		
		assertTrue(HttpServletServer.factory.get(5678).isAlive());
		
		String echo = "hello";
		URL urlService1 = new URL("http://localhost:5678/junit/echo/" + echo);
		String responseService1 = response(urlService1);
		assertTrue(responseService1.equals(echo));
		
		URL urlService2 = new URL("http://localhost:5678/junit/endpoints/http/servers");
		String responseService2 = response(urlService2);
		assertTrue(responseService2.contains(randomName));
		
		HttpServletServer.factory.destroy();		
		assertTrue(HttpServletServer.factory.inventory().size() == 0);
	}
	
	@Test
	public void testServiceClass() throws Exception {
		logger.info("-- testServiceClass() --");
		String randomName = UUID.randomUUID().toString();
		
		HttpServletServer server = HttpServletServer.factory.build(randomName, "localhost", 5678, "/", false, true);
		server.addServletClass("/*", RestEchoService.class.getCanonicalName());
		server.waitedStart(5000);
		
		assertTrue(HttpServletServer.factory.get(5678).isAlive());
		
		String echo = "hello";
		URL urlService1 = new URL("http://localhost:5678/junit/echo/" + echo);
		String responseService1 = response(urlService1);
		assertTrue(responseService1.equals(echo));
		
		HttpServletServer.factory.destroy();		
		assertTrue(HttpServletServer.factory.inventory().size() == 0);
	}
	
	@Test
	public void testMultiServiceClass() throws Exception {
		logger.info("-- testMultiServiceClass() --");
		
		String randomName = UUID.randomUUID().toString();
		
		HttpServletServer server = HttpServletServer.factory.build(randomName, "localhost", 5678, "/", false, true);
		server.addServletClass("/*", RestEchoService.class.getCanonicalName());
		server.addServletClass("/*", RestEndpoints.class.getCanonicalName());
		server.waitedStart(5000);
		
		assertTrue(HttpServletServer.factory.get(5678).isAlive());
		
		String echo = "hello";
		URL urlService1 = new URL("http://localhost:5678/junit/echo/" + echo);
		String responseService1 = response(urlService1);
		assertTrue(responseService1.equals(echo));
		
		URL urlService2 = new URL("http://localhost:5678/junit/endpoints/http/servers");
		String responseService2 = response(urlService2);
		assertTrue(responseService2.contains(randomName));
		
		HttpServletServer.factory.destroy();		
		assertTrue(HttpServletServer.factory.inventory().size() == 0);
	}

	/**
	 * @param url
	 * @throws IOException
	 */
	protected String response(URL url) throws IOException {
		BufferedReader ioReader = new BufferedReader(new InputStreamReader(url.openStream()));
		String response = "";
		String line;
		while ((line = ioReader.readLine()) != null) {
			response += line; 
		}
		ioReader.close();
		return response;
	}
	
	

}
