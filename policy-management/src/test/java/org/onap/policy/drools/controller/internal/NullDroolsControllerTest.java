/*-
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018-2021 AT&T Intellectual Property. All rights reserved.
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

import org.junit.Assert;
import org.junit.Test;
import org.onap.policy.common.utils.gson.GsonTestUtils;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.controller.DroolsControllerConstants;

public class NullDroolsControllerTest {

    @Test
    public void testStart() {
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
    public void testSerialize() {
        assertThatCode(() -> new GsonTestUtils().compareGson(new NullDroolsController(),
                        NullDroolsControllerTest.class)).doesNotThrowAnyException();
    }

    @Test
    public void testLock() {
        DroolsController controller = new NullDroolsController();
        controller.lock();
        Assert.assertFalse(controller.isLocked());
        controller.unlock();
        Assert.assertFalse(controller.isLocked());
    }

    @Test
    public void getGroupId() {
        Assert.assertEquals(DroolsControllerConstants.NO_GROUP_ID, new NullDroolsController().getGroupId());
    }

    @Test
    public void getArtifactId() {
        Assert.assertEquals(DroolsControllerConstants.NO_ARTIFACT_ID, new NullDroolsController().getArtifactId());
    }

    @Test
    public void getVersion() {
        Assert.assertEquals(DroolsControllerConstants.NO_VERSION, new NullDroolsController().getVersion());
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
        Assert.assertEquals(0, new NullDroolsController().getRecentSourceEvents().length);
    }

    @Test
    public void getRecentSinkEvents() {
        Assert.assertEquals(0, new NullDroolsController().getRecentSinkEvents().length);
    }

    @Test
    public void getContainer() {
        Assert.assertNull(new NullDroolsController().getContainer());
    }

    @Test
    public void getDomains() {
        Assert.assertTrue(new NullDroolsController().getBaseDomainNames().isEmpty());
    }

    @Test(expected = IllegalStateException.class)
    public void ownsCoder() {
        new NullDroolsController().ownsCoder(null, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void fetchModelClass() {
        new NullDroolsController().fetchModelClass(this.getClass().getName());
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
        Assert.assertEquals(0, new NullDroolsController().factCount(null));
    }

    @Test
    public void facts() {
        Assert.assertTrue(new NullDroolsController().facts(null, null, true).isEmpty());
    }

    @Test
    public void factQuery() {
        Assert.assertTrue(new NullDroolsController().factQuery(null, null, null, false).isEmpty());
    }

    @Test
    public void exists() {
        Object o1 = new Object();
        Assert.assertFalse(new NullDroolsController().exists("blah", o1));
        Assert.assertFalse(new NullDroolsController().exists(o1));
    }
}
