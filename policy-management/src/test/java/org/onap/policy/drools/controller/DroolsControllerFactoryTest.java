/*-
 * ============LICENSE_START=======================================================
 * policy-management
 * ================================================================================
 * Copyright (C) 2017 AT&T Intellectual Property. All rights reserved.
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
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Properties;
import org.junit.Test;

public class DroolsControllerFactoryTest {

    @Test
    public void buildNullController() {
        Properties droolsProps = new Properties();
        DroolsController droolsController =
            DroolsController.factory.build(droolsProps, null, null);

        assertNullController(droolsController);
    }

    @Test
    public void getNullController() {
        DroolsController controller =
            DroolsController.factory.get(DroolsController.NO_GROUP_ID,
                DroolsController.NO_ARTIFACT_ID, DroolsController.NO_VERSION);

        assertNotNull(controller);
        assertEquals(controller.getGroupId(), DroolsController.NO_GROUP_ID);
        assertEquals(controller.getArtifactId(), DroolsController.NO_ARTIFACT_ID);
        assertEquals(controller.getVersion(), DroolsController.NO_VERSION);
    }

    @Test
    public void inventory() {
        List<DroolsController> controllers = DroolsController.factory.inventory();
        assertNotNull(controllers);
        assertTrue(controllers.size() == 1);
        assertNullController(controllers.get(0));
    }

    @Test
    public void shutdown() {
        DroolsControllerFactory droolsFactory = new IndexedDroolsControllerFactory();
        droolsFactory.shutdown();
        assertTrue(droolsFactory.inventory().isEmpty());
    }

    @Test
    public void destroy() {
        DroolsControllerFactory droolsFactory = new IndexedDroolsControllerFactory();
        droolsFactory.destroy();
        assertTrue(droolsFactory.inventory().isEmpty());
    }

    private void assertNullController(DroolsController droolsController) {
        assertNotNull(droolsController);
        assertEquals(droolsController.getGroupId(), DroolsController.NO_GROUP_ID);
        assertEquals(droolsController.getArtifactId(), DroolsController.NO_ARTIFACT_ID);
        assertEquals(droolsController.getVersion(), DroolsController.NO_VERSION);
    }

}