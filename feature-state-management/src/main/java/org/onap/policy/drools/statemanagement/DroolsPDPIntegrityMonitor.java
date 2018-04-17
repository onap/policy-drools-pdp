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

import java.io.IOException;
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
   * @throws IntegrityMonitorException (passed from superclass)
   */
	private DroolsPDPIntegrityMonitor(String resourceName,
			Properties consolidatedProperties
			) throws IntegrityMonitorException {
	super(resourceName, consolidatedProperties);
  }
  
  private static void missingProperty(String prop) throws IntegrityMonitorException{
		String msg = "init: missing IntegrityMonitor property: ".concat(prop);
		logger.error(msg);
		throw new IntegrityMonitorException(msg);
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
   * @throws StateManagementPropertiesException
   * @throws IntegrityMonitorException 
   */
  public static DroolsPDPIntegrityMonitor init(String configDir) throws IntegrityMonitorException
  {
	  	  
	logger.info("init: Entering and invoking PropertyUtil.getProperties() on '{}'", configDir);
		
	// read in properties
	Properties stateManagementProperties = getProperties(configDir);
	
	// fetch and verify definitions of some properties, adding defaults where
	// appropriate
	// (the 'IntegrityMonitor' constructor does some additional verification)
	
	checkPropError(stateManagementProperties, StateManagementProperties.TEST_HOST);
	checkPropError(stateManagementProperties, StateManagementProperties.TEST_PORT);

	addDefaultPropError(stateManagementProperties,
			StateManagementProperties.TEST_SERVICES,
			StateManagementProperties.TEST_SERVICES_DEFAULT);
	
	addDefaultPropError(stateManagementProperties,
			StateManagementProperties.TEST_REST_CLASSES,
			StateManagementProperties.TEST_REST_CLASSES_DEFAULT);

	addDefaultPropWarn(stateManagementProperties,
			StateManagementProperties.TEST_MANAGED,
			StateManagementProperties.TEST_MANAGED_DEFAULT);
	
	addDefaultPropWarn(stateManagementProperties,
			StateManagementProperties.TEST_SWAGGER,
			StateManagementProperties.TEST_SWAGGER_DEFAULT);
	
	checkPropError(stateManagementProperties, StateManagementProperties.RESOURCE_NAME);
	checkPropError(stateManagementProperties, StateManagementProperties.FP_MONITOR_INTERVAL);
	checkPropError(stateManagementProperties, StateManagementProperties.FAILED_COUNTER_THRESHOLD);
	checkPropError(stateManagementProperties, StateManagementProperties.TEST_TRANS_INTERVAL);
	checkPropError(stateManagementProperties, StateManagementProperties.WRITE_FPC_INTERVAL);
	checkPropError(stateManagementProperties, StateManagementProperties.SITE_NAME);
	checkPropError(stateManagementProperties, StateManagementProperties.NODE_TYPE);
	checkPropError(stateManagementProperties, StateManagementProperties.DEPENDENCY_GROUPS);
	checkPropError(stateManagementProperties, StateManagementProperties.DB_DRIVER);
	checkPropError(stateManagementProperties, StateManagementProperties.DB_URL);
	checkPropError(stateManagementProperties, StateManagementProperties.DB_USER);
	checkPropError(stateManagementProperties, StateManagementProperties.DB_PWD);
	
	String testHost = stateManagementProperties.getProperty(StateManagementProperties.TEST_HOST);
	String testPort = stateManagementProperties.getProperty(StateManagementProperties.TEST_PORT);
	String resourceName = stateManagementProperties.getProperty(StateManagementProperties.RESOURCE_NAME);

	subsystemTestProperties = stateManagementProperties;

	// Now that we've validated the properties, create Drools Integrity Monitor
	// with these properties.
	im = makeMonitor(resourceName, stateManagementProperties);
	logger.info("init: New DroolsPDPIntegrityMonitor instantiated, resourceName = ", resourceName);

	// create http server
	makeRestServer(testHost, testPort, stateManagementProperties);
	logger.info("init: Exiting and returning DroolsPDPIntegrityMonitor");
	
	return im;
  }

  /**
   * Makes an Integrity Monitor.
   * @param resourceName unique name of this Integrity Monitor
   * @param properties properties used to configure the Integrity Monitor
   * @return
   * @throws IntegrityMonitorException
   */
  private static DroolsPDPIntegrityMonitor makeMonitor(String resourceName, Properties properties)
		throws IntegrityMonitorException {
	  
	try {
		return new DroolsPDPIntegrityMonitor(resourceName, properties);
		
	} catch (Exception e) {
		throw new IntegrityMonitorException(e);
	}
  }

  /**
   * Makes a rest server for the Integrity Monitor.
   * @param testHost		host name
   * @param testPort		port
   * @param properties		properties used to configure the rest server
   * @throws IntegrityMonitorException
   */
  private static void makeRestServer(String testHost, String testPort, Properties properties)
		throws IntegrityMonitorException {
	  
	try {
		logger.info("init: Starting HTTP server, addr= {}", testHost+":"+testPort);
		
		IntegrityMonitorRestServer server = new IntegrityMonitorRestServer();
		server.init(properties);
		
	} catch (Exception e) {
		logger.error("init: Caught Exception attempting to start server on testPort= {} message:",
								testPort, e);
		throw new IntegrityMonitorException(e);
	}
  }

  /**
   * Gets the properties from the property file.
   * @param configDir	directory containing the property file
   * @return the properties
   * @throws IntegrityMonitorException 
   */
  private static Properties getProperties(String configDir) throws IntegrityMonitorException {
	try {
		return PropertyUtil.getProperties(configDir + "/" + PROPERTIES_NAME);
		
	} catch (IOException e) {
		throw new IntegrityMonitorException(e);
	}
  }
  
  /**
   * Checks that a property is defined.
   * @param props	set of properties
   * @param name	name of the property to check
   * @throws IntegrityMonitorException
   */
  private static void checkPropError(Properties props, String name) throws IntegrityMonitorException {
	  String val = props.getProperty(name);
	  if(val == null) {
		  missingProperty(name);
	  }
	  
	  logPropertyValue(name, val);
  }
  
  /**
   * Checks a property's value to verify that it matches the expected value.
   * If the property is not defined, then it is added to the property set,
   * with the expected value.  Logs an error if the property is defined,
   * but does not have the expected value.
   * @param props		set of properties
   * @param name		name of the property to check
   * @param expected	expected/default value
   */
  private static void addDefaultPropError(Properties props, String name, String expected) {
	  String val = props.getProperty(name);
	  if(val == null) {
		  props.setProperty(name, expected);
		  
	  } else if( ! val.equals(expected)) {
		  logger.error(INVALID_PROPERTY_VALUE, name, expected);
	  }
	  
	  logPropertyValue(name, val);
  }
  
  /**
   * Checks a property's value to verify that it matches the expected value.
   * If the property is not defined, then it is added to the property set,
   * with the expected value.  Logs a warning if the property is defined,
   * but does not have the expected value.
   * @param props		set of properties
   * @param name		name of the property to check
   * @param expected	expected/default value
   */
  private static void addDefaultPropWarn(Properties props, String name, String dflt) {
	  String val = props.getProperty(name);
	  if(val == null) {
		  props.setProperty(name, dflt);
		  
	  } else if( ! val.equals(dflt)) {
		  logger.warn(INVALID_PROPERTY_VALUE, name, dflt);
	  }
	  
	  logPropertyValue(name, val);
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

	public static DroolsPDPIntegrityMonitor getInstance() throws IntegrityMonitorException{
		if(logger.isDebugEnabled()){
			logger.debug("getInstance() called");
		}
		if (im == null) {
			String msg = "No DroolsPDPIntegrityMonitor instance exists."
					+ " Please use the method DroolsPDPIntegrityMonitor init(String configDir)";
			throw new IntegrityMonitorException(msg);
		}else{
			return im;
		}
	}
}
