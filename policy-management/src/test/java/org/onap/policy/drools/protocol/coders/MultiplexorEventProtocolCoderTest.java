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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.onap.policy.drools.controller.DroolsController;
import org.springframework.test.util.ReflectionTestUtils;

class MultiplexorEventProtocolCoderTest {

    @Mock
    EventProtocolEncoder encoder;

    @Mock
    EventProtocolDecoder decoder;

    MultiplexorEventProtocolCoder coder = new MultiplexorEventProtocolCoder();

    AutoCloseable closeable;

    private static final String GROUP_ID = "group";
    private static final String ARTIFACT_ID = "artifact";
    private static final String TOPIC = "topic";

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(coder, "encoders", encoder);
        ReflectionTestUtils.setField(coder, "decoders", decoder);
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    void addDecoder() {
        var mockParams = mock(EventProtocolParams.class);
        doNothing().when(decoder).add(mockParams);

        assertDoesNotThrow(() -> coder.addDecoder(mockParams));
    }

    @Test
    void addEncoder() {
        var mockParams = mock(EventProtocolParams.class);
        doNothing().when(encoder).add(mockParams);

        assertDoesNotThrow(() -> coder.addEncoder(mockParams));
    }

    @Test
    void removeDecoders() {
        doNothing().when(decoder).remove(GROUP_ID, ARTIFACT_ID, TOPIC);
        assertDoesNotThrow(() -> coder.removeDecoders(GROUP_ID, ARTIFACT_ID, TOPIC));
    }

    @Test
    void removeEncoders() {
        doNothing().when(encoder).remove(GROUP_ID, ARTIFACT_ID, TOPIC);
        assertDoesNotThrow(() -> coder.removeEncoders(GROUP_ID, ARTIFACT_ID, TOPIC));
    }

    @Test
    void isDecodingSupported() {
        HashMap<String, ProtocolCoderToolset> coders = new HashMap<>();
        coders.put("otherGroup:artifact:topic", mock(ProtocolCoderToolset.class));
        ReflectionTestUtils.setField(decoder, "coders", coders);
        when(decoder.codersKey(GROUP_ID, ARTIFACT_ID, TOPIC)).thenCallRealMethod();
        when(decoder.isCodingSupported(GROUP_ID, ARTIFACT_ID, TOPIC))
            .thenReturn(true)
            .thenCallRealMethod();
        assertTrue(coder.isDecodingSupported(GROUP_ID, ARTIFACT_ID, TOPIC));
        assertFalse(coder.isDecodingSupported(GROUP_ID, ARTIFACT_ID, TOPIC));
    }

    @Test
    void isEncodingSupported() {
        HashMap<String, ProtocolCoderToolset> coders = new HashMap<>();
        coders.put("otherGroup:artifact:topic", mock(ProtocolCoderToolset.class));
        ReflectionTestUtils.setField(encoder, "coders", coders);
        when(encoder.codersKey(GROUP_ID, ARTIFACT_ID, TOPIC)).thenCallRealMethod();
        when(encoder.isCodingSupported(GROUP_ID, ARTIFACT_ID, TOPIC))
            .thenReturn(true)
            .thenCallRealMethod();
        assertTrue(coder.isEncodingSupported(GROUP_ID, ARTIFACT_ID, TOPIC));
        assertFalse(coder.isEncodingSupported(GROUP_ID, ARTIFACT_ID, TOPIC));
    }

    @Test
    void decode() {
        when(decoder.decode(GROUP_ID, ARTIFACT_ID, TOPIC, "{}"))
            .thenReturn(new Object())
            .thenCallRealMethod();
        when(decoder.isCodingSupported(GROUP_ID, ARTIFACT_ID, TOPIC)).thenReturn(false);

        // first mock call to return directly new Object()
        assertNotNull(coder.decode(GROUP_ID, ARTIFACT_ID, TOPIC, "{}"));

        // second mock call to check isCodingSupport return false, then throws exception
        assertThrows(IllegalArgumentException.class,
            () -> coder.decode(GROUP_ID, ARTIFACT_ID, TOPIC, "{}"));
    }

    @Test
    void encode() {
        var event = new Object();
        when(encoder.encode(GROUP_ID, ARTIFACT_ID, TOPIC, event))
            .thenReturn("{}")
            .thenCallRealMethod();
        when(encoder.isCodingSupported(GROUP_ID, ARTIFACT_ID, TOPIC)).thenReturn(false);

        // first mock call to return directly new Object()
        assertNotNull(coder.encode(GROUP_ID, ARTIFACT_ID, TOPIC, event));

        // second mock call to check isCodingSupport return false, then throws exception
        assertThrows(IllegalArgumentException.class,
            () -> coder.encode(GROUP_ID, ARTIFACT_ID, TOPIC, event));
    }

    @Test
    void encode_WithTopicAndEvent() {
        var event = new Object();
        when(encoder.encode(TOPIC, event)).thenReturn("{}");

        var result = coder.encode(TOPIC, event);
        assertNotNull(result);
        assertInstanceOf(String.class, result);
    }

    @Test
    void encode_WithTopicEncodedClassAndController() {
        var event = new Object();
        var controller = mock(DroolsController.class);
        when(encoder.encode(TOPIC, event, controller)).thenReturn("{}");

        var result = coder.encode(TOPIC, event, controller);
        assertNotNull(result);
        assertInstanceOf(String.class, result);
    }

