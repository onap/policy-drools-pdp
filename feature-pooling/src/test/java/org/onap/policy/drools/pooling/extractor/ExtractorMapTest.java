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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.function.Function;
import org.junit.Before;
import org.junit.Test;

public class ExtractorMapTest {

    private static final int NTIMES = 5;

    private static final String MY_TYPE = "theType";
    private static final String PROP_PREFIX = "extractor." + MY_TYPE + ".";

    private static final String VALUE = "a value";
    private static final Integer INT_VALUE = 10;
    private static final Integer INT_VALUE2 = 20;

    private Properties props;
    private ExtractorMap map;

    @Before
    public void setUp() {
        props = new Properties();

        props.setProperty(PROP_PREFIX + Simple.class.getName(), "${intValue}");
        props.setProperty(PROP_PREFIX + WithString.class.getName(), "${strValue}");

        map = new ExtractorMap(props, MY_TYPE);
    }

    @Test
    public void testExtract() {
        Simple obj = new Simple();
        assertEquals(INT_VALUE, map.extract(obj));

        // string value
        assertEquals(VALUE, tryIt(Simple.class, "${strValue}", xxx -> new Simple()));

        // null object
        assertNull(map.extract(null));

        // values from two different kinds of objects
        props = new Properties();
        props.setProperty(PROP_PREFIX + Simple.class.getName(), "${intValue}");
        props.setProperty(PROP_PREFIX + WithString.class.getName(), "${strValue}");
        map = new ExtractorMap(props, MY_TYPE);

        assertEquals(INT_VALUE, map.extract(new Simple()));
        assertEquals(VALUE, map.extract(new Sub()));
    }

    @Test
    public void testGetExtractor() {
        Simple obj = new Simple();

        // repeat - shouldn't re-create the extractor
        for (int x = 0; x < NTIMES; ++x) {
            assertEquals("x=" + x, INT_VALUE, map.extract(obj));
            assertEquals("x=" + x, 1, map.size());
        }
    }

    @Test
    public void testNullExtractorExtract() {
        // empty properties - should only create NullExtractor
        map = new ExtractorMap(new Properties(), MY_TYPE);

        Simple obj = new Simple();

        // repeat - shouldn't re-create the extractor
        for (int x = 0; x < NTIMES; ++x) {
            assertNull("x=" + x, map.extract(obj));
            assertEquals("x=" + x, 1, map.size());
        }
    }

    @Test
    public void testDelayedExtractorBuildExtractor_TopLevel() {
        // extractor defined for top-level class
        props = new Properties();
        props.setProperty(PROP_PREFIX + Sub.class.getName(), "${strValue}");

        map = new ExtractorMap(props, MY_TYPE);
        assertEquals(VALUE, map.extract(new Sub()));

        // one extractor for top-level class
        assertEquals(1, map.size());
    }

    @Test
    public void testDelayedExtractorBuildExtractor_SuperClass() {
        // extractor defined for superclass (interface)
        assertEquals(VALUE, map.extract(new Sub()));

        // one extractor for top-level class and one for interface
        assertEquals(2, map.size());
    }

    @Test
    public void testDelayedExtractorBuildExtractor_NotDefined() {
        // no extractor defined for "this" class
        assertNull(map.extract(this));

        // one NULL extractor for top-level class
        assertEquals(1, map.size());
    }

    @Test
    public void testDelayedExtractorBuildExtractorString() {
        // no leading "${"
        assertNull(tryIt(Simple.class, "intValue}", xxx -> new Simple()));

        // no trailing "}"
        assertNull(tryIt(Simple.class, "${intValue", xxx -> new Simple()));

        // leading "."
        assertNull(tryIt(Sub.class, "${.simple.strValue}", xxx -> new Sub()));

        // trailing "."
        assertNull(tryIt(Sub.class, "${simple.strValue.}", xxx -> new Sub()));

        // one component
        assertEquals(VALUE, tryIt(Sub.class, "${strValue}", xxx -> new Sub()));

        // two components
        assertEquals(VALUE, tryIt(Sub.class, "${simple.strValue}", xxx -> new Sub()));

        // invalid component
        assertNull(tryIt(Sub.class, "${unknown}", xxx -> new Sub()));
    }

    @Test
    public void testDelayedExtractorGetClassExtractor_InSuper() {
        // field in the superclass
        assertEquals(INT_VALUE, tryIt(Super.class, "${intValue}", xxx -> new Sub()));
    }

    @Test
    public void testDelayedExtractorGetClassExtractor_InInterface() {
        // defined in the interface
        assertEquals(VALUE, map.extract(new Sub()));
    }

    @Test
    public void testComponetizedExtractor() {
        // one component
        assertEquals(VALUE, tryIt(Sub.class, "${strValue}", xxx -> new Sub()));

        // three components
        assertEquals(VALUE, tryIt(Sub.class, "${cont.data.strValue}", xxx -> new Sub()));
    }

    @Test
    public void testComponetizedExtractorBuildExtractor_Method() {
        assertEquals(INT_VALUE, tryIt(Simple.class, "${intValue}", xxx -> new Simple()));
    }

    @Test
    public void testComponetizedExtractorBuildExtractor_Field() {
        assertEquals(VALUE, tryIt(Simple.class, "${strValue}", xxx -> new Simple()));
    }

    @Test
    public void testComponetizedExtractorBuildExtractor_Map() {
        Map<String, Object> inner = new TreeMap<>();
        inner.put("inner1", "abc1");
        inner.put("inner2", "abc2");

        Map<String, Object> outer = new TreeMap<>();
        outer.put("outer1", "def1");
        outer.put("outer2", inner);

        Simple obj = new Simple();

        props.setProperty(PROP_PREFIX + Simple.class.getName(), "${mapValue}");
        map = new ExtractorMap(props, MY_TYPE);
        assertEquals(null, map.extract(obj));

        obj.mapValue = outer;
        props.setProperty(PROP_PREFIX + Simple.class.getName(), "${mapValue.outer2.inner2}");
        map = new ExtractorMap(props, MY_TYPE);
        assertEquals("abc2", map.extract(obj));
    }

