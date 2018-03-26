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

package org.onap.policy.drools.pooling;

import java.util.Deque;
import java.util.LinkedList;
import org.onap.policy.drools.pooling.message.Forward;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finite queue of events waiting to be processed once the buckets have been
 * assigned.
 */
public class EventQueue {

    private static final Logger logger = LoggerFactory.getLogger(EventQueue.class);

    /**
     * Maximum number of events allowed in the queue. When excess events are
     * added, the older events are removed.
     */
    private int maxEvents;

    /**
     * Maximum age, in milliseconds, of events in the queue. Events that are
     * older than this are discarded rather than being handed off when
     * {@link #poll()} is invoked.
     */
    private long maxAgeMs;

    /**
     * The actual queue of events.
     */
    private Deque<Forward> events = new LinkedList<>();

    /**
     * 
     * @param maxEvents     maximum number of events to hold in the queue
     * @param maxAgeMs      maximum age of events in the queue
     */
    public EventQueue(int maxEvents, long maxAgeMs) {
        this.maxEvents = maxEvents;
        this.maxAgeMs = maxAgeMs;
    }

    /**
     * 
     * @return {@code true} if the queue is empty, {@code false} otherwise
     */
    public boolean isEmpty() {
        return events.isEmpty();
    }

    /**
     * 
     * @return the number of elements in the queue
     */
    public int size() {
        return events.size();
    }

    /**
     * Adds an item to the queue. If the queue is full, the older item is
     * removed and discarded.
     * 
     * @param event
     */
    public void add(Forward event) {
        if (events.size() >= maxEvents) {
            logger.warn("full queue - discarded event for topic {}", event.getTopic());
            events.remove();
        }

        events.add(event);
    }

    /**
     * Gets the oldest, un-expired event from the queue.
     * 
     * @return the oldest, un-expired event
     */
    public Forward poll() {
        long tmin = System.currentTimeMillis() - maxAgeMs;

        Forward ev;
        while ((ev = events.poll()) != null) {
            if (!ev.isExpired(tmin)) {
                break;
            }
        }

        return ev;
    }

}
