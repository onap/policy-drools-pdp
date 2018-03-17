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

import org.onap.policy.drools.pooling.PoolingFeatureException;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Messages sent on internal topic.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({@Type(value = Forward.class, name = "forward"), @Type(value = Heartbeat.class, name = "heartbeat"),
                @Type(value = Identification.class, name = "identification"),
                @Type(value = Leader.class, name = "leader"), @Type(value = Offline.class, name = "offline"),
                @Type(value = Query.class, name = "query")})
public class Message {

    /**
     * Name of the admin channel.
     */
    public static final String ADMIN = "_admin";

    /**
     * Consumers can read from this channel if they don't wish to receive anything.
     */
    public static final String UNKNOWN = "_unknown";

    // TODO remove references to UUID (everywhere)
    // TODO change "target" to "channel" (everywhere)

    /**
     * Host the originated the message.
     */
    private String source;

    /**
     * Channel on which the message is routed, which is the target host or {@link #ADMIN}.
     */
    private String channel;

    public Message() {

    }

    public Message(String source) {
        this.source = source;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    @JsonIgnore
    public void checkValidity() throws PoolingFeatureException {
        if (source == null || source.isEmpty()) {
            throw new PoolingFeatureException("missing message source");
        }

        if (channel == null || channel.isEmpty()) {
            throw new PoolingFeatureException("missing message channel");
        }
    }

}
