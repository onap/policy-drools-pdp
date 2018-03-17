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

package org.onap.policy.drools.pooling;

import java.io.IOException;
import java.util.Map;
import org.onap.policy.drools.pooling.message.Message;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Serialization helper functions.
 */
public class Serializer {

    /**
     * Used to encode & decode JSON messages sent & received, respectively, on
     * the internal DMaaP topic.
     */
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 
     */
    public Serializer() {
        super();
    }

    /**
     * Encodes a filter.
     * 
     * @param filter filter to be encoded
     * @return the filter, serialized as a JSON string
     * @throws JsonProcessingException if it cannot be serialized
     */
    public String encodeFilter(Map<String, Object> filter) throws JsonProcessingException {
        return mapper.writeValueAsString(filter);
    }

    /**
     * Encodes a message.
     * 
     * @param msg message to be encoded
     * @return the message, serialized as a JSON string
     * @throws JsonProcessingException if it cannot be serialized
     */
    public String encodeMsg(Message msg) throws JsonProcessingException {
        return mapper.writeValueAsString(msg);
    }

    /**
     * Decodes a JSON string into a Message.
     * 
     * @param msg JSON string representing the message
     * @return the message
     * @throws IOException if it cannot be serialized
     */
    public Message decodeMsg(String msg) throws IOException {
        return mapper.readValue(msg, Message.class);
    }
}
