/*
 * ============LICENSE_START=======================================================
 * feature-distributed-locking
 * ================================================================================
 * Copyright (C) 2018-2019, 2021 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.distributed.locking;

import java.util.Properties;
import lombok.Getter;
import lombok.Setter;
import org.eclipse.persistence.config.PersistenceUnitProperties;
import org.onap.policy.common.utils.properties.BeanConfigurator;
import org.onap.policy.common.utils.properties.Property;
import org.onap.policy.common.utils.properties.exception.PropertyException;


@Getter
@Setter
public class DistributedLockProperties {
    public static final String PREFIX = "distributed.locking.";

    public static final String DB_DRIVER = PersistenceUnitProperties.JDBC_DRIVER;
    public static final String DB_URL = PersistenceUnitProperties.JDBC_URL;
    public static final String DB_USER = PersistenceUnitProperties.JDBC_USER;
    public static final String DB_PASS = PersistenceUnitProperties.JDBC_PASSWORD;
    public static final String EXPIRE_CHECK_SEC = PREFIX + "expire.check.seconds";
    public static final String RETRY_SEC = PREFIX + "retry.seconds";
    public static final String MAX_RETRIES = PREFIX + "max.retries";

    /**
     * Database driver.
     */
    @Property(name = DB_DRIVER)
    private String dbDriver;

    /**
     * Database url.
     */
    @Property(name = DB_URL)
    private String dbUrl;

    /**
     * Database user.
     */
    @Property(name = DB_USER)
    private String dbUser;

    /**
     * Database password.
     */
    @Property(name = DB_PASS)
    private String dbPwd;

    /**
     * Time, in seconds, to wait between checks for expired locks.
     */
    @Property(name = EXPIRE_CHECK_SEC, defaultValue = "900")
    private int expireCheckSec;

    /**
     * Number of seconds to wait before retrying, after a DB error.
     */
    @Property(name = RETRY_SEC, defaultValue = "60")
    private int retrySec;

    /**
     * Maximum number of times to retry a DB operation.
     */
    @Property(name = MAX_RETRIES, defaultValue = "2")
    private int maxRetries;

    /**
     * Constructs the object, populating fields from the properties.
     *
     * @param props properties from which to configure this
     * @throws PropertyException if an error occurs
     */
    public DistributedLockProperties(Properties props) throws PropertyException {
        new BeanConfigurator().configureFromProperties(this, props);
    }
}
