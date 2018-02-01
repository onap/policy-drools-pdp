/*-
 * ============LICENSE_START=======================================================
 * policy-utils
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

package org.onap.policy.drools.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class PairTripleTest {
    
    @Test
    public void pairTest() {
        Pair<String, String> p = new Pair<String, String>("foo", "bar");
        
       assertEquals("foo", p.first());
       assertEquals("bar", p.second());
       assertEquals("foo", p.getFirst());
       assertEquals("bar", p.getSecond());
       
       p.first("one");
       p.second("two");

       assertEquals("one", p.first());
       assertEquals("two", p.second());
       assertEquals("one", p.getFirst());
       assertEquals("two", p.getSecond());
       
       assertNotNull(p.toString());
        
    } 

    @Test
    public void tripleTest() {
        Triple<String, String, String> t = new Triple<String, String,String>("foo", "bar", "fiz");
        
       assertEquals("foo", t.first());
       assertEquals("bar", t.second());
       assertEquals("fiz", t.third());
       
       t.first("one");
       t.second("two");
       t.third("three");

       assertEquals("one", t.first());
       assertEquals("two", t.second());
       assertEquals("three", t.third());   
    }

}
