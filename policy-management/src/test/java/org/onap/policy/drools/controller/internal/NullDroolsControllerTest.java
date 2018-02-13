/*-
 * ============LICENSE_START=======================================================
 * ONAP
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

package org.onap.policy.drools.controller.internal;

import org.junit.Assert;
import org.junit.Test;
import org.onap.policy.drools.controller.DroolsController;

public class NullDroolsControllerTest {

    @Test
    public void start() {
        DroolsController controller = new NullDroolsController();
        controller.start();
        Assert.assertFalse(controller.isAlive());
        controller.stop();
        Assert.assertFalse(controller.isAlive());
        controller.shutdown();
        Assert.assertFalse(controller.isAlive());
        controller.halt();
        Assert.assertFalse(controller.isAlive());
    }

    @Test
    public void lock() {
        DroolsController controller = new NullDroolsController();
        controller.lock();
        Assert.assertFalse(controller.isLocked());
        controller.unlock();
        Assert.assertFalse(controller.isLocked());
    }

    @Test
    public void getGroupId() {
        Assert.assertEquals(new NullDroolsController().getGroupId(), DroolsController.NO_GROUP_ID);
    }

    @Test
    public void getArtifactId() {
        Assert.assertEquals(new NullDroolsController().getArtifactId(), DroolsController.NO_ARTIFACT_ID);
    }

    @Test
    public void getVersion() {
        Assert.assertEquals(new NullDroolsController().getVersion(), DroolsController.NO_VERSION);
    }

    @Test
    public void getSessionNames() {
        Assert.assertTrue(new NullDroolsController().getSessionNames().isEmpty());
    }

    @Test
    public void getCanonicalSessionNames() {
        Assert.assertTrue(new NullDroolsController().getCanonicalSessionNames().isEmpty());
    }

    @Test
    public void offer() {
        Assert.assertFalse(new NullDroolsController().offer(null, null));
    }

    @Test(expected = IllegalStateException.class)
    public void deliver() {
        new NullDroolsController().deliver(null, null);
    }

    @Test
    public void getRecentSourceEvents() {
        Assert.assertTrue(new NullDroolsController().getRecentSourceEvents().length == 0);
    }

    @Test
    public void getRecentSinkEvents() {
        Assert.assertTrue(new NullDroolsController().getRecentSinkEvents().length == 0);
    }

    @Test
    public void getContainer() {
        Assert.assertNull(new NullDroolsController().getContainer());
    }

    @Test(expected = IllegalStateException.class)
    public void ownsCoder() {
        new NullDroolsController().ownsCoder(null, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void fetchModelClass() {
        new NullDroolsController().fetchModelClass(this.getClass().getCanonicalName());
    }

    @Test
    public void isBrained() {
        Assert.assertFalse(new NullDroolsController().isBrained());
    }

    @Test
    public void stringify() {
        Assert.assertNotNull(new NullDroolsController().toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void updateToVersion() {
        new NullDroolsController().updateToVersion(null, null, null, null, null);
    }

    @Test
    public void factClassNames() {
        Assert.assertTrue(new NullDroolsController().factClassNames(null).isEmpty());
    }

    @Test
    public void factCount() {
        Assert.assertTrue(new NullDroolsController().factCount(null) == 0);
    }

    @Test
    public void facts() {
        Assert.assertTrue(new NullDroolsController().facts(null, null, true).isEmpty());
    }

    @Test
    public void factQuery() {
        Assert.assertTrue(new NullDroolsController().factQuery(null, null, null, false).isEmpty());
    }
}