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

public class HeartbeatTest extends BasicMessageTester<Heartbeat> {
    
    /**
     * Sequence number to validate time stamps within the heart beat.
     */
    private long sequence = 0;

    public HeartbeatTest() {
        super(Heartbeat.class);
    }

    /**
     * Makes a message that will pass the validity check.
     * 
     * @return a valid Message
     */
    public Heartbeat makeValidMessage() {
        Heartbeat msg = new Heartbeat(VALID_HOST, ++sequence);
        msg.setChannel(VALID_CHANNEL);

        return msg;
    }

    @Override
    public void testDefaultConstructorFields(Heartbeat msg) {
        super.testDefaultConstructorFields(msg);
        
        assertEquals(sequence, msg.getTimestampMs());
    }

    @Override
    public void testValidFields(Heartbeat msg) {
        super.testValidFields(msg);
        
        assertEquals(sequence, msg.getTimestampMs());
    }

}
