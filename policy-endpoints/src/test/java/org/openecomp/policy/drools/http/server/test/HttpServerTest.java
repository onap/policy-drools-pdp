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

/**
 *
 */
public class HttpServerTest {

	@Test
	public void testSingleServer() throws Exception {
		System.out.println("-- testSingleServer() --");
		
		HttpServletServer server = HttpServletServer.factory.build("echo", "localhost", 5678, "/", true);
		server.addServletPackage("/*", this.getClass().getPackage().getName());
		server.waitedStart(5000);
		
		assertTrue(HttpServletServer.factory.get(5678).isAlive());
		
		String echo = "hello";
		URL url = new URL("http://localhost:5678/junit/echo/" + echo);
		String response = response(url);
		System.out.println("Received .. " + response);
		assertTrue(response.equals(echo));
		
		HttpServletServer.factory.destroy();
		assertTrue(HttpServletServer.factory.inventory().size() == 0);
	}
	
	@Test
	public void testMultipleServers() throws Exception {
		System.out.println("-- testMultipleServers() --");
		
		HttpServletServer server1 = HttpServletServer.factory.build("echo-1", "localhost", 5678, "/", true);
		server1.addServletPackage("/*", this.getClass().getPackage().getName());
		server1.waitedStart(5000);
		
		HttpServletServer server2 = HttpServletServer.factory.build("echo-2", "localhost", 5679, "/", true);
		server2.addServletPackage("/*", this.getClass().getPackage().getName());
		server2.waitedStart(5000);
		
		assertTrue(HttpServletServer.factory.get(5678).isAlive());
		assertTrue(HttpServletServer.factory.get(5679).isAlive());
		
		String echo = "hello";
		
		URL url1 = new URL("http://localhost:5678/junit/echo/" + echo);
		String response1 = response(url1);
		System.out.println("Received .. " + response1);
		assertTrue(response1.equals(echo));
		
		URL url2 = new URL("http://localhost:5679/junit/echo/" + echo);
		String response2 = response(url2);
		System.out.println("Received .. " + response2);
		assertTrue(response2.equals(echo));
		
		HttpServletServer.factory.destroy();		
		assertTrue(HttpServletServer.factory.inventory().size() == 0);
	}
	
	@Test
	public void testMultiServicePackage() throws Exception {
		System.out.println("-- testMultiServicePackage() --");
		
		String randomName = UUID.randomUUID().toString();
		
		HttpServletServer server = HttpServletServer.factory.build(randomName, "localhost", 5678, "/", true);
		server.addServletPackage("/*", this.getClass().getPackage().getName());
		server.waitedStart(5000);
		
		assertTrue(HttpServletServer.factory.get(5678).isAlive());
		
		String echo = "hello";
		URL urlService1 = new URL("http://localhost:5678/junit/echo/" + echo);
		String responseService1 = response(urlService1);
		System.out.println("Received .. " + responseService1);
		assertTrue(responseService1.equals(echo));
		
		URL urlService2 = new URL("http://localhost:5678/junit/endpoints/http/servers");
		String responseService2 = response(urlService2);
		System.out.println("Received .. " + responseService2);
		assertTrue(responseService2.contains(randomName));
		
		HttpServletServer.factory.destroy();		
		assertTrue(HttpServletServer.factory.inventory().size() == 0);
	}
	
	@Test
	public void testServiceClass() throws Exception {
		System.out.println("-- testServiceClass() --");
		String randomName = UUID.randomUUID().toString();
		
		HttpServletServer server = HttpServletServer.factory.build(randomName, "localhost", 5678, "/", true);
		server.addServletClass("/*", RestEchoService.class.getCanonicalName());
		server.waitedStart(5000);
		
		assertTrue(HttpServletServer.factory.get(5678).isAlive());
		
		String echo = "hello";
		URL urlService1 = new URL("http://localhost:5678/junit/echo/" + echo);
		String responseService1 = response(urlService1);
		System.out.println("Received .. " + responseService1);
		assertTrue(responseService1.equals(echo));
		
		HttpServletServer.factory.destroy();		
		assertTrue(HttpServletServer.factory.inventory().size() == 0);
	}
	
	@Test
	public void testMultiServiceClass() throws Exception {
		System.out.println("-- testMultiServiceClass() --");
		
		String randomName = UUID.randomUUID().toString();
		
		HttpServletServer server = HttpServletServer.factory.build(randomName, "localhost", 5678, "/", true);
		server.addServletClass("/*", RestEchoService.class.getCanonicalName());
		server.addServletClass("/*", RestEndpoints.class.getCanonicalName());
		server.waitedStart(5000);
		
		assertTrue(HttpServletServer.factory.get(5678).isAlive());
		
		String echo = "hello";
		URL urlService1 = new URL("http://localhost:5678/junit/echo/" + echo);
		String responseService1 = response(urlService1);
		System.out.println("Received .. " + responseService1);
		assertTrue(responseService1.equals(echo));
		
		URL urlService2 = new URL("http://localhost:5678/junit/endpoints/http/servers");
		String responseService2 = response(urlService2);
		System.out.println("Received .. " + responseService2);
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
		return response;
	}
	
	

}
