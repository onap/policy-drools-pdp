/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2019-2021 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2023-2024 Nordix Foundation.
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

package org.onap.policy.drools.controller;

import static org.onap.policy.common.message.bus.properties.MessageBusProperties.PROPERTY_KAFKA_SINK_TOPICS;
import static org.onap.policy.common.message.bus.properties.MessageBusProperties.PROPERTY_KAFKA_SOURCE_TOPICS;
import static org.onap.policy.common.message.bus.properties.MessageBusProperties.PROPERTY_NOOP_SINK_TOPICS;
import static org.onap.policy.common.message.bus.properties.MessageBusProperties.PROPERTY_NOOP_SOURCE_TOPICS;
import static org.onap.policy.drools.system.PolicyEngineConstants.PROPERTY_TOPIC_EVENTS_CUSTOM_MODEL_CODER_GSON_SUFFIX;
import static org.onap.policy.drools.system.PolicyEngineConstants.PROPERTY_TOPIC_EVENTS_FILTER_SUFFIX;
import static org.onap.policy.drools.system.PolicyEngineConstants.PROPERTY_TOPIC_EVENTS_SUFFIX;

import com.google.re2j.Pattern;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.onap.policy.common.message.bus.event.Topic;
import org.onap.policy.common.message.bus.event.Topic.CommInfrastructure;
import org.onap.policy.common.message.bus.event.TopicSink;
import org.onap.policy.common.message.bus.event.TopicSource;
import org.onap.policy.common.utils.services.FeatureApiUtils;
import org.onap.policy.drools.controller.internal.MavenDroolsController;
import org.onap.policy.drools.controller.internal.NullDroolsController;
import org.onap.policy.drools.features.DroolsControllerFeatureApi;
import org.onap.policy.drools.features.DroolsControllerFeatureApiConstants;
import org.onap.policy.drools.properties.DroolsPropertyConstants;
import org.onap.policy.drools.protocol.coders.JsonProtocolFilter;
import org.onap.policy.drools.protocol.coders.TopicCoderFilterConfiguration;
import org.onap.policy.drools.protocol.coders.TopicCoderFilterConfiguration.CustomGsonCoder;
import org.onap.policy.drools.protocol.coders.TopicCoderFilterConfiguration.PotentialCoderFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory of Drools Controllers indexed by the Maven coordinates.
 */
class IndexedDroolsControllerFactory implements DroolsControllerFactory {

    private static final Logger logger = LoggerFactory.getLogger(IndexedDroolsControllerFactory.class);
    private static final Pattern COMMA_SPACE_PAT = Pattern.compile("\\s*,\\s*");

    /**
     * Policy Controller Name Index.
     */
    protected Map<String, DroolsController> droolsControllers = new HashMap<>();

    /**
     * Null Drools Controller.
     */
    protected NullDroolsController nullDroolsController;

    /**
     * Constructs the object.
     */
    public IndexedDroolsControllerFactory() {

        /* Add a NULL controller which will always be present in the hash */

        nullDroolsController = new NullDroolsController();
        String controllerId = nullDroolsController.getGroupId() + ":" + nullDroolsController.getArtifactId();

        synchronized (this) {
            droolsControllers.put(controllerId, nullDroolsController);
        }
    }

    @Override
    public DroolsController build(Properties properties, List<? extends TopicSource> eventSources,
                                  List<? extends TopicSink> eventSinks) throws LinkageError {

        String groupId = properties.getProperty(DroolsPropertyConstants.RULES_GROUPID);
        if (StringUtils.isBlank(groupId)) {
            groupId = DroolsControllerConstants.NO_GROUP_ID;
        }

        String artifactId = properties.getProperty(DroolsPropertyConstants.RULES_ARTIFACTID);
        if (StringUtils.isBlank(artifactId)) {
            artifactId = DroolsControllerConstants.NO_ARTIFACT_ID;
        }

        String version = properties.getProperty(DroolsPropertyConstants.RULES_VERSION);
        if (StringUtils.isBlank(version)) {
            version = DroolsControllerConstants.NO_VERSION;
        }

        List<TopicCoderFilterConfiguration> topics2DecodedClasses2Filters = codersAndFilters(properties, eventSources);
        List<TopicCoderFilterConfiguration> topics2EncodedClasses2Filters = codersAndFilters(properties, eventSinks);

        return this.build(properties, groupId, artifactId, version,
            topics2DecodedClasses2Filters, topics2EncodedClasses2Filters);
    }

