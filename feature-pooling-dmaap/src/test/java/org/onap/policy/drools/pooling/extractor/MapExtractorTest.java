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

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class MapExtractorTest {
    private static final String KEY = "a.key";
    private static final String VALUE = "a.value";

    private MapExtractor ext;

    @Before
    public void setUp() {
        ext = new MapExtractor(KEY);
    }

    @Test
    public void testExtract_NotAMap() {

        // object is not a map (i.e., it's a String)
        assertNull(ext.extract(KEY));
    }

    @Test
    public void testExtract_MissingValue() {

        Map<String, Object> map = new HashMap<>();
        map.put(KEY + "x", VALUE + "x");

        // object is a map, but doesn't have the key
        assertNull(ext.extract(map));
    }

    @Test
    public void testExtract() {

        Map<String, Object> map = new HashMap<>();
        map.put(KEY + "x", VALUE + "x");
        map.put(KEY, VALUE);

        // object is a map and contains the key
        assertEquals(VALUE, ext.extract(map));

        // change to value to a different type
        map.put(KEY, 20);
        assertEquals(20, ext.extract(map));
    }

}
