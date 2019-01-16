/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018-2019 AT&T Intellectual Property. All rights reserved.
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

/**
 * Messages sent on the internal topic.
 */
public class Message {

    /**
     * Name of the administrative channel.
     */
    public static final String ADMIN = "_admin";

    /**
     * Host that originated the message.
     */
    private String source;

    /**
     * Channel on which the message is routed, which is either the target host
     * or {@link #ADMIN}.
     */
    private String channel;

    /**
     * Constructor.
     */
    public Message() {
        super();
    }

    /**
     * Constructor.
     * 
     * @param source host on which the message originated
     */
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

    /**
     * Checks the validity of the message, including verifying that required
     * fields are not missing.
     * 
     * @throws PoolingFeatureException if the message is invalid
     */
    public void checkValidity() throws PoolingFeatureException {
        if (source == null || source.isEmpty()) {
            throw new PoolingFeatureException("missing message source");
        }

        if (channel == null || channel.isEmpty()) {
            throw new PoolingFeatureException("missing message channel");
        }
    }

}
