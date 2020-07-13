/*
 * ============LICENSE_START=======================================================
 * policy-management
 * ================================================================================
 * Copyright (C) 2017-2019 AT&T Intellectual Property. All rights reserved.
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
import org.onap.policy.drools.protocol.coders.TopicCoderFilterConfiguration;

public interface DroolsControllerBuilder {
    /**
     * Return the 'controller.type' value supported by this particular builder
     * (multiple types may be specified by separating them by commas).
     *
     * @return a comma-separated list of types supported by this
     *     'PolicyControllerBuilder' instance
     */
    String getType();

    /**
     * Constructs a Drools Controller based on properties.
     *
     * @param properties properties containing initialization parameters
     * @param decoderConfigurations list of topic -> decoders -> filters mapping
     * @param encoderConfigurations list of topic -> encoders -> filters mapping
     *
     * @return the instantiated Drools Controller
     * @throws IllegalArgumentException with invalid parameters
     * @throws LinkageError Failure to link rules and models in Drools Libraries
     */
    DroolsController build(Properties properties,
        List<TopicCoderFilterConfiguration> decoderConfigurations,
        List<TopicCoderFilterConfiguration> encoderConfigurations) throws LinkageError;
}
