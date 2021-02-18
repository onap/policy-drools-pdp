/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2017-2021 AT&T Intellectual Property. All rights reserved.
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

import java.util.List;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import org.onap.policy.common.capabilities.Lockable;
import org.onap.policy.common.capabilities.Startable;
import org.onap.policy.common.endpoints.event.comm.Topic.CommInfrastructure;
import org.onap.policy.common.endpoints.event.comm.TopicListener;
import org.onap.policy.common.endpoints.event.comm.TopicSink;
import org.onap.policy.common.endpoints.event.comm.TopicSource;
import org.onap.policy.common.endpoints.http.server.HttpServletServer;
import org.onap.policy.drools.core.lock.Lock;
import org.onap.policy.drools.core.lock.LockCallback;
import org.onap.policy.drools.features.PolicyEngineFeatureApi;
import org.onap.policy.drools.metrics.Metric;
import org.onap.policy.drools.policies.DomainMaker;
import org.onap.policy.drools.protocol.configuration.ControllerConfiguration;
import org.onap.policy.drools.protocol.configuration.PdpdConfiguration;
import org.onap.policy.drools.stats.PolicyStatsManager;

/**
 * Policy Engine, the top abstraction for the Drools PDP Policy Engine. It abstracts away a Drools
 * PDP Engine from management purposes. This is the best place to looking at the code from a top
 * down approach. Other managed entities can be obtained from the PolicyEngine, hierarchically. <br>
 * PolicyEngine 1 --- * PolicyController 1 --- 1 DroolsController 1 --- 1 PolicyContainer 1 --- *
 * PolicySession <br> PolicyEngine 1 --- 1 TopicEndpointManager 1 -- * TopicReader 1 --- 1
 * UebTopicReader <br> PolicyEngine 1 --- 1 TopicEndpointManager 1 -- * TopicReader 1 --- 1
 * DmaapTopicReader <br> PolicyEngine 1 --- 1 TopicEndpointManager 1 -- * TopicWriter 1 --- 1
 * DmaapTopicWriter <br> PolicyEngine 1 --- 1 TopicEndpointManager 1 -- * TopicReader 1 --- 1
 * RestTopicReader <br> PolicyEngine 1 --- 1 TopicEndpointManager 1 -- * TopicWriter 1 --- 1
 * RestTopicWriter <br> PolicyEngine 1 --- 1 ManagementServer
 */
public interface PolicyEngine extends Startable, Lockable, TopicListener {

    /**
     * Boot the engine.
     *
     * @param cliArgs command line arguments
     */
    void boot(String[] cliArgs);

    /**
     * configure the policy engine according to the given properties.
     *
     * @param properties Policy Engine properties
     * @throws IllegalArgumentException when invalid or insufficient properties are provided
     */
    void configure(Properties properties);

    /**
     * updates the Policy Engine with the given configuration.
     *
     * @param configuration the configuration
     * @return success or failure
     * @throws IllegalArgumentException if invalid argument provided
     * @throws IllegalStateException    if the system is in an invalid state
     */
    boolean configure(PdpdConfiguration configuration);

    /**
     * open the Policy Engine to external configuration systems.
     *
     * @return success or failure
     */
    boolean open();


    /**
     * configure the engine's environment. General lab installation configuration is made available
     * to the Engine. Typically, custom lab installation that may be needed by arbitrary drools
     * applications are made available, for example network component and database host addresses.
     * Multiple environments can be passed in and tracked by the engine.
     *
     * @param properties an environment properties
     */
    void setEnvironment(Properties properties);

    /**
     * gets the engine's environment.
     *
     * @return properties object
     */
    Properties getEnvironment();

    /**
     * gets an environment's value, by 1) first from the engine's environment, and 2) from the OS
     * environment.
     *
     * @param key environment key
     * @return environment value or null if absent
     */
    String getEnvironmentProperty(String key);

    /**
     * sets an engine's environment property.
     *
     * @param key key
     * @param value value
     * @return property string
     */
    String setEnvironmentProperty(String key, String value);

    /**
     * registers a new Policy Controller with the Policy Engine initialized per properties.
     *
     * @param properties properties to initialize the Policy Controller
     * @return the newly instantiated Policy Controller
     * @throws IllegalArgumentException when invalid or insufficient properties are provided
     * @throws IllegalStateException    when the engine is in a state where this operation is not
     *                                  permitted.
     */
    PolicyController createPolicyController(String name, Properties properties);

    /**
     * updates a set of Policy Controllers with configuration information.
     *
     * @param configuration list of configurations
     * @return list of controllers
     * @throws IllegalArgumentException exception
     * @throws IllegalStateException exception
     */
    List<PolicyController> updatePolicyControllers(List<ControllerConfiguration> configuration);

    /**
     * updates an already existing Policy Controller with configuration information.
     *
     * @param configuration configuration
     * @return the updated Policy Controller
     * @throws IllegalArgumentException in the configuration is invalid
     * @throws IllegalStateException    if the controller is in a bad state
     */
    PolicyController updatePolicyController(ControllerConfiguration configuration);

    /**
     * removes the Policy Controller identified by its name from the Policy Engine.
     *
     * @param name name of the Policy Controller
     */
    void removePolicyController(String name);

    /**
     * removes a Policy Controller from the Policy Engine.
     *
     * @param controller the Policy Controller to remove from the Policy Engine
     */
    void removePolicyController(PolicyController controller);

