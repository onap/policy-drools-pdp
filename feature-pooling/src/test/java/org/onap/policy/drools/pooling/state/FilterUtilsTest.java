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

package org.onap.policy.drools.pooling.state;

import static org.junit.Assert.assertEquals;
import static org.onap.policy.drools.pooling.state.FilterUtils.CLASS_AND;
import static org.onap.policy.drools.pooling.state.FilterUtils.CLASS_EQUALS;
import static org.onap.policy.drools.pooling.state.FilterUtils.CLASS_OR;
import static org.onap.policy.drools.pooling.state.FilterUtils.JSON_CLASS;
import static org.onap.policy.drools.pooling.state.FilterUtils.JSON_FIELD;
import static org.onap.policy.drools.pooling.state.FilterUtils.JSON_FILTERS;
import static org.onap.policy.drools.pooling.state.FilterUtils.JSON_VALUE;
import static org.onap.policy.drools.pooling.state.FilterUtils.makeAnd;
import static org.onap.policy.drools.pooling.state.FilterUtils.makeEquals;
import static org.onap.policy.drools.pooling.state.FilterUtils.makeOr;
import java.util.Map;
import org.junit.Test;

public class FilterUtilsTest {

    @Test
    public void testMakeEquals() {
        checkEquals("abc", "def", makeEquals("abc", "def"));
    }

    @Test
    public void testMakeAnd() {
        @SuppressWarnings("unchecked")
        Map<String, Object> filter =
                        makeAnd(makeEquals("an1", "av1"), makeEquals("an2", "av2"), makeEquals("an3", "av3"));

        checkArray(CLASS_AND, 3, filter);
        checkEquals("an1", "av1", getItem(filter, 0));
        checkEquals("an2", "av2", getItem(filter, 1));
        checkEquals("an3", "av3", getItem(filter, 2));
    }

    @Test
    public void testMakeOr() {
        @SuppressWarnings("unchecked")
        Map<String, Object> filter =
                        makeOr(makeEquals("on1", "ov1"), makeEquals("on2", "ov2"), makeEquals("on3", "ov3"));

        checkArray(CLASS_OR, 3, filter);
        checkEquals("on1", "ov1", getItem(filter, 0));
        checkEquals("on2", "ov2", getItem(filter, 1));
        checkEquals("on3", "ov3", getItem(filter, 2));
    }

    /**
     * Checks that the filter contains an array.
     * 
     * @param expectedClassName type of filter this should represent
     * @param expectedCount number of items expected in the array
     * @param filter filter to be examined
     */
    protected void checkArray(String expectedClassName, int expectedCount, Map<String, Object> filter) {
        assertEquals(expectedClassName, filter.get(JSON_CLASS));

        Object[] val = (Object[]) filter.get(JSON_FILTERS);
        assertEquals(expectedCount, val.length);
    }

    /**
     * Checks that a map represents an "equals".
     * 
     * @param name name of the field on the left side of the equals
     * @param value value on the right side of the equals
     * @param map map whose content is to be examined
     */
    protected void checkEquals(String name, String value, Map<String, Object> map) {
        assertEquals(CLASS_EQUALS, map.get(JSON_CLASS));
        assertEquals(name, map.get(JSON_FIELD));
        assertEquals(value, map.get(JSON_VALUE));
    }

    /**
     * Gets a particular sub-filter from the array contained within a filter.
     * 
     * @param filter containing filter
     * @param index index of the sub-filter of interest
     * @return the sub-filter with the given index
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> getItem(Map<String, Object> filter, int index) {
        Object[] val = (Object[]) filter.get(JSON_FILTERS);

        return (Map<String, Object>) val[index];
    }

}
