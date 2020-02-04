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

import java.util.List;

/**
 * This interface implements a type of backup; for example, there is one
 * for backing up Drools objects within sessions, and another for
 * backing up lock data.
 */
public interface Backup {
    /**
     * This method is called to add a 'Backup' instance to the registered list.
     *
     * @param backup an object implementing the 'Backup' interface
     */
    public static void register(Backup backup) {
        List<Backup> backupList = Bucket.getBackupList();
        synchronized (backupList) {
            if (!backupList.contains(backup)) {
                backupList.add(backup);
            }
        }
    }

    /**
     * Generate Serializable backup data for the specified bucket.
     *
     * @param bucketNumber the bucket number to back up
     * @return a Serializable object containing backkup data
     */
    public Restore generate(int bucketNumber);
}