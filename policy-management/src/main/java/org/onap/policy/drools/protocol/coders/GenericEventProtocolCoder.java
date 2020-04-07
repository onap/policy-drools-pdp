/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2019 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.protocol.coders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.controller.DroolsControllerConstants;
import org.onap.policy.drools.protocol.coders.EventProtocolCoder.CoderFilters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This protocol Coder that does its best attempt to decode/encode, selecting the best class and best fitted json
 * parsing tools.
 */
abstract class GenericEventProtocolCoder {
    private static final String INVALID_ARTIFACT_ID_MSG = "Invalid artifact id";
    private static final String INVALID_GROUP_ID_MSG = "Invalid group id";
    private static final String INVALID_TOPIC_MSG = "Invalid Topic";
    private static final String UNSUPPORTED_MSG = "Unsupported";
    private static final String UNSUPPORTED_EX_MSG = "Unsupported:";
    private static final String MISSING_CLASS = "class must be provided";

    private static Logger logger = LoggerFactory.getLogger(GenericEventProtocolCoder.class);

    /**
     * Mapping topic:controller-id -> /<protocol-decoder-toolset/> where protocol-coder-toolset contains
     * a gson-protocol-coder-toolset.
     */
    protected final HashMap<String, ProtocolCoderToolset> coders =
            new HashMap<>();

    /**
     * Mapping topic + classname -> Protocol Set.
     */
    protected final HashMap<String, List<ProtocolCoderToolset>>
            reverseCoders = new HashMap<>();

    GenericEventProtocolCoder() {
        super();
    }

    /**
     * Index a new coder.
     *
     * @param eventProtocolParams parameter object for event encoder
     * @throw IllegalArgumentException if an invalid parameter is passed
     */
    public void add(EventProtocolParams eventProtocolParams) {
        if (eventProtocolParams.getGroupId() == null || eventProtocolParams.getGroupId().isEmpty()) {
            throw new IllegalArgumentException(INVALID_GROUP_ID_MSG);
        }

        if (eventProtocolParams.getArtifactId() == null || eventProtocolParams.getArtifactId().isEmpty()) {
            throw new IllegalArgumentException(INVALID_ARTIFACT_ID_MSG);
        }

        if (eventProtocolParams.getTopic() == null || eventProtocolParams.getTopic().isEmpty()) {
            throw new IllegalArgumentException(INVALID_TOPIC_MSG);
        }

        if (eventProtocolParams.getEventClass() == null) {
            throw new IllegalArgumentException("Invalid Event Class");
        }

        String key = this.codersKey(eventProtocolParams.getGroupId(), eventProtocolParams.getArtifactId(),
                eventProtocolParams.getTopic());
        String reverseKey = this.reverseCodersKey(eventProtocolParams.getTopic(), eventProtocolParams.getEventClass());

        synchronized (this) {
            if (coders.containsKey(key)) {
                ProtocolCoderToolset toolset = coders.get(key);

                logger.info("{}: adding coders for existing {}: {}", this, key, toolset);

                toolset
                        .addCoder(
                                eventProtocolParams.getEventClass(),
                                eventProtocolParams.getProtocolFilter(),
                                eventProtocolParams.getModelClassLoaderHash());

                if (!reverseCoders.containsKey(reverseKey)) {
                    logger.info(
                            "{}: adding new reverse coders (multiple classes case) for {}:{}: {}",
                            this,
                            reverseKey,
                            key,
                            toolset);

                    List<ProtocolCoderToolset> reverseMappings =
                            new ArrayList<>();
                    reverseMappings.add(toolset);
                    reverseCoders.put(reverseKey, reverseMappings);
                }
                return;
            }

            GsonProtocolCoderToolset coderTools =
                    new GsonProtocolCoderToolset(eventProtocolParams, key);

            logger.info("{}: adding coders for new {}: {}", this, key, coderTools);

            coders.put(key, coderTools);

            addReverseCoder(coderTools, key, reverseKey);
        }
    }

