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

package org.onap.policy.drools.serverpooltest;

import java.io.Serializable;

/**
 * This class provides base classes for accessing the various 'TargetLock'
 * classes. There is a separate copy of the 'TargetLock' class for each
 * adapter, and this wrapper was created to give them a common interface.
 */
public interface TargetLockWrapper extends Serializable {
    /**
     * There is a separate copy of 'TargetLock.State' for each adapter --
     * The 'TargetLockWrapper.getState()' maps these into a common
     * 'TargetLockWrapper.State' enumeration.
     */
    public enum State {
        WAITING, ACTIVE, FREE, LOST
    }

    /**
     * This calls the 'TargetLock.free()' method
     *
     * @return 'true' if successful, 'false' if it was not locked, or there
     *     appears to be corruption in 'LocalLocks' tables
     */
    public boolean free();

    /**
     * This calls the 'TargetLock.isActive()' method
     *
     * @return 'true' if the lock is in the ACTIVE state, and 'false' if not
     */
    public boolean isActive();

    /**
     * This calls the 'TargetLock.getState()' method
     *
     * @return the current state of the lock, as a 'TargetLockWrapper.State'
     */
    public State getState();

    /**
     * This calls the 'TargetLock.getOwnerKey()' method
     *
     * @return the owner key field
     */
    public String getOwnerKey();

    /**
     * Return the value returned by 'TargetLock.toString()'.
     *
     * @return the value returned by 'TargetLock.toString()'
     */
    public String toString();

    /* ============================================================ */

    /**
     * This interface mimics the 'LockCallback' interface, with the
     * exception that 'TargetLockWrapper' is used as the arguments to the
     * callback methods.
     */
    public static interface Owner {
        /**
         * Callback indicates the lock was successful.
         *
         * @param lock the 'TargetLockWrapper' instance
         */
        public void lockAvailable(TargetLockWrapper lock);

        /**
         * Callback indicates the lock request has failed.
         *
         * @param lock the 'TargetLockWrapper' instance
         */
        public void lockUnavailable(TargetLockWrapper lock);
    }
}
