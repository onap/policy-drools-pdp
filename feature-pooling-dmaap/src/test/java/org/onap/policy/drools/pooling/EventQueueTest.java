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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.LinkedList;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.drools.event.comm.Topic.CommInfrastructure;
import org.onap.policy.drools.pooling.message.Forward;

public class EventQueueTest {

    private static final int MAX_SIZE = 5;
    private static final long MAX_AGE_MS = 3000L;

    private static final String MY_SOURCE = "my.source";
    private static final CommInfrastructure MY_PROTO = CommInfrastructure.UEB;
    private static final String MY_TOPIC = "my.topic";
    private static final String MY_PAYLOAD = "my.payload";
    private static final String MY_REQID = "my.request.id";

    private EventQueue queue;

    @Before
    public void setUp() {
        queue = new EventQueue(MAX_SIZE, MAX_AGE_MS);

    }

    @Test
    public void testEventQueue() {
        // shouldn't generate an exception
        new EventQueue(1, 1);
    }

    @Test
    public void testClear() {
        // add some items
        queue.add(makeActive());
        queue.add(makeActive());

        assertFalse(queue.isEmpty());

        queue.clear();

        // should be empty now
        assertTrue(queue.isEmpty());
    }

    @Test
    public void testIsEmpty() {
        // test when empty
        assertTrue(queue.isEmpty());

        // all active
        Forward msg1 = makeActive();
        Forward msg2 = makeActive();
        queue.add(msg1);
        assertFalse(queue.isEmpty());

        queue.add(msg2);
        assertFalse(queue.isEmpty());

        assertEquals(msg1, queue.poll());
        assertFalse(queue.isEmpty());

        assertEquals(msg2, queue.poll());
        assertTrue(queue.isEmpty());

        // active, expired, expired, active
        queue.add(msg1);
        queue.add(makeInactive());
        queue.add(makeInactive());
        queue.add(msg2);

        assertEquals(msg1, queue.poll());
        assertFalse(queue.isEmpty());

        assertEquals(msg2, queue.poll());
        assertTrue(queue.isEmpty());
    }

    @Test
    public void testSize() {
        queue = new EventQueue(2, 1000L);
        assertEquals(0, queue.size());

        queue.add(makeActive());
        assertEquals(1, queue.size());

        queue.poll();
        assertEquals(0, queue.size());

        queue.add(makeActive());
        queue.add(makeActive());
        assertEquals(2, queue.size());

        queue.poll();
        assertEquals(1, queue.size());

        queue.poll();
        assertEquals(0, queue.size());
    }

    @Test
    public void testAdd() {
        int nextra = 3;

        // create excess messages
        LinkedList<Forward> msgs = new LinkedList<>();
        for (int x = 0; x < MAX_SIZE + nextra; ++x) {
            msgs.add(makeActive());
        }

        // add them to the queue
        msgs.forEach(msg -> queue.add(msg));

        // should not have added too many messages
        assertEquals(MAX_SIZE, queue.size());

        // should have discarded the first "nextra" items
        for (int x = 0; x < MAX_SIZE; ++x) {
            assertEquals("x=" + x, msgs.get(x + nextra), queue.poll());
        }

        assertEquals(null, queue.poll());
    }

    @Test
    public void testPoll() {
        // poll when empty
        assertNull(queue.poll());

        // all active
        Forward msg1 = makeActive();
        Forward msg2 = makeActive();
        queue.add(msg1);
        queue.add(msg2);

        assertEquals(msg1, queue.poll());
        assertEquals(msg2, queue.poll());
        assertEquals(null, queue.poll());

        // active, expired, expired, active
        queue.add(msg1);
        queue.add(makeInactive());
        queue.add(makeInactive());
        queue.add(msg2);

        assertEquals(msg1, queue.poll());
        assertEquals(msg2, queue.poll());
        assertEquals(null, queue.poll());

        // one that's close to the age limit
        msg1 = makeActive();
        msg1.setCreateTimeMs(System.currentTimeMillis() - MAX_AGE_MS + 100);
        queue.add(msg1);
        assertEquals(msg1, queue.poll());
        assertEquals(null, queue.poll());
    }

    private Forward makeActive() {
        return new Forward(MY_SOURCE, MY_PROTO, MY_TOPIC, MY_PAYLOAD, MY_REQID);
    }

    private Forward makeInactive() {
        Forward msg = new Forward(MY_SOURCE, MY_PROTO, MY_TOPIC, MY_PAYLOAD, MY_REQID);

        msg.setCreateTimeMs(System.currentTimeMillis() - MAX_AGE_MS - 100);

        return msg;
    }

}
