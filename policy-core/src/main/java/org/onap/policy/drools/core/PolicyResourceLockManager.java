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

package org.onap.policy.drools.core;

import static org.onap.policy.drools.core.PolicyResourceLockFeatureAPI.BeforeLockResult.ACQUIRED;
import static org.onap.policy.drools.core.PolicyResourceLockFeatureAPI.BeforeLockResult.ASYNC;
import static org.onap.policy.drools.core.PolicyResourceLockFeatureAPI.BeforeLockResult.DENIED;
import static org.onap.policy.drools.core.PolicyResourceLockFeatureAPI.BeforeLockResult.NOT_HANDLED;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import org.onap.policy.drools.core.PolicyResourceLockFeatureAPI.BeforeLockResult;
import org.onap.policy.drools.core.PolicyResourceLockFeatureAPI.Callback;
import org.onap.policy.drools.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manager of resource locks.
 */
public class PolicyResourceLockManager {

    private static Logger logger = LoggerFactory.getLogger(PolicyResourceLockManager.class);

    /**
     * Actual lock manager.
     */
    private static Manager mgr = new Manager();

    /**
     * Not used.
     */
    private PolicyResourceLockManager() {
        super();
    }

    /**
     * Attempts to lock a resource. If the lock is not immediately available, and a
     * callback is provided, then it will invoke the callback once the lock is acquired by
     * the owner.
     * 
     * @param resourceId
     * @param owner
     * @param callback function to invoke, if the requester wishes to wait for the lock to
     *        come available, {@code null} otherwise
     * @return
     *         <dl>
     *         <dt>true</dt>
     *         <dd>if the lock has been acquired</dd>
     *         <dt>false></dt>
     *         <dd>if the lock could not be acquired</dd>
     *         <dt>null</dt>
     *         <dd>if the lock is not immediately available and a callback was
     *         provided</dd>
     *         </dl>
     * @throws IllegalArgumentException if the resourceId or owner is {@code null}
     * @throws IllegalStateException if the owner has already requested a lock on the
     *         resource
     */
    public static Boolean lock(String resourceId, String owner, Callback callback) {
        if (resourceId == null) {
            throw new IllegalArgumentException("null resourceId");
        }

        if (owner == null) {
            throw new IllegalArgumentException("null owner");
        }

        BeforeLockResult result = doIntercept(impl -> {
            BeforeLockResult res = impl.beforeLock(resourceId, owner, callback);
            return (res == null || res == NOT_HANDLED ? null : res);
        });

        // check result from interceptor
        if (result != null) {
            switch (result) {
                case ACQUIRED:
                    return true;
                case DENIED:
                    return false;
                case ASYNC:
                    return null;
                default:
                    break;
            }
        }

        CallbackWrapper cbwrap = (callback == null ? null : new CallbackWrapper(resourceId, owner, callback));

        Boolean res = mgr.lock(resourceId, owner, cbwrap);

        if (res == null) {
            // asynchronous - we'll invoke afterLock() during the callback
            return null;
        }

        Boolean after = doIntercept(impl -> impl.afterLock(resourceId, owner, res));

        return (after == Boolean.TRUE || res);
    }

    /**
     * Unlocks a resource.
     * 
     * @param resourceId
     * @param owner
     * @return {@code true} if unlocked, {@code false} if it was not locked by the given
     *         owner
     * @throws IllegalArgumentException if the resourceId or owner is {@code null}
     */
    public boolean unlock(String resourceId, String owner) {
        if (resourceId == null) {
            throw new IllegalArgumentException("null resourceId");
        }

        if (owner == null) {
            throw new IllegalArgumentException("null owner");
        }

        Boolean result = doIntercept(impl -> impl.beforeUnlock(resourceId, owner));
        if (result != null) {
            return result;
        }

        boolean unlocked = mgr.unlock(resourceId, owner);

        result = doIntercept(impl -> impl.afterUnlock(resourceId, owner, unlocked));

        return (result == Boolean.TRUE || unlocked);
    }