    private void addReverseCoder(GsonProtocolCoderToolset coderTools, String key, String reverseKey) {
        if (reverseCoders.containsKey(reverseKey)) {
            // There is another controller (different group id/artifact id/topic)
            // that shares the class and the topic.

            List<ProtocolCoderToolset> toolsets =
                    reverseCoders.get(reverseKey);
            boolean present = false;
            for (ProtocolCoderToolset parserSet : toolsets) {
                // just doublecheck
                present = parserSet.getControllerId().equals(key);
                if (present) {
                    /* anomaly */
                    logger.error(
                            "{}: unexpected toolset reverse mapping found for {}:{}: {}",
                            this,
                            reverseKey,
                            key,
                            parserSet);
                }
            }

            if (!present) {
                logger.info("{}: adding coder set for {}: {} ", this, reverseKey, coderTools);
                toolsets.add(coderTools);
            }
        } else {
            List<ProtocolCoderToolset> toolsets = new ArrayList<>();
            toolsets.add(coderTools);

            logger.info("{}: adding toolset for reverse key {}: {}", this, reverseKey, toolsets);
            reverseCoders.put(reverseKey, toolsets);
        }
    }

    /**
     * produces key for indexing toolset entries.
     *
     * @param groupId    group id
     * @param artifactId artifact id
     * @param topic      topic
     * @return index key
     */
    protected String codersKey(String groupId, String artifactId, String topic) {
        return groupId + ":" + artifactId + ":" + topic;
    }

    /**
     * produces a key for the reverse index.
     *
     * @param topic      topic
     * @param eventClass coded class
     * @return reverse index key
     */
    protected String reverseCodersKey(String topic, String eventClass) {
        return topic + ":" + eventClass;
    }

    /**
     * remove coder.
     *
     * @param groupId    group id
     * @param artifactId artifact id
     * @param topic      topic
     * @throws IllegalArgumentException if invalid input
     */
    public void remove(String groupId, String artifactId, String topic) {

        if (groupId == null || groupId.isEmpty()) {
            throw new IllegalArgumentException(INVALID_GROUP_ID_MSG);
        }

        if (artifactId == null || artifactId.isEmpty()) {
            throw new IllegalArgumentException(INVALID_ARTIFACT_ID_MSG);
        }

        if (topic == null || topic.isEmpty()) {
            throw new IllegalArgumentException(INVALID_TOPIC_MSG);
        }

        String key = this.codersKey(groupId, artifactId, topic);

        synchronized (this) {
            if (coders.containsKey(key)) {
                ProtocolCoderToolset coderToolset = coders.remove(key);

                logger.info("{}: removed toolset for {}: {}", this, key, coderToolset);

                for (CoderFilters codeFilter : coderToolset.getCoders()) {
                    String className = codeFilter.getCodedClass();
                    String reverseKey = this.reverseCodersKey(topic, className);
                    removeReverseCoder(key, reverseKey);
                }
            }
        }
    }

    private void removeReverseCoder(String key, String reverseKey) {
        if (!this.reverseCoders.containsKey(reverseKey)) {
            return;
        }

        List<ProtocolCoderToolset> toolsets =
                this.reverseCoders.get(reverseKey);
        Iterator<ProtocolCoderToolset> toolsetsIter =
                toolsets.iterator();
        while (toolsetsIter.hasNext()) {
            ProtocolCoderToolset toolset = toolsetsIter.next();
            if (toolset.getControllerId().equals(key)) {
                logger.info(
                        "{}: removed coder from toolset for {} from reverse mapping", this, reverseKey);
                toolsetsIter.remove();
            }
        }

        if (this.reverseCoders.get(reverseKey).isEmpty()) {
            logger.info("{}: removing reverse mapping for {}: ", this, reverseKey);
            this.reverseCoders.remove(reverseKey);
        }
    }

    /**
     * does it support coding.
     *
     * @param groupId    group id
     * @param artifactId artifact id
     * @param topic      topic
     * @return true if its is codable
     */
    public boolean isCodingSupported(String groupId, String artifactId, String topic) {

        if (groupId == null || groupId.isEmpty()) {
            throw new IllegalArgumentException(INVALID_GROUP_ID_MSG);
        }

        if (artifactId == null || artifactId.isEmpty()) {
            throw new IllegalArgumentException(INVALID_ARTIFACT_ID_MSG);
        }

        if (topic == null || topic.isEmpty()) {
            throw new IllegalArgumentException(INVALID_TOPIC_MSG);
        }

        String key = this.codersKey(groupId, artifactId, topic);
        synchronized (this) {
            return coders.containsKey(key);
        }
    }

