/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2019-2022 AT&T Intellectual Property. All rights reserved.
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

import com.google.common.base.Strings;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jetty.http.HttpStatus;
import org.onap.policy.common.endpoints.http.client.HttpClient;
import org.onap.policy.common.endpoints.http.client.HttpClientFactory;
import org.onap.policy.common.endpoints.http.client.HttpClientFactoryInstance;
import org.onap.policy.common.endpoints.http.server.HttpServletServer;
import org.onap.policy.common.endpoints.http.server.HttpServletServerFactory;
import org.onap.policy.common.endpoints.http.server.HttpServletServerFactoryInstance;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.persistence.SystemPersistenceConstants;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.drools.system.PolicyControllerConstants;
import org.onap.policy.drools.system.PolicyControllerFactory;
import org.onap.policy.drools.system.PolicyEngine;
import org.onap.policy.drools.system.PolicyEngineConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Healthcheck Monitor.
 */

@Getter
public class HealthCheckManager implements HealthCheck {

    protected static final Logger logger = LoggerFactory.getLogger(HealthCheckManager.class);

    protected static final Pattern COMMA_SPACE_PATTERN = Pattern.compile("\\s*,\\s*");

    protected static final String ENGINE_NAME = "PDP-D";
    protected static final String HEALTHCHECK_SERVER = "HEALTHCHECK";  // expected healthcheck server name in config
    protected static final String LIVENESS_SERVER = "LIVENESS";        // expected liveness server name in config

    protected static final int SUCCESS_CODE = 200;

    protected static final int BRAINLESS_CODE = 201;
    protected static final String BRAINLESS_MESSAGE = "no rules configured";

    protected static final String ENABLED_MESSAGE = "enabled";

    protected static final int DISABLED_CODE = 400;
    protected static final String DISABLED_MESSAGE = "disabled";

    protected static final int INPROG_CODE = 100;
    protected static final String INPROG_MESSAGE = "test in progress";

    protected static final int TIMEOUT_CODE = 3000;
    protected static final String TIMEOUT_MESSAGE = "healthcheck timeout";

    protected static final int UNREACHABLE_CODE = 9000;
    protected static final String UNREACHABLE_MESSAGE = "cannot reach component";

    public static final String  UNKNOWN_ENTITY = "unknown";
    public static final int UNKNOWN_ENTITY_CODE = 9010;
    public static final String UNKNOWN_ENTITY_MESSAGE = "unknown entity";

    protected static final long DEFAULT_TIMEOUT_SECONDS = 10;

    /**
     * Healthcheck Server.
     */
    protected HttpServletServer healthcheckServer;

    /**
     * Liveness Server.
     */
    protected HttpServletServer livenessServer;

    /**
     * Attached http clients.
     */
    protected List<HttpClient> clients = new ArrayList<>();

    /**
     * Attached controllers.
     */
    protected List<PolicyController> controllers = new ArrayList<>();

    /**
     * Healthcheck configuration.
     */
    @Getter(AccessLevel.NONE)
    protected Properties healthCheckProperties = null;

    @Setter
    @Getter
    protected Long timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;

    @Override
    public Reports healthCheck() {
        // get first the engine summary report for setting start time
        var engineSummary = engineHealthcheck();
        if (!isEngineAlive()) {
            logger.info("controller healthchecks ignored as engine is not active");
            return engineSummary;
        }

        CompletableFuture<Report>[] reportFutures =
            ArrayUtils.addAll(futures(getControllers()), futures(getClients()));

        return summary(engineSummary, reportFutures);
    }

    @Override
    public Reports engineHealthcheck() {
        /*
         * An engine report is special as there always should be 1
         * report at each system or component healthcheck, since it
         * is the umbrella component.  Since it does not do IO,
         * it is generated synchronously which is different from
         * HTTP clients or Policy Controllers which are asynchronous
         * with timeout safeties.
         */
        var summary = new Reports();

        var engineReport = reportOnEngine();
        summary.getDetails().add(engineReport);
        summary.setHealthy(engineReport.isHealthy());

        return summary.setEndTime();
    }

    @Override
    public Reports controllerHealthcheck() {
        if (!isEngineAlive()) {
            logger.info("controller healthchecks ignored as engine is not active");
            return engineHealthcheck();
        }

        CompletableFuture<Report>[] reportFutures = futures(getControllers());
        return log(summary(engineHealthcheck(), reportFutures));
    }

    @Override
    public Reports controllerHealthcheck(@NonNull PolicyController controller) {
        /*
         * allow individual healthchecks without consulting engine state,
         * it could be useful for troubleshooting.
         */
        CompletableFuture<Report>[] reportFutures = futures(List.of(controller));
        return log(summary(engineHealthcheck(), reportFutures));
    }

    @Override
    public Reports clientHealthcheck() {
        if (!isEngineAlive()) {
            logger.info("client healthchecks ignored as engine is not active");
            return engineHealthcheck();
        }

        CompletableFuture<Report>[] reportFutures = futures(getClients());
        return summary(engineHealthcheck(), reportFutures);
    }

