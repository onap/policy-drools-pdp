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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.LinkedList;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.event.comm.Topic.CommInfrastructure;
import org.onap.policy.drools.event.comm.TopicListener;
import org.onap.policy.drools.pooling.PoolingManagerImpl.Factory;
import org.onap.policy.drools.pooling.extractor.ClassExtractors;
import org.onap.policy.drools.pooling.message.BucketAssignments;
import org.onap.policy.drools.pooling.message.Forward;
import org.onap.policy.drools.pooling.message.Heartbeat;
import org.onap.policy.drools.pooling.message.Message;
import org.onap.policy.drools.pooling.message.Offline;
import org.onap.policy.drools.pooling.state.ActiveState;
import org.onap.policy.drools.pooling.state.IdleState;
import org.onap.policy.drools.pooling.state.InactiveState;
import org.onap.policy.drools.pooling.state.QueryState;
import org.onap.policy.drools.pooling.state.StartState;
import org.onap.policy.drools.pooling.state.State;
import org.onap.policy.drools.system.PolicyController;

public class PoolingManagerImplTest {

    protected static final long STD_HEARTBEAT_WAIT_MS = 10;
    protected static final long STD_REACTIVATE_WAIT_MS = STD_HEARTBEAT_WAIT_MS + 1;
    protected static final long STD_IDENTIFICATION_MS = STD_REACTIVATE_WAIT_MS + 1;
    protected static final long STD_ACTIVE_HEARTBEAT_MS = STD_IDENTIFICATION_MS + 1;
    protected static final long STD_INTER_HEARTBEAT_MS = STD_ACTIVE_HEARTBEAT_MS + 1;
    protected static final long STD_OFFLINE_PUB_WAIT_MS = STD_INTER_HEARTBEAT_MS + 1;

    private static final String MY_HOST = "my.host";
    private static final String HOST2 = "other.host";

    private static final String MY_CONTROLLER = "my.controller";
    private static final String MY_TOPIC = "my.topic";

    private static final String TOPIC2 = "topic.two";

    private static final String THE_EVENT = "the event";

    private static final Object DECODED_EVENT = new Object();
    private static final String REQUEST_ID = "my.request.id";

    /**
     * Number of dmaap.publish() invocations that should be issued when the manager is
     * started.
     */
    private static final int START_PUB = 1;

    /**
     * Saved from PoolingManagerImpl and restored on exit from this test class.
     */
    private static Factory saveFactory;

    /**
     * Futures that have been allocated due to calls to scheduleXxx().
     */
    private Queue<ScheduledFuture<?>> futures;

    private Properties plainProps;
    private PoolingProperties poolProps;
    private ListeningController controller;
    private EventQueue eventQueue;
    private ClassExtractors extractors;
    private DmaapManager dmaap;
    private ScheduledThreadPoolExecutor sched;
    private DroolsController drools;
    private Serializer ser;
    private Factory factory;

    private PoolingManagerImpl mgr;

    @BeforeClass
    public static void setUpBeforeClass() {
        saveFactory = PoolingManagerImpl.getFactory();
    }

    @AfterClass
    public static void tearDownAfterClass() {
        PoolingManagerImpl.setFactory(saveFactory);
    }

    @Before
    public void setUp() throws Exception {
        plainProps = new Properties();

        poolProps = mock(PoolingProperties.class);
        when(poolProps.getSource()).thenReturn(plainProps);
        when(poolProps.getPoolingTopic()).thenReturn(MY_TOPIC);
        when(poolProps.getStartHeartbeatMs()).thenReturn(STD_HEARTBEAT_WAIT_MS);
        when(poolProps.getReactivateMs()).thenReturn(STD_REACTIVATE_WAIT_MS);
        when(poolProps.getIdentificationMs()).thenReturn(STD_IDENTIFICATION_MS);
        when(poolProps.getActiveHeartbeatMs()).thenReturn(STD_ACTIVE_HEARTBEAT_MS);
        when(poolProps.getInterHeartbeatMs()).thenReturn(STD_INTER_HEARTBEAT_MS);
        when(poolProps.getOfflinePubWaitMs()).thenReturn(STD_OFFLINE_PUB_WAIT_MS);

        futures = new LinkedList<>();
        ser = new Serializer();

        factory = mock(Factory.class);
        eventQueue = mock(EventQueue.class);
        extractors = mock(ClassExtractors.class);
        dmaap = mock(DmaapManager.class);
        controller = mock(ListeningController.class);
        sched = mock(ScheduledThreadPoolExecutor.class);
        drools = mock(DroolsController.class);

        when(factory.makeEventQueue(any())).thenReturn(eventQueue);
        when(factory.makeClassExtractors(any())).thenReturn(extractors);
        when(factory.makeDmaapManager(any(), any())).thenReturn(dmaap);
        when(factory.makeScheduler()).thenReturn(sched);
        when(factory.canDecodeEvent(drools, TOPIC2)).thenReturn(true);
        when(factory.decodeEvent(drools, TOPIC2, THE_EVENT)).thenReturn(DECODED_EVENT);

        when(extractors.extract(DECODED_EVENT)).thenReturn(REQUEST_ID);

        when(controller.getName()).thenReturn(MY_CONTROLLER);
        when(controller.getDrools()).thenReturn(drools);
        when(controller.isAlive()).thenReturn(true);

        when(sched.schedule(any(Runnable.class), any(Long.class), any(TimeUnit.class))).thenAnswer(args -> {
            ScheduledFuture<?> fut = mock(ScheduledFuture.class);
            futures.add(fut);

            return fut;
        });

        when(sched.scheduleWithFixedDelay(any(Runnable.class), any(Long.class), any(Long.class), any(TimeUnit.class)))
                        .thenAnswer(args -> {
                            ScheduledFuture<?> fut = mock(ScheduledFuture.class);
                            futures.add(fut);

                            return fut;
                        });

        PoolingManagerImpl.setFactory(factory);

        mgr = new PoolingManagerImpl(MY_HOST, controller, poolProps);
    }

