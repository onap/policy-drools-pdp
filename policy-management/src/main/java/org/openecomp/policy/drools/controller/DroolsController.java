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

package org.openecomp.policy.drools.controller;

import java.util.List;

import org.openecomp.policy.drools.event.comm.TopicSink;
import org.openecomp.policy.drools.properties.Lockable;
import org.openecomp.policy.drools.properties.Startable;
import org.openecomp.policy.drools.protocol.coders.TopicCoderFilterConfiguration;

/**
 * Drools Controller is the abstractions that wraps the
 * drools layer (policy-core)
 */
public interface DroolsController extends Startable, Lockable {	
	
	/**
	 * No Group ID identifier
	 */
	public static final String NO_GROUP_ID = "NO-GROUP-ID";

	/**
	 * No Artifact ID identifier
	 */
	public static final String NO_ARTIFACT_ID = "NO-ARTIFACT-ID";
	
	/**
	 * No version identifier
	 */
	public static final String NO_VERSION = "NO-VERSION";
	
	/**
	 * get group id
	 * @return group id
	 */
	public String getGroupId();
	
	/**
	 * get artifact id
	 * @return artifact id
	 */	
	public String getArtifactId();
	
	/**
	 * get version
	 * @return version
	 */
	public String getVersion();
	

	/**
	 * return the policy session names
	 * 
	 * @return policy session
	 */
	public List<String> getSessionNames();
	
	/**
	 * offers an event to this controller for processing
	 * 
	 * @param topic topic associated with the event
	 * @param event the event
	 * 
	 * @return true if the operation was successful
	 */
	public boolean offer(String topic, String event);
	
	/**
	 * delivers "event" to "sink"
	 * 
	 * @param sink destination
	 * @param event 
	 * @return true if successful, false if a failure has occurred.
	 * @throws IllegalArgumentException when invalid or insufficient 
	 *         properties are provided
	 * @throws IllegalStateException when the engine is in a state where
	 *         this operation is not permitted (ie. locked or stopped).
	 * @throws UnsupportedOperationException when the engine cannot deliver due
	 *         to the functionality missing (ie. communication infrastructure
	 *         not supported.
	 */
	public boolean deliver(TopicSink sink, Object event)
			throws IllegalArgumentException, IllegalStateException, 
		           UnsupportedOperationException;
	
	/**
	 * 
	 * @return the most recent received events
	 */
	public Object[] getRecentSourceEvents();

	/**
	 * 
	 * @return the most recent delivered events 
	 */
	public String[] getRecentSinkEvents();
	
	/**
	 * Supports this encoder?
	 * 
	 * @param encodedObject
	 * @return
	 */
	public boolean ownsCoder(Class<? extends Object> coderClass, int modelHash) throws IllegalStateException;
	
	/**
	 * fetches a class from the model
	 * 
	 * @param className the class to fetch
	 * @return the actual class object, or null if not found
	 */
	public Class<?> fetchModelClass(String className) throws IllegalArgumentException;
	
	/**
	 * is this controller Smart?
	 */
	public boolean isBrained();
	
	/**
	 * update the new version of the maven jar rules file
	 * 
	 * @param newGroupId - new group id
	 * @param newArtifactId - new artifact id
	 * @param newVersion - new version
	 * @param decoderConfigurations - decoder configurations
	 * @param encoderConfigurations - encoder configurations
	 * 
	 * @throws Exception from within drools libraries
	 * @throws LinkageError from within drools libraries
	 * @throws ArgumentException bad parameter passed in
	 */
	public void updateToVersion(String newGroupId, String newArtifactId, String newVersion,
			List<TopicCoderFilterConfiguration> decoderConfigurations,
			List<TopicCoderFilterConfiguration> encoderConfigurations)
	throws IllegalArgumentException, LinkageError, Exception;
	
	
	/**
	 * halts and permanently releases all resources
	 * @throws IllegalStateException
	 */
	public void halt() throws IllegalStateException;
	
	/**
	 * Factory to track and manage drools controllers
	 */
	public static final DroolsControllerFactory factory = 
									new IndexedDroolsControllerFactory();


}
