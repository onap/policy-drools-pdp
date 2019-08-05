/*
 * ============LICENSE_START=======================================================
 * feature-mdc-filters
 * ================================================================================
 * Copyright (C) 2019 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.mdc.filters;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.onap.policy.common.endpoints.event.comm.Topic;
import org.onap.policy.common.endpoints.event.comm.Topic.CommInfrastructure;
import org.onap.policy.common.endpoints.features.NetLoggerFeatureApi;
import org.onap.policy.common.endpoints.utils.NetLoggerUtil.EventType;
import org.onap.policy.drools.features.PolicyControllerFeatureApi;
import org.onap.policy.drools.persistence.SystemPersistenceConstants;
import org.onap.policy.drools.system.PolicyController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class MdcFilterFeature implements NetLoggerFeatureApi, PolicyControllerFeatureApi {

    /**
     * Logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(MdcFilterFeature.class);

    /**
     * Feature properties.
     */
    public static final String FEATURE_NAME = "feature-mdc-filters";
    public static final String SOURCE = "source";
    public static final String SINK = "sink";
    public static final String MDC_FILTERS = ".mdcFilters";

    /**
     * Mapping of 'protocol:type:topic' key to a 'MdcTopicFilter' object.
     */
    private Map<String, MdcTopicFilter> topicFilters = new HashMap<>();

    /**
     * Feature properties map obtained from the feature properties file.
     */
    private Properties featureProps = null;

    /**
     * Constructor.
     */
    public MdcFilterFeature() {
        super();
        featureProps = getFeatureProps();
    }

    /**
     * Gets the feature properties.
     *
     * @return the properties for this feature.
     */
    protected Properties getFeatureProps() {
        return SystemPersistenceConstants.getManager().getProperties(FEATURE_NAME);
    }

    /**
     * Sequence number to be used for order of feature implementer execution.
     */
    @Override
    public int getSequenceNumber() {
        return 1;
    }

    /**
     * Loops through all source and sink topics to find which topics have mdc filters and
     * inserts an MdcTopicFilter in to the topicFilters map.
     */
    @Override
    public boolean afterCreate(PolicyController controller) {
        createSourceTopicFilters(controller);
        createSinkTopicFilters(controller);
        return false;
    }

    /**
     * Extracts the fields in a JSON string that are to be logged in an abbreviated
     * message. The event delivery infrastructure details are put in the MDC as well using
     * the keys networkEventType (IN/OUT), networkProtocol (UEB/DMAAP/NOOP/REST), and
     * networkTopic.
     */
    @Override
    public boolean beforeLog(Logger eventLogger, EventType type, CommInfrastructure protocol, String topic,
                    String message) {

        String filterKey = null;
        if (type == EventType.IN) {
            filterKey = getTopicKey(protocol.name().toLowerCase(), SOURCE, topic);
        } else {
            filterKey = getTopicKey(protocol.name().toLowerCase(), SINK, topic);
        }

        MDC.put("networkEventType", type.name());
        MDC.put("networkProtocol", protocol.name());
        MDC.put("networkTopic", topic);

        MdcTopicFilter filter = topicFilters.get(filterKey);
        if (filter != null) {
            for (Map.Entry<String, List<String>> entry : filter.find(message).entrySet()) {
                String mdcKey = entry.getKey();
                List<String> results = entry.getValue();
                if (results.isEmpty()) {
                    logger.debug("No results found for key {}", mdcKey);
                } else if (results.size() > 1) {
                    logger.debug("Multple results found for key {}, returning list as a string", mdcKey);
                    MDC.put(mdcKey, results.toString());
                } else {
                    MDC.put(mdcKey, results.get(0));
                }
            }
        } else {
            logger.debug("No mdc topic filters exist for key {}", filterKey);
        }

        return false;
    }

    /**
     * Clears the MDC mapping after a message is logged.
     */
    @Override
    public boolean afterLog(Logger eventLogger, EventType type, CommInfrastructure protocol, String topic,
                    String message) {
        MDC.clear();
        return false;
    }

    /**
     * Creates a key using the protocol, type, and topic name.
     *
     * @param protocol defined as ueb, dmaap, noop
     * @param type defined as source or sink
     * @param topic name of the topic
     * @return a key that is the concatenation of the protocol, type, and topic name
     */
    private String getTopicKey(String protocol, String type, String topic) {
        return protocol + ":" + type + ":" + topic;
    }

    /**
     * Creates MdcTopicFilters for a source/sink topic based on the type.
     *
     * @param topic the topic name
     * @param type 'source' or 'sink'
     */
    private void createTopicFilter(Topic topic, String type) {
        String protocol = topic.getTopicCommInfrastructure().name().toLowerCase();
        String topicName = topic.getTopic();

        String propertyKey = protocol + "." + type + ".topics." + topicName + MDC_FILTERS;
        String propertyValue = featureProps.getProperty(propertyKey);
        if (propertyValue != null) {
            String topicKey = getTopicKey(protocol, type, topicName);
            if (!topicFilters.containsKey(topicKey)) {
                logger.debug("MdcTopicFilter created for {} {} topic {}", protocol, type, topicName);
                topicFilters.put(topicKey, new MdcTopicFilter(propertyValue));
            } else {
                logger.debug("An MdcTopicFilter already exists for key {}", topicKey);
            }
        } else {
            logger.debug("No MDC filters defined for {} {} topic {}", protocol, type, topicName);
        }
    }

    /**
     * Creates MdcTopicFilters for the controller's source topics.
     */
    private void createSourceTopicFilters(PolicyController controller) {
        controller.getTopicSources().forEach(sourceTopic -> createTopicFilter(sourceTopic, SOURCE));
    }

    /**
     * Creates MdcTopicFilters for the controller's sink topics.
     */
    private void createSinkTopicFilters(PolicyController controller) {
        controller.getTopicSinks().forEach(sinkTopic -> createTopicFilter(sinkTopic, SINK));
    }
}