    /**
     * Determines if a resource is locked by anyone.
     * 
     * @param resourceId
     * @return {@code true} if the resource is locked, {@code false} otherwise
     */
    public boolean isLocked(String resourceId) {
        if (resourceId == null) {
            throw new IllegalArgumentException("null resourceId");
        }

        Boolean result = doIntercept(impl -> impl.beforeIsLocked(resourceId));
        if (result != null) {
            return result;
        }

        return mgr.isLocked(resourceId);
    }

    /**
     * Determines if a resource is locked by a particular owner.
     * 
     * @param resourceId
     * @param owner
     * @return {@code true} if the resource is locked, {@code false} otherwise
     */
    public boolean isLockedBy(String resourceId, String owner) {
        if (resourceId == null) {
            throw new IllegalArgumentException("null resourceId");
        }

        if (owner == null) {
            throw new IllegalArgumentException("null owner");
        }

        Boolean result = doIntercept(impl -> impl.beforeIsLockedBy(resourceId, owner));
        if (result != null) {
            return result;
        }

        return mgr.isLockedBy(resourceId, owner);
    }

    /**
     * Applies a function to each implementer of the lock feature. Returns as soon as one
     * of them returns a non-null value.
     * 
     * @param func function to be applied to the implementers
     * @return first non-null value returned by an implementer, {@code null} if they all
     *         returned {@code null}
     */
    private static <T> T doIntercept(Function<PolicyResourceLockFeatureAPI, T> func) {

        for (PolicyResourceLockFeatureAPI impl : PolicyResourceLockFeatureAPI.impl.getList()) {
            try {
                T result = func.apply(impl);
                if (result != null) {
                    return result;
                }

            } catch (RuntimeException e) {
                logger.warn("lock feature {} threw an exception", impl, e);
            }
        }

        return null;
    }

    /**
     * Just manages resource locks; does <i>not</i> worry about implementers.
     */
    public static class Manager {

        /**
         * Maps a resource to the lock that holds it.
         */
        private ConcurrentHashMap<String, Lock> resource2lock = new ConcurrentHashMap<>();

        /**
         * Attempts to lock a resource. If the lock is not immediately available, and a
         * callback is provided, then it will invoke the callback once the lock is
         * acquired by the owner.
         * 
         * @param resourceId
         * @param owner
         * @param callback function to invoke, if the requester wishes to wait for the
         *        lock to come available, {@code null} otherwise
         * @return
         *         <dl>
         *         <dt>true</dt>
         *         <dd>if the lock has been acquired</dd>
         *         <dt>false></dt>
         *         <dd>if the lock could not be acquired</dd>
         *         <dt>null</dt>
         *         <dd>if the lock is not immediately available and a callback was
         *         provided</dd>
         *         </dl>
         * @throws IllegalStateException if the owner has already requested a lock on the
         *         resource
         */
        public Boolean lock(String resourceId, String owner, Callback callback) {
            if (resourceId == null) {
                throw new IllegalArgumentException("null resourceId");
            }

            if (owner == null) {
                throw new IllegalArgumentException("null owner");
            }


            // value to be returned
            Reference<BeforeLockResult> result = new Reference<>(NOT_HANDLED);

            while (result.get() == NOT_HANDLED) {

                /*
                 * check the most likely case first, which is that the lock doesn't exist
                 * yet
                 */
                resource2lock.computeIfAbsent(resourceId, xxx -> {
                    result.set(ACQUIRED);
                    return new Lock(owner);
                });

                if (result.get() != NOT_HANDLED) {
                    break;
                }

                /*
                 * in theory, someone already owns a lock on this resource - simply add
                 * the new owner to the lock's queue
                 */
                resource2lock.computeIfPresent(resourceId, (key, lock) -> {

                    if (owner.equals(lock.getOwner())) {
                        // owner already owns the lock - can't get it again
                        result.set(DENIED);

                    } else if (callback == null) {
                        // owner does not own the lock and no callback provided
                        result.set(DENIED);

                    } else if (lock.add(owner, callback)) {
                        // owner was added to the lock's queue
                        result.set(ASYNC);

                    } else {
                        // already in the queue - indicate an error
                        throw new IllegalStateException(owner + " has already requested a lock for " + resourceId);
                    }

                    return lock;
                });
            }


            switch (result.get()) {
                case ACQUIRED:
                    return true;
                case ASYNC:
                    return null;
                default:
                    // should only be DENIED
                    return false;
            }
        }

