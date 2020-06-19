/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2019-2020 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.system;

import static org.onap.policy.drools.system.PolicyEngineConstants.TELEMETRY_SERVER_DEFAULT_HOST;
import static org.onap.policy.drools.system.PolicyEngineConstants.TELEMETRY_SERVER_DEFAULT_NAME;
import static org.onap.policy.drools.system.PolicyEngineConstants.TELEMETRY_SERVER_DEFAULT_PORT;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.onap.policy.common.endpoints.event.comm.Topic;
import org.onap.policy.common.endpoints.event.comm.Topic.CommInfrastructure;
import org.onap.policy.common.endpoints.event.comm.TopicEndpoint;
import org.onap.policy.common.endpoints.event.comm.TopicEndpointManager;
import org.onap.policy.common.endpoints.event.comm.TopicSink;
import org.onap.policy.common.endpoints.event.comm.TopicSource;
import org.onap.policy.common.endpoints.http.client.HttpClient;
import org.onap.policy.common.endpoints.http.client.HttpClientFactory;
import org.onap.policy.common.endpoints.http.client.HttpClientFactoryInstance;
import org.onap.policy.common.endpoints.http.server.HttpServletServer;
import org.onap.policy.common.endpoints.http.server.HttpServletServerFactory;
import org.onap.policy.common.endpoints.http.server.HttpServletServerFactoryInstance;
import org.onap.policy.common.endpoints.properties.PolicyEndPointProperties;
import org.onap.policy.common.gson.annotation.GsonJsonIgnore;
import org.onap.policy.common.gson.annotation.GsonJsonProperty;
import org.onap.policy.common.utils.services.FeatureApiUtils;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.controller.DroolsControllerConstants;
import org.onap.policy.drools.core.PolicyContainer;
import org.onap.policy.drools.core.jmx.PdpJmxListener;
import org.onap.policy.drools.core.lock.Lock;
import org.onap.policy.drools.core.lock.LockCallback;
import org.onap.policy.drools.core.lock.PolicyResourceLockManager;
import org.onap.policy.drools.features.PolicyControllerFeatureApi;
import org.onap.policy.drools.features.PolicyControllerFeatureApiConstants;
import org.onap.policy.drools.features.PolicyEngineFeatureApi;
import org.onap.policy.drools.features.PolicyEngineFeatureApiConstants;
import org.onap.policy.drools.persistence.SystemPersistence;
import org.onap.policy.drools.persistence.SystemPersistenceConstants;
import org.onap.policy.drools.policies.DomainMaker;
import org.onap.policy.drools.properties.DroolsPropertyConstants;
import org.onap.policy.drools.protocol.coders.EventProtocolCoder;
import org.onap.policy.drools.protocol.coders.EventProtocolCoderConstants;
import org.onap.policy.drools.protocol.configuration.ControllerConfiguration;
import org.onap.policy.drools.protocol.configuration.PdpdConfiguration;
import org.onap.policy.drools.server.restful.RestManager;
import org.onap.policy.drools.server.restful.aaf.AafTelemetryAuthFilter;
import org.onap.policy.drools.system.internal.SimpleLockManager;
import org.onap.policy.drools.utils.PropertyUtil;
import org.onap.policy.drools.utils.logging.LoggerUtil;
import org.onap.policy.drools.utils.logging.MdcTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Policy Engine Manager Implementation.
 */
class PolicyEngineManager implements PolicyEngine {
    /**
     * String literals.
     */
    private static final String INVALID_TOPIC_MSG = "Invalid Topic";
    private static final String INVALID_EVENT_MSG = "Invalid Event";

    private static final String ENGINE_STOPPED_MSG = "Policy Engine is stopped";
    private static final String ENGINE_LOCKED_MSG = "Policy Engine is locked";

    public static final String EXECUTOR_THREAD_PROP = "executor.threads";
    protected static final int DEFAULT_EXECUTOR_THREADS = 5;

    /**
     * logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(PolicyEngineManager.class);

    /**
     * Is the Policy Engine running.
     */
    @Getter
    private volatile boolean alive = false;

    /**
     * Is the engine locked.
     */
    @Getter
    private volatile boolean locked = false;

    /**
     * Properties used to initialize the engine.
     */
    private Properties properties;

    /**
     * Environment Properties.
     */
    private final Properties environment = new Properties();

    /**
     * Policy Engine Sources.
     */
    @Getter
    private List<TopicSource> sources = new ArrayList<>();

    /**
     * Policy Engine Sinks.
     */
    @Getter
    private List<TopicSink> sinks = new ArrayList<>();

    /**
     * Policy Engine HTTP Servers.
     */
    @Getter
    private List<HttpServletServer> httpServers = new ArrayList<>();

    /**
     * Thread pool used to execute background tasks.
     */
    private ScheduledExecutorService executorService = null;

    /**
     * Lock manager used to create locks.
     */
    @Getter(AccessLevel.PROTECTED)
    private PolicyResourceLockManager lockManager = null;

    private DomainMaker domainMaker = new DomainMaker();

    /**
     * gson parser to decode configuration requests.
     */
    private final Gson decoder = new GsonBuilder().disableHtmlEscaping().create();


    @Override
    public synchronized void boot(String[] cliArgs) {

        if (FeatureApiUtils.apply(getEngineProviders(),
            feature -> feature.beforeBoot(this, cliArgs),
            (feature, ex) -> logger.error("{}: feature {} before-boot failure because of {}", this,
                            feature.getClass().getName(), ex.getMessage(), ex))) {
            return;
        }

        try {
            globalInitContainer(cliArgs);
        } catch (final Exception e) {
            logger.error("{}: cannot init policy-container because of {}", this, e.getMessage(), e);
        }

        FeatureApiUtils.apply(getEngineProviders(),
            feature -> feature.afterBoot(this),
            (feature, ex) -> logger.error("{}: feature {} after-boot failure because of {}", this,
                            feature.getClass().getName(), ex.getMessage(), ex));
    }

