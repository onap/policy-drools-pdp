/*-
 * ============LICENSE_START=======================================================
 * policy-management
 * ================================================================================
 * Copyright (C) 2017 AT&T Intellectual Property. All rights reserved.
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

package org.openecomp.policy.drools.protocol.configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;


/**
 * ENGINE-CONFIGURATION
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PdpdConfiguration {
	
	/**
	 * Controller Entity ID
	 */
	public static final String CONFIG_ENTITY_CONTROLLER = "controller";

    /**
     * Unique Transaction ID.   This is an UUID.
     * (Required)
     * 
     */
    @JsonProperty("requestID")
    private String requestID;
    /**
     * Set of entities on which configuration can be performed: controller
     * (Required)
     * 
     */
    @JsonProperty("entity")
    private String entity;
    /**
     * Controller Information, only applicable when the entity is set to controller
     * 
     */
    @JsonProperty("controllers")
    private List<ControllerConfiguration> controllers = new ArrayList<ControllerConfiguration>();
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();
    protected final static Object NOT_FOUND_VALUE = new Object();

    /**
     * No args constructor for use in serialization
     * 
     */
    public PdpdConfiguration() {
    }

    /**
     * 
     * @param controller
     * @param requestID
     * @param entity
     */
    public PdpdConfiguration(String requestID, String entity, List<ControllerConfiguration> controllers) {
        this.requestID = requestID;
        this.entity = entity;
        this.controllers = controllers;
    }

    /**
     * Unique Transaction ID.   This is an UUID.
     * (Required)
     * 
     * @return
     *     The requestID
     */
    @JsonProperty("requestID")
    public String getRequestID() {
        return requestID;
    }

    /**
     * Unique Transaction ID.   This is an UUID.
     * (Required)
     * 
     * @param requestID
     *     The requestID
     */
    @JsonProperty("requestID")
    public void setRequestID(String requestID) {
        this.requestID = requestID;
    }

    public PdpdConfiguration withRequestID(String requestID) {
        this.requestID = requestID;
        return this;
    }

    /**
     * Set of entities on which configuration can be performed: controller
     * (Required)
     * 
     * @return
     *     The entity
     */
    @JsonProperty("entity")
    public String getEntity() {
        return entity;
    }

    /**
     * Set of entities on which configuration can be performed: controller
     * (Required)
     * 
     * @param entity
     *     The entity
     */
    @JsonProperty("entity")
    public void setEntity(String entity) {
        this.entity = entity;
    }

    public PdpdConfiguration withEntity(String entity) {
        this.entity = entity;
        return this;
    }

    /**
     * Controller Information, only applicable when the entity is set to controller
     * 
     * @return
     *     The controller
     */
    @JsonProperty("controller")
    public List<ControllerConfiguration> getControllers() {
        return controllers;
    }

    /**
     * Controller Information, only applicable when the entity is set to controller
     * 
     * @param controller
     *     The controller
     */
    @JsonProperty("controller")
    public void setControllers(List<ControllerConfiguration> controllers) {
        this.controllers = controllers;
    }

    public PdpdConfiguration withController(List<ControllerConfiguration> controllers) {
        this.controllers = controllers;
        return this;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

    public PdpdConfiguration withAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
        return this;
    }

    @SuppressWarnings("unchecked")
	protected boolean declaredProperty(String name, Object value) {
        switch (name) {
            case "requestID":
                if (value instanceof String) {
                    setRequestID(((String) value));
                } else {
                    throw new IllegalArgumentException(("property \"requestID\" is of type \"java.lang.String\", but got "+ value.getClass().toString()));
                }
                return true;
            case "entity":
                if (value instanceof String) {
                    setEntity(((String) value));
                } else {
                    throw new IllegalArgumentException(("property \"entity\" is of type \"java.lang.String\", but got "+ value.getClass().toString()));
                }
                return true;
            case "controllers":
                if (value instanceof List) {
                    setControllers(((List<ControllerConfiguration> ) value));
                } else {
                    throw new IllegalArgumentException(("property \"controllers\" is of type \"java.util.List<org.openecomp.policy.drools.protocol.configuration.Controller>\", but got "+ value.getClass().toString()));
                }
                return true;
            default:
                return false;
        }
    }

    protected Object declaredPropertyOrNotFound(String name, Object notFoundValue) {
        switch (name) {
            case "requestID":
                return getRequestID();
            case "entity":
                return getEntity();
            case "controllers":
                return getControllers();
            default:
                return notFoundValue;
        }
    }

    @SuppressWarnings({
        "unchecked"
    })
    public<T >T get(String name) {
        Object value = declaredPropertyOrNotFound(name, PdpdConfiguration.NOT_FOUND_VALUE);
        if (PdpdConfiguration.NOT_FOUND_VALUE!= value) {
            return ((T) value);
        } else {
            return ((T) getAdditionalProperties().get(name));
        }
    }

    public void set(String name, Object value) {
        if (!declaredProperty(name, value)) {
            getAdditionalProperties().put(name, ((Object) value));
        }
    }

    public PdpdConfiguration with(String name, Object value) {
        if (!declaredProperty(name, value)) {
            getAdditionalProperties().put(name, ((Object) value));
        }
        return this;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(requestID).append(entity).append(controllers).append(additionalProperties).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof PdpdConfiguration) == false) {
            return false;
        }
        PdpdConfiguration rhs = ((PdpdConfiguration) other);
        return new EqualsBuilder().append(requestID, rhs.requestID).append(entity, rhs.entity).append(controllers, rhs.controllers).append(additionalProperties, rhs.additionalProperties).isEquals();
    }

}
