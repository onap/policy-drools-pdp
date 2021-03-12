/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2019 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.activestandby;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class FactoryTest {
    private static Factory saveFactory;

    private Factory factory;

    @BeforeClass
    public static void setUpBeforeClass() {
        saveFactory = Factory.getInstance();
        assertNotNull(saveFactory);
    }

    @AfterClass
    public static void tearDownAfterClass() {
        Factory.setInstance(saveFactory);
    }

    @Before
    public void setUp() {
        factory = new Factory();
    }

    @Test
    public void testMakeTimer() {
        assertNotNull(factory.makeTimer());
    }

    @Test
    public void testGetInstance_testSetInstance() {
        Factory.setInstance(factory);
        assertSame(factory, Factory.getInstance());

        // repeat - should be the same
        assertSame(factory, Factory.getInstance());
    }
}