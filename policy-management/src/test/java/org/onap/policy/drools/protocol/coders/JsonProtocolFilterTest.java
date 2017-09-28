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

import java.util.Properties;

import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.junit.Test;
import org.onap.policy.drools.protocol.coders.JsonProtocolFilter;
import org.onap.policy.drools.protocol.coders.JsonProtocolFilter.FilterRule;
import org.onap.policy.drools.protocol.coders.EventProtocolCoder.CoderFilters;
import org.onap.policy.drools.protocol.coders.TopicCoderFilterConfiguration.CustomGsonCoder;
import org.onap.policy.drools.protocol.coders.TopicCoderFilterConfiguration.CustomJacksonCoder;
import org.onap.policy.drools.protocol.configuration.ControllerConfiguration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.onap.policy.drools.server.restful.test.RestManagerTest;
import org.onap.policy.drools.utils.Pair;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class JsonProtocolFilterTest {
	
	private static final Logger logger = LoggerFactory.getLogger(JsonProtocolFilterTest.class);
    
    private static final String NAME1 = "name1";
    private static final String REGEX1 = "regex1";

    private static final String NAME2 = "name2";  // MSORequest
    private static final String REGEX2 = ".*";  // .*
    private static final String REGEX2b = "regex2b";  // regex2b
   
    private static final String NAME3 = "name3";
    private static final String REGEX3a = "regex3a";
    private static final String REGEX3b = "regex3b";
    
    private static final String NAME4 = "name4";
    private static final String REGEX4 = "regex4";
 
    @Test
    public void test() {

    /**
     *  1. instatiate FilterRule (1) by passing parms  
     */
    	FilterRule filterRule1 = new FilterRule(NAME1, REGEX1);
    	assertTrue(filterRule1.equals(filterRule1));
     	assertFalse(filterRule1.equals(new Object()));
  	
    	assertEquals(filterRule1.getName(), NAME1);
    	assertEquals(filterRule1.getRegex(), REGEX1);
    	
    /**
     *  2. instatiate FilterRule (2) without passing parms, using setters instead  
     */
   	   	FilterRule filterRule2 = new FilterRule();
  
    	filterRule2.setName(NAME2);
    	filterRule2.setRegex(REGEX2);

    	assertEquals(filterRule2.getName(), NAME2);
    	assertEquals(filterRule2.getRegex(), REGEX2);
    	
     /**
     *  3. test of filterRule toString() 
     */	
    	StringBuilder builder = new StringBuilder();
		builder.append("Filter [name=").append(NAME2).append(", regex=").append(REGEX2).append("]");
    	
    	assertEquals(filterRule2.toString(), builder.toString());
    	
     /**
     *  4.  define the list of filterRules by adding each filterRule 
     */	
    	List<Pair<String,String>> filterRules = new ArrayList<>();
       	
    	filterRules.add(new Pair<String,String>(filterRule2.getName(), filterRule2.getRegex()));
     	logger.info("Test 4:   filterRules = " + filterRules.toString());
     	
    	builder.setLength(0);
		builder.append("[Pair [first=").append(NAME2).append(", second=").append(REGEX2).append("]]");
    	logger.info("Test 4:   builder = " + builder);
    	assertEquals(filterRules.toString(), builder.toString());   	
    	
     /**
     *  5.  instantiate the entire JSONProtocolFilter passing it the entire filterRules list (see toString() test following)
     */	  	
    	JsonProtocolFilter protocolFilter = JsonProtocolFilter.fromRawFilters(filterRules);
     	
     /**
     *  6.  test of JsonProtocolFilter's toString() 
     */	
    	logger.info("Test 6:   protocolFilter = " + protocolFilter.toString());
    	builder.setLength(0);
    	builder.append("JsonProtocolFilter [rules=[Filter [name=" + NAME2 + ", regex=" + REGEX2 + "]]]");
	   	assertEquals(protocolFilter.toString(), builder.toString());
 	   	
     /**
     *  7.  test of protocolFilter's getRules() 
     */		
    	logger.debug("Test 7:   getRules  = " + protocolFilter.getRules().toString());
    	builder.setLength(0);
    	builder.append("[Filter [name=" + NAME2 + ", regex=" + REGEX2 + "]]");
		assertEquals(protocolFilter.getRules().toString(), builder.toString());
		
	 /**
	 *  8.  test of protocolFilter's isRules() 
	 */	
		assertTrue(protocolFilter.isRules());
		
	 /**
	 *  9.  test of protocolFilter's accept method to test json passed to it 
	 */	
		builder.setLength(0);
    	builder.append("{\"" + NAME2 + "\":\"" + REGEX2 + "\"}");
    	logger.info("Test 9:   builder = " + builder);
		assertTrue(protocolFilter.accept(builder.toString()));
  	
	 /**
	 * 10.  test of addRule (with getRules test included)
	 */	
		FilterRule filterRule3 = new FilterRule(NAME3, REGEX3a);
    	assertTrue(filterRule3.equals(filterRule3));
     	assertFalse(filterRule3.equals(new Object()));
     	
		protocolFilter.addRule(filterRule3.getName(), filterRule3.getRegex());
		logger.info("Test 10:  getRules  = " + protocolFilter.getRules().toString());
		
		builder.setLength(0);
    	builder.append("[Filter [name=" + NAME2 + ", regex=" + REGEX2 + "], Filter [name=" + NAME3 + ", regex=" + REGEX3a + "]]");

    	logger.info("builder = " + builder);
    	assertEquals(protocolFilter.getRules().toString(), builder.toString());
		 
   	 /**
     * 11.  test of getRules(String name)
     */	
    	FilterRule filterRule3b = new FilterRule(NAME3, REGEX3b);
    	assertTrue(filterRule3.equals(filterRule3));
     	assertFalse(filterRule3.equals(new Object()));
		protocolFilter.addRule(filterRule3b.getName(), filterRule3b.getRegex());
		logger.info("Test 11:  getRules  = " + protocolFilter.getRules().toString());
		
		builder.setLength(0);
    	builder.append("[Filter [name=" + NAME3 + ", regex=" + REGEX3a + "], Filter [name=" + NAME3 + ", regex=" + REGEX3b + "]]");

    	logger.info("Test 11:  builder = " + builder);
    	assertEquals(protocolFilter.getRules(NAME3).toString(), builder.toString());
    	
  	 /**
     * 12.  test of deleteRuletRule(String name, String regex)
     */	
    	protocolFilter.deleteRule(filterRule2.getName(), filterRule2.getRegex());
    	logger.info("Test 12:  getRules  = " + protocolFilter.getRules().toString());
    	
    	builder.setLength(0);
    	builder.append("[Filter [name=" + NAME3 + ", regex=" + REGEX3a + "], Filter [name=" + NAME3 + ", regex=" + REGEX3b + "]]");

    	logger.info("Test 12:  builder = " + builder);
    	assertEquals(protocolFilter.getRules().toString(), builder.toString());

 	 /**
     * 13.  test of deleteRules(String name) could delete multiple rules objects containing the same 'name' field value
     */	
	   	FilterRule filterRule4 = new FilterRule(NAME4, REGEX4);
    	assertTrue(filterRule4.equals(filterRule4));
     	assertFalse(filterRule4.equals(new Object()));
		protocolFilter.addRule(filterRule4.getName(), filterRule4.getRegex());
		logger.info("Test 13a:  getRules  = " + protocolFilter.getRules().toString());
		
    	builder.setLength(0);
    	builder.append("[Filter [name=" + NAME3 + ", regex=" + REGEX3a + "], Filter [name=" + NAME3 + ", regex=" + REGEX3b + "], Filter [name=" + NAME4 + ", regex=" + REGEX4 + "]]");

    	logger.info("Test 13a:  builder = " + builder);
    	assertEquals(protocolFilter.getRules().toString(), builder.toString());
    	logger.info("Test 13b:  getRules  = " + protocolFilter.getRules().toString());

    	protocolFilter.deleteRules(filterRule3.getName());
    	
    	logger.info("Test 13c:  getRules  = " + protocolFilter.getRules().toString());
    	
    	builder.setLength(0);
    	builder.append("[Filter [name=" + NAME4 + ", regex=" + REGEX4 + "]]");

    	logger.info("Test13b:  builder = " + builder);
     	assertEquals(protocolFilter.getRules().toString(), builder.toString());
   
   	
     	
     	
     	
    }


}
