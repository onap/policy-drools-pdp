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

package org.onap.policy.drools.system.internal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.common.endpoints.event.comm.Topic.CommInfrastructure;
import org.onap.policy.common.endpoints.event.comm.TopicEndpoint;
import org.onap.policy.common.endpoints.event.comm.TopicSink;
import org.onap.policy.common.endpoints.event.comm.TopicSource;
import org.onap.policy.common.utils.gson.GsonTestUtils;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.controller.DroolsControllerFactory;
import org.onap.policy.drools.features.PolicyControllerFeatureApi;
import org.onap.policy.drools.persistence.SystemPersistence;
import org.onap.policy.drools.protocol.configuration.DroolsConfiguration;
import org.onap.policy.drools.system.GsonMgmtTestBuilder;

public class AggregatedPolicyControllerTest {

    private static final String AGG_NAME = "agg-name";
    private static final String SINK_TOPIC1 = "sink-a";
    private static final String SINK_TOPIC2 = "sink-b";
    private static final String SOURCE_TOPIC1 = "source-a";
    private static final String SOURCE_TOPIC2 = "source-b";

    private static final String EXPECTED = "expected exception";

    private static final String MY_EVENT = "my-event";

    private static final String ARTIFACT1 = "artifact-a";
    private static final String GROUP1 = "group-a";
    private static final String VERSION1 = "version-a";

    private static final String ARTIFACT2 = "artifact-b";
    private static final String GROUP2 = "group-b";
    private static final String VERSION2 = "version-b";

    private Properties properties;
    private TopicEndpoint endpointMgr;
    private List<TopicSource> sources;
    private TopicSource source1;
    private TopicSource source2;
    private List<TopicSink> sinks;
    private TopicSink sink1;
    private TopicSink sink2;
    private SystemPersistence persist;
    private DroolsControllerFactory droolsFactory;
    private DroolsController drools;
    private DroolsConfiguration config;
    private List<PolicyControllerFeatureApi> providers;
    private PolicyControllerFeatureApi prov1;
    private PolicyControllerFeatureApi prov2;
    private AggregatedPolicyController apc;

    /**
     * Initializes the object to be tested.
     */
    @Before
    public void setUp() {
        properties = new Properties();

        source1 = mock(TopicSource.class);
        source2 = mock(TopicSource.class);
        when(source1.getTopic()).thenReturn(SOURCE_TOPIC1);
        when(source2.getTopic()).thenReturn(SOURCE_TOPIC2);

        sink1 = mock(TopicSink.class);
        sink2 = mock(TopicSink.class);
        when(sink1.getTopic()).thenReturn(SINK_TOPIC1);
        when(sink2.getTopic()).thenReturn(SINK_TOPIC2);

        sources = Arrays.asList(source1, source2);
        sinks = Arrays.asList(sink1, sink2);

        endpointMgr = mock(TopicEndpoint.class);
        when(endpointMgr.addTopicSources(any(Properties.class))).thenReturn(sources);
        when(endpointMgr.addTopicSinks(any(Properties.class))).thenReturn(sinks);

        persist = mock(SystemPersistence.class);

        drools = mock(DroolsController.class);
        when(drools.start()).thenReturn(true);
        when(drools.stop()).thenReturn(true);
        when(drools.offer(any(), any())).thenReturn(true);
        when(drools.deliver(any(), any())).thenReturn(true);
        when(drools.lock()).thenReturn(true);
        when(drools.unlock()).thenReturn(true);
        when(drools.getArtifactId()).thenReturn(ARTIFACT1);
        when(drools.getGroupId()).thenReturn(GROUP1);
        when(drools.getVersion()).thenReturn(VERSION1);

        config = mock(DroolsConfiguration.class);
        when(config.getArtifactId()).thenReturn(ARTIFACT2);
        when(config.getGroupId()).thenReturn(GROUP2);
        when(config.getVersion()).thenReturn(VERSION2);

        droolsFactory = mock(DroolsControllerFactory.class);
        when(droolsFactory.build(any(), any(), any())).thenReturn(drools);

        prov1 = mock(PolicyControllerFeatureApi.class);
        prov2 = mock(PolicyControllerFeatureApi.class);

        providers = Arrays.asList(prov1, prov2);

        apc = new AggregatedPolicyControllerImpl(AGG_NAME, properties);
    }

