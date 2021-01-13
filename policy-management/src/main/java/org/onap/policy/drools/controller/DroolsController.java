/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2017-2019, 2021 AT&T Intellectual Property. All rights reserved.
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
import java.util.Map;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.onap.policy.common.capabilities.Lockable;
import org.onap.policy.common.capabilities.Startable;
import org.onap.policy.common.endpoints.event.comm.TopicSink;
import org.onap.policy.drools.core.PolicyContainer;
import org.onap.policy.drools.protocol.coders.TopicCoderFilterConfiguration;

/**
 * Drools Controller is the abstractions that wraps the drools layer (policy-core).
 */
public interface DroolsController extends Startable, Lockable {

    /**
     * get group id.
     *
     * @return group id
     */
    String getGroupId();

    /**
     * get artifact id.
     *
     * @return artifact id
     */
    String getArtifactId();

    /**
     * get version.
     *
     * @return version
     */
    String getVersion();

    /**
     * return the policy session names.
     *
     * @return policy session
     */
    List<String> getSessionNames();

    /**
     * return the policy full session names.
     *
     * @return policy session
     */
    List<String> getCanonicalSessionNames();

    /**
     * get base domains.
     *
     * @return list of base domains.
     */
    List<String> getBaseDomainNames();

    /**
     * offers a raw event to this controller for processing.
     *
     * @param topic topic associated with the event
     * @param event the event
     *
     * @return true if the operation was successful
     */
    boolean offer(String topic, String event);

    /**
     * offers a T event to this controller for processing.
     *
     * @param event the event
     *
     * @return true if the operation was successful
     */
    <T> boolean offer(T event);

    /**
     * delivers "event" to "sink".
     *
     * @param sink destination
     * @param event event
     * @return true if successful, false if a failure has occurred.
     * @throws IllegalArgumentException when invalid or insufficient properties are provided
     * @throws IllegalStateException when the engine is in a state where this operation is not
     *         permitted (ie. locked or stopped).
     * @throws UnsupportedOperationException when the engine cannot deliver due to the functionality
     *         missing (ie. communication infrastructure not supported.
     */
    boolean deliver(TopicSink sink, Object event);

    /**
     * Get recent source events.
     *
     * @return the most recent received events.
     */
    Object[] getRecentSourceEvents();

    /**
     * Get recent sink events.
     *
     * @return the most recent delivered events
     */
    String[] getRecentSinkEvents();

    /**
     * Get container.
     *
     * @return the underlying policy container
     */
    PolicyContainer getContainer();

    /**
     * Does it owns the coder.
     *
     * @param coderClass the encoder object
     * @param modelHash the hash for the model
     * @return true it owns it
     */
    boolean ownsCoder(Class<?> coderClass, int modelHash);

    /**
     * fetches a class from the model.
     *
     * @param className the class to fetch
     * @return the actual class object, or null if not found
     */
    Class<?> fetchModelClass(String className);

    /**
     * is this controller Smart.
     */
    boolean isBrained();

    /**
     * update the new version of the maven jar rules file.
     *
     * @param newGroupId - new group id
     * @param newArtifactId - new artifact id
     * @param newVersion - new version
     * @param decoderConfigurations - decoder configurations
     * @param encoderConfigurations - encoder configurations
     *
     * @throws Exception from within drools libraries
     * @throws LinkageError from within drools libraries
     */
    void updateToVersion(String newGroupId, String newArtifactId, String newVersion,
        List<TopicCoderFilterConfiguration> decoderConfigurations,
        List<TopicCoderFilterConfiguration> encoderConfigurations) throws LinkageError;

    /**
     * gets the classnames of facts as well as the current count.
     *
     * @param sessionName the session name
     * @return map of class to count
     */
    Map<String, Integer> factClassNames(String sessionName);

    /**
     * gets the count of facts for a given session.
     *
     * @param sessionName the session name
     * @return the fact count
     */
    long factCount(String sessionName);

    /**
     * gets all the facts of a given class for a given session.
     *
     * @param sessionName the session identifier
     * @param className the class type
     * @param delete retract from drools the results of the query?
     * @return the list of facts returned by the query
     */
    List<Object> facts(String sessionName, String className, boolean delete);

    /**
     * Gets facts.
     */
    <T> List<T> facts(@NonNull String sessionName, @NonNull Class<T> clazz);

    /**
     * gets the facts associated with a query for a give session for a given queried entity.
     *
     * @param sessionName the session
     * @param queryName the query identifier
     * @param queriedEntity the queried entity
     * @param delete retract from drools the results of the query?
     * @param queryParams query parameters
     * @return list of facts returned by the query
     */
    List<Object> factQuery(String sessionName, String queryName, String queriedEntity, boolean delete,
        Object... queryParams);

    /**
     * Deletes a fact from a session.
     */
    <T> boolean delete(@NonNull String sessionName, @NonNull T fact);

    /**
     * Delete a fact object of type T.
     */
    <T> boolean delete(@NonNull T fact);

    /**
     * Deletes all facts from a given class from a session.
     */
    <T> boolean delete(@NonNull String sessionName, @NonNull Class<T> fact);

    /**
     * Deletes all facts from a given class from all sessions.
     */
    <T> boolean delete(@NonNull Class<T> fact);

    /**
     * Checks is a fact exists in a given session.
     */
    <T> boolean exists(@NonNull String sessionName, @NonNull T fact);

    /**
     * Checks if a fact exists in any session.
     */
    <T> boolean exists(@NonNull T fact);


    /**
     * halts and permanently releases all resources.
     *
     */
    void halt();
}
