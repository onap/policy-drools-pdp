/*
 * ============LICENSE_START=======================================================
 * Copyright (C) 2020 AT&T Intellectual Property. All rights reserved.
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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Properties;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.common.endpoints.event.comm.TopicEndpointManager;
import org.onap.policy.common.endpoints.properties.PolicyEndPointProperties;
import org.onap.policy.common.utils.coder.CoderException;
import org.onap.policy.drools.domain.models.controller.ControllerPolicy;
import org.onap.policy.drools.system.PolicyControllerConstants;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicy;

/**
 * Native Controller Policy Test.
 */
public class PolicyTypeNativeDroolsControllerTest extends LifecycleStateRunningTest {
    private static final String EXAMPLE_NATIVE_DROOLS_POLICY_NAME = "example.controller";
    private static final String EXAMPLE_NATIVE_DROOLS_POLICY_JSON =
            "src/test/resources/tosca-policy-native-controller-example.json";

    /**
     * Test initialization.
     */
    @Before
    public void init() throws IOException, CoderException {
        fsm = makeFsmWithPseudoTime();
    }

    @Test
    public void testDeployUndeploy() throws IOException, CoderException {
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
                new PolicyTypeNativeDroolsController(fsm, policy.getTypeIdentifier());
        assertTrue(controller.undeploy(policy));
        assertThatIllegalArgumentException().isThrownBy(
            () -> PolicyControllerConstants.getFactory().get(controllerPolicy.getName()));

        Properties noopTopicProperties = new Properties();
        noopTopicProperties.put(PolicyEndPointProperties.PROPERTY_NOOP_SOURCE_TOPICS, "DCAE_TOPIC");
        noopTopicProperties.put(PolicyEndPointProperties.PROPERTY_NOOP_SINK_TOPICS, "APPC-CL");
        TopicEndpointManager.getManager().addTopics(noopTopicProperties);

        assertTrue(controller.deploy(policy));
        /* this should be ok too */
        assertTrue(controller.deploy(policy));
    }

