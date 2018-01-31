/*-
 * ============LICENSE_START=======================================================
 * feature-state-management
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

package org.onap.policy.drools.statemanagement;

import java.util.ArrayList;
import java.util.Properties;
import org.onap.policy.common.im.IntegrityMonitor;
import org.onap.policy.common.im.IntegrityMonitorException;
import org.onap.policy.drools.http.server.HttpServletServer;
import org.onap.policy.drools.properties.Startable;
import org.onap.policy.drools.utils.PropertyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class extends 'IntegrityMonitor' for use in the 'Drools PDP'
 * virtual machine. The included audits are 'Database' and 'Repository'.
 */
public class DroolsPDPIntegrityMonitor extends IntegrityMonitor
{

	private static final String INVALID_PROPERTY_VALUE = "init: property {} does not have the expected value of {}";

// get an instance of logger
  private static final Logger  logger = LoggerFactory.getLogger(DroolsPDPIntegrityMonitor.class);

  // static global instance
  private static DroolsPDPIntegrityMonitor im = null;

  // list of audits to run
  private static AuditBase[] audits =
	new AuditBase[]{DbAudit.getInstance(), RepositoryAudit.getInstance()};

  private static Properties subsystemTestProperties = null;

  private static final String PROPERTIES_NAME = "feature-state-management.properties";

  /**
   * Constructor - pass arguments to superclass, but remember properties
   * @param resourceName unique name of this Integrity Monitor
   * @param url the JMX URL of the MBean server
   * @param properties properties used locally, as well as by
   *	'IntegrityMonitor'
   * @throws Exception (passed from superclass)
   */
	private DroolsPDPIntegrityMonitor(String resourceName,
			Properties consolidatedProperties
			) throws Exception {
	super(resourceName, consolidatedProperties);
  }

  private static void missingProperty(String prop) throws StateManagementPropertiesException{
		String msg = "init: missing IntegrityMonitor property: ".concat(prop);
		logger.error(msg);
		throw new StateManagementPropertiesException(msg);
  }

  private static void logPropertyValue(String prop, String val){
	  if(logger.isInfoEnabled()){
		  String msg = "\n\n    init: property: " + prop + " = " + val + "\n";
		  logger.info(msg);
	  }
  }

