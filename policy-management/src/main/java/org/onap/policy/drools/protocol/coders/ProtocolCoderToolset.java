/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2017-2018 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2018 Samsung Electronics Co., Ltd.
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.protocol.coders.EventProtocolCoder.CoderFilters;
import org.onap.policy.drools.protocol.coders.TopicCoderFilterConfiguration.CustomCoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Protocol Coding/Decoding Toolset.
 */
public abstract class ProtocolCoderToolset {

    /**
     * Logger.
     */
    private static Logger logger = LoggerFactory.getLogger(ProtocolCoderToolset.class);

    /**
     * topic.
     */
    protected final String topic;

    /**
     * controller id.
     */
    protected final String controllerId;

    /**
     * group id.
     */
    protected final String groupId;

    /**
     * artifact id.
     */
    protected final String artifactId;

    /**
     * Protocols and associated Filters.
     */
    protected final List<CoderFilters> coders = new CopyOnWriteArrayList<>();

    /**
     * Tree model (instead of class model) generic parsing to be able to inspect elements.
     */
    protected JsonParser filteringParser = new JsonParser();

    /**
     * custom coder.
     */
    protected CustomCoder customCoder;

    /**
     * Constructor.
     *
     * @param eventProtocolParams parameter object for event encoder
     * @param controllerId the controller id
     * @throws IllegalArgumentException if invalid data has been passed in
     */
    public ProtocolCoderToolset(EventProtocolParams eventProtocolParams, String controllerId) {

        if (eventProtocolParams == null || controllerId == null) {
            throw new IllegalArgumentException("Invalid input");
        }

        this.topic = eventProtocolParams.getTopic();
        this.controllerId = controllerId;
        this.groupId = eventProtocolParams.getGroupId();
        this.artifactId = eventProtocolParams.getArtifactId();
        this.coders.add(new CoderFilters(
                eventProtocolParams.getEventClass(),
                eventProtocolParams.getProtocolFilter(),
                eventProtocolParams.getModelClassLoaderHash()));
        this.customCoder = eventProtocolParams.getCustomCoder();
    }

    /**
     * gets the coder + filters associated with this class name.
     *
     * @param classname class name
     * @return the decoder filters or null if not found
     */
    public CoderFilters getCoder(String classname) {
        if (classname == null || classname.isEmpty()) {
            throw new IllegalArgumentException("no classname provided");
        }

        for (final CoderFilters decoder : this.coders) {
            if (decoder.factClass.equals(classname)) {
                return decoder;
            }
        }
        return null;
    }

    /**
     * get a copy of the coder filters in use.
     *
     * @return coder filters
     */
    public List<CoderFilters> getCoders() {
        return new ArrayList<>(this.coders);
    }

    /**
     * add coder or replace it exists.
     *
     * @param eventClass decoder
     * @param filter filter
     */
    public void addCoder(String eventClass, JsonProtocolFilter filter, int modelClassLoaderHash) {
        if (eventClass == null || eventClass.isEmpty()) {
            throw new IllegalArgumentException("no event class provided");
        }

        for (final CoderFilters coder : this.coders) {
            if (coder.getCodedClass().equals(eventClass)) {
                coder.setFilter(filter);
                coder.setFromClassLoaderHash(modelClassLoaderHash);
                return;
            }
        }
        this.coders.add(new CoderFilters(eventClass, filter, modelClassLoaderHash));
    }

    /**
     * remove coder.
     * 
     * @param eventClass event class
     */
    public void removeCoders(String eventClass) {
        if (eventClass == null || eventClass.isEmpty()) {
            throw new IllegalArgumentException("no event class provided");
        }

        List<CoderFilters> temp = new ArrayList<>();
        for (final CoderFilters coder : this.coders) {
            if (coder.factClass.equals(eventClass)) {
                temp.add(coder);
            }
        }

        this.coders.removeAll(temp);
    }

    /**
     * gets the topic.
     *
     * @return the topic
     */
    public String getTopic() {
        return this.topic;
    }

    /**
     * gets the controller id.
     *
     * @return the controller id
     */
    public String getControllerId() {
        return this.controllerId;
    }

    /**
     * Get group id.
     * 
     * @return the groupId
     */
    public String getGroupId() {
        return this.groupId;
    }

    /**
     * Get artifact id.
     * 
     * @return the artifactId
     */
    public String getArtifactId() {
        return this.artifactId;
    }

    /**
     * Get custom coder.
     * 
     * @return the customCoder
     */
    public CustomCoder getCustomCoder() {
        return this.customCoder;
    }

    /**
     * Set custom coder.
     * 
     * @param customCoder the customCoder to set.
     */
    public void setCustomCoder(CustomCoder customCoder) {
        this.customCoder = customCoder;
    }

