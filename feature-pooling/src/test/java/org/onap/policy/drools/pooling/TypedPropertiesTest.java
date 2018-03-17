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

package org.onap.policy.drools.pooling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import java.util.Properties;
import org.junit.Before;
import org.junit.Test;

/**
 * Test for TypedProperties.
 * <p>
 * Each test case defines a "method" variable, which invokes the particular
 * getXxx() method that is used through-out the given test case. This helps
 * reduce the likelihood of errors when cloning test cases.
 */
public class TypedPropertiesTest {
    /**
     * Name of an unknown property.
     */
    private static final String UNKNOWN = "unknown";

    // property names
    private static final String INT_NAME = "int";
    private static final String LONG_NAME = "long";
    private static final String TRUE_NAME = "trueValue";
    private static final String FALSE_NAME = "falseValue";
    private static final String STRING_NAME = "string";

    // property values
    private static final int INT_VALUE = 10;
    private static final long LONG_VALUE = Integer.MAX_VALUE * 10L;
    private static final boolean TRUE_VALUE = true;
    private static final boolean FALSE_VALUE = false;
    private static final String STRING_VALUE = "hello";

    private Properties wrapped;
    private TypedProperties props;

    @Before
    public void setUp() {
        wrapped = new Properties();
        wrapped.put(INT_NAME, INT_VALUE);
        wrapped.put(LONG_NAME, LONG_VALUE);
        wrapped.put(TRUE_NAME, TRUE_VALUE);
        wrapped.put(FALSE_NAME, FALSE_VALUE);
        wrapped.put(STRING_NAME, STRING_VALUE);

        props = new TypedProperties(wrapped);
    }

    @Test
    public void testTypedProperties() {
        // ensure no exceptions
        props = new TypedProperties();
        assertTrue(props.getOptBoolProperty("myBool", true));
        assertFalse(props.getOptBoolProperty("myBool", false));
    }

    @Test
    public void testGetProperties() {
        assertTrue(wrapped == props.getProperties());
    }

    @Test
    public void testGetStrProperty() throws Exception {
        GetFunction<String> method = nm -> props.getStrProperty(nm);

        expectMissing(UNKNOWN, nm -> method.apply(nm));

        // plain string
        assertEquals(STRING_VALUE, method.apply(STRING_NAME));

        // coerce to string
        assertEquals(String.valueOf(INT_VALUE), method.apply(INT_NAME));
        assertEquals(String.valueOf(LONG_VALUE), method.apply(LONG_NAME));
        assertEquals(String.valueOf(TRUE_VALUE), method.apply(TRUE_NAME));
        assertEquals(String.valueOf(FALSE_VALUE), method.apply(FALSE_NAME));
    }

    @Test
    public void testGetBoolProperty() throws Exception {
        GetFunction<Boolean> method = nm -> props.getBoolProperty(nm);

        expectMissing(UNKNOWN, nm -> method.apply(nm));

        assertEquals(TRUE_VALUE, method.apply(TRUE_NAME));
        assertEquals(FALSE_VALUE, method.apply(FALSE_NAME));
    }

    @Test
    public void testGetIntProperty() throws Exception {
        GetFunction<Integer> method = nm -> props.getIntProperty(nm);

        expectMissing(UNKNOWN, nm -> method.apply(nm));
        expectInvalid(STRING_NAME, nm -> method.apply(nm));

        // too big to fit in an integer
        expectInvalid(LONG_NAME, nm -> method.apply(nm));

        assertEquals(INT_VALUE, method.apply(INT_NAME).intValue());
    }

    @Test
    public void testGetLongProperty() throws Exception {
        GetFunction<Long> method = nm -> props.getLongProperty(nm);

        expectMissing(UNKNOWN, nm -> method.apply(nm));
        expectInvalid(STRING_NAME, nm -> method.apply(nm));

        assertEquals(INT_VALUE, method.apply(INT_NAME).longValue());
        assertEquals(LONG_VALUE, method.apply(LONG_NAME).longValue());
    }

    @Test
    public void testGetOptStrPropertyString() throws Exception {
        GetFunction<String> method = nm -> props.getOptStrProperty(nm);

        assertNull(method.apply(UNKNOWN));
        assertEquals(STRING_VALUE, method.apply(STRING_NAME));

        // coerce to string
        assertEquals(String.valueOf(INT_VALUE), method.apply(INT_NAME));
    }

    @Test
    public void testGetOptBoolPropertyString() throws Exception {
        GetFunction<Boolean> method = nm -> props.getOptBoolProperty(nm);

        assertNull(method.apply(UNKNOWN));

        assertEquals(TRUE_VALUE, method.apply(TRUE_NAME));
        assertEquals(FALSE_VALUE, method.apply(FALSE_NAME));
    }

    @Test
    public void testGetOptIntPropertyString() throws Exception {
        GetFunction<Integer> method = nm -> props.getOptIntProperty(nm);

        assertNull(method.apply(UNKNOWN));

        // still invalid
        expectInvalid(STRING_NAME, nm -> method.apply(nm));
        expectInvalid(LONG_NAME, nm -> method.apply(nm));

        assertEquals(INT_VALUE, method.apply(INT_NAME).intValue());
    }

