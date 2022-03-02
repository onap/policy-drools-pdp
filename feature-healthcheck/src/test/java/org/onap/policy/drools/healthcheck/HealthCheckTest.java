/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018, 2021-2022 AT&T Intellectual Property. All rights reserved.
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.onap.policy.drools.healthcheck.HealthCheck.Report;
import org.onap.policy.drools.healthcheck.HealthCheck.Reports;

public class HealthCheckTest {
    private static final long RPT_CODE = 100;
    private static final String RPT_MSG = "report-message";
    private static final String RPT_NAME = "report-name";
    private static final String RPT_URL = "report-url";

    @Test
    public void testReport() {
        Report rpt = new Report();

        assertNotNull(rpt.toString());

        rpt.setCode(RPT_CODE);
        rpt.setHealthy(true);
        rpt.setMessage(RPT_MSG);
        rpt.setName(RPT_NAME);
        rpt.setUrl(RPT_URL);
        rpt.setEndTime();

        assertEquals(RPT_CODE, rpt.getCode());
        assertTrue(rpt.isHealthy());
        assertEquals(RPT_MSG, rpt.getMessage());
        assertEquals(RPT_NAME, rpt.getName());
        assertEquals(RPT_URL, rpt.getUrl());

        assertNotEquals(0L, rpt.getStartTime());
        assertNotEquals(0L, rpt.getEndTime());
        assertEquals(rpt.getEndTime() - rpt.getStartTime(), rpt.getElapsedTime());

        // flip the flag
        rpt.setHealthy(false);
        assertFalse(rpt.isHealthy());

        // toString should work with populated data
        assertNotNull(rpt.toString());

        assertEquals(rpt, new Report(rpt));
    }

    @Test
    public void testReports() {
        Reports reports = new Reports();

        // toString should work with un-populated data
        assertNotNull(reports.toString());

        List<Report> lst = Collections.emptyList();
        reports.setDetails(lst);
        reports.setHealthy(true);

        assertSame(lst, reports.getDetails());
        assertTrue(reports.isHealthy());

        // flip the flag
        reports.setHealthy(false);
        assertFalse(reports.isHealthy());

        // toString should work with populated data
        assertNotNull(reports.toString());

        assertNotEquals(0L, reports.getStartTime());

        reports.setEndTime();
        assertNotEquals(0L, reports.getEndTime());
        assertEquals(reports.getEndTime() - reports.getStartTime(), reports.getElapsedTime());
    }
}