    @Override
    public synchronized void setEnvironment(Properties properties) {
        this.environment.putAll(PropertyUtil.getInterpolatedProperties(properties));
    }

    @JsonIgnore
    @GsonJsonIgnore
    @Override
    public synchronized Properties getEnvironment() {
        return this.environment;
    }

    @JsonIgnore
    @GsonJsonIgnore
    @Override
    public DomainMaker getDomainMaker() {
        return this.domainMaker;
    }

    @Override
    public synchronized String getEnvironmentProperty(String envKey) {
        String value = this.environment.getProperty(envKey);
        if (value == null) {
            value = System.getProperty(envKey);
            if (value == null) {
                value = System.getenv(envKey);
            }
        }
        return value;
    }

    @Override
    public synchronized String setEnvironmentProperty(String envKey, String envValue) {
        return (String) this.environment.setProperty(envKey, envValue);
    }

    @Override
    public final Properties defaultTelemetryConfig() {
        final Properties defaultConfig = new Properties();

        defaultConfig.put(PolicyEndPointProperties.PROPERTY_HTTP_SERVER_SERVICES, "TELEMETRY");
        defaultConfig.put(PolicyEndPointProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + TELEMETRY_SERVER_DEFAULT_NAME
                + PolicyEndPointProperties.PROPERTY_HTTP_HOST_SUFFIX, TELEMETRY_SERVER_DEFAULT_HOST);
        defaultConfig.put(
                PolicyEndPointProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + TELEMETRY_SERVER_DEFAULT_NAME
                        + PolicyEndPointProperties.PROPERTY_HTTP_PORT_SUFFIX,
                "" + TELEMETRY_SERVER_DEFAULT_PORT);
        defaultConfig.put(
                PolicyEndPointProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + TELEMETRY_SERVER_DEFAULT_NAME
                        + PolicyEndPointProperties.PROPERTY_HTTP_REST_PACKAGES_SUFFIX,
                RestManager.class.getPackage().getName());
        defaultConfig.put(PolicyEndPointProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + TELEMETRY_SERVER_DEFAULT_NAME
                + PolicyEndPointProperties.PROPERTY_HTTP_SWAGGER_SUFFIX, "" + Boolean.TRUE);
        defaultConfig.put(PolicyEndPointProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + TELEMETRY_SERVER_DEFAULT_NAME
                + PolicyEndPointProperties.PROPERTY_MANAGED_SUFFIX, "" + Boolean.FALSE);

        return defaultConfig;
    }

    @Override
    @JsonIgnore
    @GsonJsonIgnore
    public ScheduledExecutorService getExecutorService() {
        return executorService;
    }

    private ScheduledExecutorService makeExecutorService(Properties properties) {
        int nthreads = DEFAULT_EXECUTOR_THREADS;
        try {
            nthreads = Integer.valueOf(
                            properties.getProperty(EXECUTOR_THREAD_PROP, String.valueOf(DEFAULT_EXECUTOR_THREADS)));

        } catch (NumberFormatException e) {
            logger.error("invalid number for " + EXECUTOR_THREAD_PROP + " property", e);
        }

        return makeScheduledExecutor(nthreads);
    }

    private void createLockManager(Properties properties) {
        for (PolicyEngineFeatureApi feature : getEngineProviders()) {
            try {
                this.lockManager = feature.beforeCreateLockManager(this, properties);
                if (this.lockManager != null) {
                    return;
                }
            } catch (RuntimeException e) {
                logger.error("{}: feature {} before-create-lock-manager failure because of {}", this,
                                feature.getClass().getName(), e.getMessage(), e);
            }
        }

        try {
            this.lockManager = new SimpleLockManager(this, properties);
        } catch (RuntimeException e) {
            logger.error("{}: cannot create simple lock manager because of {}", this, e.getMessage(), e);
            this.lockManager = new SimpleLockManager(this, new Properties());
        }

        /* policy-engine dispatch post operation hook */
        FeatureApiUtils.apply(getEngineProviders(),
            feature -> feature.afterCreateLockManager(this, properties, this.lockManager),
            (feature, ex) -> logger.error("{}: feature {} after-create-lock-manager failure because of {}",
                            this, feature.getClass().getName(), ex.getMessage(), ex));
    }

    @Override
    public synchronized void configure(Properties properties) {

        if (properties == null) {
            logger.warn("No properties provided");
            throw new IllegalArgumentException("No properties provided");
        }

        /* policy-engine dispatch pre configure hook */
        if (FeatureApiUtils.apply(getEngineProviders(),
            feature -> feature.beforeConfigure(this, properties),
            (feature, ex) -> logger.error("{}: feature {} before-configure failure because of {}", this,
                            feature.getClass().getName(), ex.getMessage(), ex))) {
            return;
        }

        this.properties = properties;

        try {
            this.sources = getTopicEndpointManager().addTopicSources(properties);
            for (final TopicSource source : this.sources) {
                source.register(this);
            }
        } catch (final Exception e) {
            logger.error("{}: add-sources failed", this, e);
        }

        try {
            this.sinks = getTopicEndpointManager().addTopicSinks(properties);
        } catch (final IllegalArgumentException e) {
            logger.error("{}: add-sinks failed", this, e);
        }

        try {
            this.httpServers = getServletFactory().build(properties);
            for (HttpServletServer server : this.httpServers) {
                if (server.isAaf()) {
                    server.addFilterClass(null, AafTelemetryAuthFilter.class.getName());
                }
            }
        } catch (final IllegalArgumentException e) {
            logger.error("{}: add-http-servers failed", this, e);
        }

        executorService = makeExecutorService(properties);

        createLockManager(properties);

        /* policy-engine dispatch post configure hook */
        FeatureApiUtils.apply(getEngineProviders(),
            feature -> feature.afterConfigure(this),
            (feature, ex) -> logger.error("{}: feature {} after-configure failure because of {}", this,
                            feature.getClass().getName(), ex.getMessage(), ex));
    }

