/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2019-2021 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.utils.logging;

import static org.onap.policy.drools.utils.logging.MdcTransactionConstants.BEGIN_TIMESTAMP;
import static org.onap.policy.drools.utils.logging.MdcTransactionConstants.CLIENT_IP_ADDRESS;
import static org.onap.policy.drools.utils.logging.MdcTransactionConstants.CUSTOM_FIELD1;
import static org.onap.policy.drools.utils.logging.MdcTransactionConstants.CUSTOM_FIELD2;
import static org.onap.policy.drools.utils.logging.MdcTransactionConstants.CUSTOM_FIELD3;
import static org.onap.policy.drools.utils.logging.MdcTransactionConstants.CUSTOM_FIELD4;
import static org.onap.policy.drools.utils.logging.MdcTransactionConstants.DEFAULT_HOSTIP;
import static org.onap.policy.drools.utils.logging.MdcTransactionConstants.DEFAULT_HOSTNAME;
import static org.onap.policy.drools.utils.logging.MdcTransactionConstants.DEFAULT_SERVICE_NAME;
import static org.onap.policy.drools.utils.logging.MdcTransactionConstants.ELAPSED_TIME;
import static org.onap.policy.drools.utils.logging.MdcTransactionConstants.END_TIMESTAMP;
import static org.onap.policy.drools.utils.logging.MdcTransactionConstants.INSTANCE_UUID;
import static org.onap.policy.drools.utils.logging.MdcTransactionConstants.INVOCATION_ID;
import static org.onap.policy.drools.utils.logging.MdcTransactionConstants.PARTNER_NAME;
import static org.onap.policy.drools.utils.logging.MdcTransactionConstants.PROCESS_KEY;
import static org.onap.policy.drools.utils.logging.MdcTransactionConstants.REMOTE_HOST;
import static org.onap.policy.drools.utils.logging.MdcTransactionConstants.REQUEST_ID;
import static org.onap.policy.drools.utils.logging.MdcTransactionConstants.RESPONSE_CODE;
import static org.onap.policy.drools.utils.logging.MdcTransactionConstants.RESPONSE_DESCRIPTION;
import static org.onap.policy.drools.utils.logging.MdcTransactionConstants.SERVER;
import static org.onap.policy.drools.utils.logging.MdcTransactionConstants.SERVER_FQDN;
import static org.onap.policy.drools.utils.logging.MdcTransactionConstants.SERVER_IP_ADDRESS;
import static org.onap.policy.drools.utils.logging.MdcTransactionConstants.SERVICE_INSTANCE_ID;
import static org.onap.policy.drools.utils.logging.MdcTransactionConstants.SERVICE_NAME;
import static org.onap.policy.drools.utils.logging.MdcTransactionConstants.SEVERITY;
import static org.onap.policy.drools.utils.logging.MdcTransactionConstants.STATUS_CODE;
import static org.onap.policy.drools.utils.logging.MdcTransactionConstants.STATUS_CODE_COMPLETE;
import static org.onap.policy.drools.utils.logging.MdcTransactionConstants.STATUS_CODE_FAILURE;
import static org.onap.policy.drools.utils.logging.MdcTransactionConstants.TARGET_ENTITY;
import static org.onap.policy.drools.utils.logging.MdcTransactionConstants.TARGET_SERVICE_NAME;
import static org.onap.policy.drools.utils.logging.MdcTransactionConstants.TARGET_VIRTUAL_ENTITY;
import static org.onap.policy.drools.utils.logging.MdcTransactionConstants.VIRTUAL_SERVER_NAME;

import java.time.Instant;
import lombok.Getter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.onap.policy.drools.metrics.Metric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@ToString
class MdcTransactionImpl implements MdcTransaction {

    private static final Logger logger = LoggerFactory.getLogger(MdcTransactionImpl.class.getName());

    @Getter
    private final Metric execItem = new Metric();

    /**
     * Transaction with no information set.
     */
    public MdcTransactionImpl() {
        MDC.clear();
    }

    /**
     * MDC Transaction.
     *
     * @param requestId transaction id
     * @param partner transaction origin
     */
    public MdcTransactionImpl(String requestId, String partner) {
        MDC.clear();

        setRequestId(requestId);
        setPartner(partner);

        setServiceName(DEFAULT_SERVICE_NAME);
        setServer(DEFAULT_HOSTNAME);
        setServerIpAddress(DEFAULT_HOSTIP);
        setServerFqdn(DEFAULT_HOSTNAME);
        setVirtualServerName(DEFAULT_HOSTNAME);

        setStartTime(Instant.now());
    }

