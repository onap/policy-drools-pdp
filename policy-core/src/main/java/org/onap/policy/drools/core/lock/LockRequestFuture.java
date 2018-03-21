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

import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.onap.policy.drools.core.lock.PolicyResourceLockFeatureAPI.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Future associated with a lock request.
 */
public class LockRequestFuture implements Future<Boolean> {

    // messages used in exceptions
    public static final String MSG_NULL_RESOURCE_ID = "null resourceId";
    public static final String MSG_NULL_OWNER = "null owner";

    private static Logger logger = LoggerFactory.getLogger(LockRequestFuture.class);

    /**
     * The resource on which the lock was requested.
     */
    private final String resourceId;

    /**
     * The owner for which the lock was requested.
     */
    private final String owner;

    /**
     * Possible states for this future.
     */
    private enum State {
        WAITING, CANCELLED, ACQUIRED, DENIED
    };

    private AtomicReference<State> state;

    /**
     * Used to wait for the lock request to complete.
     */
    private CountDownLatch waiter = new CountDownLatch(1);

    /**
     * Callback to invoke once the lock is acquired (or denied). This is set to
     * {@code null} once the callback has been invoked.
     */
    private final AtomicReference<Callback> callback;

    /**
     * Constructs a future that has already been completed.
     * 
     * @param resourceId
     * @param owner owner for which the lock was requested
     * @param locked {@code true} if the lock has been acquired, {@code false} if the lock
     *        request has been denied
     * @throws IllegalArgumentException if any of the arguments are {@code null}
     */
    public LockRequestFuture(String resourceId, String owner, boolean locked) {
        if (resourceId == null) {
            throw makeNullArgException(MSG_NULL_RESOURCE_ID);
        }

        if (owner == null) {
            throw makeNullArgException(MSG_NULL_OWNER);
        }

        this.resourceId = resourceId;
        this.owner = owner;
        this.callback = new AtomicReference<>(null);
        this.state = new AtomicReference<>(locked ? State.ACQUIRED : State.DENIED);

        // indicate that it's already done
        this.waiter.countDown();
    }

    /**
     * Constructs a future that has not yet been completed.
     * 
     * @param resourceId
     * @param owner owner for which the lock was requested
     * @param callback item to be wrapped
     * @throws IllegalArgumentException if the resourceId or owner is {@code null}
     */
    public LockRequestFuture(String resourceId, String owner, Callback callback) {
        if (resourceId == null) {
            throw makeNullArgException(MSG_NULL_RESOURCE_ID);
        }

        if (owner == null) {
            throw makeNullArgException(MSG_NULL_OWNER);
        }

        this.resourceId = resourceId;
        this.owner = owner;
        this.callback = new AtomicReference<>(callback);
        this.state = new AtomicReference<>(State.WAITING);
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getOwner() {
        return owner;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean cancelled = state.compareAndSet(State.WAITING, State.CANCELLED);

        if (cancelled) {
            logger.info("resource {} owner {} cancelled lock request", resourceId, owner);
            waiter.countDown();
        }

        return cancelled;
    }

    /**
     * Indicates that the lock has been acquired or denied.
     * 
     * @param locked {@code true} if the lock has been acquired, {@code false} if the lock
     *        request has been denied
     * 
     * @return {@code true} if it was not already completed, {@code false} otherwise
     */
    protected boolean setLocked(boolean locked) {
        State newState = (locked ? State.ACQUIRED : State.DENIED);
        if (state.compareAndSet(State.WAITING, newState)) {
            waiter.countDown();
            return true;

        } else {
            return false;
        }
    }

    @Override
    public boolean isCancelled() {
        return (state.get() == State.CANCELLED);
    }

    @Override
    public boolean isDone() {
        return (state.get() != State.WAITING);
    }

    /**
     * Gets the current status of the lock.
     * 
     * @return {@code true} if the lock has been acquired, {@code false} otherwise
     */
    public boolean isLocked() {
        return (state.get() == State.ACQUIRED);
    }

    /**
     * @return {@code true} if the lock was acquired, {@code false} if it was denied
     */
    @Override
    public Boolean get() throws CancellationException, InterruptedException {
        waiter.await();

        switch (state.get()) {
            case CANCELLED:
                throw new CancellationException("lock request was cancelled");
            case ACQUIRED:
                return true;
            default:
                // should only be DENIED at this point
                return false;
        }
    }

    /**
     * @return {@code true} if the lock was acquired, {@code false} if it was denied
     */
    @Override
    public Boolean get(long timeout, TimeUnit unit)
                    throws CancellationException, InterruptedException, TimeoutException {

        if (!waiter.await(timeout, unit)) {
            throw new TimeoutException("lock request did not complete in time");
        }

        return get();
    }

    /**
     * Invokes the callback, indicating whether or not the lock was acquired.
     * 
     * @throws IllegalStateException if the request was previously cancelled, has not yet
     *         completed, or if the callback has already been invoked
     */
    protected void invokeCallback() {
        boolean locked;

        switch (state.get()) {
            case ACQUIRED:
                locked = true;
                break;
            case DENIED:
                locked = false;
                break;
            case CANCELLED:
                throw new IllegalStateException("cancelled lock request callback");
            default:
                // only other choice is WAITING
                throw new IllegalStateException("incomplete lock request callback");
        }

        Callback cb = callback.get();
        if (cb == null || !callback.compareAndSet(cb, null)) {
            throw new IllegalStateException("already invoked lock request callback");
        }


        // notify the callback
        try {
            cb.set(locked);

        } catch (RuntimeException e) {
            logger.info("lock request callback for resource {} owner {} threw an exception", resourceId, owner, e);
        }
    }

    /**
     * Makes an exception for when an argument is {@code null}.
     * 
     * @param msg exception message
     * @return a new Exception
     */
    public static IllegalArgumentException makeNullArgException(String msg) {
        return new IllegalArgumentException(msg);
    }
}
