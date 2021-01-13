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

package org.onap.policy.drools.controller.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.drools.core.ClassObjectFilter;
import org.kie.api.definition.KiePackage;
import org.kie.api.definition.rule.Query;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.runtime.rule.QueryResults;
import org.kie.api.runtime.rule.QueryResultsRow;
import org.onap.policy.common.endpoints.event.comm.TopicSink;
import org.onap.policy.common.gson.annotation.GsonJsonIgnore;
import org.onap.policy.common.gson.annotation.GsonJsonProperty;
import org.onap.policy.common.utils.services.FeatureApiUtils;
import org.onap.policy.common.utils.services.OrderedServiceImpl;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.controller.DroolsControllerConstants;
import org.onap.policy.drools.core.PolicyContainer;
import org.onap.policy.drools.core.PolicySession;
import org.onap.policy.drools.core.jmx.PdpJmx;
import org.onap.policy.drools.features.DroolsControllerFeatureApi;
import org.onap.policy.drools.features.DroolsControllerFeatureApiConstants;
import org.onap.policy.drools.protocol.coders.EventProtocolCoder;
import org.onap.policy.drools.protocol.coders.EventProtocolCoderConstants;
import org.onap.policy.drools.protocol.coders.EventProtocolParams;
import org.onap.policy.drools.protocol.coders.JsonProtocolFilter;
import org.onap.policy.drools.protocol.coders.TopicCoderFilterConfiguration;
import org.onap.policy.drools.protocol.coders.TopicCoderFilterConfiguration.CustomGsonCoder;
import org.onap.policy.drools.protocol.coders.TopicCoderFilterConfiguration.PotentialCoderFilter;
import org.onap.policy.drools.utils.ReflectionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maven-based Drools Controller that interacts with the
 * policy-core PolicyContainer and PolicySession to manage
 * Drools containers instantiated using Maven.
 */
public class MavenDroolsController implements DroolsController {

    private static final String FACT_RETRIEVE_ERROR = "Object cannot be retrieved from fact {}";

    /**
     * logger.
     */
    private static Logger  logger = LoggerFactory.getLogger(MavenDroolsController.class);

    /**
     * Policy Container, the access object to the policy-core layer.
     */
    @GsonJsonIgnore
    protected final PolicyContainer policyContainer;

    /**
     * alive status of this drools controller,
     * reflects invocation of start()/stop() only.
     */
    protected volatile boolean alive = false;

    /**
     * locked status of this drools controller,
     * reflects if i/o drools related operations are permitted,
     * more specifically: offer() and deliver().
     * It does not affect the ability to start and stop
     * underlying drools infrastructure
     */
    protected volatile boolean locked = false;

    /**
     * list of topics, each with associated decoder classes, each
     * with a list of associated filters.
     */
    protected List<TopicCoderFilterConfiguration> decoderConfigurations;

    /**
     * list of topics, each with associated encoder classes, each
     * with a list of associated filters.
     */
    protected List<TopicCoderFilterConfiguration> encoderConfigurations;

    /**
     * recent source events processed.
     */
    protected final CircularFifoQueue<Object> recentSourceEvents = new CircularFifoQueue<>(10);

    /**
     * recent sink events processed.
     */
    protected final CircularFifoQueue<String> recentSinkEvents = new CircularFifoQueue<>(10);

    /**
     * original Drools Model/Rules classloader hash.
     */
    protected int modelClassLoaderHash;

    /**
     * Expanded version of the constructor.
     *
     * @param groupId maven group id
     * @param artifactId maven artifact id
     * @param version maven version
     * @param decoderConfigurations list of topic -> decoders -> filters mapping
     * @param encoderConfigurations list of topic -> encoders -> filters mapping
     *
     * @throws IllegalArgumentException invalid arguments passed in
     */
    public MavenDroolsController(String groupId,
            String artifactId,
            String version,
            List<TopicCoderFilterConfiguration> decoderConfigurations,
            List<TopicCoderFilterConfiguration> encoderConfigurations) {

        logger.info("drools-controller instantiation [{}:{}:{}]", groupId, artifactId, version);

        if (groupId == null || groupId.isEmpty()) {
            throw new IllegalArgumentException("Missing maven group-id coordinate");
        }

        if (artifactId == null || artifactId.isEmpty()) {
            throw new IllegalArgumentException("Missing maven artifact-id coordinate");
        }

        if (version == null || version.isEmpty()) {
            throw new IllegalArgumentException("Missing maven version coordinate");
        }

        this.policyContainer = makePolicyContainer(groupId, artifactId, version);
        this.init(decoderConfigurations, encoderConfigurations);

        logger.debug("{}: instantiation completed ", this);
    }

