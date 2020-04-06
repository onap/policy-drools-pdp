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

package org.onap.policy.drools.pooling;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.onap.policy.common.endpoints.event.comm.Topic.CommInfrastructure;
import org.onap.policy.common.endpoints.event.comm.TopicListener;
import org.onap.policy.drools.controller.DroolsController;
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
     * Futures that have been allocated due to calls to scheduleXxx().
     */
    private Queue<ScheduledFuture<?>> futures;

    private PoolingProperties poolProps;
    private ListeningController controller;
    private ClassExtractors extractors;
    private DmaapManager dmaap;
    private boolean gotDmaap;
    private ScheduledThreadPoolExecutor sched;
    private int schedCount;
    private DroolsController drools;
    private Serializer ser;
    private CountDownLatch active;

    private PoolingManagerImpl mgr;

    /**
     * Setup.
     *
     * @throws Exception throws exception
     */
    @Before
    public void setUp() throws Exception {
        Properties plainProps = new Properties();

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
        active = new CountDownLatch(1);

        extractors = mock(ClassExtractors.class);
        dmaap = mock(DmaapManager.class);
        gotDmaap = false;
        controller = mock(ListeningController.class);
        sched = mock(ScheduledThreadPoolExecutor.class);
        schedCount = 0;
        drools = mock(DroolsController.class);

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

        mgr = new PoolingManagerTest(MY_HOST, controller, poolProps, active);
    }

    @Test
    public void testPoolingManagerImpl() throws Exception {
        assertTrue(gotDmaap);

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

        assertThatThrownBy(() -> new PoolingManagerTest(MY_HOST, ctlr, poolProps, active))
                        .isInstanceOf(PoolingFeatureRtException.class).hasCauseInstanceOf(ClassCastException.class);
    }

    @Test
    public void testPoolingManagerImpl_PoolEx() throws PoolingFeatureException {
        // throw an exception when we try to create the dmaap manager
        PoolingFeatureException ex = new PoolingFeatureException();

        assertThatThrownBy(() -> new PoolingManagerTest(MY_HOST, controller, poolProps, active) {
            @Override
            protected DmaapManager makeDmaapManager(String topic) throws PoolingFeatureException {
                throw ex;
            }
        }).isInstanceOf(PoolingFeatureRtException.class).hasCause(ex);
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

        mgr = new PoolingManagerTest(HOST2, controller, poolProps, active);
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

        assertEquals(1, schedCount);
        verify(sched).setMaximumPoolSize(1);
        verify(sched).setContinueExistingPeriodicTasksAfterShutdownPolicy(false);


        // try again - nothing should happen
        mgr.beforeStart();

        verify(dmaap).startPublisher();

        assertEquals(1, schedCount);
        verify(sched).setMaximumPoolSize(1);
        verify(sched).setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
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
        mgr.startDistributing(makeAssignments(false));

        Forward msg = new Forward(mgr.getHost(), CommInfrastructure.UEB, TOPIC2, THE_EVENT, REQUEST_ID);
        mgr.handle(msg);
        verify(dmaap, times(START_PUB + 1)).publish(any());

        mgr.beforeStop();

        verify(dmaap).stopConsumer(mgr);
        verify(sched).shutdownNow();
        verify(dmaap, times(START_PUB + 2)).publish(any());
        verify(dmaap).publish(contains("offline"));

        assertTrue(mgr.getCurrent() instanceof IdleState);

        // verify that next message is handled locally
        mgr.handle(msg);
        verify(dmaap, times(START_PUB + 2)).publish(any());
        verify(controller).onTopicEvent(CommInfrastructure.UEB, TOPIC2, THE_EVENT);
    }

    @Test
    public void testBeforeStop_NotRunning() throws Exception {
        final State st = mgr.getCurrent();

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

        final State st = mgr.getCurrent();

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

        mgr.afterStop();

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
        assertThatCode(() -> startMgr()).doesNotThrowAnyException();

        // no exception, means success
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

        assertThatCode(() -> mgr.publish(Message.ADMIN, new Offline(mgr.getHost()))).doesNotThrowAnyException();
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

        assertThatCode(() -> mgr.onTopicEvent(CommInfrastructure.UEB, TOPIC2, null)).doesNotThrowAnyException();
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

        final CountDownLatch latch = catchRecursion(false);

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

        final CountDownLatch latch = catchRecursion(true);

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

        assertFalse(mgr.beforeInsert(CommInfrastructure.UEB, TOPIC2, THE_EVENT, DECODED_EVENT));
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
    }

    @Test
    public void testHandleExternalCommInfrastructureStringStringString() throws Exception {
        startMgr();

        assertFalse(mgr.beforeInsert(CommInfrastructure.UEB, TOPIC2, THE_EVENT, DECODED_EVENT));
    }

    @Test
    public void testHandleExternalForward_NoAssignments() throws Exception {
        startMgr();

        assertFalse(mgr.beforeInsert(CommInfrastructure.UEB, TOPIC2, THE_EVENT, DECODED_EVENT));
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

        mgr = new PoolingManagerTest(MY_HOST, controller, poolProps, active) {
            @Override
            protected boolean canDecodeEvent(DroolsController drools2, String topic2) {
                return false;
            }
        };

        startMgr();

        when(controller.isLocked()).thenReturn(true);

        // create assignments, though they are irrelevant
        mgr.startDistributing(makeAssignments(false));

        assertFalse(mgr.beforeOffer(CommInfrastructure.UEB, TOPIC2, THE_EVENT));
    }

    @Test
    public void testDecodeEvent_UnsuppEx() throws Exception {

        // generate exception
        mgr = new PoolingManagerTest(MY_HOST, controller, poolProps, active) {
            @Override
            protected Object decodeEventWrapper(DroolsController drools2, String topic2, String event) {
                throw new UnsupportedOperationException();
            }
        };

        startMgr();

        when(controller.isLocked()).thenReturn(true);

        // create assignments, though they are irrelevant
        mgr.startDistributing(makeAssignments(false));

        assertFalse(mgr.beforeOffer(CommInfrastructure.UEB, TOPIC2, THE_EVENT));
    }

    @Test
    public void testDecodeEvent_ArgEx() throws Exception {
        // generate exception
        mgr = new PoolingManagerTest(MY_HOST, controller, poolProps, active) {
            @Override
            protected Object decodeEventWrapper(DroolsController drools2, String topic2, String event) {
                throw new IllegalArgumentException();
            }
        };

        startMgr();

        when(controller.isLocked()).thenReturn(true);

        // create assignments, though they are irrelevant
        mgr.startDistributing(makeAssignments(false));

        assertFalse(mgr.beforeOffer(CommInfrastructure.UEB, TOPIC2, THE_EVENT));
    }

    @Test
    public void testDecodeEvent_StateEx() throws Exception {
        // generate exception
        mgr = new PoolingManagerTest(MY_HOST, controller, poolProps, active) {
            @Override
            protected Object decodeEventWrapper(DroolsController drools2, String topic2, String event) {
                throw new IllegalStateException();
            }
        };

        startMgr();

        when(controller.isLocked()).thenReturn(true);

        // create assignments, though they are irrelevant
        mgr.startDistributing(makeAssignments(false));

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

        // route the message to another host
        mgr.startDistributing(makeAssignments(false));

        assertTrue(mgr.beforeInsert(CommInfrastructure.UEB, TOPIC2, THE_EVENT, DECODED_EVENT));

        verify(dmaap, times(START_PUB + 1)).publish(any());
    }

    @Test
    public void testMakeForward_InvalidMsg() throws Exception {
        startMgr();

        // route the message to another host
        mgr.startDistributing(makeAssignments(false));

        assertTrue(mgr.beforeInsert(null, TOPIC2, THE_EVENT, DECODED_EVENT));

        // should not have tried to publish a message
        verify(dmaap, times(START_PUB)).publish(any());
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

        final CountDownLatch latch = catchRecursion(true);

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

        final CountDownLatch latch = catchRecursion(true);

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
    public void testHandleInternal_IoEx() throws Exception {
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
        mgr.startDistributing(makeAssignments(true));
        assertFalse(mgr.beforeInsert(CommInfrastructure.UEB, TOPIC2, THE_EVENT, DECODED_EVENT));
        verify(dmaap, times(START_PUB)).publish(any());


        // null assignments should cause message to be processed locally
        mgr.startDistributing(null);
        assertFalse(mgr.beforeInsert(CommInfrastructure.UEB, TOPIC2, THE_EVENT, DECODED_EVENT));
        verify(dmaap, times(START_PUB)).publish(any());


        // route the message to this host
        mgr.startDistributing(makeAssignments(true));
        assertFalse(mgr.beforeInsert(CommInfrastructure.UEB, TOPIC2, THE_EVENT, DECODED_EVENT));
        verify(dmaap, times(START_PUB)).publish(any());


        // route the message to the other host
        mgr.startDistributing(makeAssignments(false));
        assertTrue(mgr.beforeInsert(CommInfrastructure.UEB, TOPIC2, THE_EVENT, DECODED_EVENT));
        verify(dmaap, times(START_PUB + 1)).publish(any());
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
        assertEquals(0, active.getCount());
    }

    @Test
    public void testGoInactive() {
        State st = mgr.goInactive();
        assertTrue(st instanceof InactiveState);
        assertEquals(mgr.getHost(), st.getHost());
        assertEquals(1, active.getCount());
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
     * Manager with overrides.
     */
    private class PoolingManagerTest extends PoolingManagerImpl {

        public PoolingManagerTest(String host, PolicyController controller, PoolingProperties props,
                        CountDownLatch activeLatch) {

            super(host, controller, props, activeLatch);
        }

        @Override
        protected ClassExtractors makeClassExtractors(Properties props) {
            return extractors;
        }

        @Override
        protected DmaapManager makeDmaapManager(String topic) throws PoolingFeatureException {
            gotDmaap = true;
            return dmaap;
        }

        @Override
        protected ScheduledThreadPoolExecutor makeScheduler() {
            ++schedCount;
            return sched;
        }

        @Override
        protected boolean canDecodeEvent(DroolsController drools2, String topic2) {
            return (drools2 == drools && TOPIC2.equals(topic2));
        }

        @Override
        protected Object decodeEventWrapper(DroolsController drools2, String topic2, String event) {
            if (drools2 == drools && TOPIC2.equals(topic2) && event == THE_EVENT) {
                return DECODED_EVENT;
            } else {
                return null;
            }
        }
    }
}
