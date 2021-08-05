/*
 * ============LICENSE_START=======================================================
 * feature-healthcheck
 * ================================================================================
 * Copyright (C) 2017-2019, 2021 AT&T Intellectual Property. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.onap.policy.common.capabilities.Startable;

/**
 * Healthcheck.
 */
public interface HealthCheck extends Startable {

    /**
     * Healthcheck Report.
     */
    @Getter
    @Setter
    @ToString
    public static class Report {
        /**
         * Named Entity in the report.
         */
        private String name;

        /**
         * URL queried.
         */
        private String url;

        /**
         * healthy.
         */
        private boolean healthy;

        /**
         * return code.
         */
        private int code;

        /**
         * Message from remote entity.
         */
        private String message;
    }

    /**
     * Report aggregation.
     */
    @Getter
    @Setter
    @ToString
    public static class Reports {
        private boolean healthy;
        private List<Report> details = new ArrayList<>();
    }

    /**
     * Perform a healthcheck.
     *
     * @return a report
     */
    Reports healthCheck();
}
