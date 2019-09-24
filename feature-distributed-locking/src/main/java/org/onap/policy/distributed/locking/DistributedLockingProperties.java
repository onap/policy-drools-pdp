/*
 * ============LICENSE_START=======================================================
 * feature-distributed-locking
 * ================================================================================
 * Copyright (C) 2018 AT&T Intellectual Property. All rights reserved.
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

import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.onap.policy.common.utils.properties.BeanConfigurator;
import org.onap.policy.common.utils.properties.Property;
import org.onap.policy.common.utils.properties.exception.PropertyException;


@Getter
@Setter
public class DistributedLockingProperties {

    public static final String DB_DRIVER = "javax.persistence.jdbc.driver";
    public static final String DB_URL = "javax.persistence.jdbc.url";
    public static final String DB_USER = "javax.persistence.jdbc.user";
    public static final String DB_PASS = "javax.persistence.jdbc.password";
    public static final String TRANSIENT_ERROR_CODES = "transient.error.codes";
    public static final String EXPIRE_CHECK_SEC = "expire.check.seconds";
    public static final String RETRY_SEC = "retry.seconds";
    public static final String MAX_RETRIES = "max.retries";
    public static final String MAX_THREADS = "max.threads";

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
     * Vendor-specific error codes that are "transient", meaning they may go away if the
     * command is repeated (e.g., connection issue), as opposed to something like a syntax
     * error or a duplicate key.
     */
    @Property(name = TRANSIENT_ERROR_CODES)
    private String errorCodeStrings;

    private final Set<Integer> transientErrorCodes;

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
     * Maximum number of threads in the thread pool.
     */
    @Property(name = MAX_THREADS, defaultValue = "5")
    private int maxThreads;

    /**
     * Constructs the object, populating fields from the properties.
     *
     * @param props properties from which to configure this
     * @throws PropertyException if an error occurs
     */
    public DistributedLockingProperties(Properties props) throws PropertyException {
        new BeanConfigurator().configureFromProperties(this, props);

        Set<Integer> set = new HashSet<>();
        for (String text : errorCodeStrings.split(",")) {
            text = text.trim();
            if (text.isEmpty()) {
                continue;
            }

            try {
                set.add(Integer.valueOf(text));

            } catch (NumberFormatException e) {
                throw new PropertyException(TRANSIENT_ERROR_CODES, "errorCodeStrings", e);
            }
        }

        transientErrorCodes = Collections.unmodifiableSet(set);
    }

    /**
     * Determines if an error is transient.
     *
     * @param errorCode error code to check
     * @return {@code true} if the error is transient, {@code false} otherwise
     */
    public boolean isTransient(int errorCode) {
        return transientErrorCodes.contains(errorCode);
    }
}
