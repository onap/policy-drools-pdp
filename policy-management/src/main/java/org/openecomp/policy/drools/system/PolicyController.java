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

package org.openecomp.policy.drools.system;

import java.util.List;
import java.util.Properties;

import org.openecomp.policy.drools.controller.DroolsController;
import org.openecomp.policy.drools.event.comm.TopicSource;
import org.openecomp.policy.drools.event.comm.Topic.CommInfrastructure;
import org.openecomp.policy.drools.event.comm.TopicSink;
import org.openecomp.policy.drools.properties.Lockable;
import org.openecomp.policy.drools.properties.Startable;
import org.openecomp.policy.drools.protocol.configuration.DroolsConfiguration;

/**
 * A Policy Controller is the higher level unit of control.  It corresponds to
 * the ncomp equivalent of a controller.  It provides management of underlying
 * resources associated with the policy controller, which is a) communication
 * infrastructure, and b) policy-core (drools) session infrastructure
 *
 */
public interface PolicyController extends Startable, Lockable {
	
	/**
	 * name of this Policy Controller
	 */
	public String getName();
	
	/**
	 * Get the topic readers of interest for this controller
	 */
	public List<? extends TopicSource> getTopicSources();
	
	/**
	 * Get the topic readers of interest for this controller
	 */
	public List<? extends TopicSink> getTopicSinks();
	
	/**
	 * Get the Drools Controller
	 */
	public DroolsController getDrools();
	
	/**
	 * update maven configuration
	 * 
	 * @param newDroolsConfiguration new drools configuration
	 * @return true if the update was successful, false otherwise
	 */	
	public boolean updateDrools(DroolsConfiguration newDroolsConfiguration);
	
	/**
	 * Get the Properties
	 */
	public Properties getProperties();
	
	/**
	 * Attempts delivering of an String over communication 
	 * infrastructure "busType"
	 * 
	 * @param eventBus Communication infrastructure identifier
	 * @param topic topic
	 * @param event the event object to send
	 * 
	 * @return true if successful, false if a failure has occurred.
	 * @throws IllegalArgumentException when invalid or insufficient 
	 *         properties are provided
	 * @throws IllegalStateException when the engine is in a state where
	 *         this operation is not permitted (ie. locked or stopped).
	 * @throws UnsupportedOperationException when the engine cannot deliver due
	 *         to the functionality missing (ie. communication infrastructure
	 *         not supported.
	 */
	public boolean deliver(CommInfrastructure busType, String topic, 
			               Object event)
			throws IllegalArgumentException, IllegalStateException, 
			       UnsupportedOperationException;
	
	/**
	 * halts and permanently releases all resources
	 * @throws IllegalStateException
	 */
	public void halt() throws IllegalStateException;
	
	/**
	 * Factory that tracks and manages Policy Controllers
	 */
	public static PolicyControllerFactory factory =
						new IndexedPolicyControllerFactory();
	
}
