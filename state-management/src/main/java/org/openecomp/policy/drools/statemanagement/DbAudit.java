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

package org.openecomp.policy.drools.statemanagement;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Properties;
import java.util.UUID;

import org.openecomp.policy.common.logging.flexlogger.FlexLogger;
import org.openecomp.policy.common.logging.flexlogger.Logger;
import org.openecomp.policy.common.logging.eelf.MessageCodes;

/**
 * This class audits the database
 */
public class DbAudit extends DroolsPDPIntegrityMonitor.AuditBase
{
	// get an instance of logger 
  private static Logger  logger = FlexLogger.getLogger(DbAudit.class);	
  // single global instance of this audit object
  static private DbAudit instance = new DbAudit();

  // This indicates if 'CREATE TABLE IF NOT EXISTS Audit ...' should be
  // invoked -- doing this avoids the need to create the table in advance.
  static private boolean createTableNeeded = true;

  /**
   * @return the single 'DbAudit' instance
   */
  static DroolsPDPIntegrityMonitor.AuditBase getInstance()
  {
	return(instance);
  }

  /**
   * Constructor - set the name to 'Database'
   */
  private DbAudit()
  {
	super("Database");
  }

  /**
   * Invoke the audit
   *
   * @param properties properties to be passed to the audit
   */
  @Override
	public void invoke(Properties droolsPersistenceProperties)
  {
	logger.info("Running 'DbAudit.invoke'");
	boolean isActive = true;
	String dbAuditIsActive = StateManagementProperties.getProperty("db.audit.is.active");
	logger.debug("DbAudit.invoke: dbAuditIsActive = " + dbAuditIsActive);
	
	if (dbAuditIsActive != null) {
		try {
			isActive = Boolean.parseBoolean(dbAuditIsActive.trim());
		} catch (NumberFormatException e) {
			logger.warn("DbAudit.invoke: Ignoring invalid property: db.audit.is.active = " + dbAuditIsActive);
		}
	}
	
	if(!isActive){
		logger.info("DbAudit.invoke: exiting because isActive = " + isActive);
		return;
	}
	
	// fetch DB properties from properties file -- they are already known
	// to exist, because they were verified by the 'IntegrityMonitor'
	// constructor
	String url = droolsPersistenceProperties.getProperty(DroolsPersistenceProperties.DB_URL);
	String user = droolsPersistenceProperties.getProperty(DroolsPersistenceProperties.DB_USER);
	String password =
			droolsPersistenceProperties.getProperty(DroolsPersistenceProperties.DB_PWD);

	// connection to DB
	Connection connection = null;

	// supports SQL operations
	PreparedStatement statement = null;
	ResultSet rs = null;

	// operation phase currently running -- used to construct an error
	// message, if needed
	String phase = null;

	try
	  {
		// create connection to DB
		phase = "creating connection";
		logger.info("DbAudit: Creating connection to " + url);

		connection = DriverManager.getConnection(url, user, password);

		// create audit table, if needed
		if (createTableNeeded)
		  {
			phase = "create table";
			logger.info("DbAudit: Creating 'Audit' table, if needed");
			statement = connection.prepareStatement
			  ("CREATE TABLE IF NOT EXISTS Audit (\n"
			   + " name varchar(64) DEFAULT NULL,\n"
			   + " UNIQUE KEY name (name)\n"
			   + ") DEFAULT CHARSET=latin1;");
			statement.execute();
			statement.close();
			createTableNeeded = false;
		  }

		// insert an entry into the table
		phase = "insert entry";
		String key = UUID.randomUUID().toString();
		statement = connection.prepareStatement
		  ("INSERT INTO Audit (name) VALUES (?)");
		statement.setString(1, key);
		statement.executeUpdate();
		statement.close();

		// fetch the entry from the table
		phase = "fetch entry";
		statement = connection.prepareStatement
		  ("SELECT name FROM Audit WHERE name = ?");
		statement.setString(1, key);
		rs = statement.executeQuery();
		if (rs.first())
		  {
			// found entry
			logger.info("DbAudit: Found key " + rs.getString(1));
		  }
		else
		  {
			logger.error
			  ("DbAudit: can't find newly-created entry with key " + key);
			setResponse("Can't find newly-created entry");
		  }
		statement.close();

		// delete entries from table
		phase = "delete entry";
		statement = connection.prepareStatement
		  ("DELETE FROM Audit WHERE name = ?");
		statement.setString(1, key);
		statement.executeUpdate();
		statement.close();
		statement = null;
	  }
	catch (Exception e)
	  {
		String message = "DbAudit: Exception during audit, phase = " + phase;
		logger.error(MessageCodes.EXCEPTION_ERROR, e, message);
		setResponse(message);
	  }
	finally
	  {
		if (rs != null)
		  {
			try
			  {
				rs.close();
			  }
			catch (Exception e)
			  {
			  }
		  }
		if (statement != null)
		  {
			try
			  {
				statement.close();
			  }
			catch (Exception e)
			  {
			  }
		  }
		if (connection != null)
		  {
			try
			  {
				connection.close();
			  }
			catch (Exception e)
			  {
			  }
		  }
	  }
  }
}
