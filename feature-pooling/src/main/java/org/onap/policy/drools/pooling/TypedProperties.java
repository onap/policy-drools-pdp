/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.pooling;

import java.util.Properties;

/**
 * Properties having typed "getXxx()" methods.
 */
public class TypedProperties {

    public static final String MISSING_PROPERTY = "missing property: ";
    public static final String INVALID_PROPERTY_VALUE = "invalid property value: ";
    
    /**
     * The wrapped properties.
     */
    private final Properties props;

    /**
     * Constructs an empty property set.
     */
    public TypedProperties() {
        props = new Properties();
    }

    /**
     * Constructs a property set wrapping the given properties.
     * 
     * @param props set of properties to be wrapped
     */
    public TypedProperties(Properties props) {
        this.props = props;

    }

    public Properties getProperties() {
        return props;
    }

    /**
     * Gets a property, as a "String" value.
     * 
     * @param name name of the property
     * @return the value of the property
     * @throws PoolingFeatureException if the property does not exist
     */
    public String getStrProperty(String name) throws PoolingFeatureException {
        Object val = props.get(name);
        if (val == null) {
            throw new PoolingFeatureException(MISSING_PROPERTY + name);
        }

        return val.toString();
    }

    /**
     * Gets a property, as a "boolean" value.
     * 
     * @param name name of the property
     * @return the value of the property
     * @throws PoolingFeatureException if the property does not exist
     */
    public boolean getBoolProperty(String name) throws PoolingFeatureException {
        Object val = props.get(name);
        if (val == null) {
            throw new PoolingFeatureException(MISSING_PROPERTY + name);
        }

        return Boolean.parseBoolean(val.toString());
    }

    /**
     * Gets a property, as an "int" value.
     * 
     * @param name name of the property
     * @return the value of the property
     * @throws PoolingFeatureException if the property does not exist or is not
     *         a valid number
     */
    public int getIntProperty(String name) throws PoolingFeatureException {
        Object val = props.get(name);
        if (val == null) {
            throw new PoolingFeatureException(MISSING_PROPERTY + name);
        }

        try {
            return Integer.parseInt(val.toString());

        } catch (NumberFormatException ex) {
            throw new PoolingFeatureException(INVALID_PROPERTY_VALUE + name, ex);
        }
    }

    /**
     * Gets a property, as a "long" value.
     * 
     * @param name name of the property
     * @return the value of the property
     * @throws PoolingFeatureException if the property does not exist or is not
     *         a valid number
     */
    public long getLongProperty(String name) throws PoolingFeatureException {
        Object val = props.get(name);
        if (val == null) {
            throw new PoolingFeatureException(MISSING_PROPERTY + name);
        }

        try {
            return Long.parseLong(val.toString());

        } catch (NumberFormatException ex) {
            throw new PoolingFeatureException(INVALID_PROPERTY_VALUE + name, ex);
        }
    }

    /**
     * Gets an optional property, as a "String" value.
     * 
     * @param name name of the property
     * @return the value of the property, or {@code null} if it does not exist
     */
    public String getOptStrProperty(String name) {
        Object val = props.get(name);
        return (val == null ? null : val.toString());
    }

    /**
     * Gets an optional property, as a "Boolean" value.
     * 
     * @param name name of the property
     * @return the value of the property, or {@code null} if it does not exist
     */
    public Boolean getOptBoolProperty(String name) {
        Object val = props.get(name);
        if (val == null) {
            return null;
        }

        return Boolean.valueOf(val.toString());
    }

    /**
     * Gets an optional property, as an "Integer" value.
     * 
     * @param name name of the property
     * @return the value of the property, or {@code null} if it does not exist
     * @throws PoolingFeatureException if the value is not a valid number
     */
    public Integer getOptIntProperty(String name) throws PoolingFeatureException {
        Object val = props.get(name);
        if (val == null) {
            return null;
        }

        try {
            return Integer.valueOf(val.toString());

        } catch (NumberFormatException ex) {
            throw new PoolingFeatureException(INVALID_PROPERTY_VALUE + name, ex);
        }
    }

    /**
     * Gets an optional property, as a "Long" value.
     * 
     * @param name name of the property
     * @return the value of the property, or {@code null} if it does not exist
     * @throws PoolingFeatureException if the value is not a valid number
     */
    public Long getOptLongProperty(String name) throws PoolingFeatureException {
        Object val = props.get(name);
        if (val == null) {
            return null;
        }

        try {
            return Long.valueOf(val.toString());

        } catch (NumberFormatException ex) {
            throw new PoolingFeatureException(INVALID_PROPERTY_VALUE + name, ex);
        }
    }

    /**
     * Gets an optional property, as a "String" value.
     * 
     * @param name name of the property
     * @param defaultValue
     * @return the value of the property, or <i>defaultValue</i> if it does not
     *         exist
     */
    public String getOptStrProperty(String name, String defaultValue) {
        Object val = props.get(name);
        if (val == null) {
            return defaultValue;
        }

        return val.toString();
    }

    /**
     * Gets an optional property, as a "boolean" value.
     * 
     * @param name name of the property
     * @param defaultValue
     * @return the value of the property, or <i>defaultValue</i> if it does not
     *         exist
     */
    public boolean getOptBoolProperty(String name, boolean defaultValue) {
        Object val = props.get(name);
        if (val == null) {
            return defaultValue;
        }

        return Boolean.valueOf(val.toString());
    }

    /**
     * Gets an optional property, as an "int" value.
     * 
     * @param name name of the property
     * @param defaultValue
     * @return the value of the property, or <i>defaultValue</i> if it does not
     *         exist
     * @throws PoolingFeatureException if the value is not a valid number
     */
    public int getOptIntProperty(String name, int defaultValue) throws PoolingFeatureException {
        Object val = props.get(name);
        if (val == null) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(val.toString());

        } catch (NumberFormatException ex) {
            throw new PoolingFeatureException(INVALID_PROPERTY_VALUE + name, ex);
        }
    }

    /**
     * Gets an optional property, as a "long" value.
     * 
     * @param name name of the property
     * @param defaultValue
     * @return the value of the property, or <i>defaultValue</i> if it does not
     *         exist
     * @throws PoolingFeatureException if the value is not a valid number
     */
    public long getOptLongProperty(String name, long defaultValue) throws PoolingFeatureException {
        Object val = props.get(name);
        if (val == null) {
            return defaultValue;
        }

        try {
            return Long.parseLong(val.toString());

        } catch (NumberFormatException ex) {
            throw new PoolingFeatureException(INVALID_PROPERTY_VALUE + name, ex);
        }
    }
}
