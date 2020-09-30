/*
 * ============LICENSE_START=======================================================
 * policy-core
 * ================================================================================
 * Copyright (C) 2017-2020 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2018 Samsung Electronics Co., Ltd.
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

package org.onap.policy.drools.core;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.KieScanner;
import org.kie.api.builder.Message;
import org.kie.api.builder.ReleaseId;
import org.kie.api.builder.Results;
import org.kie.api.definition.KiePackage;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.onap.policy.common.capabilities.Startable;
import org.onap.policy.drools.util.KieUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is a wrapper around 'KieContainer', which adds the ability to automatically create and
 * track KieSession instances.
 */
public class PolicyContainer implements Startable {
    // get an instance of logger
    private static Logger logger = LoggerFactory.getLogger(PolicyContainer.class);
    // 'KieServices' singleton
    private static KieServices kieServices = KieServices.Factory.get();

    // set of all 'PolicyContainer' instances
    private static HashSet<PolicyContainer> containers = new HashSet<>();

    // maps feature objects to per-PolicyContainer data
    private ConcurrentHashMap<Object, Object> adjuncts = new ConcurrentHashMap<>();

    // 'KieContainer' associated with this 'PolicyContainer'
    private KieContainer kieContainer;

    // indicates whether the PolicyContainer is 'started'
    // (started = sessions created, threads running)
    private volatile boolean isStarted = false;

    // maps session name into the associated 'PolicySession' instance
    private HashMap<String, PolicySession> sessions = new HashMap<>();

    // if not null, this is a 'KieScanner' looking for updates
    private KieScanner scanner = null;

    // indicates whether the scanner has been started
    // (it can block for a long time)
    private boolean scannerStarted = false;

    private static final String ERROR_STRING = "ERROR: Feature API: {}";

    // packages that are included in all 'KieContainer' instances
    private static Collection<KiePackage> commonPackages = null;

    // all resources with this name consist of rules that are added to each container
    private static final String COMMON_PACKAGES_RESOURCE_NAME = "META-INF/drools/drl";

    /**
     * uses 'groupId', 'artifactId' and 'version', and fetches the associated artifact and remaining
     * dependencies from the Maven repository to create the 'PolicyContainer' and associated
     * 'KieContainer'.
     *
     * <p>An exception occurs if the creation of the 'KieContainer' fails.
     *
     * @param groupId the 'groupId' associated with the artifact
     * @param artifactId the artifact name
     * @param version a comma-separated list of possible versions
     */
    public PolicyContainer(String groupId, String artifactId, String version) {
        this(kieServices.newReleaseId(groupId, artifactId, version));
    }

    /**
     * uses the 'groupId', 'artifactId' and 'version' information in 'ReleaseId', and fetches the
     * associated artifact and remaining dependencies from the Maven repository to create the
     * 'PolicyContainer' and associated 'KieContainer'.
     *
     * <p>An exception occurs if the creation of the 'KieContainer' fails.
     *
     * @param releaseId indicates the artifact that is to be installed in this container
     */
    public PolicyContainer(ReleaseId releaseId) {
        ReleaseId newReleaseId = releaseId;
        if (newReleaseId.getVersion().contains(",")) {
            // this is actually a comma-separated list of release ids
            newReleaseId =
                    loadArtifact(newReleaseId.getGroupId(), newReleaseId.getArtifactId(), newReleaseId.getVersion());
        } else {
            kieContainer = kieServices.newKieContainer(newReleaseId);
        }

        // add common KiePackage instances
        addCommonPackages();
        synchronized (containers) {
            if (newReleaseId != null) {
                logger.info("Add a new kieContainer in containers: releaseId: {}", newReleaseId);
            } else {
                logger.warn("input releaseId is null");
            }
            containers.add(this);
        }
        // 'startScanner(releaseId)' was called at this point, but we have seen
        // at least one case where the Drools container was repeatedly updated
        // every 60 seconds. It isn't clear what conditions resulted in this
        // behavior, so the call was removed. If needed, it can be explicitly
        // called from a feature.
    }