    /**
     * init encoding/decoding configuration.
     *
     * @param decoderConfigurations list of topic -> decoders -> filters mapping
     * @param encoderConfigurations list of topic -> encoders -> filters mapping
     */
    protected void init(List<TopicCoderFilterConfiguration> decoderConfigurations,
            List<TopicCoderFilterConfiguration> encoderConfigurations) {

        this.decoderConfigurations = decoderConfigurations;
        this.encoderConfigurations = encoderConfigurations;

        this.initCoders(decoderConfigurations, true);
        this.initCoders(encoderConfigurations, false);

        this.modelClassLoaderHash = this.policyContainer.getClassLoader().hashCode();
    }

    @Override
    public void updateToVersion(String newGroupId, String newArtifactId, String newVersion,
            List<TopicCoderFilterConfiguration> decoderConfigurations,
            List<TopicCoderFilterConfiguration> encoderConfigurations)
                    throws LinkageError {

        logger.info("updating version -> [{}:{}:{}]", newGroupId, newArtifactId, newVersion);

        validateText(newGroupId, "Missing maven group-id coordinate");
        validateText(newArtifactId, "Missing maven artifact-id coordinate");
        validateText(newVersion, "Missing maven version coordinate");

        validateHasBrain(newGroupId, newArtifactId, newVersion);

        if (newGroupId.equalsIgnoreCase(this.getGroupId())
                && newArtifactId.equalsIgnoreCase(this.getArtifactId())
                        && newVersion.equalsIgnoreCase(this.getVersion())) {
            logger.warn("All in the right version: {}:{}:{} vs. {}", newGroupId, newArtifactId, newVersion, this);
            return;
        }

        validateNewVersion(newGroupId, newArtifactId, newVersion);

        /* upgrade */
        String messages = this.policyContainer.updateToVersion(newVersion);
        logger.warn("{} UPGRADE results: {}", this, messages);

        /*
         * If all sucessful (can load new container), now we can remove all coders from previous sessions
         */
        this.removeCoders();

        /*
         * add the new coders
         */
        this.init(decoderConfigurations, encoderConfigurations);

        logger.info("UPDATE-TO-VERSION: completed {}", this);
    }

