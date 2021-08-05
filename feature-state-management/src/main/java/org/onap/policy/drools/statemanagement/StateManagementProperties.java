/*-
 * ============LICENSE_START=======================================================
 * feature-state-management
 * ================================================================================
 * Copyright (C) 2017-2018, 2020-2021 AT&T Intellectual Property. All rights reserved.
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

import java.util.Properties;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.eclipse.persistence.config.PersistenceUnitProperties;
import org.onap.policy.common.endpoints.properties.PolicyEndPointProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StateManagementProperties {
    // get an instance of logger
    private static final Logger logger = LoggerFactory.getLogger(StateManagementProperties.class);

    public static final String NODE_NAME = "resource.name";
    public static final String NODE_TYPE = "node_type";
    public static final String SITE_NAME = "site_name";

    public static final String DB_DRIVER = PersistenceUnitProperties.JDBC_DRIVER;
    public static final String DB_URL = PersistenceUnitProperties.JDBC_URL;
    public static final String DB_USER = PersistenceUnitProperties.JDBC_USER;
    public static final String DB_PWD = PersistenceUnitProperties.JDBC_PASSWORD;
    public static final String DB_TYPE = PersistenceUnitProperties.TARGET_DATABASE;

    public static final String TEST_SERVICES = PolicyEndPointProperties.PROPERTY_HTTP_SERVER_SERVICES;
    public static final String TEST_SERVICES_DEFAULT = "TEST";
    public static final String TEST_HOST =
            TEST_SERVICES + "." + TEST_SERVICES_DEFAULT + PolicyEndPointProperties.PROPERTY_HTTP_HOST_SUFFIX;
    public static final String TEST_PORT =
            TEST_SERVICES + "." + TEST_SERVICES_DEFAULT + PolicyEndPointProperties.PROPERTY_HTTP_PORT_SUFFIX;
    public static final String TEST_REST_CLASSES =
            TEST_SERVICES + "." + TEST_SERVICES_DEFAULT + PolicyEndPointProperties.PROPERTY_HTTP_REST_CLASSES_SUFFIX;
    public static final String TEST_REST_CLASSES_DEFAULT = IntegrityMonitorRestManager.class.getName();
    public static final String TEST_MANAGED =
            TEST_SERVICES + "." + TEST_SERVICES_DEFAULT + PolicyEndPointProperties.PROPERTY_MANAGED_SUFFIX;
    public static final String TEST_MANAGED_DEFAULT = "false";
    public static final String TEST_SWAGGER =
            TEST_SERVICES + "." + TEST_SERVICES_DEFAULT + PolicyEndPointProperties.PROPERTY_HTTP_SWAGGER_SUFFIX;
    public static final String TEST_SWAGGER_DEFAULT = "true";
    public static final String RESOURCE_NAME = "resource.name";
    public static final String FP_MONITOR_INTERVAL = "fp_monitor_interval";
    public static final String FAILED_COUNTER_THRESHOLD = "failed_counter_threshold";
    public static final String TEST_TRANS_INTERVAL = "test_trans_interval";
    public static final String WRITE_FPC_INTERVAL = "write_fpc_interval";
    public static final String DEPENDENCY_GROUPS = "dependency_groups";

    @Getter
    private static Properties properties = null;

    /**
     * Initialize the parameter values from the feature-state-management.properties file values.
     *
     * <p>This is designed so that the Properties object is obtained from the
     * feature-state-management.properties file and then is passed to this method to initialize the
     * value of the parameters. This allows the flexibility of JUnit tests using
     * getProperties(filename) to get the properties while runtime methods can use
     * getPropertiesFromClassPath(filename).
     *
     * @param prop properties
     */
    public static void initProperties(Properties prop) {
        logger.info("StateManagementProperties.initProperties(Properties): entry");
        logger.info("\n\nStateManagementProperties.initProperties: Properties = \n{}\n\n", prop);

        properties = prop;
    }

    public static String getProperty(String key) {
        return properties.getProperty(key);
    }
}
