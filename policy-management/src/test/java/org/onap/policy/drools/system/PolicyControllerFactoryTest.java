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

package org.onap.policy.drools.system;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.common.utils.gson.GsonTestUtils;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.features.PolicyControllerFeatureApi;
import org.onap.policy.drools.protocol.configuration.DroolsConfiguration;

public class PolicyControllerFactoryTest {

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
    @Before
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
    public void testFactory() {
        // use a REAL object instead of an Impl
        ipc = new IndexedPolicyControllerFactory();
        assertNotNull(ipc.getProviders());
    }

    @Test
    public void testBuild() {
        assertEquals(controller, ipc.build(MY_NAME, properties));

        // re-build - should not create another one
        assertEquals(controller, ipc.build(MY_NAME, properties));

        // brained
        setUp();
        when(drools.isBrained()).thenReturn(true);
        ipc.build(MY_NAME, properties);
    }

    @Test
    public void testSerialize() {
        assertEquals(controller, ipc.build(MY_NAME, properties));

        new GsonTestUtils().compareGson(ipc, PolicyControllerFactoryTest.class);
    }

    @Test
    public void testPatchStringDroolsConfiguration() {
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
    public void testPatchPolicyControllerDroolsConfiguration() {
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
    public void testShutdownString() {
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
    public void testShutdownPolicyController() {
        ipc.build(MY_NAME, properties);

        ipc.shutdown(controller);

        verify(controller).shutdown();

        // should no longer be managed
        assertThatIllegalArgumentException().isThrownBy(() -> ipc.get(MY_NAME));
    }

    @Test
    public void testShutdown() {
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
    public void testUnmanage() {
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
    public void testDestroyString() {
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
    public void testDestroyPolicyController() {
        ipc.build(MY_NAME, properties);

        ipc.destroy(controller);

        verify(controller).halt();

        // should no longer be managed
        assertThatIllegalArgumentException().isThrownBy(() -> ipc.get(MY_NAME));
    }

    @Test
    public void testDestroy() {
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
    public void testGetString() {
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
    public void testGetStringString_testToKey() {
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
    public void testGetDroolsController() {
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
    public void testInventory() {
        ipc.build(MY_NAME, properties);
        ipc.build(MY_NAME2, properties);

        List<PolicyController> lst = ipc.inventory();
        Collections.sort(lst, (left, right) -> left.getName().compareTo(right.getName()));
        assertEquals(Arrays.asList(controller, controller2), lst);
    }

    @Test
    public void testGetFeatures() {
        assertEquals(Arrays.asList(FEATURE1, FEATURE2), ipc.getFeatures());
    }

    @Test
    public void testGetFeatureProviders() {
        assertEquals(providers, ipc.getFeatureProviders());
    }

    @Test
    public void testGetFeatureProvider() {
        // null name
        assertThatIllegalArgumentException().isThrownBy(() -> ipc.getFeatureProvider(null));

        // empty name
        assertThatIllegalArgumentException().isThrownBy(() -> ipc.getFeatureProvider(""));

        // unknown name
        assertThatIllegalArgumentException().isThrownBy(() -> ipc.getFeatureProvider("unknown-feature"));

        assertEquals(feature1, ipc.getFeatureProvider(FEATURE1));
        assertEquals(feature2, ipc.getFeatureProvider(FEATURE2));
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
}
