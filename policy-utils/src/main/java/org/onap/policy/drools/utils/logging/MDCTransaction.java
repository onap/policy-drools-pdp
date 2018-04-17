/*-
 * ============LICENSE_START=======================================================
 * policy-utils
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
 */

package org.onap.policy.drools.utils.logging;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.onap.policy.drools.utils.NetworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * MDC Transaction Utility Class.
 *
 * There is an implicit 2-level tree of Transactions in ONAP: transactions
 * and subtransactions.
 *
 * 1. The top level transaction relates to the overarching transaction
 *    id (ie. RequestId) and should be made available to subtransactions
 *    for reuse in the ThreadLocal MDC structure.
 *
 *    This is the data to be inherited and common to all subtransactions
 *    (not a common case but could be modified by subtransactions):
 *
 *    Request ID
 *    Virtual Server Name
 *    Partner Name
 *    Server
 *    Server IP Address
 *    Server FQDN
 *
 * 2. The second level at the leaves is formed by subtransactions and the key
 *    identifier is the invocation id.
 *
 *    Begin Timestamp
 *    End Timestamp
 *    Elapsed Time
 *    Service Instance ID
 *    Service Name
 *    Status Code
 *    Response Code
 *    Response Description
 *    Instance UUID
 *    Severity
 *    Target Entity
 *    Target Service Name
 *    Server
 *    Server IP Address
 *    Server FQDN
 *    Client IP Address
 *    Process Key
 *    Remote Host
 *    Alert Severity
 *    Target Virtual Entity
 *
 *
 * The naming convention for the fields must match the naming given at
 *
 *      https://wiki.onap.org/pages/viewpage.action?pageId=20087036
 */
public interface MDCTransaction {
    /*
     * The fields must match the naming given at
     * https://wiki.onap.org/pages/viewpage.action?pageId=20087036
     */

    /**
     * End to end transaction ID. Subtransactions will inherit this value from the transaction.
     */
    String REQUEST_ID = "RequestID";

    /**
     * Invocation ID, ie. SubTransaction ID.
     */
    String INVOCATION_ID = "InvocationID";

    /**
     * Service Name. Both transactions and subtransactions will have its own copy.
     */
    String SERVICE_NAME = "ServiceName";

    /**
     * Partner Name Subtransactions will inherit this value from the transaction.
     */
    String PARTNER_NAME = "PartnerName";

    /**
     * Start Timestamp. Both transactions and subtransactions will have its own copy.
     */
    String BEGIN_TIMESTAMP = "BeginTimestamp";

    /**
     * End Timestamp. Both transactions and subtransactions will have its own copy.
     */
    String END_TIMESTAMP = "EndTimestamp";

    /**
     * Elapsed Time. Both transactions and subtransactions will have its own copy.
     */
    String ELAPSED_TIME = "ElapsedTime";

    /**
     * Elapsed Time. Both transactions and subtransactions will have its own copy.
     */
    String SERVICE_INSTANCE_ID = "ServiceInstanceID";

    /**
     * Virtual Server Name. Subtransactions will inherit this value from the transaction.
     */
    String VIRTUAL_SERVER_NAME = "VirtualServerName";

    /**
     * Status Code Both transactions and subtransactions will have its own copy.
     */
    String STATUS_CODE = "StatusCode";

    /**
     * Response Code Both transactions and subtransactions will have its own copy.
     */
    String RESPONSE_CODE = "ResponseCode";

    /**
     * Response Description Both transactions and subtransactions will have its own copy.
     */
    String RESPONSE_DESCRIPTION = "ResponseDescription";

    /**
     * Instance UUID Both transactions and subtransactions will have its own copy.
     */
    String INSTANCE_UUID = "InstanceUUID";

    /**
     * Severity Both transactions and subtransactions will have its own copy.
     */
    String SEVERITY = "Severity";

    /**
     * Target Entity Both transactions and subtransactions will have its own copy.
     */
    String TARGET_ENTITY = "TargetEntity";

    /**
     * Target Service Name Both transactions and subtransactions will have its own copy.
     */
    String TARGET_SERVICE_NAME = "TargetServiceName";

    /**
     * Server Subtransactions inherit this value.    if (this.getSources().size() == 1)
      this.getSources().get(0).getTopic();
     */
    String SERVER = "Server";

