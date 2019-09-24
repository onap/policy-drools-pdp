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
 * Lock held on a resource.
 */
public interface Lock {

    /**
     * Frees/release the lock.
     *
     * <p/>
     * Note: client code may choose to invoke this method <i>before</i> the lock has been
     * granted.
     *
     * @return {@code true} if the request was accepted, {@code false} if the lock is
     *         unavailable
     */
    boolean free();

    /**
     * Determines if the lock is active.
     *
     * @return {@code true} if the lock is <b>ACTIVE</b>, {@code false} otherwise
     */
    boolean isActive();

    /**
     * Determines if the lock is unavailable. Once a lock object becomes unavailable, it
     * will never become active again.
     *
     * @return {@code true} if the lock is <b>UNAVAILABLE</b>, {@code false} otherwise
     */
    boolean isUnavailable();

    /**
     * Determines if this object is waiting for a lock to be granted or denied. This
     * applies when the lock is first created, or after {@link #extend(int, LockCallback)}
     * has been invoked.
     *
     * @return {@code true} if the lock is <b>WAITING</b>, {@code false} otherwise
     */
    boolean isWaiting();

    /**
     * Gets the ID of the resource to which the lock applies.
     *
     * @return the ID of the resource to which the lock applies
     */
    String getResourceId();

    /**
     * Gets the lock's owner key.
     *
     * @return the lock's owner key
     */
    String getOwnerKey();

    /**
     * Extends a lock an additional amount of time from now. The callback will always be
     * invoked, and may be invoked <i>before</i> this method returns.
     *
     * @param holdSec the additional amount of time to hold the lock, in seconds
     * @param callback callback to be invoked when the extension completes
     */
    void extend(int holdSec, LockCallback callback);
}
