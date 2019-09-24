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
     * Default provider, used when no other can be found.
     */
    private static final PolicyResourceLockFeatureApi DEFAULT_PROVIDER = new AlwaysFailProvider();

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
     * @return the lock manager singleton
     */
    protected static PolicyResourceLockManager getInstance() {
        return Singleton.instance;
    }

    /**
     * Gets the feature provider singleton.
     *
     * @return the feature provider. If no feature can be found, then a default feature is
     *         returned, whose methods always fail
     */
    public static PolicyResourceLockFeatureApi getProvider() {
        return Singleton.instance.getProvider2();
    }

    /**
     * Gets the feature provider associated with this manager.
     *
     * @return the feature provider. If no feature can be found, then a default feature is
     *         returned, whose methods always fail
     */
    protected PolicyResourceLockFeatureApi getProvider2() {
        PolicyResourceLockFeatureApi feature = factory.get();
        if (feature != null) {
            return feature;
        }

        String featureName = "unknown";

        try {
            /*
             * nothing cached - find a provider that's enabled. If the call to enabled()
             * throws an exception, then we purposely stop checking other providers until
             * all of them have had a chance to initialize
             */
            for (PolicyResourceLockFeatureApi impl : getProviders()) {
                featureName = impl.getClass().getName();
                if (!impl.enabled()) {
                    continue;
                }

                factory.compareAndSet(null, impl);

                feature = factory.get();
                logger.info("{}: chose lock feature {}", this, feature.getClass().getName());

                return feature;
            }

        } catch (RuntimeException e) {
            logger.error("{}: lock feature {} failure because of {}", this, featureName, e.getMessage(), e);
        }

        return DEFAULT_PROVIDER;
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
        return new OrderedServiceImpl<>(PolicyResourceLockFeatureApi.class).getList();
    }
}