    /**
     * Server IP Address Subtransactions inherit this value.
     */
    String SERVER_IP_ADDRESS = "ServerIpAddress";

    /**
     * Server FQDN Subtransactions inherit this value.
     */
    String SERVER_FQDN = "ServerFQDN";

    /**
     * Client IP Address Both transactions and subtransactions will have its own copy.
     */
    String CLIENT_IP_ADDRESS = "ClientIPAddress";

    /**
     * Process Key Both transactions and subtransactions will have its own copy.
     */
    String PROCESS_KEY = "ProcessKey";

    /**
     * Remote Host Both transactions and subtransactions will have its own copy.
     */
    String REMOTE_HOST = "RemoteHost";

    /**
     * Alert Severity Both transactions and subtransactions will have its own copy.
     */
    String ALERT_SEVERITY = "AlertSeverity";

    /**
     * Target Virtual Entity Both transactions and subtransactions will have its own copy.
     */
    String TARGET_VIRTUAL_ENTITY = "TargetVirtualEntity";

    /**
     * Default Service Name
     */
    String DEFAULT_SERVICE_NAME = "PDP-D";

    /**
     * Default Host Name
     */
    String DEFAULT_HOSTNAME = NetworkUtil.getHostname();

    /**
     * Default Host IP
     */
    String DEFAULT_HOSTIP = NetworkUtil.getHostIp();

    /**
     * Status Code Complete
     */
    String STATUS_CODE_COMPLETE = "COMPLETE";

    /**
     * Status Code Error
     */
    String STATUS_CODE_FAILURE = "ERROR";

    /**
     * reset subtransaction data
     */
    MDCTransaction resetSubTransaction();

    /**
     * resets transaction data
     */
    MDCTransaction resetTransaction();

    /**
     * flush to MDC structure
     */
    MDCTransaction flush();

    /**
     * convenience method to log a metric.  Alternatively caller
     * could call flush() and the logging statement directly for
     * further granularity.
     */
    MDCTransaction metric();

    /**
     * convenience method to log a transaction record.  Alternatively caller
     * could call flush() and the logging statement directly for
     * further granularity.
     */
    MDCTransaction transaction();

    /**
     * get invocation id
     */
    MDCTransaction setInvocationId(String invocationId);

    /**
     * set start time
     */
    MDCTransaction setStartTime(Instant startTime);

    /**
     * set service name
     */
    MDCTransaction setServiceName(String serviceName);

    /**
     * set status code
     */
    MDCTransaction setStatusCode(String statusCode);

    /**
     * set status code
     */
    MDCTransaction setStatusCode(boolean success);

    /**
     * sets response code
     */
    MDCTransaction setResponseCode(String responseCode);

    /**
     * sets response description
     */
    MDCTransaction setResponseDescription(String responseDescription);

    /**
     * sets instance uuid
     */
    MDCTransaction setInstanceUUID(String instanceUUID);

    /**
     * set severity
     */
    MDCTransaction setSeverity(String severity);

    /**
     * set target entity
     */
    MDCTransaction setTargetEntity(String targetEntity);

    /**
     * set target service name
     */
    MDCTransaction setTargetServiceName(String targetServiceName);

    /**
     * set target virtual entity
     */
    MDCTransaction setTargetVirtualEntity(String targetVirtualEntity);

    /**
     * set request id
     */
    MDCTransaction setRequestId(String requestId);

    /**
     * set partner
     */
    MDCTransaction setPartner(String partner);

    /**
     * set server
     */
    MDCTransaction setServer(String server);

    /**
     * set server ip address
     */
    MDCTransaction setServerIpAddress(String serverIpAddress);

    /**
     * set server fqdn
     */
    MDCTransaction setServerFqdn(String serverFqdn);

    /**
     * set virtual server
     */
    MDCTransaction setVirtualServerName(String virtualServerName);
    /**
     * sets end time
     */
    MDCTransaction setEndTime(Instant endTime);

    /**
     * sets elapsed time
     */
    MDCTransaction setElapsedTime(Long elapsedTime);

    /**
     * sets service instance id
     */
    MDCTransaction setServiceInstanceId(String serviceInstanceId);

