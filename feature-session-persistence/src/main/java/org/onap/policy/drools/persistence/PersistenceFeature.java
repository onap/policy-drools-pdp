/*
 * ============LICENSE_START=======================================================
 * feature-session-persistence
 * ================================================================================
 * Copyright (C) 2017-2020 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.persistence;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.transaction.TransactionManager;
import javax.transaction.TransactionSynchronizationRegistry;
import javax.transaction.UserTransaction;

import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.dbcp2.BasicDataSourceFactory;
import org.hibernate.cfg.AvailableSettings;
import org.kie.api.KieServices;
import org.kie.api.runtime.Environment;
import org.kie.api.runtime.EnvironmentName;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.onap.policy.drools.core.PolicyContainer;
import org.onap.policy.drools.core.PolicySession;
import org.onap.policy.drools.core.PolicySessionFeatureApi;
import org.onap.policy.drools.features.PolicyEngineFeatureApi;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.drools.system.PolicyControllerConstants;
import org.onap.policy.drools.system.PolicyEngine;
import org.onap.policy.drools.utils.PropertyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * If this feature is supported, there is a single instance of it. It adds persistence to Drools
 * sessions. In addition, if an active-standby feature exists, then that is used to determine the
 * active and last-active PDP. If it does not exist, then the current host name is used as the PDP
 * id.
 *
 * <p>The bulk of the code here was once in other classes, such as 'PolicyContainer' and 'Main'. It
 * was moved here as part of making this a separate optional feature.
 */
public class PersistenceFeature implements PolicySessionFeatureApi, PolicyEngineFeatureApi {

    private static final Logger logger = LoggerFactory.getLogger(PersistenceFeature.class);

    /** KieService factory. */
    private KieServices kieSvcFact;

    /** Persistence properties. */
    private Properties persistProps;

    /** Whether or not the SessionInfo records should be cleaned out. */
    private boolean sessInfoCleaned;

    /** SessionInfo timeout, in milli-seconds, as read from
     * {@link #persistProps}. */
    private long sessionInfoTimeoutMs;

    /** Object used to serialize cleanup of sessioninfo table. */
    private Object cleanupLock = new Object();

    /**
     * Lookup the adjunct for this feature that is associated with the specified PolicyContainer. If
     * not found, create one.
     *
     * @param policyContainer the container whose adjunct we are looking up, and possibly creating
     * @return the associated 'ContainerAdjunct' instance, which may be new
     */
    private ContainerAdjunct getContainerAdjunct(PolicyContainer policyContainer) {

        Object rval = policyContainer.getAdjunct(this);

        if (!(rval instanceof ContainerAdjunct)) {
            // adjunct does not exist, or has the wrong type (should never
            // happen)
            rval = new ContainerAdjunct(policyContainer);
            policyContainer.setAdjunct(this, rval);
        }

        return (ContainerAdjunct) rval;
    }

    /**
     * {@inheritDoc}.
     **/
    @Override
    public int getSequenceNumber() {
        return 1;
    }

    /**
     * {@inheritDoc}.
     **/
    @Override
    public void globalInit(String[] args, String configDir) {

        kieSvcFact = getKieServices();

        try {
            persistProps = loadProperties(configDir + "/feature-session-persistence.properties");

        } catch (IOException e1) {
            logger.error("initializePersistence: ", e1);
        }

        sessionInfoTimeoutMs = getPersistenceTimeout();
    }

    /**
     * Creates a persistent KieSession, loading it from the persistent store, or creating one, if it
     * does not exist yet.
     */
    @Override
    public KieSession activatePolicySession(
            PolicyContainer policyContainer, String name, String kieBaseName) {

        if (isPersistenceEnabled(policyContainer, name)) {
            cleanUpSessionInfo();

            return getContainerAdjunct(policyContainer).newPersistentKieSession(name, kieBaseName);
        }

        return null;
    }

    /**
     * {@inheritDoc}.
     **/
    @Override
    public PolicySession.ThreadModel selectThreadModel(PolicySession session) {

        PolicyContainer policyContainer = session.getPolicyContainer();
        if (isPersistenceEnabled(policyContainer, session.getName())) {
            return new PersistentThreadModel(session, getProperties(policyContainer));
        }
        return null;
    }

