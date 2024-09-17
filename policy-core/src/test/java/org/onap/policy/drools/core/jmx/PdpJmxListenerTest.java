/*-
 * ============LICENSE_START===============================================
 * ONAP
 * ========================================================================
 * Copyright (C) 2024 Nordix Foundation.
 * ========================================================================
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
 * ============LICENSE_END=================================================
 */

package org.onap.policy.drools.core.jmx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.management.ManagementFactory;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class PdpJmxListenerTest {

    @Test
    void test() {
        Assertions.assertDoesNotThrow(PdpJmxListener::start);
        Assertions.assertDoesNotThrow(PdpJmxListener::stop);
    }

    @Test
    void testExceptions()
        throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException,
        MBeanRegistrationException, InstanceNotFoundException {

        var mockMBean = Mockito.mock(MBeanServer.class);
        Mockito.doThrow(MBeanRegistrationException.class).when(mockMBean)
            .registerMBean(PdpJmx.getInstance(), new ObjectName("PolicyEngine:type=PdpJmx"));
        Mockito.doThrow(MBeanRegistrationException.class).when(mockMBean)
            .unregisterMBean(new ObjectName("PolicyEngine:type=PdpJmx"));

        // trying to reach exception catch clause, but can't validate if exception was thrown
        try (MockedStatic<ManagementFactory> factory = Mockito.mockStatic(ManagementFactory.class)) {
            factory.when(ManagementFactory::getPlatformMBeanServer).thenReturn(mockMBean);
            assertEquals(mockMBean, ManagementFactory.getPlatformMBeanServer());

            Assertions.assertDoesNotThrow(PdpJmxListener::start);
            Assertions.assertDoesNotThrow(PdpJmxListener::stop);
        }
    }
}