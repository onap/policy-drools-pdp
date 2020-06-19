/*-
 * ============LICENSE_START=======================================================
 * feature-drools-init
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

package org.onap.policy.drools.droolsinit;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;
import org.kie.api.runtime.rule.FactHandle;
import org.onap.policy.drools.core.PolicySession;
import org.onap.policy.drools.core.PolicySessionFeatureApi;
import org.onap.policy.drools.system.PolicyEngineConstants;

/**
 * This feature inserts an object of class 'DroolsInitFeature.Init' into
 * every newly-created or updated Drools session, including those that were
 * initialized with persistent data. Rules matching on objects of this type
 * can then do things like initialize global data.
 */
public class DroolsInitFeature implements PolicySessionFeatureApi {
    // default delay is 10 minutes
    private static final long DELAY = 600000L;

    /**
     * {@inheritDoc}.
     */
    @Override
    public int getSequenceNumber() {
        return 0;
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public PolicySession.ThreadModel selectThreadModel(PolicySession policySession) {
        new Init(policySession);
        return null;
    }

    /**
     * Instances of this class are inserted into Drools memory.
     */
    public static class Init implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * Place this instance in Drools memory, and then remove it after
         * one minute, if it is still there.
         *
         * @param policySession the associated session
         */
        public Init(final PolicySession policySession) {
            // insert this instance into Drools memory
            final FactHandle factHandle = policySession.getKieSession().insert(this);

            // after 10 minutes, remove the object from Drools memory (if needed)
            PolicyEngineConstants.getManager().getExecutorService().schedule(() -> {
                if (policySession.getKieSession().getObject(factHandle) != null) {
                    // object has not been removed by application -- remove it here
                    policySession.getKieSession().delete(factHandle);
                }
            }, DELAY, TimeUnit.MILLISECONDS);
        }
    }
}
