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

        assertNotNull(MDC.get(MdcTransactionConstants.REQUEST_ID));
        assertNotNull(MDC.get(MdcTransactionConstants.PARTNER_NAME));
        assertNotNull(MDC.get(MdcTransactionConstants.VIRTUAL_SERVER_NAME));
        assertNotNull(MDC.get(MdcTransactionConstants.SERVER));
        assertNotNull(MDC.get(MdcTransactionConstants.SERVER_IP_ADDRESS));
        assertNotNull(MDC.get(MdcTransactionConstants.SERVER_FQDN));
        assertNotNull(MDC.get(MdcTransactionConstants.SERVICE_NAME));

        assertNull(MDC.get(MdcTransactionConstants.INVOCATION_ID));
        assertNull(MDC.get(MdcTransactionConstants.BEGIN_TIMESTAMP));
        assertNull(MDC.get(MdcTransactionConstants.END_TIMESTAMP));
        assertNull(MDC.get(MdcTransactionConstants.ELAPSED_TIME));
        assertNull(MDC.get(MdcTransactionConstants.SERVICE_INSTANCE_ID));
        assertNull(MDC.get(MdcTransactionConstants.INSTANCE_UUID));
        assertNull(MDC.get(MdcTransactionConstants.PROCESS_KEY));
        assertNull(MDC.get(MdcTransactionConstants.STATUS_CODE));
        assertNull(MDC.get(MdcTransactionConstants.RESPONSE_CODE));
        assertNull(MDC.get(MdcTransactionConstants.RESPONSE_DESCRIPTION));
        assertNull(MDC.get(MdcTransactionConstants.SEVERITY));
        assertNull(MDC.get(MdcTransactionConstants.TARGET_ENTITY));
        assertNull(MDC.get(MdcTransactionConstants.TARGET_SERVICE_NAME));
        assertNull(MDC.get(MdcTransactionConstants.TARGET_VIRTUAL_ENTITY));
        assertNull(MDC.get(MdcTransactionConstants.CLIENT_IP_ADDRESS));
        assertNull(MDC.get(MdcTransactionConstants.REMOTE_HOST));

        assertEquals(trans.getRequestId(), MDC.get(MdcTransactionConstants.REQUEST_ID));
        assertEquals(trans.getPartner(), MDC.get(MdcTransactionConstants.PARTNER_NAME));
        assertEquals(trans.getVirtualServerName(), MDC.get(MdcTransactionConstants.VIRTUAL_SERVER_NAME));
        assertEquals(trans.getServer(), MDC.get(MdcTransactionConstants.SERVER));
        assertEquals(trans.getServerIpAddress(), MDC.get(MdcTransactionConstants.SERVER_IP_ADDRESS));
        assertEquals(trans.getServerFqdn(), MDC.get(MdcTransactionConstants.SERVER_FQDN));
        assertEquals(trans.getServiceName(), MDC.get(MdcTransactionConstants.SERVICE_NAME));
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

        assertNotNull(MDC.get(MdcTransactionConstants.INVOCATION_ID));
        assertNotNull(MDC.get(MdcTransactionConstants.BEGIN_TIMESTAMP));
        assertNotNull(MDC.get(MdcTransactionConstants.END_TIMESTAMP));
        assertNotNull(MDC.get(MdcTransactionConstants.ELAPSED_TIME));
        assertNotNull(MDC.get(MdcTransactionConstants.SERVICE_INSTANCE_ID));
        assertNotNull(MDC.get(MdcTransactionConstants.INSTANCE_UUID));
        assertNotNull(MDC.get(MdcTransactionConstants.PROCESS_KEY));
        assertNotNull(MDC.get(MdcTransactionConstants.STATUS_CODE));
        assertNotNull(MDC.get(MdcTransactionConstants.RESPONSE_CODE));
        assertNotNull(MDC.get(MdcTransactionConstants.RESPONSE_DESCRIPTION));
        assertNotNull(MDC.get(MdcTransactionConstants.SEVERITY));
        assertNotNull(MDC.get(MdcTransactionConstants.TARGET_ENTITY));
        assertNotNull(MDC.get(MdcTransactionConstants.TARGET_SERVICE_NAME));
        assertNotNull(MDC.get(MdcTransactionConstants.TARGET_VIRTUAL_ENTITY));
        assertNotNull(MDC.get(MdcTransactionConstants.CLIENT_IP_ADDRESS));
        assertNotNull(MDC.get(MdcTransactionConstants.REMOTE_HOST));

        assertEquals(trans.getInvocationId(), MDC.get(MdcTransactionConstants.INVOCATION_ID));
        assertEquals(trans.timestamp(trans.getStartTime()), MDC.get(MdcTransactionConstants.BEGIN_TIMESTAMP));
        assertEquals(trans.timestamp(trans.getEndTime()), MDC.get(MdcTransactionConstants.END_TIMESTAMP));
        assertNotEquals(trans.getElapsedTime(), MDC.get(MdcTransactionConstants.ELAPSED_TIME));
        assertEquals(String.valueOf(Duration.between(trans.getStartTime(), trans.getEndTime()).toMillis()),
            MDC.get(MdcTransactionConstants.ELAPSED_TIME));
        assertEquals(trans.getServiceInstanceId(), MDC.get(MdcTransactionConstants.SERVICE_INSTANCE_ID));
        assertEquals(trans.getInstanceUuid(), MDC.get(MdcTransactionConstants.INSTANCE_UUID));
        assertEquals(trans.getProcessKey(), MDC.get(MdcTransactionConstants.PROCESS_KEY));
        assertEquals(trans.getStatusCode(), MDC.get(MdcTransactionConstants.STATUS_CODE));
        assertEquals(trans.getResponseCode(), MDC.get(MdcTransactionConstants.RESPONSE_CODE));
        assertEquals(trans.getResponseDescription(), MDC.get(MdcTransactionConstants.RESPONSE_DESCRIPTION));
        assertEquals(trans.getSeverity(), MDC.get(MdcTransactionConstants.SEVERITY));
        assertEquals(trans.getTargetEntity(), MDC.get(MdcTransactionConstants.TARGET_ENTITY));
        assertEquals(trans.getTargetServiceName(), MDC.get(MdcTransactionConstants.TARGET_SERVICE_NAME));
        assertEquals(trans.getTargetVirtualEntity(), MDC.get(MdcTransactionConstants.TARGET_VIRTUAL_ENTITY));
        assertEquals(trans.getClientIpAddress(), MDC.get(MdcTransactionConstants.CLIENT_IP_ADDRESS));
        assertEquals(trans.getRemoteHost(), MDC.get(MdcTransactionConstants.REMOTE_HOST));

        assertEquals("service-instance-id", trans.getServiceInstanceId());
        assertEquals("process-key", trans.getProcessKey());
        assertEquals("status-code", trans.getStatusCode());
        assertEquals("response-code", trans.getResponseCode());
        assertEquals("response-description", trans.getResponseDescription());
        assertEquals("severity", trans.getSeverity());
        assertEquals("target-entity", trans.getTargetEntity());
        assertEquals("target-service-name", trans.getTargetServiceName());
        assertEquals("target-virtual-entity", trans.getTargetVirtualEntity());
        assertEquals("client-ip-address", trans.getClientIpAddress());
        assertEquals("remote-host", trans.getRemoteHost());
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