    private void validateText(String text, String errorMessage) {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private void validateHasBrain(String newGroupId, String newArtifactId, String newVersion) {
        if (newGroupId.equalsIgnoreCase(DroolsControllerConstants.NO_GROUP_ID)
                || newArtifactId.equalsIgnoreCase(DroolsControllerConstants.NO_ARTIFACT_ID)
                || newVersion.equalsIgnoreCase(DroolsControllerConstants.NO_VERSION)) {
            throw new IllegalArgumentException("BRAINLESS maven coordinates provided: "
                    + newGroupId + ":" + newArtifactId + ":"
                    + newVersion);
        }
    }

    private void validateNewVersion(String newGroupId, String newArtifactId, String newVersion) {
        if (!newGroupId.equalsIgnoreCase(this.getGroupId())
                || !newArtifactId.equalsIgnoreCase(this.getArtifactId())) {
            throw new IllegalArgumentException(
                    "Group ID and Artifact ID maven coordinates must be identical for the upgrade: "
                    + newGroupId + ":" + newArtifactId + ":"
                    + newVersion + " vs. " + this);
        }
    }

    /**
     * initialize decoders for all the topics supported by this controller
     * Note this is critical to be done after the Policy Container is
     * instantiated to be able to fetch the corresponding classes.
     *
     * @param coderConfigurations list of topic -> decoders -> filters mapping
     */
    protected void initCoders(List<TopicCoderFilterConfiguration> coderConfigurations,
            boolean decoder) {

        logger.info("INIT-CODERS: {}", this);

        if (coderConfigurations == null) {
            return;
        }


        for (TopicCoderFilterConfiguration coderConfig: coderConfigurations) {
            String topic = coderConfig.getTopic();

            CustomGsonCoder customGsonCoder = getCustomCoder(coderConfig);

            List<PotentialCoderFilter> coderFilters = coderConfig.getCoderFilters();
            if (coderFilters == null || coderFilters.isEmpty()) {
                continue;
            }

            for (PotentialCoderFilter coderFilter : coderFilters) {
                String potentialCodedClass = coderFilter.getCodedClass();
                JsonProtocolFilter protocolFilter = coderFilter.getFilter();

                if (!isClass(potentialCodedClass)) {
                    throw makeRetrieveEx(potentialCodedClass);
                } else {
                    logClassFetched(potentialCodedClass);
                }

                if (decoder) {
                    getCoderManager().addDecoder(EventProtocolParams.builder()
                            .groupId(this.getGroupId())
                            .artifactId(this.getArtifactId())
                            .topic(topic)
                            .eventClass(potentialCodedClass)
                            .protocolFilter(protocolFilter)
                            .customGsonCoder(customGsonCoder)
                            .modelClassLoaderHash(this.policyContainer.getClassLoader().hashCode()));
                } else {
                    getCoderManager().addEncoder(
                            EventProtocolParams.builder().groupId(this.getGroupId())
                                    .artifactId(this.getArtifactId()).topic(topic)
                                    .eventClass(potentialCodedClass).protocolFilter(protocolFilter)
                                    .customGsonCoder(customGsonCoder)
                                    .modelClassLoaderHash(this.policyContainer.getClassLoader().hashCode()));
                }
            }
        }
    }

    private CustomGsonCoder getCustomCoder(TopicCoderFilterConfiguration coderConfig) {
        CustomGsonCoder customGsonCoder = coderConfig.getCustomGsonCoder();
        if (customGsonCoder != null
                && customGsonCoder.getClassContainer() != null
                && !customGsonCoder.getClassContainer().isEmpty()) {

            String customGsonCoderClass = customGsonCoder.getClassContainer();
            if (!isClass(customGsonCoderClass)) {
                throw makeRetrieveEx(customGsonCoderClass);
            } else {
                logClassFetched(customGsonCoderClass);
            }
        }
        return customGsonCoder;
    }

    /**
     * Logs an error and makes an exception for an item that cannot be retrieved.
     * @param itemName the item to retrieve
     * @return a new exception
     */
    private IllegalArgumentException makeRetrieveEx(String itemName) {
        logger.error("{} cannot be retrieved", itemName);
        return new IllegalArgumentException(itemName + " cannot be retrieved");
    }

    /**
     * Logs the name of the class that was fetched.
     * @param className class name fetched
     */
    private void logClassFetched(String className) {
        logger.info("CLASS FETCHED {}", className);
    }


    /**
     * remove decoders.
     */
    protected void removeDecoders() {
        logger.info("REMOVE-DECODERS: {}", this);

        if (this.decoderConfigurations == null) {
            return;
        }


        for (TopicCoderFilterConfiguration coderConfig: decoderConfigurations) {
            String topic = coderConfig.getTopic();
            getCoderManager().removeDecoders(this.getGroupId(), this.getArtifactId(), topic);
        }
    }

    /**
     * remove decoders.
     */
    protected void removeEncoders() {

        logger.info("REMOVE-ENCODERS: {}", this);

        if (this.encoderConfigurations == null) {
            return;
        }

        for (TopicCoderFilterConfiguration coderConfig: encoderConfigurations) {
            String topic = coderConfig.getTopic();
            getCoderManager().removeEncoders(this.getGroupId(), this.getArtifactId(), topic);
        }
    }


    @Override
    public boolean ownsCoder(Class<?> coderClass, int modelHash) {
        if (!isClass(coderClass.getName())) {
            logger.error("{}{} cannot be retrieved. ", this, coderClass.getName());
            return false;
        }

        if (modelHash == this.modelClassLoaderHash) {
            logger.info("{}{} class loader matches original drools controller rules classloader {}",
                            coderClass.getName(), this, coderClass.getClassLoader());
            return true;
        } else {
            logger.warn("{}{} class loaders don't match {} vs {}", this, coderClass.getName(),
                            coderClass.getClassLoader(), this.policyContainer.getClassLoader());
            return false;
        }
    }

    @Override
    public boolean start() {

        logger.info("START: {}", this);

        synchronized (this) {
            if (this.alive) {
                return true;
            }
            this.alive = true;
        }

        return this.policyContainer.start();
    }

    @Override
    public boolean stop() {

        logger.info("STOP: {}", this);

        synchronized (this) {
            if (!this.alive) {
                return true;
            }
            this.alive = false;
        }

        return this.policyContainer.stop();
    }

    @Override
    public void shutdown() {
        logger.info("{}: SHUTDOWN", this);

        try {
            this.stop();
            this.removeCoders();
        } catch (Exception e) {
            logger.error("{} SHUTDOWN FAILED because of {}", this, e.getMessage(), e);
        } finally {
            this.policyContainer.shutdown();
        }

    }

    @Override
    public void halt() {
        logger.info("{}: HALT", this);

        try {
            this.stop();
            this.removeCoders();
        } catch (Exception e) {
            logger.error("{} HALT FAILED because of {}", this, e.getMessage(), e);
        } finally {
            this.policyContainer.destroy();
        }
    }

    /**
     * removes this drools controllers and encoders and decoders from operation.
     */
    protected void removeCoders() {
        logger.info("{}: REMOVE-CODERS", this);

        try {
            this.removeDecoders();
        } catch (IllegalArgumentException e) {
            logger.error("{} REMOVE-DECODERS FAILED because of {}", this, e.getMessage(), e);
        }

        try {
            this.removeEncoders();
        } catch (IllegalArgumentException e) {
            logger.error("{} REMOVE-ENCODERS FAILED because of {}", this, e.getMessage(), e);
        }
    }

    @Override
    public boolean isAlive() {
        return this.alive;
    }

    @Override
    public boolean offer(String topic, String event) {
        logger.debug("{}: OFFER raw event from {}", this, topic);

        if (this.locked || !this.alive || this.policyContainer.getPolicySessions().isEmpty()) {
            return true;
        }

        // 1. Now, check if this topic has a decoder:

        if (!getCoderManager().isDecodingSupported(this.getGroupId(),
                this.getArtifactId(),
                topic)) {

            logger.warn("{}: DECODING-UNSUPPORTED {}:{}:{}", this,
                    topic, this.getGroupId(), this.getArtifactId());
            return true;
        }

        // 2. Decode

        Object anEvent;
        try {
            anEvent = getCoderManager().decode(this.getGroupId(),
                    this.getArtifactId(),
                    topic,
                    event);
        } catch (UnsupportedOperationException uoe) {
            logger.debug("{}: DECODE FAILED: {} <- {} because of {}", this, topic,
                    event, uoe.getMessage(), uoe);
            return true;
        } catch (Exception e) {
            logger.warn("{}: DECODE FAILED: {} <- {} because of {}", this, topic,
                    event, e.getMessage(), e);
            return true;
        }

        return offer(anEvent);

    }

    /*
     * This method always returns "true", which causes a sonar complaint. However,
     * refactoring or restructuring it would unnecessarily complicate it, thus we'll just
     * disable the sonar complaint.
     */
    @Override
    public <T> boolean offer(T event) {     // NOSONAR
        logger.debug("{}: OFFER event", this);

        if (this.locked || !this.alive || this.policyContainer.getPolicySessions().isEmpty()) {
            return true;
        }

        synchronized (this.recentSourceEvents) {
            this.recentSourceEvents.add(event);
        }

        PdpJmx.getInstance().updateOccured();

        // Broadcast

        if (FeatureApiUtils.apply(getDroolsProviders().getList(),
            feature -> feature.beforeInsert(this, event),
            (feature, ex) -> logger.error("{}: feature {} before-insert failure because of {}", this,
                            feature.getClass().getName(), ex.getMessage(), ex))) {
            return true;
        }

        boolean successInject = this.policyContainer.insertAll(event);
        if (!successInject) {
            logger.warn("{} Failed to inject into PolicyContainer {}", this, this.getSessionNames());
        }

        FeatureApiUtils.apply(getDroolsProviders().getList(),
            feature -> feature.afterInsert(this, event, successInject),
            (feature, ex) -> logger.error("{}: feature {} after-insert failure because of {}", this,
                            feature.getClass().getName(), ex.getMessage(), ex));

        return true;

    }

    @Override
    public boolean deliver(TopicSink sink, Object event) {

        logger.info("{}DELIVER: {} FROM {} TO {}", this, event, this, sink);

        for (DroolsControllerFeatureApi feature : getDroolsProviders().getList()) {
            try {
                if (feature.beforeDeliver(this, sink, event)) {
                    return true;
                }
            } catch (Exception e) {
                logger.error("{}: feature {} before-deliver failure because of {}", this, feature.getClass().getName(),
                        e.getMessage(), e);
            }
        }

        if (sink == null) {
            throw new IllegalArgumentException(this +  " invalid sink");
        }

        if (event == null) {
            throw new IllegalArgumentException(this +  " invalid event");
        }

        if (this.locked) {
            throw new IllegalStateException(this +  " is locked");
        }

        if (!this.alive) {
            throw new IllegalStateException(this +  " is stopped");
        }

        String json =
                getCoderManager().encode(sink.getTopic(), event, this);

        synchronized (this.recentSinkEvents) {
            this.recentSinkEvents.add(json);
        }

        boolean success = sink.send(json);

        for (DroolsControllerFeatureApi feature : getDroolsProviders().getList()) {
            try {
                if (feature.afterDeliver(this, sink, event, json, success)) {
                    return true;
                }
            } catch (Exception e) {
                logger.error("{}: feature {} after-deliver failure because of {}", this, feature.getClass().getName(),
                        e.getMessage(), e);
            }
        }

        return success;

    }

    @Override
    public String getVersion() {
        return this.policyContainer.getVersion();
    }

    @Override
    public String getArtifactId() {
        return this.policyContainer.getArtifactId();
    }

    @Override
    public String getGroupId() {
        return this.policyContainer.getGroupId();
    }

    /**
     * Get model class loader hash.
     *
     * @return the modelClassLoaderHash
     */
    public int getModelClassLoaderHash() {
        return modelClassLoaderHash;
    }

    @Override
    public synchronized boolean lock() {
        logger.info("LOCK: {}",  this);

        this.locked = true;
        return true;
    }

    @Override
    public synchronized boolean unlock() {
        logger.info("UNLOCK: {}",  this);

        this.locked = false;
        return true;
    }

    @Override
    public boolean isLocked() {
        return this.locked;
    }

    @GsonJsonIgnore
    @Override
    public PolicyContainer getContainer() {
        return this.policyContainer;
    }

    @GsonJsonProperty("sessions")
    @Override
    public List<String> getSessionNames() {
        return getSessionNames(true);
    }

    /**
     * get session names.
     *
     * @param abbreviated true for the short form, otherwise the long form
     * @return session names
     */
    protected List<String> getSessionNames(boolean abbreviated) {
        List<String> sessionNames = new ArrayList<>();
        try {
            for (PolicySession session: this.policyContainer.getPolicySessions()) {
                if (abbreviated) {
                    sessionNames.add(session.getName());
                } else {
                    sessionNames.add(session.getFullName());
                }
            }
        } catch (Exception e) {
            logger.warn("Can't retrieve CORE sessions", e);
            sessionNames.add(e.getMessage());
        }
        return sessionNames;
    }

    @GsonJsonProperty("sessionCoordinates")
    @Override
    public List<String> getCanonicalSessionNames() {
        return getSessionNames(false);
    }

    @Override
    public List<String> getBaseDomainNames() {
        return new ArrayList<>(this.policyContainer.getKieContainer().getKieBaseNames());
    }

    /**
     * provides the underlying core layer container sessions.
     *
     * @return the attached Policy Container
     */
    protected List<PolicySession> getSessions() {
        List<PolicySession> sessions = new ArrayList<>();
        sessions.addAll(this.policyContainer.getPolicySessions());
        return sessions;
    }

    /**
     * provides the underlying core layer container session with name sessionName.
     *
     * @param sessionName session name
     * @return the attached Policy Container
     * @throws IllegalArgumentException when an invalid session name is provided
     * @throws IllegalStateException when the drools controller is in an invalid state
     */
    protected PolicySession getSession(String sessionName) {
        if (sessionName == null || sessionName.isEmpty()) {
            throw new IllegalArgumentException("A Session Name must be provided");
        }

        List<PolicySession> sessions = this.getSessions();
        for (PolicySession session : sessions) {
            if (sessionName.equals(session.getName()) || sessionName.equals(session.getFullName())) {
                return session;
            }
        }

        throw invalidSessNameEx(sessionName);
    }

    private IllegalArgumentException invalidSessNameEx(String sessionName) {
        return new IllegalArgumentException("Invalid Session Name: " + sessionName);
    }

    @Override
    public Map<String, Integer> factClassNames(String sessionName) {
        validateSessionName(sessionName);

        Map<String, Integer> classNames = new HashMap<>();

        PolicySession session = getSession(sessionName);
        KieSession kieSession = session.getKieSession();

        Collection<FactHandle> facts = kieSession.getFactHandles();
        for (FactHandle fact : facts) {
            try {
                String className = kieSession.getObject(fact).getClass().getName();
                if (classNames.containsKey(className)) {
                    classNames.put(className, classNames.get(className) + 1);
                } else {
                    classNames.put(className, 1);
                }
            } catch (Exception e) {
                logger.warn(FACT_RETRIEVE_ERROR, fact, e);
            }
        }

        return classNames;
    }

    private void validateSessionName(String sessionName) {
        if (sessionName == null || sessionName.isEmpty()) {
            throw invalidSessNameEx(sessionName);
        }
    }

    @Override
    public long factCount(String sessionName) {
        validateSessionName(sessionName);

        PolicySession session = getSession(sessionName);
        return session.getKieSession().getFactCount();
    }

    @Override
    public List<Object> facts(String sessionName, String className, boolean delete) {
        validateSessionName(sessionName);

        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("Invalid Class Name: " + className);
        }

        Class<?> factClass =
                ReflectionUtil.fetchClass(this.policyContainer.getClassLoader(), className);
        if (factClass == null) {
            throw new IllegalArgumentException("Class cannot be fetched in model's classloader: " + className);
        }

        PolicySession session = getSession(sessionName);
        KieSession kieSession = session.getKieSession();

        List<Object> factObjects = new ArrayList<>();

        Collection<FactHandle> factHandles = kieSession.getFactHandles(new ClassObjectFilter(factClass));
        for (FactHandle factHandle : factHandles) {
            try {
                factObjects.add(kieSession.getObject(factHandle));
                if (delete) {
                    kieSession.delete(factHandle);
                }
            } catch (Exception e) {
                logger.warn(FACT_RETRIEVE_ERROR, factHandle, e);
            }
        }

        return factObjects;
    }