    @Test
    public void testComponetizedExtractorBuildExtractor_Unknown() {
        assertNull(tryIt(Simple.class, "${unknown2}", xxx -> new Simple()));
    }

    @Test
    public void testComponetizedExtractorExtract_MiddleNull() {
        // data component is null
        assertEquals(null, tryIt(Sub.class, "${cont.data.strValue}", xxx -> {
            Sub obj = new Sub();
            obj.cont.simpleValue = null;
            return obj;
        }));
    }

    @Test
    public void testComponetizedExtractorGetMethodExtractor_VoidMethod() {
        // tell it to use getVoidValue()
        props.setProperty(PROP_PREFIX + Simple.class.getName(), "${voidValue}");
        map = new ExtractorMap(props, MY_TYPE);

        Simple obj = new Simple();
        assertNull(map.extract(obj));

        assertFalse(obj.voidInvoked);
    }

    @Test
    public void testComponetizedExtractorGetMethodExtractor() {
        assertEquals(INT_VALUE, map.extract(new Simple()));
    }

    @Test
    public void testComponetizedExtractorGetFieldExtractor() {
        // use a field
        assertEquals(VALUE, tryIt(Simple.class, "${strValue}", xxx -> new Simple()));
    }

    @Test
    public void testComponetizedExtractorGetMapExtractor() {
        Map<String, Object> inner = new TreeMap<>();
        inner.put("inner1", "abc1");
        inner.put("inner2", "abc2");

        Map<String, Object> outer = new TreeMap<>();
        outer.put("outer1", "def1");
        outer.put("outer2", inner);

        Simple obj = new Simple();

        obj.mapValue = outer;
        props.setProperty(PROP_PREFIX + Simple.class.getName(), "${mapValue.outer2.inner2}");
        map = new ExtractorMap(props, MY_TYPE);
        assertEquals("abc2", map.extract(obj));
    }

    @Test
    public void testComponetizedExtractorGetMapExtractor_MapSubclass() {
        Map<String, Object> inner = new TreeMap<>();
        inner.put("inner1", "abc1");
        inner.put("inner2", "abc2");

        MapSubclass outer = new MapSubclass();
        outer.put("outer1", "def1");
        outer.put("outer2", inner);

        Simple obj = new Simple();

        props.setProperty(PROP_PREFIX + Simple.class.getName(), "${mapValue}");
        map = new ExtractorMap(props, MY_TYPE);
        assertEquals(null, map.extract(obj));

        obj.mapValue = outer;
        props.setProperty(PROP_PREFIX + Simple.class.getName(), "${mapValue.outer2.inner2}");
        map = new ExtractorMap(props, MY_TYPE);
        assertEquals("abc2", map.extract(obj));
    }

    /**
     * Sets a property for the given class, makes an object, and then returns
     * the value extracted.
     * 
     * @param clazz class whose property is to be set
     * @param propval value to which to set the property
     * @param makeObj function to create the object whose data is to be
     *        extracted
     * @return the extracted data, or {@code null} if nothing was extracted
     */
    private Object tryIt(Class<?> clazz, String propval, Function<Void, Object> makeObj) {
        Properties props = new Properties();
        props.setProperty(PROP_PREFIX + clazz.getName(), propval);

        map = new ExtractorMap(props, MY_TYPE);

        return map.extract(makeObj.apply(null));
    }

    /**
     * A Map subclass, used to verify that getMapExtractor() still handles it.
     */
    private static class MapSubclass extends TreeMap<String, Object> {
        private static final long serialVersionUID = 1L;

    }

    /**
     * A simple class.
     */
    private static class Simple {

        /**
         * This will not be used because getIntValue() will override it.
         */
        @SuppressWarnings("unused")
        private int intValue = INT_VALUE2;

        /**
         * Used to verify retrieval via a field name.
         */
        @SuppressWarnings("unused")
        private String strValue = VALUE;

        /**
         * Used to verify retrieval within maps.
         */
        @SuppressWarnings("unused")
        private Map<String, Object> mapValue = null;

        /**
         * {@code True} if {@link #getVoidValue()} was invoked, {@code false}
         * otherwise.
         */
        private boolean voidInvoked = false;

        /**
         * This function will supercede the value in the "intValue" field.
         * 
         * @return INT_VALUE
         */
        @SuppressWarnings("unused")
        public Integer getIntValue() {
            return INT_VALUE;
        }

        /**
         * Used to verify that void functions are not invoked.
         */
        @SuppressWarnings("unused")
        public void getVoidValue() {
            voidInvoked = true;
        }
    }

    /**
     * Used to verify multi-component retrieval.
     */
    private static class Container {
        private Simple simpleValue = new Simple();

        @SuppressWarnings("unused")
        public Simple getData() {
            return simpleValue;
        }
    }

    /**
     * Used to verify extraction when the property refers to an interface.
     */
    private static interface WithString {

        public String getStrValue();
    }

    /**
     * Used to verify retrieval within a superclass.
     */
    private static class Super implements WithString {

        @SuppressWarnings("unused")
        private int intValue = INT_VALUE;

        @Override
        public String getStrValue() {
            return VALUE;
        }
    }

    /**
     * Used to verify retrieval within a subclass.
     */
    private static class Sub extends Super {

        @SuppressWarnings("unused")
        private Simple simple = new Simple();

        /**
         * Used to verify multi-component retrieval.
         */
        private Container cont = new Container();
    }
}
