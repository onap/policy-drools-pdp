/*-
 * ============LICENSE_START=======================================================
 * policy-persistence
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

package org.openecomp.policy.drools.persistence;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import org.eclipse.persistence.config.PersistenceUnitProperties;
import org.kie.api.KieServices;
import org.kie.api.runtime.Environment;
import org.kie.api.runtime.EnvironmentName;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.openecomp.policy.common.ia.IntegrityAudit;
import org.openecomp.policy.common.ia.IntegrityAuditProperties;
import org.openecomp.policy.common.im.StateManagement;
import org.openecomp.policy.common.logging.eelf.MessageCodes;
import org.openecomp.policy.common.logging.flexlogger.FlexLogger;
import org.openecomp.policy.common.logging.flexlogger.Logger;
import org.openecomp.policy.common.logging.flexlogger.PropertyUtil;
import org.openecomp.policy.drools.core.DroolsPDPIntegrityMonitor;
import org.openecomp.policy.drools.core.PolicySessionFeatureAPI;
import org.openecomp.policy.drools.core.IntegrityMonitorProperties;
import org.openecomp.policy.drools.core.PolicyContainer;
import org.openecomp.policy.drools.core.PolicySession;
import org.openecomp.policy.drools.features.PolicyEngineFeatureAPI;
import org.openecomp.policy.drools.im.PMStandbyStateChangeNotifier;
import org.openecomp.policy.drools.system.PolicyEngine;

import bitronix.tm.Configuration;
import bitronix.tm.TransactionManagerServices;
import bitronix.tm.resource.jdbc.PoolingDataSource;

/**
 * If this feature is supported, there is a single instance of it.
 * It adds persistence to Drools sessions, but it is also intertwined with
 * active/standby state management and IntegrityMonitor. For now, they are
 * all treated as a single feature, but it would be nice to separate them.
 *
 * The bulk of the code here was once in other classes, such as
 * 'PolicyContainer' and 'Main'. It was moved here as part of making this
 * a separate optional feature.
 */
public class PersistenceFeature implements PolicySessionFeatureAPI, PolicyEngineFeatureAPI
{
  // get an instance of logger
  private static Logger logger =
	FlexLogger.getLogger(PersistenceFeature.class);

  // 'KieServices' singleton
  static private KieServices kieServices = KieServices.Factory.get();

  private static DroolsPdp myPdp;
  private static Object myPdpSync = new Object();
  private static DroolsPdpsElectionHandler electionHandler;

  // indicates whether persistence has been disabled
  private static boolean persistenceDisabled = false;

  /*
   * Used by JUnit testing to verify whether or not audit is running.
   */
  private static IntegrityAudit integrityAudit = null;

  /**
   * Lookup the adjunct for this feature that is associated with the
   * specified PolicyContainer. If not found, create one.
   *
   * @param policyContainer the container whose adjunct we are looking up,
   *	and possibly creating
   * @return the associated 'ContainerAdjunct' instance, which may be new
   */
  private ContainerAdjunct getContainerAdjunct(PolicyContainer policyContainer)
  {
	Object rval = policyContainer.getAdjunct(this);
	if (rval == null || ! (rval instanceof ContainerAdjunct))
	  {
		// adjunct does not exist, or has the wrong type (should never happen)
		rval = new ContainerAdjunct(policyContainer);
		policyContainer.setAdjunct(this, rval);
	  }
	return((ContainerAdjunct)rval);
  }

  /**************************/
  /* 'FeatureAPI' interface */
  /**************************/

  /**
   * {@inheritDoc}
   */
  @Override
	public int getSequenceNumber()
  {
	return(1);
  }

