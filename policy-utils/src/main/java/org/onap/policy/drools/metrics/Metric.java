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

    protected static final String HOSTNAME = NetworkUtil.getHostname();
    protected static final String HOSTIP = NetworkUtil.getHostIp();
    protected static final String HOST_TYPE = "PDP-D";

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
        if (endTime != null) {
            this.endTime = endTime;
        } else {
            this.endTime = Instant.now();
        }
    }

    /**
     * sets the start time.
     */
    public void setStartTime(Instant startTime) {
        if (startTime != null) {
            this.startTime = startTime;
        } else {
            this.startTime = Instant.now();
        }
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
        if (invocationId != null) {
            this.invocationId = invocationId;
        } else {
            this.invocationId = UUID.randomUUID().toString();
        }
    }

    /**
     * sets the service name.
     */
    public void setServiceName(String serviceName) {
        if (serviceName != null) {
            this.serviceName = serviceName;
        } else {
            this.serviceName = HOST_TYPE;
        }
    }

    /**
     * sets the instance uuid.
     */
    public void setInstanceUuid(String instanceUuid) {
        if (instanceUuid != null) {
            this.instanceUuid = instanceUuid;
        } else {
            this.instanceUuid = UUID.randomUUID().toString();
        }
    }

    /**
     * sets the request id.
     */
    public void setRequestId(String requestId) {
        if (requestId != null) {
            this.requestId = requestId;
        } else {
            this.requestId = UUID.randomUUID().toString();
        }
    }

    /**
     * sets the partner.
     */
    public void setPartner(String partner) {
        if (partner != null) {
            this.partner = partner;
        } else {
            this.partner = HOST_TYPE;
        }
    }

    /**
     * sets the server name.
     */
    public void setServerName(String server) {
        if (server != null) {
            this.serverName = server;
        } else {
            this.serverName = HOSTNAME;
        }
    }

    /**
     * sets the server IP address.
     */
    public void setServerIpAddress(String serverIpAddress) {
        if (serverIpAddress != null) {
            this.serverIpAddress = serverIpAddress;
        } else {
            this.serverIpAddress = HOSTIP;
        }
    }

    /**
     * sets the server FQDN.
     */
    public void setServerFqdn(String serverFqdn) {
        if (serverFqdn != null) {
            this.serverFqdn = serverFqdn;
        } else {
            this.serverFqdn = HOSTNAME;
        }
    }

    /**
     * sets the virtual server name.
     */
    public void setVirtualServerName(String virtualServerName) {
        if (virtualServerName != null) {
            this.virtualServerName = virtualServerName;
        } else {
            this.virtualServerName = HOSTNAME;
        }
    }
}
