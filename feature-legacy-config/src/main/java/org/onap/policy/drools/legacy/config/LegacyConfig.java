/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2021 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2024 Nordix Foundation.
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

package org.onap.policy.drools.legacy.config;

import java.util.List;
import java.util.Properties;
import lombok.Getter;
import org.onap.policy.common.capabilities.Startable;
import org.onap.policy.common.message.bus.event.Topic;
import org.onap.policy.common.message.bus.event.TopicEndpointManager;
import org.onap.policy.common.message.bus.event.TopicListener;
import org.onap.policy.common.message.bus.event.TopicSource;
import org.onap.policy.drools.persistence.SystemPersistenceConstants;
import org.onap.policy.drools.system.PolicyEngineConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Legacy Configurator.
 */
@Getter
public class LegacyConfig implements Startable, TopicListener {

    private static final Logger logger = LoggerFactory.getLogger(LegacyConfig.class);
    private static final String CONFIGURATION_PROPERTIES_NAME = "feature-legacy-config";

    private final Properties properties;

    private final TopicSource source;

    /**
     * Constructor.
     */
    public LegacyConfig() {
        properties = SystemPersistenceConstants.getManager().getProperties(CONFIGURATION_PROPERTIES_NAME);
        List<TopicSource> sources = TopicEndpointManager.getManager().addTopicSources(properties);
        if (sources.isEmpty()) {
            throw new IllegalStateException("LegacyConfig cannot be instantiated, no sources");
        }

        this.source = sources.get(0);
        if (sources.size() != 1) {
            logger.warn("LegacyConfig: more than one source is configured ({}), using {}",
                    sources.size(), this.source);
        }

        source.register(this);
    }

    @Override
    public boolean start() {
        return source.start();
    }

    @Override
    public boolean stop() {
        return source.stop();
    }

    @Override
    public void shutdown() {
        source.shutdown();
    }

    @Override
    public boolean isAlive() {
        return source.isAlive();
    }

    @Override
    public void onTopicEvent(Topic.CommInfrastructure comm, String topic, String event) {
        PolicyEngineConstants.getManager().onTopicEvent(comm, topic, event);
    }
}
