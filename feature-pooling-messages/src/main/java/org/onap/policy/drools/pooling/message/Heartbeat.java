/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018, 2021 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.pooling.message;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Heart beat message sent to self, or to the succeeding host.
 */
@Getter
@Setter
@NoArgsConstructor
public class Heartbeat extends Message {

    /**
     * Time, in milliseconds, when this was created.
     */
    private long timestampMs;

    /**
     * Constructor.
     *
     * @param source host on which the message originated
     * @param timestampMs time, in milliseconds, associated with the message
     */
    public Heartbeat(String source, long timestampMs) {
        super(source);

        this.timestampMs = timestampMs;
    }
}
