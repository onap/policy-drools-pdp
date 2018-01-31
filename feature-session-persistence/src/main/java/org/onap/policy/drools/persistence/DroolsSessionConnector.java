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

public interface DroolsSessionConnector
{
	/**
	 * Gets a session by PDP id and name.
	 * @param sessName
	 * @return a session, or {@code null} if it is not found
	 */
	public DroolsSession get(String sessName);

	/**
	 * Replaces a session, adding it if it does not exist.
	 * @param sess		session to be replaced
	 */
	public void replace(DroolsSession sess);

}
