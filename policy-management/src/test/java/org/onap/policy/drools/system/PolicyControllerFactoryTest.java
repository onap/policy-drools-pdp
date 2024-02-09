/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018-2020 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2024 Nordix Foundation.
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

package org.onap.policy.drools.system;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.onap.policy.drools.properties.DroolsPropertyConstants.PROPERTY_CONTROLLER_TYPE;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.onap.policy.common.utils.gson.GsonTestUtils;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.controller.internal.NullDroolsController;
import org.onap.policy.drools.features.DroolsControllerFeatureApi;
import org.onap.policy.drools.features.PolicyControllerFeatureApi;
import org.onap.policy.drools.protocol.coders.TopicCoderFilterConfiguration;
import org.onap.policy.drools.protocol.configuration.DroolsConfiguration;
import org.onap.policy.drools.system.internal.AggregatedPolicyController;

class PolicyControllerFactoryTest {
    private static final String POLICY_CONTROLLER_BUILDER_TAG = "PolicyControllerFactoryTest";

    private static final String MY_NAME = "my-name-a";
    private static final String MY_NAME2 = "my-name-b";

    private static final String ARTIFACT1 = "artifact-a";
    private static final String GROUP1 = "group-a";
    private static final String VERSION1 = "version-a";

    private static final String ARTIFACT2 = "artifact-b";
    private static final String GROUP2 = "group-b";
    private static final String VERSION2 = "version-b";

    private static final String FEATURE1 = "feature-a";
    private static final String FEATURE2 = "feature-b";

    private PolicyController controller;
    private PolicyController controller2;
    private Properties properties;
    private DroolsController drools;
    private DroolsController drools2;
    private DroolsConfiguration config;
    private PolicyControllerFeatureApi feature1;
    private PolicyControllerFeatureApi feature2;
    private List<PolicyControllerFeatureApi> providers;
    private IndexedPolicyControllerFactory ipc;

    /**
     * Initializes the object to be tested.
     */
    @BeforeEach
    public void setUp() {
        controller = mock(PolicyController.class);
        controller2 = mock(PolicyController.class);
        properties = new Properties();
        drools = mock(DroolsController.class);
        drools2 = mock(DroolsController.class);
        config = mock(DroolsConfiguration.class);
        feature1 = mock(PolicyControllerFeatureApi.class);
        feature2 = mock(PolicyControllerFeatureApi.class);
        providers = Arrays.asList(feature1, feature2);

        when(feature1.getName()).thenReturn(FEATURE1);
        when(feature2.getName()).thenReturn(FEATURE2);

        when(drools.getArtifactId()).thenReturn(ARTIFACT1);
        when(drools.getGroupId()).thenReturn(GROUP1);
        when(drools.getVersion()).thenReturn(VERSION1);

        when(drools2.getArtifactId()).thenReturn(ARTIFACT2);
        when(drools2.getGroupId()).thenReturn(GROUP2);
        when(drools2.getVersion()).thenReturn(VERSION2);

        when(controller.getName()).thenReturn(MY_NAME);
        when(controller.getDrools()).thenReturn(drools);
        when(controller.updateDrools(any())).thenReturn(true);

        when(controller2.getName()).thenReturn(MY_NAME2);
        when(controller2.getDrools()).thenReturn(drools2);
        when(controller2.updateDrools(any())).thenReturn(true);

        ipc = new IndexedPolicyControllerFactoryImpl();
    }

    @Test
    void testFactory() {
        // use a REAL object instead of an Impl
        ipc = new IndexedPolicyControllerFactory();
        assertNotNull(ipc.getProviders());
    }

    @Test
    void testBuild() {
        assertEquals(controller, ipc.build(MY_NAME, properties));

        // re-build - should not create another one
        assertEquals(controller, ipc.build(MY_NAME, properties));

        // brained
        setUp();
        when(drools.isBrained()).thenReturn(true);
        ipc.build(MY_NAME, properties);
    }