    @Test
    public void testPoolingManagerImpl() throws Exception {
        verify(factory).makeDmaapManager(any(), any());

        State st = mgr.getCurrent();
        assertTrue(st instanceof IdleState);

        // ensure the state is attached to the manager
        assertEquals(mgr.getHost(), st.getHost());
    }

    @Test
    public void testPoolingManagerImpl_ClassEx() {
        /*
         * this controller does not implement TopicListener, which should cause a
         * ClassCastException
         */
        PolicyController ctlr = mock(PolicyController.class);

        PoolingFeatureRtException ex = expectException(PoolingFeatureRtException.class,
                        xxx -> new PoolingManagerImpl(MY_HOST, ctlr, poolProps));
        assertNotNull(ex.getCause());
        assertTrue(ex.getCause() instanceof ClassCastException);
    }

    @Test
    public void testPoolingManagerImpl_PoolEx() throws PoolingFeatureException {
        // throw an exception when we try to create the dmaap manager
        PoolingFeatureException ex = new PoolingFeatureException();
        when(factory.makeDmaapManager(any(), any())).thenThrow(ex);

        PoolingFeatureRtException ex2 = expectException(PoolingFeatureRtException.class,
                        xxx -> new PoolingManagerImpl(MY_HOST, controller, poolProps));
        assertEquals(ex, ex2.getCause());
    }

    @Test
    public void testGetCurrent() throws Exception {
        assertEquals(IdleState.class, mgr.getCurrent().getClass());

        startMgr();

        assertEquals(StartState.class, mgr.getCurrent().getClass());
    }

    @Test
    public void testGetHost() {
        assertEquals(MY_HOST, mgr.getHost());

        mgr = new PoolingManagerImpl(HOST2, controller, poolProps);
        assertEquals(HOST2, mgr.getHost());
    }

    @Test
    public void testGetTopic() {
        assertEquals(MY_TOPIC, mgr.getTopic());
    }

    @Test
    public void testGetProperties() {
        assertEquals(poolProps, mgr.getProperties());
    }

    @Test
    public void testBeforeStart() throws Exception {
        // not running yet
        mgr.beforeStart();

        verify(dmaap).startPublisher();

        verify(factory).makeScheduler();
        verify(sched).setMaximumPoolSize(1);
        verify(sched).setContinueExistingPeriodicTasksAfterShutdownPolicy(false);


        // try again - nothing should happen
        mgr.beforeStart();

        verify(dmaap).startPublisher();

        verify(factory).makeScheduler();
        verify(sched).setMaximumPoolSize(1);
        verify(sched).setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    }

    @Test
    public void testBeforeStart_DmaapEx() throws Exception {
        // generate an exception
        PoolingFeatureException ex = new PoolingFeatureException();
        doThrow(ex).when(dmaap).startPublisher();

        PoolingFeatureException ex2 = expectException(PoolingFeatureException.class, xxx -> mgr.beforeStart());
        assertEquals(ex, ex2);

        // should never start the scheduler
        verify(factory, never()).makeScheduler();
    }

    @Test
    public void testAfterStart() throws Exception {
        startMgr();

        verify(dmaap).startConsumer(mgr);

        State st = mgr.getCurrent();
        assertTrue(st instanceof StartState);

        // ensure the state is attached to the manager
        assertEquals(mgr.getHost(), st.getHost());

        ArgumentCaptor<Long> timeCap = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<TimeUnit> unitCap = ArgumentCaptor.forClass(TimeUnit.class);
        verify(sched).schedule(any(Runnable.class), timeCap.capture(), unitCap.capture());

        assertEquals(STD_HEARTBEAT_WAIT_MS, timeCap.getValue().longValue());
        assertEquals(TimeUnit.MILLISECONDS, unitCap.getValue());


        // already started - nothing else happens
        mgr.afterStart();

        verify(dmaap).startConsumer(mgr);

        assertTrue(mgr.getCurrent() instanceof StartState);

        verify(sched).schedule(any(Runnable.class), any(Long.class), any(TimeUnit.class));
    }

    @Test
    public void testBeforeStop() throws Exception {
        startMgr();

        mgr.beforeStop();

        verify(dmaap).stopConsumer(mgr);
        verify(sched).shutdownNow();
        verify(dmaap).publish(contains("offline"));

        assertTrue(mgr.getCurrent() instanceof IdleState);
    }

    @Test
    public void testBeforeStop_NotRunning() throws Exception {
        State st = mgr.getCurrent();

        mgr.beforeStop();

        verify(dmaap, never()).stopConsumer(any());
        verify(sched, never()).shutdownNow();

        // hasn't changed states either
        assertEquals(st, mgr.getCurrent());
    }

