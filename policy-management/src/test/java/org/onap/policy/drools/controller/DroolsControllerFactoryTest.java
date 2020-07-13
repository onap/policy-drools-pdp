/*-
 * ============LICENSE_START=======================================================
 * policy-management
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

package org.onap.policy.drools.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.onap.policy.drools.properties.DroolsPropertyConstants.PROPERTY_CONTROLLER_TYPE;

import java.util.List;
import java.util.Properties;
import org.junit.Test;
import org.onap.policy.drools.controller.internal.NullDroolsController;
import org.onap.policy.drools.features.DroolsControllerFeatureApi;
import org.onap.policy.drools.properties.DroolsPropertyConstants;
import org.onap.policy.drools.protocol.coders.TopicCoderFilterConfiguration;

public class DroolsControllerFactoryTest {

    private static final String DROOLS_CONTROLLER_BUILDER_TAG = "DroolsControllerFactoryTest";
    public static final String TEST_GROUP_ID = "testGroupId";
    public static final String TEST_ARTIFACT_ID = "testArtifactId";
    public static final String TEST_VERSION = "testVersion";

    @Test
    public void testBuildNullController() {
        Properties droolsProps = new Properties();
        DroolsController droolsController = DroolsControllerConstants.getFactory().build(droolsProps, null, null);

        if (!isNullController(droolsController)) {
            fail("drools controller is not a null controller");
        }
    }

    @Test
    public void testGetNullController() {
        DroolsController controller =
            DroolsControllerConstants.getFactory().get(DroolsControllerConstants.NO_GROUP_ID,
                DroolsControllerConstants.NO_ARTIFACT_ID, DroolsControllerConstants.NO_VERSION);

        assertNotNull(controller);
        assertEquals(DroolsControllerConstants.NO_GROUP_ID, controller.getGroupId());
        assertEquals(DroolsControllerConstants.NO_ARTIFACT_ID, controller.getArtifactId());
        assertEquals(DroolsControllerConstants.NO_VERSION, controller.getVersion());
    }

    @Test
    public void testInventory() {
        List<DroolsController> controllers = DroolsControllerConstants.getFactory().inventory();
        assertNotNull(controllers);

        for (int i = 0; i < controllers.size(); i++) {
            if (!isNullController(controllers.get(i)) && !isActualController(controllers.get(i))) {
                fail("drools controller is not a null controller");
            }
        }
    }

    @Test
    public void testShutdown() {
        DroolsControllerFactory droolsFactory = new IndexedDroolsControllerFactory();
        droolsFactory.shutdown();
        assertTrue(droolsFactory.inventory().isEmpty());
    }

    @Test
    public void testDestroy() {
        DroolsControllerFactory droolsFactory = new IndexedDroolsControllerFactory();
        droolsFactory.destroy();
        assertTrue(droolsFactory.inventory().isEmpty());
    }

    private boolean isNullController(DroolsController droolsController) {
        if (droolsController == null) {
            return false;
        }

        if (!DroolsControllerConstants.NO_GROUP_ID.equals(droolsController.getGroupId())) {
            return false;
        }

        if (!DroolsControllerConstants.NO_ARTIFACT_ID.equals(droolsController.getArtifactId())) {
            return false;
        }

        return DroolsControllerConstants.NO_VERSION.equals(droolsController.getVersion());
    }

    @Test
    public void testControllerType() {
        DroolsControllerFactory droolsFactory = new IndexedDroolsControllerFactory();
        Properties props = new Properties();

        // this should build a 'NullDroolsController'
        DroolsController ctrl1 = droolsFactory.build(props, null, null);
        assertSame(NullDroolsController.class, ctrl1.getClass());

        props.setProperty(PROPERTY_CONTROLLER_TYPE, DROOLS_CONTROLLER_BUILDER_TAG);
        props.setProperty(DroolsPropertyConstants.RULES_GROUPID, TEST_GROUP_ID);
        props.setProperty(DroolsPropertyConstants.RULES_ARTIFACTID, TEST_ARTIFACT_ID);
        props.setProperty(DroolsPropertyConstants.RULES_VERSION, TEST_VERSION);

        // this should build a 'TestDroolsController'
        DroolsController ctrl2 = droolsFactory.build(props, null, null);
        assertSame(TestDroolsController.class, ctrl2.getClass());

        // verify that we can find the controller in the factory table
        assertSame(ctrl2, droolsFactory.get(ctrl2.getGroupId(), ctrl2.getArtifactId(), null));
    }

    private boolean isActualController(DroolsController droolsController) {
        if (droolsController == null) {
            return false;
        }

        if (!"org.onap.policy.drools.test".equals(droolsController.getGroupId())) {
            return false;
        }

        if (!"protocolcoder".equals(droolsController.getArtifactId())) {
            return false;
        }

        return droolsController.getVersion() != null && droolsController.getVersion().substring(0, 1).matches("[0-9]");
    }

    /**
     * This class provides an alternate DroolsController implementation,
     * for the purpose of easy identification within a Junit test.
     */
    public static class TestDroolsController extends NullDroolsController {
        @Override
        public String getGroupId() {
            return TEST_GROUP_ID;
        }

        @Override
        public String getArtifactId() {
            return TEST_ARTIFACT_ID;
        }
    }

    /**
     * An instance of this class is created by 'IndexedDroolsControllerFactory',
     * using features. It does the build operation when the value of the
     * 'controller.type' property matches the value of DROOLS_CONTROLLER_BUILDER_TAG.
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

            if (DROOLS_CONTROLLER_BUILDER_TAG.equals(properties.getProperty(PROPERTY_CONTROLLER_TYPE))) {
                return new TestDroolsController();
            }
            return null;
        }
    }
}
