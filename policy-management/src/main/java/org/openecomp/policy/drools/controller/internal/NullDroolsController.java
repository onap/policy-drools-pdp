/*-
 * ============LICENSE_START=======================================================
 * policy-management
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

package org.openecomp.policy.drools.controller.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openecomp.policy.drools.controller.DroolsController;
import org.openecomp.policy.drools.event.comm.TopicSink;
import org.openecomp.policy.drools.protocol.coders.TopicCoderFilterConfiguration;

/**
 * no-op Drools Controller
 */
public class NullDroolsController implements DroolsController {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean start() throws IllegalStateException {
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean stop() throws IllegalStateException {
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void shutdown() throws IllegalStateException {
		return;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void halt() throws IllegalStateException {
		return;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isAlive() {
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean lock() {
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean unlock() {
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isLocked() {
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getGroupId() {
		return NO_GROUP_ID;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getArtifactId() {
		return NO_ARTIFACT_ID;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getVersion() {
		return NO_VERSION;
	}	

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<String> getSessionNames() {
		return new ArrayList<String>();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<String> getCanonicalSessionNames() {
		return new ArrayList<String>();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean offer(String topic, String event) {
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean deliver(TopicSink sink, Object event)
			throws IllegalArgumentException, IllegalStateException, UnsupportedOperationException {
		throw new IllegalArgumentException(this.getClass().getCanonicalName() + " invoked");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object[] getRecentSourceEvents() {
		return new String[0];
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String[] getRecentSinkEvents() {
		return new String[0];
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean ownsCoder(Class<? extends Object> coderClass, int modelHash) throws IllegalStateException {
		throw new IllegalArgumentException(this.getClass().getCanonicalName() + " invoked");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Class<?> fetchModelClass(String className) throws IllegalArgumentException {
		throw new IllegalArgumentException(this.getClass().getCanonicalName() + " invoked");
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isBrained() {
		return false;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("NullDroolsController []");
		return builder.toString();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void updateToVersion(String newGroupId, String newArtifactId, String newVersion,
			List<TopicCoderFilterConfiguration> decoderConfigurations,
			List<TopicCoderFilterConfiguration> encoderConfigurations)
			throws IllegalArgumentException, LinkageError, Exception {
		throw new IllegalArgumentException(this.getClass().getCanonicalName() + " invoked");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Map<String, Integer> factClassNames(String sessionName) 
		   throws IllegalArgumentException {
		return new HashMap<String,Integer>();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long factCount(String sessionName) throws IllegalArgumentException {
		return 0;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<Object> facts(String sessionName, String className, boolean delete) {
		return new ArrayList<Object>();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<Object> factQuery(String sessionName, String queryName, 
			                      String queriedEntity, 
			                      boolean delete, Object... queryParams) {
		return new ArrayList<Object>();
	}

}
