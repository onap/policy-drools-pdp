/*
 * ============LICENSE_START=======================================================
 * api-resource-locks
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

import java.util.concurrent.Future;
import org.onap.policy.drools.utils.OrderedService;
import org.onap.policy.drools.utils.OrderedServiceImpl;

/**
 * Resource locks. Each lock has an "owner", which is intended to be unique across a
 * single instance of a running PolicyEngine.
 * <p>
 * This interface provides a way to invoke optional features at various points in the
 * code. At appropriate points in the application, the code iterates through this list,
 * invoking these optional methods.
 * <p>
 * Implementers may choose to implement a level of locking appropriate to the application.
 * For instance, they may choose to implement an engine-wide locking scheme, or they may
 * choose to implement a global locking scheme (e.g., through a shared DB).
 */
public interface PolicyResourceLockFeatureAPI extends OrderedService {

    /**
     * 'FeatureAPI.impl.getList()' returns an ordered list of objects implementing the
     * 'FeatureAPI' interface.
     */
    public static OrderedServiceImpl<PolicyResourceLockFeatureAPI> impl =
                    new OrderedServiceImpl<>(PolicyResourceLockFeatureAPI.class);

    /**
     * Result returned by <i>beforeLock()</i>.
     */
    public enum LockResult {

        /**
         * The lock was acquired and handled by the implementer. No further locking logic
         * should be performed.
         */
        LOCK_ACQUIRED,

        /**
         * The lock was denied by the implementer. No further locking logic should be
         * performed.
         */
        LOCK_DENIED,

        /**
         * The result will be signaled via the callback function. No further locking logic
         * should be performed.
         */
        LOCK_ASYNC,

        /**
         * The implementer did not handle the lock; locking logic should continue.
         */
        LOCK_UNHANDLED
    };

    /**
     * Result returned by various methods.
     */
    public enum OperResult {

        /**
         * The operation succeeded and was handled by the implementer. No further locking
         * logic should be performed.
         */
        OPER_SUCCESS,

        /**
         * The operation failed and was handled by the implementer. No further locking
         * logic should be performed.
         */
        OPER_FAIL,

        /**
         * The implementer did not handle the operation; locking logic should continue.
         */
        OPER_UNHANDLED
    };

    /**
     * Callback that an implementer invokes when a lock is acquired asynchronously. The
     * implementer invokes one of the methods to indicate that the lock was acquired, or
     * that an exception occurred.
     */
    public static interface Callback {

        /**
         * Indicates that the lock has been acquired.
         */
        public void locked();

        /**
         * Indicates that the request did not complete, due to an exception.
         * 
         * @param exception exception that caused the request to abort
         */
        public void exception(Exception exception);

    }

    /**
     * This method is called before a lock is acquired on a resource. If a callback is
     * provided, and the implementer is unable to acquire the lock immediately, then the
     * implementer will invoke the callback once the lock is acquired. Note: the callback
     * will only be invoked if this method returns <i>LOCK_ASYNC</i>.
     * 
     * @param resourceId
     * @param owner
     * @param callback function to invoke, if the requester wishes to wait for the lock to
     *        come available, {@code null} to provide immediate replies
     * @param future will be populated with the request future, if the return result is
     *        <i>LOCK_ASYNC</i>. This future may be used to check the status of and cancel
     *        the outstanding lock request
     * @return the result of the operation
     */
    public default LockResult beforeLock(String resourceId, String owner, Callback callback,
                    Reference<Future<Boolean>> future) {

        return LockResult.LOCK_UNHANDLED;
    }

    /**
     * This method is called after a lock has been requested for a resource.
     * 
     * @param resourceId
     * @param owner
     * @param locked {@code true} if the lock was acquired, {@code false} otherwise
     * @return {@code true} if the implementer handled the request, {@code false}
     *         otherwise
     */
    public default boolean afterLock(String resourceId, String owner, boolean locked) {
        return false;
    }

    /**
     * This method is called after a lock has been asynchronously acquired.
     * 
     * @param resourceId
     * @param owner
     * @param locked {@code true} if the lock was acquired, {@code false} if an exception
     *        prevented the lock from being acquired
     * @return {@code true} if the implementer handled the request, {@code false}
     *         otherwise
     */
    public default boolean afterAsyncLock(String resourceId, String owner, boolean locked) {
        return false;
    }

    /**
     * This method is called before a lock on a resource is released.
     * 
     * @param resourceId
     * @param owner
     * @return the result of the operation
     */
    public default OperResult beforeUnlock(String resourceId, String owner) {
        return null;
    }

    /**
     * This method is called after a lock on a resource is released.
     * 
     * @param resourceId
     * @param owner
     * @param unlocked {@code true} if the lock was released, {@code false} otherwise
     * @return {@code true} if the implementer handled the request, {@code false}
     *         otherwise
     */
    public default boolean afterUnlock(String resourceId, String owner, boolean unlocked) {
        return false;
    }

    /**
     * This method is called before a check is made to determine if a resource is locked.
     * 
     * @param resourceId
     * @return the result of the operation
     */
    public default OperResult beforeIsLocked(String resourceId) {
        return null;
    }

    /**
     * This method is called before a check is made to determine if a particular owner
     * holds the lock on a resource.
     * 
     * @param resourceId
     * @param owner
     * @return the result of the operation
     */
    public default OperResult beforeIsLockedBy(String resourceId, String owner) {
        return null;
    }
}
