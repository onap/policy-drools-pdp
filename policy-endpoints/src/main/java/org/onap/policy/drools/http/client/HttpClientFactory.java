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
package org.onap.policy.drools.http.client;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.onap.policy.drools.http.client.internal.JerseyClient;
import org.onap.policy.drools.properties.PolicyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Http Client Factory
 */
public interface HttpClientFactory {
	
	/**
	 * build and http client with the following parameters
	 */
	public HttpClient build(String name, boolean https, 
                            boolean selfSignedCerts,
                            String hostname, int port, 
                            String baseUrl, String userName,
                            String password, boolean managed) 
    throws KeyManagementException, NoSuchAlgorithmException;
	
	/**
	 * build http client from properties
	 */
	public List<HttpClient> build(Properties properties) 
			throws KeyManagementException, NoSuchAlgorithmException;
	
	/**
	 * get http client
	 * @param name the name
	 * @return the http client
	 */
	public HttpClient get(String name);
	
	/**
	 * list of http clients
	 * @return http clients
	 */
	public List<HttpClient> inventory();
	
	/**
	 * destroy by name
	 * @param name name
	 */
	public void destroy(String name);
	
	public void destroy();
}

/**
 * http client factory implementation indexed by name
 */
class IndexedHttpClientFactory implements HttpClientFactory {
	
	/**
	 * Logger
	 */
	private static Logger logger = LoggerFactory.getLogger(IndexedHttpClientFactory.class);
	
	protected HashMap<String, HttpClient> clients = new HashMap<>();

	@Override
	public synchronized HttpClient build(String name, boolean https, boolean selfSignedCerts, 
			                             String hostname, int port,
			                             String baseUrl, String userName, String password,
			                             boolean managed) 
	throws KeyManagementException, NoSuchAlgorithmException {
		if (clients.containsKey(name))
			return clients.get(name);
		
		JerseyClient client = 
				new JerseyClient(name, https, selfSignedCerts, hostname, port, baseUrl, userName, password);
		
		if (managed)
			clients.put(name, client);
		
		return client;
	}

	@Override
	public synchronized List<HttpClient> build(Properties properties) 
	throws KeyManagementException, NoSuchAlgorithmException {
		ArrayList<HttpClient> clientList = new ArrayList<>();
		
		String clientNames = properties.getProperty(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES);
		if (clientNames == null || clientNames.isEmpty()) {
			return clientList;
		}
		
		List<String> clientNameList = 
				new ArrayList<>(Arrays.asList(clientNames.split("\\s*,\\s*")));
		
		for (String clientName : clientNameList) {
			String httpsString = properties.getProperty(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + 
                                                        clientName + 
                                                        PolicyProperties.PROPERTY_HTTP_HTTPS_SUFFIX);
			boolean https = false;
			if (httpsString != null && !httpsString.isEmpty()) {
				https = Boolean.parseBoolean(httpsString);
			}
			
			String hostName = properties.getProperty(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." +
					                                 clientName + 
                                                     PolicyProperties.PROPERTY_HTTP_HOST_SUFFIX);
					
			String servicePortString = properties.getProperty(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + 
		                                                      clientName + 
		                                                      PolicyProperties.PROPERTY_HTTP_PORT_SUFFIX);
			int port;
			try {
				if (servicePortString == null || servicePortString.isEmpty()) {
					continue;
				}
				port = Integer.parseInt(servicePortString);
			} catch (NumberFormatException nfe) {
				logger.error("http-client-factory: cannot parse port {}", servicePortString, nfe);
				continue;
			}
			
			String baseUrl = properties.getProperty(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." +
					                                clientName + 
                                                    PolicyProperties.PROPERTY_HTTP_URL_SUFFIX);
			
			String userName = properties.getProperty(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." +
					                                 clientName + 
                                                     PolicyProperties.PROPERTY_HTTP_AUTH_USERNAME_SUFFIX);

			String password = properties.getProperty(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." +
		                                             clientName + 
                                                     PolicyProperties.PROPERTY_HTTP_AUTH_PASSWORD_SUFFIX);
			
			String managedString = properties.getProperty(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." +
					                                      clientName + 
					                                      PolicyProperties.PROPERTY_MANAGED_SUFFIX);
			boolean managed = true;
			if (managedString != null && !managedString.isEmpty()) {
				managed = Boolean.parseBoolean(managedString);
			}
			
			try {
				HttpClient client =
						this.build(clientName, https, https, hostName, port, baseUrl, 
								   userName, password, managed);
				clientList.add(client);
			} catch (Exception e) {
				logger.error("http-client-factory: cannot build client {}", clientName, e);
			}
		}
		
		return clientList;
	}

	@Override
	public synchronized HttpClient get(String name) {
		if (clients.containsKey(name)) {
			return clients.get(name);
		} 
		
		throw new IllegalArgumentException("Http Client " + name + " not found");
	}

	@Override
	public synchronized List<HttpClient> inventory() {
		return new ArrayList<>(this.clients.values());
	}

	@Override
	public synchronized void destroy(String name) {
		if (!clients.containsKey(name)) {
			return;
		}
		
		HttpClient client = clients.remove(name);
		try {
			client.shutdown();
		} catch (IllegalStateException e) {
			logger.error("http-client-factory: cannot shutdown client {}", client, e);
		}
	}

	@Override
	public void destroy() {
		List<HttpClient> clientsInventory = this.inventory();
		for (HttpClient client: clientsInventory) {
			client.shutdown();
		}
		
		synchronized(this) {
			this.clients.clear();
		}
	}
	
}
