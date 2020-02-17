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

package org.onap.policy.drools.lifecycle;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.onap.policy.common.capabilities.Startable;
import org.onap.policy.common.endpoints.event.comm.Topic.CommInfrastructure;
import org.onap.policy.common.endpoints.event.comm.TopicEndpointManager;
import org.onap.policy.common.endpoints.event.comm.TopicSink;
import org.onap.policy.common.endpoints.event.comm.TopicSource;
import org.onap.policy.common.endpoints.event.comm.client.TopicSinkClient;
import org.onap.policy.common.endpoints.listeners.MessageTypeDispatcher;
import org.onap.policy.common.endpoints.listeners.ScoListener;
import org.onap.policy.common.gson.annotation.GsonJsonIgnore;
import org.onap.policy.common.utils.coder.StandardCoderObject;
import org.onap.policy.common.utils.network.NetworkUtil;
import org.onap.policy.drools.persistence.SystemPersistenceConstants;
import org.onap.policy.drools.policies.DomainMaker;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.drools.system.PolicyEngineConstants;
import org.onap.policy.models.pdp.concepts.PdpResponseDetails;
import org.onap.policy.models.pdp.concepts.PdpStateChange;
import org.onap.policy.models.pdp.concepts.PdpStatus;
import org.onap.policy.models.pdp.concepts.PdpUpdate;
import org.onap.policy.models.pdp.enums.PdpHealthStatus;
import org.onap.policy.models.pdp.enums.PdpMessageType;
import org.onap.policy.models.pdp.enums.PdpState;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicy;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicyIdentifier;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicyTypeIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lifecycle FSM.
 */
public class LifecycleFsm implements Startable {

    private static final Logger logger = LoggerFactory.getLogger(LifecycleFsm.class);

    protected static final String CONFIGURATION_PROPERTIES_NAME = "feature-lifecycle";
    protected static final String GROUP_NAME = "lifecycle.pdp.group";
    protected static final String DEFAULT_PDP_GROUP = "defaultGroup";
    protected static final long DEFAULT_STATUS_TIMER_SECONDS = 120L;
    protected static final long MIN_STATUS_INTERVAL_SECONDS = 5L;
    protected static final String PDP_MESSAGE_NAME = "messageName";

    protected static final ToscaPolicyTypeIdentifier POLICY_TYPE_DROOLS_NATIVE_RULES =
            new ToscaPolicyTypeIdentifier("onap.policies.native.Drools", "1.0.0");

    protected static final ToscaPolicyTypeIdentifier POLICY_TYPE_DROOLS_CONTROLLER =
            new ToscaPolicyTypeIdentifier("onap.policies.drools.Controller", "1.0.0");

    protected final Properties properties;

    protected TopicSource source;
    protected TopicSinkClient client;

    @Getter
    protected final String name = NetworkUtil.getHostname();

    protected LifecycleState state = new LifecycleStateTerminated(this);

    @GsonJsonIgnore
    protected ScheduledExecutorService scheduler = makeExecutor();

    @GsonJsonIgnore
    protected ScheduledFuture<?> statusTask;

    @GsonJsonIgnore
    protected MessageTypeDispatcher sourceDispatcher = new MessageTypeDispatcher(PDP_MESSAGE_NAME);

    @GsonJsonIgnore
    protected PdpStateChangeFeed stateChangeFeed = new PdpStateChangeFeed(PdpStateChange.class, this);

    @GsonJsonIgnore
    protected PdpUpdateFeed updateFeed = new PdpUpdateFeed(PdpUpdate.class, this);

    @Getter
    @Setter
    protected long statusTimerSeconds = DEFAULT_STATUS_TIMER_SECONDS;

    @Getter
    private final String group;

    @Getter
    protected String subgroup;

    @Getter
    protected final Map<ToscaPolicyTypeIdentifier, PolicyTypeController> policyTypesMap = new HashMap<>();

    protected final Map<ToscaPolicyIdentifier, ToscaPolicy> policiesMap = new HashMap<>();

    /**
     * Constructor.
     */
    public LifecycleFsm() {
        this.properties = SystemPersistenceConstants.getManager().getProperties(CONFIGURATION_PROPERTIES_NAME);
        this.group = this.properties.getProperty(GROUP_NAME, DEFAULT_PDP_GROUP);

        this.policyTypesMap.put(
                POLICY_TYPE_DROOLS_CONTROLLER,
                new PolicyTypeNativeController(this, POLICY_TYPE_DROOLS_CONTROLLER));
        this.policyTypesMap.put(
                POLICY_TYPE_DROOLS_NATIVE_RULES,
                 new PolicyTypeRulesController(this, POLICY_TYPE_DROOLS_NATIVE_RULES));
    }

    @JsonIgnore
    @GsonJsonIgnore
    public DomainMaker getDomainMaker() {
        return PolicyEngineConstants.getManager().getDomainMaker();
    }

    @Override
    public boolean isAlive() {
        return client != null && client.getSink().isAlive();
    }