    /**
     * sets process key
     */
    MDCTransaction setProcessKey(String processKey);

    /**
     * sets alert severity
     */
    MDCTransaction setAlertSeverity(String alertSeverity);

    /**
     * sets client ip address
     */
    MDCTransaction setClientIpAddress(String clientIpAddress);

    /**
     * sets remote host
     */
    MDCTransaction setRemoteHost(String remoteHost);

    /**
     * get start time
     */
    Instant getStartTime();

    /**
     * get server
     */
    String getServer();

    /**
     * get end time
     */
    Instant getEndTime();

    /**
     * get elapsed time
     */
    Long getElapsedTime();

    /**
     * get remote host
     */
    String getRemoteHost();

    /**
     * get client ip address
     */
    String getClientIpAddress();

    /**
     * get alert severity
     */
    String getAlertSeverity();

    /**
     * get process key
     */
    String getProcessKey();

    /**
     * get service instance id
     */
    String getServiceInstanceId();

    /**
     * get invocation id
     */
    String getInvocationId();

    /**
     * get service name
     */
    String getServiceName();

    /**
     * get status code
     */
    String getStatusCode();

    /**
     * get response description
     */
    String getResponseDescription();

    /**
     * get instance uuid
     */
    String getInstanceUUID();

    /**
     * get severity
     */
    String getSeverity();

    /**
     * get target entity
     */
    String getTargetEntity();

    /**
     * get service name
     */
    String getTargetServiceName();

    /**
     * get target virtual entity
     */
    String getTargetVirtualEntity();

    /**
     * get response code
     */
    String getResponseCode();

    /**
     * get request id
     */
    String getRequestId();

    /**
     * get partner
     */
    String getPartner();

    /**
     * get server fqdn
     */
    String getServerFqdn();

    /**
     * get virtual server name
     */
    String getVirtualServerName();

    /**
     * get server ip
     */
    String getServerIpAddress();

    /**
     * generate timestamp used for logging
     */
    String timestamp(Instant time);

    /**
     * create new MDC Transaction
     *
     * @param requestId transaction Id
     * @param partner requesting partner
     *
     * @return MDC Transaction
     */
    static MDCTransaction newTransaction(String requestId, String partner) {
        return new MDCTransactionImpl(requestId, partner);
    }

    /**
     * create new MDC Transaction
     */
    static MDCTransaction newTransaction() {
        return new MDCTransactionImpl();
    }

    /**
     * create new subtransaction
     *
     * @param invocationId sub-transaction od
     * @return MDC Transaction
     */
    static MDCTransaction newSubTransaction(String invocationId) {
        return new MDCTransactionImpl(invocationId);
    }

    /**
     * create transaction from an existing one
     *
     * @param transaction transaction
     * @return MDC Transaction
     */
    static MDCTransaction fromTransaction(MDCTransaction transaction) {
        return new MDCTransactionImpl(transaction);
    }

}

class MDCTransactionImpl implements MDCTransaction {

    private final static Logger logger = LoggerFactory.getLogger(MDCTransactionImpl.class.getName());

    /**
     * Logging Format for Timestamps
     */

    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS+00:00";

    /* transaction inheritable fields */

    private String requestId;
    private String partner;

    private String invocationId;
    private String virtualServerName;
    private String mdcServer;
    private String serverIpAddress;
    private String serverFqdn;

    private String serviceName;

    private Instant startTime;
    private Instant endTime;
    private Long elapsedTime;

    private String serviceInstanceId;
    private String instanceUUID;
    private String processKey;

    private String statusCode;
    private String responseCode;
    private String responseDescription;
    private String mdcSeverity;
    private String alertSeverity;

    private String targetEntity;
    private String targetServiceName;
    private String targetVirtualEntity;
    private String clientIpAddress;
    private String remoteHost;

    /**
     * Transaction with no information set
     */
    public MDCTransactionImpl() {
        MDC.clear();
    }

