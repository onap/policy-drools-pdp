/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2021 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.legacy.config;

import lombok.Getter;
import org.onap.policy.drools.features.PolicyEngineFeatureApi;
import org.onap.policy.drools.system.PolicyEngine;

/**
 * The LegacyConfigFeature enables legacy configuration mechanisms
 * in the PDP-D.
 */
public class LegacyConfigFeature implements PolicyEngineFeatureApi {

    protected static final int SEQNO = 1;

    @Getter
    private static final LegacyConfig legacyConfig = new LegacyConfig();

    @Override
    public int getSequenceNumber() {
        return SEQNO;
    }

    @Override
    public boolean afterOpen(PolicyEngine engine) {
        getLegacyConfig().start();
        return false;
    }

    @Override
    public boolean beforeShutdown(PolicyEngine engine) {
        getLegacyConfig().shutdown();
        return false;
    }

}