    @Override
    public boolean configure(PdpdConfiguration config) {

        if (config == null) {
            throw new IllegalArgumentException("No configuration provided");
        }

        final String entity = config.getEntity();

        MdcTransaction mdcTrans = MdcTransaction.newTransaction(config.getRequestId(), "brmsgw");
        if (this.getSources().size() == 1) {
            Topic topic = this.getSources().get(0);
            mdcTrans.setServiceName(topic.getTopic()).setRemoteHost(topic.getServers().toString())
                    .setTargetEntity(config.getEntity());
        }


        if (PdpdConfiguration.CONFIG_ENTITY_CONTROLLER.equals(entity)) {
            boolean success = controllerConfig(config);
            mdcTrans.resetSubTransaction().setStatusCode(success).transaction();
            return success;

        } else {
            final String msg = "Configuration Entity is not supported: " + entity;
            mdcTrans.resetSubTransaction().setStatusCode(false).setResponseDescription(msg).flush();
            logger.warn(LoggerUtil.TRANSACTION_LOG_MARKER_NAME, msg);
            throw new IllegalArgumentException(msg);
        }
    }

    @Override
    public synchronized PolicyController createPolicyController(String name, Properties properties) {

        String tempName = name;
        // check if a PROPERTY_CONTROLLER_NAME property is present
        // if so, override the given name

        final String propertyControllerName = properties.getProperty(DroolsPropertyConstants.PROPERTY_CONTROLLER_NAME);
        if (propertyControllerName != null && !propertyControllerName.isEmpty()) {
            if (!propertyControllerName.equals(tempName)) {
                throw new IllegalStateException("Proposed name (" + tempName + ") and properties name ("
                        + propertyControllerName + ") don't match");
            }
            tempName = propertyControllerName;
        }

        PolicyController controller;
        for (final PolicyControllerFeatureApi controllerFeature : getControllerProviders()) {
            try {
                controller = controllerFeature.beforeCreate(tempName, properties);
                if (controller != null) {
                    return controller;
                }
            } catch (final Exception e) {
                logger.error("{}: feature {} before-controller-create failure because of {}", this,
                        controllerFeature.getClass().getName(), e.getMessage(), e);
            }
        }

        controller = getControllerFactory().build(tempName, properties);
        if (this.isLocked()) {
            controller.lock();
        }

        // feature hook
        PolicyController controller2 = controller;
        FeatureApiUtils.apply(getControllerProviders(),
            feature -> feature.afterCreate(controller2),
            (feature, ex) -> logger.error("{}: feature {} after-controller-create failure because of {}",
                            this, feature.getClass().getName(), ex.getMessage(), ex));

        return controller;
    }


    @Override
    public List<PolicyController> updatePolicyControllers(List<ControllerConfiguration> configControllers) {

        final List<PolicyController> policyControllers = new ArrayList<>();
        if (configControllers == null || configControllers.isEmpty()) {
            logger.info("No controller configuration provided: {}", configControllers);
            return policyControllers;
        }

        for (final ControllerConfiguration configController : configControllers) {
            MdcTransaction mdcTrans = MdcTransaction.newSubTransaction(null).setTargetEntity(configController.getName())
                    .setTargetServiceName(configController.getOperation())
                    .setTargetVirtualEntity("" + configController.getDrools());
            try {
                final PolicyController policyController = this.updatePolicyController(configController);
                policyControllers.add(policyController);
                mdcTrans.setStatusCode(true).transaction();
            } catch (final Exception e) {
                mdcTrans.setStatusCode(false).setResponseCode(e.getClass().getName())
                        .setResponseDescription(e.getMessage()).flush();
                logger.error(LoggerUtil.TRANSACTION_LOG_MARKER_NAME,
                        "{}: cannot update-policy-controllers because of {}", this, e.getMessage(), e);
            }
        }

        return policyControllers;
    }

    @Override
    public synchronized PolicyController updatePolicyController(ControllerConfiguration configController) {

        if (configController == null) {
            throw new IllegalArgumentException("No controller configuration has been provided");
        }

        final String controllerName = configController.getName();
        if (controllerName == null || controllerName.isEmpty()) {
            logger.warn("controller-name  must be provided");
            throw new IllegalArgumentException("No controller configuration has been provided");
        }

        try {
            final String operation = configController.getOperation();
            if (operation == null || operation.isEmpty()) {
                logger.warn("operation must be provided");
                throw new IllegalArgumentException("operation must be provided");
            }

            PolicyController policyController = getController(controllerName);
            if (policyController == null) {
                policyController = findController(controllerName, operation);

                /* fall through to do brain update operation */
            }

            updateController(controllerName, policyController, operation, configController);

            return policyController;
        } catch (final Exception e) {
            logger.error("{}: cannot update-policy-controller", this);
            throw e;
        } catch (final LinkageError e) {
            logger.error("{}: cannot update-policy-controllers (rules)", this);
            throw new IllegalStateException(e);
        }
    }

    private PolicyController getController(final String controllerName) {
        PolicyController policyController = null;
        try {
            policyController = getControllerFactory().get(controllerName);
        } catch (final IllegalArgumentException e) {
            // not found
            logger.warn("Policy Controller {} not found", controllerName, e);
        }
        return policyController;
    }

