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
 * Rules Artifact in Native Drools Policies.
 */

@Data
@NoArgsConstructor
public class RulesArtifact implements Serializable {

    private static final long serialVersionUID = -3519759514319217518L;

    @Expose
    @SerializedName("groupId")
    protected String groupId;

    @Expose
    @SerializedName("artifactId")
    protected String artifactId;

    @Expose
    @SerializedName("version")
    protected String version;

    /**
     * Group Id builder method.
     */
    public RulesArtifact withGroupId(String groupId) {
        this.groupId = groupId;
        return this;
    }

    /**
     * Artifact Id builder method.
     */
    public RulesArtifact withArtifactId(String artifactId) {
        this.artifactId = artifactId;
        return this;
    }

    /**
     * Version builder method.
     */
    public RulesArtifact withVersion(String version) {
        this.version = version;
        return this;
    }

}
