/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2020 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.controller;

import java.util.List;
import java.util.Properties;
import org.onap.policy.common.endpoints.event.comm.TopicSink;
import org.onap.policy.common.endpoints.event.comm.TopicSource;
import org.onap.policy.drools.protocol.coders.TopicCoderFilterConfiguration;

/**
 * Drools Controller Factory to manage controller creation, destruction, and retrieval for
 * management interfaces.
 */
public interface DroolsControllerFactory {

    /**
     * Constructs a Drools Controller based on properties.
     *
     * @param properties properties containing initialization parameters
     * @param eventSources list of event sources
     * @param eventSinks list of event sinks
     *
     * @return the instantiated Drools Controller
     * @throws IllegalArgumentException with invalid parameters
     * @throws LinkageError Failure to link rules and models in Drools Libraries
     */
    DroolsController build(Properties properties, List<? extends TopicSource> eventSources,
        List<? extends TopicSink> eventSinks) throws LinkageError;

    /**
     * Explicit construction of a Drools Controller.
     *
     * @param properties properties containing initialization parameters
     * @param groupId maven group id of drools artifact
     * @param artifactId maven artifact id of drools artifact
     * @param version maven version id of drools artifact
     * @param decoderConfigurations list of decoder configurations
     * @param encoderConfigurations list of encoder configurations
     *
     * @return the instantiated Drools Controller
     * @throws IllegalArgumentException with invalid parameters
     * @throws LinkageError Failure to link rules and models in Drools Libraries
     */
    DroolsController build(Properties properties, String groupId, String artifactId, String version,
        List<TopicCoderFilterConfiguration> decoderConfigurations,
        List<TopicCoderFilterConfiguration> encoderConfigurations) throws LinkageError;

    /**
     * Releases the Drools Controller from operation.
     *
     * @param controller the Drools Controller to shut down
     */
    void shutdown(DroolsController controller);

    /**
     * Disables all Drools Controllers from operation.
     */
    void shutdown();

    /**
     * Destroys and releases resources for a Drools Controller.
     *
     * @param controller the Drools Controller to destroy
     */
    void destroy(DroolsController controller);

    /**
     * Destroys all Drools Controllers.
     */
    void destroy();

    /**
     * Gets the Drools Controller associated with the maven group and artifact id.
     *
     * @param groupId maven group id of drools artifact
     * @param artifactId maven artifact id of drools artifact
     * @param version maven version id of drools artifact
     *
     * @return the Drools Controller
     * @throws IllegalArgumentException with invalid parameters
     */
    DroolsController get(String groupId, String artifactId, String version);

    /**
     * returns the current inventory of Drools Controllers.
     *
     * @return a list of Drools Controllers
     */
    List<DroolsController> inventory();
}
