/*-
 * ============LICENSE_START=======================================================
 * feature-drools-init
 * ================================================================================
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
 * ============LICENSE_END=========================================================
 */

package org.onap.policy.drools.droolsinit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;
import org.onap.policy.drools.core.PolicySession;
import org.onap.policy.drools.system.PolicyEngineConstants;
import org.powermock.reflect.Whitebox;

public class DroolsInitFeatureTest {
    private static final String POLICY_ENGINE_EXECUTOR_FIELD = "executorService";
    private static ScheduledExecutorService saveExec;
    private static PolicySession policySession;
    private static KieSession kieSession;

    private DroolsInitFeature feature;

    /**
     * Class-level initialization.
     */
    @BeforeClass
    public static void setUpBeforeClass() {
        saveExec = Whitebox.getInternalState(PolicyEngineConstants.getManager(), POLICY_ENGINE_EXECUTOR_FIELD);

        policySession = mock(PolicySession.class);
        kieSession = mock(KieSession.class);
        when(policySession.getKieSession()).thenReturn(kieSession);
    }

    /**
     * Restore 'PolicyEngineConstants.manager'.
     */
    @AfterClass
    public static void cleanup() {
        Whitebox.setInternalState(PolicyEngineConstants.getManager(), POLICY_ENGINE_EXECUTOR_FIELD, saveExec);
    }

    @Before
    public void setUp() {
        feature = new DroolsInitFeature();
    }

    @Test
    public void getSequenceNumberTest() {
        assertEquals(0, feature.getSequenceNumber());
    }

    @Test
    public void selectThreadModelTest() {
        assertNull(feature.selectThreadModel(policySession));
    }

    @Test
    public void testInit() {
        FactHandle factHandle = mock(FactHandle.class);
        when(kieSession.insert(any())).thenReturn(factHandle);
        when(kieSession.getObject(factHandle)).thenReturn(new Object());

        // dummy 'ScheduledExecutorService' -- 'schedule' runs immediately
        ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(3) {
            @Override
            public ScheduledFuture<?> schedule(Runnable runnable, long delay, TimeUnit unit) {
                runnable.run();
                return null;
            }
        };

        Whitebox.setInternalState(PolicyEngineConstants.getManager(), POLICY_ENGINE_EXECUTOR_FIELD, executorService);

        // triggers creation of 'DroolsInitFeature.Init'
        feature.selectThreadModel(policySession);

        // prove that the 'delete' code ran
        verify(kieSession).delete(factHandle);
    }
}
