/*-
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2017 AT&T Intellectual Property. All rights reserved.
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
package org.onap.policy.drools.utils;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;

/**
 * Loger Utils
 */
public class LoggerUtil {

  /**
   * Root logger
   */
  public static final String ROOT_LOGGER = "ROOT";

  /**
   * set the log level of a logger
   *
   * @param loggerName logger name
   * @param loggerLevel logger level
   */
  public static String setLevel(String loggerName, String loggerLevel) {
    if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext))
      throw new IllegalStateException("The SLF4J logger factory is not configured for logback");

    final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    final ch.qos.logback.classic.Logger logger = context.getLogger(loggerName);
    if (logger == null)
      throw new IllegalArgumentException("no logger " + loggerName);

    logger.setLevel(ch.qos.logback.classic.Level.toLevel(loggerLevel));
    return logger.getLevel().toString();
  }
}
