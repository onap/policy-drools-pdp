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
package org.openecomp.policy.drools.http.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.openecomp.policy.drools.http.client.internal.JerseyClient;
import org.openecomp.policy.drools.properties.PolicyProperties;

public interface HttpClientFactory {
	
	public HttpClient build(String name, boolean https, 
                            boolean selfSignedCerts,
                            String hostname, int port, 
                            String baseUrl, String userName,
                            String password, boolean managed) 
    throws Exception;
	
	public ArrayList<HttpClient> build(Properties properties) throws Exception;
	
	public HttpClient get(String name);
	
	public List<HttpClient> inventory();
	
	public void destroy(String name);
	
	public void destroy();
}

class IndexedHttpClientFactory implements HttpClientFactory {
	
	protected HashMap<String, HttpClient> clients = new HashMap<String, HttpClient>();

	@Override
	public synchronized HttpClient build(String name, boolean https, boolean selfSignedCerts, 
			                             String hostname, int port,
			                             String baseUrl, String userName, String password,
			                             boolean managed) 
	throws Exception {
		if (clients.containsKey(name))
			return clients.get(name);
		
		JerseyClient client = 
				new JerseyClient(name, https, selfSignedCerts, hostname, port, baseUrl, userName, password);
		
		if (managed)
			clients.put(name, client);
		
		return client;
	}

	@Override
	public synchronized ArrayList<HttpClient> build(Properties properties) throws Exception {
		ArrayList<HttpClient> clientList = new ArrayList<HttpClient>();
		
		String clientNames = properties.getProperty(PolicyProperties.PROPERTY_HTTP_CLIENT_SERVICES);
		if (clientNames == null || clientNames.isEmpty()) {
			return clientList;
		}
		
		List<String> clientNameList = 
				new ArrayList<String>(Arrays.asList(clientNames.split("\\s*,\\s*")));
		
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
				nfe.printStackTrace();
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
				e.printStackTrace();
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
		return new ArrayList<HttpClient>(this.clients.values());
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
			e.printStackTrace();
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
