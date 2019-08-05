/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2019 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.controller;

import lombok.Getter;

public class DroolsControllerConstants  {

    /**
     * No Group ID identifier.
     */
    public static final String NO_GROUP_ID = "NO-GROUP-ID";

    /**
     * No Artifact ID identifier.
     */
    public static final String NO_ARTIFACT_ID = "NO-ARTIFACT-ID";

    /**
     * No version identifier.
     */
    public static final String NO_VERSION = "NO-VERSION";

    /**
     * Factory to track and manage drools controllers.
     */
    @Getter
    private static final DroolsControllerFactory factory = new IndexedDroolsControllerFactory();

    private DroolsControllerConstants() {
        // do nothing
    }
}
