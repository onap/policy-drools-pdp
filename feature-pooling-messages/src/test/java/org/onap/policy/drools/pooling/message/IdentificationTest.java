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

class IdentificationTest extends SupportMessageWithAssignmentsTester<Identification> {

    public IdentificationTest() {
        super(Identification.class);
    }

    @BeforeEach
    public void setUp() {
        setNullAssignments(false);
    }

    /**
     * The superclass will already invoke testJsonEncodeDecode() to verify that
     * things work with a fully populated message. This verifies that it also
     * works if the assignments are null.
     * 
     * @throws Exception if an error occurs
     */
    @Test
    final void testJsonEncodeDecode_WithNullAssignments() throws Exception {
        setNullAssignments(true);
        testJsonEncodeDecode();
    }

    /**
     * The superclass will already invoke testCheckValidity() to
     * verify that things work with a fully populated message. This verifies
     * that it also works if the assignments are null.
     * 
     * @throws Exception if an error occurs
     */
    @Test
    void testCheckValidity_NullAssignments() throws Exception {
        // null assignments are OK
        Identification msg = makeValidMessage();
        msg.setAssignments(null);
        msg.checkValidity();
    }

    @Override
    public Identification makeValidMessage() {
        Identification msg = new Identification(VALID_HOST, (isNullAssignments() ? null : VALID_ASGN));
        msg.setChannel(VALID_CHANNEL);

        return msg;
    }

}
