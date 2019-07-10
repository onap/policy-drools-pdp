/*-
 * ============LICENSE_START=======================================================
 * policy-utils
 * ================================================================================
 * Copyright (C) 2018-2019 AT&T Intellectual Property. All rights reserved.
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
    public void resetSubTransaction() {
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

        assertNotNull(MDC.get(MdcTransaction.REQUEST_ID));
        assertNotNull(MDC.get(MdcTransaction.PARTNER_NAME));
        assertNotNull(MDC.get(MdcTransaction.VIRTUAL_SERVER_NAME));
        assertNotNull(MDC.get(MdcTransaction.SERVER));
        assertNotNull(MDC.get(MdcTransaction.SERVER_IP_ADDRESS));
        assertNotNull(MDC.get(MdcTransaction.SERVER_FQDN));
        assertNotNull(MDC.get(MdcTransaction.SERVICE_NAME));

        assertNull(MDC.get(MdcTransaction.INVOCATION_ID));
        assertNull(MDC.get(MdcTransaction.BEGIN_TIMESTAMP));
        assertNull(MDC.get(MdcTransaction.END_TIMESTAMP));
        assertNull(MDC.get(MdcTransaction.ELAPSED_TIME));
        assertNull(MDC.get(MdcTransaction.SERVICE_INSTANCE_ID));
        assertNull(MDC.get(MdcTransaction.INSTANCE_UUID));
        assertNull(MDC.get(MdcTransaction.PROCESS_KEY));
        assertNull(MDC.get(MdcTransaction.STATUS_CODE));
        assertNull(MDC.get(MdcTransaction.RESPONSE_CODE));
        assertNull(MDC.get(MdcTransaction.RESPONSE_DESCRIPTION));
        assertNull(MDC.get(MdcTransaction.SEVERITY));
        assertNull(MDC.get(MdcTransaction.ALERT_SEVERITY));
        assertNull(MDC.get(MdcTransaction.TARGET_ENTITY));
        assertNull(MDC.get(MdcTransaction.TARGET_SERVICE_NAME));
        assertNull(MDC.get(MdcTransaction.TARGET_VIRTUAL_ENTITY));
        assertNull(MDC.get(MdcTransaction.CLIENT_IP_ADDRESS));
        assertNull(MDC.get(MdcTransaction.REMOTE_HOST));

        assertEquals(trans.getRequestId(), MDC.get(MdcTransaction.REQUEST_ID));
        assertEquals(trans.getPartner(), MDC.get(MdcTransaction.PARTNER_NAME));
        assertEquals(trans.getVirtualServerName(), MDC.get(MdcTransaction.VIRTUAL_SERVER_NAME));
        assertEquals(trans.getServer(), MDC.get(MdcTransaction.SERVER));
        assertEquals(trans.getServerIpAddress(), MDC.get(MdcTransaction.SERVER_IP_ADDRESS));
        assertEquals(trans.getServerFqdn(), MDC.get(MdcTransaction.SERVER_FQDN));
        assertEquals(trans.getServiceName(), MDC.get(MdcTransaction.SERVICE_NAME));
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
        assertNull(trans.getAlertSeverity());
        assertNull(trans.getTargetVirtualEntity());
    }

    protected void assertTransactionFields(MdcTransaction trans) {
        assertEquals(trans.getRequestId(), MDC.get(MdcTransaction.REQUEST_ID));
        assertEquals(trans.getPartner(), MDC.get(MdcTransaction.PARTNER_NAME));
        assertEquals(trans.getVirtualServerName(), MDC.get(MdcTransaction.VIRTUAL_SERVER_NAME));
        assertEquals(trans.getServer(), MDC.get(MdcTransaction.SERVER));
        assertEquals(trans.getServerIpAddress(), MDC.get(MdcTransaction.SERVER_IP_ADDRESS));
        assertEquals(trans.getServerFqdn(), MDC.get(MdcTransaction.SERVER_FQDN));
        assertEquals(trans.getServiceName(), MDC.get(MdcTransaction.SERVICE_NAME));

    }

    @Test
    public void flush() {
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
                .setAlertSeverity("alert-severity")
                .setTargetEntity("target-entity")
                .setTargetServiceName("target-service-name")
                .setTargetVirtualEntity("target-virtual-entity")
                .setClientIpAddress("client-ip-address")
                .setRemoteHost("remote-host")
                .flush();

        assertTransactionFields(trans);

        assertNotNull(MDC.get(MdcTransaction.INVOCATION_ID));
        assertNotNull(MDC.get(MdcTransaction.BEGIN_TIMESTAMP));
        assertNotNull(MDC.get(MdcTransaction.END_TIMESTAMP));
        assertNotNull(MDC.get(MdcTransaction.ELAPSED_TIME));
        assertNotNull(MDC.get(MdcTransaction.SERVICE_INSTANCE_ID));
        assertNotNull(MDC.get(MdcTransaction.INSTANCE_UUID));
        assertNotNull(MDC.get(MdcTransaction.PROCESS_KEY));
        assertNotNull(MDC.get(MdcTransaction.STATUS_CODE));
        assertNotNull(MDC.get(MdcTransaction.RESPONSE_CODE));
        assertNotNull(MDC.get(MdcTransaction.RESPONSE_DESCRIPTION));
        assertNotNull(MDC.get(MdcTransaction.SEVERITY));
        assertNotNull(MDC.get(MdcTransaction.ALERT_SEVERITY));
        assertNotNull(MDC.get(MdcTransaction.TARGET_ENTITY));
        assertNotNull(MDC.get(MdcTransaction.TARGET_SERVICE_NAME));
        assertNotNull(MDC.get(MdcTransaction.TARGET_VIRTUAL_ENTITY));
        assertNotNull(MDC.get(MdcTransaction.CLIENT_IP_ADDRESS));
        assertNotNull(MDC.get(MdcTransaction.REMOTE_HOST));

        assertEquals(trans.getInvocationId(), MDC.get(MdcTransaction.INVOCATION_ID));
        assertEquals(trans.timestamp(trans.getStartTime()), MDC.get(MdcTransaction.BEGIN_TIMESTAMP));
        assertEquals(trans.timestamp(trans.getEndTime()), MDC.get(MdcTransaction.END_TIMESTAMP));
        assertNotEquals(trans.getElapsedTime(), MDC.get(MdcTransaction.ELAPSED_TIME));
        assertEquals(String.valueOf(Duration.between(trans.getStartTime(), trans.getEndTime()).toMillis()),
            MDC.get(MdcTransaction.ELAPSED_TIME));
        assertEquals(trans.getServiceInstanceId(), MDC.get(MdcTransaction.SERVICE_INSTANCE_ID));
        assertEquals(trans.getInstanceUuid(), MDC.get(MdcTransaction.INSTANCE_UUID));
        assertEquals(trans.getProcessKey(),MDC.get(MdcTransaction.PROCESS_KEY));
        assertEquals(trans.getStatusCode(), MDC.get(MdcTransaction.STATUS_CODE));
        assertEquals(trans.getResponseCode(), MDC.get(MdcTransaction.RESPONSE_CODE));
        assertEquals(trans.getResponseDescription(), MDC.get(MdcTransaction.RESPONSE_DESCRIPTION));
        assertEquals(trans.getSeverity(), MDC.get(MdcTransaction.SEVERITY));
        assertEquals(trans.getAlertSeverity(), MDC.get(MdcTransaction.ALERT_SEVERITY));
        assertEquals(trans.getTargetEntity(), MDC.get(MdcTransaction.TARGET_ENTITY));
        assertEquals(trans.getTargetServiceName(), MDC.get(MdcTransaction.TARGET_SERVICE_NAME));
        assertEquals(trans.getTargetVirtualEntity(), MDC.get(MdcTransaction.TARGET_VIRTUAL_ENTITY));
        assertEquals(trans.getClientIpAddress(), MDC.get(MdcTransaction.CLIENT_IP_ADDRESS));
        assertEquals(trans.getRemoteHost(), MDC.get(MdcTransaction.REMOTE_HOST));

        assertEquals("service-instance-id", trans.getServiceInstanceId());
        assertEquals("process-key", trans.getProcessKey());
        assertEquals("status-code", trans.getStatusCode());
        assertEquals("response-code", trans.getResponseCode());
        assertEquals("response-description", trans.getResponseDescription());
        assertEquals("severity", trans.getSeverity());
        assertEquals("alert-severity", trans.getAlertSeverity());
        assertEquals("target-entity", trans.getTargetEntity());
        assertEquals("target-service-name", trans.getTargetServiceName());
        assertEquals("target-virtual-entity", trans.getTargetVirtualEntity());
        assertEquals("client-ip-address", trans.getClientIpAddress());
        assertEquals("remote-host", trans.getRemoteHost());
    }

    @Test
    public void metric() {
        MdcTransaction trans =
            MdcTransaction.newTransaction(null, null).metric();

        assertTransactionFields(trans);
    }

    @Test
    public void transaction() {
        MdcTransaction trans =
            MdcTransaction.newTransaction(null, null).transaction();

        assertTransactionFields(trans);
    }

    @Test
    public void subTransaction() {
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
            .setAlertSeverity("alert-severity")
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
