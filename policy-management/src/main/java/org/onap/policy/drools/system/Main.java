/*-
 * ============LICENSE_START=======================================================
 * ONAP
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

package org.onap.policy.drools.system;

import java.util.Properties;
import org.apache.commons.lang3.StringUtils;
import org.onap.policy.common.endpoints.event.comm.TopicEndpointManager;
import org.onap.policy.common.utils.security.CryptoUtils;
import org.onap.policy.drools.persistence.SystemPersistenceConstants;
import org.onap.policy.drools.properties.DroolsPropertyConstants;
import org.onap.policy.drools.utils.PropertyUtil;
import org.onap.policy.drools.utils.logging.LoggerUtil;
import org.onap.policy.drools.utils.logging.MdcTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Programmatic entry point to the management layer.
 */
public class Main {
    /**
     * Symmetric Key to decode sensitive configuration data.
     */
    protected static final String SYSTEM_SYMM_KEY = "engine.symm.key";

    /** constructor (hides public default one). */
    private Main() {
    }

    /**
     * main.
     *
     * @param args program arguments.
     */
    public static void main(String[] args) {

        /* start logger */

        Logger logger = LoggerFactory.getLogger(Main.class);

        /* system properties */

        for (Properties systemProperties : SystemPersistenceConstants.getManager().getSystemProperties()) {
            if (!StringUtils.isBlank(systemProperties.getProperty(SYSTEM_SYMM_KEY))) {
                PropertyUtil.setDefaultCryptoCoder(new CryptoUtils(systemProperties.getProperty(SYSTEM_SYMM_KEY)));
            }
            PropertyUtil.setSystemProperties(systemProperties);
        }

        /* 0. boot */

        PolicyEngineConstants.getManager().boot(args);

        /* 1.a. Configure Engine */

        Properties engineProperties;
        try {
            engineProperties = SystemPersistenceConstants.getManager().getEngineProperties();
        } catch (IllegalArgumentException iae) {
            logger.warn("Main: engine properties not found.  Using default configuration.", iae);
            engineProperties = PolicyEngineConstants.getManager().defaultTelemetryConfig();
        }

        PolicyEngineConstants.getManager().configure(engineProperties);

        /* 1.b. Load Installation Environment(s) */

        for (Properties env : SystemPersistenceConstants.getManager().getEnvironmentProperties()) {
            PolicyEngineConstants.getManager().setEnvironment(env);
        }

        /* 2. Add topics */

        for (Properties topicProperties : SystemPersistenceConstants.getManager().getTopicProperties()) {
            TopicEndpointManager.getManager().addTopics(topicProperties);
        }

        /* 3. Start the Engine with the basic services only (no Policy Controllers) */

        MdcTransaction trans =
                MdcTransaction.newTransaction(null, null)
                .setServiceName(Main.class.getSimpleName())
                .setTargetEntity("engine")
                .setTargetServiceName("start");
        try {
            final boolean success = PolicyEngineConstants.getManager().start();
            if (!success) {
                trans.setStatusCode(false).setResponseDescription("partial start").flush();
                logger.warn(
                        LoggerUtil.TRANSACTION_LOG_MARKER,
                        "Main: {} has been partially started",
                        PolicyEngineConstants.getManager());
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
                    PolicyEngineConstants.getManager(),
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
                    PolicyEngineConstants.getManager(),
                    e.getMessage(),
                    e);
            System.exit(1);
        }

        /* 4. Create and start the controllers */

        for (final Properties controllerProperties :
            SystemPersistenceConstants.getManager().getControllerProperties()) {
            final String controllerName =
                    controllerProperties.getProperty(DroolsPropertyConstants.PROPERTY_CONTROLLER_NAME);
            try {
                trans =
                        MdcTransaction.newTransaction(null, null)
                        .setServiceName(Main.class.getSimpleName())
                        .setTargetEntity("controller:" + controllerName)
                        .setTargetServiceName("start");

                final PolicyController controller =
                        PolicyEngineConstants.getManager().createPolicyController(controllerName, controllerProperties);
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

        PolicyEngineConstants.getManager().open();
    }
}
