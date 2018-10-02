/*
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.onap.policy.drools.healthcheck.HealthCheck.Report;
import org.onap.policy.drools.healthcheck.HealthCheck.Reports;

public class HealthCheckTest {

    private static final int RPT_CODE = 100;
    private static final String RPT_MSG = "report-message";
    private static final String RPT_NAME = "report-name";
    private static final String RPT_URL = "report-url";

    @Test
    public void testHealthCheck_Report() {
        Report rpt = new Report();

        // toString should work with un-populated data
        assertNotNull(rpt.toString());

        rpt.setCode(RPT_CODE);
        rpt.setHealthy(true);
        rpt.setMessage(RPT_MSG);
        rpt.setName(RPT_NAME);
        rpt.setUrl(RPT_URL);

        assertEquals(RPT_CODE, rpt.getCode());
        assertEquals(true, rpt.isHealthy());
        assertEquals(RPT_MSG, rpt.getMessage());
        assertEquals(RPT_NAME, rpt.getName());
        assertEquals(RPT_URL, rpt.getUrl());

        // flip the flag
        rpt.setHealthy(false);
        assertEquals(false, rpt.isHealthy());

        // toString should work with populated data
        assertNotNull(rpt.toString());
    }

    @Test
    public void testHealthCheck_Reports() {
        Reports reports = new Reports();

        // toString should work with un-populated data
        assertNotNull(reports.toString());

        List<Report> lst = Collections.emptyList();
        reports.setDetails(lst);
        reports.setHealthy(true);

        assertTrue(lst == reports.getDetails());
        assertEquals(true, reports.isHealthy());

        // flip the flag
        reports.setHealthy(false);
        assertEquals(false, reports.isHealthy());

        // toString should work with populated data
        assertNotNull(reports.toString());
    }

}
