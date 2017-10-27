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

import java.net.InetSocketAddress;
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
	
	// get an instance of logger 
  private static final Logger  logger = LoggerFactory.getLogger(DroolsPDPIntegrityMonitor.class);	

  // static global instance
  static private DroolsPDPIntegrityMonitor im = null;

  // list of audits to run
  static private AuditBase[] audits =
	new AuditBase[]{DbAudit.getInstance(), RepositoryAudit.getInstance()};
  
  static private Properties subsystemTestProperties = null;

  static private final String PROPERTIES_NAME = "feature-state-management.properties";
  /**
   * Static initialization -- create Drools Integrity Monitor, and
   * an HTTP server to handle REST 'test' requests
   */
  static public DroolsPDPIntegrityMonitor init(String configDir) throws Exception
  {
	  	  
	logger.info("init: Entering and invoking PropertyUtil.getProperties() on '{}'", configDir);
		
	// read in properties
	Properties stateManagementProperties =
	  PropertyUtil.getProperties(configDir + "/" + PROPERTIES_NAME);
	// fetch and verify definitions of some properties
	// (the 'IntegrityMonitor' constructor does some additional verification)
	String testServices = stateManagementProperties.getProperty("http.server.services");
	String testHost = stateManagementProperties.getProperty("http.server.services.TEST.host");
	String testPort = stateManagementProperties.getProperty("http.server.services.TEST.port");
	String testRestClasses = stateManagementProperties.getProperty("http.server.services.TEST.restClasses");
	String testManaged = stateManagementProperties.getProperty("http.server.services.TEST.managed");
	String testSwagger = stateManagementProperties.getProperty("http.server.services.TEST.swagger");
	String resourceName = stateManagementProperties.getProperty("resource.name");
	String fpMonitorInterval = stateManagementProperties.getProperty("fp_monitor_interval");
	String failedCounterThreshold = stateManagementProperties.getProperty("failed_counter_threshold");
	String testTransInterval = stateManagementProperties.getProperty("test_trans_interval");
	String writeFpcInterval = stateManagementProperties.getProperty("write_fpc_interval");
	String siteName = stateManagementProperties.getProperty("site_name");
	String nodeType = stateManagementProperties.getProperty("node_type");
	String dependencyGroups = stateManagementProperties.getProperty("dependency_groups");
	String javaxPersistenceJdbcDriver = stateManagementProperties.getProperty("javax.persistence.jdbc.driver");
	String javaxPersistenceJdbcUrl = stateManagementProperties.getProperty("javax.persistence.jdbc.url");
	String javaxPersistenceJdbcUser = stateManagementProperties.getProperty("javax.persistence.jdbc.user");
	String javaxPersistenceJdbcPassword = stateManagementProperties.getProperty("javax.persistence.jdbc.password");
	
	if (testServices == null) {
		logger.info("init: Setting http.server.services = TEST");
		stateManagementProperties.put("http.server.services", "TEST");
	}
	if (testHost == null){
		logger.error("init: missing IntegrityMonitor property: http.server.services.TEST.host");
		throw new Exception("init: missing IntegrityMonitor property: http.server.services.TEST.host");
	}
	if (testPort == null){
		logger.error("init: missing IntegrityMonitor property: http.server.services.TEST.port");
		throw new Exception("init: missing IntegrityMonitor property: http.server.services.TEST.port");
	}
	if (testRestClasses == null){
		logger.info("init: Setting http.server.services.TEST.restClasses = "
				+ "org.onap.policy.drools.statemanagement.IntegrityMonitorRestManager");
		stateManagementProperties.put("http.server.services.TEST.restClasses", 
				"org.onap.policy.drools.statemanagement.IntegrityMonitorRestManager");
	}
	if (testManaged == null){
		logger.info("init: Setting http.server.services.TEST.managed = false");
		stateManagementProperties.put("http.server.services.TEST.managed", "false"); 
	}
	if (testSwagger == null){
		logger.info("init: Setting http.server.services.TEST.swagger = true");
		stateManagementProperties.put("http.server.services.TEST.swagger", "true"); 
	}
	if (resourceName == null)
	  {
		logger.error("init: Missing IntegrityMonitor property: 'resource.name'");
		throw new Exception
			  ("Missing IntegrityMonitor property: 'resource.name'");
	  }
	if (fpMonitorInterval == null)
	  {
		logger.error("init: Missing IntegrityMonitor property: 'fp_monitor_interval'");
		throw new Exception
			  ("Missing IntegrityMonitor property: 'fp_monitor_interval'");
	  }	
	if (failedCounterThreshold == null)
	  {
		logger.error("init: Missing IntegrityMonitor property: 'failed_counter_threshold'");
		throw new Exception
			  ("Missing IntegrityMonitor property: 'failed_counter_threshold'");
	  }	
	if (testTransInterval == null)
	  {
		logger.error("init: Missing IntegrityMonitor property: 'test_trans_interval'");
		throw new Exception
			  ("Missing IntegrityMonitor property: 'test_trans_interval'");
	  }	
	if (writeFpcInterval == null)
	  {
		logger.error("init: Missing IntegrityMonitor property: 'write_fpc_interval'");
		throw new Exception
			  ("Missing IntegrityMonitor property: 'write_fpc_interval'");
	  }	
	if (siteName == null)
	  {
		logger.error("init: Missing IntegrityMonitor property: 'site_name'");
		throw new Exception
			  ("Missing IntegrityMonitor property: 'site_name'");
	  }	
	if (nodeType == null)
	  {
		logger.error("init: Missing IntegrityMonitor property: 'node_type'");
		throw new Exception
			  ("Missing IntegrityMonitor property: 'node_type'");
	  }	
	if (dependencyGroups == null)
	  {
		logger.error("init: Missing IntegrityMonitor property: 'dependency_groups'");
		throw new Exception
			  ("Missing IntegrityMonitor property: 'dependency_groups'");
	  }	
	if (javaxPersistenceJdbcDriver == null)
	  {
		logger.error("init: Missing IntegrityMonitor property: 'javax.persistence.jbdc.driver for xacml DB'");
		throw new Exception
			  ("Missing IntegrityMonitor property: 'javax.persistence.jbdc.driver for xacml DB'");
	  }		
	if (javaxPersistenceJdbcUrl == null)
	  {
		logger.error("init: Missing IntegrityMonitor property: 'javax.persistence.jbdc.url  for xacml DB'");
		throw(new Exception
			  ("Missing IntegrityMonitor property: 'javax.persistence.jbdc.url  for xacml DB'"));
	  }			
	if (javaxPersistenceJdbcUser == null)
	  {
		logger.error("init: Missing IntegrityMonitor property: 'javax.persistence.jbdc.user for xacml DB'");
		throw new Exception
			  ("Missing IntegrityMonitor property: 'javax.persistence.jbdc.user for xacml DB'");
	  }			
	if (javaxPersistenceJdbcPassword == null)
	  {
		logger.error("init: Missing IntegrityMonitor property: 'javax.persistence.jbdc.password for xacml DB'");
		throw new Exception
			  ("Missing IntegrityMonitor property: 'javax.persistence.jbdc.password'  for xacml DB'");
	  }
	
	subsystemTestProperties = stateManagementProperties;

	// Now that we've validated the properties, create Drools Integrity Monitor
	// with these properties.
	im = new DroolsPDPIntegrityMonitor(resourceName,
				stateManagementProperties);
	logger.info("init: New DroolsPDPIntegrityMonitor instantiated, resourcName = ", resourceName);

	// create http server
	try {
		logger.info("init: Starting HTTP server, addr= {}", testHost+":"+testPort);
		IntegrityMonitorRestServer server = new IntegrityMonitorRestServer();
		
		server.init(stateManagementProperties);
	} catch (Exception e) {
		logger.error("init: Caught Exception attempting to start server on testPort= {}, message = {}",
								testPort, e.getMessage());
		throw e;
	}
	
	logger.info("init: Exiting and returning DroolsPDPIntegrityMonitor");
	return im;
  }

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
  static public abstract class AuditBase
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
		public boolean start() throws IllegalStateException {
			try {
				ArrayList<HttpServletServer> servers = HttpServletServer.factory.build(integrityMonitorRestServerProperties);
				
				if (!servers.isEmpty()) {
					server = servers.get(0);
					
					try {
						server.waitedStart(5);
					} catch (Exception e) {
						logger.error("Exception waiting for servers to start: ", e);
					}
				}
			} catch (Exception e) {
				logger.error("Exception building servers", e);
				return false;
			}
			
			return true;
		}

  		@Override
		public boolean stop() throws IllegalStateException {
			try {
				server.stop();
			} catch (Exception e) {
				logger.error("Exception during stop", e);
			}
			
			return true;
		}

		@Override
		public void shutdown() throws IllegalStateException {
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
