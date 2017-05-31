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

package org.openecomp.policy.drools.features;

import java.util.Properties;

import org.openecomp.policy.drools.system.PolicyController;
import org.openecomp.policy.drools.utils.OrderedService;
import org.openecomp.policy.drools.utils.OrderedServiceImpl;

public interface PolicyControllerFeatureAPI extends OrderedService {

	  /**
	   * called before creating a controller with name 'name' and 
	   * properties 'properties'
	   * 
	   * @param name name of the the controller
	   * @param properties configuration properties
	   * 
	   * @return a policy controller.   A take over of the creation operation
	   * is performed by returning a non-null policy controller.   
	   * 'null' indicates that no take over has taken place, and processing should
	   * continue to the next feature provider.
	   */
	  public PolicyController beforeCreate(String name, Properties properties);

	  /**
	   * called after creating a controller with name 'name'
	   * 
	   * @param controller controller
	   * 
	   * @return true if this feature intercepts and takes ownership
	   * of the operation preventing the invocation of 
	   * lower priority features.   False, otherwise.
	   */
	  public boolean afterCreate(PolicyController controller);
	  
	  /**
	   * Feature providers implementing this interface
	   */
	  public static final OrderedServiceImpl<PolicyControllerFeatureAPI> providers = 
			  						new OrderedServiceImpl<PolicyControllerFeatureAPI>(PolicyControllerFeatureAPI.class);
}