    @Override
    public DroolsController build(Properties properties, String newGroupId, String newArtifactId, String newVersion,
                                  List<TopicCoderFilterConfiguration> decoderConfigurations,
                                  List<TopicCoderFilterConfiguration> encoderConfigurations) throws LinkageError {

        if (StringUtils.isBlank(newGroupId)) {
            throw new IllegalArgumentException("Missing maven group-id coordinate");
        }

        if (StringUtils.isBlank(newArtifactId)) {
            throw new IllegalArgumentException("Missing maven artifact-id coordinate");
        }

        if (StringUtils.isBlank(newVersion)) {
            throw new IllegalArgumentException("Missing maven version coordinate");
        }

        String controllerId = newGroupId + ":" + newArtifactId;
        DroolsController controllerCopy = null;
        synchronized (this) {
            /*
             * The Null Drools Controller for no maven coordinates is always here so when no
             * coordinates present, this is the return point
             *
             * assert (controllerCopy instanceof NullDroolsController)
             */
            if (droolsControllers.containsKey(controllerId)) {
                controllerCopy = droolsControllers.get(controllerId);
                if (controllerCopy.getVersion().equalsIgnoreCase(newVersion)) {
                    return controllerCopy;
                }
            }
        }

        if (controllerCopy != null) {
            /*
             * a controller keyed by group id + artifact id exists but with different version =>
             * version upgrade/downgrade
             */

            controllerCopy.updateToVersion(newGroupId, newArtifactId, newVersion, decoderConfigurations,
                encoderConfigurations);

            return controllerCopy;
        }

        /* new drools controller */

        DroolsController controller = applyBeforeInstance(properties, newGroupId, newArtifactId, newVersion,
            decoderConfigurations, encoderConfigurations);

        if (controller == null) {
            controller = new MavenDroolsController(newGroupId, newArtifactId, newVersion, decoderConfigurations,
                encoderConfigurations);
        }

        synchronized (this) {
            droolsControllers.put(controllerId, controller);
        }

        final DroolsController controllerFinal = controller;

        FeatureApiUtils.apply(getProviders(),
            feature -> feature.afterInstance(controllerFinal, properties),
            (feature, ex) -> logger.error("feature {} ({}) afterInstance() of drools controller {}:{}:{} failed",
                feature.getName(), feature.getSequenceNumber(),
                newGroupId, newArtifactId, newVersion, ex));

        return controller;
    }

    private DroolsController applyBeforeInstance(Properties properties, String newGroupId, String newArtifactId,
                                                 String newVersion,
                                                 List<TopicCoderFilterConfiguration> decoderConfigurations,
                                                 List<TopicCoderFilterConfiguration> encoderConfigurations) {
        DroolsController controller = null;
        for (DroolsControllerFeatureApi feature : getProviders()) {
            try {
                controller = feature.beforeInstance(properties,
                    newGroupId, newArtifactId, newVersion,
                    decoderConfigurations, encoderConfigurations);
                if (controller != null) {
                    logger.info("feature {} ({}) beforeInstance() has intercepted drools controller {}:{}:{}",
                        feature.getName(), feature.getSequenceNumber(),
                        newGroupId, newArtifactId, newVersion);
                    break;
                }
            } catch (RuntimeException r) {
                logger.error("feature {} ({}) beforeInstance() of drools controller {}:{}:{} failed",
                    feature.getName(), feature.getSequenceNumber(),
                    newGroupId, newArtifactId, newVersion, r);
            }
        }
        return controller;
    }

    protected List<DroolsControllerFeatureApi> getProviders() {
        return DroolsControllerFeatureApiConstants.getProviders().getList();
    }

    /**
     * find out decoder classes and filters.
     *
     * @param properties    properties with information about decoders
     * @param topicEntities topic sources
     * @return list of topics, each with associated decoder classes, each with a list of associated filters
     * @throws IllegalArgumentException invalid input data
     */
    protected List<TopicCoderFilterConfiguration> codersAndFilters(Properties properties,
                                                                   List<? extends Topic> topicEntities) {

        List<TopicCoderFilterConfiguration> topics2DecodedClasses2Filters = new ArrayList<>();

        if (topicEntities == null || topicEntities.isEmpty()) {
            return topics2DecodedClasses2Filters;
        }

        for (Topic topic : topicEntities) {

            // 1. first the topic

            String firstTopic = topic.getTopic();

            String propertyTopicEntityPrefix = getPropertyTopicPrefix(topic) + firstTopic;

            // 2. check if there is a custom decoder for this topic that the user prefers to use
            // instead of the ones provided in the platform

            var customGsonCoder = getCustomCoder(properties, propertyTopicEntityPrefix);

            // 3. second the list of classes associated with each topic

            String eventClasses = properties
                .getProperty(propertyTopicEntityPrefix + PROPERTY_TOPIC_EVENTS_SUFFIX);

            if (StringUtils.isBlank(eventClasses)) {
                logger.warn("There are no event classes for topic {}", firstTopic);
                continue;
            }

            List<PotentialCoderFilter> classes2Filters =
                getFilterExpressions(properties, propertyTopicEntityPrefix, eventClasses);

            topics2DecodedClasses2Filters
                .add(new TopicCoderFilterConfiguration(firstTopic, classes2Filters, customGsonCoder));
        }

        return topics2DecodedClasses2Filters;
    }

