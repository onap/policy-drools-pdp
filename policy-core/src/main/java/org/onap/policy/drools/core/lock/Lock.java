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

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import org.onap.policy.drools.utils.Pair;

/**
 * Lock that is held for a resource. This not only identifies the current owner of the
 * lock, but it also includes a queue of requesters. An item is associated with each
 * requester that is waiting in the queue. Note: this class is not thread-safe.
 * 
 * @param <T> type of item to be associated with a request
 */
public class Lock<T> {

    public enum RemoveResult {
        /**
         * The requester was the owner of the lock, and the lock is no longer needed,
         * because there were no other requests waiting to get the lock.
         */
        UNLOCKED,

        /**
         * The requester was the owner of the lock, and has been replaced with the next
         * requester waiting in the queue.
         */
        RELOCKED,

        /**
         * The requester had been waiting in the queue, and has now been removed.
         */
        REMOVED,

        /**
         * The requester was not the owner, nor was it waiting in the queue.
         */
        NOT_FOUND
    };

    /**
     * The last owner to grab the lock, never {@code null}.
     */
    private String owner;

    /**
     * Requesters waiting to get the lock. Maps the requester (i.e., owner for which the
     * request is being made) to its associated item. Uses a Linked map so that the order
     * of the requesters is maintained.
     */
    private LinkedHashMap<String, T> requester2item = new LinkedHashMap<>(5);

    /**
     * 
     * @param owner
     */
    public Lock(String owner) {
        this.owner = owner;
    }

    public String getOwner() {
        return owner;
    }

    /**
     * Adds a new requester to the queue of requesters.
     * 
     * @param requester
     * @param item to be associated with the requester, must not be {@code null}
     * @return {@code true} if the requester was added, {@code false} if it already owns
     *         the lock or is already in the queue
     * @throws IllegalArgumentException if the item is null
     */
    public boolean add(String requester, T item) {
        if (item == null) {
            throw new IllegalArgumentException("lock requester's item is null");
        }

        if (requester.equals(owner)) {
            // requester already owns the lock
            return false;
        }

        T prev = requester2item.putIfAbsent(requester, item);

        // if there's a previous value, then that means this requester is already
        // waiting for a lock on this resource. In that case, we return false
        return (prev == null);
    }

    /**
     * Removes a requester from the lock. The requester may currently own the lock, or it
     * may be in the queue waiting for the lock.
     * 
     * @param requester
     * @param newOwner the new owner info is placed here, if the result is <i>RELOCKED</i>
     * @return the result
     */
    public RemoveResult removeRequester(String requester, Pair<String, T> newOwner) {

        if (!requester.equals(owner)) {
            // requester does not currently own the lock - remove it from the
            // queue
            T ent = requester2item.remove(requester);

            // if there was an entry in the queue, then return true to indicate
            // that it was removed. Otherwise, return false
            return (ent != null ? RemoveResult.REMOVED : RemoveResult.NOT_FOUND);
        }

        /*
         * requester was the owner - find something to take over
         */
        Iterator<Entry<String, T>> it = requester2item.entrySet().iterator();
        if (!it.hasNext()) {
            // no one to take over the lock - it's now unlocked
            return RemoveResult.UNLOCKED;
        }
        
        // there's another requester to take over
        Entry<String, T> ent = it.next();
        it.remove();

        owner = ent.getKey();

        newOwner.first(owner);
        newOwner.second(ent.getValue());

        return RemoveResult.RELOCKED;
    }
}