    /**
     * {@inheritDoc}.
     **/
    @Override
    public void disposeKieSession(PolicySession policySession) {

        ContainerAdjunct contAdj =
                (ContainerAdjunct) policySession.getPolicyContainer().getAdjunct(this);
        if (contAdj != null) {
            contAdj.disposeKieSession(policySession.getName());
        }
    }

    /**
     * {@inheritDoc}.
     **/
    @Override
    public void destroyKieSession(PolicySession policySession) {

        ContainerAdjunct contAdj =
                (ContainerAdjunct) policySession.getPolicyContainer().getAdjunct(this);
        if (contAdj != null) {
            contAdj.destroyKieSession(policySession.getName());
        }
    }

    /**
     * {@inheritDoc}.
     **/
    @Override
    public boolean afterStart(PolicyEngine engine) {
        return false;
    }

    /**
     * {@inheritDoc}.
     **/
    @Override
    public boolean beforeStart(PolicyEngine engine) {
        return cleanup();
    }

    /**
     * {@inheritDoc}.
     **/
    @Override
    public boolean beforeActivate(PolicyEngine engine) {
        return cleanup();
    }

    private boolean cleanup() {
        synchronized (cleanupLock) {
            sessInfoCleaned = false;
        }

        return false;
    }

    /**
     * {@inheritDoc}.
     **/
    @Override
    public boolean afterActivate(PolicyEngine engine) {
        return false;
    }

    /* ============================================================ */

    /**
     * Gets the persistence timeout value for sessioninfo records.
     *
     * @return the timeout value, in milli-seconds, or {@code -1} if it is unspecified or invalid
     */
    private long getPersistenceTimeout() {
        String timeoutString = null;

        try {
            timeoutString = persistProps.getProperty(DroolsPersistenceProperties.DB_SESSIONINFO_TIMEOUT);

            if (timeoutString != null) {
                // timeout parameter is specified
                return Long.valueOf(timeoutString) * 1000;
            }

        } catch (NumberFormatException e) {
            logger.error(
                    "Invalid value for Drools persistence property persistence.sessioninfo.timeout: {}",
                    timeoutString,
                    e);
        }

        return -1;
    }

    /* ============================================================ */

    /**
     * Each instance of this class is a logical extension of a 'PolicyContainer' instance. Its
     * reference is stored in the 'adjuncts' table within the 'PolicyContainer', and will be
     * garbage-collected with the container.
     */
    protected class ContainerAdjunct {
        /** 'PolicyContainer' instance that this adjunct is extending. */
        private PolicyContainer policyContainer;

        /** Maps a KIE session name to its data source. */
        private Map<String, DsEmf> name2ds = new HashMap<>();

        /**
         * Constructor - initialize a new 'ContainerAdjunct'.
         *
         * @param policyContainer the 'PolicyContainer' instance this adjunct is extending
         */
        private ContainerAdjunct(PolicyContainer policyContainer) {
            this.policyContainer = policyContainer;
        }

        /**
         * Create a new persistent KieSession. If there is already a corresponding entry in the
         * database, it is used to initialize the KieSession. If not, a completely new session is
         * created.
         *
         * @param name the name of the KieSession (which is also the name of the associated
         *     PolicySession)
         * @param kieBaseName the name of the 'KieBase' instance containing this session
         * @return a new KieSession with persistence enabled
         */
        private KieSession newPersistentKieSession(String name, String kieBaseName) {

            configureSysProps();

            BasicDataSource ds = makeDataSource(getDataSourceProperties());
            DsEmf dsemf = new DsEmf(ds);

            try {
                EntityManagerFactory emf = dsemf.emf;
                DroolsSessionConnector conn = makeJpaConnector(emf);

                long desiredSessionId = getSessionId(conn, name);

                logger.info(
                        "\n\nThis controller is primary... coming up with session {} \n\n", desiredSessionId);

                // session does not exist -- attempt to create one
                logger.info(
                        "getPolicySession:session does not exist -- attempt to create one with name {}", name);

                Environment env = kieSvcFact.newEnvironment();

                configureKieEnv(env, emf);

                KieSessionConfiguration kieConf = kieSvcFact.newKieSessionConfiguration();

                KieSession kieSession =
                        (desiredSessionId >= 0
                        ? loadKieSession(kieBaseName, desiredSessionId, env, kieConf)
                                : null);

                if (kieSession == null) {
                    // loadKieSession() returned null or desiredSessionId < 0
                    logger.info(
                            "LOADING We cannot load session {}. Going to create a new one", desiredSessionId);

                    kieSession = newKieSession(kieBaseName, env);
                }

                replaceSession(conn, name, kieSession);

                name2ds.put(name, dsemf);

                return kieSession;

            } catch (RuntimeException e) {
                dsemf.close();
                throw e;
            }
        }

