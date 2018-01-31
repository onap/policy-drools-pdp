/*-
 * ============LICENSE_START=======================================================
 * feature-session-persistence
 * ================================================================================
 * Copyright (C) 2017 AT&T Intellectual Property. All rights reserved.
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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import org.eclipse.persistence.config.PersistenceUnitProperties;
import org.kie.api.KieServices;
import org.kie.api.runtime.Environment;
import org.kie.api.runtime.EnvironmentName;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.onap.policy.drools.core.PolicyContainer;
import org.onap.policy.drools.core.PolicySession;
import org.onap.policy.drools.core.PolicySessionFeatureAPI;
import org.onap.policy.drools.features.PolicyEngineFeatureAPI;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.drools.system.PolicyEngine;
import org.onap.policy.drools.utils.PropertyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bitronix.tm.BitronixTransactionManager;
import bitronix.tm.Configuration;
import bitronix.tm.TransactionManagerServices;
import bitronix.tm.resource.jdbc.PoolingDataSource;

/**
 * If this feature is supported, there is a single instance of it. It adds
 * persistence to Drools sessions. In addition, if an active-standby feature
 * exists, then that is used to determine the active and last-active PDP. If it
 * does not exist, then the current host name is used as the PDP id.
 *
 * The bulk of the code here was once in other classes, such as
 * 'PolicyContainer' and 'Main'. It was moved here as part of making this a
 * separate optional feature.
 */
public class PersistenceFeature implements PolicySessionFeatureAPI, PolicyEngineFeatureAPI {

	private static final Logger logger = LoggerFactory.getLogger(PersistenceFeature.class);


	/**
	 * Standard factory used to get various items.
	 */
	private static Factory stdFactory = new Factory();

	/**
	 * Factory used to get various items.
	 */
	private Factory fact = stdFactory;

	/**
	 * KieService factory.
	 */
	private KieServices kieSvcFact;

	/**
	 * Host name.
	 */
	private String hostName;

	/**
	 * Persistence properties.
	 */
	private Properties persistProps;

	/**
	 * Whether or not the SessionInfo records should be cleaned out.
	 */
	private boolean sessInfoCleaned;

	/**
	 * SessionInfo timeout, in milli-seconds, as read from
	 * {@link #persistProps}.
	 */
	private long sessionInfoTimeoutMs;

	/**
	 * Object used to serialize cleanup of sessioninfo table.
	 */
	private Object cleanupLock = new Object();

	/**
	 * Sets the factory to be used during junit testing.
	 *
	 * @param fact
	 *            factory to be used
	 */
	protected void setFactory(Factory fact) {
		this.fact = fact;
	}