  /**
   * {@inheritDoc}
   */
  @Override
	public void globalInit(String args[], String configDir)
  {
	// Initialization code associated with 'PolicyContainer'
	DroolsPDPIntegrityMonitor droolsPdpIntegrityMonitor = null;
	try
	  {
		droolsPdpIntegrityMonitor = DroolsPDPIntegrityMonitor.init(configDir);
	  }
	catch (Exception e)
	  {
		logger.error(MessageCodes.EXCEPTION_ERROR, e,
					 "main", "DroolsPDPIntegrityMonitor.init()");
	  }

	initializePersistence(configDir, droolsPdpIntegrityMonitor);

	// 1. Start Integrity Monitor (unless it was specifically disabled in the CORE layer
		
		
	if (persistenceDisabled) {
	  System.out.println("WARNING: Starting Engine with Persistance disabled");
	  logger.warn("Starting Engine with Persistance disabled");
	} else {
	  DroolsPDPIntegrityMonitor im = null;
	  //At this point the DroolsPDPIntegrityMonitor instance must exist
	  try {
		im = DroolsPDPIntegrityMonitor.getInstance();
	  } catch (Exception e1) {
		String msg = "policy-core startup failed to get DroolsPDPIntegrityMonitor instance:  \n" + e1;
		System.out.println(msg);
		e1.printStackTrace();
	  }
	  //Now get the StateManagement instance so we can register our observer
	  StateManagement sm = im.getStateManager();
			
	  //Create an instance of the Observer
	  PMStandbyStateChangeNotifier pmNotifier = new PMStandbyStateChangeNotifier();
			
	  //Register the PMStandbyStateChangeNotifier Observer
	  sm.addObserver(pmNotifier);
	}
  }

  /**
   * This is a hook to create a new persistent KieSession.
   *
   * {@inheritDoc}
   */
  @Override
	public KieSession activatePolicySession
	(PolicyContainer policyContainer, String name, String kieBaseName)
  {
	return(getContainerAdjunct(policyContainer)
		   .newPersistentKieSession(name, kieBaseName));
  }

  /**
   * {@inheritDoc}
   */
  @Override
	public void disposeKieSession(PolicySession policySession)
  {
	// TODO: There should be one data source per session
	getContainerAdjunct(policySession.getPolicyContainer())
	  .disposeKieSession();
  }

  /**
   * {@inheritDoc}
   */
  @Override
	public void destroyKieSession(PolicySession policySession)
  {
	// TODO: There should be one data source per session
	getContainerAdjunct(policySession.getPolicyContainer())
	  .destroyKieSession();
  }

  /**
   * {@inheritDoc}
   */
  @Override
	public boolean isPersistenceEnabled()
  {
	return(!persistenceDisabled);
  }
	
  /**
   * {@inheritDoc}
   */
  @Override
	public boolean afterStart(PolicyEngine engine) 
  {
	// ASSERTION: engine == PolicyEngine.manager
    PolicyEngine.manager.lock();
    return false;
  }
	
   /**
	* {@inheritDoc}
	*/
   @Override
	public boolean beforeStart(PolicyEngine engine) {return false;}
	
   /**
	* {@inheritDoc}
	*/
   @Override
	public boolean beforeShutdown(PolicyEngine engine) {return false;}

   /**
	* {@inheritDoc}
	*/
   @Override
	public boolean afterShutdown(PolicyEngine engine) {return false;}
   
   /**
	* {@inheritDoc}
	*/
   @Override
    public boolean beforeConfigure(PolicyEngine engine, Properties properties) {return false;}

   /**
	* {@inheritDoc}
	*/
   @Override
    public boolean afterConfigure(PolicyEngine engine) {return false;}