    /**
     * decode a json string into an Object.
     *
     * @param groupId    group id
     * @param artifactId artifact id
     * @param topic      topic
     * @param json       json string to convert to object
     * @return the decoded object
     * @throws IllegalArgumentException      if invalid argument is provided
     * @throws UnsupportedOperationException if the operation cannot be performed
     */
    public Object decode(String groupId, String artifactId, String topic, String json) {

        if (!isCodingSupported(groupId, artifactId, topic)) {
            throw new IllegalArgumentException(
                    UNSUPPORTED_EX_MSG + codersKey(groupId, artifactId, topic) + " for encoding");
        }

        String key = this.codersKey(groupId, artifactId, topic);
        ProtocolCoderToolset coderTools = coders.get(key);
        try {
            Object event = coderTools.decode(json);
            if (event != null) {
                return event;
            }
        } catch (Exception e) {
            logger.debug("{}, cannot decode {}", this, json, e);
        }

        throw new UnsupportedOperationException("Cannot decode with gson");
    }

    /**
     * encode an object into a json string.
     *
     * @param groupId    group id
     * @param artifactId artifact id
     * @param topic      topic
     * @param event      object to convert to string
     * @return the json string
     * @throws IllegalArgumentException      if invalid argument is provided
     * @throws UnsupportedOperationException if the operation cannot be performed
     */
    public String encode(String groupId, String artifactId, String topic, Object event) {

        if (!isCodingSupported(groupId, artifactId, topic)) {
            throw new IllegalArgumentException(UNSUPPORTED_EX_MSG + codersKey(groupId, artifactId, topic));
        }

        if (event == null) {
            throw new IllegalArgumentException("Unsupported topic:" + topic);
        }

        // reuse the decoder set, since there must be affinity in the model
        String key = this.codersKey(groupId, artifactId, topic);
        return this.encodeInternal(key, event);
    }

    /**
     * encode an object into a json string.
     *
     * @param topic topic
     * @param event object to convert to string
     * @return the json string
     * @throws IllegalArgumentException      if invalid argument is provided
     * @throws UnsupportedOperationException if the operation cannot be performed
     */
    public String encode(String topic, Object event) {

        if (event == null) {
            throw new IllegalArgumentException("Invalid encoded class");
        }

        if (topic == null || topic.isEmpty()) {
            throw new IllegalArgumentException("Invalid topic");
        }

        String reverseKey = this.reverseCodersKey(topic, event.getClass().getName());
        if (!this.reverseCoders.containsKey(reverseKey)) {
            throw new IllegalArgumentException("no reverse coder has been found");
        }

        List<ProtocolCoderToolset> toolsets =
                this.reverseCoders.get(reverseKey);

        String key =
                codersKey(
                        toolsets.get(0).getGroupId(), toolsets.get(0).getArtifactId(), topic);
        return this.encodeInternal(key, event);
    }

    /**
     * encode an object into a json string.
     *
     * @param topic        topic
     * @param encodedClass object to convert to string
     * @return the json string
     * @throws IllegalArgumentException      if invalid argument is provided
     * @throws UnsupportedOperationException if the operation cannot be performed
     */
    public String encode(String topic, Object encodedClass, DroolsController droolsController) {

        if (encodedClass == null) {
            throw new IllegalArgumentException("Invalid encoded class");
        }

        if (topic == null || topic.isEmpty()) {
            throw new IllegalArgumentException("Invalid topic");
        }

        String key = codersKey(droolsController.getGroupId(), droolsController.getArtifactId(), topic);
        return this.encodeInternal(key, encodedClass);
    }

    /**
     * encode an object into a json string.
     *
     * @param key   identifier
     * @param event object to convert to string
     * @return the json string
     * @throws IllegalArgumentException      if invalid argument is provided
     * @throws UnsupportedOperationException if the operation cannot be performed
     */
    protected String encodeInternal(String key, Object event) {

        logger.debug("{}: encode for {}: {}", this, key, event);

        ProtocolCoderToolset coderTools = coders.get(key);
        try {
            String json = coderTools.encode(event);
            if (json != null && !json.isEmpty()) {
                return json;
            }
        } catch (Exception e) {
            logger.warn("{}: cannot encode (first) for {}: {}", this, key, event, e);
        }

        throw new UnsupportedOperationException("Cannot decode with gson");
    }