    /**
     * Load an artifact into a new KieContainer. This method handles the case where the 'version' is
     * actually a comma-separated list of versions.
     *
     * @param groupId the 'groupId' associated with the artifact
     * @param artifactId the artifact name
     * @param version a comma-separated list of possible versions
     */
    private ReleaseId loadArtifact(String groupId, String artifactId, String version) {
        String[] versions = version.split(",");
        if (versions.length > 1) {
            logger.info("Multiple KieContainer versions are specified: {}", version);
        }

        // indicates a 'newKieContainer' call failed
        RuntimeException exception = null;

        // set prior to every 'newKieContainer' invocation
        // (if we are able to create the container, it will be the last
        // one that was successful)
        ReleaseId releaseId = null;
        for (String ver : versions) {
            try {
                // Create a 'ReleaseId' object describing the artifact, and
                // create a 'KieContainer' based upon it.
                logger.info("Create new KieContainer start, version = {} ...", ver);

                releaseId = kieServices.newReleaseId(groupId, artifactId, ver);
                kieContainer = kieServices.newKieContainer(releaseId);

                // clear any exception, and break out of the loop
                exception = null;
                break;
            } catch (RuntimeException e) {
                exception = e;
            }
        }
        if (exception != null) {
            // all of the 'newKieContainer' invocations failed -- throw the
            // most recent exception
            throw exception;
        }
        return releaseId;
    }

    /**
     * Get name.
     *
     * @return the name of the container, which is the String equivalent of the 'ReleaseId'. It has
     *         the form:
     *
     *         (groupId + ":" + artifactId + ":" + version)
     *
     *         Note that the name changes after a successful call to 'updateToVersion', although
     *         typically only the 'version' part changes.
     */
    public String getName() {
        return kieContainer.getReleaseId().toString();
    }

    /**
     * Get kie container.
     *
     * @return the associated 'KieContainer' instance
     */
    public KieContainer getKieContainer() {
        return kieContainer;
    }

    /**
     * Get class loader.
     *
     * @return the 'ClassLoader' associated with the 'KieContainer' instance
     */
    public ClassLoader getClassLoader() {
        return kieContainer.getClassLoader();
    }

    /**
     * Get group Id.
     *
     * @return the Maven GroupId of the top-level artifact wrapped by the container.
     */
    public String getGroupId() {
        return kieContainer.getReleaseId().getGroupId();
    }

    /**
     * Get artifact id.
     *
     * @return the Maven ArtifactId of the top-level artifact wrapped by the container.
     */
    public String getArtifactId() {
        return kieContainer.getReleaseId().getArtifactId();
    }

    /**
     * Get version.
     *
     * @return the version of the top-level artifact wrapped by the container (this may change as
     *         updates occur)
     */
    public String getVersion() {
        return kieContainer.getReleaseId().getVersion();
    }

    /**
     * Fetch the named 'PolicySession'.
     *
     * @param name the name of the KieSession (which is also the name of the associated
     *        PolicySession)
     * @return a PolicySession if found, 'null' if not
     */
    public PolicySession getPolicySession(String name) {
        return sessions.get(name);
    }

    /**
     * Internal method to create a PolicySession, possibly restoring it from persistent storage.
     *
     * @param name of the KieSession and PolicySession
     * @param kieBaseName name of the associated 'KieBase' instance
     * @return a new or existing PolicySession, or 'null' if not found
     */
    private PolicySession activatePolicySession(String name, String kieBaseName) {
        synchronized (sessions) {
            logger.info("activatePolicySession:name :{}", name);
            PolicySession session = sessions.computeIfAbsent(name, key -> makeSession(name, kieBaseName));

            logger.info("activatePolicySession:session - {} is returned.",
                            session == null ? "null" : session.getFullName());
            return session;
        }
    }

    private PolicySession makeSession(String name, String kieBaseName) {
        PolicySession session = null;
        KieSession kieSession = null;

        // loop through all of the features, and give each one
        // a chance to create the 'KieSession'
        for (PolicySessionFeatureApi feature : PolicySessionFeatureApiConstants.getImpl().getList()) {
            try {
                if ((kieSession = feature.activatePolicySession(this, name, kieBaseName)) != null) {
                    break;
                }
            } catch (Exception e) {
                logger.error(ERROR_STRING, feature.getClass().getName(), e);
            }
        }

        // if none of the features created the session, create one now
        if (kieSession == null) {
            kieSession = kieContainer.newKieSession(name);
        }

        if (kieSession != null) {
            // creation of 'KieSession' was successful - build
            // a PolicySession
            session = new PolicySession(name, this, kieSession);

            // notify features
            for (PolicySessionFeatureApi feature : PolicySessionFeatureApiConstants.getImpl().getList()) {
                try {
                    feature.newPolicySession(session);
                } catch (Exception e) {
                    logger.error(ERROR_STRING, feature.getClass().getName(), e);
                }
            }
            logger.info("activatePolicySession:new session was added in sessions with name {}", name);
        }

        return session;
    }

