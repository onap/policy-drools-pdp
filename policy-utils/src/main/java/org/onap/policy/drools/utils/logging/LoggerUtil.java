/*-
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2017-2019,2021 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.utils.logging;

import ch.qos.logback.classic.LoggerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * Loger Utils.
 */
public class LoggerUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggerUtil.class);

    /**
     * Logback configuration file system property.
     */
    public static final String LOGBACK_CONFIGURATION_FILE_SYSTEM_PROPERTY = "logback.configurationFile";

    /**
     * Logback default configuration file location.
     */
    public static final String LOGBACK_CONFIGURATION_FILE_DEFAULT = "config/logback.xml";

    /**
     * Root logger.
     */
    public static final String ROOT_LOGGER = "ROOT";

    /**
     * Metric Log Marker Name.
     */
    public static final String METRIC_LOG_MARKER_NAME = "metric";

    /**
     * Transaction Log Marker Name.
     */
    public static final String TRANSACTION_LOG_MARKER_NAME = "transaction";

    /**
     * Marks a logging record as a metric.
     */
    public static final Marker METRIC_LOG_MARKER = MarkerFactory.getMarker(METRIC_LOG_MARKER_NAME);

    /**
     * Marks a logging record as an end-to-end transaction.
     */
    public static final Marker TRANSACTION_LOG_MARKER = MarkerFactory.getMarker(TRANSACTION_LOG_MARKER_NAME);

    private LoggerUtil() {
        // Empty constructor
    }

    /**
     * Set the log level of a logger.
     *
     * @param loggerName logger name
     * @param loggerLevel logger level
     */
    public static String setLevel(String loggerName, String loggerLevel) {
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext)) {
            throw new IllegalStateException("The SLF4J logger factory is not configured for logback");
        }

        final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        final var logger = context.getLogger(loggerName);
        if (logger == null) {
            throw new IllegalArgumentException("no logger " + loggerName);
        }

        LOGGER.warn("setting {} logger to level {}", loggerName, loggerLevel);

        // use the current log level if the string provided cannot be converted to a valid Level.

        // NOSONAR: this method is currently used by the telemetry api (which should be authenticated).
        // It is no more or no less dangerous than an admin changing the logback level on the fly.
        // This is a controlled admin function that should not cause any risks when the system
        // is configured properly.
        logger.setLevel(ch.qos.logback.classic.Level.toLevel(loggerLevel, logger.getLevel()));  // NOSONAR

        return logger.getLevel().toString();
    }
}