    /**
     * create sub-transaction.
     *
     * @param invocationId sub-transaction id
     */
    public MdcTransactionImpl(String invocationId) {
        resetSubTransaction();

        setRequestId(MDC.get(REQUEST_ID));
        setPartner(MDC.get(PARTNER_NAME));
        setServiceName(MDC.get(SERVICE_NAME));
        setServerIpAddress(MDC.get(SERVER_IP_ADDRESS));
        setServerFqdn(MDC.get(SERVER_FQDN));
        setVirtualServerName(MDC.get(VIRTUAL_SERVER_NAME));
        setServer(MDC.get(SERVER));

        setInvocationId(invocationId);
        setStartTime(Instant.now());
    }

    /**
     * copy constructor transaction/sub-transaction.
     *
     * @param transaction transaction
     */
    public MdcTransactionImpl(MdcTransaction transaction) {
        MDC.clear();

        setClientIpAddress(transaction.getClientIpAddress());
        setElapsedTime(transaction.getElapsedTime());
        setEndTime(transaction.getEndTime());
        setInstanceUuid(transaction.getInstanceUuid());
        setInvocationId(transaction.getInvocationId());
        setPartner(transaction.getPartner());
        setProcessKey(transaction.getProcessKey());
        setRemoteHost(transaction.getRemoteHost());
        setRequestId(transaction.getRequestId());
        setResponseCode(transaction.getResponseCode());
        setResponseDescription(transaction.getResponseDescription());
        setServer(transaction.getServer());
        setServerFqdn(transaction.getServerFqdn());
        setServerIpAddress(transaction.getServerIpAddress());
        setServiceInstanceId(transaction.getServiceInstanceId());
        setServiceName(transaction.getServiceName());
        setSeverity(transaction.getSeverity());
        setStartTime(transaction.getStartTime());
        setStatusCode(transaction.getStatusCode());
        setTargetEntity(transaction.getTargetEntity());
        setTargetServiceName(transaction.getTargetServiceName());
        setTargetVirtualEntity(transaction.getTargetVirtualEntity());
        setVirtualServerName(transaction.getVirtualServerName());
        setCustomField1(transaction.getCustomField1());
        setCustomField2(transaction.getCustomField2());
        setCustomField3(transaction.getCustomField3());
        setCustomField4(transaction.getCustomField4());
    }

    /**
     * reset sub-transaction portion.
     *
     * @return MDCTransaction
     */
    @Override
    public MdcTransaction resetSubTransaction() {
        MDC.remove(INVOCATION_ID);
        MDC.remove(BEGIN_TIMESTAMP);
        MDC.remove(END_TIMESTAMP);
        MDC.remove(ELAPSED_TIME);
        MDC.remove(SERVICE_INSTANCE_ID);
        MDC.remove(STATUS_CODE);
        MDC.remove(RESPONSE_CODE);
        MDC.remove(RESPONSE_DESCRIPTION);
        MDC.remove(INSTANCE_UUID);
        MDC.remove(TARGET_ENTITY);
        MDC.remove(TARGET_SERVICE_NAME);
        MDC.remove(PROCESS_KEY);
        MDC.remove(CLIENT_IP_ADDRESS);
        MDC.remove(REMOTE_HOST);
        MDC.remove(TARGET_VIRTUAL_ENTITY);

        return this;
    }

    @Override
    public MdcTransaction resetTransaction() {
        MDC.clear();
        return this;
    }

    /**
     * flush transaction to MDC.
     */
    @Override
    public MdcTransaction flush() {
        setMdc(REQUEST_ID, execItem.getRequestId());
        setMdc(INVOCATION_ID, execItem.getInvocationId());
        setMdc(PARTNER_NAME, execItem.getPartner());
        setMdc(VIRTUAL_SERVER_NAME, execItem.getVirtualServerName());
        setMdc(SERVER, execItem.getServerName());
        setMdc(SERVER_IP_ADDRESS, execItem.getServerIpAddress());
        setMdc(SERVER_FQDN, execItem.getServerFqdn());
        setMdc(SERVICE_NAME, execItem.getServiceName());
        setMdc(BEGIN_TIMESTAMP, Metric.toTimestamp(execItem.getStartTime()));
        setMdc(END_TIMESTAMP, Metric.toTimestamp(execItem.getEndTime()));

        if (execItem.getElapsedTime() != null) {
            MDC.put(ELAPSED_TIME, String.valueOf(execItem.getElapsedTime()));
        } else {
            execItem.setElapsedTime(null);  // this computes elapsed time appropriately with start and end times
            MDC.put(ELAPSED_TIME, String.valueOf(execItem.getElapsedTime()));
        }

        setMdc(SERVICE_INSTANCE_ID, execItem.getServiceInstanceId());
        setMdc(INSTANCE_UUID, execItem.getInstanceUuid());
        setMdc(PROCESS_KEY, execItem.getProcessKey());
        setMdc(STATUS_CODE, execItem.getStatusCode());
        setMdc(RESPONSE_CODE, execItem.getResponseCode());
        setMdc(RESPONSE_DESCRIPTION, execItem.getResponseDescription());
        setMdc(SEVERITY, execItem.getAlertSeverity());
        setMdc(TARGET_ENTITY, execItem.getTargetEntity());
        setMdc(TARGET_SERVICE_NAME, execItem.getTargetServiceName());
        setMdc(TARGET_VIRTUAL_ENTITY, execItem.getTargetVirtualEntity());
        setMdc(CLIENT_IP_ADDRESS, execItem.getClientIpAddress());
        setMdc(REMOTE_HOST, execItem.getRemoteHost());
        setMdc(CUSTOM_FIELD1, execItem.getCustomField1());
        setMdc(CUSTOM_FIELD2, execItem.getCustomField2());
        setMdc(CUSTOM_FIELD3, execItem.getCustomField3());
        setMdc(CUSTOM_FIELD4, execItem.getCustomField4());

        return this;
    }