   /**
	* {@inheritDoc}
	*/
   @Override
	public boolean beforeActivate(PolicyEngine engine)
  {
	if (persistenceDisabled)
	  {
		return(false);
	  }

	// The following code will remove "old" Drools 'sessioninfo' records, so
	// they aren't used to restore data to Drools sessions. This also has the
	// useful side-effect of removing abandoned records as well.

	// Fetch the timeout value, in seconds. If it is not specified or is
	// less than or equal to 0, no records are removed.

	String timeoutString = null;
	int timeout = 0;
	try
	  {
		timeoutString = DroolsPersistenceProperties.getProperty
		  ("persistence.sessioninfo.timeout");
		if (timeoutString != null)
		  {
			// timeout parameter is specified
			timeout = Integer.valueOf(timeoutString);
		  }
	  }
	catch (NumberFormatException e)
	  {
		logger.error("Invalid value for Drools persistence property "
					 + "persistence.sessioninfo.timeout: "
					 + timeoutString);
	  }
	if (timeout <= 0)
	  {
		// parameter is not specified, is <= 0, or is an invalid number
		return(false);
	  }

	// if we reach this point, we are ready to remove old records from
	// the database

	Connection connection = null;
	PreparedStatement statement = null;
	try
	  {
		// fetch database parameters from properties

		String url = DroolsPersistenceProperties.getProperty
		  (DroolsPersistenceProperties.DB_URL);
		String user = DroolsPersistenceProperties.getProperty
		  (DroolsPersistenceProperties.DB_USER);
		String password = DroolsPersistenceProperties.getProperty
		  (DroolsPersistenceProperties.DB_PWD);

		if (url != null && user != null && password != null)
		  {
			// get DB connection
			connection = DriverManager.getConnection(url, user, password);

			// create statement to delete old records
			statement = connection.prepareStatement
			  ("DELETE FROM sessioninfo WHERE "
			   + "timestampdiff(second,lastmodificationdate,now()) > ?");
			statement.setInt(1,timeout);

			// execute statement
			int count = statement.executeUpdate();
			logger.info("Cleaning up sessioninfo table -- "
						+ count + " records removed");
		  }
	  }
	catch (SQLException e)
	  {
		logger.error("Clean up of sessioninfo table failed", e);
	  }
	finally
	  {
		// cleanup
		if (statement != null)
		  {
			try
			  {
				statement.close();
			  }
			catch (SQLException e)
			  {
				logger.error("SQL connection close failed", e);
			  }
		  }
		if (connection != null)
		  {
			try
			  {
				connection.close();
			  }
			catch (SQLException e)
			  {
				logger.error("SQL connection close failed", e);
			  }
		  }
	  }
	return(false);
  }

   /**
	* {@inheritDoc}
	*/
   @Override
   public boolean afterActivate(PolicyEngine engine) {return false;}

   /**
	* {@inheritDoc}
	*/
   @Override
    public boolean beforeDeactivate(PolicyEngine engine) {return false;}

   /**
	* {@inheritDoc}
	*/
   @Override
    public boolean afterDeactivate(PolicyEngine engine) {return false;}

   /**
	* {@inheritDoc}
	*/
   @Override
    public boolean beforeStop(PolicyEngine engine) {return false;}

   /**
	* {@inheritDoc}
	*/
   @Override
    public boolean afterStop(PolicyEngine engine) {return false;}

   /**
	* {@inheritDoc}
	*/
   @Override
    public boolean beforeLock(PolicyEngine engine) {return false;}

   /**
	* {@inheritDoc}
	*/
   @Override
    public boolean afterLock(PolicyEngine engine) {return false;}

   /**
	* {@inheritDoc}
	*/
   @Override
    public boolean beforeUnlock(PolicyEngine engine) {return false;}

   /**
	* {@inheritDoc}
	*/
   @Override
    public boolean afterUnlock(PolicyEngine engine) {return false;}

  /**************************/

  /**
   * @return 'true' if Drools persistence is disabled, and 'false' if not
   */
  static public boolean getPersistenceDisabled()
  {
	return(persistenceDisabled);
  }