    /**
     * performs filtering on a json string.
     *
     * @param json json string
     * @return the decoder that passes the filter, otherwise null
     * @throws UnsupportedOperationException can't filter
     * @throws IllegalArgumentException invalid input
     */
    protected CoderFilters filter(String json) {


        // 1. Get list of decoding classes for this controller Id and topic
        // 2. If there are no classes, return error
        // 3. Otherwise, from the available classes for decoding, pick the first one that
        // passes the filters

        // Don't parse if it is not necessary

        if (this.coders.isEmpty()) {
            throw new IllegalStateException("No coders available");
        }

        if (this.coders.size() == 1) {
            final JsonProtocolFilter filter = this.coders.get(0).getFilter();
            if (!filter.isRules()) {
                return this.coders.get(0);
            }
        }

        JsonElement event;
        try {
            event = this.filteringParser.parse(json);
        } catch (final Exception e) {
            throw new UnsupportedOperationException(e);
        }

        for (final CoderFilters decoder : this.coders) {
            try {
                final boolean accepted = decoder.getFilter().accept(event);
                if (accepted) {
                    return decoder;
                }
            } catch (final Exception e) {
                logger.info("{}: unexpected failure accepting {} because of {}", this, event,
                        e.getMessage(), e);
                // continue
            }
        }

        return null;
    }

    /**
     * Decode json into a POJO object.
     *
     * @param json json string
     *
     * @return a POJO object for the json string
     * @throws IllegalArgumentException if an invalid parameter has been received
     * @throws UnsupportedOperationException if parsing into POJO is not possible
     */
    public abstract Object decode(String json);

    /**
     * Encodes a POJO object into a JSON String.
     *
     * @param event JSON POJO event to be converted to String
     * @return JSON string version of POJO object
     * @throws IllegalArgumentException if an invalid parameter has been received
     * @throws UnsupportedOperationException if parsing into POJO is not possible
     */
    public abstract String encode(Object event);

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("ProtocolCoderToolset [topic=").append(this.topic).append(", controllerId=")
        .append(this.controllerId).append(", groupId=").append(this.groupId).append(", artifactId=")
        .append(this.artifactId).append(", coders=").append(this.coders)
        .append(", filteringParser=").append(this.filteringParser).append(", customCoder=")
        .append(this.customCoder).append("]");
        return builder.toString();
    }
}


/**
 * Tools used for encoding/decoding using Jackson.
 */
class JacksonProtocolCoderToolset extends ProtocolCoderToolset {
    private static final String WARN_FETCH_FAILED = "{}: cannot fetch application class {}";
    private static final String WARN_FETCH_FAILED_BECAUSE = "{}: cannot fetch application class {} because of {}";
    private static final String FETCH_FAILED = "cannot fetch application class ";
    private static final String ENCODE_FAILED = "event cannot be encoded";
    private static Logger logger = LoggerFactory.getLogger(JacksonProtocolCoderToolset.class);
    
    /**
     * decoder.
     */
    @JsonIgnore
    protected final ObjectMapper decoder = new ObjectMapper();

    /**
     * encoder.
     */
    @JsonIgnore
    protected final ObjectMapper encoder = new ObjectMapper();

    /**
     * Toolset to encode/decode tools associated with a topic.
     *
     * @param eventProtocolParams parameter object for event encoder
     * @param controllerId controller id
     */
    public JacksonProtocolCoderToolset(EventProtocolParams eventProtocolParams, String controllerId) {
        super(eventProtocolParams, controllerId);
        this.decoder.registerModule(new JavaTimeModule());
        this.decoder.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * gets the Jackson decoder.
     *
     * @return the Jackson decoder
     */
    @JsonIgnore
    protected ObjectMapper getDecoder() {
        return this.decoder;
    }

    /**
     * gets the Jackson encoder.
     *
     * @return the Jackson encoder
     */
    @JsonIgnore
    protected ObjectMapper getEncoder() {
        return this.encoder;
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public Object decode(String json) {

        // 0. Use custom coder if available

        if (this.customCoder != null) {
            throw new UnsupportedOperationException(
                    "Jackon Custom Decoder is not supported at this time");
        }

        final DroolsController droolsController =
                DroolsController.factory.get(this.groupId, this.artifactId, "");
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
                logger.warn(WARN_FETCH_FAILED, this, decoderFilter.getCodedClass());
                throw new IllegalStateException(
                        FETCH_FAILED + decoderFilter.getCodedClass());
            }
        } catch (final Exception e) {
            logger.warn(WARN_FETCH_FAILED_BECAUSE, this,
                    decoderFilter.getCodedClass(), e.getMessage());
            throw new UnsupportedOperationException(
                    FETCH_FAILED + decoderFilter.getCodedClass(), e);
        }


        try {
            return this.decoder.readValue(json, decoderClass);
        } catch (final Exception e) {
            logger.warn("{} cannot decode {} into {} because of {}", this, json, decoderClass.getName(),
                    e.getMessage(), e);
            throw new UnsupportedOperationException(
                    "cannont decode into " + decoderFilter.getCodedClass(), e);
        }
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public String encode(Object event) {

        // 0. Use custom coder if available

        if (this.customCoder != null) {
            throw new UnsupportedOperationException(
                    "Jackon Custom Encoder is not supported at this time");
        }

        try {
            return this.encoder.writeValueAsString(event);
        } catch (final JsonProcessingException e) {
            logger.error("{} cannot encode {} because of {}", this, event, e.getMessage(), e);
            throw new UnsupportedOperationException(ENCODE_FAILED);
        }
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("JacksonProtocolCoderToolset [toString()=").append(super.toString()).append("]");
        return builder.toString();
    }

}



/**
 * Tools used for encoding/decoding using Jackson.
 */
class GsonProtocolCoderToolset extends ProtocolCoderToolset {
    /**
     * Logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(GsonProtocolCoderToolset.class);

    /**
     * Formatter for JSON encoding/decoding.
     */
    @JsonIgnore
    public static final DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSxxx");

