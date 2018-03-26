package org.onap.policy.drools.utils.logging;

import static org.junit.Assert.*;

import java.time.Duration;
import java.time.Instant;
import org.junit.Test;
import org.slf4j.MDC;

public class MDCTransactionTest {

    @Test
    public void resetSubTransaction() {
        MDCTransaction trans =
            MDCTransaction.newTransaction(null, null).resetSubTransaction();

        assertNotNull(trans.getRequestId());
        assertNotNull(trans.getPartner());
        assertNotNull(trans.getServiceName());
        assertNotNull(trans.getServer());
        assertNotNull(trans.getServerIpAddress());
        assertNotNull(trans.getServerFqdn());
        assertNotNull(trans.getVirtualServerName());
        assertNotNull(trans.getStartTime());

        assertNullSubTransactionFields(trans);

        assertNotNull(MDC.get(MDCTransaction.REQUEST_ID));
        assertNotNull(MDC.get(MDCTransaction.PARTNER_NAME));
        assertNotNull(MDC.get(MDCTransaction.VIRTUAL_SERVER_NAME));
        assertNotNull(MDC.get(MDCTransaction.SERVER));
        assertNotNull(MDC.get(MDCTransaction.SERVER_IP_ADDRESS));
        assertNotNull(MDC.get(MDCTransaction.SERVER_FQDN));
        assertNotNull(MDC.get(MDCTransaction.SERVICE_NAME));

        assertNull(MDC.get(MDCTransaction.INVOCATION_ID));
        assertNull(MDC.get(MDCTransaction.BEGIN_TIMESTAMP));
        assertNull(MDC.get(MDCTransaction.END_TIMESTAMP));
        assertNull(MDC.get(MDCTransaction.ELAPSED_TIME));
        assertNull(MDC.get(MDCTransaction.SERVICE_INSTANCE_ID));
        assertNull(MDC.get(MDCTransaction.INSTANCE_UUID));
        assertNull(MDC.get(MDCTransaction.PROCESS_KEY));
        assertNull(MDC.get(MDCTransaction.STATUS_CODE));
        assertNull(MDC.get(MDCTransaction.RESPONSE_CODE));
        assertNull(MDC.get(MDCTransaction.RESPONSE_DESCRIPTION));
        assertNull(MDC.get(MDCTransaction.SEVERITY));
        assertNull(MDC.get(MDCTransaction.ALERT_SEVERITY));
        assertNull(MDC.get(MDCTransaction.TARGET_ENTITY));
        assertNull(MDC.get(MDCTransaction.TARGET_SERVICE_NAME));
        assertNull(MDC.get(MDCTransaction.TARGET_VIRTUAL_ENTITY));
        assertNull(MDC.get(MDCTransaction.CLIENT_IP_ADDRESS));
        assertNull(MDC.get(MDCTransaction.REMOTE_HOST));

        assertEquals(trans.getRequestId(), MDC.get(MDCTransaction.REQUEST_ID));
        assertEquals(trans.getPartner(), MDC.get(MDCTransaction.PARTNER_NAME));
        assertEquals(trans.getVirtualServerName(), MDC.get(MDCTransaction.VIRTUAL_SERVER_NAME));
        assertEquals(trans.getServer(), MDC.get(MDCTransaction.SERVER));
        assertEquals(trans.getServerIpAddress(), MDC.get(MDCTransaction.SERVER_IP_ADDRESS));
        assertEquals(trans.getServerFqdn(), MDC.get(MDCTransaction.SERVER_FQDN));
        assertEquals(trans.getServiceName(), MDC.get(MDCTransaction.SERVICE_NAME));
    }

    private void assertNullSubTransactionFields(MDCTransaction trans) {
        assertNull(trans.getInvocationId());
        assertNullSubTransactionFieldsButInvocationId(trans);
    }

