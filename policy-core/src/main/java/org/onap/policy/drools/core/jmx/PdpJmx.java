/*-
 * ============LICENSE_START=======================================================
 * policy-core
 * ================================================================================
 * Copyright (C) 2017-2018, 2021 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2024 Nordix Foundation.
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

package org.onap.policy.drools.core.jmx;

import java.util.concurrent.atomic.AtomicLong;
import lombok.Getter;

public class PdpJmx implements PdpJmxMBean  {

    @Getter
    private static PdpJmx instance = new PdpJmx();

    private final AtomicLong updates = new AtomicLong();
    private final AtomicLong actions = new AtomicLong();

    @Override
    public long getUpdates() {
        return updates.longValue();
    }

    @Override
    public long getRulesFired() {
        return actions.longValue();
    }

    public void updateOccurred() {
        updates.incrementAndGet();
    }

    public void ruleFired() {
        actions.incrementAndGet();
    }
}