    @Test
    void testSerialize() {
        assertEquals(controller, ipc.build(MY_NAME, properties));

        new GsonTestUtils().compareGson(ipc, PolicyControllerFactoryTest.class);
    }

    @Test
    void testPatchStringDroolsConfiguration() {
        // unknown controller
        assertThatIllegalArgumentException().isThrownBy(() -> ipc.patch(MY_NAME, config));

        /*
         * Build controller to be used by remaining tests.
         */
        ipc.build(MY_NAME, properties);

        // null name
        String nullName = null;
        assertThatIllegalArgumentException().isThrownBy(() -> ipc.patch(nullName, config));

        // empty name
        assertThatIllegalArgumentException().isThrownBy(() -> ipc.patch("", config));

        // success
        ipc.patch(MY_NAME, config);
        verify(controller).updateDrools(config);

        // create a factory whose get() method returns null
        ipc = new IndexedPolicyControllerFactory() {
            @Override
            public PolicyController get(String name) {
                return null;
            }
        };
        ipc.build(MY_NAME, properties);
        assertThatIllegalArgumentException().isThrownBy(() -> ipc.patch(MY_NAME, config));
    }

    @Test
    void testPatchPolicyControllerDroolsConfiguration() {
        ipc.patch(controller, config);
        verify(controller).updateDrools(config);

        // null controller
        PolicyController nullCtlr = null;
        assertThatIllegalArgumentException().isThrownBy(() -> ipc.patch(nullCtlr, config));

        // null config
        assertThatIllegalArgumentException().isThrownBy(() -> ipc.patch(controller, null));

        // brained
        when(drools.isBrained()).thenReturn(true);
        ipc.patch(controller, config);

        // update failed
        when(controller.updateDrools(config)).thenReturn(false);
        assertThatIllegalArgumentException().isThrownBy(() -> ipc.patch(controller, config));
    }

    @Test
    void testShutdownString() {
        // null name
        String nullName = null;
        assertThatIllegalArgumentException().isThrownBy(() -> ipc.shutdown(nullName));

        // empty name
        assertThatIllegalArgumentException().isThrownBy(() -> ipc.shutdown(""));

        // unknown controller
        ipc.shutdown(MY_NAME);
        verify(controller, never()).shutdown();

        // valid controller
        ipc.build(MY_NAME, properties);
        ipc.shutdown(MY_NAME);
        verify(controller).shutdown();
    }

    @Test
    void testShutdownPolicyController() {
        ipc.build(MY_NAME, properties);

        ipc.shutdown(controller);

        verify(controller).shutdown();

        // should no longer be managed
        assertThatIllegalArgumentException().isThrownBy(() -> ipc.get(MY_NAME));
    }

    @Test
    void testShutdown() {
        ipc.build(MY_NAME, properties);
        ipc.build(MY_NAME2, properties);

        ipc.shutdown();

        verify(controller).shutdown();
        verify(controller2).shutdown();

        // should no longer be managed
        assertThatIllegalArgumentException().isThrownBy(() -> ipc.get(MY_NAME));
        assertThatIllegalArgumentException().isThrownBy(() -> ipc.get(MY_NAME2));
    }

    @Test
    void testUnmanage() {
        ipc.build(MY_NAME, properties);
        ipc.build(MY_NAME2, properties);

        ipc.shutdown(MY_NAME);

        verify(controller).shutdown();
        verify(controller2, never()).shutdown();

        // should no longer be managed
        assertThatIllegalArgumentException().isThrownBy(() -> ipc.get(MY_NAME));

        // should still be managed
        assertEquals(controller2, ipc.get(MY_NAME2));

        // null controller
        PolicyController nullCtlr = null;
        assertThatIllegalArgumentException().isThrownBy(() -> ipc.shutdown(nullCtlr));

        // unknown controller
        ipc.shutdown(controller);
        verify(controller, times(2)).shutdown();
    }

