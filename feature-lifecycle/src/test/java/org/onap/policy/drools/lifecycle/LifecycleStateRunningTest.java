/*
 * ============LICENSE_START=======================================================
 * Copyright (C) 2019 AT&T Intellectual Property. All rights reserved.
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
 *
 * SPDX-License-Identifier: Apache-2.0
 * =============LICENSE_END========================================================
 */

package org.onap.policy.drools.lifecycle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.onap.policy.drools.persistence.SystemPersistence;
import org.onap.policy.drools.utils.logging.LoggerUtil;

public abstract class LifecycleStateRunningTest {

    private static final String CONTROLLER_NAME = "lifecycle";
    protected static ControllerSupport controllerSupport = new ControllerSupport(CONTROLLER_NAME);
    protected LifecycleFsm fsm;

    /**
     * Set up.
     */
    @BeforeClass
    public static void setUp() throws IOException {
        LoggerUtil.setLevel(LoggerUtil.ROOT_LOGGER, "INFO");
        LoggerUtil.setLevel("org.onap.policy.common.endpoints", "WARN");
        LoggerUtil.setLevel("org.onap.policy.drools", "WARN");
        SystemPersistence.manager.setConfigurationDir("src/test/resources");
        controllerSupport.createController();
    }

    /**
     * Tear Down.
     */
    @AfterClass
    public static void tearDown() {
        controllerSupport.destroyController();
        try {
            Files.deleteIfExists(Paths.get(SystemPersistence.manager.getConfigurationPath().toString(),
                                     CONTROLLER_NAME + "-controller.properties.bak"));
        } catch (IOException e) {
            ;
        }
        SystemPersistence.manager.setConfigurationDir(null);
    }
}
