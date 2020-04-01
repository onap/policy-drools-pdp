/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2020 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.lifecycle;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import lombok.Getter;
import org.onap.policy.common.endpoints.event.comm.TopicEndpointManager;
import org.onap.policy.common.endpoints.event.comm.TopicSink;
import org.onap.policy.common.endpoints.event.comm.TopicSource;
import org.onap.policy.common.gson.annotation.GsonJsonIgnore;
import org.onap.policy.common.utils.coder.CoderException;
import org.onap.policy.drools.domain.models.controller.ControllerCustomSerialization;
import org.onap.policy.drools.domain.models.controller.ControllerEvent;
import org.onap.policy.drools.domain.models.controller.ControllerPolicy;
import org.onap.policy.drools.domain.models.controller.ControllerProperties;
import org.onap.policy.drools.domain.models.controller.ControllerSinkTopic;
import org.onap.policy.drools.domain.models.controller.ControllerSourceTopic;
import org.onap.policy.drools.properties.DroolsPropertyConstants;
import org.onap.policy.drools.system.PolicyControllerConstants;
import org.onap.policy.drools.system.PolicyEngineConstants;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicy;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicyTypeIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PolicyTypeNativeDroolsController implements PolicyTypeController {
    private static final Logger logger = LoggerFactory.getLogger(PolicyTypeNativeDroolsController.class);

    @Getter
    protected final ToscaPolicyTypeIdentifier policyType;

    @GsonJsonIgnore
    @JsonIgnore
    protected final transient LifecycleFsm fsm;

    public PolicyTypeNativeDroolsController(LifecycleFsm fsm, ToscaPolicyTypeIdentifier policyType) {
        this.policyType = policyType;
        this.fsm = fsm;
    }

    @Override
    public boolean deploy(ToscaPolicy policy) {
        Properties controllerProps = new Properties();
        ControllerPolicy controllerPolicy = toDomainPolicy(policy);
        if (controllerPolicy == null) {
            return false;
        }

        ControllerProperties controllerConfig = controllerPolicy.getProperties();

        boolean success =
            configControllerName(controllerConfig, controllerProps)
            && configControllerSources(controllerConfig, controllerProps)
            && configControllerSinks(controllerConfig, controllerProps)
            && configControllerCustom(controllerConfig, controllerProps);

        if (!success) {
            return false;
        }

        try {
            PolicyEngineConstants.getManager()
                        .createPolicyController(controllerConfig.getControllerName(), controllerProps)
                        .start();
        } catch (RuntimeException e) {
            logger.warn("failed deploy (cannot create controller) for policy: {}", policy);
            return false;
        }

        return true;
    }

    @Override
    public boolean undeploy(ToscaPolicy policy) {
        try {
            ControllerPolicy nativePolicy = fsm.getDomainMaker().convertTo(policy, ControllerPolicy.class);
            PolicyEngineConstants.getManager()
                    .removePolicyController(nativePolicy.getProperties().getControllerName());
            return true;
        } catch (RuntimeException | CoderException e) {
            logger.warn("failed undeploy of policy: {}", policy);
            return false;
        }
    }

    private ControllerPolicy toDomainPolicy(ToscaPolicy policy) {
        ControllerPolicy nativePolicy = null;
        try {
            nativePolicy = fsm.getDomainMaker().convertTo(policy, ControllerPolicy.class);
            ControllerProperties config = nativePolicy.getProperties();

            /* check for duplicates */

            if (isDups(sourceTopics(config.getSourceTopics()))
                        || isDups(sinkTopics(config.getSinkTopics()))) {
                logger.warn("there are duplicated topics in policy {}", policy);
                return null;
            }

            /* check for non-existance of the controller - throws IAE if there's not */

            PolicyControllerConstants.getFactory().get(nativePolicy.getProperties().getControllerName());

        } catch (CoderException e) {
            logger.warn("failed deploy of policy (invalid): {}", policy);
            return null;
        } catch (IllegalArgumentException e) {
            // this is OK
            logger.trace("proceeding with the deploy of native controller policy: {}", policy);
        }

        return nativePolicy;
    }

    private boolean configControllerName(ControllerProperties controllerConfig, Properties controllerProps)  {
        controllerProps
                .setProperty(DroolsPropertyConstants.PROPERTY_CONTROLLER_NAME, controllerConfig.getControllerName());
        return true;
    }

    private boolean configControllerSources(ControllerProperties controllerConfig, Properties controllerProps) {
        if (controllerConfig.getSourceTopics() == null) {
            return true;
        }

        for (ControllerSourceTopic configSourceTopic : controllerConfig.getSourceTopics()) {
            List<TopicSource> sources =
                    TopicEndpointManager.getManager().getTopicSources(List.of(configSourceTopic.getTopicName()));
            if (sources.size() != 1) {
                logger.warn("Topic {} is not present or ambigous {}", configSourceTopic.getTopicName(), sources);
                return false;
            }

            configSourceTopic(sources.get(0), configSourceTopic, controllerProps);
        }
        return true;
    }

    private void configSourceTopic(TopicSource topic, ControllerSourceTopic configTopic, Properties controllerProps) {
        String configCommPrefix = topic.getTopicCommInfrastructure().name().toLowerCase() + ".source";
        configTopic(configCommPrefix, topic.getTopic(), configTopic.getEvents(), controllerProps);
    }

    private boolean configControllerSinks(ControllerProperties controllerConfig, Properties controllerProps) {
        if (controllerConfig.getSinkTopics() == null) {
            return true;
        }

        for (ControllerSinkTopic configSinkTopic : controllerConfig.getSinkTopics()) {
            List<TopicSink> sinks =
                    TopicEndpointManager.getManager().getTopicSinks(List.of(configSinkTopic.getTopicName()));
            if (sinks.size() != 1) {
                logger.warn("Topic {} is not present or ambigous {}", configSinkTopic.getTopicName(), sinks);
                return false;
            }

            configSinkTopic(sinks.get(0), configSinkTopic, controllerProps);
        }
        return true;
    }

    private void configSinkTopic(TopicSink topic, ControllerSinkTopic configTopic, Properties controllerProps) {
        String configCommPrefix = topic.getTopicCommInfrastructure().name().toLowerCase() + ".sink";
        configTopic(configCommPrefix, topic.getTopic(), configTopic.getEvents(), controllerProps);
    }

    private void configTopic(
            String configCommPrefix, String topicName, List<ControllerEvent> events, Properties controllerProps) {
        String configTopicPrefix = configCommPrefix + "." + topicName;
        configTopics(configCommPrefix, topicName, controllerProps);
        for (ControllerEvent configEvent : events) {
            configEvent(configTopicPrefix, configEvent, controllerProps);
        }
    }

    private void configTopics(String propPrefix, String topicName, Properties controllerProps) {
        String topicsPropKey = propPrefix + ".topics";
        configTopicItemList(topicsPropKey, topicName, controllerProps);
    }

    private void configEvent(String propPrefix, ControllerEvent configEvent, Properties controllerProps) {
        String eventPropPrefix = propPrefix + ".events";
        controllerProps.setProperty(eventPropPrefix, configEvent.getEventClass());
        if (configEvent.getEventFilter() != null) {
            controllerProps.setProperty(
                eventPropPrefix + "." + configEvent.getEventClass() + ".filter", configEvent.getEventFilter());
        }
        if (configEvent.getCustomSerialization() != null) {
            configSerialization(eventPropPrefix, configEvent.getCustomSerialization(), controllerProps);
        }
        configTopicItemList(eventPropPrefix, configEvent.getEventClass(), controllerProps);
    }

    private void configTopicItemList(String itemPrefix, String item, Properties controllerProps) {
        if (controllerProps.getProperty(itemPrefix) == null) {
            controllerProps.setProperty(itemPrefix, item);
        } else {
            controllerProps.setProperty(itemPrefix, "," + item);
        }
    }

    private void configSerialization(
            String propPrefix, ControllerCustomSerialization configCustom, Properties controllerProps) {
        String customPropPrefix = propPrefix + ".custom.gson";
        controllerProps.setProperty(
                customPropPrefix, configCustom.getCustomSerializerClass() + "," + configCustom.getJsonParser());
    }

    private boolean configControllerCustom(ControllerProperties controllerConfig, Properties controllerProps) {
        Map<String, String> configCustom = controllerConfig.getCustomConfig();
        if (configCustom == null || configCustom.isEmpty()) {
            return true;
        }

        controllerProps.putAll(configCustom);
        return true;
    }

    private <T> boolean isDups(List<T> items) {
        return items.size() != items.stream().distinct().count();
    }

    private List<String> sourceTopics(List<ControllerSourceTopic> sourceTopics) {
        if (sourceTopics == null) {
            return Collections.EMPTY_LIST;
        }

        return sourceTopics.stream()
                       .map(ControllerSourceTopic::getTopicName)
                       .collect(Collectors.toList());
    }

    private List<String> sinkTopics(List<ControllerSinkTopic> sinkTopics) {
        if (sinkTopics == null) {
            return Collections.EMPTY_LIST;
        }

        return sinkTopics.stream()
                       .map(ControllerSinkTopic::getTopicName)
                       .collect(Collectors.toList());
    }

}
