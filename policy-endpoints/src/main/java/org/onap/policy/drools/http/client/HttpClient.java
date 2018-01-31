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

import javax.ws.rs.core.Response;

import org.onap.policy.drools.properties.Startable;

public interface HttpClient extends Startable {

	public Response get(String path);

	public Response get();

	public static <T> T getBody(Response response, Class<T> entityType) {
		return response.readEntity(entityType);
	}

	public String getName();
	public boolean isHttps();
	public boolean isSelfSignedCerts();
	public String getHostname();
	public int getPort();
	public String getBasePath();
	public String getUserName();
	public String getPassword();
	public String getBaseUrl();


	public static final HttpClientFactory factory = new IndexedHttpClientFactory();
}
