/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018-2020 AT&T Intellectual Property. All rights reserved.
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
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
import org.onap.policy.drools.pooling.message.BucketAssignments;
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

        dmaap = mock(DmaapManager.class);
        gotDmaap = false;
        controller = mock(ListeningController.class);
        sched = mock(ScheduledThreadPoolExecutor.class);
        schedCount = 0;
        drools = mock(DroolsController.class);

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

        verify(dmaap, times(START_PUB)).publish(any());

        mgr.beforeStop();

        verify(dmaap).stopConsumer(mgr);
        verify(sched).shutdownNow();
        verify(dmaap, times(START_PUB + 1)).publish(any());
        verify(dmaap).publish(contains("offline"));

        assertTrue(mgr.getCurrent() instanceof IdleState);

        // verify that next message is handled locally
        assertFalse(mgr.beforeInsert(TOPIC2, DECODED_EVENT));
        verify(dmaap, times(START_PUB + 1)).publish(any());
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

        /*
         * now go offline while it's locked
         */
        lockMgr();

        // should have cancelled the timers
        assertEquals(2, futures.size());
        verify(futures.poll()).cancel(false);
        verify(futures.poll()).cancel(false);

        /*
         * now go back online
         */
        unlockMgr();

        // new timers should now be active
        assertEquals(2, futures.size());
        verify(futures.poll(), never()).cancel(false);
        verify(futures.poll(), never()).cancel(false);
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
    public void testBeforeOffer_Unlocked() throws Exception {
        startMgr();

        // route the message to another host
        mgr.startDistributing(makeAssignments(false));

        assertFalse(mgr.beforeOffer(TOPIC2, THE_EVENT));
    }

    @Test
    public void testBeforeOffer_Locked() throws Exception {
        startMgr();
        lockMgr();

        // route the message to another host
        mgr.startDistributing(makeAssignments(false));

        assertTrue(mgr.beforeOffer(TOPIC2, THE_EVENT));
    }

    @Test
    public void testBeforeInsert() throws Exception {
        startMgr();
        lockMgr();

        // route the message to this host
        mgr.startDistributing(makeAssignments(true));

        assertFalse(mgr.beforeInsert(TOPIC2, DECODED_EVENT));
    }

    @Test
    public void testHandleExternalCommInfrastructureStringStringString_NullReqId() throws Exception {
        validateHandleReqId(null);
    }

    @Test
    public void testHandleExternalCommInfrastructureStringStringString_EmptyReqId() throws Exception {
        validateHandleReqId("");
    }

    @Test
    public void testHandleExternalCommInfrastructureStringStringString_InvalidMsg() throws Exception {
        startMgr();

        assertFalse(mgr.beforeInsert(TOPIC2, "invalid message"));
    }

    @Test
    public void testHandleExternalCommInfrastructureStringStringString() throws Exception {
        validateUnhandled();
    }

    @Test
    public void testHandleExternalForward_NoAssignments() throws Exception {
        validateUnhandled();
    }

    @Test
    public void testHandleExternalForward() throws Exception {
        validateNoForward();
    }

    @Test
    public void testHandleEvent_NullTarget() throws Exception {
        // buckets have null targets
        validateDiscarded(new BucketAssignments(new String[] {null, null}));
    }

    @Test
    public void testHandleEvent_SameHost() throws Exception {
        validateNoForward();
    }

    @Test
    public void testHandleEvent_DiffHost() throws Exception {
        // route the message to the *OTHER* host
        validateDiscarded(makeAssignments(false));
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

        assertFalse(mgr.beforeOffer(TOPIC2, THE_EVENT));
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

        assertFalse(mgr.beforeOffer(TOPIC2, THE_EVENT));
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

        assertFalse(mgr.beforeOffer(TOPIC2, THE_EVENT));
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

        assertFalse(mgr.beforeOffer(TOPIC2, THE_EVENT));
    }

    @Test
    public void testDecodeEvent() throws Exception {
        startMgr();

        when(controller.isLocked()).thenReturn(true);

        // route to another host
        mgr.startDistributing(makeAssignments(false));

        assertTrue(mgr.beforeOffer(TOPIC2, THE_EVENT));
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
        validateNoForward();


        // null assignments should cause message to be processed locally
        mgr.startDistributing(null);
        assertFalse(mgr.beforeInsert(TOPIC2, DECODED_EVENT));
        verify(dmaap, times(START_PUB)).publish(any());


        // message for this host
        mgr.startDistributing(makeAssignments(true));
        assertFalse(mgr.beforeInsert(TOPIC2, DECODED_EVENT));


        // message for another host
        mgr.startDistributing(makeAssignments(false));
        assertTrue(mgr.beforeInsert(TOPIC2, DECODED_EVENT));
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

    private void validateHandleReqId(String requestId) throws PoolingFeatureException {
        startMgr();

        assertFalse(mgr.beforeInsert(TOPIC2, DECODED_EVENT));
    }

    private void validateNoForward() throws PoolingFeatureException {
        startMgr();

        // route the message to this host
        mgr.startDistributing(makeAssignments(true));

        assertFalse(mgr.beforeInsert(TOPIC2, DECODED_EVENT));

        verify(dmaap, times(START_PUB)).publish(any());
    }

    private void validateUnhandled() throws PoolingFeatureException {
        startMgr();
        assertFalse(mgr.beforeInsert(TOPIC2, DECODED_EVENT));
    }

    private void validateDiscarded(BucketAssignments bucketAssignments) throws PoolingFeatureException {
        startMgr();

        // buckets have null targets
        mgr.startDistributing(bucketAssignments);

        assertTrue(mgr.beforeInsert(TOPIC2, DECODED_EVENT));
    }

    /**
     * Makes an assignment with two buckets.
     *
     * @param sameHost {@code true} if the {@link #REQUEST_ID} should hash to the
     *        manager's bucket, {@code false} if it should hash to the other host's bucket
     * @return a new bucket assignment
     */
    private BucketAssignments makeAssignments(boolean sameHost) {
        int slot = DECODED_EVENT.hashCode() % 2;

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
        when(controller.isLocked()).thenReturn(true);
    }

    /**
     * Invokes methods necessary to unlock the manager.
     */
    private void unlockMgr() {
        mgr.afterUnlock();
        when(controller.isLocked()).thenReturn(false);
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
