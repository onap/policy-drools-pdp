/*-
 * ============LICENSE_START=======================================================
 * policy-core
 * ================================================================================
 * Copyright (C) 2017-2019 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.properties;

public class DroolsPropertyConstants {

    /* Controller Properties */

    public static final String PROPERTY_CONTROLLER_NAME = "controller.name";

    public static final String PROPERTY_CONTROLLER_TYPE = "controller.type";

    public static final String DEFAULT_CONTROLLER_POLICY_TYPE_VERSION = "1.0.0";
    public static final String PROPERTY_CONTROLLER_POLICY_TYPES = "controller.policy.types";

    /* Drools Properties */

    public static final String RULES_GROUPID = "rules.groupId";
    public static final String RULES_ARTIFACTID = "rules.artifactId";
    public static final String RULES_VERSION = "rules.version";

    private DroolsPropertyConstants() {
        // do nothing
    }
}