    /**
     * returns a list of the available Policy Controllers.
     *
     * @return list of Policy Controllers
     */
    List<PolicyController> getPolicyControllers();


    /**
     * get policy controller names.
     *
     * @return list of controller names
     */
    List<String> getPolicyControllerIds();

    /**
     * get unmanaged sources.
     *
     * @return unmanaged sources
     */
    List<TopicSource> getSources();

    /**
     * get unmanaged sinks.
     *
     * @return unmanaged sinks
     */
    List<TopicSink> getSinks();

    /**
     * get unmmanaged http servers list.
     *
     * @return http servers
     */
    List<HttpServletServer> getHttpServers();

    /**
     * Gets a thread pool that can be used to execute background tasks.
     */
    ScheduledExecutorService getExecutorService();

    /**
     * get properties configuration.
     *
     * @return properties objects
     */
    Properties getProperties();

    /**
     * get features attached to the Policy Engine.
     *
     * @return list of features
     */
    List<PolicyEngineFeatureApi> getFeatureProviders();

    /**
     * get named feature attached to the Policy Engine.
     *
     * @return the feature
     */
    PolicyEngineFeatureApi getFeatureProvider(String featureName);

    /**
     * get features attached to the Policy Engine.
     *
     * @return list of features
     */
    List<String> getFeatures();

    /**
     * get domain maker.
     *
     * @return the domain maker
     */
    DomainMaker getDomainMaker();

    /**
     * get statistics for this PDP.
     *
     * @return statistics
     */
    PolicyStatsManager getStats();

    /**
     * Attempts the dispatching of an "event" object.
     *
     * @param topic topic
     * @param event the event object to send
     * @return true if successful, false if a failure has occurred.
     * @throws IllegalArgumentException when invalid or insufficient properties are provided
     * @throws IllegalStateException    when the engine is in a state where this operation is not
     *                                  permitted (ie. locked or stopped).
     */
    boolean deliver(String topic, Object event);

    /**
     * Attempts the dispatching of an "event" object over communication infrastructure "busType".
     *
     * @param topic topic
     * @param event the event object to send
     * @return true if successful, false if a failure has occurred.
     * @throws IllegalArgumentException      when invalid or insufficient properties are provided
     * @throws IllegalStateException         when the engine is in a state where this operation is not
     *                                       permitted (ie. locked or stopped).
     * @throws UnsupportedOperationException when the engine cannot deliver due to the functionality
     *                                       missing (ie. communication infrastructure not supported.
     */
    boolean deliver(String busType, String topic, Object event);

    /**
     * Attempts the dispatching of an "event" object over communication infrastructure "busType".
     *
     * @param topic topic
     * @param event the event object to send
     * @return true if successful, false if a failure has occurred.
     * @throws IllegalArgumentException      when invalid or insufficient properties are provided
     * @throws IllegalStateException         when the engine is in a state where this operation is not
     *                                       permitted (ie. locked or stopped).
     * @throws UnsupportedOperationException when the engine cannot deliver due to the functionality
     *                                       missing (ie. communication infrastructure not supported.
     */
    boolean deliver(CommInfrastructure busType, String topic, Object event);

    /**
     * Attempts delivering of an String over communication infrastructure "busType".
     *
     * @param topic topic
     * @param event the event object to send
     * @return true if successful, false if a failure has occurred.
     * @throws IllegalArgumentException      when invalid or insufficient properties are provided
     * @throws IllegalStateException         when the engine is in a state where this operation is not
     *                                       permitted (ie. locked or stopped).
     * @throws UnsupportedOperationException when the engine cannot deliver due to the functionality
     *                                       missing (ie. communication infrastructure not supported.
     */
    boolean deliver(CommInfrastructure busType, String topic, String event);

    /**
     * Requests a lock on a resource. Typically, the lock is not immediately granted,
     * though a "lock" object is always returned. Once the lock has been granted (or
     * denied), the callback will be invoked to indicate the result.
     *
     * <p/>
     * Notes:
     * <dl>
     * <li>The callback may be invoked <i>before</i> this method returns</li>
     * <li>The implementation need not honor waitForLock={@code true}</li>
     * </dl>
     *
     * @param resourceId identifier of the resource to be locked
     * @param ownerKey information identifying the owner requesting the lock
     * @param holdSec amount of time, in seconds, for which the lock should be held once
     *        it has been granted, after which it will automatically be released
     * @param callback callback to be invoked once the lock is granted, or subsequently
     *        lost; must not be {@code null}
     * @param waitForLock {@code true} to wait for the lock, if it is currently locked,
     *        {@code false} otherwise
     * @return a new lock
     */
    Lock createLock(String resourceId, String ownerKey, int holdSec, LockCallback callback,
        boolean waitForLock);

    /**
     * Invoked when the host goes into the active state.
     */
    void activate();

    /**
     * Invoked when the host goes into the standby state.
     */
    void deactivate();

    /**
     * produces a default telemetry configuration.
     *
     * @return policy engine configuration
     */
    Properties defaultTelemetryConfig();

    /**
     * Track a policy execution metric.
     *
     * @param controllerName controller name
     * @param policyName policy name
     * @param metric metric
     */
    void metric(String controllerName, String policyName, Metric metric);

    /**
     * Track a policy execution transaction.
     *
     * @param controllerName controller name
     * @param policyName policy name
     * @param transaction transaction
     */
    void transaction(String controllerName, String policyName, Metric transaction);
}
