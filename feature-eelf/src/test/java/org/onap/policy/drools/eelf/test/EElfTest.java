/*-
 * ============LICENSE_START=======================================================
 * feature-eelf
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

package org.onap.policy.drools.eelf.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import ch.qos.logback.classic.LoggerContext;
import com.att.eelf.configuration.Configuration;
import com.att.eelf.configuration.EELFLogger;
import com.att.eelf.configuration.EELFLogger.Level;
import com.att.eelf.configuration.EELFManager;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.onap.policy.common.logging.flexlogger.FlexLogger;
import org.onap.policy.drools.eelf.EelfFeature;
import org.onap.policy.drools.system.PolicyEngineConstants;
import org.onap.policy.drools.utils.logging.LoggerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logger Tests.
 */
public class EElfTest {

    /**
     * logback configuration location.
     */
    public static final String LOGBACK_CONFIGURATION_FILE_DEFAULT = "src/main/feature/config/logback-eelf.xml";

    /**
     * SLF4J Logger.
     */
    private final Logger slf4jLogger = LoggerFactory.getLogger(EElfTest.class);

    /**
     * Get all loggers.
     *
     * @return list of all loggers
     */
    protected List<String> loggers() {
        List<String> loggers = new ArrayList<String>();
        LoggerContext context =
                (LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
        for (org.slf4j.Logger logger: context.getLoggerList()) {
            loggers.add(logger.getName());
        }
        slf4jLogger.info(loggers.toString());
        return loggers;
    }

    /**
     * Assert Log Levels are the same between an EELF Logger and an SLF4J Logger.
     *
     * @param eelfLogger EELF Logger
     * @param slf4jLogger SLF4J Logger
     */
    protected void assertLogLevels(EELFLogger eelfLogger, Logger slf4jLogger) {
        assertTrue(slf4jLogger.isDebugEnabled() == eelfLogger.isDebugEnabled());
        assertTrue(slf4jLogger.isInfoEnabled() == eelfLogger.isInfoEnabled());
        assertTrue(slf4jLogger.isErrorEnabled() == eelfLogger.isErrorEnabled());
        assertTrue(slf4jLogger.isWarnEnabled() == eelfLogger.isWarnEnabled());
        assertTrue(slf4jLogger.isTraceEnabled() == eelfLogger.isTraceEnabled());
    }

    @Test
    public void testSlf4jLog() {

        /* standard slf4j using defaults */

        slf4jLogger.info("slf4j info");

        List<String> loggers = loggers();

        assertFalse(loggers.contains(Configuration.DEBUG_LOGGER_NAME));
        assertFalse(loggers.contains(Configuration.AUDIT_LOGGER_NAME));
        assertFalse(loggers.contains(Configuration.ERROR_LOGGER_NAME));
        assertFalse(loggers.contains(Configuration.METRICS_LOGGER_NAME));

        /* set logback configuration */

        System.setProperty(LoggerUtil.LOGBACK_CONFIGURATION_FILE_SYSTEM_PROPERTY,
                LOGBACK_CONFIGURATION_FILE_DEFAULT);

        /* set up eelf throuth common loggings library */

        EelfFeature feature = new EelfFeature();
        feature.beforeBoot(PolicyEngineConstants.getManager(), null);

        loggers = loggers();
        assertTrue(loggers.contains(Configuration.DEBUG_LOGGER_NAME));
        assertTrue(loggers.contains(Configuration.AUDIT_LOGGER_NAME));
        assertTrue(loggers.contains(Configuration.ERROR_LOGGER_NAME));
        assertTrue(loggers.contains(Configuration.METRICS_LOGGER_NAME));

        final EELFLogger eelfAuditLogger = EELFManager.getInstance().getAuditLogger();
        final Logger slf4jAuditLogger = org.slf4j.LoggerFactory.getLogger(Configuration.AUDIT_LOGGER_NAME);
        org.onap.policy.common.logging.flexlogger.Logger flexLogger =
                FlexLogger.getLogger(EElfTest.class, true);

        /* generate an error entry */

        Exception testException = new IllegalStateException("exception test");
        flexLogger.error("flex-logger exception", testException);
        EELFManager.getInstance().getErrorLogger().error("eelf-logger exception", testException);
        org.slf4j.LoggerFactory.getLogger(Configuration.ERROR_LOGGER_NAME).error("slf4j-logger", testException);


        /* generate an audit entry through all logs */

        flexLogger.audit("flexlogger audit");
        eelfAuditLogger.info("eelf audit");
        slf4jAuditLogger.info("slf4j audit");

        /* check log levels in eelf and standard slf4j  change in both directions */

        /* eelf initiated */
        eelfAuditLogger.setLevel(Level.ERROR);
        assertLogLevels(eelfAuditLogger, slf4jAuditLogger);

        /* slf4j initiated */
        ((ch.qos.logback.classic.Logger) slf4jLogger).setLevel((ch.qos.logback.classic.Level.INFO));
        assertLogLevels(eelfAuditLogger, slf4jAuditLogger);
    }
}
