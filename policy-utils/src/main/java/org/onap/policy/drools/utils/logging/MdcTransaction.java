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

import java.time.Instant;

/**
 * MDC Transaction Utility Class.
 *
 * <p>There is an implicit 2-level tree of Transactions in ONAP: transactions and subtransactions.
 *
 * <p>1. The top level transaction relates to the overarching transaction id (ie. RequestId) and should
 * be made available to subtransactions for reuse in the ThreadLocal MDC structure.
 *
 * <p>This is the data to be inherited and common to all subtransactions (not a common case but could
 * be modified by subtransactions):
 *
 * <p>Request ID Virtual Server Name Partner Name Server Server IP Address Server FQDN
 *
 * <p>2. The second level at the leaves is formed by subtransactions and the key identifier is the
 * invocation id.
 *
 * <p>Begin Timestamp End Timestamp Elapsed Time Service Instance ID Service Name Status Code Response
 * Code Response Description Instance UUID Severity Target Entity Target Service Name Server Server
 * IP Address Server FQDN Client IP Address Process Key Remote Host Alert Severity Target Virtual
 * Entity
 *
 *
 * <p>The naming convention for the fields must match the naming given at
 *
 * <p>https://wiki.onap.org/pages/viewpage.action?pageId=20087036
 */
public interface MdcTransaction {

    /**
     * reset subtransaction data.
     */
    MdcTransaction resetSubTransaction();

    /**
     * resets transaction data.
     */
    MdcTransaction resetTransaction();

    /**
     * flush to MDC structure.
     */
    MdcTransaction flush();

    /**
     * convenience method to log a metric. Alternatively caller could call flush() and the logging
     * statement directly for further granularity.
     */
    MdcTransaction metric();

    /**
     * convenience method to log a transaction record. Alternatively caller could call flush() and
     * the logging statement directly for further granularity.
     */
    MdcTransaction transaction();

    /**
     * get invocation id.
     */
    MdcTransaction setInvocationId(String invocationId);

    /**
     * set start time.
     */
    MdcTransaction setStartTime(Instant startTime);

    /**
     * set service name.
     */
    MdcTransaction setServiceName(String serviceName);

    /**
     * set status code.
     */
    MdcTransaction setStatusCode(String statusCode);

    /**
     * set status code.
     */
    MdcTransaction setStatusCode(boolean success);

    /**
     * sets response code.
     */
    MdcTransaction setResponseCode(String responseCode);

    /**
     * sets response description.
     */
    MdcTransaction setResponseDescription(String responseDescription);

    /**
     * sets instance uuid.
     */
    MdcTransaction setInstanceUuid(String instanceUuid);

    /**
     * set severity.
     */
    MdcTransaction setSeverity(String severity);

    /**
     * set target entity.
     */
    MdcTransaction setTargetEntity(String targetEntity);

    /**
     * set target service name.
     */
    MdcTransaction setTargetServiceName(String targetServiceName);

    /**
     * set target virtual entity.
     */
    MdcTransaction setTargetVirtualEntity(String targetVirtualEntity);

    /**
     * set request id.
     */
    MdcTransaction setRequestId(String requestId);

    /**
     * set partner.
     */
    MdcTransaction setPartner(String partner);

    /**
     * set server.
     */
    MdcTransaction setServer(String server);

    /**
     * set server ip address.
     */
    MdcTransaction setServerIpAddress(String serverIpAddress);

    /**
     * set server fqdn.
     */
    MdcTransaction setServerFqdn(String serverFqdn);

    /**
     * set virtual server.
     */
    MdcTransaction setVirtualServerName(String virtualServerName);

    /**
     * sets end time.
     */
    MdcTransaction setEndTime(Instant endTime);

    /**
     * sets elapsed time.
     */
    MdcTransaction setElapsedTime(Long elapsedTime);

    /**
     * sets service instance id.
     */
    MdcTransaction setServiceInstanceId(String serviceInstanceId);

    /**
     * sets process key.
     */
    MdcTransaction setProcessKey(String processKey);

    /**
     * sets client ip address.
     */
    MdcTransaction setClientIpAddress(String clientIpAddress);

    /**
     * sets remote host.
     */
    MdcTransaction setRemoteHost(String remoteHost);

    /**
     * sets CustomField1 data.
     */
    MdcTransaction setCustomField1(String customField1);

    /**
     * sets CustomField2 data.
     */
    MdcTransaction setCustomField2(String customField2);

    /**
     * sets CustomField3 data.
     */
    MdcTransaction setCustomField3(String customField3);

    /**
     * sets CustomField4 data.
     */
    MdcTransaction setCustomField4(String customField4);

    /**
     * get start time.
     */
    Instant getStartTime();

    /**
     * get server.
     */
    String getServer();

    /**
     * get end time.
     */
    Instant getEndTime();

    /**
     * get elapsed time.
     */
    Long getElapsedTime();

    /**
     * get remote host.
     */
    String getRemoteHost();

    /**
     * get client ip address.
     */
    String getClientIpAddress();

    /**
     * get process key.
     */
    String getProcessKey();

    /**
     * get service instance id.
     */
    String getServiceInstanceId();

    /**
     * get invocation id.
     */
    String getInvocationId();

    /**
     * get service name.
     */
    String getServiceName();

    /**
     * get status code.
     */
    String getStatusCode();

    /**
     * get response description.
     */
    String getResponseDescription();

    /**
     * get instance uuid.
     */
    String getInstanceUuid();

    /**
     * get severity.
     */
    String getSeverity();

    /**
     * get target entity.
     */
    String getTargetEntity();

    /**
     * get service name.
     */
    String getTargetServiceName();

    /**
     * get target virtual entity.
     */
    String getTargetVirtualEntity();

    /**
     * get response code.
     */
    String getResponseCode();

    /**
     * get request id.
     */
    String getRequestId();

    /**
     * get partner.
     */
    String getPartner();

    /**
     * get server fqdn.
     */
    String getServerFqdn();

    /**
     * get virtual server name.
     */
    String getVirtualServerName();

    /**
     * get server ip.
     */
    String getServerIpAddress();

    /**
     * get customer field1.
     */
    String getCustomField1();

    /**
     * get customer field2.
     */
    String getCustomField2();

    /**
     * get customer field3 which contains notification info.
     */
    String getCustomField3();

    /**
     * get customer field4.
     */
    String getCustomField4();

    /**
     * generate timestamp used for logging.
     */
    String timestamp(Instant time);

    /**
     * create new MDC Transaction.
     *
     * @param requestId transaction Id
     * @param partner requesting partner
     *
     * @return MDC Transaction
     */
    static MdcTransaction newTransaction(String requestId, String partner) {
        return new MdcTransactionImpl(requestId, partner);
    }

    /**
     * create new MDC Transaction.
     */
    static MdcTransaction newTransaction() {
        return new MdcTransactionImpl();
    }

    /**
     * create new subtransaction.
     *
     * @param invocationId sub-transaction od
     * @return MDC Transaction
     */
    static MdcTransaction newSubTransaction(String invocationId) {
        return new MdcTransactionImpl(invocationId);
    }

    /**
     * create transaction from an existing one.
     *
     * @param transaction transaction
     * @return MDC Transaction
     */
    static MdcTransaction fromTransaction(MdcTransaction transaction) {
        return new MdcTransactionImpl(transaction);
    }

}
