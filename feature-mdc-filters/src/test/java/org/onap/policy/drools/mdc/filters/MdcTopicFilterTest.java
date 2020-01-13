/*
 * ============LICENSE_START=======================================================
 * feature-mdc-filters
 * ================================================================================
 * Copyright (C) 2019-2020 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.mdc.filters;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.onap.policy.drools.mdc.filters.MdcTopicFilter.FilterRule;

public class MdcTopicFilterTest {

    /**
     * Test the simple case of having one filter rule for a key.
     */
    @Test
    public void singleFilterOnePathTest() {
        String topicFilterProp = "requestID=$.requestID";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);

        FilterRule rule = topicFilter.getFilterRule("requestID");
        assertEquals("requestID", rule.getMdcKey());
        assertEquals("[$.requestID]", rule.getPaths().toString());
    }

    /**
     * Tests having one filter rule with a set of potential paths to the key.
     */
    @Test
    public void singleFilterMultiPathTest() {
        String topicFilterProp = "requestID=$.requestID|$.request-id";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);

        FilterRule rule = topicFilter.getFilterRule("requestID");
        assertEquals("requestID", rule.getMdcKey());
        assertEquals(2, rule.getPaths().size());
        assertEquals("[$.requestID, $.request-id]", rule.getPaths().toString());
    }

    /**
     * Tests having two filter rules that each have one key/path pair.
     */
    @Test
    public void multiFilterSinglePathTest() {
        String topicFilterProp = "requestID=$.requestID,closedLoopControlName=$.closedLoopControlName";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);

        FilterRule rule = topicFilter.getFilterRule("requestID");
        assertEquals("requestID", rule.getMdcKey());
        assertEquals(1, rule.getPaths().size());
        assertEquals("[$.requestID]", rule.getPaths().toString());

        FilterRule rule2 = topicFilter.getFilterRule("closedLoopControlName");
        assertEquals("closedLoopControlName", rule2.getMdcKey());
        assertEquals(1, rule2.getPaths().size());
        assertEquals("[$.closedLoopControlName]", rule2.getPaths().toString());
    }

    /**
     * Tests having two filter rules that each have two key/path pairs.
     */
    @Test
    public void multiFilterMultiPathTest() {
        String topicFilterProp = "requestID=$.requestID|$.body.request-id,"
                + "closedLoopControlName=$.closedLoopControlName"
                + "|$.body.closedLoopControlName";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);

        FilterRule rule = topicFilter.getFilterRule("requestID");
        assertEquals("requestID", rule.getMdcKey());
        assertEquals(2, rule.getPaths().size());
        assertEquals("[$.requestID, $.body.request-id]", rule.getPaths().toString());

        FilterRule rule2 = topicFilter.getFilterRule("closedLoopControlName");
        assertEquals("closedLoopControlName", rule2.getMdcKey());
        assertEquals(2, rule2.getPaths().size());
        assertEquals("[$.closedLoopControlName, $.body.closedLoopControlName]", rule2.getPaths().toString());
    }

    /**
     * Tests that the regex split logic for '|' in the feature code doesn't
     * break parsing when "||" is used as a predicate in a JsonPath query.
     */
    @Test
    public void addOrPredicateFilterTest() {
        String topicFilterProp = "requestID=$.requestID||$.body.requestID";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);
        assertEquals(1, topicFilter.getFilterRule().size());
        assertEquals("requestID", topicFilter.getFilterRule("requestID").getMdcKey());
        assertEquals(Arrays.asList("$.requestID||$.body.requestID"), topicFilter
                .getFilterRule("requestID").getPaths());
    }

    /**
     * Tests getting all filter rules for a given topic.
     */
    @Test
    public void getAllFilterRulesTest() {
        String topicFilterProp = "requestID=$.requestID,subRequestID=$.subRequestID,"
                + "closedLoopControlName=$.closedLoopControlName";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);
        assertEquals(3, topicFilter.getFilterRule().size());
    }

    /**
     * Tests getting a filter rule by its key.
     */
    @Test
    public void getFilterRuleTest() {
        String topicFilterProp = "requestID=$.requestID,subRequestID=$.subRequestID,"
                + "closedLoopControlName=$.closedLoopControlName";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);

        FilterRule rule = topicFilter.getFilterRule("requestID");
        assertNotNull(rule);
    }

    /**
     * Tests throwing an exception for passing in a null key.
     */
    @Test(expected = IllegalArgumentException.class)
    public void getFilterRuleNullKeyTest() {
        String topicFilterProp = "requestID=$.requestID,subRequestID=$.subRequestID";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);
        topicFilter.getFilterRule(null);
    }

    /**
     * Tests throwing an exception for passing in an empty key.
     */
    @Test(expected = IllegalArgumentException.class)
    public void getFilterRuleEmptyKeyTest() {
        String topicFilterProp = "requestID=$.requestID,subRequestID=$.subRequestID";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);
        topicFilter.getFilterRule("");
    }

    /**
     * Tests adding a filter rule with a single path.
     */
    @Test
    public void addFilterRuleSinglePathTest() {
        String topicFilterProp = "requestID=$.requestID";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);

        String key = "subRequestID";
        String path = "$.subRequestID";
        FilterRule rule = topicFilter.addFilterRule(key, path);
        assertEquals(topicFilter.getFilterRule(key), rule);
    }

    /**
     * Tests adding a filter rule with multiple paths.
     */
    @Test
    public void addFilterRuleMultiPathTest() {
        String topicFilterProp = "requestID=$.requestID";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);

        String key = "subRequestID";
        List<String> paths = Arrays.asList("$.subRequestID", "$.sub-request-id");
        FilterRule rule = topicFilter.addFilterRule(key, paths);
        assertEquals(topicFilter.getFilterRule(key), rule);
    }

    /**
     * Tests throwing an exception for passing a null key and a
     * single path.
     */
    @Test(expected = IllegalArgumentException.class)
    public void addFilterRuleNullKeyStringPathTest() {
        String topicFilterProp = "requestID=$.requestID";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);
        topicFilter.addFilterRule(null, "$.subRequestID");
    }

    /**
     * Tests throwing an exception for passing a null key and a list
     * of paths.
     */
    @Test(expected = IllegalArgumentException.class)
    public void addFilterRuleNullKeyPathListTest() {
        String topicFilterProp = "requestID=$.requestID";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);
        topicFilter.addFilterRule(null, Arrays.asList("$.subRequestID"));
    }

    /**
     * Tests throwing an exception for passing an empty key and
     * a single path.
     */
    @Test(expected = IllegalArgumentException.class)
    public void addFilterRuleEmptyKeyStringPathTest() {
        String topicFilterProp = "requestID=$.requestID";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);
        topicFilter.addFilterRule("", "$.subRequestID");
    }

    /**
     * Tests throwing an exception for passing an empty key and
     * a list of paths.
     */
    @Test(expected = IllegalArgumentException.class)
    public void addFilterRuleEmptyKeyPathListTest() {
        String topicFilterProp = "requestID=$.requestID";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);
        topicFilter.addFilterRule("", Arrays.asList("$.subRequestID"));
    }

    /**
     * Tests throwing an exception for passing an empty path string.
     */
    @Test(expected = IllegalArgumentException.class)
    public void addFilterRuleEmptyPathTest() {
        String topicFilterProp = "requestID=$.requestID";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);
        topicFilter.addFilterRule("subRequestID", "");
    }

    /**
     * Tests throwing an exception for passing an empty paths list.
     */
    @Test(expected = IllegalArgumentException.class)
    public void addFilterRuleEmptyPathsTest() {
        String topicFilterProp = "requestID=$.requestID";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);
        topicFilter.addFilterRule("subRequestID", Arrays.asList());
    }

    /**
     * Tests throwing an exception for trying to add a filter with a key that
     * already exists with a single filter.
     */
    @Test(expected = IllegalArgumentException.class)
    public void addExistingFilterRuleStringTest() {
        String topicFilterProp = "requestID=$.requestID";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);
        topicFilter.addFilterRule("requestID", "$.test");
    }

    /**
     * Tests throwing an exception for trying to add a filter with a key that
     * already exists with a list of filters.
     */
    @Test(expected = IllegalArgumentException.class)
    public void addExistingFilterRuleListTest() {
        String topicFilterProp = "requestID=$.requestID";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);
        topicFilter.addFilterRule("requestID", Arrays.asList("$.test"));
    }

    @Test
    public void createFilterRuleExceptionTest() {
        assertThatIllegalArgumentException().isThrownBy(() -> new MdcTopicFilter("invalid filter"))
                        .withMessage("could not parse filter rule");
    }

    /**
     * Tests modifying a filter rule to add a new path.
     */
    @Test
    public void modifyFilterRuleSinglePathTest() {
        String topicFilterProp = "requestID=$.requestID";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);

        FilterRule rule = topicFilter.modifyFilterRule("requestID", "$.request-id");
        assertEquals(topicFilter.getFilterRule("requestID"), rule);
        assertEquals(Arrays.asList("$.requestID", "$.request-id"), rule.getPaths());
    }

    /**
     * Tests modifying a filter rule to add a list of new paths.
     */
    @Test
    public void modifyFilterRuleMultiPathTest() {
        String topicFilterProp = "requestID=$.requestID";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);

        FilterRule rule = topicFilter.modifyFilterRule("requestID",
                Arrays.asList("$.request-id", "$.requestId"));
        assertEquals(topicFilter.getFilterRule("requestID"), rule);
        assertEquals(
                Arrays.asList("$.requestID", "$.request-id", "$.requestId"),
                rule.getPaths());
    }

    @Test
    public void testModifyFilterRuleMultiPathException() {
        MdcTopicFilter filter = new MdcTopicFilter("abc=$a.value");
        assertThatIllegalArgumentException()
                        .isThrownBy(() -> filter.modifyFilterRule("def", "abc", Arrays.asList("$.b", "$.c")))
                        .withMessage("a filter rule already exists for key: abc");
    }

    /**
     * Tests modifying a filter rule key.
     */
    @Test
    public void modifyFilterRuleKeyTest() {
        String topicFilterProp = "requestID=$.requestID";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);

        FilterRule rule = topicFilter.modifyFilterRule("requestID",
                "request-id", Arrays.asList("$.request-id"));
        assertEquals(topicFilter.getFilterRule("request-id"), rule);
        assertEquals("[$.request-id]", rule.getPaths().toString());
    }

    /**
     * Tests throwing an exception when passing a null key and
     * a single path.
     */
    @Test(expected = IllegalArgumentException.class)
    public void modifyFilterRuleNullKeyStringPathTest() {
        String topicFilterProp = "requestID=$.requestID";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);
        topicFilter.modifyFilterRule(null, "$.request-id");
    }

    /**
     * Tests throwing an exception when passing a null key and
     * a list of multiple paths.
     */
    @Test(expected = IllegalArgumentException.class)
    public void modifyFilterRuleNullKeyPathListTest() {
        String topicFilterProp = "requestID=$.requestID";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);
        topicFilter.modifyFilterRule(null, Arrays.asList("$.request-id"));
    }

    /**
     * Tests throwing an exception when passing an empty key and
     * a single path.
     */
    @Test(expected = IllegalArgumentException.class)
    public void modifyFilterRuleEmptyKeyStringPathTest() {
        String topicFilterProp = "requestID=$.requestID";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);
        topicFilter.modifyFilterRule("", "$.request-id");
    }

    /**
     * Tests throwing an exception when passing an empty key and
     * a list of multiple paths.
     */
    @Test(expected = IllegalArgumentException.class)
    public void modifyFilterRuleEmptyKeyPathListTest() {
        String topicFilterProp = "requestID=$.requestID";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);
        topicFilter.modifyFilterRule("", Arrays.asList("$.request-id"));
    }

    /**
     * Tests throwing an exception when passing an empty string path.
     */
    @Test(expected = IllegalArgumentException.class)
    public void modifyFilterRuleEmptyPathStringTest() {
        String topicFilterProp = "requestID=$.requestID";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);
        topicFilter.modifyFilterRule("requestID", "");
    }

    /**
     * Tests throwing an exception when passing an empty list of paths.
     */
    @Test(expected = IllegalArgumentException.class)
    public void modifyFilterRuleEmptyPathListTest() {
        String topicFilterProp = "requestID=$.requestID";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);
        topicFilter.modifyFilterRule("requestID", Arrays.asList());
    }

    /**
     * Tests throwing an exception when passing a key that is
     * not in the filter rules map and a string path.
     */
    @Test(expected = IllegalArgumentException.class)
    public void modifyFilterRuleMissingKeyStringPathTest() {
        String topicFilterProp = "requestID=$.requestID";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);
        topicFilter.modifyFilterRule("request-id", "$.request-id");
    }

    /**
     * Tests throwing an exception when passing a key that is
     * not in the filter rules map and a list of paths.
     */
    @Test(expected = IllegalArgumentException.class)
    public void modifyFilterRuleMissingKeyPathListTest() {
        String topicFilterProp = "requestID=$.requestID";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);
        topicFilter.modifyFilterRule("request-id", Arrays.asList("$.request-id"));
    }


    /**
     * Tests throwing an exception when passing a null oldKey.
     */
    @Test(expected = IllegalArgumentException.class)
    public void modifyFilterRuleNullOldKeyTest() {
        String topicFilterProp = "requestID=$.requestID";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);
        topicFilter.modifyFilterRule(null, "request-id", Arrays.asList("$.request-id"));
    }

    /**
     * Tests throwing an exception when passing an empty oldKey.
     */
    @Test(expected = IllegalArgumentException.class)
    public void modifyFilterRuleEmptyOldKeyTest() {
        String topicFilterProp = "requestID=$.requestID";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);
        topicFilter.modifyFilterRule("", "request-id", Arrays.asList("$.request-id"));
    }

    /**
     * Tests throwing an exception when passing a null newKey.
     */
    @Test(expected = IllegalArgumentException.class)
    public void modifyFilterRuleNullNewKeyTest() {
        String topicFilterProp = "requestID=$.requestID";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);
        topicFilter.modifyFilterRule("requestID", null, Arrays.asList("$.request-id"));
    }

    /**
     * Tests throwing an exception when passing an empty newKey.
     */
    @Test(expected = IllegalArgumentException.class)
    public void modifyFilterRuleEmptyNewKeyTest() {
        String topicFilterProp = "requestID=$.requestID";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);
        topicFilter.modifyFilterRule("requestID", "", Arrays.asList("$.request-id"));
    }

    /**
     * Tests throwing an exception when the old and new key are the same.
     */
    @Test(expected = IllegalArgumentException.class)
    public void modifyFilterRuleSameKeyTest() {
        String topicFilterProp = "requestID=$.requestID";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);
        topicFilter.modifyFilterRule("requestID", "requestID",
                Arrays.asList("$.request-id"));
    }

    /**
     * Tests throwing an exception when passing an empty paths list.
     */
    @Test(expected = IllegalArgumentException.class)
    public void modifyFilterRuleEmptyPathsTest() {
        String topicFilterProp = "requestID=$.requestID";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);
        topicFilter.modifyFilterRule("requestID", "request-id", Arrays.asList());
    }

    /**
     * Tests throwing an exception when the old key doesn't exist
     * in the rules map.
     */
    @Test(expected = IllegalArgumentException.class)
    public void modifyFilterRuleNonExistingOldKeyTest() {
        String topicFilterProp = "requestID=$.requestID";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);
        topicFilter.modifyFilterRule("request-id", "id", Arrays.asList("$.request-id"));
    }

    /**
     * Tests deleting all filter rules in the rules map.
     */
    @Test
    public void deleteAllFilterRulesTest() {
        String topicFilterProp = "requestID=$.requestID,subRequestID=$.subRequestID,"
                + "closedLoopControlName=$.closedLoopControlName";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);

        assertEquals(3, topicFilter.getFilterRule().size());
        topicFilter.deleteFilterRule();
        assertEquals(0, topicFilter.getFilterRule().size());
    }

    /**
     * Tests deleting a single filter rule by its key from the rules map.
     */
    @Test
    public void deleteFilterRuleTest() {
        String topicFilterProp = "requestID=$.requestID,subRequestID=$.subRequestID,"
                + "closedLoopControlName=$.closedLoopControlName";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);

        assertEquals(3, topicFilter.getFilterRule().size());
        topicFilter.deleteFilterRule("closedLoopControlName");
        assertEquals(2, topicFilter.getFilterRule().size());
    }

    /**
     * Tests throwing an exception if the key is null.
     */
    @Test(expected = IllegalArgumentException.class)
    public void deleteFilterRuleNullKeyTest() {
        String topicFilterProp = "requestID=$.requestID,subRequestID=$.subRequestID,"
                + "closedLoopControlName=$.closedLoopControlName";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);
        topicFilter.deleteFilterRule(null);
    }

    /**
     * Tests throwing an exception if the key is empty.
     */
    @Test(expected = IllegalArgumentException.class)
    public void deleteFilterRuleEmptyKeyTest() {
        String topicFilterProp = "requestID=$.requestID,subRequestID=$.subRequestID,"
                + "closedLoopControlName=$.closedLoopControlName";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);
        topicFilter.deleteFilterRule("");
    }

    /**
     * Tests finding all results for each filter rule corresponding to a topic.
     */
    @Test
    public void findAllTest() {
        String message = "{\"requestID\":\"38adde30-cc22-11e8-a8d5-f2801f1b9fd1\",\"entity\":\"controller\","
                + "\"controllers\":[{\"name\":\"test-controller\","
                + "\"drools\":{\"groupId\":\"org.onap.policy.drools.test\","
                + "\"artifactId\":\"test\",\"version\":\"0.0.1\"},\"operation\":\"update\"}]}";

        String topicFilterProp = "requestID=$.requestID,controllerName=$.controllers[0].name,"
                + "operation=$.controllers[0].operation";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);

        Map<String, List<String>> results = topicFilter.find(message);
        assertEquals("38adde30-cc22-11e8-a8d5-f2801f1b9fd1",
                results.get("requestID").get(0));
        assertEquals("test-controller", results.get("controllerName").get(0));
        assertEquals("update", results.get("operation").get(0));
    }

    @Test
    public void testFindAllNotFound() {
        String message = "{\"requestID\":\"38adde30-cc22-11e8-a8d5-f2801f1b9fd1\",\"entity\":\"controller\","
                        + "\"controllers\":[{\"name\":\"test-controller\","
                        + "\"drools\":{\"groupId\":\"org.onap.policy.drools.test\","
                        + "\"artifactId\":\"test\",\"version\":\"0.0.1\"},\"operation\":\"update\"}]}";

        String topicFilterProp = "requestID=$.requestID[3]";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);

        assertTrue(topicFilter.find(message).get("requestID").isEmpty());
    }

    /**
     * Tests finding field matches for a filter rule corresponding to a topic.
     */
    @Test
    public void findTest() {
        String message = "{\"requestID\":\"38adde30-cc22-11e8-a8d5-f2801f1b9fd1\",\"entity\":\"controller\","
                + "\"controllers\":[{\"name\":\"test-controller\","
                + "\"drools\":{\"groupId\":\"org.onap.policy.drools.test\","
                + "\"artifactId\":\"test\",\"version\":\"0.0.1\"},\"operation\":\"update\"}]}";

        String topicFilterProp = "requestID=$.requestID,controllerName=$.controllers[0].name,"
                + "operation=$.controllers[0].operation";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);

        List<String> results = topicFilter.find(message, "requestID");
        assertEquals("38adde30-cc22-11e8-a8d5-f2801f1b9fd1", results.get(0));
    }

    @Test
    public void testFindNotFound() {
        String message = "{\"requestID\":\"38adde30-cc22-11e8-a8d5-f2801f1b9fd1\",\"entity\":\"controller\","
                + "\"controllers\":[{\"name\":\"test-controller\","
                + "\"drools\":{\"groupId\":\"org.onap.policy.drools.test\","
                + "\"artifactId\":\"test\",\"version\":\"0.0.1\"},\"operation\":\"update\"}]}";

        String topicFilterProp = "requestID=$.requestID[3]";
        MdcTopicFilter topicFilter = new MdcTopicFilter(topicFilterProp);

        assertTrue(topicFilter.find(message, "requestID").isEmpty());
    }

    @Test
    public void testFilterRuleStringString() {
        FilterRule rule = new FilterRule("hello", "world");

        assertEquals("hello", rule.getMdcKey());
        assertEquals("[world]", rule.getPaths().toString());
    }

    @Test
    public void testFilterRuleMdcKey() {
        FilterRule rule = new FilterRule("abc", "def");

        // check error cases first
        assertThatIllegalArgumentException().isThrownBy(() -> rule.setMdcKey(null))
                        .withMessage(MdcTopicFilter.MDC_KEY_ERROR);
        assertThatIllegalArgumentException().isThrownBy(() -> rule.setMdcKey(""))
                        .withMessage(MdcTopicFilter.MDC_KEY_ERROR);

        // success cases
        rule.setMdcKey("my-mdc-key");
        assertEquals("my-mdc-key", rule.getMdcKey());
    }

    @Test
    public void testFilterRulePaths() {
        FilterRule rule = new FilterRule("abc", "def");

        // check error cases first
        assertThatIllegalArgumentException().isThrownBy(() -> rule.setPaths(null))
                        .withMessage(MdcTopicFilter.JSON_PATH_ERROR);
        assertThatIllegalArgumentException().isThrownBy(() -> rule.setPaths(Collections.emptyList()))
                        .withMessage(MdcTopicFilter.JSON_PATH_ERROR);

        assertThatIllegalArgumentException().isThrownBy(() -> rule.addPaths(null))
                        .withMessage(MdcTopicFilter.JSON_PATH_ERROR);
        assertThatIllegalArgumentException().isThrownBy(() -> rule.addPaths(Collections.emptyList()))
                        .withMessage(MdcTopicFilter.JSON_PATH_ERROR);

        assertThatIllegalArgumentException().isThrownBy(() -> rule.addPath(null))
                        .withMessage(MdcTopicFilter.JSON_PATH_ERROR);
        assertThatIllegalArgumentException().isThrownBy(() -> rule.addPath(""))
                        .withMessage(MdcTopicFilter.JSON_PATH_ERROR);

        // success cases
        rule.setPaths(new ArrayList<>(Arrays.asList("pathA", "pathB")));
        assertEquals("[pathA, pathB]", rule.getPaths().toString());

        rule.addPath("pathC");
        assertEquals("[pathA, pathB, pathC]", rule.getPaths().toString());

        rule.addPaths(Arrays.asList("pathD", "pathE"));
        assertEquals("[pathA, pathB, pathC, pathD, pathE]", rule.getPaths().toString());
    }
}
