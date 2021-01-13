/*-
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2019-2021 AT&T Intellectual Property. All rights reserved.
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

import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.kie.api.builder.ReleaseId;
import org.onap.policy.common.utils.gson.GsonTestUtils;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.util.KieUtils;

public class MavenDroolsControllerTest {
    public static final String JUNIT_ECHO_KSESSION = "echo";
    public static final String JUNIT_ECHO_KBASE = "onap.policies.test";
    public static final String JUNIT_ECHO_KMODULE_DRL_PATH = "src/test/resources/echo.drl";
    public static final String JUNIT_ECHO_KMODULE_POM_PATH = "src/test/resources/echo.pom";
    public static final String JUNIT_ECHO_KMODULE_PATH = "src/test/resources/echo.kmodule";
    public static final String JUNIT_ECHO_KJAR_DRL_PATH = "src/main/resources/kbEcho/org/onap/policy/drools/test/";

    private static volatile ReleaseId releaseId;
    private static volatile CountDownLatch running;

    /**
     * Set up.
     *
     * @throws IOException throws an IO exception
     */
    @BeforeClass
    public static void setUpBeforeClass() throws IOException {
        releaseId =
            KieUtils.installArtifact(Paths.get(JUNIT_ECHO_KMODULE_PATH).toFile(),
                Paths.get(JUNIT_ECHO_KMODULE_POM_PATH).toFile(),
                JUNIT_ECHO_KJAR_DRL_PATH,
                Paths.get(JUNIT_ECHO_KMODULE_DRL_PATH).toFile());
    }

    @Before
    public void setUp() {
        running = new CountDownLatch(1);
    }

    public static void setRunning() {
        running.countDown();
    }

    @Test
    public void stop() throws InterruptedException {
        createDroolsController(10000L).stop();
    }

    @Test
    public void shutdown() throws InterruptedException {
        createDroolsController(10000L).shutdown();
    }

    @Test
    public void testLock() throws InterruptedException {
        DroolsController controller = createDroolsController(30000L);

        controller.lock();
        Assert.assertTrue(controller.isLocked());

        controller.unlock();
        Assert.assertFalse(controller.isLocked());

        controller.halt();
        Assert.assertFalse(controller.isAlive());

        new GsonTestUtils().compareGson(controller, MavenDroolsControllerTest.class);
    }

    @Test
    public void testFact() throws InterruptedException {
        DroolsController controller = createDroolsController(30000L);

        Integer one = 1;
        Integer two = 2;

        Assert.assertTrue(controller.offer(one));
        Assert.assertTrue(controller.exists(one));
        Assert.assertFalse(controller.exists(two));

        Assert.assertTrue(controller.offer(two));
        Assert.assertTrue(controller.exists(one));
        Assert.assertTrue(controller.exists(two));

        Integer three = 3;
        Assert.assertFalse(controller.delete(three));
        Assert.assertTrue(controller.exists(one));
        Assert.assertTrue(controller.exists(two));

        Assert.assertTrue(controller.delete(two));
        Assert.assertTrue(controller.exists(one));
        Assert.assertFalse(controller.exists(two));

        Assert.assertTrue(controller.delete(one));
        Assert.assertFalse(controller.exists(one));
        Assert.assertFalse(controller.exists(two));
    }

    private DroolsController createDroolsController(long courtesyStartTimeMs) throws InterruptedException {
        if (releaseId == null) {
            throw new IllegalStateException("no prereq artifact installed in maven repository");
        }

        DroolsController controller = new MavenDroolsController(releaseId.getGroupId(),
            releaseId.getArtifactId(), releaseId.getVersion(), null, null);

        Assert.assertFalse(controller.isAlive());
        Assert.assertTrue(controller.isBrained());

        controller.start();

        Assert.assertTrue(controller.isAlive());
        Assert.assertTrue(controller.isBrained());

        Assert.assertEquals(releaseId.getGroupId(), controller.getGroupId());
        Assert.assertEquals(releaseId.getArtifactId(), controller.getArtifactId());
        Assert.assertEquals(releaseId.getVersion(), controller.getVersion());

        Assert.assertEquals(releaseId.getGroupId(), controller.getContainer().getGroupId());
        Assert.assertEquals(releaseId.getArtifactId(), controller.getContainer().getArtifactId());
        Assert.assertEquals(releaseId.getVersion(), controller.getContainer().getVersion());

        /* allow full initialization from local maven repository */
        Assert.assertTrue(running.await(courtesyStartTimeMs, TimeUnit.MILLISECONDS));

        Assert.assertEquals(1, controller.getSessionNames().size());
        Assert.assertEquals(JUNIT_ECHO_KSESSION, controller.getSessionNames().get(0));
        Assert.assertEquals(1, controller.getCanonicalSessionNames().size());
        Assert.assertTrue(controller.getCanonicalSessionNames().get(0).contains(JUNIT_ECHO_KSESSION));

        Assert.assertEquals(JUNIT_ECHO_KBASE, String.join(",", controller.getBaseDomainNames()));

        return controller;
    }
}
