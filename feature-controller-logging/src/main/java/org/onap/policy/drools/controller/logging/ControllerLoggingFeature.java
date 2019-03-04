/*
 * ============LICENSE_START=======================================================
 * feature-controller-logging
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

package org.onap.policy.drools.controller.logging;

import org.onap.policy.common.endpoints.event.comm.Topic.CommInfrastructure;
import org.onap.policy.common.endpoints.event.comm.TopicSink;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.features.DroolsControllerFeatureAPI;
import org.onap.policy.drools.features.PolicyControllerFeatureAPI;
import org.onap.policy.drools.features.PolicyEngineFeatureAPI;
import org.onap.policy.drools.protocol.configuration.ControllerConfiguration;
import org.onap.policy.drools.protocol.configuration.PdpdConfiguration;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.drools.system.PolicyEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class hooks the network logging implementation into DroolsPDP. It will disable the
 * default network logger where all topic traffic is logged and segregates the topic
 * traffic by controller for each supported control loop use case.
 */

/*
 * PolicyControllerFeatureAPI - the 'beforeStart' hook is used to shut off the default
 * network logger and the 'beforeOffer' hook is used to log incoming topic messages
 *
 * DroolsControllerFeatureAPI - the 'afterDeliver' hook is where the outgoing topic
 * messages are logged
 *
 */
public class ControllerLoggingFeature
                implements PolicyEngineFeatureAPI, DroolsControllerFeatureAPI, PolicyControllerFeatureAPI {

    @Override
    public int getSequenceNumber() {
        return 1000;
    }

    /**
     * The 'beforeOffer' hook will intercept an incoming topic message and append it to
     * the log file that is configured for the controller logger.
     */
    @Override
    public boolean beforeOffer(PolicyController controller, CommInfrastructure protocol, String topic, String event) {
        Logger controllerLogger = LoggerFactory.getLogger(controller.getName());
        controllerLogger.info("[IN|{}|{}]{}{}", protocol, topic, System.lineSeparator(), event);
        return false;
    }

    /**
     * The 'afterDeliver' hook will intercept an outgoing topic message and append it to
     * the log file that is configured for the controller logger.
     */
    @Override
    public boolean afterDeliver(DroolsController controller, TopicSink sink, Object fact, String json,
                    boolean success) {
        if (success) {
            Logger controllerLogger = LoggerFactory.getLogger(PolicyController.factory.get(controller).getName());
            controllerLogger.info("[OUT|{}|{}]{}{}", sink.getTopicCommInfrastructure(), sink.getTopic(),
                            System.lineSeparator(), json);
        }
        return false;
    }

    /**
     * The 'afterOnTopicEvent' hook will determine which controllers were updated and log
     * the event to the appropriate controller logs.
     */
    @Override
    public boolean afterOnTopicEvent(PolicyEngine engine, PdpdConfiguration configuration, CommInfrastructure commType,
                    String topic, String event) {
        for (ControllerConfiguration controller : configuration.getControllers()) {
            Logger controllerLogger = LoggerFactory.getLogger(controller.getName());
            controllerLogger.info("[IN|{}|{}]{}{}", commType, topic, System.lineSeparator(), event);
        }
        return false;
    }
}
