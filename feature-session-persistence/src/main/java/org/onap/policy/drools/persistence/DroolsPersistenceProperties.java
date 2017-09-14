/*-
 * ============LICENSE_START=======================================================
 * feature-session-persistence
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

package org.onap.policy.drools.persistence;

public class DroolsPersistenceProperties {
	/*
	 * feature-session-persistence.properties parameter key values
	 */
	public static final String DB_DRIVER = "javax.persistence.jdbc.driver";
	public static final String DB_URL = "javax.persistence.jdbc.url";
	public static final String DB_USER = "javax.persistence.jdbc.user";
	public static final String DB_PWD = "javax.persistence.jdbc.password";
	public static final String DB_SESSIONINFO_TIMEOUT = "persistence.sessioninfo.timeout";
	public static final String JTA_OBJECTSTORE_DIR = "persistence.objectstore.dir";
}