    /**
     * Current state.
     */
    public PdpState state() {
        return state.state();
    }

    /* ** FSM events - entry points of events into the FSM ** */

    @Override
    public synchronized boolean start() {
        logger.info("lifecycle event: start engine");
        return state.start();
    }

    /**
     * Start a controller event.
     */
    public synchronized void start(@NonNull PolicyController controller) {
        logger.info("lifecycle event: start controller: {}", controller.getName());
        for (ToscaPolicyTypeIdentifier id : controller.getPolicyTypes()) {
            if (isToscaPolicyType(id.getName())) {
                policyTypesMap.put(id, new PolicyTypeDroolsController(this, id, controller));
            }
        }
    }

    @Override
    public synchronized boolean stop() {
        logger.info("lifecycle event: stop engine");
        return state.stop();
    }

    /**
     * Stop a controller event.
     */
    public synchronized void stop(@NonNull PolicyController controller) {
        logger.info("lifecycle event: stop controller: {}", controller.getName());
        for (ToscaPolicyTypeIdentifier id : controller.getPolicyTypes()) {
            policyTypesMap.remove(id);
        }
    }

    @Override
    public synchronized void shutdown() {
        logger.info("lifecycle event: shutdown engine");
        state.shutdown();
    }

    /**
     * Status reporting event.
     * @return true if successful
     */
    public synchronized boolean status() {
        logger.info("lifecycle event: status");
        return state.status();
    }

    public synchronized boolean stateChange(PdpStateChange stateChange) {
        logger.info("lifecycle event: state-change");
        return state.stateChange(stateChange);
    }

    public synchronized boolean update(PdpUpdate update) {
        logger.info("lifecycle event: update");
        return state.update(update);
    }
    /* ** FSM State Actions ** */

    protected boolean startAction() {
        if (isAlive()) {
            return true;
        }

        return startIo() && startTimers();
    }

    protected boolean stopAction() {
        if (!isAlive()) {
            return true;
        }

        boolean successTimers = stopTimers();
        boolean successIo = stopIo();
        return successTimers && successIo;
    }

    protected void shutdownAction() {
        shutdownIo();
        shutdownTimers();
    }

    protected boolean statusAction() {
        return statusAction(state(), null);
    }

    protected boolean statusAction(PdpResponseDetails response) {
        return statusAction(state(), response);
    }

    protected boolean statusAction(PdpState state, PdpResponseDetails response) {
        if (!isAlive()) {
            return false;
        }

        PdpStatus status = statusPayload(state);
        if (response != null) {
            status.setRequestId(response.getResponseTo());     // for standard logging of transactions
            status.setResponse(response);
        }

        return client.send(status);
    }

    protected void setSubGroupAction(String subgroup) {
        this.subgroup = subgroup;
    }

    protected synchronized void transitionToAction(@NonNull LifecycleState newState) {
        state = newState;
    }

    protected boolean setStatusIntervalAction(long intervalSeconds) {
        if (intervalSeconds == statusTimerSeconds || intervalSeconds == 0) {
            return true;
        }

        if (intervalSeconds <= MIN_STATUS_INTERVAL_SECONDS) {
            logger.warn("interval is too low (< {}): {}", MIN_STATUS_INTERVAL_SECONDS, intervalSeconds);
            return false;
        }

        setStatusTimerSeconds(intervalSeconds);
        return stopTimers() && startTimers();
    }

    protected PolicyTypeController getController(ToscaPolicyTypeIdentifier policyType) {
        return policyTypesMap.get(policyType);
    }

    protected List<ToscaPolicy> getDeployablePoliciesAction(@NonNull List<ToscaPolicy> policies) {
        List<ToscaPolicy> deployPolicies = new ArrayList<>(policies);
        deployPolicies.removeAll(policiesMap.values());
        return deployPolicies;
    }

    protected List<ToscaPolicy> getUndeployablePoliciesAction(@NonNull List<ToscaPolicy> policies) {
        List<ToscaPolicy> undeployPolicies = new ArrayList<>(policiesMap.values());
        undeployPolicies.removeAll(policies);
        return undeployPolicies;
    }

    protected void deployedPolicyAction(@NonNull ToscaPolicy policy) {
        policiesMap.put(policy.getIdentifier(), policy);
    }

    protected void undeployedPolicyAction(@NonNull ToscaPolicy policy) {
        policiesMap.remove(policy.getIdentifier());
    }

    protected List<ToscaPolicy> resetPoliciesAction() {
        List<ToscaPolicy> policies = new ArrayList<>(policiesMap.values());
        policiesMap.clear();
        return policies;
    }

    protected boolean updatePoliciesAction(List<ToscaPolicy> toscaPolicies) {
        return (this.scheduler.submit( () -> state.updatePolicies(toscaPolicies)) != null);
    }

    /* ** Action Helpers ** */

    private boolean startIo() {
        return source() && sink();
    }

