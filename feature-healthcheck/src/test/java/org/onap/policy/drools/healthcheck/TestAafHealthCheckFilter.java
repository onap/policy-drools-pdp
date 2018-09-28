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

package org.onap.policy.drools.healthcheck;

import javax.servlet.http.HttpServletRequest;
import org.onap.policy.common.utils.network.NetworkUtil;

/**
 * Healthcheck AAF Authorization Filter.
 */
public class TestAafHealthCheckFilter extends AafHealthCheckFilter {

    @Override
    protected String getRole(HttpServletRequest request) {
        String expectedPerm = "org.onap.policy.pdpd.healthcheck.test|"
            + NetworkUtil.getHostname() + "|get";
        if (!expectedPerm.equals(super.getRole(request))) {
            throw new IllegalStateException("unexpected AAF granular permission");
        } else {
            return "user";
        }
    }

    @Override
    public String getPermissionTypeRoot() {
        return "org.onap.policy.pdpd";
    }
}
