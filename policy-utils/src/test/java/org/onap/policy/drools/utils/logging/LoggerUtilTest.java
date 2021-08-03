/*-
 * ============LICENSE_START=======================================================
 * policy-utils
 * ================================================================================
 * Copyright (C) 2018, 2021 AT&T Intellectual Property. All rights reserved.
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.onap.policy.common.utils.logging.LoggerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggerUtilTest {

    @Test
    public void test() {

        Logger logger = LoggerFactory.getLogger(LoggerUtilTest.class);

        assertTrue(logger.isInfoEnabled());

        logger.info("line 1");
        logger.info(LoggerUtils.METRIC_LOG_MARKER, "line 1 Metric");
        logger.info(LoggerUtils.TRANSACTION_LOG_MARKER, "line 1 Transaction");

        LoggerUtils.setLevel(LoggerUtils.ROOT_LOGGER, "warn");
        logger.info("line 2");
        logger.info(LoggerUtils.METRIC_LOG_MARKER, "line 2 Metric");
        logger.info(LoggerUtils.TRANSACTION_LOG_MARKER, "line 2 Transaction");

        assertFalse(logger.isInfoEnabled());

        LoggerUtils.setLevel(LoggerUtils.ROOT_LOGGER, "debug");
        logger.debug("line 3");
        logger.debug(LoggerUtils.METRIC_LOG_MARKER, "line 3 Metric");
        logger.debug(LoggerUtils.TRANSACTION_LOG_MARKER, "line 3 Transaction");

        assertTrue(logger.isDebugEnabled());
    }

}
