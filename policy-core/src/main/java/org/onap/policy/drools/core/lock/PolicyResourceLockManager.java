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
import java.util.function.Function;
import org.onap.policy.drools.core.lock.PolicyResourceLockFeatureAPI.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manager of resource locks. Checks for API implementers.
 */
public class PolicyResourceLockManager extends PlainLockManager {

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
    public LockRequestFuture lock(String resourceId, String owner, Callback callback) {
        if (resourceId == null) {
            throw makeNullArgException(MSG_NULL_RESOURCE_ID);
        }

        if (owner == null) {
            throw makeNullArgException(MSG_NULL_OWNER);
        }

        LockRequestFuture future = doIntercept(null, impl -> impl.beforeLock(resourceId, owner, callback));
        if (future != null) {
            return future;
        }

        /*
         * Note: no need to call afterLock(), as that is done by the ImplFuture when its
         * setLocked() method is called
         */

        return super.lock(resourceId, owner, callback);
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
     * Makes a future that invokes afterLock() when it's notified.
     */
    @Override
    protected ImplFuture makeFuture(String resourceId, String owner, Callback callback) {
        return new ImplFuture(resourceId, owner, callback);
    }


    /**
     * Lock Request Future that invokes the afterLock() method for each implementer, once
     * the lock has been acquired (or denied).
     */
    protected class ImplFuture extends MapFuture {

        /**
         * 
         * @param resourceId
         * @param owner
         * @param callback
         */
        public ImplFuture(String resourceId, String owner, Callback callback) {
            super(resourceId, owner, callback);
        }

        @Override
        public boolean setLocked(boolean locked) {
            boolean result = super.setLocked(locked);

            if (result) {
                // wasn't complete yet - tell the implementer
                doIntercept(false, impl -> impl.afterLock(getResourceId(), getOwner(), locked));
            }

            return result;
        }
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