    @Test
    public void testBeforeStop_AfterPartialStart() throws Exception {
        // call beforeStart but not afterStart
        mgr.beforeStart();

        State st = mgr.getCurrent();

        mgr.beforeStop();

        // should still shut the scheduler down
        verify(sched).shutdownNow();

        verify(dmaap, never()).stopConsumer(any());

        // hasn't changed states
        assertEquals(st, mgr.getCurrent());
    }

    @Test
    public void testAfterStop() throws Exception {
        startMgr();
        mgr.beforeStop();

        when(eventQueue.isEmpty()).thenReturn(false);
        when(eventQueue.size()).thenReturn(3);

        mgr.afterStop();

        verify(eventQueue).clear();
        verify(dmaap).stopPublisher(STD_OFFLINE_PUB_WAIT_MS);
    }

    @Test
    public void testAfterStop_EmptyQueue() throws Exception {
        startMgr();
        mgr.beforeStop();

        when(eventQueue.isEmpty()).thenReturn(true);
        when(eventQueue.size()).thenReturn(0);

        mgr.afterStop();

        verify(eventQueue, never()).clear();
        verify(dmaap).stopPublisher(STD_OFFLINE_PUB_WAIT_MS);
    }

    @Test
    public void testBeforeLock() throws Exception {
        startMgr();

        mgr.beforeLock();

        assertTrue(mgr.getCurrent() instanceof IdleState);
    }

    @Test
    public void testAfterUnlock_AliveIdle() throws Exception {
        // this really shouldn't happen

        lockMgr();

        mgr.afterUnlock();

        // stays in idle state, because it has no scheduler
        assertTrue(mgr.getCurrent() instanceof IdleState);
    }

    @Test
    public void testAfterUnlock_AliveStarted() throws Exception {
        startMgr();
        lockMgr();

        mgr.afterUnlock();

        assertTrue(mgr.getCurrent() instanceof StartState);
    }

    @Test
    public void testAfterUnlock_StoppedIdle() throws Exception {
        startMgr();
        lockMgr();

        // controller is stopped
        when(controller.isAlive()).thenReturn(false);

        mgr.afterUnlock();

        assertTrue(mgr.getCurrent() instanceof IdleState);
    }

    @Test
    public void testAfterUnlock_StoppedStarted() throws Exception {
        startMgr();

        // Note: don't lockMgr()

        // controller is stopped
        when(controller.isAlive()).thenReturn(false);

        mgr.afterUnlock();

        assertTrue(mgr.getCurrent() instanceof StartState);
    }

    @Test
    public void testChangeState() throws Exception {
        // start should invoke changeState()
        startMgr();

        int ntimes = 0;

        // should have set the filter for the StartState
        verify(dmaap, times(++ntimes)).setFilter(any());

        /*
         * now go offline while it's locked
         */
        lockMgr();

        // should have set the new filter
        verify(dmaap, times(++ntimes)).setFilter(any());

        // should have cancelled the timers
        assertEquals(2, futures.size());
        verify(futures.poll()).cancel(false);
        verify(futures.poll()).cancel(false);

        /*
         * now go back online
         */
        unlockMgr();

        // should have set the new filter
        verify(dmaap, times(++ntimes)).setFilter(any());

        // new timers should now be active
        assertEquals(2, futures.size());
        verify(futures.poll(), never()).cancel(false);
        verify(futures.poll(), never()).cancel(false);
    }

    @Test
    public void testSetFilter() throws Exception {
        // start should cause a filter to be set
        startMgr();

        verify(dmaap).setFilter(any());
    }

    @Test
    public void testSetFilter_DmaapEx() throws Exception {

        // generate an exception
        doThrow(new PoolingFeatureException()).when(dmaap).setFilter(any());

        // start should invoke setFilter()
        startMgr();

        // no exception, means success
    }

    @Test
    public void testInternalTopicFailed() throws Exception {
        startMgr();

        CountDownLatch latch = mgr.internalTopicFailed();

        // wait for the thread to complete
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        verify(controller).stop();
    }

    @Test
    public void testSchedule() throws Exception {
        // must start the scheduler
        startMgr();

        CountDownLatch latch = new CountDownLatch(1);

        mgr.schedule(STD_ACTIVE_HEARTBEAT_MS, () -> {
            latch.countDown();
            return null;
        });

        // capture the task
        ArgumentCaptor<Runnable> taskCap = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Long> timeCap = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<TimeUnit> unitCap = ArgumentCaptor.forClass(TimeUnit.class);

        verify(sched, times(2)).schedule(taskCap.capture(), timeCap.capture(), unitCap.capture());

        assertEquals(STD_ACTIVE_HEARTBEAT_MS, timeCap.getValue().longValue());
        assertEquals(TimeUnit.MILLISECONDS, unitCap.getValue());

        // execute it
        taskCap.getValue().run();

        assertEquals(0, latch.getCount());
    }

    @Test
    public void testScheduleWithFixedDelay() throws Exception {
        // must start the scheduler
        startMgr();

        CountDownLatch latch = new CountDownLatch(1);

        mgr.scheduleWithFixedDelay(STD_HEARTBEAT_WAIT_MS, STD_ACTIVE_HEARTBEAT_MS, () -> {
            latch.countDown();
            return null;
        });

        // capture the task
        ArgumentCaptor<Runnable> taskCap = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Long> initCap = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> timeCap = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<TimeUnit> unitCap = ArgumentCaptor.forClass(TimeUnit.class);

        verify(sched, times(2)).scheduleWithFixedDelay(taskCap.capture(), initCap.capture(), timeCap.capture(),
                        unitCap.capture());

        assertEquals(STD_HEARTBEAT_WAIT_MS, initCap.getValue().longValue());
        assertEquals(STD_ACTIVE_HEARTBEAT_MS, timeCap.getValue().longValue());
        assertEquals(TimeUnit.MILLISECONDS, unitCap.getValue());

        // execute it
        taskCap.getValue().run();

        assertEquals(0, latch.getCount());
    }

