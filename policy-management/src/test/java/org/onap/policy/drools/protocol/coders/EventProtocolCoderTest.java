/*-
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018-2020 AT&T Intellectual Property. All rights reserved.
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
import org.onap.policy.common.endpoints.event.comm.TopicEndpointManager;
import org.onap.policy.common.endpoints.properties.PolicyEndPointProperties;
import org.onap.policy.drools.protocol.configuration.DroolsConfiguration;

/**
 * Tests Coders.
 */
public class EventProtocolCoderTest {

    /**
     * Coder Group.
     */
    private static final String ENCODER_GROUP = "foo";

    /**
     * Coder Artifact.
     */
    private static final String ENCODER_ARTIFACT = "bar";

    /**
     * Coder Version.
     */
    private static final String ENCODER_VERSION = null;

    /**
     * noop topic.
     */
    private static final String NOOP_TOPIC = "JUNIT";

    /**
     * Event Test Class.
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
        noopSinkProperties.put(PolicyEndPointProperties.PROPERTY_NOOP_SINK_TOPICS, NOOP_TOPIC);

        TopicEndpointManager.getManager().addTopicSinks(noopSinkProperties);

        EventProtocolCoderConstants.getManager().addEncoder(
                EventProtocolParams.builder().groupId(ENCODER_GROUP).artifactId(ENCODER_ARTIFACT)
                        .topic(NOOP_TOPIC).eventClass(DroolsConfiguration.class.getName())
                        .protocolFilter(new JsonProtocolFilter()).customGsonCoder(null)
                        .modelClassLoaderHash(DroolsConfiguration.class.getName().hashCode()));

        final String json = EventProtocolCoderConstants.getManager().encode(NOOP_TOPIC,
                new DroolsConfiguration(ENCODER_ARTIFACT, ENCODER_GROUP, ENCODER_VERSION));

        assertTrue(json.contains(ENCODER_GROUP));
        assertTrue(json.contains(ENCODER_ARTIFACT));

        EventProtocolCoderConstants.getManager().removeEncoders(ENCODER_GROUP, ENCODER_ARTIFACT, NOOP_TOPIC);
    }
}
