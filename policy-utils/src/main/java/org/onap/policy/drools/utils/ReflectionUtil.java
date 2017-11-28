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
package org.onap.policy.drools.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Reflection utilities
 *
 */
public class ReflectionUtil {
	
	protected static final Logger logger = LoggerFactory.getLogger(ReflectionUtil.class);

	private ReflectionUtil(){
	}
	
	/**
	 * returns (if exists) a class fetched from a given classloader
	 * 
	 * @param classLoader the class loader
	 * @param className the class name
	 * @return the actual class
	 * @throws IllegalArgumentException if an invalid parameter has been passed in
	 */
	public static Class<?> fetchClass(ClassLoader classLoader, 
			                          String className) 
		throws IllegalArgumentException {
		if (classLoader == null)
			throw new IllegalArgumentException("A class loader must be provided");
		
		if (className == null)
			throw new IllegalArgumentException("A class name to be fetched in class loader " +
		                                       classLoader + " must be provided");
		
		try {
			return Class.forName(className, true, classLoader);
		} catch (Exception e) {
			logger.error("class {} fetched in {} does not exist", className, classLoader, e);
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
		return parent.isAssignableFrom(presumedSubclass);
	}

}