    @Test
    public void testPublishAdmin() throws Exception {
        Offline msg = new Offline(mgr.getHost());
        mgr.publishAdmin(msg);

        assertEquals(Message.ADMIN, msg.getChannel());

        verify(dmaap).publish(any());
    }

    @Test
    public void testPublish() throws Exception {
        Offline msg = new Offline(mgr.getHost());
        mgr.publish("my.channel", msg);

        assertEquals("my.channel", msg.getChannel());

        verify(dmaap).publish(any());
    }

    @Test
    public void testPublish_InvalidMsg() throws Exception {
        // message is missing data
        mgr.publish(Message.ADMIN, new Offline());

        // should not have attempted to publish it
        verify(dmaap, never()).publish(any());
    }

    @Test
    public void testPublish_DmaapEx() throws Exception {

        // generate exception
        doThrow(new PoolingFeatureException()).when(dmaap).publish(any());

        mgr.publish(Message.ADMIN, new Offline(mgr.getHost()));
    }

    @Test
    public void testOnTopicEvent() throws Exception {
        startMgr();

        StartState st = (StartState) mgr.getCurrent();

        /*
         * give it its heart beat, that should cause it to transition to the Query state.
         */
        Heartbeat hb = new Heartbeat(mgr.getHost(), st.getHbTimestampMs());
        hb.setChannel(Message.ADMIN);

        String msg = ser.encodeMsg(hb);

        mgr.onTopicEvent(CommInfrastructure.UEB, MY_TOPIC, msg);

        assertTrue(mgr.getCurrent() instanceof QueryState);
    }

    @Test
    public void testOnTopicEvent_NullEvent() throws Exception {
        startMgr();

        mgr.onTopicEvent(CommInfrastructure.UEB, TOPIC2, null);
    }

    @Test
    public void testBeforeOffer_Unlocked_NoIntercept() throws Exception {
        startMgr();

        assertFalse(mgr.beforeOffer(CommInfrastructure.UEB, TOPIC2, THE_EVENT));
    }

    @Test
    public void testBeforeOffer_Locked_NoIntercept() throws Exception {
        startMgr();

        lockMgr();

        assertFalse(mgr.beforeOffer(CommInfrastructure.UEB, TOPIC2, THE_EVENT));
    }

    @Test
    public void testBeforeOffer_Locked_Intercept() throws Exception {
        startMgr();
        lockMgr();

        // route the message to this host
        mgr.startDistributing(makeAssignments(true));

        CountDownLatch latch = catchRecursion(false);

        Forward msg = new Forward(mgr.getHost(), CommInfrastructure.UEB, TOPIC2, THE_EVENT, REQUEST_ID);
        mgr.handle(msg);

        verify(dmaap, times(START_PUB)).publish(any());
        verify(controller).onTopicEvent(CommInfrastructure.UEB, TOPIC2, THE_EVENT);

        // ensure we made it past both beforeXxx() methods
        assertEquals(0, latch.getCount());
    }

    @Test
    public void testBeforeInsert_Intercept() throws Exception {
        startMgr();
        lockMgr();

        // route the message to this host
        mgr.startDistributing(makeAssignments(true));

        CountDownLatch latch = catchRecursion(true);

        Forward msg = new Forward(mgr.getHost(), CommInfrastructure.UEB, TOPIC2, THE_EVENT, REQUEST_ID);
        mgr.handle(msg);

        verify(dmaap, times(START_PUB)).publish(any());
        verify(controller).onTopicEvent(CommInfrastructure.UEB, TOPIC2, THE_EVENT);

        // ensure we made it past both beforeXxx() methods
        assertEquals(0, latch.getCount());
    }

    @Test
    public void testBeforeInsert_NoIntercept() throws Exception {
        startMgr();

        long tbegin = System.currentTimeMillis();

        assertTrue(mgr.beforeInsert(CommInfrastructure.UEB, TOPIC2, THE_EVENT, DECODED_EVENT));

        ArgumentCaptor<Forward> msgCap = ArgumentCaptor.forClass(Forward.class);
        verify(eventQueue).add(msgCap.capture());

        validateMessageContent(tbegin, msgCap.getValue());
    }

    @Test
    public void testHandleExternalCommInfrastructureStringStringString_NullReqId() throws Exception {
        startMgr();

        when(extractors.extract(any())).thenReturn(null);

        assertFalse(mgr.beforeInsert(CommInfrastructure.UEB, TOPIC2, THE_EVENT, DECODED_EVENT));
    }

    @Test
    public void testHandleExternalCommInfrastructureStringStringString_EmptyReqId() throws Exception {
        startMgr();

        when(extractors.extract(any())).thenReturn("");

        assertFalse(mgr.beforeInsert(CommInfrastructure.UEB, TOPIC2, THE_EVENT, DECODED_EVENT));
    }

