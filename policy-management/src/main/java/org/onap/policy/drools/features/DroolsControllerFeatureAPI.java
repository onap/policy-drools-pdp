/*-
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.features;

import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.utils.OrderedService;
import org.onap.policy.drools.utils.OrderedServiceImpl;

/**
 * Drools Controller Feature API.   Hooks into the Drools Controller operations.
 */
public interface DroolsControllerFeatureAPI extends OrderedService {

	  /**
	   * intercepts before the Drools Controller gives the Policy Container a fact to
	   * insert into its Policy Sessions
	   *
	   * @return true if this feature intercepts and takes ownership
	   * of the operation preventing the invocation of
	   * lower priority features.   False, otherwise.
	   */
	  default boolean beforeInsert(DroolsController controller, Object fact) {return false;}

	  /**
	   * called after a fact is injected into the Policy Container
	   *
	   * @return true if this feature intercepts and takes ownership
	   * of the operation preventing the invocation of
	   * lower priority features.   False, otherwise.
	   */
	  default boolean afterInsert(DroolsController controller, Object fact, boolean successInsert) {return false;}

	  /**
	   * Feature providers implementing this interface
	   */
	  public static final OrderedServiceImpl<DroolsControllerFeatureAPI> providers =
			  						new OrderedServiceImpl<DroolsControllerFeatureAPI>(DroolsControllerFeatureAPI.class);
}
