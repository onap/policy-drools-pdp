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

package org.openecomp.policy.drools.utils;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * This class is a template for building a sorted list of service instances,
 * which are discovered and created using 'ServiceLoader'. 
 */
public class OrderedServiceImpl<T extends OrderedService>
{
  // sorted list of instances implementing the service
  private List<T> implementers = null;

  // 'ServiceLoader' that is used to discover and create the services
  private ServiceLoader<T> serviceLoader = null; //ServiceLoader.load(T.class);

  /**
   * Constructor - create the 'ServiceLoader' instance
   *
   * @param clazz the class object associated with 'T' (I supposed it could
   *	be a subclass, but I'm not sure this is useful)
   */
  public OrderedServiceImpl(Class clazz)
  {
	// This constructor wouldn't be needed if 'T.class' was legal
	serviceLoader = ServiceLoader.load(clazz);
  }

  /**
   * @return the sorted list of services implementing interface 'T' discovered
   *	by 'ServiceLoader'.
   */
  public synchronized List<T> getList()
  {
	if (implementers == null)
	  {
		rebuildList();
	  }
	return(implementers);
  }

  /**
   * This method is called by 'getList', but could also be called directly if
   * we were running with a 'ClassLoader' that supported the dynamic addition
   * of JAR files. In this case, it could be invoked in order to discover any
   * new services implementing interface 'T'. This is probably a relatively
   * expensive operation in terms of CPU and elapsed time, so it is best if it
   * isn't invoked too frequently.
   *
   * @return the sorted list of services implementing interface 'T' discovered
   *	by 'ServiceLoader'.
   */
  public synchronized List<T> rebuildList()
  {
	// build a list of all of the current implementors
	List<T> tmp = new LinkedList<T>();
	for (T service : serviceLoader)
	  {
		tmp.add(service);
	  }

	// Sort the list according to sequence number, and then alphabetically
	// according to full class name.
	Collections.sort(tmp, new Comparator<T>()
					 {
					   public int compare(T o1, T o2)
						 {
						   int s1 = o1.getSequenceNumber();
						   int s2 = o2.getSequenceNumber();
						   int rval;
						   if (s1 < s2)
							 {
							   rval = -1;
							 }
						   else if (s1 > s2)
							 {
							   rval = 1;
							 }
						   else
							 {
							   rval = o1.getClass().getName().compareTo
								 (o2.getClass().getName());
							 }
						   return(rval);
						 }
					 });

	// create an unmodifiable version of this list
	implementers = Collections.unmodifiableList(tmp);
	System.out.println("***** OrderedServiceImpl implementers:\n" + implementers);
	return(implementers);
  }
}
