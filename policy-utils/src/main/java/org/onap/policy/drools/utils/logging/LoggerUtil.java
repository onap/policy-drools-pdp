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

package org.onap.policy.drools.utils.logging;

import ch.qos.logback.classic.LoggerContext;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * Loger Utils.
 */
public class LoggerUtil {

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
        final ch.qos.logback.classic.Logger logger = context.getLogger(loggerName);
        if (logger == null) {
            throw new IllegalArgumentException("no logger " + loggerName);
        }

        logger.setLevel(ch.qos.logback.classic.Level.toLevel(loggerLevel));
        return logger.getLevel().toString();
    }
}