    @Override
    public <T> List<T> facts(@NonNull String sessionName, @NonNull Class<T> clazz) {
        return facts(sessionName, clazz.getName(), false)
            .stream()
            .filter(clazz::isInstance)
            .map(clazz::cast)
            .collect(Collectors.toList());
    }

    @Override
    public List<Object> factQuery(String sessionName, String queryName, String queriedEntity,
            boolean delete, Object... queryParams) {
        validateSessionName(sessionName);

        if (queryName == null || queryName.isEmpty()) {
            throw new IllegalArgumentException("Invalid Query Name: " + queryName);
        }

        if (queriedEntity == null || queriedEntity.isEmpty()) {
            throw new IllegalArgumentException("Invalid Queried Entity: " + queriedEntity);
        }

        PolicySession session = getSession(sessionName);
        KieSession kieSession = session.getKieSession();

        validateQueryName(kieSession, queryName);

        List<Object> factObjects = new ArrayList<>();

        QueryResults queryResults = kieSession.getQueryResults(queryName, queryParams);
        for (QueryResultsRow row : queryResults) {
            try {
                factObjects.add(row.get(queriedEntity));
                if (delete) {
                    kieSession.delete(row.getFactHandle(queriedEntity));
                }
            } catch (Exception e) {
                logger.warn("Object cannot be retrieved from row: {}", row, e);
            }
        }

        return factObjects;
    }

