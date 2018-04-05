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
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.LinkedList;
import java.util.List;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.event.comm.Topic.CommInfrastructure;
import org.onap.policy.drools.pooling.PoolingFeature.Factory;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.drools.utils.Pair;

public class PoolingFeatureTest {

    private static final String CONFIG_DIR = "src/test/java/org/onap/policy/drools/pooling";

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

    /**
     * Saved from PoolingFeature and restored on exit from this test class.
     */
    private static Factory saveFactory;

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
    private Factory factory;

    private PoolingFeature pool;


    @BeforeClass
    public static void setUpBeforeClass() {
        saveFactory = PoolingFeature.getFactory();
    }

    @AfterClass
    public static void tearDownAfterClass() {
        PoolingFeature.setFactory(saveFactory);
    }

    @Before
    public void setUp() throws Exception {
        factory = mock(Factory.class);
        controller1 = mock(PolicyController.class);
        controller2 = mock(PolicyController.class);
        controllerDisabled = mock(PolicyController.class);
        controllerException = mock(PolicyController.class);
        controllerUnknown = mock(PolicyController.class);
        drools1 = mock(DroolsController.class);
        drools2 = mock(DroolsController.class);
        droolsDisabled = mock(DroolsController.class);
        managers = new LinkedList<>();

        PoolingFeature.setFactory(factory);

        when(controller1.getName()).thenReturn(CONTROLLER1);
        when(controller2.getName()).thenReturn(CONTROLLER2);
        when(controllerDisabled.getName()).thenReturn(CONTROLLER_DISABLED);
        when(controllerException.getName()).thenReturn(CONTROLLER_EX);
        when(controllerUnknown.getName()).thenReturn(CONTROLLER_UNKNOWN);

        when(factory.getController(drools1)).thenReturn(controller1);
        when(factory.getController(drools2)).thenReturn(controller2);
        when(factory.getController(droolsDisabled)).thenReturn(controllerDisabled);

        when(factory.makeManager(any(), any())).thenAnswer(args -> {
            PoolingProperties props = args.getArgument(1);

            PoolingManagerImpl mgr = mock(PoolingManagerImpl.class);

            managers.add(new Pair<>(mgr, props));

            return mgr;
        });

        pool = new PoolingFeature();

        pool.globalInit(null, CONFIG_DIR);

        pool.afterCreate(controller1);
        pool.afterCreate(controller2);

        mgr1 = managers.get(0).first();
        mgr2 = managers.get(1).first();
    }

    @Test
    public void test() {
        assertEquals(2, managers.size());
    }

    @Test
    public void testGetSequenceNumber() {
        assertEquals(0, pool.getSequenceNumber());
    }

    @Test
    public void testGlobalInit() {
        pool = new PoolingFeature();

        pool.globalInit(null, CONFIG_DIR);
    }

    @Test(expected = PoolingFeatureRtException.class)
    public void testGlobalInit_NotFound() {
        pool = new PoolingFeature();

        pool.globalInit(null, CONFIG_DIR + "/unknown");
    }

    @Test
    public void testAfterCreate() {
        managers.clear();
        pool = new PoolingFeature();
        pool.globalInit(null, CONFIG_DIR);

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
        pool = new PoolingFeature();
        pool.globalInit(null, CONFIG_DIR);

        assertFalse(pool.afterCreate(controllerDisabled));
        assertTrue(managers.isEmpty());
    }

    @Test(expected = PoolingFeatureRtException.class)
    public void testAfterCreate_PropertyEx() {
        managers.clear();
        pool = new PoolingFeature();
        pool.globalInit(null, CONFIG_DIR);

        pool.afterCreate(controllerException);
    }

    @Test(expected = PoolingFeatureRtException.class)
    public void testAfterCreate_NoProps() {
        pool = new PoolingFeature();
        
        // did not perform globalInit, which is an error
        
        pool.afterCreate(controller1);
    }

    @Test
    public void testAfterCreate_NoFeatProps() {
        managers.clear();
        pool = new PoolingFeature();
        pool.globalInit(null, CONFIG_DIR);

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

        // ensure it has been removed from the map by re-invoking
        assertFalse(pool.afterStop(controller1));

        // count should be unchanged
        verify(mgr1).afterStop();

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
        verify(mgr1).beforeOffer(CommInfrastructure.UEB, TOPIC1, EVENT1);

        // ensure that the args were captured
        pool.beforeInsert(drools1, OBJECT1);
        verify(mgr1).beforeInsert(CommInfrastructure.UEB, TOPIC1, EVENT1, OBJECT1);


        // ensure it's still in the map by re-invoking
        assertFalse(pool.beforeOffer(controller1, CommInfrastructure.UEB, TOPIC2, EVENT2));
        verify(mgr1).beforeOffer(CommInfrastructure.UEB, TOPIC2, EVENT2);

        // ensure that the new args were captured
        pool.beforeInsert(drools1, OBJECT2);
        verify(mgr1).beforeInsert(CommInfrastructure.UEB, TOPIC2, EVENT2, OBJECT2);


        assertFalse(pool.beforeOffer(controllerDisabled, CommInfrastructure.UEB, TOPIC1, EVENT1));
    }

