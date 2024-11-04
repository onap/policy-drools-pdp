/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2017-2021 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2021, 2024 Nordix Foundation.
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

package org.onap.policy.drools.system.internal;

import com.google.re2j.Pattern;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.ToString;
import org.onap.policy.common.gson.annotation.GsonJsonIgnore;
import org.onap.policy.common.message.bus.event.Topic;
import org.onap.policy.common.message.bus.event.TopicEndpoint;
import org.onap.policy.common.message.bus.event.TopicEndpointManager;
import org.onap.policy.common.message.bus.event.TopicListener;
import org.onap.policy.common.message.bus.event.TopicSink;
import org.onap.policy.common.message.bus.event.TopicSource;
import org.onap.policy.common.utils.services.FeatureApiUtils;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.controller.DroolsControllerConstants;
import org.onap.policy.drools.controller.DroolsControllerFactory;
import org.onap.policy.drools.features.PolicyControllerFeatureApi;
import org.onap.policy.drools.features.PolicyControllerFeatureApiConstants;
import org.onap.policy.drools.persistence.SystemPersistence;
import org.onap.policy.drools.persistence.SystemPersistenceConstants;
import org.onap.policy.drools.properties.DroolsPropertyConstants;
import org.onap.policy.drools.protocol.configuration.DroolsConfiguration;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.drools.utils.PropertyUtil;
import org.onap.policy.models.tosca.authorative.concepts.ToscaConceptIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This implementation of the Policy Controller merely aggregates and tracks for management purposes
 * all underlying resources that this controller depends upon.
 */
@ToString(onlyExplicitlyIncluded = true)
public class AggregatedPolicyController implements PolicyController, TopicListener {

    private static final String BEFORE_OFFER_FAILURE = "{}: feature {} before-offer failure because of {}";
    private static final String AFTER_OFFER_FAILURE = "{}: feature {} after-offer failure because of {}";

    /**
     * Logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(AggregatedPolicyController.class);
    private static final Pattern COMMA_SPACE_PAT = Pattern.compile("\\s*,\\s*");

    /**
     * identifier for this policy controller.
     */
    @Getter
    @ToString.Include
    private final String name;

    /**
     * Abstracted Event Sources List regardless communication technology.
     */
    @Getter
    protected final List<TopicSource> topicSources;

    /**
     * Abstracted Event Sinks List regardless communication technology.
     */
    @Getter
    protected final List<TopicSink> topicSinks;

    /**
     * Mapping topics to sinks.
     */
    @GsonJsonIgnore
    private final HashMap<String, TopicSink> topic2Sinks = new HashMap<>();

    /**
     * Is this Policy Controller running (alive) ? reflects invocation of start()/stop() only.
     */
    @Getter
    @ToString.Include
    private volatile boolean alive;

    /**
     * Is this Policy Controller locked ? reflects if i/o controller related operations and start
     * are permitted, more specifically: start(), deliver() and onTopicEvent(). It does not affect
     * the ability to stop the underlying drools infrastructure
     */
    @Getter
    @ToString.Include
    private volatile boolean locked;

    /**
     * Policy Drools Controller.
     */
    @ToString.Include
    protected final AtomicReference<DroolsController> droolsController = new AtomicReference<>();

    /**
     * Properties used to initialize controller.
     */
    private final Properties properties;

    /**
     * Policy Types.
     */
    private final List<ToscaConceptIdentifier> policyTypes;

    /**
     * Constructor version mainly used for bootstrapping at initialization time a policy engine
     * controller.
     *
     * @param name controller name
     * @param properties controller properties
     *
     * @throws IllegalArgumentException when invalid arguments are provided
     */
    public AggregatedPolicyController(String name, Properties properties) {

        this.name = name;

        /*
         * 1. Register read topics with network infrastructure
         * 2. Register write topics with network infrastructure
         * 3. Register with drools infrastructure
         */

        // Create/Reuse Readers/Writers for all event sources endpoints

        this.topicSources = getEndpointManager().addTopicSources(properties);
        this.topicSinks = getEndpointManager().addTopicSinks(properties);

        initDrools(properties);
        initSinks();

        /* persist new properties */
        getPersistenceManager().storeController(name, properties);
        this.properties = PropertyUtil.getInterpolatedProperties(properties);

        this.policyTypes = getPolicyTypesFromProperties();
    }

