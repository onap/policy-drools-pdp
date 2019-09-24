/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2019 AT&T Intellectual Property. All rights reserved.
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class LockTest {

    @Test
    public void test() {
        AtomicReference<Lock.State> state = new AtomicReference<>(Lock.State.ACTIVE);

        Lock lock = new Lock() {
            @Override
            public State getState() {
                return state.get();
            }

            @Override
            public boolean free() {
                return false;
            }

            @Override
            public String getResourceId() {
                return null;
            }

            @Override
            public Object getOwnerInfo() {
                return null;
            }

            @Override
            public void extend(int holdSec, LockCallback callback) {
                // do nothing
            }

        };

        assertTrue(lock.isActive());

        state.set(Lock.State.UNAVAILABLE);
        assertFalse(lock.isActive());
    }
}