    /**
     * This creates a 'PolicySession' instance within this 'PolicyContainer', and ties it to the
     * specified 'KieSession'. 'name' must not currently exist within the 'PolicyContainer', and the
     * 'KieBase' object associated with 'KieSession' must belong to the 'KieContainer'. This method
     * provides a way for 'KieSession' instances that are created programmatically to fit into this
     * framework.
     *
     * @param name the name for the new 'PolicySession'
     * @param kieSession a 'KieSession' instance, that will be included in this infrastructure
     * @return the new 'PolicySession'
     * @throws IllegalArgumentException if 'kieSession' does not reside within this container
     * @throws IllegalStateException if a 'PolicySession' already exists with this name
     */
    public PolicySession adoptKieSession(String name, KieSession kieSession) {

        if (name == null) {
            logger.warn("adoptKieSession:input name is null");
            throw new IllegalArgumentException("KieSession input name is null " + getName());
        } else if (kieSession == null) {
            logger.warn("adoptKieSession:input kieSession is null");
            throw new IllegalArgumentException("KieSession '" + name + "' is null " + getName());
        } else {
            logger.info("adoptKieSession:name: {} kieSession: {}", name, kieSession);
        }
        // fetch KieBase, and verify it belongs to this KieContainer
        boolean match = false;
        KieBase kieBase = kieSession.getKieBase();
        logger.info("adoptKieSession:kieBase: {}", kieBase);
        for (String kieBaseName : kieContainer.getKieBaseNames()) {
            logger.info("adoptKieSession:kieBaseName: {}", kieBaseName);
            if (kieBase == kieContainer.getKieBase(kieBaseName)) {
                match = true;
                break;
            }
        }
        logger.info("adoptKieSession:match {}", match);
        // if we don't have a match yet, the last chance is to look at the
        // default KieBase, if it exists
        if (!match && kieBase != kieContainer.getKieBase()) {
            throw new IllegalArgumentException(
                    "KieSession '" + name + "' does not reside within container " + getName());
        }

        synchronized (sessions) {
            if (sessions.get(name) != null) {
                throw new IllegalStateException("PolicySession '" + name + "' already exists");
            }

            // create the new 'PolicySession', add it to the table,
            // and return the object to the caller
            logger.info("adoptKieSession:create a new policySession with name {}", name);
            PolicySession policySession = new PolicySession(name, this, kieSession);
            sessions.put(name, policySession);

            // notify features
            for (PolicySessionFeatureApi feature : PolicySessionFeatureApiConstants.getImpl().getList()) {
                try {
                    feature.newPolicySession(policySession);
                } catch (Exception e) {
                    logger.error(ERROR_STRING, feature.getClass().getName(), e);
                }
            }
            return policySession;
        }
    }

    /**
     * This call 'KieContainer.updateToVersion()', and returns the associated response as a String.
     * If successful, the name of this 'PolicyContainer' changes to match the new version.
     *
     * @param newVersion this is the version to update to (the 'groupId' and 'artifactId' remain the
     *        same)
     * @return the list of messages associated with the update (not sure if this can be 'null', or
     *         how to determine success/failure)
     */
    public String updateToVersion(String newVersion) {
        ReleaseId releaseId = kieContainer.getReleaseId();
        Results results = this.updateToVersion(
                kieServices.newReleaseId(releaseId.getGroupId(), releaseId.getArtifactId(), newVersion));

        List<Message> messages = results == null ? null : results.getMessages();
        return messages == null ? null : messages.toString();
    }

    /**
     * This calls 'KieContainer.updateToVersion()', and returns the associated response. If
     * successful, the name of this 'PolicyContainer' changes to match the new version.
     *
     * @param releaseId the new artifact (usually new version) to be installed
     * @return the 'Results' parameter from 'KieContainer.updateToVersion'
     */
    public Results updateToVersion(ReleaseId releaseId) {
        if (releaseId == null) {
            logger.warn("updateToVersion:input releaseId is null");
        } else {
            logger.info("updateToVersion:releaseId {}", releaseId);
        }

        // stop all session threads
        for (PolicySession session : sessions.values()) {
            session.stopThread();
        }

        // update the version
        Results results = kieContainer.updateToVersion(releaseId);


        // add common KiePackage instances
        addCommonPackages();

        // restart all session threads, and notify the sessions
        for (PolicySession session : sessions.values()) {
            session.startThread();
            session.updated();
        }

        return results;
    }

    /**
     * Get policy containers.
     *
     * @return all existing 'PolicyContainer' instances
     */
    public static Collection<PolicyContainer> getPolicyContainers() {
        synchronized (containers) {
            return new HashSet<>(containers);
        }
    }