    @Test
    public void testGetOptLongPropertyString() throws Exception {
        GetFunction<Long> method = nm -> props.getOptLongProperty(nm);

        assertNull(method.apply(UNKNOWN));

        // still invalid
        expectInvalid(STRING_NAME, nm -> method.apply(nm));

        assertEquals(INT_VALUE, method.apply(INT_NAME).longValue());
        assertEquals(LONG_VALUE, method.apply(LONG_NAME).longValue());
    }

    @Test
    public void testGetOptStrPropertyStringString() throws Exception {
        GetFunctionWithDefault<String> method = (nm, dflt) -> props.getOptStrProperty(nm, dflt);

        assertNull(method.apply(UNKNOWN, null));
        assertEquals("abc", method.apply(UNKNOWN, "abc"));

        assertEquals(STRING_VALUE, method.apply(STRING_NAME, null));
        assertEquals(STRING_VALUE, method.apply(STRING_NAME, "def"));

        // coerce to string
        assertEquals(String.valueOf(INT_VALUE), method.apply(INT_NAME, "ghi"));
    }

    @Test
    public void testGetOptBoolPropertyStringBoolean() throws Exception {
        GetFunctionWithDefault<Boolean> method = (nm, dflt) -> props.getOptBoolProperty(nm, dflt);

        assertTrue(method.apply(UNKNOWN, true));
        assertFalse(method.apply(UNKNOWN, false));

        assertEquals(TRUE_VALUE, method.apply(TRUE_NAME, false));
        assertEquals(FALSE_VALUE, method.apply(FALSE_NAME, true));
    }

    @Test
    public void testGetOptIntPropertyStringInt() throws Exception {
        GetFunctionWithDefault<Integer> method = (nm, dflt) -> props.getOptIntProperty(nm, dflt);

        assertEquals(-20, method.apply(UNKNOWN, -20).intValue());

        // still invalid
        expectInvalid(STRING_NAME, nm -> method.apply(nm, -10));
        expectInvalid(LONG_NAME, nm -> method.apply(nm, -10));

        assertEquals(INT_VALUE, method.apply(INT_NAME, -30).intValue());
    }

    @Test
    public void testGetOptLongPropertyStringLong() throws Exception {
        GetFunctionWithDefault<Long> method = (nm, dflt) -> props.getOptLongProperty(nm, dflt);

        assertEquals(-20L, method.apply(UNKNOWN, -20L).longValue());

        // still invalid
        expectInvalid(STRING_NAME, nm -> method.apply(nm, -10L));

        assertEquals(INT_VALUE, method.apply(INT_NAME, -30L).longValue());
        assertEquals(LONG_VALUE, method.apply(LONG_NAME, -40L).longValue());
    }

    /**
     * Invokes a getXxx() method, expecting a "missing property" exception.
     * 
     * @param propnm name of the property to get
     * @param func function to invoke to get the property
     */
    private void expectMissing(String propnm, VoidGetFunction func) {
        try {
            func.apply(propnm);
            fail("missing exception");

        } catch (PoolingFeatureException expected) {
            assertEquals(TypedProperties.MISSING_PROPERTY + propnm, expected.getMessage());
        }
    }

    /**
     * Invokes a getXxx() method, expecting an "invalid property" exception.
     * 
     * @param propnm name of the property to get
     * @param func function to invoke to get the property
     */
    private void expectInvalid(String propnm, VoidGetFunction func) {
        try {
            func.apply(propnm);
            fail("missing exception");

        } catch (PoolingFeatureException expected) {
            assertEquals(TypedProperties.INVALID_PROPERTY_VALUE + propnm, expected.getMessage());
        }
    }

    /**
     * Function to invoke a props.getXxx() method, and return nothing
     */
    @FunctionalInterface
    public static interface VoidGetFunction {

        /**
         * Invokes a getXxx() method.
         * 
         * @param propnm name of the property to get
         * @throws PoolingFeatureException
         */
        public void apply(String propnm) throws PoolingFeatureException;
    }

    /**
     * Function to invoke a props.getXxx() method, and return the result.
     */
    @FunctionalInterface
    public static interface GetFunction<T> {

        /**
         * Invokes a getXxx() method.
         * 
         * @param propnm name of the property to get
         * @return the value that was gotten
         * @throws PoolingFeatureException
         */
        public T apply(String propnm) throws PoolingFeatureException;
    }

    /**
     * Function to invoke a props.getXxx() method, and return the result.
     */
    @FunctionalInterface
    public static interface GetFunctionWithDefault<T> {

        /**
         * Invokes a getXxx() method.
         * 
         * @param propnm name of the property to get
         * @param defaultValue
         * @return the value that was gotten
         * @throws PoolingFeatureException
         */
        public T apply(String propnm, T defaultValue) throws PoolingFeatureException;
    }

}
