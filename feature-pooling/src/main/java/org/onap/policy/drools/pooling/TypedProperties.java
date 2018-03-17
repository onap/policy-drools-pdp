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

public class TypedProperties {

    private final Properties props;

    public TypedProperties() {
        props = new Properties();
    }

    public TypedProperties(Properties props) {
        this.props = props;

    }

    public Properties getProperties() {
        return props;
    }

    public String getStrProperty(String name) throws PoolingFeatureException {
        Object val = props.get(name);
        if (val == null) {
            throw new PoolingFeatureException("missing property: " + name);
        }

        return val.toString();
    }

    public boolean getBoolProperty(String name) throws PoolingFeatureException {
        Object val = props.get(name);
        if (val == null) {
            throw new PoolingFeatureException("missing property: " + name);
        }

        return Boolean.parseBoolean(val.toString());
    }

    public int getIntProperty(String name) throws PoolingFeatureException {
        Object val = props.get(name);
        if (val == null) {
            throw new PoolingFeatureException("missing property: " + name);
        }

        try {
            return Integer.parseInt(val.toString());

        } catch (NumberFormatException ex) {
            throw new PoolingFeatureException("invalid property value: " + name, ex);
        }
    }

    public long getLongProperty(String name) throws PoolingFeatureException {
        Object val = props.get(name);
        if (val == null) {
            throw new PoolingFeatureException("missing property: " + name);
        }

        try {
            return Long.parseLong(val.toString());

        } catch (NumberFormatException ex) {
            throw new PoolingFeatureException("invalid property value: " + name, ex);
        }
    }

    public String getOptStrProperty(String name) {
        Object val = props.get(name);
        return (val == null ? null : val.toString());
    }

    public Boolean getOptBoolProperty(String name) {
        Object val = props.get(name);
        if (val == null) {
            return null;
        }

        return Boolean.valueOf(val.toString());
    }

    public Integer getOptIntProperty(String name) throws PoolingFeatureException {
        Object val = props.get(name);
        if (val == null) {
            return null;
        }

        try {
            return Integer.valueOf(val.toString());

        } catch (NumberFormatException ex) {
            throw new PoolingFeatureException("invalid property value: " + name, ex);
        }
    }

    public Long getOptLongProperty(String name) throws PoolingFeatureException {
        Object val = props.get(name);
        if (val == null) {
            return null;
        }

        try {
            return Long.valueOf(val.toString());

        } catch (NumberFormatException ex) {
            throw new PoolingFeatureException("invalid property value: " + name, ex);
        }
    }

    public String getOptStrProperty(String name, String defaultValue) {
        Object val = props.get(name);
        if (val == null) {
            return defaultValue;
        }

        return val.toString();
    }

    public boolean getOptBoolProperty(String name, boolean defaultValue) {
        Object val = props.get(name);
        if (val == null) {
            return defaultValue;
        }

        return Boolean.valueOf(val.toString());
    }

    public int getOptIntProperty(String name, int defaultValue) throws PoolingFeatureException {
        Object val = props.get(name);
        if (val == null) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(val.toString());

        } catch (NumberFormatException ex) {
            throw new PoolingFeatureException("invalid property value: " + name, ex);
        }
    }

    public long getOptLongProperty(String name, long defaultValue) throws PoolingFeatureException {
        Object val = props.get(name);
        if (val == null) {
            return defaultValue;
        }

        try {
            return Long.parseLong(val.toString());

        } catch (NumberFormatException ex) {
            throw new PoolingFeatureException("invalid property value: " + name, ex);
        }
    }
}
