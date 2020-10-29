/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2020 Nordix Foundation
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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.common.endpoints.event.comm.Topic.CommInfrastructure;
import org.onap.policy.common.endpoints.event.comm.TopicSink;
import org.onap.policy.common.endpoints.event.comm.TopicSource;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.drools.system.PolicyEngine;

public class PoolingFeatureTest {

    private static final String CONTROLLER1 = "controllerA";
    private static final String CONTROLLER2 = "controllerB";
    private static final String CONTROLLER_DISABLED = "controllerDisabled";
    private static final String CONTROLLER_EX = "controllerException";
    private static final String CONTROLLER_UNKNOWN = "controllerUnknown";

    private static final String TOPIC1 = "topic.one";
    private static final String TOPIC2 = "topic.two";

    private static final String EVENT1 = "event.one";
    private static final String EVENT2 = "event.two";

    private static final Object OBJECT1 = new Object();
    private static final Object OBJECT2 = new Object();

    private Properties props;
    private PolicyEngine engine;
    private PolicyController controller1;
    private PolicyController controller2;
    private PolicyController controllerDisabled;
    private PolicyController controllerException;
    private PolicyController controllerUnknown;
    private DroolsController drools1;
    private DroolsController drools2;
    private DroolsController droolsDisabled;
    private List<Pair<PoolingManagerImpl, PoolingProperties>> managers;
    private PoolingManagerImpl mgr1;
    private PoolingManagerImpl mgr2;

    private PoolingFeature pool;

    /**
     * Setup.
     *
     * @throws Exception exception
     */
    @Before
    public void setUp() throws Exception {
        props = initProperties();
        engine = mock(PolicyEngine.class);
        controller1 = mock(PolicyController.class);
        controller2 = mock(PolicyController.class);
        controllerDisabled = mock(PolicyController.class);
        controllerException = mock(PolicyController.class);
        controllerUnknown = mock(PolicyController.class);
        drools1 = mock(DroolsController.class);
        drools2 = mock(DroolsController.class);
        droolsDisabled = mock(DroolsController.class);
        managers = new LinkedList<>();

        when(controller1.getName()).thenReturn(CONTROLLER1);
        when(controller2.getName()).thenReturn(CONTROLLER2);
        when(controllerDisabled.getName()).thenReturn(CONTROLLER_DISABLED);
        when(controllerException.getName()).thenReturn(CONTROLLER_EX);
        when(controllerUnknown.getName()).thenReturn(CONTROLLER_UNKNOWN);

        pool = new PoolingFeatureImpl();

        pool.beforeStart(engine);

        pool.afterCreate(controller1);
        pool.afterCreate(controller2);

        mgr1 = managers.get(0).getLeft();
        mgr2 = managers.get(1).getLeft();
    }

    @Test
    public void test() {
        assertEquals(2, managers.size());
    }

    @Test
    public void testGetHost() {
        String host = pool.getHost();
        assertNotNull(host);

        // create another and ensure it generates another host name
        pool = new PoolingFeatureImpl();
        String host2 = pool.getHost();
        assertNotNull(host2);

        assertNotEquals(host, host2);
    }

    @Test
    public void testGetSequenceNumber() {
        assertEquals(0, pool.getSequenceNumber());
    }

    @Test
    public void testBeforeStartEngine() {
        pool = new PoolingFeatureImpl();

        assertFalse(pool.beforeStart(engine));
    }

    @Test
    public void testAfterCreate() {
        managers.clear();
        pool = new PoolingFeatureImpl();
        pool.beforeStart(engine);

        assertFalse(pool.afterCreate(controller1));
        assertEquals(1, managers.size());

        // duplicate
        assertFalse(pool.afterCreate(controller1));
        assertEquals(1, managers.size());

        // second controller
        assertFalse(pool.afterCreate(controller2));
        assertEquals(2, managers.size());
    }

    @Test
    public void testAfterCreate_NotEnabled() {
        managers.clear();
        pool = new PoolingFeatureImpl();
        pool.beforeStart(engine);

        assertFalse(pool.afterCreate(controllerDisabled));
        assertTrue(managers.isEmpty());
    }

