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
import java.util.List;
import java.util.concurrent.Future;
import java.util.function.Function;
import org.onap.policy.drools.core.lock.PolicyResourceLockFeatureAPI.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manager of resource locks. Checks for API implementers.
 */
public class PolicyResourceLockManager extends SimpleLockManager {

    private static Logger logger = LoggerFactory.getLogger(PolicyResourceLockManager.class);

    /**
     * Used to access various objects.
     */
    public static Factory factory = new Factory();

    /**
     * Used by junit tests.
     */
    protected PolicyResourceLockManager() {
        super();
    }

    /**
     * 
     * @return the manager singleton
     */
    public static PolicyResourceLockManager getInstance() {
        return Singleton.instance;
    }

    protected static Factory getFactory() {
        return factory;
    }

    /**
     * Sets the factory to be used by junit tests.
     * 
     * @param factory
     */
    protected static void setFactory(Factory factory) {
        PolicyResourceLockManager.factory = factory;
    }

    @Override
    public Future<Boolean> lock(String resourceId, String owner, Callback callback) {
        if (resourceId == null) {
            throw makeNullArgException(MSG_NULL_RESOURCE_ID);
        }

        if (owner == null) {
            throw makeNullArgException(MSG_NULL_OWNER);
        }

        Future<Boolean> result = doIntercept(null, impl -> impl.beforeLock(resourceId, owner, callback));
        if (result != null) {
            return result;
        }

        // implementer didn't do the work - use superclass
        result = super.lock(resourceId, owner, callback);

        boolean locked = ((LockRequestFuture) result).isLocked();

        doIntercept(false, impl -> impl.afterLock(resourceId, owner, locked));

        return result;
    }

    @Override
    public boolean unlock(String resourceId, String owner) {
        if (resourceId == null) {
            throw makeNullArgException(MSG_NULL_RESOURCE_ID);
        }

        if (owner == null) {
            throw makeNullArgException(MSG_NULL_OWNER);
        }

        Boolean result = doIntercept(null, impl -> impl.beforeUnlock(resourceId, owner));
        if (result != null) {
            return result;
        }

        // implementer didn't do the work - use superclass
        boolean unlocked = super.unlock(resourceId, owner);

        doIntercept(false, impl -> impl.afterUnlock(resourceId, owner, unlocked));

        return unlocked;
    }

    /**
     * 
     * @throws IllegalArgumentException if the resourceId is {@code null}
     */
    @Override
    public boolean isLocked(String resourceId) {
        if (resourceId == null) {
            throw makeNullArgException(MSG_NULL_RESOURCE_ID);
        }

        Boolean result = doIntercept(null, impl -> impl.beforeIsLocked(resourceId));
        if (result != null) {
            return result;
        }

        return super.isLocked(resourceId);
    }

    /**
     * 
     * @throws IllegalArgumentException if the resourceId or owner is {@code null}
     */
    @Override
    public boolean isLockedBy(String resourceId, String owner) {
        if (resourceId == null) {
            throw makeNullArgException(MSG_NULL_RESOURCE_ID);
        }

        if (owner == null) {
            throw makeNullArgException(MSG_NULL_OWNER);
        }

        Boolean result = doIntercept(null, impl -> impl.beforeIsLockedBy(resourceId, owner));
        if (result != null) {
            return result;
        }

        return super.isLockedBy(resourceId, owner);
    }

    /**
     * Applies a function to each implementer of the lock feature. Returns as soon as one
     * of them returns a non-null value.
     * 
     * @param continueValue if the implementer returns this value, then it continues to
     *        check addition implementers
     * @param func function to be applied to the implementers
     * @return first non-null value returned by an implementer, <i>continueValue<i/> if
     *         they all returned <i>continueValue<i/>
     */
    public static <T> T doIntercept(T continueValue, Function<PolicyResourceLockFeatureAPI, T> func) {

        for (PolicyResourceLockFeatureAPI impl : factory.getImplementers()) {
            try {
                T result = func.apply(impl);
                if (result != continueValue) {
                    return result;
                }

            } catch (RuntimeException e) {
                logger.warn("lock feature {} threw an exception", impl, e);
            }
        }

        return continueValue;
    }

    /**
     * Initialization-on-demand holder idiom.
     */
    private static class Singleton {
        private static final PolicyResourceLockManager instance = new PolicyResourceLockManager();
    }

    /**
     * Used to access various objects.
     */
    public static class Factory {

        /**
         * 
         * @return the list of feature implementers
         */
        public List<PolicyResourceLockFeatureAPI> getImplementers() {
            return PolicyResourceLockFeatureAPI.impl.getList();
        }
    }
}
