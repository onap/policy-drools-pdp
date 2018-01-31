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

package org.onap.policy.drools.protocol.configuration;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Maven Related Information
 *
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DroolsConfiguration {

    /**
     * Maven Artifact ID
     * (Required)
     *
     */
    @JsonProperty("artifactId")
    private String artifactId;
    /**
     * Maven Group ID
     * (Required)
     *
     */
    @JsonProperty("groupId")
    private String groupId;
    /**
     * Maven Version
     * (Required)
     *
     */
    @JsonProperty("version")
    private String version;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<>();
    protected final static Object NOT_FOUND_VALUE = new Object();

    /**
     * No args constructor for use in serialization
     *
     */
    public DroolsConfiguration() {
    	// Empty
    }

    /**
     *
     * @param groupId
     * @param artifactId
     * @param version
     */
    public DroolsConfiguration(String artifactId, String groupId, String version) {
        this.artifactId = artifactId;
        this.groupId = groupId;
        this.version = version;
    }

    /**
     * Maven Artifact ID
     * (Required)
     *
     * @return
     *     The artifactId
     */
    @JsonProperty("artifactId")
    public String getArtifactId() {
        return artifactId;
    }

    /**
     * Maven Artifact ID
     * (Required)
     *
     * @param artifactId
     *     The artifactId
     */
    @JsonProperty("artifactId")
    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public DroolsConfiguration withArtifactId(String artifactId) {
        this.artifactId = artifactId;
        return this;
    }

    /**
     * Maven Group ID
     * (Required)
     *
     * @return
     *     The groupId
     */
    @JsonProperty("groupId")
    public String getGroupId() {
        return groupId;
    }

    /**
     * Maven Group ID
     * (Required)
     *
     * @param groupId
     *     The groupId
     */
    @JsonProperty("groupId")
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public DroolsConfiguration withGroupId(String groupId) {
        this.groupId = groupId;
        return this;
    }

    /**
     * Maven Version
     * (Required)
     *
     * @return
     *     The version
     */
    @JsonProperty("version")
    public String getVersion() {
        return version;
    }

    /**
     * Maven Version
     * (Required)
     *
     * @param version
     *     The version
     */
    @JsonProperty("version")
    public void setVersion(String version) {
        this.version = version;
    }

    public DroolsConfiguration withVersion(String version) {
        this.version = version;
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

    public DroolsConfiguration withAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
        return this;
    }

    protected boolean declaredProperty(String name, Object value) {
        switch (name) {
            case "artifactId":
                callSetArtifactId(value);
                return true;
            case "groupId":
                callSetGroupId(value);
                return true;
            case "version":
                callSetVersion(value);
                return true;
            default:
                return false;
        }
    }

    protected Object declaredPropertyOrNotFound(String name, Object notFoundValue) {
        switch (name) {
            case "artifactId":
                return getArtifactId();
            case "groupId":
                return getGroupId();
            case "version":
                return getVersion();
            default:
                return notFoundValue;
        }
    }

    @SuppressWarnings({
        "unchecked"
    })
    public<T >T get(String name) {
        Object value = declaredPropertyOrNotFound(name, DroolsConfiguration.NOT_FOUND_VALUE);
        if (DroolsConfiguration.NOT_FOUND_VALUE!= value) {
            return (T) value;
        } else {
            return (T) getAdditionalProperties().get(name);
        }
    }

    public void set(String name, Object value) {
        if (!declaredProperty(name, value)) {
            getAdditionalProperties().put(name, value);
        }
    }

    public DroolsConfiguration with(String name, Object value) {
        if (!declaredProperty(name, value)) {
            getAdditionalProperties().put(name, value);
        }
        return this;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(artifactId).append(groupId).append(version).append(additionalProperties).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof DroolsConfiguration) == false) {
            return false;
        }
        DroolsConfiguration rhs = ((DroolsConfiguration) other);
        return new EqualsBuilder().append(artifactId, rhs.artifactId).append(groupId, rhs.groupId).append(version, rhs.version).append(additionalProperties, rhs.additionalProperties).isEquals();
    }

    public void callSetArtifactId(Object value) {
        if (value instanceof String) {
            setArtifactId((String) value);
        } else {
            throw new IllegalArgumentException("property \"artifactId\" is of type \"java.lang.String\", but got "+ value.getClass().toString());
        }
    }

    public void callSetGroupId(Object value) {
        if (value instanceof String) {
            setGroupId((String) value);
        } else {
            throw new IllegalArgumentException("property \"groupId\" is of type \"java.lang.String\", but got "+ value.getClass().toString());
        }
    }

    public void callSetVersion(Object value) {
        if (value instanceof String) {
            setVersion((String) value);
        } else {
            throw new IllegalArgumentException("property \"version\" is of type \"java.lang.String\", but got "+ value.getClass().toString());
        }
    }
}
