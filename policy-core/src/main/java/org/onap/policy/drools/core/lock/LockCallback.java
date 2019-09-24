/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2019 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.core.lock;

/**
 * Callback invoked when a lock is granted or lost.
 *
 * <p/>
 * Note: these methods may or may not be invoked by the thread that requested the lock.
 */
public interface LockCallback {

    /**
     * Called to indicate that a lock has been granted.
     *
     * @param lock lock that has been granted
     */
    void lockAvailable(Lock lock);

    /**
     * Called to indicate that a lock is permanently unavailable (e.g., lost, expired).
     *
     * @param lock lock that has been lost
     */
    void lockUnavailable(Lock lock);
}