    private void assertNullSubTransactionFieldsButInvocationId(MDCTransaction trans) {
        assertNull(trans.getEndTime());
        assertNull(trans.getElapsedTime());
        assertNull(trans.getServiceInstanceId());
        assertNull(trans.getStatusCode());
        assertNull(trans.getResponseCode());
        assertNull(trans.getResponseDescription());
        assertNull(trans.getInstanceUUID());
        assertNull(trans.getTargetEntity());
        assertNull(trans.getTargetServiceName());
        assertNull(trans.getProcessKey());
        assertNull(trans.getClientIpAddress());
        assertNull(trans.getRemoteHost());
        assertNull(trans.getAlertSeverity());
        assertNull(trans.getTargetVirtualEntity());
    }

    protected void assertTransactionFields(MDCTransaction trans) {
        assertEquals(trans.getRequestId(), MDC.get(MDCTransaction.REQUEST_ID));
        assertEquals(trans.getPartner(), MDC.get(MDCTransaction.PARTNER_NAME));
        assertEquals(trans.getVirtualServerName(), MDC.get(MDCTransaction.VIRTUAL_SERVER_NAME));
        assertEquals(trans.getServer(), MDC.get(MDCTransaction.SERVER));
        assertEquals(trans.getServerIpAddress(), MDC.get(MDCTransaction.SERVER_IP_ADDRESS));
        assertEquals(trans.getServerFqdn(), MDC.get(MDCTransaction.SERVER_FQDN));
        assertEquals(trans.getServiceName(), MDC.get(MDCTransaction.SERVICE_NAME));

    }

    @Test
    public void flush() {
        MDCTransaction trans =
            MDCTransaction.newTransaction().
                setRequestId(null).
                setInvocationId(null).
                setPartner(null).
                setVirtualServerName(null).
                setServer(null).
                setServerIpAddress(null).
                setServerFqdn(null).
                setServiceName(null).
                setStartTime(null).
                setEndTime(null).
                setServiceInstanceId("service-instance-id").
                setInstanceUUID(null).
                setProcessKey("process-key").
                setStatusCode("status-code").
                setResponseCode("response-code").
                setResponseDescription("response-description").
                setSeverity("severity").
                setAlertSeverity("alert-severity").
                setTargetEntity("target-entity").
                setTargetServiceName("target-service-name").
                setTargetVirtualEntity("target-virtual-entity").
                setClientIpAddress("client-ip-address").
                setRemoteHost("remote-host").
                flush();

        assertTransactionFields(trans);

        assertNotNull(MDC.get(MDCTransaction.INVOCATION_ID));
        assertNotNull(MDC.get(MDCTransaction.BEGIN_TIMESTAMP));
        assertNotNull(MDC.get(MDCTransaction.END_TIMESTAMP));
        assertNotNull(MDC.get(MDCTransaction.ELAPSED_TIME));
        assertNotNull(MDC.get(MDCTransaction.SERVICE_INSTANCE_ID));
        assertNotNull(MDC.get(MDCTransaction.INSTANCE_UUID));
        assertNotNull(MDC.get(MDCTransaction.PROCESS_KEY));
        assertNotNull(MDC.get(MDCTransaction.STATUS_CODE));
        assertNotNull(MDC.get(MDCTransaction.RESPONSE_CODE));
        assertNotNull(MDC.get(MDCTransaction.RESPONSE_DESCRIPTION));
        assertNotNull(MDC.get(MDCTransaction.SEVERITY));
        assertNotNull(MDC.get(MDCTransaction.ALERT_SEVERITY));
        assertNotNull(MDC.get(MDCTransaction.TARGET_ENTITY));
        assertNotNull(MDC.get(MDCTransaction.TARGET_SERVICE_NAME));
        assertNotNull(MDC.get(MDCTransaction.TARGET_VIRTUAL_ENTITY));
        assertNotNull(MDC.get(MDCTransaction.CLIENT_IP_ADDRESS));
        assertNotNull(MDC.get(MDCTransaction.REMOTE_HOST));

        assertEquals(trans.getInvocationId(), MDC.get(MDCTransaction.INVOCATION_ID));
        assertEquals(trans.timestamp(trans.getStartTime()), MDC.get(MDCTransaction.BEGIN_TIMESTAMP));
        assertEquals(trans.timestamp(trans.getEndTime()), MDC.get(MDCTransaction.END_TIMESTAMP));
        assertNotEquals(trans.getElapsedTime(), MDC.get(MDCTransaction.ELAPSED_TIME));
        assertEquals(String.valueOf(Duration.between(trans.getStartTime(), trans.getEndTime()).toMillis()),
            MDC.get(MDCTransaction.ELAPSED_TIME));
        assertEquals(trans.getServiceInstanceId(), MDC.get(MDCTransaction.SERVICE_INSTANCE_ID));
        assertEquals(trans.getInstanceUUID(), MDC.get(MDCTransaction.INSTANCE_UUID));
        assertEquals(trans.getProcessKey(),MDC.get(MDCTransaction.PROCESS_KEY));
        assertEquals(trans.getStatusCode(), MDC.get(MDCTransaction.STATUS_CODE));
        assertEquals(trans.getResponseCode(), MDC.get(MDCTransaction.RESPONSE_CODE));
        assertEquals(trans.getResponseDescription(), MDC.get(MDCTransaction.RESPONSE_DESCRIPTION));
        assertEquals(trans.getSeverity(), MDC.get(MDCTransaction.SEVERITY));
        assertEquals(trans.getAlertSeverity(), MDC.get(MDCTransaction.ALERT_SEVERITY));
        assertEquals(trans.getTargetEntity(), MDC.get(MDCTransaction.TARGET_ENTITY));
        assertEquals(trans.getTargetServiceName(), MDC.get(MDCTransaction.TARGET_SERVICE_NAME));
        assertEquals(trans.getTargetVirtualEntity(), MDC.get(MDCTransaction.TARGET_VIRTUAL_ENTITY));
        assertEquals(trans.getClientIpAddress(), MDC.get(MDCTransaction.CLIENT_IP_ADDRESS));
        assertEquals(trans.getRemoteHost(), MDC.get(MDCTransaction.REMOTE_HOST));

        assertEquals(trans.getServiceInstanceId(),"service-instance-id");
        assertEquals(trans.getProcessKey(),"process-key");
        assertEquals(trans.getStatusCode(),"status-code");
        assertEquals(trans.getResponseCode(),"response-code");
        assertEquals(trans.getResponseDescription(),"response-description");
        assertEquals(trans.getSeverity(),"severity");
        assertEquals(trans.getAlertSeverity(),"alert-severity");
        assertEquals(trans.getTargetEntity(),"target-entity");
        assertEquals(trans.getTargetServiceName(),"target-service-name");
        assertEquals(trans.getTargetVirtualEntity(),"target-virtual-entity");
        assertEquals(trans.getClientIpAddress(),"client-ip-address");
        assertEquals(trans.getRemoteHost(),"remote-host");
    }

