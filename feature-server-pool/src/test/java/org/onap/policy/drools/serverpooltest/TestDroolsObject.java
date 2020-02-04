/*
 * ============LICENSE_START=======================================================
 * feature-server-pool
 * ================================================================================
 * Copyright (C) 2020 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.serverpooltest;

import java.io.Serializable;
import java.util.Objects;

/**
 * Instances of this class can be inserted into a Drools session, and used
 * to test things like message routing and bucket migration.
 */
public class TestDroolsObject implements Serializable {
    // determines the bucket number
    private String key;

    /**
     * Constructor - no key specified.
     */
    public TestDroolsObject() {
        this.key = null;
    }

    /**
     * Constructor - initialize the key.
     *
     * @param key key that is hashed to determine the bucket number
     */
    public TestDroolsObject(String key) {
        this.key = key;
    }

    /**
     * Set the key field.
     *
     * @param key the new value of the key
     */
    public void setKey(String key) {
        this.key = key;
    }

    /**
     * Return the 'key' field.
     *
     * @return the 'key' field
     */
    public String getKey() {
        return this.key;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "TestDroolsObject[key=" + key + "]";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return key.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        return obj != null
               && (obj instanceof TestDroolsObject)
               && Objects.equals(key, ((TestDroolsObject)obj).getKey());
    }
}
