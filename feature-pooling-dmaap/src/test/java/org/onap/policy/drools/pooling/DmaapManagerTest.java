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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Properties;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.onap.policy.drools.event.comm.FilterableTopicSource;
import org.onap.policy.drools.event.comm.TopicListener;
import org.onap.policy.drools.event.comm.TopicSink;
import org.onap.policy.drools.event.comm.TopicSource;
import org.onap.policy.drools.pooling.DmaapManager.Factory;

public class DmaapManagerTest {

    private static String MY_TOPIC = "my.topic";
    private static String MSG = "a message";
    private static String FILTER = "a filter";

    /**
     * Original factory, to be restored when all tests complete.
     */
    private static Factory saveFactory;

    private Properties props;
    private Factory factory;
    private TopicListener listener;
    private FilterableTopicSource source;
    private TopicSink sink;
    private DmaapManager mgr;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        saveFactory = DmaapManager.getFactory();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        DmaapManager.setFactory(saveFactory);
    }

    @Before
    public void setUp() throws Exception {
        props = new Properties();

        listener = mock(TopicListener.class);
        factory = mock(Factory.class);
        source = mock(FilterableTopicSource.class);
        sink = mock(TopicSink.class);

        DmaapManager.setFactory(factory);

        when(source.getTopic()).thenReturn(MY_TOPIC);

        when(sink.getTopic()).thenReturn(MY_TOPIC);
        when(sink.send(any())).thenReturn(true);

        // three sources, with the desired one in the middle
        when(factory.initTopicSources(props))
                        .thenReturn(Arrays.asList(mock(TopicSource.class), source, mock(TopicSource.class)));

        // three sinks, with the desired one in the middle
        when(factory.initTopicSinks(props))
                        .thenReturn(Arrays.asList(mock(TopicSink.class), sink, mock(TopicSink.class)));

        mgr = new DmaapManager(MY_TOPIC, props);
    }

    @Test
    public void testDmaapManager() {
        // verify that the init methods were called
        verify(factory).initTopicSinks(props);
        verify(factory).initTopicSinks(props);
    }

    @Test(expected = PoolingFeatureException.class)
    public void testDmaapManager_PoolingEx() throws PoolingFeatureException {
        // force error by having no topics match
        when(source.getTopic()).thenReturn("");

        new DmaapManager(MY_TOPIC, props);
    }

    @Test(expected = PoolingFeatureException.class)
    public void testDmaapManager_IllegalArgEx() throws PoolingFeatureException {
        // force error
        when(factory.initTopicSources(props)).thenThrow(new IllegalArgumentException("expected"));

        new DmaapManager(MY_TOPIC, props);
    }

    @Test(expected = PoolingFeatureException.class)
    public void testDmaapManager_CannotFilter() throws PoolingFeatureException {
        // force an error when setFilter() is called
        doThrow(new UnsupportedOperationException("expected")).when(source).setFilter(any());

        new DmaapManager(MY_TOPIC, props);
    }

    @Test
    public void testGetTopic() {
        assertEquals(MY_TOPIC, mgr.getTopic());
    }

    @Test
    public void testFindTopicSource() {
        // getting here means it worked
    }

    @Test(expected = PoolingFeatureException.class)
    public void testFindTopicSource_NotFilterableTopicSource() throws PoolingFeatureException {

        // matching topic, but doesn't have the correct interface
        TopicSource source2 = mock(TopicSource.class);
        when(source2.getTopic()).thenReturn(MY_TOPIC);

        when(factory.initTopicSources(props)).thenReturn(Arrays.asList(source2));

        new DmaapManager(MY_TOPIC, props);
    }

    @Test(expected = PoolingFeatureException.class)
    public void testFindTopicSource_NotFound() throws PoolingFeatureException {
        // one item in list, and its topic doesn't match
        when(factory.initTopicSources(props)).thenReturn(Arrays.asList(mock(TopicSource.class)));

        new DmaapManager(MY_TOPIC, props);
    }

    @Test(expected = PoolingFeatureException.class)
    public void testFindTopicSource_EmptyList() throws PoolingFeatureException {
        // empty list
        when(factory.initTopicSources(props)).thenReturn(new LinkedList<>());

        new DmaapManager(MY_TOPIC, props);
    }

    @Test
    public void testFindTopicSink() {
        // getting here means it worked
    }

    @Test(expected = PoolingFeatureException.class)
    public void testFindTopicSink_NotFound() throws PoolingFeatureException {
        // one item in list, and its topic doesn't match
        when(factory.initTopicSinks(props)).thenReturn(Arrays.asList(mock(TopicSink.class)));

        new DmaapManager(MY_TOPIC, props);
    }

    @Test(expected = PoolingFeatureException.class)
    public void testFindTopicSink_EmptyList() throws PoolingFeatureException {
        // empty list
        when(factory.initTopicSinks(props)).thenReturn(new LinkedList<>());

        new DmaapManager(MY_TOPIC, props);
    }

    @Test
    public void testStartPublisher() throws PoolingFeatureException {
        // not started yet
        verify(sink, never()).start();

        mgr.startPublisher();
        verify(sink).start();

        // restart should have no effect
        mgr.startPublisher();
        verify(sink).start();

        // should be able to publish now
        mgr.publish(MSG);
        verify(sink).send(MSG);
    }

    @Test
    public void testStartPublisher_Exception() throws PoolingFeatureException {
        // force exception when it starts
        doThrow(new IllegalStateException("expected")).when(sink).start();

        expectException("startPublisher,start", xxx -> mgr.startPublisher());
        expectException("startPublisher,publish", xxx -> mgr.publish(MSG));

        // allow it to succeed this time
        reset(sink);
        when(sink.send(any())).thenReturn(true);

        mgr.startPublisher();
        verify(sink).start();

        // should be able to publish now
        mgr.publish(MSG);
        verify(sink).send(MSG);
    }

    @Test
    public void testStopPublisher() throws PoolingFeatureException {
        // not publishing yet, so stopping should have no effect
        mgr.stopPublisher(0);
        verify(sink, never()).stop();

        // now start it
        mgr.startPublisher();

        // this time, stop should do something
        mgr.stopPublisher(0);
        verify(sink).stop();

        // re-stopping should have no effect
        mgr.stopPublisher(0);
        verify(sink).stop();
    }

    @Test
    public void testStopPublisher_WithDelay() throws PoolingFeatureException {

        mgr.startPublisher();

        long tbeg = System.currentTimeMillis();

        mgr.stopPublisher(100L);

        assertTrue(System.currentTimeMillis() >= tbeg + 100L);
    }

    @Test
    public void testStopPublisher_WithDelayInterrupted() throws Exception {

        mgr.startPublisher();

        long minms = 2000L;

        // tell the publisher to stop in minms + additional time
        Thread thread = new Thread(() -> mgr.stopPublisher(minms + 3000L));
        thread.start();

        // give the thread a chance to start
        Thread.sleep(50L);

        // interrupt it - it should immediately finish its work
        thread.interrupt();

        // wait for it to stop, but only wait the minimum time
        thread.join(minms);

        assertFalse(thread.isAlive());
    }

    @Test
    public void testStopPublisher_Exception() throws PoolingFeatureException {
        mgr.startPublisher();

        // force exception when it stops
        doThrow(new IllegalStateException("expected")).when(sink).stop();

        mgr.stopPublisher(0);
    }

    @Test
    public void testStartConsumer() {
        // not started yet
        verify(source, never()).register(any());

        mgr.startConsumer(listener);
        verify(source).register(listener);

        // restart should have no effect
        mgr.startConsumer(listener);
        verify(source).register(listener);
    }

    @Test
    public void testStopConsumer() {
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
    public void testSetFilter() throws PoolingFeatureException {
        mgr.setFilter(FILTER);
    }

    @Test(expected = PoolingFeatureException.class)
    public void testSetFilter_Exception() throws PoolingFeatureException {
        // force an error when setFilter() is called
        doThrow(new UnsupportedOperationException("expected")).when(source).setFilter(any());

        mgr.setFilter(FILTER);
    }

    @Test
    public void testPublish() throws PoolingFeatureException {
        // cannot publish before starting
        expectException("publish,pre", xxx -> mgr.publish(MSG));

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
        expectException("publish,stopped", xxx -> mgr.publish(MSG));
    }

    @Test(expected = PoolingFeatureException.class)
    public void testPublish_SendFailed() throws PoolingFeatureException {
        mgr.startPublisher();

        // arrange for send() to fail
        when(sink.send(MSG)).thenReturn(false);

        mgr.publish(MSG);
    }

    @Test(expected = PoolingFeatureException.class)
    public void testPublish_SendEx() throws PoolingFeatureException {
        mgr.startPublisher();

        // arrange for send() to throw an exception
        doThrow(new IllegalStateException("expected")).when(sink).send(MSG);

        mgr.publish(MSG);
    }

    private void expectException(String testnm, VFunction func) {
        try {
            func.apply(null);
            fail(testnm + " missing exception");

        } catch (PoolingFeatureException expected) {
            // OK
        }
    }

    @FunctionalInterface
    public static interface VFunction {
        public void apply(Void arg) throws PoolingFeatureException;
    }
}
