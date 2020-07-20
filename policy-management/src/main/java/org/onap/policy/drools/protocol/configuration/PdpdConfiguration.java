/*
 * ============LICENSE_START=======================================================
 * policy-management
 * ================================================================================
 * Copyright (C) 2017-2020 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.protocol.configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.ToString;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.onap.policy.common.gson.annotation.GsonJsonAnyGetter;
import org.onap.policy.common.gson.annotation.GsonJsonAnySetter;
import org.onap.policy.common.gson.annotation.GsonJsonIgnore;
import org.onap.policy.common.gson.annotation.GsonJsonProperty;


/**
 * ENGINE-CONFIGURATION.
 */
@ToString
public class PdpdConfiguration {

    /** Controller Entity ID. */
    public static final String CONFIG_ENTITY_CONTROLLER = "controller";

    /** Unique Transaction ID. This is an UUID. (Required) */
    @GsonJsonProperty("requestID")
    private String requestId;
    /* Set of entities on which configuration can be performed: controller (Required) */
    @GsonJsonProperty("entity")
    private String entity;
    /* Controller Information, only applicable when the entity is set to controller */
    @GsonJsonProperty("controllers")
    private List<ControllerConfiguration> controllers = new ArrayList<>();

    @GsonJsonIgnore private Map<String, Object> additionalProperties = new HashMap<>();
    protected static final Object NOT_FOUND_VALUE = new Object();

    /** No args constructor for use in serialization. */
    public PdpdConfiguration() {
        // Empty
    }

    /**
     * Constructor.
     *
     * @param requestId request id
     * @param entity entity
     * @param controllers controllers
     */
    public PdpdConfiguration(
            String requestId, String entity, List<ControllerConfiguration> controllers) {
        this.requestId = requestId;
        this.entity = entity;
        this.controllers = controllers;
    }

    /**
     * Unique Transaction ID. This is an UUID. (Required)
     *
     * @return The requestID
     */
    @GsonJsonProperty("requestID")
    public String getRequestId() {
        return requestId;
    }

    /**
     * Unique Transaction ID. This is an UUID. (Required)
     *
     * @param requestId The requestID
     */
    @GsonJsonProperty("requestID")
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public PdpdConfiguration withRequestId(String requestId) {
        this.requestId = requestId;
        return this;
    }

    /**
     * Set of entities on which configuration can be performed: controller (Required).
     *
     * @return The entity
     */
    @GsonJsonProperty("entity")
    public String getEntity() {
        return entity;
    }

    /**
     * Set of entities on which configuration can be performed: controller (Required).
     *
     * @param entity The entity
     */
    @GsonJsonProperty("entity")
    public void setEntity(String entity) {
        this.entity = entity;
    }

    public PdpdConfiguration withEntity(String entity) {
        this.entity = entity;
        return this;
    }

    /**
     * Controller Information, only applicable when the entity is set to controller.
     *
     * @return The controller
     */
    @GsonJsonProperty("controllers")
    public List<ControllerConfiguration> getControllers() {
        return controllers;
    }

    /**
     * Controller Information, only applicable when the entity is set to controller.
     *
     * @param controllers controllers
     */
    @GsonJsonProperty("controllers")
    public void setControllers(List<ControllerConfiguration> controllers) {
        this.controllers = controllers;
    }

    public PdpdConfiguration withController(List<ControllerConfiguration> controllers) {
        this.controllers = controllers;
        return this;
    }

    @GsonJsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @GsonJsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

    public PdpdConfiguration withAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
        return this;
    }

    protected boolean declaredProperty(String name, Object value) {
        switch (name) {
            case "requestID":
                callSetRequestId(value);
                return true;
            case "entity":
                callSetEntity(value);
                return true;
            case "controllers":
                callSetControllers(value);
                return true;
            default:
                return false;
        }
    }

    protected Object declaredPropertyOrNotFound(String name, Object notFoundValue) {
        switch (name) {
            case "requestID":
                return getRequestId();
            case "entity":
                return getEntity();
            case "controllers":
                return getControllers();
            default:
                return notFoundValue;
        }
    }

    /**
     * Get.
     *
     * @param name name
     * @return object
     */
    @SuppressWarnings({"unchecked"})
    public <T> T get(String name) {
        Object value = declaredPropertyOrNotFound(name, PdpdConfiguration.NOT_FOUND_VALUE);
        if (PdpdConfiguration.NOT_FOUND_VALUE != value) {
            return (T) value;
        } else {
            return (T) getAdditionalProperties().get(name);
        }
    }

    /**
     * Set property.
     *
     * @param name name
     * @param value value
     */
    public void set(String name, Object value) {
        if (!declaredProperty(name, value)) {
            getAdditionalProperties().put(name, value);
        }
    }

    /**
     * With - sets and returns the object.
     */
    public PdpdConfiguration with(String name, Object value) {
        if (!declaredProperty(name, value)) {
            getAdditionalProperties().put(name, value);
        }
        return this;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(requestId)
                .append(entity)
                .append(controllers)
                .append(additionalProperties)
                .toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof PdpdConfiguration)) {
            return false;
        }
        PdpdConfiguration rhs = (PdpdConfiguration) other;
        return new EqualsBuilder()
                .append(requestId, rhs.requestId)
                .append(entity, rhs.entity)
                .append(controllers, rhs.controllers)
                .append(additionalProperties, rhs.additionalProperties)
                .isEquals();
    }

    /**
     * Call set request id.
     *
     * @param value value
     */
    public void callSetRequestId(Object value) {
        if (value instanceof String) {
            setRequestId((String) value);
        } else {
            throw new IllegalArgumentException(
                    "property \"requestID\" is of type \"java.lang.String\", but got "
                            + value.getClass().toString());
        }
    }

    /**
     * Call set entity.
     *
     * @param value value
     */
    public void callSetEntity(Object value) {
        if (value instanceof String) {
            setEntity((String) value);
        } else {
            throw new IllegalArgumentException(
                    "property \"entity\" is of type \"java.lang.String\", but got "
                            + value.getClass().toString());
        }
    }

    /**
     * Call set controllers.
     *
     * @param value value
     */
    @SuppressWarnings("unchecked")
    public void callSetControllers(Object value) {
        if (value instanceof List) {
            setControllers((List<ControllerConfiguration>) value);
        } else {
            throw new IllegalArgumentException(
                    "property \"controllers\" is of type "
                            + "\"java.util.List<org.onap.policy.drools.protocol.configuration.Controller>\", "
                            + "but got "
                            + value.getClass().toString());
        }
    }
}
