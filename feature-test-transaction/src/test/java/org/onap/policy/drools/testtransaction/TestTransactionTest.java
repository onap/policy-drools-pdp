/*-
 * ============LICENSE_START=======================================================
 * feature-test-transaction
 * ================================================================================
 * Copyright (C) 2017-2020 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.testtransaction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.BeforeClass;
import org.junit.Test;
import org.onap.policy.drools.persistence.SystemPersistenceConstants;
import org.onap.policy.drools.properties.DroolsPropertyConstants;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.drools.system.PolicyControllerConstants;
import org.onap.policy.drools.system.PolicyEngineConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestTransactionTest {
    /** Test JUnit Controller Name. */
    public static final String TEST_CONTROLLER_NAME = "unnamed";
    /** Controller Configuration File. */
    public static final String TEST_CONTROLLER_FILE = TEST_CONTROLLER_NAME + "-controller.properties";

    /** Controller Configuration Backup File. */
    public static final String TEST_CONTROLLER_FILE_BAK =
            TEST_CONTROLLER_NAME + "-controller.properties.bak";

    /** logger. */
    private static Logger logger = LoggerFactory.getLogger(TestTransactionTest.class);

    /**
     * Start up.
     *
     * @throws IOException exception
     */
    @BeforeClass
    public static void startUp() throws IOException {
        logger.info("enter");

        cleanUpWorkingDir();

        /* ensure presence of config directory */
        SystemPersistenceConstants.getManager().setConfigurationDir(null);
    }

    @Test
    public void testRegisterUnregister() throws InterruptedException {
        final Properties controllerProperties = new Properties();
        controllerProperties.put(DroolsPropertyConstants.PROPERTY_CONTROLLER_NAME, TEST_CONTROLLER_NAME);
        final PolicyController controller =
                PolicyEngineConstants.getManager().createPolicyController(TEST_CONTROLLER_NAME, controllerProperties);
        assertNotNull(PolicyControllerConstants.getFactory().get(TEST_CONTROLLER_NAME));
        logger.info(controller.toString());

        CountDownLatch latch = new CountDownLatch(1);

        // use our own impl so we can decrement the latch when run() completes
        TtImpl impl = new TtImpl() {
            @Override
            protected TtControllerTask makeControllerTask(PolicyController controller) {
                return new TtControllerTask(controller) {
                    @Override
                    public void run() {
                        super.run();
                        latch.countDown();
                    }
                };
            }
        };

        impl.register(controller);
        assertNotNull(TestTransactionConstants.getManager());

        /*
         * Unregistering the controller should terminate its TestTransaction thread if it hasn't already
         * been terminated
         */
        impl.unregister(controller);

        Thread ttThread = getThread(latch, "tt-controller-task-" + TEST_CONTROLLER_NAME);
        assertEquals(null, ttThread);
    }

    /**
     * Returns thread object based on String name.
     * @param latch indicates when the thread has finished running
     * @param threadName thread name
     * @return the thread
     * @throws InterruptedException exception
     */
    public Thread getThread(CountDownLatch latch, String threadName) throws InterruptedException {
        // give a chance to the transaction thread to be spawned/destroyed
        latch.await(5, TimeUnit.SECONDS);

        final Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        for (final Thread thread : threadSet) {
            if (thread.getName().equals(threadName)) {
                return thread;
            }
        }
        return null;
    }

    /** clean up working directory. */
    protected static void cleanUpWorkingDir() {
        final Path testControllerPath =
                        Paths.get(SystemPersistenceConstants.getManager().getConfigurationPath().toString(),
                                        TEST_CONTROLLER_FILE);
        try {
            Files.deleteIfExists(testControllerPath);
        } catch (final Exception e) {
            logger.info("Problem cleaning {}", testControllerPath, e);
        }

        final Path testControllerBakPath =
                        Paths.get(SystemPersistenceConstants.getManager().getConfigurationPath().toString(),
                                        TEST_CONTROLLER_FILE_BAK);
        try {
            Files.deleteIfExists(testControllerBakPath);
        } catch (final Exception e) {
            logger.info("Problem cleaning {}", testControllerBakPath, e);
        }
    }
}
