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
import static org.junit.Assert.fail;
import org.junit.Test;
import org.onap.policy.drools.pooling.PoolingFeatureException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Superclass used to test subclasses of {@link Message}.
 * 
 * @param <T> type of {@link Message} subclass that this tests
 */
public abstract class BasicMessageTester<T extends Message> {
    // values set by makeValidMessage()
    public static final String VALID_HOST_PREDECESSOR = "hostA";
    public static final String VALID_HOST = "hostB";
    public static final String VALID_CHANNEL = "channelC";

    /**
     * Used to perform JSON serialization and de-serialization.
     */
    public final ObjectMapper mapper = new ObjectMapper();

    /**
     * The subclass of the type of Message being tested.
     */
    private final Class<T> subclazz;

    /**
     * 
     * @param subclazz subclass of {@link Message} being tested
     */
    public BasicMessageTester(Class<T> subclazz) {
        this.subclazz = subclazz;
    }

    /**
     * Creates a default Message and verifies that the source and channel are
     * {@code null}.
     * 
     * @return the default Message
     */
    @Test
    public final void testDefaultConstructor() {
        testDefaultConstructorFields(makeDefaultMessage());
    }

    /**
     * Tests that the Message has the correct source, and that the channel is
     * {@code null}.
     * 
     * @param msg message to be checked
     * @param expectedSource what the source is expected to be
     */
    @Test
    public final void testConstructorWithArgs() {
        testValidFields(makeValidMessage());
    }

    /**
     * Makes a valid message and then verifies that it can be serialized and
     * de-serialized. Verifies that the de-serialized message is of the same
     * type, and has the same content, as the original.
     * 
     * @throws Exception if an error occurs
     */
    @Test
    public final void testJsonEncodeDecode() throws Exception {
        T originalMsg = makeValidMessage();

        Message msg = mapper.readValue(mapper.writeValueAsString(originalMsg), Message.class);
        assertEquals(subclazz, msg.getClass());

        msg.checkValidity();

        testValidFields(subclazz.cast(msg));
    }

    /**
     * Creates a valid Message and verifies that checkValidity() passes.
     * 
     * @throws PoolingFeatureException if an error occurs
     */
    @Test
    public final void testCheckValidity_Ok() throws PoolingFeatureException {
        T msg = makeValidMessage();
        msg.checkValidity();

        testValidFields(subclazz.cast(msg));
    }

    /**
     * Creates a default Message and verifies that checkValidity() fails. Does
     * not throw an exception.
     */
    @Test
    public final void testCheckValidity_DefaultConstructor() {
        try {
            makeDefaultMessage().checkValidity();
            fail("missing exception");

        } catch (PoolingFeatureException expected) {
            // success
        }
    }

    /**
     * Creates a message via {@link #makeValidMessage()}, updates it via the
     * given function, and then invokes the checkValidity() method on it. It is
     * expected that the checkValidity() will throw an exception.
     * 
     * @param func function to update the message prior to invoking
     *        checkValidity()
     */
    public void expectCheckValidityFailure(MessageUpdateFunction<T> func) {
        try {
            T msg = makeValidMessage();
            func.update(msg);

            msg.checkValidity();

            fail("missing exception");

        } catch (PoolingFeatureException expected) {
            // success
        }
    }

    /**
     * Creates a message via {@link #makeValidMessage()}, updates one of its
     * fields via the given function, and then invokes the checkValidity()
     * method on it. It is expected that the checkValidity() will throw an
     * exception. It checks both the case when the message's field is set to
     * {@code null}, and when it is set to empty (i.e., "").
     * 
     * @param func function to update the message's field prior to invoking
     *        checkValidity()
     */
    public void expectCheckValidityFailure_NullOrEmpty(MessageFieldUpdateFunction<T> func) {
        expectCheckValidityFailure(msg -> func.update(msg, null));
        expectCheckValidityFailure(msg -> func.update(msg, ""));
    }

    /**
     * Makes a message using the default constructor.
     * 
     * @return a new Message
     */
    public final T makeDefaultMessage() {
        try {
            return subclazz.getConstructor().newInstance();

        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }


    // the remaining methods will typically be overridden

    /**
     * Makes a message that will pass the validity check. Note: this should use
     * the non-default constructor, and the source and channel should be set to
     * {@link VALID_HOST} and {@link VALID_CHANNEL}, respectively.
     * 
     * @return a valid Message
     */
    public abstract T makeValidMessage();

    /**
     * Verifies that fields are set as expected by
     * {@link #makeDefaultMessage()}.
     * 
     * @param msg the default Message
     */
    public void testDefaultConstructorFields(T msg) {
        assertNull(msg.getSource());
        assertNull(msg.getChannel());
    }

    /**
     * Verifies that fields are set as expected by {@link #makeValidMessage()}.
     * 
     * @param msg message whose fields are to be validated
     */
    public void testValidFields(T msg) {
        assertEquals(VALID_HOST, msg.getSource());
        assertEquals(VALID_CHANNEL, msg.getChannel());
    }

    /**
     * Function that updates a message.
     * 
     * @param <T> type of Message the function updates
     */
    @FunctionalInterface
    public static interface MessageUpdateFunction<T extends Message> {

        /**
         * Updates a message.
         * 
         * @param msg message to be updated
         */
        public void update(T msg);
    }

    /**
     * Function that updates a single field within a message.
     * 
     * @param <T> type of Message the function updates
     */
    @FunctionalInterface
    public static interface MessageFieldUpdateFunction<T extends Message> {

        /**
         * Updates a field within a message.
         * 
         * @param msg message to be updated
         * @param newValue new field value
         */
        public void update(T msg, String newValue);
    }
}
