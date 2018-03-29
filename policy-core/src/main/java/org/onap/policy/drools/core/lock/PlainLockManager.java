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

import static org.onap.policy.drools.core.lock.Lock.RemoveResult.NOT_FOUND;
import static org.onap.policy.drools.core.lock.Lock.RemoveResult.RELOCKED;
import static org.onap.policy.drools.core.lock.Lock.RemoveResult.UNLOCKED;
import static org.onap.policy.drools.core.lock.LockRequestFuture.MSG_NULL_OWNER;
import static org.onap.policy.drools.core.lock.LockRequestFuture.MSG_NULL_RESOURCE_ID;
import static org.onap.policy.drools.core.lock.LockRequestFuture.makeNullArgException;
import java.util.concurrent.ConcurrentHashMap;
import org.onap.policy.drools.core.lock.Lock.RemoveResult;
import org.onap.policy.drools.core.lock.PolicyResourceLockFeatureAPI.Callback;
import org.onap.policy.drools.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plain lock manager. Does not redirect to lock feature implementers.
 */
public class PlainLockManager {

    protected static Logger logger = LoggerFactory.getLogger(PlainLockManager.class);

    /**
     * Maps a resource to the lock that holds it.
     */
    private ConcurrentHashMap<String, Lock<LockRequestFuture>> resource2lock = new ConcurrentHashMap<>();

    /**
     * Lock state, used while attempting to acquire a lock.
     */
    private enum LockState {
        WAITING, ACQUIRED, DENIED, ASYNC
    }

    /**
     * 
     */
    public PlainLockManager() {
        super();
    }

    // TODO: for ease of use by clients, should we always invoke the callback, even if
    // the response is synchronous?

