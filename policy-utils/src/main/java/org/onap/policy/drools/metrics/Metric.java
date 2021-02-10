/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2021 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.metrics;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.onap.policy.common.utils.network.NetworkUtil;

/**
 * Metric Record. This class is a model of the data
 * used for both, logging and statistics.
 */

@Data
@NoArgsConstructor
@ToString
@EqualsAndHashCode
public class Metric {

    /**
     * Logging Format for Timestamps.
     */
    protected static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS+00:00";

    /**
     * Host name.
     */
    public static final String HOSTNAME = NetworkUtil.getHostname();

    /**
     * Host IP address.
     */
    public static final String HOSTIP = NetworkUtil.getHostIp();

    /**
     * Host Type.
     */
    public static final String HOST_TYPE = "PDP-D";

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
    private String customField1;
    private String customField2;
    private String customField3;
    private String customField4;

    private boolean success = false;

    /**
     * converts time to timestamp with format yyyy-MM-dd'T'HH:mm:ss.SSS+00:00.
     */
    public static String toTimestamp(Instant time) {
        return new SimpleDateFormat(DATE_FORMAT).format(Date.from((time != null) ? time : Instant.now()));
    }

    /* override lombok setters for some data members */

    /**
     * sets the end time.
     */
    public void setEndTime(Instant endTime) {
        this.endTime = Optional.ofNullable(endTime).orElseGet(Instant::now);
    }

    /**
     * sets the start time.
     */
    public void setStartTime(Instant startTime) {
        this.startTime = Optional.ofNullable(startTime).orElseGet(Instant::now);
    }

    /**
     * sets the elapsed time.
     */
    public void setElapsedTime(Long elapsedTime) {
        if (elapsedTime != null) {
            this.elapsedTime = elapsedTime;
            return;
        }

        if (endTime != null && startTime != null) {
            this.elapsedTime =
                    Duration.between(startTime, endTime).toMillis();
            return;
        }

        this.elapsedTime = null;
    }

    /**
     * sets the invocation id.
     */
    public void setInvocationId(String invocationId) {
        this.invocationId = Optional.ofNullable(invocationId).orElseGet(UUID.randomUUID()::toString);
    }

    /**
     * sets the service name.
     */
    public void setServiceName(String serviceName) {
        this.serviceName = Optional.ofNullable(serviceName).orElse(HOST_TYPE);
    }

    /**
     * sets the instance uuid.
     */
    public void setInstanceUuid(String instanceUuid) {
        this.instanceUuid = Optional.ofNullable(instanceUuid).orElseGet(UUID.randomUUID()::toString);
    }

    /**
     * sets the request id.
     */
    public void setRequestId(String requestId) {
        this.requestId = Optional.ofNullable(requestId).orElseGet(UUID.randomUUID()::toString);
    }

    /**
     * sets the partner.
     */
    public void setPartner(String partner) {
        this.partner = Optional.ofNullable(partner).orElse(HOST_TYPE);
    }

    /**
     * sets the server name.
     */
    public void setServerName(String server) {
        this.serverName = Optional.ofNullable(server).orElse(HOSTNAME);
    }

    /**
     * sets the server IP address.
     */
    public void setServerIpAddress(String serverIpAddress) {
        this.serverIpAddress = Optional.ofNullable(serverIpAddress).orElse(HOSTIP);
    }

    /**
     * sets the server FQDN.
     */
    public void setServerFqdn(String serverFqdn) {
        this.serverFqdn = Optional.ofNullable(serverFqdn).orElse(HOSTNAME);
    }

    /**
     * sets the virtual server name.
     */
    public void setVirtualServerName(String virtualServerName) {
        this.virtualServerName = Optional.ofNullable(virtualServerName).orElse(HOSTNAME);
    }
}