        /**
         * Loads an existing KieSession from the persistent store.
         *
         * @param kieBaseName the name of the 'KieBase' instance containing this session
         * @param desiredSessionId id of the desired KieSession
         * @param env Kie Environment for the session
         * @param kConf Kie Configuration for the session
         * @return the persistent session, or {@code null} if it could not be loaded
         */
        private KieSession loadKieSession(
                String kieBaseName, long desiredSessionId, Environment env, KieSessionConfiguration kieConf) {
            try {
                KieSession kieSession =
                        kieSvcFact
                        .getStoreServices()
                        .loadKieSession(
                                desiredSessionId,
                                policyContainer.getKieContainer().getKieBase(kieBaseName),
                                kieConf,
                                env);

                logger.info("LOADING Loaded session {}", desiredSessionId);

                return kieSession;

            } catch (Exception e) {
                logger.error("loadKieSession error: ", e);
                return null;
            }
        }

        /**
         * Creates a new, persistent KieSession.
         *
         * @param kieBaseName the name of the 'KieBase' instance containing this session
         * @param env Kie Environment for the session
         * @return a new, persistent session
         */
        private KieSession newKieSession(String kieBaseName, Environment env) {
            KieSession kieSession =
                    kieSvcFact
                    .getStoreServices()
                    .newKieSession(policyContainer.getKieContainer().getKieBase(kieBaseName), null, env);

            logger.info("LOADING CREATED {}", kieSession.getIdentifier());

            return kieSession;
        }

        /**
         * Closes the data source associated with a session.
         *
         * @param name name of the session being destroyed
         */
        private void destroyKieSession(String name) {
            closeDataSource(name);
        }

        /**
         * Closes the data source associated with a session.
         *
         * @param name name of the session being disposed of
         */
        private void disposeKieSession(String name) {
            closeDataSource(name);
        }

        /**
         * Closes the data source associated with a session.
         *
         * @param name name of the session whose data source is to be closed
         */
        private void closeDataSource(String name) {
            DsEmf ds = name2ds.remove(name);
            if (ds != null) {
                ds.close();
            }
        }

        /** Configures java system properties for JPA/JTA. */
        private void configureSysProps() {
            System.setProperty("com.arjuna.ats.arjuna.coordinator.defaultTimeout", "60");
            System.setProperty(
                    "com.arjuna.ats.arjuna.objectstore.objectStoreDir",
                    persistProps.getProperty(DroolsPersistenceProperties.JTA_OBJECTSTORE_DIR));
            System.setProperty(
                    "ObjectStoreEnvironmentBean.objectStoreDir",
                    persistProps.getProperty(DroolsPersistenceProperties.JTA_OBJECTSTORE_DIR));
        }

        /**
         * Configures a Kie Environment.
         *
         * @param env environment to be configured
         * @param emf entity manager factory
         */
        private void configureKieEnv(Environment env, EntityManagerFactory emf) {
            env.set(EnvironmentName.ENTITY_MANAGER_FACTORY, emf);
            env.set(EnvironmentName.TRANSACTION, getUserTrans());
            env.set(EnvironmentName.TRANSACTION_SYNCHRONIZATION_REGISTRY, getTransSyncReg());
            env.set(EnvironmentName.TRANSACTION_MANAGER, getTransMgr());
        }

        /**
         * Gets a session's ID from the persistent store.
         *
         * @param conn persistence connector
         * @param sessnm name of the session
         * @return the session's id, or {@code -1} if the session is not found
         */
        private long getSessionId(DroolsSessionConnector conn, String sessnm) {
            DroolsSession sess = conn.get(sessnm);
            return sess != null ? sess.getSessionId() : -1;
        }

        /**
         * Replaces a session within the persistent store, if it exists. Adds it otherwise.
         *
         * @param conn persistence connector
         * @param sessnm name of session to be updated
         * @param kieSession new session information
         */
        private void replaceSession(DroolsSessionConnector conn, String sessnm, KieSession kieSession) {

            DroolsSessionEntity sess = new DroolsSessionEntity();

            sess.setSessionName(sessnm);
            sess.setSessionId(kieSession.getIdentifier());

            conn.replace(sess);
        }
    }

