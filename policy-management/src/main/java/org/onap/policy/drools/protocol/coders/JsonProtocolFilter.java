/*
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

import java.util.ArrayList;
import java.util.List;

import java.util.concurrent.CopyOnWriteArrayList;
import org.onap.policy.drools.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * JSON Protocol Filter.
 */
public class JsonProtocolFilter {
	
	private static final String MISSING_RULE_NAME = "no rule name provided";
	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger(JsonProtocolFilter.class);
	
	/**
	 * Helper class to collect Filter information
	 */
	public static class FilterRule {
		/**
		 * Field name
		 */
		private String name;
		
		/**
		 * Field Value regex
		 */
		private String regex;
		
		/**
		 * Filter Constructor
		 * 
		 * @param name field name
		 * @param regex field regex value
		 */
		public FilterRule(String name, String regex) {
			this.setName(name);
			this.setRegex(regex);
		}

		/**
		 * Default constructor (for serialization only)
		 */
		public FilterRule() {
			super();
		}

		/**
		 * gets name
		 * 
		 * @return
		 */
		public String getName() {
			return name;
		}

		/**
		 * gets regex
		 * 
		 * @return
		 */
		public String getRegex() {
			return regex;
		}

		/**
		 * sets field name
		 * @param name field name
		 */
		public void setName(String name) {
			if (name == null || name.isEmpty())
				throw new IllegalArgumentException("filter field name must be provided");

			this.name = name;
		}

		/**
		 * sets regex name
		 * @param regex
		 */
		public void setRegex(String regex) {
		    if (regex == null || regex.isEmpty())
		    	this.regex = ".*";

			this.regex = regex;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("Filter [name=").append(name).append(", regex=").append(regex).append("]");
			return builder.toString();
		}
	}
	
	/**
	 * all the filters to be applied
	 */
	protected List<FilterRule> rules = new CopyOnWriteArrayList<>();
	
	/**
	 * Create a Protocol Filter
	 * 
	 * @throws IllegalArgumentException an invalid input has been provided
	 */
	public JsonProtocolFilter() {}
	
	/**
	 * 
	 * @param filters filter list
	 * 
	 * @throws IllegalArgumentException an invalid input has been provided
	 */
	public JsonProtocolFilter(List<FilterRule> filters) {
		List<FilterRule> temp = new ArrayList<>();
		for (FilterRule rule : filters) {
			if (rule.getName() == null || rule.getName().isEmpty()) {
					continue;
			}

			if (rule.getRegex() == null || rule.getRegex().isEmpty()) {
				rule.setRegex(".*");
			}

			temp.add(rule);
		}

		this.rules.addAll(temp);
	}
	
	/**
	 * 
	 * @param rawFilters raw filter initialization
	 * 
	 * @throws IllegalArgumentException an invalid input has been provided
	 */
	public static JsonProtocolFilter fromRawFilters(List<Pair<String, String>> rawFilters) {
		
		if (rawFilters == null) {
			throw new IllegalArgumentException("No raw filters provided");
		}
		
		List<FilterRule> filters = new ArrayList<>();
		for (Pair<String, String> filterPair: rawFilters) {
			if  (filterPair.first() == null || filterPair.first().isEmpty()) {
				continue;
			}
			
			filters.add(new FilterRule(filterPair.first(), filterPair.second()));
		}
		return new JsonProtocolFilter(filters);
	}

	/**
	 * are there any filters?
	 * 
	 * @return true if there are filters, false otherwise
	 */
	public boolean isRules() {
		return !this.rules.isEmpty();
	}

