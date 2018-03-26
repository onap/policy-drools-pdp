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

package org.onap.policy.drools.pooling.extractor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.lang.reflect.Method;
import org.junit.Before;
import org.junit.Test;

public class MethodExtractorTest {

    private static final String VALUE = "the value";
    private static final Integer INT_VALUE = 10;

    private Method meth;
    private MethodExtractor ext;

    @Before
    public void setUp() throws Exception {
        meth = MyClass.class.getMethod("getValue");
        ext = new MethodExtractor(meth);
    }

    @Test
    public void testExtract() throws Exception {
        assertEquals(VALUE, ext.extract(new MyClass()));

        // repeat
        assertEquals(VALUE, ext.extract(new MyClass()));

        // null value
        MyClass obj = new MyClass();
        meth = MyClass.class.getMethod("getNullValue");
        ext = new MethodExtractor(meth);
        assertEquals(null, ext.extract(obj));

        // different value type
        meth = MyClass.class.getMethod("getIntValue");
        ext = new MethodExtractor(meth);
        assertEquals(INT_VALUE, ext.extract(new MyClass()));
    }

    @Test
    public void testExtract_ArgEx() {
        // pass it the wrong class type
        assertNull(ext.extract(this));
    }

    @Test
    public void testExtract_InvokeEx() throws Exception {
        // invoke method that throws an exception
        meth = MyClass.class.getMethod("throwException");
        ext = new MethodExtractor(meth);
        assertEquals(null, ext.extract(new MyClass()));
    }

    private static class MyClass {

        @SuppressWarnings("unused")
        public String getValue() {
            return VALUE;
        }

        @SuppressWarnings("unused")
        public int getIntValue() {
            return INT_VALUE;
        }

        @SuppressWarnings("unused")
        public String getNullValue() {
            return null;
        }

        @SuppressWarnings("unused")
        public String throwException() {
            throw new IllegalStateException("expected");
        }
    }

}