    @Override
    public List<ToscaConceptIdentifier> getPolicyTypes() {
        if (!policyTypes.isEmpty()) {
            return policyTypes;
        }

        return droolsController
                .get()
                .getBaseDomainNames()
                .stream()
                .map(d -> new ToscaConceptIdentifier(d,
                                DroolsPropertyConstants.DEFAULT_CONTROLLER_POLICY_TYPE_VERSION))
                .collect(Collectors.toList());
    }

    protected List<ToscaConceptIdentifier> getPolicyTypesFromProperties() {
        List<ToscaConceptIdentifier> policyTypeIds = new ArrayList<>();

        String ptiPropValue = properties.getProperty(DroolsPropertyConstants.PROPERTY_CONTROLLER_POLICY_TYPES);
        if (ptiPropValue == null) {
            return policyTypeIds;
        }

        for (String pti : COMMA_SPACE_PAT.split(ptiPropValue)) {
            String[] ptv = pti.split(":");
            if (ptv.length == 1) {
                policyTypeIds.add(new ToscaConceptIdentifier(ptv[0],
                    DroolsPropertyConstants.DEFAULT_CONTROLLER_POLICY_TYPE_VERSION));
            } else if (ptv.length == 2) {
                policyTypeIds.add(new ToscaConceptIdentifier(ptv[0], ptv[1]));
            }
        }

        return policyTypeIds;
    }

