/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2017-2019, 2021-2022 AT&T Intellectual Property. All rights reserved.
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
import lombok.Data;
import lombok.NoArgsConstructor;
import org.onap.policy.common.capabilities.Startable;
import org.onap.policy.common.endpoints.http.client.HttpClient;
import org.onap.policy.drools.system.PolicyController;

/**
 * Healthcheck.
 */
public interface HealthCheck extends Startable {

    /**
     * Healthcheck Report.
     */
    @Data
    @NoArgsConstructor
    class Report {
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
        private long code;

        /**
         * start time.
         */
        private long startTime = System.currentTimeMillis();

        /**
         * end time.
         */
        private long endTime;

        /**
         * elapsed time.
         */
        private long elapsedTime;

        /**
         * Message from remote entity.
         */
        private String message;


        /**
         * Create a report.
         *
         * @param report the report to create
         */
        public Report(Report report) {
            this.startTime = report.startTime;
            this.code = report.code;
            this.elapsedTime = report.elapsedTime;
            this.endTime = report.endTime;
            this.healthy = report.healthy;
            this.message = report.message;
            this.name = report.name;
            this.url = report.url;
        }

        /**
         * Set the end time on the report as now.
         *
         * @return the report
         */
        public Report setEndTime() {
            setEndTime(System.currentTimeMillis());
            setElapsedTime(endTime - startTime);
            return this;
        }
    }

    /**
     * Report aggregation.
     */
    @Data
    class Reports {
        private boolean healthy;
        private final long startTime = System.currentTimeMillis();
        private long endTime;
        private long elapsedTime;
        private List<Report> details = new ArrayList<>();

        /**
         * Set the end time on the report as now.
         *
         * @return the report
         */
        public Reports setEndTime() {
            this.endTime = System.currentTimeMillis();
            this.elapsedTime = this.endTime - this.startTime;
            return this;
        }
    }

    /**
     * Process engine open status.
     */
    void open();

    /**
     *  System healthcheck.
     */
    Reports healthCheck();

    /**
     * Engine only healthcheck.
     */
    Reports engineHealthcheck();

    /**
     * Controllers only healthcheck.
     */
    Reports controllerHealthcheck();

    /**
     * Healthcheck on a controller.
     */
    Reports controllerHealthcheck(PolicyController controller);

    /**
     * HTTP Clients only healthcheck.
     */
    Reports clientHealthcheck();

    /**
     * Healthcheck on an HTTP Client.
     */
    Reports clientHealthcheck(HttpClient client);

}
