/*-
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2017-2019 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.system;

import java.util.Properties;
import org.onap.policy.drools.persistence.SystemPersistence;
import org.onap.policy.drools.properties.DroolsProperties;
import org.onap.policy.drools.utils.PropertyUtil;
import org.onap.policy.drools.utils.logging.LoggerUtil;
import org.onap.policy.drools.utils.logging.MDCTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Programmatic entry point to the management layer.
 */
public class Main {

    /** constructor (hides public default one). */
    private Main() {}

    /**
     * main.
     *
     * @param args program arguments.
     */
    public static void main(String[] args) {

        /* start logger */

        Logger logger = LoggerFactory.getLogger(Main.class);

        /* system properties */

        for (Properties systemProperties : SystemPersistence.manager.getSystemProperties()) {
            PropertyUtil.setSystemProperties(systemProperties);
        }

        /* 0. boot */

        PolicyEngine.manager.boot(args);

        /* 1.a. Configure Engine */

        Properties engineProperties;
        try {
            engineProperties = SystemPersistence.manager.getEngineProperties();
        } catch (IllegalArgumentException iae) {
            logger.warn("Main: engine properties not found.  Using default configuration.", iae);
            engineProperties = PolicyEngine.manager.defaultTelemetryConfig();
        }

        PolicyEngine.manager.configure(engineProperties);

        /* 1.b. Load Installation Environment(s) */

        for (Properties env : SystemPersistence.manager.getEnvironmentProperties()) {
            PolicyEngine.manager.setEnvironment(env);
        }

        /* 2. Start the Engine with the basic services only (no Policy Controllers) */

        MDCTransaction trans =
                MDCTransaction.newTransaction(null, null)
                .setServiceName(Main.class.getSimpleName())
                .setTargetEntity("engine")
                .setTargetServiceName("start");
        try {
            final boolean success = PolicyEngine.manager.start();
            if (!success) {
                trans.setStatusCode(false).setResponseDescription("partial start").flush();
                logger.warn(
                        LoggerUtil.TRANSACTION_LOG_MARKER,
                        "Main: {} has been partially started",
                        PolicyEngine.manager);
            } else {
                trans.setStatusCode(true).transaction();
            }
        } catch (final IllegalStateException e) {
            trans
                .setStatusCode(false)
                .setResponseCode(e.getClass().getSimpleName())
                .setResponseDescription(e.getMessage())
                .flush();
            logger.warn(
                    LoggerUtil.TRANSACTION_LOG_MARKER,
                    "Main: cannot start {} (bad state) because of {}",
                    PolicyEngine.manager,
                    e.getMessage(),
                    e);
        } catch (final Exception e) {
            trans
                .setStatusCode(false)
                .setResponseCode(e.getClass().getSimpleName())
                .setResponseDescription(e.getMessage())
                .flush();
            logger.warn(
                    LoggerUtil.TRANSACTION_LOG_MARKER,
                    "Main: cannot start {} because of {}",
                    PolicyEngine.manager,
                    e.getMessage(),
                    e);
            System.exit(1);
        }

        /* 3. Create and start the controllers */

        for (final Properties controllerProperties :
            SystemPersistence.manager.getControllerProperties()) {
            final String controllerName =
                    controllerProperties.getProperty(DroolsProperties.PROPERTY_CONTROLLER_NAME);
            try {
                trans =
                        MDCTransaction.newTransaction(null, null)
                        .setServiceName(Main.class.getSimpleName())
                        .setTargetEntity("controller:" + controllerName)
                        .setTargetServiceName("start");

                final PolicyController controller =
                        PolicyEngine.manager.createPolicyController(controllerName, controllerProperties);
                controller.start();

                trans
                    .setStatusCode(true)
                    .setResponseDescription(controller.getDrools().getCanonicalSessionNames().toString())
                    .transaction();
            } catch (final Exception e) {
                trans
                    .setStatusCode(false)
                    .setResponseCode(e.getClass().getSimpleName())
                    .setResponseDescription(e.getMessage())
                    .flush();
                logger.error(
                        LoggerUtil.TRANSACTION_LOG_MARKER,
                        "Main: cannot instantiate policy-controller {} because of {}",
                        controllerName,
                        e.getMessage(),
                        e);
            } catch (final LinkageError e) {
                trans
                    .setStatusCode(false)
                    .setResponseCode(e.getClass().getSimpleName())
                    .setResponseDescription(e.getMessage())
                    .flush();
                logger.warn(
                        LoggerUtil.TRANSACTION_LOG_MARKER,
                        "Main: cannot instantiate policy-controller {} (linkage) because of {}",
                        controllerName,
                        e.getMessage(),
                        e);
            }
        }
    }
}
