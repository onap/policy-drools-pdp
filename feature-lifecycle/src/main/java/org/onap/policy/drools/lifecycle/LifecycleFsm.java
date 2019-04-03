/*
 * ============LICENSE_START=======================================================
 * ONAP
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

package org.onap.policy.drools.lifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.onap.policy.common.capabilities.Startable;
import org.onap.policy.common.endpoints.event.comm.Topic.CommInfrastructure;
import org.onap.policy.common.endpoints.event.comm.TopicEndpoint;
import org.onap.policy.common.endpoints.event.comm.TopicSink;
import org.onap.policy.common.endpoints.event.comm.TopicSource;
import org.onap.policy.common.endpoints.event.comm.client.TopicSinkClient;
import org.onap.policy.common.endpoints.listeners.MessageTypeDispatcher;
import org.onap.policy.common.endpoints.listeners.ScoListener;
import org.onap.policy.common.gson.annotation.GsonJsonIgnore;
import org.onap.policy.common.utils.coder.StandardCoderObject;
import org.onap.policy.common.utils.network.NetworkUtil;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.persistence.SystemPersistence;
import org.onap.policy.models.pdp.concepts.PdpStateChange;
import org.onap.policy.models.pdp.concepts.PdpStatus;
import org.onap.policy.models.pdp.concepts.PolicyTypeIdent;
import org.onap.policy.models.pdp.enums.PdpHealthStatus;
import org.onap.policy.models.pdp.enums.PdpMessageType;
import org.onap.policy.models.pdp.enums.PdpState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lifecycle FSM.
 */
public class LifecycleFsm implements Startable {

    protected static final String CONFIGURATION_PROPERTIES_NAME = "feature-lifecycle";
    protected static final String POLICY_TYPE_VERSION = "1.0.0";
    protected static final long DEFAULT_STATUS_TIMER_SECONDS = 60L;
    protected static final String PDP_MESSAGE_NAME = "messageName";

    private static final Logger logger = LoggerFactory.getLogger(LifecycleFsm.class);

    protected final Properties properties;

    protected TopicSource source;
    protected TopicSinkClient client;

    @Getter
    protected final String name = NetworkUtil.getHostname();

    protected volatile LifecycleState state = new LifecycleStateTerminated(this);

    @GsonJsonIgnore
    protected ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);

    @GsonJsonIgnore
    protected ScheduledFuture<?> statusTask;

    @GsonJsonIgnore
    protected MessageTypeDispatcher sourceDispatcher = new MessageTypeDispatcher(new String[]{PDP_MESSAGE_NAME});

    @GsonJsonIgnore
    protected MessageNameDispatcher nameDispatcher = new MessageNameDispatcher(PdpStateChange.class, this);

    @Getter
    @Setter
    protected long statusTimerSeconds = DEFAULT_STATUS_TIMER_SECONDS;

    @Getter
    protected String group;

    @Getter
    protected String subgroup;

    /**
     * Constructor.
     */
    public LifecycleFsm() {
        this.properties = SystemPersistence.manager.getProperties(CONFIGURATION_PROPERTIES_NAME);

        scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        scheduler.setRemoveOnCancelPolicy(true);
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
        logger.info("lifecycle event: start");
        return state.start();
    }

    @Override
    public synchronized boolean stop() {
        logger.info("lifecycle event: stop");
        return state.stop();
    }

    @Override
    public synchronized void shutdown() {
        logger.info("lifecycle event: shutdown");
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

    public synchronized void stateChange(PdpStateChange stateChange) {
        logger.info("lifecycle event: state-change");
        state.stateChange(stateChange);
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

    protected boolean statusAction(PdpState state) {
        if (!isAlive()) {
            return false;
        }

        return client.send(statusPayload(state));
    }

    protected void setGroupAction(String group, String subgroup) {
        this.group = group;
        this.subgroup = subgroup;
    }

    protected void transitionToAction(@NonNull LifecycleState newState) {
        state = newState;
    }

    /* ** Action Helpers ** */

    private boolean startIo() {
        return source() && sink();
    }

    private boolean startTimers() {
        statusTask =
                this.scheduler.scheduleAtFixedRate(() -> status(), 0, statusTimerSeconds, TimeUnit.SECONDS);
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

    private PdpStatus statusPayload(PdpState state) {
        PdpStatus status = new PdpStatus();
        status.setName(name);
        status.setPdpGroup(group);
        status.setPdpSubgroup(subgroup);
        status.setState(state);
        status.setHealthy(isAlive() ? PdpHealthStatus.HEALTHY : PdpHealthStatus.NOT_HEALTHY);
        status.setPdpType("drools");    // TODO: enum ?
        return status;
    }

    private boolean source() {
        List<TopicSource> sources = TopicEndpoint.manager.addTopicSources(properties);
        if (sources.isEmpty()) {
            return false;
        }

        if (sources.size() != 1) {
            logger.warn("Lifecycle Manager: unexpected: more than one source configured ({})", sources.size());
        }

        this.source = sources.get(0);
        this.source.register(this.sourceDispatcher);
        this.sourceDispatcher.register(PdpMessageType.PDP_STATE_CHANGE.name(), nameDispatcher);
        return source.start();
    }

    private boolean sink() {
        List<TopicSink> sinks = TopicEndpoint.manager.addTopicSinks(properties);
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

    private List<PolicyTypeIdent> getCapabilities() {
        List<PolicyTypeIdent> capabilities = new ArrayList<>();
        for (DroolsController dc : DroolsController.factory.inventory()) {
            if (!dc.isBrained()) {
                continue;
            }

            for (String domain : dc.getBaseDomainNames()) {
                // HACK: until legacy controllers are removed
                if (StringUtils.countMatches(domain, ".") > 1) {
                    capabilities.add(new PolicyTypeIdent(domain, POLICY_TYPE_VERSION));
                } else {
                    logger.info("legacy controller {} with domain {}", dc.getCanonicalSessionNames(), domain);
                }
            }
        }
        return capabilities;
    }

    protected  boolean isMine(PdpStateChange change) {
        if (change == null) {
            return false;
        }

        if (Objects.equals(name, change.getName())) {
            return true;
        }

        return change.getName() == null
            && change.getPdpGroup() != null
            && Objects.equals(group, change.getPdpGroup())
            && Objects.equals(subgroup, change.getPdpSubgroup());
    }

    /* **** IO listeners ***** */

    /**
     * PDP State Change Message Listener.
     */
    public static class MessageNameDispatcher extends ScoListener<PdpStateChange> {

        protected final LifecycleFsm fsm;

        /**
         * Constructor.
         */
        public MessageNameDispatcher(Class<PdpStateChange> clazz, LifecycleFsm fsm) {
            super(clazz);
            this.fsm = fsm;
        }

        @Override
        public void onTopicEvent(CommInfrastructure comm, String topic,
                                 StandardCoderObject coder, PdpStateChange stateChange) {

            if (!fsm.isMine(stateChange)) {
                logger.warn("pdp-state-chage from {}:{} is invalid: {}", comm, topic, stateChange);
                return;
            }

            fsm.stateChange(stateChange);
        }
    }

}
