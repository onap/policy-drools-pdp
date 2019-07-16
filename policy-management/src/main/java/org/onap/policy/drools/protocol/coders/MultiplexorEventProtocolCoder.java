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
import java.util.List;
import org.onap.policy.drools.controller.DroolsController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Protocol Coder that does its best attempt to decode/encode, selecting the best class and best fitted json parsing
 * tools.
 */
class MultiplexorEventProtocolCoder implements EventProtocolCoder {

    /**
     * Logger.
     */
    private static Logger logger = LoggerFactory.getLogger(MultiplexorEventProtocolCoder.class);

    /**
     * Decoders.
     */
    protected EventProtocolDecoder decoders = new EventProtocolDecoder();

    /**
     * Encoders.
     */
    protected EventProtocolEncoder encoders = new EventProtocolEncoder();

    /**
     * {@inheritDoc}.
     */
    @Override
    public void addDecoder(EventProtocolParams eventProtocolParams) {
        logger.info(
                "{}: add-decoder {}:{}:{}:{}:{}:{}:{}",
                this,
                eventProtocolParams.getGroupId(),
                eventProtocolParams.getArtifactId(),
                eventProtocolParams.getTopic(),
                eventProtocolParams.getEventClass(),
                eventProtocolParams.getProtocolFilter(),
                eventProtocolParams.getCustomGsonCoder(),
                eventProtocolParams.getModelClassLoaderHash());
        this.decoders.add(eventProtocolParams);
    }

    /**
     * {@inheritDoc}.
     *
     * @param eventProtocolParams parameter object for event encoder
     */
    @Override
    public void addEncoder(EventProtocolParams eventProtocolParams) {
        logger.info(
                "{}: add-decoder {}:{}:{}:{}:{}:{}:{}",
                this,
                eventProtocolParams.getGroupId(),
                eventProtocolParams.getArtifactId(),
                eventProtocolParams.getTopic(),
                eventProtocolParams.getEventClass(),
                eventProtocolParams.getProtocolFilter(),
                eventProtocolParams.getCustomGsonCoder(),
                eventProtocolParams.getModelClassLoaderHash());
        this.encoders.add(eventProtocolParams);
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public void removeDecoders(String groupId, String artifactId, String topic) {
        logger.info("{}: remove-decoder {}:{}:{}", this, groupId, artifactId, topic);
        this.decoders.remove(groupId, artifactId, topic);
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public void removeEncoders(String groupId, String artifactId, String topic) {
        logger.info("{}: remove-encoder {}:{}:{}", this, groupId, artifactId, topic);
        this.encoders.remove(groupId, artifactId, topic);
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public boolean isDecodingSupported(String groupId, String artifactId, String topic) {
        return this.decoders.isCodingSupported(groupId, artifactId, topic);
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public boolean isEncodingSupported(String groupId, String artifactId, String topic) {
        return this.encoders.isCodingSupported(groupId, artifactId, topic);
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public Object decode(String groupId, String artifactId, String topic, String json) {
        logger.debug("{}: decode {}:{}:{}:{}", this, groupId, artifactId, topic, json);
        return this.decoders.decode(groupId, artifactId, topic, json);
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public String encode(String groupId, String artifactId, String topic, Object event) {
        logger.debug("{}: encode {}:{}:{}:{}", this, groupId, artifactId, topic, event);
        return this.encoders.encode(groupId, artifactId, topic, event);
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public String encode(String topic, Object event) {
        logger.debug("{}: encode {}:{}", this, topic, event);
        return this.encoders.encode(topic, event);
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public String encode(String topic, Object event, DroolsController droolsController) {
        logger.debug("{}: encode {}:{}:{}", this, topic, event, droolsController);
        return this.encoders.encode(topic, event, droolsController);
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public List<CoderFilters> getDecoderFilters(String groupId, String artifactId, String topic) {
        return this.decoders.getFilters(groupId, artifactId, topic);
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public CoderFilters getDecoderFilters(
            String groupId, String artifactId, String topic, String classname) {
        return this.decoders.getFilters(groupId, artifactId, topic, classname);
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public List<CoderFilters> getDecoderFilters(String groupId, String artifactId) {
        return this.decoders.getFilters(groupId, artifactId);
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public ProtocolCoderToolset getDecoders(String groupId, String artifactId, String topic) {
        ProtocolCoderToolset decoderToolsets =
                this.decoders.getCoders(groupId, artifactId, topic);
        if (decoderToolsets == null) {
            throw new IllegalArgumentException(
                    "Decoders not found for " + groupId + ":" + artifactId + ":" + topic);
        }

        return decoderToolsets;
    }

    /**
     * get all deocders by maven coordinates and topic.
     *
     * @param groupId    group id
     * @param artifactId artifact id
     * @return list of decoders
     * @throws IllegalArgumentException if invalid input
     */
    @Override
    public List<ProtocolCoderToolset> getDecoders(String groupId, String artifactId) {

        List<ProtocolCoderToolset> decoderToolsets =
                this.decoders.getCoders(groupId, artifactId);
        if (decoderToolsets == null) {
            throw new IllegalArgumentException("Decoders not found for " + groupId + ":" + artifactId);
        }

        return new ArrayList<>(decoderToolsets);
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public List<CoderFilters> getEncoderFilters(String groupId, String artifactId, String topic) {
        return this.encoders.getFilters(groupId, artifactId, topic);
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public CoderFilters getEncoderFilters(
            String groupId, String artifactId, String topic, String classname) {
        return this.encoders.getFilters(groupId, artifactId, topic, classname);
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public List<CoderFilters> getEncoderFilters(String groupId, String artifactId) {
        return this.encoders.getFilters(groupId, artifactId);
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public List<CoderFilters> getReverseEncoderFilters(String topic, String encodedClass) {
        return this.encoders.getReverseFilters(topic, encodedClass);
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public DroolsController getDroolsController(String topic, Object encodedClass) {
        return this.encoders.getDroolsController(topic, encodedClass);
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public List<DroolsController> getDroolsControllers(String topic, Object encodedClass) {
        return this.encoders.getDroolsControllers(topic, encodedClass);
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public String toString() {
        return "MultiplexorEventProtocolCoder [decoders="
                + decoders
                + ", encoders="
                + encoders
                + "]";
    }
}
