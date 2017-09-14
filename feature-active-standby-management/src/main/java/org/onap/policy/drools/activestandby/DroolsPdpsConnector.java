/*-
 * ============LICENSE_START=======================================================
 * feature-active-standby-management
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

package org.onap.policy.drools.activestandby;

import java.util.Collection;

public interface DroolsPdpsConnector {

	
	//return a list of PDPs, NOT including this PDP
	public Collection<DroolsPdp> getDroolsPdps();
	
	public void update(DroolsPdp pdp);
	
	//determines if the DroolsPdp parameter is considered "current" or expired (has it been too long since the Pdp sent an update)
	public boolean isPdpCurrent(DroolsPdp pdp);
	
	// Updates DESIGNATED boolean in PDP record.
	public void setDesignated(DroolsPdp pdp, boolean designated);
	
	// Marks droolspdpentity.DESIGNATED=false, so another PDP-D will go active.
	public void standDownPdp(String pdpId);
			
	// This is used in a JUnit test environment to manually
	// insert a PDP
	public void insertPdp(DroolsPdp pdp);
	
	// This is used in a JUnit test environment to manually
	// delete a PDP
	public void deletePdp(String pdpId);
		
	// This is used in a JUnit test environment to manually
	// clear the droolspdpentity table.
	public void deleteAllPdps();
	
	// This is used in a JUnit test environment to manually
	// get a PDP
	public DroolsPdpEntity getPdp(String pdpId);
	
	// Used by DroolsPdpsElectionHandler to determine if the currently designated
	// PDP has failed.
	public boolean hasDesignatedPdpFailed(Collection<DroolsPdp> pdps);

	
}
