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
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSyntaxException;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.onap.policy.drools.controller.DroolsControllerConstants;
import org.onap.policy.drools.controller.DroolsControllerFactory;
import org.onap.policy.drools.controller.internal.NullDroolsController;
import org.springframework.test.util.ReflectionTestUtils;

class GsonProtocolCoderToolsetTest {

    @Test
    void decode() {
        var mockGsonToolset = mock(GsonProtocolCoderToolset.class);
        ReflectionTestUtils.setField(mockGsonToolset, "groupId", "group");
        ReflectionTestUtils.setField(mockGsonToolset, "artifactId", "artifact");
        doCallRealMethod().when(mockGsonToolset).decode("json");

        var decoderClass = ProtocolCoderToolsetTest.ThreeStrings.class;
        var mockGson = mock(Gson.class);
        when(mockGson.fromJson("json", decoderClass)).thenThrow(new JsonSyntaxException("error"));
        when(mockGsonToolset.getDecoder()).thenReturn(mockGson);

        var mockCustomCoder = mock(TopicCoderFilterConfiguration.CustomCoder.class);
        when(mockCustomCoder.getClassContainer()).thenReturn("classContainer");
        ReflectionTestUtils.setField(mockGsonToolset, "customCoder", mockCustomCoder);

        var mockFilter = mock(EventProtocolCoder.CoderFilters.class);
        when(mockFilter.getFactClass()).thenReturn("someClassName");
        when(mockGsonToolset.filter("json")).thenReturn(mockFilter);

        var droolsController = mock(NullDroolsController.class);
        when(droolsController.fetchModelClass("someClassName"))
            .thenReturn(null)
            .thenAnswer((Answer<Class<?>>) invocation -> decoderClass)
            .thenAnswer((Answer<Class<?>>) invocation -> decoderClass);
        when(droolsController.fetchModelClass("classContainer")).thenReturn(null);

        var mockFactory = mock(DroolsControllerFactory.class);
        when(mockFactory.get("group", "artifact", ""))
            .thenReturn(null)
            .thenReturn(droolsController);

        try (MockedStatic<DroolsControllerConstants> factory = Mockito.mockStatic(DroolsControllerConstants.class)) {
            factory.when(DroolsControllerConstants::getFactory).thenReturn(mockFactory);
            assertEquals(mockFactory, DroolsControllerConstants.getFactory());

            // first call to fail when droolsController returns null
            assertThatThrownBy(() -> mockGsonToolset.decode("json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no drools-controller to process event");

            // second call to fail when droolsController.fetchModelClass returns null
            assertThatThrownBy(() -> mockGsonToolset.decode("json"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("cannot fetch application class someClassName");

            // third call to fail when droolsController.fetchModelClass returns null when using customCoder
            assertThatThrownBy(() -> mockGsonToolset.decode("json"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("cannot decode with customCoder: classContainer")
                .hasMessageContaining("using application class someClassName");

            // set customCoder to null to test default decoder
            ReflectionTestUtils.setField(mockGsonToolset, "customCoder", null);

            // fourth call to fail when decoder can't parse json
            assertThatThrownBy(() -> mockGsonToolset.decode("json"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("cannot decode into");
        }
    }

    @Test
    void encode() {
        var event = new Object();

        var mockGsonToolset = mock(GsonProtocolCoderToolset.class);
        ReflectionTestUtils.setField(mockGsonToolset, "groupId", "group");
        ReflectionTestUtils.setField(mockGsonToolset, "artifactId", "artifact");
        doCallRealMethod().when(mockGsonToolset).encode(event);

        var mockGson = mock(Gson.class);
        when(mockGson.toJson(event)).thenThrow(new JsonIOException("error"));
        when(mockGsonToolset.getEncoder()).thenReturn(mockGson);

        var mockCustomCoder = mock(TopicCoderFilterConfiguration.CustomCoder.class);
        ReflectionTestUtils.setField(mockGsonToolset, "customCoder", mockCustomCoder);

        var mockFactory = mock(DroolsControllerFactory.class);
        when(mockFactory.get("group", "artifact", "")).thenReturn(null);

        try (MockedStatic<DroolsControllerConstants> factory = Mockito.mockStatic(DroolsControllerConstants.class)) {
            factory.when(DroolsControllerConstants::getFactory).thenReturn(mockFactory);
            assertEquals(mockFactory, DroolsControllerConstants.getFactory());

            // first call to encode fails with droolsController returning null, therefore, can't process event
            assertThatThrownBy(() -> mockGsonToolset.encode(event))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("event cannot be custom encoded");

            // set customCoder to null to test default encoder
            ReflectionTestUtils.setField(mockGsonToolset, "customCoder", null);

            // second call to encode fails when gson.toJson raises an exception
            assertThatThrownBy(() -> mockGsonToolset.encode(event))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("event cannot be encoded");
        }
    }

    @Test
    void test_GsonInstantAdapter() {
        var instantAdapter = new GsonProtocolCoderToolset.GsonInstantAdapter();

        var currentTime = System.currentTimeMillis();
        var json = mock(JsonElement.class);
        when(json.getAsLong()).thenReturn(currentTime);

        var result = instantAdapter.deserialize(json, mock(Type.class), mock(JsonDeserializationContext.class));
        assertInstanceOf(Instant.class, result);
        assertEquals(currentTime, result.toEpochMilli());

        var jsonResult = instantAdapter.serialize(result, mock(Type.class), mock(JsonSerializationContext.class));
        assertInstanceOf(JsonElement.class, jsonResult);
        assertEquals(currentTime, jsonResult.getAsLong());
    }

    @Test
    void test_GsonUtcAdapter() {
        var utcAdapter = new GsonProtocolCoderToolset.GsonUtcAdapter();

        var currentZone = ZonedDateTime.now();
        var formattedCurrentZone = ZonedDateTime.now().format(GsonProtocolCoderToolset.format);
        var json = mock(JsonElement.class);
        when(json.getAsString()).thenReturn(formattedCurrentZone).thenReturn("invalid json");

        var result = utcAdapter.deserialize(json, mock(Type.class), mock(JsonDeserializationContext.class));
        assertNotNull(result);
        assertAll(() -> {
            assertEquals(currentZone.getYear(), result.getYear());
            assertEquals(currentZone.getMonth(), result.getMonth());
            assertEquals(currentZone.getDayOfMonth(), result.getDayOfMonth());
            assertEquals(currentZone.getHour(), result.getHour());
            assertEquals(currentZone.getMinute(), result.getMinute());
            assertEquals(currentZone.getSecond(), result.getSecond());
        });

        // when json.getAsString returns invalid json, should fail and return null
        assertNull(utcAdapter.deserialize(json, mock(Type.class), mock(JsonDeserializationContext.class)));

        var result2 = utcAdapter.serialize(result, mock(Type.class), mock(JsonSerializationContext.class));
        assertNotNull(result2);
        assertEquals(formattedCurrentZone, result2.getAsString());
    }
}