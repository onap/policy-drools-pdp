/*
 * ============LICENSE_START=======================================================
 * policy-management
 * ================================================================================
 * Copyright (C) 2017-2020 AT&T Intellectual Property. All rights reserved.
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

public class TopicCoderFilterConfiguration {

    /**
     * Custom coder, contains class and static field to access parser that the controller desires to
     * use instead of the framework provided parser.
     */
    public abstract static class CustomCoder {
        protected String className;
        protected String staticCoderField;

        /**
         * create custom coder from raw string in the following format (typically embedded in a property
         * file):
         *
         * <p>Note this is to support decoding/encoding of partial structures that are only known by the
         * model.
         *
         * @param rawCustomCoder with format: &lt;class-containing-custom-coder&gt;,&lt;static-coder-field&gt.
         */
        public CustomCoder(String rawCustomCoder) {
            if (rawCustomCoder != null && !rawCustomCoder.isEmpty()) {

                this.className = rawCustomCoder.substring(0, rawCustomCoder.indexOf(','));
                if (this.className == null || this.className.isEmpty()) {
                    throw new IllegalArgumentException(
                            "No classname to create CustomCoder cannot be created");
                }

                this.staticCoderField = rawCustomCoder.substring(rawCustomCoder.indexOf(',') + 1);
                if (this.staticCoderField == null || this.staticCoderField.isEmpty()) {
                    throw new IllegalArgumentException(
                            "No staticCoderField to create CustomCoder cannot be created for class " + className);
                }
            }
        }

        /**
         * Constructor.
         *
         * @param className class name
         * @param staticCoderField static coder field
         */
        public CustomCoder(String className, String staticCoderField) {
            if (className == null || className.isEmpty()) {
                throw new IllegalArgumentException("No classname to create CustomCoder cannot be created");
            }

            if (staticCoderField == null || staticCoderField.isEmpty()) {
                throw new IllegalArgumentException(
                        "No staticCoderField to create CustomCoder cannot be created for class " + className);
            }

            this.className = className;
            this.staticCoderField = staticCoderField;
        }

        /**
         * Get class container.
         *
         * @return the className
         **/
        public String getClassContainer() {
            return className;
        }

        /**
         * Set class container.
         *
         * @param className the className to set
         **/
        public void setClassContainer(String className) {
            this.className = className;
        }

        /**
         * Get static coder field.
         *
         * @return the staticCoderField
         **/
        public String getStaticCoderField() {
            return staticCoderField;
        }

        /**
         * Set static coder field.
         *
         * @param staticCoderField the staticGson to set
         **/
        public void setStaticCoderField(String staticCoderField) {
            this.staticCoderField = staticCoderField;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder
                .append("CustomCoder [className=")
                .append(className)
                .append(", staticCoderField=")
                .append(staticCoderField)
                .append("]");
            return builder.toString();
        }
    }

    public static class CustomGsonCoder extends CustomCoder {

        public CustomGsonCoder(String className, String staticCoderField) {
            super(className, staticCoderField);
        }

        public CustomGsonCoder(String customGson) {
            super(customGson);
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("CustomGsonCoder [toString()=").append(super.toString()).append("]");
            return builder.toString();
        }
    }

    /**
     * Coder/Decoder class and Filter container. The decoder class is potential, in order to be
     * operational needs to be fetched from an available class loader.
     */
    public static class PotentialCoderFilter {

        /* decoder class (pending from being able to be fetched and found in some class loader) */
        protected String codedClass;

        /* filters to apply to the selection of the decodedClass */
        protected JsonProtocolFilter filter;

        /**
         * constructor.
         *
         * @param codedClass decoder class
         * @param filter filters to apply
         */
        public PotentialCoderFilter(String codedClass, JsonProtocolFilter filter) {
            this.codedClass = codedClass;
            this.filter = filter;
        }

        /**
         * Get coded class.
         *
         * @return the decodedClass
         **/
        public String getCodedClass() {
            return codedClass;
        }

        /** Set coded class.
         *
         * @param decodedClass the decodedClass to set
         **/
        public void setCodedClass(String decodedClass) {
            this.codedClass = decodedClass;
        }

        /**
         * Get filter.
         *
         * @return the filter
         **/
        public JsonProtocolFilter getFilter() {
            return filter;
        }

        /**
         * Set filter.
         *
         * @param filter the filter to set
         **/
        public void setFilter(JsonProtocolFilter filter) {
            this.filter = filter;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder
                .append("PotentialCoderFilter [codedClass=")
                .append(codedClass)
                .append(", filter=")
                .append(filter)
                .append("]");
            return builder.toString();
        }
    }

    /* the source topic */
    protected final String topic;

    /* List of decoder -> filters */
    protected final List<PotentialCoderFilter> coderFilters;

    /* custom gson coder that this controller prefers to use instead of the framework ones */
    protected CustomGsonCoder customGsonCoder;

    /**
     * Constructor.
     *
     * @param topic the topic
     * @param decoderFilters list of decoders and associated filters
     * @param customGsonCoder GSON coder
     */
    public TopicCoderFilterConfiguration(
            String topic,
            List<PotentialCoderFilter> decoderFilters,
            CustomGsonCoder customGsonCoder) {
        this.coderFilters = decoderFilters;
        this.topic = topic;
        this.customGsonCoder = customGsonCoder;
    }

    /**
     * Get topic.
     * @return the topic
     **/
    public String getTopic() {
        return topic;
    }

    /** Get coder filters.
     *
     * @return the decoderFilters
     **/
    public List<PotentialCoderFilter> getCoderFilters() {
        return coderFilters;
    }

    /**
     * Get custom gson coder.
     *
     * @return the customGsonCoder
     **/
    public CustomGsonCoder getCustomGsonCoder() {
        return customGsonCoder;
    }

    /**
     * Set custom gson coder.
     *
     * @param customGsonCoder the customGsonCoder to set
     **/
    public void setCustomGsonCoder(CustomGsonCoder customGsonCoder) {
        this.customGsonCoder = customGsonCoder;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder
            .append("TopicCoderFilterConfiguration [topic=")
            .append(topic)
            .append(", coderFilters=")
            .append(coderFilters)
            .append(", customGsonCoder=")
            .append(customGsonCoder)
            .append("]");
        return builder.toString();
    }
}
