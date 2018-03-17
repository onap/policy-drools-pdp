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

package org.onap.policy.drools.pooling.state;

import static org.onap.policy.drools.pooling.state.FilterUtils.MSG_CHANNEL;
import static org.onap.policy.drools.pooling.state.FilterUtils.makeEquals;
import java.util.Map;
import org.onap.policy.drools.pooling.message.Message;

public class InactiveState extends State {

    /**
     * Amount of time, in milliseconds, to wait before attempting to re-activate this host.
     */
    private static final long REACTIVATE_MS = 0L;

    public InactiveState(State oldState) {
        super(oldState);

        schedule(REACTIVATE_MS, xxx -> {
            return new StartState(InactiveState.this);
        });
    }

    @Override
    public Map<String, Object> getFilter() {
        // ignore all messages
        return makeEquals(MSG_CHANNEL, Message.UNKNOWN);
    }

}
