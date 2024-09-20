/*
 * ============LICENSE_START=======================================================
 * Copyright (C) 2020-2022 AT&T Intellectual Property. All rights reserved.
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
 *
 * SPDX-License-Identifier: Apache-2.0
 * =============LICENSE_END========================================================
 */

package org.onap.policy.drools.lifecycle;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.onap.policy.common.endpoints.event.comm.TopicEndpointManager;
import org.onap.policy.common.endpoints.properties.PolicyEndPointProperties;
import org.onap.policy.common.utils.coder.CoderException;
import org.onap.policy.drools.domain.models.controller.ControllerPolicy;
import org.onap.policy.drools.server.restful.TestConstants;
import org.onap.policy.drools.system.PolicyControllerConstants;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicy;

/**
 * Native Controller Policy Test.
 */
class PolicyTypeNativeDroolsControllerTest extends LifecycleStateRunningTest {
    private static final String EXAMPLE_NATIVE_DROOLS_POLICY_NAME = "example.controller";
    private static final String EXAMPLE_NATIVE_DROOLS_POLICY_JSON =
        "src/test/resources/tosca-policy-native-controller-example.json";

    /**
     * Test initialization.
     */
    @BeforeEach
    public void init() {
        fsm = makeFsmWithPseudoTime();
    }

    @Test
    void testDeployUndeploy() throws IOException, CoderException {
        fsm = makeFsmWithPseudoTime();

        assertTrue(controllerSupport.getController().getDrools().isBrained());
        assertFalse(controllerSupport.getController().isAlive());
        assertFalse(controllerSupport.getController().getDrools().isAlive());
        assertSame(controllerSupport.getController(), PolicyControllerConstants.getFactory().get("lifecycle"));

        assertTrue(controllerSupport.getController().start());

        assertTrue(controllerSupport.getController().isAlive());
        assertTrue(controllerSupport.getController().getDrools().isAlive());
        assertTrue(controllerSupport.getController().getDrools().isBrained());
        assertSame(controllerSupport.getController(), PolicyControllerConstants.getFactory().get("lifecycle"));

        ToscaPolicy policy = getPolicyFromFile(EXAMPLE_NATIVE_DROOLS_POLICY_JSON, EXAMPLE_NATIVE_DROOLS_POLICY_NAME);
        ControllerPolicy controllerPolicy = fsm.getDomainMaker().convertTo(policy, ControllerPolicy.class);
        PolicyTypeNativeDroolsController controller =
            new PolicyTypeNativeDroolsController(policy.getTypeIdentifier(), fsm);
        assertTrue(controller.undeploy(policy));
        assertThatIllegalArgumentException().isThrownBy(
            () -> PolicyControllerConstants.getFactory().get(controllerPolicy.getName()));

        Properties noopTopicProperties = new Properties();
        noopTopicProperties.put(PolicyEndPointProperties.PROPERTY_NOOP_SOURCE_TOPICS, TestConstants.DCAE_TOPIC);
        noopTopicProperties.put(PolicyEndPointProperties.PROPERTY_NOOP_SINK_TOPICS, TestConstants.APPC_CL_TOPIC);
        TopicEndpointManager.getManager().addTopics(noopTopicProperties);

        assertTrue(controller.deploy(policy));
        /* this should be ok too */
        assertTrue(controller.deploy(policy));
    }

    @Test
    void testControllerProperties() throws CoderException {
        Properties noopTopicProperties = new Properties();
        String noopSources = String.join(",", TestConstants.DCAE_TOPIC, TestConstants.APPC_CL_TOPIC,
            TestConstants.APPC_LCM_WRITE_TOPIC, TestConstants.SDNR_CL_RSP_TOPIC);
        noopTopicProperties.put(PolicyEndPointProperties.PROPERTY_NOOP_SOURCE_TOPICS, noopSources);

        String noopSinks = String.join(",", TestConstants.APPC_CL_TOPIC, TestConstants.APPC_LCM_READ_TOPIC,
            TestConstants.POLICY_CL_MGT_TOPIC, TestConstants.DCAE_CL_RSP_TOPIC);
        noopTopicProperties.put(PolicyEndPointProperties.PROPERTY_NOOP_SINK_TOPICS, noopSinks);
        TopicEndpointManager.getManager().addTopics(noopTopicProperties);

        ToscaPolicy nativeControllerPolicy =
            getExamplesPolicy("policies/usecases.native.controller.policy.input.tosca.json", "usecases");
        PolicyTypeNativeDroolsController controller =
            new PolicyTypeNativeDroolsController(nativeControllerPolicy.getTypeIdentifier(), fsm);
        assertTrue(controller.deploy(nativeControllerPolicy));
        Properties properties = PolicyControllerConstants.getFactory().get("usecases").getProperties();

        assertEquals("usecases", properties.getProperty("controller.name"));

        assertNull(properties.getProperty("rules.groupId"));
        assertNull(properties.getProperty("rules.artifactId"));
        assertNull(properties.getProperty("rules.version"));

        assertSourceTopics(properties);

        assertSinkTopics(properties);

        assertEquals("test", properties.getProperty("notes"));
        assertEquals("auto", properties.getProperty("persistence.type"));

        assertTrue(controller.undeploy(nativeControllerPolicy));
    }

