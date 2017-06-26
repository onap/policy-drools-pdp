/*-
 * ============LICENSE_START=======================================================
 * feature-eelf
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
package org.openecomp.policy.drools.eelf.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.openecomp.policy.common.logging.flexlogger.FlexLogger;
import org.openecomp.policy.drools.eelf.EelfFeature;
import org.openecomp.policy.drools.system.Main;
import org.openecomp.policy.drools.system.PolicyEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.att.eelf.configuration.Configuration;
import com.att.eelf.configuration.EELFLogger;
import com.att.eelf.configuration.EELFLogger.Level;
import com.att.eelf.configuration.EELFManager;

import ch.qos.logback.classic.LoggerContext;

/**
 * Logger Tests
 */
public class EElfTest {
	
	/**
	 * logback configuration location
	 */
	public final static String LOGBACK_CONFIGURATION_FILE_DEFAULT = "src/main/install/config/logback.xml";
	
	/**
	 * SLF4J Logger
	 */
	private final Logger slf4jLogger = LoggerFactory.getLogger(EElfTest.class);
	
	/**
	 * get all loggers
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
	 * Assert Log Levels are the same between an EELF Logger and an SLF4J Logger
	 * 
	 * @param eelfAuditLogger
	 * @param slf4jAuditLogger
	 */
	protected void assertLogLevels(EELFLogger eelfAuditLogger, Logger slf4jAuditLogger) {
		assertTrue(slf4jAuditLogger.isDebugEnabled() == eelfAuditLogger.isDebugEnabled());
		assertTrue(slf4jAuditLogger.isInfoEnabled() == eelfAuditLogger.isInfoEnabled());
		assertTrue(slf4jAuditLogger.isErrorEnabled() == eelfAuditLogger.isErrorEnabled());
		assertTrue(slf4jAuditLogger.isWarnEnabled() == eelfAuditLogger.isWarnEnabled());
		assertTrue(slf4jAuditLogger.isTraceEnabled() == eelfAuditLogger.isTraceEnabled());
	}
	
	@Test
	public void slf4jLog() {
		
		/* standard slf4j using defaults */
		
		slf4jLogger.info("slf4j info");
		
		List<String> loggers = loggers();  	
    	
		assertFalse(loggers.contains(Configuration.DEBUG_LOGGER_NAME));
		assertFalse(loggers.contains(Configuration.AUDIT_LOGGER_NAME));
		assertFalse(loggers.contains(Configuration.ERROR_LOGGER_NAME));
		assertFalse(loggers.contains(Configuration.METRICS_LOGGER_NAME));
		
		/* set logback configuration */
		
    	System.setProperty(Main.LOGBACK_CONFIGURATION_FILE_SYSTEM_PROPERTY, 
                           LOGBACK_CONFIGURATION_FILE_DEFAULT);
    	
    	/* set up eelf throuth common loggings library */
    	
		EelfFeature feature = new EelfFeature();
		feature.beforeBoot(PolicyEngine.manager, null);
		
		loggers = loggers();
		assertTrue(loggers.contains(Configuration.DEBUG_LOGGER_NAME));
		assertTrue(loggers.contains(Configuration.AUDIT_LOGGER_NAME));
		assertTrue(loggers.contains(Configuration.ERROR_LOGGER_NAME));
		assertTrue(loggers.contains(Configuration.METRICS_LOGGER_NAME));
		
		EELFLogger eelfAuditLogger = EELFManager.getInstance().getAuditLogger();
		Logger slf4jAuditLogger = org.slf4j.LoggerFactory.getLogger(Configuration.AUDIT_LOGGER_NAME);
		org.openecomp.policy.common.logging.flexlogger.Logger flexLogger = 
												FlexLogger.getLogger(EElfTest.class);
		
		/* generate an audit entry through both logs */
		
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
