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
         * 
     * Result of a requested operation.
     */
    public enum OperResult {

        /**
         * The implementer accepted the request; no additional locking logic should be
         * performed.
         */
        OPER_ACCEPTED,

        /**
         * The implementer denied the request; no additional locking logic should be
         * performed.
         */
        OPER_DENIED,


        /**
         * The implementer did not handle the request; additional locking logic <i>should
         * be<i> performed.
         */
        OPER_UNHANDLED
    }

    /**
     * This method is called before a lock is acquired on a resource.  It may be
     * invoked repeatedly to extend the time that a lock is held.
     * 
     * @param resourceId
     * @param owner
     * @param holdSec the amount of time, in seconds, that the lock should be held
     * @return the result, where <b>OPER_DENIED</b> indicates that the lock is currently
     *         held by another owner
     */
    public default OperResult beforeLock(String resourceId, String owner, int holdSec) {
        return OperResult.OPER_UNHANDLED;
    }

    /**
     * This method is called after a lock for a resource has been acquired or denied.
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
     * @return the result, where <b>OPER_DENIED</b> indicates that the lock is not
     *         currently held by the given owner
     */
    public default OperResult beforeUnlock(String resourceId, String owner) {
        return OperResult.OPER_UNHANDLED;
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
     * @return the result, where <b>OPER_ACCEPTED</b> indicates that the resource is
     *         locked, while <b>OPER_DENIED</b> indicates that it is not
     */
    public default OperResult beforeIsLocked(String resourceId) {
        return OperResult.OPER_UNHANDLED;
    }

    /**
     * This method is called before a check is made to determine if a particular owner
     * holds the lock on a resource.
     * 
     * @param resourceId
     * @param owner
     * @return the result, where <b>OPER_ACCEPTED</b> indicates that the resource is
     *         locked by the given owner, while <b>OPER_DENIED</b> indicates that it is
     *         not
     */
    public default OperResult beforeIsLockedBy(String resourceId, String owner) {
        return OperResult.OPER_UNHANDLED;
    }
}
