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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.controller.DroolsControllerConstants;
import org.onap.policy.drools.controller.DroolsControllerFactory;
import org.onap.policy.drools.controller.internal.NullDroolsController;
import org.springframework.test.util.ReflectionTestUtils;

class GenericProtocolCoderTest {

    EventProtocolEncoder encoder = new EventProtocolEncoder();
    EventProtocolDecoder decoder = new EventProtocolDecoder();

    private static final String GROUP_ID = "groupId";
    private static final String ARTIFACT_ID = "artifactId";
    private static final String TOPIC = "topic";
    private static final String VALID_KEY = GROUP_ID + ":" + ARTIFACT_ID + ":" + TOPIC;
    private static final String INVALID_KEY = "anotherKey";

    @Test
    void testAdd_ReverseCoder() {
        var params = new EventProtocolParams(GROUP_ID, ARTIFACT_ID, TOPIC, "java.lang.Object",
            mock(JsonProtocolFilter.class), mock(TopicCoderFilterConfiguration.CustomGsonCoder.class), 1);

        var myKey = "group:artifact:topic";

        var mockEncoder = mock(EventProtocolEncoder.class);
        // set the key to be returned when checking the hash maps
        when(mockEncoder.codersKey(GROUP_ID, ARTIFACT_ID, TOPIC)).thenReturn(myKey);
        when(mockEncoder.reverseCodersKey(GROUP_ID, "java.lang.Object")).thenReturn(myKey);
        doCallRealMethod().when(mockEncoder).add(params);


        // create the hash maps for coders/reverseCoders
        var toolset = mock(ProtocolCoderToolset.class);
        HashMap<String, ProtocolCoderToolset> coders = new HashMap<>();
        coders.put(myKey, toolset);
        ReflectionTestUtils.setField(mockEncoder, "coders", coders);
        HashMap<String, List<ProtocolCoderToolset>> reverseCoders = new HashMap<>();
        reverseCoders.put("group:javaClass", Arrays.asList(toolset)); // NOSONAR list can't be immutable
        ReflectionTestUtils.setField(mockEncoder, "reverseCoders", reverseCoders);

        assertDoesNotThrow(() -> mockEncoder.add(params));
        assertEquals(2, mockEncoder.reverseCoders.size());
        assertEquals(1, mockEncoder.coders.size());
    }

    @Test
    void testAdd_InvalidParams_GroupId() {
        var mockEventProtocolsParams = mock(EventProtocolParams.class);
        when(mockEventProtocolsParams.getGroupId()).thenReturn(null);

        assertThatThrownBy(() -> encoder.add(mockEventProtocolsParams))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid group id");

        when(mockEventProtocolsParams.getGroupId()).thenReturn("");

        assertThatThrownBy(() -> encoder.add(mockEventProtocolsParams))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid group id");
    }

    @Test
    void testAdd_InvalidParams_ArtifactId() {
        var mockEventProtocolsParams = mock(EventProtocolParams.class);
        when(mockEventProtocolsParams.getGroupId()).thenReturn(GROUP_ID);
        when(mockEventProtocolsParams.getArtifactId()).thenReturn(null);

        assertThatThrownBy(() -> encoder.add(mockEventProtocolsParams))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid artifact id");

        when(mockEventProtocolsParams.getArtifactId()).thenReturn("");

        assertThatThrownBy(() -> encoder.add(mockEventProtocolsParams))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid artifact id");
    }

    @Test
    void testAdd_InvalidParams_Topic() {
        var mockEventProtocolsParams = mock(EventProtocolParams.class);
        when(mockEventProtocolsParams.getGroupId()).thenReturn(GROUP_ID);
        when(mockEventProtocolsParams.getArtifactId()).thenReturn(ARTIFACT_ID);
        when(mockEventProtocolsParams.getTopic()).thenReturn(null);

        assertThatThrownBy(() -> encoder.add(mockEventProtocolsParams))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid Topic");

        when(mockEventProtocolsParams.getGroupId()).thenReturn(GROUP_ID);
        when(mockEventProtocolsParams.getArtifactId()).thenReturn(ARTIFACT_ID);
        when(mockEventProtocolsParams.getTopic()).thenReturn("");

        assertThatThrownBy(() -> encoder.add(mockEventProtocolsParams))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid Topic");
    }

