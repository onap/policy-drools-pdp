/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018 AT&T Intellectual Property. All rights reserved.
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LeaderTest extends SupportMessageWithAssignmentsTester<Leader> {

    public LeaderTest() {
        super(Leader.class);
    }

    @BeforeEach
    public void setUp() {
        setNullAssignments(false);
    }

    /**
     * The superclass will already invoke testCheckValidity_InvalidFields() to
     * verify that things work with a fully populated message. This verifies
     * that it also works if the assignments are null.
     *
     */
    @Test
    void testCheckValidity_InvalidFields_NullAssignments() {
        // null assignments are invalid
        expectCheckValidityFailure(msg -> msg.setAssignments(null));
    }

    @Test
    void testCheckValidity_SourceIsNotLeader() {
        Leader ldr = makeValidMessage();

        ldr.setSource("xyz");

        // the source does not have an assignment
        BucketAssignments asgnUnassigned = new BucketAssignments(new String[] {"abc", "def"});
        expectCheckValidityFailure(msg -> msg.setAssignments(asgnUnassigned));

        // the source is not the smallest UUID in this assignment
        BucketAssignments asgnNotSmallest = new BucketAssignments(new String[] {VALID_HOST_PREDECESSOR, VALID_HOST});
        expectCheckValidityFailure(msg -> msg.setAssignments(asgnNotSmallest));
    }

    @Override
    public Leader makeValidMessage() {
        Leader msg = new Leader(VALID_HOST, (isNullAssignments() ? null : VALID_ASGN));
        msg.setChannel(VALID_CHANNEL);

        return msg;
    }

}
