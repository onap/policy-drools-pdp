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

import static org.onap.policy.drools.core.lock.PolicyResourceLockFeatureAPI.LockResult.LOCK_ACQUIRED;
import static org.onap.policy.drools.core.lock.PolicyResourceLockFeatureAPI.LockResult.LOCK_ASYNC;
import static org.onap.policy.drools.core.lock.PolicyResourceLockFeatureAPI.LockResult.LOCK_DENIED;
import static org.onap.policy.drools.core.lock.PolicyResourceLockFeatureAPI.LockResult.LOCK_UNHANDLED;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import org.onap.policy.drools.core.lock.Lock.RemoveResult;
import org.onap.policy.drools.core.lock.PolicyResourceLockFeatureAPI.Callback;
import org.onap.policy.drools.core.lock.PolicyResourceLockFeatureAPI.LockResult;
import org.onap.policy.drools.utils.Pair;

/**
 * Plain lock manager. Does not redirect to lock feature implementers.
 */
public class PlainLockManager {

    /**
     * Maps a resource to the lock that holds it.
     */
    private ConcurrentHashMap<String, Lock<LockRequestFuture>> resource2lock = new ConcurrentHashMap<>();

    /**
     * 
     */
    public PlainLockManager() {
        super();
    }

    /**
     * Attempts to lock a resource. If the lock is not immediately available, and a
     * callback is provided, then it will invoke the callback once the lock is acquired by
     * the owner. The default method never returns <i>LOCK_UNHANDLED</i>.
     * 
     * @param resourceId
     * @param owner
     * @param callback function to invoke, if the requester wishes to wait for the lock to
     *        come available, {@code null} otherwise
     * @param future will be populated with the request future, if the return result is
     *        <i>LOCK_ASYNC</i>. This future may be used to check the status of and cancel
     *        the outstanding lock request
     * @return the result
     * @throws IllegalStateException if the owner has already requested a lock on the
     *         resource
     */
    public LockResult lock(String resourceId, String owner, Callback callback, Reference<Future<Boolean>> future) {

        if (resourceId == null) {
            throw new IllegalArgumentException("null resourceId");
        }

        if (owner == null) {
            throw new IllegalArgumentException("null owner");
        }


        // value to be returned
        Reference<LockResult> result = new Reference<>(LOCK_UNHANDLED);

        while (result.get() == LOCK_UNHANDLED) {

            /*
             * check the most likely case first, which is that the lock doesn't exist yet
             */
            resource2lock.computeIfAbsent(resourceId, xxx -> {
                result.set(LOCK_ACQUIRED);
                return new Lock<>(owner);
            });

            if (result.get() != LOCK_UNHANDLED) {
                break;
            }

            /*
             * in theory, someone already owns a lock on this resource - simply add the
             * new owner to the lock's queue
             */
            resource2lock.computeIfPresent(resourceId, (key, lock) -> {

                synchronized (lock) {

                    if (owner.equals(lock.getOwner())) {
                        // owner already owns the lock - can't get it again
                        result.set(LOCK_DENIED);

                    } else if (callback == null) {
                        // owner does not own the lock and no callback provided
                        result.set(LOCK_DENIED);

                    } else if (lock.add(owner, setFuture(future, makeFuture(resourceId, owner, callback)))) {
                        // owner was added to the lock's queue
                        result.set(LOCK_ASYNC);

                    } else {
                        // already in the queue - indicate an error
                        throw new IllegalStateException(owner + " has already requested a lock for " + resourceId);
                    }

                    return lock;
                }
            });
        }

        return result.get();
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

        /*
         * this starts with the given owner. However, if the owner is removed and the lock
         * falls to a new owner and the new owner has cancelled its lock request, then
         * this will be set to the new owner. This process will repeat until there is no
         * new owner
         */
        Reference<String> requester = new Reference<>(owner);

        // updated each time lock.removeRequester() is invoked
        Pair<String, LockRequestFuture> newOwner = new Pair<>(null, null);

        // we only set this the first time through the loop
        Reference<Boolean> result = new Reference<>(null);

        // result of call to removeRequester()
        Reference<RemoveResult> removeResult = new Reference<>(null);

        for (;;) {

            // remove the request from the lock, assuming it exists
            resource2lock.computeIfPresent(resourceId, (key, lock) -> {

                removeResult.set(lock.removeRequester(requester.get(), newOwner));

                return (removeResult.get() == RemoveResult.UNLOCKED ? null : lock);
            });


            // only update the result if it hasn't already been set
            if (result.get() == null) {
                result.set(removeResult.get() != RemoveResult.NOT_FOUND);
            }

            if (removeResult.get() != RemoveResult.RELOCKED) {
                return result.get();
            }


            // the lock has a new owner

            // signal the new owner's future that it now has the lock
            LockRequestFuture fut = newOwner.second();
            if (fut.setLocked()) {
                // the notification was accepted
                return result.get();
            }

            // the new owner had already cancelled before it got the lock, thus we have to
            // remove the new owner from the lock
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

        Lock<LockRequestFuture> lock = resource2lock.get(resourceId);
        if (lock == null) {
            return false;
        }

        synchronized (lock) {
            return owner.equals(lock.getOwner());
        }
    }

    /**
     * Sets the future reference to the given future.
     * 
     * @param ref where to place the future
     * @param future
     * @return the future
     * @throws NullPointerException if the future is {@code null}
     */
    private LockRequestFuture setFuture(Reference<Future<Boolean>> ref, LockRequestFuture future) {
        if (future == null) {
            throw new NullPointerException("null future");
        }

        ref.set(future);
        return future;
    }

    /**
     * Makes a new future. Subclasses may override this.
     * 
     * @param resourceId
     * @param owner
     * @param callback
     * @return a new future
     */
    protected LockRequestFuture makeFuture(String resourceId, String owner, Callback callback) {
        return new LockRequestFuture(resourceId, owner, callback);
    }

    /**
     * Lock Request Future that unlocks the resource, if the future is cancelled.
     */
    protected class MapFuture extends LockRequestFuture {

        /**
         * 
         * @param resourceId
         * @param owner
         * @param callback
         */
        public MapFuture(String resourceId, String owner, Callback callback) {
            super(resourceId, owner, callback);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(mayInterruptIfRunning);

            if (cancelled) {
                unlock(getResourceId(), getOwner());
            }

            return cancelled;
        }

    }

}
