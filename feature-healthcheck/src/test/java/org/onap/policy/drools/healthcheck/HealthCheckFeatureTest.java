/*-
 * ============LICENSE_START=======================================================
 * feature-healthcheck
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

package org.onap.policy.drools.healthcheck;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.onap.policy.common.endpoints.properties.PolicyEndPointProperties;
import org.onap.policy.common.utils.network.NetworkUtil;
import org.onap.policy.drools.healthcheck.HealthCheck.Report;
import org.onap.policy.drools.healthcheck.HealthCheck.Reports;
import org.onap.policy.drools.persistence.SystemPersistence;
import org.onap.policy.drools.system.PolicyEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HealthCheckFeatureTest {

    /**
     * Healthcheck Configuration File.
     */
    private static final String HEALTH_CHECK_PROPERTIES_FILE = "feature-healthcheck.properties";

    private static final Path healthCheckPropsPath =
            Paths.get(SystemPersistence.manager.getConfigurationPath().toString(), HEALTH_CHECK_PROPERTIES_FILE);

    private static final Path healthCheckPropsBackupPath = Paths
            .get(SystemPersistence.manager.getConfigurationPath().toString(), HEALTH_CHECK_PROPERTIES_FILE + ".bak");


    /**
     * logger.
     */
    private static Logger logger = LoggerFactory.getLogger(HealthCheckFeatureTest.class);

    private static Properties httpProperties = new Properties();

    /**
     * Set up. 
     */
    @BeforeClass
    public static void setup() {

        httpProperties.setProperty(PolicyEndPointProperties.PROPERTY_HTTP_SERVER_SERVICES, "HEALTHCHECK");
        httpProperties.setProperty(PolicyEndPointProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + "HEALTHCHECK"
                + PolicyEndPointProperties.PROPERTY_HTTP_HOST_SUFFIX, "localhost");
        httpProperties.setProperty(PolicyEndPointProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + "HEALTHCHECK"
                + PolicyEndPointProperties.PROPERTY_HTTP_PORT_SUFFIX, "7777");
        httpProperties.setProperty(PolicyEndPointProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + "HEALTHCHECK"
                + PolicyEndPointProperties.PROPERTY_HTTP_AUTH_USERNAME_SUFFIX, "username");
        httpProperties.setProperty(PolicyEndPointProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + "HEALTHCHECK"
                + PolicyEndPointProperties.PROPERTY_HTTP_AUTH_PASSWORD_SUFFIX, "password");
        httpProperties.setProperty(
                PolicyEndPointProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + "HEALTHCHECK"
                        + PolicyEndPointProperties.PROPERTY_HTTP_REST_CLASSES_SUFFIX,
                org.onap.policy.drools.healthcheck.RestMockHealthCheck.class.getName());
        httpProperties.setProperty(
                PolicyEndPointProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + "HEALTHCHECK"
                    + PolicyEndPointProperties.PROPERTY_HTTP_FILTER_CLASSES_SUFFIX,
                org.onap.policy.drools.healthcheck.TestAafHealthCheckFilter.class.getName());
        httpProperties.setProperty(PolicyEndPointProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + "HEALTHCHECK"
                + PolicyEndPointProperties.PROPERTY_MANAGED_SUFFIX, "true");


        httpProperties.setProperty(PolicyEndPointProperties.PROPERTY_HTTP_CLIENT_SERVICES, "HEALTHCHECK");
        httpProperties.setProperty(PolicyEndPointProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "HEALTHCHECK"
                + PolicyEndPointProperties.PROPERTY_HTTP_HOST_SUFFIX, "localhost");
        httpProperties.setProperty(PolicyEndPointProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "HEALTHCHECK"
                + PolicyEndPointProperties.PROPERTY_HTTP_PORT_SUFFIX, "7777");
        httpProperties.setProperty(PolicyEndPointProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "HEALTHCHECK"
                + PolicyEndPointProperties.PROPERTY_HTTP_URL_SUFFIX, "healthcheck/test");
        httpProperties.setProperty(PolicyEndPointProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "HEALTHCHECK"
                + PolicyEndPointProperties.PROPERTY_HTTP_HTTPS_SUFFIX, "false");
        httpProperties.setProperty(PolicyEndPointProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "HEALTHCHECK"
                + PolicyEndPointProperties.PROPERTY_HTTP_AUTH_USERNAME_SUFFIX, "username");
        httpProperties.setProperty(PolicyEndPointProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "HEALTHCHECK"
                + PolicyEndPointProperties.PROPERTY_HTTP_AUTH_PASSWORD_SUFFIX, "password");
        httpProperties.setProperty(PolicyEndPointProperties.PROPERTY_HTTP_CLIENT_SERVICES + "." + "HEALTHCHECK"
                + PolicyEndPointProperties.PROPERTY_MANAGED_SUFFIX, "true");

        configDirSetup();

    }

    /**
     * Tear down.
     */
    @AfterClass
    public static void tearDown() {
        logger.info("-- tearDown() --");

        configDirCleanup();
    }

    @Test
    public void test() throws IOException, InterruptedException {

        HealthCheckFeature feature = new HealthCheckFeature();
        feature.afterStart(PolicyEngine.manager);

        if (!NetworkUtil.isTcpPortOpen("localhost", 7777, 5, 10000L)) {
            throw new IllegalStateException("cannot connect to port " + 7777);
        }

        Reports reports = HealthCheck.monitor.healthCheck();

        for (Report rpt : reports.getDetails()) {
            if ("HEALTHCHECK".equals(rpt.getName())) {
                assertTrue(rpt.isHealthy());
                assertEquals(200, rpt.getCode());
                assertEquals("All Alive", rpt.getMessage());
                break;
            }
        }

        feature.afterShutdown(PolicyEngine.manager);

    }


    /**
     * setup up config directory.
     */
    private static void configDirSetup() {

        File origPropsFile = new File(healthCheckPropsPath.toString());
        File backupPropsFile = new File(healthCheckPropsBackupPath.toString());
        Path configDir = Paths.get(SystemPersistence.DEFAULT_CONFIGURATION_DIR);

        try {

            if (Files.notExists(configDir)) {
                Files.createDirectories(configDir);
            }

            Files.deleteIfExists(healthCheckPropsBackupPath);
            origPropsFile.renameTo(backupPropsFile);

            FileWriter writer = new FileWriter(origPropsFile);
            httpProperties.store(writer, "Machine created healthcheck-feature Properties");

        } catch (final Exception e) {
            logger.info("Problem cleaning {}", healthCheckPropsPath, e);
        }
    }

    /**
     * cleanup up config directory.
     */
    private static void configDirCleanup() {

        File origPropsFile = new File(healthCheckPropsBackupPath.toString());
        File backupPropsFile = new File(healthCheckPropsPath.toString());

        try {
            Files.deleteIfExists(healthCheckPropsPath);
            origPropsFile.renameTo(backupPropsFile);
        } catch (final Exception e) {
            logger.info("Problem cleaning {}", healthCheckPropsPath, e);
        }
    }

}