    /**
     * Drools creators.
     *
     * @param topic        topic
     * @param encodedClass encoded class
     * @return list of controllers
     * @throws IllegalStateException    illegal state
     * @throws IllegalArgumentException argument
     */
    protected List<DroolsController> droolsCreators(String topic, Object encodedClass) {

        List<DroolsController> droolsControllers = new ArrayList<>();

        String reverseKey = this.reverseCodersKey(topic, encodedClass.getClass().getName());
        if (!this.reverseCoders.containsKey(reverseKey)) {
            logger.warn("{}: no reverse mapping for {}", this, reverseKey);
            return droolsControllers;
        }

        List<ProtocolCoderToolset> toolsets =
                this.reverseCoders.get(reverseKey);

        // There must be multiple toolsets associated with <topic,classname> reverseKey
        // case 2 different controllers use the same models and register the same encoder for
        // the same topic.  This is assumed not to occur often but for the purpose of encoding
        // but there should be no side-effects.  Ownership is crosscheck against classname and
        // classloader reference.

        if (toolsets == null || toolsets.isEmpty()) {
            throw new IllegalStateException(
                    "No Encoders toolsets available for topic "
                            + topic
                            + " encoder "
                            + encodedClass.getClass().getName());
        }

        for (ProtocolCoderToolset encoderSet : toolsets) {
            addToolsetControllers(droolsControllers, encodedClass, encoderSet);
        }

        if (droolsControllers.isEmpty()) {
            throw new IllegalStateException(
                    "No Encoders toolsets available for "
                            + topic
                            + ":"
                            + encodedClass.getClass().getName());
        }

        return droolsControllers;
    }

    private void addToolsetControllers(List<DroolsController> droolsControllers, Object encodedClass,
                    ProtocolCoderToolset encoderSet) {
        // figure out the right toolset
        String groupId = encoderSet.getGroupId();
        String artifactId = encoderSet.getArtifactId();
        List<CoderFilters> coderFilters = encoderSet.getCoders();
        for (CoderFilters coder : coderFilters) {
            if (coder.getCodedClass().equals(encodedClass.getClass().getName())) {
                DroolsController droolsController =
                                DroolsControllerConstants.getFactory().get(groupId, artifactId, "");
                if (droolsController.ownsCoder(
                        encodedClass.getClass(), coder.getModelClassLoaderHash())) {
                    droolsControllers.add(droolsController);
                }
            }
        }
    }

    /**
     * get all filters by maven coordinates and topic.
     *
     * @param groupId    group id
     * @param artifactId artifact id
     * @param topic      topic
     * @return list of coders
     * @throws IllegalArgumentException if invalid input
     */
    public List<CoderFilters> getFilters(String groupId, String artifactId, String topic) {

        if (!isCodingSupported(groupId, artifactId, topic)) {
            throw new IllegalArgumentException(UNSUPPORTED_EX_MSG + codersKey(groupId, artifactId, topic));
        }

        String key = this.codersKey(groupId, artifactId, topic);
        ProtocolCoderToolset coderTools = coders.get(key);
        return coderTools.getCoders();
    }

    /**
     * get all coders by maven coordinates and topic.
     *
     * @param groupId    group id
     * @param artifactId artifact id
     * @return list of coders
     * @throws IllegalArgumentException if invalid input
     */
    public List<CoderFilters> getFilters(String groupId, String artifactId) {

        if (groupId == null || groupId.isEmpty()) {
            throw new IllegalArgumentException(INVALID_GROUP_ID_MSG);
        }

        if (artifactId == null || artifactId.isEmpty()) {
            throw new IllegalArgumentException(INVALID_ARTIFACT_ID_MSG);
        }

        String key = this.codersKey(groupId, artifactId, "");

        List<CoderFilters> codersFilters = new ArrayList<>();
        for (Map.Entry<String, ProtocolCoderToolset> entry :
                coders.entrySet()) {
            if (entry.getKey().startsWith(key)) {
                codersFilters.addAll(entry.getValue().getCoders());
            }
        }

        return codersFilters;
    }

    /**
     * get all filters by maven coordinates, topic, and classname.
     *
     * @param groupId    group id
     * @param artifactId artifact id
     * @param topic      topic
     * @param classname  classname
     * @return list of coders
     * @throws IllegalArgumentException if invalid input
     */
    public CoderFilters getFilters(
            String groupId, String artifactId, String topic, String classname) {

        if (!isCodingSupported(groupId, artifactId, topic)) {
            throw new IllegalArgumentException(UNSUPPORTED_EX_MSG + codersKey(groupId, artifactId, topic));
        }

        if (classname == null || classname.isEmpty()) {
            throw new IllegalArgumentException("classname must be provided");
        }

        String key = this.codersKey(groupId, artifactId, topic);
        ProtocolCoderToolset coderTools = coders.get(key);
        return coderTools.getCoder(classname);
    }