    @Test
    public void testFactory() {
        apc = new AggregatedPolicyController(AGG_NAME, properties);
        assertNotNull(apc.getDroolsFactory());
        assertNotNull(apc.getEndpointManager());
        assertNotNull(apc.getProviders());
        assertNotNull(apc.getPersistenceManager());
    }

    @Test
    public void testAggregatedPolicyController_() {
        verify(persist).storeController(AGG_NAME, properties);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInitDrools_Ex() {
        new AggregatedPolicyControllerImpl(AGG_NAME, properties) {
            @Override
            protected DroolsControllerFactory getDroolsFactory() {
                throw new RuntimeException(EXPECTED);
            }
        };
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInitDrools_Error() {
        new AggregatedPolicyControllerImpl(AGG_NAME, properties) {
            @Override
            protected DroolsControllerFactory getDroolsFactory() {
                throw new LinkageError(EXPECTED);
            }
        };
    }

    @Test
    public void testUpdateDrools_ConfigVariations() {

        // config should return same values as current controller
        when(config.getArtifactId()).thenReturn(ARTIFACT1.toUpperCase());
        when(config.getGroupId()).thenReturn(GROUP1.toUpperCase());
        when(config.getVersion()).thenReturn(VERSION1.toUpperCase());

        assertTrue(apc.updateDrools(config));

        // number of times store should have been called
        int count = 0;

        // invoked once during construction, but shouldn't be invoked during update
        verify(persist, times(++count)).storeController(any(), any());


        // different artifact
        when(config.getArtifactId()).thenReturn(ARTIFACT2);

        assertTrue(apc.updateDrools(config));

        // should be invoked during update
        verify(persist, times(++count)).storeController(any(), any());


        // different group
        when(config.getArtifactId()).thenReturn(ARTIFACT1);
        when(config.getGroupId()).thenReturn(GROUP2);

        assertTrue(apc.updateDrools(config));

        // should be invoked during update
        verify(persist, times(++count)).storeController(any(), any());


        // different version
        when(config.getGroupId()).thenReturn(GROUP1);
        when(config.getVersion()).thenReturn(VERSION2);

        assertTrue(apc.updateDrools(config));

        // should be invoked during update
        verify(persist, times(++count)).storeController(any(), any());


        /*
         * Exception case.
         */
        when(drools.lock()).thenThrow(new IllegalArgumentException(EXPECTED));
        when(drools.unlock()).thenThrow(new IllegalArgumentException(EXPECTED));

        assertFalse(apc.updateDrools(config));
    }

    @Test
    public void testUpdateDrools_LockVariations() {
        // not locked
        apc.updateDrools(config);
        verify(drools, never()).lock();
        verify(drools).unlock();

        // locked
        setUp();
        apc.lock();
        apc.updateDrools(config);
        verify(drools, times(2)).lock();
        verify(drools, never()).unlock();
    }

    @Test
    public void testUpdateDrools_AliveVariations() {
        // not started
        apc.updateDrools(config);
        verify(drools, never()).start();
        verify(drools).stop();

        // started
        setUp();
        apc.start();
        apc.updateDrools(config);
        verify(drools, times(2)).start();
        verify(drools, never()).stop();
    }

    @Test
    public void testSerialize() {
        GsonTestUtils gson = new GsonMgmtTestBuilder().addDroolsControllerMock().addTopicSinkMock().addTopicSourceMock()
                        .build();
        assertThatCode(() -> gson.compareGson(apc, AggregatedPolicyControllerTest.class)).doesNotThrowAnyException();
    }

    @Test
    public void testGetName() {
        assertEquals(AGG_NAME, apc.getName());
    }

    @Test
    public void testStart() {
        // arrange for first provider to throw exceptions
        when(prov1.beforeStart(any())).thenThrow(new RuntimeException(EXPECTED));
        when(prov1.afterStart(any())).thenThrow(new RuntimeException(EXPECTED));

        // arrange for first sink to throw exception
        when(sink1.start()).thenThrow(new RuntimeException(EXPECTED));

        // start it
        assertTrue(apc.start());

        assertTrue(apc.isAlive());

        verify(prov1).beforeStart(apc);
        verify(prov2).beforeStart(apc);

        verify(source1).register(apc);
        verify(source2).register(apc);

        verify(sink1).start();
        verify(sink2).start();

        verify(prov1).afterStart(apc);
        verify(prov2).afterStart(apc);

        checkBeforeAfter(
            (prov, flag) -> when(prov.beforeStart(apc)).thenReturn(flag),
            (prov, flag) -> when(prov.afterStart(apc)).thenReturn(flag),
            () -> apc.start(),
            prov -> verify(prov).beforeStart(apc),
            () -> verify(source1).register(apc),
            prov -> verify(prov).afterStart(apc));
    }

    @Test
    public void testStart_AlreadyStarted() {
        apc.start();

        // re-start it
        assertTrue(apc.start());

        assertTrue(apc.isAlive());

        // these should now have been called twice
        verify(prov1, times(2)).beforeStart(apc);
        verify(prov2, times(2)).beforeStart(apc);

        // these should still only have been called once
        verify(source1).register(apc);
        verify(sink1).start();
        verify(prov1).afterStart(apc);
    }

    @Test
    public void testStart_Locked() {
        apc.lock();

        // start it
        assertThatIllegalStateException().isThrownBy(() -> apc.start());

        assertFalse(apc.isAlive());

        // should call beforeStart(), but stop after that
        verify(prov1).beforeStart(apc);
        verify(prov2).beforeStart(apc);

        verify(source1, never()).register(apc);
        verify(sink1, never()).start();
        verify(prov1, never()).afterStart(apc);
    }

    @Test
    public void testStop() {
        // arrange for first provider to throw exceptions
        when(prov1.beforeStop(any())).thenThrow(new RuntimeException(EXPECTED));
        when(prov1.afterStop(any())).thenThrow(new RuntimeException(EXPECTED));

        // start it
        apc.start();

        // now stop it
        assertTrue(apc.stop());

        assertFalse(apc.isAlive());

        verify(prov1).beforeStop(apc);
        verify(prov2).beforeStop(apc);

        verify(source1).unregister(apc);
        verify(source2).unregister(apc);

        verify(prov1).afterStop(apc);
        verify(prov2).afterStop(apc);

        // ensure no shutdown operations were called
        verify(prov1, never()).beforeShutdown(apc);
        verify(droolsFactory, never()).shutdown(drools);
        verify(prov2, never()).afterShutdown(apc);

        checkBeforeAfter(
            (prov, flag) -> when(prov.beforeStop(apc)).thenReturn(flag),
            (prov, flag) -> when(prov.afterStop(apc)).thenReturn(flag),
            () -> {
                apc.start();
                apc.stop();
            },
            prov -> verify(prov).beforeStop(apc),
            () -> verify(source1).unregister(apc),
            prov -> verify(prov).afterStop(apc));
    }

    @Test
    public void testStop_AlreadyStopped() {
        apc.start();
        apc.stop();

        // now re-stop it
        assertTrue(apc.stop());

        // called again
        verify(prov1, times(2)).beforeStop(apc);
        verify(prov2, times(2)).beforeStop(apc);

        // should NOT be called again
        verify(source1).unregister(apc);
        verify(prov1).afterStop(apc);
    }

    @Test
    public void testShutdown() {
        // arrange for first provider to throw exceptions
        when(prov1.beforeShutdown(any())).thenThrow(new RuntimeException(EXPECTED));
        when(prov1.afterShutdown(any())).thenThrow(new RuntimeException(EXPECTED));

        // start it
        apc.start();

        // now shut it down
        apc.shutdown();

        verify(prov1).beforeShutdown(apc);
        verify(prov2).beforeShutdown(apc);

        assertFalse(apc.isAlive());

        verify(prov1).afterStop(apc);
        verify(prov2).afterStop(apc);

        verify(droolsFactory).shutdown(drools);

        verify(prov1).afterShutdown(apc);
        verify(prov2).afterShutdown(apc);

        // ensure no halt operation was called
        verify(prov1, never()).beforeHalt(apc);

        checkBeforeAfter(
            (prov, flag) -> when(prov.beforeShutdown(apc)).thenReturn(flag),
            (prov, flag) -> when(prov.afterShutdown(apc)).thenReturn(flag),
            () -> {
                apc.start();
                apc.shutdown();
            },
            prov -> verify(prov).beforeShutdown(apc),
            () -> verify(source1).unregister(apc),
            prov -> verify(prov).afterShutdown(apc));
    }

    @Test
    public void testHalt() {
        // arrange for first provider to throw exceptions
        when(prov1.beforeHalt(any())).thenThrow(new RuntimeException(EXPECTED));
        when(prov1.afterHalt(any())).thenThrow(new RuntimeException(EXPECTED));

        // start it
        apc.start();

        // now halt it
        apc.halt();

        verify(prov1).beforeHalt(apc);
        verify(prov2).beforeHalt(apc);

        assertFalse(apc.isAlive());

        verify(prov1).beforeStop(apc);
        verify(prov2).beforeStop(apc);

        verify(droolsFactory).destroy(drools);
        verify(persist).deleteController(AGG_NAME);

        verify(prov1).afterHalt(apc);
        verify(prov2).afterHalt(apc);

        // ensure no shutdown operation was called
        verify(prov1, never()).beforeShutdown(apc);

        checkBeforeAfter(
            (prov, flag) -> when(prov.beforeHalt(apc)).thenReturn(flag),
            (prov, flag) -> when(prov.afterHalt(apc)).thenReturn(flag),
            () -> {
                apc.start();
                apc.halt();
            },
            prov -> verify(prov).beforeHalt(apc),
            () -> verify(source1).unregister(apc),
            prov -> verify(prov).afterHalt(apc));
    }

    @Test
    public void testOnTopicEvent() {
        // arrange for first provider to throw exceptions
        when(prov1.beforeOffer(apc, CommInfrastructure.NOOP, SOURCE_TOPIC1, MY_EVENT))
                        .thenThrow(new RuntimeException(EXPECTED));
        when(prov1.afterOffer(apc, CommInfrastructure.NOOP, SOURCE_TOPIC1, MY_EVENT, true))
                        .thenThrow(new RuntimeException(EXPECTED));

        // start it
        apc.start();

        // now offer it
        apc.onTopicEvent(CommInfrastructure.NOOP, SOURCE_TOPIC1, MY_EVENT);

        verify(prov1).beforeOffer(apc, CommInfrastructure.NOOP, SOURCE_TOPIC1, MY_EVENT);
        verify(prov2).beforeOffer(apc, CommInfrastructure.NOOP, SOURCE_TOPIC1, MY_EVENT);

        verify(drools).offer(SOURCE_TOPIC1, MY_EVENT);

        verify(prov1).afterOffer(apc, CommInfrastructure.NOOP, SOURCE_TOPIC1, MY_EVENT, true);
        verify(prov2).afterOffer(apc, CommInfrastructure.NOOP, SOURCE_TOPIC1, MY_EVENT, true);

        checkBeforeAfter(
            (prov, flag) -> when(prov.beforeOffer(apc, CommInfrastructure.NOOP, SOURCE_TOPIC1, MY_EVENT))
                            .thenReturn(flag),
            (prov, flag) -> when(
                            prov.afterOffer(apc, CommInfrastructure.NOOP, SOURCE_TOPIC1, MY_EVENT, true))
                                            .thenReturn(flag),
            () -> {
                apc.start();
                apc.onTopicEvent(CommInfrastructure.NOOP, SOURCE_TOPIC1, MY_EVENT);
            },
            prov -> verify(prov).beforeOffer(apc, CommInfrastructure.NOOP, SOURCE_TOPIC1, MY_EVENT),
            () -> verify(drools).offer(SOURCE_TOPIC1, MY_EVENT),
            prov -> verify(prov).afterOffer(apc, CommInfrastructure.NOOP, SOURCE_TOPIC1, MY_EVENT, true));
    }

    @Test
    public void testOnTopicEvent_Locked() {
        // start it
        apc.start();

        apc.lock();

        // now offer it
        apc.onTopicEvent(CommInfrastructure.NOOP, SOURCE_TOPIC1, MY_EVENT);

        verify(prov1, never()).beforeOffer(apc, CommInfrastructure.NOOP, SOURCE_TOPIC1, MY_EVENT);
        verify(prov2, never()).beforeOffer(apc, CommInfrastructure.NOOP, SOURCE_TOPIC1, MY_EVENT);

        // never gets this far
        verify(drools, never()).offer(SOURCE_TOPIC1, MY_EVENT);
        verify(prov1, never()).afterOffer(apc, CommInfrastructure.NOOP, SOURCE_TOPIC1, MY_EVENT, true);
    }

    @Test
    public void testOnTopicEvent_NotStarted() {

        // offer it
        apc.onTopicEvent(CommInfrastructure.NOOP, SOURCE_TOPIC1, MY_EVENT);

        verify(prov1, never()).beforeOffer(apc, CommInfrastructure.NOOP, SOURCE_TOPIC1, MY_EVENT);
        verify(prov2, never()).beforeOffer(apc, CommInfrastructure.NOOP, SOURCE_TOPIC1, MY_EVENT);

        // never gets this far
        verify(drools, never()).offer(SOURCE_TOPIC1, MY_EVENT);
        verify(prov1, never()).afterOffer(apc, CommInfrastructure.NOOP, SOURCE_TOPIC1, MY_EVENT, true);
    }

    @Test
    public void testDeliver_testInitSinks() {
        // arrange for first provider to throw exceptions
        when(prov1.beforeDeliver(apc, CommInfrastructure.NOOP, SINK_TOPIC1, MY_EVENT))
                        .thenThrow(new RuntimeException(EXPECTED));
        when(prov1.afterDeliver(apc, CommInfrastructure.NOOP, SINK_TOPIC1, MY_EVENT, true))
                        .thenThrow(new RuntimeException(EXPECTED));

        // start it
        apc.start();

        // now offer it
        assertTrue(apc.deliver(CommInfrastructure.NOOP, SINK_TOPIC1, MY_EVENT));

        verify(prov1).beforeDeliver(apc, CommInfrastructure.NOOP, SINK_TOPIC1, MY_EVENT);
        verify(prov2).beforeDeliver(apc, CommInfrastructure.NOOP, SINK_TOPIC1, MY_EVENT);

        verify(drools).deliver(sink1, MY_EVENT);
        verify(drools, never()).deliver(sink2, MY_EVENT);

        verify(prov1).afterDeliver(apc, CommInfrastructure.NOOP, SINK_TOPIC1, MY_EVENT, true);
        verify(prov2).afterDeliver(apc, CommInfrastructure.NOOP, SINK_TOPIC1, MY_EVENT, true);

        // offer to the other topic
        assertTrue(apc.deliver(CommInfrastructure.NOOP, SINK_TOPIC2, MY_EVENT));

        // now both topics should show one message delivered
        verify(drools).deliver(sink1, MY_EVENT);
        verify(drools).deliver(sink2, MY_EVENT);

        checkBeforeAfter(
            (prov, flag) -> when(prov.beforeDeliver(apc, CommInfrastructure.NOOP, SINK_TOPIC1, MY_EVENT))
                            .thenReturn(flag),
            (prov, flag) -> when(
                            prov.afterDeliver(apc, CommInfrastructure.NOOP, SINK_TOPIC1, MY_EVENT, true))
                                            .thenReturn(flag),
            () -> {
                apc.start();
                apc.deliver(CommInfrastructure.NOOP, SINK_TOPIC1, MY_EVENT);
            },
            prov -> verify(prov).beforeDeliver(apc, CommInfrastructure.NOOP, SINK_TOPIC1, MY_EVENT),
            () -> verify(drools).deliver(sink1, MY_EVENT),
            prov -> verify(prov).afterDeliver(apc, CommInfrastructure.NOOP, SINK_TOPIC1, MY_EVENT, true));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeliver_NullTopic() {
        apc.start();
        apc.deliver(CommInfrastructure.NOOP, null, MY_EVENT);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeliver_EmptyTopic() {
        apc.start();
        apc.deliver(CommInfrastructure.NOOP, "", MY_EVENT);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeliver_NullEvent() {
        apc.start();
        apc.deliver(CommInfrastructure.NOOP, SINK_TOPIC1, null);
    }

    @Test(expected = IllegalStateException.class)
    public void testDeliver_NotStarted() {
        // do NOT start
        apc.deliver(CommInfrastructure.NOOP, SINK_TOPIC1, MY_EVENT);
    }

    @Test(expected = IllegalStateException.class)
    public void testDeliver_Locked() {
        apc.start();
        apc.lock();
        apc.deliver(CommInfrastructure.NOOP, SINK_TOPIC1, MY_EVENT);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeliver_UnknownTopic() {
        apc.start();
        apc.deliver(CommInfrastructure.NOOP, "unknown-topic", MY_EVENT);
    }

    @Test
    public void testIsAlive() {
        assertFalse(apc.isAlive());

        apc.start();
        assertTrue(apc.isAlive());

        apc.stop();
        assertFalse(apc.isAlive());
    }

    @Test
    public void testLock() {
        // arrange for first provider to throw exceptions
        when(prov1.beforeLock(any())).thenThrow(new RuntimeException(EXPECTED));
        when(prov1.afterLock(any())).thenThrow(new RuntimeException(EXPECTED));

        // start it
        apc.start();

        // now lock it
        assertTrue(apc.lock());

        verify(prov1).beforeLock(apc);
        verify(prov2).beforeLock(apc);

        assertTrue(apc.isLocked());

        verify(drools).lock();

        verify(prov1).afterLock(apc);
        verify(prov2).afterLock(apc);

        checkBeforeAfter(
            (prov, flag) -> when(prov.beforeLock(apc)).thenReturn(flag),
            (prov, flag) -> when(prov.afterLock(apc)).thenReturn(flag),
            () -> {
                apc.start();
                apc.lock();
            },
            prov -> verify(prov).beforeLock(apc),
            () -> verify(drools).lock(),
            prov -> verify(prov).afterLock(apc));
    }

    @Test
    public void testLock_AlreadyLocked() {
        apc.start();
        apc.lock();

        // now re-lock it
        assertTrue(apc.lock());

        // these should be invoked a second time
        verify(prov1, times(2)).beforeLock(apc);
        verify(prov2, times(2)).beforeLock(apc);

        assertTrue(apc.isLocked());

        // these shouldn't be invoked a second time
        verify(drools).lock();
        verify(prov1).afterLock(apc);
    }

    @Test
    public void testUnlock() {
        // arrange for first provider to throw exceptions
        when(prov1.beforeUnlock(any())).thenThrow(new RuntimeException(EXPECTED));
        when(prov1.afterUnlock(any())).thenThrow(new RuntimeException(EXPECTED));

        // start it
        apc.start();
        apc.lock();

        // now unlock it
        assertTrue(apc.unlock());

        verify(prov1).beforeUnlock(apc);
        verify(prov2).beforeUnlock(apc);

        assertFalse(apc.isLocked());

        verify(drools).unlock();

        verify(prov1).afterUnlock(apc);
        verify(prov2).afterUnlock(apc);

        checkBeforeAfter(
            (prov, flag) -> when(prov.beforeUnlock(apc)).thenReturn(flag),
            (prov, flag) -> when(prov.afterUnlock(apc)).thenReturn(flag),
            () -> {
                apc.start();
                apc.lock();
                apc.unlock();
            },
            prov -> verify(prov).beforeUnlock(apc),
            () -> verify(drools).unlock(),
            prov -> verify(prov).afterUnlock(apc));
    }

    @Test
    public void testUnlock_NotLocked() {
        apc.start();

        // now unlock it
        assertTrue(apc.unlock());

        verify(prov1).beforeUnlock(apc);
        verify(prov2).beforeUnlock(apc);

        assertFalse(apc.isLocked());

        // these shouldn't be invoked
        verify(drools, never()).unlock();
        verify(prov1, never()).afterLock(apc);
    }

    @Test
    public void testIsLocked() {
        assertFalse(apc.isLocked());

        apc.lock();
        assertTrue(apc.isLocked());

        apc.unlock();
        assertFalse(apc.isLocked());
    }

    @Test
    public void testGetTopicSources() {
        assertEquals(sources, apc.getTopicSources());
    }

    @Test
    public void testGetTopicSinks() {
        assertEquals(sinks, apc.getTopicSinks());
    }

    @Test
    public void testGetDrools() {
        assertEquals(drools, apc.getDrools());
    }

    @Test
    public void testGetProperties() {
        assertEquals(properties, apc.getProperties());
    }

    @Test
    public void testToString() {
        assertTrue(apc.toString().startsWith("AggregatedPolicyController ["));
    }

    /**
     * Performs an operation that has a beforeXxx method and an afterXxx method. Tries
     * combinations where beforeXxx and afterXxx return {@code true} and {@code false}.
     *
     * @param setBefore function to set the return value of a provider's beforeXxx method
     * @param setAfter function to set the return value of a provider's afterXxx method
     * @param action invokes the operation
     * @param verifyBefore verifies that a provider's beforeXxx method was invoked
     * @param verifyMiddle verifies that the action occurring between the beforeXxx loop
     *        and the afterXxx loop was invoked
     * @param verifyAfter verifies that a provider's afterXxx method was invoked
     */
    private void checkBeforeAfter(BiConsumer<PolicyControllerFeatureApi, Boolean> setBefore,
                    BiConsumer<PolicyControllerFeatureApi, Boolean> setAfter, Runnable action,
                    Consumer<PolicyControllerFeatureApi> verifyBefore, Runnable verifyMiddle,
                    Consumer<PolicyControllerFeatureApi> verifyAfter) {

        checkBeforeAfter_FalseFalse(setBefore, setAfter, action, verifyBefore, verifyMiddle, verifyAfter);
        checkBeforeAfter_FalseTrue(setBefore, setAfter, action, verifyBefore, verifyMiddle, verifyAfter);
        checkBeforeAfter_TrueFalse(setBefore, setAfter, action, verifyBefore, verifyMiddle, verifyAfter);

        // don't need to test true-true, as it's behavior is a subset of true-false
    }

    /**
     * Performs an operation that has a beforeXxx method and an afterXxx method. Tries the
     * case where both the beforeXxx and afterXxx methods return {@code false}.
     *
     * @param setBefore function to set the return value of a provider's beforeXxx method
     * @param setAfter function to set the return value of a provider's afterXxx method
     * @param action invokes the operation
     * @param verifyBefore verifies that a provider's beforeXxx method was invoked
     * @param verifyMiddle verifies that the action occurring between the beforeXxx loop
     *        and the afterXxx loop was invoked
     * @param verifyAfter verifies that a provider's afterXxx method was invoked
     */
    private void checkBeforeAfter_FalseFalse(BiConsumer<PolicyControllerFeatureApi, Boolean> setBefore,
                    BiConsumer<PolicyControllerFeatureApi, Boolean> setAfter, Runnable action,
                    Consumer<PolicyControllerFeatureApi> verifyBefore, Runnable verifyMiddle,
                    Consumer<PolicyControllerFeatureApi> verifyAfter) {

        setUp();

        // configure for the test
        setBefore.accept(prov1, false);
        setBefore.accept(prov2, false);

        setAfter.accept(prov1, false);
        setAfter.accept(prov2, false);

        // run the action
        action.run();

        // verify that various methods were invoked
        verifyBefore.accept(prov1);
        verifyBefore.accept(prov2);

        verifyMiddle.run();

        verifyAfter.accept(prov1);
        verifyAfter.accept(prov2);
    }

    /**
     * Performs an operation that has a beforeXxx method and an afterXxx method. Tries the
     * case where the first provider's afterXxx returns {@code true}, while the others
     * return {@code false}.
     *
     * @param setBefore function to set the return value of a provider's beforeXxx method
     * @param setAfter function to set the return value of a provider's afterXxx method
     * @param action invokes the operation
     * @param verifyBefore verifies that a provider's beforeXxx method was invoked
     * @param verifyMiddle verifies that the action occurring between the beforeXxx loop
     *        and the afterXxx loop was invoked
     * @param verifyAfter verifies that a provider's afterXxx method was invoked
     */
    private void checkBeforeAfter_FalseTrue(BiConsumer<PolicyControllerFeatureApi, Boolean> setBefore,
                    BiConsumer<PolicyControllerFeatureApi, Boolean> setAfter, Runnable action,
                    Consumer<PolicyControllerFeatureApi> verifyBefore, Runnable verifyMiddle,
                    Consumer<PolicyControllerFeatureApi> verifyAfter) {

        setUp();

        // configure for the test
        setBefore.accept(prov1, false);
        setBefore.accept(prov2, false);

        setAfter.accept(prov1, true);
        setAfter.accept(prov2, false);

        // run the action
        action.run();

        // verify that various methods were invoked
        verifyBefore.accept(prov1);
        verifyBefore.accept(prov2);

        verifyMiddle.run();

        verifyAfter.accept(prov1);
        assertThatThrownBy(() -> verifyAfter.accept(prov2)).isInstanceOf(AssertionError.class);
    }

    /**
     * Performs an operation that has a beforeXxx method and an afterXxx method. Tries the
     * case where the first provider's beforeXxx returns {@code true}, while the others
     * return {@code false}.
     *
     * @param setBefore function to set the return value of a provider's beforeXxx method
     * @param setAfter function to set the return value of a provider's afterXxx method
     * @param action invokes the operation
     * @param verifyBefore verifies that a provider's beforeXxx method was invoked
     * @param verifyMiddle verifies that the action occurring between the beforeXxx loop
     *        and the afterXxx loop was invoked
     * @param verifyAfter verifies that a provider's afterXxx method was invoked
     */
    private void checkBeforeAfter_TrueFalse(BiConsumer<PolicyControllerFeatureApi, Boolean> setBefore,
                    BiConsumer<PolicyControllerFeatureApi, Boolean> setAfter, Runnable action,
                    Consumer<PolicyControllerFeatureApi> verifyBefore, Runnable verifyMiddle,
                    Consumer<PolicyControllerFeatureApi> verifyAfter) {

        setUp();

        // configure for the test
        setBefore.accept(prov1, true);
        setBefore.accept(prov2, false);

        setAfter.accept(prov1, false);
        setAfter.accept(prov2, false);

        // run the action
        action.run();

        // verify that various methods were invoked
        verifyBefore.accept(prov1);

        // remaining methods should not have been invoked
        assertThatThrownBy(() -> verifyBefore.accept(prov2)).isInstanceOf(AssertionError.class);

        assertThatThrownBy(verifyMiddle::run).isInstanceOf(AssertionError.class);

        assertThatThrownBy(() -> verifyAfter.accept(prov1)).isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> verifyAfter.accept(prov2)).isInstanceOf(AssertionError.class);
    }

    /**
     * Controller with overrides.
     */
    private class AggregatedPolicyControllerImpl extends AggregatedPolicyController {

        public AggregatedPolicyControllerImpl(String name, Properties properties) {
            super(name, properties);
        }

        @Override
        protected SystemPersistence getPersistenceManager() {
            return persist;
        }

        @Override
        protected TopicEndpoint getEndpointManager() {
            return endpointMgr;
        }

        @Override
        protected DroolsControllerFactory getDroolsFactory() {
            return droolsFactory;
        }

        @Override
        protected List<PolicyControllerFeatureApi> getProviders() {
            return providers;
        }
    }
}
