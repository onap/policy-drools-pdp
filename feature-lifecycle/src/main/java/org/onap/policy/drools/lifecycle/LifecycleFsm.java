/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2019-2021 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2021 Nordix Foundation.
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

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.apache.commons.beanutils.BeanUtils;
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
import org.onap.policy.drools.metrics.Metric;
import org.onap.policy.drools.persistence.SystemPersistenceConstants;
import org.onap.policy.drools.policies.DomainMaker;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.drools.system.PolicyEngineConstants;
import org.onap.policy.models.pdp.concepts.PdpResponseDetails;
import org.onap.policy.models.pdp.concepts.PdpStateChange;
import org.onap.policy.models.pdp.concepts.PdpStatistics;
import org.onap.policy.models.pdp.concepts.PdpStatus;
import org.onap.policy.models.pdp.concepts.PdpUpdate;
import org.onap.policy.models.pdp.enums.PdpHealthStatus;
import org.onap.policy.models.pdp.enums.PdpMessageType;
import org.onap.policy.models.pdp.enums.PdpState;
import org.onap.policy.models.tosca.authorative.concepts.ToscaConceptIdentifier;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lifecycle FSM.
 */
public class LifecycleFsm implements Startable {

    /**
     * Default Status Timer in seconds.
     */
    public static final long DEFAULT_STATUS_TIMER_SECONDS = 120L;

    private static final Logger logger = LoggerFactory.getLogger(LifecycleFsm.class);

    protected static final String CONFIGURATION_PROPERTIES_NAME = "feature-lifecycle";
    protected static final String GROUP_NAME = "lifecycle.pdp.group";
    protected static final String MANDATORY_POLICY_TYPES = "lifecycle.pdp.policytypes";
    protected static final String DEFAULT_PDP_GROUP = "defaultGroup";
    protected static final long MIN_STATUS_INTERVAL_SECONDS = 5L;
    protected static final String PDP_MESSAGE_NAME = "messageName";

    protected static final ToscaConceptIdentifier POLICY_TYPE_DROOLS_NATIVE_RULES =
            new ToscaConceptIdentifier("onap.policies.native.drools.Artifact", "1.0.0");

    protected static final ToscaConceptIdentifier POLICY_TYPE_DROOLS_NATIVE_CONTROLLER =
            new ToscaConceptIdentifier("onap.policies.native.drools.Controller", "1.0.0");

    @Getter
    protected final Properties properties;

    @Getter
    protected TopicSource source;

    @Getter
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
    private String group;

    @Getter
    protected String subGroup;

    @Getter
    protected Set<String> mandatoryPolicyTypes = new HashSet<>();

    @Getter
    protected final Map<ToscaConceptIdentifier, PolicyTypeController> policyTypesMap = new HashMap<>();

    @Getter
    protected final Map<ToscaConceptIdentifier, ToscaPolicy> policiesMap = new HashMap<>();

    @Getter
    protected final PdpStatistics stats = new PdpStatistics();

    /**
     * Constructor.
     */
    public LifecycleFsm() {
        properties = SystemPersistenceConstants.getManager().getProperties(CONFIGURATION_PROPERTIES_NAME);
        setGroup(properties.getProperty(GROUP_NAME, DEFAULT_PDP_GROUP));

        policyTypesMap.put(POLICY_TYPE_DROOLS_NATIVE_CONTROLLER,
                new PolicyTypeNativeDroolsController(this, POLICY_TYPE_DROOLS_NATIVE_CONTROLLER));
        policyTypesMap.put(
                POLICY_TYPE_DROOLS_NATIVE_RULES,
                 new PolicyTypeNativeArtifactController(this, POLICY_TYPE_DROOLS_NATIVE_RULES));

        String commaSeparatedPolicyTypes = properties.getProperty(MANDATORY_POLICY_TYPES);
        if (!StringUtils.isBlank(commaSeparatedPolicyTypes)) {
            Collections.addAll(mandatoryPolicyTypes, commaSeparatedPolicyTypes.split("\\s*,\\s*"));
        }

        logger.info("The mandatory Policy Types are {}. Compliance is {}",
                mandatoryPolicyTypes, isMandatoryPolicyTypesCompliant());

        stats.setPdpInstanceId(Metric.HOSTNAME);
    }

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