    private PolicyController findController(final String controllerName, final String operation) {
        if (operation.equalsIgnoreCase(ControllerConfiguration.CONFIG_CONTROLLER_OPERATION_LOCK)
                || operation.equalsIgnoreCase(ControllerConfiguration.CONFIG_CONTROLLER_OPERATION_UNLOCK)) {
            throw new IllegalArgumentException(controllerName + " is not available for operation " + operation);
        }

        /* Recovery case */

        logger.warn("controller {} does not exist. Attempting recovery from disk", controllerName);

        final Properties controllerProperties =
                getPersistenceManager().getControllerProperties(controllerName);

        /*
         * returned properties cannot be null (per implementation) assert (properties !=
         * null)
         */

        if (controllerProperties == null) {
            throw new IllegalArgumentException(controllerName + " is invalid");
        }

        logger.warn("controller being recovered. {} Reset controller's bad maven coordinates to brainless",
                controllerName);

        /*
         * try to bring up bad controller in brainless mode, after having it
         * working, apply the new create/update operation.
         */
        controllerProperties.setProperty(DroolsPropertyConstants.RULES_GROUPID,
                        DroolsControllerConstants.NO_GROUP_ID);
        controllerProperties.setProperty(DroolsPropertyConstants.RULES_ARTIFACTID,
                        DroolsControllerConstants.NO_ARTIFACT_ID);
        controllerProperties.setProperty(DroolsPropertyConstants.RULES_VERSION,
                        DroolsControllerConstants.NO_VERSION);

        return getPolicyEngine().createPolicyController(controllerName, controllerProperties);
    }

    private void updateController(final String controllerName, PolicyController policyController,
                    final String operation, ControllerConfiguration configController) {
        switch (operation) {
            case ControllerConfiguration.CONFIG_CONTROLLER_OPERATION_CREATE:
                getControllerFactory().patch(policyController, configController.getDrools());
                break;
            case ControllerConfiguration.CONFIG_CONTROLLER_OPERATION_UPDATE:
                policyController.unlock();
                getControllerFactory().patch(policyController, configController.getDrools());
                break;
            case ControllerConfiguration.CONFIG_CONTROLLER_OPERATION_LOCK:
                policyController.lock();
                break;
            case ControllerConfiguration.CONFIG_CONTROLLER_OPERATION_UNLOCK:
                policyController.unlock();
                break;
            default:
                final String msg = "Controller Operation Configuration is not supported: " + operation + " for "
                        + controllerName;
                logger.warn(msg);
                throw new IllegalArgumentException(msg);
        }
    }

    @Override
    public synchronized boolean start() {

        /* policy-engine dispatch pre start hook */
        if (FeatureApiUtils.apply(getEngineProviders(),
            feature -> feature.beforeStart(this),
            (feature, ex) -> logger.error("{}: feature {} before-start failure because of {}", this,
                            feature.getClass().getName(), ex.getMessage(), ex))) {
            return true;
        }

        if (this.locked) {
            throw new IllegalStateException(ENGINE_LOCKED_MSG);
        }

        this.alive = true;

        AtomicReference<Boolean> success = new AtomicReference<>(true);

        try {
            success.compareAndSet(true, this.lockManager.start());
        } catch (final RuntimeException e) {
            logger.warn("{}: cannot start lock manager because of {}", this, e.getMessage(), e);
            success.set(false);
        }

        /* Start managed and unmanaged http servers */

        attempt(success,
            Stream.concat(getServletFactory().inventory().stream(), this.httpServers.stream())
                .collect(Collectors.toList()),
            httpServer -> httpServer.waitedStart(10 * 1000L),
            (item, ex) -> logger.error("{}: cannot start http-server {} because of {}", this, item,
                ex.getMessage(), ex));

        /* Start managed Http Clients */

        attempt(success, getHttpClientFactory().inventory(),
            HttpClient::start,
            (item, ex) -> logger.error("{}: cannot start http-client {} because of {}",
                this, item, ex.getMessage(), ex));

        /* Start Policy Controllers */

        attempt(success, getControllerFactory().inventory(),
            PolicyController::start,
            (item, ex) -> {
                logger.error("{}: cannot start policy-controller {} because of {}", this, item,
                                ex.getMessage(), ex);
                success.set(false);
            });

        /* Start managed Topic Endpoints */

        try {
            if (!getTopicEndpointManager().start()) {
                success.set(false);
            }
        } catch (final IllegalStateException e) {
            logger.warn("{}: Topic Endpoint Manager is in an invalid state because of {}", this, e.getMessage(), e);
        }

        // Start the JMX listener

        startPdpJmxListener();

        /* policy-engine dispatch after start hook */
        FeatureApiUtils.apply(getEngineProviders(),
            feature -> feature.afterStart(this),
            (feature, ex) -> logger.error("{}: feature {} after-start failure because of {}", this,
                            feature.getClass().getName(), ex.getMessage(), ex));

        return success.get();
    }

    @Override
    public synchronized boolean open() {

        /* pre-open hook */
        if (FeatureApiUtils.apply(getEngineProviders(),
            feature -> feature.beforeOpen(this),
            (feature, ex) -> logger.error("{}: feature {} before-open failure because of {}", this,
                feature.getClass().getName(), ex.getMessage(), ex))) {
            return true;
        }

        if (this.locked) {
            throw new IllegalStateException(ENGINE_LOCKED_MSG);
        }

        if (!this.alive) {
            throw new IllegalStateException(ENGINE_STOPPED_MSG);
        }

        AtomicReference<Boolean> success = new AtomicReference<>(true);

        /* Open the unmanaged topics to external components for configuration purposes */

        attempt(success, this.sources,
            TopicSource::start,
            (item, ex) -> logger.error("{}: cannot start topic-source {} because of {}",
                this, item, ex.getMessage(), ex));

        attempt(success, this.sinks,
            TopicSink::start,
            (item, ex) -> logger.error("{}: cannot start topic-sink {} because of {}",
                this, item, ex.getMessage(), ex));

        /* post-open hook */
        FeatureApiUtils.apply(getEngineProviders(),
            feature -> feature.afterOpen(this),
            (feature, ex) -> logger.error("{}: feature {} after-open failure because of {}", this,
                feature.getClass().getName(), ex.getMessage(), ex));

        return success.get();
    }

