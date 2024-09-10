/*
 * ============LICENSE_START=======================================================
 * policy-management
 * ================================================================================
 * Copyright (C) 2017-2021 AT&T Intellectual Property. All rights reserved.
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

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

@Getter
@ToString
public class TopicCoderFilterConfiguration {

    /**
     * Custom coder, contains class and static field to access parser that the controller desires to
     * use instead of the framework provided parser.
     */
    @Getter
    @Setter
    @ToString
    public abstract static class CustomCoder {
        protected String classContainer;
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
        protected CustomCoder(String rawCustomCoder) {
            if (StringUtils.isBlank(rawCustomCoder)) {
                throw new IllegalArgumentException("Constructor argument cannot be empty. "
                    + "Use format \"customCoderClass,staticCoderField\"");
            }

            this.classContainer = rawCustomCoder.substring(0, rawCustomCoder.indexOf(','));
            if (StringUtils.isBlank(this.classContainer)) {
                throw new IllegalArgumentException(
                    "No classname to create CustomCoder cannot be created");
            }

            this.staticCoderField = rawCustomCoder.substring(rawCustomCoder.indexOf(',') + 1);
            if (StringUtils.isBlank(this.staticCoderField)) {
                throw new IllegalArgumentException(
                    "No staticCoderField to create CustomCoder cannot be created for class " + classContainer);
            }
        }

        /**
         * Constructor.
         *
         * @param className class name
         * @param staticCoderField static coder field
         */
        protected CustomCoder(String className, String staticCoderField) {
            if (StringUtils.isBlank(className)) {
                throw new IllegalArgumentException("No classname to create CustomCoder cannot be created");
            }

            if (StringUtils.isBlank(staticCoderField)) {
                throw new IllegalArgumentException(
                        "No staticCoderField to create CustomCoder cannot be created for class " + className);
            }

            this.classContainer = className;
            this.staticCoderField = staticCoderField;
        }
    }

    @ToString(callSuper = true)
    public static class CustomGsonCoder extends CustomCoder {

        public CustomGsonCoder(String className, String staticCoderField) {
            super(className, staticCoderField);
        }

        public CustomGsonCoder(String customGson) {
            super(customGson);
        }
    }

    /**
     * Coder/Decoder class and Filter container. The decoder class is potential, in order to be
     * operational needs to be fetched from an available class loader.
     */
    @Getter
    @Setter
    @ToString
    @AllArgsConstructor
    public static class PotentialCoderFilter {

        /* decoder class (pending from being able to be fetched and found in some class loader) */
        protected String codedClass;

        /* filters to apply to the selection of the decodedClass */
        protected JsonProtocolFilter filter;
    }

    /* the source topic */
    protected final String topic;

    /* List of decoder -> filters */
    protected final List<PotentialCoderFilter> coderFilters;

    /* custom gson coder that this controller prefers to use instead of the framework ones */
    @Setter
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
}
