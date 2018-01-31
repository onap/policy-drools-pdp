/*-
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2017 AT&T Intellectual Property. All rights reserved.
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

import static org.junit.Assert.assertTrue;

import java.util.Properties;


import org.junit.Test;
import org.onap.policy.drools.event.comm.TopicEndpoint;
import org.onap.policy.drools.properties.PolicyProperties;
import org.onap.policy.drools.protocol.configuration.DroolsConfiguration;

/**
 * Tests Coders
 */
public class EventProtocolCoderTest {

    /**
     * Coder Group
     */
    private static final String ENCODER_GROUP = "foo";

    /**
     * Coder Artifact
     */
    private static final String ENCODER_ARTIFACT = "bar";

    /**
     * Coder Version
     */
    private static final String ENCODER_VERSION = null;

    /**
     * noop topic
     */
    private static final String NOOP_TOPIC = "JUNIT";

    /**
     * Event Test Class
     */
    public static class EventTest {

        private String field;

        public EventTest(String field) {
            super();
            this.field = field;
        }

        public String getField() {
            return this.field;
        }

        public void setField(String field) {
            this.field = field;
        }
    }

    @Test
    public void test() {

        final Properties noopSinkProperties = new Properties();
        noopSinkProperties.put(PolicyProperties.PROPERTY_NOOP_SINK_TOPICS, NOOP_TOPIC);

        TopicEndpoint.manager.addTopicSinks(noopSinkProperties);

        EventProtocolCoder.manager.addEncoder(ENCODER_GROUP, ENCODER_ARTIFACT, NOOP_TOPIC,
            DroolsConfiguration.class.getCanonicalName(), new JsonProtocolFilter(), null, null,
            DroolsConfiguration.class.getName().hashCode());

        final String json = EventProtocolCoder.manager.encode(NOOP_TOPIC,
            new DroolsConfiguration(ENCODER_GROUP, ENCODER_ARTIFACT, ENCODER_VERSION));

        assertTrue(json.contains(ENCODER_GROUP));
        assertTrue(json.contains(ENCODER_ARTIFACT));

        EventProtocolCoder.manager.removeEncoders(ENCODER_GROUP, ENCODER_ARTIFACT, NOOP_TOPIC);
    }
}