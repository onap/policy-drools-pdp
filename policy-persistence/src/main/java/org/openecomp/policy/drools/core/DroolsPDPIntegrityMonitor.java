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

package org.openecomp.policy.drools.core;

import java.io.File;
import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Properties;

import org.openecomp.policy.common.im.IntegrityMonitor;
import org.openecomp.policy.common.logging.eelf.MessageCodes;
import org.openecomp.policy.common.logging.flexlogger.FlexLogger;
import org.openecomp.policy.common.logging.flexlogger.Logger;
import org.openecomp.policy.common.logging.flexlogger.PropertyUtil;
import org.openecomp.policy.drools.http.server.HttpServletServer;
import org.openecomp.policy.drools.persistence.XacmlPersistenceProperties;
import org.openecomp.policy.drools.properties.Startable;

/**
 * This class extends 'IntegrityMonitor' for use in the 'Drools PDP'
 * virtual machine. The included audits are 'Database' and 'Repository'.
 */
public class DroolsPDPIntegrityMonitor extends IntegrityMonitor
{
	
	// get an instance of logger 
  private static Logger  logger = FlexLogger.getLogger(DroolsPDPIntegrityMonitor.class);	

  // static global instance
  static private DroolsPDPIntegrityMonitor im = null;

  // list of audits to run
  static private AuditBase[] audits =
	new AuditBase[]{DbAudit.getInstance(), RepositoryAudit.getInstance()};

  // save initialization properties
  private Properties droolsPersistenceProperties = null;
  