    @Test
    public void testBeforeOffer_NotFound() {
        assertFalse(pool.beforeOffer(controllerDisabled, CommInfrastructure.UEB, TOPIC1, EVENT1));
    }

    @Test
    public void testBeforeOffer_MgrTrue() {

        // manager will return true
        when(mgr1.beforeOffer(any(), any(), any())).thenReturn(true);

        assertTrue(pool.beforeOffer(controller1, CommInfrastructure.UEB, TOPIC1, EVENT1));
        verify(mgr1).beforeOffer(CommInfrastructure.UEB, TOPIC1, EVENT1);

        // ensure it's still in the map by re-invoking
        assertTrue(pool.beforeOffer(controller1, CommInfrastructure.UEB, TOPIC2, EVENT2));
        verify(mgr1).beforeOffer(CommInfrastructure.UEB, TOPIC2, EVENT2);

        assertFalse(pool.beforeOffer(controllerDisabled, CommInfrastructure.UEB, TOPIC1, EVENT1));
    }

    @Test
    public void testBeforeInsert() {
        pool.beforeOffer(controller1, CommInfrastructure.UEB, TOPIC1, EVENT1);
        assertFalse(pool.beforeInsert(drools1, OBJECT1));
        verify(mgr1).beforeInsert(CommInfrastructure.UEB, TOPIC1, EVENT1, OBJECT1);

        // ensure it's still in the map by re-invoking
        pool.beforeOffer(controller1, CommInfrastructure.UEB, TOPIC2, EVENT2);
        assertFalse(pool.beforeInsert(drools1, OBJECT2));
        verify(mgr1).beforeInsert(CommInfrastructure.UEB, TOPIC2, EVENT2, OBJECT2);

        pool.beforeOffer(controllerDisabled, CommInfrastructure.UEB, TOPIC2, EVENT2);
        assertFalse(pool.beforeInsert(droolsDisabled, OBJECT1));
    }

    @Test
    public void testBeforeInsert_NoArgs() {

        // call beforeInsert without beforeOffer
        assertFalse(pool.beforeInsert(drools1, OBJECT1));
        verify(mgr1, never()).beforeInsert(any(), any(), any(), any());

        assertFalse(pool.beforeInsert(droolsDisabled, OBJECT1));
        verify(mgr1, never()).beforeInsert(any(), any(), any(), any());
    }

    @Test
    public void testBeforeInsert_ArgEx() {

        // generate exception
        doThrow(new IllegalArgumentException()).when(factory).getController(any());

        pool.beforeOffer(controller1, CommInfrastructure.UEB, TOPIC1, EVENT1);
        assertFalse(pool.beforeInsert(drools1, OBJECT1));
        verify(mgr1, never()).beforeInsert(any(), any(), any(), any());
    }

    @Test
    public void testBeforeInsert_StateEx() {

        // generate exception
        doThrow(new IllegalStateException()).when(factory).getController(any());

        pool.beforeOffer(controller1, CommInfrastructure.UEB, TOPIC1, EVENT1);
        assertFalse(pool.beforeInsert(drools1, OBJECT1));
        verify(mgr1, never()).beforeInsert(any(), any(), any(), any());
    }

    @Test
    public void testBeforeInsert_NullController() {

        // return null controller
        when(factory.getController(any())).thenReturn(null);

        pool.beforeOffer(controller1, CommInfrastructure.UEB, TOPIC1, EVENT1);
        assertFalse(pool.beforeInsert(drools1, OBJECT1));
        verify(mgr1, never()).beforeInsert(any(), any(), any(), any());
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
        verify(mgr1, never()).beforeInsert(any(), any(), any(), any());


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

    @Test(expected = PoolingFeatureRtException.class)
    public void testDoManager_Ex() throws Exception {

        // generate exception
        doThrow(new PoolingFeatureException()).when(mgr1).beforeStart();

        pool.beforeStart(controller1);
    }

    @Test
    public void testDoDeleteManager() {
        assertFalse(pool.afterStop(controller1));
        verify(mgr1).afterStop();

        // ensure it has been removed from the map by re-invoking
        assertFalse(pool.afterStop(controller1));

        // count should be unchanged
        verify(mgr1).afterStop();


        // different controller
        assertFalse(pool.afterStop(controller2));
        verify(mgr2).afterStop();

        // ensure it has been removed from the map by re-invoking
        assertFalse(pool.afterStop(controller2));

        // count should be unchanged
        verify(mgr2).afterStop();


        assertFalse(pool.afterStop(controllerDisabled));
    }

    @Test
    public void testDoDeleteManager_NotFound() {
        assertFalse(pool.afterStop(controllerDisabled));
    }

    @Test(expected = PoolingFeatureRtException.class)
    public void testDoDeleteManager_Ex() {

        // generate exception
        doThrow(new PoolingFeatureRtException()).when(mgr1).afterStop();

        pool.afterStop(controller1);
    }

}
