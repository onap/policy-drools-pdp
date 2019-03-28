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
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.onap.policy.common.capabilities.Startable;
import org.onap.policy.common.endpoints.event.comm.Topic.CommInfrastructure;
import org.onap.policy.common.endpoints.event.comm.TopicEndpoint;
import org.onap.policy.common.endpoints.event.comm.TopicListener;
import org.onap.policy.common.endpoints.event.comm.TopicSink;
import org.onap.policy.common.endpoints.event.comm.TopicSource;
import org.onap.policy.common.endpoints.event.comm.client.TopicSinkClient;
import org.onap.policy.common.gson.annotation.GsonJsonIgnore;
import org.onap.policy.common.utils.network.NetworkUtil;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.persistence.SystemPersistence;
import org.onap.policy.models.pdp.concepts.PdpStatus;
import org.onap.policy.models.pdp.concepts.PolicyTypeIdent;
import org.onap.policy.models.pdp.enums.PdpHealthStatus;
import org.onap.policy.models.pdp.enums.PdpState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lifecycle FSM.
 */
public class LifecycleFsm implements Startable, TopicListener {

    public static final String CONFIGURATION_PROPERTIES_NAME = "feature-lifecycle";
    public static final String POLICY_TYPE_VERSION = "1.0.0";

    private static final Logger logger = LoggerFactory.getLogger(LifecycleFsm.class);

    protected final Properties properties;

    protected TopicSource source;
    protected TopicSinkClient client;

    protected LifecycleState state = new LifecycleStateTerminated(this);

    @GsonJsonIgnore
    protected ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);

    @GsonJsonIgnore
    protected ScheduledFuture<?> statusTask;

    /**
     * Constructor.
     */
    public LifecycleFsm() {
        this.properties = SystemPersistence.manager.getProperties(CONFIGURATION_PROPERTIES_NAME);

        scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        scheduler.setRemoveOnCancelPolicy(true);
    }

    /**
     * Transition to a new state.
     *
     * @param newState new state
     */
    public void changeState(@NonNull LifecycleState newState) {
        state = newState;
    }

    @Override
    public boolean isAlive() {
        return client != null && client.getSink().isAlive();
    }

    /* ** FSM events ** */

    @Override
    public boolean start() {
        return state.start();
    }

    @Override
    public boolean stop() {
        return state.stop();
    }

    @Override
    public void shutdown() {
        state.shutdown();
    }

    /**
     * Status reporting event.
     * @return true if successful
     */
    public boolean status() {
        return state.status();
    }

    /* ** FSM Actions ** */

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

    /* FSM Action Helpers */

    private boolean startIo() {
        return source() && sink();
    }

    private boolean startTimers() {
        statusTask =
                this.scheduler.scheduleAtFixedRate(() -> status(), 0, 60, TimeUnit.SECONDS);
        return statusTask != null;
    }

    private boolean stopIo() {
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
        status.setRequestId(UUID.randomUUID().toString());
        status.setTimestampMs(System.currentTimeMillis());
        status.setInstance(NetworkUtil.getHostname());
        status.setState(state);
        status.setHealthy(isAlive() ? PdpHealthStatus.HEALTHY : PdpHealthStatus.NOT_HEALTHY);
        status.setPdpType("drools");    // TODO: enum ?
        status.setSupportedPolicyTypes(getCapabilities());
        status.setPolicies(Collections.emptyList());    // TODO
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
        this.source.register(this);
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

    /* IO listeners */

    @Override
    public void onTopicEvent(CommInfrastructure commInfrastructure, String topic, String event) {
        // TODO
    }
}