  /**
   * Static initialization -- create Drools Integrity Monitor, and
   * an HTTP server to handle REST 'test' requests
   */
  static public DroolsPDPIntegrityMonitor init(String configDir) throws Exception
  {
	  	  
	logger.info("init: Entering and invoking PropertyUtil.getProperties() on '"
				+ configDir + "'");
		
	// read in properties
	Properties integrityMonitorProperties =
	  PropertyUtil.getProperties(configDir + "/IntegrityMonitor.properties");
	Properties droolsPersistenceProperties =
	  PropertyUtil.getProperties(configDir + "/droolsPersistence.properties");
	Properties xacmlPersistenceProperties =
	  PropertyUtil.getProperties(configDir + "/xacmlPersistence.properties");

	// fetch and verify definitions of some properties
	// (the 'IntegrityMonitor' constructor does some additional verification)
	String resourceName = integrityMonitorProperties.getProperty("resource.name");
	String hostPort = integrityMonitorProperties.getProperty("hostPort");
	String fpMonitorInterval = integrityMonitorProperties.getProperty("fp_monitor_interval");
	String failedCounterThreshold = integrityMonitorProperties.getProperty("failed_counter_threshold");
	String testTransInterval = integrityMonitorProperties.getProperty("test_trans_interval");
	String writeFpcInterval = integrityMonitorProperties.getProperty("write_fpc_interval");
	String siteName = integrityMonitorProperties.getProperty("site_name");
	String nodeType = integrityMonitorProperties.getProperty("node_type");
	String dependencyGroups = integrityMonitorProperties.getProperty("dependency_groups");
	String droolsJavaxPersistenceJdbcDriver = droolsPersistenceProperties.getProperty("javax.persistence.jdbc.driver");
	String droolsJavaxPersistenceJdbcUrl = droolsPersistenceProperties.getProperty("javax.persistence.jdbc.url");
	String droolsJavaxPersistenceJdbcUser = droolsPersistenceProperties.getProperty("javax.persistence.jdbc.user");
	String droolsJavaxPersistenceJdbcPassword = droolsPersistenceProperties.getProperty("javax.persistence.jdbc.password");	
	String xacmlJavaxPersistenceJdbcDriver = xacmlPersistenceProperties.getProperty("javax.persistence.jdbc.driver");
	String xacmlJavaxPersistenceJdbcUrl = xacmlPersistenceProperties.getProperty("javax.persistence.jdbc.url");
	String xacmlJavaxPersistenceJdbcUser = xacmlPersistenceProperties.getProperty("javax.persistence.jdbc.user");
	String xacmlJavaxPersistenceJdbcPassword = xacmlPersistenceProperties.getProperty("javax.persistence.jdbc.password");
	
	if (resourceName == null)
	  {
		logger.error("init: Missing IntegrityMonitor property: 'resource.name'");
		throw(new Exception
			  ("Missing IntegrityMonitor property: 'resource.name'"));
	  }
	if (hostPort == null)
	  {
		logger.error("init: Missing IntegrityMonitor property: 'hostPort'");
		throw(new Exception
			  ("Missing IntegrityMonitor property: 'hostPort'"));
	  }
	if (fpMonitorInterval == null)
	  {
		logger.error("init: Missing IntegrityMonitor property: 'fp_monitor_interval'");
		throw(new Exception
			  ("Missing IntegrityMonitor property: 'fp_monitor_interval'"));
	  }	
	if (failedCounterThreshold == null)
	  {
		logger.error("init: Missing IntegrityMonitor property: 'failed_counter_threshold'");
		throw(new Exception
			  ("Missing IntegrityMonitor property: 'failed_counter_threshold'"));
	  }	
	if (testTransInterval == null)
	  {
		logger.error("init: Missing IntegrityMonitor property: 'test_trans_interval'");
		throw(new Exception
			  ("Missing IntegrityMonitor property: 'test_trans_interval'"));
	  }	
	if (writeFpcInterval == null)
	  {
		logger.error("init: Missing IntegrityMonitor property: 'write_fpc_interval'");
		throw(new Exception
			  ("Missing IntegrityMonitor property: 'write_fpc_interval'"));
	  }	
	if (siteName == null)
	  {
		logger.error("init: Missing IntegrityMonitor property: 'site_name'");
		throw(new Exception
			  ("Missing IntegrityMonitor property: 'site_name'"));
	  }	
	if (nodeType == null)
	  {
		logger.error("init: Missing IntegrityMonitor property: 'node_type'");
		throw(new Exception
			  ("Missing IntegrityMonitor property: 'node_type'"));
	  }	
	if (dependencyGroups == null)
	  {
		logger.error("init: Missing IntegrityMonitor property: 'dependency_groups'");
		throw(new Exception
			  ("Missing IntegrityMonitor property: 'dependency_groups'"));
	  }	
	if (droolsJavaxPersistenceJdbcDriver == null)
	  {
		logger.error("init: Missing IntegrityMonitor property: 'javax.persistence.jbdc.driver for drools DB'");
		throw(new Exception
			  ("Missing IntegrityMonitor property: 'javax.persistence.jbdc.driver for drools DB'"));
	  }		
	if (droolsJavaxPersistenceJdbcUrl == null)
	  {
		logger.error("init: Missing IntegrityMonitor property: 'javax.persistence.jbdc.url  for drools DB'");
		throw(new Exception
			  ("Missing IntegrityMonitor property: 'javax.persistence.jbdc.url for drools DB'"));
	  }			
	if (droolsJavaxPersistenceJdbcUser == null)
	  {
		logger.error("init: Missing IntegrityMonitor property: 'javax.persistence.jbdc.user for drools DB'");
		throw(new Exception
			  ("Missing IntegrityMonitor property: 'javax.persistence.jbdc.user for drools DB'"));
	  }			
	if (droolsJavaxPersistenceJdbcPassword == null)
	  {
		logger.error("init: Missing IntegrityMonitor property: 'javax.persistence.jbdc.password for drools DB'");
		throw(new Exception
			  ("Missing IntegrityMonitor property: 'javax.persistence.jbdc.password for drools DB'"));
	  }	
	if (xacmlJavaxPersistenceJdbcDriver == null)
	  {
		logger.error("init: Missing IntegrityMonitor property: 'javax.persistence.jbdc.driver for xacml DB'");
		throw(new Exception
			  ("Missing IntegrityMonitor property: 'javax.persistence.jbdc.driver for xacml DB'"));
	  }		
	if (xacmlJavaxPersistenceJdbcUrl == null)
	  {
		logger.error("init: Missing IntegrityMonitor property: 'javax.persistence.jbdc.url  for xacml DB'");
		throw(new Exception
			  ("Missing IntegrityMonitor property: 'javax.persistence.jbdc.url  for xacml DB'"));
	  }			
	if (xacmlJavaxPersistenceJdbcUser == null)
	  {
		logger.error("init: Missing IntegrityMonitor property: 'javax.persistence.jbdc.user for xacml DB'");
		throw(new Exception
			  ("Missing IntegrityMonitor property: 'javax.persistence.jbdc.user for xacml DB'"));
	  }			
	if (xacmlJavaxPersistenceJdbcPassword == null)
	  {
		logger.error("init: Missing IntegrityMonitor property: 'javax.persistence.jbdc.password for xacml DB'");
		throw(new Exception
			  ("Missing IntegrityMonitor property: 'javax.persistence.jbdc.password'  for xacml DB'"));
	  }		

	logger.info("init: loading consolidatedProperties");
	Properties consolidatedProperties = new Properties();
	consolidatedProperties.load(new FileInputStream(new File(configDir + "/IntegrityMonitor.properties")));
	consolidatedProperties.load(new FileInputStream(new File(configDir + "/xacmlPersistence.properties")));
	// verify that consolidatedProperties has properties from both properties files.
	logger.info("init: PDP_INSTANCE_ID=" + consolidatedProperties.getProperty(IntegrityMonitorProperties.PDP_INSTANCE_ID));
	logger.info("init: DB_URL=" + consolidatedProperties.getProperty(XacmlPersistenceProperties.DB_URL));

	// Now that we've validated the properties, create Drools Integrity Monitor
	// with these properties.
	im = new DroolsPDPIntegrityMonitor(resourceName,
				consolidatedProperties, droolsPersistenceProperties);
	logger.info("init: New DroolsPDPIntegrityMonitor instantiated, hostPort=" + hostPort);

	// determine host and port for HTTP server
	int index = hostPort.lastIndexOf(':');
	InetSocketAddress addr;

	if (index < 0)
	  {
		addr = new InetSocketAddress(Integer.valueOf(hostPort));
	  }
	else
	  {
		addr = new InetSocketAddress
		  (hostPort.substring(0, index),
		   Integer.valueOf(hostPort.substring(index + 1)));
	  }

	// create http server
	try {
		logger.info("init: Starting HTTP server, addr=" + addr);
		IntegrityMonitorRestServer server = new IntegrityMonitorRestServer();
		
		server.init(integrityMonitorProperties);

		System.out.println("init: Started server on hostPort=" + hostPort);
	} catch (Exception e) {
			if (PolicyContainer.isUnitTesting) {
				System.out
						.println("init: Caught Exception attempting to start server on hostPort="
								+ hostPort + ", message=" + e.getMessage());
		} else {
			throw e;
		}
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
			Properties consolidatedProperties,
			Properties droolsPersistenceProperties) throws Exception {
	super(resourceName, consolidatedProperties);
	this.droolsPersistenceProperties = droolsPersistenceProperties;
  }

  /**
   * Run tests (audits) unique to Drools PDP VM (Database + Repository)
   */
  @Override
	public void subsystemTest() throws Exception
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
			audit.invoke(droolsPersistenceProperties);
		  }
		catch (Exception e)
		  {
			logger.error(MessageCodes.EXCEPTION_ERROR, e,
							   audit.getName() + " audit error");
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
		  throw new Exception(responseMsg);
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
	  return(name);
	}

	/**
	 * @return the response String (non-null indicates the error message)
	 */
	public String getResponse()
	{
	  return(response);
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
	 * @param droolsPersistenceProperties Used for DB access
	 * @throws Exception passed in by the audit
	 */
	abstract void invoke(Properties droolsPersistenceProperties) throws Exception;
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
						server.start();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			} catch (Exception e) {
				return false;
			}
			
			return true;
		}

  		@Override
		public boolean stop() throws IllegalStateException {
			try {
				server.stop();
			} catch (Exception e) {
				e.printStackTrace();
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
		logger.info("getInstance() called");
		if (im == null) {
			String msg = "No DroolsPDPIntegrityMonitor instance exists."
					+ " Please use the method DroolsPDPIntegrityMonitor init(String configDir)";
			throw new Exception(msg);
		}else{
			return im;
		}
	}
}
