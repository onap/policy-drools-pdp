/*-
 * ============LICENSE_START=======================================================
 * policy-core
 * ================================================================================
 * Copyright (C) 2017-2018 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.core.jmx;

import java.lang.management.ManagementFactory;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PdpJmxListener {

    public static final Logger logger = LoggerFactory.getLogger(PdpJmxListener.class);

    private PdpJmxListener() {
    }

    /**
     * Stop the listener.
     */
    public static void stop() {
        final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        try {
            server.unregisterMBean(new ObjectName("PolicyEngine:type=PdpJmx"));
        } catch (MBeanRegistrationException | InstanceNotFoundException
                | MalformedObjectNameException e) {
            logger.error("PdpJmxListener.stop(): "
                    + "Could not unregister PolicyEngine:type=PdpJmx MBean "
                    + "with the MBean server", e);
        }

    }

    /**
     *  Start.
     */
    public static void start() {
        final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        try {
            server.registerMBean(PdpJmx.getInstance(), new ObjectName("PolicyEngine:type=PdpJmx"));
        } catch (InstanceAlreadyExistsException | MBeanRegistrationException
                | NotCompliantMBeanException | MalformedObjectNameException e) {
            logger.error("PdpJmxListener.start(): "
                    + "Could not unregister PolicyEngine:type=PdpJmx MBean "
                    + "with the MBean server", e);
        }

    }

}
