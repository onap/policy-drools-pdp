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

package org.onap.policy.drools.pooling.state;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ScheduledFuture;
import org.onap.policy.drools.pooling.PoolingManager;
import org.onap.policy.drools.pooling.PoolingProperties;
import org.onap.policy.drools.pooling.message.BucketAssignments;
import org.onap.policy.drools.pooling.message.Leader;
import org.onap.policy.drools.pooling.message.Message;

/**
 * Superclass used to test subclasses of {@link Message}.
 */
public class BasicStateTester {

    protected static final long STD_HEARTBEAT_WAIT_MS = 10;
    protected static final long STD_REACTIVATE_WAIT_MS = STD_HEARTBEAT_WAIT_MS + 1;
    protected static final long STD_IDENTIFICATION_MS = STD_REACTIVATE_WAIT_MS + 1;
    protected static final long STD_ACTIVE_HEARTBEAT_MS = STD_IDENTIFICATION_MS + 1;
    protected static final long STD_INTER_HEARTBEAT_MS = STD_ACTIVE_HEARTBEAT_MS + 1;

    protected static final String MY_TOPIC = "myTopic";

    protected static final String PREV_HOST = "prevHost";
    protected static final String PREV_HOST2 = PREV_HOST + "A";

    // this follows PREV_HOST, alphabetically
    protected static final String MY_HOST = PREV_HOST + "X";

    // these follow MY_HOST, alphabetically
    protected static final String HOST1 = MY_HOST + "1";
    protected static final String HOST2 = MY_HOST + "2";
    protected static final String HOST3 = MY_HOST + "3";
    protected static final String HOST4 = MY_HOST + "4";

    protected static final String LEADER = HOST1;

    protected static final String[] HOST_ARR3 = {HOST1, MY_HOST, HOST2};

    protected static final BucketAssignments EMPTY_ASGN = new BucketAssignments();
    protected static final BucketAssignments ASGN3 = new BucketAssignments(HOST_ARR3);

    /**
     * Futures returned by schedule().
     */
    protected LinkedList<ScheduledFuture<?>> onceFutures;

    /**
     * Tasks captured via schedule().
     */
    protected LinkedList<Pair<Long, StateTimerTask>> onceTasks;

    /**
     * Futures returned by scheduleWithFixedDelay().
     */
    protected LinkedList<ScheduledFuture<?>> repeatedFutures;

    /**
     * Tasks captured via scheduleWithFixedDelay().
     */
    protected LinkedList<Triple<Long, Long, StateTimerTask>> repeatedTasks;

    /**
     * Messages captured via publish().
     */
    protected LinkedList<Pair<String, Message>> published;

    /**
     * Messages captured via publishAdmin().
     */
    protected LinkedList<Message> admin;

    protected PoolingManager mgr;
    protected PoolingProperties props;
    protected State prevState;

    public BasicStateTester() {
        super();
    }