    @Test
    public void metric() {
        MDCTransaction trans =
            MDCTransaction.newTransaction(null, null).metric();

        assertTransactionFields(trans);
    }

    @Test
    public void transaction() {
        MDCTransaction trans =
            MDCTransaction.newTransaction(null, null).transaction();

        assertTransactionFields(trans);
    }

    @Test
    public void subTransaction() {
        MDCTransaction trans =
            MDCTransaction.newTransaction(null, "partner");

        MDCTransaction subTrans = MDCTransaction.newSubTransaction(null);

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

        subTrans.setServiceInstanceId("service-instance-id").
            setInstanceUUID(null).
            setProcessKey("process-key").
            setStatusCode("status-code").
            setResponseCode("response-code").
            setResponseDescription("response-description").
            setSeverity("severity").
            setAlertSeverity("alert-severity").
            setTargetEntity("target-entity").
            setTargetServiceName("target-service-name").
            setTargetVirtualEntity("target-virtual-entity").
            setClientIpAddress("client-ip-address").
            setRemoteHost("remote-host").
            setEndTime(Instant.now());

        subTrans.setStatusCode(false).setResponseCode("400");

        MDCTransaction subTrans2 = MDCTransaction.fromTransaction(subTrans);

        assertEquals(subTrans.toString(), subTrans2.toString());

        subTrans.metric();
        subTrans2.setStatusCode("202").setProcessKey("junit").metric();

        trans.resetSubTransaction().setStatusCode(true).setResponseCode("200").metric();
    }

}