    private void setMdc(String paramName, String value) {
        if (!StringUtils.isBlank(value)) {
            MDC.put(paramName, value);
        }
    }

    @Override
    public MdcTransaction metric() {
        flush();
        logger.info(LoggerUtil.METRIC_LOG_MARKER, "");
        return this;
    }

    @Override
    public MdcTransaction transaction() {
        flush();
        logger.info(LoggerUtil.TRANSACTION_LOG_MARKER, "");
        return this;
    }

    @Override
    public MdcTransaction setEndTime(Instant endTime) {
        execItem.setEndTime(endTime);
        return this;
    }

    @Override
    public MdcTransaction setElapsedTime(Long elapsedTime) {
        execItem.setElapsedTime(elapsedTime);
        return this;
    }

    @Override
    public MdcTransaction setServiceInstanceId(String serviceInstanceId) {
        execItem.setServiceInstanceId(serviceInstanceId);
        return this;
    }

    @Override
    public MdcTransaction setProcessKey(String processKey) {
        execItem.setProcessKey(processKey);
        return this;
    }

    @Override
    public MdcTransaction setClientIpAddress(String clientIpAddress) {
        execItem.setClientIpAddress(clientIpAddress);
        return this;
    }

    @Override
    public MdcTransaction setRemoteHost(String remoteHost) {
        execItem.setRemoteHost(remoteHost);
        return this;
    }

    @Override
    public Instant getStartTime() {
        return execItem.getStartTime();
    }

    @Override
    public String getServer() {
        return execItem.getServerName();
    }

    @Override
    public Instant getEndTime() {
        return execItem.getEndTime();
    }

    @Override
    public Long getElapsedTime() {
        return execItem.getElapsedTime();
    }

    @Override
    public String getRemoteHost() {
        return execItem.getRemoteHost();
    }

    @Override
    public String getClientIpAddress() {
        return execItem.getClientIpAddress();
    }

    @Override
    public String getProcessKey() {
        return execItem.getProcessKey();
    }

    @Override
    public String getServiceInstanceId() {
        return execItem.getServiceInstanceId();
    }

    @Override
    public String getCustomField1() {
        return execItem.getCustomField1();
    }

    @Override
    public String getCustomField2() {
        return execItem.getCustomField2();
    }

    @Override
    public String getCustomField3() {
        return execItem.getCustomField3();
    }

    @Override
    public String getCustomField4() {
        return execItem.getCustomField4();
    }

    @Override
    public String timestamp(Instant time) {
        return Metric.toTimestamp(time);
    }

    /* transaction and sub-transaction fields */

    @Override
    public MdcTransaction setInvocationId(String invocationId) {
        execItem.setInvocationId(invocationId);
        MDC.put(INVOCATION_ID, execItem.getInvocationId());
        return this;
    }

    @Override
    public MdcTransaction setStartTime(Instant startTime) {
        execItem.setStartTime(startTime);
        MDC.put(BEGIN_TIMESTAMP, Metric.toTimestamp(execItem.getStartTime()));
        return this;
    }

    @Override
    public MdcTransaction setServiceName(String serviceName) {
        execItem.setServiceName(serviceName);
        MDC.put(SERVICE_NAME, execItem.getServiceName());
        return this;
    }

    @Override
    public MdcTransaction setStatusCode(String statusCode) {
        execItem.setStatusCode(statusCode);
        return this;
    }