  /**
   * Read in the persistence properties, determine whether persistence is
   * enabled or disabled, and initialize persistence if enabled.
   */
  private static void initializePersistence(String configDir, DroolsPDPIntegrityMonitor droolsPdpIntegrityMonitor)
  {
	  
		try {
			Properties pDrools = PropertyUtil.getProperties(configDir
					+ "/droolsPersistence.properties");
			DroolsPersistenceProperties.initProperties(pDrools);
			Properties pXacml = PropertyUtil.getProperties(configDir
					+ "/xacmlPersistence.properties");
			XacmlPersistenceProperties.initProperties(pXacml);
			if ("true".equals(pDrools.getProperty("persistenceDisabled"))) {
				// 'persistenceDisabled' only relates to the 'drools'
				// database. The fact that integrityMonitor/xacml depends upon
				// persistence is an implementation detail there (which can't
				// currently be disabled), and doesn't directly affect
				// 'policy-core'.
				persistenceDisabled = true;
			} 
		} catch (IOException e1) {
			logger.error(MessageCodes.MISS_PROPERTY_ERROR, e1,
					"initializePersistence");
		}
	  	  
	    /*
	     * Might as well handle the Integrity Monitor properties here, too.
	     */
	    try {
		  Properties pIm =
		    PropertyUtil.getProperties(configDir + "/IntegrityMonitor.properties");
		  IntegrityMonitorProperties.initProperties(pIm);
		  logger.info("initializePersistence: resourceName=" + IntegrityMonitorProperties.getProperty(IntegrityMonitorProperties.PDP_INSTANCE_ID));
	    } catch (IOException e1) {
		  logger.error(MessageCodes.MISS_PROPERTY_ERROR, e1, "initializePersistence");
	    }
	  

		if (persistenceDisabled) {
			// The persistence design is tied to 'DroolsPdpsElectionHandler',
			// so we should bypass that as well. This also means that we
			// won't get active/standby notifications, so we need to go
			// into the 'active' state in order to have any 'PolicySession'
			// instances.
			return;
		}

	    DroolsPdpsConnector conn = getDroolsPdpsConnector("ncompPU");
		String uniquePdpId = IntegrityMonitorProperties.getProperty(IntegrityMonitorProperties.PDP_INSTANCE_ID);
		if(uniquePdpId == null){
			throw new NullPointerException();
		}
		
		/*
		 * In a JUnit test environment, one or more PDPs may already have been
		 * inserted in the DB, so we need to check for this.
		 */
		DroolsPdp existingPdp = conn.getPdp(uniquePdpId);
		if (existingPdp != null) {
			System.out.println("Found existing PDP record, pdpId="
					+ existingPdp.getPdpId() + ", isDesignated="
					+ existingPdp.isDesignated() + ", updatedDate="
					+ existingPdp.getUpdatedDate());
			myPdp = existingPdp;
		}
		
	    /*
	     * Kick off integrity audit for Drools DB.
	     */
	    startIntegrityAudit(configDir);
				
		synchronized(myPdpSync){
			if(myPdp == null){

				myPdp = new DroolsPdpImpl(uniquePdpId,false,4,new Date());	
			}
			if(myPdp != null){
				String site_name = "";
				site_name = IntegrityMonitorProperties.getProperty(IntegrityMonitorProperties.SITE_NAME);
				if (site_name == null) {
					site_name = "";
				}else{
					site_name = site_name.trim();
				}
				myPdp.setSiteName(site_name);
			}
			if(electionHandler == null){
		electionHandler = new DroolsPdpsElectionHandler(conn,myPdp,droolsPdpIntegrityMonitor);
			}
		}
		Configuration bitronixConfiguration = TransactionManagerServices.getConfiguration();
		bitronixConfiguration.setJournal(null);
		bitronixConfiguration.setServerId(uniquePdpId);
		System.out.println("\n\nThis controller is a standby, waiting to be chosen as primary...\n\n");
		logger.info("\n\nThis controller is a standby, waiting to be chosen as primary...\n\n");
  }