    @Test
    public void testHandleExternalCommInfrastructureStringStringString_InvalidMsg() throws Exception {
        startMgr();

        assertTrue(mgr.beforeInsert(null, TOPIC2, THE_EVENT, DECODED_EVENT));

        // should not have tried to enqueue a message
        verify(eventQueue, never()).add(any());
    }

    @Test
    public void testHandleExternalCommInfrastructureStringStringString() throws Exception {
        startMgr();

        long tbegin = System.currentTimeMillis();

        assertTrue(mgr.beforeInsert(CommInfrastructure.UEB, TOPIC2, THE_EVENT, DECODED_EVENT));

        ArgumentCaptor<Forward> msgCap = ArgumentCaptor.forClass(Forward.class);
        verify(eventQueue).add(msgCap.capture());

        validateMessageContent(tbegin, msgCap.getValue());
    }

    @Test
    public void testHandleExternalForward_NoAssignments() throws Exception {
        startMgr();

        long tbegin = System.currentTimeMillis();

        assertTrue(mgr.beforeInsert(CommInfrastructure.UEB, TOPIC2, THE_EVENT, DECODED_EVENT));

        ArgumentCaptor<Forward> msgCap = ArgumentCaptor.forClass(Forward.class);
        verify(eventQueue).add(msgCap.capture());

        validateMessageContent(tbegin, msgCap.getValue());
    }

    @Test
    public void testHandleExternalForward() throws Exception {
        startMgr();

        // route the message to this host
        mgr.startDistributing(makeAssignments(true));

        assertFalse(mgr.beforeInsert(CommInfrastructure.UEB, TOPIC2, THE_EVENT, DECODED_EVENT));
    }

    @Test
    public void testHandleEvent_NullTarget() throws Exception {
        startMgr();

        // buckets have null targets
        mgr.startDistributing(new BucketAssignments(new String[] {null, null}));

        assertTrue(mgr.beforeInsert(CommInfrastructure.UEB, TOPIC2, THE_EVENT, DECODED_EVENT));

        verify(dmaap, times(START_PUB)).publish(any());
    }

    @Test
    public void testHandleEvent_SameHost() throws Exception {
        startMgr();

        // route the message to this host
        mgr.startDistributing(makeAssignments(true));

        assertFalse(mgr.beforeInsert(CommInfrastructure.UEB, TOPIC2, THE_EVENT, DECODED_EVENT));

        verify(dmaap, times(START_PUB)).publish(any());
    }

    @Test
    public void testHandleEvent_DiffHost_TooManyHops() throws Exception {
        startMgr();

        // route the message to this host
        mgr.startDistributing(makeAssignments(false));

        Forward msg = new Forward(mgr.getHost(), CommInfrastructure.UEB, TOPIC2, THE_EVENT, REQUEST_ID);
        msg.setNumHops(PoolingManagerImpl.MAX_HOPS + 1);
        mgr.handle(msg);

        // shouldn't publish
        verify(dmaap, times(START_PUB)).publish(any());
        verify(controller, never()).onTopicEvent(CommInfrastructure.UEB, TOPIC2, THE_EVENT);
    }

    @Test
    public void testHandleEvent_DiffHost_Forward() throws Exception {
        startMgr();

        // route the message to the *OTHER* host
        mgr.startDistributing(makeAssignments(false));

        assertTrue(mgr.beforeInsert(CommInfrastructure.UEB, TOPIC2, THE_EVENT, DECODED_EVENT));

        verify(dmaap, times(START_PUB + 1)).publish(any());
    }

    @Test
    public void testExtractRequestId_NullEvent() throws Exception {
        startMgr();

        assertFalse(mgr.beforeInsert(CommInfrastructure.UEB, TOPIC2, THE_EVENT, null));
    }

    @Test
    public void testExtractRequestId_NullReqId() throws Exception {
        startMgr();

        when(extractors.extract(any())).thenReturn(null);

        assertFalse(mgr.beforeInsert(CommInfrastructure.UEB, TOPIC2, THE_EVENT, DECODED_EVENT));
    }

    @Test
    public void testExtractRequestId() throws Exception {
        startMgr();

        // route the message to the *OTHER* host
        mgr.startDistributing(makeAssignments(false));

        assertTrue(mgr.beforeInsert(CommInfrastructure.UEB, TOPIC2, THE_EVENT, DECODED_EVENT));
    }

    @Test
    public void testDecodeEvent_CannotDecode() throws Exception {
        startMgr();

        when(controller.isLocked()).thenReturn(true);

        // create assignments, though they are irrelevant
        mgr.startDistributing(makeAssignments(false));

        when(factory.canDecodeEvent(drools, TOPIC2)).thenReturn(false);

        assertFalse(mgr.beforeOffer(CommInfrastructure.UEB, TOPIC2, THE_EVENT));
    }

    @Test
    public void testDecodeEvent_UnsuppEx() throws Exception {
        startMgr();

        when(controller.isLocked()).thenReturn(true);

        // create assignments, though they are irrelevant
        mgr.startDistributing(makeAssignments(false));

        // generate exception
        doThrow(new UnsupportedOperationException()).when(factory).decodeEvent(drools, TOPIC2, THE_EVENT);

        assertFalse(mgr.beforeOffer(CommInfrastructure.UEB, TOPIC2, THE_EVENT));
    }

