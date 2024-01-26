/*-
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018-2021 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.controller.internal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.onap.policy.common.utils.gson.GsonTestUtils;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.controller.DroolsControllerConstants;

class NullDroolsControllerTest {

    @Test
    void testStart() {
        DroolsController controller = new NullDroolsController();
        controller.start();
        assertFalse(controller.isAlive());
        controller.stop();
        assertFalse(controller.isAlive());
        controller.shutdown();
        assertFalse(controller.isAlive());
        controller.halt();
        assertFalse(controller.isAlive());
    }

    @Test
    void testSerialize() {
        assertThatCode(() -> new GsonTestUtils().compareGson(new NullDroolsController(),
                        NullDroolsControllerTest.class)).doesNotThrowAnyException();
    }

    @Test
    void testLock() {
        DroolsController controller = new NullDroolsController();
        controller.lock();
        assertFalse(controller.isLocked());
        controller.unlock();
        assertFalse(controller.isLocked());
    }

    @Test
    void getGroupId() {
        assertEquals(DroolsControllerConstants.NO_GROUP_ID, new NullDroolsController().getGroupId());
    }

    @Test
    void getArtifactId() {
        assertEquals(DroolsControllerConstants.NO_ARTIFACT_ID, new NullDroolsController().getArtifactId());
    }

    @Test
    void getVersion() {
        assertEquals(DroolsControllerConstants.NO_VERSION, new NullDroolsController().getVersion());
    }

    @Test
    void getSessionNames() {
        assertTrue(new NullDroolsController().getSessionNames().isEmpty());
    }

    @Test
    void getCanonicalSessionNames() {
        assertTrue(new NullDroolsController().getCanonicalSessionNames().isEmpty());
    }

    @Test
    void offer() {
        assertFalse(new NullDroolsController().offer(null, null));
    }

    @Test
    void deliver() {
        var controller = new NullDroolsController();
        assertThrows(IllegalStateException.class, () -> controller.deliver(null, null));
    }

    @Test
    void getRecentSourceEvents() {
        assertEquals(0, new NullDroolsController().getRecentSourceEvents().length);
    }

    @Test
    void getRecentSinkEvents() {
        assertEquals(0, new NullDroolsController().getRecentSinkEvents().length);
    }

    @Test
    void getContainer() {
        assertNull(new NullDroolsController().getContainer());
    }

    @Test
    void getDomains() {
        assertTrue(new NullDroolsController().getBaseDomainNames().isEmpty());
    }

    @Test
    void ownsCoder() {
        var controller = new NullDroolsController();
        assertThrows(IllegalStateException.class, () -> controller.ownsCoder(null, 0));
    }

    @Test
    void fetchModelClass() {
        var controller = new NullDroolsController();
        var className = this.getClass().getName();
        assertThrows(IllegalArgumentException.class, () -> controller.fetchModelClass(className));
    }

    @Test
    void isBrained() {
        assertFalse(new NullDroolsController().isBrained());
    }

    @Test
    void stringify() {
        assertNotNull(new NullDroolsController().toString());
    }

    @Test
    void updateToVersion() {
        var controller = new NullDroolsController();
        assertThrows(IllegalArgumentException.class, () ->
            controller.updateToVersion(null, null, null, null, null));
    }

    @Test
    void factClassNames() {
        assertTrue(new NullDroolsController().factClassNames(null).isEmpty());
    }

    @Test
    void factCount() {
        assertEquals(0, new NullDroolsController().factCount(null));
    }

    @Test
    void facts() {
        assertTrue(new NullDroolsController().facts(null, null, true).isEmpty());
    }

    @Test
    void factQuery() {
        assertTrue(new NullDroolsController().factQuery(null, null, null, false).isEmpty());
    }

    @Test
    void exists() {
        Object o1 = new Object();
        assertFalse(new NullDroolsController().exists("blah", o1));
        assertFalse(new NullDroolsController().exists(o1));
    }
}
