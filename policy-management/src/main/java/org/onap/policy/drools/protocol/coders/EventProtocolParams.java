/*-
 * ============LICENSE_START=======================================================
 *  Copyright (C) 2018 Samsung Electronics Co., Ltd. All rights reserved.
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
 *
 * SPDX-License-Identifier: Apache-2.0
 * ============LICENSE_END=========================================================
 */

package org.onap.policy.drools.protocol.coders;

import org.onap.policy.drools.protocol.coders.TopicCoderFilterConfiguration.CustomCoder;

public class EventProtocolParams {
    private String groupId;
    private String artifactId;
    private String topic;
    private String eventClass;
    private JsonProtocolFilter protocolFilter;
    private TopicCoderFilterConfiguration.CustomGsonCoder customGsonCoder;
    private TopicCoderFilterConfiguration.CustomJacksonCoder customJacksonCoder;
    private int modelClassLoaderHash;

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getTopic() {
        return topic;
    }

    public String getEventClass() {
        return eventClass;
    }

    public JsonProtocolFilter getProtocolFilter() {
        return protocolFilter;
    }

    public TopicCoderFilterConfiguration.CustomGsonCoder getCustomGsonCoder() {
        return customGsonCoder;
    }

    public TopicCoderFilterConfiguration.CustomJacksonCoder getCustomJacksonCoder() {
        return customJacksonCoder;
    }

    public int getModelClassLoaderHash() {
        return modelClassLoaderHash;
    }

    public static EventProtocolParams builder() {
        return new EventProtocolParams();
    }

    /**
     * Setter method.
     *
     * @param groupId of the controller
     * @return EventProtocolParams
     */
    public EventProtocolParams groupId(String groupId) {
        this.groupId = groupId;
        return this;
    }

    /**
     * Setter method.
     *
     * @param artifactId of the controller
     * @return EventProtocolParams
     */
    public EventProtocolParams artifactId(String artifactId) {
        this.artifactId = artifactId;
        return this;
    }

    /**
     * Setter method.
     *
     * @param topic the topic
     * @return EventProtocolParams
     */
    public EventProtocolParams topic(String topic) {
        this.topic = topic;
        return this;
    }

    /**
     * Setter method.
     *
     * @param eventClass the event class
     * @return EventProtocolParams
     */
    public EventProtocolParams eventClass(String eventClass) {
        this.eventClass = eventClass;
        return this;
    }

    /**
     * Setter method.
     *
     * @param protocolFilter filters to selectively choose a particular decoder
     *                       when there are multiples
     * @return EventProtocolParams
     */
    public EventProtocolParams protocolFilter(JsonProtocolFilter protocolFilter) {
        this.protocolFilter = protocolFilter;
        return this;
    }

    /**
     * Setter method.
     *
     * @param customGsonCoder custom gscon coder
     * @return EventProtocolParams
     */
    public EventProtocolParams customGsonCoder(
            TopicCoderFilterConfiguration.CustomGsonCoder customGsonCoder) {
        this.customGsonCoder = customGsonCoder;
        return this;
    }

    /**
     * Setter method.
     *
     * @param customJacksonCoder custom Jackson coder
     * @return EventProtocolParams
     */
    public EventProtocolParams customJacksonCoder(
            TopicCoderFilterConfiguration.CustomJacksonCoder customJacksonCoder) {
        this.customJacksonCoder = customJacksonCoder;
        return this;
    }

    /**
     * Setter method.
     * @param modelClassLoaderHash integer representing model hash
     * @return EventProtocolParams
     */
    public EventProtocolParams modelClassLoaderHash(int modelClassLoaderHash) {
        this.modelClassLoaderHash = modelClassLoaderHash;
        return this;
    }

    public CustomCoder getCustomCoder() {
        return this.customGsonCoder != null ? this.customGsonCoder : this.customJacksonCoder;
    }
}
