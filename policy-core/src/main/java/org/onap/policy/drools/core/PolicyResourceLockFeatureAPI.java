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

package org.onap.policy.drools.core;

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
    public enum BeforeLockResult {

        /**
         * The lock was handled and acquired by the implementer.
         */
        ACQUIRED,

        /**
         * The lock was handled and denied by the implementer.
         */
        DENIED,

        /**
         * The lock was handled by the implementer, and the result will be signaled via
         * the callback function.
         */
        ASYNC,

        /**
         * The implementer did not handle the lock.
         */
        NOT_HANDLED
    };
    
    // TODO introduce a tri-state Enum

    /**
     * Callback that an implementer invokes when a lock is acquired asynchronously. The
     * implementer invokes <i>run()</i> once the lock has been acquired. In addition,
     * immediately after invoking <i>run()</i>, the implementer should invoke
     * <i>isCancelled()</i>, to see if the request had been previously cancelled. If so,
     * then the implementer should immediately release the lock. Furthermore, at any
     * point, if the implementer sees that the request has been cancelled, then the
     * implementer may choose to immediately stop trying to acquire the lock.
     * <p>
     * Note: this implies that it should be OK to invoke <i>run()</i> even after
     * <i>cancel()</i> has been called.
     */
    public static interface Callback extends Future<Boolean>, Runnable {

    }

    /**
     * This method is called before a lock is acquired on a resource. If a callback is
     * provided, and the implementer is unable to acquire the lock immediately, then the
     * implementer will invoke the callback's <i>run()</i> method once the lock is
     * acquired. Note: the callback will only be invoked if this method returns ASYNC.
     * 
     * @param resourceId
     * @param owner
     * @param callback function to invoke, if the requester wishes to wait for the lock to
     *        come available, {@code null} to provide immediate replies
     * @return the result of the operation
     */
    public default BeforeLockResult beforeLock(String resourceId, String owner, Callback callback) {
        return BeforeLockResult.NOT_HANDLED;
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
     * @return {@code true} if the implementer handled the request, {@code false}
     *         otherwise
     */
    public default boolean afterAsyncLock(String resourceId, String owner) {
        return false;
    }

    /**
     * This method is called before a lock on a resource is released.
     * 
     * @param resourceId
     * @param owner
     * @return
     *         <dl>
     *         <dt>{@code true}</dt>
     *         <dd>if the implementer handled the request, and the resource was
     *         successfully unlocked</dd>
     *         <dt>{@code false}</dt>
     *         <dd>if the implementer handled the request, and it should be denied</dd>
     *         <dt>{@code null}</dt>
     *         <dd>if the implementer did not handle the request</dd>
     *         </dl>
     */
    public default Boolean beforeUnlock(String resourceId, String owner) {
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
     * @return
     *         <dl>
     *         <dt>{@code true}</dt>
     *         <dd>if the implementer handled the request, and the resource was found to
     *         be locked</dd>
     *         <dt>{@code false}</dt>
     *         <dd>if the implementer handled the request, and the resource was found to
     *         be unlocked</dd>
     *         <dt>{@code null}</dt>
     *         <dd>if the implementer did not handle the request</dd>
     *         </dl>
     */
    public default Boolean beforeIsLocked(String resourceId) {
        return null;
    }

    /**
     * This method is called before a check is made to determine if a particular owner
     * holds the lock on a resource.
     * 
     * @param resourceId
     * @param owner
     * @return
     *         <dl>
     *         <dt>{@code true}</dt>
     *         <dd>if the implementer handled the request, and the resource was found to
     *         be locked</dd>
     *         <dt>{@code false}</dt>
     *         <dd>if the implementer handled the request, and the resource was found to
     *         be unlocked</dd>
     *         <dt>{@code null}</dt>
     *         <dd>if the implementer did not handle the request</dd>
     *         </dl>
     */
    public default Boolean beforeIsLockedBy(String resourceId, String owner) {
        return null;
    }
}