    /**
     * MDC Transaction
     *
     * @param requestId  transaction id
     * @param partner    transaction origin
     */
    public MDCTransactionImpl(String requestId, String partner) {
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
     * create subtransaction
     *
     * @param invocationId subtransaction id
     */
    public MDCTransactionImpl(String invocationId) {
        this.resetSubTransaction();

        this.setRequestId(MDC.get(REQUEST_ID));
        this.setPartner(MDC.get(PARTNER_NAME));
        this.setServiceName(MDC.get(SERVICE_NAME));
        this.setServer(MDC.get(SERVER));
        this.setServerIpAddress(MDC.get(SERVER_IP_ADDRESS));
        this.setServerFqdn(MDC.get(SERVER_FQDN));
        this.setVirtualServerName(MDC.get(VIRTUAL_SERVER_NAME));

        this.setStartTime(Instant.now());
        this.setInvocationId(invocationId);
    }

    /**
     * copy constructor transaction/subtransaction
     *
     * @param transaction
     */
    public MDCTransactionImpl(MDCTransaction transaction) {
        MDC.clear();
        this.setAlertSeverity(transaction.getAlertSeverity());
        this.setClientIpAddress(transaction.getClientIpAddress());
        this.setElapsedTime(transaction.getElapsedTime());
        this.setEndTime(transaction.getEndTime());
        this.setInstanceUUID(transaction.getInstanceUUID());
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
    }

    /**
     * reset subtransaction portion
     *
     * @return MDCTransaction
     */
    @Override
    public MDCTransaction resetSubTransaction() {
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
       MDC.remove(ALERT_SEVERITY);
       MDC.remove(TARGET_VIRTUAL_ENTITY);

       return this;
    }

    @Override
    public MDCTransaction resetTransaction() {
        MDC.clear();
        return this;
    }

    /**
     * flush transaction to MDC
     */
    @Override
    public MDCTransaction flush() {
        if (this.requestId != null && !this.requestId.isEmpty())
            MDC.put(REQUEST_ID, this.requestId);

        if (this.invocationId != null && !this.invocationId.isEmpty())
            MDC.put(INVOCATION_ID, this.invocationId);

        if (this.partner != null)
            MDC.put(PARTNER_NAME, this.partner);

        if (this.virtualServerName != null)
            MDC.put(VIRTUAL_SERVER_NAME, this.virtualServerName);

        if (this.mdcServer != null)
            MDC.put(SERVER, this.mdcServer);

        if (this.serverIpAddress != null)
            MDC.put(SERVER_IP_ADDRESS, this.serverIpAddress);

        if (this.serverFqdn != null)
            MDC.put(SERVER_FQDN, this.serverFqdn);

        if (this.serviceName != null)
            MDC.put(SERVICE_NAME, this.serviceName);

        if (this.startTime != null)
            MDC.put(BEGIN_TIMESTAMP, timestamp(this.startTime));

        if (this.endTime != null) {
            MDC.put(END_TIMESTAMP, timestamp(this.endTime));
        } else {
            this.setEndTime(null);
            MDC.put(END_TIMESTAMP, timestamp(this.endTime));
        }

        if (this.elapsedTime != null) {
            MDC.put(ELAPSED_TIME, String.valueOf(this.elapsedTime));
        } else {
            if (endTime != null && startTime != null) {
                 this.elapsedTime = Duration.between(startTime, endTime).toMillis();
                MDC.put(ELAPSED_TIME, String.valueOf(this.elapsedTime));
            }
        }

        if (this.serviceInstanceId != null)
            MDC.put(SERVICE_INSTANCE_ID, this.serviceInstanceId);

        if (this.instanceUUID != null)
            MDC.put(INSTANCE_UUID, this.instanceUUID);

        if (this.processKey != null)
            MDC.put(PROCESS_KEY, this.processKey);

        if (this.statusCode != null)
            MDC.put(STATUS_CODE, this.statusCode);

        if (this.responseCode != null)
            MDC.put(RESPONSE_CODE, this.responseCode);

        if (this.responseDescription != null)
            MDC.put(RESPONSE_DESCRIPTION, this.responseDescription);

        if (this.mdcSeverity != null)
            MDC.put(SEVERITY, this.mdcSeverity);

        if (this.alertSeverity != null)
            MDC.put(ALERT_SEVERITY, this.alertSeverity);

        if (this.targetEntity != null)
            MDC.put(TARGET_ENTITY, this.targetEntity);

        if (this.targetServiceName != null)
            MDC.put(TARGET_SERVICE_NAME, this.targetServiceName);

        if (this.targetVirtualEntity != null)
            MDC.put(TARGET_VIRTUAL_ENTITY, this.targetVirtualEntity);

        if (this.clientIpAddress != null)
            MDC.put(CLIENT_IP_ADDRESS, this.clientIpAddress);

        if (this.remoteHost != null)
            MDC.put(REMOTE_HOST, this.remoteHost);

        return this;
    }

    @Override
    public MDCTransaction metric() {
        this.flush();
        logger.info(LoggerUtil.METRIC_LOG_MARKER, "");
        return this;
    }

    @Override
    public MDCTransaction transaction() {
        this.flush();
        logger.info(LoggerUtil.TRANSACTION_LOG_MARKER, "");
        return this;
    }

    @Override
    public MDCTransaction setEndTime(Instant endTime) {
        if (endTime == null) {
            this.endTime = Instant.now();
        } else {
            this.endTime = endTime;
        }
        return this;
    }

    @Override
    public MDCTransaction setElapsedTime(Long elapsedTime) {
        this.elapsedTime = elapsedTime;
        return this;
    }

    @Override
    public MDCTransaction setServiceInstanceId(String serviceInstanceId) {
        this.serviceInstanceId = serviceInstanceId;
        return this;
    }

    @Override
    public MDCTransaction setProcessKey(String processKey) {
        this.processKey = processKey;
        return this;
    }

    @Override
    public MDCTransaction setAlertSeverity(String alertSeverity) {
        this.alertSeverity = alertSeverity;
        return this;
    }

    @Override
    public MDCTransaction setClientIpAddress(String clientIpAddress) {
        this.clientIpAddress = clientIpAddress;
        return this;
    }

    @Override
    public MDCTransaction setRemoteHost(String remoteHost) {
        this.remoteHost = remoteHost;
        return this;
    }

    @Override
    public Instant getStartTime() {
        return this.startTime;
    }

    @Override
    public String getServer() {
        return this.mdcServer;
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
    public String getAlertSeverity() {
        return this.alertSeverity;
    }

    @Override
    public String getProcessKey() {
        return this.processKey;
    }

    @Override
    public String getServiceInstanceId() {
        return this.serviceInstanceId;
    }

    /* transaction and subtransaction fields */

    @Override
    public MDCTransaction setInvocationId(String invocationId) {
        if (invocationId == null) {
            this.invocationId = UUID.randomUUID().toString();
        } else {
            this.invocationId = invocationId;
        }

        MDC.put(INVOCATION_ID, this.invocationId);

        return this;
    }

    @Override
    public MDCTransaction setStartTime(Instant startTime) {
        if (startTime == null) {
            this.startTime = Instant.now();
        } else {
            this.startTime = startTime;
        }

        MDC.put(BEGIN_TIMESTAMP, this.timestamp(this.startTime));

        return this;
    }

    @Override
    public MDCTransaction setServiceName(String serviceName) {
        if (serviceName == null || serviceName.isEmpty()) {
            this.serviceName = DEFAULT_SERVICE_NAME;
        } else {
            this.serviceName = serviceName;
        }

        MDC.put(SERVICE_NAME, this.serviceName);

        return this;
    }

    @Override
    public MDCTransaction setStatusCode(String statusCode) {
        this.statusCode = statusCode;
        return this;
    }

    @Override
    public MDCTransaction setStatusCode(boolean success) {
        if (success) {
            this.statusCode = STATUS_CODE_COMPLETE;
        } else {
            this.statusCode = STATUS_CODE_FAILURE;
        }
        return this;
    }

    @Override
    public MDCTransaction setResponseCode(String responseCode) {
        this.responseCode = responseCode;
        return this;
    }

    @Override
    public MDCTransaction setResponseDescription(String responseDescription) {
        this.responseDescription = responseDescription;
        return this;
    }

    @Override
    public MDCTransaction setInstanceUUID(String instanceUUID) {
        if (instanceUUID == null) {
            this.instanceUUID = UUID.randomUUID().toString();
        } else {
            this.instanceUUID = instanceUUID;
        }

        MDC.put(INSTANCE_UUID, this.instanceUUID);
        return this;
    }

    @Override
    public MDCTransaction setSeverity(String severity) {
        this.mdcSeverity = severity;
        return this;
    }

    @Override
    public MDCTransaction setTargetEntity(String targetEntity) {
        this.targetEntity = targetEntity;
        return this;
    }

    @Override
    public MDCTransaction setTargetServiceName(String targetServiceName) {
        this.targetServiceName = targetServiceName;
        return this;
    }

    @Override
    public MDCTransaction setTargetVirtualEntity(String targetVirtualEntity) {
        this.targetVirtualEntity = targetVirtualEntity;
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
    public String getInstanceUUID() {
        return instanceUUID;
    }

    @Override
    public String getSeverity() {
        return mdcSeverity;
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
    public MDCTransaction setRequestId(String requestId) {
        if (requestId == null || requestId.isEmpty()) {
            this.requestId = UUID.randomUUID().toString();
        } else {
            this.requestId = requestId;
        }

        MDC.put(REQUEST_ID, this.requestId);
        return this;
    }

    @Override
    public MDCTransaction setPartner(String partner) {
        if (partner == null || partner.isEmpty()) {
            this.partner = DEFAULT_SERVICE_NAME;
        } else {
            this.partner = partner;
        }

        MDC.put(PARTNER_NAME, this.partner);
        return this;
    }

    @Override
    public MDCTransaction setServer(String server) {
        if (server == null || server.isEmpty()) {
            this.mdcServer = DEFAULT_HOSTNAME;
        } else {
            this.mdcServer = server;
        }

        MDC.put(SERVER, this.mdcServer);
        return this;
    }

    @Override
    public MDCTransaction setServerIpAddress(String serverIpAddress) {
        if (serverIpAddress == null || serverIpAddress.isEmpty()) {
            this.serverIpAddress = DEFAULT_HOSTIP;
        } else {
            this.serverIpAddress = serverIpAddress;
        }

        MDC.put(SERVER_IP_ADDRESS, this.serverIpAddress);
        return this;
    }

    @Override
    public MDCTransaction setServerFqdn(String serverFqdn) {
        if (serverFqdn == null || serverFqdn.isEmpty()) {
            this.serverFqdn = DEFAULT_HOSTNAME;
        } else {
            this.serverFqdn = serverFqdn;
        }

        MDC.put(SERVER_FQDN, this.serverFqdn);
        return this;
    }

    @Override
    public MDCTransaction setVirtualServerName(String virtualServerName) {
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
        return new SimpleDateFormat(DATE_FORMAT).format(Date.from(time));
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("MDCTransaction{");
        sb.append("requestId='").append(requestId).append('\'');
        sb.append(", partner='").append(partner).append('\'');
        sb.append(", invocationId='").append(invocationId).append('\'');
        sb.append(", virtualServerName='").append(virtualServerName).append('\'');
        sb.append(", server='").append(mdcServer).append('\'');
        sb.append(", serverIpAddress='").append(serverIpAddress).append('\'');
        sb.append(", serverFqdn='").append(serverFqdn).append('\'');
        sb.append(", serviceName='").append(serviceName).append('\'');
        sb.append(", startTime=").append(startTime);
        sb.append(", endTime=").append(endTime);
        sb.append(", elapsedTime=").append(elapsedTime);
        sb.append(", serviceInstanceId='").append(serviceInstanceId).append('\'');
        sb.append(", instanceUUID='").append(instanceUUID).append('\'');
        sb.append(", processKey='").append(processKey).append('\'');
        sb.append(", statusCode='").append(statusCode).append('\'');
        sb.append(", responseCode='").append(responseCode).append('\'');
        sb.append(", responseDescription='").append(responseDescription).append('\'');
        sb.append(", severity='").append(mdcSeverity).append('\'');
        sb.append(", alertSeverity='").append(alertSeverity).append('\'');
        sb.append(", targetEntity='").append(targetEntity).append('\'');
        sb.append(", targetServiceName='").append(targetServiceName).append('\'');
        sb.append(", targetVirtualEntity='").append(targetVirtualEntity).append('\'');
        sb.append(", clientIpAddress='").append(clientIpAddress).append('\'');
        sb.append(", remoteHost='").append(remoteHost).append('\'');
        sb.append('}');
        return sb.toString();
    }

}
