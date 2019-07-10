/*
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

package org.onap.policy.drools.eelf;

import com.att.eelf.configuration.Configuration;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.onap.policy.common.logging.flexlogger.FlexLogger;
import org.onap.policy.common.logging.flexlogger.Logger;
import org.onap.policy.drools.features.PolicyEngineFeatureApi;
import org.onap.policy.drools.system.PolicyEngine;
import org.onap.policy.drools.utils.logging.LoggerUtil;

/**
 * Feature EELF : Enables EELF Logging Libraries .
 */
public class EelfFeature implements PolicyEngineFeatureApi {

    @Override
    public final boolean beforeBoot(PolicyEngine engine, String[] cliArgs) {

        String logback = System.getProperty(LoggerUtil.LOGBACK_CONFIGURATION_FILE_SYSTEM_PROPERTY,
                LoggerUtil.LOGBACK_CONFIGURATION_FILE_DEFAULT);
        Path logbackPath = Paths.get(logback);

        if (System.getProperty(Configuration.PROPERTY_LOGGING_FILE_PATH) == null) {
            System.setProperty(Configuration.PROPERTY_LOGGING_FILE_PATH,
                    logbackPath.toAbsolutePath().getParent().toString());
        }

        if (System.getProperty(Configuration.PROPERTY_LOGGING_FILE_NAME) == null) {
            System.setProperty(Configuration.PROPERTY_LOGGING_FILE_NAME,
                    logbackPath.getFileName().toString());
        }

        Logger logger = FlexLogger.getLogger(this.getClass(), true);

        if (logger.isInfoEnabled()) {
            logProperty(logger, LoggerUtil.LOGBACK_CONFIGURATION_FILE_SYSTEM_PROPERTY);
            logProperty(logger, Configuration.PROPERTY_LOGGING_FILE_PATH);
            logProperty(logger, Configuration.PROPERTY_LOGGING_FILE_NAME);
        }

        return false;
    }

    private void logProperty(Logger logger, String propnm) {
        logger.info("eelf-feature: Property " + propnm + "=" + System.getProperty(propnm));
    }

    @Override
    public int getSequenceNumber() {
        return 0;
    }

}
