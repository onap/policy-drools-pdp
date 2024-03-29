/*-
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2017-2019, 2022 AT&T Intellectual Property. All rights reserved.
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

import org.onap.policy.drools.features.PolicyEngineFeatureApi;
import org.onap.policy.drools.system.PolicyEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This feature provides healthcheck verification of remotely associated RESTful components.
 */
public class HealthCheckFeature implements PolicyEngineFeatureApi {

    /**
     * Logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(HealthCheckFeature.class);

    /**
     * Properties Configuration Name.
     */
    public static final String CONFIGURATION_PROPERTIES_NAME = "feature-healthcheck";

    @Override
    public int getSequenceNumber() {
        return 1000;
    }

    @Override
    public boolean afterStart(PolicyEngine engine) {
        try {
            getManager().start();
        } catch (IllegalStateException e) {
            logger.error("Healthcheck Monitor cannot be started", e);
        }

        return false;
    }

    @Override
    public boolean afterOpen(PolicyEngine engine) {
        try {
            getManager().open();
        } catch (IllegalStateException e) {
            logger.error("Healthcheck Monitor cannot be opened", e);
        }

        return false;
    }

    @Override
    public boolean afterShutdown(PolicyEngine engine) {
        try {
            getManager().stop();
        } catch (IllegalStateException e) {
            logger.error("Healthcheck Monitor cannot be stopped", e);
        }

        return false;
    }

    /**
     * Gets the monitor.
     *
     * @return the healthcheck manager
     */
    public HealthCheck getManager() {
        return HealthCheckConstants.getManager();
    }

}