    @Test
    void testDestroyString() {
        // null name
        String nullName = null;
        assertThatIllegalArgumentException().isThrownBy(() -> ipc.destroy(nullName));

        // empty name
        assertThatIllegalArgumentException().isThrownBy(() -> ipc.destroy(""));

        // unknown controller
        ipc.destroy(MY_NAME);
        verify(controller, never()).halt();

        // valid controller
        ipc.build(MY_NAME, properties);
        ipc.destroy(MY_NAME);
        verify(controller).halt();
    }

    @Test
    void testDestroyPolicyController() {
        ipc.build(MY_NAME, properties);

        ipc.destroy(controller);

        verify(controller).halt();

        // should no longer be managed
        assertThatIllegalArgumentException().isThrownBy(() -> ipc.get(MY_NAME));
    }

    @Test
    void testDestroy() {
        ipc.build(MY_NAME, properties);
        ipc.build(MY_NAME2, properties);

        ipc.destroy();

        verify(controller).halt();
        verify(controller2).halt();

        // should no longer be managed
        assertThatIllegalArgumentException().isThrownBy(() -> ipc.get(MY_NAME));
        assertThatIllegalArgumentException().isThrownBy(() -> ipc.get(MY_NAME2));
    }

    @Test
    void testGetString() {
        // unknown name
        assertThatIllegalArgumentException().isThrownBy(() -> ipc.get(MY_NAME));

        ipc.build(MY_NAME, properties);
        ipc.build(MY_NAME2, properties);

        assertEquals(controller, ipc.get(MY_NAME));
        assertEquals(controller2, ipc.get(MY_NAME2));

        // null name
        String nullName = null;
        assertThatIllegalArgumentException().isThrownBy(() -> ipc.get(nullName));

        // empty name
        assertThatIllegalArgumentException().isThrownBy(() -> ipc.get(""));
    }

    @Test
    void testGetStringString_testToKey() {
        // unknown controller
        assertThatIllegalArgumentException().isThrownBy(() -> ipc.get(GROUP1, ARTIFACT1));

        when(drools.isBrained()).thenReturn(true);
        when(drools2.isBrained()).thenReturn(true);

        ipc.build(MY_NAME, properties);
        ipc.build(MY_NAME2, properties);

        assertEquals(controller, ipc.get(GROUP1, ARTIFACT1));
        assertEquals(controller2, ipc.get(GROUP2, ARTIFACT2));

        // null group
        assertThatIllegalArgumentException().isThrownBy(() -> ipc.get(null, ARTIFACT1));

        // empty group
        assertThatIllegalArgumentException().isThrownBy(() -> ipc.get("", ARTIFACT1));

        // null artifact
        assertThatIllegalArgumentException().isThrownBy(() -> ipc.get(GROUP1, null));

        // empty artifact
        assertThatIllegalArgumentException().isThrownBy(() -> ipc.get(GROUP1, ""));
    }

    @Test
    void testGetDroolsController() {
        // unknown controller
        assertThatIllegalStateException().isThrownBy(() -> ipc.get(drools));

        when(drools.isBrained()).thenReturn(true);
        when(drools2.isBrained()).thenReturn(true);

        ipc.build(MY_NAME, properties);
        ipc.build(MY_NAME2, properties);

        assertEquals(controller, ipc.get(drools));
        assertEquals(controller2, ipc.get(drools2));

        // null controller
        DroolsController nullDrools = null;
        assertThatIllegalArgumentException().isThrownBy(() -> ipc.get(nullDrools));
    }

    @Test
    void testInventory() {
        ipc.build(MY_NAME, properties);
        ipc.build(MY_NAME2, properties);

        List<PolicyController> lst = ipc.inventory();
        lst.sort(Comparator.comparing(PolicyController::getName));
        assertEquals(Arrays.asList(controller, controller2), lst);
    }

    @Test
    void testGetFeatures() {
        assertEquals(Arrays.asList(FEATURE1, FEATURE2), ipc.getFeatures());
    }

