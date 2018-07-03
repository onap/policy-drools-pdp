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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import org.onap.policy.common.utils.time.CurrentTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple lock manager. Callbacks are ignored. Does not redirect to lock feature
 * implementers.
 */
public class SimpleLockManager {

    protected static Logger logger = LoggerFactory.getLogger(SimpleLockManager.class);

    // messages used in exceptions
    public static final String MSG_NULL_RESOURCE_ID = "null resourceId";
    public static final String MSG_NULL_OWNER = "null owner";
    
    /**
     * Used to access the current time.  May be overridden by junit tests.
     */
    private static CurrentTime currentTime = new CurrentTime();

    /**
     * Used to synchronize updates to {@link #resource2data} and {@link #locks}.
     */
    private final Object locker = new Object();

    /**
     * Maps a resource to its lock data. Lock data is stored in both this and in
     * {@link #locks}.
     */
    private final Map<String, Data> resource2data = new HashMap<>();

    /**
     * Lock data, sorted by expiration time. Lock data is stored in both this and in
     * {@link #resource2data}. Whenever a lock operation is performed, this structure is
     * examined and any expired locks are removed; thus no timer threads are needed to
     * remove expired locks.
     */
    private final SortedSet<Data> locks = new TreeSet<>();

    /**
     * 
     */
    public SimpleLockManager() {
        super();
    }

    /**
     * Attempts to lock a resource, extending the lock if the owner already holds it.
     * 
     * @param resourceId
     * @param owner
     * @param holdSec the amount of time, in seconds, that the lock should be held
     * @return {@code true} if locked, {@code false} if the resource is already locked by
     *         a different owner
     * @throws IllegalArgumentException if the resourceId or owner is {@code null}
     */
    public boolean lock(String resourceId, String owner, int holdSec) {

        if (resourceId == null) {
            throw makeNullArgException(MSG_NULL_RESOURCE_ID);
        }

        if (owner == null) {
            throw makeNullArgException(MSG_NULL_OWNER);
        }

        Data existingLock;
        
        synchronized(locker) {
            cleanUpLocks();

            if ((existingLock = resource2data.get(resourceId)) != null && existingLock.getOwner().equals(owner)) {
                // replace the existing lock with an extended lock
                locks.remove(existingLock);
                existingLock = null;
            }

            if (existingLock == null) {
                Data data = new Data(owner, resourceId, currentTime.getMillis() + TimeUnit.SECONDS.toMillis(holdSec));
                resource2data.put(resourceId, data);
                locks.add(data);
            }
        }

        boolean locked = (existingLock == null);
        logger.info("lock {} for resource {} owner {}", locked, resourceId, owner);

        return locked;
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
        
        Data data;
        
        synchronized(locker) {
            cleanUpLocks();
            
            if((data = resource2data.get(resourceId)) != null) {
                if(owner.equals(data.getOwner())) {
                    resource2data.remove(resourceId);
                    locks.remove(data);
                    
                } else {
                    data = null;
                }
            }
        }

        boolean unlocked = (data != null);
        logger.info("unlock resource {} owner {} = {}", resourceId, owner, unlocked);

        return unlocked;
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

        boolean locked;
        
        synchronized(locker) {
            cleanUpLocks();
            
            locked = resource2data.containsKey(resourceId);
        }

        logger.debug("resource {} isLocked = {}", resourceId, locked);

        return locked;
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

        Data data;
        
        synchronized(locker) {
            cleanUpLocks();
            
            data = resource2data.get(resourceId);
        }

        boolean locked = (data != null && owner.equals(data.getOwner()));
        logger.debug("resource {} isLockedBy {} = {}", resourceId, owner, locked);

        return locked;
    }

    /**
     * Releases expired locks.
     */
    private void cleanUpLocks() {
        long tcur = currentTime.getMillis();
        
        synchronized(locker) {
            Iterator<Data> it = locks.iterator();
            while(it.hasNext()) {
                Data d = it.next();
                if(d.getExpirationMs() <= tcur) {
                    it.remove();
                    resource2data.remove(d.getResource());
                    
                } else {
                    break;
                }
            }
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
    
    /**
     * Data for a single Lock.  Sorts by expiration time, then resource, and
     * then owner.
     */
    protected static class Data implements Comparable<Data> {
        
        /**
         * Owner of the lock.
         */
        private final String owner;
        
        /**
         * Resource that is locked.
         */
        private final String resource;
        
        /**
         * Time when the lock will expire, in milliseconds.
         */
        private final long texpireMs;
        
        /**
         * 
         * @param resource
         * @param owner
         * @param texpireMs
         */
        public Data(String owner, String resource, long texpireMs) {
            this.owner = owner;
            this.resource = resource;
            this.texpireMs = texpireMs;
        }

        public String getOwner() {
            return owner;
        }

        public String getResource() {
            return resource;
        }

        public long getExpirationMs() {
            return texpireMs;
        }

        @Override
        public int compareTo(Data o) {
            int diff = Long.compare(texpireMs, o.texpireMs);
            if(diff == 0)
                diff = resource.compareTo(o.resource);
            if(diff == 0)
                diff = owner.compareTo(o.owner);
            return diff;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((owner == null) ? 0 : owner.hashCode());
            result = prime * result + ((resource == null) ? 0 : resource.hashCode());
            result = prime * result + (int) (texpireMs ^ (texpireMs >>> 32));
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Data other = (Data) obj;
            if (owner == null) {
                if (other.owner != null)
                    return false;
            } else if (!owner.equals(other.owner))
                return false;
            if (resource == null) {
                if (other.resource != null)
                    return false;
            } else if (!resource.equals(other.resource))
                return false;
            return (texpireMs == other.texpireMs);
        }
    }
}
