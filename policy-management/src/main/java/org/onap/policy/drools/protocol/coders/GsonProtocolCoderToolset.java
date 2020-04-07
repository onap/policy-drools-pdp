/*
 * ============LICENSE_START=======================================================
 * ONAP
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

package org.onap.policy.drools.protocol.coders;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.onap.policy.common.gson.annotation.GsonJsonIgnore;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.controller.DroolsControllerConstants;
import org.onap.policy.drools.protocol.coders.EventProtocolCoder.CoderFilters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tools used for encoding/decoding using GSON.
 */
class GsonProtocolCoderToolset extends ProtocolCoderToolset {
    private static final String CANNOT_FETCH_CLASS = "{}: cannot fetch application class {}";
    private static final String FETCH_CLASS_EX_MSG = "cannot fetch application class ";

    /**
     * Logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(GsonProtocolCoderToolset.class);

    /**
     * Formatter for JSON encoding/decoding.
     */
    @JsonIgnore
    @GsonJsonIgnore
    public static final DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSxxx");

    @JsonIgnore
    @GsonJsonIgnore
    public static final DateTimeFormatter zuluFormat = DateTimeFormatter.ISO_INSTANT;

    /**
     * Adapter for ZonedDateTime.
     */
    public static class GsonUtcAdapter implements JsonSerializer<ZonedDateTime>, JsonDeserializer<ZonedDateTime> {
        @Override
        public ZonedDateTime deserialize(JsonElement element, Type type,
                JsonDeserializationContext context) {
            try {
                return ZonedDateTime.parse(element.getAsString(), format);
            } catch (final Exception e) {
                logger.info("GsonUTCAdapter: cannot parse {} because of {}", element, e.getMessage(), e);
            }
            return null;
        }

        @Override
        public JsonElement serialize(ZonedDateTime datetime, Type type,
                JsonSerializationContext context) {
            return new JsonPrimitive(datetime.format(format));
        }
    }

    public static class GsonInstantAdapter implements JsonSerializer<Instant>, JsonDeserializer<Instant> {

        @Override
        public Instant deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
            return Instant.ofEpochMilli(json.getAsLong());
        }

        @Override
        public JsonElement serialize(Instant src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.toEpochMilli());
        }

    }


    /**
     * decoder.
     */
    @JsonIgnore
    @GsonJsonIgnore
    protected final Gson decoder = new GsonBuilder().disableHtmlEscaping()
        .registerTypeAdapter(ZonedDateTime.class, new GsonUtcAdapter())
        .registerTypeAdapter(Instant.class, new GsonInstantAdapter()).create();

    /**
     * encoder.
     */
    @JsonIgnore
    @GsonJsonIgnore
    protected final Gson encoder = new GsonBuilder().disableHtmlEscaping()
        .registerTypeAdapter(ZonedDateTime.class, new GsonUtcAdapter())
        .registerTypeAdapter(Instant.class, new GsonInstantAdapter()).create();

    /**
     * Toolset to encode/decode tools associated with a topic.
     *
     * @param eventProtocolParams parameter object for event encoder
     * @param controllerId controller id
     */
    public GsonProtocolCoderToolset(EventProtocolParams eventProtocolParams, String controllerId) {
        super(eventProtocolParams, controllerId);
    }

    /**
     * gets the Gson decoder.
     *
     * @return the Gson decoder
     */
    @JsonIgnore
    @GsonJsonIgnore
    protected Gson getDecoder() {
        return this.decoder;
    }

    /**
     * gets the Gson encoder.
     *
     * @return the Gson encoder
     */
    @JsonIgnore
    @GsonJsonIgnore
    protected Gson getEncoder() {
        return this.encoder;
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public Object decode(String json) {

        final DroolsController droolsController =
                        DroolsControllerConstants.getFactory().get(this.groupId, this.artifactId, "");
        if (droolsController == null) {
            logger.warn("{}: no drools-controller to process {}", this, json);
            throw new IllegalStateException("no drools-controller to process event");
        }

        final CoderFilters decoderFilter = this.filter(json);
        if (decoderFilter == null) {
            logger.debug("{}: no decoder to process {}", this, json);
            throw new UnsupportedOperationException("no decoder to process event");
        }

        Class<?> decoderClass;
        try {
            decoderClass = droolsController.fetchModelClass(decoderFilter.getCodedClass());
            if (decoderClass == null) {
                logger.warn(CANNOT_FETCH_CLASS, this, decoderFilter.getCodedClass());
                throw new IllegalStateException(
                        FETCH_CLASS_EX_MSG + decoderFilter.getCodedClass());
            }
        } catch (final Exception e) {
            logger.warn(CANNOT_FETCH_CLASS, this, decoderFilter.getCodedClass());
            throw new UnsupportedOperationException(
                    FETCH_CLASS_EX_MSG + decoderFilter.getCodedClass(), e);
        }

        if (this.customCoder != null) {
            try {
                final Class<?> gsonClassContainer =
                        droolsController.fetchModelClass(this.customCoder.getClassContainer());
                final Field gsonField = gsonClassContainer.getField(this.customCoder.staticCoderField);
                final Object gsonObject = gsonField.get(null);
                final Method fromJsonMethod = gsonObject.getClass().getDeclaredMethod("fromJson",
                        String.class, Class.class);
                return fromJsonMethod.invoke(gsonObject, json, decoderClass);
            } catch (final Exception e) {
                logger.warn(CANNOT_FETCH_CLASS, this, decoderFilter.getCodedClass());
                throw new UnsupportedOperationException(
                        FETCH_CLASS_EX_MSG + decoderFilter.getCodedClass(), e);
            }
        } else {
            try {
                return this.decoder.fromJson(json, decoderClass);
            } catch (final Exception e) {
                logger.warn("{} cannot decode {} into {}", this, json, decoderClass.getName());
                throw new UnsupportedOperationException(
                        "cannont decode into " + decoderFilter.getCodedClass(), e);
            }
        }
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public String encode(Object event) {

        if (this.customCoder != null) {
            try {
                final DroolsController droolsController =
                                DroolsControllerConstants.getFactory().get(this.groupId, this.artifactId, null);
                final Class<?> gsonClassContainer =
                        droolsController.fetchModelClass(this.customCoder.getClassContainer());
                final Field gsonField = gsonClassContainer.getField(this.customCoder.staticCoderField);
                final Object gsonObject = gsonField.get(null);
                final Method toJsonMethod =
                        gsonObject.getClass().getDeclaredMethod("toJson", Object.class);
                return (String) toJsonMethod.invoke(gsonObject, event);
            } catch (final Exception e) {
                logger.warn("{} cannot custom-encode {}", this, event);
                throw new UnsupportedOperationException("event cannot be encoded", e);
            }
        } else {
            try {
                return this.encoder.toJson(event);
            } catch (final Exception e) {
                logger.warn("{} cannot encode {}", this, event);
                throw new UnsupportedOperationException("event cannot be encoded", e);
            }
        }
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("GsonProtocolCoderToolset [toString()=").append(super.toString()).append("]");
        return builder.toString();
    }
}
