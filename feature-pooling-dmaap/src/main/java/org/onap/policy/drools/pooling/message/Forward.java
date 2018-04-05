/*
 * ============LICENSE_START=======================================================
 * ONAP
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
 * ============LICENSE_END=========================================================
 */

package org.onap.policy.drools.pooling.message;

import org.onap.policy.drools.event.comm.Topic.CommInfrastructure;
import org.onap.policy.drools.pooling.PoolingFeatureException;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Message to forward an event to another host.
 */
public class Forward extends Message {

    /**
     * Number of hops (i.e., number of times it's been forwarded) so far.
     */
    private int numHops;

    /**
     * Time, in milliseconds, at which the message was created.
     */
    private long createTimeMs;

    /**
     * Protocol of the receiving topic.
     */
    private CommInfrastructure protocol;

    /**
     * Topic on which the event was received.
     */
    private String topic;

    /**
     * The event pay load that was received on the topic.
     */
    private String payload;

    /**
     * The request id that was extracted from the event.
     */
    private String requestId;

    /**
     * 
     */
    public Forward() {
        super();
    }

    /**
     * 
     * @param source host on which the message originated
     * @param protocol
     * @param topic
     * @param payload the actual event data received on the topic
     * @param requestId
     */
    public Forward(String source, CommInfrastructure protocol, String topic, String payload, String requestId) {
        super(source);

        this.numHops = 0;
        this.createTimeMs = System.currentTimeMillis();
        this.protocol = protocol;
        this.topic = topic;
        this.payload = payload;
        this.requestId = requestId;
    }

    /**
     * Increments {@link #numHops}.
     */
    public void bumpNumHops() {
        ++numHops;
    }

    public int getNumHops() {
        return numHops;
    }

    public void setNumHops(int numHops) {
        this.numHops = numHops;
    }

    public long getCreateTimeMs() {
        return createTimeMs;
    }

    public void setCreateTimeMs(long createTimeMs) {
        this.createTimeMs = createTimeMs;
    }

    public CommInfrastructure getProtocol() {
        return protocol;
    }

    public void setProtocol(CommInfrastructure protocol) {
        this.protocol = protocol;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    @JsonIgnore
    public boolean isExpired(long minCreateTimeMs) {
        return (createTimeMs < minCreateTimeMs);

    }

    @Override
    @JsonIgnore
    public void checkValidity() throws PoolingFeatureException {

        super.checkValidity();

        if (protocol == null) {
            throw new PoolingFeatureException("missing message protocol");
        }

        if (topic == null || topic.isEmpty()) {
            throw new PoolingFeatureException("missing message topic");
        }

        /*
         * Note: an empty pay load is OK, as an empty message could have been
         * received on the topic.
         */

        if (payload == null) {
            throw new PoolingFeatureException("missing message payload");
        }

        if (requestId == null || requestId.isEmpty()) {
            throw new PoolingFeatureException("missing message requestId");
        }

        if (numHops < 0) {
            throw new PoolingFeatureException("invalid message hop count");
        }
    }

}