    @Test
    void getDecoderFilters() {
        when(decoder.getFilters(GROUP_ID, ARTIFACT_ID, TOPIC))
            .thenReturn(new ArrayList<>())
            .thenCallRealMethod();
        when(decoder.isCodingSupported(GROUP_ID, ARTIFACT_ID, TOPIC)).thenReturn(false);

        // first mock call return a list
        var result = coder.getDecoderFilters(GROUP_ID, ARTIFACT_ID, TOPIC);
        assertTrue(result.isEmpty());

        // second call goes for real method, with isCodingSupported mocked to false, exception is thrown
        assertThrows(IllegalArgumentException.class,
            () -> coder.getDecoderFilters(GROUP_ID, ARTIFACT_ID, TOPIC));
    }

    @Test
    void testGetDecoderFilters_WithGroupArtifactTopicAndClassName() {
        when(decoder.getFilters(GROUP_ID, ARTIFACT_ID, TOPIC, "className"))
            .thenReturn(mock(EventProtocolCoder.CoderFilters.class))
            .thenCallRealMethod();
        when(decoder.isCodingSupported(GROUP_ID, ARTIFACT_ID, TOPIC)).thenReturn(false);

        // first mock call return a mock object
        assertNotNull(coder.getDecoderFilters(GROUP_ID, ARTIFACT_ID, TOPIC, "className"));

        // second call goes for real method, with isCodingSupported mocked to false, exception is thrown
        assertThrows(IllegalArgumentException.class,
            () -> coder.getDecoderFilters(GROUP_ID, ARTIFACT_ID, TOPIC, "className"));
    }

    @Test
    void testGetDecoderFilters_WithGroupAndArtifact() {
        when(decoder.getFilters(GROUP_ID, ARTIFACT_ID)).thenReturn(new ArrayList<>());

        assertThat(coder.getDecoderFilters(GROUP_ID, ARTIFACT_ID, TOPIC)).isEmpty();
    }

    @Test
    void getDecoders() {
        when(decoder.getCoders(GROUP_ID, ARTIFACT_ID, TOPIC))
            .thenReturn(mock(ProtocolCoderToolset.class))
            .thenCallRealMethod();
        when(decoder.isCodingSupported(GROUP_ID, ARTIFACT_ID, TOPIC)).thenReturn(false);

        assertNotNull(coder.getDecoders(GROUP_ID, ARTIFACT_ID, TOPIC));
        assertThrows(IllegalArgumentException.class,
            () -> coder.getDecoders(GROUP_ID, ARTIFACT_ID, TOPIC));
    }

    @Test
    void testGetDecoders_WithGroupAndArtifact() {
        when(decoder.getCoders(GROUP_ID, ARTIFACT_ID)).thenReturn(new ArrayList<>());

        assertThat(coder.getDecoders(GROUP_ID, ARTIFACT_ID)).isEmpty();
    }

    @Test
    void getEncoderFilters() {
        when(encoder.getFilters(GROUP_ID, ARTIFACT_ID, TOPIC))
            .thenReturn(new ArrayList<>())
            .thenCallRealMethod();
        when(encoder.isCodingSupported(GROUP_ID, ARTIFACT_ID, TOPIC)).thenReturn(false);

        // first mock call return a list
        var result = coder.getEncoderFilters(GROUP_ID, ARTIFACT_ID, TOPIC);
        assertTrue(result.isEmpty());

        // second call goes for real method, with isCodingSupported mocked to false, exception is thrown
        assertThrows(IllegalArgumentException.class,
            () -> coder.getEncoderFilters(GROUP_ID, ARTIFACT_ID, TOPIC));
    }

    @Test
    void testGetEncoderFilters_WithGroupArtifactTopicAndClassName() {
        when(encoder.getFilters(GROUP_ID, ARTIFACT_ID, TOPIC, "className"))
            .thenReturn(mock(EventProtocolCoder.CoderFilters.class))
            .thenCallRealMethod();
        when(encoder.isCodingSupported(GROUP_ID, ARTIFACT_ID, TOPIC)).thenReturn(false);

        // first mock call return a mock object
        assertNotNull(coder.getEncoderFilters(GROUP_ID, ARTIFACT_ID, TOPIC, "className"));

        // second call goes for real method, with isCodingSupported mocked to false, exception is thrown
        assertThrows(IllegalArgumentException.class,
            () -> coder.getEncoderFilters(GROUP_ID, ARTIFACT_ID, TOPIC, "className"));
    }

    @Test
    void testGetEncoderFilters_WithGroupAndArtifact() {
        when(encoder.getFilters(GROUP_ID, ARTIFACT_ID)).thenReturn(new ArrayList<>());

        assertThat(coder.getEncoderFilters(GROUP_ID, ARTIFACT_ID, TOPIC)).isEmpty();
    }

    @Test
    void getReverseEncoderFilters() {
        when(encoder.getReverseFilters(TOPIC, "codedClass")).thenReturn(new ArrayList<>());
        assertThat(coder.getReverseEncoderFilters(TOPIC, "codedClass")).isEmpty();
    }

    @Test
    void getDroolsController() {
        var fact = new Object();
        // mock success
        when(encoder.getDroolsController(TOPIC, fact))
            .thenReturn(mock(DroolsController.class))
            .thenCallRealMethod();
        // mock failure
        when(encoder.getDroolsControllers(TOPIC, fact)).thenReturn(new ArrayList<>());

        // first call gets mock DroolsController - pass
        assertNotNull(coder.getDroolsController(TOPIC, fact));
        // second call goes through the method, then call inside method that returns empty list, throws an exception
        assertThrows(IllegalArgumentException.class, () -> coder.getDroolsController(TOPIC, fact));
    }

    @Test
    void getDroolsControllers() {
        var fact = new Object();
        when(encoder.getDroolsControllers(TOPIC, fact)).thenReturn(new ArrayList<>());

        assertThat(coder.getDroolsControllers(TOPIC, fact)).isEmpty();
    }

    @Test
    void testToString() {
        assertThat(coder.toString())
            .contains("MultiplexorEventProtocolCoder");
    }
}