    private void validateQueryName(KieSession kieSession, String queryName) {
        for (KiePackage kiePackage : kieSession.getKieBase().getKiePackages()) {
            for (Query q : kiePackage.getQueries()) {
                if (q.getName() != null && q.getName().equals(queryName)) {
                    return;
                }
            }
        }

        throw new IllegalArgumentException("Invalid Query Name: " + queryName);
    }

    @Override
    public <T> boolean delete(@NonNull String sessionName, @NonNull T fact) {
        String factClassName = fact.getClass().getName();

        PolicySession session = getSession(sessionName);
        KieSession kieSession = session.getKieSession();

        Collection<FactHandle> factHandles = kieSession.getFactHandles(new ClassObjectFilter(fact.getClass()));
        for (FactHandle factHandle : factHandles) {
            try {
                if (Objects.equals(fact, kieSession.getObject(factHandle))) {
                    logger.info("Deleting {} from {}", factClassName, sessionName);
                    kieSession.delete(factHandle);
                    return true;
                }
            } catch (Exception e) {
                logger.warn(FACT_RETRIEVE_ERROR, factHandle, e);
            }
        }
        return false;
    }

    @Override
    public <T> boolean delete(@NonNull T fact) {
        return this.getSessionNames().stream().map(ss -> delete(ss, fact)).reduce(false, Boolean::logicalOr);
    }

