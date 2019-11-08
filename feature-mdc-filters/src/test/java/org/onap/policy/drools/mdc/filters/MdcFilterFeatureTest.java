/*
 * ============LICENSE_START=======================================================
 * feature-mdc-filters
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

package org.onap.policy.drools.mdc.filters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.common.endpoints.event.comm.Topic.CommInfrastructure;
import org.onap.policy.common.endpoints.event.comm.TopicEndpointManager;
import org.onap.policy.common.endpoints.event.comm.TopicSink;
import org.onap.policy.common.endpoints.event.comm.TopicSource;
import org.onap.policy.common.endpoints.utils.NetLoggerUtil.EventType;
import org.onap.policy.drools.system.PolicyController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class MdcFilterFeatureTest {

    /**
     * Logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(MdcFilterFeatureTest.class);

    /**
     * Test topic names for mdc topic filters.
     */
    private static final String TEST_TOPIC_A = "org.onap.policy.test-topic-a";
    private static final String TEST_TOPIC_B = "org.onap.policy.test-topic-b";

    /**
     * The mock properties to be used for the junits.
     */
    private Properties props;

    /**
     * An instance of the MdcFilterFeature.
     */
    private MdcFilterFeature mdcFilterFeature;

    /**
     * Sample json string to be logged.
     */
    private String message;

    /**
     * Setup.
     * @throws IOException thrown if onset.json file could not be read
     */
    @Before
    public void setUp() throws IOException {
        message = new String(Files.readAllBytes(Paths.get("src/test/resources/onset.json")));

        props = mockFeatureProperties();
        /**
         * The mock PolicyController to be used for the junits.
         */
        PolicyController controller = mock(PolicyController.class);

        props.setProperty("dmaap.source.topics", TEST_TOPIC_A);
        props.setProperty("dmaap.source.topics." + TEST_TOPIC_A + ".servers", "http://testing123.com/");
        props.setProperty("noop.sink.topics", TEST_TOPIC_B);

        List<TopicSource> topicSources = TopicEndpointManager.getManager().addTopicSources(props);
        doReturn(topicSources).when(controller).getTopicSources();

        List<TopicSink> topicSinks = TopicEndpointManager.getManager().addTopicSinks(props);
        doReturn(topicSinks).when(controller).getTopicSinks();

        mdcFilterFeature = new MdcFilterFeatureImpl();
        mdcFilterFeature.afterCreate(controller);
    }

    /**
     * Tests extracting fields from a JSON message and place them in the MDC and
     * then clearing the MDC.
     */
    @Test
    public void mdcLogTest() {
        mdcFilterFeature.beforeLog(logger, EventType.IN,
                CommInfrastructure.DMAAP, TEST_TOPIC_A, message);

        assertEquals("8c1b8bd8-06f7-493f-8ed7-daaa4cc481bc", MDC.get("requestID"));
        assertEquals("CL-TEST", MDC.get("closedLoopControlName"));

        assertNotNull(MDC.getCopyOfContextMap());

        mdcFilterFeature.afterLog(logger, EventType.IN,
                CommInfrastructure.DMAAP, TEST_TOPIC_A, message);

        assertNull(MDC.getCopyOfContextMap());
    }

    /**
     * Tests that the feature does not search for fields in a JSON message
     * if there is not an MdcTopicFilter object for the generated key.
     */
    @Test
    public void noTopicFilterTest() {
        mdcFilterFeature.beforeLog(logger, EventType.OUT,
                CommInfrastructure.NOOP, "no-topic", message);

        assertEquals("OUT", MDC.get("networkEventType"));
        assertEquals("NOOP", MDC.get("networkProtocol"));
        assertEquals("no-topic", MDC.get("networkTopic"));
        assertNull(MDC.get("requestID"));
    }

    /**
     * Creates a simple properties map containing an mdc filter for a test
     * topic.
     *
     * @return a properties map with mdc filter properties.
     */
    private Properties mockFeatureProperties() {
        Properties props = new Properties();

        String key = "dmaap.source.topics." + TEST_TOPIC_A + ".mdcFilters";
        String value = "requestID=$.requestID,closedLoopControlName=$.closedLoopControlName";
        props.setProperty(key, value);

        return props;
    }

    /**
     * Subclass of MdcFilterFeature for junit usage.
     */
    private class MdcFilterFeatureImpl extends MdcFilterFeature {

        public MdcFilterFeatureImpl() {
            super();
        }

        @Override
        protected Properties getFeatureProps() {
            return props;
        }
    }
}
