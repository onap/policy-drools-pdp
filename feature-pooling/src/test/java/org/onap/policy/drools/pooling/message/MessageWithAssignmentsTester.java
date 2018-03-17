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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/**
 * Superclass used to test subclasses of {@link MessageWithAssignments}.
 * 
 * @param <T> type of {@link MessageWithAssignments} subclass that this tests
 */
public abstract class MessageWithAssignmentsTester<T extends MessageWithAssignments> extends BasicMessageTester<T> {
    // values set by makeValidMessage()
    public static final String[] VALID_ARRAY = {VALID_HOST, VALID_HOST+"_xxx"};
    public static final BucketAssignments VALID_ASGN = new BucketAssignments(VALID_ARRAY);

    /**
     * {@code True} if {@code null} assignments are allowed, {@code false}
     * otherwise.
     */
    private boolean nullAssignments;

    /**
     * 
     * @param subclazz subclass of {@link MessageWithAssignments} being tested
     */
    public MessageWithAssignmentsTester(Class<T> subclazz) {
        super(subclazz);
    }

    /**
     * Indicates whether or not {@code null} assignments should be used for the
     * remaining tests.
     * 
     * @param nullAssignments {@code true} to use {@code null} assignments,
     *        {@code false} otherwise
     */
    public void setNullAssignments(boolean nullAssignments) {
        this.nullAssignments = nullAssignments;
    }

    public boolean isNullAssignments() {
        return nullAssignments;
    }

    @Test
    public void testCheckValidity_InvalidFields() throws Exception {
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