        /**
         * Unlocks a resource.
         * 
         * @param resourceId
         * @param owner
         * @return {@code true} if unlocked, {@code false} if it was not locked by the
         *         given owner
         * @throws IllegalArgumentException if the resourceId or owner is {@code null}
         */
        public boolean unlock(String resourceId, String owner) {
            if (resourceId == null) {
                throw new IllegalArgumentException("null resourceId");
            }

            if (owner == null) {
                throw new IllegalArgumentException("null owner");
            }

            /*
             * this starts with the given owner. However, if the owner is removed and the
             * lock falls to a new owner and the new owner has cancelled its lock request,
             * then this will be set to the new owner. This process will repeat until
             * there is no new owner
             */
            Reference<String> requester = new Reference<>(owner);

            // updated each time lock.removeRequester() is invoked
            Pair<String, Callback> newOwner = new Pair<>(null, null);

            Reference<Boolean> result = new Reference<>(null);

            for (;;) {

                // remove the request from the lock, assuming it exists
                resource2lock.computeIfPresent(resourceId, (key, lock) -> {

                    Boolean val = lock.removeRequester(requester.get(), newOwner);

                    // if the requester had been the owner, then it was successfully
                    // unlocked - arrange to return "true". Otherwise, set it to false
                    if (result.get() == null) {
                        result.set(val == null);
                    }

                    // if the requester was the owner AND we don't have a new owner, then
                    // discard the lock
                    return (val == null && newOwner.first() == null ? null : lock);
                });


                if (newOwner.first() == null) {
                    // removed the lock without changing owners (or the lock didn't even
                    // exist). Return true if previously set to true, return false
                    // otherwise
                    return (result.get() == Boolean.TRUE);
                }


                /*
                 * lock has a new owner
                 */

                // notify the new owner's callback that it now has the lock
                Callback cb = newOwner.second();
                cb.run();

                if (!cb.isCancelled()) {
                    // callback accepted the notification - all done for now
                    return (result.get() == Boolean.TRUE);
                }

                // the new owner cancelled before we invoked run() so we have to remove
                // the new owner from the lock
                requester.set(newOwner.first());
            }
        }

        /**
         * Determines if a resource is locked by anyone.
         * 
         * @param resourceId
         * @return {@code true} if the resource is locked, {@code false} otherwise
         */
        public boolean isLocked(String resourceId) {
            if (resourceId == null) {
                throw new IllegalArgumentException("null resourceId");
            }

            return resource2lock.contains(resourceId);
        }

        /**
         * Determines if a resource is locked by a particular owner.
         * 
         * @param resourceId
         * @param owner
         * @return {@code true} if the resource is locked, {@code false} otherwise
         */
        public boolean isLockedBy(String resourceId, String owner) {
            if (resourceId == null) {
                throw new IllegalArgumentException("null resourceId");
            }

            if (owner == null) {
                throw new IllegalArgumentException("null owner");
            }

            Lock lock = resource2lock.get(resourceId);
            return (lock != null && owner.equals(lock.getOwner()));
        }
    }

    /**
     * Lock that is held for a resource. All methods are thread-safe.
     */
    public static class Lock {
        /**
         * The last owner to grab the lock, never {@code null}.
         */
        private String owner;

        /**
         * Requesters waiting to get the lock. Maps the requester (i.e., owner for which
         * the request is being made) to the requester's callback. Uses a Linked map so
         * that the order of the requesters is maintained.
         */
        private LinkedHashMap<String, Callback> requester2callback = new LinkedHashMap<>(5);

        /**
         * 
         * @param owner
         */
        public Lock(String owner) {
            this.owner = owner;
        }

        protected String getOwner() {
            synchronized (requester2callback) {
                return owner;
            }
        }

