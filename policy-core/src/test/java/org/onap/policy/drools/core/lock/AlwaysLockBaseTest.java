/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2021 AT&T Intellectual Property. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.Test;

public abstract class AlwaysLockBaseTest<T extends LockImpl> {
    protected static final String RESOURCE = "hello";
    protected static final String OWNER_KEY = "world";
    protected static final int HOLD_SEC = 10;
    protected static final int HOLD_SEC2 = 10;

    protected LockCallback callback;
    protected T lock;

    @Test
    public void testSerializable() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(lock);
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        T lock2;
        try (ObjectInputStream ois = new ObjectInputStream(bais)) {
            lock2 = (T) ois.readObject();
        }

        assertEquals(lock.getState(), lock2.getState());
        assertEquals(lock.getResourceId(), lock2.getResourceId());
        assertEquals(lock.getOwnerKey(), lock2.getOwnerKey());
        assertEquals(lock.getHoldSec(), lock2.getHoldSec());

        // these fields are transient
        assertNull(lock2.getCallback());
    }

    @Test
    public void testAlwaysLockData() {
        assertEquals(RESOURCE, lock.getResourceId());
        assertEquals(OWNER_KEY, lock.getOwnerKey());
        assertEquals(HOLD_SEC, lock.getHoldSec());
        assertSame(callback, lock.getCallback());
    }
}
