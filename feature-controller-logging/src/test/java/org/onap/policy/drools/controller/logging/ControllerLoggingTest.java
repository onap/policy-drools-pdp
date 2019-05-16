/*
 * ============LICENSE_START=======================================================
 * feature-controller-logging
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

package org.onap.policy.drools.controller.logging;

import static org.junit.Assert.assertEquals;

import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.onap.policy.common.endpoints.event.comm.Topic;
import org.onap.policy.common.endpoints.event.comm.Topic.CommInfrastructure;
import org.onap.policy.common.endpoints.event.comm.bus.NoopTopicSink;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.controller.logging.ControllerLoggingFeature;
import org.onap.policy.drools.properties.DroolsProperties;
import org.onap.policy.drools.protocol.configuration.ControllerConfiguration;
import org.onap.policy.drools.protocol.configuration.PdpdConfiguration;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.drools.system.PolicyEngine;
import org.onap.policy.drools.util.KieUtils;

/**
 * Controller Logger Tests.
 */
public class ControllerLoggingTest {

    /**
     * These properties are for installing a test artifact that the drools controller can
     * fetch while testing.
     */
    private static final String JUNIT_KMODULE_DRL_PATH = "src/test/resources/test.drl";
    private static final String JUNIT_KMODULE_POM_PATH = "src/test/resources/test.pom";
    private static final String JUNIT_KMODULE_PATH = "src/test/resources/kmodule.xml";
    private static final String JUNIT_KJAR_DRL_PATH = "src/main/resources/org/onap/policy/drools/test/test.drl";

    /**
     * These properties are used for the Policy Controller to point to the test artifact.
     */
    private static final String TEST_CONTROLLER_NAME = "test-controller";
    private static final String TEST_GROUP_ID = "org.onap.policy.drools.test";
    private static final String TEST_ARTIFACT_ID = "test";
    private static final String TEST_VERSION = "1.4.1-SNAPSHOT";

    /**
     * A test topic used for delivery and network logging.
     */
    private static final String TEST_TOPIC = "test-topic";
    private static final String TEST_SERVER = "http://test.com";

    /**
     * These are used for sending PDPD configuration notifications to a policy controller.
     */
    private static Properties controllerProps = null;
    private static String message = null;
    private static PdpdConfiguration pdpdNotification = null;
    private static PolicyController policyController = null;

    /**
     * This is a list of events that are appended to the controller-test logger.
     */
    private static List<LoggingEvent> events = new ArrayList<>();

    /**
     * A custom appender used to intercept events and add them to a list of events that
     * the junits can use to determine logging was successful.
     */
    public static class NetworkAppender extends AppenderBase<LoggingEvent> {

        @Override
        protected void append(LoggingEvent event) {
            events.add(event);
        }

    }

    /**
     * Runs before all the test cases to install the drools artifact, create a policy
     * controller, and create a PDPD configuration notification.
     */
    @BeforeClass
    public static void setUp() throws IOException {
        KieUtils.installArtifact(Paths.get(JUNIT_KMODULE_PATH).toFile(), Paths.get(JUNIT_KMODULE_POM_PATH).toFile(),
                        JUNIT_KJAR_DRL_PATH, Paths.get(JUNIT_KMODULE_DRL_PATH).toFile());

        controllerProps = new Properties();
        controllerProps.put(DroolsProperties.PROPERTY_CONTROLLER_NAME, TEST_CONTROLLER_NAME);
        controllerProps.put(DroolsProperties.RULES_GROUPID, TEST_GROUP_ID);
        controllerProps.put(DroolsProperties.RULES_ARTIFACTID, TEST_ARTIFACT_ID);
        controllerProps.put(DroolsProperties.RULES_VERSION, TEST_VERSION);

        policyController = PolicyEngine.manager.createPolicyController(TEST_CONTROLLER_NAME, controllerProps);

        message = "{\"requestID\":\"38adde30-cc22-11e8-a8d5-f2801f1b9fd1\",\"entity\":\"controller\",\"controllers\":"
                        + "[{\"name\":\"test-controller\",\"drools\":{\"groupId\":\"org.onap.policy.drools.test\","
                        + "\"artifactId\":\"test\",\"version\":\"0.0.1\"},\"operation\":\"update\"}]}";

        Gson decoder = new GsonBuilder().disableHtmlEscaping().create();
        pdpdNotification = decoder.fromJson(message, PdpdConfiguration.class);
    }

    /**
     * Runs after every test case to clean up the events added to the event list during
     * unit test.
     */
    @After
    public void cleanUpLogs() {
        events.clear();
    }

    /**
     * Obtains the sequence number of the controller logging feature. This should return
     * 1000.
     */
    @Test
    public void getSequenceNumberTest() {
        ControllerLoggingFeature nlf = new ControllerLoggingFeature();
        assertEquals(1000, nlf.getSequenceNumber());
    }

    /**
     * Asserts that the controller-test logger appends the incoming message to the event
     * list.
     */
    @Test
    public void beforeOffer() {
        ControllerLoggingFeature nlf = new ControllerLoggingFeature();

        nlf.beforeOffer(policyController, Topic.CommInfrastructure.UEB, TEST_TOPIC, "{\"test\":\"test\"}");

        assertEquals(1, events.size());
    }

    /**
     * Asserts that the controller-test logger appends the outgoing message to the event
     * list.
     */
    @Test
    public void afterDeliverSuccess() {

        final ControllerLoggingFeature nlf = new ControllerLoggingFeature();

        DroolsController droolsController = DroolsController.factory.get(TEST_GROUP_ID, TEST_ARTIFACT_ID, TEST_VERSION);

        NoopTopicSink sinkTopic = new NoopTopicSink(Arrays.asList(TEST_SERVER), TEST_TOPIC);

        nlf.afterDeliver(droolsController, sinkTopic, null, "{\"test\":\"test\"}", true);

        assertEquals(1, events.size());

    }

    /**
     * Asserts that the controller-test logger does not append the outgoing message to the
     * event list if there was a failure.
     */
    @Test
    public void afterDeliverFailure() {

        final ControllerLoggingFeature nlf = new ControllerLoggingFeature();

        DroolsController droolsController = DroolsController.factory.get(TEST_GROUP_ID, TEST_ARTIFACT_ID, TEST_VERSION);

        NoopTopicSink sinkTopic = new NoopTopicSink(Arrays.asList(TEST_SERVER), TEST_TOPIC);

        nlf.afterDeliver(droolsController, sinkTopic, null, "{\"test\":\"test\"}", false);

        assertEquals(0, events.size());
    }

    /**
     * Asserts that the controller logging feature can log the messages to the proper
     * controller based on the message containing the controller name.
     */
    @Test
    public void afterOnTopicEventSuccess() {
        final ControllerLoggingFeature nlf = new ControllerLoggingFeature();

        nlf.afterOnTopicEvent(PolicyEngine.manager, pdpdNotification, CommInfrastructure.UEB, TEST_TOPIC, message);

        assertEquals(1, events.size());
    }

    /**
     * Asserts that the controller logging feature can skip logging messages that don't
     * contain the controller names in it.
     */
    @Test
    public void afterOnTopicEventFailure() {
        final ControllerLoggingFeature nlf = new ControllerLoggingFeature();

        PdpdConfiguration notification = new PdpdConfiguration();
        ControllerConfiguration config = new ControllerConfiguration();
        config.setName("test-controller-2");
        notification.setControllers(Arrays.asList(config));

        nlf.afterOnTopicEvent(PolicyEngine.manager, notification, CommInfrastructure.UEB, TEST_TOPIC, message);

        assertEquals(0, events.size());
    }
}