    @Test
    void testGetFeatureProviders() {
        assertEquals(providers, ipc.getFeatureProviders());
    }

    @Test
    void testGetFeatureProvider() {
        // null name
        assertThatIllegalArgumentException().isThrownBy(() -> ipc.getFeatureProvider(null));

        // empty name
        assertThatIllegalArgumentException().isThrownBy(() -> ipc.getFeatureProvider(""));

        // unknown name
        assertThatIllegalArgumentException().isThrownBy(() -> ipc.getFeatureProvider("unknown-feature"));

        assertEquals(feature1, ipc.getFeatureProvider(FEATURE1));
        assertEquals(feature2, ipc.getFeatureProvider(FEATURE2));
    }

    @Test
    void testControllerType() {
        PolicyControllerFactory factory = new IndexedPolicyControllerFactory();
        Properties props = new Properties();

        // this should build an 'AggregatedPolicyController'
        final String name1 = "ctrl1";
        PolicyController ctrl1 = factory.build(name1, props);

        // this should build a 'TestPolicyController'
        final String name2 = "ctrl2";
        props.setProperty(PROPERTY_CONTROLLER_TYPE, POLICY_CONTROLLER_BUILDER_TAG);
        PolicyController ctrl2 = factory.build(name2, props);

        // verify controller types
        assertSame(AggregatedPolicyController.class, ctrl1.getClass());
        assertSame(TestPolicyController.class, ctrl2.getClass());
        assertSame(NullDroolsController.class, ctrl2.getDrools().getClass());

        // verify controller lookups
        assertSame(ctrl1, factory.get(name1));
        assertSame(ctrl2, factory.get(name2));
    }

    /**
     * Factory with overrides.
     */
    private class IndexedPolicyControllerFactoryImpl extends IndexedPolicyControllerFactory {

        @Override
        protected PolicyController newPolicyController(String name, Properties properties) {
            if (MY_NAME.equals(name)) {
                return controller;

            } else if (MY_NAME2.equals(name)) {
                return controller2;

            } else {
                throw new IllegalArgumentException("unknown controller name: " + name);

            }
        }

        @Override
        protected List<PolicyControllerFeatureApi> getProviders() {
            return providers;
        }
    }

    /**
     * This class provides an alternate PolicyController implementation,
     * for the purpose of easy identification within a junit test.
     */
    public static class TestPolicyController extends AggregatedPolicyController {
        public TestPolicyController(String name, Properties properties) {
            super(name, properties);
        }
    }

    /**
     * An instance of this class is created by 'IndexedPolicyControllerFactory',
     * using features. It does the build operation when the value of the
     * 'controller.type' property matches the value of POLICY_CONTROLLER_BUILDER_TAG.
     */
    public static class PolicyBuilder implements PolicyControllerFeatureApi {
        @Override
        public int getSequenceNumber() {
            return 1;
        }

        @Override
        public PolicyController beforeInstance(String name, Properties properties) {
            if (POLICY_CONTROLLER_BUILDER_TAG.equals(properties.getProperty(PROPERTY_CONTROLLER_TYPE))) {
                return new TestPolicyController(name, properties);
            }
            return null;
        }
    }

    /**
     * An instance of this class is created by 'IndexedDroolsControllerFactory',
     * using features. It does the build operation when the value of the
     * 'controller.type' property matches the value of POLICY_CONTROLLER_BUILDER_TAG.
     */
    public static class DroolsBuilder implements DroolsControllerFeatureApi {
        @Override
        public int getSequenceNumber() {
            return 1;
        }

        @Override
        public DroolsController beforeInstance(Properties properties,
                String groupId, String artifactId, String version,
                List<TopicCoderFilterConfiguration> decoderConfigurations,
                List<TopicCoderFilterConfiguration> encoderConfigurations) {

            if (POLICY_CONTROLLER_BUILDER_TAG.equals(properties.getProperty(PROPERTY_CONTROLLER_TYPE))) {
                return new NullDroolsController();
            }
            return null;
        }
    }
}