    @Test(expected = PoolingFeatureRtException.class)
    public void testAfterCreate_PropertyEx() {
        managers.clear();
        pool = new PoolingFeatureImpl();
        pool.beforeStart(engine);

        pool.afterCreate(controllerException);
    }

    @Test(expected = PoolingFeatureRtException.class)
    public void testAfterCreate_NoProps() {
        pool = new PoolingFeatureImpl();

        // did not perform globalInit, which is an error

        pool.afterCreate(controller1);
    }

    @Test
    public void testAfterCreate_NoFeatProps() {
        managers.clear();
        pool = new PoolingFeatureImpl();
        pool.beforeStart(engine);

        assertFalse(pool.afterCreate(controllerUnknown));
        assertTrue(managers.isEmpty());
    }

    @Test
    public void testBeforeStart() throws Exception {
        assertFalse(pool.beforeStart(controller1));
        verify(mgr1).beforeStart();

        // ensure it's still in the map by re-invoking
        assertFalse(pool.beforeStart(controller1));
        verify(mgr1, times(2)).beforeStart();

        assertFalse(pool.beforeStart(controllerDisabled));
    }

    @Test
    public void testAfterStart() {
        assertFalse(pool.afterStart(controller1));
        verify(mgr1).afterStart();

        // ensure it's still in the map by re-invoking
        assertFalse(pool.afterStart(controller1));
        verify(mgr1, times(2)).afterStart();

        assertFalse(pool.afterStart(controllerDisabled));
    }

    @Test
    public void testBeforeStop() {
        assertFalse(pool.beforeStop(controller1));
        verify(mgr1).beforeStop();

        // ensure it's still in the map by re-invoking
        assertFalse(pool.beforeStop(controller1));
        verify(mgr1, times(2)).beforeStop();

        assertFalse(pool.beforeStop(controllerDisabled));
    }

    @Test
    public void testAfterStop() {
        assertFalse(pool.afterStop(controller1));
        verify(mgr1).afterStop();

        assertFalse(pool.afterStop(controllerDisabled));

        // count should be unchanged
        verify(mgr1).afterStop();
    }

    @Test
    public void testAfterHalt() {
        assertFalse(pool.afterHalt(controller1));
        assertFalse(pool.afterHalt(controller1));

        verify(mgr1, never()).afterStop();

        assertFalse(pool.afterStop(controllerDisabled));
    }

    @Test
    public void testAfterShutdown() {
        assertFalse(pool.afterShutdown(controller1));
        assertFalse(pool.afterShutdown(controller1));

        verify(mgr1, never()).afterStop();

        assertFalse(pool.afterStop(controllerDisabled));
    }

    @Test
    public void testBeforeLock() {
        assertFalse(pool.beforeLock(controller1));
        verify(mgr1).beforeLock();

        // ensure it's still in the map by re-invoking
        assertFalse(pool.beforeLock(controller1));
        verify(mgr1, times(2)).beforeLock();

        assertFalse(pool.beforeLock(controllerDisabled));
    }

    @Test
    public void testAfterUnlock() {
        assertFalse(pool.afterUnlock(controller1));
        verify(mgr1).afterUnlock();

        // ensure it's still in the map by re-invoking
        assertFalse(pool.afterUnlock(controller1));
        verify(mgr1, times(2)).afterUnlock();

        assertFalse(pool.afterUnlock(controllerDisabled));
    }

    @Test
    public void testBeforeOffer() {
        assertFalse(pool.beforeOffer(controller1, CommInfrastructure.UEB, TOPIC1, EVENT1));
        verify(mgr1).beforeOffer(TOPIC1, EVENT1);

        // ensure that the args were captured
        pool.beforeInsert(drools1, OBJECT1);
        verify(mgr1).beforeInsert(TOPIC1, OBJECT1);


        // ensure it's still in the map by re-invoking
        assertFalse(pool.beforeOffer(controller1, CommInfrastructure.UEB, TOPIC2, EVENT2));
        verify(mgr1).beforeOffer(TOPIC2, EVENT2);

        // ensure that the new args were captured
        pool.beforeInsert(drools1, OBJECT2);
        verify(mgr1).beforeInsert(TOPIC2, OBJECT2);


        assertFalse(pool.beforeOffer(controllerDisabled, CommInfrastructure.UEB, TOPIC1, EVENT1));
    }