    @Override
    public MdcTransaction setStatusCode(boolean success) {
        if (success) {
            execItem.setStatusCode(STATUS_CODE_COMPLETE);
        } else {
            execItem.setStatusCode(STATUS_CODE_FAILURE);
        }
        return this;
    }

    @Override
    public MdcTransaction setResponseCode(String responseCode) {
        execItem.setResponseCode(responseCode);
        return this;
    }

    @Override
    public MdcTransaction setResponseDescription(String responseDescription) {
        execItem.setResponseDescription(responseDescription);
        return this;
    }

    @Override
    public MdcTransaction setInstanceUuid(String instanceUuid) {
        execItem.setInstanceUuid(instanceUuid);
        MDC.put(INSTANCE_UUID, execItem.getInstanceUuid());
        return this;
    }

    @Override
    public MdcTransaction setSeverity(String severity) {
        execItem.setAlertSeverity(severity);
        return this;
    }

    @Override
    public MdcTransaction setTargetEntity(String targetEntity) {
        execItem.setTargetEntity(targetEntity);
        return this;
    }

    @Override
    public MdcTransaction setTargetServiceName(String targetServiceName) {
        execItem.setTargetServiceName(targetServiceName);
        return this;
    }

    @Override
    public MdcTransaction setTargetVirtualEntity(String targetVirtualEntity) {
        execItem.setTargetVirtualEntity(targetVirtualEntity);
        return this;
    }

    @Override
    public MdcTransaction setCustomField1(String customField1) {
        execItem.setCustomField1(customField1);
        return this;
    }

    @Override
    public MdcTransaction setCustomField2(String customField2) {
        execItem.setCustomField2(customField2);
        return this;
    }

    @Override
    public MdcTransaction setCustomField3(String customField3) {
        execItem.setCustomField3(customField3);
        return this;
    }

    @Override
    public MdcTransaction setCustomField4(String customField4) {
        execItem.setCustomField4(customField4);
        return this;
    }

    @Override
    public String getInvocationId() {
        return execItem.getInvocationId();
    }

    @Override
    public String getServiceName() {
        return execItem.getServiceName();
    }

    @Override
    public String getStatusCode() {
        return execItem.getStatusCode();
    }

    @Override
    public String getResponseDescription() {
        return execItem.getResponseDescription();
    }

    @Override
    public String getInstanceUuid() {
        return execItem.getInstanceUuid();
    }

    @Override
    public String getSeverity() {
        return execItem.getAlertSeverity();
    }

    @Override
    public String getTargetEntity() {
        return execItem.getTargetEntity();
    }

    @Override
    public String getTargetServiceName() {
        return execItem.getTargetServiceName();
    }

    @Override
    public String getTargetVirtualEntity() {
        return execItem.getTargetVirtualEntity();
    }

    @Override
    public String getResponseCode() {
        return execItem.getResponseCode();
    }

    /* inheritable fields by sub-transactions via MDC */

    @Override
    public MdcTransaction setRequestId(String requestId) {
        execItem.setRequestId(requestId);
        MDC.put(REQUEST_ID, execItem.getRequestId());
        return this;
    }

    @Override
    public MdcTransaction setPartner(String partner) {
        execItem.setPartner(partner);
        MDC.put(PARTNER_NAME, execItem.getPartner());
        return this;
    }

    @Override
    public MdcTransaction setServer(String server) {
        execItem.setServerName(server);
        MDC.put(SERVER, this.execItem.getServerName());
        return this;
    }

    @Override
    public MdcTransaction setServerIpAddress(String serverIpAddress) {
        execItem.setServerIpAddress(serverIpAddress);
        MDC.put(SERVER_IP_ADDRESS, execItem.getServerIpAddress());
        return this;
    }

    @Override
    public MdcTransaction setServerFqdn(String serverFqdn) {
        execItem.setServerFqdn(serverFqdn);
        MDC.put(SERVER_FQDN, execItem.getServerFqdn());
        return this;
    }

    @Override
    public MdcTransaction setVirtualServerName(String virtualServerName) {
        execItem.setVirtualServerName(virtualServerName);
        MDC.put(VIRTUAL_SERVER_NAME, execItem.getVirtualServerName());
        return this;
    }

    @Override
    public String getRequestId() {
        return execItem.getRequestId();
    }

    @Override
    public String getPartner() {
        return execItem.getPartner();
    }

    @Override
    public String getServerFqdn() {
        return execItem.getServerFqdn();
    }

    @Override
    public String getVirtualServerName() {
        return execItem.getVirtualServerName();
    }

    @Override
    public String getServerIpAddress() {
        return execItem.getServerIpAddress();
    }
}