    public void setUp() throws Exception {
        onceFutures = new LinkedList<>();
        onceTasks = new LinkedList<>();

        repeatedFutures = new LinkedList<>();
        repeatedTasks = new LinkedList<>();

        published = new LinkedList<>();
        admin = new LinkedList<>();

        mgr = mock(PoolingManager.class);
        props = mock(PoolingProperties.class);

        when(mgr.getHost()).thenReturn(MY_HOST);
        when(mgr.getTopic()).thenReturn(MY_TOPIC);
        when(mgr.getProperties()).thenReturn(props);

        when(props.getStartHeartbeatMs()).thenReturn(STD_HEARTBEAT_WAIT_MS);
        when(props.getReactivateMs()).thenReturn(STD_REACTIVATE_WAIT_MS);
        when(props.getIdentificationMs()).thenReturn(STD_IDENTIFICATION_MS);
        when(props.getActiveHeartbeatMs()).thenReturn(STD_ACTIVE_HEARTBEAT_MS);
        when(props.getInterHeartbeatMs()).thenReturn(STD_INTER_HEARTBEAT_MS);

        prevState = new State(mgr) {
            @Override
            public Map<String, Object> getFilter() {
                throw new UnsupportedOperationException("cannot filter");
            }
        };

        // capture publish() arguments
        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            published.add(new Pair<>((String) args[0], (Message) args[1]));

            return null;
        }).when(mgr).publish(anyString(), any(Message.class));

        // capture publishAdmin() arguments
        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            admin.add((Message) args[0]);

            return null;
        }).when(mgr).publishAdmin(any(Message.class));

        // capture schedule() arguments, and return a new future
        when(mgr.schedule(anyLong(), any(StateTimerTask.class))).thenAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            onceTasks.add(new Pair<>((Long) args[0], (StateTimerTask) args[1]));

            ScheduledFuture<?> fut = mock(ScheduledFuture.class);
            onceFutures.add(fut);
            return fut;
        });

        // capture scheduleWithFixedDelay() arguments, and return a new future
        when(mgr.scheduleWithFixedDelay(anyLong(), anyLong(), any(StateTimerTask.class))).thenAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            repeatedTasks.add(new Triple<>((Long) args[0], (Long) args[1], (StateTimerTask) args[2]));

            ScheduledFuture<?> fut = mock(ScheduledFuture.class);
            repeatedFutures.add(fut);
            return fut;
        });
    }

    /**
     * Makes a sorted set of hosts.
     * 
     * @param hosts the hosts to be sorted
     * @return the set of hosts, sorted
     */
    protected SortedSet<String> sortHosts(String... hosts) {
        return new TreeSet<>(Arrays.asList(hosts));
    }

    /**
     * Captures the host array from the Leader message published to the admin
     * channel.
     * 
     * @return the host array, as a list
     */
    protected List<String> captureHostList() {
        return Arrays.asList(captureHostArray());
    }

    /**
     * Captures the host array from the Leader message published to the admin
     * channel.
     * 
     * @return the host array
     */
    protected String[] captureHostArray() {
        BucketAssignments asgn = captureAssignments();

        String[] arr = asgn.getHostArray();
        assertNotNull(arr);

        return arr;
    }

    /**
     * Captures the assignments from the Leader message published to the admin
     * channel.
     * 
     * @return the bucket assignments
     */
    protected BucketAssignments captureAssignments() {
        Leader msg = captureAdminMessage(Leader.class);

        BucketAssignments asgn = msg.getAssignments();
        assertNotNull(asgn);
        return asgn;
    }

    /**
     * Captures the message published to the admin channel.
     * 
     * @param clazz type of {@link Message} to capture
     * @return the message that was published
     */
    protected <T extends Message> T captureAdminMessage(Class<T> clazz) {
        return captureAdminMessage(clazz, 0);
    }

    /**
     * Captures the message published to the admin channel.
     * 
     * @param clazz type of {@link Message} to capture
     * @param index index of the item to be captured
     * @return the message that was published
     */
    protected <T extends Message> T captureAdminMessage(Class<T> clazz, int index) {
        return clazz.cast(admin.get(index));
    }

    /**
     * Captures the message published to the non-admin channels.
     * 
     * @param clazz type of {@link Message} to capture
     * @return the (channel,message) pair that was published
     */
    protected <T extends Message> Pair<String, T> capturePublishedMessage(Class<T> clazz) {
        return capturePublishedMessage(clazz, 0);
    }

    /**
     * Captures the message published to the non-admin channels.
     * 
     * @param clazz type of {@link Message} to capture
     * @param index index of the item to be captured
     * @return the (channel,message) pair that was published
     */
    protected <T extends Message> Pair<String, T> capturePublishedMessage(Class<T> clazz, int index) {
        Pair<String, Message> msg = published.get(index);
        return new Pair<>(msg.first, clazz.cast(msg.second));
    }

    /**
     * Pair of values.
     * 
     * @param <F> first value's type
     * @param <S> second value's type
     */
    public static class Pair<F, S> {
        public final F first;
        public final S second;

        public Pair(F first, S second) {
            this.first = first;
            this.second = second;
        }
    }

    /**
     * Pair of values.
     * 
     * @param <F> first value's type
     * @param <S> second value's type
     * @param <T> third value's type
     */
    public static class Triple<F, S, T> {
        public final F first;
        public final S second;
        public final T third;

        public Triple(F first, S second, T third) {
            this.first = first;
            this.second = second;
            this.third = third;
        }
    }

}
