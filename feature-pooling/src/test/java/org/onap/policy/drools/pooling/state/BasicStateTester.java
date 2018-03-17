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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockito.ArgumentCaptor;
import org.onap.policy.drools.pooling.PoolingManager;
import org.onap.policy.drools.pooling.PoolingProperties;
import org.onap.policy.drools.pooling.message.BucketAssignments;
import org.onap.policy.drools.pooling.message.Leader;
import org.onap.policy.drools.pooling.message.Message;
import org.onap.policy.drools.pooling.state.State.StateFactory;

/**
 * Superclass used to test subclasses of {@link Message}.
 */
public class BasicStateTester {

    protected static final int STD_HEARTBEAT_WAIT_MS = 10;
    protected static final int STD_REACTIVATE_WAIT_MS = STD_HEARTBEAT_WAIT_MS + 1;
    protected static final int STD_IDENTIFICATION_MS = STD_REACTIVATE_WAIT_MS + 1;
    
    protected static final String MY_TOPIC = "myTopic";

    protected static final String PREV_HOST = "prevHost";

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

    protected static StateFactory saveFactory;

    protected StateFactory factory;
    protected PoolingManager mgr;
    protected PoolingProperties props;
    protected State prevState;

    public BasicStateTester() {
        super();
    }

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        saveFactory = State.getFactory();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        State.setFactory(saveFactory);
    }

    public void setUp() throws Exception {
        factory = mock(StateFactory.class);
        mgr = mock(PoolingManager.class);
        props = mock(PoolingProperties.class);

        when(mgr.getHost()).thenReturn(MY_HOST);
        when(mgr.getTopic()).thenReturn(MY_TOPIC);
        when(mgr.getProperties()).thenReturn(props);
        
        when(props.getQueryHeartbeatMs()).thenReturn(STD_HEARTBEAT_WAIT_MS);
        when(props.getReactivateMs()).thenReturn(STD_REACTIVATE_WAIT_MS);
        when(props.getIdentificationMs()).thenReturn(STD_IDENTIFICATION_MS);

        State.setFactory(factory);

        prevState = new State(mgr) {
            @Override
            public Map<String, Object> getFilter() {
                throw new UnsupportedOperationException("cannot filter");
            }
        };
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
        // capture the publishing parameter
        ArgumentCaptor<T> msgCap = ArgumentCaptor.forClass(clazz);

        verify(mgr).publishAdmin(msgCap.capture());

        assertFalse(msgCap.getAllValues().isEmpty());

        return msgCap.getValue();
    }

    /**
     * Captures the message published to the non-admin channels.
     * 
     * @param clazz type of {@link Message} to capture
     * @return the (channel,message) pair that was published
     */
    protected <T extends Message> Pair<String,T> capturePublishedMessage(Class<T> clazz) {
        
        ArgumentCaptor<String> channelCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<T> msgCap = ArgumentCaptor.forClass(clazz);

        verify(mgr).publish(channelCap.capture(), msgCap.capture());

        assertFalse(msgCap.getAllValues().isEmpty());

        return new Pair<>(channelCap.getValue(), msgCap.getValue());
    }

    /**
     * Captures the arguments to a call to schedule().
     * 
     * @return the (delay,task) pair that was scheduled
     */
    protected Pair<Long,StateTimerTask> captureSchedule() {
        
        ArgumentCaptor<Long> delayCap = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<StateTimerTask> taskCap = ArgumentCaptor.forClass(StateTimerTask.class);

        verify(mgr).schedule(delayCap.capture(), taskCap.capture());

        assertFalse(taskCap.getAllValues().isEmpty());

        return new Pair<>(delayCap.getValue(), taskCap.getValue());
    }
    
    /**
     * Pair of values.
     * 
     * @param <F>   first value's type
     * @param <S>   second value's type
     */
    public static class Pair<F,S> {
        public final F first;
        public final S second;
        
        public Pair(F first, S second) {
            this.first = first;
            this.second = second;            
        }
    }

}
