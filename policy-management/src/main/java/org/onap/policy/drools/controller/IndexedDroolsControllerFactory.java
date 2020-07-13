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

package org.onap.policy.drools.controller;

import static org.onap.policy.drools.properties.DroolsPropertyConstants.PROPERTY_CONTROLLER_TYPE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.ServiceLoader;
import org.onap.policy.common.endpoints.event.comm.Topic;
import org.onap.policy.common.endpoints.event.comm.Topic.CommInfrastructure;
import org.onap.policy.common.endpoints.event.comm.TopicSink;
import org.onap.policy.common.endpoints.event.comm.TopicSource;
import org.onap.policy.common.endpoints.properties.PolicyEndPointProperties;
import org.onap.policy.drools.controller.internal.MavenDroolsController;
import org.onap.policy.drools.controller.internal.NullDroolsController;
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

    /**
     * logger.
     */
    private static Logger logger = LoggerFactory.getLogger(IndexedDroolsControllerFactory.class);

    // table mapping 'controller.type' values to 'DroolsControllerBuilder' instances
    private static final HashMap<String, DroolsControllerBuilder> builders = new HashMap<>();

    static {
        for (DroolsControllerBuilder builder : ServiceLoader.load(DroolsControllerBuilder.class)) {
            for (String type : builder.getType().split(",")) {
                builders.put(type, builder);
                logger.info("Adding DroolsControllerBuilder for {}={}, with {}",
                            PROPERTY_CONTROLLER_TYPE, type, builder.getClass());
            }
        }
    }

    /**
     * Policy Controller Name Index.
     */
    protected HashMap<String, DroolsController> droolsControllers = new HashMap<>();

    /**
     * Null Drools Controller.
     */
    protected NullDroolsController nullDroolsController = new NullDroolsController();

    /**
     * Constructs the object.
     */
    public IndexedDroolsControllerFactory() {

        /* Add a NULL controller which will always be present in the hash */

        DroolsController controller = new NullDroolsController();
        String controllerId = controller.getGroupId() + ":" + controller.getArtifactId();

        synchronized (this) {
            droolsControllers.put(controllerId, controller);
        }
    }

    @Override
    public DroolsController build(Properties properties, List<? extends TopicSource> eventSources,
            List<? extends TopicSink> eventSinks) throws LinkageError {

        String groupId = properties.getProperty(DroolsPropertyConstants.RULES_GROUPID);
        if (groupId == null || groupId.isEmpty()) {
            groupId = DroolsControllerConstants.NO_GROUP_ID;
        }

        String artifactId = properties.getProperty(DroolsPropertyConstants.RULES_ARTIFACTID);
        if (artifactId == null || artifactId.isEmpty()) {
            artifactId = DroolsControllerConstants.NO_ARTIFACT_ID;
        }

        String version = properties.getProperty(DroolsPropertyConstants.RULES_VERSION);
        if (version == null || version.isEmpty()) {
            version = DroolsControllerConstants.NO_VERSION;
        }

        List<TopicCoderFilterConfiguration> topics2DecodedClasses2Filters = codersAndFilters(properties, eventSources);
        List<TopicCoderFilterConfiguration> topics2EncodedClasses2Filters = codersAndFilters(properties, eventSinks);

        String type = properties.getProperty(PROPERTY_CONTROLLER_TYPE);
        if (type == null) {
            return this.build(groupId, artifactId, version,
                              topics2DecodedClasses2Filters, topics2EncodedClasses2Filters);
        }

        // 'controller.type' is specified -- look up, and then invoke the builder
        DroolsControllerBuilder builder = builders.get(type);
        if (builder == null) {
            throw new IllegalArgumentException("No DroolsControllerBuilder for "
                                               + PROPERTY_CONTROLLER_TYPE + "=" + type);
        }

        DroolsController controller =
            builder.build(properties, topics2DecodedClasses2Filters, topics2EncodedClasses2Filters);
        if (controller != null) {
            String controllerId = controller.getGroupId() + ":" + controller.getArtifactId();
            synchronized (this) {
                droolsControllers.put(controllerId, controller);
            }
        }
        return controller;
    }

    @Override
    public DroolsController build(String newGroupId, String newArtifactId, String newVersion,
            List<TopicCoderFilterConfiguration> decoderConfigurations,
            List<TopicCoderFilterConfiguration> encoderConfigurations) throws LinkageError {

        if (newGroupId == null || newGroupId.isEmpty()) {
            throw new IllegalArgumentException("Missing maven group-id coordinate");
        }

        if (newArtifactId == null || newArtifactId.isEmpty()) {
            throw new IllegalArgumentException("Missing maven artifact-id coordinate");
        }

        if (newVersion == null || newVersion.isEmpty()) {
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

        DroolsController controller = new MavenDroolsController(newGroupId, newArtifactId, newVersion,
                decoderConfigurations, encoderConfigurations);

        synchronized (this) {
            droolsControllers.put(controllerId, controller);
        }

        return controller;
    }

    /**
     * find out decoder classes and filters.
     *
     * @param properties properties with information about decoders
     * @param topicEntities topic sources
     * @return list of topics, each with associated decoder classes, each with a list of associated
     *         filters
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

            CustomGsonCoder customGsonCoder = getCustomCoder(properties, propertyTopicEntityPrefix);

            // 3. second the list of classes associated with each topic

            String eventClasses = properties
                    .getProperty(propertyTopicEntityPrefix + PolicyEndPointProperties.PROPERTY_TOPIC_EVENTS_SUFFIX);

            if (eventClasses == null || eventClasses.isEmpty()) {
                logger.warn("There are no event classes for topic {}", firstTopic);
                continue;
            }

            List<PotentialCoderFilter> classes2Filters =
                            getFilterExpressions(properties, propertyTopicEntityPrefix, eventClasses);

            TopicCoderFilterConfiguration topic2Classes2Filters =
                    new TopicCoderFilterConfiguration(firstTopic, classes2Filters, customGsonCoder);
            topics2DecodedClasses2Filters.add(topic2Classes2Filters);
        }

        return topics2DecodedClasses2Filters;
    }

    private String getPropertyTopicPrefix(Topic topic) {
        boolean isSource = topic instanceof TopicSource;
        CommInfrastructure commInfra = topic.getTopicCommInfrastructure();
        if (commInfra == CommInfrastructure.UEB) {
            if (isSource) {
                return PolicyEndPointProperties.PROPERTY_UEB_SOURCE_TOPICS + ".";
            } else {
                return PolicyEndPointProperties.PROPERTY_UEB_SINK_TOPICS + ".";
            }
        } else if (commInfra == CommInfrastructure.DMAAP) {
            if (isSource) {
                return PolicyEndPointProperties.PROPERTY_DMAAP_SOURCE_TOPICS + ".";
            } else {
                return PolicyEndPointProperties.PROPERTY_DMAAP_SINK_TOPICS + ".";
            }
        } else if (commInfra == CommInfrastructure.NOOP) {
            if (isSource) {
                return PolicyEndPointProperties.PROPERTY_NOOP_SOURCE_TOPICS + ".";
            } else {
                return PolicyEndPointProperties.PROPERTY_NOOP_SINK_TOPICS + ".";
            }
        } else {
            throw new IllegalArgumentException("Invalid Communication Infrastructure: " + commInfra);
        }
    }

    private CustomGsonCoder getCustomCoder(Properties properties, String propertyPrefix) {
        String customGson = properties.getProperty(propertyPrefix
                + PolicyEndPointProperties.PROPERTY_TOPIC_EVENTS_CUSTOM_MODEL_CODER_GSON_SUFFIX);

        CustomGsonCoder customGsonCoder = null;
        if (customGson != null && !customGson.isEmpty()) {
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
                    String eventClasses) {

        List<PotentialCoderFilter> classes2Filters = new ArrayList<>();

        List<String> topicClasses = new ArrayList<>(Arrays.asList(eventClasses.split("\\s*,\\s*")));

        for (String theClass : topicClasses) {

            // 4. for each coder class, get the filter expression

            String filter = properties
                    .getProperty(propertyPrefix
                            + PolicyEndPointProperties.PROPERTY_TOPIC_EVENTS_SUFFIX
                            + "." + theClass + PolicyEndPointProperties.PROPERTY_TOPIC_EVENTS_FILTER_SUFFIX);

            JsonProtocolFilter protocolFilter = new JsonProtocolFilter(filter);
            PotentialCoderFilter class2Filters = new PotentialCoderFilter(theClass, protocolFilter);
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

        if (groupId == null || artifactId == null || groupId.isEmpty() || artifactId.isEmpty()) {
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
        StringBuilder builder = new StringBuilder();
        builder.append("IndexedDroolsControllerFactory [#droolsControllers=").append(droolsControllers.size())
                .append("]");
        return builder.toString();
    }

}