    /**
     * Attempts to lock a resource. If a callback is provided, and the lock cannot be
     * immediately acquired (or denied), then the callback will be invoked once the lock
     * is acquired.
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
     * @throws IllegalStateException if the owner has already requested a lock on the
     *         resource
     */
    public LockRequestFuture lock(String resourceId, String owner, Callback callback) {

        if (resourceId == null) {
            throw makeNullArgException(MSG_NULL_RESOURCE_ID);
        }

        if (owner == null) {
            throw makeNullArgException(MSG_NULL_OWNER);
        }


        // value to be returned
        LockRequestFuture future = makeFuture(resourceId, owner, callback);

        /*
         * loop until we either create a Lock or we add it to an existing Lock's queue.
         * Typically, this loop will only be executed once. However, there are race
         * conditions that may cause it to be executed a couple of times.
         */
        Reference<LockState> state = new Reference<>(LockState.WAITING);

        while (state.get() == LockState.WAITING) {

            /*
             * check the most likely case first, which is that the lock doesn't exist yet
             */
            resource2lock.computeIfAbsent(resourceId, xxx -> {
                state.set(LockState.ACQUIRED);
                return new Lock<>(owner);
            });

            if (state.get() != LockState.WAITING) {
                break;
            }

            /*
             * in theory, someone already owns a lock on this resource - simply add the
             * new owner to the lock's queue
             */

            // exception message goes here
            Reference<String> exmsg = new Reference<>(null);

            resource2lock.computeIfPresent(resourceId, (key, lock) -> {

                synchronized (lock) {

                    if (owner.equals(lock.getOwner())) {
                        // owner already owns the lock - can't get it again
                        exmsg.set(" already holds a lock for ");

                    } else if (callback == null) {
                        // owner does not own the lock and no callback provided
                        state.set(LockState.DENIED);

                    } else if (lock.add(owner, future)) {
                        // owner was added to the lock's queue
                        state.set(LockState.ASYNC);

                    } else {
                        // already in the queue - indicate an error
                        exmsg.set(" has already requested a lock for ");
                    }

                    return lock;
                }
            });

            if (exmsg.get() != null) {
                throw new IllegalStateException(owner + exmsg.get() + resourceId);
            }
        }

        // if the lock has been acquired or denied, then set the status
        switch (state.get()) {
            case ACQUIRED:
                future.setLocked(true);
                break;
            case DENIED:
                future.setLocked(false);
                break;
            default:
                break;
        }

        logger.info("lock {} for resource {} owner {}", state.get(), resourceId, owner);

        return future;
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

        /*
         * this starts with the given owner. However, if the owner is removed, AND the
         * lock falls to a new owner, AND the new owner has cancelled its lock request,
         * then this will be set to the new owner. This process will repeat until there is
         * no new owner
         */
        Reference<String> requester = new Reference<>(owner);

        // updated each time lock.removeRequester() is invoked
        Pair<String, LockRequestFuture> newOwner = new Pair<>(null, null);

        // we only set this the first time through the loop
        Reference<Boolean> result = new Reference<>(null);

        // result of call to removeRequester()
        Reference<RemoveResult> removeResult = new Reference<>(null);

        for (;;) {
            // assume it doesn't exist, until proven otherwise
            removeResult.set(NOT_FOUND);

            // remove the requester from the lock, assuming it exists
            resource2lock.computeIfPresent(resourceId, (key, lock) -> {
                synchronized (lock) {
                    removeResult.set(lock.removeRequester(requester.get(), newOwner));
                    return (removeResult.get() == UNLOCKED ? null : lock);
                }
            });


            // only update the result if it hasn't already been set
            if (result.get() == null) {
                // set to true if it had been the owner
                result.set(wasOwner(removeResult.get()));
                logger.info("unlock resource {} owner {} = {}", resourceId, owner, result.get());
            }

            // if we don't have a new owner, then we're done
            if (removeResult.get() != RELOCKED) {
                return result.get();
            }


            // the lock has a new owner

            // signal the new owner's future that it now has the lock
            LockRequestFuture fut = newOwner.second();
            if (fut.setLocked(true)) {
                // the notification was accepted
                logger.info("resource {} now locked by {}", resourceId, newOwner.first());
                fut.invokeCallback();
                return result.get();
            }

            // the new owner had already cancelled before it got the lock, thus we have to
            // remove the new owner from the lock
            requester.set(newOwner.first());

            logger.info("lock cancelled for resource {} owner {}", resourceId, newOwner.first());
        }
    }

    /**
     * Determines, based on the RemoveResult, if the requester had been the previous owner
     * of the lock.
     * 
     * @param removeResult
     * @return {@code true} if the requester had been the previous owner, {@code false}
     *         otherwise
     */
    protected boolean wasOwner(RemoveResult removeResult) {
        return (removeResult == RELOCKED || removeResult == UNLOCKED);
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

        boolean result = resource2lock.containsKey(resourceId);

        logger.debug("resource {} isLocked = {}", resourceId, result);

        return result;
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

        Lock<LockRequestFuture> lock = resource2lock.get(resourceId);
        if (lock == null) {
            logger.debug("resource {} isLockedBy {} = false", resourceId, owner);
            return false;
        }


        boolean result;

        synchronized (lock) {
            result = lock.getOwner().equals(owner);
        }

        logger.debug("resource {} isLockedBy {} = {}", resourceId, owner, result);

        return result;
    }

    /**
     * Makes a new future. Subclasses may override this.
     * 
     * @param resourceId
     * @param owner
     * @param callback
     * @return a new future
     */
    protected MapFuture makeFuture(String resourceId, String owner, Callback callback) {
        return new MapFuture(resourceId, owner, callback);
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

        /**
         * If the request is successfully cancelled, then this invokes <i>unlock()</i>, to
         * remove the owner from the lock queue.
         */
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(mayInterruptIfRunning);

            if (cancelled) {
                // remove the owner from the lock queue
                unlock(getResourceId(), getOwner());
            }

            return cancelled;
        }
    }
}
