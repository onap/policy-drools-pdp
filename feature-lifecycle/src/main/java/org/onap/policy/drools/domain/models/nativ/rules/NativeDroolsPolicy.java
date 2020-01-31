/*
 * ============LICENSE_START=======================================================
 *  Copyright (C) 2020 AT&T Intellectual Property. All rights reserved.
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
 *
 * SPDX-License-Identifier: Apache-2.0
 * ============LICENSE_END=========================================================
 */

package org.onap.policy.drools.domain.models.nativ.rules;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Native Drools Policy model root object.
 */

@Data
@NoArgsConstructor
public class NativeDroolsPolicy implements Serializable {

    private static final long serialVersionUID = -8171337852833516581L;

    @Expose
    @SerializedName("type")
    protected String type;

    @Expose
    @SerializedName("type_version")
    protected String typeVersion;

    @Expose
    @SerializedName("version")
    protected String version;

    @Expose
    @SerializedName("name")
    protected String name;

    @Expose
    @SerializedName("metadata")
    protected Metadata metadata;

    @Expose
    @SerializedName("properties")
    protected Properties properties;

    /**
     * Type version builder method.
     */
    public NativeDroolsPolicy withType(String type) {
        this.type = type;
        return this;
    }

    /**
     * Type version builder method.
     */
    public NativeDroolsPolicy withTypeVersion(String typeVersion) {
        this.typeVersion = typeVersion;
        return this;
    }

    /**
     * Version builder method.
     */
    public NativeDroolsPolicy withVersion(String version) {
        this.version = version;
        return this;
    }

    /**
     * Name builder method.
     */
    public NativeDroolsPolicy withName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Metadata builder method.
     */
    public NativeDroolsPolicy withMetadata(Metadata metadata) {
        this.metadata = metadata;
        return this;
    }

    /**
     * Properties builder method.
     */
    public NativeDroolsPolicy withProperties(Properties properties) {
        this.properties = properties;
        return this;
    }

}
