/*-
 * ============LICENSE_START===============================================
 * ONAP
 * ========================================================================
 * Copyright (C) 2024 Nordix Foundation.
 * ========================================================================
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
 * ============LICENSE_END=================================================
 */

package org.onap.policy.drools.protocol.coders;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class TopicCoderFilterConfigurationTest {

    @Test
    void setCustomGsonCoder() {
        var topic = "topic";
        var decodeFilter = new TopicCoderFilterConfiguration
            .PotentialCoderFilter("codedClass", new JsonProtocolFilter("rule"));
        var customCoder = new TopicCoderFilterConfiguration
            .CustomGsonCoder("className", "staticCoderField");
        var topicConfig = new TopicCoderFilterConfiguration(topic, List.of(decodeFilter), customCoder);

        assertNotNull(topicConfig);
        assertNotNull(topicConfig.getCoderFilters());
        assertEquals("topic", topicConfig.getTopic());

        assertEquals("className", topicConfig.getCustomGsonCoder().getClassContainer());

        var customCoder2 = new TopicCoderFilterConfiguration.CustomGsonCoder("className2,staticCoderField2");
        topicConfig.setCustomGsonCoder(customCoder2);

        assertEquals("className2", topicConfig.getCustomGsonCoder().getClassContainer());
    }

    @Test
    void setCustomCoder_Exceptions() {
        assertThatThrownBy(() -> new TopicCoderFilterConfiguration.CustomGsonCoder("", "staticCoderField"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No classname to create CustomCoder cannot be created");

        assertThatThrownBy(() -> new TopicCoderFilterConfiguration.CustomGsonCoder("className", ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No staticCoderField to create CustomCoder cannot be created for class className");

        assertThatThrownBy(() -> new TopicCoderFilterConfiguration.CustomGsonCoder(",staticCoderField"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No classname to create CustomCoder cannot be created");

        assertThatThrownBy(() -> new TopicCoderFilterConfiguration.CustomGsonCoder("className,"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No staticCoderField to create CustomCoder cannot be created for class className");

        assertThatThrownBy(() -> new TopicCoderFilterConfiguration.CustomGsonCoder(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Constructor argument cannot be empty.");
    }
}