    @FunctionalInterface
    private static interface PredicateWithEx<T> {
        boolean test(T value) throws InterruptedException;
    }

    @Override
    public synchronized boolean stop() {

        /* policy-engine dispatch pre stop hook */
        if (FeatureApiUtils.apply(getEngineProviders(),
            feature -> feature.beforeStop(this),
            (feature, ex) -> logger.error("{}: feature {} before-stop failure because of {}", this,
                            feature.getClass().getName(), ex.getMessage(), ex))) {
            return true;
        }

        /* stop regardless of the lock state */

        if (!this.alive) {
            return true;
        }

        this.alive = false;

        AtomicReference<Boolean> success = new AtomicReference<>(true);

        attempt(success, getControllerFactory().inventory(),
            PolicyController::stop,
            (item, ex) -> {
                logger.error("{}: cannot stop policy-controller {} because of {}", this, item,
                                ex.getMessage(), ex);
                success.set(false);
            });

        /* Stop Policy Engine owned (unmanaged) sources */
        attempt(success, this.sources,
            TopicSource::stop,
            (item, ex) -> logger.error("{}: cannot stop topic-source {} because of {}", this, item,
                            ex.getMessage(), ex));

        /* Stop Policy Engine owned (unmanaged) sinks */
        attempt(success, this.sinks,
            TopicSink::stop,
            (item, ex) -> logger.error("{}: cannot stop topic-sink {} because of {}", this, item,
                            ex.getMessage(), ex));

        /* stop all managed topics sources and sinks */
        if (!getTopicEndpointManager().stop()) {
            success.set(false);
        }

        /* stop all managed and unmanaged http servers */
        attempt(success,
            Stream.concat(getServletFactory().inventory().stream(), this.httpServers.stream())
                    .collect(Collectors.toList()),
            HttpServletServer::stop,
            (item, ex) -> logger.error("{}: cannot stop http-server {} because of {}", this, item,
                ex.getMessage(), ex));

        /* stop all managed http clients */
        attempt(success,
            getHttpClientFactory().inventory(),
            HttpClient::stop,
            (item, ex) -> logger.error("{}: cannot stop http-client {} because of {}", this, item,
                ex.getMessage(), ex));

        try {
            success.compareAndSet(true, this.lockManager.stop());
        } catch (final RuntimeException e) {
            logger.warn("{}: cannot stop lock manager because of {}", this, e.getMessage(), e);
            success.set(false);
        }

        // stop JMX?

        /* policy-engine dispatch post stop hook */
        FeatureApiUtils.apply(getEngineProviders(),
            feature -> feature.afterStop(this),
            (feature, ex) -> logger.error("{}: feature {} after-stop failure because of {}", this,
                            feature.getClass().getName(), ex.getMessage(), ex));

        return success.get();
    }

    @Override
    public synchronized void shutdown() {

        /*
         * shutdown activity even when underlying subcomponents (features, controllers, topics, etc
         * ..) are stuck
         */

        Thread exitThread = makeShutdownThread();
        exitThread.start();

        /* policy-engine dispatch pre shutdown hook */
        if (FeatureApiUtils.apply(getEngineProviders(),
            feature -> feature.beforeShutdown(this),
            (feature, ex) -> logger.error("{}: feature {} before-shutdown failure because of {}", this,
                            feature.getClass().getName(), ex.getMessage(), ex))) {
            return;
        }

        this.alive = false;

        /* Shutdown Policy Engine owned (unmanaged) sources */
        applyAll(this.sources,
            TopicSource::shutdown,
            (item, ex) -> logger.error("{}: cannot shutdown topic-source {} because of {}", this, item,
                            ex.getMessage(), ex));

        /* Shutdown Policy Engine owned (unmanaged) sinks */
        applyAll(this.sinks,
            TopicSink::shutdown,
            (item, ex) -> logger.error("{}: cannot shutdown topic-sink {} because of {}", this, item,
                            ex.getMessage(), ex));

        /* Shutdown managed resources */
        getControllerFactory().shutdown();
        getTopicEndpointManager().shutdown();
        getServletFactory().destroy();
        getHttpClientFactory().destroy();

        try {
            this.lockManager.shutdown();
        } catch (final RuntimeException e) {
            logger.warn("{}: cannot shutdown lock manager because of {}", this, e.getMessage(), e);
        }

        executorService.shutdownNow();

        // Stop the JMX listener

        stopPdpJmxListener();

        /* policy-engine dispatch post shutdown hook */
        FeatureApiUtils.apply(getEngineProviders(),
            feature -> feature.afterShutdown(this),
            (feature, ex) -> logger.error("{}: feature {} after-shutdown failure because of {}", this,
                            feature.getClass().getName(), ex.getMessage(), ex));

        exitThread.interrupt();
        logger.info("{}: normal termination", this);
    }

    private <T> void attempt(AtomicReference<Boolean> success, List<T> items, PredicateWithEx<T> pred,
                    BiConsumer<T, Exception> handleEx) {

        for (T item : items) {
            try {
                if (!pred.test(item)) {
                    success.set(false);
                }

            } catch (InterruptedException ex) {
                handleEx.accept(item, ex);
                Thread.currentThread().interrupt();

            } catch (RuntimeException ex) {
                handleEx.accept(item, ex);
            }
        }
    }