    /**
     * get all coders by maven coordinates and topic.
     *
     * @param groupId    group id
     * @param artifactId artifact id
     * @param topic      topic
     * @return list of coders
     * @throws IllegalArgumentException if invalid input
     */
    public ProtocolCoderToolset getCoders(
            String groupId, String artifactId, String topic) {

        if (!isCodingSupported(groupId, artifactId, topic)) {
            throw new IllegalArgumentException(UNSUPPORTED_EX_MSG + codersKey(groupId, artifactId, topic));
        }

        String key = this.codersKey(groupId, artifactId, topic);
        return coders.get(key);
    }

    /**
     * get all coders by maven coordinates and topic.
     *
     * @param groupId    group id
     * @param artifactId artifact id
     * @return list of coders
     * @throws IllegalArgumentException if invalid input
     */
    public List<ProtocolCoderToolset> getCoders(
            String groupId, String artifactId) {

        if (groupId == null || groupId.isEmpty()) {
            throw new IllegalArgumentException(INVALID_GROUP_ID_MSG);
        }

        if (artifactId == null || artifactId.isEmpty()) {
            throw new IllegalArgumentException(INVALID_ARTIFACT_ID_MSG);
        }

        String key = this.codersKey(groupId, artifactId, "");

        List<ProtocolCoderToolset> coderToolset = new ArrayList<>();
        for (Map.Entry<String, ProtocolCoderToolset> entry :
                coders.entrySet()) {
            if (entry.getKey().startsWith(key)) {
                coderToolset.add(entry.getValue());
            }
        }

        return coderToolset;
    }

    /**
     * get coded based on class and topic.
     *
     * @param topic      topic
     * @param codedClass class
     * @return list of reverse filters
     */
    public List<CoderFilters> getReverseFilters(String topic, String codedClass) {

        if (topic == null || topic.isEmpty()) {
            throw new IllegalArgumentException(UNSUPPORTED_MSG);
        }

        if (codedClass == null) {
            throw new IllegalArgumentException(MISSING_CLASS);
        }

        String key = this.reverseCodersKey(topic, codedClass);
        List<ProtocolCoderToolset> toolsets = this.reverseCoders.get(key);
        if (toolsets == null) {
            throw new IllegalArgumentException("No Coder found for " + key);
        }

        List<CoderFilters> coderFilters = new ArrayList<>();
        for (ProtocolCoderToolset toolset : toolsets) {
            coderFilters.addAll(toolset.getCoders());
        }

        return coderFilters;
    }

    /**
     * returns group and artifact id of the creator of the encoder.
     *
     * @param topic topic
     * @param fact  fact
     * @return the drools controller
     */
    DroolsController getDroolsController(String topic, Object fact) {

        if (topic == null || topic.isEmpty()) {
            throw new IllegalArgumentException(UNSUPPORTED_MSG);
        }

        if (fact == null) {
            throw new IllegalArgumentException(MISSING_CLASS);
        }

        List<DroolsController> droolsControllers = droolsCreators(topic, fact);

        if (droolsControllers.isEmpty()) {
            throw new IllegalArgumentException("Invalid Topic: " + topic);
        }

        if (droolsControllers.size() > 1) {
            logger.warn(
                    "{}: multiple drools-controller {} for {}:{} ",
                    this,
                    droolsControllers,
                    topic,
                    fact.getClass().getName());
            // continue
        }
        return droolsControllers.get(0);
    }

    /**
     * returns group and artifact id of the creator of the encoder.
     *
     * @param topic topic
     * @param fact  fact
     * @return list of drools controllers
     */
    List<DroolsController> getDroolsControllers(String topic, Object fact) {

        if (topic == null || topic.isEmpty()) {
            throw new IllegalArgumentException(UNSUPPORTED_MSG);
        }

        if (fact == null) {
            throw new IllegalArgumentException(MISSING_CLASS);
        }

        List<DroolsController> droolsControllers = droolsCreators(topic, fact);
        if (droolsControllers.size() > 1) {
            // unexpected
            logger.warn(
                    "{}: multiple drools-controller {} for {}:{} ",
                    this,
                    droolsControllers,
                    topic,
                    fact.getClass().getName());
            // continue
        }
        return droolsControllers;
    }

    @Override
    public String toString() {
        return "GenericEventProtocolCoder [coders="
                + coders.keySet()
                + ", reverseCoders="
                + reverseCoders.keySet()
                + "]";
    }
}