    @JsonIgnore
    public static final DateTimeFormatter zuluFormat = DateTimeFormatter.ISO_INSTANT;

    /**
     * Adapter for ZonedDateTime.
     */
    public static class GsonUTCAdapter implements JsonSerializer<ZonedDateTime>, JsonDeserializer<ZonedDateTime> {
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
    protected final Gson decoder = new GsonBuilder().disableHtmlEscaping()
        .registerTypeAdapter(ZonedDateTime.class, new GsonUTCAdapter())
        .registerTypeAdapter(Instant.class, new GsonInstantAdapter()).create();

    /**
     * encoder.
     */
    @JsonIgnore
    protected final Gson encoder = new GsonBuilder().disableHtmlEscaping()
        .registerTypeAdapter(ZonedDateTime.class, new GsonUTCAdapter())
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
    protected Gson getDecoder() {
        return this.decoder;
    }

    /**
     * gets the Gson encoder.
     *
     * @return the Gson encoder
     */
    @JsonIgnore
    protected Gson getEncoder() {
        return this.encoder;
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public Object decode(String json) {

        final DroolsController droolsController =
                DroolsController.factory.get(this.groupId, this.artifactId, "");
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
                logger.warn("{}: cannot fetch application class {}", this, decoderFilter.getCodedClass());
                throw new IllegalStateException(
                        "cannot fetch application class " + decoderFilter.getCodedClass());
            }
        } catch (final Exception e) {
            logger.warn("{}: cannot fetch application class {} because of {}", this,
                    decoderFilter.getCodedClass(), e.getMessage());
            throw new UnsupportedOperationException(
                    "cannot fetch application class " + decoderFilter.getCodedClass(), e);
        }

        if (this.customCoder != null) {
            try {
                final Class<?> gsonClassContainer =
                        droolsController.fetchModelClass(this.customCoder.getClassContainer());
                final Field gsonField = gsonClassContainer.getField(this.customCoder.staticCoderField);
                final Object gsonObject = gsonField.get(null);
                final Method fromJsonMethod = gsonObject.getClass().getDeclaredMethod("fromJson",
                        new Class[] {String.class, Class.class});
                return fromJsonMethod.invoke(gsonObject, json, decoderClass);
            } catch (final Exception e) {
                logger.warn("{}: cannot fetch application class {} because of {}", this,
                        decoderFilter.getCodedClass(), e.getMessage());
                throw new UnsupportedOperationException(
                        "cannot fetch application class " + decoderFilter.getCodedClass(), e);
            }
        } else {
            try {
                return this.decoder.fromJson(json, decoderClass);
            } catch (final Exception e) {
                logger.warn("{} cannot decode {} into {} because of {}", this, json, decoderClass.getName(),
                        e.getMessage(), e);
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
                        DroolsController.factory.get(this.groupId, this.artifactId, null);
                final Class<?> gsonClassContainer =
                        droolsController.fetchModelClass(this.customCoder.getClassContainer());
                final Field gsonField = gsonClassContainer.getField(this.customCoder.staticCoderField);
                final Object gsonObject = gsonField.get(null);
                final Method toJsonMethod =
                        gsonObject.getClass().getDeclaredMethod("toJson", new Class[] {Object.class});
                return (String) toJsonMethod.invoke(gsonObject, event);
            } catch (final Exception e) {
                logger.warn("{} cannot custom-encode {} because of {}", this, event, e.getMessage(), e);
                throw new UnsupportedOperationException("event cannot be encoded", e);
            }
        } else {
            try {
                return this.encoder.toJson(event);
            } catch (final Exception e) {
                logger.warn("{} cannot encode {} because of {}", this, event, e.getMessage(), e);
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
