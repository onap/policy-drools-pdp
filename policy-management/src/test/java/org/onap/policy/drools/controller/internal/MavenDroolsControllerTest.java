/*-
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2019-2021 AT&T Intellectual Property. All rights reserved.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    @BeforeAll
    public static void setUpBeforeClass() throws IOException {
        releaseId =
            KieUtils.installArtifact(Paths.get(JUNIT_ECHO_KMODULE_PATH).toFile(),
                Paths.get(JUNIT_ECHO_KMODULE_POM_PATH).toFile(),
                JUNIT_ECHO_KJAR_DRL_PATH,
                Paths.get(JUNIT_ECHO_KMODULE_DRL_PATH).toFile());
    }

    @BeforeEach
    public void setUp() {
        running = new CountDownLatch(1);
    }

    public static void setRunning() {
        running.countDown();
    }

    @Test
    void stop() throws InterruptedException {
        createDroolsController(10000L).stop();
    }

    @Test
    void shutdown() throws InterruptedException {
        createDroolsController(10000L).shutdown();
    }

    @Test
    void testLock() throws InterruptedException {
        DroolsController controller = createDroolsController(30000L);

        controller.lock();
        assertTrue(controller.isLocked());

        controller.unlock();
        assertFalse(controller.isLocked());

        controller.halt();
        assertFalse(controller.isAlive());

        new GsonTestUtils().compareGson(controller, MavenDroolsControllerTest.class);
    }

    @Test
    void testFact() throws InterruptedException {
        DroolsController controller = createDroolsController(30000L);

        Integer one = 1;
        Integer two = 2;

        assertTrue(controller.offer(one));
        assertTrue(controller.exists(one));
        assertFalse(controller.exists(two));

        assertTrue(controller.offer(two));
        assertTrue(controller.exists(one));
        assertTrue(controller.exists(two));

        Integer three = 3;
        assertFalse(controller.delete(three));
        assertTrue(controller.exists(one));
        assertTrue(controller.exists(two));

        assertTrue(controller.delete(two));
        assertTrue(controller.exists(one));
        assertFalse(controller.exists(two));

        assertTrue(controller.delete(one));
        assertFalse(controller.exists(one));
        assertFalse(controller.exists(two));
    }

    private DroolsController createDroolsController(long courtesyStartTimeMs) throws InterruptedException {
        if (releaseId == null) {
            throw new IllegalStateException("no prereq artifact installed in maven repository");
        }

        DroolsController controller = new MavenDroolsController(releaseId.getGroupId(),
            releaseId.getArtifactId(), releaseId.getVersion(), null, null);

        assertFalse(controller.isAlive());
        assertTrue(controller.isBrained());

        controller.start();

        assertTrue(controller.isAlive());
        assertTrue(controller.isBrained());

        assertEquals(releaseId.getGroupId(), controller.getGroupId());
        assertEquals(releaseId.getArtifactId(), controller.getArtifactId());
        assertEquals(releaseId.getVersion(), controller.getVersion());

        assertEquals(releaseId.getGroupId(), controller.getContainer().getGroupId());
        assertEquals(releaseId.getArtifactId(), controller.getContainer().getArtifactId());
        assertEquals(releaseId.getVersion(), controller.getContainer().getVersion());

        /* allow full initialization from local maven repository */
        assertTrue(running.await(courtesyStartTimeMs, TimeUnit.MILLISECONDS));

        assertEquals(1, controller.getSessionNames().size());
        assertEquals(JUNIT_ECHO_KSESSION, controller.getSessionNames().get(0));
        assertEquals(1, controller.getCanonicalSessionNames().size());
        assertTrue(controller.getCanonicalSessionNames().get(0).contains(JUNIT_ECHO_KSESSION));

        assertEquals(JUNIT_ECHO_KBASE, String.join(",", controller.getBaseDomainNames()));

        return controller;
    }
}