    /**
     * set group.
     */
    public synchronized void setGroup(String group) {
        this.group = group;
        this.stats.setPdpGroupName(group);
    }

    /**
     * set subgroup.
     */
    public synchronized void setSubGroup(String subGroup) {
        this.subGroup = subGroup;
        this.stats.setPdpSubGroupName(subGroup);
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
        if (!controller.getDrools().isBrained()) {
            logger.warn("ignoring lifecycle event: start controller: {}", controller);
            return;
        }

        for (ToscaConceptIdentifier id : controller.getPolicyTypes()) {
            PolicyTypeDroolsController ptDc = (PolicyTypeDroolsController) policyTypesMap.get(id); //NOSONAR
            if (ptDc == null) {
                policyTypesMap.put(id, new PolicyTypeDroolsController(this, id, controller));
                logger.info("policy-type {} added", id);
            } else {
                ptDc.add(controller);
            }
        }
    }

    /**
     * Patch a controller event.
     */
    public synchronized void patch(@NonNull PolicyController controller) {
        logger.info("lifecycle event: patch controller: {}", controller.getName());
        if (controller.getDrools().isBrained()) {
            this.start(controller);
        } else {
            this.stop(controller);
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

        List<PolicyTypeDroolsController> opControllers =
            policyTypesMap.values().stream()
                .filter(typeController -> typeController instanceof PolicyTypeDroolsController)
                .map(PolicyTypeDroolsController.class::cast)
                .filter(opController -> opController.getControllers().containsKey(controller.getName()))
                .collect(Collectors.toList());

        for (PolicyTypeDroolsController opController : opControllers) {
            opController.remove(controller);
            if (opController.controllers().isEmpty()) {
                policyTypesMap.remove(opController.getPolicyType());
                logger.info("policy-type {} removed", opController.getPolicyType());
            }
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

    /* FSM State Actions (executed sequentially) */

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
        return statusAction(null);
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
            status.setRequestId(response.getResponseTo());
            status.setResponse(response);
        }

        return client.send(status);
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

    protected List<ToscaPolicy> getDeployablePoliciesAction(@NonNull List<ToscaPolicy> policies) {
        List<ToscaPolicy> deployPolicies = new ArrayList<>(policies);
        deployPolicies.removeAll(getActivePolicies());

        // Ensure that the sequence of policy deployments is sane to minimize potential errors,
        // First policies to deploy are the controller related ones, those that affect the lifecycle of
        // controllers, starting with the ones that affect the existence of the controller (native controller),
        // second the ones that "brain" the controller with application logic (native artifacts).
        // Lastly the application specific ones such as operational policies.

        // group policies by policy types
        Map<String, List<ToscaPolicy>> policyTypeGroups = groupPoliciesByPolicyType(deployPolicies);

        // place native controller policies at the start of the list
        List<ToscaPolicy> orderedDeployableList = new ArrayList<>(
                policyTypeGroups.getOrDefault(POLICY_TYPE_DROOLS_NATIVE_CONTROLLER.getName(), Collections.emptyList()));

        // add to the working list the native controller policies
        orderedDeployableList.addAll(
            policyTypeGroups.getOrDefault(POLICY_TYPE_DROOLS_NATIVE_RULES.getName(), Collections.emptyList()));

        // place non-native policies to place at the end of the list
        orderedDeployableList.addAll(getNonNativePolicies(policyTypeGroups));

        return orderedDeployableList;
    }

    protected List<ToscaPolicy> getUndeployablePoliciesAction(@NonNull List<ToscaPolicy> policies) {
        List<ToscaPolicy> undeployPolicies = new ArrayList<>(getActivePolicies());
        undeployPolicies.removeAll(policies);
        if (undeployPolicies.isEmpty()) {
            return undeployPolicies;
        }

        // Ensure that the sequence of policy undeployments is sane to minimize potential errors,
        // as it is assumed not smart ordering from the policies sent by the PAP.
        // First policies to undeploy are those that are only of relevance within a drools container,
        // such as the operational policies.   The next set of policies to undeploy are those that
        // affect the overall PDP-D application support, firstly the ones that supports the
        // application software wiring (native rules policies), and second those that relate
        // to the PDP-D controllers lifecycle.

        // group policies by policy types
        Map<String, List<ToscaPolicy>> policyTypeGroups = groupPoliciesByPolicyType(undeployPolicies);

        // place controller only (non-native policies) at the start of the list of the undeployment list
        List<ToscaPolicy> orderedUndeployableList = getNonNativePolicies(policyTypeGroups);

        // add to the working list the native rules policies if any
        orderedUndeployableList.addAll(
            policyTypeGroups.getOrDefault(POLICY_TYPE_DROOLS_NATIVE_RULES.getName(), Collections.emptyList()));

        // finally add to the working list native controller policies if any
        orderedUndeployableList.addAll(
            policyTypeGroups.getOrDefault(POLICY_TYPE_DROOLS_NATIVE_CONTROLLER.getName(), Collections.emptyList()));

        return orderedUndeployableList;
    }

    protected void deployedPolicyAction(@NonNull ToscaPolicy policy) {
        if (!policiesMap.containsKey(policy.getIdentifier())) {
            // avoid counting reapplies in a second pass when a mix of native and non-native
            // policies are present.
            getStats().setPolicyDeployCount(getStats().getPolicyDeployCount() + 1);
            getStats().setPolicyDeploySuccessCount(getStats().getPolicyDeploySuccessCount() + 1);
            policiesMap.put(policy.getIdentifier(), policy);
        }
    }

    protected void undeployedPolicyAction(@NonNull ToscaPolicy policy) {
        if (policiesMap.containsKey(policy.getIdentifier())) {
            // avoid counting reapplies in a second pass when a mix of native and non-native
            // policies are present.
            getStats().setPolicyDeployCount(getStats().getPolicyDeployCount() + 1);
            getStats().setPolicyDeploySuccessCount(getStats().getPolicyDeploySuccessCount() + 1);
            policiesMap.remove(policy.getIdentifier());
        }
    }

    protected void failedDeployPolicyAction(ToscaPolicy failedPolicy) {
        getStats().setPolicyDeployCount(getStats().getPolicyDeployCount() + 1);
        getStats().setPolicyDeployFailCount(getStats().getPolicyDeployFailCount() + 1);
    }

    protected void failedUndeployPolicyAction(ToscaPolicy failedPolicy) {
        failedDeployPolicyAction(failedPolicy);
        policiesMap.remove(failedPolicy.getIdentifier());
    }

    protected void updateDeployCountsAction(Long deployCount, Long deploySuccesses, Long deployFailures) {
        PdpStatistics statistics = getStats();
        if (deployCount != null) {
            statistics.setPolicyDeployCount(deployCount);
        }

        if (deploySuccesses != null) {
            statistics.setPolicyDeploySuccessCount(deploySuccesses);
        }

        if (deployFailures != null) {
            statistics.setPolicyDeployFailCount(deployFailures);
        }
    }

    protected void resetDeployCountsAction() {
        getStats().setPolicyDeployCount(0);
        getStats().setPolicyDeployFailCount(0);
        getStats().setPolicyDeploySuccessCount(0);
    }

    protected List<ToscaPolicy> resetPoliciesAction() {
        resetDeployCountsAction();
        List<ToscaPolicy> policies = new ArrayList<>(getActivePolicies());
        policiesMap.clear();
        return policies;
    }

    protected void updatePoliciesAction(List<ToscaPolicy> toscaPolicies) {
        this.scheduler.submit(() -> state.updatePolicies(toscaPolicies));
    }

    protected PolicyTypeController getController(ToscaConceptIdentifier policyType) {
        return policyTypesMap.get(policyType);
    }

    protected Map<String, List<ToscaPolicy>> groupPoliciesByPolicyType(List<ToscaPolicy> deployPolicies) {
        return deployPolicies.stream()
            .distinct()
            .collect(Collectors.groupingBy(policy -> policy.getTypeIdentifier().getName()));
    }

    protected List<ToscaPolicy> getNonNativePolicies(@NonNull Map<String, List<ToscaPolicy>> policyTypeGroups) {
        return policyTypeGroups.entrySet().stream()
            .filter(entry -> !entry.getKey().equals(POLICY_TYPE_DROOLS_NATIVE_RULES.getName())
                && !entry.getKey().equals(POLICY_TYPE_DROOLS_NATIVE_CONTROLLER.getName()))
            .flatMap(entry -> entry.getValue().stream()).collect(Collectors.toList());
    }

    protected List<ToscaPolicy> getNativeArtifactPolicies(@NonNull Map<String, List<ToscaPolicy>> policyTypeGroups) {
        return policyTypeGroups.entrySet().stream()
            .filter(entry -> entry.getKey().equals(POLICY_TYPE_DROOLS_NATIVE_RULES.getName()))
            .flatMap(entry -> entry.getValue().stream()).collect(Collectors.toList());
    }

    protected List<ToscaPolicy> getNativeControllerPolicies(@NonNull Map<String, List<ToscaPolicy>> policyTypeGroups) {
        return policyTypeGroups.entrySet().stream()
                       .filter(entry -> entry.getKey().equals(POLICY_TYPE_DROOLS_NATIVE_CONTROLLER.getName()))
                       .flatMap(entry -> entry.getValue().stream()).collect(Collectors.toList());
    }

    protected String getPolicyIdsMessage(List<ToscaPolicy> policies) {
        return policies.stream()
                       .distinct()
                       .map(ToscaPolicy::getIdentifier).collect(Collectors.toList())
                       .toString();
    }

    /**
     * Do I support the mandatory policy types?.
     */
    protected boolean isMandatoryPolicyTypesCompliant() {
        return getCurrentPolicyTypes().containsAll(getMandatoryPolicyTypes());
    }

    protected Set<String> getCurrentPolicyTypes() {
        return getPolicyTypesMap().keySet().stream()
                       .map(ToscaConceptIdentifier::getName).collect(Collectors.toSet());
    }

    protected List<ToscaPolicy> getActivePolicies() {
        return new ArrayList<>(policiesMap.values());
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

    protected PdpStatus statusPayload(@NonNull PdpState state) {
        PdpStatus status = new PdpStatus();
        status.setName(name);
        status.setPdpGroup(group);
        status.setPdpSubgroup(subGroup);
        status.setState(state);
        status.setHealthy(isAlive() ? PdpHealthStatus.HEALTHY : PdpHealthStatus.NOT_HEALTHY);
        status.setPdpType("drools");
        status.setPolicies(new ArrayList<>(policiesMap.keySet()));
        status.setStatistics(statisticsPayload());
        return status;
    }

    private PdpStatistics statisticsPayload() {
        PdpStatistics updateStats = new PdpStatistics(stats);
        updateStats.setTimeStamp(new Date());

        try {
            BeanUtils.copyProperties(updateStats, PolicyEngineConstants.getManager().getStats());
        } catch (IllegalAccessException | InvocationTargetException ex) {
            logger.debug("statistics mapping failure", ex);
        }

        return updateStats;
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

    protected boolean isItMe(String name, String group, String subgroup) {
        if (Objects.equals(name, getName())) {
            return true;
        }

        return name == null && group != null
            && Objects.equals(group, getGroup())
            && Objects.equals(subgroup, getSubGroup());
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
