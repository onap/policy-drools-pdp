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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lock feature provider whose locks always fail. This is used if no other provider is
 * available.
 */
public class AlwaysFailProvider implements PolicyResourceLockFeatureApi {
    private static Logger logger = LoggerFactory.getLogger(PolicyResourceLockManager.class);


    @Override
    public int getSequenceNumber() {
        return 1000;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public Lock lock(String resourceId, Object ownerInfo, int holdSec, LockCallback callback, boolean waitForLock) {
        logger.warn("{}: no lock feature available at this time", this);
        AlwaysFailLock lock = new AlwaysFailLock(resourceId, ownerInfo, null, holdSec, callback);
        lock.notifyUnavailable();
        return lock;
    }
}
