/*-
 * ============LICENSE_START=======================================================
 * policy-management
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

package org.onap.policy.drools.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Properties;
import org.junit.Test;

public class DroolsControllerFactoryTest {

    @Test
    public void buildNullController() {
        Properties droolsProps = new Properties();
        DroolsController droolsController = DroolsController.factory.build(droolsProps, null, null);

        if (!isNullController(droolsController)) {
        		fail("drools controller is not a null controller");
        }
    }

    @Test
    public void getNullController() {
        DroolsController controller =
            DroolsController.factory.get(DroolsController.NO_GROUP_ID,
                DroolsController.NO_ARTIFACT_ID, DroolsController.NO_VERSION);

        assertNotNull(controller);
        assertEquals(DroolsController.NO_GROUP_ID, controller.getGroupId());
        assertEquals(DroolsController.NO_ARTIFACT_ID, controller.getArtifactId());
        assertEquals(DroolsController.NO_VERSION, controller.getVersion());
    }

    @Test
    public void inventory() {
        List<DroolsController> controllers = DroolsController.factory.inventory();
        assertNotNull(controllers);
        
        for (int i = 0; i < controllers.size(); i++) {
            if (!isNullController(controllers.get(i)) && !isActualController(controllers.get(i))) {
            		fail("drools controller is not a null controller");
            }
        }
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

    private boolean isNullController(DroolsController droolsController) {
        if (droolsController == null) {
        		return false;
        }
        
        if (!DroolsController.NO_GROUP_ID.equals(droolsController.getGroupId())) {
        		return false;
        }
        
        if (!DroolsController.NO_ARTIFACT_ID.equals(droolsController.getArtifactId())) {
        		return false;
        }

        return DroolsController.NO_VERSION.equals(droolsController.getVersion());
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
}