	private static void startIntegrityAudit(String configDir) {

		logger.info("startIntegrityAudit: Entering, configDir='" + configDir
				+ "'");

		/*
		 * Initialize Integrity Audit properties. file.
		 */
		try {

			String resourceName = IntegrityMonitorProperties
					.getProperty(IntegrityMonitorProperties.PDP_INSTANCE_ID);

			/*
			 * Load properties for auditing of Drools DB.
			 */
			Properties droolsPia = PropertyUtil.getProperties(configDir
					+ "/IntegrityMonitor.properties");

			/*
			 * Supplement properties specific to the IntegrityMonitor (e.g.
			 * site_name, node_type, resource.name) with properties specific to
			 * persisting Drools DB entities (see
			 * ../policy-core/src/main/resources/persistence.xml)
			 * 
			 * Note: integrity_audit_period_seconds is defined in
			 * IntegrityMonitor.properties, rather than creating a whole new
			 * "IntegrityAudit.properties" file for just one property.
			 */
			droolsPia
					.setProperty(
							IntegrityAuditProperties.DB_DRIVER,
							DroolsPersistenceProperties
									.getProperty(DroolsPersistenceProperties.DB_DRIVER));
			droolsPia.setProperty(IntegrityAuditProperties.DB_PWD,
					DroolsPersistenceProperties
							.getProperty(DroolsPersistenceProperties.DB_PWD));
			droolsPia.setProperty(IntegrityAuditProperties.DB_URL,
					DroolsPersistenceProperties
							.getProperty(DroolsPersistenceProperties.DB_URL));
			droolsPia.setProperty(IntegrityAuditProperties.DB_USER,
					DroolsPersistenceProperties
							.getProperty(DroolsPersistenceProperties.DB_USER));

			/*
			 * Start audit for Drools DB.
			 */
			integrityAudit = new IntegrityAudit(
					resourceName, "auditDroolsPU", droolsPia);
			integrityAudit.startAuditThread();

		} catch (IOException e1) {
			logger.error(
					MessageCodes.MISS_PROPERTY_ERROR,
					e1,
					"initializePersistence: IntegrityAuditProperties: "
							+ e1.getMessage());
		} catch (Exception e2) {
			logger.error(
					MessageCodes.EXCEPTION_ERROR,
					e2,
					"initializePersistence: IntegrityAuditProperties: "
							+ e2.getMessage());
		}

		logger.debug("startIntegrityAudit: Exiting");

	}
  
	/*
	 * Moved code to instantiate a JpaDroolsPdpsConnector object from main() to
	 * this method, so it can also be accessed from StandbyStateChangeNotifier
	 * class.
	 */
	public static DroolsPdpsConnector getDroolsPdpsConnector(String pu) {

		Map<String, Object> propMap = new HashMap<String, Object>();
		propMap.put("javax.persistence.jdbc.driver", DroolsPersistenceProperties
				.getProperty(DroolsPersistenceProperties.DB_DRIVER));
		propMap.put("javax.persistence.jdbc.url",
				DroolsPersistenceProperties.getProperty(DroolsPersistenceProperties.DB_URL));
		propMap.put("javax.persistence.jdbc.user", DroolsPersistenceProperties
				.getProperty(DroolsPersistenceProperties.DB_USER));
		propMap.put("javax.persistence.jdbc.password",
				DroolsPersistenceProperties.getProperty(DroolsPersistenceProperties.DB_PWD));

		EntityManagerFactory emf = Persistence.createEntityManagerFactory(
				pu, propMap);
		DroolsPdpsConnector conn = new JpaDroolsPdpsConnector(emf);

		return conn;
	}

	/*
	 * IntegrityAudit instance is needed by JUnit testing to ascertain whether
	 * or not audit is running.
	 */
	public static IntegrityAudit getIntegrityAudit() {

		return integrityAudit;

	}

  /* ============================================================ */

  /**
   * Each instance of this class is a logical extension of a 'PolicyContainer'
   * instance. It's reference is stored in the 'adjuncts' table within the
   * 'PolicyContainer', and will be garbage-collected with the container.
   */
  class ContainerAdjunct
  {
	// this is the 'PolicyContainer' instance that this adjunct is extending
	private PolicyContainer policyContainer;
	private PoolingDataSource ds = null;

	/**
	 * Constructor - initialize a new 'ContainerAdjunct'
	 *
	 * @param policyContainer the 'PolicyContainer' instance this adjunct
	 *		is extending
	 */
	ContainerAdjunct(PolicyContainer policyContainer)
	{
	  this.policyContainer = policyContainer;
	}

