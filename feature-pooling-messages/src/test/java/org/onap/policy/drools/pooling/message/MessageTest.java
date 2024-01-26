/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2024 Nordix Foundation.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class MessageTest extends SupportBasicMessageTester<Message> {

    public MessageTest() {
        super(Message.class);
    }

    @Test
    void testGetSource_testSetSource() {
        Message msg = new Message();

        msg.setSource("hello");
        assertEquals("hello", msg.getSource());
        assertNull(msg.getChannel());

        msg.setSource("world");
        assertEquals("world", msg.getSource());
        assertNull(msg.getChannel());
    }

    @Test
    void testGetChannel_testSetChannel() {
        Message msg = new Message();

        msg.setChannel("hello");
        assertEquals("hello", msg.getChannel());
        assertNull(msg.getSource());

        msg.setChannel("world");
        assertEquals("world", msg.getChannel());
        assertNull(msg.getSource());
    }

    @Test
    void testCheckValidity_InvalidFields() {
        // null or empty source
        expectCheckValidityFailure_NullOrEmpty((msg, value) -> msg.setSource(value));

        // null or empty channel
        expectCheckValidityFailure_NullOrEmpty((msg, value) -> msg.setChannel(value));
    }

    /**
     * Makes a message that will pass the validity check.
     * 
     * @return a valid Message
     */
    public Message makeValidMessage() {
        Message msg = new Message(VALID_HOST);
        msg.setChannel(VALID_CHANNEL);

        return msg;
    }

}
