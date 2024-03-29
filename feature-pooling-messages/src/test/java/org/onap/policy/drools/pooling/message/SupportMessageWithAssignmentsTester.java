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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.Test;

/**
 * Superclass used to test subclasses of {@link MessageWithAssignments}.
 * 
 * @param <T> type of {@link MessageWithAssignments} subclass that this tests
 */
@Setter
@Getter
public abstract class SupportMessageWithAssignmentsTester<T extends MessageWithAssignments>
        extends SupportBasicMessageTester<T> {
    // values set by makeValidMessage()
    public static final String[] VALID_ARRAY = {VALID_HOST, VALID_HOST + "_xxx"};
    public static final BucketAssignments VALID_ASGN = new BucketAssignments(VALID_ARRAY);

    /**
     * {@code True} if {@code null} assignments are allowed, {@code false}
     * otherwise.
     */
    private boolean nullAssignments;

    /**
     * Constructor.
     * 
     * @param subclazz subclass of {@link MessageWithAssignments} being tested
     */
    public SupportMessageWithAssignmentsTester(Class<T> subclazz) {
        super(subclazz);
    }

    @Test
    public void testCheckValidity_InvalidFields() {
        // null source (i.e., superclass field)
        expectCheckValidityFailure(msg -> msg.setSource(null));

        // empty assignments
        expectCheckValidityFailure(msg -> msg.setAssignments(new BucketAssignments(new String[0])));

        // invalid assignment
        String[] invalidAssignment = {"abc", null};
        expectCheckValidityFailure(msg -> msg.setAssignments(new BucketAssignments(invalidAssignment)));
    }

    @Test
    public void testGetAssignments_testSetAssignments() {
        MessageWithAssignments msg = makeValidMessage();

        // from constructor
        assertEquals(VALID_ASGN, msg.getAssignments());

        BucketAssignments asgn = new BucketAssignments();
        msg.setAssignments(asgn);
        assertEquals(asgn, msg.getAssignments());
    }

    @Override
    public void testDefaultConstructorFields(T msg) {
        super.testDefaultConstructorFields(msg);

        assertNull(msg.getAssignments());
    }

    @Override
    public void testValidFields(T msg) {
        super.testValidFields(msg);

        if (nullAssignments) {
            assertNull(msg.getAssignments());

        } else {
            assertEquals(VALID_ASGN, msg.getAssignments());
        }
    }

}
