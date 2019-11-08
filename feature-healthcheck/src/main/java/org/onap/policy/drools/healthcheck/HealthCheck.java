/*
 * ============LICENSE_START=======================================================
 * feature-healthcheck
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

package org.onap.policy.drools.healthcheck;

import java.util.ArrayList;
import java.util.List;
import org.onap.policy.common.capabilities.Startable;

/**
 * Healthcheck.
 */
public interface HealthCheck extends Startable {

    /**
     * Healthcheck Report.
     */
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
        private int code;

        /**
         * Message from remote entity.
         */
        private String message;

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("Report [name=");
            builder.append(getName());
            builder.append(", url=");
            builder.append(getUrl());
            builder.append(", healthy=");
            builder.append(isHealthy());
            builder.append(", code=");
            builder.append(getCode());
            builder.append(", message=");
            builder.append(getMessage());
            builder.append("]");
            return builder.toString();
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public boolean isHealthy() {
            return healthy;
        }

        public void setHealthy(boolean healthy) {
            this.healthy = healthy;
        }

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    /**
     * Report aggregation.
     */
    class Reports {
        private boolean healthy;
        private List<Report> details = new ArrayList<>();

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("Reports [healthy=");
            builder.append(isHealthy());
            builder.append(", details=");
            builder.append(getDetails());
            builder.append("]");
            return builder.toString();
        }

        public boolean isHealthy() {
            return healthy;
        }

        public void setHealthy(boolean healthy) {
            this.healthy = healthy;
        }

        public List<Report> getDetails() {
            return details;
        }

        public void setDetails(List<Report> details) {
            this.details = details;
        }
    }

    /**
     * Perform a healthcheck.
     *
     * @return a report
     */
    Reports healthCheck();
}
