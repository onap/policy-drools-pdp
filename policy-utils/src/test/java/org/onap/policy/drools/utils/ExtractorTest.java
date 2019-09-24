/*
 * ============LICENSE_START=======================================================
 * ONAP
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

package org.onap.policy.drools.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import lombok.Getter;
import org.junit.Test;
import org.onap.policy.drools.utils.Extractor;

public class ExtractorTest {
    private static final String PREFIX = "my.prefix.";
    private static final int INTVAL = 10;
    private static final String STRVAL = "hello";
    private static final String STRVAL2 = "world";

    @Test
    public void test() {

        /*
         * Test register() with different properties.
         */

        Properties props = new Properties();

        // wrong prefix - property should be ignored
        props.setProperty("other.prefix.", "");

        // empty class name - property should be ignored
        props.setProperty(PREFIX, "v.someVal");

        // unknown field
        props.setProperty(PREFIX + MyObject.class.getName(), "v.unknownField, v.strVal");

        props.setProperty(PREFIX + MyInterface.class.getName(), "v.intVal");

        props.setProperty(PREFIX + WithMap.class.getName(), "v.data.text");


        Extractor extractor = new Extractor();
        extractor.register(props, PREFIX);


        /*
         * Provide alternative scripts for MyObject.
         */

        extractor.register(MyObject.class.getName(), "v.strVal2 ");


        /*
         * Test extract().
         */

        assertNull(extractor.extract(null));
        assertNull(extractor.extract(new Object()));
        assertEquals(STRVAL, extractor.extract(new MyObject()));
        assertEquals(STRVAL, extractor.extract(new SubObject()));
        assertEquals(String.valueOf(INTVAL), extractor.extract(new WithInterface()));

        // superclass extractor takes precedence over interface extractor
        assertEquals(STRVAL, extractor.extract(new Both()));

        // should fall back to second script because strVal=null
        Both both = new Both();
        both.strVal = null;
        assertEquals(STRVAL2, extractor.extract(both));

        // check with missing properties
        WithMap map = new WithMap();
        assertNull(extractor.extract(map));

        map.data = new TreeMap<>();
        assertNull(extractor.extract(map));

        map.data.put("text", "abc");
        assertEquals("abc", extractor.extract(map));
    }

    @Getter
    public static class MyObject {
        int intVal = INTVAL;
        String strVal = STRVAL;
        String strVal2 = STRVAL2;
    }

    public static class SubObject extends MyObject {

    }

    public static interface MyInterface {
        public int getIntVal();
    }

    @Getter
    public static class WithInterface implements MyInterface {
        int intVal = INTVAL;
    }

    public static class Both extends SubObject implements MyInterface {

    }

    @Getter
    public static class WithMap {
        private Map<String, String> data;
    }
}