	/**
	 * accept a JSON string as conformant it if passes all filters
	 * 
	 * @param json json is a JSON object
	 * @return true if json string is conformant
	 * 
	 * @throws IllegalArgumentException an invalid input has been provided
	 */
	public boolean accept(JsonElement json) {
		if (json == null) {
			throw new IllegalArgumentException("no JSON provided");
		}

		if (!json.isJsonObject()) {
			return false;
		}

		if (rules.isEmpty()) {
			return true;
		}

		try {
			JsonObject event = json.getAsJsonObject();
			for (FilterRule filter: rules) {
				if (filter.getRegex() == null ||
					filter.getRegex().isEmpty() ||
					".*".equals(filter.getRegex())) {
					
					// Only check for presence
					if (!event.has(filter.getName())) {
						return false;
					}
				} else {
					JsonElement field = event.get(filter.getName());
					if (field == null) {
						return false;
					}
					
					String fieldValue = field.getAsString();
					if (!fieldValue.matches(filter.getRegex())) {
						return false;
					}
				}
			}
			return true;
		} catch (Exception e) {
			throw new IllegalArgumentException(e);
		}
	}
	
	/**
	 * accept a JSON string as conformant it if passes all filters
	 * 
	 * @param json json string
	 * @return true if json string is conformant
	 * 
	 * @throws IllegalArgumentException an invalid input has been provided
	 */
	public boolean accept(String json) {
		if (json == null || json.isEmpty()) {
			throw new IllegalArgumentException("no JSON provided");
		}
		
		if (rules.isEmpty()) {
			return true;
		}
		
		try {
			JsonElement element = new JsonParser().parse(json);
			if (element == null || !element.isJsonObject()) {
				return false;
			}
			
			return this.accept(element.getAsJsonObject());
		} catch (IllegalArgumentException ile) {
			throw ile;
		} catch (Exception e) {
			logger.info("{}: cannot accept {} because of {}", 
					    this, json, e.getMessage(), e);
			throw new IllegalArgumentException(e);			
		}
	}

	public List<FilterRule> getRules() {
		return new ArrayList<>(this.rules);
	}

	public List<FilterRule> getRules(String name) {
		if (name == null || name.isEmpty())
			throw new IllegalArgumentException(MISSING_RULE_NAME);

		ArrayList<FilterRule> temp = new ArrayList<>();
		for (FilterRule rule : this.rules) {
			if (rule.getName().equals(name)) {
				temp.add(rule);
			}
		}
		return temp;
	}

	public void setRules(List<FilterRule> rulesFilters) {
		if (rulesFilters == null)
			throw new IllegalArgumentException("no rules provided");

	    this.rules.clear();
	    this.rules.addAll(rulesFilters);
	}
	
	public void deleteRules(String name) {
		if (name == null || name.isEmpty())
			throw new IllegalArgumentException(MISSING_RULE_NAME);

		List<FilterRule> temp = new ArrayList<>();
		for (FilterRule rule : this.rules) {
			if (rule.name.equals(name)) {
				temp.add(rule);
			}
		}
		this.rules.removeAll(temp);
	}

	public void deleteRule(String name, String regex) {
		if (name == null || name.isEmpty())
			throw new IllegalArgumentException(MISSING_RULE_NAME);

		String nonNullRegex = regex;
		if (regex == null || regex.isEmpty()) {
			nonNullRegex = ".*";
		}

		List<FilterRule> temp = new ArrayList<>();
		for (FilterRule rule : this.rules) {
		    if (rule.name.equals(name) && rule.getRegex().equals(nonNullRegex)) {
		    	temp.add(rule);
		    }
		}

		this.rules.removeAll(temp);
	}
	
	public void addRule(String name, String regex) {
		if (name == null || name.isEmpty())
			throw new IllegalArgumentException(MISSING_RULE_NAME);

		String nonNullRegex = regex;
		if (regex == null || regex.isEmpty()) {
			nonNullRegex = ".*";
		}

		for (FilterRule rule : this.rules) {
		    if (rule.getName().equals(name) && rule.getRegex().equals(regex)) {
					return;
		    }
		}

		this.rules.add(new FilterRule(name, nonNullRegex));
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("JsonProtocolFilter [rules=").append(rules).append("]");
		return builder.toString();
	}

}