    private boolean startTimers() {
        statusTask =
                this.scheduler.scheduleAtFixedRate(this::status, 0, statusTimerSeconds, TimeUnit.SECONDS);
        return !statusTask.isCancelled() && !statusTask.isDone();
    }

    private boolean stopIo() {
        source.unregister(sourceDispatcher);
        boolean successSource = source.stop();
        boolean successSink = client.getSink().stop();
        return successSource && successSink;
    }

    private boolean stopTimers() {
        boolean success = true;
        if (statusTask != null) {
            success = statusTask.cancel(false);
        }

        return success;
    }

    private void shutdownIo() {
        client.getSink().shutdown();
        source.shutdown();
    }

    private void shutdownTimers() {
        scheduler.shutdownNow();
    }

    private PdpStatus statusPayload(@NonNull PdpState state) {
        PdpStatus status = new PdpStatus();
        status.setName(name);
        status.setPdpGroup(group);
        status.setPdpSubgroup(subgroup);
        status.setState(state);
        status.setHealthy(isAlive() ? PdpHealthStatus.HEALTHY : PdpHealthStatus.NOT_HEALTHY);
        status.setPdpType("drools");
        status.setPolicies(new ArrayList<>(policiesMap.keySet()));
        return status;
    }

    private boolean source() {
        List<TopicSource> sources = TopicEndpointManager.getManager().addTopicSources(properties);
        if (sources.isEmpty()) {
            return false;
        }

        if (sources.size() != 1) {
            logger.warn("Lifecycle Manager: unexpected: more than one source configured ({})", sources.size());
        }

        this.source = sources.get(0);
        this.source.register(this.sourceDispatcher);
        this.sourceDispatcher.register(PdpMessageType.PDP_STATE_CHANGE.name(), stateChangeFeed);
        this.sourceDispatcher.register(PdpMessageType.PDP_UPDATE.name(), updateFeed);
        return source.start();
    }

    private boolean sink() {
        List<TopicSink> sinks = TopicEndpointManager.getManager().addTopicSinks(properties);
        if (sinks.isEmpty()) {
            logger.error("Lifecycle Manager sinks have not been configured");
            return false;
        }

        if (sinks.size() != 1) {
            logger.warn("Lifecycle Manager: unexpected: more than one sink configured ({})", sinks.size());
        }

        this.client = new TopicSinkClient(sinks.get(0));
        return this.client.getSink().start();
    }

    protected boolean isToscaPolicyType(String domain) {
        // HACK: until legacy controllers support is removed
        return StringUtils.countMatches(domain, ".") > 1;
    }

    protected boolean isItMe(String name, String group, String subgroup) {
        if (Objects.equals(name, getName())) {
            return true;
        }

        return name == null && group != null
            && Objects.equals(group, getGroup())
            && Objects.equals(subgroup, getSubgroup());
    }

    /* **** IO listeners ***** */

    /**
     * PDP State Change Message Listener.
     */
    public static class PdpStateChangeFeed extends ScoListener<PdpStateChange> {

        protected final LifecycleFsm fsm;

        protected PdpStateChangeFeed(Class<PdpStateChange> clazz, LifecycleFsm fsm) {
            super(clazz);
            this.fsm = fsm;
        }

        @Override
        public void onTopicEvent(CommInfrastructure comm, String topic,
                                 StandardCoderObject coder, PdpStateChange stateChange) {

            if (!isMine(stateChange)) {
                logger.warn("pdp-state-chage from {}:{} is invalid: {}", comm, topic, stateChange);
                return;
            }

            fsm.stateChange(stateChange);
        }

        protected boolean isMine(PdpStateChange change) {
            if (change == null) {
                return false;
            }

            return fsm.isItMe(change.getName(), change.getPdpGroup(), change.getPdpSubgroup());
        }
    }

    /**
     * PDP Update Message Listener.
     */
    public static class PdpUpdateFeed extends ScoListener<PdpUpdate> {

        protected final LifecycleFsm fsm;

        public PdpUpdateFeed(Class<PdpUpdate> clazz, LifecycleFsm fsm) {
            super(clazz);
            this.fsm = fsm;
        }

        @Override
        public void onTopicEvent(CommInfrastructure comm, String topic,
                                 StandardCoderObject coder, PdpUpdate update) {

            if (!isMine(update)) {
                logger.warn("pdp-update from {}:{} is invalid: {}", comm, topic, update);
                return;
            }

            fsm.update(update);
        }

        protected boolean isMine(PdpUpdate update) {
            if (update == null) {
                return false;
            }

            return fsm.isItMe(update.getName(), update.getPdpGroup(), update.getPdpSubgroup());
        }
    }

    // these may be overridden by junit tests

    protected ScheduledExecutorService makeExecutor() {
        ScheduledThreadPoolExecutor exec = new ScheduledThreadPoolExecutor(1);
        exec.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        exec.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        exec.setRemoveOnCancelPolicy(true);

        return exec;
    }
}
