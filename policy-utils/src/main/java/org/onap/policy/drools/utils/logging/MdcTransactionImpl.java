/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2019-2020 AT&T Intellectual Property. All rights reserved.
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
import static org.onap.policy.drools.utils.logging.MdcTransactionConstants.CUSTOM_FIELD3;
import static org.onap.policy.drools.utils.logging.MdcTransactionConstants.CLIENT_IP_ADDRESS;
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

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class MdcTransactionImpl implements MdcTransaction {

    private static final Logger logger = LoggerFactory.getLogger(MdcTransactionImpl.class.getName());

    /**
     * Logging Format for Timestamps.
     */

    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS+00:00";

    /* transaction inheritable fields */

    private String requestId;
    private String partner;

    private String invocationId;
    private String virtualServerName;
    private String serverName;
    private String serverIpAddress;
    private String serverFqdn;

    private String serviceName;

    private Instant startTime;
    private Instant endTime;
    private Long elapsedTime;

    private String serviceInstanceId;
    private String instanceUuid;
    private String processKey;

    private String statusCode;
    private String responseCode;
    private String responseDescription;
    private String alertSeverity;

    private String targetEntity;
    private String targetServiceName;
    private String targetVirtualEntity;
    private String clientIpAddress;
    private String remoteHost;
    private String customField3;

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

        this.setRequestId(requestId);
        this.setPartner(partner);

        this.setServiceName(DEFAULT_SERVICE_NAME);
        this.setServer(DEFAULT_HOSTNAME);
        this.setServerIpAddress(DEFAULT_HOSTIP);
        this.setServerFqdn(DEFAULT_HOSTNAME);
        this.setVirtualServerName(DEFAULT_HOSTNAME);

        this.setStartTime(Instant.now());
    }

    /**
     * create subtransaction.
     *
     * @param invocationId subtransaction id
     */
    public MdcTransactionImpl(String invocationId) {
        this.resetSubTransaction();

        this.setRequestId(MDC.get(REQUEST_ID));
        this.setPartner(MDC.get(PARTNER_NAME));
        this.setServiceName(MDC.get(SERVICE_NAME));
        this.setServer(MDC.get(SERVER));
        this.setServerIpAddress(MDC.get(SERVER_IP_ADDRESS));
        this.setServerFqdn(MDC.get(SERVER_FQDN));
        this.setVirtualServerName(MDC.get(VIRTUAL_SERVER_NAME));

        this.setInvocationId(invocationId);
        this.setStartTime(Instant.now());
    }

    /**
     * copy constructor transaction/subtransaction.
     *
     * @param transaction transaction
     */
    public MdcTransactionImpl(MdcTransaction transaction) {
        MDC.clear();
        this.setClientIpAddress(transaction.getClientIpAddress());
        this.setElapsedTime(transaction.getElapsedTime());
        this.setEndTime(transaction.getEndTime());
        this.setInstanceUuid(transaction.getInstanceUuid());
        this.setInvocationId(transaction.getInvocationId());
        this.setPartner(transaction.getPartner());
        this.setProcessKey(transaction.getProcessKey());
        this.setRemoteHost(transaction.getRemoteHost());
        this.setRequestId(transaction.getRequestId());
        this.setResponseCode(transaction.getResponseCode());
        this.setResponseDescription(transaction.getResponseDescription());
        this.setServer(transaction.getServer());
        this.setServerFqdn(transaction.getServerFqdn());
        this.setServerIpAddress(transaction.getServerIpAddress());
        this.setServiceInstanceId(transaction.getServiceInstanceId());
        this.setServiceName(transaction.getServiceName());
        this.setSeverity(transaction.getSeverity());
        this.setStartTime(transaction.getStartTime());
        this.setStatusCode(transaction.getStatusCode());
        this.setTargetEntity(transaction.getTargetEntity());
        this.setTargetServiceName(transaction.getTargetServiceName());
        this.setTargetVirtualEntity(transaction.getTargetVirtualEntity());
        this.setVirtualServerName(transaction.getVirtualServerName());
        this.setCustomField3(transaction.getCustomField3());
    }

    /**
     * reset subtransaction portion.
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
        setMdc(REQUEST_ID, this.requestId);
        setMdc(INVOCATION_ID, this.invocationId);
        setMdc(PARTNER_NAME, this.partner);
        setMdc(VIRTUAL_SERVER_NAME, this.virtualServerName);
        setMdc(SERVER, this.serverName);
        setMdc(SERVER_IP_ADDRESS, this.serverIpAddress);
        setMdc(SERVER_FQDN, this.serverFqdn);
        setMdc(SERVICE_NAME, this.serviceName);
        setMdc(BEGIN_TIMESTAMP, timestamp(this.startTime));
        setMdc(END_TIMESTAMP, timestamp(this.endTime));

        if (this.elapsedTime != null) {
            MDC.put(ELAPSED_TIME, String.valueOf(this.elapsedTime));
        } else if (endTime != null && startTime != null) {
            this.elapsedTime = Duration.between(startTime, endTime).toMillis();
            MDC.put(ELAPSED_TIME, String.valueOf(this.elapsedTime));
        }

        setMdc(SERVICE_INSTANCE_ID, this.serviceInstanceId);
        setMdc(INSTANCE_UUID, this.instanceUuid);
        setMdc(PROCESS_KEY, this.processKey);
        setMdc(STATUS_CODE, this.statusCode);
        setMdc(RESPONSE_CODE, this.responseCode);
        setMdc(RESPONSE_DESCRIPTION, this.responseDescription);
        setMdc(SEVERITY, this.alertSeverity);
        setMdc(TARGET_ENTITY, this.targetEntity);
        setMdc(TARGET_SERVICE_NAME, this.targetServiceName);
        setMdc(TARGET_VIRTUAL_ENTITY, this.targetVirtualEntity);
        setMdc(CLIENT_IP_ADDRESS, this.clientIpAddress);
        setMdc(REMOTE_HOST, this.remoteHost);
        setMdc(CUSTOM_FIELD3, this.customField3);

        return this;
    }

    private void setMdc(String paramName, String value) {
        if (!StringUtils.isBlank(value)) {
            MDC.put(paramName, value);
        }
    }

    @Override
    public MdcTransaction metric() {
        this.flush();
        logger.info(LoggerUtil.METRIC_LOG_MARKER, "");
        return this;
    }

    @Override
    public MdcTransaction transaction() {
        this.flush();
        logger.info(LoggerUtil.TRANSACTION_LOG_MARKER, "");
        return this;
    }

    @Override
    public MdcTransaction setEndTime(Instant endTime) {
        if (endTime == null) {
            this.endTime = Instant.now();
        } else {
            this.endTime = endTime;
        }
        return this;
    }

    @Override
    public MdcTransaction setElapsedTime(Long elapsedTime) {
        this.elapsedTime = elapsedTime;
        return this;
    }

    @Override
    public MdcTransaction setServiceInstanceId(String serviceInstanceId) {
        this.serviceInstanceId = serviceInstanceId;
        return this;
    }

    @Override
    public MdcTransaction setProcessKey(String processKey) {
        this.processKey = processKey;
        return this;
    }

    @Override
    public MdcTransaction setClientIpAddress(String clientIpAddress) {
        this.clientIpAddress = clientIpAddress;
        return this;
    }

    @Override
    public MdcTransaction setRemoteHost(String remoteHost) {
        this.remoteHost = remoteHost;
        return this;
    }

    @Override
    public Instant getStartTime() {
        return this.startTime;
    }

    @Override
    public String getServer() {
        return this.serverName;
    }

    @Override
    public Instant getEndTime() {
        return this.endTime;
    }

    @Override
    public Long getElapsedTime() {
        return this.elapsedTime;
    }

    @Override
    public String getRemoteHost() {
        return this.remoteHost;
    }

    @Override
    public String getClientIpAddress() {
        return this.clientIpAddress;
    }

    @Override
    public String getProcessKey() {
        return this.processKey;
    }

    @Override
    public String getServiceInstanceId() {
        return this.serviceInstanceId;
    }

    @Override
    public String getCustomField3() {
        return this.customField3;
    }

    /* transaction and subtransaction fields */

    @Override
    public MdcTransaction setInvocationId(String invocationId) {
        if (invocationId == null) {
            this.invocationId = UUID.randomUUID().toString();
        } else {
            this.invocationId = invocationId;
        }

        MDC.put(INVOCATION_ID, this.invocationId);

        return this;
    }

    @Override
    public MdcTransaction setStartTime(Instant startTime) {
        if (startTime == null) {
            this.startTime = Instant.now();
        } else {
            this.startTime = startTime;
        }

        MDC.put(BEGIN_TIMESTAMP, this.timestamp(this.startTime));

        return this;
    }

    @Override
    public MdcTransaction setServiceName(String serviceName) {
        if (serviceName == null || serviceName.isEmpty()) {
            this.serviceName = DEFAULT_SERVICE_NAME;
        } else {
            this.serviceName = serviceName;
        }

        MDC.put(SERVICE_NAME, this.serviceName);

        return this;
    }

    @Override
    public MdcTransaction setStatusCode(String statusCode) {
        this.statusCode = statusCode;
        return this;
    }

    @Override
    public MdcTransaction setStatusCode(boolean success) {
        if (success) {
            this.statusCode = STATUS_CODE_COMPLETE;
        } else {
            this.statusCode = STATUS_CODE_FAILURE;
        }
        return this;
    }

    @Override
    public MdcTransaction setResponseCode(String responseCode) {
        this.responseCode = responseCode;
        return this;
    }

    @Override
    public MdcTransaction setResponseDescription(String responseDescription) {
        this.responseDescription = responseDescription;
        return this;
    }

    @Override
    public MdcTransaction setInstanceUuid(String instanceUuid) {
        if (instanceUuid == null) {
            this.instanceUuid = UUID.randomUUID().toString();
        } else {
            this.instanceUuid = instanceUuid;
        }

        MDC.put(INSTANCE_UUID, this.instanceUuid);
        return this;
    }

    @Override
    public MdcTransaction setSeverity(String severity) {
        this.alertSeverity = severity;
        return this;
    }

    @Override
    public MdcTransaction setTargetEntity(String targetEntity) {
        this.targetEntity = targetEntity;
        return this;
    }

    @Override
    public MdcTransaction setTargetServiceName(String targetServiceName) {
        this.targetServiceName = targetServiceName;
        return this;
    }

    @Override
    public MdcTransaction setTargetVirtualEntity(String targetVirtualEntity) {
        this.targetVirtualEntity = targetVirtualEntity;
        return this;
    }

    @Override
    public MdcTransaction setCustomField3(String customField3) {
        this.customField3 = customField3;
        return this;
    }

    @Override
    public String getInvocationId() {
        return invocationId;
    }

    @Override
    public String getServiceName() {
        return serviceName;
    }

    @Override
    public String getStatusCode() {
        return statusCode;
    }

    @Override
    public String getResponseDescription() {
        return responseDescription;
    }

    @Override
    public String getInstanceUuid() {
        return instanceUuid;
    }

    @Override
    public String getSeverity() {
        return alertSeverity;
    }

    @Override
    public String getTargetEntity() {
        return targetEntity;
    }

    @Override
    public String getTargetServiceName() {
        return targetServiceName;
    }

    @Override
    public String getTargetVirtualEntity() {
        return targetVirtualEntity;
    }

    @Override
    public String getResponseCode() {
        return responseCode;
    }

    /* inheritable fields by subtransactions via MDC */

    @Override
    public MdcTransaction setRequestId(String requestId) {
        if (requestId == null || requestId.isEmpty()) {
            this.requestId = UUID.randomUUID().toString();
        } else {
            this.requestId = requestId;
        }

        MDC.put(REQUEST_ID, this.requestId);
        return this;
    }

    @Override
    public MdcTransaction setPartner(String partner) {
        if (partner == null || partner.isEmpty()) {
            this.partner = DEFAULT_SERVICE_NAME;
        } else {
            this.partner = partner;
        }

        MDC.put(PARTNER_NAME, this.partner);
        return this;
    }

    @Override
    public MdcTransaction setServer(String server) {
        if (server == null || server.isEmpty()) {
            this.serverName = DEFAULT_HOSTNAME;
        } else {
            this.serverName = server;
        }

        MDC.put(SERVER, this.serverName);
        return this;
    }

    @Override
    public MdcTransaction setServerIpAddress(String serverIpAddress) {
        if (serverIpAddress == null || serverIpAddress.isEmpty()) {
            this.serverIpAddress = DEFAULT_HOSTIP;
        } else {
            this.serverIpAddress = serverIpAddress;
        }

        MDC.put(SERVER_IP_ADDRESS, this.serverIpAddress);
        return this;
    }

    @Override
    public MdcTransaction setServerFqdn(String serverFqdn) {
        if (serverFqdn == null || serverFqdn.isEmpty()) {
            this.serverFqdn = DEFAULT_HOSTNAME;
        } else {
            this.serverFqdn = serverFqdn;
        }

        MDC.put(SERVER_FQDN, this.serverFqdn);
        return this;
    }

    @Override
    public MdcTransaction setVirtualServerName(String virtualServerName) {
        if (virtualServerName == null || virtualServerName.isEmpty()) {
            this.virtualServerName = DEFAULT_HOSTNAME;
        } else {
            this.virtualServerName = virtualServerName;
        }

        MDC.put(VIRTUAL_SERVER_NAME, this.virtualServerName);
        return this;
    }

    @Override
    public String getRequestId() {
        return requestId;
    }

    @Override
    public String getPartner() {
        return partner;
    }

    @Override
    public String getServerFqdn() {
        return serverFqdn;
    }

    @Override
    public String getVirtualServerName() {
        return virtualServerName;
    }

    @Override
    public String getServerIpAddress() {
        return serverIpAddress;
    }

    @Override
    public String timestamp(Instant time) {
        if (time == null) {
            return null;
        }

        return new SimpleDateFormat(DATE_FORMAT).format(Date.from(time));
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("MDCTransaction{");
        sb.append("requestId='").append(requestId).append('\'');
        sb.append(", partner='").append(partner).append('\'');
        sb.append(", invocationId='").append(invocationId).append('\'');
        sb.append(", virtualServerName='").append(virtualServerName).append('\'');
        sb.append(", server='").append(serverName).append('\'');
        sb.append(", serverIpAddress='").append(serverIpAddress).append('\'');
        sb.append(", serverFqdn='").append(serverFqdn).append('\'');
        sb.append(", serviceName='").append(serviceName).append('\'');
        sb.append(", startTime=").append(startTime);
        sb.append(", endTime=").append(endTime);
        sb.append(", elapsedTime=").append(elapsedTime);
        sb.append(", serviceInstanceId='").append(serviceInstanceId).append('\'');
        sb.append(", instanceUUID='").append(instanceUuid).append('\'');
        sb.append(", processKey='").append(processKey).append('\'');
        sb.append(", statusCode='").append(statusCode).append('\'');
        sb.append(", responseCode='").append(responseCode).append('\'');
        sb.append(", responseDescription='").append(responseDescription).append('\'');
        sb.append(", severity='").append(alertSeverity).append('\'');
        sb.append(", targetEntity='").append(targetEntity).append('\'');
        sb.append(", targetServiceName='").append(targetServiceName).append('\'');
        sb.append(", targetVirtualEntity='").append(targetVirtualEntity).append('\'');
        sb.append(", clientIpAddress='").append(clientIpAddress).append('\'');
        sb.append(", remoteHost='").append(remoteHost).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