    /* ============================================================ */

    /**
     * Gets the data source properties.
     *
     * @return the data source properties
     */
    private Properties getDataSourceProperties() {
        Properties props = new Properties();
        props.put("driverClassName", persistProps.getProperty(DroolsPersistenceProperties.DB_DRIVER));
        props.put("url", persistProps.getProperty(DroolsPersistenceProperties.DB_URL));
        props.put("username", persistProps.getProperty(DroolsPersistenceProperties.DB_USER));
        props.put("password", persistProps.getProperty(DroolsPersistenceProperties.DB_PWD));
        props.put("maxActive", "3");
        props.put("maxIdle", "1");
        props.put("maxWait", "120000");
        props.put("whenExhaustedAction", "2");
        props.put("testOnBorrow", "false");
        props.put("poolPreparedStatements", "true");

        return props;
    }

    /**
     * Removes "old" Drools 'sessioninfo' records, so they aren't used to restore data to Drools
     * sessions. This also has the useful side-effect of removing abandoned records as well.
     */
    private void cleanUpSessionInfo() {

        synchronized (cleanupLock) {
            if (sessInfoCleaned) {
                logger.info("Clean up of sessioninfo table: already done");
                return;
            }

            if (sessionInfoTimeoutMs < 0) {
                logger.info("Clean up of sessioninfo table: no timeout specified");
                return;
            }

            // now do the record deletion
            try (BasicDataSource ds = makeDataSource(getDataSourceProperties());
                    Connection connection = ds.getConnection();
                    PreparedStatement statement =
                            connection.prepareStatement(
                                "DELETE FROM sessioninfo WHERE timestampdiff(second,lastmodificationdate,now()) > ?")) {

                connection.setAutoCommit(true);

                statement.setLong(1, sessionInfoTimeoutMs / 1000);

                int count = statement.executeUpdate();
                logger.info("Cleaning up sessioninfo table -- {} records removed", count);

            } catch (SQLException e) {
                logger.error("Clean up of sessioninfo table failed", e);
            }

            // delete DroolsSessionEntity where sessionId not in (sessinfo.xxx)?

            sessInfoCleaned = true;
        }
    }

    /**
     * Determine whether persistence is enabled for a specific container.
     *
     * @param container container to be checked
     * @param sessionName name of the session to be checked
     * @return {@code true} if persistence is enabled for this container, and {@code false} if not
     */
    private boolean isPersistenceEnabled(PolicyContainer container, String sessionName) {
        Properties properties = getProperties(container);
        boolean rval = false;

        if (properties != null) {
            // fetch the 'type' property
            String type = getProperty(properties, sessionName, "type");
            rval = "auto".equals(type) || "native".equals(type);
        }

        return rval;
    }

    /**
     * Determine the controller properties associated with the policy container.
     *
     * @param container container whose properties are to be retrieved
     * @return the container's properties, or {@code null} if not found
     */
    private Properties getProperties(PolicyContainer container) {
        try {
            return getPolicyController(container).getProperties();
        } catch (IllegalArgumentException e) {
            logger.error("getProperties exception: ", e);
            return null;
        }
    }

    /**
     * Fetch the persistence property associated with a session. The name may have the form:
     *
     * <ul>
     *   <li>persistence.SESSION-NAME.PROPERTY
     *   <li>persistence.PROPERTY
     * </ul>
     *
     * @param properties properties from which the value is to be retrieved
     * @param sessionName session name of interest
     * @param property property name of interest
     * @return the property value, or {@code null} if not found
     */
    private String getProperty(Properties properties, String sessionName, String property) {
        String value = properties.getProperty("persistence." + sessionName + "." + property);
        if (value == null) {
            value = properties.getProperty("persistence." + property);
        }

        return value;
    }

    /* ============================================================ */

    /**
     * This 'ThreadModel' variant periodically calls 'KieSession.fireAllRules()', because the
     * 'fireUntilHalt' method isn't compatible with persistence.
     */
    public class PersistentThreadModel implements Runnable, PolicySession.ThreadModel {

        /** Session associated with this persistent thread. */
        private final PolicySession session;