    @Test
    void testAdd_InvalidParams_EventClass() {
        var mockEventProtocolsParams = mock(EventProtocolParams.class);
        when(mockEventProtocolsParams.getGroupId()).thenReturn(GROUP_ID);
        when(mockEventProtocolsParams.getArtifactId()).thenReturn(ARTIFACT_ID);
        when(mockEventProtocolsParams.getTopic()).thenReturn(TOPIC);
        when(mockEventProtocolsParams.getEventClass()).thenReturn(null);

        assertThatThrownBy(() -> encoder.add(mockEventProtocolsParams))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid Event Class");
    }

    @Test
    void testDecode_Exceptions() {
        var mockDecoder = mock(EventProtocolDecoder.class);
        when(mockDecoder.isCodingSupported(GROUP_ID, ARTIFACT_ID, TOPIC))
            .thenReturn(false);
        when(mockDecoder.decode(GROUP_ID, ARTIFACT_ID, TOPIC, null))
            .thenCallRealMethod();
        when(mockDecoder.codersKey(GROUP_ID, ARTIFACT_ID, TOPIC))
            .thenCallRealMethod();

        assertThatThrownBy(() -> mockDecoder.decode(GROUP_ID, ARTIFACT_ID, TOPIC, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported: groupId:artifactId:topic for encoding");
    }

    @Test
    void testDecode_ExceptionCantDecode() {
        var mockDecoder = mock(EventProtocolDecoder.class);

        HashMap<String, ProtocolCoderToolset> coders = new HashMap<>();
        var params = mock(EventProtocolParams.class);
        coders.put("groupId:artifactId:topic", new GsonProtocolCoderToolset(params, "controllerId"));
        ReflectionTestUtils.setField(mockDecoder, "coders", coders);

        when(mockDecoder.isCodingSupported(GROUP_ID, ARTIFACT_ID, TOPIC))
            .thenReturn(true);
        when(mockDecoder.decode(GROUP_ID, ARTIFACT_ID, TOPIC, null))
            .thenCallRealMethod();
        when(mockDecoder.codersKey(GROUP_ID, ARTIFACT_ID, TOPIC))
            .thenCallRealMethod();

        assertThatThrownBy(() -> mockDecoder.decode(GROUP_ID, ARTIFACT_ID, TOPIC, null))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("Cannot decode with gson");
    }

    @Test
    void testDecode_Decode() {
        var mockDecoder = mock(EventProtocolDecoder.class);
        var myKey = "groupId:artifactId:topic";
        var json = "{\"json\":\"true\"}";

        HashMap<String, ProtocolCoderToolset> coders = new HashMap<>();
        var mockToolset = mock(ProtocolCoderToolset.class);
        when(mockToolset.decode(json))
            .thenReturn(new Object()) // success case
            .thenReturn(null); // failure case

        coders.put(myKey, mockToolset);
        ReflectionTestUtils.setField(mockDecoder, "coders", coders);

        when(mockDecoder.isCodingSupported(GROUP_ID, ARTIFACT_ID, TOPIC)).thenReturn(true);
        when(mockDecoder.codersKey(GROUP_ID, ARTIFACT_ID, TOPIC)).thenReturn(myKey);
        when(mockDecoder.decode(GROUP_ID, ARTIFACT_ID, TOPIC, json))
            .thenCallRealMethod();

        assertNotNull(mockDecoder.decode(GROUP_ID, ARTIFACT_ID, TOPIC, json));

        assertThatThrownBy(() -> mockDecoder.decode(GROUP_ID, ARTIFACT_ID, TOPIC, json))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("Cannot decode with gson");
    }

    @Test
    void testEncode_WithGroupArtifactTopicAndEvent() {
        var mockEncoder = mock(EventProtocolEncoder.class);
        var event = new Object();
        when(mockEncoder.isCodingSupported(GROUP_ID, ARTIFACT_ID, TOPIC))
            .thenReturn(false);
        when(mockEncoder.encode(GROUP_ID, ARTIFACT_ID, TOPIC, event))
            .thenCallRealMethod();
        when(mockEncoder.codersKey(GROUP_ID, ARTIFACT_ID, TOPIC))
            .thenCallRealMethod();

        assertThatThrownBy(() -> mockEncoder.encode(GROUP_ID, ARTIFACT_ID, TOPIC, event))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported: groupId:artifactId:topic");

        // test with event null
        when(mockEncoder.isCodingSupported(GROUP_ID, ARTIFACT_ID, TOPIC))
            .thenReturn(true);
        when(mockEncoder.codersKey(GROUP_ID, ARTIFACT_ID, TOPIC))
            .thenCallRealMethod();
        when(mockEncoder.encode(GROUP_ID, ARTIFACT_ID, TOPIC, null))
            .thenCallRealMethod();

        assertThatThrownBy(() -> mockEncoder.encode(GROUP_ID, ARTIFACT_ID, TOPIC, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Event cannot be null or empty");
    }

    @Test
    void testEncode_WithGroupArtifactTopicAndEvent_ReturnJson() {
        var mockEncoder = mock(EventProtocolEncoder.class);
        var event = new Object();
        var myKey = "group:artifact:topic";

        // test with event null
        when(mockEncoder.isCodingSupported(GROUP_ID, ARTIFACT_ID, TOPIC)).thenReturn(true);
        when(mockEncoder.codersKey(GROUP_ID, ARTIFACT_ID, TOPIC)).thenReturn(myKey);
        when(mockEncoder.encode(GROUP_ID, ARTIFACT_ID, TOPIC, event)).thenCallRealMethod();
        when(mockEncoder.encodeInternal(myKey, event)).thenCallRealMethod();

        var mockToolset = mock(ProtocolCoderToolset.class);
        when(mockToolset.encode(event)).thenReturn("{\"json\":\"true\"}");

        HashMap<String, ProtocolCoderToolset> coders = new HashMap<>();
        coders.put(myKey, mockToolset);
        ReflectionTestUtils.setField(mockEncoder, "coders", coders);

        var result = mockEncoder.encode(GROUP_ID, ARTIFACT_ID, TOPIC, event);
        assertNotNull(result);
        assertEquals("{\"json\":\"true\"}", result);
    }

    @Test
    void testEncode_WithTopicAndEvent() {
        var event = new Object();

        assertThatThrownBy(() -> encoder.encode(null, event))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid Topic");

        assertThatThrownBy(() -> encoder.encode("", event))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid Topic");

        assertThatThrownBy(() -> encoder.encode(TOPIC, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Event cannot be null or empty");

        assertThatThrownBy(() -> encoder.encode(TOPIC, event))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no reverse coder has been found");
    }

    @Test
    void testEncode_WithTopicEncodedClassAndDroolsController() {
        var encodedClass = new Object();
        var droolsController = new NullDroolsController();

        assertThatThrownBy(() -> encoder.encode(null, encodedClass, droolsController))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid Topic");

        assertThatThrownBy(() -> encoder.encode("", encodedClass, droolsController))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid Topic");

        assertThatThrownBy(() -> encoder.encode(TOPIC, null, droolsController))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid encoded class");

        assertThatThrownBy(() -> encoder.encode(TOPIC, encodedClass, droolsController))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("Cannot encode with gson");
    }

    @Test
    void testEncode_WithTopicEncodedClassAndDroolsController_ReturnNullJson() {
        var encodedClass = new Object();
        var droolsController = new NullDroolsController();
        var mockCoderTools = mock(ProtocolCoderToolset.class);
        when(mockCoderTools.encode(encodedClass)).thenReturn(null);

        var mockEncoder = mock(EventProtocolEncoder.class);
        when(mockEncoder.codersKey(anyString(), anyString(), anyString())).thenReturn("myKey");
        when(mockEncoder.encodeInternal("myKey", encodedClass)).thenCallRealMethod();
        when(mockEncoder.encode(TOPIC, encodedClass, droolsController)).thenCallRealMethod();

        HashMap<String, ProtocolCoderToolset> coders = new HashMap<>();
        coders.put("myKey", mockCoderTools);
        ReflectionTestUtils.setField(mockEncoder, "coders", coders);

        assertThatThrownBy(() -> mockEncoder.encode(TOPIC, encodedClass, droolsController))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("Cannot encode with gson");
    }

    @Test
    void droolsCreators() {
        var encodedClass = new Object();
        assertTrue(encoder.droolsCreators(TOPIC, encodedClass).isEmpty());
    }

    @Test
    void droolsCreators_ContainsReverseKey() {
        var encodedClass = new Object();
        var mockDecoder = mock(EventProtocolDecoder.class);
        when(mockDecoder.droolsCreators(TOPIC, encodedClass)).thenCallRealMethod();
        when(mockDecoder.reverseCodersKey(TOPIC, encodedClass.getClass().getName()))
            .thenReturn("topic:java.lang.Object");

        HashMap<String, List<ProtocolCoderToolset>> reverseCoders = new HashMap<>();
        reverseCoders.put("topic:java.lang.Object", List.of());
        ReflectionTestUtils.setField(mockDecoder, "reverseCoders", reverseCoders);

        assertThatThrownBy(() -> mockDecoder.droolsCreators(TOPIC, encodedClass))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No Encoders toolsets available for topic:java.lang.Object");
    }

    @Test
    void droolsCreators_ContainsReverseKey_ReturnControllers() {
        var encodedClass = new Object();
        var mockDecoder = mock(EventProtocolDecoder.class);
        when(mockDecoder.droolsCreators(TOPIC, encodedClass)).thenCallRealMethod();
        when(mockDecoder.reverseCodersKey(TOPIC, encodedClass.getClass().getName()))
            .thenReturn("group:artifact:topic");

        var toolset = mock(ProtocolCoderToolset.class);
        when(toolset.getGroupId()).thenReturn(GROUP_ID);
        when(toolset.getArtifactId()).thenReturn(ARTIFACT_ID);

        var mockCoders = mock(EventProtocolCoder.CoderFilters.class);
        when(mockCoders.getFactClass()).thenReturn("java.lang.Object");
        when(mockCoders.getModelClassLoaderHash()).thenReturn(1);
        when(toolset.getCoders()).thenReturn(List.of(mockCoders));

        HashMap<String, List<ProtocolCoderToolset>> reverseCoders = new HashMap<>();
        var toolsetList = new ArrayList<ProtocolCoderToolset>();
        toolsetList.add(toolset);
        reverseCoders.put("group:artifact:topic", toolsetList);
        ReflectionTestUtils.setField(mockDecoder, "reverseCoders", reverseCoders);

        var mockDroolsController = mock(DroolsController.class);
        when(mockDroolsController.ownsCoder(encodedClass.getClass(), 1)).thenReturn(true);

        var mockFactory = mock(DroolsControllerFactory.class);
        when(mockFactory.get(GROUP_ID, ARTIFACT_ID, "")).thenReturn(mockDroolsController);

        try (MockedStatic<DroolsControllerConstants> factory = Mockito.mockStatic(DroolsControllerConstants.class)) {
            factory.when(DroolsControllerConstants::getFactory).thenReturn(mockFactory);
            assertEquals(mockFactory, DroolsControllerConstants.getFactory());
            assertFalse(mockDecoder.droolsCreators(TOPIC, encodedClass).isEmpty());
        }
    }

    @Test
    void testGetFilters_WithGroupArtifactAndTopic() {
        assertThatThrownBy(() -> encoder.getFilters(GROUP_ID, ARTIFACT_ID, TOPIC))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported: groupId:artifactId:topic");

        var mockEncoder = mock(EventProtocolEncoder.class);
        when(mockEncoder.isCodingSupported(GROUP_ID, ARTIFACT_ID, TOPIC)).thenReturn(true);
        when((mockEncoder.codersKey(GROUP_ID, ARTIFACT_ID, TOPIC))).thenCallRealMethod();
        when(mockEncoder.getFilters(GROUP_ID, ARTIFACT_ID, TOPIC)).thenCallRealMethod();

        HashMap<String, ProtocolCoderToolset> coders = new HashMap<>();
        var mockCoderTools = mock(ProtocolCoderToolset.class);
        when(mockCoderTools.getCoders()).thenReturn(List.of());
        coders.put(VALID_KEY, mockCoderTools);
        ReflectionTestUtils.setField(mockEncoder, "coders", coders);

        assertTrue(mockEncoder.getFilters(GROUP_ID, ARTIFACT_ID, TOPIC).isEmpty());
    }

    @Test
    void testGetFilters_WithGroupAndArtifact() {
        assertTrue(encoder.getFilters(GROUP_ID, ARTIFACT_ID).isEmpty());

        assertThatThrownBy(() -> encoder.getFilters("", ARTIFACT_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid group");

        assertThatThrownBy(() -> encoder.getFilters(GROUP_ID, ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid artifact");
    }

    @Test
    void testGetFilters_WithGroupArtifactTopicAndClassName() {
        assertThatThrownBy(() -> encoder.getFilters(GROUP_ID, ARTIFACT_ID, TOPIC, "className"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported: groupId:artifactId:topic");
    }

    @Test
    void testGetFilters_WithGroupArtifactTopicAndClassNameNull() {
        var mockEncoder = mock(EventProtocolEncoder.class);
        when(mockEncoder.isCodingSupported(GROUP_ID, ARTIFACT_ID, TOPIC)).thenReturn(true);
        when(mockEncoder.getFilters(GROUP_ID, ARTIFACT_ID, TOPIC, ""))
            .thenCallRealMethod();

        assertThatThrownBy(() -> mockEncoder.getFilters(GROUP_ID, ARTIFACT_ID, TOPIC, ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("classname must be provided");
    }

    @Test
    void testGetFilters_WithGroupArtifactTopicAndClassName_ReturnValue() {
        var mockEncoder = mock(EventProtocolEncoder.class);
        when(mockEncoder.isCodingSupported(GROUP_ID, ARTIFACT_ID, TOPIC)).thenReturn(true);
        when(mockEncoder.getFilters(GROUP_ID, ARTIFACT_ID, TOPIC, "className"))
            .thenCallRealMethod();
        when((mockEncoder.codersKey(GROUP_ID, ARTIFACT_ID, TOPIC))).thenCallRealMethod();

        HashMap<String, ProtocolCoderToolset> coders = new HashMap<>();
        var mockCoderTools = mock(ProtocolCoderToolset.class);
        when(mockCoderTools.getCoder("className")).thenReturn(null);
        coders.put(VALID_KEY, mockCoderTools);
        ReflectionTestUtils.setField(mockEncoder, "coders", coders);

        assertNull(mockEncoder.getFilters(GROUP_ID, ARTIFACT_ID, TOPIC, "className"));
    }

    @Test
    void testGetCoders_ReturnObject() {
        var mockEncoder = mock(EventProtocolEncoder.class);
        when(mockEncoder.isCodingSupported(GROUP_ID, ARTIFACT_ID, TOPIC)).thenReturn(true);
        when(mockEncoder.getCoders(GROUP_ID, ARTIFACT_ID, TOPIC))
            .thenCallRealMethod();
        HashMap<String, ProtocolCoderToolset> coders = new HashMap<>();
        ReflectionTestUtils.setField(mockEncoder, "coders", coders);

        assertNull(mockEncoder.getCoders(GROUP_ID, ARTIFACT_ID, TOPIC));
    }

    @Test
    void testGetCoders_ReturnList() {
        assertThatThrownBy(() -> encoder.getCoders("", ARTIFACT_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid group");

        assertThatThrownBy(() -> encoder.getCoders(GROUP_ID, ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid artifact");

        assertTrue(encoder.getCoders(GROUP_ID, ARTIFACT_ID).isEmpty());

        // mock a successful return
        var mockEncoder = mock(EventProtocolEncoder.class);
        HashMap<String, ProtocolCoderToolset> coders = new HashMap<>();
        var params = mock(EventProtocolParams.class);
        coders.put(VALID_KEY, new GsonProtocolCoderToolset(params, "controllerId"));
        ReflectionTestUtils.setField(mockEncoder, "coders", coders);
        when(mockEncoder.codersKey(GROUP_ID, ARTIFACT_ID, "")).thenCallRealMethod();
        when(mockEncoder.getCoders(GROUP_ID, ARTIFACT_ID)).thenCallRealMethod();

        var resultList = mockEncoder.getCoders(GROUP_ID, ARTIFACT_ID);
        assertFalse(resultList.isEmpty());
        assertEquals(1, resultList.size());

        var emptyResult = mockEncoder.getCoders("group2", "artifact2");
        assertTrue(emptyResult.isEmpty());
    }

    @Test
    void testGetReverseFilters() {
        assertThatThrownBy(() -> decoder.getReverseFilters("", "codedClass"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid Topic");

        assertThatThrownBy(() -> decoder.getReverseFilters(TOPIC, ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("class must be provided");

        assertTrue(decoder.getReverseFilters(TOPIC, "codedClass").isEmpty());
    }

    @Test
    void testGetReverseFilters_ReturnToolset() {
        var mockDecoder = mock(EventProtocolDecoder.class);

        // mock a successful return
        HashMap<String, List<ProtocolCoderToolset>> reverseCoders = new HashMap<>();
        var params = mock(EventProtocolParams.class);
        var toolset = new GsonProtocolCoderToolset(params, "controllerId");
        reverseCoders.put("topic:codedClass", List.of(toolset));
        ReflectionTestUtils.setField(mockDecoder, "reverseCoders", reverseCoders);

        when(mockDecoder.reverseCodersKey(TOPIC, "codedClass")).thenReturn("topic:codedClass");
        when(mockDecoder.getReverseFilters(TOPIC, "codedClass")).thenCallRealMethod();

        var resultList = mockDecoder.getReverseFilters(TOPIC, "codedClass");
        assertFalse(resultList.isEmpty());
        assertEquals(1, resultList.size());
    }

    @Test
    void testGetDroolsController_Exception() {
        var fact = new Object();
        assertThatThrownBy(() -> decoder.getDroolsController("", fact))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid Topic");

        assertThatThrownBy(() -> decoder.getDroolsController(TOPIC, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("class must be provided");

        assertThatThrownBy(() -> decoder.getDroolsController(TOPIC, fact))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported topic topic and fact class java.lang.Object");
    }

    @Test
    void testGetDroolsController_ReturnObject() {
        var mockDecoder = mock(EventProtocolDecoder.class);
        var droolsController = new NullDroolsController();
        var droolsController2 = new NullDroolsController();
        var fact = new Object();

        // mock first call to return only one controller, then second call should return 2 controllers
        when(mockDecoder.getDroolsControllers(TOPIC, fact))
            .thenReturn(List.of(droolsController))
            .thenReturn(List.of(droolsController, droolsController2));
        when(mockDecoder.getDroolsController(TOPIC, fact)).thenCallRealMethod();

        var result = mockDecoder.getDroolsController(TOPIC, fact);
        assertNotNull(result);

        // second call supposed to return 2 controllers internally, but still return 1 item
        var result2 = mockDecoder.getDroolsController(TOPIC, fact);
        assertNotNull(result2);
    }

    @Test
    void testGetDroolsControllers_Exception() {
        var fact = new Object();
        assertThatThrownBy(() -> decoder.getDroolsControllers("", fact))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid Topic");

        assertThatThrownBy(() -> decoder.getDroolsControllers(TOPIC, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("class must be provided");

        assertTrue(decoder.getDroolsControllers(TOPIC, fact).isEmpty());
    }

    @Test
    void testGetDroolsControllers_ReturnValues() {
        var mockDecoder = mock(EventProtocolDecoder.class);
        var droolsController = new NullDroolsController();
        var droolsController2 = new NullDroolsController();
        var fact = new Object();

        // mock first call to return only one controller, then second call should return 2 controllers
        when(mockDecoder.droolsCreators(TOPIC, fact))
            .thenReturn(List.of(droolsController))
            .thenReturn(List.of(droolsController, droolsController2));
        when(mockDecoder.getDroolsControllers(TOPIC, fact)).thenCallRealMethod();

        var result = mockDecoder.getDroolsControllers(TOPIC, fact);
        assertEquals(1, result.size());

        // second call supposed to return 2 controllers
        var result2 = mockDecoder.getDroolsControllers(TOPIC, fact);
        assertEquals(2, result2.size());
    }

    @Test
    void testRemove() {
        var mockDecoder = mock(EventProtocolDecoder.class);
        doCallRealMethod().when(mockDecoder).remove(GROUP_ID, ARTIFACT_ID, TOPIC);
        when(mockDecoder.codersKey(GROUP_ID, ARTIFACT_ID, TOPIC)).thenCallRealMethod();
        when(mockDecoder.reverseCodersKey(TOPIC, "className")).thenReturn(INVALID_KEY);

        var toolset = mock(ProtocolCoderToolset.class);
        var mockCoders = new EventProtocolCoder.CoderFilters("className", mock(JsonProtocolFilter.class), 1);
        when(toolset.getCoders()).thenReturn(List.of(mockCoders));

        HashMap<String, ProtocolCoderToolset> coders = new HashMap<>();
        coders.put(VALID_KEY, toolset);
        ReflectionTestUtils.setField(mockDecoder, "coders", coders);
        HashMap<String, List<ProtocolCoderToolset>> reverseCoders = new HashMap<>();
        var toolsetList = new ArrayList<ProtocolCoderToolset>();
        toolsetList.add(toolset);
        reverseCoders.put(VALID_KEY, toolsetList);
        ReflectionTestUtils.setField(mockDecoder, "reverseCoders", reverseCoders);

        assertDoesNotThrow(() -> mockDecoder.remove(GROUP_ID, ARTIFACT_ID, TOPIC));
        assertEquals(1, mockDecoder.reverseCoders.size());
        assertTrue(mockDecoder.coders.isEmpty());
    }

    @Test
    void testRemove2() {
        var myKey = "group:artifact:topic";
        var mockDecoder = mock(EventProtocolDecoder.class);
        doCallRealMethod().when(mockDecoder).remove(GROUP_ID, ARTIFACT_ID, TOPIC);
        when(mockDecoder.codersKey(GROUP_ID, ARTIFACT_ID, TOPIC)).thenReturn(myKey);
        when(mockDecoder.reverseCodersKey(TOPIC, "className")).thenReturn(myKey);

        var toolset = mock(ProtocolCoderToolset.class);
        var mockCoders = new EventProtocolCoder.CoderFilters("className", mock(JsonProtocolFilter.class), 1);
        when(toolset.getCoders()).thenReturn(List.of(mockCoders));

        HashMap<String, ProtocolCoderToolset> coders = new HashMap<>();
        coders.put(myKey, toolset);
        ReflectionTestUtils.setField(mockDecoder, "coders", coders);
        HashMap<String, List<ProtocolCoderToolset>> reverseCoders = new HashMap<>();
        reverseCoders.put(myKey, new ArrayList<>());
        ReflectionTestUtils.setField(mockDecoder, "reverseCoders", reverseCoders);

        assertDoesNotThrow(() -> mockDecoder.remove(GROUP_ID, ARTIFACT_ID, TOPIC));
        assertTrue(mockDecoder.reverseCoders.isEmpty());
        assertTrue(mockDecoder.coders.isEmpty());
    }

}