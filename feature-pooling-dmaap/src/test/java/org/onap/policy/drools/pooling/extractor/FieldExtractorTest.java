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
import java.lang.reflect.Field;
import org.junit.Before;
import org.junit.Test;

public class FieldExtractorTest {

    private static final String VALUE = "the value";
    private static final Integer INT_VALUE = 10;

    private Field field;
    private FieldExtractor ext;

    @Before
    public void setUp() throws Exception {
        field = MyClass.class.getDeclaredField("value");
        ext = new FieldExtractor(field);
    }

    @Test
    public void testExtract() throws Exception {
        assertEquals(VALUE, ext.extract(new MyClass()));

        // repeat
        assertEquals(VALUE, ext.extract(new MyClass()));

        // null value
        MyClass obj = new MyClass();
        obj.value = null;
        assertEquals(null, ext.extract(obj));

        obj.value = VALUE + "X";
        assertEquals(VALUE + "X", ext.extract(obj));

        // different value type
        field = MyClass.class.getDeclaredField("value2");
        ext = new FieldExtractor(field);
        assertEquals(INT_VALUE, ext.extract(new MyClass()));
    }

    @Test
    public void testExtract_ArgEx() {
        // pass it the wrong class type
        assertNull(ext.extract(this));
    }

    private static class MyClass {
        @SuppressWarnings("unused")
        public String value = VALUE;

        @SuppressWarnings("unused")
        public int value2 = INT_VALUE;
    }
}
