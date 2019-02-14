/*-
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2017-2018 AT&T Intellectual Property. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;


public class JsonProtocolFilterTest {

    public final String json = "{\"requestID\":\"38adde30-cc22-11e8-a8d5-f2801f1b9fd1\",\"entity\":\"controller\","
            + "\"controllers\":[{\"name\":\"test-controller\","
            + "\"drools\":{\"groupId\":\"org.onap.policy.drools.test\","
            + "\"artifactId\":\"test\",\"version\":\"0.0.1\"},\"operation\":\"update\"}]}";

    /**
     * Tests that the a valid rule can be added.
     */
    @Test
    public void hasRulesTrueTest() {
        assertTrue(new JsonProtocolFilter("$.test").hasRule());
    }

    /**
     * Tests that an empty rule will not be added.
     */
    @Test
    public void hasRulesEmptyTest() {
        assertFalse(new JsonProtocolFilter("").hasRule());
    }

    /**
     * Tests that an null rule will not be added.
     */
    @Test
    public void hasRulesNullTest() {
        assertFalse(new JsonProtocolFilter(null).hasRule());
    }

    /**
     * Tests getting the rule expression of the filter.
     */
    @Test
    public void getRuleTest() {
        assertEquals("$.test", new JsonProtocolFilter("$.test").getRule());
    }

    /**
     * Tests setting the rule expression of the filter.
     */
    @Test
    public void setRuleTest() {
        JsonProtocolFilter filter = new JsonProtocolFilter();
        assertFalse(filter.hasRule());
        filter.setRule("$.test");
        assertTrue(filter.hasRule());
    }

    /**
     * Tests that the rule expression cannot be set to an empty string.
     */
    @Test(expected = IllegalArgumentException.class)
    public void setRuleEmptyTest() {
        new JsonProtocolFilter("$.test").setRule("");
    }

    /**
     * Tests that the rule expression cannot be set to null.
     */
    @Test(expected = IllegalArgumentException.class)
    public void setRuleNullTest() {
        new JsonProtocolFilter("$.test").setRule(null);
    }

    /**
     * Tests deleting the rule expression of the filter.
     */
    @Test
    public void deleteRuleTest() {
        JsonProtocolFilter filter = new JsonProtocolFilter("$.test");
        assertTrue(filter.hasRule());
        filter.deleteRule();
        assertFalse(filter.hasRule());
    }

    /**
     * Tests accepting a message if all filter rules pass.
     */
    @Test
    public void acceptPassTest() {
        assertTrue(new JsonProtocolFilter(
                        "$.controllers[?(@.drools.version =~ /\\d\\.\\d\\.\\d/ && @.operation == 'update')]")
                                        .accept(json));
    }

    /**
     * Tests rejecting a message if one or more of the filter rules fail.
     */
    @Test
    public void acceptFailTest() {
        assertFalse(
            new JsonProtocolFilter("$.controllers[?(@.drools.version =~ /\\\\d\\\\.\\\\d\\\\.2/)]")
                .accept(json));
    }

    /**
     * Tests finding field matches for a filter rule corresponding to a topic.
     */
    @Test
    public void filterPassTest() {
        assertEquals("38adde30-cc22-11e8-a8d5-f2801f1b9fd1", new JsonProtocolFilter("$.requestID").filter(json).get(0));
    }

    /**
     * Tests that an empty list is returned when no matches are found.
     */
    @Test
    public void filterFailTest() {
        assertTrue(new JsonProtocolFilter("$.test").filter(json).isEmpty());
    }

    /**
     * Tests static method for filtering a JSON string with an arbitrary expression.
     */
    @Test
    public void staticFilterPassTest() {
        assertEquals("controller", JsonProtocolFilter.filter(json, "$.entity").get(0));
    }

    /**
     * Tests that an empty list is returned when the static filter() method does not find any matches.
     */
    @Test
    public void staticFilterFailTest() {
        assertTrue(JsonProtocolFilter.filter(json, "$.test").isEmpty());
    }
}