        /** The session thread. */
        private final Thread thread;

        /** Used to indicate that processing should stop. */
        private final CountDownLatch stopped = new CountDownLatch(1);

        /** Minimum time, in milli-seconds, that the thread should sleep before firing rules again. */
        long minSleepTime = 100;

        /**
         * Maximum time, in milli-seconds, that the thread should sleep before firing rules again. This
         * is a "half" time, so that we can multiply it by two without overflowing the word size.
         */
        long halfMaxSleepTime = 5000L / 2L;

        /**
         * Constructor - initialize variables and create thread.
         *
         * @param session the 'PolicySession' instance
         * @param properties may contain additional session properties
         */
        public PersistentThreadModel(PolicySession session, Properties properties) {
            this.session = session;
            this.thread = new Thread(this, getThreadName());

            if (properties == null) {
                return;
            }

            // extract 'minSleepTime' and/or 'maxSleepTime'
            String name = session.getName();

            // fetch 'minSleepTime' value, and update if defined
            String sleepTimeString = getProperty(properties, name, "minSleepTime");
            if (sleepTimeString != null) {
                try {
                    minSleepTime = Math.max(1, Integer.valueOf(sleepTimeString));
                } catch (Exception e) {
                    logger.error("{}: Illegal value for 'minSleepTime'", sleepTimeString, e);
                }
            }

            // fetch 'maxSleepTime' value, and update if defined
            long maxSleepTime = 2 * halfMaxSleepTime;
            sleepTimeString = getProperty(properties, name, "maxSleepTime");
            if (sleepTimeString != null) {
                try {
                    maxSleepTime = Math.max(1, Integer.valueOf(sleepTimeString));
                } catch (Exception e) {
                    logger.error("{}: Illegal value for 'maxSleepTime'", sleepTimeString, e);
                }
            }

            // swap values if needed
            if (minSleepTime > maxSleepTime) {
                logger.error("minSleepTime({}) is greater than maxSleepTime({}) -- swapping", minSleepTime,
                                maxSleepTime);
                long tmp = minSleepTime;
                minSleepTime = maxSleepTime;
                maxSleepTime = tmp;
            }

            halfMaxSleepTime = Math.max(1, maxSleepTime / 2);
        }

        /**
         * Get thread name.
         *
         * @return the String to use as the thread name */
        private String getThreadName() {
            return "Session " + session.getFullName() + " (persistent)";
        }

        /*=========================*/
        /* 'ThreadModel' interface */
        /*=========================*/

        /**
         * {@inheritDoc}.
         **/
        @Override
        public void start() {
            thread.start();
        }

        /**
         * {@inheritDoc}.
         **/
        @Override
        public void stop() {
            // tell the thread to stop
            stopped.countDown();

            // wait up to 10 seconds for the thread to stop
            try {
                thread.join(10000);

            } catch (InterruptedException e) {
                logger.error("stopThread exception: ", e);
                Thread.currentThread().interrupt();
            }

            // verify that it's done
            if (thread.isAlive()) {
                logger.error("stopThread: still running");
            }
        }

        /**
         * {@inheritDoc}.
         **/
        @Override
        public void updated() {
            // the container artifact has been updated -- adjust the thread name
            thread.setName(getThreadName());
        }

        /*======================*/
        /* 'Runnable' interface */
        /*======================*/

        /**
         * {@inheritDoc}.
         **/
        @Override
        public void run() {
            logger.info("PersistentThreadModel running");

            // set thread local variable
            session.setPolicySession();

            KieSession kieSession = session.getKieSession();
            long sleepTime = 2 * halfMaxSleepTime;

            // We want to continue, despite any exceptions that occur
            // while rules are fired.

            boolean cont = true;
            while (cont) {

                try {
                    if (kieSession.fireAllRules() > 0) {
                        // some rules fired -- reduce poll delay
                        sleepTime = Math.max(minSleepTime, sleepTime / 2);
                    } else {
                        // no rules fired -- increase poll delay
                        sleepTime = 2 * Math.min(halfMaxSleepTime, sleepTime);
                    }

                } catch (Exception | LinkageError e) {
                    logger.error("Exception during kieSession.fireAllRules", e);
                }

                try {
                    if (stopped.await(sleepTime, TimeUnit.MILLISECONDS)) {
                        cont = false;
                    }

                } catch (InterruptedException e) {
                    logger.error("startThread exception: ", e);
                    Thread.currentThread().interrupt();
                    cont = false;
                }
            }

            logger.info("PersistentThreadModel completed");
        }
    }

