/*-
 * ============LICENSE_START=======================================================
 * policy-core
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

package org.openecomp.policy.drools.properties;

public interface PolicyProperties {
	
	/* Controller Properties */
	
	public static final String PROPERTY_CONTROLLER_NAME = "controller.name";
	
	/* Generic property suffixes */
	
	public static final String PROPERTY_TOPIC_SERVERS_SUFFIX = ".servers";
	public static final String PROPERTY_TOPIC_API_KEY_SUFFIX = ".apiKey";
	public static final String PROPERTY_TOPIC_API_SECRET_SUFFIX = ".apiSecret";	
	public static final String PROPERTY_TOPIC_AAF_MECHID_SUFFIX = ".aafMechId";
	public static final String PROPERTY_TOPIC_AAF_PASSWORD_SUFFIX = ".aafPassword";	
	public static final String PROPERTY_TOPIC_EVENTS_SUFFIX =".events";
	public static final String PROPERTY_TOPIC_EVENTS_FILTER_SUFFIX =".filter";
	public static final String PROPERTY_TOPIC_EVENTS_CUSTOM_MODEL_CODER_GSON_SUFFIX =".events.custom.gson";
	public static final String PROPERTY_TOPIC_EVENTS_CUSTOM_MODEL_CODER_JACKSON_SUFFIX =".events.custom.jackson";
	
	public static final String PROPERTY_TOPIC_SOURCE_CONSUMER_GROUP_SUFFIX = ".consumerGroup";
	public static final String PROPERTY_TOPIC_SOURCE_CONSUMER_INSTANCE_SUFFIX = ".consumerInstance";
	public static final String PROPERTY_TOPIC_SOURCE_FETCH_TIMEOUT_SUFFIX = ".fetchTimeout";
	public static final String PROPERTY_TOPIC_SOURCE_FETCH_LIMIT_SUFFIX = ".fetchLimit";
	public static final String PROPERTY_MANAGED_SUFFIX =".managed";
	
	public static final String PROPERTY_TOPIC_SINK_PARTITION_KEY_SUFFIX =".partitionKey";
	
	/* UEB Properties */
	
	public static final String PROPERTY_UEB_SOURCE_TOPICS = "ueb.source.topics";	
	public static final String PROPERTY_UEB_SINK_TOPICS = "ueb.sink.topics";	
	
	/* DMAAP Properties */
	
	public static final String PROPERTY_DMAAP_SOURCE_TOPICS = "dmaap.source.topics";
	public static final String PROPERTY_DMAAP_SINK_TOPICS = "dmaap.sink.topics";
	
	/* HTTP Server Properties */
	
	public static final String PROPERTY_HTTP_SERVER_SERVICES = "http.server.services";
	
	public static final String PROPERTY_HTTP_HOST_SUFFIX = ".host";
	public static final String PROPERTY_HTTP_PORT_SUFFIX = ".port";
	public static final String PROPERTY_HTTP_CONTEXT_URIPATH_SUFFIX = ".contextUriPath";
	
	public static final String PROPERTY_HTTP_AUTH_USERNAME_SUFFIX = ".userName";
	public static final String PROPERTY_HTTP_AUTH_PASSWORD_SUFFIX = ".password";
	public static final String PROPERTY_HTTP_AUTH_URIPATH_SUFFIX = ".authUriPath";
	
	public static final String PROPERTY_HTTP_REST_CLASSES_SUFFIX = ".restClasses";
	public static final String PROPERTY_HTTP_REST_PACKAGES_SUFFIX = ".restPackages";
	public static final String PROPERTY_HTTP_REST_URIPATH_SUFFIX = ".restUriPath";
	
	public static final String PROPERTY_HTTP_HTTPS_SUFFIX = ".https";
	
	/* HTTP Client Properties */
	
	public static final String PROPERTY_HTTP_CLIENT_SERVICES = "http.client.services";
	
	public static final String PROPERTY_HTTP_URL_SUFFIX = PROPERTY_HTTP_CONTEXT_URIPATH_SUFFIX;
	
	/* Drools Properties */
	
	public static final String RULES_GROUPID = "rules.groupId";
	public static final String RULES_ARTIFACTID = "rules.artifactId";
	public static final String RULES_VERSION = "rules.version";
	
	/* Management Server Properties */	
	
	public static final String ENV_MANAGEMENT_SERVER_PORT = "ENGINE_MANAGEMENT_PORT";
	public static final String ENV_MANAGEMENT_SERVER_HOST = "ENGINE_MANAGEMENT_HOST";
	public static final String ENV_MANAGEMENT_AUTH_USER = "ENGINE_MANAGEMENT_USER";
	public static final String ENV_MANAGEMENT_AUTH_PASSWD = "ENGINE_MANAGEMENT_PASSWORD";
	
}
