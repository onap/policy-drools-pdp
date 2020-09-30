/*-
 * ============LICENSE_START=======================================================
 * policy-utils
 * ================================================================================
 * Copyright (C) 2018-2020 AT&T Intellectual Property. All rights reserved.
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
 */

package org.onap.policy.drools.utils.logging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.time.Duration;
import java.time.Instant;
import org.junit.Test;
import org.slf4j.MDC;

public class MdcTransactionTest {

    @Test
    public void testResetSubTransaction() {
        MdcTransaction trans =
            MdcTransaction.newTransaction(null, null).resetSubTransaction();

        assertNotNull(trans.getRequestId());
        assertNotNull(trans.getPartner());
        assertNotNull(trans.getServiceName());
        assertNotNull(trans.getServer());
        assertNotNull(trans.getServerIpAddress());
        assertNotNull(trans.getServerFqdn());
        assertNotNull(trans.getVirtualServerName());
        assertNotNull(trans.getStartTime());

        assertNullSubTransactionFields(trans);

        assertNotNullKeys(
            MdcTransactionConstants.REQUEST_ID,
            MdcTransactionConstants.PARTNER_NAME,
            MdcTransactionConstants.VIRTUAL_SERVER_NAME,
            MdcTransactionConstants.SERVER,
            MdcTransactionConstants.SERVER_IP_ADDRESS,
            MdcTransactionConstants.SERVER_FQDN,
            MdcTransactionConstants.SERVICE_NAME
        );


        assertNullKeys(
            MdcTransactionConstants.INVOCATION_ID,
            MdcTransactionConstants.BEGIN_TIMESTAMP,
            MdcTransactionConstants.END_TIMESTAMP,
            MdcTransactionConstants.ELAPSED_TIME,
            MdcTransactionConstants.SERVICE_INSTANCE_ID,
            MdcTransactionConstants.INSTANCE_UUID,
            MdcTransactionConstants.PROCESS_KEY,
            MdcTransactionConstants.STATUS_CODE,
            MdcTransactionConstants.RESPONSE_CODE,
            MdcTransactionConstants.RESPONSE_DESCRIPTION,
            MdcTransactionConstants.SEVERITY,
            MdcTransactionConstants.TARGET_ENTITY,
            MdcTransactionConstants.TARGET_SERVICE_NAME,
            MdcTransactionConstants.TARGET_VIRTUAL_ENTITY,
            MdcTransactionConstants.CLIENT_IP_ADDRESS,
            MdcTransactionConstants.REMOTE_HOST
        );

        assertTransactionFields(trans);
    }

    private void assertNotNullKeys(String... notNullKeys) {
        for (String key: notNullKeys) {
            assertNotNull(key, MDC.get(key));
        }
    }

    private void assertNullKeys(String... nullKeys) {
        for (String key: nullKeys) {
            assertNull(key, MDC.get(key));
        }
    }

    private void assertNullSubTransactionFields(MdcTransaction trans) {
        assertNull(trans.getInvocationId());
        assertNullSubTransactionFieldsButInvocationId(trans);
    }

    private void assertNullSubTransactionFieldsButInvocationId(MdcTransaction trans) {
        assertNull(trans.getEndTime());
        assertNull(trans.getElapsedTime());
        assertNull(trans.getServiceInstanceId());
        assertNull(trans.getStatusCode());
        assertNull(trans.getResponseCode());
        assertNull(trans.getResponseDescription());
        assertNull(trans.getInstanceUuid());
        assertNull(trans.getTargetEntity());
        assertNull(trans.getTargetServiceName());
        assertNull(trans.getProcessKey());
        assertNull(trans.getClientIpAddress());
        assertNull(trans.getRemoteHost());
        assertNull(trans.getSeverity());
        assertNull(trans.getTargetVirtualEntity());
    }

    protected void assertTransactionFields(MdcTransaction trans) {
        assertEquals(trans.getRequestId(), MDC.get(MdcTransactionConstants.REQUEST_ID));
        assertEquals(trans.getPartner(), MDC.get(MdcTransactionConstants.PARTNER_NAME));
        assertEquals(trans.getVirtualServerName(), MDC.get(MdcTransactionConstants.VIRTUAL_SERVER_NAME));
        assertEquals(trans.getServer(), MDC.get(MdcTransactionConstants.SERVER));
        assertEquals(trans.getServerIpAddress(), MDC.get(MdcTransactionConstants.SERVER_IP_ADDRESS));
        assertEquals(trans.getServerFqdn(), MDC.get(MdcTransactionConstants.SERVER_FQDN));
        assertEquals(trans.getServiceName(), MDC.get(MdcTransactionConstants.SERVICE_NAME));

    }