    private static void assertSourceTopics(Properties properties) {
        assertEquals("dcae_topic,appc-cl,appc-lcm-write,sdnr-cl-rsp",
            properties.getProperty("noop.source.topics"));

        assertEquals("org.onap.policy.controlloop.CanonicalOnset,org.onap.policy.controlloop.CanonicalAbated",
            properties.getProperty("noop.source.topics.dcae_topic.events"));

        assertEquals("[?($.closedLoopEventStatus == 'ONSET')]",
            properties.getProperty("noop.source.topics.dcae_topic.events."
                + "org.onap.policy.controlloop.CanonicalOnset.filter"));

        assertEquals("[?($.closedLoopEventStatus == 'ABATED')]",
            properties.getProperty("noop.source.topics.dcae_topic.events."
                + "org.onap.policy.controlloop.CanonicalAbated.filter"));

        assertEquals("org.onap.policy.controlloop.util.Serialization,gson",
            properties.getProperty("noop.source.topics.dcae_topic.events.custom.gson"));

        assertEquals("org.onap.policy.appc.Response",
            properties.getProperty("noop.source.topics.appc-cl.events"));

        assertEquals("[?($.CommonHeader && $.Status)]",
            properties.getProperty("noop.source.topics.appc-cl.events.org.onap.policy.appc.Response.filter"));

        assertEquals("org.onap.policy.appc.util.Serialization,gsonPretty",
            properties.getProperty("noop.source.topics.appc-cl.events.custom.gson"));

        assertEquals("org.onap.policy.appclcm.AppcLcmMessageWrapper",
            properties.getProperty("noop.source.topics.appc-lcm-write.events"));

        assertEquals("[?($.type == 'response')]",
            properties.getProperty("noop.source.topics.appc-lcm-write.events."
                + "org.onap.policy.appclcm.AppcLcmMessageWrapper.filter"));

        assertEquals("org.onap.policy.appclcm.util.Serialization,gson",
            properties.getProperty("noop.source.topics.appc-lcm-write.events.custom.gson"));

        assertEquals("org.onap.policy.sdnr.PciResponseWrapper",
            properties.getProperty("noop.source.topics.sdnr-cl-rsp.events"));

        assertEquals("[?($.type == 'response')]",
            properties.getProperty("noop.source.topics.sdnr-cl-rsp.events."
                + "org.onap.policy.sdnr.PciResponseWrapper.filter"));

        assertEquals("org.onap.policy.sdnr.util.Serialization,gson",
            properties.getProperty("noop.source.topics.sdnr-cl-rsp.events.custom.gson"));
    }

    private static void assertSinkTopics(Properties properties) {
        assertEquals("appc-cl,appc-lcm-read,policy-cl-mgt,dcae_cl_rsp",
            properties.getProperty("noop.sink.topics"));

        assertEquals("org.onap.policy.appc.Request",
            properties.getProperty("noop.sink.topics.appc-cl.events"));

        assertEquals("org.onap.policy.appc.util.Serialization,gsonPretty",
            properties.getProperty("noop.sink.topics.appc-cl.events.custom.gson"));

        assertEquals("org.onap.policy.appclcm.AppcLcmMessageWrapper",
            properties.getProperty("noop.sink.topics.appc-lcm-read.events"));

        assertEquals("org.onap.policy.appclcm.util.Serialization,gson",
            properties.getProperty("noop.sink.topics.appc-lcm-read.events.custom.gson"));

        assertEquals("org.onap.policy.controlloop.VirtualControlLoopNotification",
            properties.getProperty("noop.sink.topics.policy-cl-mgt.events"));

        assertEquals("org.onap.policy.controlloop.util.Serialization,gsonPretty",
            properties.getProperty("noop.sink.topics.policy-cl-mgt.events.custom.gson"));

        assertEquals("org.onap.policy.controlloop.ControlLoopResponse",
            properties.getProperty("noop.sink.topics.dcae_cl_rsp.events"));

        assertEquals("org.onap.policy.controlloop.util.Serialization,gsonPretty",
            properties.getProperty("noop.sink.topics.dcae_cl_rsp.events.custom.gson"));
    }
}
