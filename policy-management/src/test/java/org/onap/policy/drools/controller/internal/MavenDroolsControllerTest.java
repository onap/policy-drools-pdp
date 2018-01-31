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

package org.onap.policy.drools.controller.internal;

import java.io.IOException;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.kie.api.builder.ReleaseId;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.util.KieUtils;

public class MavenDroolsControllerTest {

    private static final String JUNIT_ECHO_KSESSION = "echo";
    private static final String JUNIT_ECHO_KMODULE_DRL_PATH = "src/test/resources/echo.drl";
    private static final String JUNIT_ECHO_KMODULE_POM_PATH = "src/test/resources/echo.pom";
    private static final String JUNIT_ECHO_KMODULE_PATH = "src/test/resources/echo.kmodule";
    private static final String JUNIT_ECHO_KJAR_DRL_PATH =
        "src/main/resources/kbEcho/org/onap/policy/drools/test/echo.drl";

    private static volatile ReleaseId releaseId;

    @BeforeClass
    public static void setUp() throws IOException {
        releaseId =
            KieUtils.installArtifact(Paths.get(JUNIT_ECHO_KMODULE_PATH).toFile(),
                Paths.get(JUNIT_ECHO_KMODULE_POM_PATH).toFile(),
                JUNIT_ECHO_KJAR_DRL_PATH,
                Paths.get(JUNIT_ECHO_KMODULE_DRL_PATH).toFile());
    }

    @Test
    public void stop() throws IOException, InterruptedException {
        createDroolsController(10000L).stop();
    }

    @Test
    public void shutdown() throws IOException, InterruptedException {
        createDroolsController(10000L).shutdown();
    }

    @Test
    public void lock() throws IOException, InterruptedException {
        DroolsController controller = createDroolsController(30000L);

        controller.lock();
        Assert.assertTrue(controller.isLocked());

        controller.unlock();
        Assert.assertFalse(controller.isLocked());

        controller.halt();
        Assert.assertFalse(controller.isAlive());
    }

    private DroolsController createDroolsController(long courtesyStartTimeMs) throws InterruptedException {
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

        /* courtesy timer to allow full initialization from local maven repository */
        Thread.sleep(courtesyStartTimeMs);

        Assert.assertTrue(controller.getSessionNames().size() == 1);
        Assert.assertEquals(JUNIT_ECHO_KSESSION, controller.getSessionNames().get(0));
        Assert.assertTrue(controller.getCanonicalSessionNames().size() == 1);
        Assert.assertTrue(controller.getCanonicalSessionNames().get(0).contains(JUNIT_ECHO_KSESSION));

        return controller;
    }
}