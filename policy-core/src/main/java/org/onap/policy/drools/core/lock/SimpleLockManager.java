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

import static org.onap.policy.drools.core.lock.LockRequestFuture.MSG_NULL_OWNER;
import static org.onap.policy.drools.core.lock.LockRequestFuture.MSG_NULL_RESOURCE_ID;
import static org.onap.policy.drools.core.lock.LockRequestFuture.makeNullArgException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import org.onap.policy.drools.core.lock.PolicyResourceLockFeatureAPI.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple lock manager. Callbacks are ignored. Does not redirect to lock feature
 * implementers.
 */
public class SimpleLockManager {

    protected static Logger logger = LoggerFactory.getLogger(SimpleLockManager.class);

    /**
     * Maps a resource to the owner that holds the lock on it.
     */
    private ConcurrentHashMap<String, String> resource2owner = new ConcurrentHashMap<>();

    /**
     * 
     */
    public SimpleLockManager() {
        super();
    }

    /**
     * Attempts to lock a resource. This method ignores the callback and always returns a
     * {@link CompletedLockRequest}.
     * 
     * @param resourceId
     * @param owner
     * @param callback function to invoke, if the requester wishes to wait for the lock to
     *        be acquired, {@code null} to provide immediate replies
     * @return a future for the lock request. The future will be in one of three states:
     *         <dl>
     *         <dt>isDone()=true and get()=true</dt>
     *         <dd>the lock has been acquired; the callback may or may not have been
     *         invoked</dd>
     *         <dt>isDone()=true and get()=false</dt>
     *         <dd>the lock request has been denied; the callback may or may not have been
     *         invoked</dd>
     *         <dt>isDone()=false</dt>
     *         <dd>the lock was not immediately available and a callback was provided. The
     *         callback will be invoked once the lock is acquired (or denied). In this
     *         case, the future may be used to cancel the request</dd>
     *         </dl>
     * @throws IllegalArgumentException if the resourceId or owner is {@code null}
     * @throws IllegalStateException if the owner already holds the lock or is already in
     *         the queue to get the lock
     */
    public Future<Boolean> lock(String resourceId, String owner, Callback callback) {

        if (resourceId == null) {
            throw makeNullArgException(MSG_NULL_RESOURCE_ID);
        }

        if (owner == null) {
            throw makeNullArgException(MSG_NULL_OWNER);
        }

        boolean locked = (resource2owner.putIfAbsent(resourceId, owner) == null);

        if (!locked && owner.equals(resource2owner.get(resourceId))) {
            throw new IllegalStateException("lock for resource " + resourceId + " already owned by " + owner);
        }

        logger.info("lock {} for resource {} owner {}", locked, resourceId, owner);

        return new LockRequestFuture(resourceId, owner, locked);
    }

    /**
     * Unlocks a resource.
     * 
     * @param resourceId
     * @param owner
     * @return {@code true} if unlocked, {@code false} if the given owner does not
     *         currently hold a lock on the resource
     * @throws IllegalArgumentException if the resourceId or owner is {@code null}
     */
    public boolean unlock(String resourceId, String owner) {
        if (resourceId == null) {
            throw makeNullArgException(MSG_NULL_RESOURCE_ID);
        }

        if (owner == null) {
            throw makeNullArgException(MSG_NULL_OWNER);
        }

        boolean unlocked = resource2owner.remove(resourceId, owner);
        logger.info("unlock resource {} owner {} = {}", resourceId, owner, unlocked);

        return unlocked;
    }

    /**
     * Determines if a resource is locked by anyone.
     * 
     * @param resourceId
     * @return {@code true} if the resource is locked, {@code false} otherwise
     * @throws IllegalArgumentException if the resourceId is {@code null}
     */
    public boolean isLocked(String resourceId) {

        if (resourceId == null) {
            throw makeNullArgException(MSG_NULL_RESOURCE_ID);
        }

        boolean locked = resource2owner.containsKey(resourceId);

        logger.debug("resource {} isLocked = {}", resourceId, locked);

        return locked;
    }

    /**
     * Determines if a resource is locked by a particular owner.
     * 
     * @param resourceId
     * @param owner
     * @return {@code true} if the resource is locked, {@code false} otherwise
     * @throws IllegalArgumentException if the resourceId or owner is {@code null}
     */
    public boolean isLockedBy(String resourceId, String owner) {

        if (resourceId == null) {
            throw makeNullArgException(MSG_NULL_RESOURCE_ID);
        }

        if (owner == null) {
            throw makeNullArgException(MSG_NULL_OWNER);
        }

        boolean locked = owner.equals(resource2owner.get(resourceId));
        logger.debug("resource {} isLockedBy {} = {}", resourceId, owner, locked);

        return locked;
    }
}
