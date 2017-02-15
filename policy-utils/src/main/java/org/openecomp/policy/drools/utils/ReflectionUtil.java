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

/**
 * 
 */
package org.openecomp.policy.drools.utils;

import org.openecomp.policy.common.logging.eelf.PolicyLogger;

/**
 * Reflection utilities
 *
 */
public class ReflectionUtil {
	
	/**
	 * returns (if exists) a class fetched from a given classloader
	 * 
	 * @param classLoader the class loader
	 * @param classname the class name
	 * @return the PolicyEvent class
	 * @throws IllegalArgumentException if an invalid parameter has been passed in
	 */
	public static Class<?> fetchClass(ClassLoader classLoader, 
			                          String classname) 
		throws IllegalArgumentException {
		
		PolicyLogger.info("FETCH-CLASS: " +  classname + " FROM " + classLoader);
		
		if (classLoader == null)
			throw new IllegalArgumentException("A class loader must be provided");
		
		if (classname == null)
			throw new IllegalArgumentException("A class name to be fetched in class loader " +
		                                       classLoader + " must be provided");
		
		try {
			Class<?> aClass = Class.forName(classname, 
					                        true, 
					                        classLoader);
			return aClass;
		} catch (Exception e) {
			e.printStackTrace();
			PolicyLogger.error("FETCH-CLASS: " + classname + " IN " + classLoader + " does NOT exist");
		}
		
		return null;
	}
	
	/**
	 * 
	 * @param classLoader target class loader
	 * @param classname class name to fetch
	 * @return true if exists
	 * @throws IllegalArgumentException if an invalid parameter has been passed in
	 */
	public static boolean isClass(ClassLoader classLoader, String classname) 
           throws IllegalArgumentException {
		return fetchClass(classLoader, classname) != null;
	}
	
	/**
	 * is a subclass?
	 * @param parent superclass
	 * @param presumedSubclass subclass
	 * @return
	 */
	public static boolean isSubclass(Class<?> parent, Class<?> presumedSubclass) {		
		PolicyLogger.debug("IS-SUBCLASS: superclass: " +  parent.getCanonicalName() + 
				          " subclass: " + presumedSubclass.getCanonicalName());
		return (parent.isAssignableFrom(presumedSubclass));
	}

}
