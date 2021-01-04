/*
 * ============LICENSE_START=======================================================
 * policy-management
 * ================================================================================
 * Copyright (C) 2017-2019 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2021 Nordix Foundation.
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

package org.onap.policy.drools.system;

import java.util.List;
import java.util.Properties;
import org.onap.policy.common.capabilities.Lockable;
import org.onap.policy.common.capabilities.Startable;
import org.onap.policy.common.endpoints.event.comm.Topic.CommInfrastructure;
import org.onap.policy.common.endpoints.event.comm.TopicSink;
import org.onap.policy.common.endpoints.event.comm.TopicSource;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.protocol.configuration.DroolsConfiguration;
import org.onap.policy.models.tosca.authorative.concepts.ToscaConceptIdentifier;

/**
 * A Policy Controller is the higher level unit of control. It corresponds to the ncomp equivalent
 * of a controller. It provides management of underlying resources associated with the policy
 * controller, which is a) communication infrastructure, and b) policy-core (drools) session
 * infrastructure
 *
 */
public interface PolicyController extends Startable, Lockable {

    /**
     * name of this Policy Controller.
     */
    String getName();

    /**
     * Get the topic readers of interest for this controller.
     */
    List<TopicSource> getTopicSources();

    /**
     * Get the topic readers of interest for this controller.
     */
    List<TopicSink> getTopicSinks();

    /**
     * Get the Drools Controller.
     */
    DroolsController getDrools();

    /**
     * Get Policy Types supported by this controller.
     */
    List<ToscaConceptIdentifier> getPolicyTypes();

    /**
     * Update maven configuration.
     *
     * @param newDroolsConfiguration new drools configuration
     * @return true if the update was successful, false otherwise
     */
    boolean updateDrools(DroolsConfiguration newDroolsConfiguration);

    /**
     * Get the Properties.
     */
    Properties getProperties();

    /**
     * Offer an event of type T.
     */
    <T> boolean offer(T event);

    /**
     * Attempts delivering of an String over communication infrastructure "busType".
     *
     * @param busType bus type
     * @param topic topic
     * @param event Communication infrastructure identifier
     *
     * @return true if successful, false if a failure has occurred.
     * @throws IllegalArgumentException when invalid or insufficient properties are provided
     * @throws IllegalStateException when the engine is in a state where this operation is not
     *         permitted (ie. locked or stopped).
     * @throws UnsupportedOperationException when the engine cannot deliver due to the functionality
     *         missing (ie. communication infrastructure not supported.
     */
    boolean deliver(CommInfrastructure busType, String topic, Object event);

    /**
     * halts and permanently releases all resources.
     *
     * @throws IllegalStateException throws illegal state exception
     */
    void halt();

}
