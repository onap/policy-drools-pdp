/*-
 * ============LICENSE_START=======================================================
 * feature-state-management
 * ================================================================================
 * Copyright (C) 2017-2019 AT&T Intellectual Property. All rights reserved.
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
import java.sql.SQLException;
import java.util.Properties;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This class audits the database.
 */
public class DbAudit extends DroolsPdpIntegrityMonitor.AuditBase {
    // get an instance of logger
    private static Logger logger = LoggerFactory.getLogger(DbAudit.class);
    // single global instance of this audit object
    private static final DbAudit instance = new DbAudit();

    // This indicates if 'CREATE TABLE IF NOT EXISTS Audit ...' should be
    // invoked -- doing this avoids the need to create the table in advance.
    private static boolean createTableNeeded = true;

    private static boolean isJunit = false;

    /** Constructor - set the name to 'Database'. */
    private DbAudit() {
        super("Database");
    }

    private static synchronized void setCreateTableNeeded(boolean isNeeded) {
        DbAudit.createTableNeeded = isNeeded;
    }

    public static synchronized void setIsJunit(boolean isJUnit) {
        DbAudit.isJunit = isJUnit;
    }

    public static boolean isJunit() {
        return DbAudit.isJunit;
    }

    /**
     * Get the instance.
     *
     * @return the single 'DbAudit' instance. */
    public static DroolsPdpIntegrityMonitor.AuditBase getInstance() {
        return instance;
    }

    /**
     * Invoke the audit.
     *
     * @param properties properties to be passed to the audit
     */
    @Override
    public void invoke(Properties properties) {
        logger.debug("Running 'DbAudit.invoke'");
        boolean doCreate = createTableNeeded && !isJunit;

        if (!isActive()) {
            logger.info("DbAudit.invoke: exiting because isActive = false");
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
        logger.debug("DbAudit: Creating connection to {}", url);
        try (Connection connection = DriverManager.getConnection(url, user, password)) {

            // create audit table, if needed
            if (doCreate) {
                phase = "create table";
                createTable(connection);
            }

            // insert an entry into the table
            phase = "insert entry";
            String key = UUID.randomUUID().toString();
            insertEntry(connection, key);

            phase = "fetch entry";
            findEntry(connection, key);

            phase = "delete entry";
            deleteEntry(connection, key);
        } catch (Exception e) {
            String message = "DbAudit: Exception during audit, phase = " + phase;
            logger.error(message, e);
            setResponse(message);
        }
    }

    /**
     * Determines if the DbAudit is active, based on properties. Defaults to {@code true}, if not
     * found in the properties.
     *
     * @return {@code true} if DbAudit is active, {@code false} otherwise
     */
    private boolean isActive() {
        String dbAuditIsActive = StateManagementProperties.getProperty("db.audit.is.active");
        logger.debug("DbAudit.invoke: dbAuditIsActive = {}", dbAuditIsActive);

        if (dbAuditIsActive != null) {
            try {
                return Boolean.parseBoolean(dbAuditIsActive.trim());
            } catch (NumberFormatException e) {
                logger.warn(
                        "DbAudit.invoke: Ignoring invalid property: db.audit.is.active = {}", dbAuditIsActive);
            }
        }

        return true;
    }

    /**
     * Creates the table.
     *
     * @param connection connection
     * @throws SQLException exception
     */
    private void createTable(Connection connection) throws SQLException {
        logger.info("DbAudit: Creating 'Audit' table, if needed");
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "CREATE TABLE IF NOT EXISTS Audit (\n"
                                + " name varchar(64) DEFAULT NULL,\n"
                                + " UNIQUE KEY name (name)\n"
                                + ") DEFAULT CHARSET=latin1;")) {
            statement.execute();
            DbAudit.setCreateTableNeeded(false);
        }
    }

    /**
     * Inserts an entry.
     *
     * @param connection connection
     * @param key key
     * @throws SQLException exception
     */
    private void insertEntry(Connection connection, String key) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement("INSERT INTO Audit (name) VALUES (?)")) {
            statement.setString(1, key);
            statement.executeUpdate();
        }
    }

    /**
     * Finds an entry.
     *
     * @param connection connection
     * @param key key
     * @throws SQLException exception
     */
    private void findEntry(Connection connection, String key) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement("SELECT name FROM Audit WHERE name = ?")) {
            statement.setString(1, key);
            getEntry(statement, key);
        }
    }

    /**
     * Executes the query to determine if the entry exists. Sets the response if it fails.
     *
     * @param statement statement
     * @param key key
     * @throws SQLException exception
     */
    private void getEntry(PreparedStatement statement, String key) throws SQLException {
        try (ResultSet rs = statement.executeQuery()) {
            if (rs.first()) {
                // found entry
                if (logger.isDebugEnabled()) {
                    logger.debug("DbAudit: Found key {}", rs.getString(1));
                }
            } else {
                logger.error("DbAudit: can't find newly-created entry with key {}", key);
                setResponse("Can't find newly-created entry");
            }
        }
    }

    /**
     * Deletes an entry.
     *
     * @param connection connection
     * @param key key
     * @throws SQLException exception
     */
    private void deleteEntry(Connection connection, String key) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement("DELETE FROM Audit WHERE name = ?")) {
            statement.setString(1, key);
            statement.executeUpdate();
        }
    }
}
