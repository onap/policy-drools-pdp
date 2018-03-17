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

package org.onap.policy.drools.pooling.message;

import org.onap.policy.drools.pooling.PoolingFeatureException;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Indicates that the "source" of this message is now the "lead" host.
 */
public class Leader extends MessageWithAssignments {

    /**
     * 
     */
    public Leader() {
        super();
    }

    /**
     * 
     * @param source host on which the message originated
     * @param assignments
     */
    public Leader(String source, BucketAssignments assignments) {
        super(source, assignments);
    }

    /**
     * Also verifies that buckets have been assigned and that the source is
     * indeed the leader.
     */
    @Override
    @JsonIgnore
    public void checkValidity() throws PoolingFeatureException {

        super.checkValidity();

        BucketAssignments assignments = getAssignments();
        if (assignments == null) {
            throw new PoolingFeatureException("missing message bucket assignments");
        }

        String leader = getSource();
        
        if(!assignments.hasAssignment(leader)) {
            throw new PoolingFeatureException("leader " + leader + " has no bucket assignments");            
        }
        
        for (String host : assignments.getHostArray()) {
            if (host.compareTo(leader) < 0) {
                throw new PoolingFeatureException("invalid leader " + leader + ", should be " + host);
            }
        }
    }

}
