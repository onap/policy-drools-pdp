/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2017-2019 AT&T Intellectual Property. All rights reserved.
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

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** JSON Protocol Filter. */
public class JsonProtocolFilter {

    /** Default filter to match anything. */
    public static final String MATCH_ANY = "[?($ =~ /.*/)]";

    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(JsonProtocolFilter.class);

    /** A rule based on a JsonPath expression that is used for filtering. */
    private String rule;

    /**
     * Default constructor (for serialization only).
     */
    public JsonProtocolFilter() {
        super();
        this.setRule(null);
    }

    /**
     * Constructor.
     *
     * @param rule the JsonPath expression used for the filter rule
     * @throws IllegalArgumentException an invalid input has been provided
     */
    public JsonProtocolFilter(String rule) {
        this.setRule(rule);
    }

    /**
     * Gets the filter expression rule.
     *
     * @return the filter expression associated with this JsonProtocolFilter
     */
    public String getRule() {
        return this.rule;
    }

    /**
     * Sets the filter expression rule.
     *
     * @param rule the JsonPath expression rule
     */
    public void setRule(String rule) {
        String ruleExpression = rule;
        if (rule == null || rule.isEmpty()) {
            ruleExpression = MATCH_ANY;
        }
        this.rule = ruleExpression;
    }

    /**
     * Accepts a JSON message if there is a match on the filter expression.
     *
     * @return true if a match is found or the rule uses the match any policy, false otherwise
     */
    public boolean accept(String json) {
        if (MATCH_ANY.equals(this.rule)) {
            return true;
        }
        return !filter(json).isEmpty();
    }

    /**
     * Finds a field based on a path or a subset of the JSON if using an expression.
     *
     * @param json the JSON string to be parsed
     * @return a list of strings that match the expression
     */
    public List<String> filter(String json) {
        return filter(json, this.rule);
    }

    /**
     * Finds all occurrences of a field in a JSON document based on the JsonPath
     * expression.
     *
     * @param json the JSON string to be parsed
     * @param expression the JsonPath expression
     * @return a list of matches from the JSON document
     */
    public static List<String> filter(String json, String expression) {
        if (json == null || json.isEmpty()) {
            throw new IllegalArgumentException("a json string must be provided");
        }

        if (expression == null || expression.isEmpty()) {
            throw new IllegalArgumentException("an expression must be provided");
        }

        Configuration conf = Configuration.defaultConfiguration().addOptions(Option.ALWAYS_RETURN_LIST);
        DocumentContext document = JsonPath.using(conf).parse(json);

        List<String> matches = new ArrayList<>();
        try {
            matches = document.read(expression);
        } catch (Exception e) {
            logger.error("JsonPath couldn't read {} because of {}", expression, e.getMessage(), e);
        }

        if (matches.isEmpty()) {
            logger.warn("Could not find any matches for rule {} in json {}", expression, json);
        }

        return matches;
    }
}