    @Override
    public Reports clientHealthcheck(@NonNull HttpClient client) {
        /*
         * allow individual healthchecks without consulting engine state,
         * it could be useful for troubleshooting.
         */
        CompletableFuture<Report>[] reportFutures = futures(List.of(client));
        return summary(engineHealthcheck(), reportFutures);
    }

    protected Reports summary(@NonNull Reports summary, @NonNull CompletableFuture<Report>[] futures) {
        CompletableFuture.allOf(futures).join();

        Arrays.stream(futures)
                .map(CompletableFuture::join)
                .forEach(summary.getDetails()::add);

        summary.setHealthy(summary.getDetails()
                .stream()
                .map(this::timeout)
                .map(Report::isHealthy)
                .reduce(true, Boolean::logicalAnd));

        return summary.setEndTime();
    }

    protected Report timeout(Report report) {
        if (report.getCode() == INPROG_CODE && INPROG_MESSAGE.equals(report.getMessage())) {
            report.setHealthy(false);
            report.setCode(TIMEOUT_CODE);
            report.setMessage(TIMEOUT_MESSAGE);
        }

        return report;
    }

    @SuppressWarnings("unchecked")
    protected <T> CompletableFuture<Report>[] futures(List<T> entities) {
        return entities.stream()
                .map(this::supplier)
                .toArray(CompletableFuture[]::new);
    }

    protected Report reportOnEngine() {
        var report = new Report();

        report.setName(ENGINE_NAME);
        report.setUrl("engine");
        report.setHealthy(isEngineAlive());

        if (isEngineAlive()) {
            report.setCode(SUCCESS_CODE);
            report.setMessage(ENABLED_MESSAGE);
        } else {
            report.setCode(DISABLED_CODE);
            report.setMessage(DISABLED_MESSAGE);
        }

        return report.setEndTime();
    }

    protected Report reportOn(@NonNull PolicyController controller, @NonNull Report report) {
        report.setName(controller.getName());

        if (!controller.isAlive()) {
            report.setUrl(controller.getName());
            report.setCode(DISABLED_CODE);
            report.setMessage(DISABLED_MESSAGE);
            report.setHealthy(false);
            return report.setEndTime();
        }

        DroolsController drools = controller.getDrools();
        report.setUrl(getControllerCoordinates(drools));

        if (!drools.isBrained()) {
            report.setCode(BRAINLESS_CODE);
            report.setMessage(BRAINLESS_MESSAGE);
            report.setHealthy(true);
            return report.setEndTime();
        }

        /*
         * potentially blocking drools application operation
         */

        return reportOn(controller.getDrools(), report).setEndTime();
    }

    private Report reportOn(@NonNull DroolsController drools, @NonNull Report report) {
        if (!drools.isAlive()) {
            report.setCode(DISABLED_CODE);
            report.setMessage(DISABLED_MESSAGE);
            return report;
        }

        /*
         * The code below will block in unresponsive applications.
         */
        long factCount = 0;
        StringBuilder message = new StringBuilder();
        for (String sessionName: drools.getSessionNames()) {
            message.append("[").append(sessionName).append(":").append(getFactTypes(drools, sessionName)).append("]");
            factCount += getFactCount(drools, sessionName);
        }

        /* success */

        report.setHealthy(true);
        report.setCode(factCount);
        report.setMessage("" + message);

        return report;
    }

    protected Report reportOn(@NonNull HttpClient client, @NonNull Report report) {
        report.setName(client.getName());
        report.setUrl(client.getBaseUrl());

        try {
            Response response = client.get();
            report.setHealthy(response.getStatus() == HttpStatus.OK_200);
            report.setCode(response.getStatus());
            report.setMessage(response.getStatusInfo().getReasonPhrase());
        } catch (Exception e) {
            report.setHealthy(false);
            report.setCode(UNREACHABLE_CODE);
            report.setMessage(UNREACHABLE_MESSAGE);
            logger.info("{}: cannot contact http-client {}", this, client.getName(), e);
        }

        return report.setEndTime();
    }

    private <T> Supplier<Report> createSupplier(@NonNull T entity, @NonNull Report report) {
        if (entity instanceof PolicyController) {
            return () -> reportOn((PolicyController) entity, report);
        } else if (entity instanceof HttpClient) {
            return () -> reportOn((HttpClient) entity, report);
        } else {
            return () -> reportOnUnknown(entity, report);
        }
    }

    private Report reportOnUnknown(Object o, Report report) {
        report.setName(UNKNOWN_ENTITY);
        report.setCode(UNKNOWN_ENTITY_CODE);
        report.setMessage(UNKNOWN_ENTITY_MESSAGE);
        report.setHealthy(false);
        report.setUrl(o.getClass().getName());
        return report.setEndTime();
    }

