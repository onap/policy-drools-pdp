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

/**
 * Reference to an object. Used within functional methods, where thread-safety is not
 * required.
 * 
 * @param <T> type of object contained within the reference
 */
public class Reference<T> {
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

    /**
     * Sets the value to a new value, if the value is currently the same as the old value.
     * 
     * @param oldValue
     * @param newValue
     * @return {@code true} if the value was updated, {@code false} otherwise
     */
    public boolean compareAndSet(T oldValue, T newValue) {
        if (value == oldValue) {
            value = newValue;
            return true;

        } else {
            return false;
        }
    }
}
