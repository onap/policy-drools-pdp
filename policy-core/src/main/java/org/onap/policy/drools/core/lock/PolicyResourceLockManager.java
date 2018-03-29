/*
 * ============LICENSE_START=======================================================
 * ONAP
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

package org.onap.policy.drools.core.lock;

import static org.onap.policy.drools.core.lock.PolicyResourceLockFeatureAPI.LockResult.LOCK_ACQUIRED;
import static org.onap.policy.drools.core.lock.PolicyResourceLockFeatureAPI.LockResult.LOCK_ASYNC;
import static org.onap.policy.drools.core.lock.PolicyResourceLockFeatureAPI.LockResult.LOCK_UNHANDLED;
import static org.onap.policy.drools.core.lock.PolicyResourceLockFeatureAPI.OperResult.OPER_SUCCESS;
import static org.onap.policy.drools.core.lock.PolicyResourceLockFeatureAPI.OperResult.OPER_UNHANDLED;
import java.util.concurrent.Future;
import java.util.function.Function;
import org.onap.policy.drools.core.lock.PolicyResourceLockFeatureAPI.Callback;
import org.onap.policy.drools.core.lock.PolicyResourceLockFeatureAPI.LockResult;
import org.onap.policy.drools.core.lock.PolicyResourceLockFeatureAPI.OperResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manager of resource locks.
 */
public class PolicyResourceLockManager extends PlainLockManager {

    private static Logger logger = LoggerFactory.getLogger(PolicyResourceLockManager.class);

    /**
     * Actual lock manager.
     */
    private static PolicyResourceLockManager mgr = new PolicyResourceLockManager();

    /**
     * Not used.
     */
    private PolicyResourceLockManager() {
        super();
    }

    public static PolicyResourceLockManager getInstance() {
        return mgr;
    }

    /**
     * Attempts to lock a resource. If the lock is not immediately available, and a
     * callback is provided, then it will invoke the callback once the lock is acquired by
     * the owner.
     * 
     * @param resourceId
     * @param owner
     * @param callback function to invoke, if the requester wishes to wait for the lock to
     *        come available, {@code null} otherwise
     * @param future will be populated with the request future, if the return result is
     *        <i>LOCK_ASYNC</i>. This future may be used to check the status of and cancel
     *        the outstanding lock request
     * @return the result
     * @throws IllegalStateException if the owner has already requested a lock on the
     *         resource
     */
    public LockResult lock(String resourceId, String owner, Callback callback, Reference<Future<Boolean>> future) {
        if (resourceId == null) {
            throw new IllegalArgumentException("null resourceId");
        }

        if (owner == null) {
            throw new IllegalArgumentException("null owner");
        }

        LockResult result = doIntercept(LOCK_UNHANDLED, impl -> impl.beforeLock(resourceId, owner, callback, future));

        if (result != LOCK_UNHANDLED) {
            return result;
        }

        result = super.lock(resourceId, owner, callback, future);
        if (result == LOCK_ASYNC) {
            // asynchronous - we'll invoke afterLock() during the callback
            return result;
        }

        // invoke afterLock() on interceptors
        boolean locked = (result == LOCK_ACQUIRED);
        doIntercept(false, impl -> impl.afterLock(resourceId, owner, locked));

        return result;
    }

    /**
     * Unlocks a resource.
     * 
     * @param resourceId
     * @param owner
     * @return {@code true} if unlocked, {@code false} if it was not locked by the given
     *         owner
     * @throws IllegalArgumentException if the resourceId or owner is {@code null}
     */
    public boolean unlock(String resourceId, String owner) {
        if (resourceId == null) {
            throw new IllegalArgumentException("null resourceId");
        }

        if (owner == null) {
            throw new IllegalArgumentException("null owner");
        }

        OperResult result = doIntercept(OPER_UNHANDLED, impl -> impl.beforeUnlock(resourceId, owner));

        if (result != OPER_UNHANDLED) {
            return (result == OPER_SUCCESS);
        }

        boolean unlocked = super.unlock(resourceId, owner);

        doIntercept(false, impl -> impl.afterUnlock(resourceId, owner, unlocked));

        return unlocked;
    }

    /**
     * Determines if a resource is locked by anyone.
     * 
     * @param resourceId
     * @return {@code true} if the resource is locked, {@code false} otherwise
     */
    public boolean isLocked(String resourceId) {
        if (resourceId == null) {
            throw new IllegalArgumentException("null resourceId");
        }

        OperResult result = doIntercept(OPER_UNHANDLED, impl -> impl.beforeIsLocked(resourceId));

        if (result != OPER_UNHANDLED) {
            return (result == OPER_SUCCESS);
        }

        return super.isLocked(resourceId);
    }

    /**
     * Determines if a resource is locked by a particular owner.
     * 
     * @param resourceId
     * @param owner
     * @return {@code true} if the resource is locked, {@code false} otherwise
     */
    public boolean isLockedBy(String resourceId, String owner) {
        if (resourceId == null) {
            throw new IllegalArgumentException("null resourceId");
        }

        if (owner == null) {
            throw new IllegalArgumentException("null owner");
        }

        OperResult result = doIntercept(OPER_UNHANDLED, impl -> impl.beforeIsLockedBy(resourceId, owner));

        if (result != OPER_UNHANDLED) {
            return (result == OPER_SUCCESS);
        }

        return super.isLockedBy(resourceId, owner);
    }

    /**
     * Applies a function to each implementer of the lock feature. Returns as soon as one
     * of them returns a non-null value.
     * 
     * @param continueValue if the implementer returns this value, then it continues to
     *        check addition implementers
     * @param func function to be applied to the implementers
     * @return first non-null value returned by an implementer, <i>continueValue<i/> if
     *         they all returned <i>continueValue<i/>
     */
    private static <T> T doIntercept(T continueValue, Function<PolicyResourceLockFeatureAPI, T> func) {

        for (PolicyResourceLockFeatureAPI impl : PolicyResourceLockFeatureAPI.impl.getList()) {
            try {
                T result = func.apply(impl);
                if (result != continueValue) {
                    return result;
                }

            } catch (RuntimeException e) {
                logger.warn("lock feature {} threw an exception", impl, e);
            }
        }

        return continueValue;
    }

    /**
     * Makes a future that invokes afterAsyncLock() when it's notified.
     */
    @Override
    protected LockRequestFuture makeFuture(String resourceId, String owner, Callback callback) {
        return new ImplFuture(resourceId, owner, callback);
    }


    /**
     * Lock Request Future that invokes the afterAsyncLock() method for each implementer.
     */
    public class ImplFuture extends MapFuture {

        /**
         * 
         * @param resourceId
         * @param owner
         * @param callback
         */
        public ImplFuture(String resourceId, String owner, Callback callback) {
            super(resourceId, owner, callback);
        }

        @Override
        public boolean setLocked() {
            boolean result = super.setLocked();

            if (result) {
                // wasn't complete yet - tell afterAsyncLock
                doIntercept(false, impl -> impl.afterAsyncLock(getResourceId(), getOwner(), true));
            }

            return result;
        }

        @Override
        public boolean setException(Exception exception) {
            boolean result = super.setException(exception);

            if (result) {
                // wasn't complete yet - tell afterAsyncLock
                doIntercept(false, impl -> impl.afterAsyncLock(getResourceId(), getOwner(), false));
            }

            return result;
        }
    }
}