    protected <T> CompletableFuture<Report> supplier(T entity) {
        var report = new Report();
        report.setHealthy(false);
        report.setCode(INPROG_CODE);
        report.setMessage(INPROG_MESSAGE);

        return CompletableFuture
                       .supplyAsync(createSupplier(entity, report))
                       .completeOnTimeout(report, getTimeoutSeconds(), TimeUnit.SECONDS)
                       .thenApply(HealthCheck.Report::setEndTime);
    }

    private String getControllerCoordinates(DroolsController drools) {
        return drools.getGroupId() + ":" + drools.getArtifactId() + ":" + drools.getVersion();
    }

    protected long getFactCount(DroolsController drools, String sessionName) {
        return drools.factCount(sessionName);
    }

    protected Map<String, Integer> getFactTypes(DroolsController drools, String sessionName) {
        return drools.factClassNames(sessionName);
    }

    @Override
    public boolean start() {
        try {
            this.healthCheckProperties = getPersistentProperties();
            Map<String, HttpServletServer> servers =
                    getServerFactory().build(healthCheckProperties).stream()
                        .collect(Collectors.toMap(HttpServletServer::getName, Function.identity()));

            this.healthcheckServer = servers.get(HEALTHCHECK_SERVER);
            this.livenessServer = servers.get(LIVENESS_SERVER);

            setTimeoutSeconds(
                    Long.valueOf(this.healthCheckProperties
                            .getProperty("liveness.controllers.timeout",
                                    "" + DEFAULT_TIMEOUT_SECONDS)));

            this.clients = getClientFactory().build(healthCheckProperties);

            return startHealthcheckServer();
        } catch (Exception e) {
            logger.warn("{}: cannot start", HEALTHCHECK_SERVER, e);
            return false;
        }
    }

    @Override
    public void open() {
        if (this.livenessServer != null) {
            startServer(this.livenessServer);
        }

        String controllerNames = this.healthCheckProperties.getProperty("liveness.controllers");
        if (Strings.isNullOrEmpty(controllerNames)) {
            logger.info("no controllers to live check");
            return;
        }

        if ("*".equals(controllerNames)) {
            // monitor all controllers
            this.controllers = getControllerFactory().inventory();
            return;
        }

        for (String controllerName : COMMA_SPACE_PATTERN.split(controllerNames)) {
            try {
                this.controllers.add(getControllerFactory().get(controllerName));
            } catch (RuntimeException rex) {
                logger.warn("cannot get controller {}", controllerName);
            }
        }
    }

    @Override
    public boolean stop() {
        if (this.healthcheckServer != null) {
            this.healthcheckServer.stop();
        }

        if (this.livenessServer != null) {
            this.livenessServer.stop();
        }

        for (HttpClient client : getClients()) {
            logger.warn("{}: cannot stop http-client", client.getName());
            client.stop();
        }

        return true;
    }

    @Override
    public void shutdown() {
        this.stop();
    }

    @Override
    public synchronized boolean isAlive() {
        return this.healthCheckProperties != null;
    }

    protected boolean startHealthcheckServer() {
        if (this.healthcheckServer == null) {
            logger.warn("no {} server found", HEALTHCHECK_SERVER);
            return false;
        }

        if (this.healthcheckServer.isAaf()) {
            this.healthcheckServer.addFilterClass(null, AafHealthCheckFilter.class.getName());
        }

        return startServer(this.healthcheckServer);
    }

    protected boolean startServer(HttpServletServer server) {
        try {
            return server.start();
        } catch (Exception e) {
            logger.warn("cannot start http-server {}", server.getName(), e);
        }
        return false;
    }

    protected boolean isTimeout(Reports reports) {
        return
            reports.getDetails()
                .stream()
                .anyMatch(report -> report.getCode() == TIMEOUT_CODE);
    }

    protected Reports log(Reports reports) {
        if (isTimeout(reports)) {
            logger.warn("Healthcheck Timeout encountered");
            Arrays.stream(ManagementFactory.getThreadMXBean()
                    .dumpAllThreads(true, true, 100))
                .forEach(threadInfo -> logger.info("Healthcheck Timeout Encountered:\n{}", threadInfo));
        }
        return reports;
    }

    // the following methods may be overridden by junit tests

    protected PolicyEngine getEngineManager() {
        return PolicyEngineConstants.getManager();
    }

    protected boolean isEngineAlive() {
        return getEngineManager().isAlive();
    }

    protected HttpServletServerFactory getServerFactory() {
        return HttpServletServerFactoryInstance.getServerFactory();
    }

    protected HttpClientFactory getClientFactory() {
        return HttpClientFactoryInstance.getClientFactory();
    }

    protected PolicyControllerFactory getControllerFactory() {
        return PolicyControllerConstants.getFactory();
    }

    protected Properties getPersistentProperties() {
        return SystemPersistenceConstants.getManager().getProperties(HealthCheckFeature.CONFIGURATION_PROPERTIES_NAME);
    }
}