    @Test
    public void testFlush() {
        MdcTransaction trans =
                        MdcTransaction.newTransaction()
                .setRequestId(null)
                .setInvocationId(null)
                .setPartner(null)
                .setVirtualServerName(null)
                .setServer(null)
                .setServerIpAddress(null)
                .setServerFqdn(null)
                .setServiceName(null)
                .setStartTime(null)
                .setEndTime(null)
                .setServiceInstanceId("service-instance-id")
                .setInstanceUuid(null)
                .setProcessKey("process-key")
                .setStatusCode("status-code")
                .setResponseCode("response-code")
                .setResponseDescription("response-description")
                .setSeverity("severity")
                .setTargetEntity("target-entity")
                .setTargetServiceName("target-service-name")
                .setTargetVirtualEntity("target-virtual-entity")
                .setClientIpAddress("client-ip-address")
                .setRemoteHost("remote-host")
                .flush();

        assertTransactionFields(trans);

        assertNotNullKeys(
            MdcTransactionConstants.INVOCATION_ID,
            MdcTransactionConstants.BEGIN_TIMESTAMP,
            MdcTransactionConstants.END_TIMESTAMP,
            MdcTransactionConstants.ELAPSED_TIME,
            MdcTransactionConstants.SERVICE_INSTANCE_ID,
            MdcTransactionConstants.INSTANCE_UUID,
            MdcTransactionConstants.PROCESS_KEY,
            MdcTransactionConstants.STATUS_CODE,
            MdcTransactionConstants.RESPONSE_CODE,
            MdcTransactionConstants.RESPONSE_DESCRIPTION,
            MdcTransactionConstants.SEVERITY,
            MdcTransactionConstants.TARGET_ENTITY,
            MdcTransactionConstants.TARGET_SERVICE_NAME,
            MdcTransactionConstants.TARGET_VIRTUAL_ENTITY,
            MdcTransactionConstants.CLIENT_IP_ADDRESS,
            MdcTransactionConstants.REMOTE_HOST);

        assertEquals(trans.getInvocationId(), MDC.get(MdcTransactionConstants.INVOCATION_ID));
        assertEquals(trans.timestamp(trans.getStartTime()), MDC.get(MdcTransactionConstants.BEGIN_TIMESTAMP));
        assertEquals(trans.timestamp(trans.getEndTime()), MDC.get(MdcTransactionConstants.END_TIMESTAMP));
        assertEquals(String.valueOf(Duration.between(trans.getStartTime(), trans.getEndTime()).toMillis()),
            MDC.get(MdcTransactionConstants.ELAPSED_TIME));
        assertEquals(trans.getInstanceUuid(), MDC.get(MdcTransactionConstants.INSTANCE_UUID));

        assertKeyEquals("service-instance-id", trans.getServiceInstanceId(),
                        MdcTransactionConstants.SERVICE_INSTANCE_ID);
        assertKeyEquals("process-key", trans.getProcessKey(), MdcTransactionConstants.PROCESS_KEY);
        assertKeyEquals("status-code", trans.getStatusCode(), MdcTransactionConstants.STATUS_CODE);
        assertKeyEquals("response-code", trans.getResponseCode(), MdcTransactionConstants.RESPONSE_CODE);
        assertKeyEquals("response-description", trans.getResponseDescription(),
                        MdcTransactionConstants.RESPONSE_DESCRIPTION);
        assertKeyEquals("severity", trans.getSeverity(), MdcTransactionConstants.SEVERITY);
        assertKeyEquals("target-entity", trans.getTargetEntity(), MdcTransactionConstants.TARGET_ENTITY);
        assertKeyEquals("target-service-name", trans.getTargetServiceName(),
                        MdcTransactionConstants.TARGET_SERVICE_NAME);
        assertKeyEquals("target-virtual-entity", trans.getTargetVirtualEntity(),
                        MdcTransactionConstants.TARGET_VIRTUAL_ENTITY);
        assertKeyEquals("client-ip-address", trans.getClientIpAddress(), MdcTransactionConstants.CLIENT_IP_ADDRESS);
        assertKeyEquals("remote-host", trans.getRemoteHost(), MdcTransactionConstants.REMOTE_HOST);
    }

    private void assertKeyEquals(String expected, String transValue, String mdcKey) {
        assertEquals("trans." + expected, expected, transValue);
        assertEquals("mdc." + expected, expected, MDC.get(mdcKey));
    }

    @Test
    public void testMetric() {
        MdcTransaction trans =
            MdcTransaction.newTransaction(null, null).metric();

        assertTransactionFields(trans);
    }

    @Test
    public void testTransaction() {
        MdcTransaction trans =
            MdcTransaction.newTransaction(null, null).transaction();

        assertTransactionFields(trans);
    }

    @Test
    public void testSubTransaction() {
        MdcTransaction trans =
            MdcTransaction.newTransaction(null, "partner");

        MdcTransaction subTrans = MdcTransaction.newSubTransaction(null);

        assertTransactionFields(trans);
        assertTransactionFields(subTrans);

        assertEquals(trans.getRequestId(), trans.getRequestId());
        assertEquals(trans.getPartner(), trans.getPartner());
        assertEquals(trans.getVirtualServerName(), trans.getVirtualServerName());
        assertEquals(trans.getServer(), trans.getServer());
        assertEquals(trans.getServerIpAddress(), trans.getServerIpAddress());
        assertEquals(trans.getServerFqdn(), trans.getServerFqdn());
        assertEquals(trans.getServiceName(), trans.getServiceName());

        assertNotEquals(trans.getInvocationId(), subTrans.getInvocationId());
        assertNull(trans.getInvocationId());
        assertNotNull(subTrans.getInvocationId());

        assertNotNull(subTrans.getStartTime());
        assertNullSubTransactionFieldsButInvocationId(trans);

        subTrans.setServiceInstanceId("service-instance-id")
            .setInstanceUuid(null)
            .setProcessKey("process-key")
            .setStatusCode("status-code")
            .setResponseCode("response-code")
            .setResponseDescription("response-description")
            .setSeverity("severity")
            .setTargetEntity("target-entity")
            .setTargetServiceName("target-service-name")
            .setTargetVirtualEntity("target-virtual-entity")
            .setClientIpAddress("client-ip-address")
            .setRemoteHost("remote-host")
            .setEndTime(Instant.now());

        subTrans.setStatusCode(false).setResponseCode("400");

        MdcTransaction subTrans2 = MdcTransaction.fromTransaction(subTrans);

        assertEquals(subTrans.toString(), subTrans2.toString());

        subTrans.metric();
        subTrans2.setStatusCode("202").setProcessKey("junit").metric();

        trans.resetSubTransaction().setStatusCode(true).setResponseCode("200").metric();
    }

}
