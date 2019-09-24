/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018-2019 AT&T Intellectual Property. All rights reserved.
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

import java.util.List;
import org.onap.policy.common.utils.services.OrderedServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manager of resource locks. Checks for API implementers.
 */
public class PolicyResourceLockManager {
    private static Logger logger = LoggerFactory.getLogger(PolicyResourceLockManager.class);

    /**
     * Possible providers.
     */
    private static List<PolicyResourceLockFeatureApi> providers =
                    new OrderedServiceImpl<>(PolicyResourceLockFeatureApi.class).getList();


    /**
     * Constructs the object.
     */
    public PolicyResourceLockManager() {
        super();
    }

    /**
     * Gets the lock manager singleton.
     *
     * @return the lock manager singleton
     */
    public static PolicyResourceLockManager getInstance() {
        return Singleton.instance;
    }

    /**
     * Requests a lock on a resource. Typically, the lock is not immediately granted,
     * though a "lock" object is always returned. Once the lock has been granted (or
     * denied), the callback will be invoked to indicate the result.
     *
     * <p/>
     * Notes:
     * <dl>
     * <li>The callback may be invoked <i>before</i> this method returns</li>
     * <li>The implementation need not honor waitForLock={@code true}</li>
     * </dl>
     *
     * @param resourceId identifier of the resource to be locked
     * @param ownerInfo information identifying the owner requesting the lock
     * @param holdSec amount of time, in seconds, for which the lock should be held once
     *        it has been granted, after which it will automatically be released
     * @param callback callback to be invoked once the lock is granted, or subsequently
     *        lost; must not be {@code null}
     * @param waitForLock {@code true} to wait for the lock, if it is currently locked,
     *        {@code false} otherwise
     * @return a new lock
     */
    public Lock createLock(String resourceId, Object ownerInfo, int holdSec, LockCallback callback,
                    boolean waitForLock) {

        // see if any provider handles the lock
        for (PolicyResourceLockFeatureApi feature : getProviders()) {
            try {
                Lock lock = feature.beforeCreateLock(resourceId, ownerInfo, holdSec, callback, waitForLock);
                if (lock != null) {
                    return lock;
                }
            } catch (RuntimeException e) {
                logger.error("{}: lock feature {} failure because of {}", this, feature.getName(), e.getMessage(), e);
                break;
            }
        }

        // no provider handled it - return a lock that always fails
        logger.error("{}: no lock available", this);
        AlwaysFailLock lock = new AlwaysFailLock(resourceId, ownerInfo, null, holdSec, callback);

        // see if any provider handled the lock
        for (PolicyResourceLockFeatureApi feature : getProviders()) {
            try {
                if (feature.afterCreateLock(lock, holdSec, callback, waitForLock)) {
                    return lock;
                }
            } catch (RuntimeException e) {
                logger.error("{}: lock feature {} failure because of {}", this, feature.getName(), e.getMessage(), e);
            }
        }

        lock.notifyUnavailable();
        return lock;
    }

    /**
     * Initialization-on-demand holder idiom.
     */
    private static class Singleton {

        private static final PolicyResourceLockManager instance = new PolicyResourceLockManager();

        /**
         * Not invoked.
         */
        private Singleton() {
            super();
        }
    }

    // these may be overridden by junit tests

    protected List<PolicyResourceLockFeatureApi> getProviders() {
        return providers;
    }
}