    private <T> void applyAll(List<T> items, Consumer<T> function,
                    BiConsumer<T, Exception> handleEx) {

        for (T item : items) {
            try {
                function.accept(item);

            } catch (RuntimeException ex) {
                handleEx.accept(item, ex);
            }
        }
    }

    /**
     * Thread that shuts down http servers.
     */
    protected class ShutdownThread extends Thread {
        private static final long SHUTDOWN_MAX_GRACE_TIME = 30000L;

        @Override
        public void run() {
            try {
                doSleep(SHUTDOWN_MAX_GRACE_TIME);
                logger.warn("{}: abnormal termination - shutdown graceful time period expiration",
                        PolicyEngineManager.this);
            } catch (final InterruptedException e) {
                synchronized (PolicyEngineManager.this) {
                    /* courtesy to shutdown() to allow it to return */
                    Thread.currentThread().interrupt();
                }
                logger.info("{}: finishing a graceful shutdown ", PolicyEngineManager.this, e);
            } finally {
                /*
                 * shut down the Policy Engine owned http servers as the very last thing
                 */
                applyAll(PolicyEngineManager.this.getHttpServers(),
                    HttpServletServer::shutdown,
                    (item, ex) -> logger.error("{}: cannot shutdown http-server {} because of {}", this, item,
                                    ex.getMessage(), ex));

                logger.info("{}: exit", PolicyEngineManager.this);
                doExit(0);
            }
        }

        // these may be overridden by junit tests

        protected void doSleep(long sleepMs) throws InterruptedException {
            Thread.sleep(sleepMs);
        }

        protected void doExit(int code) {
            System.exit(code);
        }
    }

    @Override
    public synchronized boolean lock() {

        /* policy-engine dispatch pre lock hook */
        if (FeatureApiUtils.apply(getEngineProviders(),
            feature -> feature.beforeLock(this),
            (feature, ex) -> logger.error("{}: feature {} before-lock failure because of {}", this,
                            feature.getClass().getName(), ex.getMessage(), ex))) {
            return true;
        }

        if (this.locked) {
            return true;
        }

        this.locked = true;

        boolean success = true;
        final List<PolicyController> controllers = getControllerFactory().inventory();
        for (final PolicyController controller : controllers) {
            try {
                success = controller.lock() && success;
            } catch (final Exception e) {
                logger.error("{}: cannot lock policy-controller {} because of {}", this, controller, e.getMessage(), e);
                success = false;
            }
        }

        success = getTopicEndpointManager().lock() && success;

        try {
            success = (this.lockManager == null || this.lockManager.lock()) && success;
        } catch (final RuntimeException e) {
            logger.warn("{}: cannot lock() lock manager because of {}", this, e.getMessage(), e);
            success = false;
        }

        /* policy-engine dispatch post lock hook */
        FeatureApiUtils.apply(getEngineProviders(),
            feature -> feature.afterLock(this),
            (feature, ex) -> logger.error("{}: feature {} after-lock failure because of {}", this,
                            feature.getClass().getName(), ex.getMessage(), ex));

        return success;
    }

    @Override
    public synchronized boolean unlock() {

        /* policy-engine dispatch pre unlock hook */
        if (FeatureApiUtils.apply(getEngineProviders(),
            feature -> feature.beforeUnlock(this),
            (feature, ex) -> logger.error("{}: feature {} before-unlock failure because of {}", this,
                            feature.getClass().getName(), ex.getMessage(), ex))) {
            return true;
        }

        if (!this.locked) {
            return true;
        }

        this.locked = false;

        boolean success = true;

        try {
            success = (this.lockManager == null || this.lockManager.unlock()) && success;
        } catch (final RuntimeException e) {
            logger.warn("{}: cannot unlock() lock manager because of {}", this, e.getMessage(), e);
            success = false;
        }

        final List<PolicyController> controllers = getControllerFactory().inventory();
        for (final PolicyController controller : controllers) {
            try {
                success = controller.unlock() && success;
            } catch (final Exception e) {
                logger.error("{}: cannot unlock policy-controller {} because of {}", this, controller, e.getMessage(),
                        e);
                success = false;
            }
        }

        success = getTopicEndpointManager().unlock() && success;

        /* policy-engine dispatch after unlock hook */
        FeatureApiUtils.apply(getEngineProviders(),
            feature -> feature.afterUnlock(this),
            (feature, ex) -> logger.error("{}: feature {} after-unlock failure because of {}", this,
                            feature.getClass().getName(), ex.getMessage(), ex));

        return success;
    }

    @Override
    public void removePolicyController(String name) {
        getControllerFactory().destroy(name);
    }

    @Override
    public void removePolicyController(PolicyController controller) {
        getControllerFactory().destroy(controller);
    }

    @JsonIgnore
    @GsonJsonIgnore
    @Override
    public List<PolicyController> getPolicyControllers() {
        return getControllerFactory().inventory();
    }

    @JsonProperty("controllers")
    @GsonJsonProperty("controllers")
    @Override
    public List<String> getPolicyControllerIds() {
        final List<String> controllerNames = new ArrayList<>();
        for (final PolicyController controller : getControllerFactory().inventory()) {
            controllerNames.add(controller.getName());
        }
        return controllerNames;
    }

    @Override
    @JsonIgnore
    @GsonJsonIgnore
    public Properties getProperties() {
        return this.properties;
    }

    @Override
    public List<String> getFeatures() {
        final List<String> features = new ArrayList<>();
        for (final PolicyEngineFeatureApi feature : getEngineProviders()) {
            features.add(feature.getName());
        }
        return features;
    }

