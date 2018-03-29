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

package org.onap.policy.drools.resourcelocks;

import org.onap.policy.drools.utils.OrderedService;
import org.onap.policy.drools.utils.OrderedServiceImpl;

/**
 * This interface provides a way to invoke optional features at various points
 * in the code. At appropriate points in the application, the code iterates
 * through this list, invoking these optional methods.
 */
public interface ResourceLockFeatureAPI extends OrderedService {

    /**
     * 'FeatureAPI.impl.getList()' returns an ordered list of objects
     * implementing the 'FeatureAPI' interface.
     */
    public static OrderedServiceImpl<ResourceLockFeatureAPI> impl =
                    new OrderedServiceImpl<>(ResourceLockFeatureAPI.class);

    /**
     * This method is called before a lock is acquired for a resource.
     * 
     * @param resourceId
     * @return
     *         <dl>
     *         <dt>{@code true}</dt>
     *         <dd>if the implementor handled the request, and the resource was
     *         successfully locked</dd>
     *         <dt>{@code false}</dt>
     *         <dd>if the lock request should be denied</dd>
     *         <dt>{@code null}</dt>
     *         <dd>if the implementor did not handle the request</dd>
     *         </dl>
     */
    public default Boolean beforeLock(String resourceId) {
        return null;
    }

    /**
     * This method is called after a lock has been requested for a resource.
     * 
     * @param resourceId
     * @param locked {@code true} if the lock was acquired, {@code false}
     *        otherwise
     * @return {@code true} if the implementor handled the request,
     *         {@code false} otherwise
     */
    public default boolean afterLock(String resourceId, boolean locked) {
        return false;
    }

    /**
     * This method is called before a lock on a resource is released.
     * 
     * @param resourceId
     * @return
     *         <dl>
     *         <dt>{@code true}</dt>
     *         <dd>if the implementor handled the request, and the resource was
     *         successfully unlocked</dd>
     *         <dt>{@code false}</dt>
     *         <dd>if the lock request should be denied</dd>
     *         <dt>{@code null}</dt>
     *         <dd>if the implementor did not handle the request</dd>
     *         </dl>
     */
    public default Boolean beforeUnlock(String resourceId) {
        return null;
    }

    /**
     * This method is called after a lock on a resource is released.
     * 
     * @param resourceId
     * @param unlocked {@code true} if the lock was released, {@code false}
     *        otherwise
     * @return {@code true} if the implementor handled the request,
     *         {@code false} otherwise
     */
    public default boolean afterUnlock(String resourceId, boolean unlocked) {
        return false;
    }

    /**
     * This method is called before a check is made to determine if a resource
     * is locked.
     * 
     * @param resourceId
     * @return
     *         <dl>
     *         <dt>{@code true}</dt>
     *         <dd>if the implementor handled the request, and the resource was
     *         found to be locked</dd>
     *         <dt>{@code false}</dt>
     *         <dd>if the implementor handled the request, and the resource was
     *         found to be unlocked</dd>
     *         <dt>{@code null}</dt>
     *         <dd>if the implementor did not handle the request</dd>
     *         </dl>
     */
    public default Boolean beforeIsActive(String resourceId) {
        return null;
    }
}
