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

package org.onap.policy.drools.testtransaction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.system.PolicyController;

public class TestTransactionFeatureTest {

    private AtomicInteger regCount;
    private AtomicInteger unregCount;
    private TestTransaction mgr;
    private DroolsController drools;
    private PolicyController ctlr;
    private TestTransactionFeature feat;

    /**
     * Initialize objects for each test.
     */
    @Before
    public void setUp() {
        regCount = new AtomicInteger(0);
        unregCount = new AtomicInteger(0);
        mgr = mock(TestTransaction.class);
        drools = mock(DroolsController.class);
        ctlr = mock(PolicyController.class);

        feat = new TestTransactionFeature() {
            @Override
            protected TestTransaction getTestTransMgr() {
                return mgr;
            }
        };

        when(ctlr.getDrools()).thenReturn(drools);

        doAnswer(args -> {
            regCount.incrementAndGet();
            return null;
        }).when(mgr).register(ctlr);

        doAnswer(args -> {
            unregCount.incrementAndGet();
            return null;
        }).when(mgr).unregister(ctlr);
    }

    @Test
    public void testAfterStart() {
        // try each combination of alive, locked, and brained
        checkCombos(regCount, ctlr -> feat.afterStart(ctlr));
    }

    @Test
    public void testAfterLock() {
        checkSimple(unregCount, ctlr -> feat.afterLock(ctlr));
    }

    @Test
    public void testAfterUnlock() {
        // try each combination of alive, locked, and brained
        checkCombos(regCount, ctlr -> feat.afterUnlock(ctlr));
    }

    @Test
    public void testBeforeStop() {
        checkSimple(unregCount, ctlr -> feat.beforeStop(ctlr));
    }

    @Test
    public void testGetSequenceNumber() {
        assertEquals(1000, feat.getSequenceNumber());
    }

    @Test
    public void testGetTestTransMgr() {
        assertNotNull(new TestTransactionFeature().getTestTransMgr());
    }

    /**
     * Try each combination of alive, locked, and brained.
     *
     * @param counter counter to check after each invocation
     * @param method method to invoke
     */
    private void checkCombos(AtomicInteger counter, Function<PolicyController, Boolean> method) {
        when(ctlr.isAlive()).thenReturn(true);
        when(ctlr.isLocked()).thenReturn(true);
        when(drools.isBrained()).thenReturn(true);
        assertFalse(method.apply(ctlr));
        assertEquals(0, counter.getAndSet(0));

        when(ctlr.isAlive()).thenReturn(true);
        when(ctlr.isLocked()).thenReturn(true);
        when(drools.isBrained()).thenReturn(false);
        assertFalse(method.apply(ctlr));
        assertEquals(0, counter.getAndSet(0));

        // this is the only one that should cause it to register
        when(ctlr.isAlive()).thenReturn(true);
        when(ctlr.isLocked()).thenReturn(false);
        when(drools.isBrained()).thenReturn(true);
        assertFalse(method.apply(ctlr));
        assertEquals(1, counter.getAndSet(0));

        when(ctlr.isAlive()).thenReturn(true);
        when(ctlr.isLocked()).thenReturn(false);
        when(drools.isBrained()).thenReturn(false);
        assertFalse(method.apply(ctlr));
        assertEquals(0, counter.getAndSet(0));

        when(ctlr.isAlive()).thenReturn(false);
        when(ctlr.isLocked()).thenReturn(true);
        when(drools.isBrained()).thenReturn(true);
        assertFalse(method.apply(ctlr));
        assertEquals(0, counter.getAndSet(0));

        when(ctlr.isAlive()).thenReturn(false);
        when(ctlr.isLocked()).thenReturn(true);
        when(drools.isBrained()).thenReturn(false);
        assertFalse(method.apply(ctlr));
        assertEquals(0, counter.getAndSet(0));

        when(ctlr.isAlive()).thenReturn(false);
        when(ctlr.isLocked()).thenReturn(false);
        when(drools.isBrained()).thenReturn(true);
        assertFalse(method.apply(ctlr));
        assertEquals(0, counter.getAndSet(0));

        when(ctlr.isAlive()).thenReturn(false);
        when(ctlr.isLocked()).thenReturn(false);
        when(drools.isBrained()).thenReturn(false);
        assertFalse(method.apply(ctlr));
        assertEquals(0, counter.getAndSet(0));
    }

    /**
     * Check the simple case that doesn't depend on the controller state.
     *
     * @param counter counter to check after each invocation
     * @param method method to invoke
     */
    private void checkSimple(AtomicInteger counter, Function<PolicyController, Boolean> method) {
        when(ctlr.isAlive()).thenReturn(true);
        assertFalse(method.apply(ctlr));
        assertEquals(1, counter.getAndSet(0));

        when(ctlr.isAlive()).thenReturn(false);
        assertFalse(method.apply(ctlr));
        assertEquals(1, counter.getAndSet(0));
    }
}
