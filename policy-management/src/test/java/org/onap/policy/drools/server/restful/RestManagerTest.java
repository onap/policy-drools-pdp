/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2024 Nordix Foundation.
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

package org.onap.policy.drools.server.restful;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.CREATED;
import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static jakarta.ws.rs.core.Response.Status.NOT_ACCEPTABLE;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static jakarta.ws.rs.core.Response.Status.NOT_MODIFIED;
import static jakarta.ws.rs.core.Response.Status.OK;
import static jakarta.ws.rs.core.Response.Status.PARTIAL_CONTENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.onap.policy.drools.properties.DroolsPropertyConstants.PROPERTY_CONTROLLER_NAME;

import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.onap.policy.common.endpoints.event.comm.TopicEndpoint;
import org.onap.policy.common.endpoints.event.comm.TopicEndpointManager;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.controller.internal.NullDroolsController;
import org.onap.policy.drools.features.PolicyControllerFeatureApi;
import org.onap.policy.drools.features.PolicyEngineFeatureApi;
import org.onap.policy.drools.protocol.coders.EventProtocolCoder;
import org.onap.policy.drools.protocol.coders.EventProtocolCoderConstants;
import org.onap.policy.drools.protocol.coders.JsonProtocolFilter;
import org.onap.policy.drools.protocol.coders.ProtocolCoderToolset;
import org.onap.policy.drools.protocol.configuration.ControllerConfiguration;
import org.onap.policy.drools.protocol.configuration.PdpdConfiguration;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.drools.system.PolicyControllerConstants;
import org.onap.policy.drools.system.PolicyControllerFactory;
import org.onap.policy.drools.system.PolicyDroolsPdpRuntimeException;
import org.onap.policy.drools.system.PolicyEngineConstants;
import org.onap.policy.drools.system.PolicyEngineManager;
import org.onap.policy.drools.system.internal.AggregatedPolicyController;
import org.onap.policy.drools.system.internal.LockManager;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

class RestManagerTest {

    private static final String CONTROLLER_NAME = "myControllerName";
    private static final String FACT_CLASS = "factClass";
    private static final String TOPIC = "topic";
    private static final String GROUP_ID = "group";
    private static final String ARTIFACT_ID = "artifact";

    @Mock
    RestManager restApi;

    @Mock
    PolicyControllerFactory controllerFactory;

    @Mock
    EventProtocolCoder coderManager;

    @Mock
    TopicEndpoint topicManager;

    @Mock
    PolicyEngineManager policyEngineManager;

    AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);

        try (MockedStatic<TopicEndpointManager> constants = mockStatic(TopicEndpointManager.class)) {
            setupTopicEndpointManager(constants);
            restApi = mock(RestManager.class);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    void swagger() {
        when(restApi.swagger()).thenCallRealMethod();
        var response = restApi.swagger();
        assertNotNull(response);
        assertInstanceOf(Response.class, response);
        assertEquals(OK.getStatusCode(), response.getStatus());

        when(restApi.getSwaggerContents()).thenThrow(new PolicyDroolsPdpRuntimeException("exception"));

        response = restApi.swagger();
        assertNotNull(response);
        assertInstanceOf(Response.class, response);
        assertEquals(INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    }

    @Test
    void engineShutdown() {
        try (MockedStatic<PolicyEngineConstants> constants = mockStatic(PolicyEngineConstants.class)) {
            setupLockManagerAndExecutorService();

            doNothing().doThrow(new IllegalStateException("should throw exception"))
                .when(policyEngineManager).shutdown();

            setupPolicyEngineManager(constants);

            restApi = mock(RestManager.class);

            when(restApi.engineShutdown()).thenCallRealMethod();

            var response = restApi.engineShutdown();
            assertNotNull(response);
            assertInstanceOf(Response.class, response);
            assertEquals(OK.getStatusCode(), response.getStatus());

            response = restApi.engineShutdown();
            assertEquals(BAD_REQUEST.getStatusCode(), response.getStatus());
        }
    }

    @Test
    void engineFeature() {
        try (MockedStatic<PolicyEngineConstants> constants = mockStatic(PolicyEngineConstants.class)) {
            setupLockManagerAndExecutorService();
            when(policyEngineManager.getFeatureProvider("testFeature"))
                .thenReturn(mock(PolicyEngineFeatureApi.class))
                .thenThrow(new IllegalArgumentException("should throw exception"));

            setupPolicyEngineManager(constants);

            restApi = mock(RestManager.class);
            when(restApi.engineFeature("testFeature")).thenCallRealMethod();

            var response = restApi.engineFeature("testFeature");
            assertNotNull(response);
            assertInstanceOf(Response.class, response);
            assertEquals(OK.getStatusCode(), response.getStatus());

            response = restApi.engineFeature("testFeature");
            assertEquals(NOT_FOUND.getStatusCode(), response.getStatus());
            assertEquals("should throw exception", ((RestManager.Error) response.getEntity()).getError());
        }
    }

    @Test
    void engineUpdate() {
        try (MockedStatic<PolicyEngineConstants> constants = mockStatic(PolicyEngineConstants.class)) {
            setupLockManagerAndExecutorService();
            var pdpdConfig = mock(PdpdConfiguration.class);
            when(policyEngineManager.configure(pdpdConfig))
                .thenReturn(true)
                .thenReturn(false)
                .thenThrow(new IllegalArgumentException("should throw exception"));

            setupPolicyEngineManager(constants);

            restApi = mock(RestManager.class);
            when(restApi.engineUpdate(pdpdConfig)).thenCallRealMethod();

            var response = restApi.engineUpdate(pdpdConfig);
            assertNotNull(response);
            assertInstanceOf(Response.class, response);
            assertEquals(OK.getStatusCode(), response.getStatus());

            response = restApi.engineUpdate(pdpdConfig);
            assertEquals(NOT_ACCEPTABLE.getStatusCode(), response.getStatus());

            // call again, should be going on exception
            response = restApi.engineUpdate(pdpdConfig);
            assertEquals(NOT_ACCEPTABLE.getStatusCode(), response.getStatus());
        }
    }

    @Test
    void engineActivation() {
        try (MockedStatic<PolicyEngineConstants> constants = mockStatic(PolicyEngineConstants.class)) {
            setupLockManagerAndExecutorService();
            doNothing().doThrow(new IllegalStateException("should throw exception"))
                .when(policyEngineManager).activate();

            setupPolicyEngineManager(constants);

            restApi = mock(RestManager.class);
            when(restApi.engineActivation()).thenCallRealMethod();

            var response = restApi.engineActivation();
            assertNotNull(response);
            assertInstanceOf(Response.class, response);
            assertEquals(OK.getStatusCode(), response.getStatus());

            response = restApi.engineActivation();
            assertEquals(NOT_ACCEPTABLE.getStatusCode(), response.getStatus());
        }
    }

    @Test
    void engineDeactivation() {
        try (MockedStatic<PolicyEngineConstants> constants = mockStatic(PolicyEngineConstants.class)) {
            setupLockManagerAndExecutorService();
            doNothing().doThrow(new IllegalStateException("should throw exception"))
                .when(policyEngineManager).deactivate();

            setupPolicyEngineManager(constants);

            restApi = mock(RestManager.class);
            when(restApi.engineDeactivation()).thenCallRealMethod();

            var response = restApi.engineDeactivation();
            assertNotNull(response);
            assertInstanceOf(Response.class, response);
            assertEquals(OK.getStatusCode(), response.getStatus());

            response = restApi.engineDeactivation();
            assertEquals(NOT_ACCEPTABLE.getStatusCode(), response.getStatus());
        }
    }

    @Test
    void controllerAdd() {
        try (MockedStatic<PolicyControllerConstants> controllerConst = mockStatic(PolicyControllerConstants.class)) {
            try (MockedStatic<PolicyEngineConstants> engConst = mockStatic(PolicyEngineConstants.class)) {
                setupLockManagerAndExecutorService();

                when(controllerFactory.get(CONTROLLER_NAME))
                    .thenReturn(mock(AggregatedPolicyController.class))
                    .thenThrow(new IllegalStateException("exception to test fetch policy failed"))
                    .thenReturn(null);

                setupPolicyControllerFactory(controllerConst);

                var invalidProps = new Properties();
                invalidProps.setProperty(PROPERTY_CONTROLLER_NAME, "");

                var properties = new Properties();
                properties.setProperty(PROPERTY_CONTROLLER_NAME, CONTROLLER_NAME);

                var policyController = mock(PolicyController.class);
                when(policyController.start())
                    .thenReturn(false)
                    .thenThrow(new IllegalStateException("exception when starting controller"))
                    .thenReturn(true);

                when(policyEngineManager.createPolicyController(CONTROLLER_NAME, properties))
                    .thenThrow(new IllegalArgumentException("exception creating controller"))
                    .thenReturn(policyController);

                setupPolicyEngineManager(engConst);

                restApi = mock(RestManager.class);

                when(restApi.controllerAdd(null)).thenCallRealMethod();
                when(restApi.controllerAdd(invalidProps)).thenCallRealMethod();
                when(restApi.controllerAdd(properties)).thenCallRealMethod();

                // first test - null properties config
                var response = restApi.controllerAdd(null);
                assertNotNull(response);
                assertInstanceOf(Response.class, response);
                assertEquals(BAD_REQUEST.getStatusCode(), response.getStatus());

                // second test - controllerName is empty
                response = restApi.controllerAdd(invalidProps);
                assertEquals(BAD_REQUEST.getStatusCode(), response.getStatus());
                assertThat(((RestManager.Error) response.getEntity()).getError())
                    .contains("Configuration must have an entry for controller.name");

                // third test - controller exists
                response = restApi.controllerAdd(properties);
                assertEquals(NOT_MODIFIED.getStatusCode(), response.getStatus());

                // fourth test - IllegalStateException
                response = restApi.controllerAdd(properties);
                assertEquals(NOT_ACCEPTABLE.getStatusCode(), response.getStatus());
                assertThat(((RestManager.Error) response.getEntity()).getError())
                    .contains("myControllerName not found");

                // fifth test - cannot create controller
                response = restApi.controllerAdd(properties);
                assertEquals(BAD_REQUEST.getStatusCode(), response.getStatus());
                assertThat(((RestManager.Error) response.getEntity()).getError())
                    .contains("exception creating controller");

                // sixth test - cannot start controller
                response = restApi.controllerAdd(properties);
                assertEquals(PARTIAL_CONTENT.getStatusCode(), response.getStatus());
                assertThat(((RestManager.Error) response.getEntity()).getError())
                    .contains("myControllerName can't be started");

                // seventh test - cannot start controller but Exception
                response = restApi.controllerAdd(properties);
                assertEquals(PARTIAL_CONTENT.getStatusCode(), response.getStatus());
                assertEquals(policyController, response.getEntity());

                // final test - works
                response = restApi.controllerAdd(properties);
                assertEquals(CREATED.getStatusCode(), response.getStatus());
                assertEquals(policyController, response.getEntity());
            }
        }
    }

    @Test
    void controllerFeature() {
        try (MockedStatic<PolicyControllerConstants> controllerConst = mockStatic(PolicyControllerConstants.class)) {
            var myFeature = mock(PolicyControllerFeatureApi.class);
            when(controllerFactory.getFeatureProvider("myFeature"))
                .thenReturn(myFeature)
                .thenThrow(new IllegalArgumentException("exception when getting feature"));
            setupPolicyControllerFactory(controllerConst);

            restApi = mock(RestManager.class);
            when(restApi.controllerFeature("myFeature")).thenCallRealMethod();

            var response = restApi.controllerFeature("myFeature");
            assertNotNull(response);
            assertInstanceOf(Response.class, response);
            assertEquals(OK.getStatusCode(), response.getStatus());
            assertEquals(myFeature, response.getEntity());

            response = restApi.controllerFeature("myFeature");
            assertEquals(NOT_FOUND.getStatusCode(), response.getStatus());
            assertThat(((RestManager.Error) response.getEntity()).getError())
                .contains("exception when getting feature");
        }
    }

    @Test
    void controllerDelete() {
        try (MockedStatic<PolicyControllerConstants> controllerConst = mockStatic(PolicyControllerConstants.class)) {
            try (MockedStatic<PolicyEngineConstants> engConst = mockStatic(PolicyEngineConstants.class)) {
                setupLockManagerAndExecutorService();

                var policyController = mock(PolicyController.class);

                when(controllerFactory.get("nullController"))
                    .thenReturn(null);
                when(controllerFactory.get("exceptionController"))
                    .thenThrow(new IllegalArgumentException("can't find the controller"))
                    .thenThrow(new IllegalStateException("exception to test fetch policy controller"));
                when(controllerFactory.get("myValidController")).thenReturn(policyController);

                setupPolicyControllerFactory(controllerConst);

                doThrow(new IllegalStateException("exception when deleting controller"))
                    .doNothing()
                    .when(policyEngineManager).removePolicyController("myValidController");

                setupPolicyEngineManager(engConst);

                restApi = mock(RestManager.class);
                when(restApi.controllerDelete(anyString())).thenCallRealMethod();

                // first test - controller returns null
                var response = restApi.controllerDelete("nullController");
                assertNotNull(response);
                assertInstanceOf(Response.class, response);
                assertEquals(BAD_REQUEST.getStatusCode(), response.getStatus());
                assertThat(((RestManager.Error) response.getEntity()).getError())
                    .contains("nullController does not exist");

                // second test - getController throws IllegalArgExc
                response = restApi.controllerDelete("exceptionController");
                assertEquals(BAD_REQUEST.getStatusCode(), response.getStatus());
                assertThat(((RestManager.Error) response.getEntity()).getError())
                    .contains("can't find the controller");

                // third test - getController throws IllegalStateExc
                response = restApi.controllerDelete("exceptionController");
                assertEquals(NOT_ACCEPTABLE.getStatusCode(), response.getStatus());
                assertThat(((RestManager.Error) response.getEntity()).getError())
                    .contains("exceptionController not acceptable");

                // fourth test - gets controller but exception when removing
                response = restApi.controllerDelete("myValidController");
                assertEquals(INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
                assertThat(((RestManager.Error) response.getEntity()).getError())
                    .contains("exception when deleting controller");

                // fifth test - gets controller and removes it from engine manager
                response = restApi.controllerDelete("myValidController");
                assertEquals(OK.getStatusCode(), response.getStatus());
                assertEquals(policyController, response.getEntity());
            }
        }
    }

    @Test
    void controllerUpdate() {
        try (MockedStatic<PolicyEngineConstants> constants = mockStatic(PolicyEngineConstants.class)) {
            setupLockManagerAndExecutorService();

            var config = mock(ControllerConfiguration.class);
            when(config.getName()).thenReturn(CONTROLLER_NAME);

            var policyController = mock(PolicyController.class);
            when(policyEngineManager.updatePolicyController(config))
                .thenThrow(new IllegalArgumentException("exception to test fetch policy controller"))
                .thenReturn(null)
                .thenReturn(policyController);

            setupPolicyEngineManager(constants);

            restApi = mock(RestManager.class);
            when(restApi.catchArgStateGenericEx(any(), any())).thenCallRealMethod();
            when(restApi.controllerUpdate(config, "invalidName")).thenCallRealMethod();
            when(restApi.controllerUpdate(config, CONTROLLER_NAME)).thenCallRealMethod();
            when(restApi.controllerUpdate(null, CONTROLLER_NAME)).thenCallRealMethod();
            when(restApi.controllerUpdate(config, "")).thenCallRealMethod();

            // invalid controller name
            var response = restApi.controllerUpdate(config, "");
            assertNotNull(response);
            assertInstanceOf(Response.class, response);
            assertEquals(BAD_REQUEST.getStatusCode(), response.getStatus());

            // controller configuration is null
            response = restApi.controllerUpdate(null, CONTROLLER_NAME);
            assertEquals(BAD_REQUEST.getStatusCode(), response.getStatus());

            // controller name doesn't match name in configuration
            response = restApi.controllerUpdate(config, "invalidName");
            assertEquals(BAD_REQUEST.getStatusCode(), response.getStatus());

            // IllegalArgException
            response = restApi.controllerUpdate(config, CONTROLLER_NAME);
            assertEquals(NOT_FOUND.getStatusCode(), response.getStatus());

            // return null controller
            response = restApi.controllerUpdate(config, CONTROLLER_NAME);
            assertEquals(BAD_REQUEST.getStatusCode(), response.getStatus());

            // return valid controller
            response = restApi.controllerUpdate(config, CONTROLLER_NAME);
            assertEquals(OK.getStatusCode(), response.getStatus());
            assertEquals(policyController, response.getEntity());
        }
    }

    @Test
    void controllerLock() {
        try (MockedStatic<PolicyControllerConstants> controllerConst = mockStatic(PolicyControllerConstants.class)) {
            var policyController = mock(PolicyController.class);
            when(policyController.lock())
                .thenReturn(true)
                .thenReturn(false);

            when(controllerFactory.get(CONTROLLER_NAME)).thenReturn(policyController);

            setupPolicyControllerFactory(controllerConst);

            restApi = mock(RestManager.class);

            when(restApi.controllerLock(CONTROLLER_NAME)).thenCallRealMethod();

            // controller lock
            var response = restApi.controllerLock(CONTROLLER_NAME);
            assertNotNull(response);
            assertInstanceOf(Response.class, response);
            assertEquals(OK.getStatusCode(), response.getStatus());
            assertEquals(policyController, response.getEntity());

            // controller fails to lock
            response = restApi.controllerLock(CONTROLLER_NAME);
            assertEquals(NOT_ACCEPTABLE.getStatusCode(), response.getStatus());
            assertThat(((RestManager.Error) response.getEntity()).getError())
                .contains("myControllerName cannot be locked");
        }
    }

    @Test
    void controllerUnlock() {
        try (MockedStatic<PolicyControllerConstants> controllerConst = mockStatic(PolicyControllerConstants.class)) {
            var policyController = mock(PolicyController.class);
            when(policyController.unlock())
                .thenReturn(true)
                .thenReturn(false);

            when(controllerFactory.get(CONTROLLER_NAME)).thenReturn(policyController);

            setupPolicyControllerFactory(controllerConst);

            restApi = mock(RestManager.class);

            when(restApi.controllerUnlock(CONTROLLER_NAME)).thenCallRealMethod();

            // controller unlock
            var response = restApi.controllerUnlock(CONTROLLER_NAME);
            assertNotNull(response);
            assertInstanceOf(Response.class, response);
            assertEquals(OK.getStatusCode(), response.getStatus());
            assertEquals(policyController, response.getEntity());

            // controller fails to unlock
            response = restApi.controllerUnlock(CONTROLLER_NAME);
            assertEquals(NOT_ACCEPTABLE.getStatusCode(), response.getStatus());
            assertThat(((RestManager.Error) response.getEntity()).getError())
                .contains("myControllerName cannot be unlocked");
        }
    }

    @Test
    void droolsFactsDelete() {
        try (MockedStatic<PolicyControllerConstants> controllerConst = mockStatic(PolicyControllerConstants.class)) {
            var sessionName = "sessionName";
            var queryName = "queryName";
            var queriedEntity = "queriedEntity";

            var drools = mock(NullDroolsController.class);
            when(drools.factQuery(sessionName, queryName, queriedEntity, true))
                .thenReturn(new ArrayList<>(List.of()));
            var policyController = mock(PolicyController.class);
            when(policyController.getDrools()).thenReturn(drools);

            var controllerWithoutDrools = mock(PolicyController.class);
            when(controllerWithoutDrools.getDrools()).thenReturn(null);

            when(controllerFactory.get("nullController")).thenReturn(null);
            when(controllerFactory.get("controllerWithoutDrools")).thenReturn(controllerWithoutDrools);
            when(controllerFactory.get(CONTROLLER_NAME)).thenReturn(policyController);

            setupPolicyControllerFactory(controllerConst);

            restApi = mock(RestManager.class);

            when(restApi.catchArgStateGenericEx(any(), any())).thenCallRealMethod();
            when(restApi.getDroolsController(anyString())).thenCallRealMethod();
            when(restApi.droolsFactsDelete("nullController", sessionName, queryName, queriedEntity))
                .thenCallRealMethod();
            when(restApi.droolsFactsDelete("controllerWithoutDrools", sessionName, queryName, queriedEntity))
                .thenCallRealMethod();
            when(restApi.droolsFactsDelete(CONTROLLER_NAME, sessionName, queryName, queriedEntity))
                .thenCallRealMethod();

            // controller is null
            var response = restApi.droolsFactsDelete("nullController", sessionName, queryName, queriedEntity);
            assertNotNull(response);
            assertInstanceOf(Response.class, response);
            assertEquals(NOT_FOUND.getStatusCode(), response.getStatus());
            assertEquals("nullController:sessionName:queryName:queriedEntity not found",
                ((RestManager.Error) response.getEntity()).getError());

            // controller has no drools
            response = restApi.droolsFactsDelete("controllerWithoutDrools", sessionName, queryName, queriedEntity);
            assertEquals(NOT_FOUND.getStatusCode(), response.getStatus());
            assertEquals("controllerWithoutDrools:sessionName:queryName:queriedEntity not found",
                ((RestManager.Error) response.getEntity()).getError());

            // delete works
            response = restApi.droolsFactsDelete(CONTROLLER_NAME, sessionName, queryName, queriedEntity);
            assertEquals(OK.getStatusCode(), response.getStatus());
            assertInstanceOf(List.class, response.getEntity());
        }
    }

    @Test
    void rules() {
        when(restApi.rules(anyString())).thenCallRealMethod();
        var response = restApi.rules("expression");
        assertNotNull(response);
        assertInstanceOf(Response.class, response);
        assertEquals(OK.getStatusCode(), response.getStatus());
    }

    @Test
    void decoderFilter2() {
        try (MockedStatic<EventProtocolCoderConstants> constants = mockStatic(EventProtocolCoderConstants.class)) {
            var decoder = mock(ProtocolCoderToolset.class);
            when(decoder.getCoders()).thenReturn(new ArrayList<>(List.of()));
            when(coderManager.getDecoders(GROUP_ID, ARTIFACT_ID, TOPIC))
                .thenReturn(decoder);
            when(coderManager.getDecoders(GROUP_ID, ARTIFACT_ID, "invalidTopic"))
                .thenReturn(null);

            setupEventProtocolManager(constants);

            var drools = getDroolsControllerWithGroupArtifact();
            restApi = mock(RestManager.class);
            when(restApi.getDroolsController(CONTROLLER_NAME)).thenReturn(drools);
            when(restApi.catchArgStateGenericEx(any(), any())).thenCallRealMethod();
            when(restApi.decoderFilter2(anyString(), anyString())).thenCallRealMethod();
            when(restApi.getDroolsController("noDecoderCtrl")).thenReturn(drools);
            when(restApi.getDroolsController("exceptionCtrl"))
                .thenThrow(new IllegalArgumentException("exceptionCtrl"));

            // should be ok
            var response = restApi.decoderFilter2(CONTROLLER_NAME, TOPIC);
            assertNotNull(response);
            assertInstanceOf(Response.class, response);
            assertEquals(OK.getStatusCode(), response.getStatus());
            assertInstanceOf(List.class, response.getEntity());

            // controller has no decoder
            response = restApi.decoderFilter2("noDecoderCtrl", "invalidTopic");
            assertNotNull(response);
            assertInstanceOf(Response.class, response);
            assertEquals(BAD_REQUEST.getStatusCode(), response.getStatus());

            // can't get a controller
            response = restApi.decoderFilter2("exceptionCtrl", TOPIC);
            assertNotNull(response);
            assertInstanceOf(Response.class, response);
            assertEquals(NOT_FOUND.getStatusCode(), response.getStatus());
        }
    }

    @Test
    void decoderFilter1() {
        try (MockedStatic<EventProtocolCoderConstants> constants = mockStatic(EventProtocolCoderConstants.class)) {
            var decoder = mock(ProtocolCoderToolset.class);
            when(decoder.getCoder(FACT_CLASS)).thenReturn(mock(EventProtocolCoder.CoderFilters.class));
            when(decoder.getCoder("invalidClass")).thenReturn(null);

            when(coderManager.getDecoders(GROUP_ID, ARTIFACT_ID, TOPIC))
                .thenReturn(decoder);
            when(coderManager.getDecoders(GROUP_ID, ARTIFACT_ID, "invalidTopic"))
                .thenThrow(new IllegalArgumentException("exception"));

            setupEventProtocolManager(constants);

            var drools = getDroolsControllerWithGroupArtifact();
            restApi = mock(RestManager.class);
            when(restApi.getDroolsController(CONTROLLER_NAME)).thenReturn(drools);
            when(restApi.catchArgStateGenericEx(any(), any())).thenCallRealMethod();
            when(restApi.decoderFilter1(anyString(), anyString(), anyString())).thenCallRealMethod();

            // should be ok
            var response = restApi.decoderFilter1(CONTROLLER_NAME, TOPIC, FACT_CLASS);
            assertNotNull(response);
            assertInstanceOf(Response.class, response);
            assertEquals(OK.getStatusCode(), response.getStatus());
            assertInstanceOf(EventProtocolCoder.CoderFilters.class, response.getEntity());

            // controller has no decoder
            response = restApi.decoderFilter1(CONTROLLER_NAME, TOPIC, "invalidClass");
            assertNotNull(response);
            assertInstanceOf(Response.class, response);
            assertEquals(BAD_REQUEST.getStatusCode(), response.getStatus());

            // can't get a controller
            response = restApi.decoderFilter1(CONTROLLER_NAME, "invalidTopic", FACT_CLASS);
            assertNotNull(response);
            assertInstanceOf(Response.class, response);
            assertEquals(NOT_FOUND.getStatusCode(), response.getStatus());
        }
    }

    @Test
    void decoderFilter() {
        try (MockedStatic<EventProtocolCoderConstants> constants = mockStatic(EventProtocolCoderConstants.class)) {
            var coderFilter = new EventProtocolCoder.CoderFilters(FACT_CLASS, new JsonProtocolFilter(), 1);

            var decoder = mock(ProtocolCoderToolset.class);
            when(decoder.getCoder(FACT_CLASS)).thenReturn(coderFilter);
            when(decoder.getCoder("invalidClass")).thenReturn(null);

            when(coderManager.getDecoders(GROUP_ID, ARTIFACT_ID, TOPIC))
                .thenReturn(decoder);
            when(coderManager.getDecoders(GROUP_ID, ARTIFACT_ID, "invalidTopic"))
                .thenThrow(new IllegalArgumentException("exception"));

            setupEventProtocolManager(constants);


            restApi = mock(RestManager.class);
            var configFilters = new JsonProtocolFilter();
            when(restApi.decoderFilter(eq(configFilters), eq(CONTROLLER_NAME), anyString(),
                anyString())).thenCallRealMethod();
            when(restApi.catchArgStateGenericEx(any(), any())).thenCallRealMethod();
            when(restApi.decoderFilter(isNull(), eq(CONTROLLER_NAME), anyString(), anyString())).thenCallRealMethod();
            var drools = getDroolsControllerWithGroupArtifact();
            when(restApi.getDroolsController(CONTROLLER_NAME)).thenReturn(drools);

            // should be ok
            var response = restApi.decoderFilter(configFilters, CONTROLLER_NAME, TOPIC, FACT_CLASS);
            assertNotNull(response);
            assertInstanceOf(Response.class, response);
            assertEquals(OK.getStatusCode(), response.getStatus());
            if (response.getEntity() instanceof EventProtocolCoder.CoderFilters) {
                var filters = (EventProtocolCoder.CoderFilters) response.getEntity();
                assertEquals(configFilters, filters.getFilter());
            }

            // controller has no decoder
            response = restApi.decoderFilter(configFilters, CONTROLLER_NAME, TOPIC, "invalidClass");
            assertNotNull(response);
            assertEquals(BAD_REQUEST.getStatusCode(), response.getStatus());

            // can't get a controller
            response = restApi.decoderFilter(configFilters, CONTROLLER_NAME, "invalidTopic", FACT_CLASS);
            assertNotNull(response);
            assertEquals(NOT_FOUND.getStatusCode(), response.getStatus());

            // can't get a controller
            response = restApi.decoderFilter(null, CONTROLLER_NAME, "invalidTopic", "invalidClass");
            assertNotNull(response);
            assertEquals(BAD_REQUEST.getStatusCode(), response.getStatus());
        }
    }

    @Test
    void decoderFilterRules() {
        when(restApi.catchArgStateGenericEx(any(), any())).thenCallRealMethod();
        when(restApi.decoderFilterRules(anyString(), anyString(), anyString())).thenCallRealMethod();

        when(restApi.checkControllerDecoderAndFilter(CONTROLLER_NAME, TOPIC, FACT_CLASS))
            .thenReturn(new JsonProtocolFilter());
        when(restApi.checkControllerDecoderAndFilter(CONTROLLER_NAME, TOPIC, "invalidClass"))
            .thenReturn(Response.status(BAD_REQUEST).build());
        // trying to trigger RuntimeException for code coverage - normally it wouldn't happen here
        when(restApi.checkControllerDecoderAndFilter("invalidController", TOPIC, FACT_CLASS))
            .thenReturn(null);

        // should return a string rule
        var response = restApi.decoderFilterRules(CONTROLLER_NAME, TOPIC, FACT_CLASS);
        assertNotNull(response);
        assertInstanceOf(Response.class, response);
        assertEquals(OK.getStatusCode(), response.getStatus());
        assertInstanceOf(String.class, response.getEntity());

        // checkControllerEtc... returned error response, so it just returns it to function
        response = restApi.decoderFilterRules(CONTROLLER_NAME, TOPIC, "invalidClass");
        assertEquals(BAD_REQUEST.getStatusCode(), response.getStatus());

        // the runtime exception
        response = restApi.decoderFilterRules("invalidController", TOPIC, FACT_CLASS);
        assertEquals(INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    }

    @Test
    void decoderFilterRuleDelete() {
        when(restApi.catchArgStateGenericEx(any(), any())).thenCallRealMethod();
        when(restApi.decoderFilterRuleDelete(anyString(), anyString(), anyString())).thenCallRealMethod();

        when(restApi.checkControllerDecoderAndFilter(CONTROLLER_NAME, TOPIC, FACT_CLASS))
            .thenReturn(new JsonProtocolFilter());
        when(restApi.checkControllerDecoderAndFilter(CONTROLLER_NAME, TOPIC, "invalidClass"))
            .thenReturn(Response.status(BAD_REQUEST).build());

        // should return a string rule
        var response = restApi.decoderFilterRuleDelete(CONTROLLER_NAME, TOPIC, FACT_CLASS);
        assertNotNull(response);
        assertInstanceOf(Response.class, response);
        assertEquals(OK.getStatusCode(), response.getStatus());
        assertInstanceOf(String.class, response.getEntity());

        // checkControllerEtc... returned error response, so it just returns it to function
        response = restApi.decoderFilterRuleDelete(CONTROLLER_NAME, TOPIC, "invalidClass");
        assertEquals(BAD_REQUEST.getStatusCode(), response.getStatus());
    }

    @Test
    void decoderFilterRule() {
        when(restApi.catchArgStateGenericEx(any(), any())).thenCallRealMethod();
        when(restApi.decoderFilterRule(anyString(), anyString(), anyString(), anyString())).thenCallRealMethod();

        when(restApi.decoderFilterRule2(CONTROLLER_NAME, TOPIC, FACT_CLASS, "rule"))
            .thenReturn(new JsonProtocolFilter().getRule());
        when(restApi.decoderFilterRule2(CONTROLLER_NAME, TOPIC, FACT_CLASS, ""))
            .thenCallRealMethod();
        when(restApi.decoderFilterRule("invalidController", TOPIC, FACT_CLASS, "rule"))
            .thenThrow(new IllegalArgumentException("invalid controller"));

        var response = restApi.decoderFilterRule(CONTROLLER_NAME, TOPIC, FACT_CLASS, "rule");
        assertNotNull(response);
        assertInstanceOf(Response.class, response);
        assertEquals(OK.getStatusCode(), response.getStatus());
        assertInstanceOf(String.class, response.getEntity());

        response = restApi.decoderFilterRule(CONTROLLER_NAME, TOPIC, FACT_CLASS, "");
        assertEquals(BAD_REQUEST.getStatusCode(), response.getStatus());
        assertInstanceOf(RestManager.Error.class, response.getEntity());

        response = restApi.decoderFilterRule("invalidController", TOPIC, FACT_CLASS, "rule");
        assertEquals(NOT_FOUND.getStatusCode(), response.getStatus());
        assertInstanceOf(RestManager.Error.class, response.getEntity());
    }

    @Test
    void decode_EmptyParams() {
        when(restApi.decode(anyString(), anyString(), anyString())).thenCallRealMethod();

        var response = restApi.decode(CONTROLLER_NAME, "", "json");
        assertNotNull(response);
        assertInstanceOf(Response.class, response);
        assertEquals(NOT_ACCEPTABLE.getStatusCode(), response.getStatus());

        response = restApi.decode("", TOPIC, "json");
        assertNotNull(response);
        assertInstanceOf(Response.class, response);
        assertEquals(NOT_ACCEPTABLE.getStatusCode(), response.getStatus());
    }

    @Test
    void decode() {
        try (MockedStatic<EventProtocolCoderConstants> constants = mockStatic(EventProtocolCoderConstants.class)) {
            var event = new Object();
            when(coderManager.decode(GROUP_ID, ARTIFACT_ID, TOPIC, "json")).thenReturn(event);
            when(coderManager.decode(GROUP_ID, ARTIFACT_ID, "otherTopic", "json")).thenReturn(event);
            when(coderManager.decode(GROUP_ID, ARTIFACT_ID, TOPIC, "invalidJson"))
                .thenThrow(new IllegalArgumentException("invalid json"));

            when(coderManager.encode(TOPIC, event)).thenReturn("json");
            when(coderManager.encode("otherTopic", event))
                .thenThrow(new IllegalArgumentException("invalid topic"));

            setupEventProtocolManager(constants);

            var drools = getDroolsControllerWithGroupArtifact();

            restApi = mock(RestManager.class);
            when(restApi.getDroolsController(CONTROLLER_NAME)).thenReturn(drools);
            when(restApi.decode(anyString(), anyString(), anyString())).thenCallRealMethod();

            // should work
            var response = restApi.decode(CONTROLLER_NAME, TOPIC, "json");
            assertNotNull(response);
            assertInstanceOf(Response.class, response);
            assertEquals(OK.getStatusCode(), response.getStatus());
            assertNotNull(response.getEntity());
            if (response.getEntity() instanceof RestManager.CodingResult) {
                var codingResult = (RestManager.CodingResult) response.getEntity();
                assertTrue(codingResult.getDecoding());
                assertTrue(codingResult.getEncoding());
                assertEquals("json", codingResult.getJsonEncoding());
            }

            // can't decode
            response = restApi.decode(CONTROLLER_NAME, TOPIC, "invalidJson");
            assertEquals(BAD_REQUEST.getStatusCode(), response.getStatus());

            // can't encode, but return decoded result
            response = restApi.decode(CONTROLLER_NAME, "otherTopic", "json");
            assertEquals(OK.getStatusCode(), response.getStatus());
            assertNotNull(response.getEntity());
            if (response.getEntity() instanceof RestManager.CodingResult) {
                var codingResult = (RestManager.CodingResult) response.getEntity();
                assertTrue(codingResult.getDecoding());
                assertFalse(codingResult.getEncoding());
                assertNull(codingResult.getJsonEncoding());
            }
        }
    }

    @Test
    void encoderFilters() {
        try (MockedStatic<EventProtocolCoderConstants> constants = mockStatic(EventProtocolCoderConstants.class)) {
            when(coderManager.getEncoderFilters(GROUP_ID, ARTIFACT_ID))
                .thenReturn(new ArrayList<>(List.of()));

            setupEventProtocolManager(constants);

            var drools = getDroolsControllerWithGroupArtifact();
            when(restApi.getDroolsController(CONTROLLER_NAME)).thenReturn(drools);
            when(restApi.getDroolsController("invalidController"))
                .thenThrow(new IllegalArgumentException("invalid controller"));
            when(restApi.catchArgStateGenericEx(any(), any())).thenCallRealMethod();
            when(restApi.encoderFilters(anyString())).thenCallRealMethod();

            // should work
            var response = restApi.encoderFilters(CONTROLLER_NAME);
            assertNotNull(response);
            assertInstanceOf(Response.class, response);
            assertEquals(OK.getStatusCode(), response.getStatus());
            assertNotNull(response.getEntity());

            // exceptions
            response = restApi.encoderFilters("invalidController");
            assertEquals(NOT_FOUND.getStatusCode(), response.getStatus());
        }
    }

    @Test
    void topicsLock() {
        try (MockedStatic<TopicEndpointManager> constants = mockStatic(TopicEndpointManager.class)) {
            when(topicManager.lock())
                .thenReturn(true)
                .thenReturn(false);
            setupTopicEndpointManager(constants);

            restApi = mock(RestManager.class);
            when(restApi.topicsLock()).thenCallRealMethod();

            // should work and return ok
            var response = restApi.topicsLock();
            assertNotNull(response);
            assertInstanceOf(Response.class, response);
            assertEquals(OK.getStatusCode(), response.getStatus());

            // lock fails, return false
            response = restApi.topicsLock();
            assertNotNull(response);
            assertEquals(NOT_ACCEPTABLE.getStatusCode(), response.getStatus());
        }
    }

    @Test
    void topicsUnlock() {
        try (MockedStatic<TopicEndpointManager> constants = mockStatic(TopicEndpointManager.class)) {
            when(topicManager.unlock())
                .thenReturn(true)
                .thenReturn(false);
            setupTopicEndpointManager(constants);

            restApi = mock(RestManager.class);
            when(restApi.topicsUnlock()).thenCallRealMethod();

            // should work and return ok
            var response = restApi.topicsUnlock();
            assertNotNull(response);
            assertInstanceOf(Response.class, response);
            assertEquals(OK.getStatusCode(), response.getStatus());

            // lock fails, return false
            response = restApi.topicsUnlock();
            assertNotNull(response);
            assertEquals(NOT_ACCEPTABLE.getStatusCode(), response.getStatus());
        }
    }

    @Test
    void commSources() {
        try (MockedStatic<TopicEndpointManager> constants = mockStatic(TopicEndpointManager.class)) {
            when(topicManager.getNoopTopicSources()).thenReturn(new ArrayList<>(List.of()));
            when(topicManager.getKafkaTopicSources()).thenReturn(new ArrayList<>(List.of()));
            setupTopicEndpointManager(constants);

            restApi = mock(RestManager.class);
            when(restApi.commSources(anyString())).thenCallRealMethod();

            var response = restApi.commSources("noop");
            assertNotNull(response);
            assertInstanceOf(Response.class, response);
            assertEquals(OK.getStatusCode(), response.getStatus());

            response = restApi.commSources("kafka");
            assertEquals(OK.getStatusCode(), response.getStatus());

            // topic type rest valid not supported
            response = restApi.commSources("rest");
            assertEquals(BAD_REQUEST.getStatusCode(), response.getStatus());

            // empty topic type
            response = restApi.commSources("");
            assertEquals(NOT_ACCEPTABLE.getStatusCode(), response.getStatus());
        }
    }

    @Test
    void commSinks() {
        try (MockedStatic<TopicEndpointManager> constants = mockStatic(TopicEndpointManager.class)) {
            when(topicManager.getNoopTopicSinks()).thenReturn(new ArrayList<>(List.of()));
            when(topicManager.getKafkaTopicSinks()).thenReturn(new ArrayList<>(List.of()));
            setupTopicEndpointManager(constants);

            restApi = mock(RestManager.class);
            when(restApi.commSinks(anyString())).thenCallRealMethod();

            var response = restApi.commSinks("noop");
            assertNotNull(response);
            assertInstanceOf(Response.class, response);
            assertEquals(OK.getStatusCode(), response.getStatus());

            response = restApi.commSinks("kafka");
            assertEquals(OK.getStatusCode(), response.getStatus());

            // topic type rest valid but not supported
            response = restApi.commSinks("rest");
            assertEquals(BAD_REQUEST.getStatusCode(), response.getStatus());

            // empty topic type
            response = restApi.commSinks("");
            assertEquals(NOT_ACCEPTABLE.getStatusCode(), response.getStatus());
        }
    }

    @Test
    void loggers() {
        try (MockedStatic<LoggerFactory> loggerFactory = mockStatic(LoggerFactory.class)) {
            var mockFactory = mock(ILoggerFactory.class);
            loggerFactory.when(LoggerFactory::getILoggerFactory).thenReturn(mockFactory);

            restApi = mock(RestManager.class);
            when(restApi.loggers()).thenCallRealMethod();

            var response = restApi.loggers();
            assertNotNull(response);
            assertInstanceOf(Response.class, response);
            assertEquals(INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        }
    }

    @Test
    void loggerName1() {
        try (MockedStatic<LoggerFactory> loggerFactory = mockStatic(LoggerFactory.class)) {
            var mockFactory = mock(ILoggerFactory.class);
            loggerFactory.when(LoggerFactory::getILoggerFactory).thenReturn(mockFactory);

            restApi = mock(RestManager.class);
            when(restApi.loggerName1(anyString())).thenCallRealMethod();

            var response = restApi.loggerName1("logger1");
            assertNotNull(response);
            assertInstanceOf(Response.class, response);
            assertEquals(INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        }
    }

    @Test
    void loggerName_EmptyValues() {
        when(restApi.loggerName(anyString(), anyString())).thenCallRealMethod();

        var response = restApi.loggerName("", "info");
        assertNotNull(response);
        assertInstanceOf(Response.class, response);
        assertEquals(NOT_ACCEPTABLE.getStatusCode(), response.getStatus());
        assertThat(((RestManager.Error) response.getEntity()).getError())
            .contains("logger name: not acceptable");

        response = restApi.loggerName("logger1", "");
        assertNotNull(response);
        assertInstanceOf(Response.class, response);
        assertEquals(NOT_ACCEPTABLE.getStatusCode(), response.getStatus());
        assertThat(((RestManager.Error) response.getEntity()).getError())
            .contains("logger level: not acceptable");
    }

    @Test
    void testLoggerName_Exceptions() {
        when(restApi.loggerName(anyString(), anyString())).thenCallRealMethod();

        var response = restApi.loggerName("audit", "abc");
        assertNotNull(response);
        assertInstanceOf(Response.class, response);
        assertEquals(INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    }

    @Test
    void testCheckControllerDecoderAndFilter() {
        try (MockedStatic<EventProtocolCoderConstants> constants = mockStatic(EventProtocolCoderConstants.class)) {
            var coderFilter = new EventProtocolCoder.CoderFilters(FACT_CLASS, new JsonProtocolFilter(), 1);
            var decoder = mock(ProtocolCoderToolset.class);
            when(decoder.getCoder(FACT_CLASS)).thenReturn(coderFilter);

            when(coderManager.getDecoders(GROUP_ID, ARTIFACT_ID, TOPIC))
                .thenReturn(decoder);

            var decoderWithoutFactClass = mock(ProtocolCoderToolset.class);
            when(decoderWithoutFactClass.getCoder("noFactClass")).thenReturn(null);
            when(coderManager.getDecoders(GROUP_ID, ARTIFACT_ID, "topic2"))
                .thenReturn(decoderWithoutFactClass);

            var coderFactClassWithoutFilter = mock(EventProtocolCoder.CoderFilters.class);
            when(coderFactClassWithoutFilter.getFilter()).thenReturn(null);
            var decoderWithoutFilter = mock(ProtocolCoderToolset.class);
            when(decoderWithoutFilter.getCoder("noFilterClass")).thenReturn(coderFactClassWithoutFilter);
            when(coderManager.getDecoders(GROUP_ID, ARTIFACT_ID, "topic3"))
                .thenReturn(decoderWithoutFilter);

            setupEventProtocolManager(constants);

            restApi = mock(RestManager.class);

            var drools = getDroolsControllerWithGroupArtifact();
            when(restApi.getDroolsController(CONTROLLER_NAME)).thenReturn(drools);
            when(restApi.getDroolsController("exceptionController"))
                .thenThrow(new IllegalArgumentException("exception"));
            when(restApi.catchArgStateGenericEx(any(), any())).thenCallRealMethod();
            when(restApi.checkControllerDecoderAndFilter(anyString(), anyString(), anyString()))
                .thenCallRealMethod();

            // should work fine and get back JsonProtocolFilter object
            var result = restApi.checkControllerDecoderAndFilter(CONTROLLER_NAME, TOPIC, FACT_CLASS);
            assertNotNull(result);
            assertInstanceOf(JsonProtocolFilter.class, result);

            // decoder doesn't have coder based on noFactClass parameter
            var response = restApi.checkControllerDecoderAndFilter(CONTROLLER_NAME, "topic2", "noFactClass");
            assertNotNull(response);
            if (response instanceof Response) {
                var actualResponse = (Response) response;
                assertEquals(BAD_REQUEST.getStatusCode(), actualResponse.getStatus());
                assertThat(((RestManager.Error) actualResponse.getEntity()).getError())
                    .contains("noFactClass does not exist");
            }

            // decoder has factClass, but factClass has no filter
            response = restApi.checkControllerDecoderAndFilter(CONTROLLER_NAME, "topic3", "noFilterClass");
            assertNotNull(response);
            if (response instanceof Response) {
                var actualResponse = (Response) response;
                assertEquals(BAD_REQUEST.getStatusCode(), actualResponse.getStatus());
                assertThat(((RestManager.Error) actualResponse.getEntity()).getError())
                    .contains("noFilterClass has no filters");
            }
        }
    }

    @Test
    void testDecoderFilterRule2() {
        var filter = new JsonProtocolFilter();
        when(restApi.checkControllerDecoderAndFilter(CONTROLLER_NAME, TOPIC, FACT_CLASS))
            .thenReturn(filter);
        when(restApi.checkControllerDecoderAndFilter(CONTROLLER_NAME, TOPIC, "invalidFactClass"))
            .thenReturn(Response.status(BAD_REQUEST).build());

        when(restApi.decoderFilterRule2(anyString(), anyString(), anyString(), anyString()))
            .thenCallRealMethod();
        when(restApi.decoderFilterRule2(anyString(), anyString(), anyString(), isNull()))
            .thenCallRealMethod();

        // should work
        var result = restApi.decoderFilterRule2(CONTROLLER_NAME, TOPIC, FACT_CLASS, "rule");
        assertNotNull(result);
        assertInstanceOf(String.class, result);

        // rule is null
        var response = restApi.decoderFilterRule2(CONTROLLER_NAME, TOPIC, FACT_CLASS, null);
        assertNotNull(response);
        if (response instanceof Response) {
            var actualResponse = (Response) response;
            assertEquals(BAD_REQUEST.getStatusCode(), actualResponse.getStatus());
            assertThat(((RestManager.Error) actualResponse.getEntity()).getError())
                .contains("no filter rule provided");
        }

        // rule is empty string
        response = restApi.decoderFilterRule2(CONTROLLER_NAME, TOPIC, FACT_CLASS, "");
        assertNotNull(response);
        if (response instanceof Response) {
            var actualResponse = (Response) response;
            assertEquals(BAD_REQUEST.getStatusCode(), actualResponse.getStatus());
            assertThat(((RestManager.Error) actualResponse.getEntity()).getError())
                .contains("no filter rule provided");
        }

        // checkControllerDecoderFilter returns error response
        response = restApi.decoderFilterRule2(CONTROLLER_NAME, TOPIC, "invalidFactClass", "rule");
        assertNotNull(response);
        assertEquals(BAD_REQUEST.getStatusCode(), ((Response) response).getStatus());
    }

    private void setupPolicyEngineManager(MockedStatic<PolicyEngineConstants> constants) {
        constants.when(PolicyEngineConstants::getManager).thenReturn(policyEngineManager);
        assertEquals(policyEngineManager, PolicyEngineConstants.getManager());
    }

    private void setupLockManagerAndExecutorService() {
        var executorService = mock(ScheduledExecutorService.class);
        when(executorService.shutdownNow()).thenReturn(new ArrayList<>());
        when(policyEngineManager.getExecutorService()).thenReturn(executorService);

        var lockManager = mock(LockManager.class);
        doNothing().when(lockManager).shutdown();
        ReflectionTestUtils.setField(policyEngineManager, "lockManager", lockManager);
    }

    private void setupPolicyControllerFactory(MockedStatic<PolicyControllerConstants> constants) {
        constants.when(PolicyControllerConstants::getFactory).thenReturn(controllerFactory);
        assertEquals(controllerFactory, PolicyControllerConstants.getFactory());
    }

    private void setupEventProtocolManager(MockedStatic<EventProtocolCoderConstants> constants) {
        constants.when(EventProtocolCoderConstants::getManager).thenReturn(coderManager);
        assertEquals(coderManager, EventProtocolCoderConstants.getManager());
    }

    private void setupTopicEndpointManager(MockedStatic<TopicEndpointManager> constants) {
        constants.when(TopicEndpointManager::getManager).thenReturn(topicManager);
        assertEquals(topicManager, TopicEndpointManager.getManager());
    }

    private static DroolsController getDroolsControllerWithGroupArtifact() {
        var drools = mock(DroolsController.class);
        when(drools.getGroupId()).thenReturn(GROUP_ID);
        when(drools.getArtifactId()).thenReturn(ARTIFACT_ID);
        return drools;
    }
}