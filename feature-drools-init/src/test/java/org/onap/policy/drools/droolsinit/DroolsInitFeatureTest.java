/*-
 * ============LICENSE_START=======================================================
 * feature-drools-init
 * ================================================================================
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
 * ============LICENSE_END=========================================================
 */

package org.onap.policy.drools.droolsinit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.kie.api.runtime.KieSession;
import org.onap.policy.drools.core.PolicySession;

public class DroolsInitFeatureTest {

    private static PolicySession policySession;
    private static KieSession kieSession;

    private DroolsInitFeature feature;

    /**
     * Class-level initialization.
     */
    @BeforeClass
    public static void setUpBeforeClass() {
        policySession = mock(PolicySession.class);
        kieSession = mock(KieSession.class);
        when(policySession.getKieSession()).thenReturn(kieSession);
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
}
