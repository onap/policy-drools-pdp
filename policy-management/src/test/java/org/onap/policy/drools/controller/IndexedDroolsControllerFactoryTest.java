/*-
 * ============LICENSE_START===============================================
 * ONAP
 * ========================================================================
 * Copyright (C) 2024 Nordix Foundation.
 * ========================================================================
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
 * ============LICENSE_END=================================================
 */

package org.onap.policy.drools.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.onap.policy.drools.protocol.coders.TopicCoderFilterConfiguration;
import org.springframework.test.util.ReflectionTestUtils;

class IndexedDroolsControllerFactoryTest {

    IndexedDroolsControllerFactory factory;
    static final String GROUP_ID = "groupId";
    static final String ARTIFACT_ID = "artifactId";
    static final String VERSION = "version";
    static final String OTHER_VERSION = "otherVersion";

    @BeforeEach
    void setUp() {
        this.factory = new IndexedDroolsControllerFactory();
    }

    @Test
    void build_EmptyArguments() {
        var props = new Properties();
        List<TopicCoderFilterConfiguration> decoderConfigs = List.of();
        List<TopicCoderFilterConfiguration> encoderConfigs = List.of();

        assertThatThrownBy(() -> factory.build(props, "", ARTIFACT_ID, VERSION, decoderConfigs, encoderConfigs))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Missing maven group-id coordinate");

        assertThatThrownBy(() -> factory.build(props, GROUP_ID, "", VERSION, decoderConfigs, encoderConfigs))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Missing maven artifact-id coordinate");

        assertThatThrownBy(() -> factory.build(props, GROUP_ID, ARTIFACT_ID, "", decoderConfigs, encoderConfigs))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Missing maven version coordinate");
    }

    @Test
    void testBuild_CheckControllerCopy() {
        var props = new Properties();
        List<TopicCoderFilterConfiguration> decoderConfigs = List.of();
        List<TopicCoderFilterConfiguration> encoderConfigs = List.of();

        var mockFactory = mock(IndexedDroolsControllerFactory.class);
        when(mockFactory.build(props, GROUP_ID, ARTIFACT_ID, VERSION, decoderConfigs, encoderConfigs))
            .thenCallRealMethod();

        var controller = mock(DroolsController.class);
        doNothing().when(controller).updateToVersion(GROUP_ID, ARTIFACT_ID, VERSION, decoderConfigs, encoderConfigs);
        when(controller.getVersion()).thenReturn(OTHER_VERSION);
        Map<String, DroolsController> controllers = new HashMap<>();
        controllers.put(GROUP_ID + ":" + ARTIFACT_ID, controller);
        ReflectionTestUtils.setField(mockFactory, "droolsControllers", controllers);

        assertNotNull(mockFactory.build(props, GROUP_ID, ARTIFACT_ID, VERSION, decoderConfigs, encoderConfigs));
    }

    @Test
    void unmanage() {
        assertThatThrownBy(() -> factory.unmanage(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No controller provided");

        var mockController = mock(DroolsController.class);
        when(mockController.isBrained()).thenReturn(false)
            .thenReturn(true).thenReturn(true);
        when(mockController.getGroupId()).thenReturn(GROUP_ID);
        when(mockController.getArtifactId()).thenReturn(ARTIFACT_ID);

        var mockFactory = mock(IndexedDroolsControllerFactory.class);
        doCallRealMethod().when(mockFactory).unmanage(mockController);
        when(mockFactory.toString()).thenCallRealMethod();

        Map<String, DroolsController> controllers = new HashMap<>();
        controllers.put(GROUP_ID + ":" + ARTIFACT_ID, mockController);
        ReflectionTestUtils.setField(mockFactory, "droolsControllers", controllers);

        // should return after isBrained returns false
        assertDoesNotThrow(() -> mockFactory.unmanage(mockController));
        assertFalse(mockFactory.droolsControllers.isEmpty());
        assertEquals("IndexedDroolsControllerFactory [#droolsControllers=1]", mockFactory.toString());

        // should go ahead and remove controller from hash map
        assertDoesNotThrow(() -> mockFactory.unmanage(mockController));
        assertTrue(mockFactory.droolsControllers.isEmpty());
        assertEquals("IndexedDroolsControllerFactory [#droolsControllers=0]", mockFactory.toString());

        controllers.put("anotherKey", mockController);
        ReflectionTestUtils.setField(mockFactory, "droolsControllers", controllers);

        // should return after comparing the key in the hash map (does not match)
        assertDoesNotThrow(() -> mockFactory.unmanage(mockController));
        assertFalse(mockFactory.droolsControllers.isEmpty());
        assertEquals("IndexedDroolsControllerFactory [#droolsControllers=1]", mockFactory.toString());
    }

    @Test
    void shutdown() {
        var mockController = mock(DroolsController.class);
        doNothing().when(mockController).shutdown();

        var mockFactory = mock(IndexedDroolsControllerFactory.class);
        doNothing().when(mockFactory).unmanage(mockController);

        assertDoesNotThrow(() -> mockFactory.shutdown(mockController));
    }

    @Test
    void get_EmptyParameters() {
        assertThatThrownBy(() -> factory.get("", ARTIFACT_ID, VERSION))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Missing maven coordinates");
        assertThatThrownBy(() -> factory.get(GROUP_ID, "", VERSION))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Missing maven coordinates");
    }
}