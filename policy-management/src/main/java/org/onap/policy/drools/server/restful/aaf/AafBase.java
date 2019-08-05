/*-
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

package org.onap.policy.drools.server.restful.aaf;

import org.onap.policy.common.endpoints.http.server.aaf.AafAuthFilter;
import org.onap.policy.drools.system.PolicyEngineConstants;

/**
 * AAF Base Class.
 */
public abstract class AafBase extends AafAuthFilter {
    public static final String AAF_NODETYPE = "pdpd";
    public static final String AAF_ROOT_PERMISSION_PROPERTY = "aaf.root.permission";
    public static final String AAF_ROOT_PERMISSION =
        PolicyEngineConstants.getManager().getProperties().getProperty(
                AAF_ROOT_PERMISSION_PROPERTY, DEFAULT_NAMESPACE + "." + AAF_NODETYPE);
}