    @Test
    public void testDecodeEvent_ArgEx() throws Exception {
        startMgr();

        when(controller.isLocked()).thenReturn(true);

        // create assignments, though they are irrelevant
        mgr.startDistributing(makeAssignments(false));

        // generate exception
        doThrow(new IllegalArgumentException()).when(factory).decodeEvent(drools, TOPIC2, THE_EVENT);

        assertFalse(mgr.beforeOffer(CommInfrastructure.UEB, TOPIC2, THE_EVENT));
    }

    @Test
    public void testDecodeEvent_StateEx() throws Exception {
        startMgr();

        when(controller.isLocked()).thenReturn(true);

        // create assignments, though they are irrelevant
        mgr.startDistributing(makeAssignments(false));

        // generate exception
        doThrow(new IllegalStateException()).when(factory).decodeEvent(drools, TOPIC2, THE_EVENT);

        assertFalse(mgr.beforeOffer(CommInfrastructure.UEB, TOPIC2, THE_EVENT));
    }

    @Test
    public void testDecodeEvent() throws Exception {
        startMgr();

        when(controller.isLocked()).thenReturn(true);

        // route to another host
        mgr.startDistributing(makeAssignments(false));

        assertTrue(mgr.beforeOffer(CommInfrastructure.UEB, TOPIC2, THE_EVENT));
    }

    @Test
    public void testMakeForward() throws Exception {
        startMgr();

        long tbegin = System.currentTimeMillis();

        assertTrue(mgr.beforeInsert(CommInfrastructure.UEB, TOPIC2, THE_EVENT, DECODED_EVENT));

        ArgumentCaptor<Forward> msgCap = ArgumentCaptor.forClass(Forward.class);
        verify(eventQueue).add(msgCap.capture());

        validateMessageContent(tbegin, msgCap.getValue());
    }

    @Test
    public void testMakeForward_InvalidMsg() throws Exception {
        startMgr();

        assertTrue(mgr.beforeInsert(null, TOPIC2, THE_EVENT, DECODED_EVENT));

        // should not have tried to enqueue a message
        verify(eventQueue, never()).add(any());
    }

    @Test
    public void testHandle_SameHost() throws Exception {
        startMgr();

        // route the message to this host
        mgr.startDistributing(makeAssignments(true));

        Forward msg = new Forward(mgr.getHost(), CommInfrastructure.UEB, TOPIC2, THE_EVENT, REQUEST_ID);
        mgr.handle(msg);

        verify(dmaap, times(START_PUB)).publish(any());
        verify(controller).onTopicEvent(CommInfrastructure.UEB, TOPIC2, THE_EVENT);
    }

    @Test
    public void testHandle_DiffHost() throws Exception {
        startMgr();

        // route the message to this host
        mgr.startDistributing(makeAssignments(false));

        Forward msg = new Forward(mgr.getHost(), CommInfrastructure.UEB, TOPIC2, THE_EVENT, REQUEST_ID);
        mgr.handle(msg);

        verify(dmaap, times(START_PUB + 1)).publish(any());
        verify(controller, never()).onTopicEvent(CommInfrastructure.UEB, TOPIC2, THE_EVENT);
    }

    @Test
    public void testInject() throws Exception {
        startMgr();

        // route the message to this host
        mgr.startDistributing(makeAssignments(true));

        CountDownLatch latch = catchRecursion(true);

        Forward msg = new Forward(mgr.getHost(), CommInfrastructure.UEB, TOPIC2, THE_EVENT, REQUEST_ID);
        mgr.handle(msg);

        verify(dmaap, times(START_PUB)).publish(any());
        verify(controller).onTopicEvent(CommInfrastructure.UEB, TOPIC2, THE_EVENT);

        // ensure we made it past both beforeXxx() methods
        assertEquals(0, latch.getCount());
    }

    @Test
    public void testInject_Ex() throws Exception {
        startMgr();

        // route the message to this host
        mgr.startDistributing(makeAssignments(true));

        // generate RuntimeException when onTopicEvent() is invoked
        doThrow(new IllegalArgumentException("expected")).when(controller).onTopicEvent(any(), any(), any());

        CountDownLatch latch = catchRecursion(true);

        Forward msg = new Forward(mgr.getHost(), CommInfrastructure.UEB, TOPIC2, THE_EVENT, REQUEST_ID);
        mgr.handle(msg);

        verify(dmaap, times(START_PUB)).publish(any());
        verify(controller).onTopicEvent(CommInfrastructure.UEB, TOPIC2, THE_EVENT);

        // ensure we made it past both beforeXxx() methods
        assertEquals(0, latch.getCount());
    }

    @Test
    public void testHandleInternal() throws Exception {
        startMgr();

        StartState st = (StartState) mgr.getCurrent();

        /*
         * give it its heart beat, that should cause it to transition to the Query state.
         */
        Heartbeat hb = new Heartbeat(mgr.getHost(), st.getHbTimestampMs());
        hb.setChannel(Message.ADMIN);

        String msg = ser.encodeMsg(hb);

        mgr.onTopicEvent(CommInfrastructure.UEB, MY_TOPIC, msg);

        assertTrue(mgr.getCurrent() instanceof QueryState);
    }

    @Test
    public void testHandleInternal_IOEx() throws Exception {
        startMgr();

        mgr.onTopicEvent(CommInfrastructure.UEB, MY_TOPIC, "invalid message");

        assertTrue(mgr.getCurrent() instanceof StartState);
    }

