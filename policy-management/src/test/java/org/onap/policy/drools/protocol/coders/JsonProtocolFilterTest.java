/*-
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2017-2019 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2024 Nordix Foundation.
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

package org.onap.policy.drools.protocol.coders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JsonProtocolFilterTest {

    private static final String JSON =
                    "{\"requestID\":\"38adde30-cc22-11e8-a8d5-f2801f1b9fd1\",\"entity\":\"controller\","
                                    + "\"controllers\":[{\"name\":\"test-controller\","
                                    + "\"drools\":{\"groupId\":\"org.onap.policy.drools.test\","
                                    + "\"artifactId\":\"test\",\"version\":\"0.0.1\"},\"operation\":\"update\"}]}";

    /**
     * Tests getting the rule expression of the filter.
     */
    @Test
    void getRuleTest() {
        assertEquals("$.test", new JsonProtocolFilter("$.test").getRule());
    }

    /**
     * Tests setting the rule expression of the filter.
     */
    @Test
    void setRuleTest() {
        JsonProtocolFilter filter = new JsonProtocolFilter();
        assertEquals(JsonProtocolFilter.MATCH_ANY, filter.getRule());
        filter.setRule("$.test");
        assertEquals("$.test", filter.getRule());
    }

    /**
     * Tests that the rule expression will be set to match anything if an empty string is passed.
     */
    @Test
    void setRuleEmptyTest() {
        assertEquals(JsonProtocolFilter.MATCH_ANY, new JsonProtocolFilter("").getRule());
    }

    /**
     * Tests that the rule expression will be set to match anything if a null string is passed.
     */
    @Test
    void setRuleNullTest() {
        assertEquals(JsonProtocolFilter.MATCH_ANY, new JsonProtocolFilter(null).getRule());
    }

    /**
     * Tests accepting a message if all filter rules pass.
     */
    @Test
    void acceptPassTest() {
        assertTrue(new JsonProtocolFilter(
                        "$.controllers[?(@.drools.version =~ /\\d\\.\\d\\.\\d/ && @.operation == 'update')]")
                                        .accept(JSON));
    }

    /**
     * Tests accepting a message without having to filter if the rule is set to match anything.
     */
    @Test
    void acceptAnyTest() {
        assertTrue(new JsonProtocolFilter(null).accept(JSON));
    }

    /**
     * Tests rejecting a message if one or more of the filter rules fail.
     */
    @Test
    void acceptFailTest() {
        assertFalse(
            new JsonProtocolFilter("$.controllers[?(@.drools.version =~ /\\\\d\\\\.\\\\d\\\\.2/)]")
                .accept(JSON));
    }

    /**
     * Tests finding field matches for a filter rule corresponding to a topic.
     */
    @Test
    void filterPassTest() {
        assertEquals("38adde30-cc22-11e8-a8d5-f2801f1b9fd1", new JsonProtocolFilter("$.requestID").filter(JSON).get(0));
    }

    /**
     * Tests that an empty list is returned when no matches are found.
     */
    @Test
    void filterFailTest() {
        assertTrue(new JsonProtocolFilter("$.test").filter(JSON).isEmpty());
    }

    /**
     * Tests static method for filtering a JSON string with an arbitrary expression.
     */
    @Test
    void staticFilterPassTest() {
        assertEquals("controller", JsonProtocolFilter.filter(JSON, "$.entity").get(0));
    }

    /**
     * Tests that an empty list is returned when the static filter() method does not find any matches.
     */
    @Test
    void staticFilterFailTest() {
        assertTrue(JsonProtocolFilter.filter(JSON, "$.test").isEmpty());
    }

    /**
     * Tests that an exception is thrown if a null JSON string is passed.
     */
    @Test
    void staticFilterNullJsonTest() {
        assertThrows(IllegalArgumentException.class, () -> JsonProtocolFilter.filter(null, "[?($ =~ /.*/"));
    }

    /**
     * Tests that an exception is thrown if an empty JSON string is passed.
     */
    @Test
    void staticFilterEmptyJsonTest() {
        assertThrows(IllegalArgumentException.class, () -> JsonProtocolFilter.filter("", "[?($ =~ /.*/"));
    }

    /**
     * Tests that an exception is thrown if a null expression string is passed.
     */
    @Test
    void staticFilterNullExpressionTest() {
        assertThrows(IllegalArgumentException.class, () -> JsonProtocolFilter.filter("{\"hello\":\"world\"}", null));
    }

    /**
     * Tests that an exception is thrown if an empty expression string is passed.
     */
    @Test
    void staticFilterEmptyExpressionTest() {
        assertThrows(IllegalArgumentException.class, () -> JsonProtocolFilter.filter("{\"hello\":\"world\"}", ""));
    }
}