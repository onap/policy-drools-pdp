/*-
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2017-2019,2022 AT&T Intellectual Property. All rights reserved.
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.kie.api.builder.ReleaseId;
import org.mockito.AdditionalAnswers;
import org.onap.policy.common.endpoints.http.client.HttpClientFactoryInstance;
import org.onap.policy.common.endpoints.http.server.HttpServletServerFactoryInstance;
import org.onap.policy.common.utils.logging.LoggerUtils;
import org.onap.policy.common.utils.network.NetworkUtil;
import org.onap.policy.drools.healthcheck.HealthCheck.Reports;
import org.onap.policy.drools.persistence.SystemPersistenceConstants;
import org.onap.policy.drools.properties.DroolsPropertyConstants;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.drools.system.PolicyControllerConstants;
import org.onap.policy.drools.system.PolicyEngineConstants;
import org.onap.policy.drools.util.KieUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HealthCheckFeatureTest {

    private static final Logger logger = LoggerFactory.getLogger(HealthCheckFeatureTest.class);
    private static final String EXPECTED = "expected exception";

    /**
     * Set up.
     */
    @BeforeClass
    public static void setup() throws IOException {
        SystemPersistenceConstants.getManager().setConfigurationDir("target/test-classes");

        LoggerUtils.setLevel("org.onap.policy.common.endpoints", "WARN");
        LoggerUtils.setLevel("org.eclipse", "ERROR");
        LoggerUtils.setLevel("org.onap.policy.drools.healthcheck", "DEBUG");
        LoggerUtils.setLevel("ROOT", "INFO");

        ReleaseId coords = KieUtils.installArtifact(Paths.get("src/test/resources/echo.kmodule").toFile(),
            Paths.get("src/test/resources/echo.pom").toFile(),
            "src/main/resources/kbecho/org/onap/policy/drools/healthcheck/",
            List.of(Paths.get("src/test/resources/echo.drl").toFile()));

        Properties controllerProps = new Properties();
        controllerProps.put(DroolsPropertyConstants.PROPERTY_CONTROLLER_NAME, "echo");
        controllerProps.put(DroolsPropertyConstants.RULES_GROUPID, coords.getGroupId());
        controllerProps.put(DroolsPropertyConstants.RULES_ARTIFACTID, coords.getArtifactId());
        controllerProps.put(DroolsPropertyConstants.RULES_VERSION, coords.getVersion());

        PolicyController controller = PolicyControllerConstants.getFactory().build("echo", controllerProps);
        controller.start();
    }

    /**
     * Tear down.
     */
    @AfterClass
    public static void teardown() {
        PolicyControllerConstants.getFactory().destroy();
        HttpClientFactoryInstance.getClientFactory().destroy();
        HttpServletServerFactoryInstance.getServerFactory().destroy();
    }

    @Test
    public void test() throws InterruptedException {
        var manager = spy(HealthCheckManager.class);
        var feature = new HealthCheckFeatureImpl(manager);
        when(manager.isEngineAlive()).thenReturn(true);

        feature.afterStart(PolicyEngineConstants.getManager());
        feature.afterOpen(PolicyEngineConstants.getManager());

        checkOpen(7777);
        checkOpen(7776);

        var reports = healthcheck(manager);
        serverChecks(reports);
        checkReports(reports, List.of("STUCK"),
                HttpStatus.OK_200, HttpStatus.getMessage(200));
        checkReports(reports, List.of("echo"), 1, "[echo:{java.lang.String=1}]");

        /* mock controller and clients stuck */

        RestMockHealthCheck.stuck = true;   // make the server named STUCK unresponsive
        doAnswer(AdditionalAnswers
                .answersWithDelay((manager.getTimeoutSeconds() + 2) * 1000L,
                        invocationOnMock -> new HashMap<String, Integer>()))
                .when(manager).getFactTypes(any(), any());

        reports = healthcheck(manager);
        RestMockHealthCheck.stuck = false;  // unstuck the server named STUCK

        serverChecks(reports);
        checkReports(reports, List.of("STUCK"),
                HealthCheckManager.TIMEOUT_CODE, HealthCheckManager.TIMEOUT_MESSAGE);

        assertTrue(RestMockHealthCheck.WAIT * 1000 > HealthCheckManagerTest.select(reports, "STUCK",
                        HealthCheckManager.TIMEOUT_CODE, HealthCheckManager.TIMEOUT_MESSAGE)
                .get(0).getElapsedTime());

        feature.afterShutdown(PolicyEngineConstants.getManager());
    }

    private void checkReports(Reports reports, List<String> reportNames, int code, String message) {
        reportNames
                .forEach(name -> assertEquals(1,
                        HealthCheckManagerTest.select(reports, name, code, message).size()));
    }

    private Reports healthcheck(HealthCheck manager) {
        var reports = manager.healthCheck();
        logger.info("{}", reports);
        return reports;
    }

    private void checkOpen(int port) throws InterruptedException {
        if (!NetworkUtil.isTcpPortOpen("localhost", port, 5, 10000L)) {
            throw new IllegalStateException("cannot connect to port " + port);
        }
    }

    private void serverChecks(Reports reports) {
        checkReports(reports, List.of("HEALTHCHECK", "LIVENESS"),
                HttpStatus.OK_200, HttpStatus.getMessage(200));
        checkReports(reports, List.of("UNAUTH"),
                HttpStatus.UNAUTHORIZED_401, HttpStatus.getMessage(401));
        checkReports(reports, List.of(HealthCheckManager.ENGINE_NAME),
                HealthCheckManager.SUCCESS_CODE, HealthCheckManager.ENABLED_MESSAGE);
    }

    @Test
    public void testGetSequenceNumber() {
        assertEquals(1000, new HealthCheckFeature().getSequenceNumber());
    }

    @Test
    public void testAfterStart() {
        HealthCheck checker = mock(HealthCheck.class);
        HealthCheckFeature feature = new HealthCheckFeatureImpl(checker);

        // without exception
        assertFalse(feature.afterStart(null));
        verify(checker).start();
        verify(checker, never()).stop();

        // with exception
        doThrow(new IllegalStateException(EXPECTED)).when(checker).start();
        assertFalse(feature.afterStart(null));
    }

    @Test
    public void testAfterOpen() {
        HealthCheck checker = mock(HealthCheck.class);
        HealthCheckFeature feature = new HealthCheckFeatureImpl(checker);

        // without exception
        assertFalse(feature.afterOpen(null));
        verify(checker).open();
        verify(checker, never()).stop();

        // with exception
        doThrow(new IllegalStateException(EXPECTED)).when(checker).open();
        assertFalse(feature.afterOpen(null));

    }

    @Test
    public void testAfterShutdown() {
        HealthCheck checker = mock(HealthCheck.class);
        HealthCheckFeature feature = new HealthCheckFeatureImpl(checker);

        // without exception
        assertFalse(feature.afterShutdown(null));
        verify(checker).stop();
        verify(checker, never()).start();

        // with exception
        doThrow(new IllegalStateException(EXPECTED)).when(checker).stop();
        assertFalse(feature.afterShutdown(null));
    }

    /**
     * Feature that returns a particular monitor.
     */
    private static class HealthCheckFeatureImpl extends HealthCheckFeature {
        private final HealthCheck checker;

        public HealthCheckFeatureImpl(HealthCheck checker) {
            this.checker = checker;
        }

        @Override
        public HealthCheck getManager() {
            return checker;
        }

    }
}
