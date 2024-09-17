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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    private final NullDroolsController controller = new NullDroolsController();
    private static final String NULL_EXCEPTION = "marked non-null but is null";

    @Test
    void testStart() {
        DroolsController controller1 = new NullDroolsController();
        controller1.start();
        assertFalse(controller1.isAlive());
        controller1.stop();
        assertFalse(controller1.isAlive());
        controller1.shutdown();
        assertFalse(controller1.isAlive());
        controller1.halt();
        assertFalse(controller1.isAlive());
    }

    @Test
    void testSerialize() {
        assertThatCode(() -> new GsonTestUtils().compareGson(new NullDroolsController(),
            NullDroolsControllerTest.class)).doesNotThrowAnyException();
    }

    @Test
    void testLock() {
        DroolsController controller1 = new NullDroolsController();
        controller1.lock();
        assertFalse(controller1.isLocked());
        controller1.unlock();
        assertFalse(controller1.isLocked());
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
        assertTrue(controller.getSessionNames().isEmpty());
    }

    @Test
    void getCanonicalSessionNames() {
        assertTrue(controller.getCanonicalSessionNames().isEmpty());
    }

    @Test
    void offer() {
        assertFalse(controller.offer(null, null));
        assertFalse(controller.offer(null));
    }

    @Test
    void deliver() {
        assertThrows(IllegalStateException.class, () -> controller.deliver(null, null));
    }

    @Test
    void getRecentSourceEvents() {
        assertEquals(0, controller.getRecentSourceEvents().length);
    }

    @Test
    void getRecentSinkEvents() {
        assertEquals(0, controller.getRecentSinkEvents().length);
    }

    @Test
    void getContainer() {
        assertNull(controller.getContainer());
    }

    @Test
    void getDomains() {
        assertTrue(controller.getBaseDomainNames().isEmpty());
    }

    @Test
    void ownsCoder() {
        assertThrows(IllegalStateException.class, () -> controller.ownsCoder(null, 0));
    }

    @Test
    void fetchModelClass() {
        var className = this.getClass().getName();
        assertThrows(IllegalArgumentException.class, () -> controller.fetchModelClass(className));
    }

    @Test
    void isBrained() {
        assertFalse(controller.isBrained());
    }

    @Test
    void stringify() {
        assertNotNull(controller.toString());
    }

    @Test
    void updateToVersion() {
        assertThrows(IllegalArgumentException.class, () ->
            controller.updateToVersion(null, null, null, null, null));
    }

    @Test
    void factClassNames() {
        assertTrue(controller.factClassNames(null).isEmpty());
    }

    @Test
    void factCount() {
        assertEquals(0, new NullDroolsController().factCount(null));
    }

    @Test
    void facts() {
        assertTrue(controller.facts(null, null, true).isEmpty());
        assertTrue(controller.facts("sessionName", Object.class).isEmpty());

        assertThatThrownBy(() -> controller.facts(null, Object.class)).hasMessageContaining(NULL_EXCEPTION);
        assertThatThrownBy(() -> controller.facts("sessionName", null)).hasMessageContaining(NULL_EXCEPTION);
    }

    @Test
    void factQuery() {
        assertTrue(controller.factQuery(null, null, null, false).isEmpty());
    }

    @Test
    void exists() {
        Object o1 = new Object();
        assertFalse(controller.exists("blah", o1));
        assertFalse(controller.exists(o1));

        assertThatThrownBy(() -> controller.exists("blah", null)).hasMessageContaining(NULL_EXCEPTION);
        assertThatThrownBy(() -> controller.exists(null, o1)).hasMessageContaining(NULL_EXCEPTION);
        assertThatThrownBy(() -> controller.exists(null)).hasMessageContaining(NULL_EXCEPTION);
    }

    @Test
    void testDelete() {
        assertThatThrownBy(() -> controller.delete("sessionName", null)).hasMessageContaining(NULL_EXCEPTION);
        assertThatThrownBy(() -> controller.delete(null, Object.class)).hasMessageContaining(NULL_EXCEPTION);
        assertThatThrownBy(() -> controller.delete(null)).hasMessageContaining(NULL_EXCEPTION);
        Object o1 = null;
        assertThatThrownBy(() -> controller.delete(o1)).hasMessageContaining(NULL_EXCEPTION);

        assertFalse(controller.delete("sessionName", new Object()));
        assertFalse(controller.delete("sessionName", Object.class));
        assertFalse(controller.delete(new Object()));
        assertFalse(controller.delete(Object.class));
    }
}
