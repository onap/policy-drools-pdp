/*
 * ============LICENSE_START=======================================================
 * feature-mdc-filters
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

package org.onap.policy.drools.mdc.filters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.onap.policy.drools.protocol.coders.JsonProtocolFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MdcTopicFilter {

    private static final Logger logger = LoggerFactory.getLogger(MdcTopicFilter.class);

    public static final String MDC_KEY_ERROR = "mdcKey must be provided";
    public static final String JSON_PATH_ERROR = "json path(s) must be provided";

    private Map<String, FilterRule> rules = new HashMap<>();

    public static class FilterRule {
        private String mdcKey;
        private List<String> paths;

        public FilterRule(String mdcKey, String path) {
            this.mdcKey = mdcKey;
            this.paths = Arrays.asList(path);
        }

        /**
         * Constructor.
         *
         * @param mdcKey the key to the filter rule
         * @param paths the list of potential paths to the key
         */
        public FilterRule(String mdcKey, List<String> paths) {
            this.mdcKey = mdcKey;
            this.paths = paths;
        }

        public String getMdcKey() {
            return mdcKey;
        }

        public List<String> getPaths() {
            return paths;
        }

        protected void setMdcKey(String mdcKey) {
            if (mdcKey == null || mdcKey.isEmpty()) {
                throw new IllegalArgumentException(MDC_KEY_ERROR);
            }
            this.mdcKey = mdcKey;
        }

        protected void setPaths(List<String> paths) {
            if (paths == null || paths.isEmpty()) {
                throw new IllegalArgumentException(JSON_PATH_ERROR);
            }
            this.paths = paths;
        }

        protected void addPaths(List<String> paths) {
            if (paths == null || paths.isEmpty()) {
                throw new IllegalArgumentException(JSON_PATH_ERROR);
            }
            this.paths.addAll(paths);
        }

        protected void addPath(String path) {
            if (path == null || path.isEmpty()) {
                throw new IllegalArgumentException(JSON_PATH_ERROR);
            }
            this.paths.add(path);
        }
    }

    protected MdcTopicFilter(String rawFilters) {
        for (String filter : rawFilters.split("\\s*,\\s*")) {
            FilterRule rule = createFilterRule(filter);
            rules.put(rule.mdcKey, rule);
        }
    }

    private FilterRule createFilterRule(String filter) {
        String[] filterKeyPaths = filter.split("\\s*=\\s*");
        if (filterKeyPaths.length != 2) {
            throw new IllegalArgumentException("could not parse filter rule");
        }

        String filterKey = filterKeyPaths[0];
        String paths = filterKeyPaths[1];
        List<String> filterPaths = new ArrayList<>(Arrays.asList(paths.split("(?<!\\|)\\|(?!\\|)")));
        return new FilterRule(filterKey, filterPaths);
    }

    /**
     * Gets all the filter rules for the topic.
     *
     * @return an array list of the rules for the topic
     */
    protected List<FilterRule> getFilterRule() {
        return new ArrayList<>(rules.values());
    }

    /**
     * Gets the filter rule for the specified key.
     *
     * @param mdcKey the key to the filter rule
     * @return the filter rule associated with the key
     */
    protected FilterRule getFilterRule(String mdcKey) {
        if (mdcKey == null || mdcKey.isEmpty()) {
            throw new IllegalArgumentException(MDC_KEY_ERROR);
        }
        return rules.get(mdcKey);
    }

    /**
     * Adds a filter rule for the specified key and path.
     *
     * @param mdcKey the key to the filter rule
     * @param path the json path to the key
     * @return the filter rule that was added for the topic
     */
    protected FilterRule addFilterRule(String mdcKey, String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException(JSON_PATH_ERROR);
        }
        return addFilterRule(mdcKey, Arrays.asList(path));
    }

    /**
     * Adds a filter rule for the specified key and paths.
     *
     * @param mdcKey the key to the filter rule
     * @param paths the list of potential paths to the key
     * @return the filter rule that was added for the topic
     */
    protected FilterRule addFilterRule(String mdcKey, List<String> paths) {
        if (mdcKey == null || mdcKey.isEmpty()) {
            throw new IllegalArgumentException(MDC_KEY_ERROR);
        }

        if (paths == null || paths.isEmpty()) {
            throw new IllegalArgumentException(JSON_PATH_ERROR);
        }

        if (rules.containsKey(mdcKey)) {
            throw new IllegalArgumentException("a filter rule already exists for key: " + mdcKey);
        }

        FilterRule rule = new FilterRule(mdcKey, paths);
        rules.put(mdcKey, rule);
        return rule;
    }

    /**
     * Modifies an existing filter rule by adding the specified path.
     *
     * @param mdcKey the key to the filter rule
     * @param path the path to the key
     * @return the filter rule that was modified
     */
    protected FilterRule modifyFilterRule(String mdcKey, String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException(JSON_PATH_ERROR);
        }
        return modifyFilterRule(mdcKey, Arrays.asList(path));
    }

    /**
     * Modifies an existing filter rule by adding the specified paths.
     *
     * @param mdcKey the key to the filter rule
     * @param paths the list of potential paths to the key
     * @return the filter rule that was modified
     */
    protected FilterRule modifyFilterRule(String mdcKey, List<String> paths) {
        if (mdcKey == null || mdcKey.isEmpty()) {
            throw new IllegalArgumentException(MDC_KEY_ERROR);
        }

        if (paths == null || paths.isEmpty()) {
            throw new IllegalArgumentException(JSON_PATH_ERROR);
        }

        if (!rules.containsKey(mdcKey)) {
            throw new IllegalArgumentException("a filter rule doesn't exist for key: " + mdcKey);
        }

        FilterRule rule = rules.get(mdcKey);
        rule.addPaths(paths);
        return rule;
    }

    /**
     * Modifies an existing filter rule's key and replaces the paths with the specified
     * paths.
     *
     * @param oldMdcKey the old key to the filter rule
     * @param newMdcKey the new key to the filter rule
     * @param paths the list of potential paths to the key
     * @return the filter rule that was modified
     */
    protected FilterRule modifyFilterRule(String oldMdcKey, String newMdcKey, List<String> paths) {
        if (oldMdcKey == null || oldMdcKey.isEmpty()) {
            throw new IllegalArgumentException("current mdcKey must be provided");
        }

        if (newMdcKey == null || newMdcKey.isEmpty()) {
            throw new IllegalArgumentException("new mdcKey must be provided");
        }

        if (oldMdcKey.equals(newMdcKey)) {
            throw new IllegalArgumentException("the old and new mdcKey are equivalent");
        }
        if (paths == null || paths.isEmpty()) {
            throw new IllegalArgumentException(JSON_PATH_ERROR);
        }

        if (rules.containsKey(newMdcKey)) {
            throw new IllegalArgumentException("a filter rule already exists for key: " + newMdcKey);
        }

        FilterRule rule = rules.remove(oldMdcKey);
        if (rule == null) {
            throw new IllegalArgumentException("a filter rule doesn't exist for key: " + oldMdcKey);
        }

        rule.setMdcKey(newMdcKey);
        rule.setPaths(paths);
        rules.put(newMdcKey, rule);
        return rule;
    }

    /**
     * Deletes all filter rules for the topic filter.
     */
    protected void deleteFilterRule() {
        rules.clear();
    }

    /**
     * Deletes an existing filter rule.
     *
     * @param mdcKey the key to the filter rule
     * @return the filter rule that was deleted
     */
    protected FilterRule deleteFilterRule(String mdcKey) {
        if (mdcKey == null || mdcKey.isEmpty()) {
            throw new IllegalArgumentException(MDC_KEY_ERROR);
        }
        return rules.remove(mdcKey);
    }

    /**
     * Finds all fields for each topic filter rule. The results are stored in a map that
     * is indexed by the MDC key. Each MDC key has a list of results as multiple
     * occurrences of a key can be found in a JSON document.
     *
     * @param json the json string to be parsed
     * @return a map of mdc keys and list of results for each key
     */
    protected Map<String, List<String>> find(String json) {
        Map<String, List<String>> results = new HashMap<>();
        for (FilterRule rule : rules.values()) {
            List<String> matches = new ArrayList<>();
            for (String path : rule.getPaths()) {

                try {
                    matches = JsonProtocolFilter.filter(json, path);
                } catch (Exception e) {
                    logger.debug("Could not filter on path {} because of {}", path, e.getMessage(), e);
                }

                if (!matches.isEmpty()) {
                    break;
                } else {
                    logger.error("Could not find path {} in json {}", path, json);
                }

            }
            results.put(rule.getMdcKey(), matches);
        }
        return results;
    }

    /**
     * Finds all occurrences of a field in a JSON document based on the filter rule paths.
     *
     * @param json the json string to be parsed
     * @return a list of matches from the JSON document
     */
    protected List<String> find(String json, String mdcKey) {
        List<String> matches = new ArrayList<>();
        for (String path : rules.get(mdcKey).getPaths()) {

            try {
                matches = JsonProtocolFilter.filter(json, path);
            } catch (Exception e) {
                logger.debug("Could not filter on path {} because of {}", path, e.getMessage(), e);
            }

            if (!matches.isEmpty()) {
                break;
            }

        }

        if (matches.isEmpty()) {
            logger.error("Could not find any matches for key {} in json {}", mdcKey, json);
        }

        return matches;
    }
}