    @Override
    public <T> boolean delete(@NonNull String sessionName, @NonNull Class<T> fact) {
        PolicySession session = getSession(sessionName);
        KieSession kieSession = session.getKieSession();

        boolean success = true;
        Collection<FactHandle> factHandles = kieSession.getFactHandles(new ClassObjectFilter(fact));
        for (FactHandle factHandle : factHandles) {
            try {
                kieSession.delete(factHandle);
            } catch (Exception e) {
                logger.warn(FACT_RETRIEVE_ERROR, factHandle, e);
                success = false;
            }
        }
        return success;
    }

    @Override
    public <T> boolean delete(@NonNull Class<T> fact) {
        return this.getSessionNames().stream().map(ss -> delete(ss, fact)).reduce(false, Boolean::logicalOr);
    }

    @Override
    public <T> boolean exists(@NonNull String sessionName, @NonNull T fact) {
        KieSession kieSession = getSession(sessionName).getKieSession();

        Collection<FactHandle> factHandles = kieSession.getFactHandles(new ClassObjectFilter(fact.getClass()));
        for (FactHandle factHandle : factHandles) {
            return Objects.equals(fact, kieSession.getObject(factHandle));
        }

        return false;
    }

    @Override
    public <T> boolean exists(@NonNull T fact) {
        return this.getSessionNames().stream().map(ss -> exists(ss, fact)).reduce(false, Boolean::logicalOr);
    }

