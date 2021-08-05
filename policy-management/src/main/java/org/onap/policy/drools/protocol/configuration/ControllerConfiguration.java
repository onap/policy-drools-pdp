/*
 * ============LICENSE_START=======================================================
 * policy-management
 * ================================================================================
 * Copyright (C) 2017-2021 AT&T Intellectual Property. All rights reserved.
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

import java.util.HashMap;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.onap.policy.common.gson.annotation.GsonJsonAnyGetter;
import org.onap.policy.common.gson.annotation.GsonJsonAnySetter;
import org.onap.policy.common.gson.annotation.GsonJsonIgnore;
import org.onap.policy.common.gson.annotation.GsonJsonProperty;


/**
 * Drools Related Information.
 *
 */
@ToString
@EqualsAndHashCode
@NoArgsConstructor
public class ControllerConfiguration {

    public static final String CONFIG_CONTROLLER_OPERATION_CREATE = "create";
    public static final String CONFIG_CONTROLLER_OPERATION_UPDATE = "update";
    public static final String CONFIG_CONTROLLER_OPERATION_LOCK = "lock";
    public static final String CONFIG_CONTROLLER_OPERATION_UNLOCK = "unlock";

    protected static final Object NOT_FOUND_VALUE = new Object();

    /**
     * (Required).
     *
     */
    @GsonJsonProperty("name")
    private String name;
    /**
     * Set of operations that can be applied to a controller: create, lock
     * (Required).
     *
     */
    @GsonJsonProperty("operation")
    private String operation;
    /**
     * Maven Related Information.
     *
     */
    @GsonJsonProperty("drools")
    private DroolsConfiguration drools;

    @GsonJsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<>();


    /**
     * Constructor.
     *
     * @param name name
     * @param operation operation
     * @param drools drools
     */
    public ControllerConfiguration(String name, String operation, DroolsConfiguration drools) {
        this.name = name;
        this.operation = operation;
        this.drools = drools;
    }

    /**
     * (Required).
     *
     * @return
     *     The name
     */
    @GsonJsonProperty("name")
    public String getName() {
        return name;
    }

    /**
     * (Required).
     *
     * @param name
     *     The name
     */
    @GsonJsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    public ControllerConfiguration withName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Set of operations that can be applied to a controller: create, lock
     * (Required).
     *
     * @return
     *     The operation
     */
    @GsonJsonProperty("operation")
    public String getOperation() {
        return operation;
    }

    /**
     * Set of operations that can be applied to a controller: create, lock
     * (Required).
     *
     * @param operation
     *     The operation
     */
    @GsonJsonProperty("operation")
    public void setOperation(String operation) {
        this.operation = operation;
    }

    public ControllerConfiguration withOperation(String operation) {
        this.operation = operation;
        return this;
    }

    /**
     * Maven Related Information.
     *
     * @return
     *     The drools
     */
    @GsonJsonProperty("drools")
    public DroolsConfiguration getDrools() {
        return drools;
    }

    /**
     * Maven Related Information.
     *
     * @param drools
     *     The drools
     */
    @GsonJsonProperty("drools")
    public void setDrools(DroolsConfiguration drools) {
        this.drools = drools;
    }

    public ControllerConfiguration withDrools(DroolsConfiguration drools) {
        this.drools = drools;
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

    public ControllerConfiguration withAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
        return this;
    }

    protected boolean declaredProperty(String name, Object value) {
        switch (name) {
            case "name":
                callSetName(value);
                return true;
            case "operation":
                callSetOperation(value);
                return true;
            case "drools":
                callSetDrools(value);
                return true;
            default:
                return false;
        }
    }

    protected Object declaredPropertyOrNotFound(String name, Object notFoundValue) {
        switch (name) {
            case "name":
                return getName();
            case "operation":
                return getOperation();
            case "drools":
                return getDrools();
            default:
                return notFoundValue;
        }
    }

    /**
     * Get.
     *
     * @param name name
     * @return the object
     */
    @SuppressWarnings({
        "unchecked"
        })
    public <T> T get(String name) {
        Object value = declaredPropertyOrNotFound(name, ControllerConfiguration.NOT_FOUND_VALUE);
        if (ControllerConfiguration.NOT_FOUND_VALUE != value) {
            return ((T) value);
        } else {
            return ((T) getAdditionalProperties().get(name));
        }
    }

    /**
     * Set the property.
     *
     * @param name property name
     * @param value property value
     */
    public void set(String name, Object value) {
        if (!declaredProperty(name, value)) {
            getAdditionalProperties().put(name, value);
        }
    }

    /**
     * With - sets the property and additionally returns the object.
     *
     * @param name property name
     * @param value property value
     * @return this
     */
    public ControllerConfiguration with(String name, Object value) {
        if (!declaredProperty(name, value)) {
            getAdditionalProperties().put(name, value);
        }
        return this;
    }

    /**
     * Call set name.
     *
     * @param value value
     */
    public void callSetName(Object value) {
        if (value instanceof String) {
            setName((String) value);
        } else {
            throw new IllegalArgumentException("property \"name\" is of type \"java.lang.String\", but got "
                    + value.getClass().toString());
        }
    }

    /**
     * Call set operation.
     *
     * @param value value
     */
    public void callSetOperation(Object value) {
        if (value instanceof String) {
            setOperation((String) value);
        } else {
            throw new IllegalArgumentException("property \"operation\" is of type \"java.lang.String\", but got "
                    + value.getClass().toString());
        }
    }

    /**
     * Call set drools.
     *
     * @param value value
     */
    public void callSetDrools(Object value) {
        if (value instanceof DroolsConfiguration) {
            setDrools((DroolsConfiguration) value);
        } else {
            throw new IllegalArgumentException("property \"drools\" is of type"
                    + "\"org.onap.policy.drools.protocol.configuration.Drools\", but got "
                    + value.getClass().toString());
        }
    }

}
