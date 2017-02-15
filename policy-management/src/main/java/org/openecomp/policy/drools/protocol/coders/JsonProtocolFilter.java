/*-
 * ============LICENSE_START=======================================================
 * policy-management
 * ================================================================================
 * Copyright (C) 2017 AT&T Intellectual Property. All rights reserved.
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

package org.openecomp.policy.drools.protocol.coders;

import java.util.ArrayList;
import java.util.List;

import org.openecomp.policy.drools.utils.Pair;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * JSON Protocol Filter.  Evaluates an JSON string and evaluates if it
 * passes its filters.
 */
public class JsonProtocolFilter {
	
	/**
	 * Helper class to collect Filter information
	 */
	public static class FilterRule {
		/**
		 * Field name
		 */
		protected String name;
		
		/**
		 * Field Value regex
		 */
		protected String regex;
		
		/**
		 * Filter Constructor
		 * 
		 * @param name field name
		 * @param regex field regex value
		 */
		public FilterRule(String name, String regex) {
			this.name = name;
			this.regex = regex;
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
			this.name = name;
		}

		/**
		 * sets regex name
		 * @param regex
		 */
		public void setRegex(String regex) {
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
	protected List<FilterRule> rules = new ArrayList<FilterRule>();
	
	/**
	 * 
	 * @param rawFilters raw filter initialization
	 * 
	 * @throws IllegalArgumentException an invalid input has been provided
	 */
	public static JsonProtocolFilter fromRawFilters(List<Pair<String, String>> rawFilters) 
		throws IllegalArgumentException {
		
		if (rawFilters == null) {
			throw new IllegalArgumentException("No raw filters provided");
		}
		
		List<FilterRule> filters = new ArrayList<FilterRule>();
		for (Pair<String, String> filterPair: rawFilters) {
			if  (filterPair.first() == null || filterPair.first().isEmpty()) {
				// TODO: warn
				continue;
			}
			
			filters.add(new FilterRule(filterPair.first(), filterPair.second()));
		}
		return new JsonProtocolFilter(filters);
	}
	
	/**
	 * Create a Protocol Filter
	 * 
	 * @throws IllegalArgumentException an invalid input has been provided
	 */
	public JsonProtocolFilter() throws IllegalArgumentException {}
	
	/**
	 * 
	 * @param rawFilters raw filter initialization
	 * 
	 * @throws IllegalArgumentException an invalid input has been provided
	 */
	public JsonProtocolFilter(List<FilterRule> filters) throws IllegalArgumentException {
		this.rules = filters;
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
	public synchronized boolean accept(JsonElement json) throws IllegalArgumentException {
		if (json == null) {
			throw new IllegalArgumentException("no JSON provided");
		}
		
		if (rules.isEmpty()) {
			return true;
		}
		
		try {
			if (json == null || !json.isJsonObject()) {
				return false;
			}
			
			JsonObject event = json.getAsJsonObject();
			for (FilterRule filter: rules) {
				if (filter.regex == null || 
					filter.regex.isEmpty() ||  
					filter.regex.equals(".*")) {
					
					// Only check for presence
					if (!event.has(filter.name)) {
						return false;
					}
				} else {
					JsonElement field = event.get(filter.name);
					if (field == null) {
						return false;
					}
					
					String fieldValue = field.getAsString();
					if (!fieldValue.matches(filter.regex)) {
						return false;
					}
				}
			}
			return true;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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
	public synchronized boolean accept(String json) throws IllegalArgumentException {
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
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new IllegalArgumentException(e);			
		}
	}

	public List<FilterRule> getRules() {
		return rules;
	}

	public synchronized void setRules(List<FilterRule> rulesFilters) {
		this.rules = rulesFilters;
	}
	
	public synchronized void deleteRules(String name) {
		for (FilterRule rule : new ArrayList<>(this.rules)) {
		    if (rule.name.equals(name)) {
		    	this.rules.remove(rule);
		    }
		}
	}
	
	public List<FilterRule> getRules(String name) {
		ArrayList<FilterRule> temp = new ArrayList<>();
		for (FilterRule rule : new ArrayList<>(this.rules)) {
		    if (rule.name.equals(name)) {
		    	temp.add(rule);
		    }
		}
		return temp;
	}
	
	public synchronized void deleteRule(String name, String regex) {
		for (FilterRule rule : new ArrayList<>(this.rules)) {
		    if (rule.name.equals(name) && rule.regex.equals(regex)) {
		    	this.rules.remove(rule);
		    }
		}
	}
	
	public synchronized void addRule(String name, String regex) {
		for (FilterRule rule : new ArrayList<>(this.rules)) {
		    if (rule.name.equals(name) && rule.regex.equals(regex)) {
		    	return;
		    }
		}
		
		this.rules.add(new FilterRule(name,regex));
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("JsonProtocolFilter [rules=").append(rules).append("]");
		return builder.toString();
	}

}