  /**
   * Static initialization -- create Drools Integrity Monitor, and
   * an HTTP server to handle REST 'test' requests
   */
  public static DroolsPDPIntegrityMonitor init(String configDir) throws Exception
  {
	  
	logger.info("init: Entering and invoking PropertyUtil.getProperties() on '{}'", configDir);

	// read in properties
	Properties stateManagementProperties =
	  PropertyUtil.getProperties(configDir + "/" + PROPERTIES_NAME);
	// fetch and verify definitions of some properties
	// (the 'IntegrityMonitor' constructor does some additional verification)
	String testHost = stateManagementProperties.getProperty(StateManagementProperties.TEST_HOST);
	String testPort = stateManagementProperties.getProperty(StateManagementProperties.TEST_PORT);
    String testServices = stateManagementProperties.getProperty(StateManagementProperties.TEST_SERVICES);
    String testRestClasses = stateManagementProperties.getProperty(StateManagementProperties.TEST_REST_CLASSES);
    String testManaged = stateManagementProperties.getProperty(StateManagementProperties.TEST_MANAGED);
    String testSwagger = stateManagementProperties.getProperty(StateManagementProperties.TEST_SWAGGER);
	String resourceName = stateManagementProperties.getProperty(StateManagementProperties.RESOURCE_NAME);
	String fpMonitorInterval = stateManagementProperties.getProperty(StateManagementProperties.FP_MONITOR_INTERVAL);
	String failedCounterThreshold = stateManagementProperties.getProperty(StateManagementProperties.FAILED_COUNTER_THRESHOLD);
	String testTransInterval = stateManagementProperties.getProperty(StateManagementProperties.TEST_TRANS_INTERVAL);
	String writeFpcInterval = stateManagementProperties.getProperty(StateManagementProperties.WRITE_FPC_INTERVAL);
	String siteName = stateManagementProperties.getProperty(StateManagementProperties.SITE_NAME);
	String nodeType = stateManagementProperties.getProperty(StateManagementProperties.NODE_TYPE);
	String dependencyGroups = stateManagementProperties.getProperty(StateManagementProperties.DEPENDENCY_GROUPS);
	String javaxPersistenceJdbcDriver = stateManagementProperties.getProperty(StateManagementProperties.DB_DRIVER);
	String javaxPersistenceJdbcUrl = stateManagementProperties.getProperty(StateManagementProperties.DB_URL);
	String javaxPersistenceJdbcUser = stateManagementProperties.getProperty(StateManagementProperties.DB_USER);
	String javaxPersistenceJdbcPassword = stateManagementProperties.getProperty(StateManagementProperties.DB_PWD);

	if (testHost == null){
		missingProperty(StateManagementProperties.TEST_HOST);
	}
	if (testPort == null){
		missingProperty(StateManagementProperties.TEST_PORT);
	}
    if (testServices == null) {
        testServices = StateManagementProperties.TEST_SERVICES_DEFAULT;
        stateManagementProperties.put(StateManagementProperties.TEST_SERVICES, testServices);
    }
    if (testRestClasses == null) {
        testRestClasses = StateManagementProperties.TEST_REST_CLASSES_DEFAULT;
        stateManagementProperties.put(StateManagementProperties.TEST_REST_CLASSES, testRestClasses);
    }
    if (testManaged == null) {
        testManaged = StateManagementProperties.TEST_MANAGED_DEFAULT;
        stateManagementProperties.put(StateManagementProperties.TEST_MANAGED, testManaged);
    }
    if (testSwagger == null) {
        testSwagger = StateManagementProperties.TEST_SWAGGER_DEFAULT;
        stateManagementProperties.put(StateManagementProperties.TEST_SWAGGER, testSwagger);
    }
	if (!testServices.equals(StateManagementProperties.TEST_SERVICES_DEFAULT)){
		logger.error(INVALID_PROPERTY_VALUE,
				StateManagementProperties.TEST_SERVICES,
				StateManagementProperties.TEST_SERVICES_DEFAULT);
	}
	if (!testRestClasses.equals(StateManagementProperties.TEST_REST_CLASSES_DEFAULT)){
		logger.error(INVALID_PROPERTY_VALUE,
				StateManagementProperties.TEST_REST_CLASSES,
				StateManagementProperties.TEST_REST_CLASSES_DEFAULT);
	}
	if (!testManaged.equals(StateManagementProperties.TEST_MANAGED_DEFAULT)){
		logger.warn(INVALID_PROPERTY_VALUE,
				StateManagementProperties.TEST_MANAGED,
				StateManagementProperties.TEST_MANAGED_DEFAULT);
	}
	if (!testSwagger.equals(StateManagementProperties.TEST_SWAGGER_DEFAULT)){
		logger.warn(INVALID_PROPERTY_VALUE,
				StateManagementProperties.TEST_SWAGGER,
				StateManagementProperties.TEST_SWAGGER_DEFAULT);
	}
	if (resourceName == null){
		missingProperty(StateManagementProperties.RESOURCE_NAME);
	  }
	if (fpMonitorInterval == null){
		missingProperty(StateManagementProperties.FP_MONITOR_INTERVAL);
	  }
	if (failedCounterThreshold == null){
		missingProperty(StateManagementProperties.FAILED_COUNTER_THRESHOLD);
	  }
	if (testTransInterval == null){
		missingProperty(StateManagementProperties.TEST_TRANS_INTERVAL);
	  }
	if (writeFpcInterval == null){
		missingProperty(StateManagementProperties.WRITE_FPC_INTERVAL);
	  }
	if (siteName == null){
		missingProperty(StateManagementProperties.SITE_NAME);
	  }
	if (nodeType == null){
		missingProperty(StateManagementProperties.NODE_TYPE);
	  }
	if (dependencyGroups == null){
		missingProperty(StateManagementProperties.DEPENDENCY_GROUPS);
	  }
	if (javaxPersistenceJdbcDriver == null){
		missingProperty(StateManagementProperties.DB_DRIVER);
	  }
	if (javaxPersistenceJdbcUrl == null){
		missingProperty(StateManagementProperties.DB_URL);
	  }
	if (javaxPersistenceJdbcUser == null){
		missingProperty(StateManagementProperties.DB_USER);
	  }
	if (javaxPersistenceJdbcPassword == null){
		missingProperty(StateManagementProperties.DB_PWD);
	  }

	//Log the values so we can diagnose any issues
	logPropertyValue(StateManagementProperties.TEST_HOST,testHost);
	logPropertyValue(StateManagementProperties.TEST_PORT,testPort);
	logPropertyValue(StateManagementProperties.TEST_SERVICES,testServices);
	logPropertyValue(StateManagementProperties.TEST_REST_CLASSES,testRestClasses);
	logPropertyValue(StateManagementProperties.TEST_MANAGED,testManaged);
	logPropertyValue(StateManagementProperties.TEST_SWAGGER,testSwagger);
	logPropertyValue(StateManagementProperties.RESOURCE_NAME,resourceName);
	logPropertyValue(StateManagementProperties.FP_MONITOR_INTERVAL,fpMonitorInterval);
	logPropertyValue(StateManagementProperties.FAILED_COUNTER_THRESHOLD,failedCounterThreshold);
	logPropertyValue(StateManagementProperties.TEST_TRANS_INTERVAL,testTransInterval);
	logPropertyValue(StateManagementProperties.WRITE_FPC_INTERVAL,writeFpcInterval);
	logPropertyValue(StateManagementProperties.SITE_NAME,siteName);
	logPropertyValue(StateManagementProperties.NODE_TYPE,nodeType);
	logPropertyValue(StateManagementProperties.DEPENDENCY_GROUPS,dependencyGroups);
	logPropertyValue(StateManagementProperties.DB_DRIVER,javaxPersistenceJdbcDriver);
	logPropertyValue(StateManagementProperties.DB_URL,javaxPersistenceJdbcUrl);
	logPropertyValue(StateManagementProperties.DB_USER,javaxPersistenceJdbcUser);
	logPropertyValue(StateManagementProperties.DB_PWD,javaxPersistenceJdbcPassword);

	subsystemTestProperties = stateManagementProperties;

	// Now that we've validated the properties, create Drools Integrity Monitor
	// with these properties.
	im = new DroolsPDPIntegrityMonitor(resourceName,
				stateManagementProperties);
	logger.info("init: New DroolsPDPIntegrityMonitor instantiated, resourceName = ", resourceName);

	// create http server
	try {
		logger.info("init: Starting HTTP server, addr= {}", testHost+":"+testPort);
		IntegrityMonitorRestServer server = new IntegrityMonitorRestServer();

		server.init(stateManagementProperties);
	} catch (Exception e) {
		logger.error("init: Caught Exception attempting to start server on testPort= {} message:",
								testPort, e);
		throw e;
	}
	logger.info("init: Exiting and returning DroolsPDPIntegrityMonitor");
	return im;
  }

