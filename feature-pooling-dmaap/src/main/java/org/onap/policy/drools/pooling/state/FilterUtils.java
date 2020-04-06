/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018-2020 AT&T Intellectual Property. All rights reserved.
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

import java.util.Map;
import java.util.TreeMap;

/**
 * Filter Utilities. These methods create <i>TreeMap</i> objects, because they
 * should only contain a small number of items.
 */
public class FilterUtils {
    // message element names
    public static final String MSG_CHANNEL = "channel";
    public static final String MSG_TIMESTAMP = "timestampMs";

    // json element names
    protected static final String JSON_CLASS = "class";
    protected static final String JSON_FILTERS = "filters";
    protected static final String JSON_FIELD = "field";
    protected static final String JSON_VALUE = "value";

    // values to be stuck into the "class" element
    protected static final String CLASS_OR = "Or";
    protected static final String CLASS_AND = "And";
    protected static final String CLASS_EQUALS = "Equals";

    /**
     * Constructor.
     */
    private FilterUtils() {
        super();
    }

    /**
     * Makes a filter that verifies that a field equals a value.
     *
     * @param field name of the field to check
     * @param value desired value
     * @return a map representing an "equals" filter
     */
    public static Map<String, Object> makeEquals(String field, String value) {
        Map<String, Object> map = new TreeMap<>();
        map.put(JSON_CLASS, CLASS_EQUALS);
        map.put(JSON_FIELD, field);
        map.put(JSON_VALUE, value);

        return map;
    }

    /**
     * Makes an "and" filter, where all of the items must be true.
     *
     * @param items items to be checked
     * @return an "and" filter
     */
    public static Map<String, Object> makeAnd(@SuppressWarnings("unchecked") Map<String, Object>... items) {
        Map<String, Object> map = new TreeMap<>();
        map.put(JSON_CLASS, CLASS_AND);
        map.put(JSON_FILTERS, items);

        return map;
    }

    /**
     * Makes an "or" filter, where at least one of the items must be true.
     *
     * @param items items to be checked
     * @return an "or" filter
     */
    public static Map<String, Object> makeOr(@SuppressWarnings("unchecked") Map<String, Object>... items) {
        Map<String, Object> map = new TreeMap<>();
        map.put(JSON_CLASS, CLASS_OR);
        map.put(JSON_FILTERS, items);

        return map;
    }
}