	/**
	 * Lookup the adjunct for this feature that is associated with the specified
	 * PolicyContainer. If not found, create one.
	 *
	 * @param policyContainer
	 *            the container whose adjunct we are looking up, and possibly
	 *            creating
	 * @return the associated 'ContainerAdjunct' instance, which may be new
	 */
	private ContainerAdjunct getContainerAdjunct(PolicyContainer policyContainer) {

		Object rval = policyContainer.getAdjunct(this);

		if (rval == null || !(rval instanceof ContainerAdjunct)) {
			// adjunct does not exist, or has the wrong type (should never
			// happen)
			rval = new ContainerAdjunct(policyContainer);
			policyContainer.setAdjunct(this, rval);
		}

		return (ContainerAdjunct) rval;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getSequenceNumber() {
		return 1;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void globalInit(String args[], String configDir) {

		kieSvcFact = fact.getKieServices();

		initHostName();

		try {
			persistProps = fact.loadProperties(configDir + "/feature-session-persistence.properties");

		} catch (IOException e1) {
			logger.error("initializePersistence: ", e1);
		}

		sessionInfoTimeoutMs = getPersistenceTimeout();

		Configuration bitronixConfiguration = fact.getTransMgrConfig();
		bitronixConfiguration.setJournal(null);
		bitronixConfiguration.setServerId(hostName);
	}

	/**
	 * Creates a persistent KieSession, loading it from the persistent store, or
	 * creating one, if it does not exist yet.
	 */
	@Override
	public KieSession activatePolicySession(PolicyContainer policyContainer, String name, String kieBaseName) {

		if (isPersistenceEnabled(policyContainer, name)) {
			cleanUpSessionInfo();

			return getContainerAdjunct(policyContainer).newPersistentKieSession(name, kieBaseName);
		}

		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public PolicySession.ThreadModel selectThreadModel(PolicySession session) {
		PolicyContainer policyContainer = session.getPolicyContainer();
		if (isPersistenceEnabled(policyContainer, session.getName())) {
			return new PersistentThreadModel(session, getProperties(policyContainer));
		}
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void disposeKieSession(PolicySession policySession) {

		ContainerAdjunct contAdj = (ContainerAdjunct) policySession.getPolicyContainer().getAdjunct(this);
		if(contAdj != null) {
			contAdj.disposeKieSession( policySession.getName());
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void destroyKieSession(PolicySession policySession) {

		ContainerAdjunct contAdj = (ContainerAdjunct) policySession.getPolicyContainer().getAdjunct(this);
		if(contAdj != null) {
			contAdj.destroyKieSession( policySession.getName());
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean afterStart(PolicyEngine engine) {
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean beforeStart(PolicyEngine engine) {
		synchronized (cleanupLock) {
			sessInfoCleaned = false;
		}

		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean beforeActivate(PolicyEngine engine) {
		synchronized (cleanupLock) {
			sessInfoCleaned = false;
		}

		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean afterActivate(PolicyEngine engine) {
		return false;
	}

	/* ============================================================ */

	/**
	 * Gets the persistence timeout value for sessioninfo records.
	 *
	 * @return the timeout value, in milli-seconds, or {@code -1} if it is
	 *         unspecified or invalid
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
			logger.error("Invalid value for Drools persistence property persistence.sessioninfo.timeout: {}",
					 timeoutString, e);
		}

		return -1;
	}

	/**
	 * Initializes {@link #hostName}.
	 */
	private void initHostName() {

		try {
			hostName = fact.getHostName();

		} catch (UnknownHostException e) {
			throw new RuntimeException("cannot determine local hostname", e);
		}
	}

	/* ============================================================ */

	/**
	 * Each instance of this class is a logical extension of a 'PolicyContainer'
	 * instance. Its reference is stored in the 'adjuncts' table within the
	 * 'PolicyContainer', and will be garbage-collected with the container.
	 */
	protected class ContainerAdjunct {
		/**
		 * 'PolicyContainer' instance that this adjunct is extending.
		 */
		private PolicyContainer policyContainer;

		/**
		 * Maps a KIE session name to its data source.
		 */
		private Map<String,PoolingDataSource> name2ds = new HashMap<>();

		/**
		 * Constructor - initialize a new 'ContainerAdjunct'
		 *
		 * @param policyContainer
		 *            the 'PolicyContainer' instance this adjunct is extending
		 */
		private ContainerAdjunct(PolicyContainer policyContainer) {
			this.policyContainer = policyContainer;
		}

		/**
		 * Create a new persistent KieSession. If there is already a
		 * corresponding entry in the database, it is used to initialize the
		 * KieSession. If not, a completely new session is created.
		 *
		 * @param name
		 *            the name of the KieSession (which is also the name of the
		 *            associated PolicySession)
		 * @param kieBaseName
		 *            the name of the 'KieBase' instance containing this session
		 * @return a new KieSession with persistence enabled
		 */
		private KieSession newPersistentKieSession(String name, String kieBaseName) {

			long desiredSessionId;

			DroolsSessionConnector conn = getDroolsSessionConnector("onapPU");

			desiredSessionId = getSessionId(conn, name);

			logger.info("\n\nThis controller is primary... coming up with session {} \n\n", desiredSessionId);

			// session does not exist -- attempt to create one
			logger.info("getPolicySession:session does not exist -- attempt to create one with name {}", name);

			System.getProperties().put("java.naming.factory.initial", "bitronix.tm.jndi.BitronixInitialContextFactory");

			Environment env = kieSvcFact.newEnvironment();
			String dsName = loadDataSource(name);

			configureKieEnv(name, env, dsName);

			KieSessionConfiguration kConf = kieSvcFact.newKieSessionConfiguration();

			KieSession kieSession = desiredSessionId >= 0 ? loadKieSession(kieBaseName, desiredSessionId, env, kConf)
					: null;

			if (kieSession == null) {
				// loadKieSession() returned null or desiredSessionId < 0
				logger.info("LOADING We cannot load session {}. Going to create a new one", desiredSessionId);

				kieSession = newKieSession(kieBaseName, env);
			}

			replaceSession(conn, name, kieSession);

			return kieSession;
		}

		/**
		 * Loads a data source into {@link #name2ds}, if one doesn't exist
		 * yet.
		 * @param sessName		session name
		 * @return the unique data source name
		 */
		private String loadDataSource(String sessName) {
			PoolingDataSource ds = name2ds.get(sessName);

			if(ds == null) {
				Properties props = new Properties();
				addOptProp(props, "URL", persistProps.getProperty(DroolsPersistenceProperties.DB_URL));
				addOptProp(props, "user", persistProps.getProperty(DroolsPersistenceProperties.DB_USER));
				addOptProp(props, "password", persistProps.getProperty(DroolsPersistenceProperties.DB_PWD));

				ds = fact.makePoolingDataSource();
				ds.setUniqueName("jdbc/BitronixJTADataSource/" + sessName);
				ds.setClassName(persistProps.getProperty(DroolsPersistenceProperties.DB_DATA_SOURCE));
				ds.setMaxPoolSize(3);
				ds.setIsolationLevel("SERIALIZABLE");
				ds.setAllowLocalTransactions(true);
				ds.getDriverProperties().putAll(props);
				ds.init();

				name2ds.put(sessName, ds);
			}

			return ds.getUniqueName();
		}

		/**
		 * Configures a Kie Environment
		 *
		 * @param name
		 * 				session name
		 * @param env
		 * 				environment to be configured
		 * @param dsName
		 * 				data source name
		 */
		private void configureKieEnv(String name, Environment env, String dsName) {
			Properties emfProperties = new Properties();
			emfProperties.setProperty(PersistenceUnitProperties.JTA_DATASOURCE, dsName);

			EntityManagerFactory emfact = fact.makeEntMgrFact("onapsessionsPU", emfProperties);

			env.set(EnvironmentName.ENTITY_MANAGER_FACTORY, emfact);
			env.set(EnvironmentName.TRANSACTION_MANAGER, fact.getTransMgr());
		}

		/**
		 * Loads an existing KieSession from the persistent store.
		 *
		 * @param kieBaseName
		 *            the name of the 'KieBase' instance containing this session
		 * @param desiredSessionId
		 *            id of the desired KieSession
		 * @param env
		 *            Kie Environment for the session
		 * @param kConf
		 *            Kie Configuration for the session
		 * @return the persistent session, or {@code null} if it could not be
		 *         loaded
		 */
		private KieSession loadKieSession(String kieBaseName, long desiredSessionId, Environment env,
				KieSessionConfiguration kConf) {
			try {
				KieSession kieSession = kieSvcFact.getStoreServices().loadKieSession(desiredSessionId,
						policyContainer.getKieContainer().getKieBase(kieBaseName), kConf, env);

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
		 * @param kieBaseName
		 *            the name of the 'KieBase' instance containing this session
		 * @param env
		 *            Kie Environment for the session
		 * @return a new, persistent session
		 */
		private KieSession newKieSession(String kieBaseName, Environment env) {
			KieSession kieSession = kieSvcFact.getStoreServices()
					.newKieSession(policyContainer.getKieContainer().getKieBase(kieBaseName), null, env);

			logger.info("LOADING CREATED {}", kieSession.getIdentifier());

			return kieSession;
		}

		/**
		 * Closes the data source associated with a session.
		 * @param name	name of the session being destroyed
		 */
		private void destroyKieSession(String name) {
			closeDataSource(name);
		}

		/**
		 * Closes the data source associated with a session.
		 * @param name	name of the session being disposed of
		 */
		private void disposeKieSession(String name) {
			closeDataSource(name);
		}

		/**
		 * Closes the data source associated with a session.
		 * @param name	name of the session whose data source is to be closed
		 */
		private void closeDataSource(String name) {
			PoolingDataSource ds = name2ds.remove(name);
			if(ds != null) {
				ds.close();
			}
		}
	}

	/* ============================================================ */

	/**
	 * Removes "old" Drools 'sessioninfo' records, so they aren't used to
	 * restore data to Drools sessions. This also has the useful side-effect of
	 * removing abandoned records as well.
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

			// get DB connection properties
			String url = persistProps.getProperty(DroolsPersistenceProperties.DB_URL);
			String user = persistProps.getProperty(DroolsPersistenceProperties.DB_USER);
			String password = persistProps.getProperty(DroolsPersistenceProperties.DB_PWD);

			if (url == null || user == null || password == null) {
				logger.error("Missing DB properties for clean up of sessioninfo table");
				return;
			}

			// now do the record deletion
			try (Connection connection = fact.makeDbConnection(url, user, password);
					PreparedStatement statement = connection.prepareStatement(
							"DELETE FROM sessioninfo WHERE timestampdiff(second,lastmodificationdate,now()) > ?")) {
				statement.setLong(1, sessionInfoTimeoutMs/1000);

				int count = statement.executeUpdate();
				logger.info("Cleaning up sessioninfo table -- {} records removed", count);

			} catch (SQLException e) {
				logger.error("Clean up of sessioninfo table failed", e);
			}

			// TODO: delete DroolsSessionEntity where sessionId not in
			// (sessinfo.xxx)

			sessInfoCleaned = true;
		}
	}

	/**
	 * Gets a connector for manipulating DroolsSession objects within the
	 * persistent store.
	 *
	 * @param pu
	 * @return a connector for DroolsSession objects
	 */
	private DroolsSessionConnector getDroolsSessionConnector(String pu) {

		Properties propMap = new Properties();
		addOptProp(propMap, "javax.persistence.jdbc.driver",
				persistProps.getProperty(DroolsPersistenceProperties.DB_DRIVER));
		addOptProp(propMap, "javax.persistence.jdbc.url", persistProps.getProperty(DroolsPersistenceProperties.DB_URL));
		addOptProp(propMap, "javax.persistence.jdbc.user",
				persistProps.getProperty(DroolsPersistenceProperties.DB_USER));
		addOptProp(propMap, "javax.persistence.jdbc.password",
				persistProps.getProperty(DroolsPersistenceProperties.DB_PWD));

		return fact.makeJpaConnector(pu, propMap);
	}

	/**
	 * Adds an optional property to a set of properties.
	 * @param propMap	map into which the property should be added
	 * @param name		property name
	 * @param value		property value, or {@code null} if it should not
	 * 					be added
	 */
	private void addOptProp(Properties propMap, String name, String value) {
		if (value != null) {
			propMap.put(name, value);
		}
	}

	/**
	 * Gets a session's ID from the persistent store.
	 *
	 * @param conn
	 *            persistence connector
	 * @param sessnm
	 *            name of the session
	 * @return the session's id, or {@code -1} if the session is not found
	 */
	private long getSessionId(DroolsSessionConnector conn, String sessnm) {
		DroolsSession sess = conn.get(sessnm);
		return sess != null ? sess.getSessionId() : -1;
	}

	/**
	 * Replaces a session within the persistent store, if it exists.  Adds
	 * it otherwise.
	 *
	 * @param conn
	 *            persistence connector
	 * @param sessnm
	 *            name of session to be updated
	 * @param kieSession
	 *            new session information
	 */
	private void replaceSession(DroolsSessionConnector conn, String sessnm, KieSession kieSession) {

		DroolsSessionEntity sess = new DroolsSessionEntity();

		sess.setSessionName(sessnm);
		sess.setSessionId(kieSession.getIdentifier());

		conn.replace(sess);
	}

	/**
	 * Determine whether persistence is enabled for a specific container
	 *
	 * @param container
	 *            container to be checked
	 * @param sessionName
	 *            name of the session to be checked
	 * @return {@code true} if persistence is enabled for this container, and
	 *         {@code false} if not
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
	 * @param container
	 *            container whose properties are to be retrieved
	 * @return the container's properties, or {@code null} if not found
	 */
	private Properties getProperties(PolicyContainer container) {
		try {
			return fact.getPolicyContainer(container).getProperties();
		} catch (IllegalArgumentException e) {
			logger.error("getProperties exception: ", e);
			return null;
		}
	}

	/**
	 * Fetch the persistence property associated with a session. The name may
	 * have the form:
	 * <ul>
	 * <li>persistence.SESSION-NAME.PROPERTY</li>
	 * <li>persistence.PROPERTY</li>
	 * </ul>
	 *
	 * @param properties
	 *            properties from which the value is to be retrieved
	 * @param sessionName
	 *            session name of interest
	 * @param property
	 *            property name of interest
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
	 * This 'ThreadModel' variant periodically calls
	 * 'KieSession.fireAllRules()', because the 'fireUntilHalt' method isn't
	 * compatible with persistence.
	 */
	public class PersistentThreadModel implements Runnable, PolicySession.ThreadModel {

		/**
		 * Session associated with this persistent thread.
		 */
		private final PolicySession session;

		/**
		 * The session thread.
		 */
		private final Thread thread;

		/**
		 * Used to indicate that processing should stop.
		 */
		private final CountDownLatch stopped = new CountDownLatch(1);

		/**
		 * Minimum time, in milli-seconds, that the thread should sleep
		 * before firing rules again.
		 */
		long minSleepTime = 100;

		/**
		 * Maximum time, in milli-seconds, that the thread should sleep
		 * before firing rules again.  This is a "half" time, so that
		 * we can multiply it by two without overflowing the word size.
		 */
		long halfMaxSleepTime = 5000L / 2L;

		/**
		 * Constructor - initialize variables and create thread
		 *
		 * @param session
		 *            the 'PolicySession' instance
		 * @param properties
		 *            may contain additional session properties
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
					logger.error(sleepTimeString + ": Illegal value for 'minSleepTime'", e);
				}
			}

			// fetch 'maxSleepTime' value, and update if defined
			long maxSleepTime = 2 * halfMaxSleepTime;
			sleepTimeString = getProperty(properties, name, "maxSleepTime");
			if (sleepTimeString != null) {
				try {
					maxSleepTime = Math.max(1, Integer.valueOf(sleepTimeString));
				} catch (Exception e) {
					logger.error(sleepTimeString + ": Illegal value for 'maxSleepTime'", e);
				}
			}

			// swap values if needed
			if (minSleepTime > maxSleepTime) {
				logger.error("minSleepTime(" + minSleepTime + ") is greater than maxSleepTime(" + maxSleepTime
						+ ") -- swapping");
				long tmp = minSleepTime;
				minSleepTime = maxSleepTime;
				maxSleepTime = tmp;
			}

			halfMaxSleepTime = Math.max(1, maxSleepTime/2);
		}

		/**
		 * @return the String to use as the thread name
		 */
		private String getThreadName() {
			return "Session " + session.getFullName() + " (persistent)";
		}

		/***************************/
		/* 'ThreadModel' interface */
		/***************************/

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void start() {
			thread.start();
		}

		/**
		 * {@inheritDoc}
		 */
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
			if(thread.isAlive()) {
				logger.error("stopThread: still running");
			}
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void updated() {
			// the container artifact has been updated -- adjust the thread name
			thread.setName(getThreadName());
		}

		/************************/
		/* 'Runnable' interface */
		/************************/

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void run() {
			logger.info("PersistentThreadModel running");

			// set thread local variable
			session.setPolicySession();

			KieSession kieSession = session.getKieSession();
			long sleepTime = 2 * halfMaxSleepTime;

			// We want to continue, despite any exceptions that occur
			// while rules are fired.

			for(;;) {

				try {
					if (kieSession.fireAllRules() > 0) {
						// some rules fired -- reduce poll delay
						sleepTime = Math.max(minSleepTime, sleepTime/2);
					} else {
						// no rules fired -- increase poll delay
						sleepTime = 2 * Math.min(halfMaxSleepTime, sleepTime);
					}
				} catch (Exception | LinkageError e) {
					logger.error("Exception during kieSession.fireAllRules", e);
				}

				try {
					if(stopped.await(sleepTime, TimeUnit.MILLISECONDS)) {
						break;
					}

				} catch (InterruptedException e) {
					logger.error("startThread exception: ", e);
					Thread.currentThread().interrupt();
					break;
				}
			}

			logger.info("PersistentThreadModel completed");
		}
	}

	/* ============================================================ */

	/**
	 * Factory for various items. Methods can be overridden for junit testing.
	 */
	protected static class Factory {

		/**
		 * Gets the configuration for the transaction manager.
		 *
		 * @return the configuration for the transaction manager
		 */
		public Configuration getTransMgrConfig() {
			return TransactionManagerServices.getConfiguration();
		}

		/**
		 * Gets the transaction manager.
		 *
		 * @return the transaction manager
		 */
		public BitronixTransactionManager getTransMgr() {
			return TransactionManagerServices.getTransactionManager();
		}

		/**
		 * Gets the KIE services.
		 *
		 * @return the KIE services
		 */
		public KieServices getKieServices() {
			return KieServices.Factory.get();
		}

		/**
		 * Gets the current host name.
		 *
		 * @return the current host name, associated with the IP address of the
		 *         local machine
		 * @throws UnknownHostException
		 */
		public String getHostName() throws UnknownHostException {
			return InetAddress.getLocalHost().getHostName();
		}

		/**
		 * Loads properties from a file.
		 *
		 * @param filenm
		 *            name of the file to load
		 * @return properties, as loaded from the file
		 * @throws IOException
		 *             if an error occurs reading from the file
		 */
		public Properties loadProperties(String filenm) throws IOException {
			return PropertyUtil.getProperties(filenm);
		}

		/**
		 * Makes a connection to the DB.
		 *
		 * @param url
		 *            DB URL
		 * @param user
		 *            user name
		 * @param pass
		 *            password
		 * @return a new DB connection
		 * @throws SQLException
		 */
		public Connection makeDbConnection(String url, String user, String pass) throws SQLException {

			return DriverManager.getConnection(url, user, pass);
		}

		/**
		 * Makes a new pooling data source.
		 *
		 * @return a new pooling data source
		 */
		public PoolingDataSource makePoolingDataSource() {
			return new PoolingDataSource();
		}

		/**
		 * Makes a new JPA connector for drools sessions.
		 *
		 * @param pu
		 *            PU for the entity manager factory
		 * @param propMap
		 *            properties with which the factory should be configured
		 * @return a new JPA connector for drools sessions
		 */
		public DroolsSessionConnector makeJpaConnector(String pu, Properties propMap) {

			EntityManagerFactory emf = makeEntMgrFact(pu, propMap);

			return new JpaDroolsSessionConnector(emf);
		}

		/**
		 * Makes a new entity manager factory.
		 *
		 * @param pu
		 *            PU for the entity manager factory
		 * @param propMap
		 *            properties with which the factory should be configured
		 * @return a new entity manager factory
		 */
		public EntityManagerFactory makeEntMgrFact(String pu, Properties propMap) {
			return Persistence.createEntityManagerFactory(pu, propMap);
		}

		/**
		 * Gets the policy controller associated with a given policy container.
		 *
		 * @param container
		 *            container whose controller is to be retrieved
		 * @return the container's controller
		 */
		public PolicyController getPolicyContainer(PolicyContainer container) {
			return PolicyController.factory.get(container.getGroupId(), container.getArtifactId());
		}
	}
}