        /**
         * Adds a new requester to the queue of requesters.
         * 
         * @param requester
         * @param callback
         * @return {@code true} if the requester was added, {@code false} if it already
         *         owns the lock or is already in the queue
         */
        public boolean add(String requester, Callback callback) {
            synchronized (requester2callback) {
                if (requester.equals(owner)) {
                    // requester already owns the lock
                    return false;
                }

                Callback prev = requester2callback.putIfAbsent(requester, callback);

                // if there's a previous value, then that means this requester is already
                // waiting for a lock on this resource. In that case, we return false
                return (prev == null);
            }
        }

        /**
         * Removes a requester from the lock. The requester may currently own the lock, or
         * it may be in the queue waiting for the lock. If the request was the owner, then
         * newOwner is set to the new owner and the new owner's callback. Otherwise, both
         * fields of the newOwner are set to {@code null}. Note: this method does
         * <i>not</i> invoke the new owner's callback method.
         * 
         * @param requester
         * @param newOwner where to place the new owner info
         * @return
         *         <dl>
         *         <dt>true</dt>
         *         <dd>if the requester was removed</dd>
         *         <dt>false></dt>
         *         <dd>if the requester is not the owner and is not in the queue</dd>
         *         <dt>null</dt>
         *         <dd>if the requester was the owner</dd>
         *         </dl>
         */
        public Boolean removeRequester(String requester, Pair<String, Callback> newOwner) {

            newOwner.first(null);
            newOwner.second(null);

            synchronized (requester2callback) {

                if (!requester.equals(owner)) {
                    // requester does not currently own the lock - remove it from the
                    // queue
                    Callback ent = requester2callback.remove(requester);

                    // if there was an entry in the queue, then return true to indicate
                    // that it was removed. Otherwise, return false
                    return (ent != null);
                }

                /*
                 * requester was the owner - find something to take over
                 */
                Iterator<Entry<String, Callback>> it = requester2callback.entrySet().iterator();
                if (it.hasNext()) {
                    // there's another requester to take over
                    Entry<String, Callback> ent = it.next();
                    it.remove();

                    owner = ent.getKey();

                    newOwner.first(owner);
                    newOwner.second(ent.getValue());
                }

                /*
                 * else no one to take over - newOwner is already null, so nothing more to
                 * do
                 */

                // indicate that the requester was the owner
                return null;
            }
        }
    }

    /**
     * Wrapper for a Callback.
     */
    private static class CallbackWrapper implements Callback {
        private final String resourceId;
        private final String owner;
        private final Callback callback;

        /**
         * 
         * @param callback item to be wrapped
         */
        public CallbackWrapper(String resourceId, String owner, Callback callback) {
            this.resourceId = resourceId;
            this.owner = owner;
            this.callback = callback;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            throw new UnsupportedOperationException("cancel");
        }

        @Override
        public boolean isCancelled() {
            return callback.isCancelled();
        }

        @Override
        public boolean isDone() {
            throw new UnsupportedOperationException("isDone");
        }

        @Override
        public Boolean get() throws InterruptedException, ExecutionException {
            throw new UnsupportedOperationException("get");
        }

        @Override
        public Boolean get(long timeout, TimeUnit unit)
                        throws InterruptedException, ExecutionException, TimeoutException {

            throw new UnsupportedOperationException("get");
        }

        @Override
        public void run() {
            callback.run();

            if (!callback.isCancelled()) {
                doIntercept(impl -> impl.afterLock(resourceId, owner, true));
            }
        }
    }

    /**
     * Reference to an object. Used within functional methods.
     * 
     * @param <T> type of object contained within the reference
     */
    public static class Reference<T> {
        private T value;

        /**
         * 
         * @param value
         */
        public Reference(T value) {
            this.value = value;
        }

        /**
         * @return the current value
         */
        public final T get() {
            return value;
        }

        /**
         * Sets the reference to point to a new value.
         * 
         * @param newValue
         */
        public final void set(T newValue) {
            this.value = newValue;
        }
    }
}