    @Test
    public void testHandleInternal_PoolEx() throws Exception {
        startMgr();

        StartState st = (StartState) mgr.getCurrent();

        Heartbeat hb = new Heartbeat(mgr.getHost(), st.getHbTimestampMs());

        /*
         * do NOT set the channel - this will cause the message to be invalid, triggering
         * an exception
         */

        String msg = ser.encodeMsg(hb);

        mgr.onTopicEvent(CommInfrastructure.UEB, MY_TOPIC, msg);

        assertTrue(mgr.getCurrent() instanceof StartState);
    }

    @Test
    public void testStartDistributing() throws Exception {
        startMgr();

        // route the message to this host
        assertNotNull(mgr.startDistributing(makeAssignments(true)));
        assertFalse(mgr.beforeInsert(CommInfrastructure.UEB, TOPIC2, THE_EVENT, DECODED_EVENT));
        verify(eventQueue, never()).add(any());


        // null assignments should cause message to be queued
        assertNull(mgr.startDistributing(null));
        assertTrue(mgr.beforeInsert(CommInfrastructure.UEB, TOPIC2, THE_EVENT, DECODED_EVENT));
        verify(eventQueue).add(any());


        // route the message to this host
        assertNotNull(mgr.startDistributing(makeAssignments(true)));
        assertFalse(mgr.beforeInsert(CommInfrastructure.UEB, TOPIC2, THE_EVENT, DECODED_EVENT));
        verify(eventQueue).add(any());


        // route the message to the other host
        assertNotNull(mgr.startDistributing(makeAssignments(false)));
        assertTrue(mgr.beforeInsert(CommInfrastructure.UEB, TOPIC2, THE_EVENT, DECODED_EVENT));
        verify(eventQueue).add(any());
    }

    @Test
    public void testStartDistributing_EventsInQueue_ProcessLocally() throws Exception {
        startMgr();

        // put items in the queue
        LinkedList<Forward> lst = new LinkedList<>();
        lst.add(new Forward(mgr.getHost(), CommInfrastructure.UEB, TOPIC2, THE_EVENT, REQUEST_ID));
        lst.add(new Forward(mgr.getHost(), CommInfrastructure.UEB, TOPIC2, THE_EVENT, REQUEST_ID));
        lst.add(new Forward(mgr.getHost(), CommInfrastructure.UEB, TOPIC2, THE_EVENT, REQUEST_ID));

        when(eventQueue.poll()).thenAnswer(args -> lst.poll());

        // route the messages to this host
        CountDownLatch latch = mgr.startDistributing(makeAssignments(true));
        assertNotNull(latch);
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        // all of the events should have been processed locally
        verify(dmaap, times(START_PUB)).publish(any());
        verify(controller, times(3)).onTopicEvent(CommInfrastructure.UEB, TOPIC2, THE_EVENT);
    }

    @Test
    public void testStartDistributing_EventsInQueue_Forward() throws Exception {
        startMgr();

        // put items in the queue
        LinkedList<Forward> lst = new LinkedList<>();
        lst.add(new Forward(mgr.getHost(), CommInfrastructure.UEB, TOPIC2, THE_EVENT, REQUEST_ID));
        lst.add(new Forward(mgr.getHost(), CommInfrastructure.UEB, TOPIC2, THE_EVENT, REQUEST_ID));
        lst.add(new Forward(mgr.getHost(), CommInfrastructure.UEB, TOPIC2, THE_EVENT, REQUEST_ID));

        when(eventQueue.poll()).thenAnswer(args -> lst.poll());

        // route the messages to the OTHER host
        assertTrue(mgr.startDistributing(makeAssignments(false)).await(2, TimeUnit.SECONDS));

        // all of the events should have been forwarded
        verify(dmaap, times(4)).publish(any());
        verify(controller, never()).onTopicEvent(CommInfrastructure.UEB, TOPIC2, THE_EVENT);
    }

    @Test
    public void testGoStart() {
        State st = mgr.goStart();
        assertTrue(st instanceof StartState);
        assertEquals(mgr.getHost(), st.getHost());
    }

    @Test
    public void testGoQuery() {
        BucketAssignments asgn = new BucketAssignments(new String[] {HOST2});
        mgr.startDistributing(asgn);

        State st = mgr.goQuery();

        assertTrue(st instanceof QueryState);
        assertEquals(mgr.getHost(), st.getHost());
        assertEquals(asgn, mgr.getAssignments());
    }

    @Test
    public void testGoActive() {
        BucketAssignments asgn = new BucketAssignments(new String[] {HOST2});
        mgr.startDistributing(asgn);

        State st = mgr.goActive();

        assertTrue(st instanceof ActiveState);
        assertEquals(mgr.getHost(), st.getHost());
        assertEquals(asgn, mgr.getAssignments());
    }

    @Test
    public void testGoInactive() {
        State st = mgr.goInactive();
        assertTrue(st instanceof InactiveState);
        assertEquals(mgr.getHost(), st.getHost());
    }

    @Test
    public void testTimerActionRun() throws Exception {
        // must start the scheduler
        startMgr();

        CountDownLatch latch = new CountDownLatch(1);

        mgr.schedule(STD_ACTIVE_HEARTBEAT_MS, () -> {
            latch.countDown();
            return null;
        });

        // capture the task
        ArgumentCaptor<Runnable> taskCap = ArgumentCaptor.forClass(Runnable.class);

        verify(sched, times(2)).schedule(taskCap.capture(), any(Long.class), any(TimeUnit.class));

        // execute it
        taskCap.getValue().run();

        assertEquals(0, latch.getCount());
    }

