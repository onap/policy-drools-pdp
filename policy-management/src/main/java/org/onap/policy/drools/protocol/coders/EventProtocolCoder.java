/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2017-2019 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright(C) 2018 Samsung Electronics Co., Ltd.
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

import java.util.List;
import org.onap.policy.drools.controller.DroolsController;

/**
 * Coder (Encoder/Decoder) of Events.
 */
public interface EventProtocolCoder {

    public static class CoderFilters {

        /**
         * coder class.
         */
        protected String factClass;

        /**
         * filters to apply to the selection of the decodedClass.
         */
        protected JsonProtocolFilter filter;

        /**
         * classloader hash.
         */
        protected int modelClassLoaderHash;

        /**
         * constructor.
         *
         * @param codedClass coder class
         * @param filter     filters to apply
         */
        public CoderFilters(String codedClass, JsonProtocolFilter filter, int modelClassLoaderHash) {
            this.factClass = codedClass;
            this.filter = filter;
            this.modelClassLoaderHash = modelClassLoaderHash;
        }

        /**
         * Get coded class.
         *
         * @return the codedClass
         */
        public String getCodedClass() {
            return factClass;
        }

        /**
         * Set coded class.
         *
         * @param codedClass the decodedClass to set
         */
        public void setCodedClass(String codedClass) {
            this.factClass = codedClass;
        }

        /**
         * Get filter.
         *
         * @return the filter
         */
        public JsonProtocolFilter getFilter() {
            return filter;
        }

        /**
         * Set filter.
         *
         * @param filter the filter to set
         */
        public void setFilter(JsonProtocolFilter filter) {
            this.filter = filter;
        }

        public int getModelClassLoaderHash() {
            return modelClassLoaderHash;
        }

        public void setFromClassLoaderHash(int fromClassLoaderHash) {
            this.modelClassLoaderHash = fromClassLoaderHash;
        }

        @Override
        public String toString() {
            return "CoderFilters [factClass="
                    + factClass
                    + ", filter="
                    + filter
                    + ", modelClassLoaderHash="
                    + modelClassLoaderHash
                    + "]";
        }
    }

    /**
     * Adds a Decoder class to decode the protocol over this topic.
     *
     * @param eventProtocolParams parameter object for event protocol
     * @throw IllegalArgumentException if an invalid parameter is passed
     */
    void addDecoder(EventProtocolParams eventProtocolParams);

    /**
     * removes all decoders associated with the controller id.
     *
     * @param groupId    of the controller
     * @param artifactId of the controller
     * @param topic      of the controller
     * @throws IllegalArgumentException if invalid arguments have been provided
     */
    void removeEncoders(String groupId, String artifactId, String topic);

    /**
     * removes decoders associated with the controller id and topic.
     *
     * @param groupId    of the controller
     * @param artifactId of the controller
     * @param topic      the topic
     * @throws IllegalArgumentException if invalid arguments have been provided
     */
    void removeDecoders(String groupId, String artifactId, String topic);

    /**
     * Given a controller id and a topic, it gives back its filters.
     *
     * @param groupId    of the controller
     * @param artifactId of the controller
     * @param topic      the topic
     * @return list of decoders
     * @throw IllegalArgumentException if an invalid parameter is passed
     */
    List<CoderFilters> getDecoderFilters(String groupId, String artifactId, String topic);

    /**
     * gets all decoders associated with the group and artifact ids.
     *
     * @param groupId    of the controller
     * @param artifactId of the controller
     * @throws IllegalArgumentException if invalid arguments have been provided
     */
    List<CoderFilters> getDecoderFilters(String groupId, String artifactId);

    /**
     * Given a controller id, a topic, and a classname, it gives back the classes that implements the decoding.
     *
     * @param groupId    of the controller
     * @param artifactId of the controller
     * @param topic      the topic
     * @param classname  classname
     * @return list of decoders
     * @throw IllegalArgumentException if an invalid parameter is passed
     */
    CoderFilters getDecoderFilters(
        String groupId, String artifactId, String topic, String classname);

    /**
     * Given a controller id and a topic, it gives back the decoding configuration.
     *
     * @param groupId    of the controller
     * @param artifactId of the controller
     * @param topic      the topic
     * @return decoding toolset
     * @throw IllegalArgumentException if an invalid parameter is passed
     */
    ProtocolCoderToolset getDecoders(String groupId, String artifactId, String topic);