    @JsonIgnore
    @GsonJsonIgnore
    @Override
    public List<PolicyEngineFeatureApi> getFeatureProviders() {
        return getEngineProviders();
    }

    @Override
    public PolicyEngineFeatureApi getFeatureProvider(String featureName) {
        if (featureName == null || featureName.isEmpty()) {
            throw new IllegalArgumentException("A feature name must be provided");
        }

        for (final PolicyEngineFeatureApi feature : getEngineProviders()) {
            if (feature.getName().equals(featureName)) {
                return feature;
            }
        }

        throw new IllegalArgumentException("Invalid Feature Name: " + featureName);
    }

    @Override
    public void onTopicEvent(CommInfrastructure commType, String topic, String event) {
        /* policy-engine pre topic event hook */
        if (FeatureApiUtils.apply(getFeatureProviders(),
            feature -> feature.beforeOnTopicEvent(this, commType, topic, event),
            (feature, ex) -> logger.error(
                            "{}: feature {} beforeOnTopicEvent failure on event {} because of {}", this,
                            feature.getClass().getName(), event, ex.getMessage(), ex))) {
            return;
        }

        /* configuration request */
        PdpdConfiguration configuration = null;
        try {
            configuration = this.decoder.fromJson(event, PdpdConfiguration.class);
            this.configure(configuration);
        } catch (final Exception e) {
            logger.error("{}: configuration-error due to {} because of {}", this, event, e.getMessage(), e);
        }

        /* policy-engine after topic event hook */
        PdpdConfiguration configuration2 = configuration;
        FeatureApiUtils.apply(getFeatureProviders(),
            feature -> feature.afterOnTopicEvent(this, configuration2, commType, topic, event),
            (feature, ex) -> logger.error("{}: feature {} afterOnTopicEvent failure on event {} because of {}", this,
                            feature.getClass().getName(), event, ex.getMessage(), ex));
    }

    @Override
    public boolean deliver(String topic, Object event) {

        /*
         * Note this entry point is usually from the DRL
         */

        if (topic == null || topic.isEmpty()) {
            throw new IllegalArgumentException(INVALID_TOPIC_MSG);
        }

        if (event == null) {
            throw new IllegalArgumentException(INVALID_EVENT_MSG);
        }

        if (!this.isAlive()) {
            throw new IllegalStateException(ENGINE_STOPPED_MSG);
        }

        if (this.isLocked()) {
            throw new IllegalStateException(ENGINE_LOCKED_MSG);
        }

        final List<TopicSink> topicSinks = getTopicEndpointManager().getTopicSinks(topic);
        if (topicSinks == null || topicSinks.size() != 1) {
            throw new IllegalStateException("Cannot ensure correct delivery on topic " + topic + ": " + topicSinks);
        }

        return this.deliver(topicSinks.get(0).getTopicCommInfrastructure(), topic, event);
    }

    @Override
    public boolean deliver(String busType, String topic, Object event) {

        /*
         * Note this entry point is usually from the DRL (one of the reasons busType is String.
         */

        if (StringUtils.isBlank(busType)) {
            throw new IllegalArgumentException("Invalid Communication Infrastructure");
        }

        if (StringUtils.isBlank(topic)) {
            throw new IllegalArgumentException(INVALID_TOPIC_MSG);
        }

        if (event == null) {
            throw new IllegalArgumentException(INVALID_EVENT_MSG);
        }

        boolean valid = Stream.of(Topic.CommInfrastructure.values()).map(Enum::name)
                        .anyMatch(name -> name.equals(busType));

        if (!valid) {
            throw new IllegalArgumentException("Invalid Communication Infrastructure: " + busType);
        }


        if (!this.isAlive()) {
            throw new IllegalStateException(ENGINE_STOPPED_MSG);
        }

        if (this.isLocked()) {
            throw new IllegalStateException(ENGINE_LOCKED_MSG);
        }


        return this.deliver(Topic.CommInfrastructure.valueOf(busType), topic, event);
    }

    @Override
    public boolean deliver(Topic.CommInfrastructure busType, String topic, Object event) {

        if (topic == null || topic.isEmpty()) {
            throw new IllegalArgumentException(INVALID_TOPIC_MSG);
        }

        if (event == null) {
            throw new IllegalArgumentException(INVALID_EVENT_MSG);
        }

        if (!this.isAlive()) {
            throw new IllegalStateException(ENGINE_STOPPED_MSG);
        }

        if (this.isLocked()) {
            throw new IllegalStateException(ENGINE_LOCKED_MSG);
        }

        /*
         * Try to send through the controller, this is the preferred way, since it may want to apply
         * additional processing
         */
        try {
            final DroolsController droolsController = getProtocolCoder().getDroolsController(topic, event);
            final PolicyController controller = getControllerFactory().get(droolsController);
            if (controller != null) {
                return controller.deliver(busType, topic, event);
            }
        } catch (final Exception e) {
            logger.warn("{}: cannot find policy-controller to deliver {} over {}:{} because of {}", this, event,
                    busType, topic, e.getMessage(), e);

            /* continue (try without routing through the controller) */
        }

        /*
         * cannot route through the controller, send directly through the topic sink
         */
        try {
            final String json = getProtocolCoder().encode(topic, event);
            return this.deliver(busType, topic, json);

        } catch (final Exception e) {
            logger.warn("{}: cannot deliver {} over {}:{}", this, event, busType, topic);
            throw e;
        }
    }

