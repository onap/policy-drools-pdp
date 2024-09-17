/*
 * ============LICENSE_START=======================================================
 * Copyright (C) 2021 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2024 Nordix Foundation.
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
 *
 * SPDX-License-Identifier: Apache-2.0
 * ============LICENSE_END=========================================================
 */

package org.onap.policy.drools.metrics;


import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.openpojo.reflection.PojoClass;
import com.openpojo.reflection.impl.PojoClassFactory;
import com.openpojo.validation.Validator;
import com.openpojo.validation.ValidatorBuilder;
import com.openpojo.validation.rule.impl.GetterMustExistRule;
import com.openpojo.validation.rule.impl.SetterMustExistRule;
import com.openpojo.validation.test.impl.GetterTester;
import com.openpojo.validation.test.impl.SetterTester;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;

class MetricTest {

    @Test
    void testPojo() {
        PojoClass metric = PojoClassFactory.getPojoClass(Metric.class);
        Validator val = ValidatorBuilder
            .create()
            .with(new SetterMustExistRule())
            .with(new GetterMustExistRule())
            .with(new SetterTester())
            .with(new GetterTester())
            .build();
        val.validate(metric);
    }

    @Test
    void testEndTimeSetter() {
        Metric metric = new Metric();

        assertNull(metric.getEndTime());
        metric.setEndTime(null);
        assertNotNull(metric.getEndTime());
    }

    @Test
    void testStartTimeSetter() {
        Metric metric = new Metric();

        assertNull(metric.getStartTime());
        metric.setStartTime(null);
        assertNotNull(metric.getStartTime());
    }

    @Test
    void testElapsedTimeSetter() {
        Metric metric = new Metric();

        assertNull(metric.getElapsedTime());
        metric.setElapsedTime(null);
        assertNull(metric.getElapsedTime());

        Instant start = Instant.now();
        metric.setStartTime(start);
        metric.setElapsedTime(null);
        assertNull(metric.getElapsedTime());

        Instant end = Instant.now();
        metric.setEndTime(end);
        metric.setElapsedTime(null);
        assertNotNull(metric.getElapsedTime());
        Long duration = Duration.between(start, end).toMillis();
        assertEquals(duration, metric.getElapsedTime());
    }

    @Test
    void testInvocationIdSetter() {
        Metric metric = new Metric();

        assertNull(metric.getInvocationId());
        metric.setInvocationId(null);
        assertNotNull(metric.getInvocationId());
    }

    @Test
    void testServiceNameSetter() {
        Metric metric = new Metric();

        assertNull(metric.getServiceName());
        metric.setServiceName(null);
        assertEquals(Metric.HOST_TYPE, metric.getServiceName());
    }

    @Test
    void testInstanceUuidSetter() {
        Metric metric = new Metric();

        assertNull(metric.getInstanceUuid());
        metric.setInstanceUuid(null);
        assertNotNull(metric.getInstanceUuid());
    }

    @Test
    void testRequestIdSetter() {
        Metric metric = new Metric();

        assertNull(metric.getRequestId());
        metric.setRequestId(null);
        assertNotNull(metric.getRequestId());
    }

    @Test
    void testPartnerSetter() {
        Metric metric = new Metric();

        assertNull(metric.getPartner());
        metric.setPartner(null);
        assertEquals(Metric.HOST_TYPE, metric.getPartner());
    }

    @Test
    void testServerNameSetter() {
        Metric metric = new Metric();

        assertNull(metric.getServerName());
        metric.setServerName(null);
        assertEquals(Metric.HOSTNAME, metric.getServerName());
    }

    @Test
    void testServerFqdnSetter() {
        Metric metric = new Metric();

        assertNull(metric.getServerFqdn());
        metric.setServerFqdn(null);
        assertEquals(Metric.HOSTNAME, metric.getServerFqdn());
    }

    @Test
    void testVirtualServerNameSetter() {
        Metric metric = new Metric();

        assertNull(metric.getVirtualServerName());
        metric.setVirtualServerName(null);
        assertEquals(Metric.HOSTNAME, metric.getVirtualServerName());
    }

    @Test
    void testEqualsToString() {
        Metric metric1 = new Metric();
        Metric metric2 = new Metric();

        assertEquals(metric1, metric2);

        metric1.setRequestId(null);
        assertNotEquals(metric1, metric2);

        metric1.setRequestId("a");
        metric2.setRequestId("a");
        assertEquals(metric1, metric2);

        assertThat(metric1.toString()).startsWith("Metric");
    }

    @Test
    void testToTimestamp() {
        Instant now = Instant.now();
        assertEquals(new SimpleDateFormat(Metric.DATE_FORMAT).format(Date.from(now)), Metric.toTimestamp(now));
    }

    @Test
    void testElapsedTime_EndTimeStartTimeNullValues() {
        // test seems unnecessary, but when setElapsedTime receives null,
        // the method tries to calculate elapsed time between start and end time,
        // which only having the values was covered.
        Metric metric = new Metric();

        metric.setElapsedTime(null);
        assertNull(metric.getElapsedTime());

        metric.setEndTime(Instant.now());
        metric.setElapsedTime(null);
        assertNull(metric.getElapsedTime());

        metric = new Metric();
        metric.setStartTime(Instant.now());
        metric.setElapsedTime(null);
        assertNull(metric.getElapsedTime());
    }
}