/*
 * ============LICENSE_START=======================================================
 * policy-engine
 * ================================================================================
 * Copyright (C) 2017-2019 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.features;

import java.util.Properties;
import org.onap.policy.common.endpoints.event.comm.Topic.CommInfrastructure;
import org.onap.policy.common.utils.services.OrderedService;
import org.onap.policy.drools.protocol.configuration.PdpdConfiguration;
import org.onap.policy.drools.system.PolicyEngine;

/**
 * Policy Engine Feature API.
 * Provides Interception Points during the Policy Engine lifecycle.
 */
public interface PolicyEngineFeatureApi extends OrderedService {

    /**
     * intercept before the Policy Engine is commanded to boot.
     *
     * @return true if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise.
     */
    public default boolean beforeBoot(PolicyEngine engine, String[] cliArgs) {
        return false;
    }

    /**
     * intercept after the Policy Engine is booted.
     *
     * @return true if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise.
     */
    public default boolean afterBoot(PolicyEngine engine) {
        return false;
    }

    /**
     * intercept before the Policy Engine is configured.
     *
     * @return true if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise.
     */
    public default boolean beforeConfigure(PolicyEngine engine, Properties properties) {
        return false;
    }

    /**
     * intercept after the Policy Engine is configured.
     *
     * @return true if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise.
     */
    public default boolean afterConfigure(PolicyEngine engine) {
        return false;
    }

    /**
     * intercept before the Policy Engine goes active.
     *
     * @return true if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise.
     */
    public default boolean beforeActivate(PolicyEngine engine) {
        return false;
    }

    /**
     * intercept after the Policy Engine goes active.
     *
     * @return true if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise.
     */
    public default boolean afterActivate(PolicyEngine engine) {
        return false;
    }

    /**
     * intercept before the Policy Engine goes standby.
     *
     * @return true if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise.
     */
    public default boolean beforeDeactivate(PolicyEngine engine) {
        return false;
    }

    /**
     * intercept after the Policy Engine goes standby.
     *
     * @return true if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise.
     */
    public default boolean afterDeactivate(PolicyEngine engine) {
        return false;
    }

    /**
     * intercept before the Policy Engine is started.
     *
     * @return true if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise.
     */
    public default boolean beforeStart(PolicyEngine engine) {
        return false;
    }

    /**
     * intercept after the Policy Engine is started.
     *
     * @return true if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise.
     */
    public default boolean afterStart(PolicyEngine engine) {
        return false;
    }

    /**
     * intercept before the Policy Engine is stopped.
     *
     * @return true if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise..
     */
    public default boolean beforeStop(PolicyEngine engine) {
        return false;
    }

    /**
     * intercept after the Policy Engine is stopped.
     *
     * @return true if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise.d.
     */
    public default boolean afterStop(PolicyEngine engine) {
        return false;
    }

    /**
     * intercept before the Policy Engine is locked.
     *
     * @return true if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise.
     */
    public default boolean beforeLock(PolicyEngine engine) {
        return false;
    }

    /**
     * intercept after the Policy Engine is locked.
     *
     * @return true if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise..
     */
    public default boolean afterLock(PolicyEngine engine) {
        return false;
    }

    /**
     * intercept before the Policy Engine is locked.
     *
     * @return true if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise.
     */
    public default boolean beforeUnlock(PolicyEngine engine) {
        return false;
    }

    /**
     * intercept after the Policy Engine is locked.
     *
     * @return true if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise.
     */
    public default boolean afterUnlock(PolicyEngine engine) {
        return false;
    }

    /**
     * intercept the Policy Engine is shut down.
     *
     * @return true if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise..
     */
    public default boolean beforeShutdown(PolicyEngine engine) {
        return false;
    }

    /**
     * called after the Policy Engine is shut down.
     *
     * @return true if this feature intercepts and takes ownership
     *     of the operation preventing the invocation of
     *     lower priority features.   False, otherwise.
     */
    public default boolean afterShutdown(PolicyEngine engine) {
        return false;
    }

    /**
     * Intercept an event from UEB/DMaaP before the PolicyEngine processes it.
     *
     * @return True if this feature intercepts and takes ownership of the operation
     *         preventing the invocation of lower priority features. False, otherwise.
     */
    public default boolean beforeOnTopicEvent(PolicyEngine engine, CommInfrastructure commType, String topic,
                    String event) {
        return false;
    }

    /**
     * Called after the PolicyEngine processes the events.
     *
     * @return True if this feature intercepts and takes ownership of the operation
     *         preventing the invocation of lower priority features. False, otherwise
     */
    public default boolean afterOnTopicEvent(PolicyEngine engine, PdpdConfiguration configuration,
                    CommInfrastructure commType, String topic, String event) {
        return false;
    }
}