    /**
     * Get policy sessions.
     *
     * @return all of the 'PolicySession' instances
     */
    public Collection<PolicySession> getPolicySessions() {
        // KLUDGE WARNING: this is a temporary workaround -- if there are
        // no features, we don't have persistence, and 'activate' is never
        // called. In this case, make sure the container is started.
        if (PolicySessionFeatureApiConstants.getImpl().getList().isEmpty()) {
            start();
        }

        // return current set of PolicySessions
        synchronized (sessions) {
            return new HashSet<>(sessions.values());
        }
    }

    /**
     * This method will start a 'KieScanner' (if not currently running), provided that the ReleaseId
     * version is 'LATEST' or 'RELEASE', or refers to a SNAPSHOT version.
     *
     * @param releaseId the release id used to create the container
     */
    public synchronized void startScanner(ReleaseId releaseId) {
        String version = releaseId.getVersion();

        if (scannerStarted || scanner != null || version == null) {
            return;
        }

        if (!("LATEST".equals(version) || "RELEASE".equals(version) || version.endsWith("-SNAPSHOT"))) {
            return;
        }

        // create the scanner, and poll at 60 second intervals
        try {
            scannerStarted = true;

            // start this in a separate thread -- it can block for a long time
            new Thread("Scanner Starter " + getName()) {
                @Override
                public void run() {
                    scanner = kieServices.newKieScanner(kieContainer);
                    scanner.start(60000L);
                }
            }.start();
        } catch (Exception e) {
            // sometimes the scanner initialization fails for some reason
            logger.error("startScanner error", e);
        }
    }

    /**
     * Insert a fact into a specific named session.
     *
     * @param name this is the session name
     * @param object this is the fact to be inserted into the session
     * @return 'true' if the named session was found, 'false' if not
     */
    public boolean insert(String name, Object object) {
        // TODO: Should the definition of 'name' be expanded to include an
        // alternate entry point as well? For example, 'name.entryPoint' (or
        // something other than '.' if that is a problem).
        synchronized (sessions) {
            PolicySession session = sessions.get(name);
            if (session != null) {
                session.insertDrools(object);
                return true;
            }
        }
        return false;
    }

    /**
     * Insert a fact into all sessions associated with this container.
     *
     * @param object this is the fact to be inserted into the sessions
     * @return 'true' if the fact was inserted into at least one session, 'false' if not
     */
    public boolean insertAll(Object object) {
        boolean rval = false;
        synchronized (sessions) {
            for (PolicySession session : sessions.values()) {
                session.insertDrools(object);
                rval = true;
            }
        }
        return rval;
    }

    /*=======================*/
    /* 'Startable' interface */
    /*=======================*/