    /**
     * Given a controller id and a topic, it gives back all the decoding configurations.
     *
     * @param groupId    of the controller
     * @param artifactId of the controller
     * @return decoding toolset
     * @throw IllegalArgumentException if an invalid parameter is passed
     */
    List<ProtocolCoderToolset> getDecoders(String groupId, String artifactId);

    /**
     * Given a controller id and a topic, it gives back the classes that implements the encoding.
     *
     * @param groupId    of the controller
     * @param artifactId of the controller
     * @param topic      the topic
     * @return list of decoders
     * @throw IllegalArgumentException if an invalid parameter is passed
     */
    List<CoderFilters> getEncoderFilters(String groupId, String artifactId, String topic);

    /**
     * gets all encoders associated with the group and artifact ids.
     *
     * @param groupId    of the controller
     * @param artifactId of the controller
     * @return List of filters
     * @throws IllegalArgumentException if invalid arguments have been provided
     */
    List<CoderFilters> getEncoderFilters(String groupId, String artifactId);

    /**
     * get encoder based on coordinates and classname.
     *
     * @param groupId    of the controller
     * @param artifactId of the controller
     * @param topic      protocol
     * @param classname  name of the class
     * @return CoderFilters decoders
     * @throws IllegalArgumentException invalid arguments passed in
     */
    CoderFilters getEncoderFilters(
        String groupId, String artifactId, String topic, String classname);

    /**
     * is there a decoder supported for the controller id and topic.
     *
     * @param groupId    of the controller
     * @param artifactId of the controller
     * @param topic      protocol
     * @return true if supported
     */
    boolean isDecodingSupported(String groupId, String artifactId, String topic);

    /**
     * Adds a Encoder class to encode the protocol over this topic.
     *
     * @param eventProtocolParams parameter object for event protocol
     * @throw IllegalArgumentException if an invalid parameter is passed
     */
    void addEncoder(EventProtocolParams eventProtocolParams);

    /**
     * is there an encoder supported for the controller id and topic.
     *
     * @param groupId    of the controller
     * @param artifactId of the controller
     * @param topic      protocol
     * @return true if supported
     */
    boolean isEncodingSupported(String groupId, String artifactId, String topic);

    /**
     * get encoder based on topic and encoded class.
     *
     * @param topic        topic
     * @param encodedClass encoded class
     * @return list of filters
     * @throws IllegalArgumentException invalid arguments passed in
     */
    List<CoderFilters> getReverseEncoderFilters(String topic, String encodedClass);

    /**
     * gets the identifier of the creator of the encoder.
     *
     * @param topic        topic
     * @param encodedClass encoded class
     * @return a drools controller
     * @throws IllegalArgumentException invalid arguments passed in
     */
    DroolsController getDroolsController(String topic, Object encodedClass);

    /**
     * gets the identifier of the creator of the encoder.
     *
     * @param topic        topic
     * @param encodedClass encoded class
     * @return list of drools controllers
     * @throws IllegalArgumentException invalid arguments passed in
     */
    List<DroolsController> getDroolsControllers(String topic, Object encodedClass);

    /**
     * decode topic's stringified event (json) to corresponding Event Object.
     *
     * @param groupId    of the controller
     * @param artifactId of the controller
     * @param topic      protocol
     * @param json       event string
     * @return object
     * @throws IllegalArgumentException      invalid arguments passed in
     * @throws UnsupportedOperationException if the operation is not supported
     * @throws IllegalStateException         if the system is in an illegal state
     */
    Object decode(String groupId, String artifactId, String topic, String json);

    /**
     * encodes topic's stringified event (json) to corresponding Event Object.
     *
     * @param groupId    of the controller
     * @param artifactId of the controller
     * @param topic      protocol
     * @param event      Object
     * @return encoded string
     * @throws IllegalArgumentException invalid arguments passed in
     */
    String encode(String groupId, String artifactId, String topic, Object event);

    /**
     * encodes topic's stringified event (json) to corresponding Event Object.
     *
     * @param topic topic
     * @param event event object
     * @return encoded string
     * @throws IllegalArgumentException      invalid arguments passed in
     * @throws UnsupportedOperationException operation cannot be performed
     */
    String encode(String topic, Object event);

    /**
     * encodes topic's stringified event (json) to corresponding Event Object.
     *
     * @param topic            topic
     * @param event            event object
     * @param droolsController drools controller object
     * @return encoded string
     * @throws IllegalArgumentException      invalid arguments passed in
     * @throws UnsupportedOperationException operation cannot be performed
     */
    String encode(String topic, Object event, DroolsController droolsController);
}
