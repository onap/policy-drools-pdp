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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.onap.policy.drools.event.comm.Topic.CommInfrastructure;

public class ForwardTest extends BasicMessageTester<Forward> {
    // values set by makeValidMessage()
    public static final CommInfrastructure VALID_PROTOCOL = CommInfrastructure.UEB;
    public static final int VALID_HOPS = 0;
    public static final String VALID_TOPIC = "topicA";
    public static final String VALID_PAYLOAD = "payloadA";
    public static final String VALID_REQUEST_ID = "requestIdA";

    /**
     * Time, in milliseconds, after which the most recent message was created.
     */
    private static long tcreateMs;

    public ForwardTest() {
        super(Forward.class);
    }

    @Test
    public void testBumpNumHops() {
        Forward msg = makeValidMessage();

        for (int x = 0; x < 3; ++x) {
            assertEquals("x=" + x, x, msg.getNumHops());
            msg.bumpNumHops();
        }
    }

    @Test
    public void testGetNumHops_testSetNumHops() {
        Forward msg = makeValidMessage();

        // from constructor
        assertEquals(VALID_HOPS, msg.getNumHops());

        msg.setNumHops(5);
        assertEquals(5, msg.getNumHops());

        msg.setNumHops(7);
        assertEquals(7, msg.getNumHops());
    }

    @Test
    public void testGetCreateTimeMs_testSetCreateTimeMs() {
        Forward msg = makeValidMessage();

        // from constructor
        assertTrue(msg.getCreateTimeMs() >= tcreateMs);

        msg.setCreateTimeMs(1000L);
        assertEquals(1000L, msg.getCreateTimeMs());

        msg.setCreateTimeMs(2000L);
        assertEquals(2000L, msg.getCreateTimeMs());
    }

    @Test
    public void testGetProtocol_testSetProtocol() {
        Forward msg = makeValidMessage();

        // from constructor
        assertEquals(CommInfrastructure.UEB, msg.getProtocol());

        msg.setProtocol(CommInfrastructure.DMAAP);
        assertEquals(CommInfrastructure.DMAAP, msg.getProtocol());

        msg.setProtocol(CommInfrastructure.UEB);
        assertEquals(CommInfrastructure.UEB, msg.getProtocol());
    }

    @Test
    public void testGetTopic_testSetTopic() {
        Forward msg = makeValidMessage();

        // from constructor
        assertEquals(VALID_TOPIC, msg.getTopic());

        msg.setTopic("topicX");
        assertEquals("topicX", msg.getTopic());

        msg.setTopic("topicY");
        assertEquals("topicY", msg.getTopic());
    }

    @Test
    public void testGetPayload_testSetPayload() {
        Forward msg = makeValidMessage();

        // from constructor
        assertEquals(VALID_PAYLOAD, msg.getPayload());

        msg.setPayload("payloadX");
        assertEquals("payloadX", msg.getPayload());

        msg.setPayload("payloadY");
        assertEquals("payloadY", msg.getPayload());
    }

    @Test
    public void testGetRequestId_testSetRequestId() {
        Forward msg = makeValidMessage();

        // from constructor
        assertEquals(VALID_REQUEST_ID, msg.getRequestId());

        msg.setRequestId("reqX");
        assertEquals("reqX", msg.getRequestId());

        msg.setRequestId("reqY");
        assertEquals("reqY", msg.getRequestId());
    }

    @Test
    public void testIsExpired() {
        Forward msg = makeValidMessage();

        long tcreate = msg.getCreateTimeMs();
        assertTrue(msg.isExpired(tcreate + 1));
        assertTrue(msg.isExpired(tcreate + 10));

        assertFalse(msg.isExpired(tcreate));
        assertFalse(msg.isExpired(tcreate - 1));
        assertFalse(msg.isExpired(tcreate - 10));
    }

    @Test
    public void testCheckValidity_InvalidFields() throws Exception {
        // null source (i.e., superclass field)
        expectCheckValidityFailure(msg -> msg.setSource(null));
        
        // null protocol
        expectCheckValidityFailure(msg -> msg.setProtocol(null));
        
        // null or empty topic
        expectCheckValidityFailure_NullOrEmpty((msg, value) -> msg.setTopic(value));
        
        // null payload
        expectCheckValidityFailure(msg -> msg.setPayload(null));
        
        // empty payload should NOT throw an exception
        Forward forward = makeValidMessage();
        forward.setPayload("");
        forward.checkValidity();
        
        // null or empty requestId
        expectCheckValidityFailure_NullOrEmpty((msg, value) -> msg.setRequestId(value));
    }

    /**
     * Makes a message that will pass the validity check.
     * 
     * @return a valid Message
     */
    public Forward makeValidMessage() {
        tcreateMs = System.currentTimeMillis();

        Forward msg = new Forward(VALID_HOST, VALID_PROTOCOL, VALID_TOPIC, VALID_PAYLOAD, VALID_REQUEST_ID);
        msg.setChannel(VALID_CHANNEL);

        return msg;
    }

    @Override
    public void testDefaultConstructorFields(Forward msg) {
        super.testDefaultConstructorFields(msg);
        
        assertEquals(VALID_HOPS, msg.getNumHops());
        assertEquals(0, msg.getCreateTimeMs());
        assertNull(msg.getPayload());
        assertNull(msg.getProtocol());
        assertNull(msg.getRequestId());
        assertNull(msg.getTopic());
    }

    @Override
    public void testValidFields(Forward msg) {
        super.testValidFields(msg);
        
        assertEquals(VALID_HOPS, msg.getNumHops());
        assertTrue(msg.getCreateTimeMs() >= tcreateMs);
        assertEquals(VALID_PAYLOAD, msg.getPayload());
        assertEquals(VALID_PROTOCOL, msg.getProtocol());
        assertEquals(VALID_REQUEST_ID, msg.getRequestId());
        assertEquals(VALID_TOPIC, msg.getTopic());
    }
}
