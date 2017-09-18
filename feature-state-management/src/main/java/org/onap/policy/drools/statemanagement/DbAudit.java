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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Properties;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class audits the database
 */
public class DbAudit extends DroolsPDPIntegrityMonitor.AuditBase
{
	// get an instance of logger 
  private static Logger  logger = LoggerFactory.getLogger(DbAudit.class);	
  // single global instance of this audit object
  final static private DbAudit instance = new DbAudit();

  // This indicates if 'CREATE TABLE IF NOT EXISTS Audit ...' should be
  // invoked -- doing this avoids the need to create the table in advance.
  static private boolean createTableNeeded = true;

  synchronized private static void setCreateTableNeeded(boolean b) {
		DbAudit.createTableNeeded = b;
	}
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
	public void invoke(Properties properties)
  {
	if(logger.isDebugEnabled()){
		logger.debug("Running 'DbAudit.invoke'");
	}
	boolean isActive = true;
	String dbAuditIsActive = StateManagementProperties.getProperty("db.audit.is.active");
	if(logger.isDebugEnabled()){
		logger.debug("DbAudit.invoke: dbAuditIsActive = {}", dbAuditIsActive);
	}
	
	if (dbAuditIsActive != null) {
		try {
			isActive = Boolean.parseBoolean(dbAuditIsActive.trim());
		} catch (NumberFormatException e) {
			logger.warn("DbAudit.invoke: Ignoring invalid property: db.audit.is.active = {}", dbAuditIsActive);
		}
	}
	
	if(!isActive){
		
		logger.info("DbAudit.invoke: exiting because isActive = {}", isActive);
		return;
	}
	
	// fetch DB properties from properties file -- they are already known
	// to exist, because they were verified by the 'IntegrityMonitor'
	// constructor
	String url = properties.getProperty(StateManagementProperties.DB_URL);
	String user = properties.getProperty(StateManagementProperties.DB_USER);
	String password = properties.getProperty(StateManagementProperties.DB_PWD);

	// operation phase currently running -- used to construct an error
	// message, if needed
	String phase = null;

	// create connection to DB
	phase = "creating connection";
	if(logger.isDebugEnabled()){
		logger.debug("DbAudit: Creating connection to {}", url);
	}
	try (Connection connection = DriverManager.getConnection(url, user, password))
	  {

		// create audit table, if needed
		if (createTableNeeded)
		  {
			phase = "create table";
			if(logger.isDebugEnabled()){
				logger.info("DbAudit: Creating 'Audit' table, if needed");
			}
			try (PreparedStatement statement = connection.prepareStatement
			  ("CREATE TABLE IF NOT EXISTS Audit (\n"
			   + " name varchar(64) DEFAULT NULL,\n"
			   + " UNIQUE KEY name (name)\n"
			   + ") DEFAULT CHARSET=latin1;")) {
				statement.execute();
				DbAudit.setCreateTableNeeded(false);
			} catch (Exception e) {
				throw e;
			}
		  }

		// insert an entry into the table
		phase = "insert entry";
		String key = UUID.randomUUID().toString();
		try (PreparedStatement statement = connection.prepareStatement
		  ("INSERT INTO Audit (name) VALUES (?)")) {
			statement.setString(1, key);
			statement.executeUpdate();
		} catch (Exception e) {
			throw e;
		}
		
		// fetch the entry from the table
		phase = "fetch entry";
		try (PreparedStatement statement = connection.prepareStatement
		  ("SELECT name FROM Audit WHERE name = ?")) {
			statement.setString(1, key);
			try (ResultSet rs = statement.executeQuery()) {
				if (rs.first())
				  {
					// found entry
					if(logger.isDebugEnabled()){
						logger.debug("DbAudit: Found key {}", rs.getString(1));
					}
				  }
				else
				  {
					logger.error
					  ("DbAudit: can't find newly-created entry with key {}", key);
					setResponse("Can't find newly-created entry");
				  }
			} catch (Exception e) {
				throw e;
			}
		}
		// delete entries from table
		phase = "delete entry";
		try (PreparedStatement statement = connection.prepareStatement
		  ("DELETE FROM Audit WHERE name = ?")) {
			statement.setString(1, key);
			statement.executeUpdate();
		} catch (Exception e) {
			throw e;
		}
	}
	catch (Exception e)
	  {
		String message = "DbAudit: Exception during audit, phase = " + phase;
		logger.error(message, e);
		setResponse(message);
	  }
  }

}