	/**
	 * Create a new persistent KieSession. If there is already a corresponding
	 * entry in the database, it is used to initialize the KieSession. If not,
	 * a completely new session is created.
	 *
	 * @param name the name of the KieSession (which is also the name of
	 *	the associated PolicySession)
	 * @param kieBaseName the name of the 'KieBase' instance containing
	 *	this session
	 * @return a new KieSession with persistence enabled (if persistence is
	 *	disabled, 'null' is returned
	 */
	private KieSession newPersistentKieSession(String name, String kieBaseName)
	{
	  if (persistenceDisabled)
		{
		  return(null);
		}
	  long desiredSessionId = -1;
	  synchronized (myPdpSync) {
		
	
	
		for(DroolsSession droolsSession : electionHandler.getSessions()){
		  if(droolsSession.getSessionName().equals(name)){
			desiredSessionId = droolsSession.getSessionId();
		  }
		}
	  }
	  System.out.println("\n\nThis controller is primary... coming up with session "+desiredSessionId+"\n\n");
	  logger.info("\n\nThis controller is primary... coming up with session "+desiredSessionId+"\n\n");
	  Map<String, Object> props = new HashMap<String, Object>();
	  props.put("URL", DroolsPersistenceProperties.getProperty(DroolsPersistenceProperties.DB_URL));
	  props.put("user", DroolsPersistenceProperties.getProperty(DroolsPersistenceProperties.DB_USER));
	  props.put("password", DroolsPersistenceProperties.getProperty(DroolsPersistenceProperties.DB_PWD));
	  props.put("dataSource",DroolsPersistenceProperties.getProperty(DroolsPersistenceProperties.DB_DATA_SOURCE));
	  logger.info("getPolicySession:session does not exist -- attempt to create one with name " + name);
	  // session does not exist -- attempt to create one
	  System.getProperties().put("java.naming.factory.initial","bitronix.tm.jndi.BitronixInitialContextFactory");
	  Environment env = kieServices.newEnvironment();
	  //kContainer.newKieBase(null);
	  ds = new PoolingDataSource();			
	  ds.setUniqueName("jdbc/BitronixJTADataSource"+name);
	  ds.setClassName( (String)props.remove("dataSource"));
	  //ds.setClassName( "org.h2.Driver" );
	  ds.setMaxPoolSize( 3 );
	  ds.setIsolationLevel("SERIALIZABLE");
	  ds.setAllowLocalTransactions( true );
	  //ds.getDriverProperties().put( "user", "sa" );
	  //ds.getDriverProperties().put( "password", "" );
	  //ds.getDriverProperties().put( "URL", "jdbc:h2:tcp://localhost/drools" );
	  ds.getDriverProperties().putAll(props);
	  ds.init();	
	  Properties emfProperties = new Properties();
	  emfProperties.setProperty(PersistenceUnitProperties.JTA_DATASOURCE, "jdbc/BitronixJTADataSource"+name);
	  env.set(EnvironmentName.ENTITY_MANAGER_FACTORY, Persistence.createEntityManagerFactory("ncompsessionsPU",emfProperties));
	  env.set(EnvironmentName.TRANSACTION_MANAGER,TransactionManagerServices.getTransactionManager());
	  KieSessionConfiguration kConf = KieServices.Factory.get().newKieSessionConfiguration();
	  KieSession kieSession;
	  try{
		kieSession = kieServices.getStoreServices().loadKieSession(desiredSessionId, policyContainer.getKieContainer().getKieBase(kieBaseName), kConf, env);
		System.out.println("LOADING We can load session "+desiredSessionId+", going to create a new one");
		logger.info("LOADING We can load session "+desiredSessionId+", going to create a new one");
	  }catch(Exception e){
		System.out.println("LOADING We cannot load session "+desiredSessionId+", going to create a new one");

		logger.info("LOADING We cannot load session "+desiredSessionId+", going to create a new one");
		kieSession = kieServices.getStoreServices().newKieSession(policyContainer.getKieContainer().getKieBase(kieBaseName), null, env);
		System.out.println("LOADING CREATED "+kieSession.getIdentifier());
		logger.info("LOADING CREATED "+kieSession.getIdentifier());
	  }
	  synchronized (myPdpSync) {
		myPdp.setSessionId(name,kieSession.getIdentifier());
		electionHandler.updateMyPdp();
	  }
	  return(kieSession);
	}

	private void disposeKieSession()
	{
	if (ds != null)
	  {
		ds.close();
		ds = null;
	  }
	}

	private void destroyKieSession()
	{
	  // does the same thing as 'dispose'
	  disposeKieSession();
	}
  }
}
