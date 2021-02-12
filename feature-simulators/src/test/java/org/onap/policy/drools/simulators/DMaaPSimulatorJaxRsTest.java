/*-
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2019, 2021 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.simulators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doThrow;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.servlet.http.HttpServletResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DMaaPSimulatorJaxRsTest {
    private static final String MESSAGE = "hello";
    private static final String MESSAGE2 = "world";
    private static final String EXPECTED_EXCEPTION = "expected exception";
    private static final String TOPIC = "my-topic";
    private static final int TIMEOUT_MS = 10;
    private static final int LONG_TIMEOUT_MS = 250;

    @Mock
    private HttpServletResponse resp;

    private DMaaPSimulatorJaxRs sim;

    /**
     * Initializes objects and creates the simulator.
     */
    @Before
    public void setUp() {
        sim = new DMaaPSimulatorJaxRs();
    }

    @After
    public void tearDown() {
        DMaaPSimulatorJaxRs.reset();
    }

    @Test
    public void testSubscribe() {
        sim.publish(TOPIC, MESSAGE);

        assertEquals(MESSAGE, sim.subscribe(0, TOPIC, resp));
    }

    @Test
    public void testSubscribe_FlushEx() throws IOException {
        doThrow(new IOException(EXPECTED_EXCEPTION)).when(resp).flushBuffer();

        assertEquals("Got an error", sim.subscribe(TIMEOUT_MS, TOPIC, resp));
    }

    @Test
    public void testSubscribe_BadStatus_testSetResponseCode() {
        sim.setStatus(199);
        assertEquals("You got response code: 199", sim.subscribe(TIMEOUT_MS, TOPIC, resp));

        sim.setStatus(300);
        assertEquals("You got response code: 300", sim.subscribe(TIMEOUT_MS, TOPIC, resp));
    }

    @Test
    public void testSubscribe_UnknownTopic_ZeroTimeout() {
        assertEquals(DMaaPSimulatorJaxRs.NO_TOPIC_MSG, sim.subscribe(0, TOPIC, resp));
    }

    @Test
    public void testSubscribe_UnknownTopic_NonZeroTimeout() {
        assertEquals(DMaaPSimulatorJaxRs.NO_TOPIC_MSG, sim.subscribe(TIMEOUT_MS, TOPIC, resp));
    }

    @Test
    public void testGetNextMessageFromQueue() {
        sim.publish(TOPIC, MESSAGE);
        sim.publish(TOPIC, MESSAGE2);

        assertEquals(MESSAGE, sim.subscribe(0, TOPIC, resp));
        assertEquals(MESSAGE2, sim.subscribe(0, TOPIC, resp));

        // repeat - no message
        assertEquals(DMaaPSimulatorJaxRs.NO_DATA_MSG, sim.subscribe(0, TOPIC, resp));
    }

    @Test
    public void testGetNextMessageFromQueue_Interrupted() throws InterruptedException {
        sim = new DMaaPSimulatorJaxRs() {
            @Override
            protected String poll(BlockingQueue<String> queue, int timeout) throws InterruptedException {
                throw new InterruptedException(EXPECTED_EXCEPTION);
            }
        };

        sim.publish(TOPIC, MESSAGE);

        // put it in the background so we don't interrupt the test thread
        BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        backgroundSubscribe(queue);

        assertEquals(DMaaPSimulatorJaxRs.NO_DATA_MSG, queue.take());
    }

    @Test
    public void testWaitForNextMessageFromQueue() throws InterruptedException {
        CountDownLatch waitCalled = new CountDownLatch(1);

        sim = new DMaaPSimulatorJaxRs() {
            @Override
            protected String waitForNextMessageFromQueue(int timeout, String topicName) {
                waitCalled.countDown();
                return super.waitForNextMessageFromQueue(timeout, topicName);
            }
        };

        BlockingQueue<String> queue = new LinkedBlockingQueue<>();

        CountDownLatch latch1 = backgroundSubscribe(queue);
        CountDownLatch latch2 = backgroundSubscribe(queue);

        // wait for both threads to start
        latch1.await();
        latch2.await();

        /*
         * Must pause to prevent the topic from being created before subscribe() is
         * invoked.
         */
        assertTrue(waitCalled.await(1, TimeUnit.SECONDS));

        // only publish one message
        sim.publish(TOPIC, MESSAGE);

        // wait for both subscribers to add their messages to the queue
        List<String> messages = new ArrayList<>();
        messages.add(queue.take());
        messages.add(queue.take());

        // sort them so the order is consistent
        Collections.sort(messages);

        assertEquals("[No Data, hello]", messages.toString());
    }

    @Test
    public void testWaitForNextMessageFromQueue_Interrupted() throws InterruptedException {
        sim = new DMaaPSimulatorJaxRs() {
            @Override
            protected void sleep(int timeout) throws InterruptedException {
                throw new InterruptedException(EXPECTED_EXCEPTION);
            }
        };

        // put it in the background so we don't interrupt the test thread
        BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        backgroundSubscribe(queue);

        assertEquals(DMaaPSimulatorJaxRs.NO_TOPIC_MSG, queue.take());
    }

    @Test
    public void testPublish() {
        assertEquals("", sim.publish(TOPIC, MESSAGE));
        assertEquals(MESSAGE, sim.subscribe(0, TOPIC, resp));
    }

    @Test
    public void testSetStatus() {
        assertEquals("Status code set", sim.setStatus(500));
        assertEquals("You got response code: 500", sim.subscribe(TIMEOUT_MS, TOPIC, resp));
    }

    /**
     * Invokes subscribe() in a background thread.
     *
     * @param queue where to place the returned result
     * @return a latch that will be counted down just before the background thread invokes
     *         subscribe()
     */
    private CountDownLatch backgroundSubscribe(BlockingQueue<String> queue) {
        CountDownLatch latch = new CountDownLatch(1);

        new Thread(() -> {
            latch.countDown();
            queue.add(sim.subscribe(LONG_TIMEOUT_MS, TOPIC, resp));
        }).start();

        return latch;
    }
}