    @Test
    public void testBeforeOffer_NotFound() {
        assertFalse(pool.beforeOffer(controllerDisabled, CommInfrastructure.UEB, TOPIC1, EVENT1));
    }

    @Test
    public void testBeforeOffer_MgrTrue() {

        // manager will return true
        when(mgr1.beforeOffer(any(), any())).thenReturn(true);

        assertTrue(pool.beforeOffer(controller1, CommInfrastructure.UEB, TOPIC1, EVENT1));
        verify(mgr1).beforeOffer(TOPIC1, EVENT1);

        // ensure it's still in the map by re-invoking
        assertTrue(pool.beforeOffer(controller1, CommInfrastructure.UEB, TOPIC2, EVENT2));
        verify(mgr1).beforeOffer(TOPIC2, EVENT2);

        assertFalse(pool.beforeOffer(controllerDisabled, CommInfrastructure.UEB, TOPIC1, EVENT1));
    }

    @Test
    public void testBeforeInsert() {
        pool.beforeOffer(controller1, CommInfrastructure.UEB, TOPIC1, EVENT1);
        assertFalse(pool.beforeInsert(drools1, OBJECT1));
        verify(mgr1).beforeInsert(TOPIC1, OBJECT1);

        // ensure it's still in the map by re-invoking
        pool.beforeOffer(controller1, CommInfrastructure.UEB, TOPIC2, EVENT2);
        assertFalse(pool.beforeInsert(drools1, OBJECT2));
        verify(mgr1).beforeInsert(TOPIC2, OBJECT2);

        pool.beforeOffer(controllerDisabled, CommInfrastructure.UEB, TOPIC2, EVENT2);
        assertFalse(pool.beforeInsert(droolsDisabled, OBJECT1));
    }

    @Test
    public void testBeforeInsert_NoArgs() {

        // call beforeInsert without beforeOffer
        assertFalse(pool.beforeInsert(drools1, OBJECT1));
        verify(mgr1, never()).beforeInsert(any(), any());

        assertFalse(pool.beforeInsert(droolsDisabled, OBJECT1));
        verify(mgr1, never()).beforeInsert(any(), any());
    }

    @Test
    public void testBeforeInsert_ArgEx() {
        // generate exception
        pool = new PoolingFeatureImpl() {
            @Override
            protected PolicyController getController(DroolsController droolsController) {
                throw new IllegalArgumentException();
            }
        };

        pool.beforeOffer(controller1, CommInfrastructure.UEB, TOPIC1, EVENT1);
        assertFalse(pool.beforeInsert(drools1, OBJECT1));
        verify(mgr1, never()).beforeInsert(any(), any());
    }

    @Test
    public void testBeforeInsert_StateEx() {
        // generate exception
        pool = new PoolingFeatureImpl() {
            @Override
            protected PolicyController getController(DroolsController droolsController) {
                throw new IllegalStateException();
            }
        };

        pool.beforeOffer(controller1, CommInfrastructure.UEB, TOPIC1, EVENT1);
        assertFalse(pool.beforeInsert(drools1, OBJECT1));
        verify(mgr1, never()).beforeInsert(any(), any());
    }

    @Test
    public void testBeforeInsert_NullController() {

        // return null controller
        pool = new PoolingFeatureImpl() {
            @Override
            protected PolicyController getController(DroolsController droolsController) {
                return null;
            }
        };

        pool.beforeOffer(controller1, CommInfrastructure.UEB, TOPIC1, EVENT1);
        assertFalse(pool.beforeInsert(drools1, OBJECT1));
        verify(mgr1, never()).beforeInsert(any(), any());
    }

    @Test
    public void testBeforeInsert_NotFound() {

        pool.beforeOffer(controllerDisabled, CommInfrastructure.UEB, TOPIC2, EVENT2);
        assertFalse(pool.beforeInsert(droolsDisabled, OBJECT1));
    }

