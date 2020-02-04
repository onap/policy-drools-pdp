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

import java.io.Serializable;
import java.util.IdentityHashMap;

import org.onap.policy.drools.core.lock.Lock;
import org.onap.policy.drools.core.lock.LockCallback;
import org.onap.policy.drools.serverpooltest.TargetLockWrapper;

/**
 * This class implements the 'TargetLockWrapper' interface. There is one
 * 'TargetLockWrapperImpl' class for each simulated host.
 */
public class TargetLockWrapperImpl implements TargetLockWrapper {
    // this is the 'TargetLock' instance associated with the wrapper
    private TargetLock targetLock;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean free() {
        return targetLock.free();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isActive() {
        return targetLock.isActive();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public State getState() {
        return TargetLockWrapper.State.valueOf(targetLock.getState().toString());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getOwnerKey() {
        return targetLock.getOwnerKey();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "TLW-"
               + String.valueOf(AdapterImpl.getAdapter(TargetLockWrapperImpl.class.getClassLoader()))
               + "[" + targetLock.toString() + "]";
    }

    /**
     * This method creates a new 'TargetLock'. Internally, an 'OwnerAdapter'
     * instance is built as well, which translates the 'LockCallback'
     * callbacks to 'TargetLockWrapper.Owner' callbacks. As with the call to
     * 'new TargetLock(...)', it is possible for the callback occur before
     * this method returns -- this can happen if the 'key' hashes to a bucket
     * owned by the current host.
     *
     * @param key string key identifying the lock
     * @param ownerKey string key identifying the owner, which must hash to
     *     a bucket owned by the current host (it is typically a 'RequestID')
     * @param owner owner of the lock (will be notified when going from
     *     WAITING to ACTIVE)
     * @param waitForLock this controls the behavior when 'key' is already
     *     locked - 'true' means wait for it to be freed, 'false' means fail
     * @return a 'TargetLockWrapper' instance associated with the new
     *     'TargetLock.
     */
    static TargetLockWrapper newTargetLock(
        String key, String ownerKey, TargetLockWrapper.Owner owner, boolean waitForLock) {

        TargetLockWrapperImpl rval = new TargetLockWrapperImpl();
        rval.targetLock =
            new TargetLock(key, ownerKey,
                           TargetLockWrapperImpl.getOwnerAdapter(rval, owner),
                           waitForLock);
        return rval;
    }

    /**
     * This method creates a new 'TargetLock'. Internally, an 'OwnerAdapter'
     * instance is built as well, which translates the 'LockCallback'
     * callbacks to 'TargetLockWrapper.Owner' callbacks. As with the call to
     * 'new TargetLock(...)', it is possible for the callback occur before
     * this method returns -- this can happen if the 'key' hashes to a bucket
     * owned by the current host.
     *
     * @param key string key identifying the lock
     * @param ownerKey string key identifying the owner, which must hash to
     *     a bucket owned by the current host (it is typically a 'RequestID')
     * @param owner owner of the lock (will be notified when going from
     *     WAITING to ACTIVE)
     * @return a 'TargetLockWrapper' instance associated with the new
     *     'TargetLock.
     */
    static TargetLockWrapper newTargetLock(
        String key, String ownerKey, TargetLockWrapper.Owner owner) {

        TargetLockWrapperImpl rval = new TargetLockWrapperImpl();
        rval.targetLock =
            new TargetLock(key, ownerKey,
                           TargetLockWrapperImpl.getOwnerAdapter(rval, owner));
        return rval;
    }

    /* ============================================================ */

    /**
     * This method builds an adapter that implements the 'LockCallback'
     * callback interface, translating it to 'TargetLockWrapper.Owner'.
     *
     * @param targetLock the TargetLockWrapper that is using this adapter
     * @param owner the 'TargetLockWrapper.Owner' callback
     * @return an adapter implementing the 'LockCallback' interface
     *     ('null' is returned if 'owner' is null -- this is an error condition,
     *     but is used to verify the error handling of the 'TargetLock'
     *     constructor.
     */
    public static LockCallback getOwnerAdapter(
        TargetLockWrapper targetLock, TargetLockWrapper.Owner owner) {

        return owner == null ? null : new OwnerAdapter(targetLock, owner);
    }

    /**
     * This class is an adapter that implements the 'LockCallback' callback
     * interface, translating it to a 'TargetLockWrapper.Owner' callback.
     */
    public static class OwnerAdapter implements LockCallback, Serializable {
        // the 'TargetLockWrapper' instance to pass as an argument in the callback
        TargetLockWrapper targetLock;

        // the 'TargetLockWrapper.Owner' callback
        TargetLockWrapper.Owner owner;

        /**
         * Constructor - initialize the adapter.
         *
         * @param targetLock this will be passed as an argument in the callback
         * @param owner the object implementing the 'TargetLockWrapper.Owner'
         *     interface
         */
        private OwnerAdapter(TargetLockWrapper targetLock, TargetLockWrapper.Owner owner) {
            this.targetLock = targetLock;
            this.owner = owner;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void lockAvailable(Lock lock) {
            // forward 'lockAvailable' callback
            owner.lockAvailable(targetLock);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void lockUnavailable(Lock lock) {
            // forward 'lockUnavailable' callback
            owner.lockUnavailable(targetLock);
        }
    }
}
