/*-
 * ============LICENSE_START=======================================================
 * policy-management
 * ================================================================================
 * Copyright (C) 2019, 2021-2022 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2023-2024 Nordix Foundation.
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

package org.onap.policy.drools.system;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PolicyEngineConstants {

    /**
     * Default Telemetry Server Port.
     */
    public static final int TELEMETRY_SERVER_DEFAULT_PORT = 9696;

    /**
     * Default Telemetry Server Hostname.
     */
    public static final String TELEMETRY_SERVER_DEFAULT_HOST = "localhost";

    /**
     * Default Telemetry Server Name.
     */
    public static final String TELEMETRY_SERVER_DEFAULT_NAME = "TELEMETRY";

    /**
     * Topics constants.
     */
    public static final String PROPERTY_TOPIC_EVENTS_SUFFIX = ".events";
    public static final String PROPERTY_TOPIC_EVENTS_CUSTOM_MODEL_CODER_GSON_SUFFIX = ".events.custom.gson";
    public static final String PROPERTY_TOPIC_EVENTS_FILTER_SUFFIX = ".filter";

    /**
     * Policy Engine Manager.
     */
    @Getter
    private static final PolicyEngine manager = new PolicyEngineManager();
}