    @Test
    public void testAfterOffer() {
        // this will create OfferArgs
        pool.beforeOffer(controller1, CommInfrastructure.UEB, TOPIC1, EVENT1);

        // this should clear them
        assertFalse(pool.afterOffer(controller1, CommInfrastructure.UEB, TOPIC2, EVENT2, true));

        assertFalse(pool.beforeInsert(drools1, OBJECT1));
        verify(mgr1, never()).beforeInsert(any(), any());


        assertFalse(pool.beforeInsert(droolsDisabled, OBJECT1));
    }

    @Test
    public void testDoManager() throws Exception {
        assertFalse(pool.beforeStart(controller1));
        verify(mgr1).beforeStart();

        // ensure it's still in the map by re-invoking
        assertFalse(pool.beforeStart(controller1));
        verify(mgr1, times(2)).beforeStart();


        // different controller
        assertFalse(pool.beforeStart(controller2));
        verify(mgr2).beforeStart();

        // ensure it's still in the map by re-invoking
        assertFalse(pool.beforeStart(controller2));
        verify(mgr2, times(2)).beforeStart();


        assertFalse(pool.beforeStart(controllerDisabled));
    }

    @Test
    public void testDoManager_NotFound() {
        assertFalse(pool.beforeStart(controllerDisabled));
    }

    @Test(expected = RuntimeException.class)
    public void testDoManager_Ex() throws Exception {

        // generate exception
        doThrow(new RuntimeException()).when(mgr1).beforeStart();

        pool.beforeStart(controller1);
    }

    private Properties initProperties() {
        Properties props = new Properties();

        initProperties(props, "A", 0);
        initProperties(props, "B", 1);
        initProperties(props, "Exception", 2);

        props.setProperty("pooling.controllerDisabled.enabled", "false");

        props.setProperty("pooling.controllerException.offline.queue.limit", "INVALID NUMBER");

        return props;
    }

    private void initProperties(Properties props, String suffix, int offset) {
        props.setProperty("pooling.controller" + suffix + ".topic", "topic." + suffix);
        props.setProperty("pooling.controller" + suffix + ".enabled", "true");
        props.setProperty("pooling.controller" + suffix + ".offline.queue.limit", String.valueOf(5 + offset));
        props.setProperty("pooling.controller" + suffix + ".offline.queue.age.milliseconds",
                        String.valueOf(100 + offset));
        props.setProperty("pooling.controller" + suffix + ".start.heartbeat.milliseconds", String.valueOf(10 + offset));
        props.setProperty("pooling.controller" + suffix + ".reactivate.milliseconds", String.valueOf(20 + offset));
        props.setProperty("pooling.controller" + suffix + ".identification.milliseconds", String.valueOf(30 + offset));
        props.setProperty("pooling.controller" + suffix + ".active.heartbeat.milliseconds",
                        String.valueOf(40 + offset));
        props.setProperty("pooling.controller" + suffix + ".inter.heartbeat.milliseconds", String.valueOf(50 + offset));
    }

    /**
     * Feature with overrides.
     */
    private class PoolingFeatureImpl extends PoolingFeature {

        @Override
        protected Properties getProperties(String featName) {
            if (PoolingProperties.FEATURE_NAME.equals(featName)) {
                return props;
            } else {
                throw new IllegalArgumentException("unknown feature name");
            }
        }

        @Override
        protected PoolingManagerImpl makeManager(String host, PolicyController controller, PoolingProperties props,
                        CountDownLatch activeLatch) {

            PoolingManagerImpl mgr = mock(PoolingManagerImpl.class);

            managers.add(Pair.of(mgr, props));

            return mgr;
        }

        @Override
        protected PolicyController getController(DroolsController droolsController) {
            if (droolsController == drools1) {
                return controller1;
            } else if (droolsController == drools2) {
                return controller2;
            } else if (droolsController == droolsDisabled) {
                return controllerDisabled;
            } else {
                throw new IllegalArgumentException("unknown drools controller");
            }
        }

        @Override
        protected List<TopicSource> initTopicSources(Properties props) {
            return Collections.emptyList();
        }

        @Override
        protected List<TopicSink> initTopicSinks(Properties props) {
            return Collections.emptyList();
        }
    }
}