    @Override
    public boolean deliver(Topic.CommInfrastructure busType, String topic, String event) {

        if (topic == null || topic.isEmpty()) {
            throw new IllegalArgumentException(INVALID_TOPIC_MSG);
        }

        if (event == null || event.isEmpty()) {
            throw new IllegalArgumentException(INVALID_EVENT_MSG);
        }

        if (!this.isAlive()) {
            throw new IllegalStateException(ENGINE_STOPPED_MSG);
        }

        if (this.isLocked()) {
            throw new IllegalStateException(ENGINE_LOCKED_MSG);
        }

        try {
            final TopicSink sink = getTopicEndpointManager().getTopicSink(busType, topic);

            if (sink == null) {
                throw new IllegalStateException("Inconsistent State: " + this);
            }

            return sink.send(event);

        } catch (final Exception e) {
            logger.warn("{}: cannot deliver {} over {}:{}", this, event, busType, topic);
            throw e;
        }
    }

    @Override
    public synchronized void activate() {

        /* policy-engine dispatch pre activate hook */
        if (FeatureApiUtils.apply(getEngineProviders(),
            feature -> feature.beforeActivate(this),
            (feature, ex) -> logger.error("{}: feature {} before-activate failure because of {}", this,
                            feature.getClass().getName(), ex.getMessage(), ex))) {
            return;
        }

        // activate 'policy-management'
        for (final PolicyController policyController : this.getPolicyControllers()) {
            try {
                policyController.unlock();
                policyController.start();
            } catch (final Exception e) {
                logger.error("{}: cannot activate of policy-controller {} because of {}", this, policyController,
                        e.getMessage(), e);
            } catch (final LinkageError e) {
                logger.error("{}: cannot activate (rules compilation) of policy-controller {} because of {}", this,
                        policyController, e.getMessage(), e);
            }
        }

        this.unlock();

        /* policy-engine dispatch post activate hook */
        FeatureApiUtils.apply(getEngineProviders(),
            feature -> feature.afterActivate(this),
            (feature, ex) -> logger.error("{}: feature {} after-activate failure because of {}", this,
                            feature.getClass().getName(), ex.getMessage(), ex));
    }

    @Override
    public synchronized void deactivate() {

        /* policy-engine dispatch pre deactivate hook */
        if (FeatureApiUtils.apply(getEngineProviders(),
            feature -> feature.beforeDeactivate(this),
            (feature, ex) -> logger.error("{}: feature {} before-deactivate failure because of {}", this,
                            feature.getClass().getName(), ex.getMessage(), ex))) {
            return;
        }

        this.lock();

        for (final PolicyController policyController : this.getPolicyControllers()) {
            try {
                policyController.stop();
            } catch (final Exception | LinkageError e) {
                logger.error("{}: cannot deactivate (stop) policy-controller {} because of {}", this, policyController,
                        e.getMessage(), e);
            }
        }

        /* policy-engine dispatch post deactivate hook */
        FeatureApiUtils.apply(getEngineProviders(),
            feature -> feature.afterDeactivate(this),
            (feature, ex) -> logger.error("{}: feature {} after-deactivate failure because of {}", this,
                            feature.getClass().getName(), ex.getMessage(), ex));
    }

    @Override
    public Lock createLock(@NonNull String resourceId, @NonNull String ownerKey, int holdSec,
                    @NonNull LockCallback callback, boolean waitForLock) {

        if (holdSec < 0) {
            throw new IllegalArgumentException("holdSec is negative");
        }

        if (lockManager == null) {
            throw new IllegalStateException("lock manager has not been initialized");
        }

        return lockManager.createLock(resourceId, ownerKey, holdSec, callback, waitForLock);
    }

    private boolean controllerConfig(PdpdConfiguration config) {
        /* only this one supported for now */
        final List<ControllerConfiguration> configControllers = config.getControllers();
        if (configControllers == null || configControllers.isEmpty()) {
            logger.info("No controller configuration provided: {}", config);
            return false;
        }

        final List<PolicyController> policyControllers = this.updatePolicyControllers(config.getControllers());
        return (policyControllers != null && !policyControllers.isEmpty()
                        && policyControllers.size() == configControllers.size());
    }

    @Override
    public String toString() {
        return "PolicyEngineManager [alive=" + this.alive + ", locked=" + this.locked + "]";
    }

    // these methods may be overridden by junit tests

    protected List<PolicyEngineFeatureApi> getEngineProviders() {
        return PolicyEngineFeatureApiConstants.getProviders().getList();
    }

    protected List<PolicyControllerFeatureApi> getControllerProviders() {
        return PolicyControllerFeatureApiConstants.getProviders().getList();
    }

    protected void globalInitContainer(String[] cliArgs) {
        PolicyContainer.globalInit(cliArgs);
    }

    protected TopicEndpoint getTopicEndpointManager() {
        return TopicEndpointManager.getManager();
    }

    protected HttpServletServerFactory getServletFactory() {
        return HttpServletServerFactoryInstance.getServerFactory();
    }

    protected HttpClientFactory getHttpClientFactory() {
        return HttpClientFactoryInstance.getClientFactory();
    }

    protected PolicyControllerFactory getControllerFactory() {
        return PolicyControllerConstants.getFactory();
    }

    protected void startPdpJmxListener() {
        PdpJmxListener.start();
    }

    protected void stopPdpJmxListener() {
        PdpJmxListener.stop();
    }

    protected Thread makeShutdownThread() {
        return new ShutdownThread();
    }

    protected EventProtocolCoder getProtocolCoder() {
        return EventProtocolCoderConstants.getManager();
    }

    protected SystemPersistence getPersistenceManager() {
        return SystemPersistenceConstants.getManager();
    }

    protected PolicyEngine getPolicyEngine() {
        return PolicyEngineConstants.getManager();
    }

    protected ScheduledExecutorService makeScheduledExecutor(int nthreads) {
        ScheduledThreadPoolExecutor exsvc = new ScheduledThreadPoolExecutor(nthreads);
        exsvc.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        exsvc.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        exsvc.setRemoveOnCancelPolicy(true);

        return exsvc;
    }
}
