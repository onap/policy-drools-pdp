/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2017-2022 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2018 Samsung Electronics Co., Ltd.
 * Modifications Copyright (C) 2024 Nordix Foundation.
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
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.onap.policy.drools.protocol.coders.EventProtocolCoder.CoderFilters;
import org.onap.policy.drools.protocol.coders.TopicCoderFilterConfiguration.CustomCoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Protocol Coding/Decoding Toolset.
 */
@Getter
public abstract class ProtocolCoderToolset {

    /**
     * Logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(ProtocolCoderToolset.class);

    /**
     * topic.
     */
    protected final String topic;

    /**
     * controller id.
     */
    protected final String controllerId;

    /**
     * group id.
     */
    protected final String groupId;

    /**
     * artifact id.
     */
    protected final String artifactId;

    /**
     * Protocols and associated Filters.
     */
    protected final List<CoderFilters> coders = new CopyOnWriteArrayList<>();

    /**
     * custom coder.
     */
    @Setter
    protected CustomCoder customCoder;

    /**
     * Constructor.
     *
     * @param eventProtocolParams parameter object for event encoder
     * @param controllerId the controller id
     * @throws IllegalArgumentException if invalid data has been passed in
     */
    protected ProtocolCoderToolset(EventProtocolParams eventProtocolParams, String controllerId) {

        if (eventProtocolParams == null || StringUtils.isBlank(controllerId)) {
            throw new IllegalArgumentException("Invalid input");
        }

        this.topic = eventProtocolParams.getTopic();
        this.controllerId = controllerId;
        this.groupId = eventProtocolParams.getGroupId();
        this.artifactId = eventProtocolParams.getArtifactId();
        this.coders.add(new CoderFilters(
                eventProtocolParams.getEventClass(),
                eventProtocolParams.getProtocolFilter(),
                eventProtocolParams.getModelClassLoaderHash()));
        this.customCoder = eventProtocolParams.getCustomGsonCoder();
    }

    /**
     * gets the coder + filters associated with this class name.
     *
     * @param classname class name
     * @return the decoder filters or null if not found
     */
    public CoderFilters getCoder(String classname) {
        if (StringUtils.isBlank(classname)) {
            throw new IllegalArgumentException("no classname provided");
        }

        for (final CoderFilters decoder : this.coders) {
            if (decoder.factClass.equals(classname)) {
                return decoder;
            }
        }
        return null;
    }

    /**
     * get a copy of the coder filters in use.
     *
     * @return coder filters
     */
    public List<CoderFilters> getCoders() {
        return new ArrayList<>(this.coders);
    }

    /**
     * add coder or replace it exists.
     *
     * @param eventClass decoder
     * @param filter filter
     */
    public void addCoder(String eventClass, JsonProtocolFilter filter, int modelClassLoaderHash) {
        if (StringUtils.isBlank(eventClass)) {
            throw new IllegalArgumentException("no event class provided");
        }

        for (final CoderFilters coder : this.coders) {
            if (coder.getFactClass().equals(eventClass)) {
                coder.setFilter(filter);
                coder.setModelClassLoaderHash(modelClassLoaderHash);
                return;
            }
        }
        this.coders.add(new CoderFilters(eventClass, filter, modelClassLoaderHash));
    }

    /**
     * remove coder.
     *
     * @param eventClass event class
     */
    public void removeCoders(String eventClass) {
        if (StringUtils.isBlank(eventClass)) {
            throw new IllegalArgumentException("no event class provided");
        }

        List<CoderFilters> temp = new ArrayList<>();
        for (final CoderFilters coder : this.coders) {
            if (coder.factClass.equals(eventClass)) {
                temp.add(coder);
            }
        }

        this.coders.removeAll(temp);
    }

    /**
     * performs filtering on a json string.
     *
     * @param json json string
     * @return the decoder that passes the filter, otherwise null
     * @throws UnsupportedOperationException can't filter
     * @throws IllegalArgumentException invalid input
     */
    protected CoderFilters filter(String json) {


        // 1. Get list of decoding classes for this controller Id and topic
        // 2. If there are no classes, return error
        // 3. Otherwise, from the available classes for decoding, pick the first one that
        // passes the filters

        // Don't parse if it is not necessary

        if (this.coders.isEmpty()) {
            throw new IllegalStateException("No coders available");
        }

        for (final CoderFilters decoder : this.coders) {
            try {
                boolean accepted = decoder.getFilter().accept(json);
                if (accepted) {
                    return decoder;
                }
            } catch (final Exception e) {
                logger.info("{}: unexpected failure accepting {} because of {}", this, json,
                        e.getMessage(), e);
                // continue
            }
        }

        return null;
    }

    /**
     * Decode json into a POJO object.
     *
     * @param json json string
     *
     * @return a POJO object for the json string
     * @throws IllegalArgumentException if an invalid parameter has been received
     * @throws UnsupportedOperationException if parsing into POJO is not possible
     */
    public abstract Object decode(String json);

    /**
     * Encodes a POJO object into a JSON String.
     *
     * @param event JSON POJO event to be converted to String
     * @return JSON string version of POJO object
     * @throws IllegalArgumentException if an invalid parameter has been received
     * @throws UnsupportedOperationException if parsing into POJO is not possible
     */
    public abstract String encode(Object event);

    @Override
    public String toString() {
        return "ProtocolCoderToolset [topic=" + this.topic + ", controllerId="
                + this.controllerId + ", groupId=" + groupId + ", artifactId="
                + this.artifactId + ", coders=" + this.coders
                + ", customCoder=" + this.customCoder + "]";
    }
}