    @Test
    public void testTimerActionRun_DiffState() throws Exception {
        // must start the scheduler
        startMgr();

        CountDownLatch latch = new CountDownLatch(1);

        mgr.schedule(STD_ACTIVE_HEARTBEAT_MS, () -> {
            latch.countDown();
            return null;
        });

        // capture the task
        ArgumentCaptor<Runnable> taskCap = ArgumentCaptor.forClass(Runnable.class);

        verify(sched, times(2)).schedule(taskCap.capture(), any(Long.class), any(TimeUnit.class));

        // give it a heartbeat so that it transitions to the query state
        StartState st = (StartState) mgr.getCurrent();
        Heartbeat hb = new Heartbeat(mgr.getHost(), st.getHbTimestampMs());
        hb.setChannel(Message.ADMIN);

        String msg = ser.encodeMsg(hb);

        mgr.onTopicEvent(CommInfrastructure.UEB, MY_TOPIC, msg);

        assertTrue(mgr.getCurrent() instanceof QueryState);

        // execute it
        taskCap.getValue().run();

        // it should NOT have counted down
        assertEquals(1, latch.getCount());
    }

    /**
     * Validates the message content.
     * 
     * @param tbegin creation time stamp must be no less than this
     * @param msg message to be validated
     */
    private void validateMessageContent(long tbegin, Forward msg) {
        assertEquals(0, msg.getNumHops());
        assertTrue(msg.getCreateTimeMs() >= tbegin);
        assertEquals(mgr.getHost(), msg.getSource());
        assertEquals(CommInfrastructure.UEB, msg.getProtocol());
        assertEquals(TOPIC2, msg.getTopic());
        assertEquals(THE_EVENT, msg.getPayload());
        assertEquals(REQUEST_ID, msg.getRequestId());
    }

    /**
     * Configure the mock controller to act like a real controller, invoking beforeOffer
     * and then beforeInsert, so we can make sure they pass through. We'll keep count to
     * ensure we don't get into infinite recursion.
     * 
     * @param invokeBeforeInsert {@code true} if beforeInsert() should be invoked,
     *        {@code false} if it should be skipped
     * 
     * @return a latch that will be counted down if both beforeXxx() methods return false
     */
    private CountDownLatch catchRecursion(boolean invokeBeforeInsert) {
        CountDownLatch recursion = new CountDownLatch(3);
        CountDownLatch latch = new CountDownLatch(1);

        doAnswer(args -> {

            recursion.countDown();
            if (recursion.getCount() == 0) {
                fail("recursive calls to onTopicEvent");
            }

            int iarg = 0;
            CommInfrastructure proto = args.getArgument(iarg++);
            String topic = args.getArgument(iarg++);
            String event = args.getArgument(iarg++);

            if (mgr.beforeOffer(proto, topic, event)) {
                return null;
            }

            if (invokeBeforeInsert && mgr.beforeInsert(proto, topic, event, DECODED_EVENT)) {
                return null;
            }

            latch.countDown();

            return null;
        }).when(controller).onTopicEvent(any(), any(), any());

        return latch;
    }

    /**
     * Makes an assignment with two buckets.
     * 
     * @param sameHost {@code true} if the {@link #REQUEST_ID} should hash to the
     *        manager's bucket, {@code false} if it should hash to the other host's bucket
     * @return a new bucket assignment
     */
    private BucketAssignments makeAssignments(boolean sameHost) {
        int slot = REQUEST_ID.hashCode() % 2;

        // slot numbers are 0 and 1 - reverse them if it's for a different host
        if (!sameHost) {
            slot = 1 - slot;
        }

        String[] asgn = new String[2];
        asgn[slot] = mgr.getHost();
        asgn[1 - slot] = HOST2;

        return new BucketAssignments(asgn);
    }

    /**
     * Invokes methods necessary to start the manager.
     * 
     * @throws PoolingFeatureException if an error occurs
     */
    private void startMgr() throws PoolingFeatureException {
        mgr.beforeStart();
        mgr.afterStart();
    }

    /**
     * Invokes methods necessary to lock the manager.
     */
    private void lockMgr() {
        mgr.beforeLock();
    }

    /**
     * Invokes methods necessary to unlock the manager.
     */
    private void unlockMgr() {
        mgr.afterUnlock();
    }

    /**
     * Used to create a mock object that implements both super interfaces.
     */
    private static interface ListeningController extends TopicListener, PolicyController {

    }

    /**
     * Invokes a method that is expected to throw an exception.
     * 
     * @param exClass class of exception that is expected
     * @param func function to invoke
     * @return the exception that was thrown
     * @throws AssertionError if no exception was thrown
     */
    private <T extends Exception> T expectException(Class<T> exClass, ExFunction<T> func) {
        try {
            func.apply(null);
            throw new AssertionError("missing exception");

        } catch (Exception e) {
            return exClass.cast(e);
        }
    }

    /**
     * Function that is expected to throw an exception.
     * 
     * @param <T> type of exception the function is expected to throw
     */
    @FunctionalInterface
    private static interface ExFunction<T extends Exception> {

        /**
         * Invokes the function.
         * 
         * @param arg always {@code null}
         * @throws T if an error occurs
         */
        public void apply(Void arg) throws T;

    }
}