  /**
   * Run tests (audits) unique to Drools PDP VM (Database + Repository)
   */
  @Override
	public void subsystemTest() throws IntegrityMonitorException
  {
	logger.info("DroolsPDPIntegrityMonitor.subsystemTest called");

	// clear all responses (non-null values indicate an error)
	for (AuditBase audit : audits)
	  {
		audit.setResponse(null);
	  }

	// invoke all of the audits
	for (AuditBase audit : audits)
	  {
		try
		  {
			// invoke the audit (responses are stored within the audit object)
			audit.invoke(subsystemTestProperties);
		  }
		catch (Exception e)
		  {
			logger.error("{} audit error", audit.getName(), e);
			if (audit.getResponse() == null)
			  {
				// if there is no current response, use the exception message
				audit.setResponse(e.getMessage());
			  }
		  }
	  }

	  // will contain list of subsystems where the audit failed
	  String responseMsg = "";

	  // Loop through all of the audits, and see which ones have failed.
	  // NOTE: response information is stored within the audit objects
	  // themselves -- only one can run at a time.
	  for (AuditBase audit : audits)
		{
		  String response = audit.getResponse();
		  if (response != null)
			{
			  // the audit has failed -- add subsystem and
			  // and 'responseValue' with the new information
			  responseMsg = responseMsg.concat("\n" + audit.getName() + ": " + response);
			}
		}

	  if(!responseMsg.isEmpty()){
		  throw new IntegrityMonitorException(responseMsg);
	  }
  }

  /* ============================================================ */

  /**
   * This is the base class for audits invoked in 'subsystemTest'
   */
  public abstract static class AuditBase
  {
	// name of the audit
	protected String name;

	// non-null indicates the error response
	protected String response;

	/**
	 * Constructor - initialize the name, and clear the initial response
	 * @param name name of the audit
	 */
	public AuditBase(String name)
	{
	  this.name = name;
	  this.response = null;
	}

	/**
	 * @return the name of this audit
	 */
	public String getName()
	{
	  return name;
	}

	/**
	 * @return the response String (non-null indicates the error message)
	 */
	public String getResponse()
	{
	  return response;
	}

	/**
	 * Set the response string to the specified value
	 * @param value the new value of the response string (null = no errors)
	 */
	public void setResponse(String value)
	{
	  response = value;
	}

	/**
	 * Abstract method to invoke the audit
	 * @param persistenceProperties Used for DB access
	 * @throws Exception passed in by the audit
	 */
	abstract void invoke(Properties persistenceProperties) throws Exception;
  }

  	public static class IntegrityMonitorRestServer implements Startable {
  		protected volatile HttpServletServer server = null;
  		protected volatile Properties integrityMonitorRestServerProperties = null;
  
  		public void init(Properties props) {
			this.integrityMonitorRestServerProperties = props;
			this.start();
  		}
  
  		@Override
		public boolean start() {
			try {
				ArrayList<HttpServletServer> servers = HttpServletServer.factory.build(integrityMonitorRestServerProperties);

				if (!servers.isEmpty()) {
					server = servers.get(0);

					waitServerStart();
				}
			} catch (Exception e) {
				logger.error("Exception building servers", e);
				return false;
			}

			return true;
		}

		private void waitServerStart() {
			try {
				server.waitedStart(5);
			} catch (Exception e) {
				logger.error("Exception waiting for servers to start: ", e);
			}
		}

  		@Override
		public boolean stop() {
			try {
				server.stop();
			} catch (Exception e) {
				logger.error("Exception during stop", e);
			}

			return true;
		}

		@Override
		public void shutdown() {
			this.stop();
		}

		@Override
		public synchronized boolean isAlive() {
			return this.integrityMonitorRestServerProperties != null;
		}
  	}

	public static DroolsPDPIntegrityMonitor getInstance() throws Exception{
		if(logger.isDebugEnabled()){
			logger.debug("getInstance() called");
		}
		if (im == null) {
			String msg = "No DroolsPDPIntegrityMonitor instance exists."
					+ " Please use the method DroolsPDPIntegrityMonitor init(String configDir)";
			throw new Exception(msg);
		}else{
			return im;
		}
	}
}
