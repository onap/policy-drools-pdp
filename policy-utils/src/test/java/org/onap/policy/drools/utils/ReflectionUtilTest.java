/*-
 * ============LICENSE_START=======================================================
 * policy-utils
 * ================================================================================
 * Copyright (C) 2017-2018, 2020 AT&T Intellectual Property. All rights reserved.
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class ReflectionUtilTest {
    
    public class ParentClass {

    }
    
    public class ChildClass extends ParentClass{

    }

    @Test
    public void testReflection() {
        
        try {

            Class<?> class1 = Class.forName("org.onap.policy.drools.utils.ReflectionUtil");
            
            ClassLoader classLoader = class1.getClassLoader();
            
            Class<?> class2 = ReflectionUtil.fetchClass(classLoader, "org.onap.policy.drools.utils.ReflectionUtil");
           
           
            assertTrue(ReflectionUtil.isClass(classLoader, "org.onap.policy.drools.utils.ReflectionUtil"));
            assertEquals(class1,class2);
            assertTrue(ReflectionUtil.isSubclass(ParentClass.class, ChildClass.class));
            assertFalse(ReflectionUtil.isSubclass(ChildClass.class, ParentClass.class));
            
            
        } catch (ClassNotFoundException e) {
            fail();
        }
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testException1() {
        ReflectionUtil.fetchClass(null, "org.onap.policy.drools.utils.ReflectionUtil");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testException2() {
        Class<?> class1;
        try {
            class1 = Class.forName("org.onap.policy.drools.utils.ReflectionUtil");
            ClassLoader classLoader = class1.getClassLoader();
            ReflectionUtil.fetchClass(classLoader, null);
        } catch (ClassNotFoundException e) {
            fail();
        }
    }

    @Test
    public void testException3() throws ClassNotFoundException {
        assertNull(ReflectionUtil.fetchClass(ClassLoader.getSystemClassLoader(), "foo.bar"));
    }
}