    @Test
    public void testControllerProperties() throws CoderException {
        Properties noopTopicProperties = new Properties();
        noopTopicProperties.put(PolicyEndPointProperties.PROPERTY_NOOP_SOURCE_TOPICS,
                "DCAE_TOPIC,APPC-CL,APPC-LCM-WRITE,SDNR-CL-RSP");
        noopTopicProperties.put(PolicyEndPointProperties.PROPERTY_NOOP_SINK_TOPICS,
                "APPC-CL,APPC-LCM-READ,POLICY-CL-MGT,DCAE_CL_RSP");
        TopicEndpointManager.getManager().addTopics(noopTopicProperties);

        ToscaPolicy nativeControllerPolicy =
                getExamplesPolicy("policies/usecases.native.controller.policy.input.tosca.json", "usecases");
        PolicyTypeNativeDroolsController controller =
                new PolicyTypeNativeDroolsController(fsm, nativeControllerPolicy.getTypeIdentifier());
        assertTrue(controller.deploy(nativeControllerPolicy));
        Properties properties = PolicyControllerConstants.getFactory().get("usecases").getProperties();

        assertEquals("usecases", properties.getProperty("controller.name"));

        assertNull(properties.getProperty("rules.groupId"));
        assertNull(properties.getProperty("rules.artifactId"));
        assertNull(properties.getProperty("rules.version"));

        assertEquals("DCAE_TOPIC,APPC-CL,APPC-LCM-WRITE,SDNR-CL-RSP",
                properties.getProperty("noop.source.topics"));
        assertEquals("APPC-CL,APPC-LCM-READ,POLICY-CL-MGT,DCAE_CL_RSP",
                properties.getProperty("noop.sink.topics"));

        assertEquals("org.onap.policy.controlloop.CanonicalOnset,org.onap.policy.controlloop.CanonicalAbated",
                properties.getProperty("noop.source.topics.DCAE_TOPIC.events"));
        assertEquals("[?($.closedLoopEventStatus == 'ONSET')]",
            properties
                .getProperty("noop.source.topics.DCAE_TOPIC.events.org.onap.policy.controlloop.CanonicalOnset.filter"));
        assertEquals("[?($.closedLoopEventStatus == 'ABATED')]",
            properties
                .getProperty("noop.source.topics.DCAE_TOPIC.events."
                                     + "org.onap.policy.controlloop.CanonicalAbated.filter"));
        assertEquals("org.onap.policy.controlloop.util.Serialization,gson",
                properties.getProperty("noop.source.topics.DCAE_TOPIC.events.custom.gson"));

        assertEquals("org.onap.policy.appc.Response", properties.getProperty("noop.source.topics.APPC-CL.events"));
        assertEquals("[?($.CommonHeader && $.Status)]",
                properties
                        .getProperty("noop.source.topics.APPC-CL.events.org.onap.policy.appc.Response.filter"));
        assertEquals("org.onap.policy.appc.util.Serialization,gsonPretty",
                properties.getProperty("noop.source.topics.APPC-CL.events.custom.gson"));

        assertEquals("org.onap.policy.appclcm.AppcLcmDmaapWrapper",
                properties.getProperty("noop.source.topics.APPC-LCM-WRITE.events"));
        assertEquals("[?($.type == 'response')]",
            properties
                .getProperty("noop.source.topics.APPC-LCM-WRITE.events."
                        + "org.onap.policy.appclcm.AppcLcmDmaapWrapper.filter"));
        assertEquals("org.onap.policy.appclcm.util.Serialization,gson",
                properties.getProperty("noop.source.topics.APPC-LCM-WRITE.events.custom.gson"));

        assertEquals("org.onap.policy.sdnr.PciResponseWrapper",
                properties.getProperty("noop.source.topics.SDNR-CL-RSP.events"));
        assertEquals("[?($.type == 'response')]",
                properties
                        .getProperty("noop.source.topics.SDNR-CL-RSP.events."
                                             + "org.onap.policy.sdnr.PciResponseWrapper.filter"));
        assertEquals("org.onap.policy.sdnr.util.Serialization,gson",
                properties.getProperty("noop.source.topics.SDNR-CL-RSP.events.custom.gson"));

        assertEquals("org.onap.policy.appc.Request", properties.getProperty("noop.sink.topics.APPC-CL.events"));
        assertEquals("org.onap.policy.appc.util.Serialization,gsonPretty",
                properties.getProperty("noop.sink.topics.APPC-CL.events.custom.gson"));

        assertEquals("org.onap.policy.appclcm.AppcLcmDmaapWrapper",
                properties.getProperty("noop.sink.topics.APPC-LCM-READ.events"));
        assertEquals("org.onap.policy.appclcm.util.Serialization,gson",
                properties.getProperty("noop.sink.topics.APPC-LCM-READ.events.custom.gson"));

        assertEquals("org.onap.policy.controlloop.VirtualControlLoopNotification",
                properties.getProperty("noop.sink.topics.POLICY-CL-MGT.events"));
        assertEquals("org.onap.policy.controlloop.util.Serialization,gsonPretty",
                properties.getProperty("noop.sink.topics.POLICY-CL-MGT.events.custom.gson"));

        assertEquals("org.onap.policy.controlloop.ControlLoopResponse",
                properties.getProperty("noop.sink.topics.DCAE_CL_RSP.events"));
        assertEquals("org.onap.policy.controlloop.util.Serialization,gsonPretty",
                properties.getProperty("noop.sink.topics.DCAE_CL_RSP.events.custom.gson"));

        assertEquals("test", properties.getProperty("notes"));
        assertEquals("auto", properties.getProperty("persistence.type"));

        assertTrue(controller.undeploy(nativeControllerPolicy));
    }
}