    @Override
    public Class<?> fetchModelClass(String className) {
        return ReflectionUtil.fetchClass(this.policyContainer.getClassLoader(), className);
    }

    /**
     * Get recent source events.
     *
     * @return the recentSourceEvents
     */
    @Override
    public Object[] getRecentSourceEvents() {
        synchronized (this.recentSourceEvents) {
            Object[] events = new Object[recentSourceEvents.size()];
            return recentSourceEvents.toArray(events);
        }
    }

    /**
     * Get recent sink events.
     *
     * @return the recentSinkEvents
     */
    @Override
    public String[] getRecentSinkEvents() {
        synchronized (this.recentSinkEvents) {
            String[] events = new String[recentSinkEvents.size()];
            return recentSinkEvents.toArray(events);
        }
    }

    @Override
    public boolean isBrained() {
        return true;
    }


    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder
            .append("MavenDroolsController [policyContainer=")
            .append(policyContainer.getName())
            .append(":")
            .append(", alive=")
            .append(alive)
            .append(", locked=")
            .append(", modelClassLoaderHash=")
            .append(modelClassLoaderHash)
            .append("]");
        return builder.toString();
    }

    // these may be overridden by junit tests

    protected EventProtocolCoder getCoderManager() {
        return EventProtocolCoderConstants.getManager();
    }

    protected OrderedServiceImpl<DroolsControllerFeatureApi> getDroolsProviders() {
        return DroolsControllerFeatureApiConstants.getProviders();
    }

    protected PolicyContainer makePolicyContainer(String groupId, String artifactId, String version) {
        return new PolicyContainer(groupId, artifactId, version);
    }

    protected boolean isClass(String className) {
        return ReflectionUtil.isClass(this.policyContainer.getClassLoader(), className);
    }
}