    /**
     * {@inheritDoc}.
     */
    @Override
    public synchronized boolean start() {   // NOSONAR
        /*
         * disabling sonar about returning the same value, because we prefer the code to
         * be structured this way
         */

        if (isStarted) {
            return true;
        }

        // This will create all 'PolicySession' instances specified in the
        // 'kmodule.xml' file that don't exist yet
        for (String kieBaseName : kieContainer.getKieBaseNames()) {
            for (String kieSessionName : kieContainer.getKieSessionNamesInKieBase(kieBaseName)) {
                // if the 'PolicySession' does not currently exist, this method
                // call will attempt to create it
                PolicySession session = activatePolicySession(kieSessionName, kieBaseName);
                if (session != null) {
                    session.startThread();
                }
            }
        }
        isStarted = true;
        return true;
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public synchronized boolean stop() {
        return (!isStarted || doStop());
    }

    private boolean doStop() {
        Collection<PolicySession> localSessions;

        synchronized (sessions) {
            // local set containing all of the sessions
            localSessions = new HashSet<>(sessions.values());

            // clear the 'name->session' map in 'PolicyContainer'
            sessions.clear();
        }
        for (PolicySession session : localSessions) {
            // stop session thread
            session.stopThread();

            // free KieSession resources
            session.getKieSession().dispose();

            // notify features
            for (PolicySessionFeatureApi feature : PolicySessionFeatureApiConstants.getImpl().getList()) {
                try {
                    feature.disposeKieSession(session);
                } catch (Exception e) {
                    logger.error(ERROR_STRING, feature.getClass().getName(), e);
                }
            }
        }
        isStarted = false;

        return true;
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public synchronized void shutdown() {
        // Note that this method does not call 'destroy' on the 'KieSession'
        // instances, which would remove any associated information in persistent
        // storage. Should it do this?

        stop();
        synchronized (containers) {
            containers.remove(this);
        }

        // How do we free the resources associated with the KieContainer?
        // Is garbage collection sufficient?
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public boolean isAlive() {
        return isStarted;
    }

    /**
     * This method is similar to 'shutdown', but it also frees any persistence resources as well.
     */
    public synchronized void destroy() {
        // we need all KieSession instances running in order to free
        // resources associated with persistence
        start();
        Collection<PolicySession> localSessions;

        synchronized (sessions) {
            // local set containing all of the sessions
            localSessions = new HashSet<>(sessions.values());

            // clear the 'name->session' map in 'PolicyContainer'
            sessions.clear();
        }
        for (PolicySession session : localSessions) {
            // stop session thread
            session.stopThread();

            // free KieSession resources
            session.getKieSession().destroy();

            // notify features
            for (PolicySessionFeatureApi feature : PolicySessionFeatureApiConstants.getImpl().getList()) {
                try {
                    feature.destroyKieSession(session);
                } catch (Exception e) {
                    logger.error(ERROR_STRING, feature.getClass().getName(), e);
                }
            }
        }
        isStarted = false;

        synchronized (containers) {
            containers.remove(this);
        }

        // How do we free the resources associated with the KieContainer?
        // Is garbage collection sufficient?
    }

    /**
     * This method is called when the host goes from the 'standby->active' state.
     */
    public static void activate() {
        // start all of the 'PolicyContainer' instances
        for (PolicyContainer container : containers) {
            try {
                container.start();
            } catch (Exception e) {
                logger.error("PolicyContainer.start() error in activate", e);
            }
        }
    }

    /**
     * This method is called when the host goes from the 'active->standby' state.
     */
    public static void deactivate() {
        // deactivate all of the 'PolicyContainer' instances
        for (PolicyContainer container : containers) {
            try {
                container.stop();
            } catch (Exception e) {
                logger.error("PolicyContainer.start() error in deactivate", e);
            }
        }
    }

    /**
     * This method does the following:
     *
     * <p>1) Initializes logging 2) Starts the DroolsPDP Integrity Monitor 3) Initilaizes persistence
     *
     * <p>It no longer reads in properties files, o creates 'PolicyContainer' instances.
     *
     * @param args standard 'main' arguments, which are currently ignored
     */
    public static void globalInit(String[] args) {
        String configDir = "config";
        logger.info("PolicyContainer.main: configDir={}", configDir);

        // invoke 'globalInit' on all of the features
        for (PolicySessionFeatureApi feature : PolicySessionFeatureApiConstants.getImpl().getList()) {
            try {
                feature.globalInit(args, configDir);
            } catch (Exception e) {
                logger.error(ERROR_STRING, feature.getClass().getName(), e);
            }
        }
    }

    /**
     * Fetch the adjunct object associated with a given feature.
     *
     * @param object this is typically the singleton feature object that is used as a key, but it
     *        might also be useful to use nested objects within the feature as keys.
     * @return a feature-specific object associated with the key, or 'null' if it is not found.
     */
    public Object getAdjunct(Object object) {
        return adjuncts.get(object);
    }

    /**
     * Store the adjunct object associated with a given feature.
     *
     * @param object this is typically the singleton feature object that is used as a key, but it
     *        might also be useful to use nested objects within the feature as keys.
     * @param value a feature-specific object associated with the key, or 'null' if the
     *        feature-specific object should be removed
     */
    public void setAdjunct(Object object, Object value) {
        if (value == null) {
            adjuncts.remove(object);
        } else {
            adjuncts.put(object, value);
        }
    }

    /**
     * Add 'KiePackages' that are common to all containers.
     */
    private void addCommonPackages() {
        // contains the list of 'KiePackages' to add to each 'KieBase'
        Collection<KiePackage> kiePackages;
        synchronized (PolicyContainer.class) {
            if (commonPackages == null) {
                commonPackages = KieUtils.resourceToPackages(
                    PolicyContainer.class.getClassLoader(), COMMON_PACKAGES_RESOURCE_NAME).orElse(null);
                if (commonPackages == null) {
                    // a problem occurred, which has already been logged --
                    // just store an empty collection, so we don't keep doing
                    // this over again
                    commonPackages = new HashSet<>();
                    return;
                }
            }
            kiePackages = commonPackages;
        }

        // if we reach this point, 'kiePackages' contains a non-null list
        // of packages to add
        for (String name : kieContainer.getKieBaseNames()) {
            KieUtils.addKiePackages(kieContainer.getKieBase(name), kiePackages);
        }
    }
}
