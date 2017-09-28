/*-
 * ============LICENSE_START=======================================================
 * Configuration Test
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

package org.onap.policy.drools.protocol.coders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.onap.policy.drools.protocol.coders.JsonProtocolFilter;
import org.onap.policy.drools.protocol.coders.JsonProtocolFilter.FilterRule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.onap.policy.drools.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class JsonProtocolFilterTest {

	private static final Logger logger = LoggerFactory.getLogger(JsonProtocolFilterTest.class);

	private static final String NAME1 = "name1";
	private static final String REGEX1 = "regex1";

	private static final String NAME2 = "name2";
	private static final String REGEX2 = "regex2";

	private static final String NAME3 = "name3";
	private static final String REGEX3 = "regex3";

	private static final String NAME4 = "name4";
	private static final String REGEX4a = "regex4a";
	private static final String REGEX4b = "regex4b";   


	@Test
	public void test() {
            
		//      ********************   D E F I N E   f i l t e r R u l e   O b j e c t s   ***************************
		//      DEFINE one (1) filterRule object (using constructor without parms passed; instead use set methods  
		FilterRule filterRule1 = new FilterRule();
		filterRule1.setName(NAME1);
		filterRule1.setRegex(REGEX1);
		assertEquals(filterRule1.getName(), NAME1);
		assertEquals(filterRule1.getRegex(), REGEX1);

		//      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~    	
		//      DEFINE four (4) filterRule objects (using constructor passing the field values       	
		FilterRule filterRule2 = new FilterRule(NAME2, REGEX2);
		assertEquals(filterRule2.getName(), NAME2);
		assertEquals(filterRule2.getRegex(), REGEX2);

		FilterRule filterRule3 = new FilterRule(NAME3, REGEX3);
		assertEquals(filterRule3.getName(), NAME3);
		assertEquals(filterRule3.getRegex(), REGEX3);

		FilterRule filterRule4a = new FilterRule(NAME4, REGEX4a);
		assertEquals(filterRule4a.getName(), NAME4);
		assertEquals(filterRule4a.getRegex(), REGEX4a);

		FilterRule filterRule4b = new FilterRule(NAME4, REGEX4b);
		assertEquals(filterRule4b.getName(), NAME4);
		assertEquals(filterRule4b.getRegex(), REGEX4b);



		//      ************************   D E F I N E   f i l t e r   L i s t s  ************************************	
		//      DEFINE rawFiltersA     	
		List<Pair<String,String>> rawFiltersA = new ArrayList<>();
		rawFiltersA.add(new Pair<String,String>(filterRule1.getName(), filterRule1.getRegex()));
		rawFiltersA.add(new Pair<String,String>(filterRule2.getName(), filterRule2.getRegex()));

		//      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		//      DEFINE filtersA 
		List<FilterRule> filtersA = new ArrayList<>();
		for (Pair<String, String> filterPair: rawFiltersA) {
			if  (filterPair.first() == null || filterPair.first().isEmpty()) {
				continue;
			}
			filtersA.add(new FilterRule(filterPair.first(), filterPair.second()));
		}



		//      ***********   I N S T A N T I A T E   J s o n P r o t o c o l F i l t e r   O b j e c t s   ********** 
		//      INSTANTIATE protocolFilterA  (passing raw filters to the 'fromRawFilters' constructor)	
		JsonProtocolFilter protocolFilterA = JsonProtocolFilter.fromRawFilters(rawFiltersA);
		assertTrue(protocolFilterA.isRules());
		assertEquals(protocolFilterA.getRules().toString(), filtersA.toString());

		//      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~		
		//      INSTANTIATE protocolFilterB  (passing filters list to constructor which accepts such)
		JsonProtocolFilter protocolFilterB = new JsonProtocolFilter(filtersA);
		assertTrue(protocolFilterB.isRules());
		assertEquals(protocolFilterB.getRules(), filtersA);

		//      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~		
		//      INSTANTIATE protocolFilterC  (using constructor without parms; add instead using setRules() method 
		JsonProtocolFilter protocolFilterC = new JsonProtocolFilter();
		protocolFilterC.setRules(filtersA);
		assertTrue(protocolFilterC.isRules());
		assertEquals(protocolFilterC.getRules(), filtersA);



		//      ***   D E F I N E   o t h e r   f i l t e r   L i s t s   f o r   v a l i d a t i o n s   ************	
		//      DEFINE rawFiltersB   
		List<Pair<String,String>> rawFiltersB = new ArrayList<>();
		rawFiltersB.add(new Pair<String,String>(filterRule1.getName(), filterRule1.getRegex()));
		rawFiltersB.add(new Pair<String,String>(filterRule2.getName(), filterRule2.getRegex()));
		rawFiltersB.add(new Pair<String,String>(filterRule3.getName(), filterRule3.getRegex()));
		rawFiltersB.add(new Pair<String,String>(filterRule4a.getName(), filterRule4a.getRegex()));
		rawFiltersB.add(new Pair<String,String>(filterRule4b.getName(), filterRule4b.getRegex()));

		//      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		//      DEFINE filtersB 
		List<FilterRule> filtersB = new ArrayList<>();
		for (Pair<String, String> filterPair: rawFiltersB) {
			filtersB.add(new FilterRule(filterPair.first(), filterPair.second()));
		}



		//      ***********   A D D   T O   p r o t o c o l F i l t e r B   3   m o r e   f i l t e r s   ************		
		protocolFilterB.addRule(filterRule3.getName(), filterRule3.getRegex());
		protocolFilterB.addRule(filterRule4a.getName(), filterRule4a.getRegex());
		protocolFilterB.addRule(filterRule4b.getName(), filterRule4b.getRegex());

		//      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		//      VALIDATE that protocolFilterB now contains filters passed using filtersA and the new ones just added	
		assertEquals(protocolFilterB.getRules().toString(), filtersB.toString());



		//      ************   D E L E T E   f i l t e r s   f r o m   p r o t o c o l F i l t e r B       ***********
		//      DELETE specific filter from protocolFilterB by passing both the name & regex values			
		protocolFilterB.deleteRule(NAME3, REGEX3);

		//      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		//      DELETE all filters from protocolFilterB that have a match to the same name value	
		protocolFilterB.deleteRules(NAME4);

		//      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		//      VALIDATE that protocolFilterB now only contains the filters that were originally passed using filtersA	
		assertEquals(protocolFilterB.getRules(), filtersA);



		//      ************   A C C E P T   J S O N   I F   I T   P A S S E S   A L L   F I L T E R S     ***********
		//      ACCEPT TRUE a JSON that passes all filters
		String jsonA = "{ \"name1\":\"regex1\",\"name2\":\"regex2\"}";
		assertTrue(protocolFilterA.accept(jsonA));
		//      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		//      ACCEPT FALSE a JSON that does NOT pass all filters	  
		String jsonB = "{ \"name1\":\"regex1\"}";
		assertFalse(protocolFilterA.accept(jsonB));

	}

}
