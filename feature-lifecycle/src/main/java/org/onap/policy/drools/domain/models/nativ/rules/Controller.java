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
 * Controller Model in Native Drools Policies.
 */

@Data
@NoArgsConstructor
public class Controller implements Serializable {

    private static final long serialVersionUID = -2070515139072136869L;

    @Expose
    @SerializedName("name")
    protected String name;

    @Expose
    @SerializedName("version")
    protected String version;

    public Controller withName(String name) {
        this.name = name;
        return this;
    }

    public Controller withVersion(String version) {
        this.version = version;
        return this;
    }

}
