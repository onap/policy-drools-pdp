/*
 * ============LICENSE_START=======================================================
 * feature-server-pool
 * ================================================================================
 * Copyright (C) 2020 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.serverpool;

import org.onap.policy.drools.serverpool.DroolsSessionRestore;

/**
 * Backup data associated with a Drools session.
 */
public class DroolsSessionBackup implements Backup {
    private long droolsTimeoutMillis;

    /**
     * Constructor: initialize droolsTimeoutMillis.
     */
    public DroolsSessionBackup(long droolsTimeoutMillis) {
        this.droolsTimeoutMillis = droolsTimeoutMillis;
    }

    /**
      * {@inheritDoc}
      */
    @Override
    public Restore generate(int bucketNumber) {
        /**
         * Go through all of the Drools sessions, and generate backup data.
         * If there is no data to backup for this bucket, return 'null'
         */

        DroolsSessionRestore droolsSessionRestore = new DroolsSessionRestore(droolsTimeoutMillis);
        DroolsSessionRestore restore = droolsSessionRestore;
        return restore.backup(bucketNumber) ? restore : null;
    }
}