    /**
     * initialize drools layer.
     *
     * @throws IllegalArgumentException if invalid parameters are passed in
     */
    protected void initDrools(Properties properties) {
        try {
            // Register with drools infrastructure
            this.droolsController.set(getDroolsFactory().build(properties, topicSources, topicSinks));
        } catch (Exception | LinkageError e) {
            logger.error("{}: cannot init-drools", this);
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * initialize sinks.
     *
     * @throws IllegalArgumentException if invalid parameters are passed in
     */
    private void initSinks() {
        this.topic2Sinks.clear();
        for (TopicSink sink : topicSinks) {
            this.topic2Sinks.put(sink.getTopic(), sink);
        }
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public boolean updateDrools(DroolsConfiguration newDroolsConfiguration) {
        DroolsController controller = this.droolsController.get();
        var oldDroolsConfiguration = new DroolsConfiguration(controller.getArtifactId(),
                controller.getGroupId(), controller.getVersion());

        if (oldDroolsConfiguration.getGroupId().equalsIgnoreCase(newDroolsConfiguration.getGroupId())
                && oldDroolsConfiguration.getArtifactId().equalsIgnoreCase(newDroolsConfiguration.getArtifactId())
                && oldDroolsConfiguration.getVersion().equalsIgnoreCase(newDroolsConfiguration.getVersion())) {
            logger.warn("{}: cannot update-drools: identical configuration {} vs {}", this, oldDroolsConfiguration,
                    newDroolsConfiguration);
            return true;
        }

        if (FeatureApiUtils.apply(getProviders(),
            feature -> feature.beforePatch(this, oldDroolsConfiguration, newDroolsConfiguration),
            (feature, ex) -> logger.error("{}: feature {} before-patch failure because of {}", this,
                        feature.getClass().getName(), ex.getMessage(), ex))) {
            return true;
        }

        if (controller.isBrained()
            && (newDroolsConfiguration.getArtifactId() == null
                || DroolsControllerConstants.NO_ARTIFACT_ID.equals(newDroolsConfiguration.getArtifactId()))) {
            // detach maven artifact
            DroolsControllerConstants.getFactory().destroy(controller);
        }

        var success = true;
        try {
            this.properties.setProperty(DroolsPropertyConstants.RULES_GROUPID, newDroolsConfiguration.getGroupId());
            this.properties.setProperty(DroolsPropertyConstants.RULES_ARTIFACTID,
                            newDroolsConfiguration.getArtifactId());
            this.properties.setProperty(DroolsPropertyConstants.RULES_VERSION, newDroolsConfiguration.getVersion());
            getPersistenceManager().storeController(name, this.properties);

            this.initDrools(this.properties);

            // have a new controller now - get it
            controller = this.droolsController.get();

            if (isLocked()) {
                controller.lock();
            } else {
                controller.unlock();
            }

            if (isAlive()) {
                controller.start();
            } else {
                controller.stop();
            }
        } catch (RuntimeException e) {
            logger.error("{}: cannot update-drools because of {}", this, e.getMessage(), e);
            success = false;
        }

        boolean finalSuccess = success;
        FeatureApiUtils.apply(getProviders(),
            feature -> feature.afterPatch(this, oldDroolsConfiguration, newDroolsConfiguration, finalSuccess),
            (feature, ex) -> logger.error("{}: feature {} after-patch failure because of {}", this,
                        feature.getClass().getName(), ex.getMessage(), ex));

        return finalSuccess;
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public boolean start() {
        logger.info("{}: start", this);

        if (FeatureApiUtils.apply(getProviders(),
            feature -> feature.beforeStart(this),
            (feature, ex) -> logger.error("{}: feature {} before-start failure because of {}", this,
                            feature.getClass().getName(), ex.getMessage(), ex))) {
            return true;
        }

        if (this.isLocked()) {
            throw new IllegalStateException("Policy Controller " + name + " is locked");
        }

        synchronized (this) {
            if (this.alive) {
                return true;
            }

            this.alive = true;
        }

        final boolean success = this.droolsController.get().start();

        // register for events

        for (TopicSource source : topicSources) {
            source.register(this);
        }

        for (TopicSink sink : topicSinks) {
            try {
                sink.start();
            } catch (Exception e) {
                logger.error("{}: cannot start {} because of {}", this, sink, e.getMessage(), e);
            }
        }

        FeatureApiUtils.apply(getProviders(),
            feature -> feature.afterStart(this),
            (feature, ex) -> logger.error("{}: feature {} after-start failure because of {}", this,
                            feature.getClass().getName(), ex.getMessage(), ex));

        return success;
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public boolean stop() {
        logger.info("{}: stop", this);

        if (FeatureApiUtils.apply(getProviders(),
            feature -> feature.beforeStop(this),
            (feature, ex) -> logger.error("{}: feature {} before-stop failure because of {}", this,
                            feature.getClass().getName(), ex.getMessage(), ex))) {
            return true;
        }

        /* stop regardless locked state */

        synchronized (this) {
            if (!this.alive) {
                return true;
            }

            this.alive = false;
        }

        // 1. Stop registration

        for (TopicSource source : topicSources) {
            source.unregister(this);
        }

        boolean success = this.droolsController.get().stop();

        FeatureApiUtils.apply(getProviders(),
            feature -> feature.afterStop(this),
            (feature, ex) -> logger.error("{}: feature {} after-stop failure because of {}", this,
                            feature.getClass().getName(), ex.getMessage(), ex));

        return success;
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public void shutdown() {
        logger.info("{}: shutdown", this);

        if (FeatureApiUtils.apply(getProviders(),
            feature -> feature.beforeShutdown(this),
            (feature, ex) -> logger.error("{}: feature {} before-shutdown failure because of {}", this,
                            feature.getClass().getName(), ex.getMessage(), ex))) {
            return;
        }

        this.stop();

        getDroolsFactory().shutdown(this.droolsController.get());

        FeatureApiUtils.apply(getProviders(),
            feature -> feature.afterShutdown(this),
            (feature, ex) -> logger.error("{}: feature {} after-shutdown failure because of {}", this,
                            feature.getClass().getName(), ex.getMessage(), ex));
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public void halt() {
        logger.info("{}: halt", this);

        if (FeatureApiUtils.apply(getProviders(),
            feature -> feature.beforeHalt(this),
            (feature, ex) -> logger.error("{}: feature {} before-halt failure because of {}", this,
                            feature.getClass().getName(), ex.getMessage(), ex))) {
            return;
        }

        this.stop();
        getDroolsFactory().destroy(this.droolsController.get());
        getPersistenceManager().deleteController(this.name);

        FeatureApiUtils.apply(getProviders(),
            feature -> feature.afterHalt(this),
            (feature, ex) -> logger.error("{}: feature {} after-halt failure because of {}", this,
                            feature.getClass().getName(), ex.getMessage(), ex));
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public void onTopicEvent(Topic.CommInfrastructure commType, String topic, String event) {
        logger.debug("{}: raw event offered from {}:{}: {}", this, commType, topic, event);

        if (skipOffer()) {
            return;
        }

        if (FeatureApiUtils.apply(getProviders(),
            feature -> feature.beforeOffer(this, commType, topic, event),
            (feature, ex) -> logger.error(BEFORE_OFFER_FAILURE, this,
                            feature.getClass().getName(), ex.getMessage(), ex))) {
            return;
        }

        boolean success = this.droolsController.get().offer(topic, event);

        FeatureApiUtils.apply(getProviders(),
            feature -> feature.afterOffer(this, commType, topic, event, success),
            (feature, ex) -> logger.error(AFTER_OFFER_FAILURE, this,
                            feature.getClass().getName(), ex.getMessage(), ex));
    }

    @Override
    public <T> boolean offer(T event) {
        logger.debug("{}: event offered: {}", this, event);

        if (skipOffer()) {
            return true;
        }

        if (FeatureApiUtils.apply(getProviders(),
            feature -> feature.beforeOffer(this, event),
            (feature, ex) -> logger.error(BEFORE_OFFER_FAILURE, this,
                            feature.getClass().getName(), ex.getMessage(), ex))) {
            return true;
        }

        boolean success = this.droolsController.get().offer(event);

        FeatureApiUtils.apply(getProviders(),
            feature -> feature.afterOffer(this, event, success),
            (feature, ex) -> logger.error(AFTER_OFFER_FAILURE, this,
                            feature.getClass().getName(), ex.getMessage(), ex));

        return success;
    }

    private boolean skipOffer() {
        return isLocked() || !isAlive();
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public boolean deliver(Topic.CommInfrastructure commType, String topic, Object event) {

        logger.debug("{}: deliver event to {}:{}: {}", this, commType, topic, event);

        if (FeatureApiUtils.apply(getProviders(),
            feature -> feature.beforeDeliver(this, commType, topic, event),
            (feature, ex) -> logger.error("{}: feature {} before-deliver failure because of {}", this,
                            feature.getClass().getName(), ex.getMessage(), ex))) {
            return true;
        }

        if (topic == null || topic.isEmpty()) {
            throw new IllegalArgumentException("Invalid Topic");
        }

        if (event == null) {
            throw new IllegalArgumentException("Invalid Event");
        }

        if (!this.isAlive()) {
            throw new IllegalStateException("Policy Engine is stopped");
        }

        if (this.isLocked()) {
            throw new IllegalStateException("Policy Engine is locked");
        }

        if (!this.topic2Sinks.containsKey(topic)) {
            logger.warn("{}: cannot deliver event because the sink {}:{} is not registered: {}", this, commType, topic,
                    event);
            throw new IllegalArgumentException("Unsupported topic " + topic + " for delivery");
        }

        boolean success = this.droolsController.get().deliver(this.topic2Sinks.get(topic), event);

        FeatureApiUtils.apply(getProviders(),
            feature -> feature.afterDeliver(this, commType, topic, event, success),
            (feature, ex) -> logger.error("{}: feature {} after-deliver failure because of {}", this,
                            feature.getClass().getName(), ex.getMessage(), ex));

        return success;
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public boolean lock() {
        logger.info("{}: lock", this);

        if (FeatureApiUtils.apply(getProviders(),
            feature -> feature.beforeLock(this),
            (feature, ex) -> logger.error("{}: feature {} before-lock failure because of {}", this,
                            feature.getClass().getName(), ex.getMessage(), ex))) {
            return true;
        }

        synchronized (this) {
            if (this.locked) {
                return true;
            }

            this.locked = true;
        }

        // it does not affect associated sources/sinks, they are
        // autonomous entities

        boolean success = this.droolsController.get().lock();

        FeatureApiUtils.apply(getProviders(),
            feature -> feature.afterLock(this),
            (feature, ex) -> logger.error("{}: feature {} after-lock failure because of {}", this,
                            feature.getClass().getName(), ex.getMessage(), ex));

        return success;
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public boolean unlock() {

        logger.info("{}: unlock", this);

        if (FeatureApiUtils.apply(getProviders(),
            feature -> feature.beforeUnlock(this),
            (feature, ex) -> logger.error("{}: feature {} before-unlock failure because of {}", this,
                            feature.getClass().getName(), ex.getMessage(), ex))) {
            return true;
        }

        synchronized (this) {
            if (!this.locked) {
                return true;
            }

            this.locked = false;
        }

        boolean success = this.droolsController.get().unlock();

        FeatureApiUtils.apply(getProviders(),
            feature -> feature.afterUnlock(this),
            (feature, ex) -> logger.error("{}: feature {} after-unlock failure because of {}", this,
                            feature.getClass().getName(), ex.getMessage(), ex));

        return success;
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public DroolsController getDrools() {
        return this.droolsController.get();
    }


    /**
     * {@inheritDoc}.
     */
    @Override
    @GsonJsonIgnore
    public Properties getProperties() {
        return this.properties;
    }

    // the following methods may be overridden by junit tests

    protected SystemPersistence getPersistenceManager() {
        return SystemPersistenceConstants.getManager();
    }

    protected TopicEndpoint getEndpointManager() {
        return TopicEndpointManager.getManager();
    }

    protected DroolsControllerFactory getDroolsFactory() {
        return DroolsControllerConstants.getFactory();
    }

    protected List<PolicyControllerFeatureApi> getProviders() {
        return PolicyControllerFeatureApiConstants.getProviders().getList();
    }
}

