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

package org.onap.policy.no.locking;

import java.util.Properties;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.onap.policy.drools.core.lock.AlwaysSuccessLock;
import org.onap.policy.drools.core.lock.Lock;
import org.onap.policy.drools.core.lock.LockCallback;
import org.onap.policy.drools.core.lock.PolicyResourceLockManager;
import org.onap.policy.drools.features.PolicyEngineFeatureApi;
import org.onap.policy.drools.system.PolicyEngine;

/**
 * In contrast with other implementations the no-lock manager provides non-synchronized access
 * to resources.
 */

@NoArgsConstructor
@ToString
public class NoLockManager implements PolicyResourceLockManager, PolicyEngineFeatureApi {

    protected static final int SEQNO = 2000;

    @Override
    public Lock createLock(String resourceId, String ownerKey, int holdSec,
            LockCallback callback, boolean waitForLock) {
        AlwaysSuccessLock successLock =  new AlwaysSuccessLock(resourceId, ownerKey, holdSec, callback);
        successLock.notifyAvailable();
        return successLock;
    }

    @Override
    public boolean lock() {
        return true;
    }

    @Override
    public boolean unlock() {
        return true;
    }

    @Override
    public boolean isLocked() {
        return false;
    }

    @Override
    public boolean start() {
        return true;
    }

    @Override
    public boolean stop() {
        return true;
    }

    @Override
    public void shutdown() {
        // nothing to do
    }

    @Override
    public boolean isAlive() {
        return true;
    }

    @Override
    public int getSequenceNumber() {
        return SEQNO;
    }

    @Override
    public PolicyResourceLockManager beforeCreateLockManager(PolicyEngine engine, Properties properties) {
        return this;
    }
}
