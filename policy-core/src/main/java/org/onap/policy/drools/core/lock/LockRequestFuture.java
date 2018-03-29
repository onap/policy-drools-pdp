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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.onap.policy.drools.core.lock.PolicyResourceLockFeatureAPI.Callback;

/**
 * Future associated with a lock request.
 */
public class LockRequestFuture implements Future<Boolean> {
    private final String resourceId;
    private final String owner;
    private final Callback callback;

    private enum State {
        WAITING, DONE, CANCELLED, EXCEPTION
    };

    private AtomicReference<State> state = new AtomicReference<>(State.WAITING);
    private CountDownLatch waiter = new CountDownLatch(1);
    private volatile Exception exception = null;

    /**
     * 
     * @param callback item to be wrapped
     */
    public LockRequestFuture(String resourceId, String owner, Callback callback) {
        this.resourceId = resourceId;
        this.owner = owner;
        this.callback = callback;
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
            waiter.countDown();
        }

        return cancelled;
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
     * Always returns {@code true}, once the lock has been acquired.
     */
    @Override
    public Boolean get() throws InterruptedException, ExecutionException {
        waiter.await();

        if (state.get() == State.EXCEPTION) {
            throw new ExecutionException(exception);
        }

        return true;
    }

    @Override
    public Boolean get(long timeout, TimeUnit unit)
                    throws InterruptedException, ExecutionException, TimeoutException {

        if (!waiter.await(timeout, unit)) {
            throw new TimeoutException("lock request did not complete in time");
        }

        if (state.get() == State.EXCEPTION) {
            throw new ExecutionException(exception);
        }

        return true;
    }

    /**
     * Invokes the callback to indicate that the lock has been acquired.
     * 
     * @return {@code true} if it was not already completed, {@code false} otherwise
     */
    public boolean setLocked() {
        if (state.compareAndSet(State.WAITING, State.DONE)) {
            callback.locked();
            waiter.countDown();
            return true;

        } else {
            return false;
        }
    }

    /**
     * Invokes the callback to indicate that an exception occurred.
     * 
     * @param exception
     * @return {@code true} if it was not already completed, {@code false} otherwise
     */
    public boolean setException(Exception exception) {
        if (state.compareAndSet(State.WAITING, State.EXCEPTION)) {
            callback.exception(exception);
            waiter.countDown();
            return true;

        } else {
            return false;
        }
    }
}
