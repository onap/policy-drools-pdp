/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2024 Nordix Foundation.
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

package org.onap.policy.drools.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.onap.policy.drools.lifecycle.LifecycleFsm.CONFIGURATION_PROPERTIES_NAME;

import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.onap.policy.drools.persistence.FileSystemPersistence;
import org.onap.policy.drools.persistence.SystemPersistenceConstants;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.drools.system.PolicyEngine;

@ExtendWith(MockitoExtension.class)
class LifecycleFeatureTest {

    LifecycleFeature feature;

    @Mock
    LifecycleFsm fsm;

    @Mock
    PolicyController controller;

    @Mock
    PolicyEngine engine;

    AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);

        var props = new Properties();
        var fileManager = mock(FileSystemPersistence.class);
        lenient().when(fileManager.getProperties(CONFIGURATION_PROPERTIES_NAME)).thenReturn(props);

        try (MockedStatic<SystemPersistenceConstants> constants =
                 Mockito.mockStatic(SystemPersistenceConstants.class)) {
            constants.when(SystemPersistenceConstants::getManager).thenReturn(fileManager);
            feature = mock(LifecycleFeature.class);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    void getSequenceNumber() {
        when(feature.getSequenceNumber()).thenCallRealMethod();

        assertEquals(1, feature.getSequenceNumber());
    }

    @Test
    void afterStart() {
        when(fsm.start()).thenReturn(true);
        doNothing().when(fsm).start(controller);

        when(feature.afterStart(engine)).thenCallRealMethod();
        when(feature.afterStart(controller)).thenCallRealMethod();

        try (MockedStatic<LifecycleFeature> factory = Mockito.mockStatic(LifecycleFeature.class)) {
            factory.when(LifecycleFeature::getFsm).thenReturn(fsm);
            assertEquals(fsm, LifecycleFeature.getFsm());

            assertFalse(feature.afterStart(controller));
            assertFalse(feature.afterStart(engine));
        }
    }

    @Test
    void beforeStop() {
        when(fsm.stop()).thenReturn(true);
        doNothing().when(fsm).stop(controller);

        when(feature.beforeStop(engine)).thenCallRealMethod();
        when(feature.beforeStop(controller)).thenCallRealMethod();

        try (MockedStatic<LifecycleFeature> factory = Mockito.mockStatic(LifecycleFeature.class)) {
            factory.when(LifecycleFeature::getFsm).thenReturn(fsm);
            assertEquals(fsm, LifecycleFeature.getFsm());

            assertFalse(feature.beforeStop(controller));
            assertFalse(feature.beforeStop(engine));
        }
    }

    @Test
    void beforeShutdown() {
        doNothing().when(fsm).shutdown();

        when(feature.beforeShutdown(engine)).thenCallRealMethod();

        try (MockedStatic<LifecycleFeature> factory = Mockito.mockStatic(LifecycleFeature.class)) {
            factory.when(LifecycleFeature::getFsm).thenReturn(fsm);
            assertEquals(fsm, LifecycleFeature.getFsm());

            assertFalse(feature.beforeShutdown(engine));
        }
    }

    @Test
    void beforeLock() {
        doNothing().when(fsm).stop(controller);

        when(feature.beforeLock(controller)).thenCallRealMethod();

        try (MockedStatic<LifecycleFeature> factory = Mockito.mockStatic(LifecycleFeature.class)) {
            factory.when(LifecycleFeature::getFsm).thenReturn(fsm);
            assertEquals(fsm, LifecycleFeature.getFsm());

            assertFalse(feature.beforeLock(controller));
        }
    }

    @Test
    void afterUnlock() {
        doNothing().when(fsm).start(controller);

        when(feature.afterUnlock(controller)).thenCallRealMethod();

        try (MockedStatic<LifecycleFeature> factory = Mockito.mockStatic(LifecycleFeature.class)) {
            factory.when(LifecycleFeature::getFsm).thenReturn(fsm);
            assertEquals(fsm, LifecycleFeature.getFsm());

            assertFalse(feature.afterUnlock(controller));
        }
    }
}