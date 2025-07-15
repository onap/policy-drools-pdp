/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018-2020 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2024-2025 OpenInfra Foundation Europe. All rights reserved.
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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.onap.policy.common.message.bus.event.TopicListener;
import org.onap.policy.common.message.bus.event.TopicSink;
import org.onap.policy.common.message.bus.event.TopicSource;

class TopicMessageManagerTest {

    private static final String EXPECTED = "expected";
    private static final String MY_TOPIC = "my.topic";
    private static final String MSG = "a message";

    private TopicListener listener;
    private TopicSource source;
    private boolean gotSources;
    private TopicSink sink;
    private boolean gotSinks;
    private TopicMessageManager mgr;

    /**
     * Setup.
     *
     * @throws Exception throws an exception
     */
    @BeforeEach
    void setUp() throws Exception {
        listener = mock(TopicListener.class);
        source = mock(TopicSource.class);
        gotSources = false;
        sink = mock(TopicSink.class);
        gotSinks = false;

        when(source.getTopic()).thenReturn(MY_TOPIC);

        when(sink.getTopic()).thenReturn(MY_TOPIC);
        when(sink.send(any())).thenReturn(true);

        mgr = new TopicMessageManagerImpl(MY_TOPIC);
    }

    @Test
    void testTopicMessageManager() {
        // verify that the init methods were called
        assertTrue(gotSources);
        assertTrue(gotSinks);
    }

    @Test
    void testTopicMessageManager_PoolingEx() {
        // force error by having no topics match
        when(source.getTopic()).thenReturn("");

        assertThrows(PoolingFeatureException.class, () -> new TopicMessageManagerImpl(MY_TOPIC));
    }

    @Test
    void testTopicMessageManager_IllegalArgEx() {
        // force error
        assertThrows(PoolingFeatureException.class, () ->
            new TopicMessageManagerImpl(MY_TOPIC) {
            @Override
            protected List<TopicSource> getTopicSources() {
                throw new IllegalArgumentException(EXPECTED);
            }
        });
    }

    @Test
    void testGetTopic() {
        assertEquals(MY_TOPIC, mgr.getTopic());
    }

    @Test
    void testFindTopicSource_NotFound() {
        // one item in list, and its topic doesn't match
        assertThrows(PoolingFeatureException.class, () -> new TopicMessageManagerImpl(MY_TOPIC) {
            @Override
            protected List<TopicSource> getTopicSources() {
                return Collections.singletonList(mock(TopicSource.class));
            }
        });
    }

    @Test
    void testFindTopicSource_EmptyList() {
        // empty list
        assertThrows(PoolingFeatureException.class, () -> new TopicMessageManagerImpl(MY_TOPIC) {
            @Override
            protected List<TopicSource> getTopicSources() {
                return Collections.emptyList();
            }
        });
    }

    @Test
    void testFindTopicSink_NotFound() {
        // one item in list, and its topic doesn't match
        assertThrows(PoolingFeatureException.class, () -> new TopicMessageManagerImpl(MY_TOPIC) {
            @Override
            protected List<TopicSink> getTopicSinks() {
                return Collections.singletonList(mock(TopicSink.class));
            }
        });
    }

    @Test
    void testFindTopicSink_EmptyList() {
        // empty list
        assertThrows(PoolingFeatureException.class, () -> new TopicMessageManagerImpl(MY_TOPIC) {
            @Override
            protected List<TopicSink> getTopicSinks() {
                return Collections.emptyList();
            }
        });
    }

    @Test
    void testStartPublisher() throws PoolingFeatureException {

        mgr.startPublisher();

        // restart should have no effect
        mgr.startPublisher();

        // should be able to publish now
        mgr.publish(MSG);
        verify(sink).send(MSG);
    }

    @Test
    void testStopPublisher() {
        // not publishing yet, so stopping should have no effect
        mgr.stopPublisher(0);

        // now start it
        mgr.startPublisher();

        // this time, stop should do something
        mgr.stopPublisher(0);

        // re-stopping should have no effect
        assertThatCode(() -> mgr.stopPublisher(0)).doesNotThrowAnyException();
    }

    @Test
    void testStopPublisher_WithDelay() {

        mgr.startPublisher();

        long tbeg = System.currentTimeMillis();

        mgr.stopPublisher(100L);

        assertTrue(System.currentTimeMillis() >= tbeg + 100L);
    }

    @Test
    void testStopPublisher_WithDelayInterrupted() throws Exception {

        mgr.startPublisher();

        long minms = 2000L;

        // tell the publisher to stop in minms + additional time
        CountDownLatch latch = new CountDownLatch(1);
        Thread thread = new Thread(() -> {
            latch.countDown();
            mgr.stopPublisher(minms + 3000L);
        });
        thread.start();

        // wait for the thread to start
        latch.await();

        // interrupt it - it should immediately finish its work
        thread.interrupt();

        // wait for it to stop, but only wait the minimum time
        thread.join(minms);

        assertFalse(thread.isAlive());
    }

    @Test
    void testStartConsumer() {
        // not started yet
        verify(source, never()).register(any());

        mgr.startConsumer(listener);
        verify(source).register(listener);

        // restart should have no effect
        mgr.startConsumer(listener);
        verify(source).register(listener);
    }

    @Test
    void testStopConsumer() {
        // not consuming yet, so stopping should have no effect
        mgr.stopConsumer(listener);
        verify(source, never()).unregister(any());

        // now start it
        mgr.startConsumer(listener);

        // this time, stop should do something
        mgr.stopConsumer(listener);
        verify(source).unregister(listener);

        // re-stopping should have no effect
        mgr.stopConsumer(listener);
        verify(source).unregister(listener);
    }

    @Test
    void testPublish() throws PoolingFeatureException {
        // cannot publish before starting
        assertThatThrownBy(() -> mgr.publish(MSG)).as("publish,pre").isInstanceOf(PoolingFeatureException.class);

        mgr.startPublisher();

        // publish several messages
        mgr.publish(MSG);
        verify(sink).send(MSG);

        mgr.publish(MSG + "a");
        verify(sink).send(MSG + "a");

        mgr.publish(MSG + "b");
        verify(sink).send(MSG + "b");

        // stop and verify we can no longer publish
        mgr.stopPublisher(0);
        assertThatThrownBy(() -> mgr.publish(MSG)).as("publish,stopped").isInstanceOf(PoolingFeatureException.class);
    }

    @Test
    void testPublish_SendFailed() {
        mgr.startPublisher();

        // arrange for send() to fail
        when(sink.send(MSG)).thenReturn(false);

        assertThrows(PoolingFeatureException.class, () -> mgr.publish(MSG));
    }

    @Test
    void testPublish_SendEx() {
        mgr.startPublisher();

        // arrange for send() to throw an exception
        doThrow(new IllegalStateException(EXPECTED)).when(sink).send(MSG);

        assertThrows(PoolingFeatureException.class, () -> mgr.publish(MSG));
    }

    /**
     * Manager with overrides.
     */
    private class TopicMessageManagerImpl extends TopicMessageManager {

        public TopicMessageManagerImpl(String topic) throws PoolingFeatureException {
            super(topic);
        }

        @Override
        protected List<TopicSource> getTopicSources() {
            gotSources = true;

            // three sources, with the desired one in the middle
            return Arrays.asList(mock(TopicSource.class), source, mock(TopicSource.class));
        }

        @Override
        protected List<TopicSink> getTopicSinks() {
            gotSinks = true;

            // three sinks, with the desired one in the middle
            return Arrays.asList(mock(TopicSink.class), sink, mock(TopicSink.class));
        }
    }
}
