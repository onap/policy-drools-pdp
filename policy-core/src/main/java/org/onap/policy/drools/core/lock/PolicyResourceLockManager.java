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
import java.util.concurrent.atomic.AtomicReference;
import org.onap.policy.common.utils.services.OrderedServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manager of resource locks. Checks for API implementers.
 */
public class PolicyResourceLockManager {
    private static Logger logger = LoggerFactory.getLogger(PolicyResourceLockManager.class);

    /**
     * 'FeatureAPI.impl.getList()' returns an ordered list of objects implementing the
     * 'FeatureAPI' interface.
     */
    private static final OrderedServiceImpl<PolicyResourceLockFeatureApi> providers =
                    new OrderedServiceImpl<>(PolicyResourceLockFeatureApi.class);

    /**
     * Feature implementation that was discovered.
     */
    private AtomicReference<PolicyResourceLockFeatureApi> factory = new AtomicReference<>();


    /**
     * Constructs the object.
     */
    public PolicyResourceLockManager() {
        super();
    }

    /**
     * Gets the lock manager singleton.
     *
     * @return the lock manager
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
     * Note: the callback may be invoked before this method returns.
     *
     * <p/>
     * If an implementation throws an exception, or if no implementation is available,
     * then the {@link LockCallback#lockUnavailable(Lock)} method is called and a
     * {@link Lock} is returned that never succeeds.
     *
     * @param resourceId identifier of the resource to be locked
     * @param ownerInfo information identifying the owner requesting the lock
     * @param callback callback to be invoked once the lock is granted, or subsequently
     *        lost; must not be {@code null}
     * @param holdSec amount of time, in seconds, for which the lock should be held once
     *        it has been granted, after which it will automatically be released
     * @param waitForLock {@code true} to wait for the lock, if it is currently locked,
     *        {@code false} otherwise
     * @return a new lock
     */
    public Lock lock(String resourceId, Object ownerInfo, LockCallback callback, int holdSec, boolean waitForLock) {

        String featureName = "unknown";

        try {
            // first try the cached factory
            PolicyResourceLockFeatureApi feature = factory.get();
            if (feature != null) {
                featureName = feature.getClass().getName();
                return feature.lock(resourceId, ownerInfo, callback, holdSec, waitForLock);
            }

            // nothing cached - find an implementer
            for (PolicyResourceLockFeatureApi impl : getProviders()) {
                featureName = impl.getClass().getName();
                if (!impl.enabled()) {
                    continue;
                }

                logger.info("{}: choosing feature {}", this, featureName);

                factory.compareAndSet(null, impl);

                feature = factory.get();
                featureName = feature.getClass().getName();
                return feature.lock(resourceId, ownerInfo, callback, holdSec, waitForLock);
            }

        } catch (RuntimeException e) {
            logger.error("{}: feature {} lock() failure because of {}", this, featureName, e.getMessage(), e);
        }


        // no implementer available at this time - return a lock that always fails

        return new LockImpl(Lock.State.UNAVAILABLE, resourceId, ownerInfo, null, callback, holdSec).notifyUnavailable();
    }

    // these may be overridden by junit tests

    protected List<PolicyResourceLockFeatureApi> getProviders() {
        return providers.getList();
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
}