    private String getPropertyTopicPrefix(Topic topic) {
        boolean isSource = topic instanceof TopicSource;
        var commInfra = topic.getTopicCommInfrastructure();
        if (commInfra == CommInfrastructure.NOOP) {
            if (isSource) {
                return PROPERTY_NOOP_SOURCE_TOPICS + ".";
            } else {
                return PROPERTY_NOOP_SINK_TOPICS + ".";
            }
        } else if (commInfra == CommInfrastructure.KAFKA) {
            if (isSource) {
                return PROPERTY_KAFKA_SOURCE_TOPICS + ".";
            } else {
                return PROPERTY_KAFKA_SINK_TOPICS + ".";
            }
        } else {
            throw new IllegalArgumentException("Invalid Communication Infrastructure: " + commInfra);
        }
    }

    private CustomGsonCoder getCustomCoder(Properties properties, String propertyPrefix) {
        String customGson = properties.getProperty(propertyPrefix
            + PROPERTY_TOPIC_EVENTS_CUSTOM_MODEL_CODER_GSON_SUFFIX);

        CustomGsonCoder customGsonCoder = null;
        if (StringUtils.isNotBlank(customGson)) {
            try {
                customGsonCoder = new CustomGsonCoder(customGson);
            } catch (IllegalArgumentException e) {
                logger.warn("{}: cannot create custom-gson-coder {} because of {}", this, customGson,
                    e.getMessage(), e);
            }
        }
        return customGsonCoder;
    }

    private List<PotentialCoderFilter> getFilterExpressions(Properties properties, String propertyPrefix,
                                                            @NonNull String eventClasses) {

        List<PotentialCoderFilter> classes2Filters = new ArrayList<>();
        for (String theClass : COMMA_SPACE_PAT.split(eventClasses)) {

            // 4. for each coder class, get the filter expression

            String filter = properties
                .getProperty(propertyPrefix
                    + PROPERTY_TOPIC_EVENTS_SUFFIX
                    + "." + theClass + PROPERTY_TOPIC_EVENTS_FILTER_SUFFIX);

            var class2Filters = new PotentialCoderFilter(theClass, new JsonProtocolFilter(filter));
            classes2Filters.add(class2Filters);
        }

        return classes2Filters;
    }

    @Override
    public void destroy(DroolsController controller) {
        unmanage(controller);
        controller.halt();
    }

    @Override
    public void destroy() {
        List<DroolsController> controllers = this.inventory();
        for (DroolsController controller : controllers) {
            controller.halt();
        }

        synchronized (this) {
            this.droolsControllers.clear();
        }
    }

    /**
     * unmanage the drools controller.
     *
     * @param controller the controller
     */
    protected void unmanage(DroolsController controller) {
        if (controller == null) {
            throw new IllegalArgumentException("No controller provided");
        }

        if (!controller.isBrained()) {
            logger.info("Drools Controller is NOT OPERATIONAL - nothing to destroy");
            return;
        }

        String controllerId = controller.getGroupId() + ":" + controller.getArtifactId();
        synchronized (this) {
            if (!this.droolsControllers.containsKey(controllerId)) {
                return;
            }

            droolsControllers.remove(controllerId);
        }
    }

    @Override
    public void shutdown(DroolsController controller) {
        this.unmanage(controller);
        controller.shutdown();
    }

    @Override
    public void shutdown() {
        List<DroolsController> controllers = this.inventory();
        for (DroolsController controller : controllers) {
            controller.shutdown();
        }

        synchronized (this) {
            this.droolsControllers.clear();
        }
    }

    @Override
    public DroolsController get(String groupId, String artifactId, String version) {

        if (StringUtils.isBlank(groupId) || StringUtils.isBlank(artifactId)) {
            throw new IllegalArgumentException("Missing maven coordinates: " + groupId + ":" + artifactId);
        }

        String controllerId = groupId + ":" + artifactId;

        synchronized (this) {
            if (this.droolsControllers.containsKey(controllerId)) {
                return droolsControllers.get(controllerId);
            } else {
                throw new IllegalStateException("DroolController for " + controllerId + " not found");
            }
        }
    }

    @Override
    public List<DroolsController> inventory() {
        return new ArrayList<>(this.droolsControllers.values());
    }

    @Override
    public String toString() {
        return "IndexedDroolsControllerFactory [#droolsControllers=" + droolsControllers.size() + "]";
    }

}