    /* ============================================================ */

    /** DataSource-EntityManagerFactory pair. */
    private class DsEmf {
        private BasicDataSource bds;
        private EntityManagerFactory emf;

        /**
         * Makes an entity manager factory for the given data source.
         *
         * @param bds pooled data source
         */
        public DsEmf(BasicDataSource bds) {
            try {
                Map<String, Object> props = new HashMap<>();
                props.put(AvailableSettings.JPA_JTA_DATASOURCE, bds);

                this.bds = bds;
                this.emf = makeEntMgrFact(props);

            } catch (RuntimeException e) {
                closeDataSource();
                throw e;
            }
        }

        /** Closes the entity manager factory and the data source. */
        public void close() {
            try {
                emf.close();

            } catch (RuntimeException e) {
                closeDataSource();
                throw e;
            }

            closeDataSource();
        }

        /** Closes the data source only. */
        private void closeDataSource() {
            try {
                bds.close();

            } catch (SQLException e) {
                throw new PersistenceFeatureException(e);
            }
        }
    }

    private static class SingletonRegistry {
        private static final TransactionSynchronizationRegistry transreg =
                new com.arjuna.ats.internal.jta.transaction.arjunacore
                .TransactionSynchronizationRegistryImple();

        private SingletonRegistry() {
            super();
        }
    }

    /** Factory for various items. Methods can be overridden for junit testing. */

    /**
     * Gets the transaction manager.
     *
     * @return the transaction manager
     */
    protected TransactionManager getTransMgr() {
        return com.arjuna.ats.jta.TransactionManager.transactionManager();
    }

    /**
     * Gets the user transaction.
     *
     * @return the user transaction
     */
    protected UserTransaction getUserTrans() {
        return com.arjuna.ats.jta.UserTransaction.userTransaction();
    }

    /**
     * Gets the transaction synchronization registry.
     *
     * @return the transaction synchronization registry
     */
    protected TransactionSynchronizationRegistry getTransSyncReg() {
        return SingletonRegistry.transreg;
    }

    /**
     * Gets the KIE services.
     *
     * @return the KIE services
     */
    protected KieServices getKieServices() {
        return KieServices.Factory.get();
    }

    /**
     * Loads properties from a file.
     *
     * @param filenm name of the file to load
     * @return properties, as loaded from the file
     * @throws IOException if an error occurs reading from the file
     */
    protected Properties loadProperties(String filenm) throws IOException {
        return PropertyUtil.getProperties(filenm);
    }

    /**
     * Makes a Data Source.
     *
     * @param dsProps data source properties
     * @return a new data source
     */
    protected BasicDataSource makeDataSource(Properties dsProps) {
        try {
            return BasicDataSourceFactory.createDataSource(dsProps);

        } catch (Exception e) {
            throw new PersistenceFeatureException(e);
        }
    }

    /**
     * Makes a new JPA connector for drools sessions.
     *
     * @param emf entity manager factory
     * @return a new JPA connector for drools sessions
     */
    protected DroolsSessionConnector makeJpaConnector(EntityManagerFactory emf) {
        return new JpaDroolsSessionConnector(emf);
    }

    /**
     * Makes a new entity manager factory.
     *
     * @param props properties with which the factory should be configured
     * @return a new entity manager factory
     */
    protected EntityManagerFactory makeEntMgrFact(Map<String, Object> props) {
        return Persistence.createEntityManagerFactory("onapsessionsPU", props);
    }

    /**
     * Gets the policy controller associated with a given policy container.
     *
     * @param container container whose controller is to be retrieved
     * @return the container's controller
     */
    protected PolicyController getPolicyController(PolicyContainer container) {
        return PolicyControllerConstants.getFactory().get(container.getGroupId(), container.getArtifactId());
    }

    /**
     * Runtime exceptions generated by this class. Wraps exceptions generated by delegated operations,
     * particularly when they are not, themselves, Runtime exceptions.
     */
    public static class PersistenceFeatureException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        /**
         * Constructor.
         * */
        public PersistenceFeatureException(Exception ex) {
            super(ex);
        }
    }
}
