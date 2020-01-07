/*
 * ============LICENSE_START=======================================================
 * Copyright (C) 2019-2020 AT&T Intellectual Property. All rights reserved.
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
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.onap.policy.common.utils.coder.StandardCoder;
import org.onap.policy.common.utils.time.PseudoScheduledExecutorService;
import org.onap.policy.common.utils.time.TestTimeMulti;
import org.onap.policy.drools.persistence.SystemPersistenceConstants;
import org.onap.policy.drools.utils.logging.LoggerUtil;
import org.onap.policy.models.pdp.concepts.PdpStatus;
import org.onap.policy.models.pdp.enums.PdpMessageType;
import org.onap.policy.models.pdp.enums.PdpState;

public abstract class LifecycleStateRunningTest {

    private static final String CONTROLLER_NAME = "lifecycle";
    protected static ControllerSupport controllerSupport = new ControllerSupport(CONTROLLER_NAME);
    protected TestTimeMulti time;
    protected LifecycleFsm fsm;

    /**
     * Set up.
     */
    @BeforeClass
    public static void setUp() throws IOException {
        LoggerUtil.setLevel(LoggerUtil.ROOT_LOGGER, "INFO");
        LoggerUtil.setLevel("org.onap.policy.common.endpoints", "WARN");
        LoggerUtil.setLevel("org.onap.policy.drools", "INFO");
        SystemPersistenceConstants.getManager().setConfigurationDir("target/test-classes");
        controllerSupport.createController();
    }

    /**
     * Tear Down.
     */
    @AfterClass
    public static void tearDown() {
        try {
            Files.deleteIfExists(Paths.get(SystemPersistenceConstants.getManager().getConfigurationPath().toString(),
                                     CONTROLLER_NAME + "-controller.properties.bak"));
        } catch (IOException e) {
            ;
        }
        SystemPersistenceConstants.getManager().setConfigurationDir(null);
    }

    /**
     * Creates an FSM that uses pseudo time.
     * @return a new FSM
     */
    public LifecycleFsm makeFsmWithPseudoTime() {
        time = new TestTimeMulti();

        return new LifecycleFsm() {
            @Override
            protected ScheduledExecutorService makeExecutor() {
                return new PseudoScheduledExecutorService(time);
            }
        };
    }

    public void waitUntil(long twait, TimeUnit units, Callable<Boolean> condition) {
        time.waitUntil(twait, units, condition);
    }

    protected Callable<Boolean> isStatus(PdpState state, int count) {
        return () -> {
            if (fsm.client.getSink().getRecentEvents().length != count) {
                return false;
            }

            String[] events = fsm.client.getSink().getRecentEvents();
            PdpStatus status = new StandardCoder().decode(events[events.length - 1], PdpStatus.class);

            if (status.getSupportedPolicyTypes() != null) {
                return false;
            }

            return status.getMessageName() == PdpMessageType.PDP_STATUS && state == status.getState();
        };
    }

    protected Callable<Boolean> isStatus(PdpState state) {
        return isStatus(state, fsm.client.getSink().getRecentEvents().length);
    }
}
