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
     * Callback that an implementer invokes when a lock is acquired (or denied),
     * asynchronously. The implementer invokes the method to indicate that the lock was
     * acquired (or denied).
     */
    @FunctionalInterface
    public static interface Callback {

        /**
         * 
         * @param locked {@code true} if the lock was acquired, {@code false} if the lock
         *        was denied
         */
        public void set(boolean locked);
    }

    /**
     * This method is called before a lock is acquired on a resource. If a callback is
     * provided, and the implementer is unable to acquire the lock immediately, then the
     * implementer will invoke the callback once the lock is acquired. If the implementer
     * handled the request, then it will return a future, which may be in one of three
     * states:
     * <dl>
     * <dt>isDone()=true and get()=true</dt>
     * <dd>the lock has been acquired; the callback may or may not have been invoked</dd>
     * <dt>isDone()=true and get()=false</dt>
     * <dd>the lock request has been denied; the callback may or may not have been
     * invoked</dd>
     * <dt>isDone()=false</dt>
     * <dd>the lock was not immediately available and a callback was provided. The
     * callback will be invoked once the lock is acquired (or denied). In this case, the
     * future may be used to cancel the request</dd>
     * </dl>
     * 
     * @param resourceId
     * @param owner
     * @param callback function to invoke, if the requester wishes to wait for the lock to
     *        come available, {@code null} to provide immediate replies
     * @return a future for the lock, if the implementer handled the request, {@code null}
     *         if additional locking logic should be performed
     * @throws IllegalStateException if the owner already holds the lock or is already in
     *         the queue to get the lock
     */
    public default Future<Boolean> beforeLock(String resourceId, String owner, Callback callback) {
        return null;
    }

    /**
     * This method is called after a lock for a resource has been acquired or denied. This
     * may be invoked immediately, if the status can be determined immediately, or it may
     * be invoked asynchronously, once the status has been determined.
     * 
     * @param resourceId
     * @param owner
     * @param locked {@code true} if the lock was acquired, {@code false} if it was denied
     * @return {@code true} if the implementer handled the request, {@code false}
     *         otherwise
     */
    public default boolean afterLock(String resourceId, String owner, boolean locked) {
        return false;
    }

    /**
     * This method is called before a lock on a resource is released.
     * 
     * @param resourceId
     * @param owner
     *        <dt>true</dt>
     *        <dd>the implementer handled the request and found the resource to be locked
     *        by the given owner; the resource was unlocked and no additional locking
     *        logic should be performed</dd>
     *        <dt>false</dt>
     *        <dd>the implementer handled the request and found the resource was not
     *        locked by given the owner; no additional locking logic should be
     *        performed</dd>
     *        <dt>null</dt>
     *        <dd>the implementer did not handle the request; additional locking logic
     *        <i>should be</i> performed
     *        </dl>
     */
    public default Boolean beforeUnlock(String resourceId, String owner) {
        return null;
    }

    /**
     * This method is called after a lock on a resource is released.
     * 
     * @param resourceId
     * @param owner
     * @param unlocked {@code true} if the lock was released, {@code false} if the owner
     *        did not have a lock on the resource
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
     *         <dt>true</dt>
     *         <dd>the implementer handled the request and found the resource to be
     *         locked; no additional locking logic should be performed</dd>
     *         <dt>false</dt>
     *         <dd>the implementer handled the request and found the resource was not
     *         locked; no additional locking logic should be performed</dd>
     *         <dt>null</dt>
     *         <dd>the implementer did not handle the request; additional locking logic
     *         <i>should be</i> performed
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
     *         <dt>true</dt>
     *         <dd>the implementer handled the request and found the resource to be locked
     *         by the given owner; no additional locking logic should be performed</dd>
     *         <dt>false</dt>
     *         <dd>the implementer handled the request and found the resource was not
     *         locked by given the owner; no additional locking logic should be
     *         performed</dd>
     *         <dt>null</dt>
     *         <dd>the implementer did not handle the request; additional locking logic
     *         <i>should be</i> performed
     *         </dl>
     */
    public default Boolean beforeIsLockedBy(String resourceId, String owner) {
        return null;
    }
}
